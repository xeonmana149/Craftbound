package craftbound.test;

import craftbound.card.*;
import craftbound.client.match.*;
import craftbound.game.*;
import craftbound.network.SpectatorStatePayload;
import craftbound.sleeve.Sleeves;
import com.google.gson.*;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

/** Real GUI rendering at deterministic animation times, in a disposable test world. */
public final class GameplayRenderingTest implements FabricClientGameTest {
    static Identifier id(String path){return Identifier.parse("craftbound:"+path);}
    static final class Sample extends MatchScreen {
        long time=System.nanoTime()/1_000_000L;
        Sample(String state){super(state);}
        @Override protected long animationTime(){return time;}
        void event(String type,int side,int mid,String card,int amount,int extra,int targetSide,int targetMid){
            try{
                Class<?> e=Class.forName("craftbound.client.match.MatchState$Event");
                var constructor=e.getDeclaredConstructor(String.class,int.class,int.class,Identifier.class,boolean.class,int.class,int.class,int.class,boolean.class,int.class);
                constructor.setAccessible(true);var method=MatchScreen.class.getDeclaredMethod("play",e,int.class);method.setAccessible(true);
                method.invoke(this,constructor.newInstance(type,side,mid,card==null?null:id(card),false,amount,extra,targetSide,targetMid==0,targetMid),0);
            }catch(ReflectiveOperationException ex){throw new AssertionError(ex);}
        }
    }
    static Match fixture(){
        var deck=Collections.nCopies(30,new CardRef(Cards.ZOMBIE,false));
        Match m=new Match(new MatchPlayer("Fighter",UUID.randomUUID(),CardClass.FIGHTER,Sleeves.CLASSIC,deck),new MatchPlayer("Alchemist",UUID.randomUUID(),CardClass.ALCHEMIST,Sleeves.CLASSIC,deck),RandomSource.create(3));
        m.phase=Match.Phase.PLAYING;m.current=0;m.turn=6;m.events.clear();
        m.player(0).board.add(new Minion(1,new CardRef(Cards.ZOMBIE,false)));
        m.player(0).board.add(new Minion(2,new CardRef(id("furnace"),false)));
        m.player(1).board.add(new Minion(3,new CardRef(Cards.CREEPER,false)));
        m.player(1).board.add(new Minion(4,new CardRef(Cards.SHULKER,false)));
        m.player(0).hand.clear();m.player(0).hand.add(new CardRef(Cards.ZOMBIE,false));m.player(0).hand.add(new CardRef(id("splash_potion_of_harming"),false));
        for(var p:m.players){p.xp=7;p.maxXp=7;}
        return m;
    }
    private Sample open(ClientGameTestContext c,Match m){var ref=new java.util.concurrent.atomic.AtomicReference<Sample>();c.runOnClient(mc->{var screen=new Sample(MatchView.build(m,0,m.events.size()));ref.set(screen);mc.gui.setScreen(screen);});c.waitTicks(3);return ref.get();}
    private void shot(ClientGameTestContext c,Sample s,int ms,String name){c.runOnClient(mc->s.time+=ms);c.waitTicks(3);c.takeScreenshot(name);}
    public void runTest(ClientGameTestContext c){
        RenderTestSettings.apply(c);
        try(var world=c.worldBuilder().create()){
   TestCatalogue.install();
            c.runOnClient(mc->{mc.options.pauseOnLostFocus=false;mc.options.framerateLimit().set(60);});
            Match m=fixture();Sample play=open(c,m);
            c.runOnClient(mc->{play.event("PLAY",0,0,"zombie",0,0,-1,-1);play.event("SUMMON",0,8,"zombie",2,3,-1,-1);});
            shot(c,play,210,"combat-card-morph");shot(c,play,170,"combat-card-landing");
            Sample block=open(c,m);c.runOnClient(mc->block.event("DEATH",0,2,"furnace",0,0,-1,-1));
            shot(c,block,80,"combat-block-cracks-early");shot(c,block,220,"combat-block-cracks-middle");
            shot(c,block,220,"combat-block-cracks-final");shot(c,block,90,"combat-block-poof");shot(c,block,350,"combat-block-gone");
            Sample potion=open(c,m);c.runOnClient(mc->potion.event("PLAY",0,0,"splash_potion_of_harming",1,0,1,4));shot(c,potion,210,"combat-potion-flight");shot(c,potion,210,"combat-potion-shatter");
            Sample explosion=open(c,m);c.runOnClient(mc->explosion.event("DEATH",1,3,"creeper",0,0,-1,-1));shot(c,explosion,160,"combat-explosion");
            Sample shield=open(c,m);c.runOnClient(mc->{shield.event("STATUS",0,1,null,MatchEvent.STATUS_SHIELD,0,-1,-1);shield.event("STATUS",1,4,null,MatchEvent.STATUS_FROZEN,0,-1,-1);});shot(c,shield,150,"combat-keywords");
            Sample victory=open(c,m);m.over=true;m.winner=0;c.runOnClient(mc->victory.update(MatchView.build(m,0,m.events.size())));c.waitTicks(2);
            shot(c,victory,230,"combat-portrait-burst");shot(c,victory,200,"combat-portrait-shatter");shot(c,victory,300,"combat-portrait-settle");shot(c,victory,1000,"combat-victory");
            m.over=false;m.winner=-1;JsonObject state=JsonParser.parseString(MatchView.build(m,-1,m.events.size())).getAsJsonObject();
            for(var entry:state.getAsJsonArray("players")){var p=entry.getAsJsonObject();p.addProperty("handVisible",false);p.addProperty("handPending",false);p.addProperty("canRequestHand",true);}
            c.runOnClient(mc->mc.gui.setScreen(new SpectatorScreen(new SpectatorStatePayload(BlockPos.ZERO,state.toString(),true,""))));c.waitTicks(4);c.takeScreenshot("spectator-player-ui");
            c.runOnClient(mc->mc.gui.setScreen(null));
            powerSignatures(c);
        }
    }

