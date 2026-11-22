package com.hyproj.makeium.client;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.hyproj.makeium.net.Net;
import net.minecraft.nbt.CompoundTag;

/**
 * Server replies routed to whoever is listening right now (usually the open screen). A screen calls {@link #on} in
 * init(); a newer listener for the same op replaces the older one.
 */
public final class Replies {
    private static final Map<String, Consumer<CompoundTag>> LISTENERS = new HashMap<>();

    private Replies() {}

    /** Declares {@code op} as a reply channel (once, from MakeiumClient.init). */
    static void channel(String op) {
        Net.onClient(op, d -> {
            Consumer<CompoundTag> l = LISTENERS.get(op);
            if (l != null) l.accept(d);
        });
    }

    public static void on(String op, Consumer<CompoundTag> listener) {
        LISTENERS.put(op, listener);
    }

    public static void off(String op) {
        LISTENERS.remove(op);
    }
}
