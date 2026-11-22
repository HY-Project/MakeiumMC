package com.hyproj.makeium.neoforge;

import java.nio.file.Path;

import com.hyproj.makeium.net.MkPayload;
import com.hyproj.makeium.platform.Platform;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.PacketDistributor;

public final class NeoForgePlatform implements Platform {
    @Override
    public String loaderName() {
        return "neoforge";
    }

    @Override
    public String loaderVersion() {
        return modVersion("neoforge");
    }

    @Override
    public String modVersion(String modId) {
        return ModList.get().getModContainerById(modId).map(c -> c.getModInfo().getVersion().toString()).orElse("?");
    }

    @Override
    public Path gameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isDedicatedServer() {
        return FMLEnvironment.getDist() == Dist.DEDICATED_SERVER;
    }

    @Override
    public void sendToServer(MkPayload.C2S payload) {
        ClientPacketDistributor.sendToServer(payload);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, MkPayload.S2C payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public boolean hasMakeium(ServerPlayer player) {
        return player.connection != null && player.connection.hasChannel(MkPayload.S2C_TYPE);
    }

    @Override
    public boolean serverHasMakeium() {
        var c = net.minecraft.client.Minecraft.getInstance().getConnection();
        return c != null && c.hasChannel(MkPayload.C2S_TYPE);
    }
}
