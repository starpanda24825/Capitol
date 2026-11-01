package com.createcivilization.capitol.client.gui.screen;

import com.createcivilization.capitol.client.screen.CapitolBlockNamingScreen;
import com.createcivilization.capitol.common.CapitolClientHooks;
import net.minecraft.client.Minecraft;

// Swaps in the real capitol book opener. Only ever loaded on the client.
public final class CapitolBookMenuClient {

	public static void register() {
		CapitolClientHooks.openCapitolBook = payload ->
			Minecraft.getInstance().setScreen(new CapitolBookMenu(payload));
		CapitolClientHooks.openCapitolNaming = payload ->
			Minecraft.getInstance().setScreen(new CapitolBlockNamingScreen(payload));
		// the handler lands the snapshot in ClientBookState before this runs, so the screen reads it from there
		CapitolClientHooks.openManagerBook = payload ->
			Minecraft.getInstance().setScreen(new CapitolManagerMenu());
	}

	private CapitolBookMenuClient() {}
}