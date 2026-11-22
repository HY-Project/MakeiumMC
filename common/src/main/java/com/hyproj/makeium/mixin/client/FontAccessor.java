package com.hyproj.makeium.mixin.client;

import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The glyph provider behind Minecraft's font, so Makeium can build fonts that default to Noto Sans KR. */
@Mixin(Font.class)
public interface FontAccessor {
    @Accessor("provider")
    Font.Provider makeium$provider();
}
