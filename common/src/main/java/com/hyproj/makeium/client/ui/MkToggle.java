package com.hyproj.makeium.client.ui;

import java.util.function.Consumer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** A switch (24×12 pill, square-cornered) followed by its label. */
public class MkToggle extends AbstractButton {
    private boolean value;
    private final Consumer<Boolean> listener;

    public MkToggle(int x, int y, int w, Component label, boolean value, Consumer<Boolean> listener) {
        super(x, y, w, 16, label);
        this.value = value;
        this.listener = listener;
    }

    public boolean value() {
        return value;
    }

    public void setValue(boolean v) {
        value = v;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        value = !value;
        if (listener != null) listener.accept(value);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int x = getX(), y = getY() + 2;
        boolean hot = isHoveredOrFocused();
        g.fill(x, y, x + 24, y + 12, value ? MkTheme.GREEN : MkTheme.SURFACE);
        MkTheme.border(g, x, y, 24, 12, value ? MkTheme.GREEN_HOVER : hot ? MkTheme.LINE_STRONG : MkTheme.LINE);
        int knob = value ? x + 14 : x + 2;
        g.fill(knob, y + 2, knob + 8, y + 10, 0xFFFFFFFF);
        MkTheme.text(g, MkTheme.clip(MkTheme.font(), getMessage(), getWidth() - 30), x + 30, getY() + 4, hot ? MkTheme.TEXT : MkTheme.MUTED);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
