package com.hyproj.makeium;

import com.hyproj.makeium.platform.Platform;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Makeium — an in-game toolkit for map makers: displays placed like blocks, a resource pack factory and a block-coding
 * datapack factory. Everything it produces is vanilla data, so finished maps run without the mod.
 */
public final class Makeium {
    public static final String MOD_ID = "makeium";
    public static final Logger LOGGER = LoggerFactory.getLogger("Makeium");

    private Makeium() {}

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /** Called once from each loader's common entrypoint. */
    public static void init() {
        LOGGER.info("Makeium {} on {} {}", Platform.get().modVersion(MOD_ID), Platform.get().loaderName(), Platform.get().loaderVersion());
        com.hyproj.makeium.server.ServerFeatures.init();
    }
}
