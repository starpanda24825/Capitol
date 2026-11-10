package com.createcivilization.capitol.common.data;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * One custom sub-tab of the manager's Team section: a colour and a name the team's
 * sub-claims are filtered by, so a tab lists the sub-claims whose name contains it.
 *
 * @param id     database id, unique per row
 * @param name   the filter text, also the tab's tooltip
 * @param colour index into the book sheet's five colours
 */
public record TeamSubTab(int id, String name, int colour) {

	// How many custom tabs one team may make; the two defaults never count.
	public static final int MAX_PER_TEAM = 3;

	// Longest filter text accepted, so a tab's tooltip stays readable.
	public static final int MAX_NAME = 24;

	// The sheet carries five colours, so a tab is one of them.
	public static final int COLOURS = 5;

	public static final StreamCodec<ByteBuf, TeamSubTab> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public TeamSubTab decode(ByteBuf buffer) {
			return new TeamSubTab(
				ByteBufCodecs.VAR_INT.decode(buffer),
				ByteBufCodecs.STRING_UTF8.decode(buffer),
				ByteBufCodecs.VAR_INT.decode(buffer));
		}

		@Override
		public void encode(ByteBuf buffer, TeamSubTab value) {
			ByteBufCodecs.VAR_INT.encode(buffer, value.id());
			ByteBufCodecs.STRING_UTF8.encode(buffer, value.name());
			ByteBufCodecs.VAR_INT.encode(buffer, value.colour());
		}
	};

	// Whether the team's sub-claims belong under this tab.
	public boolean matches(String subClaimName) {
		return subClaimName.toLowerCase(java.util.Locale.ROOT).contains(name.toLowerCase(java.util.Locale.ROOT));
	}
}
