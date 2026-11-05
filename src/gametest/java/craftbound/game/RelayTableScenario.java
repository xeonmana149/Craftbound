package craftbound.game;

import com.mojang.authlib.GameProfile;
import craftbound.block.ModBlocks;
import craftbound.client.match.RelayScreen;
import craftbound.network.RelayJoinPayload;
import java.util.*;
import java.util.concurrent.atomic.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;

/** Four owned binder decks, a shared turn, independent captains, and a GUI attack into the second enemy seat. */
public final class RelayTableScenario {
    private static void button(Screen s,String name) {
        var b=s.children().stream().filter(x->x instanceof AbstractWidget w && w.getMessage().getString().equals(name))
            .map(AbstractWidget.class::cast).findFirst().orElseThrow(()->new AssertionError("Missing "+name+" button on "+s));
        CommanderTableScenario.click(s,b.getX()+b.getWidth()/2,b.getY()+b.getHeight()/2);
    }
    public static void check(ClientGameTestContext ctx, TestSingleplayerContext world) {
        var at=new AtomicReference<BlockPos>();var bots=new ArrayList<UUID>();
        var match=new AtomicReference<RelayMatch>(); var allyUnit=new AtomicInteger(); var myUnit=new AtomicInteger();
        var turnAtStart=new AtomicInteger(); var server=world.getServer();
        server.runOnServer(ms->{
            var real=ms.getPlayerList().getPlayers().getFirst();var pos=real.blockPosition().offset(1,0,2);at.set(pos);
            CommanderTableScenario.place(ms.overworld(),pos,ModBlocks.TEAM_TABLE);
            var chairs=TableSeats.ensure(ms.overworld(),pos,net.minecraft.core.Direction.NORTH);
            if(!real.startRiding(chairs.get(0),true,false))throw new AssertionError("Player cannot take the near-side chair");
            CommanderTableScenario.equip(real,craftbound.deck.DeckFormat.DUOS);real.inventoryMenu.broadcastChanges();TableLobby.open(real,pos);
        });
        ctx.waitTicks(6);
        ctx.runOnClient(mc->{button(mc.gui.screen(),"Ready");button(mc.gui.screen(),"Take my seat");});
        ctx.waitTicks(6);
        ctx.runOnClient(mc->{if(!(mc.gui.screen() instanceof RelayScreen))throw new AssertionError("Real client failed to join Relay lobby");});
        server.runOnServer(ms->{
            var real=ms.getPlayerList().getPlayers().getFirst();var level=ms.overworld();var pos=at.get();
            var reservedChairs=TableSeats.ensure(level,pos,net.minecraft.core.Direction.NORTH);
            if(RelayTables.canSitWhileWaiting(level,pos,reservedChairs.get(0).blockPosition())
                    || !RelayTables.canSitWhileWaiting(level,pos,reservedChairs.get(2).blockPosition()))
                throw new AssertionError("Waiting lobby must reserve its player chair and leave its teammate chair available");
            String[] names={"EmberAlly","TideOne","TideTwo"};
            for(int i=0;i<3;i++) {
                var id=UUID.randomUUID();bots.add(id);var fake=FakePlayer.get(level,new GameProfile(id,names[i]));
                ms.getPlayerList().getPlayersByUUID().put(id,fake);
                int seat=i==0?2:i==1?1:3;
                var chair=TableSeats.ensure(level,pos,net.minecraft.core.Direction.NORTH).get(seat);
                fake.setPos(chair.getX(),chair.getY(),chair.getZ());
                if(!fake.startRiding(chair,true,false))throw new AssertionError("Fixture cannot sit in seat "+seat);
                CommanderTableScenario.equip(fake,craftbound.deck.DeckFormat.DUOS);RelayTables.join(fake,new RelayJoinPayload(pos,0,CommanderTableScenario.DECK,i==0?1:0));
                if(i<2 && RelayTables.matchAt(level,pos)!=null)throw new AssertionError("Relay started without four players");
            }
            var m=RelayTables.matchAt(level,pos);if(m==null)throw new AssertionError("Four Relay seats failed to start");
            match.set(m);
            var expected=List.of(real.getUUID(),bots.get(1),bots.get(0),bots.get(2));
            for(int seat=0;seat<4;seat++)if(!m.player(seat).playerId.equals(expected.get(seat)))
                throw new AssertionError("Seat position, not the packet team value, must choose the team");
            if(m.current%2!=0){
                m.act(m.player(1).playerId,craftbound.network.MatchActionPayload.END_TURN,0,Target.NONE);
                m.act(m.player(3).playerId,craftbound.network.MatchActionPayload.END_TURN,0,Target.NONE);
            }
            if(!m.player(0).playerId.equals(real.getUUID()))throw new AssertionError("Wrong seat for Ember participant");
            turnAtStart.set(m.turn);
            for(int side=0;side<4;side++) {
                var mob=m.summon(side,new CardRef(craftbound.card.Cards.ZOMBIE,false));
                mob.ready=true; mob.summonedThisTurn=false;
                if(side==0)myUnit.set(mob.id); else if(side==2)allyUnit.set(mob.id);
                m.summon(side,new CardRef(craftbound.card.Cards.COBBLESTONE_WALL,false));
            }
            RelayTables.open(real,pos);
        });
        ctx.waitTicks(5);ctx.runOnClient(mc->mc.gui.toastManager().clear());ctx.waitTick();ctx.takeScreenshot("craftbound-team-duos");
        server.runOnServer(ms->{
            var m=match.get();
            for(int s=0;s<4;s++){
                for(int i=1;i<Match.MAX_MOBS;i++)m.summon(s,new CardRef(craftbound.card.Cards.ZOMBIE,false));
                for(int i=1;i<Match.MAX_BLOCKS;i++)m.summon(s,new CardRef(craftbound.card.Cards.COBBLESTONE_WALL,false));
            }
            var board=com.google.gson.JsonParser.parseString(RelayTables.publicBoard(m)).getAsJsonObject();
            var spots=craftbound.client.match.TableBoardFace.spots(board);
            if(spots.size()!=4*(Match.MAX_MOBS+Match.MAX_BLOCKS))throw new AssertionError("World board omitted a player's pieces");
            for(var spot:spots){
                if(spot.x()<0 || spot.y()<0 || spot.x()+spot.w()>640 || spot.y()+spot.h()>640)
                    throw new AssertionError("World piece falls outside the playing surface");
                for(var other:spots)if(spot!=other && spot.x()<other.x()+other.w() && spot.x()+spot.w()>other.x()
                        && spot.y()<other.y()+other.h() && spot.y()+spot.h()>other.y())
                    throw new AssertionError("Two public board pieces overlap");
            }
            var real=ms.getPlayerList().getPlayers().getFirst();RelayTables.open(real,at.get());
        });
        ctx.waitTicks(3);
        TrialsMapScenario.capture(ctx,"craftbound-team-duos-compact",640,480);
        server.runOnServer(ms->{
            var m=match.get();
            for(int s=0;s<4;s++)m.player(s).board.subList(2,m.player(s).board.size()).clear();
            RelayTables.open(ms.getPlayerList().getPlayers().getFirst(),at.get());
        });
        ctx.waitTicks(4);
        ctx.runOnClient(mc->button(mc.gui.screen(),"Ready"));
        ctx.waitTicks(4);
        server.runOnServer(ms -> {
            var m=match.get();
            if (m.turn!=turnAtStart.get()) throw new AssertionError("One ready vote ended the team turn");
            var ally=m.player(2);
            if(m.act(ally.playerId,craftbound.network.MatchActionPayload.ATTACK,allyUnit.get(),Target.hero(1))!=null)
                throw new AssertionError("The second player could not act during the same team turn");
            if(m.player(1).health>=30 || m.player(1).health!=m.player(3).health || m.player(0).health!=30)
                throw new AssertionError("Damage from the partner did not change the correct team total");
            m.damageHero(1,m.player(1).health-1);
            RelayTables.open(ms.getPlayerList().getPlayers().getFirst(),at.get());
        });
        ctx.waitTicks(3);
        ctx.runOnClient(mc->button(mc.gui.screen(),"Unready"));
        ctx.waitTicks(3);
        ctx.runOnClient(mc->{
            var sc=(craftbound.client.match.MultiplayerBoardScreen)mc.gui.screen();
            MultiplayerBoardScenario.click(sc,sc.targetBounds(0,myUnit.get()),com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        });
        ctx.waitTicks(2);
        ctx.runOnClient(mc->{
            var sc=(craftbound.client.match.MultiplayerBoardScreen)mc.gui.screen();
            MultiplayerBoardScenario.click(sc,sc.targetBounds(3,0),com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        });
        ctx.waitTicks(5);
        server.runOnServer(ms->{
            var m=match.get();
            if(!m.over || m.winner%2!=0 || RelayTables.matchAt(ms.overworld(),at.get())!=null)
                throw new AssertionError("Attack into second enemy hero did not finish the team game");
            ms.overworld().removeBlock(at.get(),false);
            for(UUID id:bots)ms.getPlayerList().getPlayersByUUID().remove(id);
        });
        ctx.runOnClient(mc->mc.gui.setScreen(null));
    }
    private RelayTableScenario(){}
}