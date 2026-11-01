package craftbound.client.card;

import craftbound.client.Tooltips;
import craftbound.card.BoosterPacks;
import craftbound.card.CardRarity;
import craftbound.card.Cards;
import craftbound.item.CardItem;
import craftbound.sleeve.Sleeves;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

// Opening a booster pack: the cards lie face down in a row, least rare on the left and rarest on the right.
// Click one to flip it, or Reveal all to flip them left to right. Rare and better cards flash in their colour.
public class PackOpeningScreen extends Screen {
    private static final float FLIP_SECONDS = 0.35F;
    private static final float STAGGER_SECONDS = 0.2F;
    private static final float FLASH_SECONDS = 0.9F;
    private static final int GAP = 12;
    private static final float NOT_FLIPPED = -1.0F;
    private static final int ENCHANTED_GLOW = 0xB070FF;

    private final List<ItemStack> cards = new ArrayList<>();
    private final List<CardRarity> rarities = new ArrayList<>();
    private final List<Boolean> enchanted = new ArrayList<>();
    // When each card starts flipping, in seconds since the screen opened. NOT_FLIPPED until you flip it.
    private final float[] flipStart;
    private final boolean[] flipSoundPlayed;
    private final boolean[] revealSoundPlayed;
    private float time = 0.0F;
    private long lastFrameNanos = System.nanoTime();

    // Where things were drawn last frame, for clicks.
    private final List<int[]> cardRects = new ArrayList<>();
    private int[] revealAllButton;
    private int[] doneButton;

    public PackOpeningScreen(List<BoosterPacks.Pull> pulls) {
        super(Component.translatable("item.craftbound.booster_pack"));
        List<BoosterPacks.Pull> sorted = new ArrayList<>(pulls);
        // Least rare first; within a rarity, normal before Enchanted.
        sorted.sort(Comparator.<BoosterPacks.Pull>comparingInt(pull -> Cards.get(pull.card()).rarity().ordinal())
                .thenComparing(BoosterPacks.Pull::enchanted));
        for (BoosterPacks.Pull pull : sorted) {
            this.cards.add(CardItem.of(pull.card(), pull.enchanted()));
            this.rarities.add(Cards.get(pull.card()).rarity());
            this.enchanted.add(pull.enchanted());
        }
        this.flipStart = new float[this.cards.size()];
        Arrays.fill(this.flipStart, NOT_FLIPPED);
        this.flipSoundPlayed = new boolean[this.cards.size()];
        this.revealSoundPlayed = new boolean[this.cards.size()];
    }

    private boolean started(int i) {
        return this.flipStart[i] != NOT_FLIPPED && this.time >= this.flipStart[i];
    }

    // 0 = face down, 1 = fully face up.
    private float flipProgress(int i) {
        if (!started(i)) {
            return 0.0F;
        }
        return Math.min(1.0F, (this.time - this.flipStart[i]) / FLIP_SECONDS);
    }

