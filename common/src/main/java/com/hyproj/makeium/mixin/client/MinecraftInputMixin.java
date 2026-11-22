package com.hyproj.makeium.mixin.client;

import com.hyproj.makeium.client.tool.ToolController;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** While a Makeium tool is held, right/left/middle click go to the tool instead of vanilla use/attack/pick. */
@Mixin(Minecraft.class)
public abstract class MinecraftInputMixin {
    @Shadow
    private int rightClickDelay;

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void makeium$use(CallbackInfo ci) {
        if (ToolController.onUse()) {
            // Vanilla sets this first thing in startUseItem; without it handleKeybinds calls startUseItem again in the
            // same tick (keyUse.isDown() && rightClickDelay == 0), so one click placed two displays.
            rightClickDelay = 4;
            ci.cancel();
        }
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void makeium$attack(CallbackInfoReturnable<Boolean> cir) {
        if (ToolController.onAttack()) cir.setReturnValue(false);
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void makeium$continueAttack(boolean down, CallbackInfo ci) {
        if (ToolController.holdingTool()) ci.cancel();
    }

    @Inject(method = "pickBlockOrEntity", at = @At("HEAD"), cancellable = true)
    private void makeium$pick(CallbackInfo ci) {
        if (ToolController.onPick()) ci.cancel();
    }
}
