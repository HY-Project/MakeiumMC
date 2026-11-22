package com.hyproj.makeium.mixin.client;

import com.hyproj.makeium.client.MakeiumClient;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Lets Makeium swap a screen as it opens (the command block editor, dev screens). */
@Mixin(Gui.class)
public abstract class GuiSetScreenMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen makeium$redirect(Screen next) {
        return MakeiumClient.redirect(next);
    }
}
