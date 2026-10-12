package com.createcivilization.capitol.common.managers;

import com.createcivilization.capitol.common.data.Permission;
import com.createcivilization.capitol.common.data.TeamRole;

import java.util.Locale;
import java.util.Set;


//Pure, role rule semantics (names, hierarchy, permission masks) so the
//tricky parts can be reasoned about and unit-tested on their own.

public final class RoleRules {

	public static final int NAME_MIN_LENGTH = 1;
	public static final int NAME_MAX_LENGTH = 24;

	private static final Set<String> RESERVED_NAMES = Set.of(
		TeamRole.OWNER_ROLE_NAME,
		TeamRole.MEMBER_ROLE_NAME,
		TeamRole.DEFAULT_ROLE_NAME,
		TeamRole.MAYOR_ROLE_NAME
	);

	// administrative permissions a guest (default) role must never carry
	public static final long GUEST_FORBIDDEN_MASK = Permission.of(
		Permission.CLAIM_CHUNKS,
		Permission.UNCLAIM_CHUNKS,
		Permission.FORCELOAD_CHUNKS,
		Permission.INVITE_MEMBERS,
		Permission.KICK_MEMBERS,
		Permission.MANAGE_ROLES,
		Permission.ASSIGN_ROLES,
		Permission.MANAGE_TEAM,
		Permission.MANAGE_CAPITOL_BLOCKS,
		Permission.DECLARE_WAR,
		Permission.ASSIGN_MAYORS,
		Permission.CLAIM_SUB_CLAIMS,
		Permission.MANAGE_SUB_CLAIMS_OTHERS,
		Permission.VIEW_ALL_SUB_CLAIMS,
		Permission.SEND_MAIL_MEMBERS,
		Permission.SEND_MAIL_ROLES,
		Permission.SEND_MAIL_TEAM_WIDE,
		Permission.SEND_MAIL_EXTERNAL,
		Permission.RECEIVE_EXTERNAL_MAIL,
		Permission.DISBAND_TEAM,
		Permission.APPROVE_DISBAND
	);

	// the only permissions a scoped mayor role may grant inside its block's chunks
	public static final long MAYOR_ALLOWED_MASK = Permission.of(
		Permission.CLAIM_CHUNKS,
		Permission.UNCLAIM_CHUNKS,
		Permission.MANAGE_SUB_CLAIMS_OTHERS,
		Permission.VIEW_ALL_SUB_CLAIMS
	);

	private RoleRules() {}

	public static boolean isReservedName(String name) {
		return name != null && RESERVED_NAMES.contains(name.trim().toLowerCase(Locale.ROOT));
	}

	public static boolean isValidRoleName(String name) {
		if (name == null) return false;
		String trimmed = name.trim();
		if (trimmed.length() < NAME_MIN_LENGTH || trimmed.length() > NAME_MAX_LENGTH) return false;
		if (isReservedName(trimmed)) return false;
		for (int i = 0; i < trimmed.length(); i++) {
			char c = trimmed.charAt(i);
			if (!Character.isLetterOrDigit(c) && c != ' ' && c != '_' && c != '-') return false;
		}
		return true;
	}

	// a role/player is manageable only when strictly below the actor's highest position
	public static boolean isStrictlyBelow(int actorHighest, int targetPosition) {
		return targetPosition < actorHighest;
	}

	// true when every bit in `granted` is already held by the actor
	public static boolean canGrant(long actorPermissions, long granted) {
		return (granted & ~actorPermissions) == 0;
	}

	public static long sanitizeGuestPermissions(long requested) {
		return requested & ~GUEST_FORBIDDEN_MASK;
	}

	public static long sanitizeMayorPermissions(long requested) {
		return requested & MAYOR_ALLOWED_MASK;
	}
}
