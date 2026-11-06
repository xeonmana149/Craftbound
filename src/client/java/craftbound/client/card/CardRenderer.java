package craftbound.client.card;

import craftbound.card.CardClass;
import craftbound.card.CardDefinition;
import craftbound.game.TrialTraining;
import craftbound.card.CardKind;
import craftbound.card.CardRarity;
import craftbound.card.CardText;
import craftbound.card.Cards;
import craftbound.card.Keyword;
import craftbound.component.ModComponents;
import craftbound.sleeve.Sleeves;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// Draws a full-size card. The inspect screen uses it now; binders, the deck builder and the board can reuse it later.
public class CardRenderer {
    public static final int WIDTH = 120;
    public static final int HEIGHT = 168;
    public static final int ART_WIDTH = 52, ART_HEIGHT = 65;

    // Colours are 0xAARRGGBB. The FF at the front means fully opaque.
    private static final int FRAME = 0xFF7A4434;
    private static final int FRAME_LIGHT = 0xFF9A5A48;
    private static final int FRAME_DARK = 0xFF3E231D;
    private static final int SKY = 0xFF5BC8E0;
    private static final int GRASS = 0xFF6ABF33;
    private static final int GRASS_DARK = 0xFF4E9426;
    private static final int ITEM_BACKDROP = 0xFF6A7FC0;
    private static final int PARCHMENT = 0xFFF5C98A;
    private static final int PARCHMENT_DARK = 0xFFC9945A;
    private static final int TEXT_DARK = 0xFF3E231D;
    private static final int EMERALD = 0xFF17A84F;
    private static final int ATTACK = 0xFFE0A21B;
    private static final int HEALTH = 0xFFC8322B;
    private static final int USES = 0xFF7E858C;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int UPGRADE_STAT = 0xFF80FF74;
    private static final int UPGRADE_TEXT = 0x256B2D;

    // An area of the card that shows a tooltip when the mouse is over it. Coordinates are on screen.
    // `keyword` is set when the area is a keyword in the card text, otherwise it's null.
    public record HoverRegion(int x, int y, int width, int height, List<Component> lines, Keyword keyword) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    // Draws the card with its top-left corner at (x, y), scaled up by `scale`, and returns its hover areas.
    public static List<HoverRegion> draw(GuiGraphicsExtractor g, Font font, ItemStack stack, int x, int y, float scale) {
        return draw(new CardCanvas.Gui(g), font, stack, x, y, scale, -1, -1);
    }

    public static List<HoverRegion> drawLiveTool(GuiGraphicsExtractor g, Font font, ItemStack stack,
                                                int x, int y, float scale, int attack, int uses) {
        return draw(new CardCanvas.Gui(g), font, stack, x, y, scale, attack, uses);
    }

    static List<HoverRegion> draw(CardCanvas g, Font font, ItemStack stack,
                                           int x, int y, float scale, int liveAttack, int liveUses) {
        return drawFace(g,font,stack.get(ModComponents.CARD_ID),stack.has(ModComponents.ENCHANTED),
                x,y,scale,liveAttack,liveUses);
    }

    public static List<HoverRegion> drawFace(CardCanvas g, Font font, Identifier id, boolean enchanted,
                                    int x, int y, float scale, int liveAttack, int liveUses) {
        return drawFace(g, font, id, enchanted, x, y, scale, liveAttack, liveUses, TrialTraining.NONE);
    }

    public static List<HoverRegion> drawTrial(GuiGraphicsExtractor graphics, Font font, Identifier id, boolean enchanted,
                                               TrialTraining training, int x, int y, float scale) {
        return drawTrial(new CardCanvas.Gui(graphics), font, id, enchanted, training, x, y, scale);
    }

    static List<HoverRegion> drawTrial(CardCanvas canvas, Font font, Identifier id, boolean enchanted,
                                      TrialTraining training, int x, int y, float scale) {
        return drawFace(canvas, font, id, enchanted, x, y, scale, -1, -1, training, 0);
    }

    /** A copy in a match hand: Trials training plus Brewing Stand upgrades (craftbound.card.Brew bits). */
    public static List<HoverRegion> drawTrial(GuiGraphicsExtractor graphics, Font font, Identifier id, boolean enchanted,
                                               TrialTraining training, int brew, int x, int y, float scale) {
        return drawFace(new CardCanvas.Gui(graphics), font, id, enchanted, x, y, scale, -1, -1, training, brew);
    }

    private static List<HoverRegion> drawFace(CardCanvas g, Font font, Identifier id, boolean enchanted,
                                              int x, int y, float scale, int liveAttack, int liveUses, TrialTraining training) {
        return drawFace(g, font, id, enchanted, x, y, scale, liveAttack, liveUses, training, 0);
    }

    private static List<HoverRegion> drawFace(CardCanvas g, Font font, Identifier id, boolean enchanted,
                                              int x, int y, float scale, int liveAttack, int liveUses, TrialTraining training, int brew) {
        List<HoverRegion> regions = new ArrayList<>();
        CardDefinition card = id == null ? null : Cards.get(id);
        if (card == null) {
            return regions;
        }
        int printedAttack = training.attack(card), printedHealth = training.health(card);
        boolean liveTool = card.isTool() && liveAttack >= 0 && liveUses >= 0;
        boolean upgradedAttack = !liveTool && printedAttack > card.attack();
        boolean upgradedHealth = !liveTool && printedHealth > card.health();

        // Everything below is drawn as if the card's top-left were (0, 0) at normal size.
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);

        boolean block = card.keywords().contains(Keyword.BLOCK);
        CardFrames.Frame frame = CardFrames.forCard(card);
        boolean painted = frame != null;
        if (painted) {
            int backdrop = card.cardClass() == CardClass.NEUTRAL ? 0xFF675644 : card.cardClass().darkColor();
            g.fill(12, 13, 108, 71, backdrop);
            frame.draw(g);
            drawArt(g, id, card, 60, 42);
        } else {
            drawDefaultFrameAndArt(g, id, card, block);
        }

