package craftbound.client.card;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import craftbound.card.CardCatalog;
import craftbound.card.CardText;
import craftbound.card.Cards;
import craftbound.card.Keyword;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Vector2f;

/** Status explanations use the printed words as their real, scaled hover targets. */
public final class StatusExplanationChecks {
    private static final Identifier PATROL = Identifier.parse("craftbound:status_test_patrol");
    private static final Identifier RAIDER = Identifier.parse("craftbound:status_test_raider");
    private static final Identifier WITHERER = Identifier.parse("craftbound:status_test_witherer");

    public static void run() {
        var previousCatalogue = CardCatalog.current();
        var previousLanguage = Language.getInstance();
        try {
            Language.inject(english(previousLanguage));
            checkStyledProse();
            CardCatalog.install(fixture());
            checkGeneratedDescriptions();
            checkHoverTargets();
            checkAssociations();
            System.out.println("PASS: Bad Omen/Wither prose, aliases, derived keywords, linked-raider explanations and wrapped/scaled hover targets.");
        } finally {
            CardCatalog.install(previousCatalogue);
            Language.inject(previousLanguage);
            CardRenderer.clearTextLayouts();
        }
    }

    private static void checkStyledProse() {
        for (String prose : List.of("Drop: Give the enemy hero Bad Omen.",
                "After attacking: Wither its target.", "This target is withered.")) {
            Component styled = Cards.styledText(prose);
            require(styled.getString().equals(prose), "Styling changed authored wording: " + prose);
            var clauses = CardText.clauses(styled);
            require(clauses.size() == 1 && clauses.getFirst().getString().equals(prose),
                    "An inline status started an unintended paragraph: " + prose);
        }
        String aliases = "Bad Omen / bad omen / BAD OMEN. Wither / Withered / withered / WITHERED.";
        Component styled = Cards.styledText(aliases);
        require(styled.getString().equals(aliases), "Status aliases must preserve original casing");
        Map<Keyword, List<String>> tagged = new HashMap<>();
        styled.visit((style, words) -> {
            Keyword keyword = Keyword.fromInsertion(style.getInsertion());
            if (keyword != null) {
                require(style.isBold(), "A status word was not bold: " + words);
                tagged.computeIfAbsent(keyword, ignored -> new ArrayList<>()).add(words);
            }
            return Optional.empty();
        }, Style.EMPTY);
        require(tagged.get(Keyword.BAD_OMEN).equals(List.of("Bad Omen", "bad omen", "BAD OMEN")),
                "Bad Omen aliases lost their tooltip tags");
        require(tagged.get(Keyword.WITHER).equals(List.of("Wither", "Withered", "withered", "WITHERED")),
                "Withered aliases must point to the Wither explanation");
        for (String suffix : List.of("Add an Egg to your hand.", "Give the enemy hero Bad Omen.")) {
            var clauses = CardText.clauses(Cards.styledText("Sprint. Drop: " + suffix));
            require(clauses.size() == 2 && clauses.get(0).getString().strip().equals("Sprint.")
                    && clauses.get(1).getString().equals("Drop: " + suffix),
                    "Sprint/Drop paragraph separation regressed");
        }
        require(Keyword.BAD_OMEN.displayName().getString().equals("Bad Omen")
                && Keyword.WITHER.displayName().getString().equals("Wither"),
                "Status dictionary labels are missing");
    }

    private static void checkGeneratedDescriptions() {
        var patrol = Cards.get(PATROL);
        require(patrol.keywords().containsAll(List.of(Keyword.BAD_OMEN, Keyword.DROP)),
                "Bad Omen effect did not derive its status and trigger keywords");
        require(patrol.createdBy(Keyword.BAD_OMEN).equals(List.of(RAIDER)),
                "Bad Omen did not retain its linked raider");
        require(Cards.get(WITHERER).keywords().contains(Keyword.WITHER),
                "Wither effect did not derive its explanation keyword");
        require(CardCatalog.description(CardCatalog.entry(PATROL)).equals("Drop: Give the enemy hero Bad Omen."),
                "Bad Omen was duplicated as a standalone keyword or changed its generated sentence");
        require(CardCatalog.description(CardCatalog.entry(WITHERER)).equals("After attacking: Wither its target."),
                "Wither was duplicated as a standalone keyword or changed its generated sentence");
        var roundTrip = CardCatalog.parse(CardCatalog.current().json());
        require(roundTrip.entries().get(PATROL).definition().createdBy(Keyword.BAD_OMEN).equals(List.of(RAIDER)),
                "Catalogue round trip duplicated or lost the linked mob");
    }

