package com.hyproj.makeium.feature.display;

import net.minecraft.core.Direction;

/**
 * Where a display goes when it is "placed like a block" on a surface — the display sign. The math mirrors vanilla's
 * DisplayRenderer: orientation = rotationYXZ(-yaw, pitch, 0); a text display's local +Z is its front, +X its reading
 * direction and +Y its top; its origin is the bottom-centre of the text, 1 block = 40 text pixels (scale 0.025).
 *
 * <ul>
 *   <li>Walls: the display faces out of the wall (yaw = the face's yaw) and sits {@link #GAP} in front of the surface,
 *       so it reads as painted on without z-fighting.</li>
 *   <li>Floor / ceiling: pitch ∓90, turned so the text reads upright for the player who placed it.</li>
 *   <li>Text is shifted down by half its height (in the display's own frame) so its visual centre is the snap point.</li>
 * </ul>
 */
public final class WallSnap {
    /** Distance in front of the surface (blocks). */
    public static final double GAP = 0.01;
    /** 1 text pixel in blocks. */
    public static final double TEXT_PIXEL = 0.025;

    public enum Snap { CENTER, GRID, FREE }

    public enum Kind { TEXT, BLOCK, ITEM }

    /**
     * Result: entity position and rotation, the transformation translation (display-local) and the roll in degrees
     * around the display's normal (left_rotation about local Z).
     */
    public record Placement(double x, double y, double z, float yaw, float pitch, float tx, float ty, float tz, float roll) {}

    private WallSnap() {}

    /**
     * @param face        the face that was hit (its normal points out of the block)
     * @param hx,hy,hz    the hit point on that face
     * @param bx,by,bz    the hit block
     * @param playerYaw   the placing player's yaw (floor/ceiling orientation)
     * @param snap        how the point snaps on the face plane
     * @param quarterTurns extra 90° turns around the face normal (R key)
     * @param textHeightPx text height in text pixels (lines × 10 − 1), only for TEXT
     * @param scaleY      the display's Y scale, only for TEXT centring
     */
    public static Placement place(Kind kind, Direction face, double hx, double hy, double hz, int bx, int by, int bz,
                                  float playerYaw, Snap snap, int quarterTurns, int textHeightPx, float scaleY) {
        if (kind == Kind.BLOCK) {
            // Exactly like placing a block: the cell in front of the face, origin at its corner.
            return new Placement(bx + face.getStepX(), by + face.getStepY(), bz + face.getStepZ(), 0, 0, 0, 0, 0, 0);
        }
        // Point on the face plane, snapped along the two in-plane axes.
        double px = hx, py = hy, pz = hz;
        switch (face.getAxis()) {
            case X -> {
                py = snapAxis(hy, by, snap);
                pz = snapAxis(hz, bz, snap);
            }
            case Y -> {
                px = snapAxis(hx, bx, snap);
                pz = snapAxis(hz, bz, snap);
            }
            case Z -> {
                px = snapAxis(hx, bx, snap);
                py = snapAxis(hy, by, snap);
            }
        }
        double gap = kind == Kind.ITEM ? 0.03 : GAP;
        px += face.getStepX() * gap;
        py += face.getStepY() * gap;
        pz += face.getStepZ() * gap;

        float yaw, pitch;
        if (face.getAxis().isHorizontal()) {
            yaw = face.toYRot();
            pitch = 0;
        } else {
            // Floor and ceiling: the text's top points away from the player, as on a page they look down (or up) at.
            yaw = snap90(playerYaw + 180f);
            pitch = face == Direction.UP ? -90f : 90f;
        }
        if (face.getAxis().isHorizontal()) {
            // Turning a wall sign rotates it around its own normal: done with the transformation, not yaw.
            yaw = wrap(yaw);
        } else {
            yaw = wrap(yaw + 90f * quarterTurns);
        }

        float roll = wallRoll(face, quarterTurns);
        float tx = 0, ty = 0;
        if (kind == Kind.TEXT) {
            // Origin is the bottom-centre of the text: move it by half the text height (rotated with the roll) so the
            // text's visual centre lands on the snap point.
            double hh = (textHeightPx + 1) / 2.0 * TEXT_PIXEL * scaleY;
            double r = Math.toRadians(roll);
            tx = (float) (hh * Math.sin(r));
            ty = (float) (-hh * Math.cos(r));
        }
        return new Placement(px, py, pz, yaw, pitch, tx, ty, 0, roll);
    }

    /** Roll (degrees) that a wall-mounted display needs for {@code quarterTurns} (applied as left_rotation about Z). */
    public static float wallRoll(Direction face, int quarterTurns) {
        return face.getAxis().isHorizontal() ? 90f * Math.floorMod(quarterTurns, 4) : 0f;
    }

    static double snapAxis(double hit, int block, Snap snap) {
        return switch (snap) {
            case CENTER -> block + 0.5;
            case GRID -> Math.round(hit * 16.0) / 16.0;
            case FREE -> hit;
        };
    }

    static float snap90(float yaw) {
        return Math.round(yaw / 90f) * 90f;
    }

    static float wrap(float yaw) {
        float y = yaw % 360f;
        if (y >= 180f) y -= 360f;
        if (y < -180f) y += 360f;
        return y == -0f ? 0f : y;
    }

    /** Text height in text pixels for {@code lines} lines (what DisplayRenderer computes). */
    public static int textHeight(int lines) {
        return Math.max(1, lines) * 10 - 1;
    }
}