        // Rarity gem between the art and the rules. Tokens have none.
        int gemX = WIDTH / 2;
        int gemY = painted ? 99 : 91;
        if (!card.token()) {
            if (!painted) {
                g.fill(gemX - 4, gemY - 4, gemX + 4, gemY + 4, FRAME_DARK);
                g.fill(gemX - 3, gemY - 3, gemX + 3, gemY + 3, 0xFF000000 | card.rarity().rgb());
                g.fill(gemX - 3, gemY - 3, gemX - 1, gemY - 1, 0x80FFFFFF);
            }
            regions.add(region(x, y, scale, gemX - 5, gemY - 5, 10, 10, List.of(
                    card.rarity().displayName().copy().withStyle(card.rarity().color()),
                    Component.translatable("card_ui.craftbound.rarity.desc").withStyle(ChatFormatting.GRAY))));
        }

        // All names use the same cream lettering; rarity is the gem and legendary art frame.
        int ribbonLeft = 4;
        int ribbonTop = painted ? 80 : 4;
        int ribbonWidth = WIDTH - 4 - ribbonLeft;
        if (!painted) g.fill(ribbonLeft, ribbonTop, ribbonLeft + ribbonWidth, ribbonTop + 14, FRAME_DARK);
        // Use the printed name, independent of the cosmetic inventory-item prefix.
        fitText(g, font, Cards.name(id), ribbonLeft + ribbonWidth / 2, ribbonTop + 3, painted ? WIDTH - 24 : WIDTH - 44, 0xFFFFF1D8, true);
        List<Component> nameLines = new ArrayList<>();
        nameLines.add(Cards.name(id));
        if (card.token()) {
            nameLines.add(Component.translatable("card_ui.craftbound.token").withStyle(ChatFormatting.GOLD));
            nameLines.add(Component.translatable("card_ui.craftbound.token.desc").withStyle(ChatFormatting.GRAY));
        } else {
            nameLines.add(card.cardClass().displayName().copy().withStyle(ChatFormatting.GOLD));
            nameLines.add(card.cardClass().description().copy().withStyle(ChatFormatting.GRAY));
        }
        if (enchanted) {
            nameLines.add(Component.translatable("card_ui.craftbound.enchanted.desc").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        regions.add(region(x, y, scale, painted ? 4 : 22, ribbonTop, painted ? WIDTH - 8 : WIDTH - 26, 14, nameLines));

        // Text box: one or two standalone keywords are centered; longer rules stay left aligned.
        // Each keyword starts its own line, and the text shrinks
        // until it fits. No drop shadow, so it stays sharp on the parchment.
        int boxTop = painted ? 104 : 96;
        int boxBottom = painted ? 140 : 147;
        if (!painted) {
            g.fill(8, boxTop, WIDTH - 8, boxBottom, PARCHMENT);
            g.fill(WIDTH - 10, boxTop, WIDTH - 8, boxBottom, PARCHMENT_DARK);
            g.fill(8, boxBottom - 2, WIDTH - 8, boxBottom, PARCHMENT_DARK);
        }
        var layout = textLayout(font, id, card, painted, liveTool ? liveAttack : -1, liveTool ? liveUses : -1, training, brew);
        int number = layout.number();
        float textLeft = 12, textScale = layout.scale(), textTop = layout.top();
        g.pose().pushMatrix();
        g.pose().translate(textLeft, textTop);
        g.pose().scale(textScale, textScale);
        for (var line : layout.lines()) {
            g.text(font, line.text(), line.x(), line.y(), TEXT_DARK, false);
            for (var span : line.keywords()) {
                float left = textLeft + line.x() * textScale + span.start() * textScale;
                float top = textTop + line.y() * textScale;
                regions.add(new HoverRegion(x + Math.round(left * scale), y + Math.round((top - 1) * scale),
                        Math.round(span.width() * textScale * scale), Math.round(font.lineHeight * textScale * scale),
                        span.tooltip(), span.keyword()));
            }
        }
        g.pose().popMatrix();

        // Collector number, small, in the bottom-right corner of the text box: its pocket in the binder.
        if (number > 0) {
            String numberText = "#" + number;
            float numberScale = 0.6F;
            g.pose().pushMatrix();
            g.pose().translate(WIDTH - 11 - font.width(numberText) * numberScale, boxBottom - 3 - font.lineHeight * numberScale);
            g.pose().scale(numberScale, numberScale);
            g.text(font, numberText, 0, 0, 0xFF8A6A48, false);
            g.pose().popMatrix();
            regions.add(region(x, y, scale, WIDTH - 24, boxBottom - 9, 14, 7, List.of(
                    Component.translatable("card_ui.craftbound.number", number, layout.collectorTotal()).withStyle(ChatFormatting.GOLD),
                    Component.translatable("card_ui.craftbound.number.desc").withStyle(ChatFormatting.GRAY))));
        }

        // Cost (top-left), attack (bottom-left) and health (bottom-right). Items have no attack or health.
        if (painted) g.centeredText(font, String.valueOf(card.cost()), 11, 8, WHITE);
        else drawStat(g, font, 2, 2, card.cost(), EMERALD);
        regions.add(region(x, y, scale, 2, 2, 18, 18, List.of(
                Component.translatable("card_ui.craftbound.cost", card.cost()).withStyle(ChatFormatting.GREEN),
                Component.translatable("card_ui.craftbound.cost.desc", card.cost()).withStyle(ChatFormatting.GRAY))));
        if (card.isMob()) {
            if (!block) {
                drawPrintedStat(g, font, painted, 4, HEIGHT - 20, 19, 150, printedAttack, ATTACK, upgradedAttack);
                regions.add(region(x, y, scale, painted ? 9 : 4, HEIGHT - 20, 18, 18, List.of(
                        Component.translatable("card_ui.craftbound.attack", printedAttack).withStyle(upgradedAttack ? ChatFormatting.GREEN : ChatFormatting.GOLD),
                        Component.translatable("card_ui.craftbound.attack.desc").withStyle(ChatFormatting.GRAY))));
            }
            drawPrintedStat(g, font, painted, WIDTH - 22, HEIGHT - 20, 101, 149, printedHealth, HEALTH, upgradedHealth);
            regions.add(region(x, y, scale, painted ? 91 : WIDTH - 22, HEIGHT - 20, 18, 18, List.of(
                    Component.translatable("card_ui.craftbound.health", printedHealth).withStyle(upgradedHealth ? ChatFormatting.GREEN : ChatFormatting.RED),
                    Component.translatable("card_ui.craftbound.health.desc").withStyle(ChatFormatting.GRAY))));
        } else if (card.isTool()) {
            // Tools: how hard the hero hits (bottom-left) and how many attacks it lasts (bottom-right, grey).
            drawPrintedStat(g, font, painted, 4, HEIGHT - 20, 18, 150, (liveTool ? liveAttack : printedAttack), ATTACK, upgradedAttack);
            regions.add(region(x, y, scale, painted ? 9 : 4, HEIGHT - 20, 18, 18, List.of(
                    Component.translatable("card_ui.craftbound.attack", (liveTool ? liveAttack : printedAttack)).withStyle(upgradedAttack ? ChatFormatting.GREEN : ChatFormatting.GOLD),
                    Component.translatable("card_ui.craftbound.tool_attack.desc").withStyle(ChatFormatting.GRAY))));
            drawPrintedStat(g, font, painted, WIDTH - 22, HEIGHT - 20, 100, 150, (liveTool ? liveUses : printedHealth), USES, upgradedHealth);
            regions.add(region(x, y, scale, painted ? 91 : WIDTH - 22, HEIGHT - 20, 18, 18, List.of(
                    Component.translatable("card_ui.craftbound.uses", (liveTool ? liveUses : printedHealth)).withStyle(ChatFormatting.GRAY),
                    Component.translatable("card_ui.craftbound.uses.desc").withStyle(ChatFormatting.GRAY))));
        }

        // Every kind has a clear label; a Block can also retain its Crop/Aquatic subtype.
        {
            Component typeName = kindLabel(id, card);
            int plateWidth = Math.min(card.isMob() || card.isTool() ? 72 : WIDTH - 20, font.width(typeName) + 8);
            int plateLeft = WIDTH / 2 - plateWidth / 2;
            int plateTop = HEIGHT - 17;
            if (painted) {
                plateTop = 148;
                plateWidth = 48;
                plateLeft = (WIDTH - plateWidth) / 2;
            } else {
                g.fill(plateLeft, plateTop, plateLeft + plateWidth, plateTop + 12, FRAME_DARK);
                g.fill(plateLeft + 1, plateTop + 1, plateLeft + plateWidth - 1, plateTop + 11, PARCHMENT_DARK);
            }
            g.pose().pushMatrix();
            float labelScale = Math.min(1.0F, (plateWidth - 8) / (float) Math.max(1, font.width(typeName)));
            g.pose().translate(WIDTH / 2.0F, plateTop + 2 + (1 - labelScale) * 4);
            g.pose().scale(labelScale, labelScale);
            g.text(font, typeName, -font.width(typeName) / 2, 0, painted ? WHITE : TEXT_DARK, false);
            g.pose().popMatrix();
            regions.add(region(x, y, scale, plateLeft, plateTop, plateWidth, 12, List.of(
                    typeName.copy().withStyle(ChatFormatting.GOLD),
                    Component.translatable(block ? "keyword.craftbound.block.desc" : card.isMob() ? "card_ui.craftbound.mob_type.desc" : "card_ui.craftbound.kind.desc").withStyle(ChatFormatting.GRAY))));
        }

        if (enchanted) {
            drawEnchantedShimmer(g);
        }

        g.pose().popMatrix();
        return regions;
    }

    private static void drawDefaultFrameAndArt(CardCanvas g, Identifier id, CardDefinition card, boolean block) {
        // Frame, lit from the top-left like Minecraft items. Class cards are tinted with their class's colour.
        int[] frame = frameColors(card.cardClass());
        g.fill(0, 0, WIDTH, HEIGHT, frame[0]);
        g.fill(0, 0, WIDTH, 2, frame[1]);
        g.fill(0, 0, 2, HEIGHT, frame[1]);
        g.fill(0, HEIGHT - 2, WIDTH, HEIGHT, frame[2]);
        g.fill(WIDTH - 2, 0, WIDTH, HEIGHT, frame[2]);

        boolean item = !card.isMob() && !card.isTool();
        // Keep class-coloured edges, with a distinct material/silhouette for each kind.
        if (item) {
            g.fill(5, 5, WIDTH - 5, HEIGHT - 5, 0xFFCEAC78);
            g.fill(5, 5, WIDTH - 5, 8, 0xFFF1D7A3);
            g.fill(5, HEIGHT - 8, WIDTH - 5, HEIGHT - 5, 0xFF967244);
            for (int yy : new int[]{5, HEIGHT - 11}) {
                g.fill(3, yy, 10, yy + 6, 0xFF8B623D);
                g.fill(WIDTH - 10, yy, WIDTH - 3, yy + 6, 0xFF8B623D);
            }
        } else if (block) {
            for (int xx : new int[]{3, WIDTH - 12}) for (int yy : new int[]{3, HEIGHT - 13}) {
                g.fill(xx, yy, xx + 9, yy + 10, 0xFFB3B0A2);
                g.fill(xx + 1, yy + 1, xx + 8, yy + 3, 0xFFE1DECB);
                g.fill(xx + 3, yy + 4, xx + 6, yy + 7, 0xFF4A4B46);
            }
        }

        int artTop = card.isMob() ? 24 : 27;
        int artWidth = card.isMob() ? ART_WIDTH : WIDTH - 16;
        int artLeft = (WIDTH - artWidth) / 2;
        int artBottom = artTop + (card.isMob() ? ART_HEIGHT : 62);
        if (card.isMob()) {
            drawBoardArt(g, id, card, artLeft, artTop, ART_WIDTH, ART_HEIGHT);
        } else if (item) {
            octagon(g, WIDTH / 2 - 31, artTop, 62, FRAME_DARK);
            octagon(g, WIDTH / 2 - 29, artTop + 2, 58, 0xFF9E7649);
            octagon(g, WIDTH / 2 - 26, artTop + 5, 52, 0xFFF1D7A3);
        } else {
            g.fill(8, artTop, WIDTH - 8, artBottom, ITEM_BACKDROP);
        }
        if (!item) g.outline(artLeft - 1, artTop - 1, artWidth + 2, artBottom - artTop + 2, FRAME_DARK);
        if (block) {
            for (int xx : new int[]{artLeft - 2, artLeft + artWidth - 4}) for (int yy : new int[]{artTop - 2, artBottom - 5}) {
                g.fill(xx, yy, xx + 6, yy + 7, 0xFFBEC1B5);
                g.fill(xx + 2, yy + 2, xx + 4, yy + 4, 0xFF424C46);
            }
        }
        if (card.rarity() == CardRarity.LEGENDARY) drawLegendaryArtFrame(g, artLeft - 3, artTop - 3, artWidth + 6, artBottom - artTop + 6);
        if (!card.isMob()) drawArt(g, id, card, WIDTH / 2, (artTop + artBottom) / 2);
    }

    // Turns a box measured on the card into a box on screen.
    private static HoverRegion region(int cardX, int cardY, float scale, int x, int y, int width, int height, List<Component> lines) {
        return new HoverRegion(cardX + Math.round(x * scale), cardY + Math.round(y * scale),
                Math.round(width * scale), Math.round(height * scale), lines, null);
    }

    // The card's text as lines: one clause per keyword, each wrapped to the given width.
    public static Component liveToolText(Identifier id, int attack, int uses) {
        var e = craftbound.card.CardCatalog.entry(id);
        // Render the actual scripted abilities, omitting stale printed Attack/Durability prose.
        var scripted = new craftbound.card.CardCatalog.Entry(e.definition(), e.name(), "", true, e.passive(), e.passiveAmount(), e.effects());
        return Component.literal(attack + " Attack. " + uses + " Durability remaining. ")
                .append(Cards.styledText(craftbound.card.CardCatalog.description(scripted)));
    }

    // A brewed potion shows its upgraded rules, with the brewed upgrades in green.
    static Component brewText(Identifier id, int bits) {
        var entry = craftbound.card.CardCatalog.entry(id);
        if (entry == null) return Component.empty();
        var brew = craftbound.card.Brew.fromBits(bits);
        String text = brew.describe(entry);
        String head = "Brewed: " + String.join(", ", brew.upgrades().stream().map(u -> u.label).toList()) + ".";
        if (!text.startsWith(head)) return Cards.styledText(text);
        return Component.empty().append(Component.literal(head).withStyle(style -> style.withColor(UPGRADE_TEXT)))
                .append(Cards.styledText(text.substring(head.length())));
    }

    // A trained copy is distinct from its unmodified catalogue card, including in inspection.
    static Component trialText(Identifier id, TrialTraining training) {
        CardDefinition card = Cards.get(id);
        if (card == null) return Component.empty();
        Component body = Cards.text(id);
        if (training == TrialTraining.TEMPER && card.isTool()) {
            var entry = craftbound.card.CardCatalog.entry(id);
            var scripted = new craftbound.card.CardCatalog.Entry(entry.definition(), entry.name(), "", true,
                    entry.passive(), entry.passiveAmount(), entry.effects());
            body = Component.empty()
                    .append(Component.literal(String.valueOf(training.attack(card))).withStyle(style -> style.withColor(UPGRADE_TEXT)))
                    .append(" Attack. " + card.health() + " Durability remaining. ")
                    .append(Cards.styledText(craftbound.card.CardCatalog.description(scripted)));
        }
        if (training.keyword() != null && !card.keywords().contains(training.keyword())) {
            var trained = Component.empty().append(training.keyword().inline().copy()
                    .withStyle(style -> style.withColor(UPGRADE_TEXT))).append(".");
            if (!body.getString().isBlank()) trained.append(" ").append(body);
            body = trained;
        }
        return body;
    }

    private record TextKey(Identifier id, boolean painted, int attack, int uses, TrialTraining training, int brew) {}
    private record KeywordSpan(Keyword keyword, int start, int width, List<Component> tooltip) {}
    private record TextLine(FormattedCharSequence text, int x, int y, List<KeywordSpan> keywords) {}
    private record TextLayout(float scale, float top, List<TextLine> lines, int number, int collectorTotal) {}
    private static final Map<TextKey, TextLayout> TEXT_LAYOUTS = new LinkedHashMap<>(64, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<TextKey, TextLayout> eldest) { return size() > 512; }
    };
    private static long textEpoch = -1, layoutBuilds;
    static void clearTextLayouts() { TEXT_LAYOUTS.clear(); }
    static long layoutBuilds() { return layoutBuilds; }
    static int cachedTextLayouts() { return TEXT_LAYOUTS.size(); }

