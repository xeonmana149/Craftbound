package craftbound.test;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import craftbound.block.*;
import craftbound.card.*;
import craftbound.game.*;
import craftbound.sleeve.Sleeves;
import java.util.*;

public class TableRenderingTest implements FabricClientGameTest {
 public void runTest(ClientGameTestContext context){
        RenderTestSettings.apply(context);
  try(var world=context.worldBuilder().create()){
   CardCatalog.install(CardCatalog.builtin());
   context.runOnClient(mc->{
    mc.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,craftbound.item.BinderItem.creativeBinder());
    mc.gui.setScreen(new craftbound.client.deck.DeckCaseScreen(net.minecraft.world.InteractionHand.MAIN_HAND));
   });
   context.waitTicks(5);context.takeScreenshot("craftbound-empty-foundation");
   context.runOnClient(mc->{
    var sc=(craftbound.client.deck.DeckCaseScreen)mc.gui.screen();
    try {
     var field=sc.getClass().getDeclaredField("filtersButton");field.setAccessible(true);
     int[] bounds=(int[])field.get(sc);
     if(bounds!=null){
      var click=new net.minecraft.client.input.MouseButtonEvent(bounds[0]+4,bounds[1]+4,
       new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0));
      sc.mouseClicked(click,false);
      var expanded=sc.getClass().getDeclaredField("filtersExpanded");expanded.setAccessible(true);
      if(!expanded.getBoolean(sc))throw new AssertionError("Compact binder filter drawer did not open");
      sc.mouseClicked(click,false);
     }
    }catch(ReflectiveOperationException err){throw new AssertionError(err);}
   });
   RulebookNavigationTest.check(context);
   context.runOnClient(mc->mc.gui.setScreen(null));
   TestCatalogue.install();
   craftbound.game.MultiplayerFurnitureScenario.check(context,world);
   craftbound.game.LayoutPreviewScenario.check(context,world);
   craftbound.client.card.MobFrameScenario.check(context);
   craftbound.game.TableBinderScenario.check(context,world);
   craftbound.game.CommanderTableScenario.check(context,world);
   craftbound.game.RelayTableScenario.check(context,world);
   craftbound.game.TrialsTableScenario.check(context,world);
   var scene=new java.util.concurrent.atomic.AtomicReference<Match>();
   world.getServer().runCommand("gamemode spectator @p");
   world.getServer().runCommand("tp @p 0.5 -59.5 0.5 180 90");
   world.getServer().runCommand("time set noon");
   world.getServer().runOnServer(server->{
    var level=server.overworld();var pos=new BlockPos(0,-60,0);
    level.setBlockAndUpdate(pos,ModBlocks.CARD_TABLE.defaultBlockState());
    var deck=Collections.nCopies(30,new CardRef(Cards.ZOMBIE,false));
    var match=new Match(new MatchPlayer("Fighter",UUID.randomUUID(),CardClass.FIGHTER,Sleeves.CLASSIC,deck),
     new MatchPlayer("Farmer",UUID.randomUUID(),CardClass.FARMER,Sleeves.CLASSIC,deck),net.minecraft.util.RandomSource.create(3));
    match.phase=Match.Phase.PLAYING;match.turn=4;match.turnTicksRemaining=180;
    var ids=List.of(Cards.ZOMBIE,net.minecraft.resources.Identifier.parse("craftbound:pillager"),Cards.SHULKER);
    for(int side=0;side<2;side++)for(int i=0;i<3;i++){
     var mob=new Minion(1+side*3+i,new CardRef(ids.get(i),i==1));
     if(i==0)mob.frozen=true;if(i==1)mob.totem=true;
     match.player(side).board.add(mob);
    }
    ((CardTableBlockEntity)level.getBlockEntity(pos)).board(MatchView.publicBoard(match,false),180);
    scene.set(match);
   });
   context.runOnClient(mc->{mc.options.pauseOnLostFocus=false;});
   context.waitTicks(80);
   world.getConnection().waitForChunksRender();
   context.takeScreenshot(net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions.of("craftbound-tabletop").withSize(1280,720));
   context.waitTicks(40);
   context.takeScreenshot(net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions.of("craftbound-tabletop-cached").withSize(1280,720));
   world.getServer().runCommand("tp @p 0.5 -59.5 0.5 0 90");
   context.waitTicks(5);
   context.takeScreenshot(net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions.of("craftbound-tabletop-opposite").withSize(1280,720));
   context.runOnClient(mc->mc.gui.setScreen(new craftbound.client.card.SleevePreviewScreen(Sleeves.CLASSIC,null)));
   context.waitTicks(10);
   context.takeScreenshot("craftbound-back-inspect");
   context.runOnClient(mc->mc.gui.setScreen(new craftbound.client.match.MatchScreen(MatchView.build(scene.get(),0,0))));
   context.waitTicks(60);
   context.takeScreenshot("craftbound-match");
   context.runOnClient(mc->{
    mc.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(craftbound.item.ModItems.BINDER));
    var binder=new craftbound.client.deck.DeckCaseScreen(net.minecraft.world.InteractionHand.MAIN_HAND);
    mc.gui.setScreen(binder);
    try{
     var tab=binder.getClass().getDeclaredField("tab");tab.setAccessible(true);
     for(Object value:tab.getType().getEnumConstants())if(value.toString().equals("BACKS"))tab.set(binder,value);
    }catch(ReflectiveOperationException e){throw new AssertionError(e);}
   });
   context.waitTicks(4);context.takeScreenshot("craftbound-binder-backs");
   context.runOnClient(mc->{
    var binder=mc.gui.screen();
    try{
     var tab=binder.getClass().getDeclaredField("tab");tab.setAccessible(true);
     for(Object value:tab.getType().getEnumConstants())if(value.toString().equals("BOARDS"))tab.set(binder,value);
    }catch(ReflectiveOperationException e){throw new AssertionError(e);}
   });
   context.waitTicks(4);context.takeScreenshot("craftbound-binder-boards");
   context.runOnClient(mc->{
    mc.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,craftbound.item.BinderItem.creativeBinder());
    mc.gui.setScreen(new craftbound.client.deck.DeckCaseScreen(net.minecraft.world.InteractionHand.MAIN_HAND));
   });
   context.waitTicks(4);context.takeScreenshot("craftbound-collection-filters");
   context.runOnClient(mc->{
    try{var field=mc.gui.screen().getClass().getDeclaredField("openFilter");field.setAccessible(true);field.setInt(mc.gui.screen(),3);}
    catch(ReflectiveOperationException ex){throw new AssertionError(ex);}
   });
   context.waitTicks(3);context.takeScreenshot("craftbound-card-types");
   context.runOnClient(mc->{
    try{var field=mc.gui.screen().getClass().getDeclaredField("openFilter");field.setAccessible(true);field.setInt(mc.gui.screen(),1);}
    catch(ReflectiveOperationException ex){throw new AssertionError(ex);}
   });
   context.waitTicks(3);context.takeScreenshot("craftbound-keywords");
   context.runOnClient(mc->{
    mc.options.guiScale().set(1);mc.resizeGui();
    mc.gui.setScreen(new craftbound.client.deck.DeckCaseScreen(net.minecraft.world.InteractionHand.MAIN_HAND));
   });
   context.waitTicks(3);context.takeScreenshot("craftbound-collection-filters-wide");
   context.runOnClient(mc->{
    try{var field=mc.gui.screen().getClass().getDeclaredField("openFilter");field.setAccessible(true);field.setInt(mc.gui.screen(),3);}
    catch(ReflectiveOperationException ex){throw new AssertionError(ex);}
   });
   context.waitTicks(3);context.takeScreenshot("craftbound-card-types-wide");
   context.runOnClient(mc->{mc.options.guiScale().set(2);mc.resizeGui();});
   context.runOnClient(mc->mc.gui.setScreen(null));
  }
 }
}
