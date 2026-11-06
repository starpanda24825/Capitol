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
import java.util.function.Predicate;

import static org.lwjgl.glfw.GLFW.*;

public abstract class TextInputInteractable implements Interactable.BlitInteractable {

	public static final int gray = Color.GRAY.getRGB();
	private final Asset.Blit idleBlit;
	private final Component placeHolderText;
	private StringBuilder value = new StringBuilder();
	private int x, y;
	private final int color;
	boolean dropShadow;
	SmartScreen smartScreen;
	List<String> autoFill;

	// insert position within the value; always in [0, value.length()]
	private int cursor = 0;
	private int maxLength = 64;
	@Nullable
	private Predicate<String> validator;
	private String closestTeamName = "";

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

	// Max characters the field accepts, it defaults to 64.
	public TextInputInteractable maxLength(int maxLength) {
		this.maxLength = Math.max(1, maxLength);
		return this;
	}

	// Optional extra check applied before a change is accepted
	public TextInputInteractable validate(@Nullable Predicate<String> validator) {
		this.validator = validator;
		return this;
	}

	@Override
	public void render(GuiGraphics guiGraphics) {
		BlitInteractable.super.render(guiGraphics);

		// getX()/getY(), not the fields: a field that positions itself still wants its
		// text where it draws, which is how the screen draws the box around it
		int textX = getX() + 3;
		int textY = getY() + Minecraft.getInstance().font.lineHeight / 2 - 2;
		String value = getValue();
		if (value.isEmpty() && !isActive()) {
			guiGraphics.drawString(Minecraft.getInstance().font, placeHolderText.getString(), textX, textY, gray, dropShadow);
			return;
		}

		if (autoFill != null) {
			updateClosest(value);
			guiGraphics.drawString(Minecraft.getInstance().font, closestTeamName, textX, textY, gray, dropShadow);
		}

		guiGraphics.drawString(Minecraft.getInstance().font, value, textX, textY, color, dropShadow);
		// blinking caret drawn at the cursor's pixel offset
		if (isActive() && (System.currentTimeMillis() / 500) % 2 == 0) {
			int caretX = textX + Minecraft.getInstance().font.width(value.substring(0, Math.min(cursor, value.length())));
			guiGraphics.drawString(Minecraft.getInstance().font, "_", caretX, textY, color, dropShadow);
		}
	}

	private void updateClosest(String value) {
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
		boolean ctrl = (modifiers & GLFW_MOD_CONTROL) != 0;

		if (ctrl) {
			switch (keyCode) {
				case GLFW_KEY_A -> { cursor = value.length(); return; }
				case GLFW_KEY_C -> {
					Minecraft.getInstance().keyboardHandler.setClipboard(getValue());
					return;
				}
				case GLFW_KEY_V -> {
					insert(Minecraft.getInstance().keyboardHandler.getClipboard());
					return;
				}
				default -> { }
			}
		}

		switch (keyCode) {
			case GLFW_KEY_BACKSPACE -> {
				if (cursor > 0) {
					String next = value.substring(0, cursor - 1) + value.substring(cursor);
					if (accept(next)) {
						value.deleteCharAt(cursor - 1);
						cursor--;
					}
				}
				return;
			}
			case GLFW_KEY_DELETE -> {
				if (cursor < value.length()) {
					String next = value.substring(0, cursor) + value.substring(cursor + 1);
					if (accept(next)) value.deleteCharAt(cursor);
				}
				return;
			}
			case GLFW_KEY_LEFT -> {
				if (cursor > 0) cursor--;
				return;
			}
			case GLFW_KEY_RIGHT -> {
				if (cursor < value.length()) cursor++;
				return;
			}
			case GLFW_KEY_HOME -> { cursor = 0; return; }
			case GLFW_KEY_END -> { cursor = value.length(); return; }
			case GLFW_KEY_ENTER -> {
				if (this.smartScreen != null) this.smartScreen.lastClick = null;
				return;
			}
			case GLFW_KEY_TAB -> {
				// autofill stays on tab only when a suggestion exists
				if (autoFill != null && !closestTeamName.isEmpty()) setValue(closestTeamName);
				return;
			}
			default -> { }
		}
	}

	@Override
	public void charTyped(char character, int modifiers) {
		// the character itself comes from vanilla's charTyped path; glfwGetKeyName wouldn't give
		// us spaces or symbols, so nothing here is appended from a key press
		if (isHidden() || !isActive()) return;
		if (value.length() >= MAX_LENGTH) return;
		if (!isActive()) return;
		if (character == '\n' || character == '\r' || character == '\t') return;
		if (Character.isISOControl(character)) return;
		insert(String.valueOf(character));
	}

	// inserts at the cursor, respecting the length cap and validator
	private void insert(String text) {
		if (text == null || text.isEmpty()) return;
		StringBuilder candidate = new StringBuilder(value);
		String cleaned = text.replaceAll("\\p{Cntrl}", "");
		if (cleaned.isEmpty()) return;
		int room = maxLength - value.length();
		if (room <= 0) return;
		if (cleaned.length() > room) cleaned = cleaned.substring(0, room);

		candidate.insert(cursor, cleaned);
		if (!accept(candidate.toString())) return;
		value = candidate;
		cursor += cleaned.length();
	}

	private boolean accept(String candidate) {
		if (candidate.length() > maxLength) return false;
		return validator == null || validator.test(candidate);
	}

	private boolean isActive() {
		if (this.smartScreen == null) return false;
		return this.smartScreen.lastClick == this;
	}

	public String getValue() {
		return value.toString();
	}

	public void setValue(String val) {
		this.value = new StringBuilder(val == null ? "" : val);
		if (this.value.length() > maxLength) this.value.setLength(maxLength);
		this.cursor = this.value.length();
	}

	public int getCursor() {
		return cursor;
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
