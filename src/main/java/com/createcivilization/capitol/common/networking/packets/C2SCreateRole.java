package com.createcivilization.capitol.common.networking.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SCreateRole(String name, int color, long permissions) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<C2SCreateRole> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("capitol", "c2s_create_role"));

	public static final StreamCodec<ByteBuf, C2SCreateRole> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8,
		C2SCreateRole::name,
		ByteBufCodecs.INT,
		C2SCreateRole::color,
		ByteBufCodecs.VAR_LONG,
		C2SCreateRole::permissions,
		C2SCreateRole::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
