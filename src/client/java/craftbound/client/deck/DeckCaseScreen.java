package craftbound.client.deck;

import craftbound.binder.BinderContents;
import craftbound.card.CardClass;
import craftbound.card.CardDefinition;
import craftbound.card.Cards;
import craftbound.card.DeckRules;
import craftbound.client.card.CardInspectScreen;
import craftbound.client.card.CardRenderer;
import craftbound.component.ModComponents;
import craftbound.deck.DeckBuilding;
import craftbound.deck.DeckContents;
import craftbound.item.CardItem;
import craftbound.item.ModItems;
import craftbound.network.DeckClassPayload;
import craftbound.network.DeckEditPayload;
import craftbound.sleeve.Sleeves;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

// Building a deck. Left: every card in the binders you're carrying. Right: the deck in this case.
// Clicking a card moves a real copy out of a binder into the deck; clicking a deck row puts it back.
// The server does the moving and checks the deck rules; this screen only asks.
public class DeckCaseScreen extends Screen {
    private static final int COLUMNS = 5;
    private static final int ROWS = 3;
    private static final int CELL_GAP = 4;
    private static final int GRID_WIDTH = COLUMNS * CardRenderer.MINI_WIDTH + (COLUMNS - 1) * CELL_GAP;
    private static final int GRID_HEIGHT = ROWS * CardRenderer.MINI_HEIGHT + (ROWS - 1) * CELL_GAP;
    private static final int LIST_WIDTH = 150;
    private static final int ROW_HEIGHT = 12;
    private static final int PADDING = 10;
    private static final int HEADER = 24;
    private static final int PANEL_WIDTH = PADDING * 3 + GRID_WIDTH + LIST_WIDTH;
    private static final int PANEL_HEIGHT = HEADER + GRID_HEIGHT + 34;

    private static final int WOOD = 0xFF5A2E1E;
    private static final int WOOD_DARK = 0xFF3A1C12;
    private static final int PAGE = 0xFFF2E3C2;
    private static final int POCKET = 0xFFE3D0A8;
    private static final int TEXT_DARK = 0xFF3E231D;

    private final InteractionHand hand;
    private int page = 0;
    private int scroll = 0;

    // Where things were drawn last frame, for clicks.
    private record Spot(int x, int y, int width, int height, Identifier card) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private final List<Spot> pockets = new ArrayList<>();
    private final List<Spot> rows = new ArrayList<>();
    private int[] panel;
    private int[] listArea;
    private int[] classButton;
    private int[] previousButton;
    private int[] nextButton;

    public DeckCaseScreen(InteractionHand hand) {
        super(Component.translatable("item.craftbound.deck_case"));
        this.hand = hand;
    }

    private ItemStack deckCase() {
        return this.minecraft.player == null ? ItemStack.EMPTY : this.minecraft.player.getItemInHand(this.hand);
    }

    private DeckContents deck() {
        return deckCase().getOrDefault(ModComponents.DECK_CONTENTS, DeckContents.EMPTY);
    }

    // How many copies of each card are in all the binders you're carrying, normal and Enchanted.
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

    // Cards to show on the left: anything in your binders or already in the deck, in collection order.
    private List<Identifier> shownCards(Map<Identifier, int[]> available, DeckContents deck) {
        List<Identifier> shown = new ArrayList<>();
        for (Identifier id : Cards.collectible()) {
            if (available.containsKey(id) || deck.copies(id) > 0) {
                shown.add(id);
            }
        }
        return shown;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        if (!deckCase().is(ModItems.DECK_CASE)) {
            this.onClose();
            return;
        }
        DeckContents deck = deck();
        Map<Identifier, int[]> available = available();
        List<Identifier> shown = shownCards(available, deck);
        int perPage = COLUMNS * ROWS;
        int pageCount = Math.max(1, (shown.size() + perPage - 1) / perPage);
        this.page = Math.min(this.page, pageCount - 1);

        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        this.panel = new int[]{left, top, PANEL_WIDTH, PANEL_HEIGHT};
        g.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, WOOD);
        g.outline(left, top, PANEL_WIDTH, PANEL_HEIGHT, WOOD_DARK);

