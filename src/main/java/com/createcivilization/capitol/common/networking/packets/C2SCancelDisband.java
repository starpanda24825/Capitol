package com.createcivilization.capitol.common.networking.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SCancelDisband() implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<C2SCancelDisband> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("capitol", "c2s_cancel_disband"));

	public static final StreamCodec<ByteBuf, C2SCancelDisband> STREAM_CODEC = StreamCodec.unit(new C2SCancelDisband());

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
