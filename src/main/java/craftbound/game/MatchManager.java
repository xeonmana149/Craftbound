package craftbound.game;

import craftbound.card.CardClass;
import craftbound.card.CardDefinition;
import craftbound.card.CardRarity;
import craftbound.card.Cards;
import craftbound.card.DeckRules;
import craftbound.component.ModComponents;
import craftbound.deck.DeckBuilding;
import craftbound.deck.DeckContents;
import craftbound.entity.CardMasterEntity;
import craftbound.network.MatchActionPayload;
import craftbound.network.MatchStatePayload;
import craftbound.sleeve.Sleeves;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

// Runs every match on the server: starts matches from the card table (against the Card Master or another
// player), passes players' moves to the rules, lets the AI take its turn one move at a time, and sends each
// player what they can see.
public class MatchManager {
    // While the card pool is small, decks only need this many cards to play (a full deck is 30).
    public static final int PRACTICE_MIN_DECK = 10;
    // Ticks between the Card Master's moves (20 ticks = 1 second), so its animations can play out.
    private static final int AI_DELAY = 30;
    // A longer wait at the start of its turn, for the turn banner and its draw.
    private static final int AI_TURN_START_DELAY = 45;
    // And before its very first move, for the coin toss and mulligan to finish on screen.
    private static final int AI_FIRST_MOVE_DELAY = 90;

    // A deck picked at the table: its cards, class, card backs and the name you gave it ("" for none).
    public record DeckChoice(List<CardRef> cards, CardClass deckClass, Identifier sleeve, String name) {
    }

    private static final Map<UUID, Match> MATCHES = new HashMap<>();
    private static final Map<Match, Integer> AI_COOLDOWN = new HashMap<>();
    private static final Map<Match, CardMasterEntity> CARD_MASTERS = new HashMap<>();

