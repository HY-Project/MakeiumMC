package com.hyproj.makeium.client;

import java.util.List;

import com.hyproj.makeium.Makeium;
import com.hyproj.makeium.client.display.DisplayEditorScreen;
import com.hyproj.makeium.client.display.DisplayFactoryScreen;
import com.hyproj.makeium.client.screen.HubScreen;
import com.hyproj.makeium.client.tool.Overlays;
import com.hyproj.makeium.client.tool.ToolController;
import com.hyproj.makeium.client.ui.MkToast;
import com.hyproj.makeium.feature.display.DisplayData;
import com.hyproj.makeium.net.Net;
import com.hyproj.makeium.platform.Platform;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CommandBlockEditScreen;
import net.minecraft.network.chat.Component;

/** Client-side glue shared by both loaders. */
public final class MakeiumClient {
    private static KeyMapping openHub, rotate, snapMode;
    private static boolean helloSent;
    private static boolean serverHasMakeium;
    private static boolean canEdit;
    private static boolean devScreenShown;

    private MakeiumClient() {}

    public static void init(KeyMapping.Category category) {
        openHub = new KeyMapping("key.makeium.hub", InputConstants.KEY_M, category);
        rotate = new KeyMapping("key.makeium.rotate", InputConstants.KEY_R, category);
        snapMode = new KeyMapping("key.makeium.snap", InputConstants.KEY_V, category);

        Net.onClient("hello", d -> {
            serverHasMakeium = true;
            canEdit = d.getBooleanOr("canEdit", false);
        });
        Net.onClient("open", d -> openScreen(d.getStringOr("screen", "hub")));
        Net.onClient("display/placed", d -> {
            DisplayData data = new DisplayData(DisplayData.kind(d.getStringOr("type", "text")), d.getCompoundOrEmpty("nbt"));
            if (d.getBooleanOr("openEditor", false)) {
                Minecraft mc = Minecraft.getInstance();
                mc.gui.setScreen(new DisplayEditorScreen(null, d.getStringOr("uuid", ""), data));
            }
        });
        for (String op : List.of("display/data", "dp/list", "dp/load", "dp/saved", "dp/validate", "sb/snapshot",
                "edit/result", "cp/list", "cp/result", "pkg/result", "region/list")) {
            Replies.channel(op);
        }
        Net.onClient("toast", d -> {
            Component c = Component.translatable(d.getStringOr("key", ""), d.getStringOr("arg", ""));
            switch (d.getStringOr("kind", "info")) {
                case "ok" -> MkToast.ok(c);
                case "error" -> MkToast.error(c);
                default -> MkToast.info(c);
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() == null && mc.player != null) mc.player.sendOverlayMessage(c);
        });
        Makeium.LOGGER.debug("Makeium client ready");
    }

    public static List<KeyMapping> keys() {
        return List.of(openHub, rotate, snapMode);
    }

    public static boolean consumeRotate() {
        return rotate.consumeClick();
    }

    public static boolean consumeSnapMode() {
        return snapMode.consumeClick();
    }

    public static boolean serverHasMakeium() {
        return serverHasMakeium;
    }

    public static boolean canEdit() {
        return canEdit;
    }

    public static void onJoin() {
        helloSent = false;
        serverHasMakeium = false;
        canEdit = false;
    }

    public static void onLeave() {
        helloSent = false;
        serverHasMakeium = false;
        canEdit = false;
        ToolController.reset();
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        DevAutomation.tick();
        if (mc.player == null) return;
        if (!helloSent && Platform.get().serverHasMakeium()) {
            helloSent = true;
            Net.sendToServer("hello");
        }
        while (openHub.consumeClick()) {
            if (mc.gui.screen() == null) openScreen("hub");
        }
        ToolController.tick();
        Overlays.tick();
    }

    /** Opens a Makeium screen by name (from /makeium or the hub). */
    public static void openScreen(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screen parent = null;
        Screen s = switch (name) {
            case "display" -> new DisplayFactoryScreen(parent);
            case "resourcepack" -> new com.hyproj.makeium.client.resourcepack.ResourcePackScreen(parent);
            case "datapack" -> new com.hyproj.makeium.client.datapack.DatapackScreen(parent);
            case "regions" -> new com.hyproj.makeium.client.feature.RegionScreen(parent);
            case "edit" -> new com.hyproj.makeium.client.feature.EditScreen(parent);
            case "npc" -> new com.hyproj.makeium.client.feature.NpcScreen(parent);
            case "camera" -> new com.hyproj.makeium.client.feature.CameraScreen(parent);
            case "scoreboard" -> new com.hyproj.makeium.client.feature.ScoreboardScreen(parent);
            case "package" -> new com.hyproj.makeium.client.feature.PackageScreen(parent);
            case "checkpoint" -> new com.hyproj.makeium.client.feature.CheckpointScreen(parent);
            default -> new HubScreen(parent);
        };
        mc.gui.setScreen(s);
    }

    /** Called for every gui.setScreen: swaps the command block editor, opens a dev screen once in dev runs. */
    public static Screen redirect(Screen next) {
        if (next instanceof CommandBlockEditScreen cb && com.hyproj.makeium.client.feature.CommandBlockScreen.enabled()) {
            return com.hyproj.makeium.client.feature.CommandBlockScreen.wrap(cb);
        }
        if (!devScreenShown && next instanceof net.minecraft.client.gui.screens.TitleScreen && Boolean.getBoolean("makeium.dev")) {
            String which = System.getProperty("makeium.devScreen", "");
            if (!which.isBlank()) {
                devScreenShown = true;
                if (which.equals("resourcepack")) return new com.hyproj.makeium.client.resourcepack.ResourcePackScreen(next);
                return new HubScreen(next);
            }
        }
        return next;
    }
}
