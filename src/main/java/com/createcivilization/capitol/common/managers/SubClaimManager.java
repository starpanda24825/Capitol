package com.createcivilization.capitol.common.managers;

import com.createcivilization.capitol.common.config.CapitolConfig;
import com.createcivilization.capitol.common.data.Permission;
import com.createcivilization.capitol.common.data.SubClaim;
import com.createcivilization.capitol.common.data.SubClaimProtection;
import com.createcivilization.capitol.common.data.SubClaimSummary;
import com.createcivilization.capitol.common.data.Team;
import com.createcivilization.capitol.common.modules.database.CapitolDatabase;
import com.createcivilization.capitol.common.networking.packets.S2CSubClaimRemove;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ownership and directory rules for sub-claims. Like RoleManager, both commands and packet handlers call in here so the checks never drift apart.
 * A sub-claim is manageable by its owner, the team owner, or a member holding MANAGE_SUB_CLAIMS_OTHERS; ops bypass everything.
 */
public final class SubClaimManager {

	public static final int PAGE_SIZE = 9;
	private static final int NAME_MAX_LENGTH = 32;

	// the full set of bits a sub-claim may store
	private static final long PERMISSION_MASK = Permission.of(Permission.values());
	private static final long PROTECTION_MASK = SubClaimProtection.configDefaults();

	private SubClaimManager() {}

	private static CapitolDatabase db() {
		return DatabaseManager.database;
	}

	// Whether {@code actor} may act on {@code subClaim} (transfer/rename/delete/toggle).
	public static RoleManager.Check canManage(Player actor, Team team, SubClaim subClaim) {
		if (subClaim == null) return RoleManager.Check.deny("commands.capitol.subclaim.not_found");
		if (!subClaim.teamId().equals(team.getId())) return RoleManager.Check.deny("commands.capitol.subclaim.not_in_team");
		if (RoleManager.isOp(actor)) return RoleManager.Check.allow();
		if (actor.getUUID().equals(subClaim.ownerUuid())) return RoleManager.Check.allow();
		if (actor.getUUID().equals(db().getTeamLeader(team))) return RoleManager.Check.allow();
		if (!Permission.MANAGE_SUB_CLAIMS_OTHERS.hasPermission(db().getEffectivePermissions(actor.getUUID(), team)))
			return RoleManager.Check.deny("commands.capitol.subclaim.not_owner");
		return RoleManager.Check.allow();
	}

	// Creation cap
	public static RoleManager.Check canCreate(Player actor, Team team) {
		if (!RoleManager.isOp(actor) && !Permission.CLAIM_SUB_CLAIMS.hasPermission(db().getEffectivePermissions(actor.getUUID(), team)))
			return RoleManager.Check.deny("commands.capitol.subclaim.no_permission");
		int max = CapitolConfig.MAX_SUB_CLAIMS_PER_PLAYER.get();
		if (max > 0 && db().countSubClaimsOwnedBy(actor.getUUID()) >= max)
			return RoleManager.Check.deny("commands.capitol.subclaim.limit", max);
		return RoleManager.Check.allow();
	}

	public static RoleManager.Check transfer(Player actor, Team team, SubClaim subClaim, UUID newOwner) {
		RoleManager.Check allowed = canManage(actor, team, subClaim);
		if (!allowed.ok()) return allowed;
		if (newOwner == null || !db().isPlayerInTeam(newOwner, team))
			return RoleManager.Check.deny("commands.capitol.team.member.not_found");
		if (newOwner.equals(subClaim.ownerUuid())) return RoleManager.Check.deny("commands.capitol.subclaim.transfer.same");
		int max = CapitolConfig.MAX_SUB_CLAIMS_PER_PLAYER.get();
		if (max > 0 && db().countSubClaimsOwnedBy(newOwner) >= max)
			return RoleManager.Check.deny("commands.capitol.subclaim.limit", max);

		db().transferSubClaim(subClaim.id(), newOwner);
		MailManager.sendSystemMail(newOwner,
			net.minecraft.network.chat.Component.translatable("commands.capitol.mail.system.subclaim.subject").getString(),
			net.minecraft.network.chat.Component.translatable("commands.capitol.mail.system.subclaim.body", subClaim.name()).getString());
		return RoleManager.Check.allow();
	}

