package com.createcivilization.capitol.common.networking.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Add a custom sub-tab: a name, one of five colours, and a three-tab cap.
public record C2SCreateSubTab(String name, int colour) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<C2SCreateSubTab> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("capitol", "c2s_create_sub_tab"));

	public static final StreamCodec<ByteBuf, C2SCreateSubTab> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8,
		C2SCreateSubTab::name,
		ByteBufCodecs.VAR_INT,
		C2SCreateSubTab::colour,
		C2SCreateSubTab::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