    public static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(MatchStatePayload.TYPE, MatchStatePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MatchActionPayload.TYPE, MatchActionPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(MatchActionPayload.TYPE, (payload, context) -> handleAction(context.player(), payload));
        ServerTickEvents.END_SERVER_TICK.register(MatchManager::tick);
        // Leaving the game forfeits the match.
        ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> {
            Match match = MATCHES.get(listener.player.getUUID());
            if (match != null) {
                match.concede(match.sideOf(listener.player.getUUID()));
                sendAll(server, match);
                finish(match);
            }
        });
        TableLobby.initialize();
    }

    public static boolean inMatch(ServerPlayer player) {
        return MATCHES.containsKey(player.getUUID());
    }

    // Shows a running match again (e.g. if you closed the screen), skipping the animations you missed.
    public static void resend(ServerPlayer player) {
        Match match = MATCHES.get(player.getUUID());
        if (match != null) {
            int side = match.sideOf(player.getUUID());
            match.player(side).eventsSent = match.events.size();
            send(player, match);
        }
    }

    // Why the Deck Case in this inventory slot can't be played, as a translation key, or null if it can.
    public static String whyCantPlay(ServerPlayer player, int slot) {
        Inventory inventory = player.getInventory();
        if (slot < 0 || slot >= inventory.getContainerSize()) {
            return "match_ui.craftbound.no_deck";
        }
        ItemStack stack = inventory.getItem(slot);
        if (stack.has(ModComponents.DECK_CONTENTS)) {
            DeckBuilding.migrate(player, stack);
        }
        return DeckBuilding.whyCantPlay(inventory, stack);
    }

    // The deck in that slot, ready to shuffle. Check whyCantPlay first.
    public static DeckChoice deckIn(ServerPlayer player, int slot) {
        ItemStack stack = player.getInventory().getItem(slot);
        DeckContents deck = stack.getOrDefault(ModComponents.DECK_CONTENTS, DeckContents.EMPTY);
        List<CardRef> cards = new ArrayList<>();
        for (Identifier id : Cards.collectible()) {
            for (boolean enchanted : new boolean[]{false, true}) {
                for (int n = 0; n < deck.cards().count(id, enchanted); n++) {
                    cards.add(new CardRef(id, enchanted));
                }
            }
        }
        Identifier sleeve = stack.get(ModComponents.SLEEVE_ID);
        return new DeckChoice(cards, deck.deckClass(), sleeve == null ? Sleeves.CLASSIC : sleeve, DeckBuilding.customName(stack));
    }

    // A match against the Card Master, who has just sat down at the table (or null if it got lost on the way).
    public static void startVsCardMaster(ServerPlayer player, DeckChoice deck, CardMasterEntity cardMaster) {
        if (inMatch(player)) {
            if (cardMaster != null) {
                cardMaster.leave(false);
            }
            return;
        }
        MatchPlayer you = new MatchPlayer(player.getName().getString(), player.getUUID(), deck.deckClass(), deck.sleeve(), deck.cards());
        you.deckName = deck.name();
        CardClass masterClass = randomClass(player);
        MatchPlayer master = new MatchPlayer("Card Master", null, masterClass, Sleeves.CLASSIC, cardMasterDeck(masterClass));
        Match match = new Match(you, master, player.getRandom());
        MATCHES.put(player.getUUID(), match);
        if (cardMaster != null) {
            CARD_MASTERS.put(match, cardMaster);
        }
        // The Card Master decides its mulligan straight away; the game begins once you've done yours.
        MatchAI.mulligan(match, match.sideOf(player.getUUID()) == 0 ? 1 : 0);
        AI_COOLDOWN.put(match, AI_FIRST_MOVE_DELAY);
        send(player, match);
    }

    // A match between two players at the same table.
    public static void startVersus(ServerPlayer first, DeckChoice firstDeck, ServerPlayer second, DeckChoice secondDeck) {
        if (inMatch(first) || inMatch(second)) {
            return;
        }
        MatchPlayer a = new MatchPlayer(first.getName().getString(), first.getUUID(), firstDeck.deckClass(), firstDeck.sleeve(), firstDeck.cards());
        MatchPlayer b = new MatchPlayer(second.getName().getString(), second.getUUID(), secondDeck.deckClass(), secondDeck.sleeve(), secondDeck.cards());
        a.deckName = firstDeck.name();
        b.deckName = secondDeck.name();
        Match match = new Match(a, b, first.getRandom());
        MATCHES.put(first.getUUID(), match);
        MATCHES.put(second.getUUID(), match);
        send(first, match);
        send(second, match);
    }

    private static CardClass randomClass(ServerPlayer player) {
        CardClass[] classes = {CardClass.FIGHTER, CardClass.BUILDER, CardClass.FARMER};
        return classes[player.getRandom().nextInt(classes.length)];
    }

    // The Card Master plays every Neutral card and every card of its class: 2 of each, 1 of each Legendary.
    private static List<CardRef> cardMasterDeck(CardClass cardClass) {
        List<CardRef> deck = new ArrayList<>();
        for (Identifier id : Cards.collectible()) {
            CardDefinition card = Cards.get(id);
            if (!DeckRules.allowed(id, cardClass)) {
                continue;
            }
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
            case MatchActionPayload.POWER -> match.usePower(side);
            case MatchActionPayload.END_TURN -> {
                match.endTurn(side);
                AI_COOLDOWN.put(match, AI_TURN_START_DELAY);
            }
            case MatchActionPayload.MULLIGAN -> {
                match.mulligan(side, payload.index());
                if (match.phase == Match.Phase.PLAYING) {
                    AI_COOLDOWN.put(match, AI_FIRST_MOVE_DELAY);
                }
            }
            case MatchActionPayload.CONCEDE -> match.concede(side);
            default -> {
            }
        }
        sendAll(player.level().getServer(), match);
        if (match.over) {
            finish(match);
        }
    }

    // The Card Master takes one move every AI_DELAY ticks during its turn.
    private static void tick(MinecraftServer server) {
        for (Match match : new HashSet<>(MATCHES.values())) {
            if (match.over || match.phase != Match.Phase.PLAYING || !match.player(match.current).isAi()) {
                continue;
            }
            int cooldown = AI_COOLDOWN.getOrDefault(match, 0) - 1;
            AI_COOLDOWN.put(match, cooldown);
            if (cooldown > 0) {
                continue;
            }
            boolean keepsGoing = MatchAI.step(match, match.current);
            AI_COOLDOWN.put(match, keepsGoing ? AI_DELAY : AI_TURN_START_DELAY);
            sendAll(server, match);
            if (match.over) {
                finish(match);
            }
        }
    }

    private static void finish(Match match) {
        AI_COOLDOWN.remove(match);
        MATCHES.values().removeIf(m -> m == match);
        CardMasterEntity cardMaster = CARD_MASTERS.remove(match);
        if (cardMaster != null && !cardMaster.isRemoved()) {
            boolean masterWon = match.winner >= 0 && match.player(match.winner).isAi();
            cardMaster.leave(masterWon);
        }
    }

    private static void sendAll(MinecraftServer server, Match match) {
        for (MatchPlayer player : match.players) {
            if (player.playerId == null) {
                continue;
            }
            ServerPlayer online = server.getPlayerList().getPlayer(player.playerId);
            if (online != null) {
                send(online, match);
            }
        }
    }

    private static void send(ServerPlayer player, Match match) {
        int side = match.sideOf(player.getUUID());
        MatchPlayer viewer = match.player(side);
        String json = MatchView.build(match, side, viewer.eventsSent);
        viewer.eventsSent = match.events.size();
        ServerPlayNetworking.send(player, new MatchStatePayload(json));
    }
}
