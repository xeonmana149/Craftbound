package craftbound.client.binder;

import craftbound.client.NewCards;
import craftbound.client.Tooltips;
import com.mojang.blaze3d.platform.InputConstants;
import craftbound.binder.BinderContents;
import craftbound.binder.BinderPages;
import craftbound.card.CardClass;
import craftbound.card.CardRarity;
import craftbound.card.CardCatalog;
import craftbound.binder.Disenchanting;
import craftbound.network.DisenchantExtrasPayload;
import craftbound.card.Cards;
import craftbound.client.card.CardInspectScreen;
import craftbound.client.card.CardRenderer;
import craftbound.component.ModComponents;
import craftbound.item.CardItem;
import craftbound.item.ModItems;
import craftbound.network.FileAllCardsPayload;
import craftbound.network.TakeFromBinderPayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

// An open binder: two facing pages of 3x3 pockets, one numbered pocket for every collectible card.
// Missing cards show as empty pockets, so you can see what's left to collect.
public class BinderScreen extends Screen {
    private static final int POCKET_WIDTH = 36;
    private static final int POCKET_HEIGHT = 48;
    private static final int POCKET_GAP = 4;
    private static final int PAGE_PADDING = 10;
    private static final int PAGE_WIDTH = PAGE_PADDING * 2 + POCKET_WIDTH * 3 + POCKET_GAP * 2;
    private static final int PAGE_HEIGHT = PAGE_PADDING * 2 + POCKET_HEIGHT * 3 + POCKET_GAP * 2;
    private static final int SPINE = 8;
    private static final int BOOK_WIDTH = PAGE_WIDTH * 2 + SPINE;
    private static final int POCKETS_PER_SPREAD = 18;

    private static final int COVER = 0xFF5A2E1E;
    private static final int COVER_DARK = 0xFF3A1C12;
    private static final int PAGE = 0xFFF2E3C2;
    private static final int POCKET = 0xFFE3D0A8;
    private static final int POCKET_EDGE = 0xFFB8A27A;

    private final InteractionHand hand;
    private final Screen parent;
    private int spread = 0;
    private double pageScroll;
    private CardClass selectedClass = CardClass.NEUTRAL;
    private List<BinderPages.Spread> spreads = List.of();
    private CardCatalog.State cachedCatalog;
    private CardClass cachedClass;
    private final List<int[]> classButtons = new ArrayList<>();
    private final List<int[]> rarityButtons = new ArrayList<>();
    private float uiScale = 1;
    private float uiX;
    private float uiY;
    private static final int VIEW_WIDTH = BOOK_WIDTH + 64;
    private static final int VIEW_HEIGHT = PAGE_HEIGHT + 138;

    // Where things were drawn last frame, for clicks.
    private final List<Pocket> pockets = new ArrayList<>();
    private int[] previousButton;
    private int[] nextButton;
    private int[] fileButton;
    private int[] masteryButton;
    private int[] disenchantButton;
    private boolean confirmingDisenchant = false;
    private int[] book;

    private record Pocket(int x, int y, Identifier card) {
    }

    public BinderScreen(InteractionHand hand) { this(hand, null); }

    public BinderScreen(InteractionHand hand, Screen parent) {
        super(Component.translatable("item.craftbound.binder"));
        this.hand = hand;
        this.parent = parent;
    }

    @Override public void onClose() {
        if (this.parent != null && binder().is(ModItems.BINDER)) this.minecraft.gui.setScreen(this.parent);
        else super.onClose();
    }

    // Read the binder from your hand every frame, so the pages update as soon as the server moves cards.
    private ItemStack binder() {
        return this.minecraft.player == null ? ItemStack.EMPTY : this.minecraft.player.getItemInHand(this.hand);
    }

    private int spreadCount() {
        refreshPages();
        return this.spreads.size();
    }

