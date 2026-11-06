package craftbound.client.match;

import craftbound.card.CardCatalog;
import craftbound.card.CardClass;
import craftbound.card.CardDefinition;
import craftbound.card.Cards;
import craftbound.card.Keyword;
import craftbound.client.Tooltips;
import craftbound.client.card.CardInspectScreen;
import craftbound.client.card.CardRenderer;
import craftbound.client.match.MatchState.Mob;
import craftbound.game.HeroPowers;
import craftbound.game.TrialTraining;
import craftbound.item.CardItem;
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

/** Full-card hover presentation shared by the duel and four-seat match boards. Anchors use screen coordinates. */
final class MatchCardHover {
    private Font font;
    private int width, height;
    private Object snapshot;
    private record CardPreview(Identifier id, boolean enchanted, TrialTraining training,
                               int x, int y, int width, float scale,
                               List<FormattedCharSequence> notes, boolean playable, int brew) {
        CardPreview(Identifier id, boolean enchanted, TrialTraining training, int x, int y, int width, float scale,
                    List<FormattedCharSequence> notes, boolean playable) {
            this(id, enchanted, training, x, y, width, scale, notes, playable, 0);
        }
    }
    private CardPreview associatedPreview;
    private boolean handSource;
    private int handPointerX, handPointerY;
    private long handPointerAt;
    private static final long TOKEN_TRANSFER_NANOS = 300_000_000L;
    private Object associatedState;
    private CardCatalog.State associatedCatalogue;
    private Identifier associatedSource;
    private List<ItemStack> associatedCards;
    private int associatedWidth, associatedHeight, createdPage;
    private List<CardRenderer.HoverRegion> createdHoverRegions = List.of();
    private int[] createsPane, createsPrevious, createsNext;
    // Pinned with right-click: keyword notes expand to their full rule when hovered.
    private boolean pinned;

    void pinned(boolean pinned) { this.pinned = pinned; }

    void prepare(Font font, int width, int height, Object snapshot) {
        this.font = font;
        this.width = width;
        this.height = height;
        this.snapshot = snapshot;
    }

    boolean valid(Object snapshot, int width, int height) {
        return this.associatedPreview != null && this.createsPane != null && this.associatedState == snapshot
                && this.associatedCatalogue == CardCatalog.current()
                && this.associatedWidth == width && this.associatedHeight == height;
    }

    boolean drawRetained(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (valid(snapshot, width, height) && overAssociatedPreview(mouseX, mouseY)) {
            drawCardPreview(g, this.associatedPreview, mouseX, mouseY);
            return true;
        }
        clear();
        return false;
    }

    boolean scroll(double x, double y, double delta) {
        if (!inside(createsPane, x, y)) return false;
        if (delta != 0) page(delta < 0 ? 1 : -1);
        return true;
    }

    boolean click(double x, double y, boolean left) {
        if (!inside(createsPane, x, y)) return false;
        if (left) page(inside(createsPrevious, x, y) ? -1 : inside(createsNext, x, y) ? 1 : 0);
        return true;
    }

    private void page(int step) {
        createdPage = Math.clamp(createdPage + step, 0, associatedCards.size() - 1);
    }

    private float smallScale() {
        return Math.min(0.8F, height * 0.42F / CardRenderer.HEIGHT);
    }

    void tool(GuiGraphicsExtractor g, MatchState.Tool tool, int[] anchor, boolean near) {
        if (tool == null || Cards.get(tool.id) == null) return;
        float scale = smallScale();
        int w = Math.round(CardRenderer.WIDTH * scale);
        int[] at = besideHero(anchor, near, w, Math.round(CardRenderer.HEIGHT * scale));
        CardRenderer.drawLiveTool(g, font, CardItem.of(tool.id, tool.enchanted),
                at[0], at[1], scale, tool.attack, tool.uses);
        drawToolDetails(g, tool, at[0], at[1], w);
    }

    void power(GuiGraphicsExtractor g, HeroPowers.Power power, CardClass cardClass, int cost,
               boolean used, int[] anchor, boolean near) {
        if (power == HeroPowers.Power.NONE) return;
        float scale = smallScale();
        int[] at = besideHero(anchor, near, Math.round(CardRenderer.WIDTH * scale), Math.round(CardRenderer.HEIGHT * scale));
        CardRenderer.drawPower(g, font, power.displayName(), power.description(),
                new ItemStack(BuiltInRegistries.ITEM.getValue(power.icon())), cardClass, cost, used, at[0], at[1], scale);
    }

