package com.createcivilization.capitol.common.networking.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Opens one mail; the server answers with the body and marks it read.
public record C2SOpenMail(long id) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<C2SOpenMail> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("capitol", "c2s_open_mail"));

	public static final StreamCodec<ByteBuf, C2SOpenMail> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_LONG,
		C2SOpenMail::id,
		C2SOpenMail::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
