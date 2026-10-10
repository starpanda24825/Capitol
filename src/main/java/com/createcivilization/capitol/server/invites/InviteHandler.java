package com.createcivilization.capitol.server.invites;

import com.createcivilization.capitol.common.config.CapitolConfig;
import com.createcivilization.capitol.common.data.Team;
import com.createcivilization.capitol.common.managers.DatabaseManager;
import com.createcivilization.capitol.common.modules.database.CapitolDatabase;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InviteHandler {

	// keyed by the invitee's UUID: the Player object changes on relog, the UUID doesn't
	private static final Map<UUID, List<Invite>> invites = new ConcurrentHashMap<>();

	private record Invite(Team team, long sentAt) {}

	public static void addInvite(Player player, Team team){
		CapitolDatabase database = DatabaseManager.database;
		if(database.getPlayerTeam(player) != null) return;

		List<Invite> playerInvites = invites.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
		// a fresh invite from the same team replaces the old one instead of stacking
		playerInvites.removeIf(invite -> invite.team().getId().equals(team.getId()));
		playerInvites.add(new Invite(team, System.currentTimeMillis()));

		String acceptCmd = "/capitol invites " + team.getName() + " accept";
		String denyCmd   = "/capitol invites " + team.getName() + " deny";

		MutableComponent accept = Component.literal("[Accept]")
			.withStyle(s -> s.withColor(ChatFormatting.GREEN)
				.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, acceptCmd))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(acceptCmd))));

		MutableComponent deny = Component.literal("[Deny]")
			.withStyle(s -> s.withColor(ChatFormatting.RED)
				.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, denyCmd))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(denyCmd))));

		player.sendSystemMessage(Component.literal("You have been invited to join ").withStyle(ChatFormatting.YELLOW)
			.append(Component.literal(team.getName()).withStyle(ChatFormatting.GOLD))
			.append(Component.literal("! ").withStyle(ChatFormatting.YELLOW))
			.append(accept)
			.append(Component.literal(" ").withStyle(ChatFormatting.WHITE))
			.append(deny));
	}

	public static void removeInvite(Player player, Team team){
		removeInvite(player.getUUID(), team);
	}

	public static void removeInvite(UUID playerUUID, Team team){
		List<Invite> playerInvites = invites.get(playerUUID);
		if(playerInvites == null) return;
		playerInvites.removeIf(invite -> invite.team().getId().equals(team.getId()));
		if(playerInvites.isEmpty()) invites.remove(playerUUID);
	}

	public static void clearInvites(Player player){
		invites.remove(player.getUUID());
	}

	public static void clearInvites(UUID playerUUID){
		invites.remove(playerUUID);
	}

	public static List<Team> getInvites(Player player){
		return getInvites(player.getUUID());
	}

	public static List<Team> getInvites(UUID playerUUID){
		List<Invite> playerInvites = activeInvites(playerUUID);
		if (playerInvites.isEmpty()) return null;
		List<Team> teams = new ArrayList<>();
		for (Invite invite : playerInvites) teams.add(invite.team());
		return teams;
	}

	public static boolean hasValidInvite(UUID playerUUID, Team team){
		for (Invite invite : activeInvites(playerUUID)) {
			if (invite.team().getId().equals(team.getId())) return true;
		}
		return false;
	}

	// drops expired invites, so nothing needs a timer
	private static List<Invite> activeInvites(UUID playerUUID){
		List<Invite> playerInvites = invites.get(playerUUID);
		if (playerInvites == null) return List.of();
		long expiryMillis = CapitolConfig.INVITE_EXPIRY_SECONDS.get() * 1000L;
		playerInvites.removeIf(invite -> System.currentTimeMillis() - invite.sentAt() > expiryMillis);
		if (playerInvites.isEmpty()) invites.remove(playerUUID);
		return playerInvites;
	}

}