    private void powerSignatures(ClientGameTestContext c) {
        CardCatalog.install(CardCatalog.builtin());
        for(String sound:List.of("entity.fishing_bobber.throw","entity.fishing_bobber.splash","entity.fishing_bobber.retrieve","entity.splash_potion.throw","entity.splash_potion.break","block.stone.place","item.shield.block","item.hoe.till","block.grass.place","entity.generic.eat","entity.player.burp","entity.player.attack.sweep","entity.player.attack.strong","block.stone.break","entity.item.pickup","block.lever.click","block.piston.extend","entity.wolf.ambient","block.grass.step"))
            if(!net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.containsKey(Identifier.withDefaultNamespace(sound)))throw new AssertionError("Missing power sound: "+sound);
        for (CardClass cls : CardClass.values()) {
            if (cls == CardClass.NEUTRAL) continue;
            var deck=Collections.nCopies(30,new CardRef(Cards.WOLF_PUP,false));
            Match m=new Match(new MatchPlayer(cls.name(),UUID.randomUUID(),cls,Sleeves.CLASSIC,deck),new MatchPlayer("Opponent",UUID.randomUUID(),cls,Sleeves.CLASSIC,deck),RandomSource.create(7));
            m.phase=Match.Phase.PLAYING;m.current=0;m.turn=6;m.events.clear();
            for(var p:m.players){p.xp=5;p.maxXp=5;p.hand.clear();p.hand.add(new CardRef(Cards.WOLF_PUP,false));}
            m.player(0).board.add(new Minion(1,new CardRef(Cards.COBBLESTONE_WALL,false)));
            m.player(1).board.add(new Minion(2,new CardRef(Cards.WOLF_PUP,false)));
            Sample s=open(c,m);
            var power=HeroPowers.of(m.player(0));
            int targetSide=power==HeroPowers.Power.THROW_POTION?1:power==HeroPowers.Power.FEED||power==HeroPowers.Power.ACTIVATE?0:-1;
            int targetMid=targetSide==1?2:targetSide==0?1:-1;
            c.runOnClient(mc->s.event("POWER",0,0,null,power.ordinal(),0,targetSide,targetMid));
            String label="power-"+cls.name().toLowerCase(java.util.Locale.ROOT);
            shot(c,s,200,label+"-gesture");shot(c,s,150,label+"-impact");
            shot(c,s,260,label+"-settle");
            String token=switch(power){case FISH->"caught_salmon";case BUILD->"cobblestone_wall";case SOW->"wheat";case CALL_PUP->"wolf_pup";default->null;};
            if(token!=null){
                c.runOnClient(mc->{s.time+=100;var card=Cards.get(id(token));s.event("SUMMON",0,10,token,card.attack(),card.health(),-1,-1);});
                shot(c,s,250,label+"-arrival");
            }
        }
        c.runOnClient(mc->mc.gui.setScreen(null));
        TestCatalogue.install();
    }
}
