package com.leclowndu93150.obituaryexchange;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;



@Mod(ObituaryExchange.MODID)
public class ObituaryExchange {

    public static final String MODID = "obituaryexchange";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static ObituaryExchangeConfig CONFIG;

    public ObituaryExchange() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ObituaryExchangeConfig.SPEC);
        CONFIG = ObituaryExchangeConfig.INSTANCE;

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Obituary Exchange initialized");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ExchangeCommand.register(event.getDispatcher());
    }

    public static ObituaryExchangeConfig getConfig() {
        return CONFIG;
    }
}