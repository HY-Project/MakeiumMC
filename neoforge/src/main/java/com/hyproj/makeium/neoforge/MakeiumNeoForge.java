package com.hyproj.makeium.neoforge;

import com.hyproj.makeium.Makeium;
import com.hyproj.makeium.command.MakeiumCommand;
import com.hyproj.makeium.net.MkPayload;
import com.hyproj.makeium.net.Net;
import com.hyproj.makeium.server.ServerFeatures;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(Makeium.MOD_ID)
public final class MakeiumNeoForge {
    public MakeiumNeoForge(IEventBus modBus, ModContainer container) {
        Makeium.init();
        modBus.addListener((RegisterPayloadHandlersEvent e) -> {
            // optional(): vanilla clients may still join a server running Makeium.
            PayloadRegistrar r = e.registrar("1").optional();
            r.playToServer(MkPayload.C2S_TYPE, MkPayload.C2S_CODEC, (p, ctx) -> {
                if (ctx.player() instanceof ServerPlayer sp) Net.receiveOnServer(sp, p);
            });
            r.playToClient(MkPayload.S2C_TYPE, MkPayload.S2C_CODEC, (p, ctx) -> Net.receiveOnClient(p));
        });
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> MakeiumCommand.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> ServerFeatures.tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> Net.forget(e.getEntity().getUUID()));
    }
}
