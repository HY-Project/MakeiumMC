package com.hyproj.makeium.client.display;

import java.util.ArrayList;
import java.util.List;

import com.hyproj.makeium.mixin.client.FontManagerAccessor;
import com.hyproj.makeium.mixin.client.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Every font the client has loaded — vanilla's (default, uniform, alt, illageralt) and each resource pack's
 * ({@code assets/<ns>/font/<name>.json}) — for the display editor. Makeium's own UI fonts are left out: a vanilla
 * client would not have them.
 */
public final class FontList {
    private FontList() {}

    public static List<Identifier> all() {
        var mgr = ((MinecraftAccessor) Minecraft.getInstance()).makeium$fontManager();
        List<Identifier> out = new ArrayList<>();
        for (Identifier id : ((FontManagerAccessor) mgr).makeium$fontSets().keySet()) {
            if (id.getNamespace().equals("makeium")) continue;
            if (id.getPath().startsWith("include/")) continue;
            out.add(id);
        }
        out.sort((a, b) -> {
            boolean av = a.getNamespace().equals("minecraft"), bv = b.getNamespace().equals("minecraft");
            if (av != bv) return av ? -1 : 1;
            return a.compareTo(b);
        });
        Identifier def = Identifier.withDefaultNamespace("default");
        out.remove(def);
        out.add(0, def);
        return out;
    }
}
