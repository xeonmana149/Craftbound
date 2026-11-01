package craftbound.client.deck;

import craftbound.binder.BinderContents;
import craftbound.card.CardClass;
import craftbound.card.CardDefinition;
import craftbound.card.CardRarity;
import craftbound.card.Cards;
import craftbound.card.DeckRules;
import craftbound.client.Tooltips;
import craftbound.client.card.CardRenderer;
import craftbound.component.ModComponents;
import craftbound.deck.DeckBuilding;
import craftbound.deck.DeckContents;
import craftbound.item.CardItem;
import craftbound.item.ModItems;
import craftbound.network.DeckClassPayload;
import craftbound.network.DeckEditPayload;
import craftbound.network.DeckSleevePayload;
import craftbound.sleeve.Sleeves;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

// Building a deck, nearly full screen.
// - A new deck first asks you to pick a class; after that only that class's cards and Neutral cards fit.
// - Left: full-size cards from the binders you're carrying, with tabs (class / Neutral / Card Backs),
//   an XP cost filter and a search bar. Clicking a card moves a real copy from a binder into the deck.
// - Right: the deck list with a 0/30 bar. Click a row to put the card back; hover it to see the card.
// The server does all the moving and checks the deck rules; this screen only asks.
public class DeckCaseScreen extends Screen {
    private enum Tab { CLASS, NEUTRAL, BACKS }

    private static final int MARGIN = 8;
    private static final int PADDING = 8;
    private static final int LIST_WIDTH = 130;
    private static final int ROW_HEIGHT = 12;
    private static final int COLUMNS = 4;
    private static final int ROWS = 2;
    private static final int CARD_GAP = 8;
    private static final int LABEL_HEIGHT = 11;
    // Cost filter buttons: All, 0 to 6, and 7+.
    private static final int ALL_COSTS = -1;
    private static final int HIGH_COST = 7;

    private static final int WOOD = 0xFF5A2E1E;
    private static final int WOOD_DARK = 0xFF3A1C12;
    private static final int PAGE = 0xFFF2E3C2;
    private static final int TEXT_DARK = 0xFF3E231D;
    private static final int GOLD = 0xFFFFD27A;
    private static final int XP_GREEN = 0xFF7DFF5A;

    private final InteractionHand hand;
    private Tab tab = Tab.CLASS;
    private int costFilter = ALL_COSTS;
    // Only show cards of this rarity, or every rarity when null.
    private CardRarity rarityFilter = null;
    private int page = 0;
    private int scroll = 0;
    private EditBox search;

    // A card being pressed or dragged, and the card zoomed in by right-click (null when none).
    private Identifier pressedCard;
    private boolean pressedEnchanted;
    private boolean pressedFromDeck;
    // Whether the pressed card has a copy that can actually move.
    private boolean pressedCanMove;
    private double pressX;
    private double pressY;
    private boolean dragging;
    private Identifier zoomed;
    private boolean zoomedEnchanted;

    // Layout, worked out from the screen size.
    private int panelX, panelY, panelWidth, panelHeight;
    private int leftX, leftWidth, listX;
    private int tabsY, filterY, gridY, gridHeight, footerY;

    // Where things were drawn last frame, for clicks.
    private record Spot(int x, int y, int width, int height, Identifier id) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private final List<Spot> cardSpots = new ArrayList<>();
    private final List<Spot> sleeveSpots = new ArrayList<>();
    private final List<Spot> rowSpots = new ArrayList<>();
    private final List<int[]> tabButtons = new ArrayList<>();
    private final List<int[]> costButtons = new ArrayList<>();
    private final List<int[]> rarityButtons = new ArrayList<>();
    private final List<int[]> classChoices = new ArrayList<>();
    private int[] listArea;
    private int[] previousButton;
    private int[] nextButton;
    private int[] doneButton;
    private int[] changeClassButton;

    public DeckCaseScreen(InteractionHand hand) {
        super(Component.translatable("item.craftbound.deck_case"));
        this.hand = hand;
    }

    private void layout() {
        this.panelX = MARGIN;
        this.panelY = MARGIN;
        this.panelWidth = this.width - MARGIN * 2;
        this.panelHeight = this.height - MARGIN * 2;
        this.listX = this.panelX + this.panelWidth - PADDING - LIST_WIDTH;
        this.leftX = this.panelX + PADDING;
        this.leftWidth = this.listX - PADDING - this.leftX;
        this.tabsY = this.panelY + 6;
        this.filterY = this.tabsY + 20;
        this.gridY = this.filterY + 20;
        this.footerY = this.panelY + this.panelHeight - PADDING - 18;
        this.gridHeight = this.footerY - 6 - this.gridY;
    }

