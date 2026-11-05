package craftbound.test;

import craftbound.card.BoosterPacks;
import craftbound.card.Cards;
import craftbound.client.card.PackOpeningScreen;
import craftbound.client.card.PackReveal;
import java.lang.reflect.Field;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Fixed samples of the real GUI, in the isolated client-test profile; awards no cards. */
public final class PackRenderingTest implements FabricClientGameTest {
    private static class Sample extends PackOpeningScreen {
        final float sampleTime;
        final Field clock;
        Sample(int selected, float time, boolean jackpot) {
            super(jackpot ? List.of(new BoosterPacks.Pull(Cards.WARDEN,true),new BoosterPacks.Pull(Cards.WARDEN,true),
                    new BoosterPacks.Pull(Cards.WARDEN,true),new BoosterPacks.Pull(Cards.WARDEN,true),new BoosterPacks.Pull(Cards.WARDEN,true))
                    : List.of(new BoosterPacks.Pull(Cards.ZOMBIE,false),new BoosterPacks.Pull(Cards.CREEPER,false),
                    new BoosterPacks.Pull(Cards.ENDERMAN,false),new BoosterPacks.Pull(Cards.WARDEN,false),new BoosterPacks.Pull(Cards.WARDEN,true)),
                    jackpot,List.of(true,true,true,true,true));
            sampleTime=time;
            try {
                clock=PackOpeningScreen.class.getDeclaredField("time"); clock.setAccessible(true);
                Field state=PackOpeningScreen.class.getDeclaredField("reveal");state.setAccessible(true);
                if(selected>=0)((PackReveal)state.get(this)).queue(selected,0);
            } catch(ReflectiveOperationException e) { throw new AssertionError(e); }
        }
        @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float delta) {
            try { clock.setFloat(this,sampleTime); } catch(IllegalAccessException e) { throw new AssertionError(e); }
            super.extractRenderState(g,x,y,delta);
        }
    }
    public void runTest(ClientGameTestContext context) {
        RenderTestSettings.apply(context);
        TestCatalogue.install();
        context.runOnClient(mc->{mc.options.pauseOnLostFocus=false;mc.options.framerateLimit().set(60);});
        sample(context,"pack-unopened",-1,0,false);
        sample(context,"pack-epic-charge",2,0.55F,false);
        sample(context,"pack-epic-reveal",2,1.1F,false);
        sample(context,"pack-legendary-charge",3,0.9F,false);
        sample(context,"pack-legendary-reveal",3,1.65F,false);
        sample(context,"pack-enchanted-legendary",4,1.98F,false);
        sample(context,"pack-jackpot-bounded",0,1.98F,true);
        context.runOnClient(mc->mc.gui.setScreen(null));
    }
    private void sample(ClientGameTestContext context,String name,int selected,float time,boolean jackpot) {
        context.runOnClient(mc->mc.gui.setScreen(new Sample(selected,time,jackpot)));
        context.waitTicks(4);
        context.takeScreenshot(name);
    }
}
