package com.createcivilization.capitol.client.networking;

import com.createcivilization.capitol.common.networking.packets.S2CInboxPage;
import com.createcivilization.capitol.common.networking.packets.S2CMailBody;
import org.jetbrains.annotations.Nullable;

/*
 * Client-side mirror of what the server last told us about mail, so the mmanager GUI
 * can render an unread badge without re-asking on every screen open.
 */
public final class ClientMailCache {

	private static int unread = 0;
	@Nullable
	private static S2CInboxPage lastPage;
	@Nullable
	private static S2CMailBody openMail;

	private ClientMailCache() {}

	public static int unread() {
		return unread;
	}

	public static void setUnread(int count) {
		unread = Math.max(0, count);
	}

	@Nullable
	public static S2CInboxPage lastPage() {
		return lastPage;
	}

	@Nullable
	public static S2CMailBody openMail() {
		return openMail;
	}

	public static void setPage(S2CInboxPage page) {
		lastPage = page;
	}

	public static void setOpenMail(S2CMailBody mail) {
		openMail = mail;
	}

	/** Called on logout so a new session never shows stale mail. */
	public static void clear() {
		unread = 0;
		lastPage = null;
		openMail = null;
	}
}
