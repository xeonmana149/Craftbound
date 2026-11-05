package craftbound.game;

import craftbound.board.BoardDesign;
import craftbound.card.*;
import java.util.*;
import net.minecraft.resources.Identifier;

/** An authored identity, its Workshop-adjustable encounter stats and its live-catalogue deck. */
final class TrialsRoute {
    record Encounter(String name, CardClass hero, BoardDesign board, String style, int health, int bonusXp,
                     int extraCards, int bounty, boolean elite, boolean boss, int deckSize, int stage,
                     TrialsOpponents.Profile profile) {
        String abilityName(){return profile.ability().name();}
        String abilityText(){return TrialsOpponentRule.description(profile);}
    }

    static Encounter forNode(long seed,int row,int lane,String kind,CardClass playerClass) {
        return forNode(seed,row,lane,kind,playerClass,"");
    }
    static Encounter forNode(long seed,int row,int lane,String kind,CardClass playerClass,String preferredId) {
        TrialsOpponents.Tier tier=switch(kind){case "BOSS"->TrialsOpponents.Tier.BOSS;case "ELITE"->TrialsOpponents.Tier.ELITE;default->TrialsOpponents.Tier.REGULAR;};
        var candidates=new ArrayList<>(TrialsContent.opponents().stream().filter(p->p.tier()==tier).toList());
        candidates.sort(Comparator.comparing(TrialsOpponents.Profile::id));
        Collections.shuffle(candidates,new Random(seed ^ (0x6A09E667F3BCC909L*(tier.ordinal()+1))));
        int stage=Math.clamp(row/2,0,5),pick=Math.floorMod(row/2+lane,candidates.size());
        var chosen=candidates.stream().filter(p->p.id().equals(preferredId)).findFirst().orElse(candidates.get(pick));
        var cls=chosen.heroClass();
        if(pool(cls).isEmpty() && playerClass!=CardClass.NEUTRAL)cls=playerClass;
        if(pool(cls).isEmpty()) for(var other:CardClass.values())if(other!=CardClass.NEUTRAL&&!pool(other).isEmpty()){cls=other;break;}
        return new Encounter(chosen.name(),cls,chosen.board(),chosen.style(),Math.min(100,chosen.health()+stage*chosen.healthStep()),
            chosen.bonusXp(),chosen.extraCards(),chosen.bounty(),tier==TrialsOpponents.Tier.ELITE,
            tier==TrialsOpponents.Tier.BOSS,Math.min(40,chosen.deckSize()+stage*chosen.deckStep()),stage,chosen);
    }

    static void prepare(Encounter foe,MatchPlayer opponent) {
        opponent.startingHealth=opponent.health=opponent.maxHealth=foe.health();
        opponent.maxXp=foe.bonusXp();
        opponent.trialsFoe=foe.profile();
        opponent.deckName=foe.name();
    }
    private static List<Identifier> pool(CardClass cls) { return Cards.collectible().stream().filter(id->DeckRules.allowed(id,cls)).toList(); }
    private static boolean realMob(CardDefinition c) {return c.isMob()&&!c.keywords().contains(Keyword.BLOCK);}
    private static boolean matches(TrialsOpponents.Tag tag,CardCatalog.Entry entry){
        var c=entry.definition();
        return switch(tag){
            case MOB->realMob(c);
            case BLOCK->c.keywords().contains(Keyword.BLOCK);
            case ACTION->c.kind()==CardKind.ITEM;
            case TOOL->c.isTool();
            case ENCHANTMENT->c.kind()==CardKind.ENCHANTMENT;
            case RANGED->c.keywords().contains(Keyword.RANGED);
            case SPRINT->c.keywords().contains(Keyword.SPRINT);
            case SHIELD->c.keywords().contains(Keyword.SHIELD);
            case DROP->entry.effects().stream().anyMatch(e->e.trigger()==CardCatalog.Trigger.DROP);
            case DAMAGE,HEAL,SUMMON,BUFF,FREEZE->entry.effects().stream().anyMatch(e->e.action().name().equals(tag.name()));
        };
    }

