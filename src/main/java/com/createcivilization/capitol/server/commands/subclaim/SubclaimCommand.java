package com.createcivilization.capitol.server.commands.subclaim;

import com.createcivilization.capitol.common.data.Permission;
import com.createcivilization.capitol.common.data.SubClaim;
import com.createcivilization.capitol.common.data.SubClaimProtection;
import com.createcivilization.capitol.common.data.SubClaimSummary;
import com.createcivilization.capitol.common.data.Team;
import com.createcivilization.capitol.common.managers.DatabaseManager;
import com.createcivilization.capitol.common.managers.RoleManager;
import com.createcivilization.capitol.common.managers.SubClaimManager;
import com.createcivilization.capitol.common.modules.database.CapitolDatabase;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SubclaimCommand {

	public static LiteralArgumentBuilder<CommandSourceStack> register() {
		return Commands.literal("subclaim")
			.then(Commands.literal("list")
				.executes(context -> list(context, false))
				.then(Commands.literal("all")
					.executes(context -> list(context, true))))
			.then(Commands.literal("transfer")
				.then(Commands.argument("id", StringArgumentType.word())
					.then(Commands.argument("player", StringArgumentType.string())
						.executes(SubclaimCommand::transfer))))
			.then(Commands.literal("rename")
				.then(Commands.argument("id", StringArgumentType.word())
					.then(Commands.argument("name", StringArgumentType.string())
						.executes(SubclaimCommand::rename))))
			.then(Commands.literal("permission")
				.then(Commands.argument("id", StringArgumentType.word())
					.then(Commands.argument("permission_value", StringArgumentType.word())
						.suggests((context, builder) -> {
							for (Permission permission : Permission.values()) {
								builder.suggest(permission.name().toLowerCase());
							}
							return builder.buildFuture();
						})
						.executes(SubclaimCommand::togglePermission))))
			.then(Commands.literal("protection")
				.then(Commands.argument("id", StringArgumentType.word())
					.then(Commands.argument("protection_value", StringArgumentType.word())
						.suggests((context, builder) -> {
							for (SubClaimProtection protection : SubClaimProtection.values()) {
								builder.suggest(protection.getKey());
							}
							return builder.buildFuture();
						})
						.executes(SubclaimCommand::toggleProtection))))
			.then(Commands.literal("delete")
				.then(Commands.argument("id", StringArgumentType.word())
					.executes(SubclaimCommand::delete)));
	}

	private static int list(CommandContext<CommandSourceStack> context, boolean all) {
		Player player = context.getSource().getPlayer();
		Team team = teamOf(context);
		if (player == null || team == null) return notInTeam(context);

		List<SubClaimSummary> rows = SubClaimManager.buildPage(player, team, 0, all, null, null);
		if (rows.isEmpty()) {
			context.getSource().sendSuccess(() -> Component.translatable("commands.capitol.subclaim.list.empty").withStyle(ChatFormatting.YELLOW), false);
			return 1;
		}
		if (all && !SubClaimManager.canViewAll(player, team)) {
			context.getSource().sendSuccess(() -> Component.translatable("commands.capitol.subclaim.list.scoped").withStyle(ChatFormatting.GRAY), false);
		}
		for (SubClaimSummary row : rows) {
			context.getSource().sendSuccess(() -> Component.translatable("commands.capitol.subclaim.list.entry",
				Component.literal(row.name()).withStyle(ChatFormatting.WHITE),
				Component.literal(row.ownerName() == null ? "-" : row.ownerName()).withStyle(ChatFormatting.AQUA),
				Component.literal(row.id().toString()).withStyle(ChatFormatting.GRAY))
				.withStyle(ChatFormatting.GRAY), false);
		}
		return 1;
	}

	private static int transfer(CommandContext<CommandSourceStack> context) {
		Player player = context.getSource().getPlayer();
		Team team = teamOf(context);
		if (player == null || team == null) return notInTeam(context);

		SubClaim subClaim = getSubClaim(context);
		if (subClaim == null) return 0;

		String name = StringArgumentType.getString(context, "player");
		Optional<GameProfile> profile = context.getSource().getServer().getProfileCache().get(name);
		if (profile.isEmpty()) return fail(context, "commands.capitol.no_player_found", name);

		RoleManager.Check result = SubClaimManager.transfer(player, team, subClaim, profile.get().getId());
		if (!result.ok()) return fail(context, result.langKey(), result.args());

		context.getSource().sendSuccess(() -> Component.translatable("commands.capitol.subclaim.transfer.success",
			Component.literal(subClaim.name()).withStyle(ChatFormatting.WHITE),
			Component.literal(profile.get().getName()).withStyle(ChatFormatting.AQUA))
			.withStyle(ChatFormatting.GRAY), true);
		return 1;
	}

	private static int rename(CommandContext<CommandSourceStack> context) {
		Player player = context.getSource().getPlayer();
		Team team = teamOf(context);
		if (player == null || team == null) return notInTeam(context);

		SubClaim subClaim = getSubClaim(context);
		if (subClaim == null) return 0;

		RoleManager.Check result = SubClaimManager.rename(player, team, subClaim, StringArgumentType.getString(context, "name"));
		if (!result.ok()) return fail(context, result.langKey(), result.args());

		context.getSource().sendSuccess(() -> Component.translatable("commands.capitol.subclaim.renamed",
			Component.literal(subClaim.name()).withStyle(ChatFormatting.WHITE))
			.withStyle(ChatFormatting.GRAY), true);
		return 1;
	}

	private static int togglePermission(CommandContext<CommandSourceStack> context) {
		Player player = context.getSource().getPlayer();
		Team team = teamOf(context);
		if (player == null || team == null) return notInTeam(context);

		SubClaim subClaim = getSubClaim(context);
		if (subClaim == null) return 0;

		String permissionName = StringArgumentType.getString(context, "permission_value").toUpperCase();
		Permission permission;
		try {
			permission = Permission.valueOf(permissionName);
		} catch (IllegalArgumentException e) {
			return fail(context, "commands.capitol.invalid_permission", permissionName);
		}

		boolean oldState = subClaim.hasPermission(permission);
		long newBits = permission.toggle(subClaim.permissions());
		RoleManager.Check result = SubClaimManager.setPermissions(player, team, subClaim, newBits);
		if (!result.ok()) return fail(context, result.langKey(), result.args());
		boolean newState = permission.hasPermission(newBits);

		context.getSource().sendSuccess(() -> Component.translatable("commands.capitol.subclaim.permission.toggled",
			Component.literal(permission.name().toLowerCase()).withStyle(ChatFormatting.AQUA),
			Component.literal(subClaim.name()).withStyle(ChatFormatting.WHITE),
			Component.translatable(oldState ? "commands.capitol.enabled" : "commands.capitol.disabled")
				.withStyle(oldState ? ChatFormatting.GREEN : ChatFormatting.RED),
			Component.translatable(newState ? "commands.capitol.enabled" : "commands.capitol.disabled")
				.withStyle(newState ? ChatFormatting.GREEN : ChatFormatting.RED))
			.withStyle(ChatFormatting.GRAY), true);
		return 1;
	}

	private static int toggleProtection(CommandContext<CommandSourceStack> context) {
		Player player = context.getSource().getPlayer();
		Team team = teamOf(context);
		if (player == null || team == null) return notInTeam(context);

		SubClaim subClaim = getSubClaim(context);
		if (subClaim == null) return 0;

		String protectionName = StringArgumentType.getString(context, "protection_value").toUpperCase();
		SubClaimProtection protection;
		try {
			protection = SubClaimProtection.valueOf(protectionName);
		} catch (IllegalArgumentException e) {
			return fail(context, "commands.capitol.invalid_protection", protectionName);
		}

		long newBits = protection.toggle(subClaim.protections());
		RoleManager.Check result = SubClaimManager.setProtections(player, team, subClaim, newBits);
		if (!result.ok()) return fail(context, result.langKey(), result.args());
		boolean newState = protection.hasProtection(newBits);

		context.getSource().sendSuccess(() -> Component.translatable("commands.capitol.subclaim.protection.toggled",
			Component.literal(protection.getKey()).withStyle(ChatFormatting.AQUA),
			Component.literal(subClaim.name()).withStyle(ChatFormatting.WHITE),
			Component.translatable(newState ? "commands.capitol.enabled" : "commands.capitol.disabled")
				.withStyle(newState ? ChatFormatting.GREEN : ChatFormatting.RED))
			.withStyle(ChatFormatting.GRAY), true);
		return 1;
	}

	private static int delete(CommandContext<CommandSourceStack> context) {
		Player player = context.getSource().getPlayer();
		Team team = teamOf(context);
		if (player == null || team == null) return notInTeam(context);

		SubClaim subClaim = getSubClaim(context);
		if (subClaim == null) return 0;

		RoleManager.Check result = SubClaimManager.delete(player, team, subClaim);
		if (!result.ok()) return fail(context, result.langKey(), result.args());

		context.getSource().sendSuccess(() -> Component.translatable("commands.capitol.subclaim.deleted",
			Component.literal(subClaim.name()).withStyle(ChatFormatting.WHITE))
			.withStyle(ChatFormatting.GREEN), true);
		return 1;
	}

	private static Team teamOf(CommandContext<CommandSourceStack> context) {
		Player player = context.getSource().getPlayer();
		return player == null ? null : DatabaseManager.database.getPlayerTeam(player);
	}

	// Parses the {@code id} argument and returns the matching sub-claim, or null after sending a failure message.
	private static SubClaim getSubClaim(CommandContext<CommandSourceStack> context) {
		CapitolDatabase database = DatabaseManager.database;
		String idString = StringArgumentType.getString(context, "id");
		UUID id;
		try {
			id = UUID.fromString(idString);
		} catch (IllegalArgumentException e) {
			return failClaim(context);
		}

		SubClaim subClaim = database.getSubClaim(id);
		if (subClaim == null) return failClaim(context);
		return subClaim;
	}

	private static SubClaim failClaim(CommandContext<CommandSourceStack> context) {
		context.getSource().sendFailure(Component.translatable("commands.capitol.subclaim.not_found").withStyle(ChatFormatting.RED));
		return null;
	}

	private static int notInTeam(CommandContext<CommandSourceStack> context) {
		context.getSource().sendFailure(Component.translatable("commands.capitol.not_in_team_error").withStyle(ChatFormatting.RED));
		return 0;
	}

	private static int fail(CommandContext<CommandSourceStack> context, String key, Object... args) {
		context.getSource().sendFailure(Component.translatable(key, args).withStyle(ChatFormatting.RED));
		return 0;
	}
}
