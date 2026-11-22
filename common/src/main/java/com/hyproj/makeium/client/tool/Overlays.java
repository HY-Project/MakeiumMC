package com.hyproj.makeium.client.tool;

import java.util.List;

import com.hyproj.makeium.client.display.DisplayPicker;
import com.hyproj.makeium.client.feature.RegionCache;
import com.hyproj.makeium.feature.display.WallSnap;
import com.hyproj.makeium.server.Tools;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * In-world overlays drawn with vanilla's gizmo API from the client tick (the per-tick gizmo collector is active
 * there): display placement preview, hovered display, the block selection, trigger regions and the camera path.
 */
public final class Overlays {
    private static final int GREEN = 0xFF52A535;
    private static final int GREEN_FILL = 0x2852A535;

    private Overlays() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        try {
            Tools held = ToolController.held();
            if (ToolController.preview != null) drawPreview(ToolController.preview);
            if (ToolController.hovered != null) drawHovered(ToolController.hovered);
            if (held == Tools.SELECTION || held == Tools.REGION) drawSelection();
            if (held == Tools.REGION || RegionCache.alwaysShow) drawRegions();
            if (held == Tools.CAMERA) drawCamera();
        } catch (IllegalStateException e) {
            // no gizmo collector active (should not happen in the client tick); skip this frame
        }
    }

    private static void drawSelection() {
        BlockPos a = Selection.a(), b = Selection.b();
        if (a != null) Gizmos.cuboid(a, 0.02f, GizmoStyle.stroke(0xFFE8B931, 2f));
        if (b != null) Gizmos.cuboid(b, 0.02f, GizmoStyle.stroke(0xFF4A90D9, 2f));
        if (Selection.complete()) Gizmos.cuboid(Selection.box().inflate(0.01), GizmoStyle.strokeAndFill(GREEN, 2f, 0x1452A535));
    }

    private static void drawRegions() {
        for (RegionCache.Region r : RegionCache.regions()) {
            int color = 0xFF000000 | r.color();
            Gizmos.cuboid(r.box(), GizmoStyle.strokeAndFill(color, 2f, (0x18 << 24) | r.color()));
            Gizmos.billboardText(r.name(), r.box().getCenter().add(0, r.box().getYsize() / 2 + 0.4, 0), net.minecraft.gizmos.TextGizmo.Style.whiteAndCentered());
        }
    }

    private static void drawCamera() {
        List<CameraPath.Key> keys = CameraPath.keys();
        for (int i = 0; i < keys.size(); i++) {
            CameraPath.Key k = keys.get(i);
            Vec3 p = k.pos();
            Gizmos.point(p, GREEN, 6f);
            Vec3 look = Vec3.directionFromRotation(k.pitch, k.yaw).scale(0.8);
            Gizmos.arrow(p, p.add(look), 0xFFE8B931);
            if (i + 1 < keys.size()) Gizmos.line(p, keys.get(i + 1).pos(), GREEN, 2f);
            Gizmos.billboardText("#" + (i + 1), p.add(0, 0.3, 0), net.minecraft.gizmos.TextGizmo.Style.whiteAndCentered());
        }
    }

    private static void drawPreview(ToolController.Preview pv) {
        WallSnap.Placement p = pv.placement();
        if (pv.kind() == WallSnap.Kind.BLOCK) {
            Gizmos.cuboid(new AABB(p.x(), p.y(), p.z(), p.x() + 1, p.y() + 1, p.z() + 1), GizmoStyle.strokeAndFill(GREEN, 2f, GREEN_FILL));
            return;
        }
        // The text/item rectangle in the display's frame: orientation × translation × roll.
        Matrix4f m = new Matrix4f().translation((float) p.x(), (float) p.y(), (float) p.z());
        m.rotate(new Quaternionf().rotationYXZ((float) Math.toRadians(-p.yaw()), (float) Math.toRadians(p.pitch()), 0f));
        m.translate(p.tx(), p.ty(), p.tz());
        m.rotate(new Quaternionf().rotationZ((float) Math.toRadians(p.roll())));
        float hw = pv.width() / 2f, h = pv.height();
        float y0 = pv.kind() == WallSnap.Kind.TEXT ? 0 : -h / 2f, y1 = pv.kind() == WallSnap.Kind.TEXT ? h : h / 2f;
        Vec3 c0 = point(m, -hw, y0), c1 = point(m, hw, y0), c2 = point(m, hw, y1), c3 = point(m, -hw, y1);
        Gizmos.rect(c0, c1, c2, c3, GizmoStyle.strokeAndFill(GREEN, 2f, GREEN_FILL));
        Gizmos.point(new Vec3(p.x(), p.y(), p.z()), 0xFFE8B931, 4f);
    }

    private static Vec3 point(Matrix4f m, float x, float y) {
        Vector3f v = m.transformPosition(new Vector3f(x, y, 0));
        return new Vec3(v.x, v.y, v.z);
    }

    private static void drawHovered(Display d) {
        // Outline the picked display with a small box at its origin plus its AABB-ish bounds.
        double r = 0.08;
        Gizmos.cuboid(new AABB(d.getX() - r, d.getY() - r, d.getZ() - r, d.getX() + r, d.getY() + r, d.getZ() + r), GizmoStyle.strokeAndFill(0xFFE8B931, 2f, 0x40E8B931));
        if (d instanceof Display.TextDisplay t) {
            float[] s = DisplayPicker.textSize(t);
            Gizmos.billboardText((int) s[0] + "×" + (int) s[1] + "px", new Vec3(d.getX(), d.getY() - 0.25, d.getZ()), net.minecraft.gizmos.TextGizmo.Style.whiteAndCentered());
        }
    }
}
