package com.createcivilization.capitol.common.networking.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRespondDisband(boolean approve) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<C2SRespondDisband> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("capitol", "c2s_respond_disband"));

	public static final StreamCodec<ByteBuf, C2SRespondDisband> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.BOOL,
		C2SRespondDisband::approve,
		C2SRespondDisband::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
