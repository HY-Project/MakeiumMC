package com.hyproj.makeium.client.feature;

import java.util.ArrayList;
import java.util.List;

import com.hyproj.makeium.feature.region.RegionPack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.AABB;

/** Trigger regions of the current world as the client last loaded them (for the overlay and the region screen). */
public final class RegionCache {
    public record Region(String name, String dimension, int[] min, int[] max, int color, String onEnter, String onLeave, String whileInside, String selector) {
        public AABB box() {
            return new AABB(min[0], min[1], min[2], max[0] + 1, max[1] + 1, max[2] + 1);
        }

        public CompoundTag toTag() {
            CompoundTag t = new CompoundTag();
            t.putString("name", name);
            t.putString("dimension", dimension);
            t.putIntArray("min", min);
            t.putIntArray("max", max);
            t.putInt("color", color);
            t.putString("enter", onEnter);
            t.putString("leave", onLeave);
            t.putString("inside", whileInside);
            t.putString("selector", selector);
            return t;
        }

        public static Region fromTag(CompoundTag t) {
            int[] mn = t.getIntArray("min").orElse(new int[3]);
            int[] mx = t.getIntArray("max").orElse(new int[3]);
            return new Region(t.getStringOr("name", "region"), t.getStringOr("dimension", "minecraft:overworld"),
                    mn.length == 3 ? mn : new int[3], mx.length == 3 ? mx : new int[3],
                    t.getIntOr("color", 0x52A535), t.getStringOr("enter", ""), t.getStringOr("leave", ""), t.getStringOr("inside", ""),
                    t.getStringOr("selector", "@a"));
        }

        public RegionPack.Region toPack() {
            return new RegionPack.Region(name, dimension, min, max, selector, onEnter, onLeave, whileInside);
        }
    }

    private static final List<Region> REGIONS = new ArrayList<>();
    /** Show region outlines even without the region tool in hand. */
    public static boolean alwaysShow;

    private RegionCache() {}

    public static List<Region> regions() {
        return REGIONS;
    }

    public static void set(List<Region> list) {
        REGIONS.clear();
        REGIONS.addAll(list);
    }

    public static ListTag toTag() {
        ListTag l = new ListTag();
        for (Region r : REGIONS) l.add(r.toTag());
        return l;
    }

    public static void load(ListTag l) {
        REGIONS.clear();
        for (Tag t : l) if (t instanceof CompoundTag c) REGIONS.add(Region.fromTag(c));
    }
}
