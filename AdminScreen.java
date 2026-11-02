package com.rpserver.altarmod.client;

import com.rpserver.altarmod.net.C2SAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Админ-панель алтаря: 4 вкладки. Открывается клавишей K или /altaradmin. */
public class AdminScreen extends Screen {

    private static final String[] TABS = {"Подношение", "Кому и когда", "Наказания", "Игроки"};
    private static int lastTab = 0;

    private record Label(String text, int x, int y, int color) {}

    private int tab = lastTab;
    private String itemTxt = "minecraft:diamond";
    private String countTxt = "50";
    private String msgTxt = "Алтарь жаждет даров...";
    private String effTxt = "minecraft:regeneration";
    private String ampTxt = "0";
    private String selected = "";

    private EditBox itemBox;
    private final List<Label> labels = new ArrayList<>();
    private final List<String> names = new ArrayList<>();

    public AdminScreen() {
        super(Component.literal("Алтарь — админ-панель"));
    }

    public void refresh() {
        rebuildWidgets();
    }

    // ---------------------------------------------------------------- утилиты

    private CompoundTag st() {
        return ClientHooks.state;
    }

    private static void send(String action, String a, String b, int n) {
        PacketDistributor.sendToServer(new C2SAction(action, a, b, n));
    }

    private static void cfg(String key, int value) {
        send("cfg", key, "", value);
    }

    private static int num(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String onoff(boolean v) {
        return v ? "§aВКЛ" : "§cВЫКЛ";
    }

    private void label(String text, int x, int y) {
        labels.add(new Label(text, x, y, 0xFFFFFF));
    }

    private void label(String text, int x, int y, int color) {
        labels.add(new Label(text, x, y, color));
    }

    private EditBox box(int x, int y, int w, String value, Consumer<String> onChange) {
        EditBox b = new EditBox(font, x, y, w, 20, Component.empty());
        b.setMaxLength(256);
        b.setValue(value);
        b.setResponder(onChange);
        addRenderableWidget(b);
        return b;
    }

    private Button btn(String text, int x, int y, int w, int h, Button.OnPress press) {
        Button b = Button.builder(Component.literal(text), press).bounds(x, y, w, h).build();
        addRenderableWidget(b);
        return b;
    }

    private Button btn(String text, int x, int y, int w, Button.OnPress press) {
        return btn(text, x, y, w, 20, press);
    }

    /** Строка «название  [-] значение [+]». Shift = шаг ×5. */
    private void stepper(String name, int x, int y, int w, String key, int value, int min, int max, int step) {
        label(name, x, y + 6);
        btn("-", x + w - 96, y, 20, b ->
                cfg(key, Math.max(min, value - (hasShiftDown() ? step * 5 : step))));
        String v = String.valueOf(value);
        label(v, x + w - 76 + (56 - font.width(v)) / 2, y + 6, 0xFFFF55);
        btn("+", x + w - 20, y, 20, b ->
                cfg(key, Math.min(max, value + (hasShiftDown() ? step * 5 : step))));
    }

    /** Выбор игрока кнопками « < имя > » из тех, кто в сети. */
    private void picker(int x, int y, int w, String current, Consumer<String> onPick) {
        btn("<", x, y, 20, b -> {
            if (names.isEmpty()) return;
            int i = names.indexOf(current);
            onPick.accept(names.get(((i < 0 ? 0 : i) - 1 + names.size()) % names.size()));
        });
        btn(">", x + w - 20, y, 20, b -> {
            if (names.isEmpty()) return;
            int i = names.indexOf(current);
            onPick.accept(names.get((i + 1) % names.size()));
        });
        String shown = current.isEmpty() ? "(не выбран)" : current;
        label(shown, x + w / 2 - font.width(shown) / 2, y + 6, 0xFFFF55);
    }

    private CompoundTag selectedTag() {
        ListTag players = st().getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            if (players.getCompound(i).getString("name").equals(selected)) return players.getCompound(i);
        }
        return null;
    }

    // ---------------------------------------------------------------- построение

    @Override
    protected void init() {
        labels.clear();
        names.clear();
        ListTag players = st().getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            names.add(players.getCompound(i).getString("name"));
        }
        if (!names.contains(selected)) selected = names.isEmpty() ? "" : names.get(0);

