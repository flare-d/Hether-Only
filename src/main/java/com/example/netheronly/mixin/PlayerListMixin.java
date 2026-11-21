package com.example.netheronly.mixin;

import com.example.netheronly.NetherOnly;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Inject(method = "placeNewPlayer", at = @At("TAIL"))
    private void netheronly$spawnNewPlayerInNether(
            Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci
    ) {
        // Also correct saved player data from older versions if it points at the
        // technical Overworld dimension. Players already in the End are left alone.
        if (!player.level().dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
            return;
        }
        ServerLevel nether = player.level().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        if (nether != null && NetherOnly.isNetherWorld(nether)) {
            NetherOnly.moveToNetherSpawn(player, nether);
        }
    }

    @Inject(method = "respawn", at = @At("RETURN"))
    private void netheronly$respawnPlayerInNether(
            ServerPlayer oldPlayer, boolean alive, Entity.RemovalReason reason,
            CallbackInfoReturnable<ServerPlayer> cir
    ) {
        ServerPlayer respawned = cir.getReturnValue();
        if (respawned == null) {
            return;
        }
        ServerLevel nether = respawned.level().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        if (nether != null && NetherOnly.isNetherWorld(nether)) {
            NetherOnly.moveToNetherSpawn(respawned, nether);
        }
    }
}
