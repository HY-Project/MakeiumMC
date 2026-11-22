package com.hyproj.makeium.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * Button. PRIMARY = green fill with a 2px darker bevel underneath (minecraft.net style); DEFAULT = surface fill + line;
 * GHOST = text only, surface on hover; DANGER = red outline; TAB = text with a 2px green underline when selected;
 * NAV = sidebar row with a 3px green bar when selected. Disabled = 40% opacity; focus = 1px green outline.
 */
public class MkButton extends AbstractButton {
    public enum Kind { PRIMARY, DEFAULT, GHOST, DANGER, TAB, NAV, CHIP }

    private final Kind kind;
    private Runnable action;
    private boolean selected;
    private int accent = MkTheme.GREEN;

    public MkButton(int x, int y, int w, int h, Component label, Kind kind, Runnable action) {
        super(x, y, w, h, label);
        this.kind = kind;
        this.action = action;
    }

    public MkButton selected(boolean v) {
        this.selected = v;
        return this;
    }

    public boolean isSelected() {
        return selected;
    }

    /** Colour of the NAV bar / CHIP fill (block categories use their own colour). */
    public MkButton accent(int argb) {
        this.accent = argb;
        return this;
    }

    public MkButton action(Runnable r) {
        this.action = r;
        return this;
    }

    public MkButton enabled(boolean v) {
        this.active = v;
        return this;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        if (active && action != null) action.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hot = active && isHoveredOrFocused();
        float alpha = active ? 1f : 0.4f;
        Font font = kind == Kind.TAB || kind == Kind.PRIMARY ? MkTheme.bold() : MkTheme.font();
        int textColor;
        switch (kind) {
            case PRIMARY -> {
                g.fill(x, y, x + w, y + h, MkTheme.withAlpha(MkTheme.GREEN_DARK, alpha));
                g.fill(x, y, x + w, y + h - 2, MkTheme.withAlpha(hot ? MkTheme.GREEN_HOVER : MkTheme.GREEN, alpha));
                textColor = 0xFFFFFFFF;
            }
            case DANGER -> {
                g.fill(x, y, x + w, y + h, MkTheme.withAlpha(hot ? MkTheme.RED_DARK : MkTheme.SURFACE, alpha));
                MkTheme.border(g, x, y, w, h, MkTheme.withAlpha(MkTheme.RED, alpha));
                textColor = hot ? 0xFFFFFFFF : MkTheme.RED;
            }
            case GHOST -> {
                if (hot) g.fill(x, y, x + w, y + h, MkTheme.SURFACE);
                textColor = hot ? MkTheme.TEXT : MkTheme.MUTED;
            }
            case TAB -> {
                textColor = selected ? MkTheme.TEXT : hot ? MkTheme.TEXT : MkTheme.MUTED;
                if (selected) g.fill(x, y + h - 2, x + w, y + h, MkTheme.GREEN);
                else if (hot) g.fill(x, y + h - 1, x + w, y + h, MkTheme.LINE_STRONG);
            }
            case NAV -> {
                if (selected) g.fill(x, y, x + w, y + h, MkTheme.SURFACE);
                else if (hot) g.fill(x, y, x + w, y + h, MkTheme.withAlpha(MkTheme.SURFACE, 0.6f));
                if (selected) g.fill(x, y, x + 3, y + h, accent);
                textColor = selected ? MkTheme.TEXT : hot ? MkTheme.TEXT : MkTheme.MUTED;
            }
            case CHIP -> {
                g.fill(x, y, x + w, y + h, MkTheme.withAlpha(selected ? accent : MkTheme.SURFACE, alpha));
                if (!selected) MkTheme.border(g, x, y, w, h, hot ? accent : MkTheme.LINE);
                textColor = selected ? 0xFFFFFFFF : MkTheme.TEXT;
            }
            default -> {
                g.fill(x, y, x + w, y + h, MkTheme.withAlpha(hot ? MkTheme.SURFACE_HOVER : MkTheme.SURFACE, alpha));
                MkTheme.border(g, x, y, w, h, MkTheme.withAlpha(hot ? MkTheme.LINE_STRONG : MkTheme.LINE, alpha));
                textColor = MkTheme.TEXT;
            }
        }
        if (isFocused() && kind != Kind.NAV && kind != Kind.TAB) MkTheme.border(g, x - 1, y - 1, w + 2, h + 2, MkTheme.GREEN_HOVER);
        int innerH = kind == Kind.PRIMARY ? h - 2 : h;
        int ty = y + (innerH - 8) / 2;
        Component fit = MkTheme.clip(font, getMessage(), w - 10);
        int color = MkTheme.withAlpha(textColor, alpha);
        if (kind == Kind.NAV) {
            g.text(font, fit, x + 12, ty, color, false);
        } else {
            MkTheme.textCentered(g, font, fit, x + w / 2, ty, color);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
