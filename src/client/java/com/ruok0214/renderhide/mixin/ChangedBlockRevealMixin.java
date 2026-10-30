package com.ruok0214.renderhide.mixin;

import com.ruok0214.renderhide.ChangedBlockReveal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
abstract class ChangedBlockRevealMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void renderhide$changed(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> cir) {
        ChangedBlockReveal.changed(((LevelChunk)(Object)this).getLevel(), pos, cir.getReturnValue(), state);
    }
}
