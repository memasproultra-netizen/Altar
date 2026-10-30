package com.rpserver.altarmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

/** Вызывается только на клиенте. */
public class ClientHooks {
    public static CompoundTag state = new CompoundTag();

    public static void handleState(CompoundTag tag) {
        state = tag;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AdminScreen s) {
            s.refresh();
        } else if (tag.getBoolean("open")) {
            mc.setScreen(new AdminScreen());
        }
    }
}
