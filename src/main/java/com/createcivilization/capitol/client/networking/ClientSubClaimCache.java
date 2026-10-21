package com.createcivilization.capitol.client.networking;

import com.createcivilization.capitol.common.networking.packets.S2CSubClaimsPage;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClientSubClaimCache {

	// keyed by subclaim UUID
	public static final Map<UUID, SubClaimRenderData> subclaims = new ConcurrentHashMap<>();

	// the last directory page the server sent, for the book's Claims tab
	@Nullable
	private static S2CSubClaimsPage lastPage;

	@Nullable
	public static S2CSubClaimsPage lastPage() {
		return lastPage;
	}

	public static void setPage(S2CSubClaimsPage page) {
		lastPage = page;
	}

	public static void add(UUID id, SubClaimRenderData data) {
		subclaims.put(id, data);
	}

	public static void remove(UUID id) {
		subclaims.remove(id);
	}

	public static void clear() {
		subclaims.clear();
		lastPage = null;
	}
}