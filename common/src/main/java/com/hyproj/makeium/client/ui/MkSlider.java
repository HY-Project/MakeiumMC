package com.hyproj.makeium.client.ui;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Label on the left, value on the right, a 4px track below with a green fill and a square thumb. */
public class MkSlider extends AbstractWidget {
    private final double min, max, step;
    private double value;
    private final DoubleConsumer listener;
    private DoubleFunction<String> format = v -> MkNumberField.format(v, false);

    public MkSlider(int x, int y, int w, Component label, double min, double max, double step, double value, DoubleConsumer listener) {
        super(x, y, w, 22, label);
        this.min = min;
        this.max = max;
        this.step = step;
        this.value = value;
        this.listener = listener;
    }

    public MkSlider format(DoubleFunction<String> f) {
        this.format = f;
        return this;
    }

    public double value() {
        return value;
    }

    public void setValue(double v) {
        value = Mth.clamp(v, min, max);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int x = getX(), y = getY(), w = getWidth();
        MkTheme.text(g, getMessage(), x, y, MkTheme.MUTED);
        MkTheme.textRight(g, Component.literal(format.apply(value)), x + w, y, MkTheme.TEXT);
        int ty = y + 14;
        g.fill(x, ty, x + w, ty + 4, MkTheme.SURFACE);
        float t = (float) ((value - min) / (max - min));
        int fx = x + Math.round((w - 6) * t);
        g.fill(x, ty, fx + 3, ty + 4, MkTheme.GREEN);
        boolean hot = isHoveredOrFocused();
        g.fill(fx, ty - 3, fx + 6, ty + 7, hot ? 0xFFFFFFFF : 0xFFE0E0E0);
    }

    private void setFromMouse(double mx) {
        double t = Mth.clamp((mx - getX() - 3) / (getWidth() - 6), 0, 1);
        double v = min + (max - min) * t;
        if (step > 0) v = Math.round(v / step) * step;
        v = Mth.clamp(v, min, max);
        if (v != value) {
            value = v;
            if (listener != null) listener.accept(v);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        setFromMouse(event.x());
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dx, double dy) {
        setFromMouse(event.x());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
