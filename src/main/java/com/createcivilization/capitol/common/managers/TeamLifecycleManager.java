package com.createcivilization.capitol.common.managers;

import com.createcivilization.capitol.common.config.CapitolConfig;
import com.createcivilization.capitol.common.data.ClaimedChunk;
import com.createcivilization.capitol.common.data.DisbandRequest;
import com.createcivilization.capitol.common.data.MemberRoles;
import com.createcivilization.capitol.common.data.Permission;
import com.createcivilization.capitol.common.data.SubClaim;
import com.createcivilization.capitol.common.data.Team;
import com.createcivilization.capitol.common.data.TeamMember;
import com.createcivilization.capitol.common.data.TeamRole;
import com.createcivilization.capitol.common.data.War;
import com.createcivilization.capitol.common.modules.database.CapitolDatabase;
import com.createcivilization.capitol.common.networking.packets.S2CChunkRemove;
import com.createcivilization.capitol.common.networking.packets.S2CSubClaimRemove;
import com.createcivilization.capitol.server.events.WarTakeoverEvents;
import com.createcivilization.capitol.server.invites.InviteHandler;
import com.createcivilization.capitol.server.networking.ServerPayloadHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Leadership transfer, leave and the disband flow. Every mutation is validated
 * against permissions/hierarchy here so commands and packets share one path.
 */
public final class TeamLifecycleManager {

	private static final long HOUR_MILLIS = 60L * 60L * 1000L;

	private TeamLifecycleManager() {}

	private static CapitolDatabase db() {
		return DatabaseManager.database;
	}

	private static TeamRole systemRole(Team team, String systemKey) {
		for (TeamRole role : db().getTeamRoles(team)) {
			if (systemKey.equals(role.systemKey())) return role;
		}
		return null;
	}

	// leadership transfer / leave

	public static RoleManager.Check transferLeadership(Player actor, Team team, UUID newLeader) {
		UUID currentLeader = db().getTeamLeader(team);
		if (currentLeader == null) return RoleManager.Check.deny("commands.capitol.team.transfer.no_owner");
		if (!RoleManager.isOp(actor) && !actor.getUUID().equals(currentLeader))
			return RoleManager.Check.deny("commands.capitol.team.transfer.no_permission");
		if (newLeader.equals(currentLeader))
			return RoleManager.Check.deny("commands.capitol.team.transfer.self");
		MemberRoles target = db().getMemberRoles(newLeader, team);
		if (target.base() == null) return RoleManager.Check.deny("commands.capitol.team.transfer.not_member");

		TeamRole ownerRole = systemRole(team, TeamRole.OWNER_SYSTEM_KEY);
		TeamRole memberRole = systemRole(team, TeamRole.MEMBER_SYSTEM_KEY);
		if (ownerRole == null || memberRole == null) return RoleManager.Check.deny("commands.capitol.team.transfer.no_owner");

		db().inTransaction(() -> {
			db().updatePlayerRole(currentLeader, team, memberRole);
			db().updatePlayerRole(newLeader, team, ownerRole);
		});

		syncCapitalMayor(team, newLeader);
		cancelPendingDisband(team);
		return RoleManager.Check.allow();
	}

	public static RoleManager.Check leaveTeam(Player player, Team team, @Nullable UUID newLeader) {
		UUID leader = db().getTeamLeader(team);
		boolean isOwner = player.getUUID().equals(leader);

		if (isOwner) {
			if (newLeader == null) return RoleManager.Check.deny("commands.capitol.team.leave.owner_transfer");
			if (newLeader.equals(player.getUUID())) return RoleManager.Check.deny("commands.capitol.team.transfer.self");
			MemberRoles target = db().getMemberRoles(newLeader, team);
			if (target.base() == null) return RoleManager.Check.deny("commands.capitol.team.transfer.not_member");

			TeamRole ownerRole = systemRole(team, TeamRole.OWNER_SYSTEM_KEY);
			if (ownerRole == null) return RoleManager.Check.deny("commands.capitol.team.transfer.no_owner");

			// promote + leave atomically, so there is never a moment with zero owners
			db().inTransaction(() -> {
				db().updatePlayerRole(newLeader, team, ownerRole);
				db().removePlayerFromTeam(player.getUUID(), team);
			});
			syncCapitalMayor(team, newLeader);
			cancelPendingDisband(team);
			onApproverSetChanged(team);
			return RoleManager.Check.allow();
		}

		db().removePlayerFromTeam(player.getUUID(), team);
		onApproverSetChanged(team);
		return RoleManager.Check.allow();
	}

