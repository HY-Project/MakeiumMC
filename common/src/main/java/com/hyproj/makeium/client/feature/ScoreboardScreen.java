package com.hyproj.makeium.client.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hyproj.makeium.client.Replies;
import com.hyproj.makeium.client.ui.MkButton;
import com.hyproj.makeium.client.ui.MkDialog;
import com.hyproj.makeium.client.ui.MkList;
import com.hyproj.makeium.client.ui.MkScreen;
import com.hyproj.makeium.client.ui.MkTheme;
import com.hyproj.makeium.net.Net;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/**
 * Scoreboard / team / boss bar / storage dashboard. Reads a snapshot from the server; every change is the matching
 * vanilla command (so it is exactly what a datapack would do), then the snapshot refreshes.
 */
public class ScoreboardScreen extends MkScreen {
    private enum Tab { OBJECTIVES, TEAMS, BOSSBARS, STORAGE }

    private static Tab tab = Tab.OBJECTIVES;
    private static CompoundTag snap = new CompoundTag();
    private static String selectedObjective = "";
    private static String storageText = "";
    private MkList left, right;

    public ScoreboardScreen(Screen parent) {
        super(Component.translatable("makeium.feature.scoreboard"), parent);
    }

    @Override
    protected void init() {
        super.init();
        topActionsWidth = 70 + 76;
        Replies.on("sb/snapshot", d -> {
            if (d.contains("snbt")) {
                storageText = d.getStringOr("snbt", "");
            } else {
                snap = d;
            }
            fill();
        });
        Net.sendToServer("sb/snapshot");
        add(new MkButton(width - PAD - 60, 4, 60, 20, Component.translatable("makeium.ui.close"), MkButton.Kind.GHOST, this::onClose));
        add(new MkButton(width - PAD - 60 - 6 - 70, 4, 70, 20, Component.translatable("makeium.ui.refresh"), MkButton.Kind.DEFAULT, () -> Net.sendToServer("sb/snapshot")));
        Tab[] tabs = Tab.values();
        int tw = Math.min(110, (width - 2 * PAD) / tabs.length);
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            add(new MkButton(PAD + i * tw, TOP + 4, tw, 20, Component.translatable("makeium.sb.tab." + t.name().toLowerCase(Locale.ROOT)), MkButton.Kind.TAB, () -> {
                tab = t;
                rebuildWidgets();
            }).selected(tab == t));
        }
        int y = TOP + 30, h = height - y - 34;
        int lw = (width - 3 * PAD) / 2;
        left = add(new MkList(PAD, y, lw, h));
        right = add(new MkList(PAD * 2 + lw, y, width - 3 * PAD - lw, h));
        int by = height - 28;
        switch (tab) {
            case OBJECTIVES -> {
                add(new MkButton(PAD, by, 110, 20, Component.translatable("makeium.sb.add_objective"), MkButton.Kind.PRIMARY, () ->
                        MkDialog.prompt(this, Component.translatable("makeium.sb.add_objective"), Component.literal("name criteria"), "points dummy", v -> run("scoreboard objectives add " + v))));
                add(new MkButton(PAD + 116, by, 110, 20, Component.translatable("makeium.sb.set_score"), MkButton.Kind.DEFAULT, () ->
                        MkDialog.prompt(this, Component.translatable("makeium.sb.set_score"), Component.literal("holder value"), "@s 10", v -> run("scoreboard players set " + first(v) + " " + selectedObjective + " " + rest(v)))));
                add(new MkButton(PAD + 232, by, 90, 20, Component.translatable("makeium.sb.sidebar"), MkButton.Kind.DEFAULT, () -> run("scoreboard objectives setdisplay sidebar " + selectedObjective)));
            }
            case TEAMS -> {
                add(new MkButton(PAD, by, 110, 20, Component.translatable("makeium.sb.add_team"), MkButton.Kind.PRIMARY, () ->
                        MkDialog.prompt(this, Component.translatable("makeium.sb.add_team"), Component.empty(), "red", v -> run("team add " + v))));
                add(new MkButton(PAD + 116, by, 110, 20, Component.translatable("makeium.sb.join_team"), MkButton.Kind.DEFAULT, () ->
                        MkDialog.prompt(this, Component.translatable("makeium.sb.join_team"), Component.literal("team who"), "red @s", v -> run("team join " + v))));
            }
            case BOSSBARS -> add(new MkButton(PAD, by, 110, 20, Component.translatable("makeium.sb.add_bossbar"), MkButton.Kind.PRIMARY, () ->
                    MkDialog.prompt(this, Component.translatable("makeium.sb.add_bossbar"), Component.literal("id"), "minecraft:boss", v -> {
                        run("bossbar add " + v + " \"" + v + "\"");
                        run("bossbar set " + v + " players @a");
                    })));
            case STORAGE -> {
            }
        }
        fill();
    }

    private static String first(String v) {
        String[] p = v.strip().split("\\s+", 2);
        return p[0];
    }

    private static String rest(String v) {
        String[] p = v.strip().split("\\s+", 2);
        return p.length > 1 ? p[1] : "0";
    }

    private void run(String command) {
        PackState.runCommand(command);
        Net.sendToServer("sb/snapshot");
    }

    private void fill() {
        if (left == null) return;
        List<MkList.Item> l = new ArrayList<>(), r = new ArrayList<>();
        switch (tab) {
            case OBJECTIVES -> {
                for (Tag t : snap.getListOrEmpty("objectives")) {
                    if (!(t instanceof CompoundTag o)) continue;
                    String name = o.getStringOr("name", "");
                    if (selectedObjective.isEmpty()) selectedObjective = name;
                    l.add(new MkList.Row(Component.literal(name), Component.literal(o.getStringOr("criteria", "") + " · " + o.getStringOr("display", "")),
                            Component.literal(Integer.toString(o.getListOrEmpty("scores").size())), () -> {
                        selectedObjective = name;
                        fill();
                    }).selected(name.equals(selectedObjective)).accent(MkTheme.RED));
                    if (name.equals(selectedObjective)) {
                        for (Tag st : o.getListOrEmpty("scores")) {
                            if (!(st instanceof CompoundTag s)) continue;
                            String holder = s.getStringOr("holder", "");
                            int value = s.getIntOr("value", 0);
                            r.add(new MkList.Row(Component.literal(holder), null, Component.literal(Integer.toString(value)), () ->
                                    MkDialog.prompt(this, Component.literal(holder + " · " + name), Component.empty(), Integer.toString(value),
                                            v -> run("scoreboard players set " + holder + " " + name + " " + v.strip()))));
                        }
                    }
                }
            }
            case TEAMS -> {
                for (Tag t : snap.getListOrEmpty("teams")) {
                    if (!(t instanceof CompoundTag o)) continue;
                    String name = o.getStringOr("name", "");
                    l.add(new MkList.Row(Component.literal(name), Component.literal(o.getStringOr("color", "") + (o.getBooleanOr("friendlyFire", true) ? "" : " · no FF")),
                            Component.literal(Integer.toString(o.getListOrEmpty("members").size())), () -> {
                        selectedObjective = name;
                        fill();
                    }).selected(name.equals(selectedObjective)));
                    if (name.equals(selectedObjective)) {
                        for (Tag m : o.getListOrEmpty("members")) {
                            String member = m.asString().orElse("");
                            r.add(new MkList.Row(Component.literal(member), null, Component.translatable("makeium.sb.leave"), () -> run("team leave " + member)));
                        }
                    }
                }
            }
            case BOSSBARS -> {
                for (Tag t : snap.getListOrEmpty("bossbars")) {
                    if (!(t instanceof CompoundTag o)) continue;
                    String id = o.getStringOr("id", "");
                    l.add(new MkList.Row(Component.literal(o.getStringOr("name", id)), Component.literal(id + " · " + o.getStringOr("color", "")),
                            Component.literal(o.getIntOr("value", 0) + "/" + o.getIntOr("max", 100)), () ->
                            MkDialog.prompt(this, Component.literal(id), Component.literal("value"), Integer.toString(o.getIntOr("value", 0)), v -> run("bossbar set " + id + " value " + v.strip()))));
                }
            }
            case STORAGE -> {
                for (Tag t : snap.getListOrEmpty("storage")) {
                    String id = t.asString().orElse("");
                    l.add(new MkList.Row(Component.literal(id), null, null, () -> {
                        CompoundTag d = new CompoundTag();
                        d.putString("id", id);
                        Net.sendToServer("sb/storage", d);
                    }));
                }
                for (String line : storageText.split("\n")) {
                    if (!line.isEmpty()) r.add(new MkList.Note(Component.literal(line), MkTheme.TEXT));
                }
            }
        }
        if (l.isEmpty()) l.add(new MkList.Note(Component.translatable("makeium.sb.empty"), MkTheme.FAINT));
        left.setItems(l);
        right.setItems(r);
    }

    @Override
    protected void drawBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {}
}