    void hand(GuiGraphicsExtractor g, Identifier id, boolean enchanted, TrialTraining training, int brew,
              int centerX, int leftBound, boolean playable, int mouseX, int mouseY) {
        clear();
        handSource = true;
        handPointerX = mouseX;
        handPointerY = mouseY;
        handPointerAt = System.nanoTime();
        float scale = Math.min(1.05F, height * 0.5F / CardRenderer.HEIGHT);
        int w = Math.round(CardRenderer.WIDTH * scale);
        int x = Math.max(leftBound, Math.min(width - w - 90, centerX - w / 2));
        int y = height - Math.round(CardRenderer.HEIGHT * scale) - 4;
        drawCardPreview(g, new CardPreview(id, enchanted, training, x, y, w, scale, List.of(), playable, brew), mouseX, mouseY);
    }

    void unit(GuiGraphicsExtractor g, Mob mob, boolean ownersTurn, int[] anchor, int mouseX, int mouseY) {
        if (mob == null || mob.card() == null) return;
        besideCard(g, mob.id, mob.enchanted, mob.training, statuses(mob, ownersTurn), anchor, mouseX, mouseY);
    }

    void besideCard(GuiGraphicsExtractor g, Identifier id, boolean enchanted, TrialTraining training,
                    List<Component> status, int[] anchor, int mouseX, int mouseY) {
        float scale = smallScale();
        int w = Math.round(CardRenderer.WIDTH * scale);
        int h = Math.round(CardRenderer.HEIGHT * scale);
        List<FormattedCharSequence> notes = new ArrayList<>();
        for (Component line : status) notes.addAll(font.split(line, Math.max(w, 110) - 8));
        int noteHeight = notes.isEmpty() ? 0 : notes.size() * font.lineHeight + 11;
        int x = anchor[0] + anchor[2] + 12;
        if (x + w > width - 4) x = Math.max(4, anchor[0] - 12 - w);
        int y = Math.max(4, Math.min(height - h - noteHeight - 4, anchor[1] + anchor[3] / 2 - h / 2));
        drawCardPreview(g, new CardPreview(id, enchanted, training, x, y, Math.max(w, 110), scale, notes, false), mouseX, mouseY);
    }

    void history(GuiGraphicsExtractor g, Identifier id, int x, int y, int boxWidth, float scale,
                 List<FormattedCharSequence> lines, int mouseX, int mouseY) {
        drawCardPreview(g, new CardPreview(id, false, TrialTraining.NONE, x, y, boxWidth, scale, lines, false), mouseX, mouseY);
    }

    void clear() {
        handSource = false;
        this.associatedPreview = null;
        this.associatedState = null;
        this.createsPane = this.createsPrevious = this.createsNext = null;
        this.createdHoverRegions = List.of();
    }

    boolean overAssociatedPreview(double x, double y) {
        if (inside(this.createsPane, x, y)) return true;
        CardPreview card = this.associatedPreview;
        if (card == null || this.createsPane == null) return false;
        if (handSource) return crossingToToken(handPointerX, handPointerY, x, y,
                createsPane, System.nanoTime() - handPointerAt);
        int h = Math.round(CardRenderer.HEIGHT * card.scale());
        // Include the short bridge between the two cards, but not unrelated board rows.
        int left = Math.min(card.x(), this.createsPane[0]);
        int right = Math.max(card.x() + card.width(), this.createsPane[0] + this.createsPane[2]);
        int top = Math.max(card.y(), this.createsPane[1]);
        int bottom = Math.min(card.y() + h, this.createsPane[1] + this.createsPane[3]);
        return (x >= card.x() && x < card.x() + card.width() && y >= card.y() && y < card.y() + h)
                || (x >= left && x < right && y >= top && y < bottom);
    }

