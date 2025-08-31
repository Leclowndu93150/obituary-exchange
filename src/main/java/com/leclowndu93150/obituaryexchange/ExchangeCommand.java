package com.leclowndu93150.obituaryexchange;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import de.maxhenkel.gravestone.corelib.death.Death;
import de.maxhenkel.gravestone.corelib.death.DeathManager;
import de.maxhenkel.gravestone.Main;
import de.maxhenkel.gravestone.tileentity.GraveStoneTileEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;

import java.util.UUID;



public class ExchangeCommand {
    
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> exchangeCommand = Commands.literal("exchangeobituary")
                .requires(source -> source.hasPermission(0))
                .executes(context -> executeExchange(context.getSource()));

        LiteralArgumentBuilder<CommandSourceStack> resetCommand = Commands.literal("resetexchangecooldown")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("all")
                        .executes(context -> resetAllCooldowns(context.getSource())))
                .executes(context -> resetOwnCooldown(context.getSource()));

        dispatcher.register(exchangeCommand);
        dispatcher.register(resetCommand);
    }

    private static int executeExchange(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players"));
            return 0;
        }

        ExchangeDataManager dataManager = ExchangeDataManager.get(player.server);
        if (ObituaryExchange.getConfig().cooldownMinutes.get() > 0 && dataManager.hasCooldown(player.getUUID())) {
            long remaining = dataManager.getRemainingCooldown(player.getUUID()) / 1000;
            long minutes = remaining / 60;
            long seconds = remaining % 60;
            source.sendFailure(Component.literal("You must wait " + minutes + " minutes and " + seconds + " seconds before exchanging another obituary")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        ItemStack obituaryStack = findObituary(player);
        if (obituaryStack.isEmpty()) {
            source.sendFailure(Component.literal("You don't have an obituary in your inventory")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        Death death = getDeathFromObituary(player, obituaryStack);
        if (death == null) {
            source.sendFailure(Component.literal("This obituary is invalid or the death data cannot be found")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        if (!ObituaryExchange.getConfig().allowMultipleExchanges.get() && dataManager.hasExchanged(death.getId())) {
            source.sendFailure(Component.literal("This obituary has already been exchanged")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        if (ObituaryExchange.getConfig().requireEmptyInventory.get()) {
            int emptySlots = 0;
            for (ItemStack stack : player.getInventory().items) {
                if (stack.isEmpty()) emptySlots++;
            }
            if (emptySlots < 5) {
                source.sendFailure(Component.literal("You need at least 5 empty inventory slots to exchange")
                        .withStyle(ChatFormatting.RED));
                return 0;
            }
        }

        boolean graveFound = findAndBreakGrave(player, death);
        if (graveFound) {
            player.sendSystemMessage(Component.literal("Found and removed the associated grave to prevent item duplication")
                    .withStyle(ChatFormatting.GRAY));
        } else if (ObituaryExchange.getConfig().breakGraveOnExchange.get()) {
            player.sendSystemMessage(Component.literal("No grave found for this death (it may have already been broken or never placed)")
                    .withStyle(ChatFormatting.GRAY));
        }

        NonNullList<ItemStack> restoredItems = Main.GRAVESTONE.get().fillPlayerInventory(player, death);

        if (!restoredItems.isEmpty()) {
            if (ObituaryExchange.getConfig().dropExcessItems.get()) {
                for (ItemStack stack : restoredItems) {
                    player.drop(stack, false);
                }
                player.sendSystemMessage(Component.literal("Some items didn't fit in your inventory and were dropped")
                        .withStyle(ChatFormatting.YELLOW));
            } else {
                source.sendFailure(Component.literal("Not enough inventory space to restore all items")
                        .withStyle(ChatFormatting.RED));
                return 0;
            }
        }

        if (ObituaryExchange.getConfig().consumeObituary.get()) {
            if (player.getItemInHand(InteractionHand.MAIN_HAND) == obituaryStack) {
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            } else if (player.getItemInHand(InteractionHand.OFF_HAND) == obituaryStack) {
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            } else {
                player.getInventory().removeItem(obituaryStack);
            }
        }

        if (!ObituaryExchange.getConfig().allowMultipleExchanges.get()) {
            dataManager.markExchanged(death.getId());
        }

        dataManager.setCooldown(player.getUUID());

        player.sendSystemMessage(Component.literal("Successfully exchanged your obituary for grave items!")
                .withStyle(ChatFormatting.GREEN));

        if (ObituaryExchange.getConfig().notifyAdmins.get()) {
            Component adminMessage = Component.literal("[ObituaryExchange] ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(player.getName().getString())
                            .withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" exchanged an obituary (Death ID: ")
                            .withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(death.getId().toString())
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(")")
                            .withStyle(ChatFormatting.GRAY));

            for (ServerPlayer admin : player.server.getPlayerList().getPlayers()) {
                if (admin.hasPermissions(2) && admin != player) {
                    admin.sendSystemMessage(adminMessage);
                }
            }
        }

        return 1;
    }

    private static ItemStack findObituary(ServerPlayer player) {
        ItemStack mainHand = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (mainHand.getItem() == Main.OBITUARY.get()) {
            return mainHand;
        }

        ItemStack offHand = player.getItemInHand(InteractionHand.OFF_HAND);
        if (offHand.getItem() == Main.OBITUARY.get()) {
            return offHand;
        }

        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() == Main.OBITUARY.get()) {
                return stack;
            }
        }

        for (ItemStack stack : player.getInventory().armor) {
            if (stack.getItem() == Main.OBITUARY.get()) {
                return stack;
            }
        }

        return ItemStack.EMPTY;
    }

    private static Death getDeathFromObituary(ServerPlayer player, ItemStack stack) {
        CompoundTag compound = stack.getTag();
        if (compound == null || !compound.contains("Death")) {
            return null;
        }
        CompoundTag deathTag = compound.getCompound("Death");
        return DeathManager.getDeath(player.serverLevel(), deathTag.getUUID("PlayerUUID"), deathTag.getUUID("DeathID"));
    }
    
    private static boolean findAndBreakGrave(ServerPlayer player, Death death) {
        UUID deathId = death.getId();
        GraveTracker tracker = GraveTracker.getInstance(player.server);

        GraveTracker.GraveLocation graveLocation = tracker.getGraveLocation(deathId);
        if (graveLocation != null) {
            ServerLevel level = player.server.getLevel(graveLocation.dimension);
            if (level != null) {
                if (tryBreakGraveAt(level, graveLocation.pos, deathId, player)) {
                    tracker.removeGrave(deathId);
                    LOGGER.info("Broke tracked grave for death ID {} at {} in {}", 
                        deathId, graveLocation.pos, graveLocation.dimension.location());
                    return true;
                }
                tracker.removeGrave(deathId);
                LOGGER.debug("Tracked grave for death ID {} not found at expected location, removing from tracker", deathId);
            }
        }

        int searchRadius = ObituaryExchange.getConfig().graveSearchRadius.get();
        if (searchRadius > 0) {
            BlockPos playerPos = player.blockPosition();
            ServerLevel playerLevel = player.serverLevel();

            for (int x = -searchRadius; x <= searchRadius; x++) {
                for (int y = -searchRadius; y <= searchRadius; y++) {
                    for (int z = -searchRadius; z <= searchRadius; z++) {
                        BlockPos checkPos = playerPos.offset(x, y, z);
                        if (tryBreakGraveAt(playerLevel, checkPos, deathId, player)) {
                            LOGGER.info("Found and broke grave for death ID {} at {} via local search", 
                                deathId, checkPos);
                            return true;
                        }
                    }
                }
            }
        }
        
        LOGGER.debug("No grave found for death ID {}", deathId);
        return false;
    }
    
    private static boolean tryBreakGraveAt(ServerLevel level, BlockPos pos, UUID deathId, ServerPlayer player) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof GraveStoneTileEntity grave)) {
            return false;
        }
        
        Death graveDeath = grave.getDeath();
        if (graveDeath == null || !deathId.equals(graveDeath.getId())) {
            return false;
        }

        if (!ObituaryExchange.getConfig().breakGraveOnExchange.get()) {
            LOGGER.debug("Found grave for death ID {} but breaking is disabled", deathId);
            return false;
        }

        try {
            graveDeath.getAllItems().clear();
            graveDeath.getAdditionalItems().clear();
            graveDeath.getMainInventory().clear();
            graveDeath.getArmorInventory().clear();
            graveDeath.getOffHandInventory().clear();
        } catch (Exception e) {
            LOGGER.warn("Failed to clear grave items for death ID {}: {}", deathId, e.getMessage());
        }

        grave.setChanged();

        level.destroyBlock(pos, false);
        
        return true;
    }

    private static int resetOwnCooldown(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players"));
            return 0;
        }

        ExchangeDataManager dataManager = ExchangeDataManager.get(player.server);
        dataManager.resetCooldown(player.getUUID());
        source.sendSuccess(() -> Component.literal("Your cooldown has been reset")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int resetAllCooldowns(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        if (server == null) {
            source.sendFailure(Component.literal("Server not available"));
            return 0;
        }

        ExchangeDataManager dataManager = ExchangeDataManager.get(server);
        dataManager.resetAllCooldowns();
        source.sendSuccess(() -> Component.literal("All cooldowns have been reset")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}