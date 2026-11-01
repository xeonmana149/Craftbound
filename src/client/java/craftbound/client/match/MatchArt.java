package craftbound.client.match;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

// Small drawing pieces the match screen is built from: Minecraft hearts with a number on them, gold attack
// badges, XP orbs, damage bursts, wooden buttons and aiming arrows.
final class MatchArt {
    static final int GOLD = 0xFFE8B84A;
    static final int GOLD_DARK = 0xFF9C6D1C;
    static final int GOLD_LIGHT = 0xFFFFE08A;
    static final int WOOD_DARK = 0xFF3A2413;
    static final int WOOD_MID = 0xFF8A5A2B;
    static final int WOOD_LIGHT = 0xFFB07A40;
    static final int TEXT = 0xFFF1E8D4;
    static final int TEXT_DIM = 0xFFB5A88D;
    static final int GLOW = 0xFF7DFF5A;
    static final int RED = 0xFFD63A32;
    static final int XP = 0xFF80FF20;
    static final int XP_DARK = 0xFF2C5410;

    private static final Identifier HEART = Identifier.withDefaultNamespace("hud/heart/full");
    private static final Identifier HEART_CONTAINER = Identifier.withDefaultNamespace("hud/heart/container");

    // Text with a 1-pixel black outline, so it reads on top of anything. x is the centre when centred.
    static void outlined(GuiGraphicsExtractor g, Font font, String text, float x, float y, int color, float scale, boolean centred) {
        if ((color >>> 24) < 8) {
            return;
        }
        int width = font.width(text);
        g.pose().pushMatrix();
        g.pose().translate(centred ? x - width * scale / 2.0F : x, y);
        g.pose().scale(scale, scale);
        int outline = (color & 0xFF000000) | 0x000000;
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}}) {
            g.text(font, text, d[0], d[1], outline, false);
        }
        g.text(font, text, 0, 0, color, false);
        g.pose().popMatrix();
    }

    static void outlined(GuiGraphicsExtractor g, Font font, Component text, float x, float y, int color, float scale, boolean centred) {
        outlined(g, font, text.getString(), x, y, color, scale, centred);
    }

    // A Minecraft heart (the one from the health bar) with the number on it. White when full, red when hurt,
    // green when above its usual health.
    static void heart(GuiGraphicsExtractor g, Font font, int x, int y, int size, int value, int numberColor) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_CONTAINER, x, y, size, size);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, HEART, x, y, size, size);
        float scale = Math.max(0.75F, size / 14.0F);
        outlined(g, font, String.valueOf(value), x + size / 2.0F + 0.5F, y + size / 2.0F - 3.5F * scale, numberColor, scale, true);
    }

    // A square with its corners cut off.
    static void octagon(GuiGraphicsExtractor g, int x, int y, int size, int color) {
        int cut = Math.max(1, size / 4);
        for (int row = 0; row < size; row++) {
            int inset = row < cut ? cut - row : row >= size - cut ? row - (size - cut) + 1 : 0;
            g.fill(x + inset, y + row, x + size - inset, y + row + 1, color);
        }
    }

    // The gold badge with a mob's (or a tool's) attack on it.
    static void attack(GuiGraphicsExtractor g, Font font, int x, int y, int size, int value, int numberColor) {
        octagon(g, x - 1, y - 1, size + 2, 0xFF000000);
        octagon(g, x, y, size, 0xFFE4A828);
        g.fill(x + size / 4, y + 1, x + size - size / 4, y + 2, 0x80FFFFFF);
        float scale = Math.max(0.75F, size / 13.0F);
        outlined(g, font, String.valueOf(value), x + size / 2.0F + 0.5F, y + size / 2.0F - 3.5F * scale, numberColor, scale, true);
    }

    // The grey badge with how many attacks a tool has left.
    static void uses(GuiGraphicsExtractor g, Font font, int x, int y, int size, int value) {
        g.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xFF000000);
        g.fillGradient(x, y, x + size, y + size, 0xFF9AA1A8, 0xFF555C63);
        float scale = Math.max(0.75F, size / 13.0F);
        outlined(g, font, String.valueOf(value), x + size / 2.0F + 0.5F, y + size / 2.0F - 3.5F * scale, 0xFFFFFFFF, scale, true);
    }

    // An emerald with a cost on it, like the corner of a card.
    static void cost(GuiGraphicsExtractor g, Font font, int x, int y, int size, int value) {
        octagon(g, x - 1, y - 1, size + 2, 0xFF000000);
        octagon(g, x, y, size, 0xFF17A84F);
        g.fill(x + size / 4, y + 1, x + size / 2, y + 2, 0x90FFFFFF);
        float scale = Math.max(0.7F, size / 13.0F);
        outlined(g, font, String.valueOf(value), x + size / 2.0F + 0.5F, y + size / 2.0F - 3.5F * scale, 0xFFFFFFFF, scale, true);
    }

    // One XP orb on the XP bar.
    enum Orb { FULL, SPENT, BONUS, PREVIEW, MISSING }

    static void orb(GuiGraphicsExtractor g, int x, int y, int size, Orb orb) {
        int outer = switch (orb) {
            case FULL -> 0xFF3F7A12;
            case SPENT -> 0xFF1E2C14;
            case BONUS -> 0xFF8A6A12;
            case PREVIEW -> 0xFFB8FF80;
            case MISSING -> 0xFF7A1A14;
        };
        int inner = switch (orb) {
            case FULL -> XP;
            case SPENT -> 0xFF2A3A1E;
            case BONUS -> GOLD_LIGHT;
            case PREVIEW -> 0xFFF2FFE0;
            case MISSING -> 0xFFD63A32;
        };
        octagon(g, x - 1, y - 1, size + 2, 0xFF000000);
        octagon(g, x, y, size, outer);
        octagon(g, x + 1, y + 1, size - 2, inner);
        if (orb == Orb.FULL || orb == Orb.BONUS || orb == Orb.PREVIEW) {
            g.fill(x + size / 3, y + size / 3, x + size / 3 + Math.max(1, size / 4), y + size / 3 + Math.max(1, size / 4), 0xC0FFFFFF);
        }
    }

    // A spiky burst, like a hit splat, centred on (x, y).
    static void burst(GuiGraphicsExtractor g, float x, float y, float radius, int color, float spin) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        for (int i = 0; i < 6; i++) {
            g.pose().pushMatrix();
            g.pose().rotate(spin + (float) (i * Math.PI / 6));
            int r = Math.round(radius);
            int w = Math.max(2, Math.round(radius * 0.32F));
            g.fill(-w - 1, -r - 1, w + 1, r + 1, 0xFF000000);
            g.fill(-w, -r, w, r, color);
            g.pose().popMatrix();
        }
        int core = Math.round(radius * 0.7F);
        octagon(g, -core, -core, core * 2, color);
        g.pose().popMatrix();
    }

    // A wooden button with a gold edge. `glow` adds a pulsing green halo (0 = none).
    static void button(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int width, int height,
                       int top, int bottom, int textColor, float glow) {
        if (glow > 0) {
            int alpha = Math.round(0x90 * glow);
            g.fill(x - 4, y - 4, x + width + 4, y + height + 4, (alpha << 24) | 0x7DFF5A);
        }
        g.fill(x - 2, y - 2, x + width + 2, y + height + 2, 0xFF000000);
        g.fill(x - 1, y - 1, x + width + 1, y + height + 1, GOLD_DARK);
        g.fillGradient(x, y, x + width, y + height, top, bottom);
        g.fill(x, y, x + width, y + 1, 0x60FFFFFF);
        g.fill(x, y + height - 2, x + width, y + height, 0x40000000);
        int textWidth = font.width(text);
        float scale = Math.min(1.0F, (width - 6) / (float) textWidth);
        outlined(g, font, text, x + width / 2.0F, y + height / 2.0F - 4 * scale, textColor, scale, true);
    }

    // A dashed aiming line with a square head.
    static void arrow(GuiGraphicsExtractor g, int fromX, int fromY, int toX, int toY, int color, long time) {
        double length = Math.hypot(toX - fromX, toY - fromY);
        if (length < 1) {
            return;
        }
        // The dashes march towards the target.
        double offset = (time / 25.0) % 8;
        for (double d = offset; d < length - 6; d += 8) {
            double end = Math.min(length - 6, d + 5);
            for (double e = d; e < end; e += 1) {
                int px = (int) (fromX + (toX - fromX) * e / length);
                int py = (int) (fromY + (toY - fromY) * e / length);
                g.fill(px - 2, py - 2, px + 3, py + 3, 0xFF000000);
            }
            for (double e = d; e < end; e += 1) {
                int px = (int) (fromX + (toX - fromX) * e / length);
                int py = (int) (fromY + (toY - fromY) * e / length);
                g.fill(px - 1, py - 1, px + 2, py + 2, color);
            }
        }
        g.fill(toX - 6, toY - 6, toX + 7, toY + 7, 0xFF000000);
        g.fill(toX - 5, toY - 5, toX + 6, toY + 6, color);
        g.fill(toX - 2, toY - 2, toX + 3, toY + 3, 0xFF000000);
    }

    static int alpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(((color >>> 24) & 0xFF) * alpha)));
        return (a << 24) | (color & 0xFFFFFF);
    }

    static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    // Eases from 0 to 1, fast then slow.
    static float easeOut(float t) {
        t = clamp(t);
        return 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
    }

    // Eases from 0 to 1, slow then fast.
    static float easeIn(float t) {
        t = clamp(t);
        return t * t * t;
    }

    // Overshoots a little before settling, for things popping in.
    static float pop(float t) {
        t = clamp(t);
        float c = 1.70158F;
        return 1.0F + (c + 1) * (float) Math.pow(t - 1, 3) + c * (float) Math.pow(t - 1, 2);
    }

    private MatchArt() {
    }
}
