package craftbound.item;

import craftbound.component.ModComponents;
import craftbound.sleeve.Sleeves;
import craftbound.sleeve.SleeveCollection;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class SleeveItem extends Item {
    public SleeveItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(Identifier sleeve) {
        ItemStack stack = new ItemStack(ModItems.SLEEVE);
        stack.set(ModComponents.SLEEVE_ID, sleeve);
        return stack;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Identifier sleeve = stack.get(ModComponents.SLEEVE_ID);
        if (sleeve == null || !Sleeves.ids().contains(sleeve)) return InteractionResult.FAIL;
        if (player instanceof ServerPlayer serverPlayer) {
            if (!SleeveCollection.unlock(serverPlayer, sleeve)) {
                serverPlayer.sendSystemMessage(Component.translatable("sleeve_ui.craftbound.already_unlocked", Sleeves.name(sleeve))
                        .withStyle(ChatFormatting.GRAY));
                return InteractionResult.SUCCESS;
            }
            stack.shrink(1);
            serverPlayer.sendSystemMessage(Component.translatable("sleeve_ui.craftbound.unlocked", Sleeves.name(sleeve))
                    .withStyle(ChatFormatting.GOLD));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.3F);
        }
        return InteractionResult.SUCCESS;
    }

    // "Emerald Sleeve" instead of just "Sleeve".
    @Override
    public Component getName(ItemStack stack) {
        Identifier id = stack.get(ModComponents.SLEEVE_ID);
        return id == null ? super.getName(stack) : Sleeves.name(id);
    }
}
