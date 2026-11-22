package com.hyproj.makeium.feature.camera;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.hyproj.makeium.feature.datapack.BlockCompiler;

/**
 * Camera cutscenes → the "makeium_camera" datapack. {@code /function makeium_camera:<name>/play} puts the player in
 * spectator mode and moves the view along the keyframes, one tp per tick (Catmull-Rom between positions, shortest-way
 * yaw), then restores the game mode and position. Several players can watch at once: progress is a per-player score.
 */
public final class CameraPack {
    public static final String PACK = "makeium_camera";
    public static final String NS = "makeium_camera";
    /** Keyframes store the eye position; /tp takes the feet position (spectator eye height). */
    public static final double EYE = 1.62;

    public record Key(double x, double y, double z, float yaw, float pitch, int ticks) {}

    public record Cutscene(String name, List<Key> keys) {}

    private CameraPack() {}

    public static Map<String, String> compile(List<Cutscene> scenes) {
        Map<String, String> out = new LinkedHashMap<>();
        List<String> tick = new ArrayList<>();
        for (Cutscene c : scenes) {
            if (c.keys().size() < 2) continue;
            String n = BlockCompiler.sanitizePath(c.name()).replace('/', '_');
            String tag = "mkcam_" + n;
            List<double[]> frames = frames(c.keys());
            int total = frames.size();
            String fn = NS + ":" + n;
            out.put("data/" + NS + "/function/" + n + "/play.mcfunction", String.join("\n", List.of(
                    "# Plays the cutscene '" + c.name() + "' for the executing player (" + total + " ticks)",
                    "execute store result score @s mkcam_gm run data get entity @s playerGameType",
                    "execute store result storage " + NS + ":ret x double 0.001 run data get entity @s Pos[0] 1000",
                    "execute store result storage " + NS + ":ret y double 0.001 run data get entity @s Pos[1] 1000",
                    "execute store result storage " + NS + ":ret z double 0.001 run data get entity @s Pos[2] 1000",
                    "data modify storage " + NS + ":ret player set from entity @s UUID",
                    "gamemode spectator @s",
                    "scoreboard players set @s mkcam_t 0",
                    "tag @s add " + tag)) + "\n");
            StringBuilder step = new StringBuilder("scoreboard players add @s mkcam_t 1\n");
            for (int i = 0; i < total; i++) {
                double[] f = frames.get(i);
                step.append(String.format(Locale.ROOT, "execute if score @s mkcam_t matches %d run tp @s %.3f %.3f %.3f %.2f %.2f%n", i + 1, f[0], f[1] - EYE, f[2], f[3], f[4]));
            }
            step.append("execute if score @s mkcam_t matches ").append(total + 1).append(".. run function ").append(fn).append("/end\n");
            out.put("data/" + NS + "/function/" + n + "/step.mcfunction", step.toString());
            out.put("data/" + NS + "/function/" + n + "/end.mcfunction", String.join("\n", List.of(
                    "tag @s remove " + tag,
                    "scoreboard players reset @s mkcam_t",
                    "function " + NS + ":return with storage " + NS + ":ret",
                    "execute if score @s mkcam_gm matches 0 run gamemode survival @s",
                    "execute if score @s mkcam_gm matches 1 run gamemode creative @s",
                    "execute if score @s mkcam_gm matches 2 run gamemode adventure @s",
                    "function #makeium:camera/" + n + "/done")) + "\n");
            out.put("data/makeium/tags/function/camera/" + n + "/done.json", "{\"replace\":false,\"values\":[]}");
            tick.add("execute as @a[tag=" + tag + "] run function " + fn + "/step");
        }
        out.put("data/" + NS + "/function/return.mcfunction", "$tp @s $(x) $(y) $(z)\n");
        out.put("data/" + NS + "/function/load.mcfunction", "scoreboard objectives add mkcam_t dummy\nscoreboard objectives add mkcam_gm dummy\n");
        out.put("data/" + NS + "/function/tick.mcfunction", tick.isEmpty() ? "# no cutscenes\n" : String.join("\n", tick) + "\n");
        out.put("data/minecraft/tags/function/load.json", "{\"values\":[\"" + NS + ":load\"]}");
        out.put("data/minecraft/tags/function/tick.json", "{\"values\":[\"" + NS + ":tick\"]}");
        return out;
    }

    /** One frame per tick: x, y, z, yaw, pitch. Key i travels to key i+1 in key i's ticks. */
    public static List<double[]> frames(List<Key> keys) {
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i + 1 < keys.size(); i++) {
            Key p0 = keys.get(Math.max(0, i - 1)), p1 = keys.get(i), p2 = keys.get(i + 1), p3 = keys.get(Math.min(keys.size() - 1, i + 2));
            int n = Math.max(1, p1.ticks());
            for (int s = 0; s < n; s++) {
                double t = s / (double) n;
                out.add(new double[]{
                        catmull(p0.x(), p1.x(), p2.x(), p3.x(), t),
                        catmull(p0.y(), p1.y(), p2.y(), p3.y(), t),
                        catmull(p0.z(), p1.z(), p2.z(), p3.z(), t),
                        lerpAngle(p1.yaw(), p2.yaw(), t),
                        p1.pitch() + (p2.pitch() - p1.pitch()) * t
                });
            }
        }
        Key last = keys.get(keys.size() - 1);
        out.add(new double[]{last.x(), last.y(), last.z(), last.yaw(), last.pitch()});
        return out;
    }

    static double catmull(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t, t3 = t2 * t;
        return 0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }

    static double lerpAngle(double a, double b, double t) {
        double d = ((b - a) % 360 + 540) % 360 - 180;
        return a + d * t;
    }
}
