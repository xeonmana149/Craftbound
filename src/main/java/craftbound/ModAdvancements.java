package craftbound;

import craftbound.binder.BinderContents;
import craftbound.card.BoosterPacks;
import craftbound.card.CardRarity;
import craftbound.card.Cards;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;

// Craftbound advancements that can't be spotted from your inventory alone (opening packs, completing the
// collection, finishing a deck). Their JSON files use an "impossible" criterion called "done", which only
// this code can grant.
public class ModAdvancements {
    public static void packOpened(ServerPlayer player, BoosterPacks.Opening opening, boolean lucky) {
        award(player, "open_pack");
        if (lucky) {
            award(player, "lucky_pack");
        }
        for (BoosterPacks.Pull pull : opening.pulls()) {
            if (Cards.get(pull.card()).rarity() == CardRarity.LEGENDARY) {
                award(player, "legendary_pull");
            }
        }
        if (opening.jackpot()) {
            award(player, "jackpot");
        }
    }

    public static void binderChanged(ServerPlayer player, BinderContents contents) {
        int collectionSize = Cards.collectible().size();
        if (contents.collected(Cards.collectible()) == collectionSize) {
            award(player, "complete_collection");
        }
        if (contents.collectedEnchanted(Cards.collectible()) == collectionSize) {
            award(player, "complete_enchanted_collection");
        }
    }

    public static void deckCompleted(ServerPlayer player) {
        award(player, "full_deck");
    }

    private static void award(ServerPlayer player, String name) {
        AdvancementHolder advancement = player.level().getServer().getAdvancements().get(Craftbound.id(name));
        if (advancement != null) {
            player.getAdvancements().award(advancement, "done");
        }
    }
}