    // The enlarged hand card is display-only. Briefly bridge a direct movement from its small
    // hand slot to its token, so the token can still be read and paged across the intervening gap.
    static boolean crossingToToken(double startX, double startY, double x, double y, int[] pane, long elapsed) {
        if (elapsed > TOKEN_TRANSFER_NANOS || pane == null || inside(pane,startX,startY)) return false;
        double dx = x - startX, dy = y - startY;
        double gapX = Math.clamp(x,pane[0],pane[0]+pane[2]-1) - x;
        double gapY = Math.clamp(y,pane[1],pane[1]+pane[3]-1) - y;
        if (gapX != 0 && gapX * dx <= 0 || gapY != 0 && gapY * dy <= 0) return false;
        // Follow the pointer's direction to the first possible intersection with the token.
        double t = Math.max(gapX == 0 ? 0 : gapX/dx, gapY == 0 ? 0 : gapY/dy);
        return inside(pane,x+t*dx,y+t*dy);
    }

    private void drawCardPreview(GuiGraphicsExtractor g, CardPreview preview, int mouseX, int mouseY) {
        if (Cards.get(preview.id()) == null) { clear(); return; }
        cacheCreatedCards(preview.id());
        int w = Math.round(CardRenderer.WIDTH * preview.scale());
        int h = Math.round(CardRenderer.HEIGHT * preview.scale());
        if (!this.associatedCards.isEmpty()) {
            // Move the pair inward at the screen edge; the generated card always stays on the right.
            int tokenWidth = Math.round(CardRenderer.WIDTH * createdCardScale(preview));
            int pairWidth = Math.max(preview.width(), w + 8 + tokenWidth);
            int x = Math.max(4, Math.min(preview.x(), this.width - pairWidth - 4));
            preview = new CardPreview(preview.id(), preview.enchanted(), preview.training(),
                    x, preview.y(), preview.width(), preview.scale(), preview.notes(), preview.playable(), preview.brew());
        }
        if (preview.playable()) {
            g.fill(preview.x() - 3, preview.y() - 3, preview.x() + w + 3, preview.y() + h + 3, 0x807DFF5A);
            g.outline(preview.x() - 2, preview.y() - 2, w + 4, h + 4, MatchArt.GLOW);
        }
        CardRenderer.drawTrial(g, this.font, preview.id(), preview.enchanted(), preview.training(), preview.brew(),
                preview.x(), preview.y(), preview.scale());
        if (!preview.notes().isEmpty())
            drawNote(g, preview.notes(), preview.x(), preview.y() + h + 4, preview.width());
        drawCreates(g, preview);
        drawKeywordNotes(g, preview.id(), preview.training(), preview.x(), preview.y(), preview.width(), mouseX, mouseY);
        for (var region : this.createdHoverRegions) {
            if (region.keyword() != null && region.contains(mouseX, mouseY)) {
                Tooltips.show(g, this.font, region.lines(), mouseX, mouseY);
                break;
            }
        }
    }

    private void cacheCreatedCards(Identifier id) {
        if (this.associatedCatalogue == CardCatalog.current() && id.equals(this.associatedSource)
                && this.associatedCards != null) return;
        this.associatedCatalogue = CardCatalog.current();
        this.associatedSource = id;
        this.createdPage = 0;
        var outcomes = new ArrayList<ItemStack>();
        var seen = new java.util.HashSet<Identifier>();
        for (var association : CardInspectScreen.associations(id)) {
            if (Cards.get(association.id()) != null && seen.add(association.id()))
                outcomes.add(CardItem.of(association.id()));
        }
        this.associatedCards = List.copyOf(outcomes);
    }

    private static float createdCardScale(CardPreview preview) {
        return Math.min(.48F, preview.scale() * .6F);
    }

