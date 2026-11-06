package craftbound.client.match;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

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

    // Both badges share one 9×9 source grid and one drawing path.
    private static final String[] HEART_PIXELS = {
            ".##...##.", "#RR#.#RR#", "#RLR#RRR#", "#RRRRRRR#", "#RRRRRRR#",
            ".#RRRRD#.", "..#RDD#..", "...#D#...", "....#...."
    };
    private static final String[] ATTACK_PIXELS = {
            "..#####..", ".#LLLLL#.", "#LRRRRRR#", "#LRRRRRR#", "#RRRRRRR#",
            "#RRRRRRD#", "#RRRRRDD#", ".#DDDDD#.", "..#####.."
    };

    // Text with a 1-pixel black outline, so it reads on top of anything. x is the centre when centred.
    static void outlined(craftbound.client.card.CardCanvas g, Font font, String text, float x, float y, int color, float scale, boolean centred) {
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

    static void outlined(craftbound.client.card.CardCanvas g, Font font, Component text, float x, float y, int color, float scale, boolean centred) {
        outlined(g, font, text.getString(), x, y, color, scale, centred);
    }

    // A Minecraft-style heart with the number on it. White when full, red when hurt,
    // green when above its usual health.
    static void heart(craftbound.client.card.CardCanvas g, Font font, int x, int y, int size, int value, int numberColor) {
        pixelStat(g, HEART_PIXELS, x, y, size, 0xFFFF6666, 0xFFE53935, 0xFF981E24);
        statNumber(g, font, x, y, size, value, numberColor);
    }

    // A square with its corners cut off.
    static void octagon(craftbound.client.card.CardCanvas g, int x, int y, int size, int color) {
        int cut = Math.max(1, size / 4);
        for (int row = 0; row < size; row++) {
            int inset = row < cut ? cut - row : row >= size - cut ? row - (size - cut) + 1 : 0;
            g.fill(x + inset, y + row, x + size - inset, y + row + 1, color);
        }
    }

    // A hollow pixel outline following the same clipped corners as the portrait frame.
    // Scan only the rim so the face underneath is never painted over.
    static void octagonOutline(craftbound.client.card.CardCanvas g, int x, int y, int size, int color, int thickness) {
        octagonOutline(g, x, y, size, color, thickness, false);
    }

    static void octagonOutline(craftbound.client.card.CardCanvas g, int x, int y, int size, int color, int thickness, boolean dashed) {
        int cut = Math.max(1, size / 4);
        int innerSize = size - thickness * 2;
        int innerCut = Math.max(1, innerSize / 4);
        for (int row = 0; row < size; row++) {
            if (dashed && row % 6 >= 3) continue;
            int inset = row < cut ? cut - row : row >= size - cut ? row - (size - cut) + 1 : 0;
            if (row < thickness || row >= size - thickness) {
                g.fill(x + inset, y + row, x + size - inset, y + row + 1, color);
            } else {
                int innerRow = row - thickness;
                int innerInset = innerRow < innerCut ? innerCut - innerRow
                        : innerRow >= innerSize - innerCut ? innerRow - (innerSize - innerCut) + 1 : 0;
                int edge = Math.max(inset, thickness + innerInset);
                g.fill(x + inset, y + row, x + edge, y + row + 1, color);
                g.fill(x + size - edge, y + row, x + size - inset, y + row + 1, color);
            }
        }
    }

    // A broad iron rim, with the point below the card so it cannot hide its artwork or stats.
    static void shieldFrame(craftbound.client.card.CardCanvas g, int width, int height) {
        // A thin rectangular metal rim (no pointed bottom), so it stays clear of the hero below.
        shieldFill(g, -4, -4, width + 8, height + 8, 0, 0xFF171C20, 0xFF171C20);
        shieldFill(g, -3, -3, width + 6, height + 6, 0, 0xFFF1F3E7, 0xFF747F83);
        shieldFill(g, -1, -1, width + 2, height + 2, 0, 0xFF9DA9A7, 0xFF3E4B50);
        // Bright rivets distinguish the metal rim from the ordinary block corner braces.
        g.fill(-3, 1, -1, 3, 0xFFFFFFFF);
        g.fill(width + 1, 1, width + 3, 3, 0xFFFFFFFF);
    }

    static void shieldBadge(craftbound.client.card.CardCanvas g, int x, int y) {
        shieldFill(g, x, y, 11, 13, 4, 0xFF171C20, 0xFF171C20);
        shieldFill(g, x + 1, y + 1, 9, 11, 3, 0xFFF1F3E7, 0xFF747F83);
        shieldFill(g, x + 2, y + 2, 7, 8, 2, 0xFFBA884C, 0xFF79502D);
        g.fill(x + 5, y + 2, x + 6, y + 9, 0xFF563820);
        g.fill(x + 2, y + 4, x + 9, y + 6, 0xFFC7D1CB);
        g.fill(x + 5, y + 4, x + 6, y + 5, 0xFFFFFFFF);
    }

    private static int shieldInset(int row, int width, int height, int point) {
        if (row < 2) return 2 - row;
        // point 0: a plain rectangle with rounded corners top and bottom.
        if (point <= 0) return row >= height - 2 ? row - (height - 3) : 0;
        if (row < height - point) return 0;
        return (row - (height - point) + 1) * ((width - 1) / 2) / point;
    }

    private static void shieldFill(craftbound.client.card.CardCanvas g, int x, int y, int width, int height, int point, int light, int dark) {
        for (int row = 0; row < height; row++) {
            int inset = shieldInset(row, width, height, point);
            g.fill(x + inset, y + row, x + width - inset, y + row + 1, row < height / 2 ? light : dark);
        }
    }

    // A hollow target outline follows the shield instead of enclosing it in a rectangle.
    static void shieldOutline(craftbound.client.card.CardCanvas g, int x, int y, int width, int height, int point, int color, int thickness) {
        for (int row = 0; row < height; row++) {
            int inset = shieldInset(row, width, height, point);
            if (row < thickness || row >= height - thickness) {
                g.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
            } else {
                int edge = Math.max(inset, thickness + shieldInset(row - thickness, width - thickness * 2,
                        height - thickness * 2, point <= 0 ? 0 : Math.max(1, point - thickness)));
                g.fill(x + inset, y + row, x + edge, y + row + 1, color);
                g.fill(x + width - edge, y + row, x + width - inset, y + row + 1, color);
            }
        }
    }

    // Ice facets and falling snow stay inside the card, leaving outer targeting/Shield rims distinct.
    static void frozenCoating(craftbound.client.card.CardCanvas g, int width, int height, long now, int seed) {
        if (width < 7 || height < 9) return;
        g.fillGradient(1, 1, width - 1, height - 1, 0x606DBCE8, 0x307DCDF0);
        g.outline(1, 1, width - 2, height - 2, 0xFFB9EDFF);
        int depth = Math.max(3, Math.min(7, width / 4));
        // Angular frost growing in from each corner, not a solid sheet covering the illustration.
        for (int row = 0; row < depth; row++) {
            int reach = depth - row;
            for (int y : new int[]{2 + row, height - 3 - row}) {
                g.fill(2, y, 2 + reach, y + 1, row % 2 == 0 ? 0xFFE4FAFF : 0xFF8BCFEF);
                g.fill(width - 2 - reach, y, width - 2, y + 1, row % 2 == 0 ? 0xFFE4FAFF : 0xFF8BCFEF);
            }
        }
        // Short branching ice cracks on the left and right sides of the art.
        for (int side = 0; side < 2; side++) {
            int y0 = height / 3 + side * Math.max(2, height / 5);
            for (int i = 0; i < depth; i++) {
                int x = side == 0 ? 2 + i : width - 3 - i;
                int y = y0 + i / 2;
                g.fill(x, y, x + 1, y + 2, 0xC0E6FAFF);
                if (i == depth / 2) g.fill(x, y - 2, x + 1, y, 0xC0E6FAFF);
            }
        }
        for (int i = 0; i < 5; i++) {
            float t = Math.floorMod(now + seed * 101L + i * 397L, 2300L) / 2300.0F;
            int x = 2 + Math.floorMod(seed + i * 7, width - 5);
            int y = 2 + Math.round(t * (height - 6));
            int color = alpha(0xFFE9FDFF, 0.35F + 0.55F * (float) Math.sin(t * Math.PI));
            g.fill(x, y, x + 2, y + 1, color);
            if (i % 2 == 0) g.fill(x, y - 1, x + 1, y + 2, color);
        }
    }

    // Wither is a charred, creeping decay with rising ash; its broken veins differ from ice facets.
    static void witherDecay(craftbound.client.card.CardCanvas g, int width, int height, long now, int seed) {
        if (width < 7 || height < 9) return;
        float breath = 0.5F + 0.5F * (float) Math.sin(now / 370.0 + seed);
        g.fill(1, 1, width - 1, height - 1, alpha(0xFF17121D, 0.18F + breath * 0.10F));
        int vein = 0xE01C1722;
        for (int side = 0; side < 2; side++) {
            for (int y = 3; y < height - 3; y += 3) {
                int inward = Math.floorMod(y / 3 + seed + side, 3);
                int x = side == 0 ? 1 + inward : width - 3 - inward;
                g.fill(x, y, x + 2, Math.min(height - 2, y + 4), vein);
                if (y % 2 == 1) {
                    int branchX = side == 0 ? x + 1 : x - 2;
                    g.fill(branchX, y + 1, branchX + 3, y + 2, 0xC060536A);
                }
            }
        }
        for (int i = 0; i < 6; i++) {
            float t = Math.floorMod(now + seed * 149L + i * 313L, 2100L) / 2100.0F;
            int y = 2 + Math.round((1 - t) * (height - 6));
            int drift = Math.round((float) Math.sin(t * Math.PI * 2 + i));
            int x = (i % 2 == 0 ? 1 : width - 4) + drift;
            float fade = (float) Math.sin(t * Math.PI);
            g.fill(x - 1, y - 1, x + 3, y + 3, alpha(0xFF332B3C, fade * 0.45F));
            g.fill(x, y, x + 2, y + 2, alpha(0xFF151219, 0.35F + fade * 0.6F));
            g.fill(x, y - 1, x + 1, y, alpha(0xFF9A89A5, fade * 0.7F));
        }
    }

    // Resurrection protection: emerald wisps and gold motes, rather than another readiness/target outline.
    // Bound to the card's transform, so the blessing follows its attacks and disappears with the status.
    static void totemBlessing(craftbound.client.card.CardCanvas g, int width, int height, long now, int seed) {
        if (width < 5 || height < 7) return;
        double phase = now / 420.0 + seed * 0.7;
        float breath = 0.5F + 0.5F * (float) Math.sin(phase);
        int emerald = alpha(0xFF4CE49A, 0.24F + breath * 0.16F);
        // Soft vertical light stays at the edges, leaving the card's artwork and centre readable.
        g.fillGradient(-2, 2, 3, height - 2, 0x004CE49A, emerald);
        g.fillGradient(width - 3, 2, width + 2, height - 2, 0x004CE49A, emerald);
        int travel = Math.max(4, height - 5);
        for (int i = 0; i < 8; i++) {
            float t = Math.floorMod(now + seed * 137L + i * 223L, 1800L) / 1800.0F;
            int y = 2 + Math.round((1 - t) * travel);
            int sway = Math.round((float) Math.sin(phase + i * 1.9) * 1.5F);
            int x = (i % 2 == 0 ? 1 : width - 3) + sway;
            float opacity = 0.4F + 0.6F * (float) Math.sin(t * Math.PI);
            int color = alpha(i % 3 == 0 ? 0xFFFFD76B : 0xFF79FFC0, opacity);
            g.fill(x, y, x + 2, y + 2, color);
            if (i % 3 == 0) {
                // Gold crosses echo the Totem activation particles without covering the art.
                g.fill(x - 1, y, x + 3, y + 1, color);
                g.fill(x, y - 1, x + 1, y + 3, color);
            }
        }
    }

    // A translucent, faceted golden barrier. Draw before stat badges and legal-target outlines.
    static void invulnerableBarrier(craftbound.client.card.CardCanvas g, int width, int height, long now) {
        int w = width - 2, h = height - 2;
        if (w < 5 || h < 5) return;
        int cut = Math.max(2, Math.min(w, h) / 4);
        float pulse = 0.5F + 0.5F * (float) Math.sin(now / 280.0);
        int rim = alpha(0xFFFFDF63, 0.75F + pulse * 0.25F);
        int glass = alpha(0xFFFFD54A, 0.12F + pulse * 0.08F);
        for (int row = 0; row < h; row++) {
            int inset = row < cut ? cut - row : row >= h - cut ? row - (h - cut) + 1 : 0;
            int left = 1 + inset, right = 1 + w - inset, y = 1 + row;
            g.fill(left, y, right, y + 1, glass);
            if (row < 2 || row >= h - 2) {
                g.fill(left, y, right, y + 1, rim);
            } else {
                g.fill(left, y, Math.min(left + 2, right), y + 1, rim);
                g.fill(Math.max(left, right - 2), y, right, y + 1, rim);
            }
        }
        // Two travelling enchantment glints remain visible without obscuring the central artwork.
        int travel = Math.max(1, h - cut * 2);
        int step = (int) ((now / 90) % travel);
        sparkle(g, 2, 1 + cut + step);
        sparkle(g, width - 3, 1 + cut + travel - 1 - step);
    }

    private static void sparkle(craftbound.client.card.CardCanvas g, int x, int y) {
        g.fill(x - 2, y, x + 3, y + 1, 0xFFFFE99A);
        g.fill(x, y - 2, x + 1, y + 3, 0xFFFFE99A);
        g.fill(x, y, x + 1, y + 1, 0xFFFFFFFF);
    }

    // A closed gold lock is distinct from the pointed Shield/taunt badge above the card.
    static void invulnerableLock(craftbound.client.card.CardCanvas g, int x, int y) {
        g.fill(x + 1, y, x + 8, y + 7, 0xFF33240B);
        g.fill(x + 2, y + 1, x + 7, y + 6, 0xFFFFE99A);
        g.fill(x + 3, y + 2, x + 6, y + 5, 0xFF33240B);
        g.fill(x, y + 4, x + 9, y + 10, 0xFF33240B);
        g.fill(x + 1, y + 5, x + 8, y + 9, 0xFFF6C94C);
        g.fill(x + 1, y + 5, x + 8, y + 6, 0xFFFFF3B9);
        g.fill(x + 4, y + 6, x + 5, y + 8, 0xFF33240B);
    }

    // Merge same-colour cells into row runs; both icons use the same pixel scale and border.
    private static void pixelStat(craftbound.client.card.CardCanvas g, String[] pixels, int x, int y, int size,
                                  int light, int fill, int dark) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(size / 9.0F, size / 9.0F);
        for (int row = 0; row < 9; row++) {
            for (int col = 0; col < 9;) {
                char pixel = pixels[row].charAt(col);
                int end = col + 1;
                while (end < 9 && pixels[row].charAt(end) == pixel) end++;
                if (pixel != '.') {
                    int color = switch (pixel) { case 'L' -> light; case 'R' -> fill; case 'D' -> dark; default -> 0xFF21180C; };
                    g.fill(col, row, end, row + 1, color);
                }
                col = end;
            }
        }
        g.pose().popMatrix();
    }

    // Identical type size and baseline on attack, health and durability; multi-digit stats fit the badge.
    private static void statNumber(craftbound.client.card.CardCanvas g, Font font, int x, int y, int size, int value, int color) {
        String text = String.valueOf(value);
        float scale = Math.min(size / 15.0F, (size - 3.0F) / (font.width(text) + 2.0F));
        outlined(g, font, text, x + size / 2.0F, y + size / 2.0F - 3.5F * scale, color, scale, true);
    }

    // A simple gold circle shares the heart's pixel grid and centred number alignment.
    static void attack(craftbound.client.card.CardCanvas g, Font font, int x, int y, int size, int value, int numberColor) {
        attack(g, font, x, y, size, value, numberColor, false);
    }

    static void attack(craftbound.client.card.CardCanvas g, Font font, int x, int y, int size, int value, int numberColor, boolean ranged) {
        pixelStat(g, ATTACK_PIXELS, x, y, size, GOLD_LIGHT, GOLD, GOLD_DARK);
        statNumber(g, font, x, y, size, value, numberColor);
    }

    // The grey badge with how many attacks a tool has left.
    static void uses(craftbound.client.card.CardCanvas g, Font font, int x, int y, int size, int value) {
        g.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xFF000000);
        g.fillGradient(x, y, x + size, y + size, 0xFF9AA1A8, 0xFF555C63);
        statNumber(g, font, x, y, size, value, 0xFFFFFFFF);
    }

    // An emerald with a cost on it, like the corner of a card.
    static void cost(craftbound.client.card.CardCanvas g, Font font, int x, int y, int size, int value) {
        octagon(g, x - 1, y - 1, size + 2, 0xFF000000);
        octagon(g, x, y, size, 0xFF17A84F);
        g.fill(x + size / 4, y + 1, x + size / 2, y + 2, 0x90FFFFFF);
        float scale = Math.max(0.7F, size / 13.0F);
        outlined(g, font, String.valueOf(value), x + size / 2.0F + 0.5F, y + size / 2.0F - 3.5F * scale, 0xFFFFFFFF, scale, true);
    }

    static void handIcon(GuiGraphicsExtractor g,int iconX,int y) {
        g.fill(iconX, y, iconX + 7, y + 10, 0xFF000000);
        g.fill(iconX + 1, y + 1, iconX + 6, y + 9, MatchArt.GOLD_DARK);
        g.fill(iconX + 2, y + 2, iconX + 5, y + 8, 0xFF3B4048);
    }

    // One XP orb on the XP bar.
    enum Orb { FULL, SPENT, BONUS, PREVIEW, MISSING }

    static void orb(craftbound.client.card.CardCanvas g, int x, int y, int size, Orb orb) {
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
    static void burst(craftbound.client.card.CardCanvas g, float x, float y, float radius, int color, float spin) {
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
    static void button(craftbound.client.card.CardCanvas g, Font font, Component text, int x, int y, int width, int height,
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
    static void arrow(craftbound.client.card.CardCanvas g, int fromX, int fromY, int toX, int toY, int color, long time) {
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
    static void outlined(GuiGraphicsExtractor g, Font font, String text, float x, float y, int color, float scale, boolean centred) { outlined(new craftbound.client.card.CardCanvas.Gui(g), font, text, x, y, color, scale, centred); }
    static void outlined(GuiGraphicsExtractor g, Font font, Component text, float x, float y, int color, float scale, boolean centred) { outlined(new craftbound.client.card.CardCanvas.Gui(g), font, text, x, y, color, scale, centred); }
    static void heart(GuiGraphicsExtractor g, Font font, int x, int y, int size, int value, int numberColor) { heart(new craftbound.client.card.CardCanvas.Gui(g), font, x, y, size, value, numberColor); }
    static void octagon(GuiGraphicsExtractor g, int x, int y, int size, int color) { octagon(new craftbound.client.card.CardCanvas.Gui(g), x, y, size, color); }
    static void octagonOutline(GuiGraphicsExtractor g, int x, int y, int size, int color, int thickness) { octagonOutline(new craftbound.client.card.CardCanvas.Gui(g), x, y, size, color, thickness); }
    static void octagonOutline(GuiGraphicsExtractor g, int x, int y, int size, int color, int thickness, boolean dashed) { octagonOutline(new craftbound.client.card.CardCanvas.Gui(g), x, y, size, color, thickness, dashed); }
    static void shieldFrame(GuiGraphicsExtractor g, int width, int height) { shieldFrame(new craftbound.client.card.CardCanvas.Gui(g), width, height); }
    static void shieldBadge(GuiGraphicsExtractor g, int x, int y) { shieldBadge(new craftbound.client.card.CardCanvas.Gui(g), x, y); }
    private static void shieldFill(GuiGraphicsExtractor g, int x, int y, int width, int height, int point, int light, int dark) { shieldFill(new craftbound.client.card.CardCanvas.Gui(g), x, y, width, height, point, light, dark); }
    static void shieldOutline(GuiGraphicsExtractor g, int x, int y, int width, int height, int point, int color, int thickness) { shieldOutline(new craftbound.client.card.CardCanvas.Gui(g), x, y, width, height, point, color, thickness); }
    static void frozenCoating(GuiGraphicsExtractor g, int width, int height, long now, int seed) { frozenCoating(new craftbound.client.card.CardCanvas.Gui(g), width, height, now, seed); }
    static void witherDecay(GuiGraphicsExtractor g, int width, int height, long now, int seed) { witherDecay(new craftbound.client.card.CardCanvas.Gui(g), width, height, now, seed); }
    static void totemBlessing(GuiGraphicsExtractor g, int width, int height, long now, int seed) { totemBlessing(new craftbound.client.card.CardCanvas.Gui(g), width, height, now, seed); }
    static void invulnerableBarrier(GuiGraphicsExtractor g, int width, int height, long now) { invulnerableBarrier(new craftbound.client.card.CardCanvas.Gui(g), width, height, now); }
    private static void sparkle(GuiGraphicsExtractor g, int x, int y) { sparkle(new craftbound.client.card.CardCanvas.Gui(g), x, y); }
    static void invulnerableLock(GuiGraphicsExtractor g, int x, int y) { invulnerableLock(new craftbound.client.card.CardCanvas.Gui(g), x, y); }
    private static void pixelStat(GuiGraphicsExtractor g, String[] pixels, int x, int y, int size,
                                  int light, int fill, int dark) { pixelStat(new craftbound.client.card.CardCanvas.Gui(g), pixels, x, y, size, light, fill, dark); }
    private static void statNumber(GuiGraphicsExtractor g, Font font, int x, int y, int size, int value, int color) { statNumber(new craftbound.client.card.CardCanvas.Gui(g), font, x, y, size, value, color); }
    static void attack(GuiGraphicsExtractor g, Font font, int x, int y, int size, int value, int numberColor) { attack(new craftbound.client.card.CardCanvas.Gui(g), font, x, y, size, value, numberColor); }
    static void attack(GuiGraphicsExtractor g, Font font, int x, int y, int size, int value, int numberColor, boolean ranged) { attack(new craftbound.client.card.CardCanvas.Gui(g), font, x, y, size, value, numberColor, ranged); }
    static void uses(GuiGraphicsExtractor g, Font font, int x, int y, int size, int value) { uses(new craftbound.client.card.CardCanvas.Gui(g), font, x, y, size, value); }
    static void cost(GuiGraphicsExtractor g, Font font, int x, int y, int size, int value) { cost(new craftbound.client.card.CardCanvas.Gui(g), font, x, y, size, value); }
    static void orb(GuiGraphicsExtractor g, int x, int y, int size, Orb orb) { orb(new craftbound.client.card.CardCanvas.Gui(g), x, y, size, orb); }
    static void burst(GuiGraphicsExtractor g, float x, float y, float radius, int color, float spin) { burst(new craftbound.client.card.CardCanvas.Gui(g), x, y, radius, color, spin); }
    static void button(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int width, int height,
                       int top, int bottom, int textColor, float glow) { button(new craftbound.client.card.CardCanvas.Gui(g), font, text, x, y, width, height, top, bottom, textColor, glow); }
    static void arrow(GuiGraphicsExtractor g, int fromX, int fromY, int toX, int toY, int color, long time) { arrow(new craftbound.client.card.CardCanvas.Gui(g), fromX, fromY, toX, toY, color, time); }
}
