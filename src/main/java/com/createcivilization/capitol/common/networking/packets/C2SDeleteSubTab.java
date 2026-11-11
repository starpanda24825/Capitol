package com.createcivilization.capitol.common.networking.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Asks the server to delete one of the custom sub-tabs.
public record C2SDeleteSubTab(int id) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<C2SDeleteSubTab> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("capitol", "c2s_delete_sub_tab"));

	public static final StreamCodec<ByteBuf, C2SDeleteSubTab> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT,
		C2SDeleteSubTab::id,
		C2SDeleteSubTab::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
