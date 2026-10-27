package com.createcivilization.capitol.common.networking.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Saves the editable team fields from the manager gui; server re-validates everything.
public record C2SEditTeam(String name, String tag, String description, int color) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<C2SEditTeam> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("capitol", "c2s_edit_team"));

	public static final StreamCodec<ByteBuf, C2SEditTeam> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8,
		C2SEditTeam::name,
		ByteBufCodecs.STRING_UTF8,
		C2SEditTeam::tag,
		ByteBufCodecs.STRING_UTF8,
		C2SEditTeam::description,
		ByteBufCodecs.INT,
		C2SEditTeam::color,
		C2SEditTeam::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
