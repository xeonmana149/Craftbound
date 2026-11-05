package craftbound.game;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import craftbound.card.*;
import craftbound.sleeve.Sleeves;
import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

/** Private server journal. Deck/training slots and committed route edges survive disconnects and pauses. */
final class TrialsRun {
    static final int DRAFT_SIZE = 10, MIN_DECK = 8, MAX_DECK = 24, START_LIVES = 2;
    static final int CARD_PRICE = 2, CHARM_PRICE = 3, REMOVE_PRICE = 2, TRAIN_PRICE = 3;
    static final Gson JSON = new Gson();
    enum Phase { CLASS, DRAFT, MAP, READY, REWARD, ELITE_PRIZE, SHOP, SHOP_CARDS, SHOP_CHARMS, SERVICES, REPLACE,
        REST, EVENT, REMOVE, COPY, TRAIN_CARD, TRAIN_TYPE, BOON, TRADE_OUT, TRADE_IN, VICTORY, DEFEAT,
        // Recognize earlier saved journals once during migration.
        STOP, SALVAGE, PATH }
    enum Boon { HEART, PACK, TORCH }

    JsonObject rules;
    Map<String,Integer> purchases = new HashMap<>(), boonUses = new HashMap<>();
    List<JsonObject> pendingOptions = new ArrayList<>();
    List<TrialBoons.Earned> acquiredBoons; // Null identifies a journal written before relic icons.
    int bonusHealth;
    String shopItem, pendingCharm;
    static void validateContent(JsonObject root,List<String> errors) { TrialsRules.validate(root,errors); }
    int config(String key,int fallback) { return TrialsRules.n(rules.getAsJsonObject("config"),key,fallback); }
    int minimum() { return config("minDeck", MIN_DECK); }
    int maximum() { return config("maxDeck", MAX_DECK); }
    int optionCount() { return config("offerCount",3); }
    int version; // Missing from old journals; new journals set this explicitly.
    String id;
    long seed;
    int revision;
    Phase phase = Phase.CLASS;
    Phase afterDraft = Phase.MAP, parent, replacementOrigin;
    int repairGoal, selected = -1, marks = 3, lives = START_LIVES, wins, heart, pack, torch;
    int health = Match.START_HEALTH;
    CardClass heroClass = CardClass.NEUTRAL;
    List<String> choices = new ArrayList<>(), deck = new ArrayList<>(), visited = new ArrayList<>();
    List<TrialTraining> training = new ArrayList<>();
    List<String> charms = new ArrayList<>();
    String checkpointDimension;
    long checkpointPos;
    Integer checkpointBounty;
    private transient TrialsAct act;

    static TrialsRun create(long seed) {
        var run = new TrialsRun();
        run.version = 2;
        run.acquiredBoons = new ArrayList<>();
        run.rules = TrialsRules.snapshot();
        run.health = run.maxHealth(); run.lives = run.config("startLives",START_LIVES); run.marks = run.config("startMarks",3);
        run.id = UUID.randomUUID().toString();
        run.seed = seed;
        run.offer();
        return run;
    }
    TrialsAct act() { if (act == null) act = new TrialsAct(seed,rules); return act; }
    TrialsAct.Room room() { return visited.isEmpty() ? null : act().byId.get(visited.getLast()); }
    int maxHealth() { return config("startHealth",Match.START_HEALTH) + bonusHealth; }
    boolean terminal() { return phase == Phase.VICTORY || phase == Phase.DEFEAT; }
    int draftGoal() { return repairGoal > 0 ? repairGoal : config("draftSize",DRAFT_SIZE); }
    private List<Identifier> pool(CardClass cls) {
        return Cards.collectible().stream().filter(card -> DeckRules.allowed(card, cls)).toList();
    }
    private List<CardClass> eligibleClasses() {
        var available = new ArrayList<CardClass>();
        for (var cls : CardClass.values()) if (cls != CardClass.NEUTRAL && !pool(cls).isEmpty()) available.add(cls);
        return available;
    }
    boolean legal(String card) {
        var cardId = Identifier.tryParse(card);
        return cardId != null && DeckRules.allowed(cardId, heroClass);
    }
    long roomSeed() {
        return seed ^ (0x9E3779B97F4A7C15L * (room() == null ? 0 : 1 + room().row() * 3 + room().lane()));
    }
    TrialsRoute.Encounter encounter(TrialsAct.Room room) {
        return TrialsRoute.forNode(seed, room.row(), room.lane(), room.kind().name(), heroClass,room.opponent());
    }
    TrialsRoute.Encounter encounter() { return encounter(room()); }

