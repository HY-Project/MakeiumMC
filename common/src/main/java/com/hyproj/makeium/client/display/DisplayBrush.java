package com.hyproj.makeium.client.display;

import java.util.EnumMap;
import java.util.Map;

import com.hyproj.makeium.feature.display.DisplayData;
import com.hyproj.makeium.feature.display.WallSnap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;

/**
 * What the display tools place next: one template per kind (edited in the Display Factory, or picked up from an
 * existing display with middle-click), plus the placement options.
 */
public final class DisplayBrush {
    private static final Map<WallSnap.Kind, DisplayData> TEMPLATES = new EnumMap<>(WallSnap.Kind.class);
    public static WallSnap.Snap snap = WallSnap.Snap.CENTER;
    public static int quarterTurns;
    public static boolean openEditorAfterPlace = true;
    public static boolean alignToWall = true;

    private DisplayBrush() {}

    public static DisplayData template(WallSnap.Kind kind) {
        return TEMPLATES.computeIfAbsent(kind, k -> DisplayData.defaults(k, regs()));
    }

    public static void setTemplate(DisplayData data) {
        DisplayData copy = data.copy();
        // A template never carries identity or position.
        for (String k : new String[]{"UUID", "Pos", "Rotation", "Motion", "Tags", "id"}) copy.tag.remove(k);
        TEMPLATES.put(data.kind, copy);
    }

    public static void cycleSnap() {
        snap = WallSnap.Snap.values()[(snap.ordinal() + 1) % WallSnap.Snap.values().length];
    }

    static HolderLookup.Provider regs() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? null : mc.level.registryAccess();
    }
}