	public static RoleManager.Check rename(Player actor, Team team, SubClaim subClaim, String newName) {
		RoleManager.Check allowed = canManage(actor, team, subClaim);
		if (!allowed.ok()) return allowed;

		String trimmed = newName == null ? "" : newName.trim();
		if (trimmed.isEmpty()) return RoleManager.Check.deny("commands.capitol.subclaim.name.empty");
		if (trimmed.length() > NAME_MAX_LENGTH) return RoleManager.Check.deny("commands.capitol.sub_claim.name_too_long", NAME_MAX_LENGTH);
		// names only have to stay unique per owner
		for (SubClaim owned : db().getSubClaimsOwnedBy(subClaim.ownerUuid())) {
			if (!owned.id().equals(subClaim.id()) && owned.name().equalsIgnoreCase(trimmed))
				return RoleManager.Check.deny("commands.capitol.sub_claim.name_exists", trimmed);
		}

		db().updateSubClaimName(subClaim.id(), trimmed);
		notifyOwner(subClaim.ownerUuid(), "commands.capitol.mail.system.subclaim.renamed", subClaim.name(), trimmed);
		return RoleManager.Check.allow();
	}

	public static RoleManager.Check delete(Player actor, Team team, SubClaim subClaim) {
		RoleManager.Check allowed = canManage(actor, team, subClaim);
		if (!allowed.ok()) return allowed;
		db().removeSubClaim(subClaim.id());
		PacketDistributor.sendToAllPlayers(new S2CSubClaimRemove(subClaim.id()));
		notifyOwner(subClaim.ownerUuid(), "commands.capitol.mail.system.subclaim.deleted", subClaim.name());
		return RoleManager.Check.allow();
	}

	public static RoleManager.Check setPermissions(Player actor, Team team, SubClaim subClaim, long permissions) {
		RoleManager.Check allowed = canManage(actor, team, subClaim);
		if (!allowed.ok()) return allowed;
		// only real permission bits may be stored, and the owner's own bits stay intact
		db().setSubClaimPermissions(subClaim.id(), permissions & PERMISSION_MASK);
		return RoleManager.Check.allow();
	}

	public static RoleManager.Check setProtections(Player actor, Team team, SubClaim subClaim, long protections) {
		RoleManager.Check allowed = canManage(actor, team, subClaim);
		if (!allowed.ok()) return allowed;
		db().setSubClaimProtections(subClaim.id(), protections & PROTECTION_MASK);
		return RoleManager.Check.allow();
	}

	// One page of the directory. {@code all} only takes effect with {@code VIEW_ALL_SUB_CLAIMS} (or as an op/team owner); otherwise the viewer is scoped to their own sub-claims.
	// MAY CHANGE THIS LATER

	public static List<SubClaimSummary> buildPage(Player viewer, Team team, int page, boolean all,
												  @Nullable UUID ownerFilter, @Nullable String query) {
		UUID scopedOwner = ownerFilter;
		if (all) {
			if (!canViewAll(viewer, team)) scopedOwner = viewer.getUUID();
		} else {
			scopedOwner = viewer.getUUID();
		}

		List<SubClaimSummary> rows = new ArrayList<>();
		for (SubClaim subClaim : db().getTeamSubClaimsPage(team, Math.max(0, page), PAGE_SIZE, scopedOwner, query)) {
			rows.add(SubClaimSummary.of(subClaim, playerName(subClaim.ownerUuid())));
		}
		return rows;
	}

	public static int count(Player viewer, Team team, boolean all, @Nullable UUID ownerFilter, @Nullable String query) {
		UUID scopedOwner;
		if (all) scopedOwner = canViewAll(viewer, team) ? ownerFilter : viewer.getUUID();
		else scopedOwner = viewer.getUUID();
		return db().countTeamSubClaims(team, scopedOwner, query);
	}

	public static boolean canViewAll(Player viewer, Team team) {
		if (RoleManager.isOp(viewer)) return true;
		if (viewer.getUUID().equals(db().getTeamLeader(team))) return true;
		return Permission.VIEW_ALL_SUB_CLAIMS.hasPermission(db().getEffectivePermissions(viewer.getUUID(), team));
	}

	@Nullable
	private static String playerName(@Nullable UUID uuid) {
		if (uuid == null) return null;
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if (server == null) return null;
		ServerPlayer online = server.getPlayerList().getPlayer(uuid);
		if (online != null) return online.getName().getString();
		return server.getProfileCache().get(uuid).map(GameProfile::getName).orElse(null);
	}

	private static void notifyOwner(@Nullable UUID owner, String key, Object... args) {
		if (owner == null) return;
		MailManager.sendSystemMail(owner,
			net.minecraft.network.chat.Component.translatable("commands.capitol.mail.system.subclaim.subject").getString(),
			net.minecraft.network.chat.Component.translatable(key, args).getString());
	}
}
