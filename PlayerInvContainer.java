package com.rpserver.altarmod.data;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Окно на 54 слота, которое напрямую редактирует инвентарь выбранного игрока.
 * Ряд 0: шлем, нагрудник, поножи, ботинки, вторая рука.
 * Ряды 1-3: основной инвентарь. Ряд 4: хотбар. Ряд 5: пусто.
 */
public class PlayerInvContainer implements Container {

    private final ServerPlayer target;

    public PlayerInvContainer(ServerPlayer target) {
        this.target = target;
    }

    private boolean valid(int i) {
        return (i >= 0 && i <= 4) || (i >= 9 && i <= 44);
    }

    private ItemStack get(int i) {
        Inventory inv = target.getInventory();
        if (i >= 0 && i <= 3) return inv.armor.get(3 - i);
        if (i == 4) return inv.offhand.get(0);
        if (i >= 9 && i <= 35) return inv.items.get(i);
        if (i >= 36 && i <= 44) return inv.items.get(i - 36);
        return ItemStack.EMPTY;
    }

    private void set(int i, ItemStack s) {
        Inventory inv = target.getInventory();
        if (i >= 0 && i <= 3) inv.armor.set(3 - i, s);
        else if (i == 4) inv.offhand.set(0, s);
        else if (i >= 9 && i <= 35) inv.items.set(i, s);
        else if (i >= 36 && i <= 44) inv.items.set(i - 36, s);
    }

    @Override
    public int getContainerSize() {
        return 54;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < 54; i++) {
            if (!get(i).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int i) {
        return get(i);
    }

    @Override
    public ItemStack removeItem(int i, int amount) {
        ItemStack s = get(i);
        if (s.isEmpty() || !valid(i)) return ItemStack.EMPTY;
        ItemStack out = s.split(amount);
        setChanged();
        return out;
    }

    @Override
    public ItemStack removeItemNoUpdate(int i) {
        ItemStack s = get(i);
        if (!valid(i)) return ItemStack.EMPTY;
        set(i, ItemStack.EMPTY);
        return s;
    }

    @Override
    public void setItem(int i, ItemStack stack) {
        if (!valid(i)) return;
        set(i, stack);
        setChanged();
    }

    @Override
    public void setChanged() {
        target.getInventory().setChanged();
        target.inventoryMenu.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player admin) {
        return target.isAlive() && !target.isRemoved();
    }

    @Override
    public boolean canPlaceItem(int i, ItemStack stack) {
        return valid(i);
    }

    @Override
    public void clearContent() {
    }
}
