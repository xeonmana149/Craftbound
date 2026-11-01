package craftbound.client.card;

import craftbound.card.CardDefinition;
import craftbound.card.Cards;
import craftbound.card.Keyword;
import craftbound.component.ModComponents;
import craftbound.item.CardItem;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

public class CardInspectScreen extends Screen {
    private static final int GAP = 16;
    private static final int PANEL_WIDTH = 130;
    // How long a token card takes to slide out from behind the main card, in seconds.
    private static final float SLIDE_SECONDS = 0.2F;

    private final ItemStack card;
    // The token cards each keyword creates, e.g. Drop -> Arrow.
    private final Map<Keyword, List<ItemStack>> created = new EnumMap<>(Keyword.class);
    // How far each keyword's cards have slid out: 0 = hidden behind the main card, 1 = fully out.
    private final Map<Keyword, Float> slide = new EnumMap<>(Keyword.class);
    private Keyword pinned;
    private long lastFrameNanos = System.nanoTime();

    // Remembered from the last frame, for hover and click checks.
    private List<CardRenderer.HoverRegion> mainRegions = List.of();
    private final List<int[]> visibleCardRects = new ArrayList<>();
    private Keyword hoveredCreatedFrom;

    public CardInspectScreen(ItemStack card) {
        super(card.getHoverName());
        // A copy, so the screen keeps showing the card even if the stack in your hand changes.
        this.card = card.copy();
        Identifier id = card.get(ModComponents.CARD_ID);
        CardDefinition definition = id == null ? null : Cards.get(id);
        if (definition != null) {
            for (CardDefinition.Related related : definition.related()) {
                this.created.computeIfAbsent(related.keyword(), k -> new ArrayList<>()).add(CardItem.of(related.card()));
                this.slide.put(related.keyword(), 0.0F);
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        long now = System.nanoTime();
        float seconds = Math.min((now - this.lastFrameNanos) / 1_000_000_000.0F, 0.1F);
        this.lastFrameNanos = now;

        // The main card is always centred, twice as big when it fits.
        int scale = CardRenderer.HEIGHT * 2 + 20 <= this.height && CardRenderer.WIDTH * 2 + 20 <= this.width ? 2 : 1;
        int mainWidth = CardRenderer.WIDTH * scale;
        int mainHeight = CardRenderer.HEIGHT * scale;
        int mainX = (this.width - mainWidth) / 2;
        int mainY = (this.height - mainHeight) / 2;

        // A keyword's cards come out while you hover that keyword, while you hover the card itself, or while it's pinned.
        Keyword hovered = keywordAt(mouseX, mouseY);
        for (Map.Entry<Keyword, Float> entry : this.slide.entrySet()) {
            Keyword keyword = entry.getKey();
            boolean out = keyword == this.pinned || keyword == hovered || keyword == this.hoveredCreatedFrom;
            float step = seconds / SLIDE_SECONDS;
            entry.setValue(out ? Math.min(1.0F, entry.getValue() + step) : Math.max(0.0F, entry.getValue() - step));
        }

        // Cards that slide out go to the right of the main card, or to the left if there's no room.
        int outX = mainX + mainWidth + GAP;
        if (outX + CardRenderer.WIDTH > this.width) {
            outX = mainX - GAP - CardRenderer.WIDTH;
        }
        int startX = mainX + (mainWidth - CardRenderer.WIDTH) / 2;
        int startY = mainY + (mainHeight - CardRenderer.HEIGHT) / 2;

        // Drawn before the main card, so they start hidden behind it.
        List<CardRenderer.HoverRegion> createdRegions = new ArrayList<>();
        this.visibleCardRects.clear();
        this.hoveredCreatedFrom = null;
        int slot = 0;
        for (Map.Entry<Keyword, List<ItemStack>> entry : this.created.entrySet()) {
            float progress = this.slide.get(entry.getKey());
            for (ItemStack createdCard : entry.getValue()) {
                int targetY = mainY + slot * (CardRenderer.HEIGHT + GAP + 12) + 12;
                slot++;
                if (progress <= 0.0F) {
                    continue;
                }
                float eased = 1.0F - (1.0F - progress) * (1.0F - progress) * (1.0F - progress);
                int cardX = Math.round(startX + (outX - startX) * eased);
                int cardY = Math.round(startY + (targetY - startY) * eased);
                List<CardRenderer.HoverRegion> regions = CardRenderer.draw(g, this.font, createdCard, cardX, cardY, 1);
                if (progress >= 1.0F) {
                    g.text(this.font, Component.translatable("card_ui.craftbound.creates"), cardX, cardY - 11, 0xFFFFFFFF);
                    createdRegions.addAll(regions);
                    this.visibleCardRects.add(new int[]{cardX, cardY, CardRenderer.WIDTH, CardRenderer.HEIGHT});
                    if (inside(mouseX, mouseY, cardX, cardY, CardRenderer.WIDTH, CardRenderer.HEIGHT)) {
                        this.hoveredCreatedFrom = entry.getKey();
                    }
                }
            }
        }

        this.mainRegions = CardRenderer.draw(g, this.font, this.card, mainX, mainY, scale);
        this.visibleCardRects.add(new int[]{mainX, mainY, mainWidth, mainHeight});

        // Keywords on the main card explain themselves in a fixed panel to the left, so they never cover a token
        // sliding out on the right. A pinned keyword keeps its panel up. Everything else gets a normal tooltip.
        CardRenderer.HoverRegion panel = null;
        List<CardRenderer.HoverRegion> allRegions = new ArrayList<>(this.mainRegions);
        allRegions.addAll(createdRegions);
        for (CardRenderer.HoverRegion region : allRegions) {
            if (region.contains(mouseX, mouseY)) {
                if (region.keyword() != null && this.mainRegions.contains(region)) {
                    panel = region;
                } else {
                    g.setComponentTooltipForNextFrame(this.font, region.lines(), mouseX, mouseY);
                }
                break;
            }
        }
        if (panel == null && this.pinned != null) {
            for (CardRenderer.HoverRegion region : this.mainRegions) {
                if (region.keyword() == this.pinned) {
                    panel = region;
                    break;
                }
            }
        }
        if (panel != null) {
            drawKeywordPanel(g, panel, mainX);
        }
    }

    // A tooltip-style box beside the card's left edge, level with the keyword.
    private void drawKeywordPanel(GuiGraphicsExtractor g, CardRenderer.HoverRegion region, int mainX) {
        int padding = 5;
        int right = mainX - GAP;
        int width = Math.min(PANEL_WIDTH, right - 4);
        if (width < 40) {
            return;
        }
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (Component line : region.lines()) {
            lines.addAll(this.font.split(line, width - padding * 2));
        }
        int height = lines.size() * this.font.lineHeight + padding * 2;
        int left = right - width;
        int top = Math.max(4, Math.min(region.y() - padding, this.height - height - 4));

        g.fill(left, top, right, top + height, 0xF0100010);
        g.outline(left, top, width, height, 0xFF5000A0);
        int textY = top + padding;
        for (FormattedCharSequence line : lines) {
            g.text(this.font, line, left + padding, textY, 0xFFFFFFFF);
            textY += this.font.lineHeight;
        }
    }

    // Clicking a keyword pins (or unpins) the card it creates. Clicking off the cards closes the screen.
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        Keyword keyword = keywordAt(event.x(), event.y());
        if (keyword != null && this.created.containsKey(keyword)) {
            this.pinned = this.pinned == keyword ? null : keyword;
            return true;
        }
        for (int[] rect : this.visibleCardRects) {
            if (inside(event.x(), event.y(), rect[0], rect[1], rect[2], rect[3])) {
                return true;
            }
        }
        this.onClose();
        return true;
    }

    private Keyword keywordAt(double mouseX, double mouseY) {
        for (CardRenderer.HoverRegion region : this.mainRegions) {
            if (region.keyword() != null && region.contains(mouseX, mouseY)) {
                return region.keyword();
            }
        }
        return null;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    // Looking at a card shouldn't pause the game.
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
