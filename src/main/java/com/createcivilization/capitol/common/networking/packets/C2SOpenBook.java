package com.createcivilization.capitol.common.networking.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Asks the server to open the per-player manager book (the keybind sends this)
public record C2SOpenBook() implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<C2SOpenBook> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("capitol", "c2s_open_book"));

	public static final StreamCodec<ByteBuf, C2SOpenBook> STREAM_CODEC = StreamCodec.unit(new C2SOpenBook());

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