    private boolean allRevealed() {
        for (int i = 0; i < this.cards.size(); i++) {
            if (flipProgress(i) < 1.0F) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        long now = System.nanoTime();
        float seconds = Math.min((now - this.lastFrameNanos) / 1_000_000_000.0F, 0.1F);
        this.time += seconds;
        this.lastFrameNanos = now;

        int count = this.cards.size();
        float scale = Math.min(1.25F, Math.min(
                (this.width - 40 - GAP * (count - 1)) / (float) (count * CardRenderer.WIDTH),
                (this.height - 90) / (float) CardRenderer.HEIGHT));
        int cardWidth = Math.round(CardRenderer.WIDTH * scale);
        int cardHeight = Math.round(CardRenderer.HEIGHT * scale);
        int rowWidth = count * cardWidth + (count - 1) * GAP;
        int startX = (this.width - rowWidth) / 2;
        int y = (this.height - cardHeight) / 2 - 6;

        g.centeredText(this.font, this.title, this.width / 2, y - 18, 0xFFFFFFFF);

        this.cardRects.clear();
        List<CardRenderer.HoverRegion> regions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int x = startX + i * (cardWidth + GAP);
            this.cardRects.add(new int[]{x, y, cardWidth, cardHeight});
            float progress = flipProgress(i);
            playSounds(i, progress);

            boolean hovered = mouseX >= x && mouseX < x + cardWidth && mouseY >= y && mouseY < y + cardHeight;
            // Face-down cards lift a little under the mouse. Flipping cards rise and squash sideways, swapping
            // from back to face halfway through, so it looks like they turn over.
            float lift = progress == 0.0F && hovered ? 4.0F : (float) Math.sin(progress * Math.PI) * 10.0F;
            float squash = (float) Math.abs(Math.cos(progress * Math.PI));
            if (squash < 0.02F) {
                continue;
            }
            float centerX = x + cardWidth / 2.0F;
            g.pose().pushMatrix();
            g.pose().translate(centerX, -lift);
            g.pose().scale(squash, 1.0F);
            g.pose().translate(-centerX, 0.0F);
            if (progress < 0.5F) {
                CardRenderer.drawBack(g, Sleeves.get(Sleeves.CLASSIC), x, y, scale);
            } else {
                List<CardRenderer.HoverRegion> cardRegions = CardRenderer.draw(g, this.font, this.cards.get(i), x, y, scale);
                if (progress >= 1.0F) {
                    regions.addAll(cardRegions);
                }
            }
            g.pose().popMatrix();

            if (progress >= 1.0F) {
                drawRevealFlash(g, i, x, y, cardWidth, cardHeight);
            } else if (progress == 0.0F) {
                int liftedY = y - Math.round(lift);
                // Enchanted cards always shimmer through the back, hovered or not.
                if (this.enchanted.get(i)) {
                    CardRenderer.drawDiagonalShimmer(g, x, liftedY, cardWidth, cardHeight, Math.round(20 * scale), 0x70);
                }
                if (hovered) {
                    drawHoverGlow(g, i, x, liftedY, cardWidth, cardHeight);
                }
            }
        }

        // Buttons under the row.
        int buttonY = y + cardHeight + 14;
        this.revealAllButton = null;
        this.doneButton = null;
        boolean anyFaceDown = false;
        for (float start : this.flipStart) {
            anyFaceDown |= start == NOT_FLIPPED;
        }
        if (anyFaceDown) {
            this.revealAllButton = drawButton(g, Component.translatable("pack_ui.craftbound.reveal_all"), buttonY, mouseX, mouseY);
        } else if (allRevealed()) {
            this.doneButton = drawButton(g, Component.translatable("pack_ui.craftbound.done"), buttonY, mouseX, mouseY);
        }

        for (CardRenderer.HoverRegion region : regions) {
            if (region.contains(mouseX, mouseY)) {
                Tooltips.show(g, this.font, region.lines(), mouseX, mouseY);
                break;
            }
        }
    }

    // Hovering a face-down card hints at what's under it: Rare, Epic and Legendary cards glow in their colour
    // (an Enchanted Common glows Enchanted purple). Plain Commons stay dark.
    // The glow is a bright solid border with a wide halo fading out around it, pulsing gently.
    private void drawHoverGlow(GuiGraphicsExtractor g, int i, int x, int y, int width, int height) {
        CardRarity rarity = this.rarities.get(i);
        int color = rarity != CardRarity.COMMON ? rarity.rgb() : this.enchanted.get(i) ? ENCHANTED_GLOW : -1;
        if (color == -1) {
            return;
        }
        int bright = lighten(color, 0.35F);
        float pulse = 0.8F + 0.2F * (float) Math.sin(this.time * 5.0F);
        g.outline(x - 1, y - 1, width + 2, height + 2, 0xFF000000 | bright);
        g.outline(x - 2, y - 2, width + 4, height + 4, 0xFF000000 | bright);
        int rings = 10;
        for (int ring = 1; ring <= rings; ring++) {
            int alpha = (int) (0xE0 * pulse * (1.0F - ring / (float) (rings + 1)));
            int grow = 2 + ring;
            g.outline(x - grow, y - grow, width + grow * 2, height + grow * 2, (alpha << 24) | bright);
        }
    }

