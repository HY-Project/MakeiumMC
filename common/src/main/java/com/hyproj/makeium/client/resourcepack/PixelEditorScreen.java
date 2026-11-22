package com.hyproj.makeium.client.resourcepack;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import com.hyproj.makeium.Makeium;
import com.hyproj.makeium.client.ui.MkButton;
import com.hyproj.makeium.client.ui.MkColorButton;
import com.hyproj.makeium.client.ui.MkNumberField;
import com.hyproj.makeium.client.ui.MkScreen;
import com.hyproj.makeium.client.ui.MkTheme;
import com.hyproj.makeium.client.ui.MkToast;
import com.hyproj.makeium.client.ui.MkToggle;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Pixel editor for a resource-pack texture: pencil, eraser, fill, picker, line, rectangle, horizontal mirror, undo /
 * redo, colours with alpha, a recent-colours palette, animation frames (vertical strips, with frame time in .mcmeta),
 * and PNG drop-in import. Save writes into the pack and reloads resources.
 */
public class PixelEditorScreen extends MkScreen {
    private enum Tool { PENCIL, ERASER, FILL, PICKER, LINE, RECT }

    private final RpProject project;
    private final String target;
    private final Runnable onSaved;
    private int w, h;
    private int[] px;
    private int frame, frames;
    private int frameTime = 2;
    private Tool tool = Tool.PENCIL;
    private int color = 0xFF52A535;
    private boolean mirror;
    private final List<Integer> recent = new ArrayList<>();
    private final Deque<int[]> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private NativeImage image;
    private DynamicTexture texture;
    private final Identifier textureId = Makeium.id("pixel_editor");
    private boolean dirty;
    // stroke state
    private int startX = -1, startY = -1, hoverX = -1, hoverY = -1;
    private boolean drawing;
    private MkColorButton colorButton;

    public PixelEditorScreen(Screen parent, RpProject project, String target, NativeImage source, Runnable onSaved) {
        super(Component.translatable("makeium.rp.pixel_editor"), parent);
        this.project = project;
        this.target = target;
        this.onSaved = onSaved;
        this.w = source.getWidth();
        this.h = source.getHeight();
        this.px = new int[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) px[y * w + x] = source.getPixel(x, y);
        this.frames = h > w && h % w == 0 ? h / w : 1;
        String meta = project.readText(target + ".mcmeta");
        if (meta != null) {
            var m = java.util.regex.Pattern.compile("\"frametime\"\\s*:\\s*(\\d+)").matcher(meta);
            if (m.find()) frameTime = Integer.parseInt(m.group(1));
        }
        for (int c : new int[]{0xFF000000, 0xFFFFFFFF, 0xFF7F7F7F, 0xFFD9443A, 0xFFE8B931, 0xFF52A535, 0xFF4A90D9, 0xFFB36AD9}) recent.add(c);
    }

    /** A new blank (transparent) texture of the given size. */
    public static NativeImage blank(int w, int h) {
        NativeImage img = new NativeImage(w, h, true);
        return img;
    }

    private int frameH() {
        return frames > 1 ? w : h;
    }

    private int frameTop() {
        return frame * frameH();
    }

    // ---------------------------------------------------------------- layout

    private int toolsW() {
        return 92;
    }

    private int cell() {
        int availW = width - toolsW() - 2 * PAD - 8, availH = height - TOP - 2 * PAD;
        return Math.max(1, Math.min(availW / w, availH / frameH()));
    }

    private int canvasX() {
        return toolsW() + PAD + (width - toolsW() - 2 * PAD - cell() * w) / 2;
    }

    private int canvasY() {
        return TOP + PAD + (height - TOP - 2 * PAD - cell() * frameH()) / 2;
    }

