package com.createcivilization.capitol.common.networking.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SMoveRole(int roleId, int delta) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<C2SMoveRole> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("capitol", "c2s_move_role"));

	public static final StreamCodec<ByteBuf, C2SMoveRole> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT,
		C2SMoveRole::roleId,
		ByteBufCodecs.INT,
		C2SMoveRole::delta,
		C2SMoveRole::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
