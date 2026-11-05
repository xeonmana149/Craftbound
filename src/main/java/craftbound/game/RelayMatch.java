package craftbound.game;

import craftbound.card.CardClass;
import craftbound.card.Cards;
import craftbound.card.DeckRules;
import craftbound.network.MatchActionPayload;
import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

/** Seat 0 and 2 are Ember, seat 1 and 3 are Tide. Team Health and turns are shared. */
public final class RelayMatch extends Match {
    public static final int TEAM_HEALTH = 30;
    public static final int RETIRE = 11, LEAVE_QUEUE = 12;
    final boolean[] ready = new boolean[4];
    final int[] focus = {1,0,1,0};
    String lastMove = "", resultReason = "";

    public static String teamName(int team) { return team == 0 ? "Ember" : "Tide"; }
    public static int teamOf(int seat) { return seat & 1; }
    public static int partnerSeat(int seat) { return seat ^ 2; }

    static String deckProblem(CardClass heroClass, List<CardRef> cards) {
        if (heroClass == CardClass.NEUTRAL || cards.size() < MatchManager.PRACTICE_MIN_DECK || cards.size() > DeckRules.DECK_SIZE)
            return "Choose a class deck of " + MatchManager.PRACTICE_MIN_DECK + " to " + DeckRules.DECK_SIZE + " cards.";
        Map<Identifier,Integer> counts = new HashMap<>();
        for (CardRef card : cards) {
            if (!DeckRules.allowed(card.id(), heroClass)) return "Your deck must contain only your class and Neutral cards.";
            if (counts.merge(card.id(), 1, Integer::sum) > DeckRules.maxCopies(Cards.get(card.id()).rarity()))
                return "Your deck exceeds the copy limit for " + Cards.name(card.id()).getString() + ".";
        }
        return null;
    }

    RelayMatch(MatchPlayer[][] teams, RandomSource rng) {
        super(new MatchPlayer[]{teams[0][0],teams[1][0],teams[0][1],teams[1][1]}, Phase.PLAYING,
            new boolean[]{true,true,true,true}, rng.nextInt(2), 0, 1, rng.nextLong(), List.of());
        Set<UUID> unique = new HashSet<>();
        for (MatchPlayer player : players) {
            String problem = deckProblem(player.heroClass, player.deck);
            if (problem != null) throw new IllegalArgumentException(problem);
            if (player.playerId == null || !unique.add(player.playerId)) throw new IllegalArgumentException("Four distinct players required");
            Collections.shuffle(player.deck, new java.util.Random(random.nextLong()));
            player.health = player.maxHealth = TEAM_HEALTH;
            for (int i=0;i<4;i++) player.hand.add(player.deck.removeFirst());
        }
        startTurn();
    }
    @Override int baseHealth() { return TEAM_HEALTH; }
    @Override boolean isEnemy(int owner, int other) { return teamOf(owner)!=teamOf(other); }
    @Override int opponentSide(int seat) { return focus[seat]; }
    @Override boolean isTurnSeat(int seat) { return inPlay(seat) && teamOf(seat)==current; }
    @Override boolean yourMove(int seat) { return super.yourMove(seat) && !ready[seat]; }
    @Override int healthPool(int seat) { return teamOf(seat); }
    @Override void heroHealthChanged(int seat) {
        MatchPlayer changed=player(seat), ally=player(partnerSeat(seat));
        ally.health=changed.health;
        ally.maxHealth=changed.maxHealth;
    }

    @Override void startTurn() {
        turn++;
        turnTicksRemaining=TURN_TICKS;
        Arrays.fill(ready,false);
        log("Team "+teamName(current)+" begins turn "+turn+".");
        int ally=partnerSeat(current);
        beginTurnFor(current,false);
        beginTurnFor(ally,false);
        if(turn>1 && !over) {
            draw(current);
            if(!over)draw(ally);
            checkWinner();
        }
    }

