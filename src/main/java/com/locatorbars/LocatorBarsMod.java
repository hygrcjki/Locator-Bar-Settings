package com.locatorbars;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;

public final class LocatorBarsMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("customizable_locator_bars");
    public static final GroupService GROUPS = new GroupService();

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> GROUPS.load());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> GROUPS.save());
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            registerPlayerCommands(dispatcher);
            registerOperatorCommands(dispatcher);
        });
    }

    private static void registerPlayerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("locator")
                .requires(source -> source.getEntity() instanceof ServerPlayer player && GROUPS.canUse(player.getUUID()))
                .then(literal("create").then(argument("name", word()).executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    if (!GROUPS.canJoinOrCreate(player.getUUID())) return fail(player, "You are not allowed to create or join groups.");
                    return result(player, GROUPS.create(getString(context, "name"), player.getUUID()));
                })))
                .then(literal("invite").then(argument("player", EntityArgument.player()).executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    ServerPlayer invited = EntityArgument.getPlayer(context, "player");
                    if (!GROUPS.canJoinOrCreate(invited.getUUID())) return fail(player, "That player is not allowed to join groups.");
                    GroupService.Group group = GROUPS.groupOf(player.getUUID()).orElse(null);
                    if (group == null) return fail(player, "You are not in a group.");
                    GroupService.Result outcome = GROUPS.invite(group.name(), player.getUUID(), invited.getUUID());
                    if (outcome.success()) invited.sendSystemMessage(Component.literal("You were invited to " + group.name() + ". Use /locator accept " + group.name()));
                    return result(player, outcome);
                })))
                .then(literal("accept").then(argument("group", word()).executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    if (!GROUPS.canJoinOrCreate(player.getUUID())) return fail(player, "You are not allowed to create or join groups.");
                    return result(player, GROUPS.accept(getString(context, "group"), player.getUUID()));
                })))
                .then(literal("decline").then(argument("group", word()).executes(context ->
                        result(context.getSource().getPlayerOrException(), GROUPS.decline(getString(context, "group"), context.getSource().getPlayerOrException().getUUID())))))
                .then(literal("leave").executes(context -> result(context.getSource().getPlayerOrException(), GROUPS.leave(context.getSource().getPlayerOrException().getUUID()))))
                .then(literal("info").executes(context -> showInfo(context.getSource().getPlayerOrException())))
                .then(literal("gui").executes(context -> {
                    LocatorMenu.openPlayerMenu(context.getSource().getPlayerOrException());
                    return 1;
                })));
    }

    private static void registerOperatorCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("locatorop").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(literal("gui").executes(context -> { LocatorMenu.openOperatorMenu(context.getSource().getPlayerOrException()); return 1; }))
                .then(literal("access").then(argument("player", EntityArgument.player())
                        .then(literal("allow").executes(context -> setAccess(context, true)))
                        .then(literal("deny").executes(context -> setAccess(context, false)))))
                .then(literal("block").then(argument("player", EntityArgument.player()).executes(context -> setBlocked(context, true))))
                .then(literal("unblock").then(argument("player", EntityArgument.player()).executes(context -> setBlocked(context, false))))
                .then(literal("add").then(argument("group", word()).then(argument("player", EntityArgument.player()).executes(context ->
                        result(context.getSource().getPlayerOrException(), GROUPS.forceAdd(getString(context, "group"), EntityArgument.getPlayer(context, "player").getUUID()))))))
                .then(literal("kick").then(argument("group", word()).then(argument("player", EntityArgument.player()).executes(context ->
                        result(context.getSource().getPlayerOrException(), GROUPS.forceKick(getString(context, "group"), EntityArgument.getPlayer(context, "player").getUUID()))))))
                .then(literal("disband").then(argument("group", word()).executes(context ->
                        result(context.getSource().getPlayerOrException(), GROUPS.disband(getString(context, "group")))))));
    }

    private static int setAccess(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, boolean allowed) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        GROUPS.setCommandAccess(target.getUUID(), allowed);
        return success(context.getSource().getPlayerOrException(), allowed ? "Command access granted." : "Command access denied.");
    }
    private static int setBlocked(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, boolean blocked) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        GROUPS.setGroupBlocked(target.getUUID(), blocked);
        return success(context.getSource().getPlayerOrException(), blocked ? "Player blocked from groups." : "Player unblocked from groups.");
    }
    private static int showInfo(ServerPlayer player) {
        GroupService.Group group = GROUPS.groupOf(player.getUUID()).orElse(null);
        if (group == null) return fail(player, "You are not in a group.");
        return success(player, group.name() + ": " + group.members().size() + "/" + GroupService.MAX_MEMBERS + " members.");
    }
    static int result(ServerPlayer player, GroupService.Result outcome) { return outcome.success() ? success(player, outcome.message()) : fail(player, outcome.message()); }
    static int success(ServerPlayer player, String message) { player.sendSystemMessage(Component.literal(message)); return 1; }
    static int fail(ServerPlayer player, String message) { player.sendSystemMessage(Component.literal("[Locator Bars] " + message)); return 0; }
}
