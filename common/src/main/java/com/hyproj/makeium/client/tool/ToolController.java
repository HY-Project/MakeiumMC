package com.hyproj.makeium.client.tool;

import com.hyproj.makeium.client.MakeiumClient;
import com.hyproj.makeium.client.display.DisplayBrush;
import com.hyproj.makeium.client.display.DisplayEditorScreen;
import com.hyproj.makeium.client.display.DisplayPicker;
import com.hyproj.makeium.feature.display.DisplayData;
import com.hyproj.makeium.feature.display.WallSnap;
import com.hyproj.makeium.net.Net;
import com.hyproj.makeium.server.Tools;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Client handling of Makeium tools (vanilla items with custom_data). Vanilla use/attack/pick are cancelled while one
 * is held (MinecraftInputMixin) and turned into Makeium actions here.
 *
 * <p>Display tools: right-click a surface = place (wall-snapped), sneak + right-click a display = edit, left-click a
 * display = remove, middle-click a display = copy it as the brush. R turns, V cycles the snap mode.
 */
public final class ToolController {
    /** The placement the display tools would make right now (drawn by Overlays), or null. */
    static @Nullable Preview preview;
    /** The display under the crosshair (highlighted), or null. */
    static @Nullable Display hovered;

    record Preview(WallSnap.Kind kind, WallSnap.Placement placement, float width, float height) {}

    private ToolController() {}

    public static void reset() {
        preview = null;
        hovered = null;
    }

