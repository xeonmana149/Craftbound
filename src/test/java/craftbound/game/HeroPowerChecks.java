package craftbound.game;

import craftbound.card.*;
import com.google.gson.*;
import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

final class HeroPowerChecks {
    static Identifier id(String name) { return Identifier.parse("craftbound:"+name); }
    static void ok(boolean b,String why) { WorkshopChecks.ok(b,why); }
    static Match match(CardClass cls) {
        var deck=Collections.nCopies(30,new CardRef(id("wolf_pup"),false));
        var m=new Match(new MatchPlayer("A",UUID.randomUUID(),cls,id("classic"),deck),new MatchPlayer("B",UUID.randomUUID(),cls,id("classic"),deck),RandomSource.create(4));
        m.phase=Match.Phase.PLAYING;m.current=0;m.turn=6;m.events.clear();
        for(var p:m.players){p.hand.clear();p.xp=5;p.maxXp=5;}
        return m;
    }
    static Minion summon(Match m,int side,String card){return m.summon(side,new CardRef(id(card),false));}
    static void run() {
        CardCatalog.install(CardCatalog.builtin());
        String fishText=Cards.text(id("caught_pufferfish")).getString();
        ok(fishText.equals("Death: The enemy mob that killed this loses 2 Attack until the end of its controller's next turn.") && fishText.chars().allMatch(c->c<128), "Pufferfish text is readable plain text with unchanged duration");
        var m=match(CardClass.FIGHTER);m.usePower(0);
        ok(m.heroCanAttack(0)&&m.heroAttackOf(0)==1&&m.player(0).tool.uses==1&&m.player(0).xp==3,"Fighter equips a 1/1 sword for two XP");
        ok(m.player(0).tool.card.id().equals(Cards.CRAFTED_SWORD),"Fighter uses the required Wooden Sword token");
        m.attack(0,Match.HERO,Target.hero(1));ok(m.player(1).health==29&&!m.heroCanAttack(0)&&m.player(0).tool==null,"Wooden Sword attacks once and breaks");
        m.endTurn(0);ok(m.heroAttackOf(0)==0,"Used sword stays broken");
        m=match(CardClass.FIGHTER);m.player(0).frozen=true;m.usePower(0);ok(!m.heroCanAttack(0),"Frozen still prevents power attacks");
        m=match(CardClass.FIGHTER);m.player(0).tool=new Tool(new CardRef(id("wolf_pup"),false),3,2,false);m.usePower(0);
        ok(m.heroAttackOf(0)==1&&m.player(0).tool.attack==1&&m.player(0).tool.uses==1,"Craft replaces an existing tool");
        m=MatchSnapshot.restore(MatchSnapshot.capture(m));m.endTurn(0);
        ok(m.heroAttackOf(0)==1&&m.player(0).tool.uses==1,"Unused Wooden Sword survives pause and turn end");
        m=match(CardClass.FARMER);m.usePower(0);var crop=m.player(0).board.getFirst();
        ok(crop.attack==0&&crop.health==2&&crop.definition().mobType()==MobType.CROP,"Sow makes a 0/2 Crop");
        m=MatchSnapshot.restore(MatchSnapshot.capture(m));m.endTurn(0);ok(m.player(0).board.size()==1,"Crop waits for owner turn");
        m.endTurn(1);ok(m.player(0).board.isEmpty()&&m.player(0).maxXp==7&&m.player(0).xp==7,"Crop survives pause and grows usable max XP next turn");
        m=match(CardClass.FARMER);m.usePower(0);m.damage(m.player(0).board.getFirst(),2,null);m.resolveDeaths();m.endTurn(0);m.endTurn(1);
        ok(m.player(0).maxXp==6,"Destroyed Crop has no ramp payout");
        m=match(CardClass.FARMER);m.player(0).maxXp=10;m.usePower(0);m.endTurn(0);m.endTurn(1);ok(m.player(0).maxXp==10,"Maximum XP remains capped at ten");
        m=match(CardClass.BUILDER);m.usePower(0);var wall=m.player(0).board.getFirst();ok(wall.health==2&&wall.has(Keyword.BLOCK)&&!wall.has(Keyword.SHIELD),"Builder Wall");
        var ally=summon(m,0,"wolf_pup");m.endTurn(0);
        ok(ally.health==2&&ally.maxHealth==2&&wall.health==2,"Wall grants permanent Health to a friendly mob at end of turn");
        m=match(CardClass.TAMER);m.usePower(0);ok(m.player(0).board.getFirst().card.id().equals(id("wolf_pup")),"Tamer Pup");
        m=match(CardClass.MINER);m.usePower(0);ok(m.player(0).xp==3&&m.player(0).hand.getFirst().id().equals(id("iron_ore_token")),"Prospect gives ore for two XP");
        var pup=summon(m,0,"wolf_pup");m.playCard(0,0,Target.mob(0,pup.id));ok(pup.attack==2&&pup.health==2&&m.player(0).xp==2,"Iron Ore costs one and buffs +1/+1");
        m=match(CardClass.MINER);for(int n=0;n<10;n++)m.player(0).hand.add(new CardRef(id("wolf_pup"),false));m.usePower(0);ok(m.player(0).xp==5&&!m.player(0).powerUsed,"Full hand blocks Prospect");
        for(CardClass cls:List.of(CardClass.ALCHEMIST,CardClass.CHEF)) for(int side=0;side<2;side++) for(boolean hero:List.of(true,false)) {
            m=match(cls);var targetMob=summon(m,side,"cobblestone_wall");targetMob.health=1;m.player(side).health=28;
            var target=hero?Target.hero(side):Target.mob(side,targetMob.id);m.usePower(0,target);
            int hp=hero?m.player(side).health:targetMob.health;
            ok(hp==(cls==CardClass.CHEF?(hero?30:2):(hero?27:0)),"Potion/Feed can target either hero or Block");
            ok(m.player(0).xp==3&&m.player(0).powerUsed,"Targeted power spends two XP");
        }
        m=match(CardClass.ALCHEMIST);m.usePower(0,Target.mob(2,999));ok(m.player(0).xp==5&&!m.player(0).powerUsed,"Malformed target spends nothing");
        m=match(CardClass.FISHERMAN);Set<Identifier> catches=new HashSet<>();
        for(int i=0;i<150;i++){m.player(0).powerUsed=false;m.player(0).xp=5;m.player(0).board.clear();m.usePower(0);catches.add(m.player(0).board.getFirst().card.id());}
        ok(catches.equals(Set.of(id("caught_cod"),id("caught_salmon"),id("caught_pufferfish"))),"All three catches, no other cards");
        while(m.player(0).board.size()<Match.MAX_MOBS)summon(m,0,"wolf_pup");m.player(0).powerUsed=false;m.player(0).xp=5;m.usePower(0);ok(m.player(0).xp==5,"Full board rejects fishing");
        for (int i=0;i<Match.MAX_BLOCKS;i++) ok(summon(m,0,"cobblestone_wall")!=null,"Full mob row leaves room for walls");
        ok(summon(m,0,"cobblestone_wall")==null,"Fourth wall rejected");
        m=MatchSnapshot.restore(MatchSnapshot.capture(m));
        ok(m.player(0).board.size()==10,"Both full rows survive pause and resume");
        m=match(CardClass.FISHERMAN);var killer=summon(m,0,"caught_cod");killer.attack=4;var puffer=summon(m,1,"caught_pufferfish");
        m.damage(puffer,2,killer);m.resolveDeaths();ok(m.attackOf(killer)==2,"Pufferfish weakens killer");
        m.endTurn(0);ok(m.attackOf(killer)==2,"Penalty persists through current turn end");
        m=MatchSnapshot.restore(MatchSnapshot.capture(m));killer=m.player(0).board.getFirst();m.endTurn(1);ok(m.attackOf(killer)==2,"Penalty persists through controller next turn start");
        m.endTurn(0);ok(m.attackOf(killer)==4&&killer.attackDebuff==0,"Penalty expires at controller next turn end");
        m=match(CardClass.FISHERMAN);killer=summon(m,1,"caught_cod");killer.attack=4;puffer=summon(m,0,"caught_pufferfish");m.damage(puffer,2,killer);m.resolveDeaths();m.clearAppliedEffects(killer);m.buff(killer,-1,0);m.endTurn(0);m.endTurn(1);
        ok(m.attackOf(killer)==killer.definition().attack()-1,"Cleanse removes timed penalty without later restoring unrelated debuffs");
        m=match(CardClass.FISHERMAN);killer=summon(m,0,"caught_cod");puffer=summon(m,1,"caught_pufferfish");m.damage(puffer,2,null);m.resolveDeaths();ok(killer.attackDebuff==0,"Spell/hero kills do not invent a mob killer");
        m=match(CardClass.FISHERMAN);killer=summon(m,0,"caught_cod");killer.attack=4;killer.ready=true;killer.summonedThisTurn=false;puffer=summon(m,1,"caught_pufferfish");
        m.attack(0,killer.id,Target.mob(1,puffer.id));ok(m.attackOf(killer)==2&&killer.health==2,"Real attack attributes Pufferfish killer and retains retaliation");
        m=match(CardClass.FISHERMAN);killer=summon(m,1,"caught_cod");killer.attack=4;puffer=summon(m,0,"caught_pufferfish");puffer.ready=true;puffer.summonedThisTurn=false;
        m.attack(0,puffer.id,Target.mob(1,killer.id));ok(m.attackOf(killer)==2,"Retaliation kill also weakens killer");
        m.endTurn(0);m.endTurn(1);ok(m.attackOf(killer)==4,"Opponent's next turn end removes retaliation penalty");
        var root=JsonParser.parseString(CardCatalog.builtin().json()).getAsJsonObject();
        var block=root.getAsJsonArray("cards").asList().stream().map(JsonElement::getAsJsonObject).filter(c->c.get("id").getAsString().equals("craftbound:cobblestone_wall")).findFirst().orElseThrow().deepCopy();
        block.addProperty("id","craftbound:test_contraption");block.addProperty("class","REDSTONER");
        block.add("effects",JsonParser.parseString("[{\"trigger\":\"ACTIVATE\",\"action\":\"DRAW\",\"target\":\"SELF\",\"boardKind\":\"BOTH\",\"type\":\"\",\"amount\":1,\"attack\":0,\"health\":0,\"card\":\"\",\"excludeSelf\":false}]").getAsJsonArray());
        root.getAsJsonArray("cards").add(block);CardCatalog.install(CardCatalog.parse(root.toString()));
        m=match(CardClass.REDSTONER);ok(m.whyCantUsePower(0)!=null,"Redstoner requires an Activate block");
        wall=summon(m,0,"cobblestone_wall");m.usePower(0,Target.mob(0,wall.id));ok(m.player(0).xp==5,"Ordinary Block is not activatable");
        var machine=summon(m,0,"test_contraption");m.usePower(0,Target.mob(0,machine.id));ok(m.player(0).hand.size()==1&&m.player(0).xp==3,"Activate executes the Block's authored ability");
        ok(craftbound.mastery.MasteryProgress.EMPTY.earn(CardClass.REDSTONER,100).level(CardClass.REDSTONER)==2,"Redstoner mastery");
        CardCatalog.install(CardCatalog.builtin());
    }
}
