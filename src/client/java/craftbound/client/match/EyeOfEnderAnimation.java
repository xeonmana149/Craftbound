package craftbound.client.match;

import craftbound.client.card.CardCanvas;
import net.minecraft.world.item.ItemStack;

/** A bounded UI projectile: fly to the hand, hover and scan, then yield to the hand result or choice. */
final class EyeOfEnderAnimation {
    static final int ARRIVE_MS = 600;
    static final int DURATION_MS = 1040;
    static final String LAUNCH_SOUND = "entity.ender_eye.launch";
    static final String ARRIVE_SOUND = "entity.ender_eye.death";
    static final String REVEAL_SOUND = "block.amethyst_block.chime";

    record Frame(float x, float y, float scale, float rotation, float scan, float fade) {}

    static boolean starts(MatchState.Event effect, MatchState.Event next) {
        return effect != null && next != null && effect.type().equals("EFFECT")
                && CombatVfx.enderEye(effect.card()) && next.type().equals("HAND_REVEAL")
                && next.side() == effect.side() && next.targetSide() >= 0 && next.targetSide() < 2;
    }

    static Frame frame(float age, float sx, float sy, float tx, float ty) {
        float travel = MatchArt.clamp(age / ARRIVE_MS);
        float smooth = travel * travel * (3 - 2 * travel);
        float arc = Math.min(58, Math.max(0, Math.min(sy, ty) - 24));
        float scan = MatchArt.clamp((age - ARRIVE_MS) / (DURATION_MS - ARRIVE_MS));
        float fade = 1 - MatchArt.clamp((age - DURATION_MS + 180) / 180F);
        float x = sx + (tx - sx) * smooth;
        float y = sy + (ty - sy) * smooth - arc * (float)Math.sin(travel * Math.PI);
        y -= 5 * (float)Math.sin(scan * Math.PI);
        float grow = MatchArt.clamp(age / 95F);
        float scale = (1.35F + .25F * (float)Math.sin(travel * Math.PI)) * grow * fade;
        return new Frame(x, y, scale, .28F * (float)Math.sin(travel * Math.PI * 2), scan, fade);
    }

    static void draw(CardCanvas g, ItemStack eye, float age, float sx, float sy, float tx, float ty) {
        Frame f = frame(age, sx, sy, tx, ty);
        if (age < ARRIVE_MS) {
            // Only twelve trail pixels; no textures, particle entities or unbounded history.
            for (int i = 12; i > 0; i--) {
                float previous = age - i * 15;
                if (previous < 0) continue;
                Frame p = frame(previous, sx, sy, tx, ty);
                int size = i < 5 ? 2 : 1;
                int color = MatchArt.alpha(i % 3 == 0 ? 0xFFC48AFF : 0xFF65DFC0, (1 - i / 14F) * .85F);
                int x = Math.round(p.x()), y = Math.round(p.y());
                g.fill(x-size, y-size, x+size, y+size, color);
            }
        } else if (age < DURATION_MS) {
            for (int ring = 0; ring < 2; ring++) {
                float phase = MatchArt.clamp(f.scan() * 1.4F - ring * .24F);
                if (phase <= 0 || phase >= 1) continue;
                float radius = 9 + phase * 27;
                int color = MatchArt.alpha(ring == 0 ? 0xFF65DFC0 : 0xFFC48AFF, (1 - phase) * f.fade());
                for (int i = 0; i < 24; i++) {
                    double angle = i * Math.PI * 2 / 24 + phase * .6;
                    int x = Math.round(tx + (float)Math.cos(angle) * radius);
                    int y = Math.round(ty + (float)Math.sin(angle) * radius * .65F);
                    g.fill(x-1, y-1, x+2, y+2, color);
                }
            }
        }
        if (f.scale() <= 0) return;
        g.pose().pushMatrix();
        g.pose().translate(f.x(), f.y()).rotate(f.rotation()).scale(f.scale());
        g.item(eye, -8, -8);
        g.pose().popMatrix();
    }

    private EyeOfEnderAnimation() {}
}
