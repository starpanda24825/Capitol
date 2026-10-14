package com.createcivilization.capitol.common.networking.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;


public record C2SDeleteRole(int roleId) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<C2SDeleteRole> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("capitol", "c2s_delete_role"));

	public static final StreamCodec<ByteBuf, C2SDeleteRole> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT,
		C2SDeleteRole::roleId,
		C2SDeleteRole::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