    // Mixes a colour towards white, so glows read clearly against the dark background.
    private static int lighten(int rgb, float amount) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        red += (int) ((255 - red) * amount);
        green += (int) ((255 - green) * amount);
        blue += (int) ((255 - blue) * amount);
        return (red << 16) | (green << 8) | blue;
    }

    // A ring of the card's rarity colour that fades out just after it turns face up. Commons don't flash.
    private void drawRevealFlash(GuiGraphicsExtractor g, int i, int x, int y, int width, int height) {
        CardRarity rarity = this.rarities.get(i);
        float age = this.time - this.flipStart[i] - FLIP_SECONDS;
        if (rarity == CardRarity.COMMON || age < 0.0F || age > FLASH_SECONDS) {
            return;
        }
        float fade = 1.0F - age / FLASH_SECONDS;
        for (int ring = 1; ring <= 5; ring++) {
            int alpha = (int) (0xC0 * fade / ring);
            int grow = ring * 2;
            g.outline(x - grow, y - grow, width + grow * 2, height + grow * 2, (alpha << 24) | rarity.rgb());
        }
    }

    // A page-turn as each card starts to flip, and a rarity sound as its face appears.
    private void playSounds(int i, float progress) {
        if (!started(i)) {
            return;
        }
        if (!this.flipSoundPlayed[i]) {
            this.flipSoundPlayed[i] = true;
            playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F);
        }
        if (progress >= 0.5F && !this.revealSoundPlayed[i]) {
            this.revealSoundPlayed[i] = true;
            switch (this.rarities.get(i)) {
                case RARE -> playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F);
                case EPIC -> playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F);
                case LEGENDARY -> playSound(SoundEvents.PLAYER_LEVELUP, 1.0F);
                default -> {
                }
            }
        }
    }

    private void playSound(SoundEvent sound, float pitch) {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
    }

    private int[] drawButton(GuiGraphicsExtractor g, Component label, int y, int mouseX, int mouseY) {
        int width = this.font.width(label) + 16;
        int height = 20;
        int x = this.width / 2 - width / 2;
        boolean hover = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        g.fill(x, y, x + width, y + height, hover ? 0xFF8A5A3A : 0xFF6B4423);
        g.outline(x, y, width, height, 0xFF3A1C12);
        g.centeredText(this.font, label, x + width / 2, y + 6, 0xFFFFFFFF);
        return new int[]{x, y, width, height};
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (inside(this.revealAllButton, mouseX, mouseY)) {
            // Flip every face-down card, one after another from left to right.
            int queued = 0;
            for (int i = 0; i < this.flipStart.length; i++) {
                if (this.flipStart[i] == NOT_FLIPPED) {
                    this.flipStart[i] = this.time + queued * STAGGER_SECONDS;
                    queued++;
                }
            }
            return true;
        }
        if (inside(this.doneButton, mouseX, mouseY)) {
            this.onClose();
            return true;
        }
        for (int i = 0; i < this.cardRects.size(); i++) {
            if (!inside(this.cardRects.get(i), mouseX, mouseY)) {
                continue;
            }
            if (this.flipStart[i] == NOT_FLIPPED) {
                this.flipStart[i] = this.time;
            } else if (flipProgress(i) >= 1.0F) {
                this.minecraft.gui.setScreen(new CardInspectScreen(this.cards.get(i), this));
            }
            return true;
        }
        // Clicking empty space only closes once everything is revealed, so you can't skip the reveal by accident.
        if (allRevealed()) {
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
