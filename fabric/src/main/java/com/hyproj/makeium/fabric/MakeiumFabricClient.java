package com.hyproj.makeium.fabric;

import com.hyproj.makeium.Makeium;
import com.hyproj.makeium.client.MakeiumClient;
import com.hyproj.makeium.net.MkPayload;
import com.hyproj.makeium.net.Net;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;

public final class MakeiumFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MakeiumClient.init(KeyMapping.Category.register(Makeium.id("main")));
        for (KeyMapping k : MakeiumClient.keys()) KeyMappingHelper.registerKeyMapping(k);
        ClientPlayNetworking.registerGlobalReceiver(MkPayload.S2C_TYPE, (p, ctx) -> Net.receiveOnClient(p));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> MakeiumClient.tick());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> MakeiumClient.onJoin());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> MakeiumClient.onLeave());
    }
}
