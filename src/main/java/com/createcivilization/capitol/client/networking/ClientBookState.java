package com.createcivilization.capitol.client.networking;

import com.createcivilization.capitol.common.data.Team;
import com.createcivilization.capitol.common.networking.packets.S2COpenManagerBook;
import org.jetbrains.annotations.Nullable;

/**
 * The last manager snapshot the server sent, so the book's sections can tell what the
 * viewer is (and what they may do) without carrying it around. Lives with the other client
 * caches, and is cleared on logout with them.
 */
public final class ClientBookState {

	@Nullable
	private static Team team;
	private static long permissions = 0L;

	private ClientBookState() {}

	public static void apply(S2COpenManagerBook payload) {
		team = payload.team();
		permissions = payload.playerPermissions();
	}

	public static boolean hasTeam() {
		return team != null;
	}

	@Nullable
	public static Team team() {
		return team;
	}

	public static long permissions() {
		return permissions;
	}

	public static void clear() {
		team = null;
		permissions = 0L;
	}
}
