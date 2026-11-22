package com.hyproj.makeium.server;

import com.hyproj.makeium.net.Net;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/** Snapshot of the scoreboard, teams, boss bars and command storage for the dashboard (changes go through commands). */
public final class ScoreboardServer {
    private static final int MAX_SCORES = 400;

    private ScoreboardServer() {}

    public static void init() {
        Net.onServer("sb/snapshot", (p, d) -> Net.sendToPlayer(p, "sb/snapshot", snapshot(p)));
        Net.onServer("sb/storage", (p, d) -> {
            MinecraftServer server = p.level().getServer();
            CompoundTag out = new CompoundTag();
            String id = d.getStringOr("id", "");
            Identifier rid = Identifier.tryParse(id);
            out.putString("id", id);
            out.putString("snbt", rid == null ? "" : NbtUtils.structureToSnbt(server.getCommandStorage().get(rid)));
            Net.sendToPlayer(p, "sb/snapshot", out);
        });
    }

    private static CompoundTag snapshot(ServerPlayer p) {
        MinecraftServer server = p.level().getServer();
        Scoreboard sb = server.getScoreboard();
        CompoundTag out = new CompoundTag();
        ListTag objectives = new ListTag();
        for (Objective o : sb.getObjectives()) {
            CompoundTag t = new CompoundTag();
            t.putString("name", o.getName());
            t.putString("criteria", o.getCriteria().getName());
            t.putString("display", o.getDisplayName().getString());
            ListTag scores = new ListTag();
            int n = 0;
            for (PlayerScoreEntry e : sb.listPlayerScores(o)) {
                if (n++ >= MAX_SCORES) break;
                CompoundTag s = new CompoundTag();
                s.putString("holder", e.owner());
                s.putInt("value", e.value());
                scores.add(s);
            }
            t.put("scores", scores);
            objectives.add(t);
        }
        out.put("objectives", objectives);
        ListTag teams = new ListTag();
        for (PlayerTeam team : sb.getPlayerTeams()) {
            CompoundTag t = new CompoundTag();
            t.putString("name", team.getName());
            t.putString("color", team.getColor().map(c -> c.getSerializedName()).orElse("reset"));
            t.putString("display", team.getDisplayName().getString());
            ListTag members = new ListTag();
            for (String m : team.getPlayers()) members.add(StringTag.valueOf(m));
            t.put("members", members);
            t.putBoolean("friendlyFire", team.isAllowFriendlyFire());
            teams.add(t);
        }
        out.put("teams", teams);
        ListTag bars = new ListTag();
        for (CustomBossEvent b : server.getCustomBossEvents().getEvents()) {
            CompoundTag t = new CompoundTag();
            t.putString("id", b.customId().toString());
            t.putString("name", b.getName().getString());
            t.putInt("value", b.value());
            t.putInt("max", b.max());
            t.putString("color", b.getColor().getName());
            t.putBoolean("visible", b.isVisible());
            t.putInt("players", b.getPlayers().size());
            bars.add(t);
        }
        out.put("bossbars", bars);
        ListTag storage = new ListTag();
        server.getCommandStorage().keys().forEach(id -> storage.add(StringTag.valueOf(id.toString())));
        out.put("storage", storage);
        return out;
    }
}
