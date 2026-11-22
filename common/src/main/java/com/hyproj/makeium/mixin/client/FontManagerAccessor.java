package com.hyproj.makeium.mixin.client;

import java.util.Map;

import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Every loaded font id (vanilla + resource packs), for the display editor's font picker. */
@Mixin(FontManager.class)
public interface FontManagerAccessor {
    @Accessor("fontSets")
    Map<Identifier, FontSet> makeium$fontSets();
}
