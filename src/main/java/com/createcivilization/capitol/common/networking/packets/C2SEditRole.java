package com.createcivilization.capitol.common.networking.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Edits a role's fields. The mask says which of name/color/flags are present:
 * bit0 = name, bit1 = color, bit2 = flags.
 */
public record C2SEditRole(int roleId, String name, int color, int flags, int mask) implements CustomPacketPayload {

	public static final int MASK_NAME = 1;
	public static final int MASK_COLOR = 1 << 1;
	public static final int MASK_FLAGS = 1 << 2;

	public static final CustomPacketPayload.Type<C2SEditRole> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("capitol", "c2s_edit_role"));

	public static final StreamCodec<ByteBuf, C2SEditRole> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT,
		C2SEditRole::roleId,
		ByteBufCodecs.STRING_UTF8,
		C2SEditRole::name,
		ByteBufCodecs.INT,
		C2SEditRole::color,
		ByteBufCodecs.INT,
		C2SEditRole::flags,
		ByteBufCodecs.INT,
		C2SEditRole::mask,
		C2SEditRole::new
	);

	public boolean hasName() { return (mask & MASK_NAME) != 0; }
	public boolean hasColor() { return (mask & MASK_COLOR) != 0; }
	public boolean hasFlags() { return (mask & MASK_FLAGS) != 0; }

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