    private static void checkHoverTargets() {
        Font font = new StyledMetrics();
        for (float scale : new float[]{1F, 2.25F, .7F}) for (var id : List.of(PATROL, WITHERER)) {
            var capture = new GlyphRecorder();
            var regions = CardRenderer.drawFace(capture, font, id, false, 37, 29, scale, -1, -1);
            Keyword expected = id.equals(PATROL) ? Keyword.BAD_OMEN : Keyword.WITHER;
            var statusRegions = regions.stream().filter(region -> region.keyword() == expected).toList();
            require(!statusRegions.isEmpty(), "Missing actual card status hover region: " + expected);
            if (expected == Keyword.BAD_OMEN)
                require(statusRegions.stream().map(CardRenderer.HoverRegion::y).distinct().count() >= 2,
                        "Test must exercise a Bad Omen phrase wrapped across separate lines");
            var statusGlyphs = capture.glyphs.stream().filter(glyph -> glyph.keyword() == expected).toList();
            require(!statusGlyphs.isEmpty(), "Renderer stripped status metadata while wrapping");
            for (var glyph : statusGlyphs)
                require(statusRegions.stream().anyMatch(region -> region.contains(glyph.x(), glyph.y())),
                        "Scaled tooltip missed a rendered " + expected + " glyph at " + scale);
            for (var region : statusRegions) {
                String tooltip = region.lines().stream().map(Component::getString).collect(java.util.stream.Collectors.joining("\n"));
                require(!tooltip.contains("keyword.craftbound."), "Status explanation displayed an untranslated key");
                require(tooltip.contains("Cleanse") && tooltip.contains("Does not stack"),
                        "Status explanation omitted removal or stacking rules");
                if (expected == Keyword.BAD_OMEN)
                    require(tooltip.contains("Summons: Omen Test Raider"), "Tooltip did not name the linked raider");
                else require(tooltip.contains("1 damage") && tooltip.contains("start of") && tooltip.contains("Cannot affect heroes"),
                        "Wither tooltip omitted damage timing or hero exclusion");
            }
            var again = CardRenderer.drawFace(new GlyphRecorder(), font, id, false, 37, 29, scale, -1, -1);
            require(regions.equals(again), "Warm text cache changed status hover geometry or explanations");
        }
    }

    private static void checkAssociations() {
        var stew = Identifier.parse("craftbound:status_test_stew");
        var mooshroom = Identifier.parse("craftbound:status_test_mooshroom");
        var random = Identifier.parse("craftbound:status_test_random");
        require(CardInspectScreen.associations(PATROL).equals(List.of(new CardInspectScreen.Association(RAIDER, Keyword.BAD_OMEN))),
                "Bad Omen raider was duplicated under Drop or lacked its status association");
        require(CardInspectScreen.associations(mooshroom).equals(List.of(new CardInspectScreen.Association(stew, null))),
                "End-turn Mooshroom outcome must appear immediately without a keyword hover");
        require(CardInspectScreen.associations(random).equals(List.of(
                new CardInspectScreen.Association(stew, null),
                new CardInspectScreen.Association(RAIDER, null),
                new CardInspectScreen.Association(WITHERER, null))),
                "Random outcome previews must include all outcomes once, in pool order");
        for (String timing : List.of("play", "drop")) {
            var id = Identifier.parse("craftbound:status_test_manual_" + timing);
            require(CardInspectScreen.associations(id).equals(List.of(new CardInspectScreen.Association(RAIDER, null))),
                    "Manual text omitting its trigger must not hide the generated card: " + timing);
        }
        var before = CardCatalog.current();
        try {
            var entries = new java.util.LinkedHashMap<>(before.entries());
            var raider = entries.get(RAIDER);
            entries.put(RAIDER, new CardCatalog.Entry(raider.definition(), raider.name(), raider.text(), raider.autoText(),
                    raider.passive(), raider.passiveAmount(), raider.effects(), raider.auraType(), true,
                    raider.auraHealth(), raider.castFx(), raider.deathFx(), raider.arrivalFx(), raider.auraKind()));
            entries.remove(WITHERER);
            CardCatalog.install(new CardCatalog.State(java.util.Collections.unmodifiableMap(entries), before.json()));
            require(CardInspectScreen.associations(random).equals(List.of(new CardInspectScreen.Association(stew, null))),
                    "Missing or deleted generated outcomes must not produce previews");
            require(CardInspectScreen.associations(WITHERER).isEmpty() && CardInspectScreen.associations(null).isEmpty(),
                    "Missing source cards must have no associations");
        } finally { CardCatalog.install(before); }
    }

    /** Fixed glyph widths with styles preserved across artificial line breaks; no GPU or glyph atlas. */
    private static final class StyledMetrics extends CardFaceChecks.FontMetrics {
        @Override public List<FormattedCharSequence> split(FormattedText text, int width) {
            List<FormattedCharSequence> glyphs = new ArrayList<>();
            text.visit((style, words) -> {
                words.codePoints().forEach(cp -> glyphs.add(FormattedCharSequence.codepoint(cp, style)));
                return Optional.empty();
            }, Style.EMPTY);
            List<FormattedCharSequence> lines = new ArrayList<>();
            int count = Math.max(1, width / 6);
            for (int offset = 0; offset < glyphs.size(); offset += count)
                lines.add(FormattedCharSequence.composite(List.copyOf(glyphs.subList(offset, Math.min(glyphs.size(), offset + count)))));
            return lines;
        }
    }