    private static final int[] CURVE={1,2,2,3,2,4,3,5,1,3,4,6,2,4,5,7};
    static List<CardRef> deck(Encounter foe,long seed) {
        var available=new ArrayList<>(pool(foe.hero()));
        if(available.isEmpty())return List.of();
        var f=foe.profile();
        var random=new Random(seed ^ (7919L*f.id().hashCode()) ^ (foe.stage()*65537L));
        Collections.sort(available); Collections.shuffle(available,random);
        boolean toolAccess=foe.hero()==CardClass.FIGHTER || available.stream().anyMatch(id->Cards.get(id).isTool());
        if(!toolAccess && available.stream().anyMatch(id->!needsTool(id)))available.removeIf(TrialsRoute::needsTool);
        List<CardRef> result=new ArrayList<>();Map<Identifier,Integer> counts=new HashMap<>();
        // Each legal signature piece gets a slot before the curve is filled. Higher-cost anchors wait for later floors.
        int maxAnchorCost=foe.boss()?10:foe.elite()?7:Math.min(8,3+foe.stage());
        int coreSlots=Math.min((foe.deckSize()+1)/2,f.anchors().size());
        var anchorOrder=new ArrayList<>(f.anchors());
        // Include a Tool before its enchantments when a limited anchor budget cannot take all pieces.
        anchorOrder.sort(Comparator.comparingInt(id->{var d=Cards.get(Identifier.parse(id));return d!=null&&d.isTool()?0:1;}));
        for(String name:anchorOrder) {
            if(result.size()>=coreSlots)break;
            var id=Identifier.parse(name);if(!available.contains(id) || Cards.get(id).cost()>maxAnchorCost)continue;
            if(needsTool(id)&&foe.hero()!=CardClass.FIGHTER && !result.stream().anyMatch(r->Cards.get(r.id()).isTool()))continue;
            add(result,counts,id);
        }
        int turn=0;
        while(result.size()<foe.deckSize()){
            int desired=Math.min(CURVE[(turn++)%CURVE.length],maxAnchorCost);
            boolean seekBody=result.stream().filter(r->realMob(Cards.get(r.id()))).count()*5<3*(result.size()+1);
            boolean hasTool=foe.hero()==CardClass.FIGHTER||result.stream().anyMatch(r->Cards.get(r.id()).isTool());
            // On tiny homebrew catalogues allow another pass through the pool after exhausting ordinary copy limits.
            boolean room=available.stream().anyMatch(id->counts.getOrDefault(id,0)<DeckRules.maxCopies(Cards.get(id).rarity()));
            Identifier best=null;int highest=Integer.MIN_VALUE;
            for(var id:available){
                var entry=CardCatalog.entry(id);var c=entry.definition();int copies=counts.getOrDefault(id,0);
                if(room && copies>=DeckRules.maxCopies(c.rarity()))continue;
                int score= -5*Math.abs(c.cost()-desired) - copies*9;
                if(c.cost()>maxAnchorCost)score-=25;
                if(c.cardClass()==foe.hero())score+=3;
                if(c.mobType()!=null && f.tribes().contains(c.mobType()))score+=15;
                if(f.anchors().contains(id.toString()))score+=6;
                for(var tag:f.tags())if(matches(tag,entry))score+=5;
                if(seekBody && realMob(c))score+=12;
                if(needsTool(id) && !hasTool)score-=35;
                if(entry.effects().stream().anyMatch(e->e.type()!=null && f.tribes().contains(e.type()) && e.target()!=CardCatalog.Aim.CHOSEN_ENEMY))score+=8;
                if(entry.auraType()!=null && f.tribes().contains(entry.auraType()))score+=9;
                if(score>highest){highest=score;best=id;}
            }
            add(result,counts,Objects.requireNonNull(best));
        }
        return List.copyOf(result);
    }
    private static boolean needsTool(Identifier id) {
        var entry=CardCatalog.entry(id);
        return entry.definition().kind()==CardKind.ENCHANTMENT || entry.effects().stream().anyMatch(e->
            e.action()==CardCatalog.Action.TOOL_DAMAGE || e.action()==CardCatalog.Action.UPGRADE_TOOL || e.action()==CardCatalog.Action.ENCHANT_TOOL);
    }
    private static void add(List<CardRef> result,Map<Identifier,Integer> counts,Identifier id) {
        result.add(new CardRef(id,false)); counts.merge(id,1,Integer::sum);
    }

    /** Encounter AI spends XP differently when it needs to establish a board or can activate a tribal payoff. */
    static int playPriority(Match match,int side,CardRef ref) {
        var p=match.player(side);var c=Cards.get(ref.id());
        int score=10*c.cost();
        if(p.trialsFoe==null)return score;
        var entry=CardCatalog.entry(ref.id());
        boolean body=realMob(c);
        int mobCount=(int)p.board.stream().filter(u->!u.isDead()&&!u.has(Keyword.BLOCK)).count();
        if(mobCount==0 && body)score+=24;
        if(mobCount>=2 && entry.hasAura())score+=14;
        if(p.trialsFoe.ability().kind()==TrialsOpponents.Kind.WAR_HORN &&
            p.trialsFoeTurns%p.trialsFoe.ability().every()==p.trialsFoe.ability().every()-1 && body)score+=9;
        if(body && c.mobType()!=null && p.trialsFoe.tribes().contains(c.mobType()))score+=4;
        if(entry.effects().stream().anyMatch(e->e.type()!=null && p.board.stream().anyMatch(mob->mob.definition().mobType()==e.type())))score+=7;
        return score;
    }
    private TrialsRoute() {}
}