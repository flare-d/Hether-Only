package com.example.netheronly.mixin;

import com.example.netheronly.NetherOnly;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndPortalBlock.class)
public abstract class EndPortalBlockMixin {
    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void netheronly$returnFromEndToNether(
            BlockState state, Level level, BlockPos pos, Entity entity,
            InsideBlockEffectApplier effectApplier, boolean shouldApplyEffects, CallbackInfo ci
    ) {
        if (level.isClientSide() || !level.dimension().equals(Level.END)
                || !entity.canUsePortal(false) || !(entity instanceof ServerPlayer player)
                // Preserve Minecraft's first-time End credits. The normal credit flow
                // invokes PlayerList.respawn, which is redirected to the Nether spawn.
                || !player.seenCredits) {
            return;
        }

        ServerLevel nether = player.level().getServer().getLevel(Level.NETHER);
        if (nether != null && NetherOnly.isNetherWorld(nether)) {
            NetherOnly.moveToNetherSpawn(player, nether);
            ci.cancel();
        }
    }
}
