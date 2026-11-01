package craftbound.client.card;

import craftbound.card.CardClass;
import craftbound.card.CardDefinition;
import craftbound.card.CardText;
import craftbound.card.Cards;
import craftbound.card.Keyword;
import craftbound.component.ModComponents;
import craftbound.sleeve.Sleeves;
import java.util.ArrayList;
import java.util.List;
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

    // An area of the card that shows a tooltip when the mouse is over it. Coordinates are on screen.
    // `keyword` is set when the area is a keyword in the card text, otherwise it's null.
    public record HoverRegion(int x, int y, int width, int height, List<Component> lines, Keyword keyword) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    // Draws the card with its top-left corner at (x, y), scaled up by `scale`, and returns its hover areas.
    public static List<HoverRegion> draw(GuiGraphicsExtractor g, Font font, ItemStack stack, int x, int y, float scale) {
        List<HoverRegion> regions = new ArrayList<>();
        Identifier id = stack.get(ModComponents.CARD_ID);
        CardDefinition card = id == null ? null : Cards.get(id);
        if (card == null) {
            return regions;
        }
        boolean enchanted = stack.has(ModComponents.ENCHANTED);

        // Everything below is drawn as if the card's top-left were (0, 0) at normal size.
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);

        // Frame, lit from the top-left like Minecraft items. Class cards are tinted with their class's colour.
        int[] frame = frameColors(card.cardClass());
        g.fill(0, 0, WIDTH, HEIGHT, frame[0]);
        g.fill(0, 0, WIDTH, 2, frame[1]);
        g.fill(0, 0, 2, HEIGHT, frame[1]);
        g.fill(0, HEIGHT - 2, WIDTH, HEIGHT, frame[2]);
        g.fill(WIDTH - 2, 0, WIDTH, HEIGHT, frame[2]);

        // Art window: sky over grass for mobs, plain blue for items. The item or spawn egg stands in for real art.
        int artTop = 8;
        int artBottom = 82;
        if (card.isMob()) {
            g.fill(8, artTop, WIDTH - 8, artBottom, SKY);
            g.fill(8, artBottom - 14, WIDTH - 8, artBottom, GRASS);
            g.fill(WIDTH - 40, artBottom - 14, WIDTH - 8, artBottom, GRASS_DARK);
        } else {
            g.fill(8, artTop, WIDTH - 8, artBottom, ITEM_BACKDROP);
        }
        g.outline(7, artTop - 1, WIDTH - 14, artBottom - artTop + 2, FRAME_DARK);
        drawArt(g, id, card, WIDTH / 2, artTop + 34);

        // Rarity gem between the art and the name. Tokens have none.
        int gemX = WIDTH / 2;
        if (!card.token()) {
            g.fill(gemX - 4, artBottom - 2, gemX + 4, artBottom + 6, FRAME_DARK);
            g.fill(gemX - 3, artBottom - 1, gemX + 3, artBottom + 5, 0xFF000000 | card.rarity().rgb());
            g.fill(gemX - 3, artBottom - 1, gemX - 1, artBottom + 1, 0x80FFFFFF);
            regions.add(region(x, y, scale, gemX - 5, artBottom - 3, 10, 10, List.of(
                    card.rarity().displayName().copy().withStyle(card.rarity().color()),
                    Component.translatable("card_ui.craftbound.rarity.desc").withStyle(ChatFormatting.GRAY))));
        }

        // Name ribbon. The name is coloured by rarity and starts with "Enchanted" on Enchanted cards.
        int ribbonTop = 90;
        g.fill(4, ribbonTop, WIDTH - 4, ribbonTop + 14, FRAME_DARK);
        // Long names like "Enchanted Golden Apple" shrink to fit the ribbon instead of spilling over.
        fitText(g, font, stack.getHoverName(), WIDTH / 2, ribbonTop + 3, WIDTH - 12, WHITE, true);
        List<Component> nameLines = new ArrayList<>();
        nameLines.add(stack.getHoverName());
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
        regions.add(region(x, y, scale, 4, ribbonTop, WIDTH - 8, 14, nameLines));

        // Text box: what the card does. Each keyword starts its own line on the left, and the text shrinks
        // until it fits. No drop shadow, so it stays sharp on the parchment.
        int boxTop = 108;
        int boxBottom = 147;
        g.fill(8, boxTop, WIDTH - 8, boxBottom, PARCHMENT);
        g.fill(WIDTH - 10, boxTop, WIDTH - 8, boxBottom, PARCHMENT_DARK);
        g.fill(8, boxBottom - 2, WIDTH - 8, boxBottom, PARCHMENT_DARK);
        int padding = 4;
        float textLeft = 8 + padding;
        int textWidth = WIDTH - 16 - 2 - padding * 2;
        int number = Cards.collectorNumber(id);
        // Leave a little room at the bottom for the collector number.
        int textHeight = boxBottom - boxTop - 2 - padding - (number > 0 ? 4 : 0);
        float textScale = 1.0F;
        List<FormattedCharSequence> lines = wrapText(font, id, textWidth);
        while (lines.size() * font.lineHeight * textScale > textHeight && textScale > 0.5F) {
            textScale -= 0.05F;
            lines = wrapText(font, id, Math.round(textWidth / textScale));
        }
        float textTop = boxTop + padding / 2.0F + (textHeight - lines.size() * font.lineHeight * textScale) / 2.0F + 1;
        g.pose().pushMatrix();
        g.pose().translate(textLeft, textTop);
        g.pose().scale(textScale, textScale);
        int lineY = 0;
        for (FormattedCharSequence line : lines) {
            g.text(font, line, 0, lineY, TEXT_DARK, false);
            addKeywordRegions(regions, font, card, line, textLeft, textTop + lineY * textScale, textScale, x, y, scale);
            lineY += font.lineHeight;
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
                    Component.translatable("card_ui.craftbound.number", number, Cards.collectible().size()).withStyle(ChatFormatting.GOLD),
                    Component.translatable("card_ui.craftbound.number.desc").withStyle(ChatFormatting.GRAY))));
        }

        // Cost (top-left), attack (bottom-left) and health (bottom-right). Items have no attack or health.
        drawStat(g, font, 2, 2, card.cost(), EMERALD);
        regions.add(region(x, y, scale, 2, 2, 18, 18, List.of(
                Component.translatable("card_ui.craftbound.cost", card.cost()).withStyle(ChatFormatting.GREEN),
                Component.translatable("card_ui.craftbound.cost.desc", card.cost()).withStyle(ChatFormatting.GRAY))));
        if (card.isMob()) {
            drawStat(g, font, 4, HEIGHT - 20, card.attack(), ATTACK);
            regions.add(region(x, y, scale, 4, HEIGHT - 20, 18, 18, List.of(
                    Component.translatable("card_ui.craftbound.attack", card.attack()).withStyle(ChatFormatting.GOLD),
                    Component.translatable("card_ui.craftbound.attack.desc").withStyle(ChatFormatting.GRAY))));
            drawStat(g, font, WIDTH - 22, HEIGHT - 20, card.health(), HEALTH);
            regions.add(region(x, y, scale, WIDTH - 22, HEIGHT - 20, 18, 18, List.of(
                    Component.translatable("card_ui.craftbound.health", card.health()).withStyle(ChatFormatting.RED),
                    Component.translatable("card_ui.craftbound.health.desc").withStyle(ChatFormatting.GRAY))));
        } else if (card.isTool()) {
            // Tools: how hard the hero hits (bottom-left) and how many attacks it lasts (bottom-right, grey).
            drawStat(g, font, 4, HEIGHT - 20, card.attack(), ATTACK);
            regions.add(region(x, y, scale, 4, HEIGHT - 20, 18, 18, List.of(
                    Component.translatable("card_ui.craftbound.attack", card.attack()).withStyle(ChatFormatting.GOLD),
                    Component.translatable("card_ui.craftbound.tool_attack.desc").withStyle(ChatFormatting.GRAY))));
            drawStat(g, font, WIDTH - 22, HEIGHT - 20, card.health(), USES);
            regions.add(region(x, y, scale, WIDTH - 22, HEIGHT - 20, 18, 18, List.of(
                    Component.translatable("card_ui.craftbound.uses", card.health()).withStyle(ChatFormatting.GRAY),
                    Component.translatable("card_ui.craftbound.uses.desc").withStyle(ChatFormatting.GRAY))));
        }

        // Mob type plate along the bottom edge, between attack and health.
        if (card.mobType() != null) {
            Component typeName = card.mobType().displayName();
            int plateWidth = font.width(typeName) + 8;
            int plateLeft = WIDTH / 2 - plateWidth / 2;
            int plateTop = HEIGHT - 17;
            g.fill(plateLeft, plateTop, plateLeft + plateWidth, plateTop + 12, FRAME_DARK);
            g.fill(plateLeft + 1, plateTop + 1, plateLeft + plateWidth - 1, plateTop + 11, PARCHMENT_DARK);
            g.text(font, typeName, plateLeft + 4, plateTop + 2, TEXT_DARK, false);
            regions.add(region(x, y, scale, plateLeft, plateTop, plateWidth, 12, List.of(
                    typeName.copy().withStyle(ChatFormatting.GOLD),
                    Component.translatable("card_ui.craftbound.mob_type.desc").withStyle(ChatFormatting.GRAY))));
        }

        if (enchanted) {
            drawEnchantedShimmer(g);
        }

        g.pose().popMatrix();
        return regions;
    }

    // Turns a box measured on the card into a box on screen.
    private static HoverRegion region(int cardX, int cardY, float scale, int x, int y, int width, int height, List<Component> lines) {
        return new HoverRegion(cardX + Math.round(x * scale), cardY + Math.round(y * scale),
                Math.round(width * scale), Math.round(height * scale), lines, null);
    }

    // The card's text as lines: one clause per keyword, each wrapped to the given width.
    private static List<FormattedCharSequence> wrapText(Font font, Identifier id, int width) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (Component clause : CardText.clauses(Cards.text(id))) {
            lines.addAll(font.split(clause, width));
        }
        return lines;
    }

    // Walks a line of card text letter by letter and adds a hover area over each keyword in it.
    // The line was drawn at (lineLeft, lineTop) on the card, shrunk by textScale.
    private static void addKeywordRegions(List<HoverRegion> regions, Font font, CardDefinition card, FormattedCharSequence line,
                                          float lineLeft, float lineTop, float textScale, int cardX, int cardY, float scale) {
        int[] cursor = {0};
        int[] start = {0};
        Keyword[] current = {null};
        line.accept((index, style, codepoint) -> {
            Keyword keyword = Keyword.fromInsertion(style.getInsertion());
            if (keyword != current[0]) {
                if (current[0] != null) {
                    regions.add(keywordRegion(card, current[0], lineLeft + start[0] * textScale, lineTop,
                            (cursor[0] - start[0]) * textScale, font.lineHeight * textScale, cardX, cardY, scale));
                }
                current[0] = keyword;
                start[0] = cursor[0];
            }
            cursor[0] += font.width(FormattedCharSequence.codepoint(codepoint, style));
            return true;
        });
        if (current[0] != null) {
            regions.add(keywordRegion(card, current[0], lineLeft + start[0] * textScale, lineTop,
                    (cursor[0] - start[0]) * textScale, font.lineHeight * textScale, cardX, cardY, scale));
        }
    }

    // A hover area over a keyword, measured on the card (before the card's own scale).
    private static HoverRegion keywordRegion(CardDefinition card, Keyword keyword, float left, float top, float width, float height,
                                             int cardX, int cardY, float scale) {
        List<Component> lines = new ArrayList<>();
        lines.add(keyword.displayName().copy().withStyle(ChatFormatting.GOLD));
        lines.add(keyword.description().copy().withStyle(ChatFormatting.GRAY));
        if (!card.createdBy(keyword).isEmpty()) {
            lines.add(Component.translatable("card_ui.craftbound.pin_hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
        return new HoverRegion(cardX + Math.round(left * scale), cardY + Math.round((top - 1) * scale),
                Math.round(width * scale), Math.round(height * scale), lines, keyword);
    }

    // Shows the matching Minecraft item (or the mob's spawn egg) three times bigger until real card art exists.
    private static void drawArt(GuiGraphicsExtractor g, Identifier id, CardDefinition card, int centerX, int centerY) {
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

    // Frame colours (base, light edge, dark edge): Neutral cards keep the brown frame; class cards use the
    // prototype's class colours (Fighter red, Builder blue, Farmer green).
    private static int[] frameColors(CardClass cardClass) {
        return switch (cardClass) {
            case FIGHTER -> new int[]{0xFF9A3A2E, 0xFFC25A4C, 0xFF4A1410};
            case BUILDER -> new int[]{0xFF3F5E9E, 0xFF6286C6, 0xFF162A52};
            case FARMER -> new int[]{0xFF4E7E2C, 0xFF72A64A, 0xFF1E3A0E};
            case NEUTRAL -> new int[]{FRAME, FRAME_LIGHT, FRAME_DARK};
        };
    }

    // The Minecraft item that stands in for a card's art: the card's chosen art item if it has one,
    // otherwise the mob's spawn egg, or the item of the same name.
    public static ItemStack placeholderArt(Identifier id, CardDefinition card) {
        String itemName = card.artItem() != null ? card.artItem()
                : card.isMob() ? id.getPath() + "_spawn_egg" : id.getPath();
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(itemName)));
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
        int centerY = 54;
        octagon(g, centerX - 38, centerY - 38, 76, FRAME_DARK);
        octagon(g, centerX - 36, centerY - 36, 72, 0xFFE8B84A);
        octagon(g, centerX - 32, centerY - 32, 64, ITEM_BACKDROP);
        g.pose().pushMatrix();
        g.pose().translate(centerX, centerY);
        g.pose().scale(3.0F, 3.0F);
        g.item(icon, -8, -8);
        g.pose().popMatrix();

        // XP cost on top, over the ring.
        drawStat(g, font, centerX - 9, 4, cost, EMERALD);

        // Name ribbon.
        int ribbonTop = 96;
        g.fill(4, ribbonTop, WIDTH - 4, ribbonTop + 14, FRAME_DARK);
        fitText(g, font, name, centerX, ribbonTop + 3, WIDTH - 12, WHITE, true);

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
    private static void octagon(GuiGraphicsExtractor g, int x, int y, int size, int color) {
        int cut = size / 4;
        for (int row = 0; row < size; row++) {
            int inset = row < cut ? cut - row : row >= size - cut ? row - (size - cut) + 1 : 0;
            g.fill(x + inset, y + row, x + size - inset, y + row + 1, color);
        }
    }

    private static void drawStat(GuiGraphicsExtractor g, Font font, int x, int y, int value, int color) {
        g.fill(x, y, x + 18, y + 18, FRAME_DARK);
        g.fill(x + 1, y + 1, x + 17, y + 17, color);
        g.fill(x + 1, y + 1, x + 17, y + 3, 0x40FFFFFF);
        g.centeredText(font, String.valueOf(value), x + 9, y + 5, WHITE);
    }

    // Draws text at normal size if it fits in maxWidth, otherwise shrunk just enough to fit.
    // x is the left edge, or the centre when centred.
    public static void fitText(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int maxWidth, int color, boolean centred) {
        int width = font.width(text);
        float scale = width > maxWidth ? maxWidth / (float) width : 1.0F;
        float left = centred ? x - width * scale / 2.0F : x;
        // Keep the text vertically centred on its line as it shrinks.
        float top = y + font.lineHeight * (1.0F - scale) / 2.0F;
        g.pose().pushMatrix();
        g.pose().translate(left, top);
        g.pose().scale(scale, scale);
        g.text(font, text, 0, 0, color);
        g.pose().popMatrix();
    }

    public static final int MINI_WIDTH = 36;
    public static final int MINI_HEIGHT = 48;

    // A miniature card for binder pockets and the deck builder: frame, art window, the card's placeholder art,
    // cost, a rarity gem and a count. Enchanted ones get a purple edge and the shimmer.
    public static void drawMini(GuiGraphicsExtractor g, Font font, Identifier id, int x, int y, int count, boolean enchanted) {
        CardDefinition card = Cards.get(id);
        if (card == null) {
            return;
        }
        g.fill(x + 3, y + 3, x + MINI_WIDTH - 3, y + MINI_HEIGHT - 3, FRAME);
        g.fill(x + 6, y + 6, x + MINI_WIDTH - 6, y + 30, card.isMob() ? SKY : ITEM_BACKDROP);
        g.fill(x + 6, y + 33, x + 10, y + 37, 0xFF000000 | card.rarity().rgb());
        g.item(placeholderArt(id, card), x + MINI_WIDTH / 2 - 8, y + 10);
        if (enchanted) {
            drawDiagonalShimmer(g, x + 3, y + 3, MINI_WIDTH - 6, MINI_HEIGHT - 6, 8, 0x60);
            g.outline(x + 2, y + 2, MINI_WIDTH - 4, MINI_HEIGHT - 4, 0xFFB070FF);
        }
        String costText = String.valueOf(card.cost());
        g.fill(x, y, x + font.width(costText) + 4, y + 10, EMERALD);
        g.text(font, costText, x + 2, y + 1, WHITE);
        String countText = "x" + count;
        g.text(font, countText, x + MINI_WIDTH - 5 - font.width(countText), y + MINI_HEIGHT - 13, WHITE);
    }

    // Draws the back of a card in a sleeve's colours: a framed panel with a diamond emblem in the middle.
    public static void drawBack(GuiGraphicsExtractor g, Sleeves.Design design, int x, int y, float scale) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);

        g.fillGradient(0, 0, WIDTH, HEIGHT, design.base(), design.baseDark());
        g.fill(0, 0, WIDTH, 4, design.border());
        g.fill(0, HEIGHT - 4, WIDTH, HEIGHT, design.border());
        g.fill(0, 0, 4, HEIGHT, design.border());
        g.fill(WIDTH - 4, 0, WIDTH, HEIGHT, design.border());
        g.outline(5, 5, WIDTH - 10, HEIGHT - 10, 0xFF000000);
        g.outline(10, 10, WIDTH - 20, HEIGHT - 20, design.border());

        drawDiamond(g, WIDTH / 2, HEIGHT / 2, 26, design.emblemDark());
        drawDiamond(g, WIDTH / 2, HEIGHT / 2, 22, design.emblem());
        drawDiamond(g, WIDTH / 2 - 6, HEIGHT / 2 - 6, 5, 0x60FFFFFF);

        g.pose().popMatrix();
    }

    // A diamond made of one-pixel rows, `radius` pixels from the centre to each point.
    private static void drawDiamond(GuiGraphicsExtractor g, int centerX, int centerY, int radius, int color) {
        for (int row = -radius; row <= radius; row++) {
            int halfWidth = radius - Math.abs(row);
            g.fill(centerX - halfWidth, centerY + row, centerX + halfWidth + 1, centerY + row + 1, color);
        }
    }

    private static void drawEnchantedShimmer(GuiGraphicsExtractor g) {
        drawDiagonalShimmer(g, 0, 0, WIDTH, HEIGHT, 20, 0x70);
        g.outline(0, 0, WIDTH, HEIGHT, 0xFFB070FF);
    }

    // A purple band that sweeps diagonally across a rectangle, from the top-left corner to the bottom-right,
    // then rests for a moment before the next sweep. Used on Enchanted cards and binder pockets.
    public static void drawDiagonalShimmer(GuiGraphicsExtractor g, int x, int y, int width, int height, int halfWidth, int maxAlpha) {
        long time = System.currentTimeMillis() % 2400L;
        if (time > 1600L) {
            return;
        }
        float progress = time / 1600.0F;
        // The band slants like "/". Its centre is wherever column + row equals `diagonal`, which grows over the sweep.
        int diagonal = -halfWidth + (int) (progress * (width + height + halfWidth * 2));
        int step = 2;
        for (int row = 0; row < height; row += step) {
            int bottom = Math.min(row + step, height);
            for (int offset = -halfWidth; offset <= halfWidth; offset += step) {
                int column = diagonal - row + offset;
                int left = Math.max(column, 0);
                int right = Math.min(column + step, width);
                if (left >= right) {
                    continue;
                }
                int alpha = (int) (maxAlpha * (1.0F - Math.abs(offset) / (float) (halfWidth + 1)));
                g.fill(x + left, y + row, x + right, y + bottom, (alpha << 24) | 0xB070FF);
            }
        }
    }
}
