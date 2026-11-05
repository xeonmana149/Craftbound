package craftbound.client.deck;

import com.mojang.blaze3d.platform.InputConstants;
import craftbound.binder.BinderContents;
import craftbound.client.binder.BinderTheme;
import static craftbound.client.binder.BinderTheme.*;
import craftbound.card.CardClass;
import craftbound.card.CardDefinition;
import craftbound.card.CardRarity;
import craftbound.card.Cards;
import craftbound.card.DeckRules;
import craftbound.card.Keyword;
import craftbound.card.MobType;
import craftbound.deck.DeckCardFilter;
import java.util.EnumSet;
import craftbound.client.NewCards;
import craftbound.client.Tooltips;
import craftbound.board.BoardCollection;
import craftbound.board.BoardDesign;
import craftbound.client.match.BoardTrim;
import craftbound.client.card.CardCanvas;
import craftbound.client.card.CardRenderer;
import craftbound.client.card.CardDragMotion;
import craftbound.component.ModComponents;
import craftbound.deck.DeckBuilding;
import craftbound.deck.DeckContents;
import craftbound.item.CardItem;
import craftbound.item.ModItems;
import craftbound.network.DeckClassPayload;
import craftbound.network.DeckEditPayload;
import craftbound.network.DeckRenamePayload;
import craftbound.network.DeckSleevePayload;
import craftbound.sleeve.Sleeves;
import craftbound.sleeve.SleeveCollection;
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
import net.minecraft.world.item.Items;

// Building a deck, nearly full screen.
// - A new deck first asks you to pick a class; after that only that class's cards and Neutral cards fit.
// - Left: full-size cards from the binders you're carrying, with tabs (class / Neutral / Card Backs),
//   XP/rarity/keyword/type filters and search. Deck edits change lists; physical cards stay in binders.
// - Right: My Decks in binder overview, or the selected deck's list with a 0/30 bar.
// The server checks deck rules, ownership and all physical withdrawals.
public class DeckCaseScreen extends Screen {
    private enum Tab { CLASS, NEUTRAL, BACKS, BOARDS }
    private boolean cosmetics() { return tab == Tab.BACKS || tab == Tab.BOARDS; }
    private final java.util.EnumMap<BoardDesign, int[]> boardButtons = new java.util.EnumMap<>(BoardDesign.class);

    private static final int MARGIN = 8;
    private static final int PADDING = 8;
    private int listWidth;
    private static final int ROW_HEIGHT = 12;
    private static final int FILTER_BUTTON_WIDTH = 46, RULES_WIDTH = 30;
    private static final int ROWS = 2;
    private static final int CARD_GAP = 5;
    private static final int LABEL_HEIGHT = 11;
    private static final int CLASS_TAB_HEIGHT = 21, CLASS_TAB_STEP = 21;
    private static final int CURVE_HEIGHT = 33;
    // Cost filter buttons: All, 0 to 6, and 7+.
    private static final int ALL_COSTS = -1;
    private static final int HIGH_COST = 7;

    private static final int XP_GREEN = 0xFF7DFF5A;

    private final InteractionHand hand;
    private final String savedDeckId;
    private final int holderLocation;
    private final craftbound.client.match.TableScreen tableMenu;
    private CardClass browseClass = CardClass.NEUTRAL;
    private boolean confirmDelete;
    private int hubScroll;
    private int[] newDeckButton, fileCardsButton, deleteDeckButton;
    private final List<String> hubDeckIds = new ArrayList<>();
    private final List<int[]> hubDeckSpots = new ArrayList<>();
    private Tab tab = Tab.CLASS;
    private int costFilter = ALL_COSTS;
    // Only show cards of this rarity, or every rarity when null.
    private CardRarity rarityFilter = null;
    private final EnumSet<Keyword> keywordFilters = EnumSet.noneOf(Keyword.class);
    private final EnumSet<MobType> typeFilters = EnumSet.noneOf(MobType.class);
    private final EnumSet<DeckCardFilter.CardType> cardTypeFilters = EnumSet.noneOf(DeckCardFilter.CardType.class);
    private static final Keyword[] FILTER_KEYWORDS = java.util.Arrays.stream(Keyword.values())
            .filter(keyword -> keyword != Keyword.BLOCK).toArray(Keyword[]::new);
    private boolean compactFilters, filtersExpanded;
    private int[] filtersButton;
    private int openFilter; // 0 closed, 1 keywords, 2 mob types, 3 card types
    private int rarityY, advancedFilterY;
    private int[] keywordButton, typeButton, cardTypeButton, resetFiltersButton, normalVersionsButton, enchantedVersionsButton;
    private boolean showNormalVersions = true, showEnchantedVersions = false;
    private int versionFilterY;
    private final List<int[]> filterOptions = new ArrayList<>();
    private int page = 0;
    private int scroll = 0;
    private EditBox search;
    // The deck's name above the list: click it to rename the deck.
    private EditBox nameBox;
    // The name last sent to the server, so it's only sent again when it changes.
    private String savedName;
    // The class the name box last showed a default name for.
    private CardClass nameClass;

    // A card being pressed or dragged, and the card zoomed in by right-click (null when none).
    private Identifier pressedCard;
    private boolean pressedEnchanted;
    private boolean pressedFromDeck;
    // Whether the pressed card has a copy that can actually move.
    private boolean pressedCanMove;
    private double pressX;
    private double pressY;
    private boolean dragging;
    private final CardDragMotion dragMotion = new CardDragMotion();
    private Identifier zoomed;
    private boolean zoomedEnchanted;
    private List<Identifier> zoomRelated = List.of();
    private int zoomRelatedPage, zoomRelatedPages = 1;
    private int[] zoomPreviousPage, zoomNextPage;
    private final craftbound.client.card.InspectAnimation inspectOpening = new craftbound.client.card.InspectAnimation();

    // Layout, worked out from the screen size.
    private int panelX, panelY, panelWidth, panelHeight;
    private int leftX, leftWidth, listX;
    private int tabsY, searchX, searchY, searchWidth, filterY, gridY, gridHeight, curveY, footerY;

    // Where things were drawn last frame, for clicks.
    private record Spot(int x, int y, int width, int height, Identifier id, boolean enchanted) {
        Spot(int x, int y, int width, int height, Identifier id) { this(x, y, width, height, id, false); }
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
    private List<Component> curveTooltip;
    private Component hoveredClassTab;
    private int pageLimit = 1;
    private long lastPaperSound;