    private void drawCreates(GuiGraphicsExtractor g, CardPreview preview) {
        this.createsPane = this.createsPrevious = this.createsNext = null;
        this.createdHoverRegions = List.of();
        if (this.associatedCards.isEmpty()) return;
        float scale = createdCardScale(preview);
        int cardWidth = Math.round(CardRenderer.WIDTH * scale);
        int cardHeight = Math.round(CardRenderer.HEIGHT * scale);
        boolean pages = this.associatedCards.size() > 1;
        int paneHeight = 12 + cardHeight + (pages ? 14 : 0);
        int x = preview.x() + Math.round(CardRenderer.WIDTH * preview.scale()) + 8;
        int y = Math.max(4, Math.min(preview.y(), this.height - paneHeight - 4));
        this.createdPage = Math.max(0, Math.min(this.createdPage, this.associatedCards.size() - 1));
        // Transparent bounds retain hover and paging without a second panel covering the board.
        this.createsPane = new int[]{x, y, cardWidth, paneHeight};
        this.associatedPreview = preview;
        this.associatedState = this.snapshot;
        this.associatedWidth = this.width;
        this.associatedHeight = this.height;
        Component title = Component.translatable("card_ui.craftbound.creates");
        g.text(this.font, title, x + (cardWidth - this.font.width(title)) / 2, y, MatchArt.GOLD_LIGHT);
        this.createdHoverRegions = CardRenderer.draw(g, this.font, this.associatedCards.get(this.createdPage),
                x, y + 12, scale);
        if (pages) {
            int footerY = y + cardHeight + 16;
            this.createsPrevious = new int[]{x, footerY - 2, 12, 12};
            this.createsNext = new int[]{x + cardWidth - 12, footerY - 2, 12, 12};
            g.text(this.font, Component.literal("<"), x + 2, footerY,
                    this.createdPage > 0 ? MatchArt.TEXT : 0xFF777777);
            g.text(this.font, Component.literal(">"), x + cardWidth - 8, footerY,
                    this.createdPage + 1 < this.associatedCards.size() ? MatchArt.TEXT : 0xFF777777);
            String footer = (this.createdPage + 1) + "/" + this.associatedCards.size();
            MatchArt.outlined(g, this.font, footer, x + cardWidth / 2F, footerY, MatchArt.TEXT, .75F, true);
        }
    }

    // All the card's keywords are readable on hover, without having to aim at tiny words on its art.
    private void drawKeywordNotes(GuiGraphicsExtractor g, Identifier id, TrialTraining training, int cardX, int cardY, int cardWidth,
                                  int mouseX, int mouseY) {
        CardDefinition card = Cards.get(id);
        if (card == null || (card.keywords().isEmpty() && training.keyword() == null)) {
            return;
        }
        int gap = 8;
        int leftSpace = cardX - gap - 4;
        int rightStart = this.createsPane == null ? cardX + cardWidth + gap
                : Math.max(cardX + cardWidth + gap, this.createsPane[0] + this.createsPane[2] + gap);
        int rightSpace = this.width - 4 - rightStart;
        boolean onRight = this.createsPane != null ? leftSpace < 90 && rightSpace > leftSpace
                : rightSpace >= 140 || rightSpace >= leftSpace;
        int available = Math.max(1, onRight ? rightSpace : leftSpace);
        int boxWidth = Math.min(150, Math.max(90, available));
        // Enum order keeps the notes stable even if a card stores its keywords in an unordered set.
        List<Keyword> shown = new ArrayList<>();
        for (Keyword keyword : Keyword.values()) {
            if (keyword == Keyword.BLOCK) continue;
            if (training.keyword() != keyword && !card.keywords().contains(keyword)) continue;
            shown.add(keyword);
        }
        if (shown.isEmpty()) return;
        // Short lines first. Pinned, the note under the pointer opens to its full rule.
        Keyword expanded = null;
        boolean more = shown.stream().anyMatch(Keyword::hasMore);
        List<List<FormattedCharSequence>> boxes = keywordBoxes(shown, card, null, boxWidth, more);
        int totalHeight = boxesHeight(boxes);
        if (this.pinned) {
            float probeScale = Math.min(1.0F, Math.min(available / (float) boxWidth, (this.height - 10.0F) / totalHeight));
            int px = onRight ? rightStart : cardX - gap - (int) Math.ceil(boxWidth * probeScale);
            int py = Math.max(5, Math.min(cardY, this.height - (int) Math.ceil(totalHeight * probeScale) - 5));
            float lx = (mouseX - px) / probeScale, ly = (mouseY - py) / probeScale;
            int cursor = 0;
            for (int i = 0; i < boxes.size(); i++) {
                int boxHeight = boxes.get(i).size() * this.font.lineHeight + 7;
                if (lx >= 0 && lx < boxWidth && ly >= cursor && ly < cursor + boxHeight) { expanded = shown.get(i); break; }
                cursor += boxHeight + 4;
            }
            if (expanded != null) {
                boxes = keywordBoxes(shown, card, expanded, boxWidth, more);
                totalHeight = boxesHeight(boxes);
            }
        }
        float scale = Math.min(1.0F, Math.min(available / (float) boxWidth, (this.height - 10.0F) / totalHeight));
        int renderedWidth = (int) Math.ceil(boxWidth * scale);
        int renderedHeight = (int) Math.ceil(totalHeight * scale);
        int x = onRight ? rightStart : cardX - gap - renderedWidth;
        int y = Math.max(5, Math.min(cardY, this.height - renderedHeight - 5));
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        int cursor = 0;
        for (List<FormattedCharSequence> lines : boxes) {
            drawNote(g, lines, 0, cursor, boxWidth);
            cursor += lines.size() * this.font.lineHeight + 7 + 4;
        }
        g.pose().popMatrix();
    }