    public static @Nullable Tools held() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        Tools t = Tools.of(mc.player.getMainHandItem());
        return t;
    }

    public static boolean holdingTool() {
        return held() != null && MakeiumClient.serverHasMakeium();
    }

    private static WallSnap.Kind displayKind(Tools t) {
        return switch (t) {
            case TEXT_DISPLAY -> WallSnap.Kind.TEXT;
            case BLOCK_DISPLAY -> WallSnap.Kind.BLOCK;
            case ITEM_DISPLAY -> WallSnap.Kind.ITEM;
            default -> null;
        };
    }

    public static void tick() {
        preview = null;
        hovered = null;
        Tools t = held();
        if (t == null || !MakeiumClient.serverHasMakeium()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null) return;
        WallSnap.Kind kind = displayKind(t);
        if (kind != null || t == Tools.DISPLAY_SELECT) {
            DisplayPicker.Hit hit = DisplayPicker.pick(mc.player.blockInteractionRange() + 1);
            hovered = hit == null ? null : hit.display();
        }
        if (kind != null) {
            while (MakeiumClient.consumeRotate()) DisplayBrush.quarterTurns = (DisplayBrush.quarterTurns + 1) % 4;
            while (MakeiumClient.consumeSnapMode()) {
                DisplayBrush.cycleSnap();
                mc.player.sendOverlayMessage(Component.translatable("makeium.display.snap", Component.translatable("makeium.display.snap." + DisplayBrush.snap.name().toLowerCase(java.util.Locale.ROOT))));
            }
            if (hovered == null) preview = computePreview(kind);
        }
    }

    private static @Nullable Preview computePreview(WallSnap.Kind kind) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.hitResult instanceof BlockHitResult bh) || bh.getType() != HitResult.Type.BLOCK) return null;
        DisplayData tpl = DisplayBrush.template(kind);
        float w = 1, h = 1;
        int textH = 9;
        float scaleY = tpl.scale().y;
        if (kind == WallSnap.Kind.TEXT) {
            float[] size = DisplayPicker.textSize(tpl.text(mc.level.registryAccess()), tpl.getInt("line_width", 200));
            w = size[0] * 0.025f * tpl.scale().x;
            h = (size[1] + 2) * 0.025f * scaleY;
            textH = (int) size[1];
        } else if (kind == WallSnap.Kind.ITEM) {
            w = h = tpl.scale().x;
        }
        BlockPos b = bh.getBlockPos();
        WallSnap.Placement p = WallSnap.place(kind, bh.getDirection(), bh.getLocation().x, bh.getLocation().y, bh.getLocation().z,
                b.getX(), b.getY(), b.getZ(), mc.player.getYRot(), DisplayBrush.snap, DisplayBrush.quarterTurns, textH, scaleY);
        return new Preview(kind, p, w, h);
    }

    /** Right click. Returns true when the vanilla action must not happen. */
    public static boolean onUse() {
        Tools t = held();
        if (t == null || !MakeiumClient.serverHasMakeium()) return false;
        Minecraft mc = Minecraft.getInstance();
        WallSnap.Kind kind = displayKind(t);
        if (kind != null || t == Tools.DISPLAY_SELECT) {
            DisplayPicker.Hit hit = DisplayPicker.pick(mc.player.blockInteractionRange() + 1);
            if (hit != null && (t == Tools.DISPLAY_SELECT || mc.player.isShiftKeyDown())) {
                editDisplay(hit.display());
                return true;
            }
            if (kind != null) {
                Preview p = computePreview(kind);
                if (p != null) place(kind, p.placement());
            }
            return true;
        }
        HitResult hr = mc.hitResult;
        BlockPos target = hr instanceof BlockHitResult bh && hr.getType() == HitResult.Type.BLOCK ? bh.getBlockPos() : null;
        switch (t) {
            case SELECTION, REGION -> {
                if (target != null) {
                    Selection.setB(target);
                    mc.player.sendOverlayMessage(Component.translatable("makeium.select.b", target.toShortString(), Selection.volume()));
                } else if (t == Tools.REGION) {
                    MakeiumClient.openScreen("regions");
                } else {
                    MakeiumClient.openScreen("edit");
                }
            }
            case LINK -> {
                if (target != null) mc.gui.setScreen(new com.hyproj.makeium.client.datapack.LinkScreen(null, target, hr instanceof BlockHitResult bh ? bh.getDirection() : null));
            }
            case NPC -> MakeiumClient.openScreen("npc");
            case CAMERA -> {
                CameraPath.addCurrent();
                mc.player.sendOverlayMessage(Component.translatable("makeium.camera.added", CameraPath.size()));
            }
            default -> {
            }
        }
        return true;
    }

    /** Left click. Returns true when vanilla attack must not happen. */
    public static boolean onAttack() {
        Tools t = held();
        if (t == null || !MakeiumClient.serverHasMakeium()) return false;
        Minecraft mc = Minecraft.getInstance();
        WallSnap.Kind kind = displayKind(t);
        if (kind != null || t == Tools.DISPLAY_SELECT) {
            DisplayPicker.Hit hit = DisplayPicker.pick(mc.player.blockInteractionRange() + 1);
            if (hit != null) {
                CompoundTag d = new CompoundTag();
                d.putString("uuid", hit.display().getUUID().toString());
                Net.sendToServer("display/remove", d);
                mc.player.sendOverlayMessage(Component.translatable("makeium.display.removed"));
            }
            return true;
        }
        HitResult hr = mc.hitResult;
        if ((t == Tools.SELECTION || t == Tools.REGION) && hr instanceof BlockHitResult bh && hr.getType() == HitResult.Type.BLOCK) {
            Selection.setA(bh.getBlockPos());
            mc.player.sendOverlayMessage(Component.translatable("makeium.select.a", bh.getBlockPos().toShortString(), Selection.volume()));
        } else if (t == Tools.CAMERA) {
            MakeiumClient.openScreen("camera");
        }
        return true;
    }

    /** Middle click: pick up a display as the brush. */
    public static boolean onPick() {
        Tools t = held();
        if (t == null || !MakeiumClient.serverHasMakeium()) return false;
        if (displayKind(t) == null && t != Tools.DISPLAY_SELECT) return true;
        Minecraft mc = Minecraft.getInstance();
        DisplayPicker.Hit hit = DisplayPicker.pick(mc.player.blockInteractionRange() + 1);
        if (hit != null) {
            DisplayData data = new DisplayData(kindOf(hit.display()), com.hyproj.makeium.server.EntityNbt.save(hit.display()));
            DisplayBrush.setTemplate(data);
            mc.player.sendOverlayMessage(Component.translatable("makeium.display.picked"));
        }
        return true;
    }

    public static WallSnap.Kind kindOf(Display d) {
        if (d instanceof Display.BlockDisplay) return WallSnap.Kind.BLOCK;
        if (d instanceof Display.ItemDisplay) return WallSnap.Kind.ITEM;
        return WallSnap.Kind.TEXT;
    }

    private static void editDisplay(Display d) {
        Minecraft mc = Minecraft.getInstance();
        DisplayData data = new DisplayData(kindOf(d), com.hyproj.makeium.server.EntityNbt.save(d));
        mc.gui.setScreen(new DisplayEditorScreen(null, d.getUUID().toString(), data));
    }

    private static void place(WallSnap.Kind kind, WallSnap.Placement p) {
        DisplayData data = DisplayBrush.template(kind).copy();
        if (DisplayBrush.alignToWall) {
            Vector3f left = data.leftEuler();
            data.setTransformation(new Vector3f(p.tx(), p.ty(), p.tz()), new Vector3f(left.x, left.y, p.roll()), data.scale(), data.rightEuler());
        }
        CompoundTag d = new CompoundTag();
        d.putString("type", DisplayData.typeName(kind));
        d.put("nbt", data.tag);
        d.putDouble("x", p.x());
        d.putDouble("y", p.y());
        d.putDouble("z", p.z());
        d.putFloat("yaw", p.yaw());
        d.putFloat("pitch", p.pitch());
        d.putBoolean("openEditor", DisplayBrush.openEditorAfterPlace);
        Net.sendToServer("display/place", d);
    }
}