    /** Preserve the previous run's deck, progress and paid upgrades. A live checkpoint keeps its battle key. */
    boolean migrate() {
        if (version >= 2 && rules != null) return false;
        rules = TrialsRules.snapshot();
        bonusHealth = heart * 4;
        boonUses.put("HEART",heart); boonUses.put("PACK",pack); boonUses.put("TORCH",torch);
        version = 2;
        health = maxHealth();
        for (int i = training.size(); i < deck.size(); i++) training.add(TrialTraining.NONE);
        if (terminal()) return true;
        var old = phase;
        boolean inFight = old == Phase.READY || checkpointDimension != null;
        int lastRow = wins * 2 - 1 + (inFight ? 1 : 0);
        for (int row = 0; row <= Math.min(lastRow, act().floors - 1); row++) {
            var reachable = act().next(visited);
            if (reachable.isEmpty()) break;
            visited.add(reachable.get(reachable.size()/2));
        }
        // An already active duel may have predated this graph. Attach it to the next reachable combat room.
        while (inFight && room() != null && !room().combat() && !act().next(visited).isEmpty())
            visited.add(act().next(visited).getFirst());
        if (old != Phase.CLASS) phase = inFight ? Phase.READY : deck.size() < draftGoal() ? Phase.DRAFT : Phase.MAP;
        afterDraft = Phase.MAP;
        if (checkpointDimension == null) revision++;
        pendingCharm = null;
        offer();
        return true;
    }

    private List<String> cardOffer(long salt, String exclude) {
        var pool = new ArrayList<>(pool(heroClass));
        if (exclude != null) pool.remove(Identifier.parse(exclude));
        Random random = new Random(roomSeed() ^ salt);
        Collections.shuffle(pool, random);
        int size = phase == Phase.SHOP_CARDS ? config("shopStock",3) : optionCount();
        if (pool.size() > size && random.nextInt(3) != 0) {
            for (int i = 0; i < pool.size(); i++) if (Cards.get(pool.get(i)).cardClass() == heroClass) {
                Collections.swap(pool, random.nextInt(size), i);
                break;
            }
        }
        return pool.stream().limit(size).map(Identifier::toString).toList();
    }
    private List<String> charmOffer() {
        var stock = TrialsContent.charms().stream().map(TrialCharmSpec::id).filter(id->!charms.contains(id)).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        Collections.shuffle(stock,new Random(roomSeed() ^ 71233L));
        return stock.stream().limit(phase==Phase.SHOP_CHARMS ? config("shopStock",3) : optionCount()).toList();
    }
    List<JsonObject> shopItems() { return TrialsRules.list(rules.getAsJsonObject("shop"),"items"); }
    JsonObject item(String id) { return TrialsRules.find(shopItems(),id); }
    String service(JsonObject item) { return TrialsRules.s(item,"service",""); }
    int itemPrice(JsonObject item) {
        String fallback=switch(service(item)){case "CARD"->"cardPrice";case "CHARM"->"charmPrice"; case "TRAIN"->"trainPrice";default->"removePrice";};
        return TrialsRules.n(item,"price",config(fallback,2));
    }
    List<JsonObject> options(Phase page) {
        return switch(page) {
            case REST -> TrialsRules.list(rules,"rest");
            case BOON -> TrialsRules.list(rules,"boons");
            case EVENT -> TrialsRules.list(TrialsRules.find(TrialsRules.list(rules,"events"),room().event()),"options");
            default -> List.of();
        };
    }
    private boolean trainable(int i) {
        if (i < 0 || i >= deck.size() || training.get(i) != TrialTraining.NONE) return false;
        var id = Identifier.parse(deck.get(i));
        return Arrays.stream(TrialTraining.values()).anyMatch(t -> t != TrialTraining.NONE && t.supports(id));
    }
    private boolean canTrain() {
        for (int i = 0; i < deck.size(); i++) if (trainable(i)) return true;
        return false;
    }
    private void offer() {
        choices = new ArrayList<>(switch (phase) {
            case CLASS -> {
                var classes = eligibleClasses();
                Collections.shuffle(classes, new Random(seed));
                yield classes.stream().limit(optionCount()).map(Enum::name).toList();
            }
            case DRAFT -> cardOffer(0xC5221L * (1 + deck.size()), null);
            case MAP -> act().next(visited);
            case REWARD -> deck.size() < maximum() ? cardOffer(12477L, null) : List.of();
            case SHOP -> shopItems().stream().filter(it->purchases.getOrDefault(TrialsRules.s(it,"id",""),0)<TrialsRules.n(it,"stock",1)).map(it->TrialsRules.s(it,"id","")).toList();
            case SHOP_CARDS -> cardOffer(41719L + purchases.getOrDefault(shopItem,0), null);
            case SHOP_CHARMS, ELITE_PRIZE -> charmOffer();
            case REPLACE -> List.copyOf(charms);
            case REST, EVENT, BOON -> TrialsRules.ids(options(phase));
            case COPY, TRAIN_CARD, REMOVE, TRADE_OUT -> deck;
            case TRAIN_TYPE -> selected >= 0 && selected < deck.size()
                    ? Arrays.stream(TrialTraining.values()).filter(t -> t != TrialTraining.NONE && t.supports(Identifier.parse(deck.get(selected))))
                    .map(Enum::name).toList() : List.of();
            case TRADE_IN -> selected >= 0 && selected < deck.size() ? cardOffer(selected * 131L + 2093, deck.get(selected)) : List.of();
            default -> List.of();
        });
    }

