package craftbound.client.card;

import craftbound.client.Tooltips;
import craftbound.card.CardCatalog;
import craftbound.card.CardDefinition;
import craftbound.card.Cards;
import craftbound.card.Keyword;
import craftbound.component.ModComponents;
import craftbound.item.CardItem;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

public class CardInspectScreen extends Screen {
    private static final int GAP = 16;
    private static final int PANEL_WIDTH = 130;
    private static final float SLIDE_SECONDS = 0.2F;

    private final craftbound.game.TrialTraining training;
    private final ItemStack card;
    private final Map<Keyword, List<ItemStack>> created = new EnumMap<>(Keyword.class);
    // End-turn and other effects without a visible rules keyword must remain discoverable.
    private final List<ItemStack> alwaysCreated = new ArrayList<>();
    private final Map<Keyword, Float> slide = new EnumMap<>(Keyword.class);
    private final Map<Keyword, Long> hoverUntil = new EnumMap<>(Keyword.class);
    private Keyword pinned;
    private long lastFrameNanos = craftbound.client.AnimationClock.nanos();
    private final InspectAnimation opening = new InspectAnimation();

    private List<CardRenderer.HoverRegion> mainRegions = List.of();
    private final List<int[]> visibleCardRects = new ArrayList<>();
    private Keyword hoveredCreatedFrom;
    private Keyword lastHovered;
    private int relatedPage, relatedPages = 1;
    private int[] relatedPane, previousPageRect, nextPageRect;
    private final Screen parent;
    private boolean cursorInitialized;

    @Override protected void init() {
        super.init();
        if(parent==null && !cursorInitialized) InspectionCursor.restore(this.minecraft);
        cursorInitialized=true;
    }

    @Override public void removed() {
        if(parent==null) InspectionCursor.remember(this.minecraft);
        super.removed();
    }

    public CardInspectScreen(ItemStack card) {
        this(card, null);
    }

    public CardInspectScreen(ItemStack card, Screen parent) {
        this(card, parent, craftbound.game.TrialTraining.NONE);
    }

    public CardInspectScreen(ItemStack card, Screen parent, craftbound.game.TrialTraining training) {
        super(card.getHoverName());
        this.training = training;
        this.parent = parent;
        this.card = card.copy();
        Identifier id = card.get(ModComponents.CARD_ID);
        Component text = id == null ? Component.empty() : CardRenderer.trialText(id, training);
        for (Association association : associations(id, inspectableKeywords(text))) {
            ItemStack preview = CardItem.of(association.id());
            if (association.keyword() == null) this.alwaysCreated.add(preview);
            else {
                this.created.computeIfAbsent(association.keyword(), k -> new ArrayList<>()).add(preview);
                this.slide.put(association.keyword(), 0F);
            }
        }
    }

    public record Association(Identifier id, Keyword keyword) {}

    /** Pure catalogue/text lookup; usable by headless presentation checks. */
    public static List<Association> associations(Identifier id) {
        return associations(id, inspectableKeywords(id == null || Cards.get(id) == null ? Component.empty() : Cards.text(id)));
    }

    private static Set<Keyword> inspectableKeywords(Component description) {
        Set<Keyword> result = EnumSet.noneOf(Keyword.class);
        // Manual descriptions may omit Spawn/Drop even when the definition has that keyword.
        description.visit((style, text) -> {
            Keyword keyword = Keyword.fromInsertion(style.getInsertion());
            if (keyword != null && !text.isBlank()) result.add(keyword);
            return Optional.<Void>empty();
        }, Style.EMPTY);
        return result;
    }

    /** Explicit outcomes only; a random class card or a normal draw has no fixed card to preview. */
    static List<Association> associations(Identifier id, Set<Keyword> inspectable) {
        if (id == null) return List.of();
        var entry = CardCatalog.entry(id);
        if (entry == null) return List.of();
        Set<Association> result = new LinkedHashSet<>();
        Set<Identifier> scripted = new LinkedHashSet<>();
        for (var effect : entry.effects()) {
            List<Identifier> outcomes = switch (effect.action()) {
                case GIVE, SUMMON, TRANSFORM, BAD_OMEN -> effect.card() == null ? List.of() : List.of(effect.card());
                case GIVE_RANDOM -> effect.pool();
                default -> List.of();
            };
            Keyword keyword = effect.action() == CardCatalog.Action.BAD_OMEN ? Keyword.BAD_OMEN
                    : effect.trigger() == CardCatalog.Trigger.PLAY ? Keyword.SPAWN
                    : effect.trigger() == CardCatalog.Trigger.DROP ? Keyword.DROP : null;
            if (keyword == null || !inspectable.contains(keyword)) keyword = null;
            for (Identifier outcome : outcomes) {
                scripted.add(outcome);
                if (available(outcome)) result.add(new Association(outcome, keyword));
            }
        }
        // Retain authored/legacy links that are not represented by a catalogue action.
        for (CardDefinition.Related related : entry.definition().related()) {
            if (!scripted.contains(related.card()) && available(related.card()))
                result.add(new Association(related.card(), inspectable.contains(related.keyword()) ? related.keyword() : null));
        }
        return List.copyOf(result);
    }

