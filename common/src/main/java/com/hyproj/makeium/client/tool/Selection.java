package com.hyproj.makeium.client.tool;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/** The two-corner block selection shared by region edit, trigger regions and checkpoints. */
public final class Selection {
    private static @Nullable BlockPos a, b;

    private Selection() {}

    public static void setA(BlockPos p) {
        a = p.immutable();
    }

    public static void setB(BlockPos p) {
        b = p.immutable();
    }

    public static @Nullable BlockPos a() {
        return a;
    }

    public static @Nullable BlockPos b() {
        return b;
    }

    public static boolean complete() {
        return a != null && b != null;
    }

    public static void clear() {
        a = b = null;
    }

    public static BlockPos min() {
        return new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
    }

    public static BlockPos max() {
        return new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    public static long volume() {
        if (!complete()) return 0;
        BlockPos mn = min(), mx = max();
        return (long) (mx.getX() - mn.getX() + 1) * (mx.getY() - mn.getY() + 1) * (mx.getZ() - mn.getZ() + 1);
    }

    /** Block-aligned box covering both corners (inclusive). */
    public static AABB box() {
        BlockPos mn = min(), mx = max();
        return new AABB(mn.getX(), mn.getY(), mn.getZ(), mx.getX() + 1, mx.getY() + 1, mx.getZ() + 1);
    }

    public static CompoundTag toTag() {
        CompoundTag t = new CompoundTag();
        if (!complete()) return t;
        BlockPos mn = min(), mx = max();
        t.putIntArray("min", new int[]{mn.getX(), mn.getY(), mn.getZ()});
        t.putIntArray("max", new int[]{mx.getX(), mx.getY(), mx.getZ()});
        return t;
    }
}
