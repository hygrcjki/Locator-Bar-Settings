package com.locatorbars.mixin;

import com.locatorbars.LocatorBarsMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.waypoints.WaypointTransmitter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stops the vanilla server from making locator-bar connections across groups. */
@Mixin(targets = "net.minecraft.server.waypoints.ServerWaypointManager")
public final class ServerWaypointManagerMixin {
    @Inject(method = "createConnection", at = @At("HEAD"), cancellable = true)
    private void locatorBars$onlyConnectGroupMembers(ServerPlayer receiver, WaypointTransmitter transmitter, CallbackInfo ci) {
        if (transmitter instanceof ServerPlayer source && !LocatorBarsMod.GROUPS.canShareLocator(receiver.getUUID(), source.getUUID())) ci.cancel();
    }
}
