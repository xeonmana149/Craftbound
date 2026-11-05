package craftbound.game;

import com.google.gson.*;
import craftbound.card.*;
import craftbound.board.BoardDesign;
import net.minecraft.resources.Identifier;
import java.util.*;

final class PausedMatchChecks {
    static void run() {
        var vote=new MatchManager.PauseRequest(0,600);
        WorkshopChecks.ok(!vote.canRespond(0,1)&&vote.canRespond(1,599)&&!vote.canRespond(1,600)
                &&!vote.canRespond(-1,1),"Only opponent can approve an unexpired request");
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        Match match=WorkshopChecks.match();
        match.boardDesign = BoardDesign.AMETHYST;
        var boardSave = MatchSnapshot.capture(match);
        var restoredBoard = MatchSnapshot.restore(boardSave);
        for (int viewer : new int[]{0, 1, -1}) {
            var view = JsonParser.parseString(MatchView.build(restoredBoard,viewer,0)).getAsJsonObject();
            WorkshopChecks.ok(view.get("boardDesign").getAsString().equals("amethyst"), "Both seats and spectators get the saved board");
        }
        WorkshopChecks.ok(JsonParser.parseString(MatchView.publicBoard(restoredBoard,true)).getAsJsonObject()
                .get("boardDesign").getAsString().equals("amethyst"),"Paused tabletop keeps the selected design");
        boardSave.remove("boardDesign");
        WorkshopChecks.ok(MatchSnapshot.restore(boardSave).boardDesign==BoardDesign.CLASSIC,"Older tables resume with Classic");
        var mastery=craftbound.mastery.MasteryProgress.EMPTY;
        WorkshopChecks.ok(!BoardDesign.AMETHYST.unlockedBy(mastery)
                && BoardDesign.AMETHYST.unlockedBy(mastery.earn(CardClass.CHEF,100)),
                "A class reaching level 2 unlocks its first mastery board");
        var a=match.player(0);
        a.maxHealth=36;a.health=24;a.xp=5;a.maxXp=8;a.hunger=3;
        a.heroAttacked=true;a.frozen=true;a.skippedAttackFromFreeze=true;a.powerUsed=true;a.reap=true;
        a.hand.add(new CardRef(Cards.ENDER_DRAGON,true));
        a.tool=new Tool(new CardRef(Cards.DIAMOND_SWORD,true),7,2,false);
        a.tool.enchantments.add(new Tool.Enchantment(new CardRef(Identifier.parse("craftbound:sharpness"),true),2,0));
        a.tool.triggerEnchantments.add(new CardRef(Identifier.parse("craftbound:sharpness"),true));
        var mob=match.summon(0,new CardRef(Cards.ZOMBIE,true));
        mob.attack=9;mob.health=1;mob.maxHealth=7;mob.frozen=true;mob.withered=true;mob.totem=true;
        mob.invulnerable=true;mob.temporaryAttack=3;mob.attackDebuff=1;mob.ready=false;
        mob.lastBattleHardenedTurn=6;mob.grantedKeywords.add(Keyword.SHIELD);
        match.turn=8;match.current=1;match.mulliganDone[0]=true;match.mulliganDone[1]=true;
        JsonObject snapshot=MatchSnapshot.capture(match);
        Match restored=MatchSnapshot.restore(JsonParser.parseString(snapshot.toString()).getAsJsonObject());
        WorkshopChecks.ok(restored.phase==match.phase&&restored.current==1&&restored.turn==8,"Pause restores exact turn");
        WorkshopChecks.ok(restored.nextMinionId()==match.nextMinionId(),"Pause preserves mob ID sequence");
        WorkshopChecks.ok(restored.player(0).hand.equals(a.hand)&&restored.player(0).deck.equals(a.deck),"Private hand and ordered deck round trip");
        var b=restored.player(0);
        WorkshopChecks.ok(b.maxHealth==36&&b.health==24&&b.hunger==3&&b.xp==5&&b.maxXp==8,"Hero health, XP and hunger round trip");
        WorkshopChecks.ok(b.heroAttacked&&b.frozen&&b.skippedAttackFromFreeze&&b.powerUsed&&b.reap,"Hero once-per-turn and status state");
        WorkshopChecks.ok(b.tool.card.enchanted()&&b.tool.attack==7&&b.tool.enchantments.equals(a.tool.enchantments)
                &&b.tool.triggerEnchantments.equals(a.tool.triggerEnchantments),"Tool stats and granted triggers restored");
        var m=b.board.getFirst();
        WorkshopChecks.ok(m.totem&&m.frozen&&m.withered&&m.invulnerable&&m.grantedKeywords.contains(Keyword.SHIELD)
                &&m.attackDebuff==1&&m.temporaryAttack==3&&m.lastBattleHardenedTurn==6&&m.attack==9&&m.health==1&&m.maxHealth==7,"Full mob state round trip");
        for(int i=0;i<100;i++)WorkshopChecks.ok(match.random.nextLong()==restored.random.nextLong(),"Same random continuation");
        WorkshopChecks.ok(restored.events.isEmpty(),"No old animations replayed");
        var publicBoard=JsonParser.parseString(MatchView.publicBoard(match,true)).getAsJsonObject();
        WorkshopChecks.ok(publicBoard.get("paused").getAsBoolean()&&!publicBoard.has("events")&&!publicBoard.has("log"),"Public projection omits private events and logs");
        for(var p:publicBoard.getAsJsonArray("players"))WorkshopChecks.ok(!p.getAsJsonObject().has("hand"),"No hand identities sent to spectators");
        WorkshopChecks.ok(!publicBoard.toString().contains("ender_dragon"),"Secret hand card cannot leak to table");
        var tag=craftbound.block.CardTableBlockEntity.publicTag(publicBoard.toString(),90);
        WorkshopChecks.ok(!tag.contains("match"),"Block update NBT never contains private save");
        WorkshopChecks.ok(!tag.contains("waitingMaster"),"Paused NPC identity stays in server save only");
        WorkshopChecks.ok(tag.getString("board").orElseThrow().equals(publicBoard.toString())
                &&tag.getFloat("yaw").orElseThrow()==90,"Public board and orientation survive block update encoding");
        var human=Arrays.stream(match.players).filter(p->!p.isAi()).findFirst().orElseThrow();
        WorkshopChecks.ok(MatchSnapshot.participant(snapshot,human.playerId)&&!MatchSnapshot.participant(snapshot,UUID.randomUUID()),"Only original participants can resume");
        match.phase=Match.Phase.MULLIGAN;match.mulliganDone[1]=false;
        var opening=MatchSnapshot.restore(MatchSnapshot.capture(match));
        WorkshopChecks.ok(opening.phase==Match.Phase.MULLIGAN&&!opening.mulliganDone[1]&&opening.player(0).hand.equals(a.hand),"Mulligan survives pause without dealing twice");
        // Every new mutable field must be part of the saved player/mob/tool objects.
        var compare=MatchSnapshot.capture(restored);var twice=MatchSnapshot.capture(MatchSnapshot.restore(compare));
        compare.remove("seed");twice.remove("seed");
        WorkshopChecks.ok(compare.equals(twice),"Complete snapshot structure round trip");
    }
    static void deletion() {
        var baseline=CardCatalog.current();
        var root=JsonParser.parseString(baseline.json()).getAsJsonObject();
        int original=Cards.collectorNumber(Cards.RAVAGER),later=Cards.collectorNumber(Cards.WARDEN),total=Cards.collectorTotal();
        for(var card:root.getAsJsonArray("cards"))if(card.getAsJsonObject().get("id").getAsString().equals(Cards.RAVAGER.toString()))
            card.getAsJsonObject().addProperty("deleted",true);
        var next=CardCatalog.parse(root.toString());CardCatalog.validateReplacement(baseline,next);
        try {
            CardCatalog.install(next);
            WorkshopChecks.ok(Cards.get(Cards.RAVAGER)!=null&&!Cards.collectible().contains(Cards.RAVAGER)
                    &&!Cards.ofRarity(CardRarity.EPIC).contains(Cards.RAVAGER),"Deleted definitions retained outside active pools");
            WorkshopChecks.ok(Cards.collectorNumber(Cards.RAVAGER)==original&&Cards.collectorNumber(Cards.WARDEN)==later&&Cards.collectorTotal()==total,"Reserved collector numbers never move");
            WorkshopChecks.ok(!DeckRules.allowed(Cards.RAVAGER,CardClass.FIGHTER),"Deleted card cannot enter a deck");
            var savedDeck=new craftbound.deck.DeckContents(CardClass.FIGHTER,
                    craftbound.binder.BinderContents.EMPTY.add(Cards.RAVAGER,false,10),true);
            WorkshopChecks.ok(craftbound.deck.DeckBuilding.whyCantPlay(savedDeck,(id,ench)->99)!=null,"Existing decks with deleted cards cannot silently start short");
        } finally { CardCatalog.install(baseline); }
    }
}
