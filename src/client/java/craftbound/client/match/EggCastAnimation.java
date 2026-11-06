package craftbound.client.match;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** A straight throw from the hero. The impact stays at the original target even if the hit removes it. */
final class EggCastAnimation {
    static final int FLIGHT = 175;
    static final int SPLAT = 270;
    private static final ItemStack EGG = new ItemStack(Items.EGG);

    static void fly(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, float t) {
        float dx = x1 - x0, dy = y1 - y0;
        for (int i = 3; i > 0; i--) {
            float tail = Math.max(0, t - .033F * i);
            int x = Math.round(x0 + dx * tail), y = Math.round(y0 + dy * tail);
            g.fill(x - 1, y - 1, x + 2, y + 2, MatchArt.alpha(0xFFFFE8B4, .56F - i * .12F));
        }
        g.pose().pushMatrix();
        g.pose().translate(x0 + dx * t, y0 + dy * t);
        g.pose().rotate((float)Math.atan2(dy, dx) + 1.57F + t * .8F);
        g.pose().scale(1.15F, 1.15F);
        g.item(EGG, -8, -8);
        g.pose().popMatrix();
    }

    static void splat(GuiGraphicsExtractor g, float cx, float cy, float t) {
        float spread = MatchArt.easeOut(Math.min(1, t * 4));
        float fade = Math.min(1, (1 - t) * 2.4F);
        int x = Math.round(cx), y = Math.round(cy);
        int white = MatchArt.alpha(0xFFFFF2CA, fade);
        int yolk = MatchArt.alpha(0xFFFFBE32, fade);
        int w = 2 + Math.round(6 * spread), h = 2 + Math.round(3 * spread);
        // A flattened, irregular egg white around a distinct yolk, with chips of shell flying outward.
        g.fill(x - w, y - h + 2, x + w + 2, y + h - 1, white);
        g.fill(x - w + 2, y - h, x + w - 1, y + h, white);
        g.fill(x - 3, y - 2, x + 3, y + 2, yolk);
        g.fill(x - 2, y - 3, x + 2, y + 3, yolk);
        for (int i = 0; i < 7; i++) {
            double angle = i * Math.PI * 2 / 7 + .27;
            float r = 4 + (9 + i % 3 * 3) * MatchArt.easeOut(t);
            int px = x + (int)Math.round(Math.cos(angle) * r);
            int py = y + (int)Math.round(Math.sin(angle) * r + t * t * 4);
            g.fill(px, py, px + 2, py + (i % 2 + 1), i % 3 == 0 ? yolk : white);
        }
    }

    static void launchSound() { MatchSounds.play("entity.egg.throw", 1.25F, .65F); }
    static void impactSound() { MatchSounds.play("block.slime_block.hit", 1.45F, .55F); }
    private EggCastAnimation() {}
}
