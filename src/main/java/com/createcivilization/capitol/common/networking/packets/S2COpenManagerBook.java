package com.createcivilization.capitol.common.networking.packets;

import com.createcivilization.capitol.common.data.Team;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Opens the manager, which unlike the capitol-block book is not tied to a block:
 * it only carries the viewer's team (if any) and their effective permissions, so the
 * client can tell which sections apply to them.
 */
public record S2COpenManagerBook(@Nullable Team team, long playerPermissions) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<S2COpenManagerBook> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("capitol", "s2c_open_manager_book"));

	// the team is only on the wire when there is one, so i hand-rolled the codec instead of composite()
	public static final StreamCodec<ByteBuf, S2COpenManagerBook> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public S2COpenManagerBook decode(ByteBuf buffer) {
			boolean hasTeam = ByteBufCodecs.BOOL.decode(buffer);
			Team team = hasTeam ? Team.STREAM_CODEC.decode(buffer) : null;
			long permissions = ByteBufCodecs.VAR_LONG.decode(buffer);
			return new S2COpenManagerBook(team, permissions);
		}

		@Override
		public void encode(ByteBuf buffer, S2COpenManagerBook value) {
			ByteBufCodecs.BOOL.encode(buffer, value.team() != null);
			if (value.team() != null) Team.STREAM_CODEC.encode(buffer, value.team());
			ByteBufCodecs.VAR_LONG.encode(buffer, value.playerPermissions());
		}
	};

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