    private static boolean available(Identifier id) {
        var entry = id == null ? null : CardCatalog.entry(id);
        return entry != null && !entry.deleted() && Cards.get(id) != null;
    }

    private record Preview(ItemStack card, Keyword source, float progress) {}

    private List<Preview> visiblePreviews() {
        var result = new ArrayList<Preview>();
        Set<Identifier> shown = new LinkedHashSet<>();
        // Put a hovered/pinned keyword first so its outcome is on the first page.
        for (var entry : created.entrySet()) {
            float progress = slide.getOrDefault(entry.getKey(), 0F);
            if (progress <= 0) continue;
            for (ItemStack stack : entry.getValue())
                if (shown.add(stack.get(ModComponents.CARD_ID))) result.add(new Preview(stack, entry.getKey(), progress));
        }
        for (ItemStack stack : alwaysCreated)
            if (shown.add(stack.get(ModComponents.CARD_ID))) result.add(new Preview(stack, null, 1));
        return result;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        long now = craftbound.client.AnimationClock.nanos();
        float seconds = Math.min((now - this.lastFrameNanos) / 1_000_000_000.0F, 0.1F);
        this.lastFrameNanos = now;

        boolean hasRelated = !created.isEmpty() || !alwaysCreated.isEmpty();
        float widthScale = hasRelated ? (width - 24F - GAP) / (CardRenderer.WIDTH * 1.82F)
                : (width - 40F) / CardRenderer.WIDTH;
        float scale = Math.max(.1F, Math.min(1.25F, Math.min(height * .68F / CardRenderer.HEIGHT, widthScale))) * opening.scale();
        int mainWidth = Math.round(CardRenderer.WIDTH * scale);
        int mainHeight = Math.round(CardRenderer.HEIGHT * scale);
        float tokenScale = Math.min(1F, scale * .82F);
        int tokenWidth = Math.round(CardRenderer.WIDTH * tokenScale);
        int tokenHeight = Math.round(CardRenderer.HEIGHT * tokenScale);
        int mainX = (width - mainWidth) / 2;
        if (hasRelated) mainX = Math.min(mainX, width - 12 - tokenWidth - GAP - mainWidth);
        mainX = Math.max(4, mainX);
        int mainY = (height - mainHeight) / 2;

        Keyword hovered = keywordAt(mouseX, mouseY);
        if (hovered != null && hovered != lastHovered && created.containsKey(hovered)) relatedPage = 0;
        lastHovered = hovered;
        for (Map.Entry<Keyword, Float> entry : slide.entrySet()) {
            Keyword keyword = entry.getKey();
            boolean active = keyword == pinned || keyword == hovered || keyword == hoveredCreatedFrom;
            if (active) hoverUntil.put(keyword, now + 400_000_000L);
            boolean out = active || now < hoverUntil.getOrDefault(keyword, 0L);
            float step = seconds / SLIDE_SECONDS;
            entry.setValue(out ? Math.min(1F, entry.getValue() + step) : Math.max(0F, entry.getValue() - step));
        }

        var previews = visiblePreviews();
        int perPage = Math.max(1, Math.min(2, (height - 64 + GAP) / Math.max(1, tokenHeight + GAP)));
        relatedPages = Math.max(1, (previews.size() + perPage - 1) / perPage);
        relatedPage = Math.max(0, Math.min(relatedPage, relatedPages - 1));
        int first = relatedPage * perPage, last = Math.min(previews.size(), first + perPage);
        int visible = last - first;
        int columnHeight = visible * tokenHeight + Math.max(0, visible - 1) * GAP + 32;
        int columnY = Math.max(12, (height - columnHeight) / 2);
        int outX = mainX + mainWidth + GAP;
        int startX = mainX + (mainWidth - tokenWidth) / 2;
        int startY = mainY + (mainHeight - tokenHeight) / 2;

        List<CardRenderer.HoverRegion> createdRegions = new ArrayList<>();
        visibleCardRects.clear();
        hoveredCreatedFrom = null;
        relatedPane = previousPageRect = nextPageRect = null;
        if (visible > 0) {
            relatedPane = new int[]{outX - 3, columnY - 3, tokenWidth + 6, columnHeight + 6};
            g.text(font, Component.translatable("card_ui.craftbound.creates"), outX, columnY, 0xFFFFFFFF);
            for (int index = first; index < last; index++) {
                Preview preview = previews.get(index);
                float progress = preview.progress();
                float eased = 1F - (1F - progress) * (1F - progress) * (1F - progress);
                int targetY = columnY + 14 + (index - first) * (tokenHeight + GAP);
                int cardX = Math.round(startX + (outX - startX) * eased);
                int cardY = Math.round(startY + (targetY - startY) * eased);
                var regions = CardRenderer.draw(g, font, preview.card(), cardX, cardY, tokenScale);
                if (!inside(mouseX, mouseY, mainX, mainY, mainWidth, mainHeight)) {
                    createdRegions.addAll(regions);
                    if (inside(mouseX, mouseY, cardX, cardY, tokenWidth, tokenHeight))
                        hoveredCreatedFrom = preview.source();
                }
                visibleCardRects.add(new int[]{cardX, cardY, tokenWidth, tokenHeight});
            }
            if (relatedPages > 1) {
                int y = columnY + columnHeight - 13;
                previousPageRect = new int[]{outX, y - 2, 16, 14};
                nextPageRect = new int[]{outX + tokenWidth - 16, y - 2, 16, 14};
                g.text(font, Component.literal("<"), outX + 4, y, relatedPage > 0 ? 0xFFFFFFFF : 0xFF777777);
                g.text(font, Component.literal(">"), outX + tokenWidth - 11, y, relatedPage + 1 < relatedPages ? 0xFFFFFFFF : 0xFF777777);
                Component count = Component.literal((relatedPage + 1) + " / " + relatedPages);
                g.text(font, count, outX + (tokenWidth - font.width(count)) / 2, y, 0xFFE8D5AE);
            }
        }

        // Render the main card last so linked cards still slide out from behind it.
        mainRegions = CardRenderer.drawTrial(g, font, card.get(ModComponents.CARD_ID),
                card.has(ModComponents.ENCHANTED), training, mainX, mainY, scale);
        // Only this inspector supports pinning; other card surfaces must not advertise that action.
        mainRegions = mainRegions.stream().map(region -> {
            if (region.keyword() == null || !created.containsKey(region.keyword())) return region;
            var lines = new ArrayList<>(region.lines());
            lines.add(Component.translatable("card_ui.craftbound.pin_hint")
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY, net.minecraft.ChatFormatting.ITALIC));
            return new CardRenderer.HoverRegion(region.x(), region.y(), region.width(), region.height(),
                    List.copyOf(lines), region.keyword());
        }).toList();
        visibleCardRects.add(new int[]{mainX, mainY, mainWidth, mainHeight});