    private static TextLayout textLayout(Font font, Identifier id, CardDefinition card, boolean painted, int attack, int uses, TrialTraining training, int brew) {
        long epoch = CardRenderEpoch.current(font);
        if (epoch != textEpoch) { TEXT_LAYOUTS.clear(); textEpoch = epoch; }
        var key = new TextKey(id, painted, attack, uses, training, brew);
        TextLayout cached = TEXT_LAYOUTS.get(key);
        if (cached != null) return cached;
        layoutBuilds++;
        Component body = attack >= 0 ? liveToolText(id, attack, uses) : brew != 0 ? brewText(id, brew) : trialText(id, training);
        int boxTop = painted ? 104 : 96, boxBottom = painted ? 140 : 147;
        int textWidth = WIDTH - 16 - 2 - 8;
        int number = Cards.collectorNumber(id);
        int textHeight = boxBottom - boxTop - 6 - (number > 0 ? 4 : 0);
        float textScale = 1;
        boolean centered = CardText.centerKeywords(body);
        // Clause extraction also depends only on the text, not on each attempted wrap width.
        var clauses = CardText.clauses(body);
        List<FormattedCharSequence> lines = wrapClauses(font, clauses, textWidth);
        while (lines.size() * font.lineHeight * textScale > textHeight && textScale > .5F) {
            textScale -= .05F;
            lines = wrapClauses(font, clauses, Math.round(textWidth / textScale));
        }
        float top = boxTop + 2F + (textHeight - lines.size() * font.lineHeight * textScale) / 2F + 1;
        List<TextLine> measured = new ArrayList<>();
        int y = 0;
        for (var line : lines) {
            int x = centered ? Math.round((textWidth / textScale - font.width(line)) / 2) : 0;
            measured.add(new TextLine(line, x, y, measureKeywords(font, card, line)));
            y += font.lineHeight;
        }
        var layout = new TextLayout(textScale, top, List.copyOf(measured), number, Cards.collectorTotal());
        TEXT_LAYOUTS.put(key, layout);
        return layout;
    }

