package com.hyproj.makeium.fabric;

import com.hyproj.makeium.Makeium;
import com.hyproj.makeium.command.MakeiumCommand;
import com.hyproj.makeium.net.MkPayload;
import com.hyproj.makeium.net.Net;
import com.hyproj.makeium.server.ServerFeatures;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class MakeiumFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Makeium.init();
        PayloadTypeRegistry.serverboundPlay().register(MkPayload.C2S_TYPE, MkPayload.C2S_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MkPayload.S2C_TYPE, MkPayload.S2C_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(MkPayload.C2S_TYPE, (p, ctx) -> Net.receiveOnServer(ctx.player(), p));
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, env) -> MakeiumCommand.register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(ServerFeatures::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Net.forget(handler.getPlayer().getUUID()));
    }
}