    @Override
    protected void init() {
        layout();
        String previous = this.search == null ? "" : this.search.getValue();
        int searchWidth = Math.min(120, this.leftWidth / 3);
        this.search = new EditBox(this.font, this.leftX + this.leftWidth - searchWidth, this.tabsY, searchWidth, 16,
                Component.translatable("deck_ui.craftbound.search"));
        this.search.setHint(Component.translatable("deck_ui.craftbound.search").withStyle(ChatFormatting.DARK_GRAY));
        this.search.setMaxLength(32);
        this.search.setValue(previous);
        this.search.setResponder(text -> this.page = 0);
        addRenderableWidget(this.search);
    }

    private ItemStack deckCase() {
        return this.minecraft.player == null ? ItemStack.EMPTY : this.minecraft.player.getItemInHand(this.hand);
    }

    private DeckContents deck() {
        return deckCase().getOrDefault(ModComponents.DECK_CONTENTS, DeckContents.EMPTY);
    }

    private Identifier currentSleeve() {
        Identifier sleeve = deckCase().get(ModComponents.SLEEVE_ID);
        return sleeve == null ? Sleeves.CLASSIC : sleeve;
    }

    // How many copies of each card are in all the binders you're carrying: [normal, Enchanted].
    private Map<Identifier, int[]> available() {
        Map<Identifier, int[]> totals = new HashMap<>();
        if (this.minecraft.player == null) {
            return totals;
        }
        Inventory inventory = this.minecraft.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.is(ModItems.BINDER)) {
                continue;
            }
            BinderContents contents = stack.getOrDefault(ModComponents.BINDER_CONTENTS, BinderContents.EMPTY);
            for (Identifier id : Cards.collectible()) {
                int normal = contents.count(id, false);
                int enchanted = contents.count(id, true);
                if (normal + enchanted > 0) {
                    int[] total = totals.computeIfAbsent(id, k -> new int[2]);
                    total[0] += normal;
                    total[1] += enchanted;
                }
            }
        }
        return totals;
    }

    // Sleeves you can pick: Classic always, the one on the case, and any in your inventory.
    private boolean ownsSleeve(Identifier sleeve) {
        if (sleeve.equals(Sleeves.CLASSIC) || sleeve.equals(currentSleeve()) || this.minecraft.player == null) {
            return true;
        }
        Inventory inventory = this.minecraft.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(ModItems.SLEEVE) && sleeve.equals(stack.get(ModComponents.SLEEVE_ID))) {
                return true;
            }
        }
        return false;
    }

    // Cards shown on the left: in your binders or already in the deck, on the current tab, matching the filters.
    private List<Identifier> shownCards(Map<Identifier, int[]> available, DeckContents deck) {
        CardClass tabClass = this.tab == Tab.NEUTRAL ? CardClass.NEUTRAL : deck.deckClass();
        String query = this.search.getValue().trim().toLowerCase(Locale.ROOT);
        List<Identifier> shown = new ArrayList<>();
        for (Identifier id : Cards.collectible()) {
            CardDefinition card = Cards.get(id);
            if (!available.containsKey(id) && deck.copies(id) == 0) continue;
            if (card.cardClass() != tabClass) continue;
            if (this.costFilter == HIGH_COST && card.cost() < HIGH_COST) continue;
            if (this.costFilter != ALL_COSTS && this.costFilter != HIGH_COST && card.cost() != this.costFilter) continue;
            if (this.rarityFilter != null && card.rarity() != this.rarityFilter) continue;
            if (!query.isEmpty() && !Cards.name(id).getString().toLowerCase(Locale.ROOT).contains(query)) continue;
            shown.add(id);
        }
        shown.sort(Comparator.<Identifier>comparingInt(id -> Cards.get(id).cost())
                .thenComparing(id -> Cards.name(id).getString()));
        return shown;
    }

    // The scale that fits a 4 x 2 grid of cards (plus a label under each) into the left side.
    private float cardScale() {
        float byWidth = (this.leftWidth - (COLUMNS - 1) * CARD_GAP) / (float) (COLUMNS * CardRenderer.WIDTH);
        float byHeight = (this.gridHeight - (ROWS - 1) * CARD_GAP - ROWS * LABEL_HEIGHT) / (float) (ROWS * CardRenderer.HEIGHT);
        return Math.min(1.0F, Math.min(byWidth, byHeight));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        if (!deckCase().is(ModItems.DECK_CASE)) {
            this.onClose();
            return;
        }
        layout();
        DeckContents deck = deck();
        boolean pickingClass = deck.deckClass() == CardClass.NEUTRAL;
        this.search.visible = !pickingClass && this.tab != Tab.BACKS;

        g.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + this.panelHeight, WOOD);
        g.outline(this.panelX, this.panelY, this.panelWidth, this.panelHeight, WOOD_DARK);
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        this.cardSpots.clear();
        this.sleeveSpots.clear();
        this.rowSpots.clear();
        this.tabButtons.clear();
        this.costButtons.clear();
        this.rarityButtons.clear();
        this.classChoices.clear();
        this.previousButton = null;
        this.nextButton = null;
        this.changeClassButton = null;

        if (pickingClass) {
            drawClassPicker(g, mouseX, mouseY);
            this.doneButton = drawButton(g, Component.translatable("pack_ui.craftbound.done"),
                    this.listX + LIST_WIDTH - 50, this.footerY, 50, mouseX, mouseY);
            return;
        }

        List<Component> tooltip = null;
        drawTabs(g, deck, mouseX, mouseY);
        if (this.tab == Tab.BACKS) {
            tooltip = drawCardBacks(g, mouseX, mouseY);
        } else {
            drawCostFilter(g, mouseX, mouseY);
            tooltip = drawCollection(g, deck, mouseX, mouseY);
        }
        Identifier hoveredRow = drawDeckList(g, deck, mouseX, mouseY);

        // Hovering a deck row shows that card full size beside the list, like Hearthstone.
        if (hoveredRow != null) {
            float previewScale = Math.min(1.0F, (this.gridHeight + 20) / (float) CardRenderer.HEIGHT);
            int previewWidth = Math.round(CardRenderer.WIDTH * previewScale);
            int previewHeight = Math.round(CardRenderer.HEIGHT * previewScale);
            int previewX = this.listX - PADDING - previewWidth;
            int previewY = Math.max(this.panelY + 4, Math.min(mouseY - previewHeight / 2, this.panelY + this.panelHeight - previewHeight - 4));
            CardRenderer.draw(g, this.font, CardItem.of(hoveredRow, deck.cards().count(hoveredRow, true) > 0), previewX, previewY, previewScale);
            tooltip = rowTooltip(hoveredRow, deck);
        }
        if (this.dragging) {
            drawDrag(g, mouseX, mouseY);
        } else if (this.zoomed != null) {
            drawZoom(g);
        } else if (tooltip != null) {
            Tooltips.show(g, this.font, tooltip, mouseX, mouseY);
        }
    }

    // A new deck: pick one of the real classes. Neutral isn't a choice; Neutral cards go in every deck.
    private void drawClassPicker(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int centerX = this.panelX + this.panelWidth / 2;
        g.centeredText(this.font, Component.translatable("deck_ui.craftbound.pick_class_title"), centerX, this.panelY + 30, GOLD);
        g.centeredText(this.font, Component.translatable("deck_ui.craftbound.pick_class_hint"), centerX, this.panelY + 44, 0xFFE8D6B0);
        List<CardClass> classes = new ArrayList<>();
        for (CardClass cardClass : CardClass.values()) {
            if (cardClass != CardClass.NEUTRAL) classes.add(cardClass);
        }
        int boxWidth = 120;
        int boxHeight = 70;
        int gap = 16;
        int startX = centerX - (classes.size() * boxWidth + (classes.size() - 1) * gap) / 2;
        int y = this.panelY + this.panelHeight / 2 - boxHeight / 2;
        for (int i = 0; i < classes.size(); i++) {
            CardClass cardClass = classes.get(i);
            int x = startX + i * (boxWidth + gap);
            boolean hover = mouseX >= x && mouseX < x + boxWidth && mouseY >= y && mouseY < y + boxHeight;
            g.fill(x, y, x + boxWidth, y + boxHeight, hover ? 0xFF8A5A3A : 0xFF6B4423);
            g.outline(x, y, boxWidth, boxHeight, hover ? GOLD : WOOD_DARK);
            g.centeredText(this.font, cardClass.displayName(), x + boxWidth / 2, y + 10, GOLD);
            int lineY = y + 28;
            for (var line : this.font.split(Component.translatable("deck_ui.craftbound.class_blurb."
                    + cardClass.name().toLowerCase(Locale.ROOT)), boxWidth - 12)) {
                g.text(this.font, line, x + boxWidth / 2 - this.font.width(line) / 2, lineY, 0xFFE8D6B0);
                lineY += this.font.lineHeight;
            }
            this.classChoices.add(new int[]{x, y, boxWidth, boxHeight, cardClass.ordinal()});
        }
    }

    private void drawTabs(GuiGraphicsExtractor g, DeckContents deck, int mouseX, int mouseY) {
        Component[] labels = {
                deck.deckClass().displayName(),
                CardClass.NEUTRAL.displayName(),
                Component.translatable("deck_ui.craftbound.card_backs")
        };
        int x = this.leftX;
        for (int i = 0; i < labels.length; i++) {
            int width = this.font.width(labels[i]) + 16;
            boolean selected = this.tab.ordinal() == i;
            boolean hover = mouseX >= x && mouseX < x + width && mouseY >= this.tabsY && mouseY < this.tabsY + 16;
            g.fill(x, this.tabsY, x + width, this.tabsY + 16, selected ? PAGE : hover ? 0xFF8A5A3A : 0xFF6B4423);
            g.outline(x, this.tabsY, width, 16, WOOD_DARK);
            g.centeredText(this.font, labels[i], x + width / 2, this.tabsY + 4, selected ? TEXT_DARK : 0xFFFFFFFF);
            this.tabButtons.add(new int[]{x, this.tabsY, width, 16, i});
            x += width + 4;
        }
    }

    private void drawCostFilter(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = this.leftX;
        for (int cost = ALL_COSTS; cost <= HIGH_COST; cost++) {
            String label = cost == ALL_COSTS ? Component.translatable("deck_ui.craftbound.all").getString()
                    : cost == HIGH_COST ? "7+" : String.valueOf(cost);
            int width = Math.max(16, this.font.width(label) + 8);
            boolean selected = this.costFilter == cost;
            boolean hover = mouseX >= x && mouseX < x + width && mouseY >= this.filterY && mouseY < this.filterY + 14;
            g.fill(x, this.filterY, x + width, this.filterY + 14, selected ? 0xFF17A84F : hover ? 0xFF2E6B40 : 0xFF1E4A2C);
            g.outline(x, this.filterY, width, 14, WOOD_DARK);
            g.centeredText(this.font, label, x + width / 2, this.filterY + 3, 0xFFFFFFFF);
            this.costButtons.add(new int[]{x, this.filterY, width, 14, cost});
            x += width + 3;
        }

        // Rarity buttons after a small gap, each in its rarity's colour. Click again to show every rarity.
        x += 10;
        for (CardRarity rarity : CardRarity.values()) {
            Component label = rarity.displayName();
            int width = this.font.width(label) + 8;
            boolean selected = this.rarityFilter == rarity;
            boolean hover = mouseX >= x && mouseX < x + width && mouseY >= this.filterY && mouseY < this.filterY + 14;
            g.fill(x, this.filterY, x + width, this.filterY + 14, selected ? 0xFF000000 | rarity.rgb() : hover ? 0xFF4A3A30 : 0xFF2E241E);
            g.outline(x, this.filterY, width, 14, selected ? 0xFFFFFFFF : WOOD_DARK);
            g.centeredText(this.font, label, x + width / 2, this.filterY + 3, selected ? 0xFFFFFFFF : 0xFF000000 | rarity.rgb());
            this.rarityButtons.add(new int[]{x, this.filterY, width, 14, rarity.ordinal()});
            x += width + 3;
        }
    }

    // The grid of full-size cards. Returns a tooltip to show, if any.
    private List<Component> drawCollection(GuiGraphicsExtractor g, DeckContents deck, int mouseX, int mouseY) {
        Map<Identifier, int[]> available = available();
        List<Identifier> shown = shownCards(available, deck);
        int perPage = COLUMNS * ROWS;
        int pageCount = Math.max(1, (shown.size() + perPage - 1) / perPage);
        this.page = Math.max(0, Math.min(this.page, pageCount - 1));

        float scale = cardScale();
        int cardWidth = Math.round(CardRenderer.WIDTH * scale);
        int cardHeight = Math.round(CardRenderer.HEIGHT * scale);
        // Cards fill in from the left, like reading a page.
        int startX = this.leftX;

        if (shown.isEmpty()) {
            g.centeredText(this.font, Component.translatable("deck_ui.craftbound.no_cards"),
                    this.leftX + this.leftWidth / 2, this.gridY + this.gridHeight / 2, 0xFFE8D6B0);
        }
        List<Component> tooltip = null;
        for (int i = 0; i < perPage && this.page * perPage + i < shown.size(); i++) {
            Identifier id = shown.get(this.page * perPage + i);
            int x = startX + (i % COLUMNS) * (cardWidth + CARD_GAP);
            int y = this.gridY + (i / COLUMNS) * (cardHeight + LABEL_HEIGHT + CARD_GAP);
            int[] counts = available.getOrDefault(id, new int[2]);
            // Show the Enchanted version if that's all you have left.
            boolean showEnchanted = counts[0] == 0 && counts[1] > 0;
            List<CardRenderer.HoverRegion> regions = CardRenderer.draw(g, this.font, CardItem.of(id, showEnchanted), x, y, scale);
            String reason = counts[0] + counts[1] == 0 ? "deck_ui.craftbound.none_left" : DeckBuilding.whyNot(deck, id);
            if (reason != null) {
                g.fill(x, y, x + cardWidth, y + cardHeight, 0x99000000);
            }
            drawCardLabel(g, id, counts, deck, x, y + cardHeight + 2, cardWidth);

            Spot spot = new Spot(x, y, cardWidth, cardHeight, id);
            this.cardSpots.add(spot);
            if (spot.contains(mouseX, mouseY)) {
                for (CardRenderer.HoverRegion region : regions) {
                    if (region.contains(mouseX, mouseY)) {
                        tooltip = region.lines();
                    }
                }
                if (tooltip == null && reason != null) {
                    tooltip = List.of(Component.translatable(reason).withStyle(ChatFormatting.RED));
                }
            }
        }

        // Page arrows, page number and a hint along the bottom.
        g.text(this.font, Component.translatable("deck_ui.craftbound.hint_add"), this.leftX, this.footerY + 5, 0xFFE8D6B0);
        int pagerX = this.leftX + this.leftWidth - 70;
        g.centeredText(this.font, (this.page + 1) + " / " + pageCount, pagerX + 35, this.footerY + 5, 0xFFE8D6B0);
        this.previousButton = this.page > 0 ? drawButton(g, Component.literal("<"), pagerX, this.footerY, 18, mouseX, mouseY) : null;
        this.nextButton = this.page < pageCount - 1 ? drawButton(g, Component.literal(">"), pagerX + 52, this.footerY, 18, mouseX, mouseY) : null;
        return tooltip;
    }

    // Under each card: how many are in your binders (and how many Enchanted), and how many are in the deck.
    private void drawCardLabel(GuiGraphicsExtractor g, Identifier id, int[] counts, DeckContents deck, int x, int y, int width) {
        String owned = "x" + (counts[0] + counts[1]);
        g.text(this.font, owned, x, y, 0xFFFFFFFF);
        if (counts[1] > 0) {
            g.text(this.font, "✦" + counts[1], x + this.font.width(owned) + 4, y, 0xFFB070FF);
        }
        if (deck.copies(id) > 0) {
            String inDeck = deck.copies(id) + "/" + DeckRules.maxCopies(Cards.get(id).rarity());
            g.text(this.font, inDeck, x + width - this.font.width(inDeck), y, GOLD);
        }
    }

    // The Card Backs tab: every sleeve design. The ones you own can be put on the case.
    private List<Component> drawCardBacks(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        float scale = cardScale();
        int cardWidth = Math.round(CardRenderer.WIDTH * scale);
        int cardHeight = Math.round(CardRenderer.HEIGHT * scale);
        List<Identifier> sleeves = new ArrayList<>(Sleeves.ids());
        int startX = this.leftX;
        List<Component> tooltip = null;
        for (int i = 0; i < sleeves.size() && i < COLUMNS * ROWS; i++) {
            Identifier sleeve = sleeves.get(i);
            int x = startX + (i % COLUMNS) * (cardWidth + CARD_GAP);
            int y = this.filterY + (i / COLUMNS) * (cardHeight + LABEL_HEIGHT + 4 + CARD_GAP);
            CardRenderer.drawBack(g, Sleeves.get(sleeve), x, y, scale);
            boolean owned = ownsSleeve(sleeve);
            boolean equipped = sleeve.equals(currentSleeve());
            if (!owned) {
                g.fill(x, y, x + cardWidth, y + cardHeight, 0x99000000);
            }
            if (equipped) {
                g.outline(x - 2, y - 2, cardWidth + 4, cardHeight + 4, GOLD);
                g.outline(x - 3, y - 3, cardWidth + 6, cardHeight + 6, GOLD);
            }
            Component status = Component.translatable(equipped ? "deck_ui.craftbound.equipped"
                    : owned ? "deck_ui.craftbound.click_to_use" : "deck_ui.craftbound.not_owned");
            // Below the gold outline, so the name never sits on top of it.
            g.centeredText(this.font, Sleeves.name(sleeve), x + cardWidth / 2, y + cardHeight + 6, 0xFFFFFFFF);
            Spot spot = new Spot(x, y, cardWidth, cardHeight, sleeve);
            this.sleeveSpots.add(spot);
            if (spot.contains(mouseX, mouseY)) {
                tooltip = List.of(Sleeves.name(sleeve), status.copy().withStyle(owned ? ChatFormatting.GRAY : ChatFormatting.RED));
            }
        }
        return tooltip;
    }

    // The deck list on the right, with the class name, a 0/30 bar and the Done button. Returns the hovered row.
    private Identifier drawDeckList(GuiGraphicsExtractor g, DeckContents deck, int mouseX, int mouseY) {
        g.text(this.font, Component.translatable("deck_ui.craftbound.deck_title", deck.deckClass().displayName()),
                this.listX, this.tabsY + 4, GOLD);

        // 0/30 progress bar.
        int barY = this.filterY;
        float filled = deck.size() / (float) DeckRules.DECK_SIZE;
        g.fill(this.listX, barY, this.listX + LIST_WIDTH, barY + 14, 0xFF1E1410);
        g.fill(this.listX + 1, barY + 1, this.listX + 1 + Math.round((LIST_WIDTH - 2) * filled), barY + 13,
                deck.size() == DeckRules.DECK_SIZE ? 0xFF3FAE2A : 0xFF17A84F);
        g.centeredText(this.font, Component.translatable("deck_ui.craftbound.cards_count", deck.size(), DeckRules.DECK_SIZE),
                this.listX + LIST_WIDTH / 2, barY + 3, 0xFFFFFFFF);

        int listTop = this.gridY;
        int listHeight = this.gridHeight;
        this.listArea = new int[]{this.listX, listTop, LIST_WIDTH, listHeight};
        g.fill(this.listX, listTop, this.listX + LIST_WIDTH, listTop + listHeight, PAGE);

        List<Identifier> inDeck = new ArrayList<>();
        for (Identifier id : Cards.collectible()) {
            if (deck.copies(id) > 0) inDeck.add(id);
        }
        inDeck.sort(Comparator.<Identifier>comparingInt(id -> Cards.get(id).cost())
                .thenComparing(id -> Cards.name(id).getString()));
        int visibleRows = (listHeight - 4) / ROW_HEIGHT;
        this.scroll = Math.max(0, Math.min(this.scroll, inDeck.size() - visibleRows));
        if (inDeck.isEmpty()) {
            int lineY = listTop + 4;
            for (var line : this.font.split(Component.translatable("deck_ui.craftbound.empty"), LIST_WIDTH - 8)) {
                g.text(this.font, line, this.listX + 4, lineY, 0xFF8A7458, false);
                lineY += this.font.lineHeight;
            }
        }
        Identifier hovered = null;
        for (int r = 0; r < visibleRows && this.scroll + r < inDeck.size(); r++) {
            Identifier id = inDeck.get(this.scroll + r);
            int y = listTop + 2 + r * ROW_HEIGHT;
            Spot spot = new Spot(this.listX, y, LIST_WIDTH, ROW_HEIGHT, id);
            this.rowSpots.add(spot);
            if (spot.contains(mouseX, mouseY)) {
                hovered = id;
                g.fill(this.listX, y, this.listX + LIST_WIDTH, y + ROW_HEIGHT, 0x30000000);
            }
            drawDeckRow(g, id, deck, this.listX + 2, y);
        }
        if (inDeck.size() > visibleRows) {
            int barHeight = Math.max(10, listHeight * visibleRows / inDeck.size());
            int scrollY = listTop + (listHeight - barHeight) * this.scroll / Math.max(1, inDeck.size() - visibleRows);
            g.fill(this.listX + LIST_WIDTH - 2, scrollY, this.listX + LIST_WIDTH, scrollY + barHeight, 0xFF8A5A3A);
        }

        // Done, and Change class while the deck is still empty.
        this.doneButton = drawButton(g, Component.translatable("pack_ui.craftbound.done"),
                this.listX + LIST_WIDTH - 50, this.footerY, 50, mouseX, mouseY);
        if (deck.size() == 0) {
            this.changeClassButton = drawButton(g, Component.translatable("deck_ui.craftbound.change_class"),
                    this.listX, this.footerY, LIST_WIDTH - 54, mouseX, mouseY);
        }
        return hovered;
    }

    // One line of the deck list: cost, name in its rarity colour, and how many (purple star if any are Enchanted).
    private void drawDeckRow(GuiGraphicsExtractor g, Identifier id, DeckContents deck, int x, int y) {
        CardDefinition card = Cards.get(id);
        g.fill(x, y + 1, x + 12, y + 11, 0xFF17A84F);
        g.centeredText(this.font, String.valueOf(card.cost()), x + 6, y + 2, 0xFFFFFFFF);
        // Long names shrink so they never run into the count.
        CardRenderer.fitText(g, this.font, CardItem.of(id).getHoverName(), x + 15, y + 2, LIST_WIDTH - 45, 0xFFFFFFFF, false);
        String count = "x" + deck.copies(id);
        int countX = x + LIST_WIDTH - 8 - this.font.width(count);
        g.text(this.font, count, countX, y + 2, TEXT_DARK, false);
        if (deck.cards().count(id, true) > 0) {
            g.text(this.font, "✦", countX - 9, y + 2, 0xFFB070FF, false);
        }
    }

    private List<Component> rowTooltip(Identifier id, DeckContents deck) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("binder_ui.craftbound.owned", deck.cards().count(id, false)).withStyle(ChatFormatting.GRAY));
        if (deck.cards().count(id, true) > 0) {
            lines.add(Component.translatable("binder_ui.craftbound.owned_enchanted", deck.cards().count(id, true))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        lines.add(Component.translatable("deck_ui.craftbound.hint_remove").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    private int[] drawButton(GuiGraphicsExtractor g, Component label, int x, int y, int width, int mouseX, int mouseY) {
        int height = 18;
        boolean hover = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        g.fill(x, y, x + width, y + height, hover ? 0xFF8A5A3A : 0xFF6B4423);
        g.outline(x, y, width, height, WOOD_DARK);
        g.centeredText(this.font, label, x + width / 2, y + 5, 0xFFFFFFFF);
        return new int[]{x, y, width, height};
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // While a card is zoomed in, any click just closes it.
        if (this.zoomed != null) {
            this.zoomed = null;
            return true;
        }
        // Let the search bar take the click first, so you can type in it.
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        this.search.setFocused(false);
        double mouseX = event.x();
        double mouseY = event.y();
        boolean mainHand = this.hand == InteractionHand.MAIN_HAND;
        DeckContents deck = deck();

        if (inside(this.doneButton, mouseX, mouseY)) {
            this.onClose();
            return true;
        }
        for (int[] choice : this.classChoices) {
            if (inside(choice, mouseX, mouseY)) {
                ClientPlayNetworking.send(new DeckClassPayload(mainHand, choice[4]));
                this.tab = Tab.CLASS;
                return true;
            }
        }
        if (inside(this.changeClassButton, mouseX, mouseY)) {
            // An empty deck can go back to the class picker.
            ClientPlayNetworking.send(new DeckClassPayload(mainHand, CardClass.NEUTRAL.ordinal()));
            return true;
        }
        for (int[] tabButton : this.tabButtons) {
            if (inside(tabButton, mouseX, mouseY)) {
                this.tab = Tab.values()[tabButton[4]];
                this.page = 0;
                return true;
            }
        }
        for (int[] rarityButton : this.rarityButtons) {
            if (inside(rarityButton, mouseX, mouseY)) {
                CardRarity clicked = CardRarity.values()[rarityButton[4]];
                this.rarityFilter = this.rarityFilter == clicked ? null : clicked;
                this.page = 0;
                return true;
            }
        }
        for (int[] costButton : this.costButtons) {
            if (inside(costButton, mouseX, mouseY)) {
                this.costFilter = costButton[4];
                this.page = 0;
                return true;
            }
        }
        if (inside(this.previousButton, mouseX, mouseY)) {
            this.page--;
            return true;
        }
        if (inside(this.nextButton, mouseX, mouseY)) {
            this.page++;
            return true;
        }
        for (Spot sleeve : this.sleeveSpots) {
            if (sleeve.contains(mouseX, mouseY)) {
                if (ownsSleeve(sleeve.id())) {
                    ClientPlayNetworking.send(new DeckSleevePayload(mainHand, sleeve.id()));
                }
                return true;
            }
        }
        Map<Identifier, int[]> available = available();
        for (Spot card : this.cardSpots) {
            if (!card.contains(mouseX, mouseY)) {
                continue;
            }
            int[] counts = available.getOrDefault(card.id(), new int[2]);
            if (event.button() == 1) {
                // Right-click only zooms in.
                zoom(card.id(), counts[0] == 0 && counts[1] > 0);
                return true;
            }
            // Left: click to add it, or drag it onto the deck list.
            // Ctrl takes an Enchanted copy. Otherwise a normal one, or Enchanted if that's all you have.
            boolean enchanted = event.hasControlDown() || counts[0] == 0;
            startPress(card.id(), enchanted, false, mouseX, mouseY);
            this.pressedCanMove = counts[enchanted ? 1 : 0] > 0;
            return true;
        }
        for (Spot row : this.rowSpots) {
            if (!row.contains(mouseX, mouseY)) {
                continue;
            }
            if (event.button() == 1) {
                // Right-click only zooms in.
                zoom(row.id(), deck.cards().count(row.id(), true) > 0);
                return true;
            }
            // Left: click to put it back in a binder, or drag it out of the list.
            boolean enchanted = event.hasControlDown() || deck.cards().count(row.id(), false) == 0;
            startPress(row.id(), enchanted, true, mouseX, mouseY);
            this.pressedCanMove = deck.cards().count(row.id(), enchanted) > 0;
            return true;
        }
        return true;
    }

    // --- Dragging ---------------------------------------------------------------------------------------
    // Left-press a card, then either let go (a click) or drag it. A click moves it straight away; cards dropped
    // on the deck list are added, and deck rows dragged out of the list are put back in a binder.

    private void startPress(Identifier card, boolean enchanted, boolean fromDeck, double mouseX, double mouseY) {
        this.pressedCard = card;
        this.pressedEnchanted = enchanted;
        this.pressedFromDeck = fromDeck;
        this.pressX = mouseX;
        this.pressY = mouseY;
        this.dragging = false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.pressedCard != null) {
            double distance = Math.abs(event.x() - this.pressX) + Math.abs(event.y() - this.pressY);
            if (distance > 4) {
                this.dragging = true;
            }
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.pressedCard == null) {
            return super.mouseReleased(event);
        }
        boolean overList = inside(this.listArea, event.x(), event.y());
        boolean mainHand = this.hand == InteractionHand.MAIN_HAND;
        // A click (no movement) always moves the card; a drag only counts if it ends in the right place.
        if (this.pressedCanMove && this.pressedFromDeck && (!this.dragging || !overList)) {
            ClientPlayNetworking.send(new DeckEditPayload(mainHand, this.pressedCard, this.pressedEnchanted, false));
        } else if (this.pressedCanMove && !this.pressedFromDeck && (!this.dragging || overList)) {
            ClientPlayNetworking.send(new DeckEditPayload(mainHand, this.pressedCard, this.pressedEnchanted, true));
        }
        this.pressedCard = null;
        this.dragging = false;
        return true;
    }

    // The card under the cursor while dragging, and a green outline on the deck list when it would be added.
    private void drawDrag(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (this.pressedCard == null || !this.dragging) {
            return;
        }
        if (!this.pressedFromDeck) {
            int[] list = this.listArea;
            g.outline(list[0] - 2, list[1] - 2, list[2] + 4, list[3] + 4, 0xFF7DFF5A);
            g.outline(list[0] - 3, list[1] - 3, list[2] + 6, list[3] + 6, 0xFF7DFF5A);
        }
        float scale = 0.5F;
        int width = Math.round(CardRenderer.WIDTH * scale);
        int height = Math.round(CardRenderer.HEIGHT * scale);
        CardRenderer.draw(g, this.font, CardItem.of(this.pressedCard, this.pressedEnchanted),
                mouseX - width / 2, mouseY - height / 2, scale);
    }

    // --- Zoom -------------------------------------------------------------------------------------------
    // Right-clicking a card shows it big in the middle of the screen, with its keywords explained on the left and
    // any cards it creates on the right (the same sides as the inspect screen). Click anywhere or Esc to close.

    private void zoom(Identifier card, boolean enchanted) {
        this.zoomed = card;
        this.zoomedEnchanted = enchanted;
    }

    private void drawZoom(GuiGraphicsExtractor g) {
        if (this.zoomed == null) {
            return;
        }
        CardDefinition card = Cards.get(this.zoomed);
        g.fill(0, 0, this.width, this.height, 0xC8000000);
        float scale = Math.min(2.0F, (this.height - 30) / (float) CardRenderer.HEIGHT);
        int cardWidth = Math.round(CardRenderer.WIDTH * scale);
        int cardHeight = Math.round(CardRenderer.HEIGHT * scale);
        int cardX = (this.width - cardWidth) / 2;
        int cardY = (this.height - cardHeight) / 2;
        CardRenderer.draw(g, this.font, CardItem.of(this.zoomed, this.zoomedEnchanted), cardX, cardY, scale);

        // Keyword rules to the left, one box each.
        int boxWidth = Math.min(150, cardX - 20);
        int boxX = cardX - 12 - boxWidth;
        int boxY = cardY + 10;
        if (boxWidth > 60) {
            for (var keyword : card.keywords()) {
                List<FormattedCharSequence> lines = new ArrayList<>();
                lines.addAll(this.font.split(keyword.displayName().copy().withStyle(ChatFormatting.GOLD), boxWidth - 10));
                lines.addAll(this.font.split(keyword.description(), boxWidth - 10));
                int boxHeight = lines.size() * this.font.lineHeight + 10;
                g.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xF0100010);
                g.outline(boxX, boxY, boxWidth, boxHeight, 0xFF5000A0);
                int textY = boxY + 5;
                for (var line : lines) {
                    g.text(this.font, line, boxX + 5, textY, 0xFFFFFFFF);
                    textY += this.font.lineHeight;
                }
                boxY += boxHeight + 6;
            }
        }

        // Cards it creates to the right.
        float relatedScale = Math.min(1.0F, scale * 0.6F);
        int relatedWidth = Math.round(CardRenderer.WIDTH * relatedScale);
        int relatedHeight = Math.round(CardRenderer.HEIGHT * relatedScale);
        int relatedX = cardX + cardWidth + 12;
        int relatedY = cardY + 10;
        if (relatedX + relatedWidth < this.width - 4) {
            for (var related : card.related()) {
                if (relatedY + relatedHeight > this.height) {
                    break;
                }
                g.text(this.font, Component.translatable("card_ui.craftbound.creates"), relatedX, relatedY - 10, 0xFFFFFFFF);
                CardRenderer.draw(g, this.font, CardItem.of(related.card()), relatedX, relatedY, relatedScale);
                relatedY += relatedHeight + 16;
            }
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.zoomed != null && event.isEscape()) {
            this.zoomed = null;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inside(this.listArea, mouseX, mouseY)) {
            this.scroll -= (int) Math.signum(scrollY);
            return true;
        }
        // Scrolling over the cards turns the page.
        if (mouseX >= this.leftX && mouseX < this.leftX + this.leftWidth && mouseY >= this.gridY && mouseY < this.footerY) {
            this.page -= (int) Math.signum(scrollY);
            return true;
        }
        return false;
    }

    private static boolean inside(int[] rect, double mouseX, double mouseY) {
        return rect != null && mouseX >= rect[0] && mouseX < rect[0] + rect[2] && mouseY >= rect[1] && mouseY < rect[1] + rect[3];
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
