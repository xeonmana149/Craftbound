package craftbound.game;

import craftbound.card.*;
import com.google.gson.*;

final class CompoundBuffChecks {
    static void ok(boolean value, String message) { WorkshopChecks.ok(value,message); }
    static String effect(String trigger, String action, String target, int attack, int health, String extra) {
        return "{\"trigger\":\""+trigger+"\",\"action\":\""+action+"\",\"target\":\""+target
                +"\",\"boardKind\":\"MOB\",\"type\":\"\",\"amount\":1,\"attack\":"+attack
                +",\"health\":"+health+",\"card\":\"\",\"excludeSelf\":false"+extra+"}";
    }
    static void run() {
        var old=CardCatalog.current();
        try {
            var root=JsonParser.parseString(old.json()).getAsJsonObject();
            var illager=EffectSequenceChecks.copy(root,"zombie","combo_illager","[]");illager.addProperty("type","ILLAGER");
            String buff=effect("PLAY","BUFF","CHOSEN_FRIENDLY",2,2,",\"keyword\":\"SPRINT\"").replace("\"type\":\"\"","\"type\":\"ILLAGER\"");
            EffectSequenceChecks.copy(root,"arrow","combo_rally","["+buff+"]");
            EffectSequenceChecks.copy(root,"arrow","combo_random","["+buff.replace("CHOSEN_FRIENDLY","RANDOM_FRIENDLY_MOB")+"]");
            EffectSequenceChecks.copy(root,"sharpness","combo_looting","["+effect("PLAY","ENCHANT_TOOL","SELF",0,0,"")+","+effect("ON_KILL","DRAW","SELF",0,0,"")+"]");
            EffectSequenceChecks.copy(root,"sharpness","implicit_looting","["+effect("ON_KILL","DRAW","SELF",0,0,"")+"]");
            var sword=EffectSequenceChecks.copy(root,"crafted_sword","combo_sword","[]");sword.addProperty("attack",2);
            EffectSequenceChecks.copy(root,"zombie","fox_animation_check","["+effect("PLAY","DESTROY_TOOL","ENEMY_HERO",0,0,"")+"]");
            CardCatalog.install(CardCatalog.parse(root.toString()));
            ok(CardCatalog.description(CardCatalog.entry(WorkshopChecks.id("combo_rally"))).equals("Give a friendly Illager mob +2/+2 and Sprint."),"Combined rules text is one clear sentence");
            ok(CardCatalog.description(CardCatalog.entry(WorkshopChecks.id("combo_looting"))).equals("Your tool gains: After this destroys a mob or block, draw 1 card."),"Looting omits redundant statless attachment text");
            ok(Cards.styledText(CardCatalog.description(CardCatalog.entry(WorkshopChecks.id("combo_looting")))).getSiblings().stream()
                    .anyMatch(c->c.getString().equals("After this destroys a mob or block")&&c.getStyle().isBold()),"Looting timing phrase remains bold in-game");
            var m=WorkshopChecks.match();var a=WorkshopChecks.mob(m,0,"combo_illager");var b=WorkshopChecks.mob(m,0,"combo_illager");
            var excluded=WorkshopChecks.mob(m,0,"zombie");var enemy=WorkshopChecks.mob(m,1,"combo_illager");
            int attack=a.attack,health=a.health;
            m.player(0).hand.add(new CardRef(WorkshopChecks.id("combo_rally"),false));
            var targets=m.validTargets(0,Target.Kind.FRIENDLY_MOB,WorkshopChecks.id("combo_rally"));
            ok(!targets.contains(Target.mob(0,excluded.id)),"Compound effect rejects non-Illagers");
            ok(!targets.contains(Target.mob(1,enemy.id)),"Compound effect rejects enemy Illagers");
            m.player(0).hand.clear();m.events.clear();WorkshopChecks.play(m,"combo_rally",Target.mob(0,a.id));
            ok(a.attack==attack+2&&a.health==health+2&&a.has(Keyword.SPRINT)&&a.ready,"Chosen Illager receives both stats and usable Sprint");
            ok(b.attack==attack&&!b.has(Keyword.SPRINT)&&!excluded.has(Keyword.SPRINT),"Other mobs receive neither part");
            ok(m.events.stream().filter(e->e.type()==MatchEvent.Type.EFFECT).count()==1,"Combined effect has one animation boundary");
            ok(m.whyCantAttack(0,a.id,Target.hero(1))!=null,"Granted Sprint still cannot hit face on arrival");
            ok(m.whyCantAttack(0,a.id,Target.mob(1,enemy.id))==null,"Granted Sprint can attack a mob");
            int aid=a.id;m=MatchSnapshot.restore(MatchSnapshot.capture(m));a=m.player(0).minion(aid);
            ok(a.has(Keyword.SPRINT)&&a.attack==attack+2&&a.health==health+2,"Both parts survive pause and resume");
            for(boolean frozen:new boolean[]{true,false}) {
                m=WorkshopChecks.match();a=WorkshopChecks.mob(m,0,"combo_illager");a.frozen=frozen;a.attackedThisTurn=!frozen;a.ready=false;
                WorkshopChecks.play(m,"combo_rally",Target.mob(0,a.id));
                ok(!a.ready,"Compound Sprint cannot bypass Frozen or refresh a spent attack");
            }
            for(int seed=0;seed<16;seed++) {
                m=WorkshopChecks.match();m.random.setSeed(seed);
                a=WorkshopChecks.mob(m,0,"combo_illager");b=WorkshopChecks.mob(m,0,"combo_illager");
                excluded=WorkshopChecks.mob(m,0,"zombie");WorkshopChecks.play(m,"combo_random");
                ok(a.has(Keyword.SPRINT)!=b.has(Keyword.SPRINT),"Exactly one random Illager gets Sprint");
                ok((a.attack==attack+2)==a.has(Keyword.SPRINT)&&(b.attack==attack+2)==b.has(Keyword.SPRINT),"Random stats and keyword always land on the same mob");
                ok(!excluded.has(Keyword.SPRINT),"Random selection respects type filter");
            }
            m=WorkshopChecks.match();m.player(0).hand.add(new CardRef(WorkshopChecks.id("combo_looting"),false));
            ok(m.whyCantPlay(0,0)!=null,"Looting requires an equipped tool");
            m.player(0).hand.clear();m.player(0).hand.add(new CardRef(WorkshopChecks.id("implicit_looting"),false));
            ok(m.whyCantPlay(0,0)!=null,"Ability-only enchantment also requires an equipped tool");
            ok(EffectPreview.forTarget(new EffectPreview.Source(WorkshopChecks.id("implicit_looting"),0,-1,false,2),EffectPreview.Subject.tool(0,WorkshopChecks.id("combo_sword")))!=null,"Ability-only enchantment highlights the equipped tool");
            for(String loot:new String[]{"combo_looting","implicit_looting"}) for(String victimName:new String[]{"wolf_pup","cobblestone_wall"}) {
                m=WorkshopChecks.match();WorkshopChecks.play(m,"combo_sword");m.events.clear();WorkshopChecks.play(m,loot);
                ok(m.player(0).tool.triggerEnchantments.size()==1&&m.player(0).hand.isEmpty(),"Looting attaches without drawing immediately");
                ok(m.player(0).tool.enchantments.size()==1&&m.events.stream().filter(e->e.type()==MatchEvent.Type.ENCHANT).count()==1,"Both attachment styles record and animate exactly one enchantment");
                m=MatchSnapshot.restore(MatchSnapshot.capture(m));
                var victim=WorkshopChecks.mob(m,1,victimName);int deck=m.player(0).deck.size();m.events.clear();
                m.attack(0,Match.HERO,Target.mob(1,victim.id));
                ok(m.player(0).hand.size()==1&&m.player(0).deck.size()==deck-1,"Looting draws after destroying a mob or block");
                ok(m.player(0).tool==null,"Looting works on last durability then tool breaks");
                ok(EffectSequenceChecks.event(m,MatchEvent.Type.DEATH)<EffectSequenceChecks.event(m,MatchEvent.Type.DRAW),"Looting draw follows casualty removal");
            }
            m=WorkshopChecks.match();WorkshopChecks.play(m,"combo_sword");WorkshopChecks.play(m,"combo_looting");
            var survivor=WorkshopChecks.mob(m,1,"ravager");m.attack(0,Match.HERO,Target.mob(1,survivor.id));
            ok(m.player(0).hand.isEmpty(),"Nonlethal attack does not draw");
            m=WorkshopChecks.match();WorkshopChecks.play(m,"combo_sword");WorkshopChecks.play(m,"combo_looting");WorkshopChecks.play(m,"combo_sword");
            var victim=WorkshopChecks.mob(m,1,"wolf_pup");m.attack(0,Match.HERO,Target.mob(1,victim.id));
            ok(m.player(0).hand.isEmpty(),"Replacing enchanted tool removes its Looting ability");
            var bad=root.deepCopy();var last=bad.getAsJsonArray("cards").asList().stream().map(JsonElement::getAsJsonObject).filter(c->c.get("id").getAsString().equals("craftbound:combo_rally")).findFirst().orElseThrow();
            last.getAsJsonArray("effects").get(0).getAsJsonObject().addProperty("keyword","BLOCK");
            boolean rejected=false;try {CardCatalog.parse(bad.toString());}catch(IllegalArgumentException expected){rejected=true;}
            ok(rejected,"Compound buffs cannot grant structural Block keyword");
            m=WorkshopChecks.match();
            m.player(1).tool=new Tool(new CardRef(WorkshopChecks.id("combo_sword"),false),2,1,false);
            WorkshopChecks.play(m,"fox_animation_check",Target.hero(1));
            var thief=m.player(0).board.get(0);
            var broken=m.events.stream().filter(e->e.type()==MatchEvent.Type.BREAK).findFirst().orElseThrow();
            ok(broken.side()==1&&broken.source().equals(Target.mob(0,thief.id))&&broken.card().id().equals(WorkshopChecks.id("combo_sword")),"Fox theft event identifies the exact source mob, victim and stolen tool");
            ok(m.player(1).tool==null&&m.player(0).tool==null,"Theft animation preserves destroy-tool rules, not a gameplay transfer");
            m=WorkshopChecks.match();WorkshopChecks.play(m,"combo_sword");var target=WorkshopChecks.mob(m,1,"wolf_pup");
            m.events.clear();m.attack(0,Match.HERO,Target.mob(1,target.id));
            broken=m.events.stream().filter(e->e.type()==MatchEvent.Type.BREAK).findFirst().orElseThrow();
            ok(broken.source()==null||broken.source().isNone()||broken.source().hero(),"Ordinary durability break has no stealing mob source");
        } finally {CardCatalog.install(old);}
    }
}
