package craftbound.client.match;

import com.google.gson.JsonParser;
import craftbound.card.CardCatalog;
import craftbound.client.card.CardCanvas;
import net.minecraft.client.gui.Font;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

/** No GPU required: causal event matching, projectile path, sound IDs and fixed draw budgets. */
public final class EyeOfEnderChecks {
    public static void run() {
        var start = EyeOfEnderAnimation.frame(0, 320, 380, 590, 150);
        var arrived = EyeOfEnderAnimation.frame(EyeOfEnderAnimation.ARRIVE_MS, 320, 380, 590, 150);
        var end = EyeOfEnderAnimation.frame(EyeOfEnderAnimation.DURATION_MS, 320, 380, 590, 150);
        require(start.x() == 320 && start.y() == 380, "Eye must begin at the played card");
        require(Math.abs(arrived.x() - 590) < .001 && Math.abs(arrived.y() - 150) < .001,
                "Eye must arrive at its hand target before scanning/revealing");
        require(end.scale() == 0 && EyeOfEnderAnimation.ARRIVE_MS < EyeOfEnderAnimation.DURATION_MS,
                "Eye must finish scanning and vanish before cards appear");
        var eye = new ItemStack(Items.ENDER_EYE);
        for (float[] path : new float[][]{{320,380,590,150}, {320,28,590,380}, {28,28,28,28}}) {
            for (int ms = 0; ms <= EyeOfEnderAnimation.DURATION_MS; ms += 13) {
                var frame = EyeOfEnderAnimation.frame(ms, path[0], path[1], path[2], path[3]);
                require(Float.isFinite(frame.x()) && Float.isFinite(frame.y()) && frame.y() >= 19
                        && frame.scale() >= 0 && frame.scale() <= 1.61F, "Projectile path/scale escaped bounds");
                var canvas = new Recorder();
                EyeOfEnderAnimation.draw(canvas, eye, ms, path[0], path[1], path[2], path[3]);
                require(canvas.items <= 1 && canvas.fills <= 48, "Eye effect exceeded fixed draw budget");
                require(canvas.pose.equals(new Matrix3x2f()), "Eye left an unbalanced drawing transform");
            }
        }
        for (String sound : new String[]{EyeOfEnderAnimation.LAUNCH_SOUND, EyeOfEnderAnimation.ARRIVE_SOUND,
                EyeOfEnderAnimation.REVEAL_SOUND})
            require(BuiltInRegistries.SOUND_EVENT.containsKey(Identifier.withDefaultNamespace(sound)),
                    "Missing vanilla sound: " + sound);
        checkCausalEvents();
        System.out.println("PASS: Eye throw/scan ordering, fixed rendering budget, balanced transforms, vanilla sounds and causal reveal matching.");
    }

    private static void checkCausalEvents() {
        var original = CardCatalog.current();
        try {
            var root = JsonParser.parseString(original.json()).getAsJsonObject();
            var cards = root.getAsJsonArray("cards");
            var fixture = cards.asList().stream().map(e -> e.getAsJsonObject())
                    .filter(c -> c.get("kind").getAsString().equals("ITEM")).findFirst().orElseThrow().deepCopy();
            var eye = Identifier.parse("craftbound:test_eye");
            fixture.addProperty("id", eye.toString());
            fixture.addProperty("name", "Eye test");
            fixture.addProperty("art", "minecraft:ender_eye");
            cards.add(fixture);
            CardCatalog.install(CardCatalog.parse(root.toString()));
            var effect = event("EFFECT", 0, eye, -1);
            var reveal = event("HAND_REVEAL", 0, null, 1); // Public/redacted event still allows the public flight.
            require(EyeOfEnderAnimation.starts(effect, reveal), "Eye artwork must support renamed authored cards");
            require(!EyeOfEnderAnimation.starts(effect, event("HAND_REVEAL", 1, null, 0)),
                    "Another player's reveal cannot inherit this Eye");
            require(!EyeOfEnderAnimation.starts(effect, event("DAMAGE", 0, null, 1))
                    && !EyeOfEnderAnimation.starts(event("PLAY", 0, eye, 1), reveal)
                    && !EyeOfEnderAnimation.starts(effect, null), "Eye reveal styling must match its actual reveal effect");
            require(!CombatVfx.enderEye(null) && !CombatVfx.enderEye(Identifier.parse("craftbound:missing_eye")),
                    "Removed cards must not crash or animate");
        } finally { CardCatalog.install(original); }
    }

    private static MatchState.Event event(String type, int side, Identifier card, int target) {
        return new MatchState.Event(type, side, 0, card, false, 0, 1, target, true, 0);
    }
    private static class Recorder implements CardCanvas {
        final Matrix3x2fStack pose = new Matrix3x2fStack(8);
        int fills, items;
        public Matrix3x2fStack pose() { return pose; }
        public void fill(int x0,int y0,int x1,int y1,int color) { fills++; }
        public void item(ItemStack item,int x,int y) { items++; }
        public void text(Font font,FormattedCharSequence text,int x,int y,int color,boolean shadow) {}
        public void frameRegion(Identifier texture,int u,int v,int sw,int sh,int x,int y,int w,int h) {}
        public void shimmer(int x,int y,int w,int h,float diagonal,int halfWidth,int alpha) {}
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private EyeOfEnderChecks() {}
}