    @Override
    protected void init() {
        super.init();
        topActionsWidth = 70 + 70;
        if (texture == null) {
            image = new NativeImage(w, h, false);
            texture = new DynamicTexture(() -> "makeium pixel editor", image);
            minecraft.getTextureManager().register(textureId, texture);
            upload();
        }
        add(new MkButton(width - PAD - 60, 4, 60, 20, Component.translatable("makeium.ui.cancel"), MkButton.Kind.GHOST, this::onClose));
        add(new MkButton(width - PAD - 60 - 6 - 64, 4, 64, 20, Component.translatable("makeium.ui.save"), MkButton.Kind.PRIMARY, this::save));
        int x = 6, y = TOP + 6, bw = toolsW() - 12;
        for (Tool t : Tool.values()) {
            add(new MkButton(x, y, bw, 16, Component.translatable("makeium.px.tool." + t.name().toLowerCase(Locale.ROOT)), MkButton.Kind.NAV, () -> {
                tool = t;
                rebuildWidgets();
            }).selected(tool == t));
            y += 17;
        }
        y += 4;
        add(new MkToggle(x, y, bw, Component.translatable("makeium.px.mirror"), mirror, v -> mirror = v));
        y += 20;
        colorButton = add(new MkColorButton(this, x, y, bw, 18, color, true, c -> {
            color = c;
            remember(c);
        }));
        y += 22;
        // recent colours
        int sw = (bw - 3) / 4;
        for (int i = 0; i < Math.min(8, recent.size()); i++) {
            final int c = recent.get(i);
            int cx = x + (i % 4) * (sw + 1), cy = y + (i / 4) * (sw + 1);
            add(new MkButton(cx, cy, sw, sw, Component.empty(), MkButton.Kind.GHOST, () -> {
                color = c;
                colorButton.setValue(c);
            }) {
                @Override
                protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float a) {
                    MkTheme.checker(g, getX(), getY(), getWidth(), getHeight(), 3);
                    g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), c);
                    if (isHovered()) MkTheme.border(g, getX(), getY(), getWidth(), getHeight(), 0xFFFFFFFF);
                }
            });
        }
        y += 2 * (sw + 1) + 4;
        add(new MkButton(x, y, (bw - 2) / 2, 16, Component.literal("↶"), MkButton.Kind.DEFAULT, this::undo));
        add(new MkButton(x + (bw + 2) / 2, y, (bw - 2) / 2, 16, Component.literal("↷"), MkButton.Kind.DEFAULT, this::redo));
        y += 20;
        {
            add(new MkButton(x, y, (bw - 2) / 2, 16, Component.literal("◀"), MkButton.Kind.DEFAULT, () -> {
                frame = Math.floorMod(frame - 1, frames);
                rebuildWidgets();
            }));
            add(new MkButton(x + (bw + 2) / 2, y, (bw - 2) / 2, 16, Component.literal("▶"), MkButton.Kind.DEFAULT, () -> {
                frame = (frame + 1) % frames;
                rebuildWidgets();
            }));
            y += 18;
            add(new MkButton(x, y, bw, 16, Component.translatable("makeium.px.add_frame"), MkButton.Kind.DEFAULT, this::addFrame));
            y += 18;
            if (frames > 1) {
                MkNumberField ft = add(new MkNumberField(x, y, bw, 16, frameTime));
                ft.integer().range(1, 200).onNumber(v -> frameTime = (int) v);
            }
        }
    }

    private void remember(int c) {
        recent.remove((Integer) c);
        recent.add(0, c);
        while (recent.size() > 8) recent.remove(recent.size() - 1);
    }

    private void upload() {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) image.setPixel(x, y, px[y * w + x]);
        texture.upload();
    }

    private void pushUndo() {
        undo.push(px.clone());
        while (undo.size() > 60) undo.removeLast();
        redo.clear();
        dirty = true;
    }

    private void undo() {
        if (undo.isEmpty()) return;
        redo.push(px.clone());
        px = undo.pop();
        upload();
    }

    private void redo() {
        if (redo.isEmpty()) return;
        undo.push(px.clone());
        px = redo.pop();
        upload();
    }

    private void addFrame() {
        pushUndo();
        int fh = frameH();
        int[] next = new int[w * (h + fh)];
        System.arraycopy(px, 0, next, 0, px.length);
        // the new frame starts as a copy of the current one
        System.arraycopy(px, frameTop() * w, next, h * w, w * fh);
        px = next;
        h += fh;
        frames = h / w;
        frame = frames - 1;
        image.close();
        image = new NativeImage(w, h, false);
        texture.setPixels(image);
        upload();
        rebuildWidgets();
    }

    // ---------------------------------------------------------------- painting

    private int get(int x, int y) {
        return px[(frameTop() + y) * w + x];
    }

    private void set(int x, int y, int c) {
        if (x < 0 || y < 0 || x >= w || y >= frameH()) return;
        px[(frameTop() + y) * w + x] = c;
        if (mirror) px[(frameTop() + y) * w + (w - 1 - x)] = c;
    }

    private int paintColor() {
        return tool == Tool.ERASER ? 0 : color;
    }

    private void fill(int x, int y, int c) {
        int target = get(x, y);
        if (target == c) return;
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{x, y});
        while (!stack.isEmpty()) {
            int[] p = stack.pop();
            int cx = p[0], cy = p[1];
            if (cx < 0 || cy < 0 || cx >= w || cy >= frameH() || get(cx, cy) != target) continue;
            px[(frameTop() + cy) * w + cx] = c;
            stack.push(new int[]{cx + 1, cy});
            stack.push(new int[]{cx - 1, cy});
            stack.push(new int[]{cx, cy + 1});
            stack.push(new int[]{cx, cy - 1});
        }
    }

    private void line(int x0, int y0, int x1, int y1, int c) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
        while (true) {
            set(x0, y0, c);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    private void rect(int x0, int y0, int x1, int y1, int c) {
        line(x0, y0, x1, y0, c);
        line(x1, y0, x1, y1, c);
        line(x1, y1, x0, y1, c);
        line(x0, y1, x0, y0, c);
    }

    private int[] pixelAt(double mx, double my) {
        int c = cell();
        int x = (int) Math.floor((mx - canvasX()) / c), y = (int) Math.floor((my - canvasY()) / c);
        return x >= 0 && y >= 0 && x < w && y < frameH() ? new int[]{x, y} : null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (hasPopup()) return super.mouseClicked(e, doubleClick);
        int[] p = pixelAt(e.x(), e.y());
        if (p == null) return super.mouseClicked(e, doubleClick);
        boolean right = e.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        if (tool == Tool.PICKER || right && tool != Tool.ERASER) {
            color = get(p[0], p[1]);
            colorButton.setValue(color);
            remember(color);
            return true;
        }
        pushUndo();
        switch (tool) {
            case FILL -> fill(p[0], p[1], paintColor());
            case LINE, RECT -> {
                startX = p[0];
                startY = p[1];
            }
            default -> set(p[0], p[1], paintColor());
        }
        drawing = true;
        upload();
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (!drawing || hasPopup()) return super.mouseDragged(e, dx, dy);
        int[] p = pixelAt(e.x(), e.y());
        if (p != null && (tool == Tool.PENCIL || tool == Tool.ERASER)) {
            if (hoverX >= 0) line(hoverX, hoverY, p[0], p[1], paintColor());
            else set(p[0], p[1], paintColor());
            upload();
        }
        if (p != null) {
            hoverX = p[0];
            hoverY = p[1];
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (drawing && (tool == Tool.LINE || tool == Tool.RECT) && startX >= 0) {
            int[] p = pixelAt(e.x(), e.y());
            if (p != null) {
                if (tool == Tool.LINE) line(startX, startY, p[0], p[1], paintColor());
                else rect(startX, startY, p[0], p[1], paintColor());
                upload();
            }
        }
        drawing = false;
        startX = startY = -1;
        hoverX = hoverY = -1;
        return super.mouseReleased(e);
    }

    @Override
    public void mouseMoved(double mx, double my) {
        int[] p = pixelAt(mx, my);
        if (!drawing) {
            hoverX = p == null ? -1 : p[0];
            hoverY = p == null ? -1 : p[1];
        }
        super.mouseMoved(mx, my);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (!hasPopup() && e.hasControlDown()) {
            if (e.key() == InputConstants.KEY_Z) {
                if (e.hasShiftDown()) redo();
                else undo();
                return true;
            }
            if (e.key() == InputConstants.KEY_S) {
                save();
                return true;
            }
        }
        return super.keyPressed(e);
    }

    /** PNG files dropped on the window replace the image (scaled to the current size when they differ). */
    @Override
    public void onFilesDrop(List<Path> files) {
        for (Path f : files) {
            if (!f.toString().toLowerCase(Locale.ROOT).endsWith(".png")) continue;
            try (var in = java.nio.file.Files.newInputStream(f)) {
                NativeImage img = NativeImage.read(in);
                pushUndo();
                if (img.getWidth() == w && img.getHeight() == h) {
                    for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) px[y * w + x] = img.getPixel(x, y);
                } else {
                    // nearest-neighbour scale into the current frame
                    int fh = frameH();
                    for (int y = 0; y < fh; y++) for (int x = 0; x < w; x++) set(x, y, img.getPixel(x * img.getWidth() / w, y * img.getHeight() / fh));
                }
                img.close();
                upload();
                MkToast.ok(Component.translatable("makeium.px.imported", f.getFileName().toString()));
            } catch (Exception ex) {
                MkToast.error(Component.translatable("makeium.px.import_failed", ex.getMessage()));
            }
            return;
        }
    }

    /** Dev runs: paint a red diagonal into the current frame and save. */
    public void devPaintAndSave() {
        pushUndo();
        line(0, 0, w - 1, frameH() - 1, 0xFFFF0000);
        upload();
        save();
    }

    private void save() {
        try {
            NativeImage out = new NativeImage(w, h, false);
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) out.setPixel(x, y, px[y * w + x]);
            project.writeImage(target, out);
            out.close();
            if (frames > 1) project.writeText(target + ".mcmeta", "{\n  \"animation\": {\n    \"frametime\": " + frameTime + "\n  }\n}\n");
            dirty = false;
            MkToast.ok(Component.translatable("makeium.px.saved", target));
            project.apply();
            onSaved.run();
        } catch (Exception e) {
            MkToast.error(Component.translatable("makeium.px.save_failed", e.getMessage()));
        }
    }

    @Override
    public void removed() {
        minecraft.getTextureManager().release(textureId);
        texture = null;
        super.removed();
    }

    @Override
    protected void drawBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fill(0, TOP, toolsW(), height, MkTheme.PANEL);
        MkTheme.vline(g, toolsW() - 1, TOP, height, MkTheme.LINE);
        int c = cell(), cx = canvasX(), cy = canvasY(), fh = frameH();
        MkTheme.checker(g, cx, cy, c * w, c * fh, Math.max(2, c));
        g.blit(RenderPipelines.GUI_TEXTURED, textureId, cx, cy, 0, frameTop(), c * w, c * fh, w, fh, w, h);
        if (c >= 6) {
            for (int x = 0; x <= w; x++) g.fill(cx + x * c, cy, cx + x * c + 1, cy + fh * c, 0x30FFFFFF);
            for (int y = 0; y <= fh; y++) g.fill(cx, cy + y * c, cx + w * c, cy + y * c + 1, 0x30FFFFFF);
        }
        MkTheme.border(g, cx - 1, cy - 1, c * w + 2, c * fh + 2, MkTheme.LINE_STRONG);
        if (hoverX >= 0 && !hasPopup()) MkTheme.border(g, cx + hoverX * c, cy + hoverY * c, c, c, 0xFFFFFFFF);
        if (startX >= 0 && hoverX >= 0) MkTheme.border(g, cx + Math.min(startX, hoverX) * c, cy + Math.min(startY, hoverY) * c,
                (Math.abs(hoverX - startX) + 1) * c, (Math.abs(hoverY - startY) + 1) * c, 0xFFE8B931);
        String info = w + "×" + fh + (frames > 1 ? "  ·  " + (frame + 1) + "/" + frames : "") + (dirty ? "  •" : "") + "   " + target;
        MkTheme.text(g, MkTheme.clip(MkTheme.font(), info, width - toolsW() - 2 * PAD), toolsW() + PAD, height - 12, MkTheme.FAINT);
    }
}
