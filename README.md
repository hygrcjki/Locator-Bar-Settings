# Customizable Locator Bars

A server-side Fabric 26.2 mod for managing private locator-bar groups. Install the mod and Fabric API on the server only; unmodded 26.2 clients can join normally.

## Player commands

`/locator create <name>` creates a group (maximum 10 members, including its owner).

`/locator invite <player>` sends an invitation to a player. `/locator accept <group>` and `/locator decline <group>` answer it.

`/locator leave`, `/locator info`, and `/locator gui` leave, inspect, or open the vanilla chest-style group menu. The menu offers the actions that are available without entering a player name; invitations are still sent with the command so Minecraft can provide a player selector.

## Operator commands

`/locatorop gui` is the only operator command. It first shows paginated groups; select one to see its members. From that page an operator can select a member, allow/deny commands, allow/block group use, force-kick, or disband. **Force add** opens a paginated list of online players.

`/locator gui` lists every group member and whether they are online. The group owner can select and kick a member.

## Locator-bar privacy

The server only creates vanilla locator-bar markers between members of the same group. Players in different groups or no group at all cannot see group members, and group members cannot see them. This is enforced server-side and applies without a client mod.

Group data is stored at `config/customizable-locator-bars/groups.properties` on the server and survives restarts.
