package com.hyproj.makeium.client.display;

import java.util.List;

import com.mojang.math.Transformation;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Displays have no hitbox, so Makeium picks them itself: each display's local box (text: the text rectangle, block:
 * the unit cube, item: a centred unit cube) is transformed exactly as DisplayRenderer does, and the view ray is tested
 * against it in the display's local space.
 */
public final class DisplayPicker {
    private DisplayPicker() {}

    public record Hit(Display display, double distance) {}

    public static @Nullable Hit pick(double reach) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return null;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 dir = mc.player.getViewVector(1f);
        AABB area = mc.player.getBoundingBox().inflate(reach);
        Hit best = null;
        for (Display d : mc.level.getEntitiesOfClass(Display.class, area)) {
            double t = intersect(d, eye, dir, reach);
            if (t >= 0 && (best == null || t < best.distance)) best = new Hit(d, t);
        }
        return best;
    }

    /** Distance along the ray to the display's box, or -1. */
    public static double intersect(Display d, Vec3 eye, Vec3 dir, double reach) {
        Display.RenderState rs = d.renderState();
        Transformation tr = rs == null ? Transformation.IDENTITY : rs.transformation().get(1f);
        Matrix4f m = new Matrix4f().translation((float) d.getX(), (float) d.getY(), (float) d.getZ());
        m.rotate(orientation(d, rs));
        m.mul(tr.getMatrix());
        Vector3f min, max;
        if (d instanceof Display.TextDisplay text) {
            float[] size = textSize(text);
            min = new Vector3f(-size[0] / 2f * 0.025f, -0.025f, -0.04f);
            max = new Vector3f(size[0] / 2f * 0.025f, (size[1] + 1) * 0.025f, 0.04f);
        } else if (d instanceof Display.BlockDisplay) {
            min = new Vector3f(0, 0, 0);
            max = new Vector3f(1, 1, 1);
        } else {
            min = new Vector3f(-0.5f, -0.5f, -0.5f);
            max = new Vector3f(0.5f, 0.5f, 0.5f);
        }
        Matrix4f inv;
        try {
            inv = new Matrix4f(m).invert();
        } catch (ArithmeticException e) {
            return -1;
        }
        Vector3f o = inv.transformPosition(new Vector3f((float) eye.x, (float) eye.y, (float) eye.z));
        Vector3f far = inv.transformPosition(new Vector3f((float) (eye.x + dir.x * reach), (float) (eye.y + dir.y * reach), (float) (eye.z + dir.z * reach)));
        Vector3f ld = far.sub(o, new Vector3f());
        double t = slab(o, ld, min, max);
        return t < 0 ? -1 : t * reach;
    }

    /** Ray (o + t·d, t∈[0,1]) vs AABB: entry t or -1. */
    private static double slab(Vector3f o, Vector3f d, Vector3f min, Vector3f max) {
        double t0 = 0, t1 = 1;
        float[] os = {o.x, o.y, o.z}, ds = {d.x, d.y, d.z}, mn = {min.x, min.y, min.z}, mx = {max.x, max.y, max.z};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(ds[i]) < 1e-9) {
                if (os[i] < mn[i] || os[i] > mx[i]) return -1;
                continue;
            }
            double a = (mn[i] - os[i]) / ds[i], b = (mx[i] - os[i]) / ds[i];
            if (a > b) {
                double s = a;
                a = b;
                b = s;
            }
            t0 = Math.max(t0, a);
            t1 = Math.min(t1, b);
            if (t0 > t1) return -1;
        }
        return t0;
    }

    private static Quaternionf orientation(Display d, Display.@Nullable RenderState rs) {
        float yaw = d.getYRot(), pitch = d.getXRot();
        Minecraft mc = Minecraft.getInstance();
        Display.BillboardConstraints b = rs == null ? Display.BillboardConstraints.FIXED : rs.billboardConstraints();
        float camY = mc.gameRenderer.mainCamera().yRot(), camX = mc.gameRenderer.mainCamera().xRot();
        return switch (b) {
            case FIXED -> new Quaternionf().rotationYXZ((float) (-Math.PI / 180.0) * yaw, (float) (Math.PI / 180.0) * pitch, 0f);
            case HORIZONTAL -> new Quaternionf().rotationYXZ((float) (-Math.PI / 180.0) * yaw, (float) (Math.PI / 180.0) * -camX, 0f);
            case VERTICAL -> new Quaternionf().rotationYXZ((float) (-Math.PI / 180.0) * (camY - 180f), (float) (Math.PI / 180.0) * pitch, 0f);
            case CENTER -> new Quaternionf().rotationYXZ((float) (-Math.PI / 180.0) * (camY - 180f), (float) (Math.PI / 180.0) * -camX, 0f);
        };
    }

    /** Width and height in text pixels, measured like TextDisplayRenderer. */
    public static float[] textSize(Display.TextDisplay text) {
        Display.TextDisplay.TextRenderState s = text.textRenderState();
        Component c = s == null ? Component.empty() : s.text();
        int lw = s == null ? 200 : s.lineWidth();
        return textSize(c, lw);
    }

    public static float[] textSize(Component c, int lineWidth) {
        var font = Minecraft.getInstance().font;
        List<FormattedCharSequence> lines = font.split(c, Math.max(1, lineWidth));
        int w = 0;
        for (FormattedCharSequence l : lines) w = Math.max(w, font.width(l));
        int h = Math.max(1, lines.size()) * 10 - 1;
        return new float[]{w, h};
    }

    public static int lineCount(Component c, int lineWidth) {
        return Math.max(1, Minecraft.getInstance().font.split(c, Math.max(1, lineWidth)).size());
    }
}
