package com.rpserver.altarmod.block;

import com.rpserver.altarmod.AdminActions;
import com.rpserver.altarmod.data.AltarData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Алтарь: ПКМ с предметом — принести подношение, ПКМ пустой рукой — посмотреть, что нужно. */
public class AltarBlock extends Block {

    public AltarBlock(Properties props) {
        super(props);
    }

    /** Предмет в руке: пробуем принять подношение. Если не подошло — показываем статус. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide || hand != InteractionHand.MAIN_HAND || held.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        ServerPlayer sp = (ServerPlayer) player;
        AltarData d = AltarData.get(sp.server);
        if (!d.active) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        String id = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        AltarData.Req r = d.find(id);
        if (r == null || r.progress >= r.count) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        int take = Math.min(held.getCount(), r.count - r.progress);
        held.shrink(take);
        r.progress += take;
        d.setDirty();
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 0.8F);
        sp.server.getPlayerList().broadcastSystemMessage(
                Component.literal("§5[Алтарь] §f" + sp.getGameProfile().getName() + " §7принёс §f" + take + " × ")
                        .append(AdminActions.itemName(id)), false);

        if (d.isComplete()) {
            d.active = false;
            d.setDirty();
            sp.server.getPlayerList().broadcastSystemMessage(
                    Component.literal("§5§l[Алтарь] §r§dПодношение принято. Алтарь удовлетворён."), false);
            level.playSound(null, pos, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.6F, 1.2F);
        }
        return ItemInteractionResult.SUCCESS;
    }

    /** Пустая рука (или не подошедший предмет): показать, что требует алтарь. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ServerPlayer sp = (ServerPlayer) player;
        AltarData d = AltarData.get(sp.server);

        if (!d.active) {
            sp.sendSystemMessage(Component.literal("§8Алтарь молчит. Он ничего не просит."));
            return InteractionResult.CONSUME;
        }
        MutableComponent msg = Component.literal("§5[Алтарь] §d" + d.message);
        for (AltarData.Req r : d.reqs) {
            msg.append(Component.literal("\n§7 • §f" + r.progress + "/" + r.count + " × "))
                    .append(AdminActions.itemName(r.item));
        }
        sp.sendSystemMessage(msg);
        return InteractionResult.CONSUME;
    }
}
