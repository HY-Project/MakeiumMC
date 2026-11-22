package com.hyproj.makeium.client.ui;

import java.util.function.Consumer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Text input on an unbordered EditBox drawn with the Noto Sans font (so the caret lines up): input fill, 1px line,
 * green border when focused. (x, y, w, h) is the whole box; the text sits inside with 6px padding.
 */
public class MkField extends EditBox {
    private static final int PAD_X = 6;

    private int boxX, boxY, boxW, boxH;
    private boolean invalid;

    public MkField(int x, int y, int w, int h, Component hint) {
        this(MkTheme.font(), x, y, w, h, hint);
    }

    public MkField(Font font, int x, int y, int w, int h, Component hint) {
        super(font, x + PAD_X, y + (h - 8) / 2, w - PAD_X * 2, 9, hint);
        this.boxX = x;
        this.boxY = y;
        this.boxW = w;
        this.boxH = h;
        setBordered(false);
        setTextColor(MkTheme.TEXT);
        setTextShadow(false);
        setMaxLength(32767);
        if (hint != null && !hint.getString().isEmpty()) setHint(hint.copy().withColor(MkTheme.FAINT));
    }

    public static MkField mono(int x, int y, int w, int h, Component hint) {
        return new MkField(MkTheme.mono(), x, y, w, h, hint);
    }

    public MkField value(String v) {
        setValue(v == null ? "" : v);
        moveCursorToStart(false);
        return this;
    }

    public MkField onChange(Consumer<String> c) {
        setResponder(c);
        return this;
    }

    /** Red border (validation error). */
    public void setInvalid(boolean v) {
        invalid = v;
    }

    public void moveBox(int x, int y) {
        boxX = x;
        boxY = y;
        setX(x + PAD_X);
        setY(y + (boxH - 8) / 2);
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        if (!isVisible()) return;
        g.fill(boxX, boxY, boxX + boxW, boxY + boxH, MkTheme.INPUT);
        int border = invalid ? MkTheme.RED : isFocused() ? MkTheme.GREEN_HOVER : isHovered() ? MkTheme.LINE_STRONG : MkTheme.LINE;
        MkTheme.border(g, boxX, boxY, boxW, boxH, border);
        super.extractWidgetRenderState(g, mouseX, mouseY, a);
    }

    @Override
    public boolean isMouseOver(double mx, double my) {
        return isVisible() && mx >= boxX && my >= boxY && mx < boxX + boxW && my < boxY + boxH;
    }

    public int boxX() {
        return boxX;
    }

    public int boxY() {
        return boxY;
    }

    public int boxW() {
        return boxW;
    }

    public int boxH() {
        return boxH;
    }
}