        // вкладки
        int tw = (width - 20 - 12) / 4;
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            Button b = btn(TABS[i], 10 + i * (tw + 4), 20, tw, 18, x -> {
                tab = idx;
                lastTab = idx;
                rebuildWidgets();
            });
            b.active = i != tab;
        }
        btn("Закрыть", width - 60, 3, 50, 14, b -> onClose());

        switch (tab) {
            case 0 -> tabOffer();
            case 1 -> tabWho();
            case 2 -> tabPunish();
            default -> tabPlayers();
        }
    }

    /** Вкладка 1: что принести. */
    private void tabOffer() {
        int lx = 10;
        int pw = width / 2 - 20;
        int rx = width / 2 + 10;

        label("Предмет (id, можно из модов) и количество:", lx, 46);
        itemBox = box(lx, 58, pw - 58, itemTxt, s -> itemTxt = s);
        box(lx + pw - 54, 58, 54, countTxt, s -> countTxt = s);

        btn("Предмет из руки", lx, 82, pw / 2 - 2, b -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            ItemStack held = mc.player.getMainHandItem();
            if (held.isEmpty()) return;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(held.getItem());
            itemTxt = id.toString();
            itemBox.setValue(itemTxt);
        });
        btn("Добавить", lx + pw / 2 + 2, 82, pw / 2 - 2, b ->
                send("add_req", itemTxt, "", num(countTxt, 1)));

        label("Сообщение игрокам:", lx, 110);
        box(lx, 122, pw, msgTxt, s -> msgTxt = s);

        label("Алтарь требует:", rx, 46, 0xAAAAAA);
        ListTag reqs = st().getList("reqs", Tag.TAG_COMPOUND);
        int rows = Math.max(1, (height - 130) / 18);
        for (int i = 0; i < reqs.size() && i < rows; i++) {
            CompoundTag r = reqs.getCompound(i);
            final String id = r.getString("item");
            String line = r.getInt("progress") + "/" + r.getInt("count") + " × " + id;
            label(font.plainSubstrByWidth(line, pw - 26), rx, 63 + i * 18);
            btn("X", rx + pw - 20, 58 + i * 18, 20, 16, b -> send("del_req", id, "", 0));
        }
        if (reqs.isEmpty()) label("§8(список пуст)", rx, 63);

        int bw = (width - 30) / 2;
        btn("§aОбъявить всем", 10, height - 48, bw, b -> send("broadcast", msgTxt, "", 0));
        btn("Отменить (без наказания)", 20 + bw, height - 48, bw, b -> send("cancel", "", "", 0));
        btn("Сбросить прогресс", 10, height - 24, bw, b -> send("reset_progress", "", "", 0));
        btn("Очистить список", 20 + bw, height - 24, bw, b -> send("clear_req", "", "", 0));
    }

    /** Вкладка 2: для всех или для одного игрока, и время. */
    private void tabWho() {
        int cw = Math.min(340, width - 20);
        int cx = (width - cw) / 2;
        final boolean player = "player".equals(st().getString("mode"));

        btn("Режим: " + (player ? "§dдля одного игрока" : "§bдля всех игроков"), cx, 46, cw, b ->
                cfg("mode", player ? 0 : 1));

        if (player) {
            label("Алтарь выбирает игрока (все увидят это на экране):", cx, 74);
            picker(cx, 86, cw, st().getString("target"), name -> send("set_target", name, "", 0));
            btn("Случайный игрок", cx, 110, cw, b -> send("random_target", "", "", 0));
            final boolean helpers = st().getBoolean("helpers");
            btn("Другие могут помогать: " + (helpers ? "§aда" : "§cнет"), cx, 134, cw, b ->
                    cfg("helpers", helpers ? 0 : 1));
        } else {
            final boolean all = st().getBoolean("scope");
            label("При провале алтарь наказывает:", cx, 74);
            btn(all ? "§eВсех игроков онлайн" : "§eТех, кто ничего не принёс", cx, 86, cw, b ->
                    cfg("scope", all ? 0 : 1));
        }

        stepper("Время, мин (0 = без лимита)", cx, 164, cw, "minutes", st().getInt("minutes"), 0, 1440, 1);
        label("Shift = шаг ×5. Время запустится при «Объявить всем».", cx, 190, 0x888888);
    }

    /** Вкладка 3: что случится, если время вышло. */
    private void tabPunish() {
        int cw = Math.min(360, width - 20);
        int cx = (width - cw) / 2;
        int tw = 130;
        int sx = cx + tw + 10;
        int sw = cw - tw - 10;

        final boolean h = st().getBoolean("ph");
        btn("Здоровье: " + onoff(h), cx, 46, tw, b -> cfg("p_health", h ? 0 : 1));
        stepper("−сердец", sx, 46, sw, "p_health_n", st().getInt("phN"), 1, 9, 1);

        final boolean l = st().getBoolean("pl");
        btn("Молния: " + onoff(l), cx, 70, tw, b -> cfg("p_light", l ? 0 : 1));

        final boolean w = st().getBoolean("pw");
        btn("Инвентарь: " + onoff(w), cx, 94, tw, b -> cfg("p_wipe", w ? 0 : 1));
        stepper("стереть %", sx, 94, sw, "p_wipe_n", st().getInt("pwN"), 5, 100, 5);

        final boolean e = st().getBoolean("pe");
        btn("Взрыв: " + onoff(e), cx, 118, tw, b -> cfg("p_expl", e ? 0 : 1));
        stepper("Взрыв через, сек", cx, 142, cw, "p_expl_delay", st().getInt("peDelay"), 1, 30, 1);
        stepper("Мощность взрыва", cx, 166, cw, "p_expl_power", st().getInt("pePower"), 1, 10, 1);

        label("Можно включить несколько сразу. Shift = шаг ×5.", cx, 194, 0x888888);
        label("Проверить наказание и простить игрока — во вкладке «Игроки».", cx, 206, 0x888888);
    }

    /** Вкладка 4: инвентарь, наказать/простить, вечные эффекты. */
    private void tabPlayers() {
        int lx = 10;
        int pw = width / 2 - 20;
        int rx = width / 2 + 10;

        label("Игрок:", lx, 46);
        picker(lx, 58, pw, selected, name -> {
            selected = name;
            rebuildWidgets();
        });
        btn("Открыть инвентарь", lx, 84, pw, b -> {
            if (!selected.isEmpty()) send("view_inv", selected, "", 0);
        });
        btn("§cНаказать сейчас (по настройкам)", lx, 108, pw, b -> {
            if (!selected.isEmpty()) send("punish_now", selected, "", 0);
        });
        btn("§aПростить (вернуть здоровье)", lx, 132, pw, b -> {
            if (!selected.isEmpty()) send("heal_max", selected, "", 0);
        });
        CompoundTag sel = selectedTag();
        if (sel != null) {
            String info = "Отнято сердец: " + sel.getInt("lost")
                    + (sel.getBoolean("pending") ? " §c(кара ждёт его входа)" : "");
            label(info, lx, 160);
        }

        label("Эффект (id) и уровень (0 = I):", rx, 46);
        box(rx, 58, pw - 58, effTxt, s -> effTxt = s);
        box(rx + pw - 54, 58, 54, ampTxt, s -> ampTxt = s);
        btn("Выдать навсегда", rx, 82, pw, b -> {
            if (!selected.isEmpty()) send("give_effect", selected, effTxt, num(ampTxt, 0));
        });

        label("Вечные эффекты игрока:", rx, 110, 0xAAAAAA);
        if (sel != null) {
            ListTag effs = sel.getList("effects", Tag.TAG_COMPOUND);
            int rows = Math.max(1, (height - 160) / 18);
            for (int i = 0; i < effs.size() && i < rows; i++) {
                final String id = effs.getCompound(i).getString("id");
                String line = id + " " + (effs.getCompound(i).getInt("amp") + 1);
                label(font.plainSubstrByWidth(line, pw - 56), rx, 129 + i * 18);
                btn("Снять", rx + pw - 50, 124 + i * 18, 50, 16, b -> send("remove_effect", selected, id, 0));
            }
            if (effs.isEmpty()) {
                label("§8(нет)", rx, 129);
            } else {
                btn("Снять все эффекты", rx, height - 24, pw, b -> send("clear_effects", selected, "", 0));
            }
        }
    }

    // ---------------------------------------------------------------- отрисовка

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);

        boolean active = st().getBoolean("active");
        long left = st().getLong("left");
        String status = active ? "§a● активен" : "§7● молчит";
        if (active && left >= 0) {
            long rem = Math.max(0L, left - (System.currentTimeMillis() - ClientHooks.stateAt) / 1000L);
            status += String.format(" §c%02d:%02d", rem / 60, rem % 60);
        }
        g.drawString(font, "§5§lАлтарь  §r" + status, 10, 6, 0xFFFFFF);

        for (Label l : labels) {
            g.drawString(font, l.text(), l.x(), l.y(), l.color());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
