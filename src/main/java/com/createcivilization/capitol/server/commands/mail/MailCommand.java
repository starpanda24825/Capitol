package com.createcivilization.capitol.server.commands.mail;

import com.createcivilization.capitol.common.data.MailSummary;
import com.createcivilization.capitol.common.data.Team;
import com.createcivilization.capitol.common.data.TeamRole;
import com.createcivilization.capitol.common.managers.DatabaseManager;
import com.createcivilization.capitol.common.managers.MailManager;
import com.createcivilization.capitol.common.managers.RoleManager;
import com.createcivilization.capitol.common.networking.packets.S2CInboxPage;
import com.createcivilization.capitol.common.networking.packets.S2CMailBody;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
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
import java.util.Set;

/**
 * Text-side mail front end, mainly for testing and console use; the manager GUI
 * drives the same {@link MailManager} calls through packets.
 */

public class MailCommand {

	public static LiteralArgumentBuilder<CommandSourceStack> register() {
		return Commands.literal("mail")
			.then(Commands.literal("send")
				.then(Commands.literal("team")
					.then(Commands.argument("subject", StringArgumentType.string())
						.then(Commands.argument("body", StringArgumentType.greedyString())
							.executes(MailCommand::sendTeam))))
				.then(Commands.literal("role")
					.then(Commands.argument("role", StringArgumentType.word())
						.suggests((context, builder) -> {
							Team team = teamOf(context);
							if (team == null) return builder.buildFuture();
							for (TeamRole role : DatabaseManager.database.getTeamRoles(team)) builder.suggest(role.name());
							return builder.buildFuture();
						})
						.then(Commands.argument("subject", StringArgumentType.string())
							.then(Commands.argument("body", StringArgumentType.greedyString())
								.executes(context -> sendRole(context))))))
				.then(Commands.literal("member")
					.then(Commands.argument("player", StringArgumentType.string())
						.then(Commands.argument("subject", StringArgumentType.string())
							.then(Commands.argument("body", StringArgumentType.greedyString())
								.executes(context -> sendMember(context))))))
				.then(Commands.literal("external")
					.then(Commands.argument("team", StringArgumentType.string())
						.then(Commands.argument("subject", StringArgumentType.string())
							.then(Commands.argument("body", StringArgumentType.greedyString())
								.executes(context -> sendExternal(context)))))))
			.then(Commands.literal("inbox")
				.executes(context -> inbox(context, 0))
				.then(Commands.argument("page", IntegerArgumentType.integer(0))
					.executes(context -> inbox(context, IntegerArgumentType.getInteger(context, "page")))))
			.then(Commands.literal("sent")
				.executes(context -> sent(context, 0))
				.then(Commands.argument("page", IntegerArgumentType.integer(0))
					.executes(context -> sent(context, IntegerArgumentType.getInteger(context, "page")))))
			.then(Commands.literal("read")
				.then(Commands.argument("id", LongArgumentType.longArg(1))
					.executes(MailCommand::read)))
			.then(Commands.literal("delete")
				.then(Commands.argument("id", LongArgumentType.longArg(1))
					.executes(MailCommand::delete)));
	}

	private static int sendMember(CommandContext<CommandSourceStack> context) {
		Player player = context.getSource().getPlayer();
		Team team = teamOf(context);
		if (player == null || team == null) return notInTeam(context);

		String name = StringArgumentType.getString(context, "player");
		Optional<GameProfile> profile = context.getSource().getServer().getProfileCache().get(name);
		if (profile.isEmpty() || !DatabaseManager.database.isPlayerInTeam(profile.get().getId(), team)) {
			return fail(context, "commands.capitol.team.member.not_found");
		}
		MailManager.Target target = new MailManager.Target(Set.of(profile.get().getId()), Set.of(), false, null);
		return deliver(context, player, team, target);
	}

	private static int sendRole(CommandContext<CommandSourceStack> context) {
		Player player = context.getSource().getPlayer();
		Team team = teamOf(context);
		if (player == null || team == null) return notInTeam(context);

		String name = StringArgumentType.getString(context, "role");
		TeamRole role = DatabaseManager.database.getRoleByName(team, name);
		if (role == null) return fail(context, "commands.capitol.team.role.not_found");
		MailManager.Target target = new MailManager.Target(Set.of(), Set.of(role.id()), false, null);
		return deliver(context, player, team, target);
	}

