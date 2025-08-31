package com.leclowndu93150.obituaryexchange;

import net.minecraftforge.common.ForgeConfigSpec;

public class ObituaryExchangeConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ObituaryExchangeConfig INSTANCE;

    public final ForgeConfigSpec.IntValue cooldownMinutes;
    public final ForgeConfigSpec.BooleanValue requireEmptyInventory;
    public final ForgeConfigSpec.BooleanValue dropExcessItems;
    public final ForgeConfigSpec.BooleanValue consumeObituary;
    public final ForgeConfigSpec.BooleanValue allowMultipleExchanges;
    public final ForgeConfigSpec.BooleanValue notifyAdmins;

    public final ForgeConfigSpec.IntValue graveSearchRadius;
    public final ForgeConfigSpec.BooleanValue breakGraveOnExchange;
    public final ForgeConfigSpec.BooleanValue searchAllLoadedChunks;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        INSTANCE = new ObituaryExchangeConfig(builder);
        SPEC = builder.build();
    }

    private ObituaryExchangeConfig(ForgeConfigSpec.Builder builder) {
        builder.push("general");

        cooldownMinutes = builder
                .comment("Cooldown in minutes between exchanges (0 to disable)")
                .defineInRange("cooldown_minutes", 30, 0, Integer.MAX_VALUE);

        requireEmptyInventory = builder
                .comment("Require player to have empty inventory slots to exchange")
                .define("require_empty_inventory", false);

        dropExcessItems = builder
                .comment("Drop items that don't fit in inventory on the ground")
                .define("drop_excess_items", true);

        consumeObituary = builder
                .comment("Remove the obituary item after successful exchange")
                .define("consume_obituary", true);

        allowMultipleExchanges = builder
                .comment("Allow players to exchange the same obituary multiple times")
                .define("allow_multiple_exchanges", false);

        notifyAdmins = builder
                .comment("Notify online admins when a player exchanges an obituary")
                .define("notify_admins", true);

        builder.pop();
        
        builder.push("graves");
        
        graveSearchRadius = builder
                .comment("How far to search for graves when exchanging (in blocks, 0 to disable local search)")
                .defineInRange("grave_search_radius", 50, 0, 256);
        
        breakGraveOnExchange = builder
                .comment("Whether to automatically break graves when items are exchanged")
                .define("break_grave_on_exchange", true);
        
        searchAllLoadedChunks = builder
                .comment("Fallback to search all loaded chunks if grave not found nearby (can be performance intensive)")
                .define("search_all_loaded_chunks", false);
        
        builder.pop();
    }
}