package com.rpserver.altarmod.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.rpserver.altarmod.AltarMod;
import com.rpserver.altarmod.net.C2SAction;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public class ClientEvents {

    public static final KeyMapping OPEN_KEY = new KeyMapping(
            "key.altarmod.open_admin",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.altarmod");

    @EventBusSubscriber(modid = AltarMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModBus {
        @SubscribeEvent
        public static void onKeys(RegisterKeyMappingsEvent e) {
            e.register(OPEN_KEY);
        }
    }

    @EventBusSubscriber(modid = AltarMod.MODID, value = Dist.CLIENT)
    public static class GameBus {
        @SubscribeEvent
        public static void onTick(ClientTickEvent.Post e) {
            Minecraft mc = Minecraft.getInstance();
            while (OPEN_KEY.consumeClick()) {
                if (mc.player != null && mc.screen == null) {
                    // Права проверяет сервер: у не-операторов GUI не откроется.
                    PacketDistributor.sendToServer(new C2SAction("open", "", "", 0));
                }
            }
        }
    }
}
