package com.locatorbars;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Vanilla chest menus, so players do not need a client-side mod. */
public final class LocatorMenu extends AbstractContainerMenu {
    private static final int LIST_SIZE = 18;
    private final ServerPlayer viewer;
    private final boolean operator;
    private final SimpleContainer contents = new SimpleContainer(27);
    private Page page;
    private int groupPage;
    private int playerPage;
    private GroupService.Group selectedGroup;
    private UUID selectedPlayer;

    private LocatorMenu(int id, Inventory inventory, ServerPlayer viewer, boolean operator) {
        super(MenuType.GENERIC_9x3, id);
        this.viewer = viewer;
        this.operator = operator;
        this.page = operator ? Page.ADMIN_GROUPS : Page.PLAYER_GROUP;
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) addSlot(new Slot(contents, column + row * 9, 8 + column * 18, 18 + row * 18));
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 86 + row * 18));
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
        contents.clearContent();
        switch (page) {
            case PLAYER_GROUP -> playerGroupPage();
            case ADMIN_GROUPS -> adminGroupsPage();
            case ADMIN_MEMBERS -> adminMembersPage();
            case ADMIN_ADD_PLAYER -> adminAddPlayerPage();
        }
        broadcastChanges();
    }
    private void playerGroupPage() {
        GroupService.Group group = LocatorBarsMod.GROUPS.groupOf(viewer.getUUID()).orElse(null);
        if (group == null) {
            put(10, Items.BARRIER, "No group", "Use /locator create <name> or accept an invitation.");
            int slot = 0;
            for (GroupService.Group invite : LocatorBarsMod.GROUPS.invitationsFor(viewer.getUUID())) {
                if (slot >= LIST_SIZE) break;
                put(slot++, Items.WRITABLE_BOOK, "Accept " + invite.name(), "Click to join this group.");
            }
            return;
        }
        put(22, Items.COMPASS, group.name() + " (" + group.members().size() + "/10)", "Group members are listed above.");
        int slot = 0;
        for (UUID member : group.members()) {
            if (slot >= LIST_SIZE) break;
            boolean online = online(member) != null;
            String name = playerName(member) + (member.equals(group.owner()) ? " (owner)" : "");
            put(slot++, online ? Items.PLAYER_HEAD : Items.PAPER, name, online ? "Online" : "Offline");
        }
        put(24, Items.OAK_DOOR, "Leave group", group.owner().equals(viewer.getUUID()) ? "Leaving disbands the group." : "Leave this group.");
        if (group.owner().equals(viewer.getUUID())) put(25, Items.REDSTONE, "Kick selected member", "Click a member first, then click here.");
        if (selectedPlayer != null) put(26, Items.PLAYER_HEAD, "Selected: " + playerName(selectedPlayer), "Choose another member or kick them.");
    }
    private void adminGroupsPage() {
        List<GroupService.Group> groups = groups();
        int start = groupPage * LIST_SIZE;
        for (int i = 0; i < LIST_SIZE && start + i < groups.size(); i++) {
            GroupService.Group group = groups.get(start + i);
            put(i, Items.COMPASS, group.name() + " (" + group.members().size() + "/10)", "Click to view this group's players.");
        }
        put(18, Items.ARROW, "Previous page", "Group page " + (groupPage + 1));
        put(19, Items.ARROW, "Next page", "Group page " + (groupPage + 1));
    }
    private void adminMembersPage() {
        if (!groupStillExists()) { page = Page.ADMIN_GROUPS; selectedGroup = null; refresh(); return; }
        put(18, Items.ARROW, "Back to groups", "Return to the group list.");
        int slot = 0;
        for (UUID member : selectedGroup.members()) {
            if (slot >= LIST_SIZE) break;
            boolean online = online(member) != null;
            put(slot++, online ? Items.PLAYER_HEAD : Items.PAPER, (member.equals(selectedPlayer) ? "> " : "") + playerName(member), online ? "Online — click to select." : "Offline — click to select.");
        }
        put(20, Items.PAPER, "Toggle command access", selectedPlayer == null ? "Select a player first." : playerName(selectedPlayer));
        put(21, Items.BARRIER, "Toggle group restriction", selectedPlayer == null ? "Select a player first." : playerName(selectedPlayer));
        put(24, Items.EMERALD, "Force add to group", "Open online-player selection.");
        put(25, Items.REDSTONE, "Force kick from group", selectedPlayer == null ? "Select a member first." : playerName(selectedPlayer));
        put(26, Items.TNT, "Disband group", selectedGroup.name());
    }
    private void adminAddPlayerPage() {
        if (!groupStillExists()) { page = Page.ADMIN_GROUPS; selectedGroup = null; refresh(); return; }
        List<ServerPlayer> players = onlinePlayers();
        int start = playerPage * LIST_SIZE;
        for (int i = 0; i < LIST_SIZE && start + i < players.size(); i++) {
            ServerPlayer player = players.get(start + i);
            put(i, Items.PLAYER_HEAD, player.getName().getString(), "Click to force-add to " + selectedGroup.name() + ".");
        }
        put(18, Items.ARROW, "Previous page", "Online player page " + (playerPage + 1));
        put(19, Items.ARROW, "Next page", "Online player page " + (playerPage + 1));
        put(26, Items.OAK_DOOR, "Back to group", selectedGroup.name());
    }
    private void put(int slot, Item item, String name, String lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        contents.setItem(slot, stack);
    }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return player == viewer && viewer.isAlive(); }
    @Override public void clicked(int slot, int button, ContainerInput click, Player player) {
        if (slot < 0 || slot >= 27 || player != viewer) return;
        if (operator) clickAdmin(slot); else clickPlayer(slot);
        refresh();
    }
    private void clickPlayer(int slot) {
        GroupService.Group group = LocatorBarsMod.GROUPS.groupOf(viewer.getUUID()).orElse(null);
        if (group == null) {
            List<GroupService.Group> invites = new ArrayList<>(LocatorBarsMod.GROUPS.invitationsFor(viewer.getUUID()));
            if (slot < invites.size()) LocatorBarsMod.result(viewer, LocatorBarsMod.GROUPS.accept(invites.get(slot).name(), viewer.getUUID()));
            return;
        }
        if (slot < group.members().size()) selectedPlayer = new ArrayList<>(group.members()).get(slot);
        else if (slot == 24) LocatorBarsMod.result(viewer, LocatorBarsMod.GROUPS.leave(viewer.getUUID()));
        else if (slot == 25 && group.owner().equals(viewer.getUUID()) && selectedPlayer != null && !selectedPlayer.equals(viewer.getUUID())) LocatorBarsMod.result(viewer, LocatorBarsMod.GROUPS.forceKick(group.name(), selectedPlayer));
    }
    private void clickAdmin(int slot) {
        if (page == Page.ADMIN_GROUPS) {
            List<GroupService.Group> groups = groups(); int index = groupPage * LIST_SIZE + slot;
            if (slot < LIST_SIZE && index < groups.size()) { selectedGroup = groups.get(index); selectedPlayer = null; page = Page.ADMIN_MEMBERS; }
            else if (slot == 18 && groupPage > 0) groupPage--;
            else if (slot == 19 && (groupPage + 1) * LIST_SIZE < groups.size()) groupPage++;
        } else if (page == Page.ADMIN_MEMBERS) {
            List<UUID> members = new ArrayList<>(selectedGroup.members());
            if (slot < members.size()) selectedPlayer = members.get(slot);
            else if (slot == 18) page = Page.ADMIN_GROUPS;
            else if (slot == 20 && selectedPlayer != null) toggleAccess(selectedPlayer);
            else if (slot == 21 && selectedPlayer != null) toggleBlocked(selectedPlayer);
            else if (slot == 24) { page = Page.ADMIN_ADD_PLAYER; playerPage = 0; }
            else if (slot == 25 && selectedPlayer != null) LocatorBarsMod.result(viewer, LocatorBarsMod.GROUPS.forceKick(selectedGroup.name(), selectedPlayer));
            else if (slot == 26) LocatorBarsMod.result(viewer, LocatorBarsMod.GROUPS.disband(selectedGroup.name()));
        } else if (page == Page.ADMIN_ADD_PLAYER) {
            List<ServerPlayer> players = onlinePlayers(); int index = playerPage * LIST_SIZE + slot;
            if (slot < LIST_SIZE && index < players.size()) LocatorBarsMod.result(viewer, LocatorBarsMod.GROUPS.forceAdd(selectedGroup.name(), players.get(index).getUUID()));
            else if (slot == 18 && playerPage > 0) playerPage--;
            else if (slot == 19 && (playerPage + 1) * LIST_SIZE < players.size()) playerPage++;
            else if (slot == 26) page = Page.ADMIN_MEMBERS;
        }
    }
    private void toggleAccess(UUID player) { boolean allowed = LocatorBarsMod.GROUPS.isCommandDenied(player); LocatorBarsMod.GROUPS.setCommandAccess(player, allowed); LocatorBarsMod.success(viewer, allowed ? "Command access granted." : "Command access denied."); }
    private void toggleBlocked(UUID player) { boolean blocked = !LocatorBarsMod.GROUPS.isGroupBlocked(player); LocatorBarsMod.GROUPS.setGroupBlocked(player, blocked); LocatorBarsMod.success(viewer, blocked ? "Player blocked from groups." : "Player unblocked from groups."); }
    private List<GroupService.Group> groups() { return LocatorBarsMod.GROUPS.groups().stream().sorted(Comparator.comparing(GroupService.Group::name)).toList(); }
    private List<ServerPlayer> onlinePlayers() { return viewer.level().getServer().getPlayerList().getPlayers().stream().sorted(Comparator.comparing(p -> p.getName().getString())).toList(); }
    private boolean groupStillExists() { return selectedGroup != null && LocatorBarsMod.GROUPS.group(selectedGroup.name()).isPresent(); }
    private ServerPlayer online(UUID id) { return viewer.level().getServer().getPlayerList().getPlayer(id); }
    private String playerName(UUID id) { ServerPlayer player = online(id); return player == null ? id.toString().substring(0, 8) : player.getName().getString(); }
    private enum Page { PLAYER_GROUP, ADMIN_GROUPS, ADMIN_MEMBERS, ADMIN_ADD_PLAYER }
}