	// disband

	public static RoleManager.Check canStartDisband(Player actor, Team team) {
		if (!RoleManager.isOp(actor)
			&& !Permission.DISBAND_TEAM.hasPermission(db().getEffectivePermissions(actor.getUUID(), team)))
			return RoleManager.Check.deny("commands.capitol.team.disband.no_permission");

		long lockUntil = db().getDisbandLockUntil(team);
		if (lockUntil > System.currentTimeMillis())
			return RoleManager.Check.deny("commands.capitol.team.disband.locked");

		DisbandRequest existing = db().getDisbandRequest(team);
		if (existing != null) {
			if (!isExpired(existing)) return RoleManager.Check.deny("commands.capitol.team.disband.pending_exists");
			cancelPendingDisband(team);
		}
		return RoleManager.Check.allow();
	}

	public static boolean hasApprovers(Team team, UUID requester) {
		return !getApprovers(team, requester).isEmpty();
	}

	public static Set<UUID> getApprovers(Team team, UUID requester) {
		Set<UUID> approvers = new HashSet<>();
		for (TeamMember member : db().getTeamMembers(team)) {
			if (member.playerUUID().equals(requester)) continue;
			if (Permission.APPROVE_DISBAND.hasPermission(db().getEffectivePermissions(member.playerUUID(), team)))
				approvers.add(member.playerUUID());
		}
		return approvers;
	}

	public static void startDisbandRequest(Player requester, Team team) {
		Set<UUID> approvers = getApprovers(team, requester.getUUID());
		db().upsertDisbandRequest(team, requester.getUUID(), System.currentTimeMillis());
		for (UUID approver : approvers) db().setDisbandApproval(team, approver, false);

		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if (server == null) return;
		for (UUID approver : approvers) {
			ServerPlayer online = server.getPlayerList().getPlayer(approver);
			if (online != null) {
				online.sendSystemMessage(Component.translatable("commands.capitol.team.disband.requested_notify",
					Component.literal(team.getName()).withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.YELLOW));
			}
		}
	}

	public static RoleManager.Check respondDisband(Player actor, Team team, boolean approve) {
		DisbandRequest request = db().getDisbandRequest(team);
		if (request == null || isExpired(request)) {
			if (request != null) cancelPendingDisband(team);
			return RoleManager.Check.deny("commands.capitol.team.disband.no_request");
		}
		if (actor.getUUID().equals(request.requestedBy()))
			return RoleManager.Check.deny("commands.capitol.team.disband.requester_cannot_approve");
		if (!getApprovers(team, request.requestedBy()).contains(actor.getUUID()))
			return RoleManager.Check.deny("commands.capitol.team.disband.not_approver");

		if (!approve) {
			cancelPendingDisband(team);
			return RoleManager.Check.deny("commands.capitol.team.disband.denied");
		}

		db().setDisbandApproval(team, actor.getUUID(), true);
		if (allApproved(team, request)) {
			finalizeDisband(team);
			return RoleManager.Check.allow();
		}
		return RoleManager.Check.allow();
	}

	public static RoleManager.Check cancelDisband(Player actor, Team team) {
		DisbandRequest request = db().getDisbandRequest(team);
		if (request == null) return RoleManager.Check.deny("commands.capitol.team.disband.no_request");
		if (!RoleManager.isOp(actor) && !actor.getUUID().equals(request.requestedBy()))
			return RoleManager.Check.deny("commands.capitol.team.disband.no_permission");
		cancelPendingDisband(team);
		return RoleManager.Check.allow();
	}

