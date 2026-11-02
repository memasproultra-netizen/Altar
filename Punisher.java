package com.rpserver.altarmod;

import com.rpserver.altarmod.data.AltarData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Таймер алтаря и все виды наказаний («злость алтаря»). */
public class Punisher {

    /** Игрок -> сколько тиков осталось до взрыва. Не сохраняется. */
    private static final Map<UUID, Integer> EXPLOSIONS = new HashMap<>();
    private static int lastWarn = -1;

    private static final ResourceLocation HEALTH_MOD =
            ResourceLocation.fromNamespaceAndPath(AltarMod.MODID, "altar_wrath");

    public static void resetWarn() {
        lastWarn = -1;
    }

    /** Вызывается каждый серверный тик. */
    public static void tick(MinecraftServer server) {
        if (!EXPLOSIONS.isEmpty()) {
            Iterator<Map.Entry<UUID, Integer>> it = EXPLOSIONS.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Integer> e = it.next();
                ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
                if (p == null || !p.isAlive()) {
                    it.remove();
                    continue;
                }
                int left = e.getValue() - 1;
                if (left <= 0) {
                    it.remove();
                    explode(p, AltarData.get(server).pExplodePower);
                } else {
                    e.setValue(left);
                    if (left % 20 == 0) {
                        p.displayClientMessage(Component.literal("§4§lВзрыв через " + (left / 20) + " с!"), true);
                    }
                }
            }
        }

        if (server.getTickCount() % 20 != 0) return;
        AltarData d = AltarData.get(server);
        if (!d.active || d.deadline < 0) return;

