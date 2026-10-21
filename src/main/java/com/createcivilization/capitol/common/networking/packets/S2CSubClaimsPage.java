package com.createcivilization.capitol.common.networking.packets;

import com.createcivilization.capitol.common.data.SubClaimSummary;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record S2CSubClaimsPage(int page, int total, boolean all, List<SubClaimSummary> subClaims) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<S2CSubClaimsPage> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("capitol", "s2c_sub_claims_page"));

	public static final StreamCodec<ByteBuf, S2CSubClaimsPage> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public S2CSubClaimsPage decode(ByteBuf buffer) {
			int page = ByteBufCodecs.VAR_INT.decode(buffer);
			int total = ByteBufCodecs.VAR_INT.decode(buffer);
			boolean all = ByteBufCodecs.BOOL.decode(buffer);
			int size = ByteBufCodecs.VAR_INT.decode(buffer);
			List<SubClaimSummary> rows = new ArrayList<>(size);
			for (int i = 0; i < size; i++) rows.add(SubClaimSummary.STREAM_CODEC.decode(buffer));
			return new S2CSubClaimsPage(page, total, all, rows);
		}

		@Override
		public void encode(ByteBuf buffer, S2CSubClaimsPage value) {
			ByteBufCodecs.VAR_INT.encode(buffer, value.page());
			ByteBufCodecs.VAR_INT.encode(buffer, value.total());
			ByteBufCodecs.BOOL.encode(buffer, value.all());
			ByteBufCodecs.VAR_INT.encode(buffer, value.subClaims().size());
			for (SubClaimSummary row : value.subClaims()) SubClaimSummary.STREAM_CODEC.encode(buffer, row);
		}
	};

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