    private static List<FormattedCharSequence> wrapClauses(Font font, List<? extends Component> clauses, int width) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (Component clause : clauses) lines.addAll(font.split(clause, width));
        return lines;
    }

    private static List<KeywordSpan> measureKeywords(Font font, CardDefinition card, FormattedCharSequence line) {
        List<KeywordSpan> spans = new ArrayList<>();
        int[] cursor = {0}, start = {0};
        Keyword[] current = {null};
        line.accept((index, style, codepoint) -> {
            Keyword keyword = Keyword.fromInsertion(style.getInsertion());
            if (keyword != current[0]) {
                if (current[0] != null) spans.add(keywordSpan(card, current[0], start[0], cursor[0] - start[0]));
                current[0] = keyword;
                start[0] = cursor[0];
            }
            cursor[0] += font.width(FormattedCharSequence.codepoint(codepoint, style));
            return true;
        });
        if (current[0] != null) spans.add(keywordSpan(card, current[0], start[0], cursor[0] - start[0]));
        return List.copyOf(spans);
    }

    private static KeywordSpan keywordSpan(CardDefinition card, Keyword keyword, int start, int width) {
        List<Component> lines = new ArrayList<>();
        lines.add(keyword.displayName().copy().withStyle(ChatFormatting.GOLD));
        lines.add(keyword.description(card).copy().withStyle(ChatFormatting.GRAY));
        return new KeywordSpan(keyword, start, width, List.copyOf(lines));
    }

    // Keep the same composition from the hand to the table, including both edges of the scene.
    public static void drawBoardArt(CardCanvas g, Identifier id, CardDefinition card, int x, int y, int w, int h) {
        if (card == null || id == null || w <= 0 || h <= 0) return;
        float scale = Math.min(w / (float) ART_WIDTH, h / (float) ART_HEIGHT);
        g.pose().pushMatrix();
        g.pose().translate(x + (w - ART_WIDTH * scale) / 2, y + (h - ART_HEIGHT * scale) / 2);
        g.pose().scale(scale, scale);
        if (card.keywords().contains(Keyword.BLOCK)) {
            g.fill(0, 0, ART_WIDTH, ART_HEIGHT, 0xFF4D5552);
            for (int row = 0; row * 15 < ART_HEIGHT; row++) {
                int yy = row * 15;
                g.fill(0, yy, ART_WIDTH, yy + 1, 0xFF313B37);
                for (int xx =  (row % 2) * 17; xx < ART_WIDTH; xx += 34)
                    g.fill(xx, yy, xx + 1, Math.min(ART_HEIGHT, yy + 15), 0xFF313B37);
            }
            g.fill(0, ART_HEIGHT - 10, ART_WIDTH, ART_HEIGHT, 0xFF69716A);
        } else {
            g.fill(0, 0, ART_WIDTH, ART_HEIGHT, SKY);
            g.fill(0, ART_HEIGHT - 14, ART_WIDTH, ART_HEIGHT, GRASS);
            g.fill(ART_WIDTH - 32, ART_HEIGHT - 14, ART_WIDTH, ART_HEIGHT, GRASS_DARK);
        }
        drawArt(g, id, card, ART_WIDTH / 2, ART_HEIGHT / 2);
        g.pose().popMatrix();
    }

    // Shows the matching Minecraft item (or the mob's spawn egg) three times bigger until real card art exists.
    private static void drawArt(CardCanvas g, Identifier id, CardDefinition card, int centerX, int centerY) {
        ItemStack art = placeholderArt(id, card);
        if (art.is(Items.AIR)) {
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate(centerX, centerY);
        g.pose().scale(3.0F, 3.0F);
        g.item(art, -8, -8);
        g.pose().popMatrix();
    }

    // Shared class palette: full cards, miniatures, hero powers and match/table portraits agree.
    private static int[] frameColors(CardClass cardClass) {
        return new int[]{cardClass.frameColor(), cardClass.lightColor(), cardClass.darkColor()};
    }

    // The Minecraft item that stands in for a card's art: the card's chosen art item if it has one,
    // otherwise the mob's spawn egg, or the item of the same name.
    public static ItemStack placeholderArt(Identifier id, CardDefinition card) {
        // World tables can arrive before catalogue sync, or retain a removed card ID.
        // Resolve again on the next render rather than caching an invented definition.
        if (card == null || id == null) return new ItemStack(Items.PAPER);
        boolean mob = card.isMob() && !card.keywords().contains(Keyword.BLOCK);
        String itemName = card.artItem() != null ? card.artItem()
                : mob ? id.getPath() + "_spawn_egg" : id.getPath();
        var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemName));
        // Workshop authors may enter an entity ID (minecraft:zombie) instead of its egg item.
        // Resolve real items first so chosen artwork such as a sword or fish stays unchanged.
        if ((item == null || item == Items.AIR) && mob && !itemName.endsWith("_spawn_egg"))
            item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemName + "_spawn_egg"));
        return new ItemStack(item == null || item == Items.AIR ? Items.PAPER : item);
    }

    // Actions with an element (Fire, Frost, Ender...) show it in place of "Action", like a spell school.
    public static Component kindLabel(Identifier id, CardDefinition card) {
        if (card.kind() == CardKind.ITEM) {
            var entry = craftbound.card.CardCatalog.entry(id);
            if (entry != null && entry.potion())
                return Component.literal(entry.element() == null ? "Potion" : entry.element().displayName() + " Potion");
            if (entry != null && entry.element() != null) return Component.literal(entry.element().displayName());
        }
        return kindLabel(card);
    }

    public static Component kindLabel(CardDefinition card) {
        if (card.keywords().contains(Keyword.BLOCK)) {
            var label = Component.translatable("card_ui.craftbound.kind.block");
            return card.mobType() == null ? label : label.append(" · ").append(card.mobType().displayName());
        }
        if (card.isMob() && card.mobType() != null) return card.mobType().displayName();
        String kind = card.isMob() ? "mob" : card.isTool() ? "tool" : card.kind() == CardKind.ENCHANTMENT ? "enchantment" : "item";
        return Component.translatable("card_ui.craftbound.kind." + kind);
    }

    /** Gold, stepped corners and a crest distinguish legendary artwork at every scale. */
    public static void drawLegendaryArtFrame(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        drawLegendaryArtFrame(new CardCanvas.Gui(g), x, y, width, height);
    }

    private static void drawLegendaryArtFrame(CardCanvas g, int x, int y, int width, int height) {
        g.outline(x, y, width, height, 0xFF573916);
        g.outline(x + 1, y + 1, width - 2, height - 2, 0xFFE0AD40);
        g.outline(x + 2, y + 2, width - 4, height - 4, 0xFFFFE4A0);
        for (int xx : new int[]{x, x + width - 7}) for (int yy : new int[]{y, y + height - 7}) {
            g.fill(xx, yy, xx + 7, yy + 7, 0xFF8F611D);
            g.fill(xx + 1, yy + 1, xx + 6, yy + 6, 0xFFE0AD40);
            g.fill(xx + 1, yy + 1, xx + 4, yy + 3, 0xFFFFE4A0);
        }
        drawDiamond(g, x + width / 2, y + 1, 4, 0xFF8F611D);
        drawDiamond(g, x + width / 2, y + 1, 2, 0xFFFFE4A0);
    }

    // A class power drawn as a card, like Hearthstone's hero power card: the XP cost on top, round art, the name
    // on a ribbon and "Class Power" with what it does. Framed in the class's colour. Greyed out once used.
    public static void drawPower(GuiGraphicsExtractor g, Font font, Component name, Component description, ItemStack icon,
                                 CardClass cardClass, int cost, boolean used, int x, int y, float scale) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        int[] frame = frameColors(cardClass);
        g.fill(0, 0, WIDTH, HEIGHT, frame[0]);
        g.fill(0, 0, WIDTH, 2, frame[1]);
        g.fill(0, 0, 2, HEIGHT, frame[1]);
        g.fill(0, HEIGHT - 2, WIDTH, HEIGHT, frame[2]);
        g.fill(WIDTH - 2, 0, WIDTH, HEIGHT, frame[2]);

        // Round art: a gold ring with the power's item inside.
        int centerX = WIDTH / 2;
        int centerY = 65;
        octagon(new CardCanvas.Gui(g), centerX - 38, centerY - 38, 76, FRAME_DARK);
        octagon(new CardCanvas.Gui(g), centerX - 36, centerY - 36, 72, 0xFFE8B84A);
        octagon(new CardCanvas.Gui(g), centerX - 32, centerY - 32, 64, ITEM_BACKDROP);
        g.pose().pushMatrix();
        g.pose().translate(centerX, centerY);
        g.pose().scale(3.0F, 3.0F);
        g.item(icon, -8, -8);
        g.pose().popMatrix();

        // XP and name share the top row.

        // Name ribbon.
        g.fill(4, 4, WIDTH - 4, 18, FRAME_DARK);
        fitText(g, font, name, WIDTH / 2, 7, WIDTH - 44, WHITE, true);
        drawStat(new CardCanvas.Gui(g), font, 2, 2, cost, EMERALD);

        // "Class Power" and what it does, shrunk to fit the parchment.
        int boxTop = 114;
        int boxBottom = HEIGHT - 8;
        g.fill(8, boxTop, WIDTH - 8, boxBottom, PARCHMENT);
        g.fill(8, boxBottom - 2, WIDTH - 8, boxBottom, PARCHMENT_DARK);
        Component heading = Component.translatable("card_ui.craftbound.class_power").withStyle(ChatFormatting.BOLD);
        int textWidth = WIDTH - 24;
        int textHeight = boxBottom - boxTop - 6;
        float textScale = 1.0F;
        List<FormattedCharSequence> lines = powerLines(font, heading, description, textWidth);
        while (lines.size() * font.lineHeight * textScale > textHeight && textScale > 0.5F) {
            textScale -= 0.05F;
            lines = powerLines(font, heading, description, Math.round(textWidth / textScale));
        }
        float textTop = boxTop + 3 + (textHeight - lines.size() * font.lineHeight * textScale) / 2.0F;
        g.pose().pushMatrix();
        g.pose().translate(centerX, textTop);
        g.pose().scale(textScale, textScale);
        int lineY = 0;
        for (FormattedCharSequence line : lines) {
            g.text(font, line, -font.width(line) / 2, lineY, TEXT_DARK, false);
            lineY += font.lineHeight;
        }
        g.pose().popMatrix();

        if (used) {
            g.fill(0, 0, WIDTH, HEIGHT, 0x90000000);
        }
        g.pose().popMatrix();
    }

    // The heading on its own line(s), then the description.
    private static List<FormattedCharSequence> powerLines(Font font, Component heading, Component description, int width) {
        List<FormattedCharSequence> lines = new ArrayList<>(font.split(heading, width));
        lines.addAll(font.split(description, width));
        return lines;
    }

    // A square with its corners cut off.
    private static void octagon(CardCanvas g, int x, int y, int size, int color) {
        int cut = size / 4;
        for (int row = 0; row < size; row++) {
            int inset = row < cut ? cut - row : row >= size - cut ? row - (size - cut) + 1 : 0;
            g.fill(x + inset, y + row, x + size - inset, y + row + 1, color);
        }
    }

    private static void drawPrintedStat(CardCanvas g, Font font, boolean painted, int x, int y, int paintedCenterX, int paintedTextY, int value, int color, boolean upgraded) {
        int textColor = upgraded ? UPGRADE_STAT : WHITE;
        if (painted) g.centeredText(font, String.valueOf(value), paintedCenterX, paintedTextY, textColor);
        else drawStat(g, font, x, y, value, color, textColor);
    }

    private static void drawStat(CardCanvas g, Font font, int x, int y, int value, int color) {
        drawStat(g, font, x, y, value, color, WHITE);
    }

    private static void drawStat(CardCanvas g, Font font, int x, int y, int value, int color, int textColor) {
        g.fill(x, y, x + 18, y + 18, FRAME_DARK);
        g.fill(x + 1, y + 1, x + 17, y + 17, color);
        g.fill(x + 1, y + 1, x + 17, y + 3, 0x40FFFFFF);
        g.centeredText(font, String.valueOf(value), x + 9, y + 5, textColor);
    }

    // Draws text at normal size if it fits in maxWidth, otherwise shrunk just enough to fit.
    // x is the left edge, or the centre when centred.
    public static void fitText(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int maxWidth, int color, boolean centred) {
        fitText(g, font, text, x, y, maxWidth, color, centred, true);
    }

    public static void fitText(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int maxWidth, int color, boolean centred, boolean shadow) {
        fitText(new CardCanvas.Gui(g), font, text, x, y, maxWidth, color, centred, shadow);
    }

    private static void fitText(CardCanvas g, Font font, Component text, int x, int y, int maxWidth, int color, boolean centred) {
        fitText(g, font, text, x, y, maxWidth, color, centred, true);
    }

    private static void fitText(CardCanvas g, Font font, Component text, int x, int y, int maxWidth, int color, boolean centred, boolean shadow) {
        int width = font.width(text);
        float scale = width > maxWidth ? maxWidth / (float) width : 1.0F;
        float left = centred ? x - width * scale / 2.0F : x;
        // Keep the text vertically centred on its line as it shrinks.
        float top = y + font.lineHeight * (1.0F - scale) / 2.0F;
        g.pose().pushMatrix();
        g.pose().translate(left, top);
        g.pose().scale(scale, scale);
        g.text(font, text, 0, 0, color, shadow);
        g.pose().popMatrix();
    }

    public static final int MINI_WIDTH = 36;
    public static final int MINI_HEIGHT = 48;

    // A miniature card for binder pockets and the deck builder: frame, art window, the card's placeholder art,
    // cost, a rarity gem and a count. Enchanted ones get a purple edge and the shimmer.
    public static void drawMini(GuiGraphicsExtractor g, Font font, Identifier id, int x, int y, int count, boolean enchanted) {
        CardDefinition card = Cards.get(id);
        if (card == null) return;
        boolean block = card.keywords().contains(Keyword.BLOCK);
        boolean item = !card.isMob() && !card.isTool();
        int[] frame = frameColors(card.cardClass());
        // Card face and collection footer are separate; shimmer never washes out the numbers.
        g.fill(x + 2, y + 1, x + MINI_WIDTH - 2, y + 40, frame[0]);
        g.outline(x + 2, y + 1, MINI_WIDTH - 4, 39, frame[2]);
        g.fill(x + 3, y + 2, x + MINI_WIDTH - 3, y + 3, frame[1]);
        int artColor = block ? 0xFF4D5552 : card.isMob() ? SKY : item ? PARCHMENT : ITEM_BACKDROP;
        g.fill(x + 5, y + 4, x + MINI_WIDTH - 5, y + 26, artColor);
        if (block) {
            for (int row = 0; row < 3; row++) {
                int yy = y + 4 + row * 7;
                g.fill(x + 5, yy, x + MINI_WIDTH - 5, yy + 1, 0xFF313B37);
                int joint = x + 11 + row % 2 * 9;
                g.fill(joint, yy, joint + 1, yy + 7, 0xFF313B37);
            }
            for (int xx : new int[]{x + 4, x + MINI_WIDTH - 8}) for (int yy : new int[]{y + 3, y + 23})
                g.fill(xx, yy, xx + 4, yy + 4, 0xFFB8BCAD);
        } else if (card.isMob()) {
            g.fill(x + 5, y + 22, x + MINI_WIDTH - 5, y + 26, GRASS);
        } else if (item) {
            g.outline(x + 4, y + 3, MINI_WIDTH - 8, 24, PARCHMENT_DARK);
        }
        g.outline(x + 5, y + 4, MINI_WIDTH - 10, 22, FRAME_DARK);
        g.item(placeholderArt(id, card), x + MINI_WIDTH / 2 - 8, y + 7);
        if (card.rarity() == CardRarity.LEGENDARY) drawLegendaryArtFrame(g, x + 3, y + 2, MINI_WIDTH - 6, 25);
        if (enchanted) {
            drawDiagonalShimmer(g, x + 3, y + 2, MINI_WIDTH - 6, 37, 7, 0x45);
            g.outline(x + 2, y + 1, MINI_WIDTH - 4, 39, 0xFFB070FF);
        }
        // Rarity sits on the centreline directly beneath the art, like the full card.
        drawDiamond(new CardCanvas.Gui(g), x + MINI_WIDTH / 2, y + 28, 3, FRAME_DARK);
        drawDiamond(new CardCanvas.Gui(g), x + MINI_WIDTH / 2, y + 28, 2, 0xFF000000 | card.rarity().rgb());
        g.fill(x + MINI_WIDTH / 2 - 1, y + 27, x + MINI_WIDTH / 2, y + 28, 0x70FFFFFF);
        if (!item) {
            if (!block) drawMiniStat(g, font, x + 3, y + 30, card.attack(), ATTACK);
            drawMiniStat(g, font, x + MINI_WIDTH - 13, y + 30, card.health(), card.isTool() ? USES : HEALTH);
        } else {
            g.fill(x + 5, y + 30, x + MINI_WIDTH - 5, y + 39, PARCHMENT);
            fitText(g, font, kindLabel(id, card), x + MINI_WIDTH / 2, y + 31, MINI_WIDTH - 10, TEXT_DARK, true, false);
        }
        drawMiniStat(g, font, x + 1, y + 1, card.cost(), EMERALD);
        g.fill(x + 2, y + 40, x + MINI_WIDTH - 2, y + MINI_HEIGHT, FRAME_DARK);
        g.pose().pushMatrix();
        g.pose().translate(x + MINI_WIDTH / 2.0F, y + 40.5F);
        g.pose().scale(0.8F, 0.8F);
        fitText(g, font, Component.literal("×" + count), 0, 0, (MINI_WIDTH - 8) * 5 / 4, WHITE, true, true);
        g.pose().popMatrix();
    }

    private static void drawMiniStat(GuiGraphicsExtractor g, Font font, int x, int y, int value, int color) {
        g.fill(x, y, x + 10, y + 10, FRAME_DARK);
        g.fill(x + 1, y + 1, x + 9, y + 9, color);
        g.fill(x + 1, y + 1, x + 9, y + 2, 0x60FFFFFF);
        fitText(g, font, Component.literal(String.valueOf(value)), x + 5, y + 1, 8, WHITE, true, true);
    }

    // Draws the back of a card in a sleeve's colours: a framed panel with a diamond emblem in the middle.
    public static void drawBack(GuiGraphicsExtractor g, Sleeves.Design design, int x, int y, float scale) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);

        Identifier back = Sleeves.trialBack(design);
        if (back != null) {
            Identifier art = craftbound.Craftbound.id("textures/gui/trials/backs/" + back.getPath() + ".png");
            if (CardFrames.hasTrialArt(art)) {
                new CardCanvas.Gui(g).frameRegion(art,0,0,WIDTH,HEIGHT,0,0,WIDTH,HEIGHT);
                g.pose().popMatrix();
                return;
            }
        }
        g.fillGradient(0, 0, WIDTH, HEIGHT, design.base(), design.baseDark());
        g.fill(0, 0, WIDTH, 4, design.border());
        g.fill(0, HEIGHT - 4, WIDTH, HEIGHT, design.border());
        g.fill(0, 0, 4, HEIGHT, design.border());
        g.fill(WIDTH - 4, 0, WIDTH, HEIGHT, design.border());
        g.outline(5, 5, WIDTH - 10, HEIGHT - 10, 0xFF000000);
        g.outline(10, 10, WIDTH - 20, HEIGHT - 20, design.border());

        drawDiamond(new CardCanvas.Gui(g), WIDTH / 2, HEIGHT / 2, 26, design.emblemDark());
        drawDiamond(new CardCanvas.Gui(g), WIDTH / 2, HEIGHT / 2, 22, design.emblem());
        drawDiamond(new CardCanvas.Gui(g), WIDTH / 2 - 6, HEIGHT / 2 - 6, 5, 0x60FFFFFF);

        g.pose().popMatrix();
    }

    // A diamond made of one-pixel rows, `radius` pixels from the centre to each point.
    private static void drawDiamond(CardCanvas g, int centerX, int centerY, int radius, int color) {
        for (int row = -radius; row <= radius; row++) {
            int halfWidth = radius - Math.abs(row);
            g.fill(centerX - halfWidth, centerY + row, centerX + halfWidth + 1, centerY + row + 1, color);
        }
    }

    static void drawEnchantedShimmer(CardCanvas g) {
        drawDiagonalShimmer(g, 0, 0, WIDTH, HEIGHT, 20, 0x70);
        g.outline(0, 0, WIDTH, HEIGHT, 0xFFB070FF);
    }

    // A purple band that sweeps diagonally across a rectangle, from the top-left corner to the bottom-right,
    // then rests for a moment before the next sweep. Used on Enchanted cards and binder pockets.
    public static void drawDiagonalShimmer(GuiGraphicsExtractor g, int x, int y, int width, int height, int halfWidth, int maxAlpha) {
        drawDiagonalShimmer(new CardCanvas.Gui(g), x, y, width, height, halfWidth, maxAlpha);
    }

    private static void drawDiagonalShimmer(CardCanvas g, int x, int y, int width, int height, int halfWidth, int maxAlpha) {
        long time = craftbound.client.AnimationClock.millis() % 2400L;
        if (time > 1600L) {
            return;
        }
        float progress = time / 1600.0F;
        // The band slants like "/". Its centre is wherever column + row equals `diagonal`, which grows over the sweep.
        int diagonal = -halfWidth + (int) (progress * (width + height + halfWidth * 2));
        g.shimmer(x, y, width, height, diagonal, halfWidth, maxAlpha);
    }
}
