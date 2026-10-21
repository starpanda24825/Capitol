package com.createcivilization.capitol.common.data;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// One row of the sub-claim directory, with the owner name already resolved.
public record SubClaimSummary(UUID id, String name, @Nullable UUID ownerUuid, @Nullable String ownerName,
							  String dimension, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
							  long permissions, long protections) {

	public static SubClaimSummary of(SubClaim subClaim, @Nullable String ownerName) {
		return new SubClaimSummary(subClaim.id(), subClaim.name(), subClaim.ownerUuid(), ownerName,
			subClaim.dimension(),
			subClaim.minX(), subClaim.minY(), subClaim.minZ(),
			subClaim.maxX(), subClaim.maxY(), subClaim.maxZ(),
			subClaim.permissions(), subClaim.protections());
	}

	public static final StreamCodec<ByteBuf, SubClaimSummary> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public SubClaimSummary decode(ByteBuf buffer) {
			UUID id = UUID.fromString(ByteBufCodecs.STRING_UTF8.decode(buffer));
			String name = ByteBufCodecs.STRING_UTF8.decode(buffer);
			boolean hasOwner = ByteBufCodecs.BOOL.decode(buffer);
			UUID ownerUuid = hasOwner ? UUID.fromString(ByteBufCodecs.STRING_UTF8.decode(buffer)) : null;
			boolean hasOwnerName = ByteBufCodecs.BOOL.decode(buffer);
			String ownerName = hasOwnerName ? ByteBufCodecs.STRING_UTF8.decode(buffer) : null;
			String dimension = ByteBufCodecs.STRING_UTF8.decode(buffer);
			int minX = ByteBufCodecs.INT.decode(buffer);
			int minY = ByteBufCodecs.INT.decode(buffer);
			int minZ = ByteBufCodecs.INT.decode(buffer);
			int maxX = ByteBufCodecs.INT.decode(buffer);
			int maxY = ByteBufCodecs.INT.decode(buffer);
			int maxZ = ByteBufCodecs.INT.decode(buffer);
			long permissions = ByteBufCodecs.VAR_LONG.decode(buffer);
			long protections = ByteBufCodecs.VAR_LONG.decode(buffer);
			return new SubClaimSummary(id, name, ownerUuid, ownerName, dimension,
				minX, minY, minZ, maxX, maxY, maxZ, permissions, protections);
		}

		@Override
		public void encode(ByteBuf buffer, SubClaimSummary value) {
			ByteBufCodecs.STRING_UTF8.encode(buffer, value.id().toString());
			ByteBufCodecs.STRING_UTF8.encode(buffer, value.name());
			ByteBufCodecs.BOOL.encode(buffer, value.ownerUuid() != null);
			if (value.ownerUuid() != null) ByteBufCodecs.STRING_UTF8.encode(buffer, value.ownerUuid().toString());
			ByteBufCodecs.BOOL.encode(buffer, value.ownerName() != null);
			if (value.ownerName() != null) ByteBufCodecs.STRING_UTF8.encode(buffer, value.ownerName());
			ByteBufCodecs.STRING_UTF8.encode(buffer, value.dimension());
			ByteBufCodecs.INT.encode(buffer, value.minX());
			ByteBufCodecs.INT.encode(buffer, value.minY());
			ByteBufCodecs.INT.encode(buffer, value.minZ());
			ByteBufCodecs.INT.encode(buffer, value.maxX());
			ByteBufCodecs.INT.encode(buffer, value.maxY());
			ByteBufCodecs.INT.encode(buffer, value.maxZ());
			ByteBufCodecs.VAR_LONG.encode(buffer, value.permissions());
			ByteBufCodecs.VAR_LONG.encode(buffer, value.protections());
		}
	};
}
