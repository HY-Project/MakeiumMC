package com.hyproj.makeium.fabric;

import java.nio.file.Path;

import com.hyproj.makeium.net.MkPayload;
import com.hyproj.makeium.platform.Platform;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

public final class FabricPlatform implements Platform {
    @Override
    public String loaderName() {
        return "fabric";
    }

    @Override
    public String loaderVersion() {
        return modVersion("fabricloader");
    }

    @Override
    public String modVersion(String modId) {
        return FabricLoader.getInstance().getModContainer(modId).map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
    }

    @Override
    public Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isDedicatedServer() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;
    }

    @Override
    public void sendToServer(MkPayload.C2S payload) {
        ClientPlayNetworking.send(payload);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, MkPayload.S2C payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public boolean hasMakeium(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, MkPayload.S2C_TYPE);
    }

    @Override
    public boolean serverHasMakeium() {
        return net.minecraft.client.Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(MkPayload.C2S_TYPE);
    }
}