        CardRenderer.HoverRegion panel = null;
        List<CardRenderer.HoverRegion> allRegions = new ArrayList<>(mainRegions);
        allRegions.addAll(createdRegions);
        for (var region : allRegions) {
            if (!region.contains(mouseX, mouseY)) continue;
            if (region.keyword() != null && mainRegions.contains(region)) panel = region;
            else Tooltips.show(g, font, region.lines(), mouseX, mouseY);
            break;
        }
        if (panel == null && pinned != null)
            for (var region : mainRegions) if (region.keyword() == pinned) { panel = region; break; }
        if (panel != null) {
            if (mainX - GAP - 4 < 40) Tooltips.show(g, font, panel.lines(), mouseX, mouseY);
            else drawKeywordPanel(g, panel, mainX, mouseX, mouseY);
        }
    }

    private void drawKeywordPanel(GuiGraphicsExtractor g, CardRenderer.HoverRegion region, int mainX, int mouseX, int mouseY) {
        int padding = 5;
        int right = mainX - GAP;
        int width = Math.min(PANEL_WIDTH, right - 4);
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (Component line : region.lines()) lines.addAll(font.split(line, width - padding * 2));
        int height = lines.size() * font.lineHeight + padding * 2;
        if (height > this.height - 8) {
            Tooltips.show(g, font, region.lines(), mouseX, mouseY);
            return;
        }
        int left = right - width;
        int top = Math.max(4, Math.min(region.y() - padding, this.height - height - 4));
        g.fill(left, top, right, top + height, 0xF0100010);
        g.outline(left, top, width, height, 0xFF5000A0);
        int textY = top + padding;
        for (FormattedCharSequence line : lines) {
            g.text(font, line, left + padding, textY, 0xFFFFFFFF);
            textY += font.lineHeight;
        }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (relatedPages > 1 && vertical != 0) {
            relatedPage = Math.max(0, Math.min(relatedPages - 1, relatedPage + (vertical < 0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (contains(previousPageRect, event.x(), event.y())) { relatedPage = Math.max(0, relatedPage - 1); return true; }
        if (contains(nextPageRect, event.x(), event.y())) { relatedPage = Math.min(relatedPages - 1, relatedPage + 1); return true; }
        Keyword keyword = keywordAt(event.x(), event.y());
        if (keyword != null && created.containsKey(keyword)) {
            pinned = pinned == keyword ? null : keyword;
            relatedPage = 0;
            return true;
        }
        for (int[] rect : visibleCardRects) if (contains(rect, event.x(), event.y())) return true;
        if (contains(relatedPane, event.x(), event.y())) return true;
        onClose();
        return true;
    }

    private Keyword keywordAt(double mouseX, double mouseY) {
        for (var region : mainRegions)
            if (region.keyword() != null && region.contains(mouseX, mouseY)) return region.keyword();
        return null;
    }

    private static boolean contains(int[] rect, double x, double y) {
        return rect != null && inside(x, y, rect[0], rect[1], rect[2], rect[3]);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
