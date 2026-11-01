package craftbound.item;

import craftbound.ModAdvancements;
import craftbound.card.BoosterPacks;
import craftbound.network.PackOpenedPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

public class BoosterPackItem extends Item {
    public BoosterPackItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        // Only the server rolls the cards, so every player sees the same result.
        if (!level.isClientSide()) {
            ItemStack pack = player.getItemInHand(hand);
            // Luck (e.g. from a Potion of Luck) raises the Enchanted chance; Fortune on the pack rolls rarer cards.
            double luck = player.getAttributeValue(Attributes.LUCK);
            Holder<Enchantment> fortune = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE);
            int fortuneLevel = EnchantmentHelper.getItemEnchantmentLevel(fortune, pack);
            BoosterPacks.Opening opening = BoosterPacks.open(player.getRandom(), luck, fortuneLevel);
            for (BoosterPacks.Pull pull : opening.pulls()) {
                // Puts the card in the inventory, or drops it at the player's feet if it's full.
                player.getInventory().placeItemBackInInventory(CardItem.of(pull.card(), pull.enchanted()), Prediction.SERVER_ONLY);
            }
            // Tell the player's game to play the opening, where the cards are revealed one by one.
            if (player instanceof ServerPlayer serverPlayer) {
                ServerPlayNetworking.send(serverPlayer, new PackOpenedPayload(opening.pulls(), opening.jackpot()));
                ModAdvancements.packOpened(serverPlayer, opening, luck > 0 || fortuneLevel > 0);
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        // Uses up one pack (except in Creative, like other items).
        player.getItemInHand(hand).consume(1, player);
        return InteractionResult.SUCCESS;
    }
}
