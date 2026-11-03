package com.createcivilization.capitol.server.networking;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/*
 * A tiny per-player rate limit for the book's read requests. The GUI only asks for
 * data when a tab opens or invalidates.
 */
public final class RequestThrottle {

	// per player, per rolling second
	private static final int MAX_PER_SECOND = 5;

	private static final class Window {
		long start = System.currentTimeMillis();
		int count;
	}

	private static final Map<UUID, Window> windows = new ConcurrentHashMap<>();

	private RequestThrottle() {}

	public static boolean allow(UUID player) {
		long now = System.currentTimeMillis();
		Window window = windows.computeIfAbsent(player, key -> new Window());
		synchronized (window) {
			if (now - window.start >= 1000L) {
				window.start = now;
				window.count = 0;
			}
			return ++window.count <= MAX_PER_SECOND;
		}
	}

	public static void clear(UUID player) {
		windows.remove(player);
	}
}
