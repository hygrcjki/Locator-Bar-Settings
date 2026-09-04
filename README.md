# Customizable Locator Bars

A server-side Fabric 26.2 mod for managing private locator-bar groups. Install the mod and Fabric API on the server only; unmodded 26.2 clients can join normally.

## Player commands

`/locator create <name>` creates a group (maximum 10 members, including its owner).

`/locator invite <player>` sends an invitation to a player. `/locator accept <group>` and `/locator decline <group>` answer it.

`/locator leave`, `/locator info`, and `/locator gui` leave, inspect, or open the vanilla chest-style group menu. The menu offers the actions that are available without entering a player name; invitations are still sent with the command so Minecraft can provide a player selector.

## Operator commands

`/locatorop gui` is the only operator command. Select an online player in the administration dashboard, use the arrows to select a group, then use its buttons to allow/deny commands, allow/block group use, force-add, force-kick, or disband the selected group.

Group data is stored at `config/customizable-locator-bars/groups.properties` on the server and survives restarts.
