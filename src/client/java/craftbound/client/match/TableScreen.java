package craftbound.client.match;

import com.mojang.blaze3d.platform.InputConstants;
import craftbound.card.CardClass;
import craftbound.card.CardDefinition;
import craftbound.card.Cards;
import craftbound.card.DeckRules;
import craftbound.client.card.CardRenderer;
import craftbound.component.ModComponents;
import craftbound.deck.DeckBuilding;
import craftbound.deck.DeckContents;
import craftbound.game.HeroPowers;
import craftbound.game.MatchManager;
import craftbound.item.CardItem;
import craftbound.item.ModItems;
import craftbound.network.TableActionPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

// The card table's lobby, like Hearthstone's deck picker: the Deck Cases you're carrying on the left, the chosen
// deck's class, power, cost curve and card list on the right, and at the bottom who to play and READY.
public class TableScreen extends Screen {
    // Remembered between visits, so Ready is one click next time.
    private static int lastSlot = -1;
    private static int lastMode = TableActionPayload.CARD_MASTER;

    private static final int PANEL = 0xF0201C17;
    private static final int PANEL_EDGE = 0xFF4F3D27;
    private static final int ROW = 0xFF2B2620;
    private static final int ROW_SELECTED = 0xFF4A3A22;

    private final BlockPos table;
    private String waiting;
    private String error;
    private boolean sent;
    private long sentAt;

    private record DeckEntry(int slot, ItemStack stack, DeckContents deck, Component name, String problem) {
    }

    private record CardRow(Identifier id, boolean enchanted, int count, int missing) {
    }

    private record CardSpot(int x, int y, int width, int height, Identifier id, boolean enchanted) {
    }

    private final List<DeckEntry> decks = new ArrayList<>();
    private final List<int[]> deckSpots = new ArrayList<>();
    private final List<CardSpot> cardSpots = new ArrayList<>();
    private int selected;
    private int cardScroll;
    private int[] cardMasterButton;
    private int[] playerButton;
    private int[] readyButton;
    private int[] cardListArea;

    public TableScreen(BlockPos table, String waiting, String error) {
        super(Component.translatable("table_ui.craftbound.title"));
        this.table = table;
        this.waiting = waiting;
        this.error = error;
        if (!waiting.isEmpty()) {
            lastMode = TableActionPayload.PLAYER;
        }
    }

    public BlockPos table() {
        return this.table;
    }

    // The server's answer: someone's waiting here, or why Ready didn't work.
    public void update(String waiting, String error) {
        this.waiting = waiting;
        this.error = error;
        this.sent = false;
    }

