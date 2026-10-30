package com.rpserver.altarmod.client;

import com.rpserver.altarmod.net.C2SAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.network.PacketDistributor;

/** Админ-панель: слева алтарь и подношения, справа игроки, инвентари и вечные эффекты. */
public class AdminScreen extends Screen {

    private String itemTxt = "minecraft:diamond";
    private String countTxt = "50";
    private String msgTxt = "Алтарь жаждет даров...";
    private String effTxt = "minecraft:regeneration";
    private String ampTxt = "0";
    private String selected = "";

    private EditBox itemBox, countBox, msgBox, effBox, ampBox;

    public AdminScreen() {
        super(Component.literal("Алтарь — админ-панель"));
    }

    public void refresh() {
        rebuildWidgets();
    }

    private CompoundTag st() {
        return ClientHooks.state;
    }

    private static void send(String action, String a, String b, int n) {
        PacketDistributor.sendToServer(new C2SAction(action, a, b, n));
    }

    private static int num(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private EditBox box(int x, int y, int w, String value, java.util.function.Consumer<String> onChange) {
        EditBox b = new EditBox(font, x, y, w, 20, Component.empty());
        b.setMaxLength(256);
        b.setValue(value);
        b.setResponder(onChange);
        addRenderableWidget(b);
        return b;
    }

    private Button btn(String label, int x, int y, int w, Button.OnPress press) {
        Button b = Button.builder(Component.literal(label), press).bounds(x, y, w, 20).build();
        addRenderableWidget(b);
        return b;
    }

    @Override
    protected void init() {
        int pw = width / 2 - 20;
        int lx = 10;
        int rx = width / 2 + 10;

        // ----- левая панель: алтарь -----
        itemBox = box(lx, 40, pw - 58, itemTxt, s -> itemTxt = s);
        countBox = box(lx + pw - 54, 40, 54, countTxt, s -> countTxt = s);

        btn("Предмет из руки", lx, 64, pw / 2 - 2, b -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            ItemStack held = mc.player.getMainHandItem();
            if (held.isEmpty()) return;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(held.getItem());
            if (id != null) {
                itemTxt = id.toString();
                itemBox.setValue(itemTxt);
            }
        });
        btn("Добавить", lx + pw / 2 + 2, 64, pw / 2 - 2, b ->
                send("add_req", itemTxt, "", num(countTxt, 1)));

        ListTag reqs = st().getList("reqs", Tag.TAG_COMPOUND);
        int maxRows = Math.max(1, (height - 190) / 18);
        for (int i = 0; i < reqs.size() && i < maxRows; i++) {
            CompoundTag r = reqs.getCompound(i);
            String id = r.getString("item");
            btn("X", lx + pw - 20, 94 + i * 18, 20, b -> send("del_req", id, "", 0));
        }

        msgBox = box(lx, height - 72, pw, msgTxt, s -> msgTxt = s);
        btn("Объявить всем", lx, height - 48, pw / 2 - 2, b -> send("broadcast", msgTxt, "", 0));
        btn("Сбросить прогресс", lx + pw / 2 + 2, height - 48, pw / 2 - 2, b -> send("reset_progress", "", "", 0));
        btn("Очистить список", lx, height - 24, pw / 2 - 2, b -> send("clear_req", "", "", 0));
        btn("Закрыть", lx + pw / 2 + 2, height - 24, pw / 2 - 2, b -> onClose());

        // ----- правая панель: игроки -----
        ListTag players = st().getList("players", Tag.TAG_COMPOUND);
        int idx = 0;
        boolean found = false;
        for (int i = 0; i < players.size(); i++) {
            if (players.getCompound(i).getString("name").equals(selected)) {
                idx = i;
                found = true;
            }
        }
        if (!found && players.size() > 0) {
            selected = players.getCompound(0).getString("name");
        }
        final int cur = idx;
        final int total = players.size();

        btn("<", rx, 40, 20, b -> {
            if (total > 0) {
                selected = players.getCompound((cur - 1 + total) % total).getString("name");
                rebuildWidgets();
            }
        });
        btn(">", rx + pw - 20, 40, 20, b -> {
            if (total > 0) {
                selected = players.getCompound((cur + 1) % total).getString("name");
                rebuildWidgets();
            }
        });
        btn("Открыть инвентарь", rx, 64, pw, b -> {
            if (!selected.isEmpty()) send("view_inv", selected, "", 0);
        });

        effBox = box(rx, 108, pw - 58, effTxt, s -> effTxt = s);
        ampBox = box(rx + pw - 54, 108, 54, ampTxt, s -> ampTxt = s);
        btn("Выдать навсегда", rx, 132, pw, b -> {
            if (!selected.isEmpty()) send("give_effect", selected, effTxt, num(ampTxt, 0));
        });

        if (total > 0) {
            ListTag effs = players.getCompound(cur).getList("effects", Tag.TAG_COMPOUND);
            int rows = Math.max(1, (height - 230) / 18);
            for (int i = 0; i < effs.size() && i < rows; i++) {
                String id = effs.getCompound(i).getString("id");
                btn("Снять", rx + pw - 50, 170 + i * 18, 50, b -> send("remove_effect", selected, id, 0));
            }
            if (effs.size() > 0) {
                btn("Снять все эффекты", rx, height - 24, pw, b -> send("clear_effects", selected, "", 0));
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int pw = width / 2 - 20;
        int lx = 10;
        int rx = width / 2 + 10;

        g.drawCenteredString(font, "§5§lАлтарь — админ-панель", width / 2, 10, 0xFFFFFF);

        // левая панель
        g.drawString(font, "Подношение (id предмета, в т.ч. модового) и количество:", lx, 28, 0xFFFFFF);
        g.drawString(font, "Алтарь требует:", lx, 84, 0xAAAAAA);
        ListTag reqs = st().getList("reqs", Tag.TAG_COMPOUND);
        int maxRows = Math.max(1, (height - 190) / 18);
        for (int i = 0; i < reqs.size() && i < maxRows; i++) {
            CompoundTag r = reqs.getCompound(i);
            String line = r.getInt("progress") + "/" + r.getInt("count") + " × " + r.getString("item");
            g.drawString(font, font.plainSubstrByWidth(line, pw - 26), lx, 99 + i * 18, 0xFFFFFF);
        }
        if (reqs.isEmpty()) {
            g.drawString(font, "§8(список пуст)", lx, 99, 0xFFFFFF);
        }
        g.drawString(font, "Сообщение игрокам:", lx, height - 84, 0xFFFFFF);
        g.drawString(font, st().getBoolean("active") ? "§aАлтарь активен" : "§7Алтарь молчит", lx, height - 96, 0xFFFFFF);

        // правая панель
        ListTag players = st().getList("players", Tag.TAG_COMPOUND);
        g.drawString(font, "Игрок:", rx, 28, 0xFFFFFF);
        String name = selected.isEmpty() ? "(никого нет в сети)" : selected;
        g.drawCenteredString(font, name, rx + pw / 2, 46, 0xFFFF55);
        g.drawString(font, "Эффект (id) и уровень (0 = I):", rx, 96, 0xFFFFFF);
        g.drawString(font, "Вечные эффекты игрока:", rx, 158, 0xAAAAAA);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag p = players.getCompound(i);
            if (!p.getString("name").equals(selected)) continue;
            ListTag effs = p.getList("effects", Tag.TAG_COMPOUND);
            int rows = Math.max(1, (height - 230) / 18);
            for (int j = 0; j < effs.size() && j < rows; j++) {
                CompoundTag e = effs.getCompound(j);
                String line = e.getString("id") + " " + (e.getInt("amp") + 1);
                g.drawString(font, font.plainSubstrByWidth(line, pw - 56), rx, 175 + j * 18, 0xFFFFFF);
            }
            if (effs.isEmpty()) {
                g.drawString(font, "§8(нет)", rx, 175, 0xFFFFFF);
            }
        }

    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