	private static int sendExternal(CommandContext<CommandSourceStack> context) {
		Player player = context.getSource().getPlayer();
		Team team = teamOf(context);
		if (player == null || team == null) return notInTeam(context);

		Team target = DatabaseManager.database.getTeamByName(StringArgumentType.getString(context, "team"));
		if (target == null) return fail(context, "commands.capitol.mail.external_not_found");
		MailManager.Target mailTarget = new MailManager.Target(Set.of(), Set.of(), false, target.getId());
		return deliver(context, player, team, mailTarget);
	}

	private static int sendTeam(CommandContext<CommandSourceStack> context) {
		Player player = context.getSource().getPlayer();
		Team team = teamOf(context);
		if (player == null || team == null) return notInTeam(context);
		MailManager.Target target = new MailManager.Target(Set.of(), Set.of(), true, null);
		return deliver(context, player, team, target);
	}

	private static int deliver(CommandContext<CommandSourceStack> context, Player player, Team team, MailManager.Target target) {
		String subject = StringArgumentType.getString(context, "subject");
		String body = StringArgumentType.getString(context, "body");

		RoleManager.Check result = MailManager.send(player, team, target, subject, body);
		if (!result.ok()) return fail(context, result.langKey(), result.args());

		context.getSource().sendSuccess(() -> Component.translatable("commands.capitol.mail.sent")
			.withStyle(ChatFormatting.GRAY), false);
		return 1;
	}

	private static int inbox(CommandContext<CommandSourceStack> context, int page) {
		Player player = context.getSource().getPlayer();
		if (player == null) return 0;
		showPage(context, MailManager.buildPage(player, MailManager.BOX_INBOX, page));
		return 1;
	}

	private static int sent(CommandContext<CommandSourceStack> context, int page) {
		Player player = context.getSource().getPlayer();
		if (player == null) return 0;
		showPage(context, MailManager.buildPage(player, MailManager.BOX_SENT, page));
		return 1;
	}

	private static int read(CommandContext<CommandSourceStack> context) {
		Player player = context.getSource().getPlayer();
		if (player == null) return 0;
		long id = LongArgumentType.getLong(context, "id");
		S2CMailBody mail = MailManager.open(player, id);
		if (mail == null) return fail(context, "commands.capitol.mail.not_found");
		context.getSource().sendSuccess(() -> Component.translatable("commands.capitol.mail.read.header",
			mail.subject(), mail.senderName() == null ? "-" : mail.senderName())
			.withStyle(ChatFormatting.GOLD), false);
		context.getSource().sendSuccess(() -> Component.literal(mail.body()).withStyle(ChatFormatting.GRAY), false);
		return 1;
	}

	private static int delete(CommandContext<CommandSourceStack> context) {
		Player player = context.getSource().getPlayer();
		if (player == null) return 0;
		MailManager.applyAction(player, LongArgumentType.getLong(context, "id"), "DELETE");
		context.getSource().sendSuccess(() -> Component.translatable("commands.capitol.mail.deleted")
			.withStyle(ChatFormatting.GRAY), false);
		return 1;
	}

	private static void showPage(CommandContext<CommandSourceStack> context, S2CInboxPage page) {
		List<MailSummary> mails = page.mails();
		if (mails.isEmpty()) {
			context.getSource().sendSuccess(() -> Component.translatable("commands.capitol.mail.empty")
				.withStyle(ChatFormatting.GRAY), false);
			return;
		}
		context.getSource().sendSuccess(() -> Component.translatable("commands.capitol.mail.list.header",
			page.box(), page.page() + 1, page.unread()).withStyle(ChatFormatting.GOLD), false);
		for (MailSummary mail : mails) {
			String tag = mail.read() ? " " : "*";
			String from = mail.senderName() == null ? (mail.teamName() == null ? "-" : mail.teamName()) : mail.senderName();
			context.getSource().sendSuccess(() -> Component.literal("#" + mail.id() + tag + " " + from + ": " + mail.subject())
				.withStyle(mail.read() ? ChatFormatting.GRAY : ChatFormatting.WHITE), false);
		}
	}

	private static Team teamOf(CommandContext<CommandSourceStack> context) {
		Player player = context.getSource().getPlayer();
		if (player == null) return null;
		return DatabaseManager.database.getPlayerTeam(player);
	}

	private static int notInTeam(CommandContext<CommandSourceStack> context) {
		return fail(context, "commands.capitol.not_in_team_error");
	}

	private static int fail(CommandContext<CommandSourceStack> context, String key, Object... args) {
		context.getSource().sendFailure(Component.translatable(key, args).withStyle(ChatFormatting.RED));
		return 0;
	}
}
