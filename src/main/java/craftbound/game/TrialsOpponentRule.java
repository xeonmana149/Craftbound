package craftbound.game;

import com.google.gson.JsonObject;
import craftbound.card.*;
import java.util.*;
import net.minecraft.resources.Identifier;

/** Each encounter rule fires through actual combat events and follows the normal board limits. */
final class TrialsOpponentRule {
    static void reserveBossOpener(MatchPlayer player) {
        if(player.trialsFoe.tier()!=TrialsOpponents.Tier.BOSS || player.hand.isEmpty())return;
        for(String name:player.trialsFoe.anchors()){
            var id=Identifier.parse(name);var def=Cards.get(id);
            if(def==null||def.cost()>3)continue;
            if(player.hand.stream().anyMatch(c->c.id().equals(id)))return;
            for(int i=0;i<player.deck.size();i++)if(player.deck.get(i).id().equals(id)){
                int at=player.hand.size()-1;
                CardRef backup=player.hand.set(at,player.deck.get(i));
                player.deck.set(i,backup);
                return;
            }
        }
    }
    private static String types(TrialsOpponents.Profile foe) {
        if(foe.tribes().isEmpty())return "mob";
        return String.join(" or ",foe.tribes().stream().map(t->{
            String s=t.name().toLowerCase(Locale.ROOT);return Character.toUpperCase(s.charAt(0))+s.substring(1);
        }).toList())+" mob";
    }
    static String description(TrialsOpponents.Profile foe) {
        var a=foe.ability();int n=a.amount();String mob=types(foe);
        return switch(a.kind()){
            case NONE -> "Uses its class hero power.";
            case MOB_HEALTH -> "The first "+mob+" it plays each turn gains +"+n+" Health.";
            case PACK_ATTACK -> "Once on its turn, playing a "+mob+" beside another of its kind gives the new mob +"+n+" Attack.";
            case MOB_SPRINT -> "The first "+mob+" it plays each turn gains Sprint.";
            case BLOCK_HEALTH -> "The first Block it plays each turn gains +"+n+" Health.";
            case TOOL_DURABILITY -> "The first Tool it plays each turn gains +"+n+" Durability.";
            case HEAL_WOUNDED -> "At the start of its turn, heal its most damaged mob for "+n+".";
            case HEAVY_DRAW -> "The first time each turn it plays a mob costing 5 or more, it draws "+n+" "+(n==1?"card.":"cards.");
            case WAR_HORN -> "Every "+a.every()+" of its own turns, its mobs get +"+n+" Attack for that turn. Clear its board before the horn.";
            case DEATH_BLAST -> "The first time each turn one of its mobs with a damaging Drop dies, deal "+n+" damage to every enemy mob. Blocks are safe.";
            case DEATH_SPLIT -> a.card().isEmpty()?
                "The first time each turn one of its non-token mobs dies with a Drop that summons a mob, summon "+n+" extra "+(n==1?"copy":"copies")+" of that child.":
                "The first time each turn one of its non-token "+mob+"s dies, summon "+n+" "+cardName(a.card())+".";
        };
    }
    private static String cardName(String id){var key=Identifier.tryParse(id);return key!=null&&Cards.get(key)!=null?Cards.name(key).getString():id;}
    static JsonObject view(MatchPlayer p) {
        var f=p.trialsFoe;
        JsonObject out=new JsonObject();out.addProperty("name",f.name());out.addProperty("abilityName",f.ability().name());
        out.addProperty("abilityText",description(f));out.addProperty("boss",f.tier()==TrialsOpponents.Tier.BOSS);
        if(f.ability().kind()==TrialsOpponents.Kind.WAR_HORN)out.addProperty("turnsUntilHorn",f.ability().every()-p.trialsFoeTurns%f.ability().every());
        return out;
    }

