package com.createcivilization.capitol.common.networking.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SSetRolePermissions(int roleId, long permissions) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<C2SSetRolePermissions> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("capitol", "c2s_set_role_permissions"));

	public static final StreamCodec<ByteBuf, C2SSetRolePermissions> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT,
		C2SSetRolePermissions::roleId,
		ByteBufCodecs.VAR_LONG,
		C2SSetRolePermissions::permissions,
		C2SSetRolePermissions::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
