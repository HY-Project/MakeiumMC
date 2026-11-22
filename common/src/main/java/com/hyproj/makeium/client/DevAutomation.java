package com.hyproj.makeium.client;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import com.hyproj.makeium.Makeium;
import com.hyproj.makeium.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Dev runs only ({@code -Dmakeium.dev=true}, set by the Gradle run configs): {@code -Dmakeium.devScript=<file>} runs a
 * script of steps for automated in-game checks. One step per line:
 * <pre>
 *   world &lt;name&gt;          open (or create, flat + creative + cheats) a test world
 *   wait &lt;ticks&gt;
 *   cmd &lt;command&gt;         run a command as the player
 *   look &lt;yaw&gt; &lt;pitch&gt;   set the player's view
 *   screen &lt;name&gt;        open a Makeium screen (hub, display, datapack, ...)
 *   close                 close the open screen
 *   use / attack           press use / attack once (tools)
 *   shot &lt;name&gt;          save a screenshot to screenshots/&lt;name&gt;.png
 *   quit
 * </pre>
 */
public final class DevAutomation {
    private static Deque<String> steps;
    private static int wait;
    private static boolean worldRequested;

    private DevAutomation() {}

    public static boolean active() {
        return steps != null;
    }

    static void start() {
        if (steps != null || !Boolean.getBoolean("makeium.dev")) return;
        String file = System.getProperty("makeium.devScript", "");
        if (file.isBlank()) return;
        try {
            List<String> lines = Files.readAllLines(Path.of(file), StandardCharsets.UTF_8);
            steps = new ArrayDeque<>();
            for (String l : lines) {
                l = l.strip();
                if (!l.isEmpty() && !l.startsWith("#")) steps.add(l);
            }
            Makeium.LOGGER.info("Makeium dev script: {} steps from {}", steps.size(), file);
        } catch (Exception e) {
            Makeium.LOGGER.error("Could not read dev script {}", file, e);
        }
    }

    /** Called every client tick (also on the title screen). */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (steps == null) {
            if (mc.gui.screen() instanceof TitleScreen) start();
            if (steps == null) return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        if (worldRequested) {
            if (mc.player == null || mc.level == null) return;
            worldRequested = false;
            wait = 40;
            return;
        }
        String step = steps.poll();
        if (step == null) {
            steps = null;
            return;
        }
        Makeium.LOGGER.info("Makeium dev step: {}", step);
        String[] p = step.split(" ", 2);
        String arg = p.length > 1 ? p[1] : "";
        try {
            switch (p[0]) {
                case "world" -> openWorld(mc, arg.isBlank() ? "MakeiumTest" : arg);
                case "wait" -> wait = Integer.parseInt(arg.strip());
                case "cmd" -> {
                    if (mc.getConnection() != null) mc.getConnection().sendCommand(arg.startsWith("/") ? arg.substring(1) : arg);
                    wait = 5;
                }
                case "look" -> {
                    String[] a = arg.split(" ");
                    mc.player.setYRot(Float.parseFloat(a[0]));
                    mc.player.setXRot(Float.parseFloat(a[1]));
                    mc.player.yRotO = mc.player.getYRot();
                    mc.player.xRotO = mc.player.getXRot();
                    wait = 3;
                }
                case "screen" -> {
                    MakeiumClient.openScreen(arg.strip());
                    wait = 10;
                }
                case "close" -> {
                    mc.gui.setScreen(null);
                    wait = 5;
                }
                case "use" -> {
                    com.hyproj.makeium.client.tool.ToolController.onUse();
                    wait = 10;
                }
                case "attack" -> {
                    com.hyproj.makeium.client.tool.ToolController.onAttack();
                    wait = 10;
                }
                case "shot" -> {
                    File dir = Platform.get().gameDir().toFile();
                    Screenshot.grab(dir, arg.strip() + ".png", mc.gameRenderer.mainRenderTarget(), 1, c -> Makeium.LOGGER.info("Makeium dev screenshot: {}", c.getString()));
                    wait = 10;
                }
                case "dpdemo" -> com.hyproj.makeium.client.datapack.DatapackScreen.devDemo();
                case "dpsave" -> {
                    com.hyproj.makeium.client.datapack.DatapackScreen.save();
                    wait = 40;
                }
                case "dppage" -> com.hyproj.makeium.client.datapack.DatapackScreen.devPage(arg.strip());
                case "rpopen" -> {
                    com.hyproj.makeium.client.resourcepack.RpProject.open(arg.strip()).apply();
                    wait = 60;
                }
                case "rppage" -> com.hyproj.makeium.client.resourcepack.ResourcePackScreen.devPage(arg.strip());
                case "slot" -> {
                    mc.player.getInventory().setSelectedSlot(Integer.parseInt(arg.strip()));
                    wait = 3;
                }
                case "test" -> wait = DevTests.run(arg.strip());
                case "expect" -> {
                    String name = mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getSimpleName();
                    Makeium.LOGGER.info("{}_screen_{} (open: {})", name.contains(arg.strip()) ? "PASS" : "FAIL", arg.strip().toLowerCase(java.util.Locale.ROOT), name);
                }
                case "quit" -> mc.stop();
                default -> Makeium.LOGGER.warn("Unknown dev step '{}'", step);
            }
        } catch (Exception e) {
            Makeium.LOGGER.error("Dev step '{}' failed", step, e);
        }
    }

    private static void openWorld(Minecraft mc, String name) {
        worldRequested = true;
        if (mc.getLevelSource().levelExists(name)) {
            mc.createWorldOpenFlows().openWorld(name, () -> mc.gui.setScreen(new TitleScreen()));
            return;
        }
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(42L, false, false), WorldPresets::createNormalWorldDimensions, new TitleScreen());
    }
}
