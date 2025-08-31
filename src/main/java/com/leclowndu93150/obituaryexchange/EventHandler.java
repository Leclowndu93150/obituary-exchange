package com.leclowndu93150.obituaryexchange;

import de.maxhenkel.gravestone.tileentity.GraveStoneTileEntity;
import de.maxhenkel.gravestone.corelib.death.Death;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ObituaryExchange.MODID)
public class EventHandler {
    
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() == null || event.getLevel().isClientSide()) {
            return;
        }
        
        BlockEntity blockEntity = event.getLevel().getBlockEntity(event.getPos());
        if (blockEntity instanceof GraveStoneTileEntity gravestone) {
            Death death = gravestone.getDeath();
            if (death != null && death.getId() != null) {
                GraveTracker tracker = GraveTracker.getInstance(event.getPlayer().getServer());
                tracker.markGraveClaimed(death.getId(), event.getPlayer());
                ObituaryExchange.LOGGER.info("Player {} claimed grave with death ID {}", 
                    event.getPlayer().getName().getString(), death.getId());
            }
        }
    }
}