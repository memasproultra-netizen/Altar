package com.rpserver.altarmod;

import com.rpserver.altarmod.data.AltarData;
import com.rpserver.altarmod.data.PlayerInvContainer;
import com.rpserver.altarmod.net.S2CState;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Вся серверная логика админ-панели. Права: оператор уровня 2+. */
public class AdminActions {

    public static boolean isAdmin(ServerPlayer p) {
        return p.hasPermissions(2);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static Holder<MobEffect> effectHolder(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id.trim());
        if (rl == null) return null;
        return BuiltInRegistries.MOB_EFFECT.getHolder(rl).orElse(null);
    }

    public static void handle(ServerPlayer p, String action, String a, String b, int n) {
        if (!isAdmin(p)) {
            p.sendSystemMessage(Component.literal("§cУ тебя нет прав на админ-панель алтаря."));
            return;
        }
        MinecraftServer server = p.server;
        AltarData d = AltarData.get(server);
        boolean open = false;

        switch (action) {
            case "open" -> open = true;
            case "request" -> { }
            case "add_req" -> {
                ResourceLocation rl = ResourceLocation.tryParse(a.trim());
                if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl) || n < 1) {
                    p.sendSystemMessage(Component.literal("§cТакого предмета нет: " + a));
                } else {
                    AltarData.Req r = d.find(rl.toString());
                    if (r == null) {
                        r = new AltarData.Req();
                        r.item = rl.toString();
                        d.reqs.add(r);
                    }
                    r.count = n;
                    d.setDirty();
                }
            }
            case "del_req" -> {
                d.reqs.removeIf(r -> r.item.equals(a));
                d.setDirty();
            }
            case "clear_req" -> {
                d.reqs.clear();
                d.active = false;
                d.deadline = -1L;
                d.setDirty();
            }
            case "reset_progress" -> {
                d.reqs.forEach(r -> r.progress = 0);
                d.contributors.clear();
                d.setDirty();
            }
            case "broadcast" -> {
                if (d.reqs.isEmpty()) {
                    p.sendSystemMessage(Component.literal("§cСначала добавь хотя бы один предмет."));
                } else if (d.mode.equals("player")
                        && (d.targetId == null || server.getPlayerList().getPlayer(d.targetId) == null)) {
                    p.sendSystemMessage(Component.literal("§cВыбери игрока (он должен быть в сети) во вкладке «Кому и когда»."));
                } else {
                    d.message = a;
                    d.active = true;
                    d.reqs.forEach(r -> r.progress = 0);
                    d.contributors.clear();
                    long now = server.overworld().getGameTime();
                    d.deadline = d.minutes > 0 ? now + d.minutes * 1200L : -1L;
                    Punisher.resetWarn();
                    d.setDirty();
                    announce(server, d);
                    if (d.mode.equals("player")) {
                        Punisher.titleAll(server, "§5Алтарь выбрал", "§e§l" + d.targetName);
                    }
                }
            }
            case "cancel" -> {
                d.active = false;
                d.deadline = -1L;
                d.setDirty();
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal("§5[Алтарь] §7Алтарь успокоился. Подношение отменено, наказания не будет."), false);
            }
            case "cfg" -> {
                switch (a) {
                    case "mode" -> d.mode = n == 1 ? "player" : "all";
                    case "helpers" -> d.helpers = n == 1;
                    case "scope" -> d.scopeAll = n == 1;
                    case "minutes" -> d.minutes = clamp(n, 0, 1440);
                    case "p_health" -> d.pHealth = n == 1;
                    case "p_health_n" -> d.pHealthN = clamp(n, 1, 9);
                    case "p_light" -> d.pLightning = n == 1;
                    case "p_wipe" -> d.pWipe = n == 1;
                    case "p_wipe_n" -> d.pWipeN = clamp(n, 5, 100);
                    case "p_expl" -> d.pExplode = n == 1;
                    case "p_expl_delay" -> d.pExplodeDelay = clamp(n, 1, 30);
                    case "p_expl_power" -> d.pExplodePower = clamp(n, 1, 10);
                    default -> { }
                }
                d.setDirty();
            }
            case "set_target" -> {
                ServerPlayer t = server.getPlayerList().getPlayerByName(a);
                if (t != null) {
                    d.targetId = t.getUUID();
                    d.targetName = t.getGameProfile().getName();
                    d.setDirty();
                }
            }
            case "random_target" -> {
                List<ServerPlayer> list = server.getPlayerList().getPlayers();
                if (!list.isEmpty()) {
                    ServerPlayer t = list.get(p.getRandom().nextInt(list.size()));
                    d.targetId = t.getUUID();
                    d.targetName = t.getGameProfile().getName();
                    d.setDirty();
                }
            }
            case "punish_now" -> {
                ServerPlayer t = server.getPlayerList().getPlayerByName(a);
                if (t == null) {
                    p.sendSystemMessage(Component.literal("§cИгрок не в сети: " + a));
                } else if (!d.anyPunishment()) {
                    p.sendSystemMessage(Component.literal("§cНи одно наказание не включено (вкладка «Наказания»)."));
                } else {
                    Punisher.punish(t, d);
                }
            }
            case "heal_max" -> {
                ServerPlayer t = server.getPlayerList().getPlayerByName(a);
                if (t != null) {
                    d.healthLost.remove(t.getUUID());
                    d.pending.remove(t.getUUID());
                    d.setDirty();
                    Punisher.applyHealth(t, d);
                    t.sendSystemMessage(Component.literal("§aАлтарь простил тебя и вернул силы."));
                }
            }
            case "view_inv" -> {
                ServerPlayer t = server.getPlayerList().getPlayerByName(a);
                if (t == null) {
                    p.sendSystemMessage(Component.literal("§cИгрок не в сети: " + a));
                } else {
                    PlayerInvContainer cont = new PlayerInvContainer(t);
                    p.openMenu(new SimpleMenuProvider(
                            (id, inv, pl) -> ChestMenu.sixRows(id, inv, cont),
                            Component.literal("Инвентарь: " + t.getGameProfile().getName())));
                    return;
                }
            }
            case "give_effect" -> {
                ServerPlayer t = server.getPlayerList().getPlayerByName(a);
                Holder<MobEffect> h = effectHolder(b);
                if (t == null) {
                    p.sendSystemMessage(Component.literal("§cИгрок не в сети: " + a));
                } else if (h == null) {
                    p.sendSystemMessage(Component.literal("§cТакого эффекта нет: " + b));
                } else {
                    int amp = Math.max(0, Math.min(255, n));
                    String key = h.unwrapKey().get().location().toString();
                    d.effects.computeIfAbsent(t.getUUID(), k -> new LinkedHashMap<>()).put(key, amp);
                    d.setDirty();
                    applyEffects(t, true);
                    t.sendSystemMessage(Component.literal("§dАлтарь наделил тебя силой: §f" + key + " " + (amp + 1)));
                }
            }
            case "remove_effect" -> {
                ServerPlayer t = server.getPlayerList().getPlayerByName(a);
                Holder<MobEffect> h = effectHolder(b);
                if (t != null && h != null) {
                    String key = h.unwrapKey().get().location().toString();
                    Map<String, Integer> m = d.effects.get(t.getUUID());
                    if (m != null) {
                        m.remove(key);
                        if (m.isEmpty()) d.effects.remove(t.getUUID());
                        d.setDirty();
                    }
                    t.removeEffect(h);
                    t.sendSystemMessage(Component.literal("§7Алтарь забрал у тебя эффект: " + key));
                }
            }
            case "clear_effects" -> {
                ServerPlayer t = server.getPlayerList().getPlayerByName(a);
                if (t != null) {
                    Map<String, Integer> m = d.effects.remove(t.getUUID());
                    d.setDirty();
                    if (m != null) {
                        for (String id : m.keySet()) {
                            Holder<MobEffect> h = effectHolder(id);
                            if (h != null) t.removeEffect(h);
                        }
                    }
                }
            }
            default -> { }
        }
        sendState(p, open);
    }

    /** Рассылает всем игрокам, что нужно принести алтарю. */
    public static void announce(MinecraftServer server, AltarData d) {
        MutableComponent msg = Component.literal("§5§l[Алтарь] §r§d" + d.message);
        if (d.mode.equals("player")) {
            msg.append(Component.literal("\n§5Алтарь выбрал игрока: §e§l" + d.targetName));
        }
        for (AltarData.Req r : d.reqs) {
            msg.append(Component.literal("\n§7 • §f" + r.count + " × "))
                    .append(itemName(r.item))
                    .append(Component.literal(" §8(" + r.item + ")"));
        }
        if (d.deadline >= 0) {
            msg.append(Component.literal("\n§cВремя: §f" + d.minutes + " мин. §4Не успеете — алтарь разгневается."));
        }
        msg.append(Component.literal("\n§7Нажми ПКМ по алтарю с предметом в руке, чтобы принести его."));
        server.getPlayerList().broadcastSystemMessage(msg, false);
    }

    public static Component itemName(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return Component.literal(id);
        Item it = BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
        return it == null ? Component.literal(id) : it.getDescription();
    }

    /** Накладывает вечные эффекты и держит отнятое здоровье. force=true перекладывает всё немедленно. */
    public static void applyEffects(ServerPlayer p, boolean force) {
        AltarData d = AltarData.get(p.server);
        Punisher.applyHealth(p, d);
        Map<String, Integer> m = d.effects.get(p.getUUID());
        if (m == null) return;
        for (Map.Entry<String, Integer> en : m.entrySet()) {
            Holder<MobEffect> eff = effectHolder(en.getKey());
            if (eff == null) continue;
            int amp = en.getValue();
            MobEffectInstance cur = p.getEffect(eff);
            boolean need = force || cur == null
                    || cur.getAmplifier() < amp
                    || (cur.getAmplifier() == amp && cur.getDuration() < 200);
            if (need) {
                p.addEffect(new MobEffectInstance(eff, 400, amp, false, false, true));
            }
        }
    }

    public static void sendState(ServerPlayer p, boolean open) {
        PacketDistributor.sendToPlayer(p, new S2CState(buildState(p.server, open)));
    }

    public static CompoundTag buildState(MinecraftServer server, boolean open) {
        AltarData d = AltarData.get(server);
        CompoundTag t = new CompoundTag();
        t.putBoolean("open", open);
        t.putBoolean("active", d.active);
        t.putString("message", d.message);

        t.putString("mode", d.mode);
        t.putString("target", d.targetName);
        t.putBoolean("helpers", d.helpers);
        t.putBoolean("scope", d.scopeAll);
        t.putInt("minutes", d.minutes);
        long left = -1L;
        if (d.active && d.deadline >= 0) {
            left = Math.max(0L, (d.deadline - server.overworld().getGameTime()) / 20L);
        }
        t.putLong("left", left);

        t.putBoolean("ph", d.pHealth);
        t.putInt("phN", d.pHealthN);
        t.putBoolean("pl", d.pLightning);
        t.putBoolean("pw", d.pWipe);
        t.putInt("pwN", d.pWipeN);
        t.putBoolean("pe", d.pExplode);
        t.putInt("peDelay", d.pExplodeDelay);
        t.putInt("pePower", d.pExplodePower);

        ListTag reqs = new ListTag();
        for (AltarData.Req r : d.reqs) {
            CompoundTag c = new CompoundTag();
            c.putString("item", r.item);
            c.putInt("count", r.count);
            c.putInt("progress", r.progress);
            reqs.add(c);
        }
        t.put("reqs", reqs);

        ListTag players = new ListTag();
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            CompoundTag c = new CompoundTag();
            c.putString("name", sp.getGameProfile().getName());
            c.putInt("lost", d.healthLost.getOrDefault(sp.getUUID(), 0));
            c.putBoolean("pending", d.pending.contains(sp.getUUID()));
            ListTag effs = new ListTag();
            Map<String, Integer> m = d.effects.get(sp.getUUID());
            if (m != null) {
                for (Map.Entry<String, Integer> e : m.entrySet()) {
                    CompoundTag x = new CompoundTag();
                    x.putString("id", e.getKey());
                    x.putInt("amp", e.getValue());
                    effs.add(x);
                }
            }
            c.put("effects", effs);
            players.add(c);
        }
        t.put("players", players);
        return t;
    }
}
