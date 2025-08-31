package com.leclowndu93150.obituaryexchange;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ExchangeDataManager extends SavedData {

    private static final String DATA_NAME = "obituary_exchange_data";

    private final Map<UUID, Long> playerCooldowns = new HashMap<>();
    private final Set<UUID> exchangedDeaths = new HashSet<>();
    private final Set<UUID> refundedDeaths = new HashSet<>();

    public static ExchangeDataManager get(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();
        return storage.computeIfAbsent(ExchangeDataManager::load, ExchangeDataManager::new, DATA_NAME);
    }

    public boolean hasCooldown(UUID playerId) {
        if (!playerCooldowns.containsKey(playerId)) {
            return false;
        }

        long lastUse = playerCooldowns.get(playerId);
        long currentTime = System.currentTimeMillis();
        long cooldownMs = ObituaryExchange.getConfig().cooldownMinutes.get() * 60 * 1000L;

        if (currentTime - lastUse >= cooldownMs) {
            playerCooldowns.remove(playerId);
            setDirty();
            return false;
        }

        return true;
    }

    public long getRemainingCooldown(UUID playerId) {
        if (!playerCooldowns.containsKey(playerId)) {
            return 0;
        }

        long lastUse = playerCooldowns.get(playerId);
        long currentTime = System.currentTimeMillis();
        long cooldownMs = ObituaryExchange.getConfig().cooldownMinutes.get() * 60 * 1000L;
        long remaining = cooldownMs - (currentTime - lastUse);

        return Math.max(0, remaining);
    }

    public void setCooldown(UUID playerId) {
        playerCooldowns.put(playerId, System.currentTimeMillis());
        setDirty();
    }

    public void resetCooldown(UUID playerId) {
        playerCooldowns.remove(playerId);
        setDirty();
    }

    public void resetAllCooldowns() {
        playerCooldowns.clear();
        setDirty();
    }

    public boolean hasExchanged(UUID deathId) {
        return exchangedDeaths.contains(deathId);
    }

    public void markExchanged(UUID deathId) {
        exchangedDeaths.add(deathId);
        setDirty();
    }
    
    public boolean isDeathRefunded(UUID deathId) {
        return refundedDeaths.contains(deathId);
    }
    
    public static void markDeathAsRefunded(UUID deathId) {
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ExchangeDataManager dataManager = get(server);
            dataManager.refundedDeaths.add(deathId);
            dataManager.setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag compound) {
        ListTag cooldownList = new ListTag();
        for (Map.Entry<UUID, Long> entry : playerCooldowns.entrySet()) {
            CompoundTag cooldownTag = new CompoundTag();
            cooldownTag.putUUID("PlayerID", entry.getKey());
            cooldownTag.putLong("LastUse", entry.getValue());
            cooldownList.add(cooldownTag);
        }
        compound.put("Cooldowns", cooldownList);

        ListTag exchangedList = new ListTag();
        for (UUID deathId : exchangedDeaths) {
            exchangedList.add(NbtUtils.createUUID(deathId));
        }
        compound.put("ExchangedDeaths", exchangedList);
        
        ListTag refundedList = new ListTag();
        for (UUID deathId : refundedDeaths) {
            refundedList.add(NbtUtils.createUUID(deathId));
        }
        compound.put("RefundedDeaths", refundedList);

        return compound;
    }

    public static ExchangeDataManager load(CompoundTag compound) {
        ExchangeDataManager data = new ExchangeDataManager();

        if (compound.contains("Cooldowns")) {
            ListTag cooldownList = compound.getList("Cooldowns", 10);
            for (int i = 0; i < cooldownList.size(); i++) {
                CompoundTag cooldownTag = cooldownList.getCompound(i);
                UUID playerId = cooldownTag.getUUID("PlayerID");
                long lastUse = cooldownTag.getLong("LastUse");
                data.playerCooldowns.put(playerId, lastUse);
            }
        }

        if (compound.contains("ExchangedDeaths")) {
            ListTag exchangedList = compound.getList("ExchangedDeaths", 11);
            for (int i = 0; i < exchangedList.size(); i++) {
                data.exchangedDeaths.add(NbtUtils.loadUUID(exchangedList.get(i)));
            }
        }
        
        if (compound.contains("RefundedDeaths")) {
            ListTag refundedList = compound.getList("RefundedDeaths", 11);
            for (int i = 0; i < refundedList.size(); i++) {
                data.refundedDeaths.add(NbtUtils.loadUUID(refundedList.get(i)));
            }
        }

        return data;
    }
}