    @Override
    protected void init() {
        this.decks.clear();
        Inventory inventory = this.minecraft.player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(ModItems.DECK_CASE)) {
                continue;
            }
            DeckContents deck = stack.getOrDefault(ModComponents.DECK_CONTENTS, DeckContents.EMPTY);
            Component name = stack.has(DataComponents.CUSTOM_NAME) ? stack.getHoverName()
                    : deck.deckClass() == CardClass.NEUTRAL ? Component.translatable("table_ui.craftbound.new_deck")
                    : Component.translatable("table_ui.craftbound.class_deck", deck.deckClass().displayName());
            this.decks.add(new DeckEntry(slot, stack, deck, name, DeckBuilding.whyCantPlay(inventory, stack)));
        }
        // Pick the deck used last time, otherwise the first one that can be played.
        this.selected = 0;
        for (int i = 0; i < this.decks.size(); i++) {
            if (this.decks.get(i).slot() == lastSlot) {
                this.selected = i;
                break;
            }
            if (this.decks.get(i).problem() == null && this.decks.get(this.selected).problem() != null) {
                this.selected = i;
            }
        }
    }

    private DeckEntry chosen() {
        return this.decks.isEmpty() ? null : this.decks.get(Math.min(this.selected, this.decks.size() - 1));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int width = Math.min(this.width - 16, 480);
        int height = Math.min(this.height - 12, 300);
        int x0 = (this.width - width) / 2;
        int y0 = (this.height - height) / 2;
        g.fill(x0 - 2, y0 - 2, x0 + width + 2, y0 + height + 2, 0xFF000000);
        g.fill(x0 - 1, y0 - 1, x0 + width + 1, y0 + height + 1, MatchArt.GOLD_DARK);
        g.fill(x0, y0, x0 + width, y0 + height, PANEL);
        MatchArt.outlined(g, this.font, this.title, x0 + width / 2.0F, y0 + 7, MatchArt.GOLD_LIGHT, 1.5F, true);

        int listX = x0 + 8;
        int listWidth = Math.min(160, width / 3);
        int top = y0 + 26;
        int bottomBar = y0 + height - 36;
        drawDeckList(g, listX, top, listWidth, bottomBar - top - 6, mouseX, mouseY);
        int detailX = listX + listWidth + 10;
        drawDetails(g, detailX, top, x0 + width - 8 - detailX, bottomBar - top - 6, mouseX, mouseY);
        drawBottomBar(g, x0 + 8, bottomBar, width - 16, mouseX, mouseY);
        drawHoveredCard(g, x0, width, mouseX, mouseY);
    }

    private void drawDeckList(GuiGraphicsExtractor g, int x, int y, int width, int height, int mouseX, int mouseY) {
        MatchArt.outlined(g, this.font, Component.translatable("table_ui.craftbound.your_decks"), x, y, MatchArt.TEXT_DIM, 1.0F, false);
        this.deckSpots.clear();
        int rowY = y + 12;
        if (this.decks.isEmpty()) {
            for (var line : this.font.split(Component.translatable("table_ui.craftbound.no_decks"), width)) {
                g.text(this.font, line, x, rowY, MatchArt.TEXT);
                rowY += this.font.lineHeight + 1;
            }
            return;
        }
        int rowHeight = 30;
        for (int i = 0; i < this.decks.size() && rowY + rowHeight <= y + height; i++) {
            DeckEntry entry = this.decks.get(i);
            int[] spot = {x, rowY, width, rowHeight - 3};
            this.deckSpots.add(spot);
            boolean hover = inside(spot, mouseX, mouseY);
            boolean chosen = i == this.selected;
            g.fill(x - 1, rowY - 1, x + width + 1, rowY + rowHeight - 2, chosen ? MatchArt.GOLD : 0xFF000000);
            g.fill(x, rowY, x + width, rowY + rowHeight - 3, chosen ? ROW_SELECTED : hover ? 0xFF35302A : ROW);
            g.fill(x, rowY, x + 3, rowY + rowHeight - 3, classColor(entry.deck().deckClass()));
            g.item(entry.stack(), x + 6, rowY + 5);
            CardRenderer.fitText(g, this.font, entry.name().copy().withStyle(ChatFormatting.BOLD), x + 26, rowY + 3, width - 30, MatchArt.TEXT, false);
            Component detail = entry.problem() != null
                    ? Component.translatable(entry.problem(), MatchManager.PRACTICE_MIN_DECK).withStyle(ChatFormatting.RED)
                    : Component.translatable("table_ui.craftbound.deck_line", entry.deck().deckClass().displayName(), entry.deck().size(), DeckRules.DECK_SIZE);
            CardRenderer.fitText(g, this.font, detail, x + 26, rowY + 14, width - 30, MatchArt.TEXT_DIM, false);
            rowY += rowHeight;
        }
    }

    private void drawDetails(GuiGraphicsExtractor g, int x, int y, int width, int height, int mouseX, int mouseY) {
        this.cardSpots.clear();
        DeckEntry entry = chosen();
        g.fill(x - 1, y - 1, x + width + 1, y + height + 1, PANEL_EDGE);
        g.fill(x, y, x + width, y + height, 0xFF17140F);
        if (entry == null) {
            return;
        }
        CardClass cardClass = entry.deck().deckClass();
        int cursor = y + 5;
        CardRenderer.fitText(g, this.font, entry.name().copy().withStyle(ChatFormatting.BOLD), x + 6, cursor, width - 70, MatchArt.GOLD_LIGHT, false);
        Component count = Component.translatable("table_ui.craftbound.card_count", entry.deck().size(), DeckRules.DECK_SIZE);
        g.text(this.font, count, x + width - 6 - this.font.width(count), cursor, MatchArt.TEXT_DIM);
        cursor += 12;
        // The class and its power.
        String chip = cardClass.displayName().getString().toUpperCase(Locale.ROOT);
        int chipWidth = this.font.width(chip) + 8;
        g.fill(x + 6, cursor, x + 6 + chipWidth, cursor + 11, classColor(cardClass));
        g.text(this.font, chip, x + 10, cursor + 2, 0xFFFFFFFF);
        HeroPowers.Power power = switch (cardClass) {
            case FIGHTER -> HeroPowers.Power.CRAFT;
            case BUILDER -> HeroPowers.Power.BUILD;
            case FARMER -> HeroPowers.Power.SOW;
            case NEUTRAL -> HeroPowers.Power.NONE;
        };
        if (power != HeroPowers.Power.NONE) {
            int powerX = x + 12 + chipWidth;
            g.pose().pushMatrix();
            g.pose().translate(powerX, cursor - 2);
            g.pose().scale(0.8F, 0.8F);
            g.item(new ItemStack(BuiltInRegistries.ITEM.getValue(power.icon())), 0, 0);
            g.pose().popMatrix();
            Component name = power == HeroPowers.Power.SOW ? Component.translatable("table_ui.craftbound.sow_reap") : power.displayName();
            Component text = Component.empty().append(name.copy().withStyle(ChatFormatting.GOLD)).append(": ").append(power.description());
            int textX = powerX + 16;
            int lineY = cursor + 1;
            for (var line : this.font.split(text, x + width - 6 - textX)) {
                g.text(this.font, line, textX, lineY, MatchArt.TEXT);
                lineY += this.font.lineHeight;
            }
            cursor = Math.max(cursor + 14, lineY + 2);
        } else {
            cursor += 14;
        }
        // The cost curve: how many cards cost 0, 1, 2... 7+.
        List<CardRow> rows = rows(entry);
        int[] curve = new int[8];
        for (CardRow row : rows) {
            curve[Math.min(7, Cards.get(row.id()).cost())] += row.count();
        }
        int most = 1;
        for (int value : curve) {
            most = Math.max(most, value);
        }
        int barWidth = Math.min(16, (width - 20) / 8 - 3);
        int curveHeight = 26;
        int curveX = x + 8;
        for (int i = 0; i < 8; i++) {
            int bx = curveX + i * (barWidth + 3);
            int barHeight = Math.round(curveHeight * curve[i] / (float) most);
            g.fill(bx, cursor + curveHeight - barHeight, bx + barWidth, cursor + curveHeight, 0xFF17A84F);
            g.fill(bx, cursor + curveHeight, bx + barWidth, cursor + curveHeight + 1, PANEL_EDGE);
            String label = i == 7 ? "7+" : String.valueOf(i);
            g.text(this.font, label, bx + barWidth / 2 - this.font.width(label) / 2, cursor + curveHeight + 3, MatchArt.TEXT_DIM);
        }
        cursor += curveHeight + 15;
        // The card list, cheapest first, like a Hearthstone deck list.
        int listTop = cursor;
        int listBottom = y + height - 4;
        int rowHeight = 12;
        int visible = Math.max(1, (listBottom - listTop) / rowHeight);
        // Two columns when there's room; the list scrolls when it's longer than that.
        int columns = width >= 230 ? 2 : 1;
        int perColumn = Math.max(1, (rows.size() + columns - 1) / columns);
        int maxScroll = Math.max(0, perColumn - visible);
        this.cardScroll = Math.max(0, Math.min(this.cardScroll, maxScroll));
        this.cardListArea = new int[]{x, listTop, width, listBottom - listTop};
        int columnWidth = (width - 12 - (columns - 1) * 6) / columns;
        for (int i = 0; i < rows.size(); i++) {
            int column = i / perColumn;
            int indexInColumn = i % perColumn - this.cardScroll;
            if (indexInColumn < 0 || indexInColumn >= visible) {
                continue;
            }
            CardRow row = rows.get(i);
            CardDefinition card = Cards.get(row.id());
            int rx = x + 6 + column * (columnWidth + 6);
            int ry = listTop + indexInColumn * rowHeight;
            CardSpot spot = new CardSpot(rx, ry, columnWidth, rowHeight - 1, row.id(), row.enchanted());
            this.cardSpots.add(spot);
            boolean hover = mouseX >= rx && mouseX < rx + columnWidth && mouseY >= ry && mouseY < ry + rowHeight - 1;
            g.fill(rx, ry, rx + columnWidth, ry + rowHeight - 1, hover ? 0xFF3A3328 : ROW);
            MatchArt.cost(g, this.font, rx + 1, ry + 1, 9, card.cost());
            Component name = row.enchanted() ? Component.translatable("card.craftbound.enchanted", Cards.name(row.id())) : Cards.name(row.id());
            int nameColor = 0xFF000000 | card.rarity().rgb();
            CardRenderer.fitText(g, this.font, name, rx + 14, ry + 2, columnWidth - 34, nameColor, false);
            String copies = "x" + row.count();
            g.text(this.font, copies, rx + columnWidth - 4 - this.font.width(copies), ry + 2, row.missing() > 0 ? 0xFFFF5A4A : MatchArt.TEXT_DIM);
        }
    }

    private List<CardRow> rows(DeckEntry entry) {
        List<CardRow> rows = new ArrayList<>();
        Inventory inventory = this.minecraft.player.getInventory();
        for (Identifier id : Cards.collectible()) {
            for (boolean enchanted : new boolean[]{false, true}) {
                int count = entry.deck().cards().count(id, enchanted);
                if (count > 0) {
                    int missing = entry.deck().list() ? DeckBuilding.missing(inventory, entry.deck(), id, enchanted) : 0;
                    rows.add(new CardRow(id, enchanted, count, missing));
                }
            }
        }
        rows.sort(Comparator.comparingInt((CardRow row) -> Cards.get(row.id()).cost()).thenComparing(row -> Cards.name(row.id()).getString()));
        return rows;
    }

    private void drawBottomBar(GuiGraphicsExtractor g, int x, int y, int width, int mouseX, int mouseY) {
        int buttonWidth = Math.min(110, (width - 110) / 2 - 6);
        this.cardMasterButton = new int[]{x, y + 8, buttonWidth, 22};
        this.playerButton = new int[]{x + buttonWidth + 6, y + 8, buttonWidth, 22};
        this.readyButton = new int[]{x + width - 100, y + 6, 100, 26};
        drawToggle(g, this.cardMasterButton, Component.translatable("table_ui.craftbound.vs_card_master"), lastMode == TableActionPayload.CARD_MASTER, mouseX, mouseY);
        drawToggle(g, this.playerButton, Component.translatable("table_ui.craftbound.vs_player"), lastMode == TableActionPayload.PLAYER, mouseX, mouseY);
        DeckEntry entry = chosen();
        boolean canReady = entry != null && entry.problem() == null && !this.sent;
        boolean hover = inside(this.readyButton, mouseX, mouseY);
        Component ready = Component.translatable(this.sent ? "table_ui.craftbound.readying" : "table_ui.craftbound.ready");
        if (canReady) {
            float glow = 0.5F + 0.5F * (float) Math.sin(System.currentTimeMillis() / 300.0);
            MatchArt.button(g, this.font, ready, this.readyButton[0], this.readyButton[1], 100, 26,
                    hover ? 0xFF8CF06A : 0xFF6BD34A, 0xFF2E8A1E, 0xFFFFFFFF, glow * 0.6F);
        } else {
            MatchArt.button(g, this.font, ready, this.readyButton[0], this.readyButton[1], 100, 26, 0xFF4A4844, 0xFF33312E, 0xFF9E978B, 0);
        }
        // A note above the buttons: someone waiting here, or why you can't play.
        Component note = null;
        int color = MatchArt.TEXT_DIM;
        if (!this.error.isEmpty()) {
            note = Component.translatable(this.error, MatchManager.PRACTICE_MIN_DECK);
            color = 0xFFFF6A5A;
        } else if (!this.waiting.isEmpty()) {
            note = Component.translatable("table_ui.craftbound.someone_waiting", this.waiting);
            color = MatchArt.GOLD_LIGHT;
        } else if (lastMode == TableActionPayload.PLAYER) {
            note = Component.translatable("table_ui.craftbound.player_hint");
        } else {
            note = Component.translatable("table_ui.craftbound.card_master_hint");
        }
        CardRenderer.fitText(g, this.font, note, x, y - 4, width, color, false);
    }

    private void drawToggle(GuiGraphicsExtractor g, int[] b, Component text, boolean on, int mouseX, int mouseY) {
        boolean hover = inside(b, mouseX, mouseY);
        int topColor = on ? (hover ? 0xFFFFE07A : 0xFFFFD25A) : (hover ? 0xFF5A5650 : 0xFF4A4844);
        int bottomColor = on ? 0xFFE2A52A : 0xFF33312E;
        MatchArt.button(g, this.font, text, b[0], b[1], b[2], b[3], topColor, bottomColor, on ? 0xFFFFFFFF : 0xFFCFC6B8, 0);
    }

    // Hovering a card in the list shows it full size beside the panel.
    private void drawHoveredCard(GuiGraphicsExtractor g, int panelX, int panelWidth, int mouseX, int mouseY) {
        for (CardSpot spot : this.cardSpots) {
            if (mouseX < spot.x() || mouseX >= spot.x() + spot.width() || mouseY < spot.y() || mouseY >= spot.y() + spot.height()) {
                continue;
            }
            float scale = Math.min(1.0F, (this.height - 20) / (float) CardRenderer.HEIGHT);
            int w = Math.round(CardRenderer.WIDTH * scale);
            int h = Math.round(CardRenderer.HEIGHT * scale);
            int x = panelX + panelWidth + 8 + w <= this.width ? panelX + panelWidth + 8
                    : panelX - 8 - w >= 0 ? panelX - 8 - w : Math.min(this.width - w - 4, mouseX + 12);
            int y = Math.max(4, Math.min(this.height - h - 4, mouseY - h / 2));
            CardRenderer.draw(g, this.font, CardItem.of(spot.id(), spot.enchanted()), x, y, scale);
            return;
        }
    }

    private static int classColor(CardClass cardClass) {
        return switch (cardClass) {
            case FIGHTER -> 0xFFB23A2E;
            case BUILDER -> 0xFF3F62B0;
            case FARMER -> 0xFF4E8A2A;
            case NEUTRAL -> 0xFF5D5E66;
        };
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return true;
        }
        double mouseX = event.x();
        double mouseY = event.y();
        for (int i = 0; i < this.deckSpots.size(); i++) {
            if (inside(this.deckSpots.get(i), mouseX, mouseY)) {
                this.selected = i;
                this.cardScroll = 0;
                this.error = "";
                MatchSounds.play("ui.button.click", 1.2F, 0.4F);
                return true;
            }
        }
        if (inside(this.cardMasterButton, mouseX, mouseY)) {
            lastMode = TableActionPayload.CARD_MASTER;
            MatchSounds.play("ui.button.click", 1.0F, 0.4F);
            return true;
        }
        if (inside(this.playerButton, mouseX, mouseY)) {
            lastMode = TableActionPayload.PLAYER;
            MatchSounds.play("ui.button.click", 1.0F, 0.4F);
            return true;
        }
        DeckEntry entry = chosen();
        if (inside(this.readyButton, mouseX, mouseY) && entry != null && entry.problem() == null && !this.sent) {
            lastSlot = entry.slot();
            this.sent = true;
            this.sentAt = System.currentTimeMillis();
            this.error = "";
            MatchSounds.play("block.note_block.bell", 1.2F, 0.6F);
            ClientPlayNetworking.send(new TableActionPayload(this.table, lastMode, entry.slot()));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inside(this.cardListArea, mouseX, mouseY)) {
            this.cardScroll -= (int) Math.signum(scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void tick() {
        // If the server never answers, let Ready be pressed again.
        if (this.sent && System.currentTimeMillis() - this.sentAt > 5000) {
            this.sent = false;
        }
    }

    private static boolean inside(int[] rect, double mouseX, double mouseY) {
        return rect != null && mouseX >= rect[0] && mouseX < rect[0] + rect[2] && mouseY >= rect[1] && mouseY < rect[1] + rect[3];
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
