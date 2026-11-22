package com.hyproj.makeium.feature.datapack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** A placed block: its definition id, slot values, and (for C blocks) its body / else-body. */
public final class Node {
    public String def;
    public final Map<String, String> values = new LinkedHashMap<>();
    public final List<Node> body = new ArrayList<>();
    public final List<Node> elseBody = new ArrayList<>();
    /** Free-form note shown on the block (and written as a # comment). */
    public String comment = "";
    /** Disabled blocks are skipped by the compiler (shown greyed). */
    public boolean disabled;

    public Node(String def) {
        this.def = def;
    }

    /** A new node with the definition's default slot values. */
    public static Node create(BlockDef d) {
        Node n = new Node(d.id());
        for (SlotDef s : d.slots()) n.values.put(s.name(), s.def());
        return n;
    }

    public String value(String slot, String fallback) {
        String v = values.get(slot);
        return v == null ? fallback : v;
    }

    public Node deepCopy() {
        Node n = new Node(def);
        n.values.putAll(values);
        n.comment = comment;
        n.disabled = disabled;
        for (Node c : body) n.body.add(c.deepCopy());
        for (Node c : elseBody) n.elseBody.add(c.deepCopy());
        return n;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("block", def);
        JsonObject v = new JsonObject();
        values.forEach(v::addProperty);
        o.add("values", v);
        if (!comment.isEmpty()) o.addProperty("comment", comment);
        if (disabled) o.addProperty("disabled", true);
        if (!body.isEmpty()) o.add("body", toJson(body));
        if (!elseBody.isEmpty()) o.add("else", toJson(elseBody));
        return o;
    }

    public static JsonArray toJson(List<Node> list) {
        JsonArray a = new JsonArray();
        for (Node n : list) a.add(n.toJson());
        return a;
    }

    public static Node fromJson(JsonObject o) {
        Node n = new Node(o.has("block") ? o.get("block").getAsString() : "raw");
        if (o.has("values")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("values").entrySet()) n.values.put(e.getKey(), e.getValue().getAsString());
        }
        if (o.has("comment")) n.comment = o.get("comment").getAsString();
        if (o.has("disabled")) n.disabled = o.get("disabled").getAsBoolean();
        if (o.has("body")) n.body.addAll(listFromJson(o.getAsJsonArray("body")));
        if (o.has("else")) n.elseBody.addAll(listFromJson(o.getAsJsonArray("else")));
        return n;
    }

    public static List<Node> listFromJson(JsonArray a) {
        List<Node> out = new ArrayList<>();
        for (JsonElement e : a) if (e.isJsonObject()) out.add(fromJson(e.getAsJsonObject()));
        return out;
    }
}
