package craftbound.game;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import craftbound.binder.BinderContents;
import craftbound.block.ModBlocks;
import craftbound.card.*;
import craftbound.client.match.*;
import craftbound.component.ModComponents;
import craftbound.deck.*;
import craftbound.item.BinderItem;
import craftbound.network.CommanderJoinPayload;
import craftbound.sleeve.Sleeves;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** One actual network client and three fixture players ready separate owned binders in the same world. */
public final class CommanderTableScenario {
    static final String DECK="c72d002c-4268-4694-97aa-128c4ea8ee10";
    static Identifier equip(ServerPlayer p) { return equip(p, craftbound.deck.DeckFormat.COMMANDER); }
    static Identifier equip(ServerPlayer p, craftbound.deck.DeckFormat format) {
        Map<Identifier,Integer> singles=new LinkedHashMap<>();
        Cards.collectible().stream().filter(id->DeckRules.allowed(id,CardClass.FIGHTER)).limit(format.size()).forEach(id->singles.put(id,1));
        var leader=singles.keySet().stream().filter(CommanderMatch::eligible).findFirst().orElseThrow();
        var contents=new BinderContents(singles,Map.of(),Set.of(),Set.of());
        var binder=BinderItem.creativeBinder();
        binder.set(ModComponents.SAVED_DECKS,new SavedDecks(List.of(new SavedDecks.Entry(DECK,"Raid party",new DeckContents(CardClass.FIGHTER,contents,true,format,format==craftbound.deck.DeckFormat.COMMANDER?Optional.of(leader):Optional.empty()),Sleeves.CLASSIC))));
        p.getInventory().setItem(0,binder);
        return leader;
    }
    static void click(Screen s,int x,int y){
        var e=new MouseButtonEvent(x,y,new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0));s.mouseClicked(e,false);s.mouseReleased(e);
    }
    static void clickButton(Screen screen,String label) {
        for(var child:screen.children())if(child instanceof net.minecraft.client.gui.components.Button b
                && b.getMessage().getString().equals(label)) {
            click(screen,b.getX()+b.getWidth()/2,b.getY()+b.getHeight()/2);return;
        }
        throw new AssertionError("Missing table button: "+label);
    }
    static void place(net.minecraft.server.level.ServerLevel level,BlockPos pos,net.minecraft.world.level.block.Block block) {
        MultiplayerFurnitureScenario.prepare(level,pos);
        var state=block.defaultBlockState();level.setBlockAndUpdate(pos,state);
        block.setPlacedBy(level,pos,state,null,new net.minecraft.world.item.ItemStack(block));
    }
    public static void check(ClientGameTestContext ctx,TestSingleplayerContext world) {
        var targetSeat=new AtomicReference<Integer>();var targetUnit=new AtomicReference<Integer>();
        var loc=new AtomicReference<BlockPos>();var bots=new ArrayList<UUID>();var server=world.getServer();
        server.runOnServer(ms->{
            var p=ms.getPlayerList().getPlayers().getFirst();var l=ms.overworld();BlockPos pos=p.blockPosition().offset(2,0,0);loc.set(pos);
            place(l,pos,ModBlocks.COMMANDER_TABLE);equip(p);p.inventoryMenu.broadcastChanges();TableLobby.open(p,pos);
        });
        ctx.waitTicks(8);ctx.runOnClient(mc->mc.gui.toastManager().clear());ctx.waitTick();ctx.takeScreenshot("craftbound-table-menu");
        ctx.runOnClient(mc->{
            Screen sc=mc.gui.screen();if(!(sc instanceof TableScreen))throw new AssertionError("Table lobby did not open");
            if(!((TableScreen)sc).commanderTable())throw new AssertionError("Commander table opened the wrong mode");
            clickButton(sc,"Ready");
        });
        ctx.waitTicks(3);ctx.takeScreenshot("craftbound-commander-pick");
        ctx.runOnClient(mc->clickButton(mc.gui.screen(),"Ready for Commander"));
        ctx.waitTicks(6);
        ctx.runOnClient(mc->{if(!(mc.gui.screen() instanceof CommanderScreen))throw new AssertionError("Real client failed to ready");});
        server.runOnServer(ms->{
            var l=ms.overworld();var pos=loc.get();String[] names={"Redstone","Birch","Copper"};
            for(String name:names){
                var id=UUID.randomUUID();bots.add(id);var p=FakePlayer.get(l,new GameProfile(id,name));
                ms.getPlayerList().getPlayersByUUID().put(id,p);p.setPos(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5);
                var leader=equip(p);CommanderTables.join(p,new CommanderJoinPayload(pos,0,DECK,leader.toString()));
            }
            var m=CommanderTables.matchAt(l,pos);if(m==null)throw new AssertionError("Four distinct ready seats did not start the table");
            var real=ms.getPlayerList().getPlayers().getFirst();int me=m.sideOf(real.getUUID());
            while(m.current!=me)m.endTurn(m.current);
            m.player(me).xp=m.player(me).maxXp=7;
            for(int seat=0;seat<4;seat++){
                m.summon(seat,new CardRef(Cards.ZOMBIE,false));
                m.summon(seat,new CardRef(Cards.COBBLESTONE_WALL,false));
            }
            m.player(me).hand.add(new CardRef(Cards.ARROW,false));
            CommanderTables.open(real,pos);
        });
        ctx.waitTicks(5);ctx.takeScreenshot("craftbound-commander-table");
        ctx.runOnClient(mc->{
            var sc=(MultiplayerBoardScreen)mc.gui.screen();int me=sc.snapshot().get("you").getAsInt();
            var own=sc.snapshot().getAsJsonArray("players").get(me).getAsJsonObject();
            int arrow=own.getAsJsonArray("hand").size()-1;
            MultiplayerBoardScenario.click(sc,sc.handCardBounds(arrow),InputConstants.MOUSE_BUTTON_LEFT);
            String selected=sc.selectedMove();
            if(!selected.equals("play"+arrow))throw new AssertionError("Targeted card not selected");
            int next=(sc.focusedRival()+1)%4;if(next==me)next=(next+1)%4;
            MultiplayerBoardScenario.click(sc,sc.rivalTabBounds(next),InputConstants.MOUSE_BUTTON_LEFT);
            if(sc.focusedRival()!=next || !selected.equals(sc.selectedMove()))throw new AssertionError("Rival tab cancelled or committed selected card");
            var target=sc.snapshot().getAsJsonArray("players").get(next).getAsJsonObject().getAsJsonArray("board").get(0).getAsJsonObject();
            targetSeat.set(next);targetUnit.set(target.get("mid").getAsInt());
        });
        ctx.waitTicks(2);
        ctx.runOnClient(mc->{var sc=(MultiplayerBoardScreen)mc.gui.screen();
            MultiplayerBoardScenario.click(sc,sc.targetBounds(targetSeat.get(),targetUnit.get()),InputConstants.MOUSE_BUTTON_LEFT);
        });
        ctx.waitTicks(4);
        server.runOnServer(ms->{
            var m=CommanderTables.matchAt(ms.overworld(),loc.get());
            var target=m.player(targetSeat.get()).minion(targetUnit.get());
            if(target!=null && target.health>=target.maxHealth)throw new AssertionError("Selected rival did not take damage");
        });
        ctx.runOnClient(mc->{var sc=(MultiplayerBoardScreen)mc.gui.screen();
            MultiplayerBoardScenario.click(sc,sc.commandBounds(sc.snapshot().get("you").getAsInt()),InputConstants.MOUSE_BUTTON_LEFT);});
        ctx.waitTicks(6);
        server.runOnServer(ms->{
            var l=ms.overworld();var pos=loc.get();var m=CommanderTables.matchAt(l,pos);
            var real=ms.getPlayerList().getPlayers().getFirst();int side=m.sideOf(real.getUUID());
            if(m.casts[side]!=1 || m.player(side).minion(m.unit[side])==null)throw new AssertionError("Commander cast did not travel from GUI to authoritative fourth-seat match");
            l.removeBlock(pos,false);
            for(UUID id:bots)ms.getPlayerList().getPlayersByUUID().remove(id);
        });
        ctx.waitTicks(21);ctx.runOnClient(mc->mc.gui.setScreen(null));
    }
    private CommanderTableScenario(){}
}
