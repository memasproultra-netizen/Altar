package com.rpserver.altarmod.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Хранит требования алтаря, прогресс и вечные эффекты игроков. Сохраняется в мире. */
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
        return d;
    }
}
