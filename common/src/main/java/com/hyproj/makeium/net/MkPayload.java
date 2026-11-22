package com.hyproj.makeium.net;

import com.hyproj.makeium.Makeium;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Makeium's only two payload types. Each carries one chunk of a gzip-compressed NBT message addressed by {@code op};
 * {@link Net} splits and reassembles them so a message may be larger than a single custom payload.
 */
public final class MkPayload {
    private MkPayload() {}

    public static final CustomPacketPayload.Type<C2S> C2S_TYPE = new CustomPacketPayload.Type<>(Makeium.id("c2s"));
    public static final CustomPacketPayload.Type<S2C> S2C_TYPE = new CustomPacketPayload.Type<>(Makeium.id("s2c"));

    public static final StreamCodec<ByteBuf, C2S> C2S_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(128), C2S::op,
            ByteBufCodecs.VAR_INT, C2S::transfer,
            ByteBufCodecs.VAR_INT, C2S::index,
            ByteBufCodecs.VAR_INT, C2S::count,
            ByteBufCodecs.BYTE_ARRAY, C2S::data,
            C2S::new);

    public static final StreamCodec<ByteBuf, S2C> S2C_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(128), S2C::op,
            ByteBufCodecs.VAR_INT, S2C::transfer,
            ByteBufCodecs.VAR_INT, S2C::index,
            ByteBufCodecs.VAR_INT, S2C::count,
            ByteBufCodecs.BYTE_ARRAY, S2C::data,
            S2C::new);

    public record C2S(String op, int transfer, int index, int count, byte[] data) implements CustomPacketPayload {
        @Override
        public Type<C2S> type() {
            return C2S_TYPE;
        }
    }

    public record S2C(String op, int transfer, int index, int count, byte[] data) implements CustomPacketPayload {
        @Override
        public Type<S2C> type() {
            return S2C_TYPE;
        }
    }
}
