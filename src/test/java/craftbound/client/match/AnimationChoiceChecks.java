package craftbound.client.match;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import craftbound.card.CardCatalog;
import net.minecraft.resources.Identifier;
import java.util.LinkedHashMap;
import java.util.Map;

/** Manual visuals must survive validation and work independently of source kind/artwork. */
public final class AnimationChoiceChecks {
    public static void run() {
        var original = CardCatalog.current();
        try {
            var root = JsonParser.parseString(original.json()).getAsJsonObject();
            var cards = root.getAsJsonArray("cards");
            Map<Identifier, JsonObject> examples = new LinkedHashMap<>();
            for (String name : new String[]{"zombie", "dispenser", "bone_meal", "iron_sword", "sharpness"}) {
                var source = cards.asList().stream().map(e -> e.getAsJsonObject())
                        .filter(c -> c.get("id").getAsString().equals("craftbound:" + name)).findFirst().orElseThrow();
                var copy = source.deepCopy();
                var id = Identifier.parse("craftbound:animation_test_" + name);
                copy.addProperty("id", id.toString());
                copy.addProperty("art", "minecraft:stone");
                copy.remove("castFx"); copy.remove("arrivalFx"); copy.remove("deathFx");
                cards.add(copy); examples.put(id, copy);
            }
            CardCatalog.install(CardCatalog.parse(root.toString()));
            for (var id : examples.keySet()) {
                require(CombatVfx.castFx(id) == CardCatalog.CastFx.AUTO, "Old saves must default to AUTO");
                require(CombatVfx.arrivalFx(id) == CardCatalog.ArrivalFx.AUTO, "Old arrival must default to AUTO");
                require(CombatVfx.automaticDeath(id), "Old deaths must default to AUTO");
            }
            for (var fx : CardCatalog.CastFx.values()) {
                examples.values().forEach(c -> c.addProperty("castFx", fx.name()));
                CardCatalog.install(CardCatalog.parse(root.toString()));
                for (var id : examples.keySet()) {
                    require(CombatVfx.castFx(id) == fx, "Cast choice lost on " + id);
                    if (fx == CardCatalog.CastFx.AUTO) continue;
                    require(CombatVfx.egg(id) == (fx == CardCatalog.CastFx.EGG), "Egg restricted by kind");
                    require(CombatVfx.potion(id) == (fx == CardCatalog.CastFx.POTION), "Potion restricted by kind");
                    require(CombatVfx.enderEye(id) == (fx == CardCatalog.CastFx.EYE_OF_ENDER), "Eye restricted by kind");
                    require(CombatVfx.arrow(id) == (fx == CardCatalog.CastFx.ARROW), "Arrow restricted by kind");
                    require(CombatVfx.fox(id) == (fx == CardCatalog.CastFx.FOX), "Fox restricted by kind");
                    int duration = MatchScreen.castAnimationDuration(id);
                    require(fx == CardCatalog.CastFx.PLAIN ? duration == 0 : duration > 0,
                            "A selected cast must actually reserve a PLAY beat on " + id);
                    if (fx == CardCatalog.CastFx.EGG)
                        require(duration == EggCastAnimation.FLIGHT && duration < 250,
                                "Egg damage must wait only for the fast flight, not the fading impact");
                    if (fx == CardCatalog.CastFx.EYE_OF_ENDER)
                        require(duration >= EyeOfEnderAnimation.DURATION_MS,
                                "Results or choices cannot overtake the Eye on " + id);
                }
            }
            for (var fx : CardCatalog.DeathFx.values()) {
                examples.values().forEach(c -> c.addProperty("deathFx", fx.name()));
                CardCatalog.install(CardCatalog.parse(root.toString()));
                for (var id : examples.keySet()) {
                    require(CombatVfx.automaticDeath(id) == (fx == CardCatalog.DeathFx.AUTO), "Manual death lost");
                    if (fx == CardCatalog.DeathFx.AUTO) continue;
                    require(CombatVfx.cracks(id) == (fx == CardCatalog.DeathFx.CRACKS), "Cracks restricted by kind");
                    require(CombatVfx.splits(id) == (fx == CardCatalog.DeathFx.SPLIT), "Split restricted by kind");
                    require(CombatVfx.explodes(id) == (fx == CardCatalog.DeathFx.EXPLODE), "Explosion restricted by kind");
                }
            }
            for (var fx : CardCatalog.ArrivalFx.values()) {
                examples.values().forEach(c -> c.addProperty("arrivalFx", fx.name()));
                CardCatalog.install(CardCatalog.parse(root.toString()));
                for (var id : examples.keySet()) require(CombatVfx.arrivalFx(id) == fx, "Arrival restricted by kind");
            }
            var eye = Identifier.parse("craftbound:animation_test_bone_meal");
            examples.get(eye).addProperty("art", "minecraft:ender_eye");
            examples.get(eye).addProperty("castFx", "AUTO");
            CardCatalog.install(CardCatalog.parse(root.toString()));
            require(CombatVfx.enderEye(eye), "Automatic Eye must not depend on Reveal Hand");
            require(MatchScreen.castAnimationDuration(eye) == EyeOfEnderAnimation.DURATION_MS,
                    "Changing the Eye effect must preserve its whole PLAY animation");
            examples.get(eye).addProperty("castFx", "PLAIN");
            CardCatalog.install(CardCatalog.parse(root.toString()));
            require(!CombatVfx.enderEye(eye), "Manual standard play must suppress inferred Eye");
            require(!CombatVfx.egg(null) && !CombatVfx.potion(null) && !CombatVfx.fox(null)
                    && !CombatVfx.enderEye(null) && !CombatVfx.cracks(null) && !CombatVfx.splits(null)
                    && !CombatVfx.explodes(null), "Missing cards cannot crash animation inference");
        } finally { CardCatalog.install(original); }
        System.out.println("PASS: unrestricted manual animations, default save compatibility and explicit overrides across all card kinds.");
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private AnimationChoiceChecks() {}
}
