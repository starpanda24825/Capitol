package com.createcivilization.capitol.client.gui.interactables;

import com.createcivilization.capitol.client.gui.base.Asset;
import com.createcivilization.capitol.client.gui.base.Interactable;
import com.createcivilization.capitol.client.gui.base.SmartScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

public abstract class TextInputInteractable implements Interactable.BlitInteractable {

	public static final int gray = Color.GRAY.getRGB();
	private static final int MAX_LENGTH = 32;
	private final Asset.Blit idleBlit;
	private final Component placeHolderText;
	private StringBuilder value = new StringBuilder();
	private int x, y;
	private final int color;
	boolean dropShadow;
	SmartScreen smartScreen;
	List<String> autoFill;

	public TextInputInteractable(Asset.Blit idleBlit, @Nullable Component placeHolderText, int x, int y, @Nullable List<String> autoFill, @Nullable Integer color, boolean dropShadow) {
		this.idleBlit = idleBlit;
		this.placeHolderText = placeHolderText == null ? Component.empty() : placeHolderText;
		this.color = color == null ? Color.WHITE.getRGB() : color;
		this.dropShadow = dropShadow;
		this.x = x;
		this.y = y;
		this.autoFill = autoFill;
	}

	private boolean isHidden = false;
	private String closestTeamName = "";

	@Override
	public void render(GuiGraphics guiGraphics) {
		BlitInteractable.super.render(guiGraphics);

		String value = getValue();
		if (value.isEmpty() && !isActive()) guiGraphics.drawString(Minecraft.getInstance().font, placeHolderText.getString(), this.x + 3, this.y + Minecraft.getInstance().font.lineHeight / 2 - 2, gray, dropShadow);
		else {
			// autofill
			if (autoFill != null) {
				getClosestTeamName(value);
				guiGraphics.drawString(Minecraft.getInstance().font, closestTeamName, this.x + 3, this.y + Minecraft.getInstance().font.lineHeight / 2 - 2, gray, dropShadow);
			}
			guiGraphics.drawString(Minecraft.getInstance().font, value + (isActive() && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : ""), this.x + 3, this.y + Minecraft.getInstance().font.lineHeight / 2 - 2, color, dropShadow);
		}
	}

	private void getClosestTeamName(String value) {
		for (String string : autoFill) {
			if (string.toLowerCase().startsWith(value.toLowerCase())) {
				closestTeamName = string;
				return;
			}
		}
		closestTeamName = "";
	}

	@Override
	public void init(SmartScreen smartScreen) {
		this.smartScreen = smartScreen;
	}

	@Override
	public void input(int keyCode, int scanCode, int modifiers) {
		if (!isActive()) return;

		if (keyCode == GLFW_KEY_BACKSPACE && !value.isEmpty()) {
			value.deleteCharAt(value.length() - 1);
			return;
		}
		if (keyCode == GLFW_KEY_ENTER) {
			this.smartScreen.lastClick = null;
			return;
		}
		if (keyCode == GLFW_KEY_TAB || keyCode == GLFW_KEY_RIGHT) {
			setValue(closestTeamName);
		}
	}

	@Override
	public void charTyped(char character, int modifiers) {
		// the character itself comes from vanilla's charTyped path; glfwGetKeyName wouldn't give
		// us spaces or symbols, so nothing here is appended from a key press
		if (isHidden() || !isActive()) return;
		if (value.length() >= MAX_LENGTH) return;
		if (Character.isISOControl(character)) return;
		value.append(character);
	}

	private boolean isActive() {
		if (this.smartScreen == null) return false;
		return this.smartScreen.lastClick == this;
	}

	public String getValue() {
		return value.toString();
	}

	public void setValue(String val) {
		this.value = new StringBuilder(val);
	}

	@Override
	public boolean isHidden() {
		return isHidden;
	}

	@Override
	public void show() {
		isHidden = false;
	}

	@Override
	public void hide() {
		isHidden = true;
	}

	@Override
	public Asset.Blit getBlit() {
		return idleBlit;
	}

	@Override
	public int getX() {
		return x;
	}

	@Override
	public int getY() {
		return y;
	}

	@Override
	public void setX(int x) {
		this.x = x;
	}

	@Override
	public void setY(int y) {
		this.y = y;
	}
}