	// Any change to who holds APPROVE_DISBAND cancels a pending request and starts a lock.
	public static void onApproverSetChanged(Team team) {
		if (db().getDisbandRequest(team) == null) return;
		cancelPendingDisband(team);
		db().setDisbandLockUntil(team, System.currentTimeMillis() + CapitolConfig.DISBAND_PROTECTION_LOCK_HOURS.get() * HOUR_MILLIS);
	}

	// deletes the team and everything attached to it, broadcasting removals first
	public static void finalizeDisband(Team team) {
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		CapitolDatabase database = db();

		for (ClaimedChunk chunk : database.getTeamChunks(team)) {
			if (server == null) break;
			ServerLevel level = server.getLevel(dimensionKey(chunk.dimension()));
			if (level == null) continue;
			ChunkPos pos = new ChunkPos(chunk.chunkX(), chunk.chunkZ());
			PacketDistributor.sendToPlayersTrackingChunk(level, pos, new S2CChunkRemove(pos.toLong()));
		}

		for (ClaimedChunk chunk : database.getTeamForceloadedChunks(team)) {
			if (server == null) break;
			ServerLevel level = server.getLevel(dimensionKey(chunk.dimension()));
			if (level != null) level.setChunkForced(chunk.chunkX(), chunk.chunkZ(), false);
		}

		for (SubClaim subClaim : database.getTeamSubClaims(team)) {
			if (server == null) break;
			ServerLevel level = server.getLevel(dimensionKey(subClaim.dimension()));
			if (level == null) continue;
			ChunkPos centre = new ChunkPos(
				((subClaim.minX() + subClaim.maxX()) >> 1) >> 4,
				((subClaim.minZ() + subClaim.maxZ()) >> 1) >> 4
			);
			PacketDistributor.sendToPlayersTrackingChunk(level, centre, new S2CSubClaimRemove(subClaim.id()));
		}

		for (War war : database.getWarsForTeam(team)) WarTakeoverEvents.clearWarState(war);
		ServerPayloadHandler.broadcastWars();

		if (server != null) {
			for (TeamMember member : database.getTeamMembers(team)) {
				InviteHandler.clearInvites(member.playerUUID());
				ServerPlayer online = server.getPlayerList().getPlayer(member.playerUUID());
				if (online != null) {
					online.sendSystemMessage(Component.translatable("commands.capitol.team.disband.notify",
						Component.literal(team.getName()).withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.RED));
				}
			}
		}

		// db delete last, so the broadcasts above still see the team's data
		database.removeTeam(team);
	}

	//helpers

	private static void cancelPendingDisband(Team team) {
		db().deleteDisbandRequest(team);
		db().clearDisbandApprovals(team);
		db().setDisbandLockUntil(team, 0);
	}

	private static boolean isExpired(DisbandRequest request) {
		return System.currentTimeMillis() - request.requestedAt()
			> CapitolConfig.DISBAND_APPROVAL_HOURS.get() * HOUR_MILLIS;
	}

	private static boolean allApproved(Team team, DisbandRequest request) {
		Set<UUID> approvers = getApprovers(team, request.requestedBy());
		// if nobody holds APPROVE_DISBAND any more, don't auto-complete
		if (approvers.isEmpty()) return false;
		Map<UUID, Boolean> approvals = db().getDisbandApprovals(team);
		for (UUID approver : approvers) {
			if (!Boolean.TRUE.equals(approvals.get(approver))) return false;
		}
		return true;
	}

	private static void syncCapitalMayor(Team team, UUID newLeader) {
		Long capitalId = db().getCapitalBlockId(team);
		if (capitalId != null) db().setCapitolBlockMayor(capitalId, newLeader);
	}

	private static ResourceKey<Level> dimensionKey(String dimension) {
		return ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimension));
	}
}