        // Collection grid.
        int gridX = left + PADDING;
        int gridY = top + HEADER;
        g.text(this.font, Component.translatable("deck_ui.craftbound.collection"), gridX, top + 8, 0xFFFFD27A);
        g.fill(gridX - 4, gridY - 4, gridX + GRID_WIDTH + 4, gridY + GRID_HEIGHT + 4, PAGE);
        this.pockets.clear();
        Spot hoveredPocket = null;
        for (int i = 0; i < perPage; i++) {
            int index = this.page * perPage + i;
            int x = gridX + (i % COLUMNS) * (CardRenderer.MINI_WIDTH + CELL_GAP);
            int y = gridY + (i / COLUMNS) * (CardRenderer.MINI_HEIGHT + CELL_GAP);
            g.fill(x, y, x + CardRenderer.MINI_WIDTH, y + CardRenderer.MINI_HEIGHT, POCKET);
            if (index >= shown.size()) {
                continue;
            }
            Identifier id = shown.get(index);
            int[] counts = available.getOrDefault(id, new int[2]);
            CardRenderer.drawMini(g, this.font, id, x, y, counts[0] + counts[1], counts[1] > 0);
            // Grey out cards you can't add right now.
            if (counts[0] + counts[1] == 0 || DeckBuilding.whyNot(deck, id) != null) {
                g.fill(x, y, x + CardRenderer.MINI_WIDTH, y + CardRenderer.MINI_HEIGHT, 0x99000000);
            }
            // How many are already in the deck, top-right.
            if (deck.copies(id) > 0) {
                String inDeck = deck.copies(id) + "/" + DeckRules.maxCopies(Cards.get(id).rarity());
                int badgeWidth = this.font.width(inDeck) + 4;
                int badgeX = x + CardRenderer.MINI_WIDTH - badgeWidth;
                g.fill(badgeX, y, x + CardRenderer.MINI_WIDTH, y + 10, 0xFFE0A21B);
                g.text(this.font, inDeck, badgeX + 2, y + 1, 0xFFFFFFFF);
            }
            Spot spot = new Spot(x, y, CardRenderer.MINI_WIDTH, CardRenderer.MINI_HEIGHT, id);
            this.pockets.add(spot);
            if (spot.contains(mouseX, mouseY)) {
                hoveredPocket = spot;
            }
        }

        // Page arrows and page number under the grid.
        int footerY = gridY + GRID_HEIGHT + 8;
        g.centeredText(this.font, (this.page + 1) + " / " + pageCount, gridX + GRID_WIDTH / 2, footerY + 6, 0xFFE8D6B0);
        this.previousButton = this.page > 0 ? drawButton(g, Component.literal("<"), gridX, footerY, mouseX, mouseY) : null;
        this.nextButton = this.page < pageCount - 1
                ? drawButton(g, Component.literal(">"), gridX + GRID_WIDTH - 18, footerY, mouseX, mouseY) : null;

        // Deck list.
        int listX = gridX + GRID_WIDTH + PADDING;
        g.text(this.font, Component.translatable("deck_ui.craftbound.size", deck.size(), DeckRules.DECK_SIZE),
                listX, top + 8, deck.size() == DeckRules.DECK_SIZE ? 0xFF7DFF5A : 0xFFFFD27A);
        int listTop = gridY;
        int listHeight = GRID_HEIGHT;
        this.listArea = new int[]{listX - 4, listTop - 4, LIST_WIDTH + 8, listHeight + 8};
        g.fill(listX - 4, listTop - 4, listX + LIST_WIDTH + 4, listTop + listHeight + 4, PAGE);