        long left = d.deadline - server.overworld().getGameTime();
        if (left <= 0) {
            expire(server, d);
            return;
        }
        int secs = (int) ((left + 19) / 20);
        if ((secs == 300 || secs == 60 || secs == 30 || secs == 10) && secs != lastWarn) {
            lastWarn = secs;
            String t = secs >= 60 ? (secs / 60) + " мин." : secs + " с.";
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("§5[Алтарь] §cДо гнева алтаря осталось: §f" + t), false);
        }
    }

    /** Время вышло, а подношение не принесено. */
    public static void expire(MinecraftServer server, AltarData d) {
        d.active = false;
        d.deadline = -1L;
        d.setDirty();
        PlayerList pl = server.getPlayerList();
        pl.broadcastSystemMessage(Component.literal(
                "§4§l[Алтарь] §r§cВремя вышло. Дары не принесены, алтарь гневается..."), false);

        List<ServerPlayer> victims = new ArrayList<>();
        if (d.mode.equals("player")) {
            if (d.targetId != null) {
                ServerPlayer t = pl.getPlayer(d.targetId);
                if (t != null) {
                    victims.add(t);
                } else {
                    d.pending.add(d.targetId);
                    d.setDirty();
                    pl.broadcastSystemMessage(Component.literal("§5[Алтарь] §7" + d.targetName
                            + " скрылся от гнева, но кара настигнет его при возвращении."), false);
                }
            }
        } else {
            for (ServerPlayer sp : pl.getPlayers()) {
                if (d.scopeAll || !d.contributors.contains(sp.getUUID())) victims.add(sp);
            }
        }

        if (!d.anyPunishment()) {
            pl.broadcastSystemMessage(Component.literal("§7[Алтарь] Но наказания не назначены."), false);
            return;
        }
        if (victims.isEmpty()) return;

        StringBuilder names = new StringBuilder();
        for (ServerPlayer v : victims) {
            if (names.length() > 0) names.append(", ");
            names.append(v.getGameProfile().getName());
            punish(v, d);
        }
        pl.broadcastSystemMessage(Component.literal("§4[Алтарь] §cЗлость алтаря обрушилась на: §f" + names), false);
    }

    /** Применяет все включённые наказания к игроку. */
    public static void punish(ServerPlayer p, AltarData d) {
        if (p.isSpectator()) return;
        p.sendSystemMessage(Component.literal("§4§l[Алтарь] §r§cТебя постигла злость алтаря!"));

        if (d.pHealth) {
            addHealthLoss(p, d, d.pHealthN);
            p.sendSystemMessage(Component.literal("§c • Максимум здоровья уменьшен на " + d.pHealthN + " ♥"));
        }
        if (d.pLightning) {
            ServerLevel sl = p.serverLevel();
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(sl);
            if (bolt != null) {
                bolt.moveTo(p.getX(), p.getY(), p.getZ());
                sl.addFreshEntity(bolt);
            }
        }
        if (d.pWipe) {
            int n = wipeInventory(p, d.pWipeN);
            p.sendSystemMessage(Component.literal("§c • Алтарь стёр " + n + " стеков из твоего инвентаря"));
        }
        if (d.pExplode) {
            EXPLOSIONS.put(p.getUUID(), Math.max(1, d.pExplodeDelay) * 20);
            p.sendSystemMessage(Component.literal("§c • Ты взорвёшься через " + d.pExplodeDelay + " с!"));
        }
    }

    public static void explode(ServerPlayer p, int power) {
        ServerLevel sl = p.serverLevel();
        sl.explode(null, p.getX(), p.getY(), p.getZ(), power, Level.ExplosionInteraction.NONE);
    }

    /** Стирает percent% непустых стеков (инвентарь, броня, вторая рука). Возвращает, сколько стёрто. */
    public static int wipeInventory(ServerPlayer p, int percent) {
        Inventory inv = p.getInventory();
        List<int[]> slots = new ArrayList<>();
        for (int i = 0; i < inv.items.size(); i++) {
            if (!inv.items.get(i).isEmpty()) slots.add(new int[]{0, i});
        }
        for (int i = 0; i < inv.armor.size(); i++) {
            if (!inv.armor.get(i).isEmpty()) slots.add(new int[]{1, i});
        }
        for (int i = 0; i < inv.offhand.size(); i++) {
            if (!inv.offhand.get(i).isEmpty()) slots.add(new int[]{2, i});
        }
        Collections.shuffle(slots, new Random(p.getRandom().nextLong()));
        int cnt = (int) Math.ceil(slots.size() * Math.max(0, Math.min(100, percent)) / 100.0);
        for (int k = 0; k < cnt && k < slots.size(); k++) {
            int[] s = slots.get(k);
            if (s[0] == 0) inv.items.set(s[1], ItemStack.EMPTY);
            else if (s[0] == 1) inv.armor.set(s[1], ItemStack.EMPTY);
            else inv.offhand.set(s[1], ItemStack.EMPTY);
        }
        inv.setChanged();
        p.inventoryMenu.broadcastChanges();
        return cnt;
    }

    public static void addHealthLoss(ServerPlayer p, AltarData d, int hearts) {
        int cur = d.healthLost.getOrDefault(p.getUUID(), 0);
        d.healthLost.put(p.getUUID(), Math.min(9, cur + hearts));
        d.setDirty();
        applyHealth(p, d);
    }

    /** Держит максимум здоровья в соответствии с отнятыми сердцами (переживает смерть и перезаход). */
    public static void applyHealth(ServerPlayer p, AltarData d) {
        AttributeInstance inst = p.getAttribute(Attributes.MAX_HEALTH);
        if (inst == null) return;
        int lost = d.healthLost.getOrDefault(p.getUUID(), 0);
        AttributeModifier cur = inst.getModifier(HEALTH_MOD);
        if (lost <= 0) {
            if (cur != null) inst.removeModifier(HEALTH_MOD);
            return;
        }
        double amount = -2.0 * lost;
        if (cur != null && cur.amount() == amount) return;
        inst.removeModifier(HEALTH_MOD);
        inst.addPermanentModifier(new AttributeModifier(HEALTH_MOD, amount, AttributeModifier.Operation.ADD_VALUE));
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    /** Кара, отложенная до возвращения игрока. */
    public static void onLogin(ServerPlayer p) {
        AltarData d = AltarData.get(p.server);
        if (d.pending.remove(p.getUUID())) {
            d.setDirty();
            p.sendSystemMessage(Component.literal("§4[Алтарь] §cПока тебя не было, алтарь потерял терпение."));
            punish(p, d);
        }
    }

    /** Большой заголовок по центру экрана у всех игроков. */
    public static void titleAll(MinecraftServer server, String title, String subtitle) {
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            sp.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            sp.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle)));
            sp.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title)));
        }
    }
}