    static void start(Match m,int side) {
        MatchPlayer p=m.player(side);var f=p.trialsFoe; var a=f.ability();
        p.trialsFoeTurns++;
        switch(a.kind()) {
            case HEAL_WOUNDED -> {
                var wounded=p.board.stream().filter(unit->!unit.isDead() && !unit.has(Keyword.BLOCK) && unit.health<unit.maxHealth)
                    .max(Comparator.comparingInt(unit->unit.maxHealth-unit.health)).orElse(null);
                if(wounded!=null){m.log(a.name()+": "+Match.name(wounded.card)+" recovers Health.",wounded.card);m.heal(Target.mob(side,wounded.id),a.amount(),false);}
            }
            case WAR_HORN -> {
                if(p.trialsFoeTurns%a.every()!=0)return;
                m.log(p.name+" sounds "+a.name()+".");
                for(var unit:List.copyOf(p.board))if(!unit.isDead() && !unit.has(Keyword.BLOCK)){
                    unit.temporaryAttack+=a.amount();m.buff(unit,a.amount(),0);
                }
            }
            default -> {}
        }
    }
    private static boolean typeMatches(TrialsOpponents.Profile f,CardDefinition c) {
        return c.isMob() && !c.keywords().contains(Keyword.BLOCK)
            && (f.tribes().isEmpty() || c.mobType()!=null && f.tribes().contains(c.mobType()));
    }
    static void afterPlay(Match m,int side,CardRef card,Minion unit) {
        var p=m.player(side);var f=p.trialsFoe;var a=f.ability();var c=Cards.get(card.id());
        if(m.over || m.current!=side || p.trialsFoeUsedTurn==m.turn)return;
        boolean alive=unit!=null && !unit.isDead() && p.board.contains(unit);
        switch(a.kind()){
            case MOB_HEALTH -> {if(!alive || !typeMatches(f,c))return;m.buff(unit,0,a.amount());}
            case BLOCK_HEALTH -> {if(!alive || !unit.has(Keyword.BLOCK))return;m.buff(unit,0,a.amount());}
            case MOB_SPRINT -> {if(!alive || !typeMatches(f,c))return;m.grantKeyword(unit,Keyword.SPRINT);}
            case PACK_ATTACK -> {
                if(!alive || !typeMatches(f,c) || p.board.stream().noneMatch(other->other!=unit && !other.isDead() && typeMatches(f,other.definition())))return;
                m.buff(unit,a.amount(),0);
            }
            case TOOL_DURABILITY -> {
                if(!c.isTool() || p.tool==null || !p.tool.card.equals(card))return;
                p.tool.uses+=a.amount();m.event(MatchEvent.Type.TOOL_UPGRADE,side,Match.HERO,card,p.tool.attack,p.tool.uses,null);
            }
            case HEAVY_DRAW -> {
                if(!alive||unit.has(Keyword.BLOCK)||c.cost()<5)return;
                p.trialsFoeUsedTurn=m.turn;
                for(int i=0;i<a.amount()&&!m.over;i++)m.draw(side);
            }
            default -> {return;}
        }
        p.trialsFoeUsedTurn=m.turn;m.log(p.name+"'s "+a.name()+" triggers.",card);
        m.resolveDeaths();m.checkWinner();
    }
    static void onDeath(Match m,int side,Minion dead) {
        var p=m.player(side);var f=p.trialsFoe;var a=f.ability();
        if(m.over || dead.has(Keyword.BLOCK) || p.trialsFoeUsedTurn==m.turn)return;
        var effects=CardCatalog.entry(dead.card.id()).effects();
        if(a.kind()==TrialsOpponents.Kind.DEATH_BLAST) {
            if(effects.stream().noneMatch(e->e.trigger()==CardCatalog.Trigger.DROP && e.action()==CardCatalog.Action.DAMAGE))return;
            p.trialsFoeUsedTurn=m.turn;m.log(p.name+"'s "+a.name()+" detonates.",dead.card);
            for(var enemy:List.copyOf(m.opponent(side).board))if(!enemy.has(Keyword.BLOCK)&&!enemy.isDead())m.damage(enemy,a.amount(),null);
        } else if(a.kind()==TrialsOpponents.Kind.DEATH_SPLIT && !dead.definition().token()) {
            Identifier child=a.card().isEmpty()?effects.stream().filter(e->e.trigger()==CardCatalog.Trigger.DROP && e.action()==CardCatalog.Action.SUMMON)
                .map(CardCatalog.Effect::card).filter(Objects::nonNull).findFirst().orElse(null):Identifier.tryParse(a.card());
            if(!a.card().isEmpty() && !typeMatches(f,dead.definition()))return;
            var def=child==null?null:Cards.get(child);
            if(def==null || Cards.deleted(child) || !def.isMob() || def.keywords().contains(Keyword.BLOCK))return;
            p.trialsFoeUsedTurn=m.turn;m.log(p.name+"'s "+a.name()+" splits "+Match.name(dead.card)+".",dead.card);
            for(int i=0;i<a.amount();i++)if(m.summon(side,new CardRef(child,false))==null)break;
        }
    }
    private TrialsOpponentRule(){}
}