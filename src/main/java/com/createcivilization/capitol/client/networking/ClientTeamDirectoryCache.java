package com.createcivilization.capitol.client.networking;

import com.createcivilization.capitol.common.networking.packets.S2CTeamDirectory;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side mirror of the last team directory snapshot the server pushed, so the manager guis being rebuilt 
 * can read members/roles/invites without re-asking. Its here rather than in the GUI so the payload always has somewhere to be recieved.
 */
public final class ClientTeamDirectoryCache {

	@Nullable
	private static S2CTeamDirectory directory;

	private ClientTeamDirectoryCache() {}

	@Nullable
	public static S2CTeamDirectory directory() {
		return directory;
	}

	public static void set(S2CTeamDirectory payload) {
		directory = payload;
	}

	// Called on logout so a new session never shows a stale team.
	public static void clear() {
		directory = null;
	}
}
