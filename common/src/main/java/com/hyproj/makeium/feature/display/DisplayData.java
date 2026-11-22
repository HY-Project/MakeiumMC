package com.hyproj.makeium.feature.display;

import java.util.Locale;

import com.mojang.serialization.DynamicOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.RegistryOps;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The editable model of one display: its kind plus the entity NBT the way /summon writes it (keys as in vanilla's
 * Display: transformation, billboard, brightness, view_range, ..., text/line_width/background/... for text,
 * block_state for block, item/item_display for item). The editor reads and writes this tag; the server applies it.
 */
public final class DisplayData {
    public final WallSnap.Kind kind;
    public final CompoundTag tag;

    public DisplayData(WallSnap.Kind kind, CompoundTag tag) {
        this.kind = kind;
        this.tag = tag;
    }

    public DisplayData copy() {
        return new DisplayData(kind, tag.copy());
    }

    public static String typeName(WallSnap.Kind k) {
        return k.name().toLowerCase(Locale.ROOT);
    }

    public static WallSnap.Kind kind(String type) {
        return switch (type) {
            case "block" -> WallSnap.Kind.BLOCK;
            case "item" -> WallSnap.Kind.ITEM;
            default -> WallSnap.Kind.TEXT;
        };
    }

    /** A new display with Makeium's defaults (fixed billboard, a starter text / stone block / diamond). */
    public static DisplayData defaults(WallSnap.Kind kind, HolderLookup.Provider regs) {
        DisplayData d = new DisplayData(kind, new CompoundTag());
        d.tag.putString("billboard", "fixed");
        d.setTransformation(new Vector3f(), new Vector3f(), scaleDefault(kind), new Vector3f());
        switch (kind) {
            case TEXT -> {
                d.setText(Component.literal("Makeium"), regs);
                d.tag.putInt("line_width", 200);
                d.tag.putInt("background", 0);
                d.tag.putBoolean("shadow", true);
                d.tag.putString("alignment", "center");
            }
            case BLOCK -> {
                CompoundTag state = new CompoundTag();
                state.putString("Name", "minecraft:stone");
                d.tag.put("block_state", state);
            }
            case ITEM -> {
                CompoundTag item = new CompoundTag();
                item.putString("id", "minecraft:diamond");
                item.putInt("count", 1);
                d.tag.put("item", item);
                d.tag.putString("item_display", "fixed");
            }
        }
        return d;
    }

    private static Vector3f scaleDefault(WallSnap.Kind kind) {
        return kind == WallSnap.Kind.ITEM ? new Vector3f(0.5f) : new Vector3f(1f);
    }

    // ---------------------------------------------------------------- transformation (Euler degrees for the UI)

    public Vector3f translation() {
        return vec(transformation().getListOrEmpty("translation"), 0f);
    }

    public Vector3f scale() {
        return vec(transformation().getListOrEmpty("scale"), 1f);
    }

    /** left_rotation as XYZ Euler angles in degrees. */
    public Vector3f leftEuler() {
        return euler(transformation().getListOrEmpty("left_rotation"));
    }

    public Vector3f rightEuler() {
        return euler(transformation().getListOrEmpty("right_rotation"));
    }

    private CompoundTag transformation() {
        return tag.getCompoundOrEmpty("transformation");
    }

    public void setTransformation(Vector3f translation, Vector3f leftEulerDeg, Vector3f scale, Vector3f rightEulerDeg) {
        CompoundTag t = new CompoundTag();
        t.put("translation", floats(translation.x, translation.y, translation.z));
        Quaternionf l = quat(leftEulerDeg), r = quat(rightEulerDeg);
        t.put("left_rotation", floats(l.x, l.y, l.z, l.w));
        t.put("scale", floats(scale.x, scale.y, scale.z));
        t.put("right_rotation", floats(r.x, r.y, r.z, r.w));
        tag.put("transformation", t);
    }

    public void setTranslation(Vector3f v) {
        setTransformation(v, leftEuler(), scale(), rightEuler());
    }

    public void setScale(Vector3f v) {
        setTransformation(translation(), leftEuler(), v, rightEuler());
    }

    public void setLeftEuler(Vector3f v) {
        setTransformation(translation(), v, scale(), rightEuler());
    }

    public void setRightEuler(Vector3f v) {
        setTransformation(translation(), leftEuler(), scale(), v);
    }

    public static Quaternionf quat(Vector3f deg) {
        return new Quaternionf().rotationXYZ((float) Math.toRadians(deg.x), (float) Math.toRadians(deg.y), (float) Math.toRadians(deg.z));
    }

    private static Vector3f euler(ListTag list) {
        if (list.size() != 4) return new Vector3f();
        Quaternionf q = new Quaternionf(list.getFloatOr(0, 0), list.getFloatOr(1, 0), list.getFloatOr(2, 0), list.getFloatOr(3, 1));
        Vector3f e = q.getEulerAnglesXYZ(new Vector3f());
        return new Vector3f(round((float) Math.toDegrees(e.x)), round((float) Math.toDegrees(e.y)), round((float) Math.toDegrees(e.z)));
    }

    private static float round(float v) {
        return Math.round(v * 1000f) / 1000f;
    }

    private static Vector3f vec(ListTag list, float fallback) {
        if (list.size() != 3) return new Vector3f(fallback);
        return new Vector3f(list.getFloatOr(0, fallback), list.getFloatOr(1, fallback), list.getFloatOr(2, fallback));
    }

    public static ListTag floats(float... v) {
        ListTag l = new ListTag();
        for (float f : v) l.add(FloatTag.valueOf(f));
        return l;
    }

    // ---------------------------------------------------------------- text

    public Component text(HolderLookup.Provider regs) {
        Tag t = tag.get("text");
        if (t == null) return Component.empty();
        DynamicOps<Tag> ops = regs == null ? NbtOps.INSTANCE : RegistryOps.create(NbtOps.INSTANCE, regs);
        return ComponentSerialization.CODEC.parse(ops, t).result().orElse(Component.empty());
    }

    public void setText(Component c, HolderLookup.Provider regs) {
        DynamicOps<Tag> ops = regs == null ? NbtOps.INSTANCE : RegistryOps.create(NbtOps.INSTANCE, regs);
        ComponentSerialization.CODEC.encodeStart(ops, c).result().ifPresent(t -> tag.put("text", t));
    }

    // ---------------------------------------------------------------- small typed helpers

    public int getInt(String key, int def) {
        return tag.getIntOr(key, def);
    }

    public boolean getBool(String key, boolean def) {
        return tag.getBooleanOr(key, def);
    }

    public float getFloat(String key, float def) {
        return tag.getFloatOr(key, def);
    }

    public String getString(String key, String def) {
        return tag.getStringOr(key, def);
    }

    /** Brightness override: -1 = none, else block light (sky light is set to the same value). */
    public int brightness() {
        CompoundTag b = tag.getCompoundOrEmpty("brightness");
        return b.isEmpty() ? -1 : b.getIntOr("block", 15);
    }

    public void setBrightness(int v) {
        if (v < 0) {
            tag.remove("brightness");
            return;
        }
        CompoundTag b = new CompoundTag();
        b.putInt("block", v);
        b.putInt("sky", v);
        tag.put("brightness", b);
    }

    /** Text opacity 0-255 (stored as a signed byte, 255 = -1). */
    public int textOpacity() {
        return tag.getByteOr("text_opacity", (byte) -1) & 0xFF;
    }

    public void setTextOpacity(int v) {
        tag.putByte("text_opacity", (byte) v);
    }
}
