package com.leclowndu93150.obituaryexchange;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.player.Player;
import de.maxhenkel.gravestone.tileentity.GraveStoneTileEntity;
import de.maxhenkel.gravestone.corelib.death.Death;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GraveTracker extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DATA_NAME = "obituaryexchange_graves";
    
    private final Map<UUID, GraveLocation> graveLocations = new HashMap<>();
    
    public static class GraveLocation {
        public final BlockPos pos;
        public final ResourceKey<Level> dimension;
        
        public GraveLocation(BlockPos pos, ResourceKey<Level> dimension) {
            this.pos = pos;
            this.dimension = dimension;
        }
        
        public CompoundTag toNbt() {
            CompoundTag tag = new CompoundTag();
            tag.put("pos", NbtUtils.writeBlockPos(pos));
            tag.putString("dimension", dimension.location().toString());
            return tag;
        }
        
        public static GraveLocation fromNbt(CompoundTag tag) {
            BlockPos pos = NbtUtils.readBlockPos(tag.getCompound("pos"));
            ResourceLocation dimLoc = new ResourceLocation(tag.getString("dimension"));
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimLoc);
            return new GraveLocation(pos, dimension);
        }
    }
    
    public static GraveTracker getInstance(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        return overworld.getDataStorage().computeIfAbsent(
            GraveTracker::load,
            GraveTracker::new,
            DATA_NAME
        );
    }
    
    public void addGrave(UUID deathId, BlockPos pos, ResourceKey<Level> dimension) {
        graveLocations.put(deathId, new GraveLocation(pos, dimension));
        setDirty();
        LOGGER.debug("Added grave tracking for death ID {} at {} in {}", deathId, pos, dimension.location());
    }
    
    public void removeGrave(UUID deathId) {
        GraveLocation removed = graveLocations.remove(deathId);
        if (removed != null) {
            setDirty();
            LOGGER.debug("Removed grave tracking for death ID {}", deathId);
        }
    }
    
    @Nullable
    public GraveLocation getGraveLocation(UUID deathId) {
        return graveLocations.get(deathId);
    }
    
    public boolean hasGrave(UUID deathId) {
        return graveLocations.containsKey(deathId);
    }
    
    public boolean isGraveStillInWorld(UUID deathId, MinecraftServer server) {
        GraveLocation location = graveLocations.get(deathId);
        if (location == null) {
            return false;
        }
        
        ServerLevel level = server.getLevel(location.dimension);
        if (level == null) {
            return false;
        }
        
        if (!level.isLoaded(location.pos)) {
            return true;
        }
        
        BlockEntity blockEntity = level.getBlockEntity(location.pos);
        if (blockEntity instanceof GraveStoneTileEntity gravestone) {
            Death death = gravestone.getDeath();
            if (death != null && death.getId() != null && death.getId().equals(deathId)) {
                return true;
            }
        }
        
        return false;
    }
    
    public void markGraveClaimed(UUID deathId, Player breaker) {
        removeGrave(deathId);
        ExchangeDataManager.markDeathAsRefunded(deathId);
        LOGGER.info("Grave claimed for death ID {} by player {}", deathId, breaker.getName().getString());
    }
    
    @Nullable
    public GraveLocation findNearbyGraveForPlayer(MinecraftServer server, UUID playerUUID, BlockPos playerPos, ResourceKey<Level> dimension, int radius) {
        for (Map.Entry<UUID, GraveLocation> entry : graveLocations.entrySet()) {
            GraveLocation location = entry.getValue();
            
            if (!location.dimension.equals(dimension)) {
                continue;
            }
            
            ServerLevel level = server.getLevel(location.dimension);
            if (level == null || !level.isLoaded(location.pos)) {
                continue;
            }
            
            BlockEntity blockEntity = level.getBlockEntity(location.pos);
            if (blockEntity instanceof GraveStoneTileEntity gravestone) {
                Death death = gravestone.getDeath();
                if (death != null && death.getPlayerUUID().equals(playerUUID)) {
                    double distance = Math.sqrt(playerPos.distSqr(location.pos));
                    if (distance <= radius) {
                        return location;
                    }
                }
            }
        }
        return null;
    }
    
    @Override
    public CompoundTag save(CompoundTag compound) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, GraveLocation> entry : graveLocations.entrySet()) {
            CompoundTag graveTag = new CompoundTag();
            graveTag.putUUID("deathId", entry.getKey());
            graveTag.put("location", entry.getValue().toNbt());
            list.add(graveTag);
        }
        compound.put("graves", list);
        return compound;
    }
    
    public static GraveTracker load(CompoundTag compound) {
        GraveTracker tracker = new GraveTracker();
        ListTag list = compound.getList("graves", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag graveTag = list.getCompound(i);
            UUID deathId = graveTag.getUUID("deathId");
            GraveLocation location = GraveLocation.fromNbt(graveTag.getCompound("location"));
            tracker.graveLocations.put(deathId, location);
        }
        LOGGER.info("Loaded {} grave locations from saved data", tracker.graveLocations.size());
        return tracker;
    }
}