    private void refreshPages() {
        if (this.cachedCatalog == CardCatalog.current() && this.cachedClass == this.selectedClass) return;
        this.spreads = BinderPages.forClass(this.selectedClass);
        this.cachedCatalog = CardCatalog.current();
        this.cachedClass = this.selectedClass;
        this.spread = Math.max(0, Math.min(this.spread, this.spreads.size() - 1));
        clearPageHits();
    }

    private void clearPageHits() {
        this.pockets.clear();
        this.previousButton = null;
        this.nextButton = null;
        this.confirmingDisenchant = false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        ItemStack binder = binder();
        if (!binder.is(ModItems.BINDER)) {
            this.onClose();
            return;
        }
        BinderContents contents = binder.getOrDefault(ModComponents.BINDER_CONTENTS, BinderContents.EMPTY);
        List<Identifier> collection = Cards.collectible();
        this.spread = Math.min(this.spread, spreadCount() - 1);
        BinderPages.Spread current = this.spreads.get(this.spread);
        List<Identifier> visibleCards = current.cards();
        List<Identifier> classCards = collection.stream().filter(id -> Cards.get(id).cardClass() == this.selectedClass).toList();
        int physicalMouseX = mouseX, physicalMouseY = mouseY;
        this.uiScale = Math.min(1.0F, Math.min((this.width - 12.0F) / VIEW_WIDTH, (this.height - 12.0F) / VIEW_HEIGHT));
        this.uiScale = Math.max(0.1F, this.uiScale);
        this.uiX = (this.width - VIEW_WIDTH * this.uiScale) / 2;
        this.uiY = (this.height - VIEW_HEIGHT * this.uiScale) / 2;
        mouseX = (int) Math.floor((mouseX - this.uiX) / this.uiScale);
        mouseY = (int) Math.floor((mouseY - this.uiY) / this.uiScale);
        g.pose().pushMatrix();
        g.pose().translate(this.uiX, this.uiY);
        g.pose().scale(this.uiScale, this.uiScale);

        int bookX = 32;
        int bookY = 90;
        this.book = new int[]{bookX - 16, 0, BOOK_WIDTH + 32, VIEW_HEIGHT};

        // Cover, pages and spine.
        g.fill(bookX - 8, bookY - 38, bookX + BOOK_WIDTH + 8, bookY + PAGE_HEIGHT + 20, COVER);
        g.outline(bookX - 8, bookY - 38, BOOK_WIDTH + 16, PAGE_HEIGHT + 58, COVER_DARK);
        drawNavigation(g, bookX, current.rarity(), contents, mouseX, mouseY);
        CardRenderer.fitText(g, this.font, Component.translatable("binder_ui.craftbound.section", this.selectedClass.displayName(), current.rarity().displayName()),
                bookX + BOOK_WIDTH / 2, bookY - 31, BOOK_WIDTH - 12, 0xFFFFD27A, true);
        g.fill(bookX, bookY, bookX + PAGE_WIDTH, bookY + PAGE_HEIGHT, PAGE);
        g.fill(bookX + PAGE_WIDTH + SPINE, bookY, bookX + BOOK_WIDTH, bookY + PAGE_HEIGHT, PAGE);
        g.fill(bookX + PAGE_WIDTH, bookY - 4, bookX + PAGE_WIDTH + SPINE, bookY + PAGE_HEIGHT + 4, COVER_DARK);

        // Two counters on the cover: normal collection on the left page's side, Enchanted on the right.
        g.centeredText(this.font, Component.translatable("binder_ui.craftbound.collected",
                contents.collected(classCards), classCards.size()), bookX + PAGE_WIDTH / 2, bookY - 15, 0xFFFFD27A);
        g.centeredText(this.font, Component.translatable("binder_ui.craftbound.collected_enchanted",
                        contents.collectedEnchanted(classCards), classCards.size()),
                bookX + PAGE_WIDTH + SPINE + PAGE_WIDTH / 2, bookY - 15, 0xFFD9A8FF);

        // Pockets.
        this.pockets.clear();
        Pocket hovered = null;
        for (int i = 0; i < POCKETS_PER_SPREAD; i++) {
            int page = i / 9;
            int column = i % 3;
            int row = (i % 9) / 3;
            int x = bookX + page * (PAGE_WIDTH + SPINE) + PAGE_PADDING + column * (POCKET_WIDTH + POCKET_GAP);
            int y = bookY + PAGE_PADDING + row * (POCKET_HEIGHT + POCKET_GAP);
            int number = i;

            g.fill(x, y, x + POCKET_WIDTH, y + POCKET_HEIGHT, POCKET);
            g.outline(x, y, POCKET_WIDTH, POCKET_HEIGHT, POCKET_EDGE);
            if (number >= visibleCards.size()) {
                continue;
            }
            Identifier id = visibleCards.get(number);
            Pocket pocket = new Pocket(x, y, id);
            this.pockets.add(pocket);
            if (contents.owns(id)) {
                // New cards glow blue until you hover them.
                boolean fresh = NewCards.isNew(id);
                if (fresh) {
                    NewCards.glow(g, x + 2, y + 2, POCKET_WIDTH - 4, POCKET_HEIGHT - 4);
                }
                drawFilledPocket(g, x, y, id, contents);
                if (fresh) {
                    Component label = Component.translatable("binder_ui.craftbound.new");
                    // Small top-right tab leaves the rarity, stats and quantity footer uncovered.
                    g.fill(x + POCKET_WIDTH - 18, y + 1, x + POCKET_WIDTH - 2, y + 8, 0xFF102A40);
                    g.pose().pushMatrix();
                    g.pose().translate(x + POCKET_WIDTH - 10, y + 1);
                    g.pose().scale(0.65F, 0.65F);
                    g.centeredText(this.font, label, 0, 0, 0xFF7FD0FF);
                    g.pose().popMatrix();
                }
            } else {
                g.centeredText(this.font, "#" + Cards.collectorNumber(id), x + POCKET_WIDTH / 2, y + POCKET_HEIGHT / 2 - 4, 0xFFA08A64);
            }
            if (mouseX >= x && mouseX < x + POCKET_WIDTH && mouseY >= y && mouseY < y + POCKET_HEIGHT) {
                hovered = pocket;
                NewCards.seen(id);
            }
        }
        if (visibleCards.isEmpty()) {
            Component empty = Component.translatable("binder_ui.craftbound.empty_section",
                    this.selectedClass.displayName(), current.rarity().displayName());
            int y = bookY + PAGE_HEIGHT / 2 - 10;
            g.fill(bookX + 6, y - 4, bookX + BOOK_WIDTH - 6, y + 20, PAGE);
            CardRenderer.fitText(g, this.font, empty, bookX + BOOK_WIDTH / 2, y + 3, BOOK_WIDTH - 22, 0xFF695235, true, false);
        }
        // NEW counts follow the current class's browsing order, never collector-number order.
        int newBefore = 0, newAfter = 0;
        for (int page = 0; page < this.spreads.size(); page++) {
            if (page == this.spread) continue;
            for (Identifier id : this.spreads.get(page).cards()) if (contents.owns(id) && NewCards.isNew(id)) {
                if (page < this.spread) newBefore++; else newAfter++;
            }
        }

        // Page numbers, page arrows and the Add all cards button.
        int firstPage = this.spread * 2 + 1;
        g.centeredText(this.font, String.valueOf(firstPage), bookX + PAGE_WIDTH / 2, bookY + PAGE_HEIGHT + 4, 0xFFE8D6B0);
        g.centeredText(this.font, String.valueOf(firstPage + 1), bookX + PAGE_WIDTH + SPINE + PAGE_WIDTH / 2,
                bookY + PAGE_HEIGHT + 4, 0xFFE8D6B0);
        this.previousButton = this.spread > 0 ? drawButton(g, "<", bookX - 30, bookY + PAGE_HEIGHT / 2 - 10, mouseX, mouseY) : null;
        this.nextButton = this.spread < spreadCount() - 1
                ? drawButton(g, ">", bookX + BOOK_WIDTH + 12, bookY + PAGE_HEIGHT / 2 - 10, mouseX, mouseY) : null;
        if (this.previousButton != null && newBefore > 0) {
            NewCards.badge(g, this.font, this.previousButton[0] + this.previousButton[2] - 6, this.previousButton[1] - 6, newBefore);
        }
        if (this.nextButton != null && newAfter > 0) {
            NewCards.badge(g, this.font, this.nextButton[0] + this.nextButton[2] - 6, this.nextButton[1] - 6, newAfter);
        }
        // Add all cards and Disenchant extras, side by side under the book.
        Component fileLabel = Component.translatable("binder_ui.craftbound.file_all");
        Disenchanting.Result extras = Disenchanting.disenchantExtras(contents);
        Component disenchantLabel = this.confirmingDisenchant
                ? Component.translatable("binder_ui.craftbound.disenchant_confirm", extras.disenchanted(), extras.xp())
                : Component.translatable("binder_ui.craftbound.disenchant");
        // Disenchant extras only appears if this binder is over its limits (e.g. filled before limits existed);
        // new cards over the limit are disenchanted automatically as they're filed.
        boolean showDisenchant = extras.disenchanted() > 0;
        int fileWidth = this.font.width(fileLabel) + 12;
        int disenchantWidth = showDisenchant ? this.font.width(disenchantLabel) + 12 + 8 : 0;
        int masteryWidth = this.font.width("Mastery") + 12;
        int buttonsLeft = VIEW_WIDTH / 2 - (fileWidth + disenchantWidth + masteryWidth + 8) / 2;
        int buttonsY = bookY + PAGE_HEIGHT + 24;
        this.fileButton = drawButton(g, fileLabel, buttonsLeft, buttonsY, mouseX, mouseY);
        this.disenchantButton = showDisenchant
                ? drawButton(g, disenchantLabel, buttonsLeft + fileWidth + 8, buttonsY, mouseX, mouseY) : null;

        this.masteryButton = drawButton(g, "Mastery", buttonsLeft + fileWidth + disenchantWidth + 8, buttonsY, mouseX, mouseY);

        g.pose().popMatrix();
        if (hovered != null) {
            Tooltips.show(g, this.font, pocketTooltip(hovered.card(), contents), physicalMouseX, physicalMouseY);
        } else if (inside(this.fileButton, mouseX, mouseY)) {
            Tooltips.show(g, this.font, List.of(Component.translatable("binder_ui.craftbound.add_all_hint")), physicalMouseX, physicalMouseY);
        } else if (inside(this.disenchantButton, mouseX, mouseY)) {
            Component hint = extras.disenchanted() == 0
                    ? Component.translatable("binder_ui.craftbound.disenchant_none")
                    : Component.translatable("binder_ui.craftbound.disenchant_preview", extras.disenchanted(), extras.xp());
            Tooltips.show(g, this.font, List.of(hint, Component.translatable("binder_ui.craftbound.disenchant_hint")
                    .withStyle(ChatFormatting.GRAY)), physicalMouseX, physicalMouseY);
        }
    }

