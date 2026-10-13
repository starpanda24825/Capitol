package com.createcivilization.capitol.common.managers;

import com.createcivilization.capitol.common.config.CapitolConfig;
import com.createcivilization.capitol.common.data.MemberRoles;
import com.createcivilization.capitol.common.data.Permission;
import com.createcivilization.capitol.common.data.Team;
import com.createcivilization.capitol.common.data.TeamRole;
import com.createcivilization.capitol.common.modules.database.CapitolDatabase;
import net.minecraft.server.level.ServerPlayer;
import javax.annotation.Nullable;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;


// Single source of truth for role and member rule checks. 
// Commands and packet handlers both call in here so the rules never drift apart.

public final class RoleManager {

	// what to tell the player when a rule rejects an action
	public record Check(boolean ok, String langKey, Object[] args) {
		public static Check allow() {
			return new Check(true, null, new Object[0]);
		}

		public static Check deny(String langKey, Object... args) {
			return new Check(false, langKey, args);
		}
	}

	private RoleManager() {}

	private static CapitolDatabase db() {
		return DatabaseManager.database;
	}

	public static boolean isOp(Player actor) {
		return actor instanceof ServerPlayer serverPlayer && serverPlayer.hasPermissions(4);
	}

	// highest hierarchy position the actor holds; ops sit above everyone.
	public static int highestPosition(Player actor, Team team) {
		if (isOp(actor)) return Integer.MAX_VALUE;
		return db().getHighestPosition(actor.getUUID(), team);
	}

	public static long permissionsOf(Player actor, Team team) {
		return db().getEffectivePermissions(actor.getUUID(), team);
	}

	public static long sanitizeFor(TeamRole role, long requested) {
		if (role.isDefaultRole()) return RoleRules.sanitizeGuestPermissions(requested);
		if (role.isMayor()) return RoleRules.sanitizeMayorPermissions(requested);
		return requested;
	}

	// role management

	public static Check canCreateRole(Player actor, Team team) {
		if (!isOp(actor) && !Permission.MANAGE_ROLES.hasPermission(permissionsOf(actor, team)))
			return Check.deny("commands.capitol.team.role.create.no_permission");
		int max = CapitolConfig.MAX_ROLES_PER_TEAM.get();
		if (max > 0 && db().getTeamRoles(team).size() >= max)
			return Check.deny("commands.capitol.team.role.create.limit", max);
		return Check.allow();
	}

	public static Check canEditRole(Player actor, Team team, TeamRole role) {
		if (role == null) return Check.deny("commands.capitol.team.role.not_found");
		// owner role is untouchable for everyone, ops included
		if (role.isOwner()) return Check.deny("commands.capitol.team.role.edit.no_owner");
		if (isOp(actor)) return Check.allow();
		if (!Permission.MANAGE_ROLES.hasPermission(permissionsOf(actor, team)))
			return Check.deny("commands.capitol.team.role.no_manage_permission");
		if (!RoleRules.isStrictlyBelow(highestPosition(actor, team), role.position()))
			return Check.deny("commands.capitol.team.role.hierarchy.too_high");
		return Check.allow();
	}

	public static Check canSetPermissions(Player actor, Team team, TeamRole role, long requested) {
		Check edit = canEditRole(actor, team, role);
		if (!edit.ok()) return edit;
		long sanitized = sanitizeFor(role, requested);
		if (!isOp(actor) && !RoleRules.canGrant(permissionsOf(actor, team), sanitized))
			return Check.deny("commands.capitol.team.role.grant_missing");
		return Check.allow();
	}

	public static Check canDeleteRole(Player actor, Team team, TeamRole role) {
		if (role == null) return Check.deny("commands.capitol.team.role.not_found");
		if (role.isSystem()) return Check.deny("commands.capitol.team.role.delete.system");
		return canEditRole(actor, team, role);
	}

	public static Check canMoveRole(Player actor, Team team, TeamRole role) {
		return canEditRole(actor, team, role);
	}

