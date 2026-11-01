package craftbound.game;

import craftbound.card.CardClass;
import craftbound.card.CardDefinition;
import craftbound.card.CardRarity;
import craftbound.card.Cards;
import craftbound.card.DeckRules;
import craftbound.component.ModComponents;
import craftbound.deck.DeckBuilding;
import craftbound.deck.DeckContents;
import craftbound.item.ModItems;
import craftbound.network.MatchActionPayload;
import craftbound.network.MatchStatePayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

// Runs every match on the server: starts practice matches against the Card Master, passes players' moves to
// the rules, lets the AI take its turn one move at a time, and sends each player what they can see.
public class MatchManager {
    // While the card pool is small, practice decks only need this many cards (a full deck is 30).
    public static final int PRACTICE_MIN_DECK = 10;
    // Ticks between the Card Master's moves (20 ticks = 1 second), so you can follow what it does.
    private static final int AI_DELAY = 25;

    private static final Map<UUID, Match> MATCHES = new HashMap<>();
    private static final Map<Match, Integer> AI_COOLDOWN = new HashMap<>();

    public static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(MatchStatePayload.TYPE, MatchStatePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MatchActionPayload.TYPE, MatchActionPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(MatchActionPayload.TYPE, (payload, context) -> handleAction(context.player(), payload));
        ServerTickEvents.END_SERVER_TICK.register(MatchManager::tick);
        // Leaving the game forfeits the match.
        ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> {
            Match match = MATCHES.remove(listener.player.getUUID());
            if (match != null) {
                match.concede(match.sideOf(listener.player.getUUID()));
                AI_COOLDOWN.remove(match);
            }
        });
    }

    public static boolean inMatch(ServerPlayer player) {
        return MATCHES.containsKey(player.getUUID());
    }

    // Shows a running match again (e.g. if you closed the screen).
    public static void resend(ServerPlayer player) {
        Match match = MATCHES.get(player.getUUID());
        if (match != null) {
            send(player, match);
        }
    }

    // A practice match against the Card Master, using the first playable Deck Case in your inventory.
    public static void startPractice(ServerPlayer player) {
        if (inMatch(player)) {
            resend(player);
            return;
        }
        List<CardRef> deck = playableDeck(player);
        if (deck == null) {
            return;
        }
        MatchPlayer you = new MatchPlayer(player.getName().getString(), player.getUUID(), deck);
        MatchPlayer cardMaster = new MatchPlayer("Card Master", null, cardMasterDeck());
        Match match = new Match(you, cardMaster, player.getRandom());
        MATCHES.put(player.getUUID(), match);
        AI_COOLDOWN.put(match, AI_DELAY);
        send(player, match);
    }

    // The cards from the first Deck Case that's ready to play, or null (with a message saying why).
    private static List<CardRef> playableDeck(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        String problem = "match_ui.craftbound.no_deck";
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.is(ModItems.DECK_CASE)) {
                continue;
            }
            DeckBuilding.migrate(player, stack);
            DeckContents deck = stack.getOrDefault(ModComponents.DECK_CONTENTS, DeckContents.EMPTY);
            if (deck.deckClass() == CardClass.NEUTRAL || deck.size() < PRACTICE_MIN_DECK) {
                problem = "match_ui.craftbound.deck_too_small";
                continue;
            }
            List<CardRef> cards = new ArrayList<>();
            boolean missing = false;
            for (Identifier id : Cards.collectible()) {
                for (boolean enchanted : new boolean[]{false, true}) {
                    if (DeckBuilding.missing(inventory, deck, id, enchanted) > 0) {
                        missing = true;
                    }
                    for (int n = 0; n < deck.cards().count(id, enchanted); n++) {
                        cards.add(new CardRef(id, enchanted));
                    }
                }
            }
            if (missing) {
                problem = "match_ui.craftbound.deck_missing";
                continue;
            }
            return cards;
        }
        player.sendOverlayMessage(Component.translatable(problem, PRACTICE_MIN_DECK));
        return null;
    }

    // The Card Master plays every card in the game: 2 of each, 1 of each Legendary.
    private static List<CardRef> cardMasterDeck() {
        List<CardRef> deck = new ArrayList<>();
        for (Identifier id : Cards.collectible()) {
            CardDefinition card = Cards.get(id);
            int copies = card.rarity() == CardRarity.LEGENDARY ? 1 : DeckRules.maxCopies(card.rarity());
            for (int n = 0; n < copies; n++) {
                deck.add(new CardRef(id, false));
            }
        }
        return deck;
    }

    private static void handleAction(ServerPlayer player, MatchActionPayload payload) {
        Match match = MATCHES.get(player.getUUID());
        if (match == null) {
            return;
        }
        int side = match.sideOf(player.getUUID());
        Target target = payload.targetSide() < 0 ? Target.NONE
                : payload.targetHero() ? Target.hero(payload.targetSide()) : Target.mob(payload.targetSide(), payload.targetMinion());
        switch (payload.action()) {
            case MatchActionPayload.PLAY -> match.playCard(side, payload.index(), target);
            case MatchActionPayload.ATTACK -> match.attack(side, payload.index(), target);
            case MatchActionPayload.END_TURN -> {
                match.endTurn(side);
                AI_COOLDOWN.put(match, AI_DELAY);
            }
            case MatchActionPayload.CONCEDE -> match.concede(side);
            default -> {
            }
        }
        send(player, match);
        if (match.over) {
            finish(match);
        }
    }

    // The Card Master takes one move every AI_DELAY ticks during its turn.
    private static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Match>> entries = MATCHES.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, Match> entry = entries.next();
            Match match = entry.getValue();
            if (match.over || !match.player(match.current).isAi()) {
                continue;
            }
            int cooldown = AI_COOLDOWN.getOrDefault(match, 0) - 1;
            AI_COOLDOWN.put(match, cooldown);
            if (cooldown > 0) {
                continue;
            }
            MatchAI.step(match, match.current);
            AI_COOLDOWN.put(match, AI_DELAY);
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                send(player, match);
            }
            if (match.over) {
                AI_COOLDOWN.remove(match);
                entries.remove();
            }
        }
    }

    private static void finish(Match match) {
        AI_COOLDOWN.remove(match);
        MATCHES.values().removeIf(m -> m == match);
    }

    private static void send(ServerPlayer player, Match match) {
        int side = match.sideOf(player.getUUID());
        ServerPlayNetworking.send(player, new MatchStatePayload(MatchView.build(match, side)));
    }
}
