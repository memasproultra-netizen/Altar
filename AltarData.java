package com.rpserver.altarmod.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Хранит требования алтаря, режим, таймер, наказания и вечные эффекты. Сохраняется в мире. */
public class AltarData extends SavedData {

    public static class Req {
        public String item;
        public int count;
        public int progress;
    }

    public final List<Req> reqs = new ArrayList<>();
    public String message = "";
    public boolean active = false;

    /** UUID игрока -> (id эффекта -> уровень). */
    public final Map<UUID, LinkedHashMap<String, Integer>> effects = new HashMap<>();

    // ----- режим -----
    /** "all" — требование для всех, "player" — алтарь выбрал одного игрока. */
    public String mode = "all";
    public UUID targetId = null;
    public String targetName = "";
    /** В режиме "player": могут ли другие приносить дары за выбранного. */
    public boolean helpers = false;
    /** В режиме "all": true — наказать всех онлайн, false — только тех, кто ничего не принёс. */
    public boolean scopeAll = false;

    // ----- таймер -----
    public int minutes = 0;
    /** Игровое время (тики оверворлда), когда алтарь разгневается. -1 = без времени. */
    public long deadline = -1L;

    // ----- наказания -----
    public boolean pHealth = true;
    public int pHealthN = 2;
    public boolean pLightning = false;
    public boolean pWipe = false;
    public int pWipeN = 25;
    public boolean pExplode = false;
    public int pExplodeDelay = 3;
    public int pExplodePower = 3;

    /** Кто в этом раунде принёс хоть что-то. */
    public final Set<UUID> contributors = new HashSet<>();
    /** Игроки, которых кара ждёт при входе в игру. */
    public final Set<UUID> pending = new HashSet<>();
    /** UUID -> сколько сердец максимального здоровья отнято. */
    public final Map<UUID, Integer> healthLost = new HashMap<>();

    public static AltarData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(AltarData::new, AltarData::load, null), "altarmod_data");
    }

    public Req find(String itemId) {
        for (Req r : reqs) {
            if (r.item.equals(itemId)) return r;
        }
        return null;
    }

    public boolean isComplete() {
        if (reqs.isEmpty()) return false;
        for (Req r : reqs) {
            if (r.progress < r.count) return false;
        }
        return true;
    }

    public boolean anyPunishment() {
        return pHealth || pLightning || pWipe || pExplode;
    }

    private static ListTag uuidList(Set<UUID> set) {
        ListTag l = new ListTag();
        for (UUID u : set) l.add(StringTag.valueOf(u.toString()));
        return l;
    }

    private static void readUuids(CompoundTag tag, String key, Set<UUID> out) {
        ListTag l = tag.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < l.size(); i++) {
            try {
                out.add(UUID.fromString(l.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("message", message);
        tag.putBoolean("active", active);

        ListTag reqList = new ListTag();
        for (Req r : reqs) {
            CompoundTag c = new CompoundTag();
            c.putString("item", r.item);
            c.putInt("count", r.count);
            c.putInt("progress", r.progress);
            reqList.add(c);
        }
        tag.put("reqs", reqList);

        ListTag effList = new ListTag();
        for (Map.Entry<UUID, LinkedHashMap<String, Integer>> e : effects.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("uuid", e.getKey().toString());
            ListTag list = new ListTag();
            for (Map.Entry<String, Integer> ef : e.getValue().entrySet()) {
                CompoundTag x = new CompoundTag();
                x.putString("id", ef.getKey());
                x.putInt("amp", ef.getValue());
                list.add(x);
            }
            c.put("list", list);
            effList.add(c);
        }
        tag.put("effects", effList);

        tag.putString("mode", mode);
        tag.putString("targetName", targetName);
        if (targetId != null) tag.putString("targetId", targetId.toString());
        tag.putBoolean("helpers", helpers);
        tag.putBoolean("scopeAll", scopeAll);
        tag.putInt("minutes", minutes);
        tag.putLong("deadline", deadline);

        tag.putBoolean("pHealth", pHealth);
        tag.putInt("pHealthN", pHealthN);
        tag.putBoolean("pLightning", pLightning);
        tag.putBoolean("pWipe", pWipe);
        tag.putInt("pWipeN", pWipeN);
        tag.putBoolean("pExplode", pExplode);
        tag.putInt("pExplodeDelay", pExplodeDelay);
        tag.putInt("pExplodePower", pExplodePower);

        tag.put("contributors", uuidList(contributors));
        tag.put("pending", uuidList(pending));

        ListTag lost = new ListTag();
        for (Map.Entry<UUID, Integer> e : healthLost.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("uuid", e.getKey().toString());
            c.putInt("n", e.getValue());
            lost.add(c);
        }
        tag.put("healthLost", lost);
        return tag;
    }

    public static AltarData load(CompoundTag tag, HolderLookup.Provider registries) {
        AltarData d = new AltarData();
        d.message = tag.getString("message");
        d.active = tag.getBoolean("active");

        ListTag reqList = tag.getList("reqs", Tag.TAG_COMPOUND);
        for (int i = 0; i < reqList.size(); i++) {
            CompoundTag c = reqList.getCompound(i);
            Req r = new Req();
            r.item = c.getString("item");
            r.count = c.getInt("count");
            r.progress = c.getInt("progress");
            d.reqs.add(r);
        }

        ListTag effList = tag.getList("effects", Tag.TAG_COMPOUND);
        for (int i = 0; i < effList.size(); i++) {
            CompoundTag c = effList.getCompound(i);
            try {
                UUID id = UUID.fromString(c.getString("uuid"));
                LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
                ListTag list = c.getList("list", Tag.TAG_COMPOUND);
                for (int j = 0; j < list.size(); j++) {
                    CompoundTag x = list.getCompound(j);
                    map.put(x.getString("id"), x.getInt("amp"));
                }
                d.effects.put(id, map);
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (tag.contains("mode")) d.mode = tag.getString("mode");
        d.targetName = tag.getString("targetName");
        if (tag.contains("targetId")) {
            try {
                d.targetId = UUID.fromString(tag.getString("targetId"));
            } catch (IllegalArgumentException ignored) {
            }
        }
        d.helpers = tag.getBoolean("helpers");
        d.scopeAll = tag.getBoolean("scopeAll");
        d.minutes = tag.getInt("minutes");
        d.deadline = tag.contains("deadline") ? tag.getLong("deadline") : -1L;

        if (tag.contains("pHealth")) {
            d.pHealth = tag.getBoolean("pHealth");
            d.pHealthN = tag.getInt("pHealthN");
            d.pLightning = tag.getBoolean("pLightning");
            d.pWipe = tag.getBoolean("pWipe");
            d.pWipeN = tag.getInt("pWipeN");
            d.pExplode = tag.getBoolean("pExplode");
            d.pExplodeDelay = tag.getInt("pExplodeDelay");
            d.pExplodePower = tag.getInt("pExplodePower");
        }

        readUuids(tag, "contributors", d.contributors);
        readUuids(tag, "pending", d.pending);

        ListTag lost = tag.getList("healthLost", Tag.TAG_COMPOUND);
        for (int i = 0; i < lost.size(); i++) {
            CompoundTag c = lost.getCompound(i);
            try {
                d.healthLost.put(UUID.fromString(c.getString("uuid")), c.getInt("n"));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return d;
    }
}