	public static Check canAssignRole(Player actor, Team team, TeamRole role, UUID target) {
		if (role == null) return Check.deny("commands.capitol.team.role.not_found");
		if (role.isOwner()) return Check.deny("commands.capitol.team.role.assign.owner");
		if (role.isSystem()) return Check.deny("commands.capitol.team.role.assign.system");

		MemberRoles targetRoles = db().getMemberRoles(target, team);
		if (targetRoles.base() == null) return Check.deny("commands.capitol.team.member.not_found");
		if (targetRoles.isOwner()) return Check.deny("commands.capitol.team.role.owner_untouchable");
		for (TeamRole existing : targetRoles.extra()) {
			if (existing.id() == role.id()) return Check.deny("commands.capitol.team.role.assign.already");
		}
		if (isOp(actor)) return Check.allow();

		if (!Permission.ASSIGN_ROLES.hasPermission(permissionsOf(actor, team)))
			return Check.deny("commands.capitol.team.role.assign.no_permission");
		if (!RoleRules.isStrictlyBelow(highestPosition(actor, team), db().getHighestPosition(target, team)))
			return Check.deny("commands.capitol.team.role.hierarchy.too_high");
		// assigning a role grants its permissions, so you can't hand out more than you hold
		if (!RoleRules.canGrant(permissionsOf(actor, team), role.permissions()))
			return Check.deny("commands.capitol.team.role.grant_missing");
		int max = CapitolConfig.MAX_EXTRA_ROLES_PER_MEMBER.get();
		if (max > 0 && targetRoles.extra().size() >= max)
			return Check.deny("commands.capitol.team.role.assign.limit", max);
		return Check.allow();
	}

	public static Check canUnassignRole(Player actor, Team team, TeamRole role, UUID target) {
		if (role == null) return Check.deny("commands.capitol.team.role.not_found");
		if (role.isOwner()) return Check.deny("commands.capitol.team.role.assign.owner");
		if (role.isSystem()) return Check.deny("commands.capitol.team.role.assign.system");

		MemberRoles targetRoles = db().getMemberRoles(target, team);
		if (targetRoles.base() == null) return Check.deny("commands.capitol.team.member.not_found");
		if (targetRoles.isOwner()) return Check.deny("commands.capitol.team.role.owner_untouchable");
		if (isOp(actor)) return Check.allow();
		if (!Permission.ASSIGN_ROLES.hasPermission(permissionsOf(actor, team)))
			return Check.deny("commands.capitol.team.role.assign.no_permission");
		if (!RoleRules.isStrictlyBelow(highestPosition(actor, team), db().getHighestPosition(target, team)))
			return Check.deny("commands.capitol.team.role.hierarchy.too_high");
		return Check.allow();
	}

	public static Check canSetIndividualPermissions(Player actor, Team team, UUID target, long requested) {
		MemberRoles targetRoles = db().getMemberRoles(target, team);
		if (targetRoles.base() == null) return Check.deny("commands.capitol.team.member.not_found");
		if (targetRoles.isOwner()) return Check.deny("commands.capitol.team.role.owner_untouchable");
		if (isOp(actor)) return Check.allow();

		long actorPermissions = permissionsOf(actor, team);
		if (!Permission.MANAGE_ROLES.hasPermission(actorPermissions))
			return Check.deny("commands.capitol.team.player.no_permission");
		if (!RoleRules.isStrictlyBelow(highestPosition(actor, team), db().getHighestPosition(target, team)))
			return Check.deny("commands.capitol.team.role.hierarchy.too_high");
		if (!RoleRules.canGrant(actorPermissions, requested))
			return Check.deny("commands.capitol.team.role.grant_missing");
		return Check.allow();
	}

	public static Check canKick(Player actor, Team team, UUID target) {
		if (actor.getUUID().equals(target)) return Check.deny("commands.capitol.team.kick.self");
		MemberRoles targetRoles = db().getMemberRoles(target, team);
		if (targetRoles.base() == null) return Check.deny("commands.capitol.team.kick.not_member");
		if (targetRoles.isOwner()) return Check.deny("commands.capitol.team.kick.owner");
		if (isOp(actor)) return Check.allow();
		if (!Permission.KICK_MEMBERS.hasPermission(permissionsOf(actor, team)))
			return Check.deny("commands.capitol.team.kick.no_permission");
		if (!RoleRules.isStrictlyBelow(highestPosition(actor, team), db().getHighestPosition(target, team)))
			return Check.deny("commands.capitol.team.kick.hierarchy");
		return Check.allow();
	}

	// actions: validation + write, shared by commands and packet handlers