    private List<List<FormattedCharSequence>> keywordBoxes(List<Keyword> keywords, CardDefinition card, Keyword expanded,
                                                           int boxWidth, boolean more) {
        List<List<FormattedCharSequence>> boxes = new ArrayList<>();
        for (Keyword keyword : keywords) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            lines.addAll(this.font.split(keyword.displayName().copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), boxWidth - 8));
            Component text = keyword == expanded ? keyword.description(card) : keyword.shortDescription();
            lines.addAll(this.font.split(text.copy().withStyle(ChatFormatting.GRAY), boxWidth - 8));
            boxes.add(lines);
        }
        if (more) {
            Component hint = Component.literal(this.pinned ? "Hover a note for the full rule." : "Right-click to pin and read more.")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
            boxes.getLast().addAll(this.font.split(hint, boxWidth - 8));
        }
        return boxes;
    }

    private int boxesHeight(List<List<FormattedCharSequence>> boxes) {
        int total = 0;
        for (var lines : boxes) total += lines.size() * this.font.lineHeight + 7 + 4;
        return total - 4;
    }

    // Where a tool or power card goes: above it for you (the board is above your hero), below it for them.
    private int[] besideHero(int[] spot, boolean near, int width, int height) {
        int x = Math.max(4, Math.min(this.width - width - 4, spot[0] + spot[2] / 2 - width / 2));
        int y = near ? spot[1] - height - 10 : spot[1] + spot[3] + 10;
        return new int[]{x, Math.max(4, Math.min(this.height - height - 4, y))};
    }

    // A small dark box of text, for what goes under a previewed card.
    private void drawToolDetails(GuiGraphicsExtractor g, MatchState.Tool tool, int cardX, int cardY, int cardWidth) {
        List<Component> details = new ArrayList<>();
        details.add(Component.literal("Equipped · " + tool.attack + " Attack / " + tool.uses + " Durability").withStyle(ChatFormatting.GOLD));
        if (tool.enchanted) details.add(Component.literal("Enchanted collectible finish").withStyle(ChatFormatting.LIGHT_PURPLE));
        if (!tool.enchantments.isEmpty()) {
            details.add(Component.literal("Applied enchantments").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
            var totals = new java.util.LinkedHashMap<Identifier, int[]>();
            for (var e : tool.enchantments) {
                int[] total = totals.computeIfAbsent(e.card().id(), unused -> new int[3]);
                total[0]++; total[1] += e.attack(); total[2] += e.durability();
            }
            totals.forEach((id, total) -> details.add(Cards.name(id).copy()
                    .append(total[0] > 1 ? " ×" + total[0] : "")
                    .append(": +" + total[1] + " Attack, +" + total[2] + " Durability")));
            totals.forEach((id, total) -> {
                var entry = craftbound.card.CardCatalog.entry(id);
                if (entry == null) return;
                var attached = entry.effects().stream().filter(e -> e.trigger() != craftbound.card.CardCatalog.Trigger.PLAY).toList();
                if (!attached.isEmpty()) details.add(Cards.styledText(craftbound.card.CardCatalog.description(
                        new craftbound.card.CardCatalog.Entry(entry.definition(), entry.name(), "", true,
                                craftbound.card.CardCatalog.Passive.NONE, 0, attached))));
            });
        }
        if (tool.ranged) details.add(Keyword.RANGED.displayName().copy().append(": ").append(Keyword.RANGED.description()));
        int width = Math.min(180, this.width - cardWidth - 24);
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (Component line : details) lines.addAll(this.font.split(line, width - 8));
        float scale = Math.min(1.0F, (this.height - 16.0F) / (lines.size() * this.font.lineHeight + 9));
        int x = cardX + cardWidth + 6;
        if (x + width * scale > this.width - 4) x = cardX - 6 - Math.round(width * scale);
        int y = Math.max(4, Math.min(cardY, this.height - Math.round((lines.size() * this.font.lineHeight + 9) * scale) - 4));
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        drawNote(g, lines, 0, 0, width);
        g.pose().popMatrix();
    }

    private void drawNote(GuiGraphicsExtractor g, List<FormattedCharSequence> lines, int x, int y, int width) {
        int height = lines.size() * this.font.lineHeight + 7;
        g.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF000000);
        g.fill(x, y, x + width, y + height, 0xF014110D);
        g.outline(x, y, width, height, MatchArt.GOLD_DARK);
        int textY = y + 4;
        for (FormattedCharSequence line : lines) {
            g.text(this.font, line, x + 4, textY, MatchArt.TEXT);
            textY += this.font.lineHeight;
        }
    }

    // What's going on with a mob that its card doesn't say: frozen, withered, a totem, invulnerable, not ready.
    private List<Component> statuses(Mob mob, boolean ownersTurn) {
        List<Component> lines = new ArrayList<>();
        if (mob.impaled != null) {
            var thrown = craftbound.card.CardCatalog.entry(mob.impaled);
            lines.add(Component.literal((thrown == null ? "A thrown tool" : thrown.name()) + " is stuck in this mob. It goes back to its thrower when this mob leaves the board.").withStyle(ChatFormatting.AQUA));
        }
        if (mob.usesLeft >= 0) lines.add(Component.literal(mob.usesLeft + (mob.usesLeft == 1 ? " use left" : " uses left") + " before it is destroyed").withStyle(ChatFormatting.GOLD));
        if (mob.attackDebuff > 0) lines.add(Component.literal("Weakened: −" + mob.attackDebuff + " Attack").withStyle(ChatFormatting.GRAY));
        for (var potion : mob.potions) {
            var entry = CardCatalog.entry(potion.id());
            if (entry == null) continue;
            var brew = craftbound.card.Brew.fromBits(potion.brew());
            var line = Component.literal("Under ").append(Cards.name(potion.id()).copy().withStyle(ChatFormatting.BOLD));
            if (!brew.none()) line.append(Component.literal(" (brewed: " + String.join(", ", brew.upgrades().stream().map(u -> u.label).toList()) + ")")
                    .withStyle(ChatFormatting.GREEN));
            lines.add(line.withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (mob.ignoreShield && !mob.card().keywords().contains(Keyword.IGNORE_SHIELD)) lines.add(Keyword.IGNORE_SHIELD.inline().copy().append(": ").append(Keyword.IGNORE_SHIELD.shortDescription()));
        if (mob.shield && !mob.card().keywords().contains(Keyword.SHIELD)) lines.add(Keyword.SHIELD.inline().copy().append(": ").append(Keyword.SHIELD.shortDescription()));
        if (mob.sprint && !mob.card().keywords().contains(Keyword.SPRINT)) lines.add(Keyword.SPRINT.inline().copy().append(": ").append(Keyword.SPRINT.shortDescription()));
        if (mob.ranged && !mob.card().keywords().contains(Keyword.RANGED)) lines.add(Keyword.RANGED.inline().copy().append(": ").append(Keyword.RANGED.shortDescription()));
        if (mob.frozen) lines.add(Component.translatable("match_ui.craftbound.status.frozen").withStyle(ChatFormatting.AQUA));
        if (mob.withered) lines.add(Keyword.WITHER.displayName().copy().append(": ")
                .append(Keyword.WITHER.shortDescription().copy().withStyle(style -> style.withBold(false))).withStyle(ChatFormatting.GRAY));
        if (mob.totem) lines.add(Component.translatable("match_ui.craftbound.status.totem").withStyle(ChatFormatting.GOLD));
        if (mob.invulnerable) lines.add(Component.translatable("match_ui.craftbound.status.invulnerable").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        if (!mob.faceReady && mob.card().keywords().contains(Keyword.SPRINT))
            lines.add(Component.translatable("match_ui.craftbound.status.sprint_board_only").withStyle(ChatFormatting.GRAY));
        if (ownersTurn && !mob.ready && !mob.block) {
            lines.add(Component.translatable("match_ui.craftbound.status.not_ready").withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    private static boolean inside(int[] rect, double mouseX, double mouseY) {
        return rect != null && mouseX >= rect[0] && mouseX < rect[0] + rect[2] && mouseY >= rect[1] && mouseY < rect[1] + rect[3];
    }

}