        List<Identifier> inDeck = new ArrayList<>();
        for (Identifier id : Cards.collectible()) {
            if (deck.copies(id) > 0) {
                inDeck.add(id);
            }
        }
        // Sorted by cost, then name, like a deck list.
        inDeck.sort(Comparator.<Identifier>comparingInt(id -> Cards.get(id).cost())
                .thenComparing(id -> Cards.name(id).getString()));
        int visibleRows = listHeight / ROW_HEIGHT;
        this.scroll = Math.max(0, Math.min(this.scroll, inDeck.size() - visibleRows));
        this.rows.clear();
        Spot hoveredRow = null;
        if (inDeck.isEmpty()) {
            g.text(this.font, Component.translatable("deck_ui.craftbound.empty"), listX + 2, listTop + 2, 0xFF8A7458, false);
        }
        for (int r = 0; r < visibleRows && this.scroll + r < inDeck.size(); r++) {
            Identifier id = inDeck.get(this.scroll + r);
            int y = listTop + r * ROW_HEIGHT;
            Spot spot = new Spot(listX, y, LIST_WIDTH, ROW_HEIGHT, id);
            this.rows.add(spot);
            boolean hover = spot.contains(mouseX, mouseY);
            if (hover) {
                hoveredRow = spot;
                g.fill(listX, y, listX + LIST_WIDTH, y + ROW_HEIGHT, 0x30000000);
            }
            drawDeckRow(g, id, deck, listX, y);
        }
        if (inDeck.size() > visibleRows) {
            // A simple scroll bar on the right edge of the list.
            int barHeight = Math.max(10, listHeight * visibleRows / inDeck.size());
            int barY = listTop + (listHeight - barHeight) * this.scroll / Math.max(1, inDeck.size() - visibleRows);
            g.fill(listX + LIST_WIDTH + 1, barY, listX + LIST_WIDTH + 3, barY + barHeight, 0xFF8A5A3A);
        }

        // Class button and sleeve under the deck list.
        Component classLabel = Component.translatable("deck_ui.craftbound.class", deck.deckClass().displayName());
        this.classButton = drawButton(g, classLabel, listX, footerY, mouseX, mouseY);
        Identifier sleeve = deckCase().get(ModComponents.SLEEVE_ID);
        Component sleeveName = Sleeves.name(sleeve == null ? Sleeves.CLASSIC : sleeve);
        g.text(this.font, sleeveName, listX + LIST_WIDTH - this.font.width(sleeveName), footerY + 6, 0xFFE8D6B0);

