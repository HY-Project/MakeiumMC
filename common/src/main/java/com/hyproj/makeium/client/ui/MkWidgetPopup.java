package com.hyproj.makeium.client.ui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import org.jspecify.annotations.Nullable;

/** A popup that hosts ordinary widgets (fields, buttons) with its own focus. */
public abstract class MkWidgetPopup extends MkPopup {
    protected final List<AbstractWidget> children = new ArrayList<>();
    private @Nullable AbstractWidget focused;
    private @Nullable AbstractWidget dragging;

    protected MkWidgetPopup(int x, int y, int w, int h) {
        super(x, y, w, h);
    }

    protected <T extends AbstractWidget> T child(T w) {
        children.add(w);
        return w;
    }

    protected void focus(@Nullable AbstractWidget w) {
        if (focused != null) focused.setFocused(false);
        focused = w;
        if (w != null) w.setFocused(true);
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        renderFrame(g, mouseX, mouseY, a);
        for (AbstractWidget w : children) w.extractRenderState(g, mouseX, mouseY, a);
        renderAfter(g, mouseX, mouseY, a);
    }

    protected abstract void renderFrame(GuiGraphicsExtractor g, int mouseX, int mouseY, float a);

    protected void renderAfter(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {}

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        for (AbstractWidget w : new ArrayList<>(children)) {
            if (w.visible && w.isMouseOver(e.x(), e.y())) {
                focus(w);
                dragging = w;
                w.mouseClicked(e, doubleClick);
                return true;
            }
        }
        focus(null);
        return contains(e.x(), e.y());
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        AbstractWidget d = dragging;
        dragging = null;
        return d != null && d.mouseReleased(e);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        return dragging != null && dragging.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        for (AbstractWidget w : children) {
            if (w.isMouseOver(mx, my) && w.mouseScrolled(mx, my, sx, sy)) return true;
        }
        return contains(mx, my);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (focused != null && focused.keyPressed(e)) return true;
        if (e.isCycleFocus()) {
            List<AbstractWidget> fields = children.stream().filter(c -> c instanceof MkField).toList();
            if (!fields.isEmpty()) {
                int i = fields.indexOf(focused);
                focus(fields.get((i + 1) % fields.size()));
            }
            return true;
        }
        if (e.isConfirmation() && onEnter()) return true;
        return super.keyPressed(e);
    }

    /** Enter outside a focused widget that consumed it. */
    protected boolean onEnter() {
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        return focused != null && focused.charTyped(e);
    }

    public boolean preeditUpdated(@Nullable PreeditEvent event) {
        return focused != null && focused.preeditUpdated(event);
    }
}
