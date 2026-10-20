package com.createcivilization.capitol.common.managers;

import com.createcivilization.capitol.common.config.CapitolConfig;
import com.createcivilization.capitol.common.data.MailBody;
import com.createcivilization.capitol.common.data.MailHeader;
import com.createcivilization.capitol.common.data.MailSummary;
import com.createcivilization.capitol.common.data.Permission;
import com.createcivilization.capitol.common.data.Team;
import com.createcivilization.capitol.common.data.TeamMember;
import com.createcivilization.capitol.common.data.TeamRole;
import com.createcivilization.capitol.common.modules.database.CapitolDatabase;
import com.createcivilization.capitol.common.networking.packets.S2CInboxPage;
import com.createcivilization.capitol.common.networking.packets.S2CMailBody;
import com.createcivilization.capitol.common.networking.packets.S2CMailUnread;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-team (and external) mail: recipient resolution, permission/limit checks,
 * cooldowns, and system mail for plugin events.
 */
public final class MailManager {

	// which players a message is aimed at; externalTeamId wins when set
	public record Target(@Nullable Set<UUID> members, Set<Integer> roleIds, boolean wholeTeam,
						 @Nullable UUID externalTeamId) {}

	private static final Map<UUID, Long> lastSend = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> lastExternalSend = new ConcurrentHashMap<>();

	private MailManager() {}

	private static CapitolDatabase db() {
		return DatabaseManager.database;
	}

	public static RoleManager.Check send(Player sender, Team team, Target target, String subject, String body) {
		if (!CapitolConfig.MAIL_ENABLED.get()) return RoleManager.Check.deny("commands.capitol.mail.disabled");

		String cleanSubject = sanitize(subject, CapitolConfig.MAIL_SUBJECT_MAX.get());
		String cleanBody = sanitize(body, CapitolConfig.MAIL_BODY_MAX.get());
		if (cleanSubject.isBlank()) return RoleManager.Check.deny("commands.capitol.mail.empty_subject");

		long perms = db().getEffectivePermissions(sender.getUUID(), team);
		boolean external = target.externalTeamId() != null;
		Set<UUID> recipients = new HashSet<>();

		if (external) {
			RoleManager.Check resolved = resolveExternal(team, target, perms, recipients);
			if (!resolved.ok()) return resolved;
		} else {
			RoleManager.Check resolved = resolveInternal(team, target, perms, recipients);
			if (!resolved.ok()) return resolved;
		}

		recipients.remove(sender.getUUID());
		if (recipients.isEmpty()) return RoleManager.Check.deny("commands.capitol.mail.no_recipients");
		int max = CapitolConfig.MAIL_MAX_RECIPIENTS.get();
		if (recipients.size() > max) return RoleManager.Check.deny("commands.capitol.mail.too_many_recipients", max);

		RoleManager.Check cooldown = checkCooldown(sender, external);
		if (!cooldown.ok()) return cooldown;

		int inboxLimit = CapitolConfig.MAIL_INBOX_LIMIT.get();
		if (inboxLimit > 0) {
			for (UUID recipient : recipients) {
				if (db().countInboxMail(recipient) >= inboxLimit)
					return RoleManager.Check.deny("commands.capitol.mail.inbox_full");
			}
		}

		db().insertMail(sender.getUUID(), team.getId(),
			external ? target.externalTeamId() : null, cleanSubject, cleanBody, external, recipients);
		pushUnread(recipients);
		return RoleManager.Check.allow();
	}

	public static void sendSystemMail(UUID recipient, String subject, String body) {
		db().insertMail(null, null, null, sanitize(subject, CapitolConfig.MAIL_SUBJECT_MAX.get()),
			sanitize(body, CapitolConfig.MAIL_BODY_MAX.get()), false, List.of(recipient));
		pushUnread(Set.of(recipient));
	}

	/** Pushes the fresh unread count to any of these players who are online. */
	public static void pushUnread(Collection<UUID> recipients) {
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if (server == null) return;
		for (UUID recipient : recipients) {
			ServerPlayer online = server.getPlayerList().getPlayer(recipient);
			if (online != null) {
				PacketDistributor.sendToPlayer(online, new S2CMailUnread(db().countUnreadMail(recipient)));
			}
		}
	}

	//inbox / sent views

	// Rows per book page; the GUI lays out roughly this many at row height 14.
	public static final int PAGE_SIZE = 9;
	public static final String BOX_INBOX = "inbox";
	public static final String BOX_SENT = "sent";

	//Builds one page of a box with names already resolved, ready to send.
	public static S2CInboxPage buildPage(Player viewer, String box, int page) {
		UUID uuid = viewer.getUUID();
		boolean sent = BOX_SENT.equalsIgnoreCase(box);
		int cleanPage = Math.max(0, page);
		List<MailHeader> headers = sent
			? db().getSentPage(uuid, cleanPage, PAGE_SIZE)
			: db().getInboxPage(uuid, cleanPage, PAGE_SIZE);

		List<MailSummary> rows = new ArrayList<>(headers.size());
		for (MailHeader header : headers) {
			rows.add(new MailSummary(
				header.id(),
				sent ? null : playerName(viewer, header.senderUuid()),
				sent ? teamName(header.targetTeamId()) : teamName(header.senderTeamId()),
				header.subject(), header.sentAt(), header.read(), header.external(), header.recipients()));
		}
		return new S2CInboxPage(sent ? BOX_SENT : BOX_INBOX, cleanPage, db().countUnreadMail(uuid), rows);
	}