        // Tooltips.
        if (hoveredPocket != null) {
            g.setComponentTooltipForNextFrame(this.font, pocketTooltip(hoveredPocket.card(), available, deck), mouseX, mouseY);
        } else if (hoveredRow != null) {
            g.setComponentTooltipForNextFrame(this.font, rowTooltip(hoveredRow.card(), deck), mouseX, mouseY);
        } else if (inside(this.classButton, mouseX, mouseY)) {
            g.setComponentTooltipForNextFrame(this.font, List.of(
                    Component.translatable("deck_ui.craftbound.class_hint").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        }
    }

    // One line of the deck list: cost, name in its rarity colour, and how many copies (purple star if any are Enchanted).
    private void drawDeckRow(GuiGraphicsExtractor g, Identifier id, DeckContents deck, int x, int y) {
        CardDefinition card = Cards.get(id);
        String cost = String.valueOf(card.cost());
        g.fill(x + 1, y + 1, x + 12, y + 11, 0xFF17A84F);
        g.centeredText(this.font, cost, x + 7, y + 2, 0xFFFFFFFF);
        Component name = CardItem.of(id).getHoverName();
        g.text(this.font, name, x + 15, y + 2, 0xFFFFFFFF);
        String count = "x" + deck.copies(id);
        int countX = x + LIST_WIDTH - 4 - this.font.width(count);
        g.text(this.font, count, countX, y + 2, TEXT_DARK, false);
        if (deck.cards().count(id, true) > 0) {
            g.text(this.font, "✦", countX - 9, y + 2, 0xFFB070FF, false);
        }
    }

    private List<Component> pocketTooltip(Identifier id, Map<Identifier, int[]> available, DeckContents deck) {
        int[] counts = available.getOrDefault(id, new int[2]);
        List<Component> lines = new ArrayList<>();
        lines.add(CardItem.of(id).getHoverName());
        lines.add(Component.translatable("deck_ui.craftbound.in_binders", counts[0] + counts[1], counts[1]).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("deck_ui.craftbound.in_deck", deck.copies(id),
                DeckRules.maxCopies(Cards.get(id).rarity())).withStyle(ChatFormatting.GRAY));
        String reason = counts[0] + counts[1] == 0 ? "deck_ui.craftbound.none_left" : DeckBuilding.whyNot(deck, id);
        if (reason != null) {
            lines.add(Component.translatable(reason).withStyle(ChatFormatting.RED));
        }
        lines.add(Component.empty());
        lines.add(Component.translatable("deck_ui.craftbound.hint_add").withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("binder_ui.craftbound.hint_inspect").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    private List<Component> rowTooltip(Identifier id, DeckContents deck) {
        List<Component> lines = new ArrayList<>();
        lines.add(CardItem.of(id).getHoverName());
        lines.add(Component.translatable("binder_ui.craftbound.owned", deck.cards().count(id, false)).withStyle(ChatFormatting.GRAY));
        if (deck.cards().count(id, true) > 0) {
            lines.add(Component.translatable("binder_ui.craftbound.owned_enchanted", deck.cards().count(id, true))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        lines.add(Component.empty());
        lines.add(Component.translatable("deck_ui.craftbound.hint_remove").withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("binder_ui.craftbound.hint_inspect").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    private int[] drawButton(GuiGraphicsExtractor g, Component label, int x, int y, int mouseX, int mouseY) {
        int width = Math.max(18, this.font.width(label) + 12);
        int height = 20;
        boolean hover = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        g.fill(x, y, x + width, y + height, hover ? 0xFF8A5A3A : 0xFF6B4423);
        g.outline(x, y, width, height, WOOD_DARK);
        g.centeredText(this.font, label, x + width / 2, y + 6, 0xFFFFFFFF);
        return new int[]{x, y, width, height};
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        boolean mainHand = this.hand == InteractionHand.MAIN_HAND;
        DeckContents deck = deck();
        if (inside(this.previousButton, mouseX, mouseY)) {
            this.page--;
            return true;
        }
        if (inside(this.nextButton, mouseX, mouseY)) {
            this.page++;
            return true;
        }
        if (inside(this.classButton, mouseX, mouseY)) {
            // Step to the next class the current cards allow.
            CardClass[] classes = CardClass.values();
            for (int step = 1; step < classes.length; step++) {
                CardClass next = classes[(deck.deckClass().ordinal() + step) % classes.length];
                if (DeckBuilding.canSwitchTo(deck, next)) {
                    ClientPlayNetworking.send(new DeckClassPayload(mainHand, next.ordinal()));
                    break;
                }
            }
            return true;
        }
        Map<Identifier, int[]> available = available();
        for (Spot pocket : this.pockets) {
            if (!pocket.contains(mouseX, mouseY)) {
                continue;
            }
            int[] counts = available.getOrDefault(pocket.card(), new int[2]);
            if (event.button() == 1) {
                this.minecraft.gui.setScreen(new CardInspectScreen(CardItem.of(pocket.card(), counts[0] == 0 && counts[1] > 0), this));
                return true;
            }
            // Ctrl adds an Enchanted copy. Otherwise a normal one, or Enchanted if that's all you have.
            boolean enchanted = event.hasControlDown() || counts[0] == 0;
            if (counts[enchanted ? 1 : 0] > 0) {
                ClientPlayNetworking.send(new DeckEditPayload(mainHand, pocket.card(), enchanted, true));
            }
            return true;
        }
        for (Spot row : this.rows) {
            if (!row.contains(mouseX, mouseY)) {
                continue;
            }
            if (event.button() == 1) {
                this.minecraft.gui.setScreen(new CardInspectScreen(CardItem.of(row.card(), deck.cards().count(row.card(), true) > 0), this));
                return true;
            }
            boolean enchanted = event.hasControlDown() || deck.cards().count(row.card(), false) == 0;
            if (deck.cards().count(row.card(), enchanted) > 0) {
                ClientPlayNetworking.send(new DeckEditPayload(mainHand, row.card(), enchanted, false));
            }
            return true;
        }
        if (!inside(this.panel, mouseX, mouseY)) {
            this.onClose();
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inside(this.listArea, mouseX, mouseY)) {
            this.scroll -= (int) Math.signum(scrollY);
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
