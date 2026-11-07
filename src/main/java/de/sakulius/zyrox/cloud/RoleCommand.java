package de.sakulius.zyrox.cloud;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public final class RoleCommand {
	private RoleCommand() {
	}

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(ClientCommandManager.literal("zyrox").then(ClientCommandManager.literal("role")
			.then(ClientCommandManager.literal("add").then(ClientCommandManager.argument("name", StringArgumentType.word())
				.then(ClientCommandManager.argument("role", StringArgumentType.word())
					.suggests((context, builder) -> builder.suggest("staff").suggest("tester").buildFuture())
					.executes(RoleCommand::add))))
			.then(ClientCommandManager.literal("remove").then(ClientCommandManager.argument("name", StringArgumentType.word())
				.executes(context -> change(context, null))))
			.then(ClientCommandManager.literal("list").executes(RoleCommand::list))));
	}

	private static int add(CommandContext<FabricClientCommandSource> context) {
		var role = Roles.parse(StringArgumentType.getString(context, "role"));
		if (role == null || role == Roles.Role.OWNER) {
			context.getSource().sendError(Component.literal("Use staff or tester."));
			return 0;
		}
		return change(context, role);
	}

	private static int change(CommandContext<FabricClientCommandSource> context, Roles.Role role) {
		var source = context.getSource();
		if (!Roles.isOwner()) {
			source.sendError(Component.literal("Only the owner can give roles."));
			return 0;
		}
		Roles.set(source.getClient(), StringArgumentType.getString(context, "name"), role, text -> source.sendFeedback(Component.literal(text).withStyle(ChatFormatting.GRAY)));
		return 1;
	}

	private static int list(CommandContext<FabricClientCommandSource> context) {
		var source = context.getSource();
		if (!Roles.isStaff()) {
			source.sendError(Component.literal("Only the team can see this."));
			return 0;
		}
		var team = Roles.team();
		source.sendFeedback(Component.literal("Zyrox team (" + team.size() + ")"));
		for (var member : team)
			source.sendFeedback(Component.literal(member.role().label + "  ").withColor(member.role().color).append(Component.literal(member.name().isEmpty() ? "Unknown" : member.name()).withStyle(ChatFormatting.GRAY)));
		return 1;
	}
}
