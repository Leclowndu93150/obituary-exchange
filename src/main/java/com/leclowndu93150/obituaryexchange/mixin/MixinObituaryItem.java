package com.leclowndu93150.obituaryexchange.mixin;

import de.maxhenkel.gravestone.items.ObituaryItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;

@Mixin(ObituaryItem.class)
public class MixinObituaryItem extends Item {

    public MixinObituaryItem(Properties p_41383_) {
        super(p_41383_);
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> tooltip, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, tooltip, p_41424_);
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("Use /exchangeobituary to recover items")
                .withStyle(ChatFormatting.YELLOW));
    }
}