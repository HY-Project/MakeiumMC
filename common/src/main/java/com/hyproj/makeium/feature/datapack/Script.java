package com.hyproj.makeium.feature.datapack;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;

/**
 * A stack of blocks on the canvas. When the first block is an event (hat) the script compiles to a function; loose
 * stacks without an event are kept (Scratch-style) but not compiled.
 */
public final class Script {
    public double x, y;
    public final List<Node> nodes = new ArrayList<>();

    public Script(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public Node head() {
        return nodes.isEmpty() ? null : nodes.get(0);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("x", x);
        o.addProperty("y", y);
        o.add("blocks", Node.toJson(nodes));
        return o;
    }

    public static Script fromJson(JsonObject o) {
        Script s = new Script(o.has("x") ? o.get("x").getAsDouble() : 0, o.has("y") ? o.get("y").getAsDouble() : 0);
        if (o.has("blocks")) s.nodes.addAll(Node.listFromJson(o.getAsJsonArray("blocks")));
        return s;
    }
}
