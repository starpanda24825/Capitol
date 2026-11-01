package com.createcivilization.capitol.server.commands.menu;

import com.createcivilization.capitol.common.data.Team;
import com.createcivilization.capitol.common.managers.DatabaseManager;
import com.createcivilization.capitol.common.networking.packets.S2COpenManagerBook;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

// Opens the per-player manager; the keybind requests the same snapshot with a packet.
public class MenuCommand {

	public static LiteralArgumentBuilder<CommandSourceStack> register() {
		return Commands.literal("menu").executes(MenuCommand::open);
	}

	private static int open(CommandContext<CommandSourceStack> context) {
		ServerPlayer player = context.getSource().getPlayer();
		if (player == null) return 0;

		Team team = DatabaseManager.database.getPlayerTeam(player);
		long permissions = team == null ? 0L : DatabaseManager.database.getPlayerPermission(player, team);
		PacketDistributor.sendToPlayer(player, new S2COpenManagerBook(team, permissions));
		return 1;
	}
}
