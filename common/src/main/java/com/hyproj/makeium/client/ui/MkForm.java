package com.hyproj.makeium.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.joml.Vector3f;

/**
 * Lays out a vertical form inside a column: captions, labelled fields, rows of small fields. Widgets are added to
 * the screen as they are placed; labels are remembered and drawn by {@link #draw}.
 */
public class MkForm {
    private record Label(Component text, int x, int y, boolean caption) {}

    private final MkScreen screen;
    private final java.util.function.Function<net.minecraft.client.gui.components.AbstractWidget, net.minecraft.client.gui.components.AbstractWidget> adder;
    public final int x, w;
    public int y;
    private final List<Label> labels = new ArrayList<>();

    public MkForm(MkScreen screen, java.util.function.Function<net.minecraft.client.gui.components.AbstractWidget, net.minecraft.client.gui.components.AbstractWidget> adder, int x, int y, int w) {
        this.screen = screen;
        this.adder = adder;
        this.x = x;
        this.y = y;
        this.w = w;
    }

    @SuppressWarnings("unchecked")
    public <T extends net.minecraft.client.gui.components.AbstractWidget> T add(T widget) {
        return (T) adder.apply(widget);
    }

    public MkForm caption(Component c) {
        y += 4;
        labels.add(new Label(c, x, y, true));
        y += 14;
        return this;
    }

    public MkForm label(Component c) {
        labels.add(new Label(c, x, y, false));
        y += 12;
        return this;
    }

    public MkForm gap(int px) {
        y += px;
        return this;
    }

    public MkField field(Component label, String value, Consumer<String> onChange) {
        label(label);
        MkField f = add(new MkField(x, y, w, 18, Component.empty()));
        f.value(value);
        f.onChange(onChange);
        y += 22;
        return f;
    }

    public MkNumberField number(Component label, double value, DoubleConsumer onChange) {
        label(label);
        MkNumberField f = add(new MkNumberField(x, y, w, 18, value));
        f.onNumber(onChange);
        y += 22;
        return f;
    }

    /** Label on the left, number field on the right (one row). */
    public MkNumberField numberInline(Component label, double value, int fieldW, DoubleConsumer onChange) {
        labels.add(new Label(label, x, y + 5, false));
        MkNumberField f = add(new MkNumberField(x + w - fieldW, y, fieldW, 18, value));
        f.onNumber(onChange);
        y += 22;
        return f;
    }

    /** Three fields X Y Z on one row. */
    public MkNumberField[] vec3(Component label, Vector3f v, Consumer<Vector3f> onChange) {
        label(label);
        int fw = (w - 8) / 3;
        MkNumberField[] out = new MkNumberField[3];
        Vector3f cur = new Vector3f(v);
        String[] axes = {"X", "Y", "Z"};
        for (int i = 0; i < 3; i++) {
            final int axis = i;
            int fx = x + i * (fw + 4);
            labels.add(new Label(Component.literal(axes[i]).withColor(i == 0 ? 0xFFE06060 : i == 1 ? 0xFF60E060 : 0xFF6090E0), fx + 3, y + 5, false));
            MkNumberField f = add(new MkNumberField(fx + 10, y, fw - 10, 18, cur.get(i)));
            f.onNumber(d -> {
                cur.setComponent(axis, (float) d);
                onChange.accept(new Vector3f(cur));
            });
            out[i] = f;
        }
        y += 22;
        return out;
    }

    public MkToggle toggle(Component label, boolean value, Consumer<Boolean> onChange) {
        MkToggle t = add(new MkToggle(x, y, w, label, value, onChange));
        y += 20;
        return t;
    }

    public MkSlider slider(Component label, double min, double max, double step, double value, DoubleConsumer onChange) {
        MkSlider s = add(new MkSlider(x, y, w, label, min, max, step, value, onChange));
        y += 26;
        return s;
    }

    public <T> MkDropdown<T> dropdown(Component label, List<T> options, T value, java.util.function.Function<T, Component> names, Consumer<T> onChange) {
        label(label);
        MkDropdown<T> d = add(new MkDropdown<>(screen, x, y, w, 18, options, value, names, onChange));
        y += 22;
        return d;
    }

    public MkColorButton color(Component label, int argb, boolean alpha, java.util.function.IntConsumer onChange) {
        label(label);
        MkColorButton c = add(new MkColorButton(screen, x, y, w, 18, argb, alpha, onChange));
        y += 22;
        return c;
    }

    public MkButton button(Component label, MkButton.Kind kind, Runnable action) {
        MkButton b = add(new MkButton(x, y, w, 20, label, kind, action));
        y += 24;
        return b;
    }

    /** Buttons sharing one row equally. */
    public MkButton[] buttons(MkButton.Kind kind, Component[] labels, Runnable[] actions) {
        int n = labels.length;
        int bw = (w - (n - 1) * 4) / n;
        MkButton[] out = new MkButton[n];
        for (int i = 0; i < n; i++) {
            out[i] = add(new MkButton(x + i * (bw + 4), y, bw, 20, labels[i], kind, actions[i]));
        }
        y += 24;
        return out;
    }

    public void draw(GuiGraphicsExtractor g) {
        for (Label l : labels) {
            if (l.caption) {
                MkTheme.caption(g, l.text, l.x, l.y);
                MkTheme.hline(g, l.x + MkTheme.bold().width(l.text) + 6, x + w, l.y + 4, MkTheme.LINE);
            } else {
                MkTheme.text(g, MkTheme.clip(MkTheme.font(), l.text, w), l.x, l.y, MkTheme.MUTED);
            }
        }
    }
}
