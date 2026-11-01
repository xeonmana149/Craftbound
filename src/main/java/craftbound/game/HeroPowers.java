package craftbound.game;

import craftbound.card.Cards;
import craftbound.card.MobType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

// Each class's power: costs Match.POWER_COST XP, once per turn. From the Mobdeck prototype:
// - Fighter, Craft: equip a 1/1 Wooden Sword (replacing the tool you hold).
// - Builder, Build: place a 0/2 Cobblestone Wall with Shield.
// - Farmer, Sow / Reap: Sow plants a 0/2 Wheat, and the next use becomes Reap, which harvests all your
//   crops and draws a card for each. With no crops left it's always Sow.
public class HeroPowers {
    public enum Power {
        CRAFT, BUILD, SOW, REAP, NONE;

        public Component displayName() {
            return Component.translatable("power.craftbound." + name().toLowerCase(Locale.ROOT));
        }

        public Component description() {
            return Component.translatable("power.craftbound." + name().toLowerCase(Locale.ROOT) + ".desc");
        }

        // The Minecraft item that stands in for the power's icon.
        public Identifier icon() {
            return Identifier.withDefaultNamespace(switch (this) {
                case CRAFT -> "crafting_table";
                case BUILD -> "cobblestone_wall";
                case SOW -> "wheat_seeds";
                case REAP -> "iron_hoe";
                case NONE -> "barrier";
            });
        }
    }

    public static Power of(MatchPlayer player) {
        return switch (player.heroClass) {
            case FIGHTER -> Power.CRAFT;
            case BUILDER -> Power.BUILD;
            case FARMER -> player.reap && !crops(player).isEmpty() ? Power.REAP : Power.SOW;
            case NEUTRAL -> Power.NONE;
        };
    }

    // Why the class part of the power can't be used right now (XP and once-per-turn are checked by Match).
    static String whyCantUse(Match match, int side) {
        MatchPlayer player = match.player(side);
        return switch (of(player)) {
            case NONE -> "no power";
            case BUILD, SOW -> player.board.size() >= Match.MAX_BOARD ? "your board is full" : null;
            case CRAFT, REAP -> null;
        };
    }

    static void use(Match match, int side) {
        MatchPlayer player = match.player(side);
        switch (of(player)) {
            case CRAFT -> {
                match.log(player.name + " crafts a Wooden Sword.", Cards.CRAFTED_SWORD);
                match.equip(side, new CardRef(Cards.CRAFTED_SWORD, false));
            }
            case BUILD -> {
                match.log(player.name + " builds a Cobblestone Wall.", Cards.COBBLESTONE_WALL);
                match.summon(side, new CardRef(Cards.COBBLESTONE_WALL, false));
            }
            case SOW -> {
                match.log(player.name + " sows a Wheat. Next time: Reap.", Cards.WHEAT);
                match.summon(side, new CardRef(Cards.WHEAT, false));
                player.reap = true;
            }
            case REAP -> {
                List<Minion> crops = crops(player);
                match.log(player.name + " reaps " + crops.size() + (crops.size() == 1 ? " crop" : " crops")
                        + " and draws a card for each.", Cards.WHEAT);
                for (Minion crop : crops) {
                    match.remove(crop);
                }
                player.reap = false;
                for (int i = 0; i < crops.size(); i++) {
                    match.draw(side);
                }
            }
            case NONE -> {
            }
        }
    }

    public static boolean isCrop(Minion minion) {
        return minion.definition().mobType() == MobType.CROP;
    }

    public static List<Minion> crops(MatchPlayer player) {
        List<Minion> crops = new ArrayList<>();
        for (Minion minion : player.board) {
            if (isCrop(minion)) {
                crops.add(minion);
            }
        }
        return crops;
    }

    private HeroPowers() {
    }
}
