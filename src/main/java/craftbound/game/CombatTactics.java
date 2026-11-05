package craftbound.game;

import craftbound.card.CardCatalog;
import craftbound.card.Cards;
import craftbound.card.Keyword;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Attack-only forecasts use visible mobs and equipped tools. Re-evaluate after each real attack and its triggers. */
final class CombatTactics {
    static boolean takeLethal(Match match,int side) {
        Target face=Target.hero(match.opponentSide(side));
        int total=0, first=Match.HERO, strongest=0;
        for (Minion mob : match.player(side).board) {
            if (match.whyCantAttack(side,mob.id,face)!=null) continue;
            int power=match.attackOf(mob);
            total+=power;
            if (power>strongest) { first=mob.id; strongest=power; }
        }
        if (match.whyCantAttack(side,Match.HERO,face)==null) {
            int power=match.heroAttackOf(side);
            total+=power;
            if (power>strongest) first=Match.HERO;
        }
        if (total<=0 || total<match.player(face.side()).health) return false;
        match.attack(side,first,face);
        return true;
    }

    private record Swing(int id,int damage,int counter,int valueLost) {}
    private record Trade(int first,Target target,int margin,int cost,int swings) {
        boolean betterThan(Trade other) {
            if (other==null) return true;
            if ((margin>0)!=(other.margin>0)) return margin>0;
            // When both lines survive, save attackers. When still in danger, remove the most incoming damage.
            if (margin<=0 && margin!=other.margin) return margin>other.margin;
            if (cost!=other.cost) return cost<other.cost;
            if (swings!=other.swings) return swings<other.swings;
            return margin>other.margin;
        }
    }

    static boolean defend(Match match,int side) {
        MatchPlayer me=match.player(side);
        int enemySide=match.opponentSide(side);
        Incoming board=new Incoming(match,enemySide,null);
        if (board.total<me.health) return false;
        List<Minion> shields=me.board.stream().filter(m->!m.isDead() && m.has(Keyword.SHIELD)).toList();
        int before=me.health-board.faceDamage(shields,0,0);
        if (before>0) return false;

        List<Integer> ids=new ArrayList<>();
        for (Minion attacker : me.board) if (attacker.canAttack() && match.attackOf(attacker)>0) ids.add(attacker.id);
        if (match.heroCanAttack(side)) ids.add(Match.HERO);
        if (ids.isEmpty()) return false;

        // With no guarding Shields or overlapping enemy auras, saved damage from separate kills adds up.
        // Allocate attackers across victims so two small trades can beat spending every attack on one kill.
        List<Minion> enemies=match.player(enemySide).board;
        boolean independent=shields.isEmpty() && enemies.stream().noneMatch(m->CardCatalog.entry(m.card.id()).hasAura());
        Trade[] plans=new Trade[1<<ids.size()];
        Trade best=null;
        for (Minion target : enemies) {
            if (target.isDead() || target.invulnerable || target.totem) continue;
            Target aim=Target.mob(enemySide,target.id);
            int retaliation=match.retaliationOf(target);
            Swing[] swings=new Swing[ids.size()];
            int legal=0, availableDamage=0;
            for (int i=0;i<ids.size();i++) {
                int id=ids.get(i);
                if (match.whyCantAttack(side,id,aim)!=null) continue;
                Minion mob=me.minion(id);
                int power=mob==null ? match.heroAttackOf(side) : match.attackOf(mob);
                int hit=target.afterArmor(power);
                if (hit<=0 || mob==null && retaliation>=me.health) continue;
                int counter=mob==null ? retaliation : mob.invulnerable ? 0 : mob.afterArmor(retaliation);
                int cost=mob==null ? counter : counter>=mob.health && !mob.totem ? power+mob.health : 0;
                swings[i]=new Swing(id,hit,counter,cost);
                legal|=1<<i;
                availableDamage+=hit;
            }
            if (availableDamage<target.health) continue;
            Incoming after=new Incoming(match,enemySide,target);
            int[] shieldBits=new int[ids.size()];
            for (int i=0;i<ids.size();i++) for (int g=0;g<shields.size();g++)
                if (swings[i]!=null && shields.get(g).id==ids.get(i) && swings[i].counter>0) shieldBits[i]=1<<g;
            int[] forecasts=new int[1<<shields.size()];
            Arrays.fill(forecasts,-1);
            Trade[] earlier=independent ? plans.clone() : null;
            for (int mask=legal;mask!=0;mask=(mask-1)&legal) {
                int dealt=0, smallest=Integer.MAX_VALUE, first=-1, life=me.health, cost=0, hitShields=0;
                for (int bits=mask;bits!=0;bits &= bits-1) {
                    int i=Integer.numberOfTrailingZeros(bits);
                    Swing s=swings[i];
                    dealt+=s.damage;
                    if (s.damage<smallest) { smallest=s.damage; first=s.id; }
                    if (s.id==Match.HERO) life-=s.counter;
                    cost+=s.valueLost;
                    hitShields|=shieldBits[i];
                }
                // Do not reserve extra swings that could have gone into a second threat.
                if (dealt<target.health || dealt-smallest>=target.health) continue;
                if (forecasts[hitShields]<0) forecasts[hitShields]=after.faceDamage(shields,hitShields,retaliation);
                int margin=life-forecasts[hitShields], gain=margin-before;
                if (gain<=0) continue;
                var trade=new Trade(first,aim,margin,cost,Integer.bitCount(mask));
                if (trade.betterThan(best)) best=trade;
                if (!independent) continue;
                if (trade.betterThan(plans[mask])) plans[mask]=trade;
                int free=(plans.length-1)^mask;
                for (int used=free;used!=0;used=(used-1)&free) {
                    var prev=earlier[used];
                    if (prev==null) continue;
                    var both=new Trade(prev.first,prev.target,prev.margin+gain,prev.cost+cost,prev.swings+trade.swings);
                    if (both.betterThan(plans[used|mask])) plans[used|mask]=both;
                    if (both.betterThan(best)) best=both;
                }
            }
        }
        if (best==null) return false;
        match.attack(side,best.first,best.target);
        return true;
    }

