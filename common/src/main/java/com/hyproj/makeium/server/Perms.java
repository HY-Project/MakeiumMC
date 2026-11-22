package com.hyproj.makeium.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/** Who may use Makeium's editing tools: game masters (op level 2), which includes singleplayer with cheats on. */
public final class Perms {
    private Perms() {}

    public static boolean canEdit(ServerPlayer player) {
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }
}