    private void endTeamTurn() {
        int ending=current;
        endTurnFor(ending);
        endTurnFor(partnerSeat(ending));
        clearTurnAttackBonuses();
        resolveDeaths();
        checkWinner();
        if(!over) {
            current=1-ending;
            startTurn();
        }
    }
    @Override public void endTurn(int seat) {
        if(over || pendingChoice()!=null || !isTurnSeat(seat)) return;
        ready[seat]=!ready[seat];
        lastMove=player(seat).name+(ready[seat]?" is ready to end the team turn.":" is taking another action.");
        if(ready[seat] && ready[partnerSeat(seat)]) endTeamTurn();
    }
    @Override public boolean tickTurnClock() {
        if(over || pendingChoice()!=null || phase!=Phase.PLAYING || --turnTicksRemaining>0) return false;
        turnTicksRemaining=0;
        lastMove=teamName(current)+" ran out of time.";
        endTeamTurn();
        return true;
    }

    private void finish(int winningTeam,String reason) {
        if(over) return;
        over=true;winner=winningTeam;resultReason=reason;lastMove=reason;log(reason);
    }
    @Override void draw(int seat) {
        if(over) return;
        if(player(seat).deck.isEmpty()) {
            finish(1-teamOf(seat),player(seat).name+" had to draw from an empty deck.");
            return;
        }
        super.draw(seat);
    }
    @Override void checkWinner() {
        if(over) return;
        boolean ember=player(0).health<=0, tide=player(1).health<=0;
        if(ember && tide) finish(-1,"Both teams ran out of Health.");
        else if(ember || tide) finish(ember?1:0,teamName(ember?0:1)+" ran out of Health.");
    }
    @Override void effectLoop() {
        finish(-1,"A repeating effect reached the resolution limit.");
    }
    @Override public void concede(int seat) {
        if(!inPlay(seat)) return;
        concededSide=seat;
        finish(1-teamOf(seat),player(seat).name+" conceded for "+teamName(teamOf(seat))+".");
    }
    void disconnected(UUID id) {
        int seat=sideOf(id);
        if(seat>=0)finish(1-teamOf(seat),player(seat).name+" did not reconnect in time.");
    }

    String act(UUID id, int action, int index, Target target) {
        int seat=sideOf(id);
        if(seat<0) return "You are watching this game.";
        int rival=!target.isNone() && isEnemy(seat,target.side())?target.side():opponentSide(seat);
        return act(id,action,index,rival,target);
    }
    String act(UUID id,int action,int index,int rival,Target target) {
        int seat=sideOf(id);
        if(over || seat<0) return "The game is over or you are spectating.";
        if(action==MatchActionPayload.CHOOSE_CARD)return chooseFromHand(seat,index)?null:"That choice is no longer available.";
        if(action==RETIRE) {concede(seat);return null;}
        if(!isTurnSeat(seat)) return "Wait for your team's turn.";
        if(action==MatchActionPayload.END_TURN) {endTurn(seat);return null;}
        if(!yourMove(seat)) return "Unready to continue playing.";
        if(!inPlay(rival) || !isEnemy(seat,rival)) return "Choose an opposing player.";
        if(!target.isNone() && (!inPlay(target.side()) || !target.hero() && player(target.side()).minion(target.minionId())==null))
            return "That target is no longer on the board.";
        int previous=focus[seat];
        focus[seat]=rival;
        String issue=perform(seat,action,index,target);
        if(issue!=null)focus[seat]=previous;
        return issue;
    }
    private String perform(int seat,int action,int index,Target target) {
        MatchPlayer pilot=player(seat);
        switch(action) {
            case MatchActionPayload.PLAY -> {
                String issue=whyCantPlay(seat,index);
                if(issue!=null) return issue;
                var card=pilot.hand.get(index);
                var legal=validTargets(seat,CardEffects.targetKind(card.id()),card.id());
                if(target.isNone()?!legal.isEmpty():!legal.contains(target)) return "Choose a highlighted target.";
                playCard(seat,index,target);
                lastMove=pilot.name+" played "+Cards.name(card.id()).getString()+".";
            }
            case MatchActionPayload.ATTACK -> {
                String issue=whyCantAttack(seat,index,target);
                if(issue!=null)return issue;
                attack(seat,index,target); lastMove=pilot.name+" attacked "+player(target.side()).name+"'s side.";
            }
            case MatchActionPayload.POWER -> {
                String issue=whyCantUsePower(seat);
                if(issue!=null)return issue;
                if(!HeroPowers.validTarget(this,seat,target))return "Choose a highlighted target.";
                usePower(seat,target);lastMove=pilot.name+" used "+HeroPowers.of(pilot).displayName().getString()+".";
            }
            default -> {return "Unknown move.";}
        }
        if(over) lastMove=resultReason;
        return null;
    }
}