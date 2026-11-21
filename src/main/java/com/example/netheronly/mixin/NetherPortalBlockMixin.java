package com.example.netheronly.mixin;

import com.example.netheronly.NetherOnly;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetherPortalBlock.class)
public abstract class NetherPortalBlockMixin {
    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void netheronly$disableNetherPortals(
            BlockState state, Level level, BlockPos pos, Entity entity,
            InsideBlockEffectApplier effectApplier, boolean shouldApplyEffects, CallbackInfo ci
    ) {
        if (!level.isClientSide() && level instanceof ServerLevel serverLevel
                && NetherOnly.isNetherWorld(serverLevel)) {
            ci.cancel();
        }
    }
}
