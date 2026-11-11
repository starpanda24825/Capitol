package com.createcivilization.capitol.common.networking.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Asks the server to rename one of the custom sub-tabs. The server re-validates the name and colou
public record C2SRenameSubTab(int id, String name, int colour) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<C2SRenameSubTab> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("capitol", "c2s_rename_sub_tab"));

	public static final StreamCodec<ByteBuf, C2SRenameSubTab> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT,
		C2SRenameSubTab::id,
		ByteBufCodecs.STRING_UTF8,
		C2SRenameSubTab::name,
		ByteBufCodecs.VAR_INT,
		C2SRenameSubTab::colour,
		C2SRenameSubTab::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
