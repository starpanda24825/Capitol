package com.createcivilization.capitol.common.data;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public enum Permission {
	// --- Administrative ---
	CLAIM_CHUNKS(1L, PermissionCategory.ADMINISTRATIVE),
	UNCLAIM_CHUNKS(1L << 1, PermissionCategory.ADMINISTRATIVE),
	FORCELOAD_CHUNKS(1L << 2, PermissionCategory.ADMINISTRATIVE),
	INVITE_MEMBERS(1L << 3, PermissionCategory.ADMINISTRATIVE),
	KICK_MEMBERS(1L << 4, PermissionCategory.ADMINISTRATIVE),
	MANAGE_ROLES(1L << 5, PermissionCategory.ADMINISTRATIVE),  // create/edit/delete roles
	ASSIGN_ROLES(1L << 6, PermissionCategory.ADMINISTRATIVE),  // change a member's roles
	MANAGE_TEAM(1L << 7, PermissionCategory.ADMINISTRATIVE),  // rename team, etc.
	MANAGE_CAPITOL_BLOCKS(1L << 27, PermissionCategory.ADMINISTRATIVE),  // place + upgrade extra (non-Capital) capitol blocks
	DECLARE_WAR(1L << 28, PermissionCategory.ADMINISTRATIVE),  // declare + end wars
	ASSIGN_MAYORS(1L << 39, PermissionCategory.ADMINISTRATIVE),  // assign/clear capitol block mayors

	// --- Block Protection ---
	BREAK_BLOCKS(1L << 8, PermissionCategory.BLOCK),
	PLACE_BLOCKS(1L << 9, PermissionCategory.BLOCK),
	INTERACT_BLOCKS(1L << 10, PermissionCategory.BLOCK), // doors, buttons, levers, etc.
	OPEN_CONTAINERS(1L << 11, PermissionCategory.BLOCK), // chests, barrels, hoppers, furnaces
	CROP_TRAMPLE(1L << 12, PermissionCategory.BLOCK),
	FROST_WALKING(1L << 13, PermissionCategory.BLOCK),

	// --- Entity Protection ---
	/// Interaction with all entities (including non-living) – villagers, item frames, armour stands, etc.
	INTERACT_ENTITIES(1L << 14, PermissionCategory.ENTITY),
	/// Whether passive entities can be attacked.
	ATTACK_PASSIVE(1L << 15, PermissionCategory.ENTITY),
	/// Whether hostile entities can be attacked.
	ATTACK_HOSTILE(1L << 16, PermissionCategory.ENTITY),
	/// Whether players can be attacked.
	ATTACK_PLAYER(1L << 17, PermissionCategory.ENTITY),

	// --- Item Protection ---
	/// Whether items can be used.
	USE_ITEMS(1L << 18, PermissionCategory.ITEM),
	/// Whether dropped items can be picked up.
	PICKUP_ITEMS(1L << 19, PermissionCategory.ITEM),
	/// Whether XP can be picked up.
	PICKUP_XP(1L << 20, PermissionCategory.ITEM),
	/// Whether items can be dropped.
	DROP_ITEMS(1L << 21, PermissionCategory.ITEM),
	/// Whether mobs drop loot on death.
	MOB_LOOT(1L << 22, PermissionCategory.ITEM),
	/// Whether items dropped by a player on death can be picked up.
	PLAYER_DEATH_LOOT(1L << 23, PermissionCategory.ITEM),

	// --- Redstone / Mechanical ---
	INTERACT_REDSTONE(1L << 24, PermissionCategory.REDSTONE), // pressure plates, tripwires, buttons, pistons, dispensers, etc.

	// --- Portal / Teleportation ---
	USE_NETHER_PORTALS(1L << 25, PermissionCategory.PORTAL),
	CHORUS_FRUIT_TELEPORT(1L << 26, PermissionCategory.PORTAL),

	// --- Sub-Claims ---
	CLAIM_SUB_CLAIMS(1L << 29, PermissionCategory.SUB_CLAIMS),  // use the sub-claim wand (was bit 27, which collided)
	MANAGE_SUB_CLAIMS_OTHERS(1L << 32, PermissionCategory.SUB_CLAIMS),  // transfer/delete/edit other members' sub-claims
	VIEW_ALL_SUB_CLAIMS(1L << 33, PermissionCategory.SUB_CLAIMS),  // see the full team sub-claim directory

	// --- Mail ---
	SEND_MAIL_MEMBERS(1L << 34, PermissionCategory.MAIL),
	SEND_MAIL_ROLES(1L << 35, PermissionCategory.MAIL),
	SEND_MAIL_TEAM_WIDE(1L << 36, PermissionCategory.MAIL),
	SEND_MAIL_EXTERNAL(1L << 37, PermissionCategory.MAIL),
	RECEIVE_EXTERNAL_MAIL(1L << 38, PermissionCategory.MAIL),

	// --- Disband ---
	DISBAND_TEAM(1L << 30, PermissionCategory.DISBAND),  // start a disband request
	APPROVE_DISBAND(1L << 31, PermissionCategory.DISBAND);  // holders must approve a disband

	private final long flag;
	private final PermissionCategory category;

	Permission(long flag, PermissionCategory category) {
		this.flag = flag;
		this.category = category;
	}

	public PermissionCategory category() {
		return category;
	}

	public static long of(Permission... permissions) {
		long bits = 0L;
		for (Permission permission : permissions) {
			bits = permission.add(bits);
		}
		return bits;
	}

	public long add(long bits) {
		return bits | this.flag;
	}

	public long remove(long bits) {
		return bits & ~this.flag;
	}

	public long toggle(long bits) {
		return bits ^ this.flag;
	}

	public boolean hasPermission(long bits) {
		return (bits & this.flag) != 0;
	}

	//All permissions grouped by category, in declaration order (for GUI sections).
	public static Map<PermissionCategory, List<Permission>> categories() {
		Map<PermissionCategory, List<Permission>> grouped = new LinkedHashMap<>();
		for (PermissionCategory category : PermissionCategory.values()) {
			grouped.put(category, new ArrayList<>());
		}
		for (Permission permission : values()) {
			grouped.get(permission.category).add(permission);
		}
		return grouped;
	}

	public static EnumSet<Permission> fromBits(long bits) {
		EnumSet<Permission> permissions = EnumSet.noneOf(Permission.class);
		for (Permission permission : values()) {
			if (permission.hasPermission(bits)) permissions.add(permission);
		}
		return permissions;
	}
}