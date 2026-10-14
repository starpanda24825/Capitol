package com.createcivilization.capitol.common.networking.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record C2SAssignRole(UUID target, int roleId, boolean add) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<C2SAssignRole> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("capitol", "c2s_assign_role"));

	public static final StreamCodec<ByteBuf, C2SAssignRole> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8,
		payload -> payload.target().toString(),
		ByteBufCodecs.VAR_INT,
		C2SAssignRole::roleId,
		ByteBufCodecs.BOOL,
		C2SAssignRole::add,
		(target, roleId, add) -> new C2SAssignRole(UUID.fromString(target), roleId, add)
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