    boolean reconcile() {
        boolean changed = migrate();
        changed |= TrialBoons.ensure(this);
        if (terminal() || checkpointDimension != null) return changed;
        int before = deck.size();
        for (int i = before - 1; i >= 0; i--) {
            if (!legal(deck.get(i))) { deck.remove(i); training.remove(i); }
            else if (training.get(i) != TrialTraining.NONE && !training.get(i).supports(Identifier.parse(deck.get(i)))) {
                training.set(i, TrialTraining.NONE); changed = true;
            }
        }
        if (deck.size() < before) {
            if (phase != Phase.DRAFT) {
                if (phase==Phase.MAP || phase==Phase.READY) afterDraft=phase;
                else if (room()==null) afterDraft=Phase.MAP;
                else afterDraft=switch(room().kind()){
                    case SHOP->Phase.SHOP;case REST->Phase.REST;case EVENT->Phase.EVENT;
                    // A cancelled elite substitution still owes the free charm; it has never been charged.
                    default->phase==Phase.ELITE_PRIZE || (phase==Phase.REPLACE && replacementOrigin==Phase.ELITE_PRIZE) ? Phase.ELITE_PRIZE : Phase.REWARD;
                };
                pendingOptions.clear();pendingCharm=null;replacementOrigin=null;parent=null;shopItem=null;
                repairGoal = before;
            }
            phase = Phase.DRAFT;
            selected = -1;
            changed = true;
        }
        // Offers seen in an existing journal survive unrelated additions to the live catalogue.
        // Replace an offer only if the visible set has become illegal or its available size changed.
        var old=List.copyOf(choices);
        if (changed) offer();
        else switch(phase) {
            case CLASS -> {
                var allowed=eligibleClasses();
                if(choices.size()!=Math.min(optionCount(),allowed.size())
                        || choices.stream().anyMatch(key->!allowed.contains(CardClass.valueOf(key))))offer();
            }
            case DRAFT,REWARD,SHOP_CARDS,TRADE_IN -> {
                int cap=phase==Phase.SHOP_CARDS?config("shopStock",3):optionCount();
                int available=pool(heroClass).size()-(phase==Phase.TRADE_IN?1:0);
                int need=phase==Phase.REWARD&&deck.size()>=maximum()?0:Math.min(cap,Math.max(0,available));
                if(choices.size()!=need || choices.stream().anyMatch(key->!legal(key)))offer();
            }
            case SHOP_CHARMS,ELITE_PRIZE -> {
                if(choices.stream().anyMatch(key->TrialCharmSpec.find(key)==null) || choices.size()!=charmOffer().size())offer();
            }
            case TRAIN_TYPE -> offer();
        }
        changed |= !old.equals(choices);
        if (changed) revision++;
        return changed;
    }

