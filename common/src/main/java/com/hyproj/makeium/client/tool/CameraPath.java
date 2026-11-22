package com.hyproj.makeium.client.tool;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

/** The cutscene being recorded: camera keyframes with the travel time (ticks) to the next one. */
public final class CameraPath {
    public static final class Key {
        public double x, y, z;
        public float yaw, pitch;
        public int ticks = 40;

        public Key(double x, double y, double z, float yaw, float pitch) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public Vec3 pos() {
            return new Vec3(x, y, z);
        }
    }

    private static final List<Key> KEYS = new ArrayList<>();
    public static String name = "intro";

    private CameraPath() {}

    public static List<Key> keys() {
        return KEYS;
    }

    public static int size() {
        return KEYS.size();
    }

    public static void addCurrent() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Vec3 eye = mc.player.getEyePosition();
        KEYS.add(new Key(eye.x, eye.y, eye.z, mc.player.getYRot(), mc.player.getXRot()));
    }

    public static void clear() {
        KEYS.clear();
    }

    public static CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("name", name);
        ListTag l = new ListTag();
        for (Key k : KEYS) {
            CompoundTag c = new CompoundTag();
            c.putDouble("x", k.x);
            c.putDouble("y", k.y);
            c.putDouble("z", k.z);
            c.putFloat("yaw", k.yaw);
            c.putFloat("pitch", k.pitch);
            c.putInt("ticks", k.ticks);
            l.add(c);
        }
        t.put("keys", l);
        return t;
    }

    public static void load(CompoundTag t) {
        KEYS.clear();
        name = t.getStringOr("name", "intro");
        for (Tag e : t.getListOrEmpty("keys")) {
            if (!(e instanceof CompoundTag c)) continue;
            Key k = new Key(c.getDoubleOr("x", 0), c.getDoubleOr("y", 0), c.getDoubleOr("z", 0), c.getFloatOr("yaw", 0), c.getFloatOr("pitch", 0));
            k.ticks = c.getIntOr("ticks", 40);
            KEYS.add(k);
        }
    }
}
