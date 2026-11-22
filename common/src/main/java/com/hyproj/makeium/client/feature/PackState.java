package com.hyproj.makeium.client.feature;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.hyproj.makeium.Makeium;
import com.hyproj.makeium.client.Replies;
import com.hyproj.makeium.net.Net;
import com.hyproj.makeium.server.ServerFeatures;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.TagParser;

/**
 * Feature screens (regions, NPCs, cutscenes, links) keep their editable state inside the pack they generate, as
 * {@code makeium/state.snbt}, next to the vanilla files compiled from it. Loading reads that file back.
 */
public final class PackState {
    public static final String FILE = "makeium/state.snbt";

    private PackState() {}

    /** Asks the server for the pack; {@code onState} gets its saved state (empty when the pack is new). */
    public static void load(String pack, Consumer<CompoundTag> onState) {
        Replies.on("dp/load", d -> {
            if (!pack.equals(d.getStringOr("name", ""))) return;
            Map<String, String> files = ServerFeatures.fromTag(d.getCompoundOrEmpty("files"));
            String snbt = files.get(FILE);
            CompoundTag state = new CompoundTag();
            if (snbt != null) {
                try {
                    state = TagParser.parseCompoundFully(snbt);
                } catch (CommandSyntaxException e) {
                    Makeium.LOGGER.warn("Could not read {} of {}: {}", FILE, pack, e.getMessage());
                }
            }
            onState.accept(state);
        });
        CompoundTag d = new CompoundTag();
        d.putString("name", pack);
        Net.sendToServer("dp/load", d);
    }

    /** Writes the compiled files plus the state, deleting the previously generated files no longer produced. */
    public static void save(String pack, String description, Map<String, String> compiled, CompoundTag state) {
        Map<String, String> all = new LinkedHashMap<>(compiled);
        ListTag gen = new ListTag();
        for (String k : compiled.keySet()) gen.add(StringTag.valueOf(k));
        ListTag deleted = new ListTag();
        for (var t : state.getListOrEmpty("generated")) {
            String old = t.asString().orElse("");
            if (!old.isEmpty() && !compiled.containsKey(old)) deleted.add(StringTag.valueOf(old));
        }
        state.put("generated", gen);
        all.put(FILE, NbtUtils.structureToSnbt(state));
        CompoundTag d = new CompoundTag();
        d.putString("name", pack);
        d.putString("description", description);
        d.put("files", ServerFeatures.toTag(all));
        d.put("deleted", deleted);
        Replies.on("dp/saved", r -> {
            if (!pack.equals(r.getStringOr("name", ""))) return;
            var problems = r.getListOrEmpty("problems");
            if (r.contains("error")) {
                com.hyproj.makeium.client.ui.MkToast.error(net.minecraft.network.chat.Component.translatable("makeium.dp.save_failed", r.getStringOr("error", "")));
            } else if (!problems.isEmpty()) {
                for (var t : problems) Makeium.LOGGER.warn("{}: {}", pack, t.asString().orElse(""));
                com.hyproj.makeium.client.ui.MkToast.error(net.minecraft.network.chat.Component.translatable("makeium.dp.saved_problems", problems.size()));
            }
        });
        Net.sendToServer("dp/save", d);
    }

    public static void runCommand(String command) {
        CompoundTag d = new CompoundTag();
        d.putString("command", command);
        Net.sendToServer("cmd/run", d);
    }
}
