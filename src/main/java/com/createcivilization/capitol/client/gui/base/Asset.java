package com.createcivilization.capitol.client.gui.base;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public class Asset {
	private final ResourceLocation texture;
	private final int textureWidth, textureHeight;

	public Asset(ResourceLocation texture, int textureWidth, int textureHeight) {
		this.texture = texture;
		this.textureWidth = textureWidth;
		this.textureHeight = textureHeight;
	}

	public Blit blit(int startX, int startY, int width, int height) {
		return new Blit(startX, startY, width, height);
	}

	public class Blit {
		private int startX, startY, blitWidth, blitHeight;
		//The size this slice is drawn at when it differs from the art it samples; 0 means "as cut".
		private int drawWidth, drawHeight;

		public Blit(int startX, int startY, int blitWidth, int blitHeight) {
			this.startX = startX;
			this.startY = startY;
			this.blitWidth = blitWidth;
			this.blitHeight = blitHeight;
		}

		// Draws the same slice wider, taller or both, for art that has to cover more room than it was cut for.
		public Blit stretchedTo(int width, int height) {
			this.drawWidth = width;
			this.drawHeight = height;
			return this;
		}

		public void renderBlit(GuiGraphics guiGraphics, int x, int y) {
			if (drawWidth > 0 || drawHeight > 0) renderBlit(guiGraphics, x, y, drawWidth, drawHeight);
			else guiGraphics.blit(texture, x, y, startX, startY, blitWidth, blitHeight, textureWidth, textureHeight);
		}

		// The same slice drawn at a different size, for art that has to grow rather than move.
		public void renderBlit(GuiGraphics guiGraphics, int x, int y, int width, int height) {
			guiGraphics.blit(texture, x, y, width, height, startX, startY, blitWidth, blitHeight, textureWidth, textureHeight);
		}

		public void setPosition(@Nullable Integer x, @Nullable Integer y) {
			startX = x == null ? startX : x;
			startY = y == null ? startY : y;
		}

		public void setSize(@Nullable Integer width, @Nullable Integer height) {
			blitWidth = width == null ? blitWidth : width;
			blitHeight = height == null ? blitHeight : height;
		}

		public int getBlitWidth() {
			return drawWidth > 0 ? drawWidth : blitWidth;
		}
		public int getBlitHeight() {
			return drawHeight > 0 ? drawHeight : blitHeight;
		}
	}
}
