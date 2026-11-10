package com.createcivilization.capitol.common.managers;

import com.createcivilization.capitol.common.data.MemberRoles;
import com.createcivilization.capitol.common.data.Permission;
import com.createcivilization.capitol.common.data.Team;
import com.createcivilization.capitol.common.data.TeamMember;
import com.createcivilization.capitol.common.data.TeamRole;
import com.createcivilization.capitol.common.modules.database.CapitolDatabase;
import com.createcivilization.capitol.common.networking.packets.S2CTeamDirectory;
import com.createcivilization.capitol.server.invites.InviteHandler;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the read-only team snapshot the Members and Roles tabs read from. Only
 * display data lives here: the server re-checks every rule when the client sends
 * an action back, so a stale snapshot can never grant anything.
 */
public final class TeamDirectoryManager {

	private TeamDirectoryManager() {}

	private static CapitolDatabase db() {
		return DatabaseManager.database;
	}

	public static void send(UUID playerUuid) {
		ServerPlayer player = online(playerUuid);
		if (player == null) return;
		Team team = db().getPlayerTeam(player);
		if (team == null) return;
		PacketDistributor.sendToPlayer(player, build(player, team));
	}

	public static void broadcast(Team team) {
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if (server == null) return;
		for (TeamMember member : db().getTeamMembers(team)) {
			ServerPlayer player = server.getPlayerList().getPlayer(member.playerUUID());
			if (player != null) PacketDistributor.sendToPlayer(player, build(player, team));
		}
	}

	public static S2CTeamDirectory build(Player viewer, Team team) {
		long permissions = db().getEffectivePermissions(viewer.getUUID(), team);
		boolean op = RoleManager.isOp(viewer);

		List<S2CTeamDirectory.MemberRow> members = new ArrayList<>();
		for (TeamMember member : db().getTeamMembers(team)) {
			members.add(memberRow(member.playerUUID(), team));
		}

		Map<Integer, Integer> roleCounts = db().countRoleMembers(team);
		List<S2CTeamDirectory.RoleRow> roles = new ArrayList<>();
		for (TeamRole role : db().getTeamRoles(team)) {
			roles.add(new S2CTeamDirectory.RoleRow(role.id(), role.name(), role.color(), role.position(),
				role.systemKey(), role.isMailable(), role.permissions(), roleCounts.getOrDefault(role.id(), 0)));
		}

		List<S2CTeamDirectory.InviteRow> invites = new ArrayList<>();
		for (UUID invitee : InviteHandler.getInviteesForTeam(team)) {
			invites.add(new S2CTeamDirectory.InviteRow(invitee, nameOr(invitee, "?")));
		}

		return new S2CTeamDirectory(members, roles, invites, db().getTeamSubTabs(team),
			op || Permission.KICK_MEMBERS.hasPermission(permissions),
			op || Permission.ASSIGN_ROLES.hasPermission(permissions),
			op || Permission.MANAGE_ROLES.hasPermission(permissions),
			op || Permission.INVITE_MEMBERS.hasPermission(permissions),
			RoleManager.highestPosition(viewer, team), permissions);
	}

	private static S2CTeamDirectory.MemberRow memberRow(UUID uuid, Team team) {
		MemberRoles held = db().getMemberRoles(uuid, team);
		List<S2CTeamDirectory.RoleChip> chips = new ArrayList<>();
		for (TeamRole role : held.all()) chips.add(chip(role));

		Long individual = db().getIndividualPermissions(uuid, team);
		boolean online = online(uuid) != null;
		return new S2CTeamDirectory.MemberRow(uuid, nameOr(uuid, "?"), held.isOwner(), online,
			individual == null ? 0L : individual, chips);
	}

	private static S2CTeamDirectory.RoleChip chip(TeamRole role) {
		return new S2CTeamDirectory.RoleChip(role.id(), role.name(), role.color(), role.position(), role.isSystem());
	}

	@Nullable
	private static ServerPlayer online(UUID uuid) {
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if (server == null) return null;
		return server.getPlayerList().getPlayer(uuid);
	}

	private static String nameOr(UUID uuid, String fallback) {
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if (server == null) return fallback;
		ServerPlayer online = server.getPlayerList().getPlayer(uuid);
		if (online != null) return online.getName().getString();
		return server.getProfileCache().get(uuid).map(GameProfile::getName).orElse(fallback);
	}
}