	// Full body of one mail; reading it clears the unread flag.
	@Nullable
	public static S2CMailBody open(Player viewer, long mailId) {
		MailBody body = db().getMailBody(mailId, viewer.getUUID());
		if (body == null) return null;
		db().markMailRead(mailId, viewer.getUUID());
		return new S2CMailBody(body.id(), playerName(viewer, body.senderUuid()), teamName(body.senderTeamId()),
			body.subject(), body.body(), body.sentAt(), body.external());
	}

	// Applies "READ" or "DELETE" to one mail.
	public static void applyAction(Player viewer, long mailId, String action) {
		if ("DELETE".equalsIgnoreCase(action)) db().deleteMailFor(mailId, viewer.getUUID());
		else db().markMailRead(mailId, viewer.getUUID());
	}

	//helpers

	// display name that also works for offline players via the profile cache
	@Nullable
	private static String playerName(Player viewer, @Nullable UUID uuid) {
		if (uuid == null) return null;
		MinecraftServer server = viewer.getServer();
		if (server != null) {
			ServerPlayer online = server.getPlayerList().getPlayer(uuid);
			if (online != null) return online.getName().getString();
			return server.getProfileCache().get(uuid).map(GameProfile::getName).orElse(null);
		}
		return null;
	}

	@Nullable
	private static String teamName(@Nullable UUID teamId) {
		if (teamId == null) return null;
		Team team = db().getTeam(teamId);
		return team == null ? null : team.getName();
	}

	private static RoleManager.Check resolveInternal(Team team, Target target, long perms, Set<UUID> recipients) {
		if (target.wholeTeam()) {
			if (!Permission.SEND_MAIL_TEAM_WIDE.hasPermission(perms))
				return RoleManager.Check.deny("commands.capitol.mail.no_permission");
			for (TeamMember member : db().getTeamMembers(team)) recipients.add(member.playerUUID());
		}
		if (target.roleIds() != null && !target.roleIds().isEmpty()) {
			if (!Permission.SEND_MAIL_ROLES.hasPermission(perms))
				return RoleManager.Check.deny("commands.capitol.mail.no_permission");
			for (int roleId : target.roleIds()) {
				TeamRole role = db().getRole(team, roleId);
				if (role == null) return RoleManager.Check.deny("commands.capitol.team.role.not_found");
				if (!role.isMailable()) return RoleManager.Check.deny("commands.capitol.mail.role_not_mailable", role.name());
				recipients.addAll(db().getRoleMembers(team, roleId));
			}
		}
		if (target.members() != null && !target.members().isEmpty()) {
			if (!Permission.SEND_MAIL_MEMBERS.hasPermission(perms))
				return RoleManager.Check.deny("commands.capitol.mail.no_permission");
			for (UUID memberId : target.members()) {
				if (db().isPlayerInTeam(memberId, team)) recipients.add(memberId);
			}
		}
		return RoleManager.Check.allow();
	}

	private static RoleManager.Check resolveExternal(Team team, Target target, long perms, Set<UUID> recipients) {
		if (!CapitolConfig.MAIL_ALLOW_EXTERNAL.get())
			return RoleManager.Check.deny("commands.capitol.mail.external_disabled");
		if (!Permission.SEND_MAIL_EXTERNAL.hasPermission(perms))
			return RoleManager.Check.deny("commands.capitol.mail.no_permission");
		Team targetTeam = db().getTeam(target.externalTeamId());
		if (targetTeam == null) return RoleManager.Check.deny("commands.capitol.mail.external_not_found");
		for (TeamMember member : db().getTeamMembers(targetTeam)) {
			if (Permission.RECEIVE_EXTERNAL_MAIL.hasPermission(db().getEffectivePermissions(member.playerUUID(), targetTeam)))
				recipients.add(member.playerUUID());
		}
		if (recipients.isEmpty()) return RoleManager.Check.deny("commands.capitol.mail.no_external_recipients");
		return RoleManager.Check.allow();
	}

	private static RoleManager.Check checkCooldown(Player sender, boolean external) {
		int seconds = external ? CapitolConfig.MAIL_EXTERNAL_COOLDOWN_SECONDS.get() : CapitolConfig.MAIL_SEND_COOLDOWN_SECONDS.get();
		Map<UUID, Long> map = external ? lastExternalSend : lastSend;
		Long last = map.get(sender.getUUID());
		if (last != null && System.currentTimeMillis() - last < seconds * 1000L)
			return RoleManager.Check.deny("commands.capitol.mail.cooldown", seconds);
		map.put(sender.getUUID(), System.currentTimeMillis());
		return RoleManager.Check.allow();
	}

	// trims, strips control characters, and caps the length
	private static String sanitize(String input, int max) {
		if (input == null) return "";
		String stripped = input.strip();
		StringBuilder builder = new StringBuilder(Math.min(stripped.length(), max));
		for (int i = 0; i < stripped.length() && builder.length() < max; i++) {
			char c = stripped.charAt(i);
			if (c == '\n' || c == '\t' || !Character.isISOControl(c)) builder.append(c);
		}
		return builder.toString();
	}
}
