package com.leclowndu93150.obituaryexchange.mixin;

import com.leclowndu93150.obituaryexchange.GraveTracker;
import de.maxhenkel.gravestone.corelib.death.Death;
import de.maxhenkel.gravestone.tileentity.GraveStoneTileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(GraveStoneTileEntity.class)
public abstract class MixinGraveStoneTileEntity extends BlockEntity {

    @Shadow
    public abstract Death getDeath();

    public MixinGraveStoneTileEntity(BlockPos pos, BlockState state) {
        super(null, pos, state);
    }

    @Inject(method = "setDeath", at = @At("TAIL"), remap = false)
    private void onSetDeath(Death death, CallbackInfo ci) {
        if (death != null && this.level != null && !this.level.isClientSide) {
            UUID deathId = death.getId();
            if (deathId != null && !deathId.equals(new UUID(0, 0))) {
                GraveTracker.getInstance(this.level.getServer()).addGrave(deathId, this.worldPosition, this.level.dimension());
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level != null && !this.level.isClientSide) {
            Death death = getDeath();
            if (death != null) {
                UUID deathId = death.getId();
                if (deathId != null && !deathId.equals(new UUID(0, 0))) {
                    GraveTracker.getInstance(this.level.getServer()).removeGrave(deathId);
                }
            }
        }
    }
}