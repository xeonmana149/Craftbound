package craftbound.game;

import craftbound.card.CardClass;
import craftbound.card.CardDefinition;
import craftbound.card.Cards;
import craftbound.card.DeckRules;
import craftbound.component.ModComponents;
import craftbound.deck.DeckBuilding;
import craftbound.deck.DeckContents;
import craftbound.entity.CardMasterEntity;
import craftbound.network.MatchActionPayload;
import craftbound.network.MatchStatePayload;
import craftbound.board.BoardCollection;
import craftbound.board.BoardDesign;
import craftbound.sleeve.Sleeves;
import craftbound.sleeve.SleeveCollection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
import net.minecraft.util.RandomSource;
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
    private static final Map<Match, TableSeats> SEATS = new HashMap<>();

    record PauseRequest(int side, long expires) {
        boolean canRespond(int responder,long now) { return responder==1-side && now<expires; }
    }
    private static final Map<Match, PauseRequest> PAUSE_REQUESTS = new HashMap<>();

    public static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(MatchStatePayload.TYPE, MatchStatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(craftbound.network.TurnClockPayload.TYPE, craftbound.network.TurnClockPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MatchActionPayload.TYPE, MatchActionPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(MatchActionPayload.TYPE, (payload, context) -> handleAction(context.player(), payload));
        ServerTickEvents.END_SERVER_TICK.register(MatchManager::tick);
        // Leaving the game forfeits the match.
        ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> {
            Match match = MATCHES.get(listener.player.getUUID());
            if (match != null) {
                if (match.trialsBattle != null && canCheckpoint(match)) {
                    pause(server, match);
                    return;
                }
                match.concede(match.sideOf(listener.player.getUUID()));
                sendAll(server, match);
                finish(server, match);
            }
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Match match : new HashSet<>(MATCHES.values()))
                if (match.trialsBattle != null && canCheckpoint(match)) pause(server, match);
            for(TableSeats seats:SEATS.values()) seats.close();
            SEATS.clear();MATCHES.clear();AI_COOLDOWN.clear();CARD_MASTERS.clear();PAUSE_REQUESTS.clear();
        });
        TableLobby.initialize();
        TableSpectating.initialize();
    }

    public static boolean inMatch(ServerPlayer player) {
        return MATCHES.containsKey(player.getUUID()) || CommanderTables.busy(player) || RelayTables.busy(player);
    }

    public static boolean tableInUse(net.minecraft.server.level.ServerLevel level,net.minecraft.core.BlockPos table) {
        return RelayTables.reserved(level,table) || CommanderTables.reserved(level,table) || level.getBlockEntity(table) instanceof craftbound.block.CardTableBlockEntity data && data.paused()
                || SEATS.values().stream().anyMatch(seats->seats.at(level,table));
    }

    public static boolean hasActiveMatches() {
        return !MATCHES.isEmpty() || CommanderTables.hasGames() || RelayTables.hasGames();
    }
    static Match matchAt(net.minecraft.server.level.ServerLevel level,net.minecraft.core.BlockPos table) {
        return SEATS.entrySet().stream().filter(e->e.getValue().at(level,table)).map(Map.Entry::getKey).findFirst().orElse(null);
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
    public static String whyCantPlay(ServerPlayer player, int slot) { return whyCantPlay(player, slot, ""); }

    public static String whyCantPlay(ServerPlayer player, int slot, String deckId) {
        Inventory inventory = player.getInventory();
        if (slot < 0 || slot >= inventory.getContainerSize()) {
            return "match_ui.craftbound.no_deck";
        }
        ItemStack stack = craftbound.deck.BinderDecks.resolve(inventory, slot, deckId);
        if (stack.has(ModComponents.DECK_CONTENTS)) {
            DeckBuilding.migrate(player, stack);
        }
        return DeckBuilding.whyCantPlay(inventory, stack);
    }

    // The deck in that slot, ready to shuffle. Check whyCantPlay first.
    public static DeckChoice deckIn(ServerPlayer player, int slot) { return deckIn(player, slot, ""); }

    public static DeckChoice deckIn(ServerPlayer player, int slot, String deckId) {
        ItemStack stack = craftbound.deck.BinderDecks.resolve(player.getInventory(), slot, deckId);
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
    public static void startVsCardMaster(ServerPlayer player, DeckChoice deck, CardMasterEntity cardMaster, net.minecraft.core.BlockPos table) {
        if (inMatch(player)) {
            if (cardMaster != null) {
                cardMaster.leave(false);
            }
            return;
        }
        MatchPlayer you = new MatchPlayer(player.getName().getString(), player.getUUID(), deck.deckClass(), deck.sleeve(), deck.cards());
        you.deckName = deck.name();
        CardClass masterClass = randomClass(player);
        int available=Cards.collectible().stream().filter(id->DeckRules.allowed(id,masterClass))
                .mapToInt(id->DeckRules.maxCopies(Cards.get(id).rarity())).sum();
        if(available<DeckRules.DECK_SIZE) {
            tell(player,"There are not enough active cards to build this Card Master's 30-card deck.");
            if(cardMaster!=null)cardMaster.leave(false);return;
        }
        MatchPlayer master = new MatchPlayer("Card Master", null, masterClass, Sleeves.CLASSIC, cardMasterDeck(masterClass, player.getRandom()));
        Match match = new Match(you, master, player.getRandom());
        match.boardDesign = BoardDesign.choose(BoardCollection.selected(player), BoardDesign.CLASSIC, player.getRandom());
        startAiMatch(player, table, cardMaster, match);
    }

    static boolean startAiMatch(ServerPlayer player, net.minecraft.core.BlockPos table, CardMasterEntity cardMaster, Match match) {
        var level = (net.minecraft.server.level.ServerLevel) player.level();
        if (inMatch(player) || tableInUse(level, table)) {
            if (cardMaster != null) cardMaster.leave(false);
            tell(player, "This table or player is already in a match.");
            return false;
        }
        TableSeats seats = TableSeats.start(level, table, player, cardMaster);
        if (seats == null) {
            if (cardMaster != null) cardMaster.leave(false);
            TableSeats.explain(player);
            return false;
        }
        SEATS.put(match,seats);
        MATCHES.put(player.getUUID(), match);
        if (cardMaster != null) {
            CARD_MASTERS.put(match, cardMaster);
        }
        // The Card Master decides its mulligan straight away; the game begins once you've done yours.
        MatchAI.mulligan(match, match.sideOf(player.getUUID()) == 0 ? 1 : 0);
        AI_COOLDOWN.put(match, AI_FIRST_MOVE_DELAY);
        send(player, match);
        return true;
    }

    // A match between two players at the same table.
    public static void startVersus(ServerPlayer first, DeckChoice firstDeck, ServerPlayer second, DeckChoice secondDeck, net.minecraft.core.BlockPos table) {
        if (inMatch(first) || inMatch(second)) {
            return;
        }
        MatchPlayer a = new MatchPlayer(first.getName().getString(), first.getUUID(), firstDeck.deckClass(), firstDeck.sleeve(), firstDeck.cards());
        MatchPlayer b = new MatchPlayer(second.getName().getString(), second.getUUID(), secondDeck.deckClass(), secondDeck.sleeve(), secondDeck.cards());
        a.deckName = firstDeck.name();
        b.deckName = secondDeck.name();
        Match match = new Match(a, b, first.getRandom());
        match.boardDesign = BoardDesign.choose(BoardCollection.selected(first), BoardCollection.selected(second), first.getRandom());
        if(tableInUse((net.minecraft.server.level.ServerLevel)first.level(),table)) {
            TableSeats.explain(first);TableSeats.explain(second);return;
        }
        TableSeats seats=TableSeats.start((net.minecraft.server.level.ServerLevel)first.level(),table,first,second);
        if(seats==null) { TableSeats.explain(first);TableSeats.explain(second);return; }
        SEATS.put(match,seats);
        MATCHES.put(first.getUUID(), match);
        MATCHES.put(second.getUUID(), match);
        send(first, match);
        send(second, match);
    }

    private static CardClass randomClass(ServerPlayer player) {
        CardClass[] classes = {CardClass.FIGHTER, CardClass.BUILDER, CardClass.FARMER, CardClass.FISHERMAN, CardClass.ALCHEMIST, CardClass.MINER, CardClass.CHEF, CardClass.TAMER, CardClass.REDSTONER};
        return classes[player.getRandom().nextInt(classes.length)];
    }

    // Keep the class's identity, then fill a full legal deck with a random selection of Neutral cards.
    private static List<CardRef> cardMasterDeck(CardClass cardClass, RandomSource random) {
        List<CardRef> deck = new ArrayList<>();
        for (Identifier id : Cards.collectible()) {
            CardDefinition card = Cards.get(id);
            if (!DeckRules.allowed(id, cardClass)) {
                continue;
            }
            int copies = DeckRules.maxCopies(card.rarity());
            for (int n = 0; n < copies; n++) {
                deck.add(new CardRef(id, false));
            }
        }
        if (deck.size() < DeckRules.DECK_SIZE) {
            throw new IllegalStateException("Not enough legal cards for a full Card Master deck: " + cardClass);
        }
        Collections.shuffle(deck, new java.util.Random(random.nextLong()));
        // List.sort is stable: each group retains its random order. Match shuffles the final deck for play.
        deck.sort(Comparator.comparing(ref -> Cards.get(ref.id()).cardClass() == CardClass.NEUTRAL));
        return new ArrayList<>(deck.subList(0, DeckRules.DECK_SIZE));
    }

    private static void handleAction(ServerPlayer player, MatchActionPayload payload) {
        Match match = MATCHES.get(player.getUUID());
        if (match == null) {
            return;
        }
        int side = match.sideOf(player.getUUID());
        if (payload.action() == MatchActionPayload.REQUEST_PAUSE) {
            var pending=PAUSE_REQUESTS.get(match);
            if(pending!=null && pending.side()==side) {
                PAUSE_REQUESTS.remove(match);sendAll(player.level().getServer(),match);return;
            }
            if (!match.over && !PAUSE_REQUESTS.containsKey(match)) {
                if(match.pauseRequestedTurn[side]==match.turn) { tell(player,"You can request a pause once per turn.");return; }
                match.pauseRequestedTurn[side]=match.turn;
                if (match.opponent(side).isAi()) pause(player.level().getServer(),match);
                else {
                    PAUSE_REQUESTS.put(match,new PauseRequest(side,player.level().getServer().getTickCount()+600L));
                    sendAll(player.level().getServer(),match);
                }
            }
            return;
        }
        PauseRequest request=PAUSE_REQUESTS.get(match);
        if (payload.action()==MatchActionPayload.ACCEPT_PAUSE || payload.action()==MatchActionPayload.DECLINE_PAUSE) {
            if(request!=null && request.canRespond(side,player.level().getServer().getTickCount())) {
                if(payload.action()==MatchActionPayload.ACCEPT_PAUSE) pause(player.level().getServer(),match);
                else { PAUSE_REQUESTS.remove(match);sendAll(player.level().getServer(),match); }
            }
            return;
        }
        if(request!=null && payload.action()!=MatchActionPayload.CONCEDE) return;
        Target target = payload.targetSide() < 0 ? Target.NONE
                : payload.targetHero() ? Target.hero(payload.targetSide()) : Target.mob(payload.targetSide(), payload.targetMinion());
        switch (payload.action()) {
            case MatchActionPayload.PLAY -> match.playCard(side, payload.index(), target);
            case MatchActionPayload.ATTACK -> match.attack(side, payload.index(), target);
            case MatchActionPayload.POWER -> match.usePower(side, target);
            case MatchActionPayload.DISMANTLE -> { return; } // Retired packet ID; old clients cannot remove blocks.
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
            case MatchActionPayload.CHOOSE_CARD -> {
                if (!match.chooseFromHand(side, payload.index())) return;
                // Let a waiting Card Master watch the result before it moves again.
                AI_COOLDOWN.put(match, AI_DELAY);
            }
            default -> {
            }
        }
        sendAll(player.level().getServer(), match);
        if (match.over) {
            finish(player.level().getServer(), match);
        }
    }

    // The Card Master takes one move every AI_DELAY ticks during its turn.
    private static void tick(MinecraftServer server) {
        for (Match match : new HashSet<>(MATCHES.values())) {
            TableSeats seats=SEATS.get(match);
            if(seats!=null && !seats.valid()) {
                match.over=true;match.winner=-1;
                match.log("The table match ended because a seat or participant is no longer available.");
                sendAll(server,match);finish(server,match);continue;
            }
            PauseRequest request=PAUSE_REQUESTS.get(match);
            if(request!=null) {
                if(server.getTickCount()>=request.expires()) { PAUSE_REQUESTS.remove(match);sendAll(server,match); }
                else continue;
            }
            // A hand pick holds the match (and the Card Master) until it's made, or picks at random.
            if(match.pendingChoice()!=null) {
                if(--match.choiceTicksRemaining<=0) {
                    match.resolveChoiceRandomly();
                    sendAll(server,match);
                    if(match.over)finish(server,match);
                }
                continue;
            }
            if(match.tickTurnClock()) {
                sendAll(server,match);
                if(match.over)finish(server,match);
                continue;
            }
            if(match.timed() && match.phase==Match.Phase.PLAYING && server.getTickCount()%20==0) {
                syncTable(match);
                for(var p:match.players) {
                    var online=server.getPlayerList().getPlayer(p.playerId);
                    if(online!=null)ServerPlayNetworking.send(online,new craftbound.network.TurnClockPayload(match.turn,match.current,match.turnTicksRemaining,false));
                }
            }
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
                finish(server, match);
            }
        }
    }

    private static void finish(MinecraftServer server, Match match) {
        TableSpectating.revokeMatch(server,match);
        PAUSE_REQUESTS.remove(match);
        AI_COOLDOWN.remove(match);
        TableSeats seats=SEATS.remove(match);
        if(seats!=null && seats.tableData()!=null) seats.tableData().board("",0);
        if(seats!=null) seats.close();
        boolean wasActive = MATCHES.values().removeIf(m -> m == match);
        // Trials pay their own reward for clearing the route. An encounter cannot pay normal duel rewards as well.
        boolean trial = match.trialsBattle != null;
        if (wasActive && trial && match.over) TrialsTables.finish(server, match);
        if (wasActive && !trial && match.over && match.winner >= 0 && !match.player(match.winner).isAi()
                && match.opponent(match.winner).isAi()) {
            ServerPlayer winner = server.getPlayerList().getPlayer(match.player(match.winner).playerId);
            if (winner != null) SleeveCollection.awardVictory(winner);
        }
        // Award once, after a played match. No farming progress from immediate concessions.
        if (wasActive && !trial && match.over && match.winner >= 0 && match.turn >= 6) {
            for (int side = 0; side < 2; side++) {
                MatchPlayer participant = match.player(side);
                if (participant.isAi() || side == match.concededSide) continue;
                ServerPlayer online = server.getPlayerList().getPlayer(participant.playerId);
                if (online != null) craftbound.mastery.ClassMastery.award(online, participant.heroClass, side == match.winner);
            }
        }
        CardMasterEntity cardMaster = CARD_MASTERS.remove(match);
        if (cardMaster != null && !cardMaster.isRemoved()) {
            boolean masterWon = match.winner >= 0 && match.player(match.winner).isAi();
            cardMaster.leave(masterWon);
        }
    }

    private static void syncTable(Match match) {
        TableSeats seats=SEATS.get(match);
        if(seats!=null && seats.tableData()!=null) {
            java.util.UUID first=match.player(0).playerId;
            seats.tableData().board(MatchView.publicBoard(match,false),seats.boardYaw(first));
        }
    }

    private static boolean canCheckpoint(Match match) {
        TableSeats seats = SEATS.get(match);
        return !match.over && seats != null && seats.tableData() != null;
    }

    private static void pause(MinecraftServer server,Match match) {
        TableSeats seats=SEATS.get(match);
        var data=seats==null?null:seats.tableData();
        if(data==null || match.over) return;
        TableSpectating.revokeMatch(server,match);
        // Saves don't hold an open hand pick: settle it at random first.
        while(match.pendingChoice()!=null && !match.over) match.resolveChoiceRandomly();
        if(match.over) { sendAll(server,match);finish(server,match);return; }
        data.saveMatch(MatchSnapshot.capture(match).toString());
        data.board(MatchView.publicBoard(match,true),seats.boardYaw(match.player(0).playerId));
        PAUSE_REQUESTS.remove(match);AI_COOLDOWN.remove(match);SEATS.remove(match);
        MATCHES.values().removeIf(m->m==match);
        seats.close();
        var master=CARD_MASTERS.remove(match);
        if(master!=null) { master.waitAt(data.getBlockPos());data.waitingMaster(master.getUUID()); }
        for(MatchPlayer p:match.players) if(!p.isAi()) {
            ServerPlayer online=server.getPlayerList().getPlayer(p.playerId);
            if(online!=null) {
                ServerPlayNetworking.send(online,new MatchStatePayload("{\"paused\":true}"));
                online.sendOverlayMessage(net.minecraft.network.chat.Component.literal("Match paused at this table. Right-click it to return."));
            }
        }
    }

    /** Repair legacy paused saves, without creating another opponent while its chunk is loading. */
    public static void maintainPaused(net.minecraft.server.level.ServerLevel level,craftbound.block.CardTableBlockEntity data) {
        if(data==null || !data.paused())return;
        var pos=data.getBlockPos();
        for(int x:new int[]{-1,1})for(int z:new int[]{-1,1})
            if(!level.hasChunkAt(pos.offset(x,0,z))
                    || !level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.pack(pos.offset(x,0,z))))return;
        if(data.waitingMaster()!=null && level.getEntity(data.waitingMaster()) instanceof CardMasterEntity existing
                && existing.waitingAt(pos))return;
        // Also recover the link if a crash saved the entity and table in different ticks.
        var nearby=level.getEntitiesOfClass(CardMasterEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(4),m->m.waitingAt(pos));
        if(!nearby.isEmpty()) { data.waitingMaster(nearby.getFirst().getUUID());return; }
        try {
            var saved=MatchSnapshot.restore(com.google.gson.JsonParser.parseString(data.savedMatch()).getAsJsonObject());
            if(!saved.player(0).isAi()&&!saved.player(1).isAi())return;
            var master=CardMasterEntity.restoreWaiting(level,pos,data.boardYaw());
            if(master!=null)data.waitingMaster(master.getUUID());
        } catch(RuntimeException ignored) {
            // Leave an unrecognised save intact; openPaused reports the actionable error to its owner.
        }
    }

    /** Right-click only offers a prompt. Both humans must explicitly confirm at this table. */
    public static boolean openPaused(ServerPlayer player,net.minecraft.core.BlockPos pos) {
        return openPaused(player,pos,false);
    }
    public static boolean resumePaused(ServerPlayer player,net.minecraft.core.BlockPos pos) {
        return openPaused(player,pos,true);
    }
    private static boolean openPaused(ServerPlayer player,net.minecraft.core.BlockPos pos,boolean confirmed) {
        var level=(net.minecraft.server.level.ServerLevel)player.level();
        var data=craftbound.block.CardTableBlock.data(level,pos);
        if(data==null || !data.paused()) return false;
        var server=level.getServer();
        if(inMatch(player)) { tell(player,"Finish your current match before resuming this one.");return true; }
        try {
            var json=com.google.gson.JsonParser.parseString(data.savedMatch()).getAsJsonObject();
            if(!MatchSnapshot.participant(json,player.getUUID())) { tell(player,"This table holds another player's paused match.");return true; }
            Match match=MatchSnapshot.restore(json);
            if (match.trialsBattle != null && !TrialsTables.ownsCheckpoint(player, match.trialsBattle)) {
                tell(player, "This encounter belongs to an earlier Trials run.");
                return true;
            }
            if(!confirmed) {
                data.cancelReady(player.getUUID());
                ServerPlayNetworking.send(player,new craftbound.network.ResumeMatchPayload(pos,match.timed()));
                return true;
            }
            data.ready(player.getUUID(),level.getGameTime());
            var humans=new ArrayList<ServerPlayer>();
            for(MatchPlayer participant:match.players) if(!participant.isAi()) {
                var online=server.getPlayerList().getPlayer(participant.playerId);
                if(online==null || online.level()!=level || online.distanceToSqr(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5)>36
                        || inMatch(online) || !data.isReady(participant.playerId,level.getGameTime())) {
                    tell(player,"Ready to resume. Both players must confirm at this table within 60 seconds.");return true;
                }
                humans.add(online);
            }
            CardMasterEntity master=null;
            boolean reusedMaster=false;
            if(humans.size()==1) {
                // Wait for both chair chunks to finish loading before deciding the old NPC is missing.
                for(var direction:net.minecraft.core.Direction.Plane.HORIZONTAL) {
                    var adjacent=pos.relative(direction);
                    if(!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.pack(adjacent))) {
                        tell(player,"The table is still loading. Try again in a moment.");return true;
                    }
                }
                if(data.waitingMaster()!=null && level.getEntity(data.waitingMaster()) instanceof CardMasterEntity existing
                        && existing.waitingAt(pos)) { master=existing;reusedMaster=true; }
                if(master==null) master=CardMasterEntity.comeTo(level,pos,humans.get(0));
            }
            var seats=TableSeats.start(level,pos,humans.get(0),humans.size()==2?humans.get(1):master);
            if(seats==null) { if(master!=null&&!reusedMaster)master.leave(false);TableSeats.explain(player);return true; }
            if(master!=null)master.resumeMatch();
            match.resumeFromPause();
            data.waitingMaster(null);
            data.saveMatch("");
            SEATS.put(match,seats);
            for(var human:humans) MATCHES.put(human.getUUID(),match);
            if(master!=null)CARD_MASTERS.put(match,master);
            AI_COOLDOWN.put(match,AI_FIRST_MOVE_DELAY);
            sendAll(server,match);
        } catch(RuntimeException e) {
            craftbound.Craftbound.LOGGER.error("Could not resume table match at {}",pos,e);
            tell(player,"Could not restore this match. Its save is still at this table; check the server log.");
        }
        return true;
    }
    private static void tell(ServerPlayer player,String message) {
        player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(message));
    }

    private static void sendAll(MinecraftServer server, Match match) {
        // The tabletop is the same for everyone, so refresh it once rather than once per player.
        syncTable(match);
        for (MatchPlayer player : match.players) {
            if (player.playerId == null) {
                continue;
            }
            ServerPlayer online = server.getPlayerList().getPlayer(player.playerId);
            if (online != null) {
                sendView(online, match);
            }
        }
    }

    private static void send(ServerPlayer player, Match match) {
        syncTable(match);
        sendView(player, match);
    }

    private static void sendView(ServerPlayer player, Match match) {
        int side = match.sideOf(player.getUUID());
        MatchPlayer viewer = match.player(side);
        var state=MatchView.buildJson(match, side, viewer.eventsSent);
        PauseRequest request=PAUSE_REQUESTS.get(match);
        state.addProperty("pauseRequest",request==null?-1:request.side());
        if(request!=null) state.addProperty("myTurn",false);
        String json=state.toString();
        viewer.eventsSent = match.events.size();
        ServerPlayNetworking.send(player, new MatchStatePayload(json));
    }
}