    private void drawNavigation(GuiGraphicsExtractor g, int left, CardRarity rarity, BinderContents contents, int mouseX, int mouseY) {
        this.classButtons.clear();
        this.rarityButtons.clear();
        CardClass[] classes = CardClass.values();
        int gap = 4;
        int classWidth = (BOOK_WIDTH - gap * (classes.length - 1)) / classes.length;
        for (int i = 0; i < classes.length; i++) {
            CardClass choice = classes[i];
            int[] button = navigationButton(g, choice.displayName(), left + i * (classWidth + gap), 6,
                    classWidth, choice == this.selectedClass, mouseX, mouseY, i);
            this.classButtons.add(button);
            boolean fresh = Cards.collectible().stream().anyMatch(id -> Cards.get(id).cardClass() == choice && contents.owns(id) && NewCards.isNew(id));
            if (fresh) g.fill(button[0] + button[2] - 5, button[1] + 2, button[0] + button[2] - 2, button[1] + 5, 0xFF7FD0FF);
        }
        CardRarity[] rarities = CardRarity.values();
        int rarityWidth = (BOOK_WIDTH - gap * (rarities.length - 1)) / rarities.length;
        for (int i = 0; i < rarities.length; i++) {
            CardRarity choice = rarities[i];
            this.rarityButtons.add(navigationButton(g, choice.displayName(), left + i * (rarityWidth + gap), 30,
                    rarityWidth, choice == rarity, mouseX, mouseY, i));
        }
    }