    private void playPaperSound(float pitch, float volume) {
        long now = System.nanoTime() / 1_000_000L;
        if (now - lastPaperSound < 95) return;
        minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, pitch, volume));
        lastPaperSound = now;
    }

    private void turnPage(int direction) {
        int next = Math.clamp(page + direction, 0, pageLimit - 1);
        if (next == page) return;
        page = next;
        playPaperSound(direction > 0 ? 1.04F : .92F, .5F);
    }

    private int[] previousButton;
    private int[] nextButton;
    private int[] doneButton;
    private int[] changeClassButton;
    private int[] masteryButton;
    private int[] rulesButton;
    private int[] clearDeckButton;
    private boolean choosingClass;

    public DeckCaseScreen(InteractionHand hand) { this(hand, null); }

    public DeckCaseScreen(InteractionHand hand, String savedDeckId) {
        this(hand, savedDeckId, hand == InteractionHand.MAIN_HAND ? 1 : 0, null);
    }

    public static DeckCaseScreen fromTable(craftbound.client.match.TableScreen table, int slot, String deckId, boolean create) {
        var screen = new DeckCaseScreen(InteractionHand.MAIN_HAND, deckId,
                craftbound.deck.BinderDecks.locationForSlot(slot), table);
        screen.choosingClass = create;
        return screen;
    }

    private DeckCaseScreen(InteractionHand hand, String deckId, int location, craftbound.client.match.TableScreen table) {
        super(Component.literal("Card Collection"));
        this.hand = hand;
        this.savedDeckId = deckId;
        this.holderLocation = location;
        this.tableMenu = table;
    }

    private DeckCaseScreen pageFor(String deckId) {
        return new DeckCaseScreen(hand, deckId, holderLocation, tableMenu);
    }

    private ItemStack holder() {
        return minecraft.player == null ? ItemStack.EMPTY : craftbound.deck.BinderDecks.holder(minecraft.player, holderLocation);
    }

    private boolean commanderEditing() { return tableMenu != null && tableMenu.commanderTable(); }
    private int deckTarget() { return commanderEditing() ? craftbound.game.CommanderMatch.DECK_SIZE : DeckRules.DECK_SIZE; }
    private int copyLimit(Identifier id) { return commanderEditing() ? 1 : DeckRules.maxCopies(Cards.get(id).rarity()); }
    private String cannotAdd(DeckContents deck, Identifier id) {
        if (commanderEditing()) {
            if (deck.copies(id) >= 1) return "Commander allows one copy of each card, including Enchanted.";
            if (deck.size() >= deckTarget()) return "This Commander deck already has 20 cards.";
        }
        return DeckBuilding.whyNot(deck, id);
    }
    private static boolean commanderReady(DeckContents deck) {
        return deck.deckClass() != craftbound.card.CardClass.NEUTRAL
                && deck.size() == craftbound.game.CommanderMatch.DECK_SIZE
                && Cards.collectible().stream().filter(id -> deck.copies(id) == 1 && DeckRules.allowed(id, deck.deckClass())).count() == deck.size()
                && Cards.collectible().stream().anyMatch(id -> deck.copies(id) > 0 && craftbound.game.CommanderMatch.eligible(id));
    }

    private boolean overview() { return holder().is(ModItems.BINDER) && this.savedDeckId == null; }
    private boolean validHolder() { return holder().is(ModItems.BINDER) || holder().is(ModItems.DECK_CASE); }

    private void savedAction(int action, String value, boolean enchanted) {
        ClientPlayNetworking.send(new craftbound.network.BinderDeckPayload(
                this.holderLocation, this.savedDeckId, action, value, enchanted));
    }

    @Override public void onClose() {
        if (this.choosingClass && overview()) { this.choosingClass = false; return; }
        if (tableMenu != null) {
            saveName();
            tableMenu.returnFromBinder(holderLocation - 2, savedDeckId);
        } else if (this.savedDeckId != null && holder().is(ModItems.BINDER)) {
            saveName();
            this.minecraft.gui.setScreen(pageFor(null));
        } else super.onClose();
    }

    private int headingWidth() { return Math.max(1, listWidth - RULES_WIDTH - 4); }

    private void layout() {
        this.panelX = MARGIN;
        this.panelY = MARGIN;
        this.panelWidth = this.width - MARGIN * 2;
        this.panelHeight = this.height - MARGIN * 2;
        this.listWidth = Math.clamp(this.panelWidth / 6, 80, 103);
        this.listX = this.panelX + this.panelWidth - PADDING - listWidth;
        this.leftX = this.panelX + PADDING;
        this.leftWidth = this.listX - PADDING - this.leftX;
        this.tabsY = this.panelY + 6;
        int tabRows = overview() ? tabCell(CardClass.values().length+1) / classTabColumns() + 1 : 1;
        this.filterY = this.tabsY + (overview() ? tabRows * CLASS_TAB_HEIGHT : 17) + 2;
        int costWidth = 0;
        for (int cost = ALL_COSTS; cost <= HIGH_COST; cost++)
            costWidth += Math.max(13, font.width(cost == ALL_COSTS ? Component.translatable("deck_ui.craftbound.all").getString() : cost == HIGH_COST ? "7+" : String.valueOf(cost)) + 6) + 2;
        int rarityWidth = 0;
        for (CardRarity rarity : CardRarity.values()) rarityWidth += font.width(rarity.displayName()) + 8;
        int searchRoom = leftWidth - costWidth - rarityWidth - FILTER_BUTTON_WIDTH - 14;
        compactFilters = searchRoom < 56;
        searchWidth = compactFilters ? leftWidth - FILTER_BUTTON_WIDTH - 4 : searchRoom;
        searchX = leftX + leftWidth - FILTER_BUTTON_WIDTH - 4 - searchWidth;
        searchY = filterY;
        gridY = cosmetics() ? filterY + 2 : filterY + 18;
        int costY = compactFilters ? gridY + 1 : filterY;
        rarityY = compactFilters && costWidth + 6 + rarityWidth > leftWidth ? costY + 15 : costY;
        advancedFilterY = compactFilters ? rarityY + 16 : gridY + 1;
        versionFilterY = advancedFilterY + (leftWidth < 360 ? 15 : 0);
        footerY = panelY + panelHeight - PADDING - 15;
        curveY = cosmetics() ? footerY - 3 : overview() ? footerY - (leftWidth < 365 ? 15 : 0) - 2 : footerY - CURVE_HEIGHT - 3;
        gridHeight = curveY - 5 - gridY;
        if (this.search != null) {
            this.search.setX(searchX);
            this.search.setY(searchY);
            this.search.setWidth(searchWidth);
        }
    }

    @Override
    protected void init() {
        layout();
        String previous = this.search == null ? "" : this.search.getValue();
        this.search = new EditBox(this.font, this.searchX, this.searchY, searchWidth, 14,
                Component.translatable("deck_ui.craftbound.search"));
        this.search.setHint(Component.translatable("deck_ui.craftbound.search").withStyle(ChatFormatting.DARK_GRAY));
        this.search.setMaxLength(32);
        this.search.setValue(previous);
        this.search.setResponder(text -> this.page = 0);
        addRenderableWidget(this.search);

        String previousName = this.nameBox == null ? null : this.nameBox.getValue();
        this.nameBox = new EditBox(this.font, this.listX, this.tabsY + 3, headingWidth(), 10,
                Component.translatable("deck_ui.craftbound.rename_hint"));
        this.nameBox.setBordered(false);
        this.nameBox.setTextColor(GOLD);
        this.nameBox.setMaxLength(DeckRenamePayload.MAX_LENGTH);
        this.nameBox.setValue(previousName != null ? previousName : deckCase().getHoverName().getString());
        if (this.savedName == null) {
            this.savedName = this.nameBox.getValue();
        }
        addRenderableWidget(this.nameBox);
    }

    // Sends the deck's new name if it changed. A blank name goes back to the default ("Farmer Deck").
    private void saveName() {
        if (this.nameBox == null || this.minecraft == null || this.minecraft.player == null || !validHolder() || overview() || deckCase().isEmpty() || this.nameClass != deck().deckClass()) {
            return;
        }
        String value = this.nameBox.getValue().strip();
        if (value.isEmpty()) {
            value = DeckBuilding.defaultName(deck()).getString();
            this.nameBox.setValue(value);
        }
        if (!value.equals(this.savedName)) {
            this.savedName = value;
            if (this.savedDeckId != null) savedAction(craftbound.network.BinderDeckPayload.RENAME, value, false);
            else ClientPlayNetworking.send(new DeckRenamePayload(this.hand == InteractionHand.MAIN_HAND, value));
        }
    }

    @Override
    public void tick() {
        if (this.nameBox != null && !this.nameBox.isFocused()) {
            saveName();
        }
    }

    @Override
    public void removed() {
        saveName();
        super.removed();
    }

    private ItemStack deckCase() {
        if (this.savedDeckId != null && holder().is(ModItems.BINDER)) {
            var entry = craftbound.deck.BinderDecks.get(holder()).find(this.savedDeckId);
            return entry == null ? ItemStack.EMPTY : entry.asStack();
        }
        return holder();
    }

    private DeckContents deck() {
        return overview() ? new DeckContents(this.browseClass, BinderContents.EMPTY, true)
                : deckCase().getOrDefault(ModComponents.DECK_CONTENTS, DeckContents.EMPTY);
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
            if (!stack.is(ModItems.BINDER) || (overview() && stack != holder())) {
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

    // Permanent unlocks are synced from the server; simply carrying a sleeve no longer equips it.
    private boolean ownsSleeve(Identifier sleeve) {
        if (sleeve.equals(Sleeves.CLASSIC) || sleeve.equals(currentSleeve())) {
            return true;
        }
        return this.minecraft.player != null && SleeveCollection.owns(this.minecraft.player, sleeve);
    }

    // Cards shown on the left: in your binders or already in the deck, on the current tab, matching the filters.
    private List<Identifier> shownCards(Map<Identifier, int[]> available, DeckContents deck) {
        CardClass tabClass = this.tab == Tab.NEUTRAL ? CardClass.NEUTRAL : deck.deckClass();
        String query = this.search.getValue().trim().toLowerCase(Locale.ROOT);
        List<Identifier> shown = new ArrayList<>();
        for (Identifier id : Cards.collectible()) {
            CardDefinition card = Cards.get(id);
            if (!overview() && !available.containsKey(id) && deck.copies(id) == 0) continue;
            if (card.cardClass() != tabClass) continue;
            if (!DeckCardFilter.matches(card, this.costFilter, this.rarityFilter, this.keywordFilters, this.typeFilters, this.cardTypeFilters)) continue;
            if (!query.isEmpty() && !Cards.name(id).getString().toLowerCase(Locale.ROOT).contains(query)) continue;
            shown.add(id);
        }
        shown.sort(Comparator.<Identifier>comparingInt(id -> Cards.get(id).cost())
                .thenComparing(id -> Cards.name(id).getString()));
        return shown;
    }

    // Short windows give a single row more room; wide pages fit more cards at the same scale.
    private int gridRows() { return this.gridHeight < 206 ? 1 : ROWS; }

    private float heightScale() {
        return Math.max(0.03F, Math.min(1.35F,
                (this.gridHeight - (gridRows() - 1) * CARD_GAP - gridRows() * LABEL_HEIGHT)
                        / (float) (gridRows() * CardRenderer.HEIGHT)));
    }

    private int gridColumns() {
        float fit = (leftWidth + CARD_GAP) / (CardRenderer.WIDTH * heightScale() + CARD_GAP);
        // A pair never wraps between rows or pages while both finishes are shown.
        boolean pairs = !cosmetics() && showNormalVersions && showEnchantedVersions;
        return Math.max(2, pairs ? Math.round(fit / 2) * 2 : Math.round(fit));
    }

    private float cardScale() {
        int columns = gridColumns();
        float byWidth = (this.leftWidth - (columns - 1) * CARD_GAP) / (float) (columns * CardRenderer.WIDTH);
        return Math.max(0.03F, Math.min(heightScale(), byWidth));
    }

    private int classTabColumns() { return Math.max(1, Math.min(CardClass.values().length + 2, (this.leftWidth - 8) / CLASS_TAB_STEP)); }

    private static int normalCollected(BinderContents contents, List<Identifier> ids) {
        return (int) ids.stream().filter(id -> contents.count(id, false) > 0).count();
    }

    private List<Identifier> classCards(CardClass cardClass) {
        return Cards.collectible().stream().filter(id -> Cards.get(id).cardClass() == cardClass).toList();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        if (!validHolder()) {
            this.onClose();
            return;
        }
        layout();
        DeckContents deck = deck();
        boolean pickingClass = this.choosingClass || (!overview() && deck.deckClass() == CardClass.NEUTRAL);
        this.search.visible = !pickingClass && !cosmetics();
        if (!search.visible && search.isFocused()) setFocused(null);
        this.nameBox.visible = !pickingClass && !overview();
        // A deck without a name of its own is called after its class, so picking a class changes the name.
        if (deck.deckClass() != this.nameClass) {
            this.nameClass = deck.deckClass();
            if (!this.nameBox.isFocused() && DeckBuilding.customName(deckCase()).isEmpty()) {
                this.nameBox.setValue(deckCase().getHoverName().getString());
                this.savedName = this.nameBox.getValue();
            }
        }

        BinderTheme.cover(g,panelX,panelY,panelWidth,panelHeight);
        if (!pickingClass && gridHeight>0) {
            g.fill(leftX-3,gridY-3,leftX+leftWidth+3,gridY+gridHeight+3,0xFF38261F);
            g.outline(leftX-4,gridY-4,leftWidth+8,gridHeight+8,0xFF966D4B);
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        this.cardSpots.clear();
        this.sleeveSpots.clear();
        this.boardButtons.clear();
        this.rowSpots.clear();
        this.tabButtons.clear();
        this.costButtons.clear();
        this.rarityButtons.clear();
        this.classChoices.clear();
        this.keywordButton = this.typeButton = this.cardTypeButton = this.resetFiltersButton = this.normalVersionsButton = this.enchantedVersionsButton = null;
        this.filterOptions.clear();
        this.previousButton = null;
        this.nextButton = null;
        this.changeClassButton = null;
        this.clearDeckButton = null;
        this.masteryButton = null;
        this.rulesButton = null; this.filtersButton = null;
        this.newDeckButton = this.fileCardsButton = this.deleteDeckButton = null;
        this.hubDeckIds.clear();
        this.hubDeckSpots.clear();
        this.curveTooltip = null;

        rulesButton = drawSmallButton(g, Component.literal("Rules"), listX + listWidth - RULES_WIDTH, tabsY + 1, RULES_WIDTH, mouseX, mouseY);
        if (pickingClass) {
            drawClassPicker(g, mouseX, mouseY);
            this.doneButton = drawSmallButton(g, Component.translatable("pack_ui.craftbound.done"),
                    this.listX + listWidth - 32, this.footerY, 32, mouseX, mouseY);
            return;
        }

        List<Component> tooltip = null;
        hoveredClassTab=null;
        drawTabs(g, deck, mouseX, mouseY);
        if (this.tab == Tab.BOARDS) {
            tooltip = drawBoards(g, mouseX, mouseY);
        } else if (this.tab == Tab.BACKS) {
            tooltip = drawCardBacks(g, mouseX, mouseY);
        } else {
            if (!compactFilters) drawCostFilter(g, mouseX, mouseY);
            int active = keywordFilters.size() + typeFilters.size() + cardTypeFilters.size()
                    + (costFilter == ALL_COSTS ? 0 : 1) + (rarityFilter == null ? 0 : 1)
                    + (showNormalVersions && !showEnchantedVersions ? 0 : 1);
            String filterLabel = filtersExpanded ? "Close" : active == 0 ? "Filters" : "Filters " + active;
            filtersButton = drawSmallButton(g, Component.literal(filterLabel), leftX + leftWidth - FILTER_BUTTON_WIDTH, filterY, FILTER_BUTTON_WIDTH, mouseX, mouseY);
            tooltip = drawCollection(g, deck, mouseX, mouseY);
        }
        Identifier hoveredRow = overview() ? drawSavedDecks(g, mouseX, mouseY) : drawDeckList(g, deck, mouseX, mouseY);
        if (!cosmetics()) {
            if (overview()) drawCollectionFooter(g, mouseX, mouseY);
            else drawXpCurve(g, deck, mouseX, mouseY);
        }
        drawPager(g, pageLimit, mouseX, mouseY);

        // Hovering a deck row shows that card full size beside the list, like Hearthstone.
        if (hoveredRow != null && this.openFilter == 0) {
            float previewScale = Math.min(1.0F, (this.gridHeight + 20) / (float) CardRenderer.HEIGHT);
            int previewWidth = Math.round(CardRenderer.WIDTH * previewScale);
            int previewHeight = Math.round(CardRenderer.HEIGHT * previewScale);
            int previewX = this.listX - PADDING - previewWidth;
            int previewY = Math.max(this.panelY + 4, Math.min(mouseY - previewHeight / 2, this.panelY + this.panelHeight - previewHeight - 4));
            CardRenderer.draw(g, this.font, CardItem.of(hoveredRow, deck.cards().count(hoveredRow, true) > 0), previewX, previewY, previewScale);
            // No tooltip here: it would cover the card. The row's count already turns red if copies are missing.
            tooltip = null;
        } else if (this.nameBox.visible && !this.nameBox.isFocused() && this.nameBox.isMouseOver(mouseX, mouseY)) {
            tooltip = List.of(Component.translatable("deck_ui.craftbound.rename_hint").withStyle(ChatFormatting.GRAY));
        }
        if (!overview() && mouseX >= this.listX && mouseX < this.listX + listWidth && mouseY >= this.filterY + 18 && mouseY < this.filterY + 29) {
            tooltip = List.of(Component.literal("Click to view all Class Mastery").withStyle(ChatFormatting.GOLD),
                    Component.literal("Win: 25 points · Completed loss: 10"),
                    Component.literal("100 points per level · Maximum level 30"),
                    Component.literal("Matches must reach turn 6. Conceding earns no points."),
                    Component.literal("Levels 2/4/6/8/10: a missing Common playset"),
                    Component.literal("Rewards check cards and binders you carry."),
                    Component.literal("Mastery never increases combat stats."));
        }
        if (this.curveTooltip != null) tooltip = this.curveTooltip;
        if(hoveredClassTab!=null) tooltip=List.of(hoveredClassTab);
        if (filtersExpanded && !cosmetics()) {
            g.nextStratum();
            g.fill(leftX-3,gridY-3,leftX+leftWidth+3,versionFilterY+16,WOOD_DARK);
            g.outline(leftX-3,gridY-3,leftWidth+6,versionFilterY+19-gridY,0xFF966D4B);
            if (compactFilters) drawCostFilter(g,mouseX,mouseY);
            drawAdvancedFilters(g,mouseX,mouseY);
            cardSpots.clear();
            tooltip=null;
        }
        if (this.openFilter != 0 && !cosmetics()) {
            drawFilterPopup(g, mouseX, mouseY);
        } else if (this.dragging) {
            drawDrag(g, mouseX, mouseY);
        } else if (this.zoomed != null) {
            drawZoom(g, mouseX, mouseY);
        } else if (tooltip != null) {
            Tooltips.show(g, this.font, tooltip, mouseX, mouseY);
        }
    }

    // A new deck: pick one of the real classes. Neutral isn't a choice; Neutral cards go in every deck.
    private void drawClassPicker(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int centerX = this.panelX + this.panelWidth / 2;
        g.centeredText(this.font, Component.translatable("deck_ui.craftbound.pick_class_title"), centerX, this.panelY + 30, GOLD);
        CardRenderer.fitText(g, this.font, overview() ? Component.literal("Choose a class for your new saved deck.")
                : Component.translatable(this.choosingClass ? "deck_ui.craftbound.switch_hint" : "deck_ui.craftbound.pick_class_hint"),
                this.panelX + 12, this.panelY + 44, this.panelWidth - 24, 0xFFE8D6B0, false);
        List<CardClass> classes = new ArrayList<>();
        for (CardClass cardClass : CardClass.values()) {
            if (cardClass != CardClass.NEUTRAL) classes.add(cardClass);
        }
        int boxWidth = 120;
        int gap = 16;
        int columns = Math.max(1, Math.min(classes.size(), (this.panelWidth - 24 + gap) / (boxWidth + gap)));
        int rows = (classes.size() + columns - 1) / columns;
        int boxHeight = Math.min(70, Math.max(40, (this.panelHeight - 94 - (rows - 1) * 10) / rows));
        int startX = centerX - (columns * boxWidth + (columns - 1) * gap) / 2;
        int startY = this.panelY + 64;
        for (int i = 0; i < classes.size(); i++) {
            CardClass cardClass = classes.get(i);
            int x = startX + (i % columns) * (boxWidth + gap);
            int y = startY + (i / columns) * (boxHeight + 10);
            boolean hover = mouseX >= x && mouseX < x + boxWidth && mouseY >= y && mouseY < y + boxHeight;
            g.fill(x, y, x + boxWidth, y + boxHeight, hover ? 0xFF8A5A3A : 0xFF6B4423);
            g.outline(x, y, boxWidth, boxHeight, hover ? GOLD : WOOD_DARK);
            g.centeredText(this.font, cardClass.displayName(), x + boxWidth / 2, y + 10, GOLD);
            int lineY = y + 28;
            for (var line : this.font.split(Component.translatable("deck_ui.craftbound.class_blurb."
                      + cardClass.name().toLowerCase(Locale.ROOT)), boxWidth - 12)) {
                if (lineY + this.font.lineHeight > y + boxHeight - 3) break;
                g.text(this.font, line, x + boxWidth / 2 - this.font.width(line) / 2, lineY, 0xFFE8D6B0);
                lineY += this.font.lineHeight;
            }
            this.classChoices.add(new int[]{x, y, boxWidth, boxHeight, cardClass.ordinal()});
        }
    }

    private Component[] tabLabels(DeckContents deck) {
        return new Component[]{
                deck.deckClass().displayName(),
                CardClass.NEUTRAL.displayName(),
                Component.translatable("deck_ui.craftbound.card_backs"), Component.literal("Boards")
        };
    }

    private int tabWidth(Component label, int index) {
        // Reserve space even after cards are seen, so the tabs and their click targets never jump.
        int badgeSpace = index < 2 ? NewCards.badgeWidth(this.font, Cards.collectible().size()) + 6 : 0;
        return this.font.width(label) + 16 + badgeSpace;
    }

    private void drawTabs(GuiGraphicsExtractor g, DeckContents deck, int mouseX, int mouseY) {
        if (overview()) { drawClassTabs(g, mouseX, mouseY); return; }
        Component[] labels = tabLabels(deck);
        List<int[]> badges = new ArrayList<>();
        int x = this.leftX;
        for (int i = 0; i < labels.length; i++) {
            if (i == 2) x += 6;
            int width = i >= 2 ? 20 : Math.min(tabWidth(labels[i], i), Math.max(24,(leftWidth - 62)/2));
            boolean selected = this.tab.ordinal() == i;
            boolean hover = mouseX >= x && mouseX < x + width && mouseY >= this.tabsY && mouseY < this.tabsY + 16;
            g.fill(x, this.tabsY, x + width, this.tabsY + 16, selected ? PAGE : hover ? 0xFF8A5A3A : 0xFF6B4423);
            g.outline(x, this.tabsY, width, 16, WOOD_DARK);
            if (i >= 2) {
                cosmeticIcon(g, i == 3, x + 2, tabsY);
                if (hover) hoveredClassTab = labels[i];
            } else CardRenderer.fitText(g,font,labels[i],x+width/2,tabsY+4,width-8,selected?TEXT_DARK:0xFFFFFFFF,true,!selected);
            // How many new cards are waiting on the class and Neutral tabs.
            int fresh = i == 0 ? NewCards.count(deck.deckClass()) : i == 1 ? NewCards.count(CardClass.NEUTRAL) : 0;
            if (fresh > 0) {
                int badgeX = x + width - 4 - NewCards.badgeWidth(this.font, fresh);
                int badgeY = this.tabsY + (16 - NewCards.badgeHeight(this.font)) / 2 + 1;
                badges.add(new int[]{badgeX, badgeY, fresh});
            }
            this.tabButtons.add(new int[]{x, this.tabsY, width, 16, i});
            x += width + 4;
        }
        // Draw overlays after every tab background, with their full border contained inside the tab.
        for (int[] badge : badges) {
            NewCards.badge(g, this.font, badge[0], badge[1], badge[2]);
        }
    }

    // Keep the two cosmetic tabs together if the last class row has only one free cell.
    private int tabCell(int index) {
        int classes = CardClass.values().length;
        int columns = classTabColumns();
        if (index >= classes && columns > 1 && classes % columns == columns - 1) return index + 1;
        return index;
    }

    private void cosmeticIcon(GuiGraphicsExtractor g, boolean board, int x, int y) {
        if (board) g.item(Items.MAP.getDefaultInstance(),x,y);
        else CardRenderer.drawBack(g,Sleeves.get(Sleeves.CLASSIC),x+3,y+1,.085F);
    }

    private void drawClassTabs(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int columns = classTabColumns();
        CardClass[] classes = CardClass.values();
        for (int i = 0; i < classes.length + 2; i++) {
            int cell = tabCell(i);
            boolean cosmetic = i >= classes.length;
            int x = this.leftX + (cell % columns) * CLASS_TAB_STEP + (cosmetic ? 8 : 0);
            int right = x + CLASS_TAB_STEP - 2;
            int y = this.tabsY + (cell / columns) * CLASS_TAB_HEIGHT;
            int width = right - x;
            boolean board = i == classes.length + 1;
            boolean selected = cosmetic ? tab == (board ? Tab.BOARDS : Tab.BACKS) : !cosmetics() && browseClass == classes[i];
            boolean hover = mouseX >= x && mouseX < right && mouseY >= y && mouseY < y + CLASS_TAB_HEIGHT - 2;
            g.fill(x, y, right, y + CLASS_TAB_HEIGHT - 2, selected ? PAGE : hover ? 0xFF805635 : 0xFF44291C);
            g.outline(x, y, width, CLASS_TAB_HEIGHT - 2, selected ? GOLD : WOOD_DARK);
            if (!cosmetic) g.fill(x + 1, y + CLASS_TAB_HEIGHT - 5, right - 1, y + CLASS_TAB_HEIGHT - 3, classes[i].lightColor());
            if (hover) hoveredClassTab = board ? Component.literal("Board designs") : cosmetic
                    ? Component.translatable("deck_ui.craftbound.card_backs") : classes[i].displayName();
            int ix=x+(width-16)/2,iy=y+1;
            if(cosmetic) cosmeticIcon(g,board,ix,iy);
            else g.item((switch(classes[i]){
                case BUILDER -> Items.CRAFTING_TABLE;
                case FIGHTER -> Items.IRON_SWORD; case FARMER -> Items.IRON_HOE;
                case FISHERMAN -> Items.FISHING_ROD; case ALCHEMIST -> Items.SPLASH_POTION;
                case MINER -> Items.IRON_PICKAXE; case CHEF -> Items.CAKE;
                case TAMER -> Items.BONE; case REDSTONER -> Items.REDSTONE; default -> Items.BOOK;
            }).getDefaultInstance(),ix,iy);
            this.tabButtons.add(new int[]{x, y, width, CLASS_TAB_HEIGHT - 2, i});
        }
    }

    private void drawCostFilter(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int costY = compactFilters ? gridY + 1 : filterY;
        int x = this.leftX;
        int naturalCostWidth = 0;
        for (int cost = ALL_COSTS; cost <= HIGH_COST; cost++)
            naturalCostWidth += Math.max(13, this.font.width(cost == ALL_COSTS ? Component.translatable("deck_ui.craftbound.all").getString() : cost == HIGH_COST ? "7+" : String.valueOf(cost)) + 6) + 2;
        float costScale = Math.min(1.0F, this.leftWidth / (float) naturalCostWidth);
        for (int cost = ALL_COSTS; cost <= HIGH_COST; cost++) {
            String label = cost == ALL_COSTS ? Component.translatable("deck_ui.craftbound.all").getString()
                    : cost == HIGH_COST ? "7+" : String.valueOf(cost);
            int width = Math.max(8, Math.round((Math.max(13, this.font.width(label) + 6) + 2) * costScale) - 2);
            boolean selected = this.costFilter == cost;
            boolean hover = mouseX >= x && mouseX < x + width && mouseY >= costY && mouseY < costY + 14;
            g.fill(x, costY, x + width, costY + 14, selected ? 0xFF17A84F : hover ? 0xFF2E6B40 : 0xFF1E4A2C);
            g.outline(x, costY, width, 14, WOOD_DARK);
            CardRenderer.fitText(g, this.font, Component.literal(label), x + width / 2, costY + 3, width - 2, 0xFFFFFFFF, true);
            this.costButtons.add(new int[]{x, costY, width, 14, cost});
            x += width + 2;
        }

        // Rarity buttons after a small gap, each in its rarity's colour. Click again to show every rarity.
        x = this.rarityY == costY ? x + 5 : this.leftX;
        int naturalRarityWidth = 0;
        for (CardRarity rarity : CardRarity.values()) naturalRarityWidth += this.font.width(rarity.displayName()) + 8;
        float rarityScale = Math.min(1.0F, (this.leftX + this.leftWidth - (compactFilters ? 0 : this.searchWidth + FILTER_BUTTON_WIDTH + 8) - x) / (float) naturalRarityWidth);
        for (CardRarity rarity : CardRarity.values()) {
            Component label = rarity.displayName();
            int width = Math.max(10, Math.round((this.font.width(label) + 8) * rarityScale) - 2);
            boolean selected = this.rarityFilter == rarity;
            boolean hover = mouseX >= x && mouseX < x + width && mouseY >= this.rarityY && mouseY < this.rarityY + 14;
            g.fill(x, this.rarityY, x + width, this.rarityY + 14, selected ? 0xFF000000 | rarity.rgb() : hover ? 0xFF4A3A30 : 0xFF2E241E);
            g.outline(x, this.rarityY, width, 14, selected ? 0xFFFFFFFF : WOOD_DARK);
            boolean lightBackground = selected && (rarity == CardRarity.COMMON || rarity == CardRarity.LEGENDARY);
            CardRenderer.fitText(g, this.font, label, x + width / 2, this.rarityY + 3, width - 4,
                    selected ? (lightBackground ? TEXT_DARK : 0xFFFFFFFF) : 0xFF000000 | rarity.rgb(), true, !lightBackground);
            this.rarityButtons.add(new int[]{x, this.rarityY, width, 14, rarity.ordinal()});
            x += width + 2;
        }
    }

    private void drawAdvancedFilters(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int resetWidth = 38;
        int filterWidth = this.leftWidth - (this.leftWidth >= 360 ? 148 : 0);
        int width = Math.max(30, (filterWidth - resetWidth - 12) / 3);
        Component keywords = Component.literal("Keywords: " + (this.keywordFilters.isEmpty() ? "All"
                : this.keywordFilters.size() == 1 ? this.keywordFilters.iterator().next().displayName().getString() : this.keywordFilters.size()));
        Component types = Component.literal("Mob Types: " + (this.typeFilters.isEmpty() ? "All"
                : this.typeFilters.size() == 1 ? this.typeFilters.iterator().next().displayName().getString() : this.typeFilters.size()));
        Component cardTypes = Component.literal("Card Types: " + (this.cardTypeFilters.isEmpty() ? "All"
                : this.cardTypeFilters.size() == 1 ? this.cardTypeFilters.iterator().next().label : this.cardTypeFilters.size()));
        this.keywordButton = drawSmallButton(g, keywords, this.leftX, this.advancedFilterY, width, mouseX, mouseY);
        this.typeButton = drawSmallButton(g, types, this.leftX + width + 4, this.advancedFilterY, width, mouseX, mouseY);
        this.cardTypeButton = drawSmallButton(g, cardTypes, this.leftX + 2 * (width + 4), this.advancedFilterY, width, mouseX, mouseY);
        this.resetFiltersButton = drawSmallButton(g, Component.literal("Reset"), this.leftX + this.leftWidth - resetWidth, this.advancedFilterY, resetWidth, mouseX, mouseY);
        int x = this.leftWidth >= 360 ? this.leftX + this.leftWidth - resetWidth - 148 : this.leftX;
        int normalWidth = this.leftWidth >= 360 ? 60 : (this.leftWidth - 4) / 2;
        int enchantedWidth = this.leftWidth >= 360 ? 80 : this.leftWidth - normalWidth - 4;
        this.normalVersionsButton = drawSmallButton(g, Component.literal((this.showNormalVersions ? "[x] " : "[ ] ") + "Normal"),
                x, this.versionFilterY, normalWidth, mouseX, mouseY);
        this.enchantedVersionsButton = drawSmallButton(g, Component.literal((this.showEnchantedVersions ? "[x] " : "[ ] ") + "Enchanted"),
                x + normalWidth + 4, this.versionFilterY, enchantedWidth, mouseX, mouseY);
    }

    private void drawFilterPopup(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.nextStratum();
        int count = switch (this.openFilter) {
            case 1 -> FILTER_KEYWORDS.length + 1;
            case 2 -> MobType.values().length + 1;
            default -> DeckCardFilter.CardType.values().length + 1;
        };
        int rows = (count + 1) / 2;
        int width = Math.min(this.leftWidth, 240);
        int x = this.leftX;
        int y = Math.min(this.gridY, this.height - rows * 17 - 30);
        g.fill(x, y, x + width, y + rows * 17 + 22, WOOD_DARK);
        g.outline(x, y, width, rows * 17 + 22, GOLD);
        for (int i = 0; i < count; i++) {
            boolean selected = switch (this.openFilter) {
                case 1 -> i == 0 ? this.keywordFilters.isEmpty() : this.keywordFilters.contains(FILTER_KEYWORDS[i - 1]);
                case 2 -> i == 0 ? this.typeFilters.isEmpty() : this.typeFilters.contains(MobType.values()[i - 1]);
                default -> i == 0 ? this.cardTypeFilters.isEmpty() : this.cardTypeFilters.contains(DeckCardFilter.CardType.values()[i - 1]);
            };
            Component label = i == 0 ? Component.literal("All") : switch (this.openFilter) {
                case 1 -> FILTER_KEYWORDS[i - 1].displayName();
                case 2 -> MobType.values()[i - 1].displayName();
                default -> Component.literal(DeckCardFilter.CardType.values()[i - 1].label);
            };
            int bx = x + 3 + i / rows * (width / 2);
            int by = y + 3 + i % rows * 17;
            int bw = width / 2 - 6;
            g.fill(bx, by, bx + bw, by + 15, selected ? 0xFF326345 : WOOD);
            CardRenderer.fitText(g, this.font, Component.literal(selected ? "[x] " : "[ ] ").append(label),
                    bx + 3, by + 3, bw - 6, 0xFFFFFFFF, false);
            this.filterOptions.add(new int[]{bx, by, bw, 15, i - 1});
        }
        CardRenderer.fitText(g, this.font, Component.literal("Any selected; click outside to close"),
                x + 4, y + rows * 17 + 5, width - 8, GOLD, false);
    }


    private Identifier drawSavedDecks(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        var saved = craftbound.deck.BinderDecks.get(holder());
        String count = saved.entries().size() + "/" + craftbound.deck.SavedDecks.LIMIT;
        Component heading = Component.literal("My Decks · " + count);
        int headingWidth = headingWidth();
        boolean stacked = font.width(heading) > headingWidth;
        CardRenderer.fitText(g, font, stacked ? Component.literal("My Decks") : heading,
                listX, tabsY + 4, headingWidth, GOLD, false);
        if (stacked) g.text(font, count, listX, tabsY + 15, 0xFFE8D6B0, false);
        int top = tabsY + (stacked ? 27 : 20);
        int height = Math.max(28, footerY - top - 6);
        this.listArea = new int[]{this.listX, top, listWidth, height};
        g.fill(this.listX, top, this.listX + listWidth, top + height, WOOD_DARK);
        int rows = Math.max(1, height / 34);
        this.hubScroll = Math.max(0, Math.min(this.hubScroll, saved.entries().size() - rows));
        for (int i = 0; i < rows && i + this.hubScroll < saved.entries().size(); i++) {
            var entry = saved.entries().get(i + this.hubScroll);
            int y = top + i * 34;
            int[] spot = {this.listX + 1, y + 1, listWidth - 2, 30};
            this.hubDeckSpots.add(spot);
            this.hubDeckIds.add(entry.id());
            ItemStack stack = entry.asStack();
            g.fill(spot[0], spot[1], spot[0]+spot[2], spot[1]+spot[3], inside(spot, mouseX, mouseY) ? 0xFF805635 : 0xFF684127);
            g.outline(spot[0], spot[1], spot[2], spot[3], 0xFFB58B54);
            CardRenderer.fitText(g, this.font, stack.getHoverName(), this.listX + 5, y + 5, listWidth - 10, GOLD, false);
            String problem = DeckBuilding.whyCantPlay(this.minecraft.player.getInventory(), stack);
            Component detail = Component.literal(entry.deck().size() + (commanderReady(entry.deck()) ? "/20 · Commander · " : "/" + deckTarget() + " · "));
            detail = detail.copy().append(problem != null && problem.equals("match_ui.craftbound.deck_missing")
                    ? Component.literal("Missing cards").withStyle(ChatFormatting.RED) : entry.deck().deckClass().displayName());
            CardRenderer.fitText(g, this.font, detail, this.listX + 5, y + 18, listWidth - 10, 0xFFE8D6B0, false);
        }
        if (saved.entries().isEmpty()) {
            int y = top + 6;
            for (var line : this.font.split(Component.literal("Create a deck, choose its class, then add cards from your collection."), listWidth - 12)) {
                g.text(this.font, line, this.listX + 6, y, 0xFFE8D6B0); y += 11;
            }
        }
        if (saved.entries().size() > rows) {
            int bar = Math.max(8, height * rows / saved.entries().size());
            int y = top + (height - bar) * this.hubScroll / Math.max(1, saved.entries().size() - rows);
            g.fill(this.listX + listWidth - 2, y, this.listX + listWidth, y + bar, GOLD);
        }
        this.newDeckButton = saved.entries().size() < craftbound.deck.SavedDecks.LIMIT
                ? drawSmallButton(g, Component.literal("New Deck"), this.listX, this.footerY, listWidth - 35, mouseX, mouseY) : null;
        this.doneButton = drawSmallButton(g, Component.literal("Done"), this.listX + listWidth - 32, this.footerY, 32, mouseX, mouseY);
        return null;
    }

    private void drawCollectionFooter(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = this.leftX, y = this.curveY;
        boolean stacked = leftWidth < 365;
        int right = x + leftWidth - 69;
        g.fill(x,y,x+leftWidth,footerY+15,WOOD_DARK);
        g.outline(x,y,leftWidth,footerY+15-y,0xFF916B48);
        var collection = holder().getOrDefault(ModComponents.BINDER_CONTENTS, BinderContents.EMPTY);
        var ids = classCards(browseClass);
        Component summary = browseClass.displayName().copy().append(" · Normal " + normalCollected(collection,ids) + "/" + ids.size())
                .append(Component.literal(" · Enchanted " + collection.collectedEnchanted(ids) + "/" + ids.size()).withStyle(ChatFormatting.LIGHT_PURPLE));
        int addWidth = Math.min(86, Math.max(39, (right-x)*3/5-5)), masteryWidth = Math.min(46,Math.max(30,right-x-addWidth-9));
        int bx = stacked ? x + 3 : right - masteryWidth - addWidth - 6;
        CardRenderer.fitText(g,font,summary,x+4,stacked ? y+3 : footerY+3,stacked ? leftWidth-8 : bx-x-8,GOLD,false);
        this.fileCardsButton = drawSmallButton(g,Component.literal("Add inventory cards"),bx,footerY,addWidth,mouseX,mouseY);
        this.masteryButton = drawSmallButton(g,Component.literal("Mastery"),bx+addWidth+3,footerY,masteryWidth,mouseX,mouseY);
    }

    private static int[] visibleVersions(boolean normal, boolean enchanted) {
        return normal ? enchanted ? new int[]{0, 1} : new int[]{0} : enchanted ? new int[]{1} : new int[0];
    }

    // The grid of full-size cards. Returns a tooltip to show, if any.
    private List<Component> drawCollection(GuiGraphicsExtractor g, DeckContents deck, int mouseX, int mouseY) {
        Map<Identifier, int[]> available = available();
        List<Identifier> shown = shownCards(available, deck);
        float scale = cardScale();
        int cardWidth = Math.round(CardRenderer.WIDTH * scale);
        int cardHeight = Math.round(CardRenderer.HEIGHT * scale);
        int columns = gridColumns();
        int perPage = columns * gridRows();
        int[] versions = visibleVersions(this.showNormalVersions, this.showEnchantedVersions);
        int slotCount = shown.size() * versions.length;
        int pageCount = Math.max(1, (slotCount + perPage - 1) / perPage);
        this.pageLimit=pageCount;
        this.page = Math.max(0, Math.min(this.page, pageCount - 1));
        // Cards fill in from the left, like reading a page.
        int startX = this.leftX;

        List<Component> tooltip = null;
        for (int i = 0; i < perPage; i++) {
            int pocketX = startX + (i % columns) * (leftWidth - cardWidth) / (columns - 1);
            int pocketY = gridY + (i / columns) * (cardHeight + LABEL_HEIGHT + CARD_GAP);
            drawPocket(g,pocketX,pocketY,cardWidth,cardHeight);
            if (page * perPage + i >= slotCount) continue;
            int slotIndex = this.page * perPage + i;
            Identifier id = shown.get(slotIndex / versions.length);
            boolean showEnchanted = versions[slotIndex % versions.length] == 1;
            int x = pocketX;
            int y = pocketY;
            int[] counts = available.getOrDefault(id, new int[2]);
            int[] spare = spare(counts, deck, id);

            // New cards glow blue until you hover them, like Hearthstone.
            boolean isNew = NewCards.isNew(id);
            if (isNew) {
                NewCards.glow(g, x, y, cardWidth, cardHeight);
            }
            List<CardRenderer.HoverRegion> regions = CardRenderer.draw(g, this.font, CardItem.of(id, showEnchanted), x, y, scale);
            int variant = showEnchanted ? 1 : 0;
            String reason = (overview() ? counts[variant] : spare[variant]) == 0 ? "deck_ui.craftbound.none_left"
                    : overview() ? null : cannotAdd(deck, id);
            if (reason != null) {
                g.fill(x, y, x + cardWidth, y + cardHeight, 0x99000000);
            }
            drawCardLabel(g, id, counts[variant], x, y + cardHeight + 2, cardWidth);
            if (isNew) {
                Component label = Component.translatable("deck_ui.craftbound.new");
                int labelWidth = Math.min(cardWidth - 8, this.font.width(label) + 4);
                g.fill(x + cardWidth - labelWidth - 2, y + 2, x + cardWidth - 2, y + 13, 0xFF183D59);
                CardRenderer.fitText(g, this.font, label, x + cardWidth - labelWidth, y + 3, labelWidth - 4, 0xFF7FD0FF, false);
            }

            Spot spot = new Spot(x, y, cardWidth, cardHeight, id, showEnchanted);
            this.cardSpots.add(spot);
            if (!filtersExpanded && openFilter == 0 && spot.contains(mouseX, mouseY)) {
                if (!this.dragging && this.zoomed == null && this.openFilter == 0)
                    g.outline(x - 1, y - 1, cardWidth + 2, cardHeight + 2, 0xFFFFFFFF);
                NewCards.seen(id);
                for (CardRenderer.HoverRegion region : regions) {
                    if (region.contains(mouseX, mouseY)) {
                        tooltip = region.lines();
                    }
                }
                if (tooltip == null) {
                    tooltip = new ArrayList<>();
                    tooltip.add(Component.literal((showEnchanted ? "Enchanted" : "Normal") + " · Owned: " + counts[variant]));
                    if (overview()) tooltip.add(Component.literal("Shift-click: take one out to trade").withStyle(ChatFormatting.GRAY));
                    else tooltip.add(Component.literal("In deck: " + deck.cards().count(id, showEnchanted)
                            + " · Limit: " + copyLimit(id) + " across both versions"));
                    if (reason != null) tooltip.add(Component.translatable(reason).withStyle(ChatFormatting.RED));
                }
            }
        }

        if (slotCount == 0) {
            Component empty = Cards.collectible().isEmpty() ? Component.literal("No collectible cards yet.")
                    : versions.length == 0 ? Component.literal("Enable Normal or Enchanted to show cards.")
                    : Component.translatable("deck_ui.craftbound.no_cards");
            int width = Math.min(leftWidth - 8, font.width(empty) + 12);
            int x = leftX + (leftWidth - width)/2, y = gridY + gridHeight/2 - 7;
            g.fill(x,y,x+width,y+15,WOOD_DARK);
            CardRenderer.fitText(g,font,empty,x+width/2,y+3,width-6,0xFFE8D6B0,true);
        }
        return tooltip;
    }

    private void drawPager(GuiGraphicsExtractor g,int pageCount,int mouseX,int mouseY) {
        int x = leftX + leftWidth - 66;
        CardRenderer.fitText(g,font,Component.literal((page+1)+" / "+pageCount),x+33,footerY+3,36,0xFFE8D6B0,true);
        this.previousButton = page > 0 ? drawSmallButton(g,Component.literal("<"),x,footerY,14,mouseX,mouseY) : null;
        this.nextButton = page < pageCount-1 ? drawSmallButton(g,Component.literal(">"),x+52,footerY,14,mouseX,mouseY) : null;
    }

    // A separate full-width collection footer; page controls sit below it.
    private void drawXpCurve(GuiGraphicsExtractor g, DeckContents deck, int mouseX, int mouseY) {
        var curve = craftbound.deck.DeckXpCurve.of(deck);
        int x = this.leftX, y = this.curveY;
        int width = this.leftWidth;
        g.fill(x, y, x + width, y + CURVE_HEIGHT, WOOD_DARK);
        g.outline(x, y, width, CURVE_HEIGHT, 0xFFB58B54);
        g.fill(x + 1, y + 1, x + width - 1, y + 2, GOLD);
        int chartX = x + 48, chartWidth = width - 52;
        int bottom = y + CURVE_HEIGHT - 12, max = 1;
        for (int count : curve.counts()) max = Math.max(max, count);
        String average = curve.total() == 0 ? "—" : String.format(Locale.ROOT, "%.1f", curve.average());
        CardRenderer.fitText(g, this.font, Component.literal("XP curve · Avg " + average), x + 6, y + 5, width - 12, GOLD, false);
        this.curveTooltip = null;
        for (int cost = 0; cost < 8; cost++) {
            int count = curve.counts()[cost], bx = chartX + cost * chartWidth / 8;
            int right = chartX + (cost + 1) * chartWidth / 8;
            int center = (bx + right) / 2;
            boolean hover = mouseX >= bx && mouseX < right && mouseY >= y + 2 && mouseY < y + CURVE_HEIGHT - 1;
            int height = count == 0 ? 0 : Math.max(2, Math.round(9F * count / max));
            g.fill(bx + 2, bottom - 1, right - 2, bottom, 0xFF9A7855);
            if (height > 0) {
                g.fill(bx + 2, bottom - height, right - 2, bottom, hover ? 0xFF9ADF69 : 0xFF5EA84C);
                g.fill(bx + 2, bottom - height, right - 2, bottom - height + 1, 0xFFC5E893);
                g.centeredText(this.font, String.valueOf(count), center, bottom - height - 9, 0xFFFFEAC2);
            }
            g.centeredText(this.font, cost == 7 ? "7+" : String.valueOf(cost), center, bottom + 3, 0xFFE8D6B0);
            if (hover) this.curveTooltip = List.of(
                    Component.literal((cost == 7 ? "7+ XP" : cost + " XP") + ": " + count + (count == 1 ? " card" : " cards")).withStyle(ChatFormatting.GOLD),
                    Component.literal("Counts every copy in your deck."),
                    Component.literal("Average XP: " + average + " · " + curve.total() + " cards"));
        }
    }

    // A single centered ownership count. Legendary slots need no redundant ×1.
    private void drawCardLabel(GuiGraphicsExtractor g, Identifier id, int count, int x, int y, int width) {
        if (Cards.get(id).rarity() != CardRarity.LEGENDARY && count > 1) {
            CardRenderer.fitText(g, this.font, Component.literal("×" + Math.min(count, DeckRules.maxCopies(Cards.get(id).rarity()))), x + width / 2, y, width, 0xFFFFFFFF, true);
        }
    }

    // Card backs use collection-sized slots; adding designs adds pages, never shrinks every back.
    private static void drawPocket(GuiGraphicsExtractor g,int x,int y,int w,int h) {
        g.fill(x-2,y-2,x+w+2,y+h+2,0xFF241B18);
        g.fill(x-1,y-1,x+w+1,y+h+1,0xFF48352B);
        g.fill(x-2,y+h+1,x+w+2,y+h+2,0xFF956F50);
        g.fill(x+w+1,y-1,x+w+2,y+h+1,0xFF79573E);
        // A short seam at the mouth is visible only when the pocket is empty.
        if (w >= 18 && h >= 24) {
            g.fill(x+2,y+2,x+w-2,y+3,0xFF3D2B23);
            g.fill(x+2,y+h-4,x+w-2,y+h-3,0xFF634936);
        }
    }

    private List<Component> drawBoards(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        BoardDesign[] designs = BoardDesign.values();
        int columns = Math.clamp(leftWidth / 86, 1, 4);
        int rows = Math.clamp(gridHeight / 64, 1, 2);
        int capacity = columns * rows;
        pageLimit = Math.max(1,(designs.length + capacity - 1) / capacity);
        page = Math.clamp(page,0,pageLimit-1);
        int cell = leftWidth / columns;
        int w = Math.max(12, Math.min(cell-10, (gridHeight/rows-26)*2));
        int h = Math.max(10,w/2);
        List<Component> tip = null;
        BoardDesign equipped = BoardCollection.selected(minecraft.player);
        for(int i=0;i<capacity;i++) {
            int x = leftX+(i%columns)*cell+(cell-w)/2;
            int y = gridY+(i/columns)*(gridHeight/rows)+3;
            drawPocket(g,x,y,w,h);
            if(page*capacity+i>=designs.length)continue;
            BoardDesign design=designs[page*capacity+i];
            g.fill(x,y,x+w,y+h,design.brick);
            g.fill(x+3,y+3,x+w-3,y+h-3,design.mat);
            g.fill(x+4,y+h/2,x+w-4,y+h/2+1,design.accent);
            BoardTrim.draw(new CardCanvas.Gui(g),design,x+2,y+2,w-4,h-4);
            boolean owned = BoardCollection.owns(minecraft.player,design);
            int[] bounds={x-2,y-2,w+4,h+font.lineHeight*2+6};
            boardButtons.put(design,bounds);
            boolean hover=inside(bounds,mouseX,mouseY);
            if(!owned) g.fill(x,y,x+w,y+h,0x66000000);
            if(hover || equipped==design) g.outline(x-1,y-1,w+2,h+2,hover?0xFFFFFFFF:GOLD);
            CardRenderer.fitText(g,font,Component.literal(design.title),x+w/2,y+h+3,cell-4,0xFFFFE8C2,true,false);
            String state=equipped==design?"Equipped":owned?"Available":design.trialReward()?"Trials reward":"Mastery "+design.masteryLevel;
            CardRenderer.fitText(g,font,Component.literal(state),x+w/2,y+h+4+font.lineHeight,cell-4,equipped==design?GOLD:0xFFD1B49A,true,false);
            if(hover) tip=List.of(Component.literal(design.title),Component.literal(owned?"Each player’s selected board has a 50% chance.":design.trialReward()?"Earn this board from a class clear in The Trials.":"Reach Mastery "+design.masteryLevel+" with any class to unlock."),
                    Component.literal(equipped==design?"Currently equipped":owned?"Click to equip":"Locked"));
        }
        return tip;
    }

    private List<Component> drawCardBacks(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        List<Identifier> sleeves = new ArrayList<>(Sleeves.ids());
        int columns=gridColumns(),rows=gridRows(),perPage=columns*rows;
        int pageCount=Math.max(1,(sleeves.size()+perPage-1)/perPage);
        this.pageLimit=pageCount;
        this.page=Math.clamp(this.page,0,pageCount-1);
        int cellWidth=(this.leftWidth-(columns-1)*CARD_GAP)/columns;
        int labelHeight=2*font.lineHeight+10;
        int cellHeight=Math.max(20,(this.gridHeight-(rows-1)*CARD_GAP)/rows);
        float scale=Math.max(.03F,Math.min(cardScale(),Math.min((cellWidth-8F)/CardRenderer.WIDTH,
                (cellHeight-labelHeight)/(float)CardRenderer.HEIGHT)));
        int cardWidth = Math.round(CardRenderer.WIDTH * scale);
        int cardHeight = Math.round(CardRenderer.HEIGHT * scale);
        int startX = this.leftX;
        List<Component> tooltip = null;
        for (int i = 0; i < perPage; i++) {
            int cellX=startX+(i%columns)*(cellWidth+CARD_GAP);
            int x = cellX+(cellWidth-cardWidth)/2;
            int y = this.gridY + (i / columns) * (cellHeight + CARD_GAP);
            drawPocket(g,x,y,cardWidth,cardHeight);
            if (page*perPage+i >= sleeves.size()) continue;
            Identifier sleeve = sleeves.get(page*perPage+i);
            CardRenderer.drawBack(g, Sleeves.get(sleeve), x, y, scale);
            boolean owned = ownsSleeve(sleeve);
            boolean equipped = !overview() && sleeve.equals(currentSleeve());
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
            int labelY=y+cardHeight+6;
            for(var line:font.split(Sleeves.name(sleeve),Math.max(10,cellWidth-8)).stream().limit(2).toList()) {
                g.centeredText(font,line,cellX+cellWidth/2,labelY,0xFFFFFFFF);labelY+=font.lineHeight;
            }
            Spot spot = new Spot(x, y, cardWidth, cardHeight, sleeve);
            this.sleeveSpots.add(spot);
            if (spot.contains(mouseX, mouseY)) {
                g.outline(x-1,y-1,cardWidth+2,cardHeight+2,0xFFFFFFFF);
                tooltip = List.of(Sleeves.name(sleeve), Component.literal("Right-click to inspect"), overview() ? Component.literal("Open a saved deck to choose its card back.")
                        : status.copy().withStyle(owned ? ChatFormatting.GRAY : ChatFormatting.RED));
            }
        }
        return tooltip;
    }

    // The deck list on the right, under the deck's name (a text box: click it to rename the deck), with a 0/30 bar
    // and the Done button. Returns the hovered row.
    private Identifier drawDeckList(GuiGraphicsExtractor g, DeckContents deck, int mouseX, int mouseY) {
        // An underline shows the name can be edited.
        if (this.nameBox.isFocused() || this.nameBox.isMouseOver(mouseX, mouseY)) {
            g.fill(this.listX, this.tabsY + 13, this.listX + listWidth - RULES_WIDTH - 4, this.tabsY + 14, this.nameBox.isFocused() ? GOLD : 0x80FFD27A);
        }

        // 0/30 progress bar. If your binders are missing cards this deck lists, it says so in red.
        int missingTotal = 0;
        for (Identifier id : Cards.collectible()) {
            missingTotal += missing(deck, id);
        }
        int barY = this.filterY;
        float filled = Math.min(1F, deck.size() / (float) deckTarget());
        g.fill(this.listX, barY, this.listX + listWidth, barY + 14, 0xFF1E1410);
        g.fill(this.listX + 1, barY + 1, this.listX + 1 + Math.round((listWidth - 2) * filled), barY + 13,
                deck.size() == deckTarget() ? 0xFF3FAE2A : 0xFF17A84F);
        Component count = missingTotal > 0
                ? Component.translatable("deck_ui.craftbound.cards_missing", deck.size(), deckTarget(), missingTotal)
                : Component.translatable("deck_ui.craftbound.cards_count", deck.size(), deckTarget());
        boolean commanderReady = missingTotal == 0 && commanderReady(deck);
        if (commanderReady) count=Component.literal(deck.size() + " cards · Commander ready");
        CardRenderer.fitText(g,this.font,count,this.listX + listWidth / 2,barY+3,listWidth-4,missingTotal>0?0xFFFF8A7A:0xFFFFFFFF,true);

        if (this.minecraft.player != null) {
            var mastery = craftbound.mastery.ClassMastery.get(this.minecraft.player);
            int level = mastery.level(deck.deckClass());
            Component label = Component.literal("Mastery " + level + (level == 30 ? " · MAX" : " · " + mastery.progress(deck.deckClass()) + "/100"));
            this.masteryButton = new int[]{this.listX, barY + 17, listWidth, 13};
            boolean hover = inside(this.masteryButton, mouseX, mouseY);
            g.fill(this.listX, barY + 17, this.listX + listWidth, barY + 30, hover ? 0xFF70492A : 0xFF4B301F);
            CardRenderer.fitText(g, this.font, label.copy().append(" ›"), this.listX + 3, barY + 19, listWidth - 6, GOLD, false);
        }
        this.changeClassButton = drawButton(g, Component.translatable("deck_ui.craftbound.change_class"),
                this.listX, this.filterY + 31, this.savedDeckId == null ? listWidth : listWidth - 35, mouseX, mouseY);
        if (this.savedDeckId != null) this.deleteDeckButton = drawButton(g, Component.literal(this.confirmDelete ? "Delete?" : "Delete"),
                this.listX + listWidth - 32, this.filterY + 31, 32, mouseX, mouseY);
        int listTop = this.filterY + 53;
        int listHeight = Math.max(16, this.footerY - 6 - listTop);
        this.listArea = new int[]{this.listX, listTop, listWidth, listHeight};
        g.fill(this.listX, listTop, this.listX + listWidth, listTop + listHeight, PAGE);

        List<Identifier> inDeck = new ArrayList<>();
        for (Identifier id : Cards.collectible()) {
            if (deck.copies(id) > 0) inDeck.add(id);
        }
        inDeck.sort(Comparator.<Identifier>comparingInt(id -> Cards.get(id).cost())
                .thenComparing(id -> Cards.name(id).getString()));
        int visibleRows = Math.max(1, (listHeight - 4) / ROW_HEIGHT);
        this.scroll = Math.max(0, Math.min(this.scroll, inDeck.size() - visibleRows));
        if (inDeck.isEmpty()) {
            int lineY = listTop + 4;
            for (var line : this.font.split(Component.translatable("deck_ui.craftbound.empty"), listWidth - 8)) {
                g.text(this.font, line, this.listX + 4, lineY, 0xFF8A7458, false);
                lineY += this.font.lineHeight;
            }
        }
        Identifier hovered = null;
        for (int r = 0; r < visibleRows && this.scroll + r < inDeck.size(); r++) {
            Identifier id = inDeck.get(this.scroll + r);
            int y = listTop + 2 + r * ROW_HEIGHT;
            Spot spot = new Spot(this.listX, y, listWidth, ROW_HEIGHT, id);
            this.rowSpots.add(spot);
            if (spot.contains(mouseX, mouseY)) {
                hovered = id;
                g.fill(this.listX, y, this.listX + listWidth, y + ROW_HEIGHT, 0x30000000);
            }
            drawDeckRow(g, id, deck, this.listX + 2, y);
        }
        if (inDeck.size() > visibleRows) {
            int barHeight = Math.max(10, listHeight * visibleRows / inDeck.size());
            int scrollY = listTop + (listHeight - barHeight) * this.scroll / Math.max(1, inDeck.size() - visibleRows);
            g.fill(this.listX + listWidth - 2, scrollY, this.listX + listWidth, scrollY + barHeight, 0xFF8A5A3A);
        }

        // Done, and Change class while the deck is still empty.
        this.doneButton = drawSmallButton(g, Component.translatable("pack_ui.craftbound.done"),
                this.listX + listWidth - 32, this.footerY, 32, mouseX, mouseY);
        this.clearDeckButton = drawSmallButton(g, Component.translatable("deck_ui.craftbound.clear_deck"),
                this.listX, this.footerY, listWidth - 35, mouseX, mouseY);
        return hovered;
    }

    // Copies you own but haven't listed in this deck yet: [normal, Enchanted].
    private static int[] spare(int[] owned, DeckContents deck, Identifier id) {
        return new int[]{
                Math.max(0, owned[0] - deck.cards().count(id, false)),
                Math.max(0, owned[1] - deck.cards().count(id, true))
        };
    }

    // How many copies the deck lists that your binders don't have (normal plus Enchanted).
    private int missing(DeckContents deck, Identifier id) {
        if (this.minecraft.player == null) {
            return 0;
        }
        Inventory inventory = this.minecraft.player.getInventory();
        return DeckBuilding.missing(inventory, deck, id, false) + DeckBuilding.missing(inventory, deck, id, true);
    }

    // One line of the deck list: cost, name in its rarity colour, and how many (purple star if any are Enchanted).
    // The count turns red if your binders are missing copies the deck needs.
    private void drawDeckRow(GuiGraphicsExtractor g, Identifier id, DeckContents deck, int x, int y) {
        CardDefinition card = Cards.get(id);
        g.fill(x, y + 1, x + 12, y + 11, 0xFF17A84F);
        g.centeredText(this.font, String.valueOf(card.cost()), x + 6, y + 2, 0xFFFFFFFF);
        // Long names shrink so they never run into the count.
        CardRenderer.fitText(g, this.font, CardItem.of(id).getHoverName(), x + 15, y + 2, listWidth - (deck.cards().count(id, true) > 0 ? 42 : 33), TEXT_DARK, false, false);
        String count = "x" + deck.copies(id);
        int countX = x + listWidth - 8 - this.font.width(count);
        g.text(this.font, count, countX, y + 2, missing(deck, id) > 0 ? 0xFFD02020 : TEXT_DARK, false);
        if (deck.cards().count(id, true) > 0) {
            g.text(this.font, "✦", countX - 9, y + 2, 0xFFB070FF, false);
        }
    }

    private int[] drawButton(GuiGraphicsExtractor g, Component label, int x, int y, int width, int mouseX, int mouseY) {
        return drawButton(g,label,x,y,width,18,mouseX,mouseY);
    }

    private int[] drawSmallButton(GuiGraphicsExtractor g,Component label,int x,int y,int width,int mouseX,int mouseY) {
        return drawButton(g,label,x,y,width,14,mouseX,mouseY);
    }

    private int[] drawButton(GuiGraphicsExtractor g,Component label,int x,int y,int width,int height,int mouseX,int mouseY) {
        int textY = y + (height - 8) / 2;
        boolean hover = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        g.fill(x, y, x + width, y + height, hover ? 0xFF8A5A3A : 0xFF6B4423);
        g.outline(x, y, width, height, WOOD_DARK);
        if (this.font.width(label) <= width - 6) g.centeredText(this.font, label, x + width / 2, textY, 0xFFFFFFFF);
        else CardRenderer.fitText(g, this.font, label, x + 3, textY, width - 6, 0xFFFFFFFF, false);
        return new int[]{x, y, width, height};
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.zoomed != null) {
            if (inside(zoomPreviousPage,event.x(),event.y())) zoomRelatedPage = Math.max(0,zoomRelatedPage-1);
            else if (inside(zoomNextPage,event.x(),event.y())) zoomRelatedPage = Math.min(zoomRelatedPages-1,zoomRelatedPage+1);
            else this.zoomed = null;
            return true;
        }
        if (this.openFilter != 0) {
            for (int[] option : this.filterOptions) if (inside(option, event.x(), event.y())) {
                int value = option[4];
                if (this.openFilter == 1) {
                    if (value < 0) this.keywordFilters.clear();
                    else if (!this.keywordFilters.remove(FILTER_KEYWORDS[value])) this.keywordFilters.add(FILTER_KEYWORDS[value]);
                } else if (this.openFilter == 2) {
                    if (value < 0) this.typeFilters.clear();
                    else if (!this.typeFilters.remove(MobType.values()[value])) this.typeFilters.add(MobType.values()[value]);
                } else {
                    if (value < 0) this.cardTypeFilters.clear();
                    else if (!this.cardTypeFilters.remove(DeckCardFilter.CardType.values()[value])) this.cardTypeFilters.add(DeckCardFilter.CardType.values()[value]);
                }
                this.page = 0;
                return true;
            }
            this.openFilter = 0;
            return true;
        }
        // Let the search bar take the click first, so you can type in it.
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        // Clear the screen's focused child too. Otherwise clicking the same search box again
        // can leave its own focused flag false while the screen still thinks it is focused.
        this.setFocused(null);
        double mouseX = event.x();
        double mouseY = event.y();
        boolean mainHand = this.hand == InteractionHand.MAIN_HAND;
        if (event.button()==InputConstants.MOUSE_BUTTON_LEFT && inside(filtersButton,mouseX,mouseY)) {
            filtersExpanded=!filtersExpanded; return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && inside(this.rulesButton, mouseX, mouseY)) {
            this.minecraft.gui.setScreen(new craftbound.client.rules.RulebookScreen(this));
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && inside(this.masteryButton, mouseX, mouseY)) {
            this.minecraft.gui.setScreen(new craftbound.client.mastery.MasteryScreen(this));return true;
        }
        DeckContents deck = deck();

        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && overview()) {
            for (int i = 0; i < this.hubDeckSpots.size(); i++) if (inside(this.hubDeckSpots.get(i), mouseX, mouseY)) {
                this.minecraft.gui.setScreen(pageFor(this.hubDeckIds.get(i)));
                return true;
            }
            if (inside(this.newDeckButton, mouseX, mouseY)) {
                this.choosingClass = true;
                return true;
            }
            if (inside(this.fileCardsButton, mouseX, mouseY)) {
                ClientPlayNetworking.send(new craftbound.network.FileAllCardsPayload(holderLocation));
                return true;
            }

        }

        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && inside(this.normalVersionsButton, mouseX, mouseY)) {
            this.showNormalVersions = !this.showNormalVersions; this.page = 0; return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && inside(this.enchantedVersionsButton, mouseX, mouseY)) {
            this.showEnchantedVersions = !this.showEnchantedVersions; this.page = 0; return true;
        }
        if (inside(this.keywordButton, mouseX, mouseY) || inside(this.typeButton, mouseX, mouseY) || inside(this.cardTypeButton, mouseX, mouseY)) {
            this.openFilter = inside(this.keywordButton, mouseX, mouseY) ? 1 : inside(this.typeButton, mouseX, mouseY) ? 2 : 3;
            return true;
        }
        if (inside(this.resetFiltersButton, mouseX, mouseY)) {
            this.keywordFilters.clear(); this.typeFilters.clear(); this.cardTypeFilters.clear();
            this.costFilter = ALL_COSTS; this.rarityFilter = null;
            this.showNormalVersions = this.showEnchantedVersions = true;
            this.search.setValue(""); this.page = 0;
            return true;
        }
        if (inside(this.doneButton, mouseX, mouseY)) {
            this.onClose();
            return true;
        }
        for (int[] choice : this.classChoices) {
            if (inside(choice, mouseX, mouseY)) {
                if (overview()) {
                    String id = java.util.UUID.randomUUID().toString();
                    ClientPlayNetworking.send(new craftbound.network.BinderDeckPayload(holderLocation, id,
                            craftbound.network.BinderDeckPayload.CREATE, CardClass.values()[choice[4]].name(), false));
                    this.minecraft.gui.setScreen(pageFor(id));
                    return true;
                } else if (this.savedDeckId != null) savedAction(craftbound.network.BinderDeckPayload.CLASS, CardClass.values()[choice[4]].name(), false);
                else ClientPlayNetworking.send(new DeckClassPayload(mainHand, choice[4]));
                this.choosingClass = false;
                this.page = 0;
                this.scroll = 0;
                this.tab = Tab.CLASS;
                return true;
            }
        }
        if (inside(this.changeClassButton, mouseX, mouseY)) {
            this.choosingClass = true;
            return true;
        }
        if (inside(this.deleteDeckButton, mouseX, mouseY)) {
            if (this.confirmDelete) {
                savedAction(craftbound.network.BinderDeckPayload.DELETE, "", false);
                this.minecraft.gui.setScreen(pageFor(null));
            } else this.confirmDelete = true;
            return true;
        }
        this.confirmDelete = false;
        if (inside(this.clearDeckButton, mouseX, mouseY)) {
            if (this.savedDeckId != null) savedAction(craftbound.network.BinderDeckPayload.CLEAR, "", false);
            else ClientPlayNetworking.send(new craftbound.network.ClearDeckPayload(mainHand));
            this.scroll = 0;
            return true;
        }
        for (int[] tabButton : this.tabButtons) {
            if (inside(tabButton, mouseX, mouseY)) {
                int classCount = CardClass.values().length;
                int previous = overview() ? (cosmetics() ? classCount + (tab == Tab.BOARDS ? 1 : 0) : browseClass.ordinal()) : tab.ordinal();
                int next = tabButton[4];
                if (previous == next) return true;
                if (overview() && next < classCount) { browseClass = CardClass.values()[next]; tab = Tab.CLASS; }
                else tab = overview() ? (next == classCount ? Tab.BACKS : Tab.BOARDS) : Tab.values()[next];
                openFilter = 0; filtersExpanded = false;
                playPaperSound(1.34F, .35F);
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
        if (filtersExpanded && !cosmetics() && mouseX >= leftX-3 && mouseX <= leftX+leftWidth+3 && mouseY >= gridY-3) {
            filtersExpanded=false;
            return true;
        }
        if (inside(this.previousButton, mouseX, mouseY)) {
            turnPage(-1);
            return true;
        }
        if (inside(this.nextButton, mouseX, mouseY)) {
            turnPage(1);
            return true;
        }
        if (tab==Tab.BOARDS && event.button()==InputConstants.MOUSE_BUTTON_LEFT) {
            for (var entry : boardButtons.entrySet()) if (inside(entry.getValue(),mouseX,mouseY)) {
                BoardDesign picked = entry.getKey();
                if (BoardCollection.owns(minecraft.player,picked) && picked!=BoardCollection.selected(minecraft.player))
                    ClientPlayNetworking.send(new craftbound.network.SelectBoardPayload(picked.id()));
                return true;
            }
        }
        for (Spot sleeve : this.sleeveSpots) {
            if (sleeve.contains(mouseX, mouseY)) {
                if(event.button()==InputConstants.MOUSE_BUTTON_RIGHT || overview()) {
                    minecraft.gui.setScreen(new craftbound.client.card.SleevePreviewScreen(sleeve.id(),this));return true;
                }
                if (ownsSleeve(sleeve.id())) {
                    if (this.savedDeckId != null) savedAction(craftbound.network.BinderDeckPayload.SLEEVE, sleeve.id().toString(), false);
                    else if (!overview()) ClientPlayNetworking.send(new DeckSleevePayload(mainHand, sleeve.id()));
                }
                return true;
            }
        }
        Map<Identifier, int[]> available = available();
        for (Spot card : this.cardSpots) {
            if (!card.contains(mouseX, mouseY)) {
                continue;
            }
            int[] spare = spare(available.getOrDefault(card.id(), new int[2]), deck, card.id());
            // 26.3 numbers mouse buttons the SDL way (left 1, right 3), so use Minecraft's constants.
            if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                // Right-click only zooms in.
                zoom(card.id(), card.enchanted());
                return true;
            }
            if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
                return true;
            }
            if (overview()) {
                if (event.hasShiftDown()) ClientPlayNetworking.send(new craftbound.network.TakeFromBinderPayload(
                        holderLocation, card.id(), card.enchanted(), false));
                else zoom(card.id(), card.enchanted());
                return true;
            }
            // The selected slot is the exact version added, dragged or inspected.
            boolean enchanted = card.enchanted();
            startPress(card.id(), enchanted, false, mouseX, mouseY);
            this.pressedCanMove = spare[enchanted ? 1 : 0] > 0 && cannotAdd(deck, card.id()) == null;
            return true;
        }
        for (Spot row : this.rowSpots) {
            if (!row.contains(mouseX, mouseY)) {
                continue;
            }
            if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                // Right-click only zooms in.
                zoom(row.id(), deck.cards().count(row.id(), true) > 0);
                return true;
            }
            if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
                return true;
            }
            // Left: remove a list entry, or drag it out. Ctrl chooses the Enchanted entry.
            boolean enchanted = event.hasControlDown() || deck.cards().count(row.id(), false) == 0;
            startPress(row.id(), enchanted, true, mouseX, mouseY);
            this.pressedCanMove = deck.cards().count(row.id(), enchanted) > 0;
            return true;
        }
        return true;
    }

    // --- Dragging ---------------------------------------------------------------------------------------
    // Left-press a card, then either let go (a click) or drag it. A click moves it straight away; cards dropped
    // on the deck list are added, and deck rows dragged out are removed from the saved list.

    private void startPress(Identifier card, boolean enchanted, boolean fromDeck, double mouseX, double mouseY) {
        this.dragMotion.reset((float) mouseX, (float) mouseY);
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
            if (this.savedDeckId != null) savedAction(craftbound.network.BinderDeckPayload.REMOVE, this.pressedCard.toString(), this.pressedEnchanted);
            else ClientPlayNetworking.send(new DeckEditPayload(mainHand, this.pressedCard, this.pressedEnchanted, false));
        } else if (this.pressedCanMove && !this.pressedFromDeck && (!this.dragging || overList)) {
            if (this.savedDeckId != null) savedAction(craftbound.network.BinderDeckPayload.ADD, this.pressedCard.toString(), this.pressedEnchanted);
            else ClientPlayNetworking.send(new DeckEditPayload(mainHand, this.pressedCard, this.pressedEnchanted, true));
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
        this.dragMotion.begin(g, mouseX, mouseY, width);
        CardRenderer.draw(g, this.font, CardItem.of(this.pressedCard, this.pressedEnchanted), -width / 2, -height / 2, scale);
        g.pose().popMatrix();
    }

    // --- Zoom -------------------------------------------------------------------------------------------
    // Right-clicking a card shows it big in the middle of the screen, with its keywords explained on the left and
    // any cards it creates on the right (the same sides as the inspect screen). Click anywhere or Esc to close.

    private void zoom(Identifier card, boolean enchanted) {
        this.zoomed = card;
        this.zoomedEnchanted = enchanted;
        this.zoomRelated = craftbound.client.card.CardInspectScreen.associations(card).stream()
                .map(craftbound.client.card.CardInspectScreen.Association::id).distinct().toList();
        this.zoomRelatedPage = 0;
        this.zoomRelatedPages = 1;
        this.zoomPreviousPage = this.zoomNextPage = null;
        this.pressedCard = null;
        this.dragging = false;
        this.inspectOpening.restart();
    }

    private void drawZoom(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (this.zoomed == null) return;
        CardDefinition card = Cards.get(this.zoomed);
        if (card == null) { this.zoomed = null; return; }
        g.fill(0, 0, this.width, this.height, Math.round(200 * inspectOpening.progress()) << 24);
        boolean hasRelated = !zoomRelated.isEmpty();
        float widthScale = hasRelated ? (width - 36F) / (CardRenderer.WIDTH * 1.82F)
                : (width - 40F) / CardRenderer.WIDTH;
        float scale = Math.max(.1F, Math.min(1.25F, Math.min(height * .68F / CardRenderer.HEIGHT, widthScale))) * inspectOpening.scale();
        int cardWidth = Math.round(CardRenderer.WIDTH * scale);
        int cardHeight = Math.round(CardRenderer.HEIGHT * scale);
        float relatedScale = Math.min(1F, scale * .82F);
        int relatedWidth = Math.round(CardRenderer.WIDTH * relatedScale);
        int relatedHeight = Math.round(CardRenderer.HEIGHT * relatedScale);
        int cardX = (width - cardWidth) / 2;
        if (hasRelated) cardX = Math.min(cardX, width - 12 - relatedWidth - 12 - cardWidth);
        cardX = Math.max(4, cardX);
        int cardY = (height - cardHeight) / 2;
        List<CardRenderer.HoverRegion> regions = new ArrayList<>(CardRenderer.draw(g, font,
                CardItem.of(zoomed, zoomedEnchanted), cardX, cardY, scale));

        // Keep readable keyword boxes where they fit. Every keyword remains available on hover.
        int boxWidth = Math.min(150, cardX - 20);
        int boxX = cardX - 12 - boxWidth;
        int boxY = cardY + 10;
        if (boxWidth > 60) {
            for (var keyword : card.keywords()) {
                if (keyword == Keyword.BLOCK) continue;
                List<FormattedCharSequence> lines = new ArrayList<>();
                lines.addAll(font.split(keyword.displayName().copy().withStyle(ChatFormatting.GOLD), boxWidth - 10));
                lines.addAll(font.split(keyword.description(card), boxWidth - 10));
                int boxHeight = lines.size() * font.lineHeight + 10;
                if (boxY + boxHeight > height - 8) break;
                g.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xF0100010);
                g.outline(boxX, boxY, boxWidth, boxHeight, 0xFF5000A0);
                int textY = boxY + 5;
                for (var line : lines) {
                    g.text(font, line, boxX + 5, textY, 0xFFFFFFFF);
                    textY += font.lineHeight;
                }
                boxY += boxHeight + 6;
            }
        }

        zoomPreviousPage = zoomNextPage = null;
        int perPage = Math.max(1, Math.min(2, (height - 64 + 12) / Math.max(1, relatedHeight + 12)));
        zoomRelatedPages = Math.max(1, (zoomRelated.size() + perPage - 1) / perPage);
        zoomRelatedPage = Math.max(0, Math.min(zoomRelatedPage, zoomRelatedPages - 1));
        int first = zoomRelatedPage * perPage, last = Math.min(zoomRelated.size(), first + perPage);
        if (last > first) {
            int relatedX = cardX + cardWidth + 12;
            int columnHeight = (last - first) * relatedHeight + (last - first - 1) * 12 + 32;
            int columnY = Math.max(12, (height - columnHeight) / 2);
            g.text(font, Component.translatable("card_ui.craftbound.creates"), relatedX, columnY, 0xFFFFFFFF);
            for (int i = first; i < last; i++) {
                int relatedY = columnY + 14 + (i - first) * (relatedHeight + 12);
                regions.addAll(CardRenderer.draw(g, font, CardItem.of(zoomRelated.get(i)), relatedX, relatedY, relatedScale));
            }
            if (zoomRelatedPages > 1) {
                int y = columnY + columnHeight - 13;
                zoomPreviousPage = new int[]{relatedX, y - 2, 16, 14};
                zoomNextPage = new int[]{relatedX + relatedWidth - 16, y - 2, 16, 14};
                g.text(font, Component.literal("<"), relatedX + 4, y, zoomRelatedPage > 0 ? 0xFFFFFFFF : 0xFF777777);
                g.text(font, Component.literal(">"), relatedX + relatedWidth - 11, y,
                        zoomRelatedPage + 1 < zoomRelatedPages ? 0xFFFFFFFF : 0xFF777777);
                Component count = Component.literal((zoomRelatedPage + 1) + " / " + zoomRelatedPages);
                g.text(font, count, relatedX + (relatedWidth - font.width(count)) / 2, y, 0xFFE8D5AE);
            }
        }
        for (var region : regions) {
            if (region.contains(mouseX, mouseY)) {
                Tooltips.show(g, font, region.lines(), mouseX, mouseY);
                break;
            }
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.openFilter != 0 && event.isEscape()) { this.openFilter = 0; return true; }
        if (this.zoomed != null && event.isEscape()) {
            this.zoomed = null;
            return true;
        }
        // Enter saves the deck's name.
        if (this.nameBox.isFocused() && event.isConfirmation()) {
            this.nameBox.setFocused(false);
            this.setFocused(null);
            saveName();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.zoomed != null) {
            if (scrollY != 0) zoomRelatedPage = Math.max(0, Math.min(zoomRelatedPages-1,
                    zoomRelatedPage + (scrollY < 0 ? 1 : -1)));
            return true;
        }
        if (this.openFilter != 0) return true;
        if (inside(this.listArea, mouseX, mouseY)) {
            if (overview()) this.hubScroll -= (int) Math.signum(scrollY);
            else this.scroll -= (int) Math.signum(scrollY);
            return true;
        }
        // Scrolling over the cards turns the page.
        if (mouseX >= this.leftX && mouseX < this.leftX + this.leftWidth && mouseY >= this.gridY && mouseY < this.curveY) {
            turnPage(-(int)Math.signum(scrollY));
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
