package craftbound.game;

import craftbound.board.*;
import craftbound.card.CardClass;
import craftbound.deck.*;
import craftbound.item.ModItems;
import craftbound.network.*;
import craftbound.sleeve.*;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Probe a real first boss clear, retry its settlement, then equip the rewards from the connected client. */
final class TrialsRewardScenario {
    private Match boss;
    private CardClass cls;
    private Identifier back;
    private BoardDesign board;
    private int packs, binderSlot, binderLocation;
    private final String deckId=UUID.randomUUID().toString();

    private static int packs(ServerPlayer p){
        int total=0;
        for(int i=0;i<p.getInventory().getContainerSize();i++){var slot=p.getInventory().getItem(i);if(slot.is(ModItems.BOOSTER_PACK))total+=slot.getCount();}
        return total;
    }
    void before(ServerPlayer p,Match match) {
        int s=match.sideOf(p.getUUID());
        var rival=match.opponent(s);
        if(rival.trialsFoe==null || rival.trialsFoe.tier()!=TrialsOpponents.Tier.BOSS)return;
        boss=match;cls=match.player(s).heroClass;
        var reward=TrialsContent.rewards().stream().filter(r->r.heroClass()==cls).findFirst().orElseThrow();
        back=Identifier.parse(reward.cardBack());board=BoardDesign.fromId(reward.board());packs=packs(p);
        if(TrialsRewards.cleared(p,cls) || BoardCollection.owns(p,board) || SleeveCollection.owns(p,back))
            throw new AssertionError("Test player already owns this first-clear prize");
    }

    void after(ClientGameTestContext ctx,TestSingleplayerContext world) {
        if(boss==null)throw new AssertionError("The expedition did not enter a boss room");
        world.getServer().runOnServer(ms->{
            var player=ms.getPlayerList().getPlayers().getFirst();
            if(!boss.over || boss.winner!=boss.sideOf(player.getUUID()) || !TrialsRewards.cleared(player,cls)
                || !BoardCollection.owns(player,board) || !SleeveCollection.owns(player,back))
                throw new AssertionError("A real first boss victory did not grant permanent class, back and board rewards");
            int awarded=packs(player), expected=TrialsContent.section("config").getAsJsonObject().get("bossPacks").getAsInt();
            if(awarded!=packs+expected)throw new AssertionError("Incorrect boss pack reward");
            TrialsTables.finish(ms,boss);TrialsRewards.afterClear(player,cls);
            if(packs(player)!=awarded || SleeveCollection.unlock(player,back))throw new AssertionError("Boss result or first clear was rewarded twice");
            for(var other:TrialsContent.rewards())if(other.heroClass()!=cls && TrialsRewards.cleared(player,other.heroClass()))
                throw new AssertionError("An unrelated class received a clear");
            var inventory=player.getInventory();binderSlot=-1;
            for(int i=0;i<inventory.getContainerSize();i++)if(inventory.getItem(i).is(ModItems.BINDER)){binderSlot=i;break;}
            if(binderSlot==-1) {
                for(int i=0;i<inventory.getContainerSize();i++)if(inventory.getItem(i).isEmpty()){binderSlot=i;break;}
                if(binderSlot<0)throw new AssertionError("No free slot for a test binder");
                inventory.setItem(binderSlot,new ItemStack(ModItems.BINDER));
            }
            binderLocation=BinderDecks.locationForSlot(binderSlot);
            BinderDecks.handle(player,new BinderDeckPayload(binderLocation,deckId,BinderDeckPayload.CREATE,cls.name(),false));
            player.inventoryMenu.broadcastChanges();
        });
        ctx.waitFor(mc->TrialsRewards.cleared(mc.player,cls) && BoardCollection.owns(mc.player,board) && SleeveCollection.owns(mc.player,back));
        ctx.runOnClient(mc->{
            ClientPlayNetworking.send(new BinderDeckPayload(binderLocation,deckId,BinderDeckPayload.SLEEVE,back.toString(),false));
            ClientPlayNetworking.send(new SelectBoardPayload(board.id()));
        });
        ctx.waitTicks(6);
        world.getServer().runOnServer(ms->{
            var p=ms.getPlayerList().getPlayers().getFirst();
            var equipped=BinderDecks.get(p.getInventory().getItem(binderSlot)).find(deckId);
            if(equipped==null||!back.equals(equipped.sleeve())||BoardCollection.selected(p)!=board)
                throw new AssertionError("Binder did not accept the earned cosmetics after network sync");
        });
    }
}