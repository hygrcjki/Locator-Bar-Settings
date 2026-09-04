package com.locatorbars;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A vanilla generic-chest menu: it needs no client mod code or packets. */
public final class LocatorMenu extends AbstractContainerMenu {
    private final ServerPlayer viewer;
    private final boolean operator;
    private final SimpleContainer contents = new SimpleContainer(27);
    private final List<UUID> onlinePlayers = new ArrayList<>();

    private LocatorMenu(int id, Inventory inventory, ServerPlayer viewer, boolean operator) {
        super(MenuType.GENERIC_9x3, id);
        this.viewer = viewer;
        this.operator = operator;
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            addSlot(new Slot(contents, column + row * 9, 8 + column * 18, 18 + row * 18));
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 86 + row * 18));
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 8 + column * 18, 144));
        refresh();
    }

    public static void openPlayerMenu(ServerPlayer player) { player.openMenu(provider(player, false)); }
    public static void openOperatorMenu(ServerPlayer player) { player.openMenu(provider(player, true)); }
    private static MenuProvider provider(ServerPlayer viewer, boolean operator) {
        return new MenuProvider() {
            @Override public Component getDisplayName() { return Component.literal(operator ? "Locator Bars Admin" : "Locator Group"); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new LocatorMenu(id, inventory, viewer, operator); }
        };
    }

    private void refresh() {
        contents.clearContent(); onlinePlayers.clear();
        if (operator) refreshOperator(); else refreshPlayer();
        broadcastChanges();
    }
    private void refreshPlayer() {
        GroupService.Group group = LocatorBarsMod.GROUPS.groupOf(viewer.getUUID()).orElse(null);
        if (group == null) put(10, Items.BARRIER, "No group", "Use /locator create <name> or accept an invitation.");
        else {
            put(10, Items.COMPASS, group.name() + " (" + group.members().size() + "/10)", "Your current locator group");
            put(12, Items.OAK_DOOR, "Leave group", "Click to leave. Owners disband their group.");
        }
        int slot = 0;
        for (GroupService.Group invitation : LocatorBarsMod.GROUPS.invitationsFor(viewer.getUUID())) {
            if (slot >= 9) break;
            put(slot++, Items.WRITABLE_BOOK, "Accept " + invitation.name(), "Click to accept this invitation.");
        }
        put(26, Items.BARRIER, "Close", "Close this menu");
    }
    private void refreshOperator() {
        put(18, Items.PAPER, "Admin controls", "Left click a player: toggle command access.");
        put(19, Items.BARRIER, "Group restriction", "Right click a player: toggle create/join block.");
        put(26, Items.COMPASS, LocatorBarsMod.GROUPS.groups().size() + " groups", "Use /locatorop add, kick, and disband for forced membership changes.");
        int slot = 0;
        for (ServerPlayer player : viewer.level().getServer().getPlayerList().getPlayers()) {
            if (slot >= 18) break;
            UUID id = player.getUUID(); onlinePlayers.add(id);
            String access = LocatorBarsMod.GROUPS.isCommandDenied(id) ? "DENIED" : "ALLOWED";
            String group = LocatorBarsMod.GROUPS.isGroupBlocked(id) ? "BLOCKED" : "JOIN OK";
            put(slot++, Items.PLAYER_HEAD, player.getName().getString(), "Left: commands " + access + " | Right: groups " + group);
        }
    }
    private void put(int slot, net.minecraft.world.item.Item item, String name, String lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(name));
        contents.setItem(slot, stack);
    }

    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return player == viewer && viewer.isAlive(); }
    @Override public void clicked(int slot, int button, ContainerInput click, Player player) {
        if (slot < 0 || slot >= 27 || player != viewer) return;
        if (operator) clickOperator(slot, button); else clickPlayer(slot);
    }
    private void clickPlayer(int slot) {
        if (slot == 12) { LocatorBarsMod.result(viewer, LocatorBarsMod.GROUPS.leave(viewer.getUUID())); refresh(); return; }
        if (slot >= 0 && slot < 9) {
            List<GroupService.Group> invites = new ArrayList<>(LocatorBarsMod.GROUPS.invitationsFor(viewer.getUUID()));
            if (slot < invites.size()) LocatorBarsMod.result(viewer, LocatorBarsMod.GROUPS.accept(invites.get(slot).name(), viewer.getUUID()));
            refresh();
        }
    }
    private void clickOperator(int slot, int button) {
        if (slot >= onlinePlayers.size() || slot >= 18) return;
        UUID target = onlinePlayers.get(slot);
        if (button == 1) {
            boolean blocked = !LocatorBarsMod.GROUPS.isGroupBlocked(target);
            LocatorBarsMod.GROUPS.setGroupBlocked(target, blocked);
            LocatorBarsMod.success(viewer, blocked ? "Player blocked from groups." : "Player unblocked from groups.");
        } else {
            boolean allowed = LocatorBarsMod.GROUPS.isCommandDenied(target);
            LocatorBarsMod.GROUPS.setCommandAccess(target, allowed);
            LocatorBarsMod.success(viewer, allowed ? "Command access granted." : "Command access denied.");
        }
        refresh();
    }
}
