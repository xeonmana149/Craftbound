package craftbound.game;

import craftbound.card.*;
import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

/** One shared battlefield and turn order. Seat IDs never change, including after elimination. */
public final class CommanderMatch extends Match {
    public static final int SEATS=4, DECK_SIZE=20, HEALTH=40, LETHAL=21, TAX=2;
    final CardRef[] leaders = new CardRef[SEATS];
    final int[] casts = new int[SEATS], unit = new int[SEATS], focus = new int[SEATS];
    final int[][] strikes = new int[SEATS][SEATS]; // attacking commander -> defending hero
    private final boolean[] eliminated = new boolean[SEATS];
    private int casting = -1;

    public static boolean eligible(Identifier id) {
        var d=Cards.get(id);
        return d!=null && !d.token() && !Cards.deleted(id) && d.isMob() && !d.keywords().contains(Keyword.BLOCK);
    }
    public static String problem(CardClass cls,List<CardRef> deck,Identifier leader) {
        if (cls==CardClass.NEUTRAL || deck.size()!=DECK_SIZE) return "Build a deck of exactly "+DECK_SIZE+" cards, including your commander.";
        Set<Identifier> seen=new HashSet<>();
        for (CardRef c:deck) {
            if (!DeckRules.allowed(c.id(),cls)) return "Use only your class and Neutral cards.";
            if (!seen.add(c.id())) return "Use one copy of each card; Enchanted copies count as the same card.";
        }
        if (!seen.contains(leader) || !eligible(leader)) return "Choose a collectible mob from this deck to lead it.";
        return null;
    }

    public CommanderMatch(List<MatchPlayer> entrants, Map<UUID,Identifier> picks, RandomSource rng) {
        super(entrants.toArray(MatchPlayer[]::new), Phase.PLAYING, new boolean[SEATS],0,0,1,rng.nextLong(),List.of());
        if (players.length!=SEATS || picks.size()!=SEATS) throw new IllegalArgumentException("Four distinct players required");
        Collections.shuffle(Arrays.asList(players), new java.util.Random(random.nextLong()));
        Set<UUID> ids=new HashSet<>();
        for (int i=0;i<SEATS;i++) {
            var p=player(i); var id=picks.get(p.playerId);
            String issue=problem(p.heroClass,p.deck,id);
            if (p.playerId==null || !ids.add(p.playerId) || issue!=null) throw new IllegalArgumentException(issue==null?"Invalid seat":issue);
            leaders[i]=p.deck.stream().filter(c->c.id().equals(id)).findFirst().orElseThrow();
            p.deck.remove(leaders[i]);
            Collections.shuffle(p.deck,new java.util.Random(random.nextLong()));
            p.health=p.maxHealth=HEALTH;
            for(int j=0;j<5;j++) deal(i);
            mulliganDone[i]=true; focus[i]=(i+1)%SEATS;
        }
        log("Commander begins. "+player(0).name+" takes the first turn.");
        startTurn();
    }
    public static final int COMMANDER_TURN_TICKS = 120 * 20;
    @Override public int turnLimitTicks() { return COMMANDER_TURN_TICKS; }
    @Override public int turnWarningTicks() { return 20 * 20; }
    @Override int baseHealth() { return HEALTH; }
    @Override boolean inPlay(int s) { return super.inPlay(s) && !eliminated[s]; }
    @Override int nextSeat(int s) {
        for(int i=1;i<=SEATS;i++) if(inPlay((s+i)%SEATS)) return (s+i)%SEATS;
        return s;
    }
    @Override int opponentSide(int s) {
        return inPlay(focus[s]) && focus[s]!=s ? focus[s] : nextSeat(s);
    }
    void focus(int s,int rival) { if(inPlay(rival) && rival!=s) focus[s]=rival; }
    @Override boolean commandUnit(int s,int mid) { return mid>0 && unit[s]==mid; }
    @Override void onRevival(int s,int oldId,int newId) { if(commandUnit(s,oldId)) unit[s]=newId; }
    @Override Minion summon(int s, CardRef card) {
        var mob=super.summon(s,card);
        if(casting==s && mob!=null) { unit[s]=mob.id; casting=-1; }
        return mob;
    }
    int commandCost(int s) {return Math.min(MAX_XP,Cards.get(leaders[s].id()).cost()+casts[s]*TAX);}
    String whyCantCommand(int s) {
        if(over || !inPlay(s) || current!=s) return "Wait for your turn.";
        if(player(s).minion(unit[s])!=null) return "Your commander is already in play.";
        if(player(s).xp<commandCost(s)) return "Not enough XP.";
        String full=player(s).rowFullReason(false);
        return full!=null?full:CardEffects.whyCantPlay(this,s,leaders[s].id());
    }
    void cast(int s, Target target) {
        if(whyCantCommand(s)!=null) return;
        int cost=commandCost(s); casts[s]++; casting=s;
        try { resolvePlay(s,-1,leaders[s],cost,target); } finally { casting=-1; }
    }
    @Override void combatHit(int s,int mid,int victim,int amount) {
        if(commandUnit(s,mid)) strikes[s][victim]+=Math.max(0,amount);
    }
    @Override public void concede(int s) {
        if(over || !inPlay(s)) return;
        player(s).health=0; log(player(s).name+" concedes."); checkWinner();
    }
    @Override void checkWinner() {
        if(over) return;
        int left=0,last=-1;
        for(int s=0;s<SEATS;s++) {
            if(!inPlay(s)) continue;
            boolean lethal=false;
            for(int a=0;a<SEATS;a++) if(strikes[a][s]>=LETHAL) lethal=true;
            if(player(s).health<=0 || lethal) {
                eliminated[s]=true; player(s).board.clear(); player(s).tool=null;
                log(player(s).name+(lethal?" falls to commander damage.":" is eliminated."));
            } else {left++;last=s;}
        }
        if(left<=1) {over=true;winner=last;log(last<0?"The table draws.":player(last).name+" wins Commander!");}
        else if(!inPlay(current)) passTurn();
    }
}