    private record Glyph(Keyword keyword, float x, float y) {}
    private static final class GlyphRecorder extends CardFaceChecks.Recorder {
        final List<Glyph> glyphs = new ArrayList<>();
        @Override public void text(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
            super.text(font, text, x, y, color, shadow);
            int[] cursor = {0};
            text.accept((index, style, cp) -> {
                int width = font.width(FormattedCharSequence.codepoint(cp, style));
                Keyword keyword = Keyword.fromInsertion(style.getInsertion());
                if (keyword != null) {
                    require(style.isBold(), "A rendered status glyph lost bold styling");
                    var center = pose.transformPosition(x + cursor[0] + width / 2F, y + font.lineHeight / 2F, new Vector2f());
                    glyphs.add(new Glyph(keyword, center.x, center.y));
                }
                cursor[0] += width;
                return true;
            });
        }
    }

    private static CardCatalog.State fixture() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonArray cards = new JsonArray();
        root.add("cards", cards);
        cards.add(mob(RAIDER, "Omen Test Raider"));
        var patrol = mob(PATROL, "Test Patrol");
        var omen = effect("DROP", "BAD_OMEN", "ENEMY_HERO");
        omen.addProperty("card", RAIDER.toString());
        patrol.getAsJsonArray("effects").add(omen);
        cards.add(patrol);
        var witherer = mob(WITHERER, "Test Witherer");
        witherer.getAsJsonArray("effects").add(effect("AFTER_ATTACK", "WITHER", "EVENT"));
        cards.add(witherer);
        var stewId = Identifier.parse("craftbound:status_test_stew");
        var stew = mob(stewId, "Mushroom Stew");
        stew.addProperty("kind", "ITEM"); stew.addProperty("attack", 0); stew.addProperty("health", 0);
        cards.add(stew);
        var mooshroom = mob(Identifier.parse("craftbound:status_test_mooshroom"), "Mooshroom");
        var stewEffect = effect("END_TURN", "GIVE", "SELF");
        stewEffect.addProperty("card", stewId.toString());
        mooshroom.getAsJsonArray("effects").add(stewEffect);
        cards.add(mooshroom);
        var random = mob(Identifier.parse("craftbound:status_test_random"), "Random Outcomes");
        var randomEffect = effect("END_TURN", "GIVE_RANDOM", "SELF");
        JsonArray pool = new JsonArray();
        for (var id : List.of(stewId, RAIDER, stewId, WITHERER)) pool.add(id.toString());
        randomEffect.add("pool", pool);
        random.getAsJsonArray("effects").add(randomEffect);
        cards.add(random);
        for (String timing : List.of("PLAY", "DROP")) {
            var manual = mob(Identifier.parse("craftbound:status_test_manual_" + timing.toLowerCase(java.util.Locale.ROOT)), "Manual Rules");
            manual.addProperty("autoText", false); manual.addProperty("text", "Add a raider to your hand.");
            var give = effect(timing, "GIVE", "SELF"); give.addProperty("card", RAIDER.toString());
            manual.getAsJsonArray("effects").add(give);
            cards.add(manual);
        }
        return CardCatalog.parse(root.toString());
    }

    private static JsonObject mob(Identifier id, String name) {
        JsonObject card = new JsonObject();
        card.addProperty("id", id.toString()); card.addProperty("name", name);
        card.addProperty("kind", "MOB"); card.addProperty("class", "NEUTRAL"); card.addProperty("rarity", "COMMON");
        card.addProperty("cost", 2); card.addProperty("attack", 2); card.addProperty("health", 3);
        card.addProperty("type", ""); card.addProperty("token", false); card.addProperty("art", "minecraft:zombie_spawn_egg");
        card.addProperty("text", ""); card.addProperty("autoText", true);
        card.addProperty("passive", "NONE"); card.addProperty("passiveAmount", 0);
        card.add("keywords", new JsonArray()); card.add("effects", new JsonArray());
        return card;
    }

    private static JsonObject effect(String trigger, String action, String target) {
        JsonObject effect = new JsonObject();
        effect.addProperty("trigger", trigger); effect.addProperty("action", action); effect.addProperty("target", target);
        effect.addProperty("boardKind", "MOB"); effect.addProperty("type", ""); effect.addProperty("card", "");
        effect.addProperty("amount", 1); effect.addProperty("attack", 0); effect.addProperty("health", 0);
        effect.addProperty("excludeSelf", false);
        return effect;
    }

    private static Language english(Language fallback) {
        Map<String, String> translations = new HashMap<>();
        try (var input = StatusExplanationChecks.class.getResourceAsStream("/assets/craftbound/lang/en_us.json")) {
            if (input == null) throw new AssertionError("Missing Craftbound dictionary");
            Language.loadFromJson(input, translations::put);
        } catch (IOException e) { throw new AssertionError("Could not load status dictionary", e); }
        return new Language() {
            public String getOrDefault(String key, String defaultValue) { return translations.getOrDefault(key, fallback.getOrDefault(key, defaultValue)); }
            public boolean has(String key) { return translations.containsKey(key) || fallback.has(key); }
            public boolean isDefaultRightToLeft() { return false; }
            public FormattedCharSequence getVisualOrder(FormattedText text) { return fallback.getVisualOrder(text); }
        };
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private StatusExplanationChecks() {}
}
