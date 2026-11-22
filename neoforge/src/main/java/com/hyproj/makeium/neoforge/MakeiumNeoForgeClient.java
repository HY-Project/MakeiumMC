package com.hyproj.makeium.neoforge;

import com.hyproj.makeium.Makeium;
import com.hyproj.makeium.client.MakeiumClient;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Makeium.MOD_ID, dist = Dist.CLIENT)
public final class MakeiumNeoForgeClient {
    public MakeiumNeoForgeClient(IEventBus modBus) {
        KeyMapping.Category category = new KeyMapping.Category(Makeium.id("main"));
        MakeiumClient.init(category);
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            e.registerCategory(category);
            for (KeyMapping k : MakeiumClient.keys()) e.register(k);
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> MakeiumClient.tick());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn e) -> MakeiumClient.onJoin());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> MakeiumClient.onLeave());
    }
}