    private int[] navigationButton(GuiGraphicsExtractor g, Component label, int x, int y, int width, boolean selected,
                                   int mouseX, int mouseY, int value) {
        boolean hover = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 20;
        g.fill(x, y, x + width, y + 20, selected ? PAGE : hover ? 0xFF8A5A3A : COVER);
        g.outline(x, y, width, 20, selected ? 0xFFFFD27A : COVER_DARK);
        CardRenderer.fitText(g, this.font, label, x + width / 2, y + 6, width - 8,
                selected ? 0xFF3E231D : 0xFFF2E3C2, true, false);
        return new int[]{x, y, width, 20, value};
    }

    // If you own an Enchanted copy, that's the one on show.
    private void drawFilledPocket(GuiGraphicsExtractor g, int x, int y, Identifier id, BinderContents contents) {
        int total = contents.count(id, false) + contents.count(id, true);
        CardRenderer.drawMini(g, this.font, id, x, y, total, contents.count(id, true) > 0);
    }

    private List<Component> pocketTooltip(Identifier id, BinderContents contents) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("card_ui.craftbound.number", Cards.collectorNumber(id), Cards.collectorTotal()).withStyle(ChatFormatting.GRAY));
        lines.add(Cards.get(id).rarity().displayName().copy().withStyle(Cards.get(id).rarity().color()));
        if (!contents.owns(id)) {
            lines.add(Component.translatable("binder_ui.craftbound.missing").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        lines.add(CardItem.of(id).getHoverName());
        lines.add(Component.translatable("binder_ui.craftbound.owned", contents.count(id, false)).withStyle(ChatFormatting.GRAY));
        if (contents.count(id, true) > 0) {
            lines.add(Component.translatable("binder_ui.craftbound.owned_enchanted", contents.count(id, true))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        lines.add(Component.empty());
        lines.add(Component.translatable("binder_ui.craftbound.hint_take").withStyle(ChatFormatting.DARK_GRAY));
        if (contents.count(id, true) > 0) {
            lines.add(Component.translatable("binder_ui.craftbound.hint_take_enchanted").withStyle(ChatFormatting.DARK_GRAY));
        }
        lines.add(Component.translatable("binder_ui.craftbound.hint_inspect").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    private int[] drawButton(GuiGraphicsExtractor g, String label, int x, int y, int mouseX, int mouseY) {
        return drawButton(g, Component.literal(label), x, y, mouseX, mouseY);
    }

    private int[] drawButton(GuiGraphicsExtractor g, Component label, int x, int y, int mouseX, int mouseY) {
        int width = Math.max(18, this.font.width(label) + 12);
        int height = 20;
        boolean hover = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        g.fill(x, y, x + width, y + height, hover ? 0xFF8A5A3A : 0xFF6B4423);
        g.outline(x, y, width, height, COVER_DARK);
        g.centeredText(this.font, label, x + width / 2, y + 6, 0xFFFFFFFF);
        return new int[]{x, y, width, height};
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = (event.x() - this.uiX) / this.uiScale;
        double mouseY = (event.y() - this.uiY) / this.uiScale;
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && inside(this.masteryButton, mouseX, mouseY)) {
            this.minecraft.gui.setScreen(new craftbound.client.mastery.MasteryScreen(this));return true;
        }
        for (int[] button : this.classButtons) if (inside(button, mouseX, mouseY)) {
            CardClass choice = CardClass.values()[button[4]];
            if (choice != this.selectedClass) {
                this.selectedClass = choice;
                this.spread = 0;
                this.pageScroll = 0;
                refreshPages();
            }
            return true;
        }
        for (int[] button : this.rarityButtons) if (inside(button, mouseX, mouseY)) {
            refreshPages();
            this.pageScroll = 0;
            turnPages(BinderPages.firstSpread(this.spreads, CardRarity.values()[button[4]]) - this.spread);
            return true;
        }
        boolean mainHand = this.hand == InteractionHand.MAIN_HAND;
        if (inside(this.previousButton, mouseX, mouseY)) {
            turnPages(-1);
            return true;
        }
        if (inside(this.nextButton, mouseX, mouseY)) {
            turnPages(1);
            return true;
        }
        if (inside(this.fileButton, mouseX, mouseY)) {
            ClientPlayNetworking.send(new FileAllCardsPayload(mainHand));
            return true;
        }
        // Disenchant asks for a second click to confirm, since the cards are gone afterwards.
        if (inside(this.disenchantButton, mouseX, mouseY)) {
            BinderContents current = binder().getOrDefault(ModComponents.BINDER_CONTENTS, BinderContents.EMPTY);
            if (this.confirmingDisenchant) {
                ClientPlayNetworking.send(new DisenchantExtrasPayload(mainHand));
                this.confirmingDisenchant = false;
            } else if (Disenchanting.disenchantExtras(current).disenchanted() > 0) {
                this.confirmingDisenchant = true;
            }
            return true;
        }
        this.confirmingDisenchant = false;
        BinderContents contents = binder().getOrDefault(ModComponents.BINDER_CONTENTS, BinderContents.EMPTY);
        for (Pocket pocket : this.pockets) {
            if (!inside(new int[]{pocket.x(), pocket.y(), POCKET_WIDTH, POCKET_HEIGHT}, mouseX, mouseY)
                    || !contents.owns(pocket.card())) {
                continue;
            }
            boolean ownsEnchanted = contents.count(pocket.card(), true) > 0;
            // 26.3 numbers mouse buttons the SDL way (left 1, right 3), so use Minecraft's constants.
            if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                // Inspect the best copy you own.
                this.minecraft.gui.setScreen(new CardInspectScreen(CardItem.of(pocket.card(), ownsEnchanted), this));
                return true;
            }
            // Ctrl takes an Enchanted copy. Otherwise normal copies come out first, then Enchanted once they're gone.
            boolean enchanted = event.hasControlDown() || contents.count(pocket.card(), false) == 0;
            if (enchanted && !ownsEnchanted) {
                return true;
            }
            ClientPlayNetworking.send(new TakeFromBinderPayload(mainHand, pocket.card(), enchanted, event.hasShiftDown()));
            return true;
        }
        if (!inside(this.book, mouseX, mouseY)) {
            this.onClose();
        }
        return true;
    }

    private void turnPages(int amount) {
        int next = Math.max(0, Math.min(spreadCount() - 1, this.spread + amount));
        if (next == this.spread) return;
        this.spread = next;
        clearPageHits();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        if (this.pageScroll * scrollY < 0) this.pageScroll = 0;
        this.pageScroll += scrollY;
        int steps = (int) this.pageScroll;
        if (steps != 0) {
            this.pageScroll -= steps;
            turnPages(-steps); // Down goes forward; up goes back.
        }
        if ((this.spread == 0 && scrollY > 0) || (this.spread == spreadCount() - 1 && scrollY < 0)) {
            this.pageScroll = 0;
        }
        return true;
    }

    private static boolean inside(int[] rect, double mouseX, double mouseY) {
        return rect != null && mouseX >= rect[0] && mouseX < rect[0] + rect[2] && mouseY >= rect[1] && mouseY < rect[1] + rect[3];
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
