package craftbound.client;

import craftbound.binder.BinderContents;
import craftbound.card.CardClass;
import craftbound.card.Cards;
import craftbound.component.ModComponents;
import craftbound.item.ModItems;
import craftbound.network.CardSeenPayload;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

// Hearthstone-style new cards: a card filed into a binder for the first time glows blue in the deck builder and
// the binder until you hover or click it. The binders remember which cards are new; this tells the server when
// you've looked at one, and stops its glow straight away rather than waiting for the reply.
public final class NewCards {
    // Cards you've looked at whose binders haven't caught up yet.
    private static final Set<Identifier> SEEN = new HashSet<>();

    public static boolean isNew(Identifier id) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        Inventory inventory = minecraft.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            BinderContents contents = stack.get(ModComponents.BINDER_CONTENTS);
            if (stack.is(ModItems.BINDER) && contents != null && contents.isNew(id)) {
                return !SEEN.contains(id);
            }
        }
        SEEN.remove(id);
        return false;
    }

    // You hovered or clicked it: it isn't new any more.
    public static void seen(Identifier id) {
        if (isNew(id)) {
            SEEN.add(id);
            ClientPlayNetworking.send(new CardSeenPayload(id));
        }
    }

    // How many new cards of one class (or Neutral) you have, for the number on the deck builder's tabs.
    public static int count(CardClass cardClass) {
        int count = 0;
        for (Identifier id : Cards.collectible()) {
            if (Cards.get(id).cardClass() == cardClass && isNew(id)) {
                count++;
            }
        }
        return count;
    }

    // A pulsing blue glow around a card. Draw it before the card so only the edge shows.
    public static void glow(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        float pulse = 0.65F + 0.35F * (float) Math.sin(System.currentTimeMillis() / 260.0);
        int halo = Math.round(0x70 * pulse);
        int edge = Math.round(0xFF * pulse);
        g.fill(x - 5, y - 5, x + width + 5, y + height + 5, (halo << 24) | 0x2F8FE8);
        g.outline(x - 3, y - 3, width + 6, height + 6, (edge << 24) | 0x7FD0FF);
        g.outline(x - 2, y - 2, width + 4, height + 4, (edge << 24) | 0x7FD0FF);
    }

    // A small blue badge with a number: how many new cards are behind a tab or page arrow.
    public static void badge(GuiGraphicsExtractor g, Font font, int x, int y, int count) {
        String text = String.valueOf(count);
        int width = Math.max(9, font.width(text) + 4);
        g.fill(x - 1, y - 1, x + width + 1, y + 10, 0xFF000000);
        g.fill(x, y, x + width, y + 9, 0xFF2F8FE8);
        g.fill(x, y, x + width, y + 1, 0xFF7FD0FF);
        g.text(font, text, x + (width - font.width(text)) / 2 + 1, y + 1, 0xFFFFFFFF);
    }

    private NewCards() {
    }
}
