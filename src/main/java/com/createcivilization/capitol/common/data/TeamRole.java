package com.createcivilization.capitol.common.data;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public record TeamRole(int id, UUID teamId, String name, long permissions,
					   int color, int position, String systemKey, int flags) {

	public static final String OWNER_ROLE_NAME = "owner";
	public static final String MEMBER_ROLE_NAME = "member";
	public static final String DEFAULT_ROLE_NAME = "default";
	public static final String MAYOR_ROLE_NAME = "mayor";

	public static final String OWNER_SYSTEM_KEY = "owner";
	public static final String MEMBER_SYSTEM_KEY = "member";
	public static final String DEFAULT_SYSTEM_KEY = "default";
	public static final String MAYOR_SYSTEM_KEY = "mayor";

	// bit 0: role can be addressed as a mail group
	public static final int FLAG_MAILABLE = 1;

	// system roles are identified by key, not name, so a rename can't grant or revoke rights
	public boolean isOwner() {
		return OWNER_SYSTEM_KEY.equals(systemKey);
	}

	public boolean isMemberRole() {
		return MEMBER_SYSTEM_KEY.equals(systemKey);
	}

	public boolean isDefaultRole() {
		return DEFAULT_SYSTEM_KEY.equals(systemKey);
	}

	public boolean isMayor() {
		return MAYOR_SYSTEM_KEY.equals(systemKey);
	}

	public boolean isSystem() {
		return systemKey != null;
	}

	public boolean isMailable() {
		return (flags & FLAG_MAILABLE) != 0;
	}

	public boolean hasPermission(Permission perm) {
		return perm.hasPermission(permissions);
	}

	public static TeamRole fromResultSet(ResultSet rs) throws SQLException {
		return new TeamRole(
			rs.getInt("id"),
			UUID.fromString(rs.getString("team_id")),
			rs.getString("name"),
			rs.getLong("permissions"),
			rs.getInt("color"),
			rs.getInt("position"),
			rs.getString("system_key"),
			rs.getInt("flags")
		);
	}

	public static long ownerPermissions() {
		return Permission.of(Permission.values());
	}

	public static long memberPermissions() {
		return Permission.of(
			Permission.BREAK_BLOCKS,
			Permission.PLACE_BLOCKS,
			Permission.INTERACT_BLOCKS,
			Permission.OPEN_CONTAINERS,
			Permission.CROP_TRAMPLE,
			Permission.FROST_WALKING,
			Permission.INTERACT_ENTITIES,
			Permission.ATTACK_PASSIVE,
			Permission.ATTACK_HOSTILE,
			Permission.ATTACK_PLAYER,
			Permission.USE_ITEMS,
			Permission.PICKUP_ITEMS,
			Permission.PICKUP_XP,
			Permission.DROP_ITEMS,
			Permission.MOB_LOOT,
			Permission.PLAYER_DEATH_LOOT,
			Permission.INTERACT_REDSTONE,
			Permission.USE_NETHER_PORTALS,
			Permission.CHORUS_FRUIT_TELEPORT,
			Permission.SEND_MAIL_MEMBERS
		);
	}

	public static long defaultPermissions() {
		return 0;
	}
}