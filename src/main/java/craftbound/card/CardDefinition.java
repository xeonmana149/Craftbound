package craftbound.card;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

// What a card is. Its name and ability text live in the lang file under card.craftbound.<id> and card.craftbound.<id>.text.
// Keywords fill the %s slots in the ability text, in order, e.g. "%s. %s: add an Arrow to your hand." -> "Ranged. Drop: ..."
// mobType is null for cards without one, like items or the Creeper.
// artItem is the Minecraft item shown as placeholder art; null means "the card's own spawn egg or item".
public record CardDefinition(CardKind kind, CardClass cardClass, CardRarity rarity, int cost, int attack, int health,
                             MobType mobType, List<Keyword> keywords, List<Related> related, boolean token, String artItem) {

    // A card this one creates, and the keyword that creates it, e.g. Drop -> Arrow.
    public record Related(Keyword keyword, Identifier card) {
    }

    public List<Identifier> createdBy(Keyword keyword) {
        List<Identifier> result = new ArrayList<>();
        for (Related entry : related) {
            if (entry.keyword() == keyword) {
                result.add(entry.card());
            }
        }
        return result;
    }

    public static Builder mob(CardRarity rarity, int cost, int attack, int health) {
        return new Builder(CardKind.MOB, rarity, cost, attack, health);
    }

    public static Builder item(CardRarity rarity, int cost) {
        return new Builder(CardKind.ITEM, rarity, cost, 0, 0);
    }

    public boolean isMob() {
        return kind == CardKind.MOB;
    }

    // The lines under the card's name in the inventory.
    public void appendTooltip(Identifier id, Consumer<Component> tooltip) {
        tooltip.accept(Component.translatable("card_ui.craftbound.cost_short", cost).withStyle(ChatFormatting.GREEN));
        if (isMob()) {
            tooltip.accept(Component.translatable("card_ui.craftbound.stats_short", attack, health).withStyle(ChatFormatting.GRAY));
        }
        if (mobType != null) {
            tooltip.accept(mobType.displayName().copy().withStyle(ChatFormatting.GRAY));
        }
        for (Component clause : CardText.clauses(Cards.text(id))) {
            tooltip.accept(clause.copy().withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public static class Builder {
        private final CardKind kind;
        private final CardRarity rarity;
        private final int cost;
        private final int attack;
        private final int health;
        private CardClass cardClass = CardClass.NEUTRAL;
        private MobType mobType = null;
        private final List<Keyword> keywords = new ArrayList<>();
        private final List<Related> related = new ArrayList<>();
        private boolean token = false;
        private String artItem = null;

        private Builder(CardKind kind, CardRarity rarity, int cost, int attack, int health) {
            this.kind = kind;
            this.rarity = rarity;
            this.cost = cost;
            this.attack = attack;
            this.health = health;
        }

        public Builder cardClass(CardClass cardClass) {
            this.cardClass = cardClass;
            return this;
        }

        public Builder type(MobType mobType) {
            this.mobType = mobType;
            return this;
        }

        public Builder keywords(Keyword... keywords) {
            this.keywords.addAll(List.of(keywords));
            return this;
        }

        // A card this one creates through a keyword, like the Arrow from a Skeleton's Drop.
        // Hovering that keyword in the inspect screen pops the card out.
        public Builder creates(Keyword keyword, Identifier card) {
            this.related.add(new Related(keyword, card));
            return this;
        }

        // Tokens are made by other cards. They never come from packs.
        public Builder token() {
            this.token = true;
            return this;
        }

        // Use a different Minecraft item as placeholder art, e.g. "creeper_spawn_egg" for the Charged Creeper.
        public Builder art(String minecraftItem) {
            this.artItem = minecraftItem;
            return this;
        }

        public CardDefinition build() {
            return new CardDefinition(kind, cardClass, rarity, cost, attack, health, mobType,
                    List.copyOf(keywords), List.copyOf(related), token, artItem);
        }
    }
}