    /** Face damage after paying for any Shields. An exhausted or freshly summoned enemy refreshes next turn. */
    private static final class Incoming {
        private final int[] power;
        private final int total, bypass;
        Incoming(Match match,int side,Minion removed) {
            List<Integer> hits=new ArrayList<>();
            int through=0;
            var player=match.player(side);
            var aura=removed==null ? null : CardCatalog.entry(removed.card.id());
            for (Minion mob : player.board) {
                if (mob==removed || mob.isDead() || mob.frozen || mob.has(Keyword.BLOCK)
                        || mob.withered && mob.afterArmor(1)>=mob.health) continue;
                int hit=match.attackOf(mob);
                if (aura!=null && aura.hasAura() && aura.auraMatches(mob.definition()))
                    hit=Math.max(0,hit-CardCatalog.passiveAmount(removed.card.id()));
                if (hit<=0) continue;
                hits.add(hit);
                if (mob.has(Keyword.IGNORE_SHIELD)) through+=hit;
            }
            int hero=match.heroAttackOf(side);
            if (!player.frozen && hero>0) {
                hits.add(hero);
                if (player.tool!=null && Cards.get(player.tool.card.id()).keywords().contains(Keyword.IGNORE_SHIELD)) through+=hero;
            }
            power=hits.stream().mapToInt(Integer::intValue).toArray();
            total=Arrays.stream(power).sum(); bypass=through;
        }

        int faceDamage(List<Minion> shields,int hitShields,int retaliation) {
            if (shields.isEmpty() || total==bypass) return total;
            int count=1<<power.length;
            boolean[] spent=new boolean[count];
            spent[0]=true;
            for (int i=0;i<shields.size();i++) {
                Minion guard=shields.get(i);
                int hp=guard.health-((hitShields & (1<<i))!=0 ? guard.afterArmor(retaliation) : 0);
                if (hp<=0) continue;
                if (guard.invulnerable) return bypass;
                int[] damage=new int[count];
                for (int mask=1;mask<count;mask++)
                    damage[mask]=damage[mask & (mask-1)]+guard.afterArmor(power[Integer.numberOfTrailingZeros(mask)]);
                if (damage[count-1]<hp) return bypass;
                boolean[] next=new boolean[count];
                for (int used=0;used<count;used++) if (spent[used]) {
                    int free=count-1-used;
                    for (int group=free;group!=0;group=(group-1)&free)
                        if (damage[group]>=hp) next[used|group]=true;
                }
                spent=next;
            }
            int best=bypass;
            for (int used=0;used<count;used++) if (spent[used]) {
                int rest=total;
                for (int bits=used;bits!=0;bits &= bits-1) rest-=power[Integer.numberOfTrailingZeros(bits)];
                best=Math.max(best,rest);
            }
            return best;
        }
    }
    private CombatTactics() {}
}
