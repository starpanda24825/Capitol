package com.createcivilization.capitol.server.networking;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers the capitol block a player just placed and is still naming, so the
 * naming packet can be checked against something the server actually saw placed
 * instead of trusting the client's position.
 */
public final class PendingCapitolBlock {

	// long enough to name a block, short enough that a stale entry can't be reused later
	private static final long EXPIRY_MILLIS = 5 * 60 * 1000L;

	private static final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

	private PendingCapitolBlock() {}

	private record Pending(BlockPos pos, String dimension, long expiresAt) {}

	public static void mark(UUID player, BlockPos pos, String dimension) {
		pending.put(player, new Pending(pos.immutable(), dimension, System.currentTimeMillis() + EXPIRY_MILLIS));
	}

	// does this player have an unexpired pending block at exactly this spot?
	public static boolean matches(UUID player, BlockPos pos, String dimension) {
		Pending entry = pending.get(player);
		if (entry == null) return false;
		if (System.currentTimeMillis() > entry.expiresAt()) {
			pending.remove(player);
			return false;
		}
		return entry.pos().equals(pos) && entry.dimension().equals(dimension);
	}

	public static void clear(UUID player) {
		pending.remove(player);
	}

	@Nullable
	public static BlockPos pendingPos(UUID player) {
		Pending entry = pending.get(player);
		return entry == null ? null : entry.pos();
	}
}
