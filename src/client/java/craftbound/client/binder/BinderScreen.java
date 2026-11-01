package craftbound.client.binder;

import craftbound.binder.BinderContents;
import craftbound.card.CardDefinition;
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
    private static final int CARD_FRAME = 0xFF7A4434;
    private static final int CARD_SKY = 0xFF5BC8E0;
    private static final int ENCHANTED_EDGE = 0xFFB070FF;

    private final InteractionHand hand;
    private int spread = 0;

    // Where things were drawn last frame, for clicks.
    private final List<Pocket> pockets = new ArrayList<>();
    private int[] previousButton;
    private int[] nextButton;
    private int[] fileButton;
    private int[] book;

    private record Pocket(int x, int y, Identifier card) {
    }

    public BinderScreen(InteractionHand hand) {
        super(Component.translatable("item.craftbound.binder"));
        this.hand = hand;
    }

    // Read the binder from your hand every frame, so the pages update as soon as the server moves cards.
    private ItemStack binder() {
        return this.minecraft.player == null ? ItemStack.EMPTY : this.minecraft.player.getItemInHand(this.hand);
    }

    private int spreadCount() {
        return Math.max(1, (Cards.collectible().size() + POCKETS_PER_SPREAD - 1) / POCKETS_PER_SPREAD);
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

        int bookX = (this.width - BOOK_WIDTH) / 2;
        int bookY = (this.height - PAGE_HEIGHT) / 2;
        this.book = new int[]{bookX - 8, bookY - 22, BOOK_WIDTH + 16, PAGE_HEIGHT + 50};

        // Cover, pages and spine.
        g.fill(bookX - 8, bookY - 22, bookX + BOOK_WIDTH + 8, bookY + PAGE_HEIGHT + 28, COVER);
        g.outline(bookX - 8, bookY - 22, BOOK_WIDTH + 16, PAGE_HEIGHT + 50, COVER_DARK);
        g.fill(bookX, bookY, bookX + PAGE_WIDTH, bookY + PAGE_HEIGHT, PAGE);
        g.fill(bookX + PAGE_WIDTH + SPINE, bookY, bookX + BOOK_WIDTH, bookY + PAGE_HEIGHT, PAGE);
        g.fill(bookX + PAGE_WIDTH, bookY - 4, bookX + PAGE_WIDTH + SPINE, bookY + PAGE_HEIGHT + 4, COVER_DARK);

        // Two counters on the cover: normal collection on the left page's side, Enchanted on the right.
        g.centeredText(this.font, Component.translatable("binder_ui.craftbound.collected",
                contents.collected(collection), collection.size()), bookX + PAGE_WIDTH / 2, bookY - 15, 0xFFFFD27A);
        g.centeredText(this.font, Component.translatable("binder_ui.craftbound.collected_enchanted",
                        contents.collectedEnchanted(collection), collection.size()),
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
            int number = this.spread * POCKETS_PER_SPREAD + i;

            g.fill(x, y, x + POCKET_WIDTH, y + POCKET_HEIGHT, POCKET);
            g.outline(x, y, POCKET_WIDTH, POCKET_HEIGHT, POCKET_EDGE);
            if (number >= collection.size()) {
                continue;
            }
            Identifier id = collection.get(number);
            Pocket pocket = new Pocket(x, y, id);
            this.pockets.add(pocket);
            if (contents.owns(id)) {
                drawFilledPocket(g, x, y, id, contents);
            } else {
                g.centeredText(this.font, "#" + (number + 1), x + POCKET_WIDTH / 2, y + POCKET_HEIGHT / 2 - 4, 0xFFA08A64);
            }
            if (mouseX >= x && mouseX < x + POCKET_WIDTH && mouseY >= y && mouseY < y + POCKET_HEIGHT) {
                hovered = pocket;
            }
        }

        // Page numbers, page arrows and the File all button.
        int firstPage = this.spread * 2 + 1;
        g.centeredText(this.font, String.valueOf(firstPage), bookX + PAGE_WIDTH / 2, bookY + PAGE_HEIGHT + 4, 0xFFE8D6B0);
        g.centeredText(this.font, String.valueOf(firstPage + 1), bookX + PAGE_WIDTH + SPINE + PAGE_WIDTH / 2,
                bookY + PAGE_HEIGHT + 4, 0xFFE8D6B0);
        this.previousButton = this.spread > 0 ? drawButton(g, "<", bookX - 30, bookY + PAGE_HEIGHT / 2 - 10, mouseX, mouseY) : null;
        this.nextButton = this.spread < spreadCount() - 1
                ? drawButton(g, ">", bookX + BOOK_WIDTH + 12, bookY + PAGE_HEIGHT / 2 - 10, mouseX, mouseY) : null;
        Component fileLabel = Component.translatable("binder_ui.craftbound.file_all");
        int fileWidth = this.font.width(fileLabel) + 12;
        this.fileButton = drawButton(g, fileLabel, this.width / 2 - fileWidth / 2, bookY + PAGE_HEIGHT + 34, mouseX, mouseY);

        if (hovered != null) {
            g.setComponentTooltipForNextFrame(this.font, pocketTooltip(hovered.card(), contents), mouseX, mouseY);
        }
    }

    // A miniature card: frame, sky, the card's placeholder art, a rarity gem and how many you own.
    // If you own an Enchanted copy, that's the one on show: purple edge and a shimmer sweeping across it.
    private void drawFilledPocket(GuiGraphicsExtractor g, int x, int y, Identifier id, BinderContents contents) {
        CardDefinition card = Cards.get(id);
        boolean hasEnchanted = contents.count(id, true) > 0;
        g.fill(x + 3, y + 3, x + POCKET_WIDTH - 3, y + POCKET_HEIGHT - 3, CARD_FRAME);
        g.fill(x + 6, y + 6, x + POCKET_WIDTH - 6, y + 30, card.isMob() ? CARD_SKY : 0xFF6A7FC0);
        g.fill(x + 6, y + 33, x + 10, y + 37, 0xFF000000 | card.rarity().rgb());
        g.item(CardRenderer.placeholderArt(id, card), x + POCKET_WIDTH / 2 - 8, y + 10);
        if (hasEnchanted) {
            drawPocketShimmer(g, x + 3, y + 3, POCKET_WIDTH - 6, POCKET_HEIGHT - 6);
            g.outline(x + 2, y + 2, POCKET_WIDTH - 4, POCKET_HEIGHT - 4, ENCHANTED_EDGE);
        }
        int total = contents.count(id, false) + contents.count(id, true);
        String count = "x" + total;
        g.text(this.font, count, x + POCKET_WIDTH - 5 - this.font.width(count), y + POCKET_HEIGHT - 13, 0xFFFFFFFF);
    }

    // The same purple sweep as an inspected Enchanted card, shrunk to fit a pocket.
    private static void drawPocketShimmer(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        float progress = (System.currentTimeMillis() % 2000L) / 2000.0F;
        int bandCenter = x - 10 + (int) (progress * (width + 20));
        for (int dx = -8; dx <= 8; dx++) {
            int column = bandCenter + dx;
            if (column < x || column >= x + width) {
                continue;
            }
            int alpha = (int) (0x60 * (1.0F - Math.abs(dx) / 8.0F));
            g.fill(column, y, column + 1, y + height, (alpha << 24) | 0xB070FF);
        }
    }

    private List<Component> pocketTooltip(Identifier id, BinderContents contents) {
        List<Component> lines = new ArrayList<>();
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
        double mouseX = event.x();
        double mouseY = event.y();
        boolean mainHand = this.hand == InteractionHand.MAIN_HAND;
        if (inside(this.previousButton, mouseX, mouseY)) {
            this.spread--;
            return true;
        }
        if (inside(this.nextButton, mouseX, mouseY)) {
            this.spread++;
            return true;
        }
        if (inside(this.fileButton, mouseX, mouseY)) {
            ClientPlayNetworking.send(new FileAllCardsPayload(mainHand));
            return true;
        }
        BinderContents contents = binder().getOrDefault(ModComponents.BINDER_CONTENTS, BinderContents.EMPTY);
        for (Pocket pocket : this.pockets) {
            if (!inside(new int[]{pocket.x(), pocket.y(), POCKET_WIDTH, POCKET_HEIGHT}, mouseX, mouseY)
                    || !contents.owns(pocket.card())) {
                continue;
            }
            boolean ownsEnchanted = contents.count(pocket.card(), true) > 0;
            if (event.button() == 1) {
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

    private static boolean inside(int[] rect, double mouseX, double mouseY) {
        return rect != null && mouseX >= rect[0] && mouseX < rect[0] + rect[2] && mouseY >= rect[1] && mouseY < rect[1] + rect[3];
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