	public static Check createRole(Player actor, Team team, String name, int color, long permissions) {
		Check allowed = canCreateRole(actor, team);
		if (!allowed.ok()) return allowed;
		String trimmed = name == null ? "" : name.trim();
		if (!RoleRules.isValidRoleName(trimmed))
			return Check.deny(RoleRules.isReservedName(trimmed)
				? "commands.capitol.team.role.name.reserved" : "commands.capitol.team.role.name.invalid");
		if (db().roleNameExists(team, trimmed))
			return Check.deny("commands.capitol.team.role.name.exists");
		if (!isOp(actor) && !RoleRules.canGrant(permissionsOf(actor, team), permissions))
			return Check.deny("commands.capitol.team.role.grant_missing");

		// stack new custom roles above the existing ones
		int position = 2;
		for (TeamRole role : db().getTeamRoles(team)) {
			if (!role.isSystem() && role.position() >= position) position = role.position() + 1;
		}
		db().createRole(team, trimmed, color, permissions, position, null);
		return Check.allow();
	}


	// Edits a role's fields; pass null to leave a field unchanged.

	public static Check updateRole(Player actor, Team team, TeamRole role, @Nullable String name,
								   @Nullable Integer color, @Nullable Long permissions, @Nullable Integer flags) {
		Check allowed = canEditRole(actor, team, role);
		if (!allowed.ok()) return allowed;

		String newName = role.name();
		if (name != null) {
			String trimmed = name.trim();
			if (!RoleRules.isValidRoleName(trimmed))
				return Check.deny(RoleRules.isReservedName(trimmed)
					? "commands.capitol.team.role.name.reserved" : "commands.capitol.team.role.name.invalid");
			if (!trimmed.equalsIgnoreCase(role.name()) && db().roleNameExists(team, trimmed))
				return Check.deny("commands.capitol.team.role.name.exists");
			newName = trimmed;
		}

		long newPermissions = role.permissions();
		if (permissions != null) {
			Check permsAllowed = canSetPermissions(actor, team, role, permissions);
			if (!permsAllowed.ok()) return permsAllowed;
			newPermissions = sanitizeFor(role, permissions);
		}

		db().updateRole(role, newName, color != null ? color : role.color(), newPermissions,
			role.position(), flags != null ? flags : role.flags());
		return Check.allow();
	}

	public static Check setRolePermissions(Player actor, Team team, TeamRole role, long permissions) {
		Check allowed = canSetPermissions(actor, team, role, permissions);
		if (!allowed.ok()) return allowed;
		db().updateRole(role, role.name(), role.color(), sanitizeFor(role, permissions), role.position(), role.flags());
		return Check.allow();
	}

	public static Check deleteRole(Player actor, Team team, TeamRole role) {
		Check allowed = canDeleteRole(actor, team, role);
		if (!allowed.ok()) return allowed;
		db().deleteRole(team, role.id());
		return Check.allow();
	}

	public static Check moveRole(Player actor, Team team, TeamRole role, int delta) {
		Check allowed = canMoveRole(actor, team, role);
		if (!allowed.ok()) return allowed;
		db().moveRole(team, role.id(), delta);
		return Check.allow();
	}

	public static Check assignRole(Player actor, Team team, TeamRole role, UUID target) {
		Check allowed = canAssignRole(actor, team, role, target);
		if (!allowed.ok()) return allowed;
		db().addMemberRole(target, team, role.id());
		return Check.allow();
	}

	public static Check unassignRole(Player actor, Team team, TeamRole role, UUID target) {
		Check allowed = canUnassignRole(actor, team, role, target);
		if (!allowed.ok()) return allowed;
		db().removeMemberRole(target, team, role.id());
		return Check.allow();
	}

	public static Check setIndividualPermissions(Player actor, Team team, UUID target, long permissions) {
		Check allowed = canSetIndividualPermissions(actor, team, target, permissions);
		if (!allowed.ok()) return allowed;
		db().setIndividualPermissions(target, team, permissions);
		return Check.allow();
	}

	public static Check kick(Player actor, Team team, UUID target) {
		Check allowed = canKick(actor, team, target);
		if (!allowed.ok()) return allowed;
		db().removePlayerFromTeam(target, team);
		return Check.allow();
	}
}
