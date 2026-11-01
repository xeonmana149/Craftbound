package craftbound.item;

import craftbound.card.BoosterPacks;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class BoosterPackItem extends Item {
    public BoosterPackItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        // Only the server rolls the cards, so every player sees the same result.
        if (!level.isClientSide()) {
            MutableComponent message = Component.literal("You opened a pack: ");
            List<BoosterPacks.Pull> pulls = BoosterPacks.open(player.getRandom());
            for (int i = 0; i < pulls.size(); i++) {
                ItemStack card = CardItem.of(pulls.get(i).card(), pulls.get(i).enchanted());
                if (i > 0) {
                    message.append(Component.literal(", "));
                }
                message.append(card.getHoverName());
                // Puts the card in the inventory, or drops it at the player's feet if it's full.
                player.getInventory().placeItemBackInInventory(card, Prediction.SERVER_ONLY);
            }
            player.sendSystemMessage(message);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        // Uses up one pack (except in Creative, like other items).
        player.getItemInHand(hand).consume(1, player);
        return InteractionResult.SUCCESS;
    }
}
