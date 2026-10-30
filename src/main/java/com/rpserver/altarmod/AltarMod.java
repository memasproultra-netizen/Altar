package com.rpserver.altarmod;

import com.rpserver.altarmod.block.AltarBlock;
import com.rpserver.altarmod.net.Net;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.commands.Commands;

@Mod(AltarMod.MODID)
public class AltarMod {
    public static final String MODID = "altarmod";

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    /** Неразрушаем в выживании; ломается только в креативе. */
    public static final DeferredBlock<Block> ALTAR = BLOCKS.registerBlock("altar", AltarBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(-1.0F, 3600000.0F)
                    .lightLevel(s -> 10));

    public static final DeferredItem<BlockItem> ALTAR_ITEM = ITEMS.registerSimpleBlockItem("altar", ALTAR);

    public AltarMod(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(AltarMod::addCreative);
        modBus.addListener(Net::register);
    }

    private static void addCreative(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            e.accept(ALTAR_ITEM.get());
        }
    }

    /** Серверные события (игровая шина). */
    @EventBusSubscriber(modid = MODID)
    public static class GameEvents {

        // Вечные эффекты: переживают смерть, молоко и перезаход.
        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post e) {
            if (e.getEntity() instanceof ServerPlayer sp && sp.tickCount % 20 == 0) {
                AdminActions.applyEffects(sp, false);
            }
        }

        @SubscribeEvent
        public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
            if (e.getEntity() instanceof ServerPlayer sp) AdminActions.applyEffects(sp, true);
        }

        @SubscribeEvent
        public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
            if (e.getEntity() instanceof ServerPlayer sp) AdminActions.applyEffects(sp, true);
        }

        // Команда /altaradmin открывает GUI (то же, что клавиша K).
        @SubscribeEvent
        public static void onCommands(RegisterCommandsEvent e) {
            e.getDispatcher().register(Commands.literal("altaradmin")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> {
                        ServerPlayer p = ctx.getSource().getPlayerOrException();
                        AdminActions.sendState(p, true);
                        return 1;
                    }));
        }
    }
}
