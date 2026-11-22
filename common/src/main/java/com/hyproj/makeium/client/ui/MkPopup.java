package com.hyproj.makeium.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * A floating layer owned by an {@link MkScreen} (dropdown list, colour picker, modal dialog). Popups draw above every
 * widget and get input first; a click outside {@link #contains} closes the top popup.
 */
public abstract class MkPopup {
    protected int x, y, w, h;
    protected MkScreen owner;

    protected MkPopup(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    /** Keeps the popup on screen. */
    void place(int screenW, int screenH) {
        if (x + w > screenW - 4) x = Math.max(4, screenW - 4 - w);
        if (y + h > screenH - 4) y = Math.max(4, screenH - 4 - h);
        x = Math.max(4, x);
        y = Math.max(4, y);
        layout();
    }

    /** Called after placement; position child elements here. */
    protected void layout() {}

    public boolean contains(double mx, double my) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    public void close() {
        if (owner != null) owner.closePopup(this);
    }

    /** Called when a click outside closed it (editors commit here). */
    protected void onDismiss() {}

    /** Whether a click outside closes it (modal dialogs say no). */
    public boolean closeOnOutsideClick() {
        return true;
    }

    /** Dim the screen behind (modals). */
    public boolean dims() {
        return false;
    }

    public abstract void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float a);

    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        return contains(e.x(), e.y());
    }

    public boolean mouseReleased(MouseButtonEvent e) {
        return false;
    }

    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        return contains(mx, my);
    }

    public boolean keyPressed(KeyEvent e) {
        if (e.isEscape()) {
            close();
            return true;
        }
        return false;
    }

    public boolean charTyped(CharacterEvent e) {
        return false;
    }

    public void tick() {}
}
