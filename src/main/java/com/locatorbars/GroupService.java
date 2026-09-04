package com.locatorbars;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/** Server-owned group and access data. All calls are made on the server thread. */
public final class GroupService {
    public static final int MAX_MEMBERS = 10;
    private final Path file = FabricLoader.getInstance().getConfigDir()
            .resolve("customizable-locator-bars").resolve("groups.properties");
    private final Map<String, Group> groups = new HashMap<>();
    private final Set<UUID> deniedCommands = new HashSet<>();
    private final Set<UUID> blockedFromGroups = new HashSet<>();

    public void load() {
        groups.clear(); deniedCommands.clear(); blockedFromGroups.clear();
        if (!Files.exists(file)) return;
        Properties data = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            data.load(input);
            split(data.getProperty("denied", "")).forEach(value -> deniedCommands.add(UUID.fromString(value)));
            split(data.getProperty("blocked", "")).forEach(value -> blockedFromGroups.add(UUID.fromString(value)));
            for (String id : split(data.getProperty("groups", ""))) {
                String prefix = "group." + id + ".";
                UUID owner = UUID.fromString(data.getProperty(prefix + "owner"));
                LinkedHashSet<UUID> members = new LinkedHashSet<>();
                split(data.getProperty(prefix + "members", "")).forEach(value -> members.add(UUID.fromString(value)));
                members.add(owner);
                Set<UUID> invites = new HashSet<>();
                split(data.getProperty(prefix + "invites", "")).forEach(value -> invites.add(UUID.fromString(value)));
                groups.put(id, new Group(data.getProperty(prefix + "name", id), owner, members, invites));
            }
        } catch (IOException | IllegalArgumentException error) {
            LocatorBarsMod.LOGGER.error("Could not load locator group data from {}", file, error);
        }
    }

    public void save() {
        Properties data = new Properties();
        data.setProperty("denied", join(deniedCommands));
        data.setProperty("blocked", join(blockedFromGroups));
        data.setProperty("groups", String.join(",", groups.keySet()));
        for (Map.Entry<String, Group> entry : groups.entrySet()) {
            String prefix = "group." + entry.getKey() + ".";
            Group group = entry.getValue();
            data.setProperty(prefix + "name", group.name());
            data.setProperty(prefix + "owner", group.owner().toString());
            data.setProperty(prefix + "members", join(group.members()));
            data.setProperty(prefix + "invites", join(group.invites()));
        }
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream output = Files.newOutputStream(file)) {
                data.store(output, "Customizable Locator Bars server data");
            }
        } catch (IOException error) {
            LocatorBarsMod.LOGGER.error("Could not save locator group data to {}", file, error);
        }
    }

    public boolean canUse(UUID player) { return !deniedCommands.contains(player); }
    public boolean canJoinOrCreate(UUID player) { return canUse(player) && !blockedFromGroups.contains(player); }
    public boolean isCommandDenied(UUID player) { return deniedCommands.contains(player); }
    public boolean isGroupBlocked(UUID player) { return blockedFromGroups.contains(player); }
    public void setCommandAccess(UUID player, boolean allowed) { change(deniedCommands, player, !allowed); }
    public void setGroupBlocked(UUID player, boolean blocked) { change(blockedFromGroups, player, blocked); }

    public Optional<Group> group(String name) { return Optional.ofNullable(groups.get(normalize(name))); }
    public Optional<Group> groupOf(UUID player) { return groups.values().stream().filter(g -> g.members().contains(player)).findFirst(); }
    public Collection<Group> groups() { return Collections.unmodifiableCollection(groups.values()); }
    public Collection<Group> invitationsFor(UUID player) { return groups.values().stream().filter(g -> g.invites().contains(player)).toList(); }

    public Result create(String name, UUID owner) {
        String id = normalize(name);
        if (!id.matches("[a-z0-9_-]{3,16}")) return Result.error("Group names must be 3-16 letters, numbers, _ or -.");
        if (groups.containsKey(id)) return Result.error("That group name is already in use.");
        if (groupOf(owner).isPresent()) return Result.error("You are already in a group.");
        groups.put(id, new Group(name, owner, new LinkedHashSet<>(Set.of(owner)))); save();
        return Result.ok("Created group " + name + ".");
    }

    public Result invite(String name, UUID inviter, UUID invited) {
        Group group = groups.get(normalize(name));
        if (group == null) return Result.error("That group does not exist.");
        if (!group.members().contains(inviter)) return Result.error("Only a group member may invite players.");
        if (group.members().size() >= MAX_MEMBERS) return Result.error("This group already has 10 members.");
        if (groupOf(invited).isPresent()) return Result.error("That player is already in a group.");
        group.invites().add(invited); save();
        return Result.ok("Invitation sent.");
    }

    public Result accept(String name, UUID player) {
        Group group = groups.get(normalize(name));
        if (group == null || !group.invites().remove(player)) return Result.error("You do not have an invitation to that group.");
        if (groupOf(player).isPresent()) return Result.error("You are already in a group.");
        if (group.members().size() >= MAX_MEMBERS) return Result.error("This group is now full.");
        group.members().add(player); save();
        return Result.ok("You joined " + group.name() + ".");
    }

    public Result decline(String name, UUID player) {
        Group group = groups.get(normalize(name));
        if (group == null || !group.invites().remove(player)) return Result.error("You do not have an invitation to that group.");
        save(); return Result.ok("Invitation declined.");
    }

    public Result leave(UUID player) { return remove(groupOf(player).orElse(null), player, false); }
    public Result forceAdd(String name, UUID player) {
        Group group = groups.get(normalize(name));
        if (group == null) return Result.error("That group does not exist.");
        if (groupOf(player).isPresent()) return Result.error("That player is already in a group.");
        if (group.members().size() >= MAX_MEMBERS) return Result.error("This group already has 10 members.");
        group.members().add(player); save(); return Result.ok("Player added to " + group.name() + ".");
    }
    public Result forceKick(String name, UUID player) { return remove(groups.get(normalize(name)), player, true); }
    public Result disband(String name) {
        Group removed = groups.remove(normalize(name));
        if (removed == null) return Result.error("That group does not exist.");
        save(); return Result.ok("Disbanded " + removed.name() + ".");
    }

    private Result remove(Group group, UUID player, boolean force) {
        if (group == null || !group.members().contains(player)) return Result.error("That player is not in a group.");
        if (group.owner().equals(player)) {
            groups.remove(normalize(group.name())); save();
            return Result.ok(force ? "Owner kicked; group disbanded." : "You left and disbanded " + group.name() + ".");
        }
        group.members().remove(player); save(); return Result.ok(force ? "Player removed from " + group.name() + "." : "You left " + group.name() + ".");
    }

    private static void change(Set<UUID> set, UUID id, boolean include) { if (include) set.add(id); else set.remove(id); }
    private static String normalize(String name) { return name.toLowerCase(Locale.ROOT); }
    private static Collection<String> split(String value) { return value.isBlank() ? ListHolder.EMPTY : java.util.List.of(value.split(",")); }
    private static String join(Collection<UUID> values) { return values.stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(",")); }
    private static final class ListHolder { private static final Collection<String> EMPTY = Collections.emptyList(); }

    public record Group(String name, UUID owner, LinkedHashSet<UUID> members, Set<UUID> invites) {
        public Group(String name, UUID owner, LinkedHashSet<UUID> members) { this(name, owner, members, new HashSet<>()); }
    }
    public record Result(boolean success, String message) {
        static Result ok(String message) { return new Result(true, message); }
        static Result error(String message) { return new Result(false, message); }
    }
}
