package com.hyproj.makeium.client.ui;

import java.util.Locale;
import java.util.function.DoubleConsumer;

import net.minecraft.network.chat.Component;

/** Numeric input: accepts decimals (and a leading minus); turns red while the text is not a number. */
public class MkNumberField extends MkField {
    private DoubleConsumer listener;
    private double min = -Double.MAX_VALUE, max = Double.MAX_VALUE;
    private boolean integer;

    public MkNumberField(int x, int y, int w, int h, double value) {
        super(MkTheme.mono(), x, y, w, h, Component.empty());
        setNumber(value);
        setResponder(s -> {
            Double v = parse(s);
            setInvalid(v == null);
            if (v != null && listener != null) listener.accept(clamp(v));
        });
    }

    public MkNumberField range(double min, double max) {
        this.min = min;
        this.max = max;
        return this;
    }

    public MkNumberField integer() {
        this.integer = true;
        return this;
    }

    public MkNumberField onNumber(DoubleConsumer c) {
        this.listener = c;
        return this;
    }

    public void setNumber(double v) {
        DoubleConsumer l = listener;
        listener = null;
        setValue(format(v, integer));
        moveCursorToStart(false);
        listener = l;
    }

    public double number(double fallback) {
        Double v = parse(getValue());
        return v == null ? fallback : clamp(v);
    }

    private double clamp(double v) {
        return Math.max(min, Math.min(max, v));
    }

    private static Double parse(String s) {
        if (s.isEmpty() || s.equals("-") || s.equals(".") || s.equals("-.")) return null;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String format(double v, boolean integer) {
        if (integer || v == Math.rint(v) && Math.abs(v) < 1e9) return Long.toString(Math.round(v));
        String s = String.format(Locale.ROOT, "%.4f", v);
        s = s.replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }
}
