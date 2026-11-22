package com.hyproj.makeium.mixin;

import com.hyproj.makeium.server.Tools;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makeium tools are vanilla items (a sign, an item frame, ...); their vanilla use (placing the sign) must not happen.
 * The Makeium client handles the click itself and sends a Makeium message instead.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void makeium$toolUseOn(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (Tools.of(stack) != null) cir.setReturnValue(InteractionResult.FAIL);
    }

    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void makeium$toolUse(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (Tools.of(stack) != null) cir.setReturnValue(InteractionResult.FAIL);
    }
}
