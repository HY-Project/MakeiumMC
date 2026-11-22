package com.hyproj.makeium.platform;

import java.nio.file.Path;

import com.hyproj.makeium.net.MkPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * What common code needs from the mod loader. One universal jar carries both implementations, so the loader is
 * detected by class presence and only that implementation is loaded (the other one would fail to link).
 */
public interface Platform {
    /** "neoforge" or "fabric". */
    String loaderName();

    String loaderVersion();

    /** The version string of a loaded mod, or "?" when it is not loaded. */
    String modVersion(String modId);

    Path gameDir();

    Path configDir();

    boolean isDedicatedServer();

    /** Client → server. Only call on the client. */
    void sendToServer(MkPayload.C2S payload);

    /** Server → client. Silently dropped when the player's client has no Makeium. */
    void sendToPlayer(ServerPlayer player, MkPayload.S2C payload);

    /** Whether this player's client can receive Makeium payloads (has the mod). */
    boolean hasMakeium(ServerPlayer player);

    /** Client side: whether the connected server accepts Makeium payloads. */
    boolean serverHasMakeium();

    static Platform get() {
        return Holder.INSTANCE;
    }

    final class Holder {
        private static final Platform INSTANCE = detect();

        private static Platform detect() {
            String impl = present("net.neoforged.fml.ModList") ? "com.hyproj.makeium.neoforge.NeoForgePlatform"
                    : present("net.fabricmc.loader.api.FabricLoader") ? "com.hyproj.makeium.fabric.FabricPlatform"
                    : null;
            if (impl == null) throw new IllegalStateException("Makeium: running on neither NeoForge nor Fabric");
            try {
                return (Platform) Class.forName(impl, true, Platform.class.getClassLoader()).getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Makeium: could not create " + impl, e);
            }
        }

        private static boolean present(String cls) {
            try {
                Class.forName(cls, false, Platform.class.getClassLoader());
                return true;
            } catch (ClassNotFoundException | LinkageError e) {
                return false;
            }
        }

        private Holder() {}
    }
}
