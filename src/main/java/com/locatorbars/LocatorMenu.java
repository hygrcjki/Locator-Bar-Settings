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
    private final List<GroupService.Group> adminGroups = new ArrayList<>();
    private UUID selectedPlayer;
    private int selectedGroupIndex;

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
        contents.clearContent(); onlinePlayers.clear(); adminGroups.clear();
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
        adminGroups.addAll(LocatorBarsMod.GROUPS.groups());
        if (selectedGroupIndex >= adminGroups.size()) selectedGroupIndex = Math.max(0, adminGroups.size() - 1);
        String groupName = selectedGroup() == null ? "No group selected" : selectedGroup().name();
        String playerName = selectedPlayer == null ? "No player selected" : playerName(selectedPlayer);
        int slot = 0;
        for (ServerPlayer player : viewer.level().getServer().getPlayerList().getPlayers()) {
            if (slot >= 18) break;
            UUID id = player.getUUID(); onlinePlayers.add(id);
            put(slot++, Items.PLAYER_HEAD, (id.equals(selectedPlayer) ? "> " : "") + player.getName().getString(), "Click to select this player.");
        }
        put(18, Items.ARROW, "Previous group", groupName);
        put(19, Items.ARROW, "Next group", groupName);
        put(20, Items.COMPASS, "Selected group", groupName);
        put(21, Items.PLAYER_HEAD, "Selected player", playerName);
        put(22, Items.PAPER, "Toggle command access", playerName);
        put(23, Items.BARRIER, "Toggle group restriction", playerName);
        put(24, Items.EMERALD, "Force add to group", playerName + " -> " + groupName);
        put(25, Items.REDSTONE, "Force kick from group", playerName + " from " + groupName);
        put(26, Items.TNT, "Disband selected group", groupName);
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
        if (operator) clickOperator(slot); else clickPlayer(slot);
    }
    private void clickPlayer(int slot) {
        if (slot == 12) { LocatorBarsMod.result(viewer, LocatorBarsMod.GROUPS.leave(viewer.getUUID())); refresh(); return; }
        if (slot >= 0 && slot < 9) {
            List<GroupService.Group> invites = new ArrayList<>(LocatorBarsMod.GROUPS.invitationsFor(viewer.getUUID()));
            if (slot < invites.size()) LocatorBarsMod.result(viewer, LocatorBarsMod.GROUPS.accept(invites.get(slot).name(), viewer.getUUID()));
            refresh();
        }
    }
    private void clickOperator(int slot) {
        if (slot < 18) {
            if (slot < onlinePlayers.size()) {
                selectedPlayer = onlinePlayers.get(slot);
                LocatorBarsMod.success(viewer, "Selected " + playerName(selectedPlayer) + ".");
            }
        } else if (slot == 18 && !adminGroups.isEmpty()) {
            selectedGroupIndex = Math.floorMod(selectedGroupIndex - 1, adminGroups.size());
        } else if (slot == 19 && !adminGroups.isEmpty()) {
            selectedGroupIndex = (selectedGroupIndex + 1) % adminGroups.size();
        } else if (slot == 22 && selectedPlayer != null) {
            boolean allowed = LocatorBarsMod.GROUPS.isCommandDenied(selectedPlayer);
            LocatorBarsMod.GROUPS.setCommandAccess(selectedPlayer, allowed);
            LocatorBarsMod.success(viewer, allowed ? "Command access granted." : "Command access denied.");
        } else if (slot == 23 && selectedPlayer != null) {
            boolean blocked = !LocatorBarsMod.GROUPS.isGroupBlocked(selectedPlayer);
            LocatorBarsMod.GROUPS.setGroupBlocked(selectedPlayer, blocked);
            LocatorBarsMod.success(viewer, blocked ? "Player blocked from groups." : "Player unblocked from groups.");
        } else if (slot == 24 && selectedPlayer != null && selectedGroup() != null) {
            LocatorBarsMod.result(viewer, LocatorBarsMod.GROUPS.forceAdd(selectedGroup().name(), selectedPlayer));
        } else if (slot == 25 && selectedPlayer != null && selectedGroup() != null) {
            LocatorBarsMod.result(viewer, LocatorBarsMod.GROUPS.forceKick(selectedGroup().name(), selectedPlayer));
        } else if (slot == 26 && selectedGroup() != null) {
            LocatorBarsMod.result(viewer, LocatorBarsMod.GROUPS.disband(selectedGroup().name()));
        }
        refresh();
    }
    private GroupService.Group selectedGroup() { return adminGroups.isEmpty() ? null : adminGroups.get(selectedGroupIndex); }
    private String playerName(UUID id) {
        ServerPlayer player = viewer.level().getServer().getPlayerList().getPlayer(id);
        return player == null ? id.toString() : player.getName().getString();
    }
}
