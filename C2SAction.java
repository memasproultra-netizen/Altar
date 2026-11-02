package com.rpserver.altarmod.net;

import com.rpserver.altarmod.AdminActions;
import com.rpserver.altarmod.AltarMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Действие админа: клиент -> сервер. Права проверяются на сервере. */
public record C2SAction(String action, String a, String b, int n) implements CustomPacketPayload {

    public static final Type<C2SAction> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AltarMod.MODID, "action"));

    public static final StreamCodec<FriendlyByteBuf, C2SAction> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, C2SAction::action,
            ByteBufCodecs.STRING_UTF8, C2SAction::a,
            ByteBufCodecs.STRING_UTF8, C2SAction::b,
            ByteBufCodecs.VAR_INT, C2SAction::n,
            C2SAction::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SAction msg, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer p) {
            AdminActions.handle(p, msg.action(), msg.a(), msg.b(), msg.n());
        }
    }
}
