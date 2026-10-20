package com.createcivilization.capitol.common.networking.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Asks for one page of a box; {@code box} is "inbox" or "sent".
public record C2SRequestInbox(String box, int page) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<C2SRequestInbox> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("capitol", "c2s_request_inbox"));

	public static final StreamCodec<ByteBuf, C2SRequestInbox> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8,
		C2SRequestInbox::box,
		ByteBufCodecs.VAR_INT,
		C2SRequestInbox::page,
		C2SRequestInbox::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
