package com.createcivilization.capitol.common.networking.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

// Owners must supply a new leader; for others hasLeader is false.

public record C2SLeaveTeam(boolean hasLeader, UUID newLeader) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<C2SLeaveTeam> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("capitol", "c2s_leave_team"));

	public static final StreamCodec<ByteBuf, C2SLeaveTeam> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.BOOL,
		C2SLeaveTeam::hasLeader,
		ByteBufCodecs.STRING_UTF8,
		payload -> payload.newLeader().toString(),
		(hasLeader, newLeader) -> new C2SLeaveTeam(hasLeader, UUID.fromString(newLeader))
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
