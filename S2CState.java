package com.rpserver.altarmod.net;

import com.rpserver.altarmod.AltarMod;
import com.rpserver.altarmod.client.ClientHooks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Состояние алтаря и игроков: сервер -> клиент админа. */
public record S2CState(CompoundTag tag) implements CustomPacketPayload {

    public static final Type<S2CState> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AltarMod.MODID, "state"));

    public static final StreamCodec<FriendlyByteBuf, S2CState> CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG, S2CState::tag,
            S2CState::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Выполняется только на клиенте (регистрируется через playToClient). */
    public static void handle(S2CState msg, IPayloadContext ctx) {
        ClientHooks.handleState(msg.tag());
    }
}