    int price(String key) {
        if(phase==Phase.SHOP) return itemPrice(item(key));
        if(shopItem!=null && (phase==Phase.SHOP_CARDS || phase==Phase.SHOP_CHARMS || phase==Phase.REPLACE || phase==Phase.TRAIN_CARD || phase==Phase.REMOVE))
            return itemPrice(item(shopItem));
        var opt=TrialsRules.find(options(phase),key);
        return opt==null?0:TrialsRules.n(opt,"costMarks",0);
    }
    private boolean canUse(String service) {
        return switch(service) {
            case "TRAIN","TRAIN_CARD" -> canTrain();
            case "REMOVE","REMOVE_CARD" -> deck.size()>minimum();
            case "COPY_CARD","ADD_CARD","CARD" -> deck.size()<maximum();
            case "SWAP_CARD" -> pool(heroClass).size()>1;
            case "CHARM" -> !charmOffer().isEmpty();
            default -> true;
        };
    }
    boolean enabled(int index) {
        if(index<0 || index>=choices.size())return false;
        String key=choices.get(index);
        if(phase==Phase.TRAIN_CARD) return trainable(index);
        if(phase==Phase.SHOP) return marks>=price(key) && canUse(service(item(key)));
        if(phase!=Phase.REST && phase!=Phase.BOON && phase!=Phase.EVENT) return marks>=price(key);
        var opt=TrialsRules.find(options(phase),key);
        int spentMarks=0,spentHealth=0;
        for(var pending:pendingOptions){spentMarks+=TrialsRules.n(pending,"costMarks",0);spentHealth+=TrialsRules.n(pending,"costHealth",0);}
        if(spentMarks+TrialsRules.n(opt,"costMarks",0)>marks || spentHealth+TrialsRules.n(opt,"costHealth",0)>=health)return false;
        if(phase==Phase.BOON && boonUses.getOrDefault(key,0)>=TrialsRules.n(opt,"limit",Integer.MAX_VALUE))return false;
        var ops=TrialsRules.list(opt,"operations");
        if(ops.size()==1 && TrialsRules.s(ops.getFirst(),"type","").equals("HEAL") && health==maxHealth())return false;
        return ops.stream().allMatch(op->canUse(TrialsRules.s(op,"type","")));
    }
    private void add(String card, TrialTraining t) { deck.add(card); training.add(t); }
    private void completeRoom() { selected=-1; parent=null; replacementOrigin=null; pendingCharm=null; shopItem=null; pendingOptions.clear(); phase=Phase.MAP; }
    private void enter(String destination) {
        if (!act().next(visited).contains(destination)) throw new IllegalStateException("Disconnected Trials room");
        visited.add(destination);
        purchases.clear();
        selected = -1;
        phase = switch(room().kind()) {
            case FIGHT, ELITE, BOSS -> Phase.READY;
            case SHOP -> Phase.SHOP; case REST -> Phase.REST; case EVENT -> Phase.EVENT;
        };
    }
    private void service(Phase next) { parent = phase; selected = -1; phase = next; }
    private void settle() {
        if(shopItem!=null) {
            marks-=itemPrice(item(shopItem));purchases.merge(shopItem,1,Integer::sum);
            shopItem=null; parent=null; selected=-1; phase=Phase.SHOP;
            return;
        }
        TrialBoons.ensure(this);
        for(var opt:pendingOptions) {
            int healthBefore = bonusHealth, drawBefore = pack, xpBefore = torch;
            marks-=TrialsRules.n(opt,"costMarks",0);health-=TrialsRules.n(opt,"costHealth",0);
            if(opt.has("boon"))boonUses.merge(TrialsRules.s(opt,"id",""),1,Integer::sum);
            for(var op:TrialsRules.list(opt,"operations")) {
                int v=TrialsRules.n(op,"amount",1);
                switch(TrialsRules.s(op,"type","")) {
                    case "MARKS" -> marks+=v;
                    case "HEAL" -> health=Math.min(maxHealth(),health+v);
                    case "MAX_HEALTH" -> {bonusHealth+=v;health+=v;}
                    case "OPENING_DRAW" -> pack=Math.min(4,pack+v);
                    case "START_XP" -> torch=Math.min(Match.MAX_XP-1,torch+v);
                }
            }
            TrialBoons.record(this, opt, healthBefore, drawBefore, xpBefore);
        }
        completeRoom();
    }
    private void chooseOption(String id) {
        var opt=TrialsRules.find(options(phase),id).deepCopy();
        if(phase==Phase.BOON)opt.addProperty("boon",true);
        pendingOptions.add(opt);var ops=TrialsRules.list(opt,"operations");
        switch(TrialsRules.s(ops.getLast(),"type","")) {
            case "COPY_CARD" -> service(Phase.COPY);
            case "SWAP_CARD" -> service(Phase.TRADE_OUT);
            case "TRAIN_CARD" -> service(Phase.TRAIN_CARD);
            case "REMOVE_CARD" -> service(Phase.REMOVE);
            case "ADD_CARD" -> service(Phase.REWARD);
            case "CHARM" -> service(Phase.ELITE_PRIZE);
            case "BOON" -> phase=Phase.BOON;
            default -> settle();
        }
    }
    boolean pick(int index) {
        if (checkpointDimension != null || !enabled(index)) return false;
        String key = choices.get(index);
        switch (phase) {
            case CLASS -> { heroClass = CardClass.valueOf(key); phase = Phase.DRAFT; }
            case DRAFT -> {
                if (!legal(key)) return false;
                add(key, TrialTraining.NONE);
                if (deck.size() >= draftGoal()) { phase = afterDraft; afterDraft = Phase.MAP; repairGoal = 0; }
            }
            case MAP -> enter(key);
            case REWARD -> { if(!legal(key) || deck.size()>=maximum())return false;add(key,TrialTraining.NONE);settle(); }
            case SHOP -> {
                shopItem=key;
                service(switch(service(item(key))) {
                    case "CARD" -> Phase.SHOP_CARDS; case "CHARM" -> Phase.SHOP_CHARMS;
                    case "TRAIN" -> Phase.TRAIN_CARD; default -> Phase.REMOVE;
                });
            }
            case SHOP_CARDS -> {
                if(!legal(key) || deck.size()>=maximum())return false;
                add(key,TrialTraining.NONE);settle();
            }
            case SHOP_CHARMS, ELITE_PRIZE -> {
                if(charms.contains(key))return false;
                replacementOrigin=phase;
                if(charms.size()>=config("charmSlots",2)){pendingCharm=key;phase=Phase.REPLACE;}
                else gainCharm(key);
            }
            case REPLACE -> {
                if(pendingCharm==null || (shopItem!=null && marks<itemPrice(item(shopItem))))return false;
                charms.remove(index);gainCharm(pendingCharm);
            }
            case REST, EVENT, BOON -> chooseOption(key);
            case REMOVE -> {
                if (deck.size() <= minimum()) return false;
                deck.remove(index); training.remove(index); settle();
            }
            case COPY -> {
                if (deck.size() >= maximum()) return false;
                add(key, training.get(index)); settle();
            }
            case TRAIN_CARD -> { selected = index; phase = Phase.TRAIN_TYPE; }
            case TRAIN_TYPE -> {
                if (!trainable(selected)) return false;
                var upgrade = TrialTraining.valueOf(key);
                if (!upgrade.supports(Identifier.parse(deck.get(selected)))) return false;
                training.set(selected, upgrade); settle();
            }
            case TRADE_OUT -> { selected = index; phase = Phase.TRADE_IN; }
            case TRADE_IN -> {
                if (selected < 0 || selected >= deck.size() || !legal(key) || deck.get(selected).equals(key)) return false;
                deck.set(selected,key); training.set(selected,TrialTraining.NONE); settle();
            }
            default -> { return false; }
        }
        revision++; offer(); return true;
    }
    private void gainCharm(String charm) {
        charms.add(charm); pendingCharm=null;replacementOrigin=null;
        if(shopItem!=null || !pendingOptions.isEmpty())settle(); else phase=Phase.REWARD;
    }
    boolean leaveChoice() {
        if (checkpointDimension!=null)return false;
        switch(phase) {
            case SHOP, REST, EVENT -> completeRoom();
            case REWARD,ELITE_PRIZE -> {
                if(!pendingOptions.isEmpty())backToOwner();
                else if(phase==Phase.ELITE_PRIZE)phase=Phase.REWARD;
                else completeRoom();
            }
            case BOON -> {
                if(!pendingOptions.isEmpty())pendingOptions.removeLast();
                phase=room().kind()==TrialsAct.Kind.REST?Phase.REST:Phase.EVENT;
            }
            case SHOP_CARDS,SHOP_CHARMS,TRAIN_CARD,COPY,REMOVE,TRADE_OUT -> backToOwner();
            case REPLACE -> {phase=replacementOrigin;replacementOrigin=null;pendingCharm=null;}
            case TRAIN_TYPE -> {selected=-1;phase=Phase.TRAIN_CARD;}
            case TRADE_IN -> {selected=-1;phase=Phase.TRADE_OUT;}
            default -> {return false;}
        }
        revision++;offer();return true;
    }
    private void backToOwner(){
        phase=parent;parent=null;selected=-1;
        if(shopItem!=null)shopItem=null;
        else if(!pendingOptions.isEmpty())pendingOptions.removeLast();
    }
    Match createBattle(String name, UUID playerId, RandomSource random) {
        if (phase != Phase.READY || checkpointDimension != null || deck.size() < minimum() || deck.stream().anyMatch(card -> !legal(card)))
            throw new IllegalStateException("Trial deck is not ready");
        List<CardRef> refs = new ArrayList<>();
        for (int i=0;i<deck.size();i++) refs.add(new CardRef(Identifier.parse(deck.get(i)), false, training.get(i)));
        var human = new MatchPlayer(name, playerId, heroClass, Sleeves.CLASSIC, refs);
        human.deckName = "Trials draft"; human.trialsCharms = List.copyOf(charms);
        human.health = health; human.maxHealth = human.startingHealth = maxHealth();
        human.maxXp = torch;
        var foe = encounter();
        var boss = new MatchPlayer(foe.name(), null, foe.hero(), Sleeves.CLASSIC, TrialsRoute.deck(foe, roomSeed()));
        TrialsRoute.prepare(foe, boss);
        var match = new Match(human, boss, random);
        match.boardDesign = foe.board();
        int side = match.sideOf(playerId);
        for (int i = 0; i < pack; i++) match.deal(side);
        for (int i = 0; i < foe.extraCards(); i++) match.deal(match.opponentSide(side));
        match.log(act().name + " • Floor " + (room().row()+1) + " — " + foe.name());
        return match;
    }
    void startBattle(String dimension, long pos) {
        if (phase != Phase.READY || checkpointDimension != null) throw new IllegalStateException("Encounter already resolved");
        checkpointDimension = dimension; checkpointPos = pos; checkpointBounty = encounter().bounty(); revision++;
    }
    String battleKey() { return id + "/" + revision; }
    boolean finish(String key, Boolean win) { return finish(key, win, health); }
    boolean finish(String key, Boolean win, int remainingHealth) {
        if (checkpointDimension == null || phase != Phase.READY || !battleKey().equals(key)) return false;
        checkpointDimension = null;
        if (Boolean.TRUE.equals(win)) {
            health = Math.clamp(remainingHealth, 1, maxHealth());
            marks += checkpointBounty == null ? encounter().bounty() : checkpointBounty;
            wins++;
            phase = switch(room().kind()) {
                case BOSS -> Phase.VICTORY; case ELITE -> charms.size()<TrialsContent.charms().size()?Phase.ELITE_PRIZE:Phase.REWARD; default -> Phase.REWARD;
            };
        } else if (Boolean.FALSE.equals(win)) {
            if (--lives == 0) phase = Phase.DEFEAT;
            else health = maxHealth();
        }
        checkpointBounty=null;
        revision++; offer(); return true;
    }
    boolean retire() {
        if (terminal() || checkpointDimension != null) return false;
        phase = Phase.DEFEAT; lives = 0; revision++; offer(); return true;
    }
    JsonObject view(String message) { return TrialsJournalView.build(this,message); }
}
