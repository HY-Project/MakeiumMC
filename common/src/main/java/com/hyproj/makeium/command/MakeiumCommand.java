package com.hyproj.makeium.command;

import com.hyproj.makeium.net.Net;
import com.hyproj.makeium.platform.Platform;
import com.hyproj.makeium.server.Tools;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /makeium [screen]} opens a Makeium screen on the caller's client; {@code /makeium tool <id>} hands out a tool.
 * Game masters only (op level 2, or cheats on in singleplayer).
 */
public final class MakeiumCommand {
    /** Screens a client can open; each maps to a client screen in MakeiumClient.openScreen. */
    public static final String[] SCREENS = {
            "hub", "display", "resourcepack", "datapack", "regions", "edit", "npc", "camera", "scoreboard", "package", "checkpoint"
    };

    private MakeiumCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("makeium")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(c -> open(c.getSource(), "hub"));
        for (String s : SCREENS) {
            root.then(Commands.literal(s).executes(c -> open(c.getSource(), s)));
        }
        root.then(Commands.literal("tool")
                .then(Commands.argument("id", StringArgumentType.word())
                        .suggests((c, b) -> {
                            for (Tools t : Tools.values()) b.suggest(t.id());
                            return b.buildFuture();
                        })
                        .executes(c -> giveTool(c.getSource(), StringArgumentType.getString(c, "id")))));
        d.register(root);
    }

    private static int open(CommandSourceStack src, String screen) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        if (!Platform.get().hasMakeium(player)) {
            src.sendFailure(Component.translatable("makeium.error.client_missing"));
            return 0;
        }
        CompoundTag t = new CompoundTag();
        t.putString("screen", screen);
        Net.sendToPlayer(player, "open", t);
        return 1;
    }

    private static int giveTool(CommandSourceStack src, String id) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        Tools tool = Tools.byId(id);
        if (tool == null) {
            src.sendFailure(Component.translatable("makeium.error.unknown_tool", id));
            return 0;
        }
        if (!player.getInventory().add(tool.create())) player.drop(tool.create(), false, net.minecraft.util.Prediction.SERVER_ONLY);
        src.sendSuccess(() -> Component.translatable("makeium.tool.given", Component.translatable("makeium.tool." + tool.id())), false);
        return 1;
    }
}
