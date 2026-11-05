package craftbound.client.card;

import com.mojang.blaze3d.platform.InputConstants;
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

/** Client-only theatre: pulls are already awarded by the server before this screen opens. */
public class PackOpeningScreen extends Screen {
    private static final int GAP = 12;
    private static final int ENCHANTED = 0xC58AFF;
    private final List<ItemStack> cards = new ArrayList<>();
    private final CardRarity[] rarities;
    private final boolean[] enchanted, fresh;
    private final int[] soundStage;
    private final PackReveal reveal;
    private final boolean jackpot;
    private final List<int[]> cardRects = new ArrayList<>();
    private float time;
    private long lastFrameNanos = craftbound.client.AnimationClock.nanos();
    private int[] actionButton;

    public PackOpeningScreen(List<BoosterPacks.Pull> pulls, boolean jackpot, List<Boolean> fresh) {
        super(Component.translatable(jackpot ? "pack_ui.craftbound.jackpot" : "item.craftbound.booster_pack"));
        this.jackpot = jackpot;
        var order = new ArrayList<Integer>();
        for (int i = 0; i < pulls.size(); i++) order.add(i);
        order.sort(Comparator.<Integer>comparingInt(i -> Cards.get(pulls.get(i).card()).rarity().ordinal())
                .thenComparing(i -> pulls.get(i).enchanted()));
        int n = order.size();
        this.rarities = new CardRarity[n]; this.enchanted = new boolean[n]; this.fresh = new boolean[n];
        this.soundStage = new int[n];
        for (int i = 0; i < n; i++) {
            int original = order.get(i); var pull = pulls.get(original);
            this.cards.add(CardItem.of(pull.card(), pull.enchanted()));
            this.rarities[i] = Cards.get(pull.card()).rarity(); this.enchanted[i] = pull.enchanted();
            this.fresh[i] = original < fresh.size() && fresh.get(original);
        }
        this.reveal = new PackReveal(rarities, enchanted);
    }

    @Override protected void init() { super.init(); lastFrameNanos = craftbound.client.AnimationClock.nanos(); }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        long now = craftbound.client.AnimationClock.nanos();
        time += Math.min((now - lastFrameNanos) / 1_000_000_000F, 0.1F); lastFrameNanos = now;
        int count = cards.size();
        float scale = Math.max(0.1F, Math.min(1.25F, Math.min(
                (width - 40F - GAP * (count - 1)) / (Math.max(1, count) * CardRenderer.WIDTH),
                (height - 110F) / CardRenderer.HEIGHT)));
        int cw = Math.round(CardRenderer.WIDTH * scale), ch = Math.round(CardRenderer.HEIGHT * scale);
        int startX = (width - count * cw - (count - 1) * GAP) / 2, rowY = (height - ch) / 2;
        int active = reveal.active(time);
        g.fill(0, 0, width, height, 0xA00A0813);
        g.centeredText(font, title, width / 2, 16, jackpot ? 0xFFFFD376 : 0xFFF4EADB);
        cardRects.clear();
        List<CardRenderer.HoverRegion> regions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int x = startX + i * (cw + GAP);
            cardRects.add(new int[]{x, rowY, cw, ch});
            if (i != active) drawCard(g, i, x, rowY, scale, mouseX, mouseY, active < 0, regions);
        }
        if (active >= 0) {
            float focus = reveal.focus(active, time);
            g.fill(0, 34, width, height - 34, argb(Math.round(175 * focus), 0x070610));
            int[] rect = cardRects.get(active);
            // Draw the featured pull last, above neighbours, particles and dimming.
            float featured = Math.min(1.75F, Math.min((height - 115F) / CardRenderer.HEIGHT, (width - 70F) / CardRenderer.WIDTH));
            float drawScale = scale + Math.max(0, featured - scale) * focus;
            float pop = (float)Math.sin(Math.PI * PackReveal.clamp(reveal.burstAge(active, time) / 0.32F)) * 0.055F;
            drawScale *= 1 + pop * focus;
            int x = Math.round(rect[0] + ((width - CardRenderer.WIDTH * drawScale) / 2F - rect[0]) * focus);
            int y = Math.round(rect[1] + ((height - CardRenderer.HEIGHT * drawScale) / 2F - 4 - rect[1]) * focus);
            playSounds(active);
            drawCard(g, active, x, y, drawScale, mouseX, mouseY, false, regions);
        }
        String key = reveal.anyUnqueued() ? "reveal_all" : reveal.allSettled(time) ? "done" : "skip";
        actionButton = drawButton(g, Component.translatable("pack_ui.craftbound." + key), height - 29, mouseX, mouseY);
        if (active < 0) for (var region : regions) if (region.contains(mouseX, mouseY)) {
            Tooltips.show(g, font, region.lines(), mouseX, mouseY); break;
        }
    }

    private int color(int i) {
        return switch (rarities[i]) {
            case COMMON -> enchanted[i] ? ENCHANTED : 0xC5BDB1;
            case RARE -> 0x779FFF;
            case EPIC -> 0xD370FF;
            case LEGENDARY -> 0xFFC550;
        };
    }

    private void drawCard(GuiGraphicsExtractor g, int i, int x, int y, float scale,
                          int mouseX, int mouseY, boolean interactive, List<CardRenderer.HoverRegion> regions) {
        int w = Math.round(CardRenderer.WIDTH * scale), h = Math.round(CardRenderer.HEIGHT * scale);
        float age = reveal.age(i, time), flip = reveal.flip(i, time), burst = reveal.burstAge(i, time);
        boolean active = age >= 0 && !reveal.settled(i, time);
        boolean hover = interactive && inside(new int[]{x, y, w, h}, mouseX, mouseY);
        float charge = PackReveal.clamp(age / reveal.charge(i));
        int lift = !active && !reveal.queued(i) && hover ? 4 : 0;
        y -= lift;
        float cx = x + w / 2F, cy = y + h / 2F;
        int rgb = color(i);
        if (active || (hover && flip == 0) || enchanted[i]) {
            halo(g, x, y, w, h, rgb, active ? 0.65F + charge * 0.35F : 0.35F);
        }
        if (active && reveal.special(i)) {
            if (burst < 0) chargeParticles(g, i, cx, cy, w, h, charge);
            else burst(g, i, cx, cy, w, h, burst);
        }
        float squash = Math.abs((float)Math.cos(flip * Math.PI));
        float shake = active && flip == 0 ? (float)Math.sin(time * 53) * charge * charge * 1.4F : 0;
        if (squash > 0.015F) {
            g.pose().pushMatrix(); g.pose().translate(cx + shake, cy);
            g.pose().scale(squash, 1); g.pose().translate(-cx, -cy);
            if (flip < 0.5F) {
                CardRenderer.drawBack(g, Sleeves.get(Sleeves.CLASSIC), x, y, scale);
                if (enchanted[i]) CardRenderer.drawDiagonalShimmer(g, x, y, w, h, Math.max(4, Math.round(18 * scale)), 0x85);
            } else {
                var drawn = CardRenderer.draw(g, font, cards.get(i), x, y, scale);
                if (interactive && reveal.settled(i, time)) regions.addAll(drawn);
            }
            g.pose().popMatrix();
        }
        if (active) {
            // Border and corner sparks sit above the face without obscuring its text.
            g.outline(x - 2, y - 2, w + 4, h + 4, argb(230, rgb));
            if (enchanted[i]) {
                g.outline(x - 4, y - 4, w + 8, h + 8, argb(210, ENCHANTED));
                orbit(g, i, cx, cy, w + 14, h + 14, 8, 1);
            }
            if (reveal.special(i)) {
                Component label = enchanted[i]
                        ? Component.translatable("pack_ui.craftbound.enchanted_rarity", rarities[i].displayName())
                        : rarities[i].displayName();
                int labelY = Math.min(height - 43, y + h + 12), labelWidth = font.width(label);
                g.fill(Math.round(cx) - labelWidth / 2 - 5, labelY - 2, Math.round(cx) + labelWidth / 2 + 5, labelY + 11, 0xE00D0914);
                g.centeredText(font, label, Math.round(cx), labelY, argb(255, rgb));
            }
        }
        if (flip >= 0.5F && fresh[i]) {
            Component label = Component.translatable("pack_ui.craftbound.new"); int tw = font.width(label);
            g.fill(Math.round(cx) - tw / 2 - 5, y - 16, Math.round(cx) + tw / 2 + 5, y - 3, 0xF021502C);
            g.centeredText(font, label, Math.round(cx), y - 13, 0xFFD6FFAE);
        }
    }

    private int particleCount(int wanted) {
        String option = minecraft.options.particles().get().name();
        return option.equals("MINIMAL") ? 0 : option.equals("DECREASED") ? wanted / 2 : wanted;
    }

    private void halo(GuiGraphicsExtractor g, int x, int y, int w, int h, int rgb, float power) {
        float pulse = 0.85F + 0.15F * (float)Math.sin(time * 5);
        for (int ring = 8; ring > 0; ring--) {
            int grow = ring * 2, c = argb((int)(70 * power * pulse * (1 - ring / 9F)), rgb);
            g.fill(x - grow, y - grow, x + w + grow, y - grow + 2, c);
            g.fill(x - grow, y + h + grow - 2, x + w + grow, y + h + grow, c);
            g.fill(x - grow, y - grow + 2, x - grow + 2, y + h + grow - 2, c);
            g.fill(x + w + grow - 2, y - grow + 2, x + w + grow, y + h + grow - 2, c);
        }
        g.outline(x - 1, y - 1, w + 2, h + 2, argb((int)(220 * power), rgb));
    }

    private void chargeParticles(GuiGraphicsExtractor g, int i, float cx, float cy, int w, int h, float charge) {
        // A gathering portal for Epics; a larger golden seal for Legendaries. All geometry is bounded.
        if (rarities[i].ordinal() >= CardRarity.EPIC.ordinal()) {
            int marks=rarities[i]==CardRarity.LEGENDARY?12:8;
            for(int p=0;p<marks;p++){
                double a=p*Math.PI*2/marks+time*(enchanted[i]?-1.1:.35);
                float radius=1.2F-charge*.22F;
                int px=Math.round(cx+(float)Math.cos(a)*(w*.65F+18)*radius),py=Math.round(cy+(float)Math.sin(a)*(h*.55F+18)*radius);
                g.pose().pushMatrix();g.pose().translate(px,py);g.pose().rotate((float)a);
                g.outline(-3,-3,7,7,argb((int)(220*charge),p%2==0&&enchanted[i]?ENCHANTED:color(i)));
                g.fill(-1,-1,2,2,argb((int)(255*charge),0xFFF4DE));g.pose().popMatrix();
            }
        }
        orbit(g, i, cx, cy, w + 22, h + 22, 20, charge);
        int n = particleCount(enchanted[i] ? 28 : 16);
        for (int p = 0; p < n; p++) {
            float phase = (time * 0.7F + p * 0.618034F) % 1;
            double angle = p * 2.39996 + i;
            float radius = 1.3F - phase * 0.7F;
            int px = Math.round(cx + (float)Math.cos(angle) * (w / 2F + 28) * radius);
            int py = Math.round(cy + (float)Math.sin(angle) * (h / 2F + 22) * radius);
            spark(g, px, py, p % 4 == 0 ? 2 : 1, argb((int)(180 * phase * charge), enchanted[i] && p % 2 == 0 ? ENCHANTED : color(i)));
        }
    }

    private void orbit(GuiGraphicsExtractor g, int i, float cx, float cy, int w, int h, int wanted, float strength) {
        int n = particleCount(wanted);
        for (int p = 0; p < n; p++) {
            double a = time * 1.6 + p * Math.PI * 2 / n;
            spark(g, Math.round(cx + (float)Math.cos(a) * w * 0.56F), Math.round(cy + (float)Math.sin(a) * h * 0.56F),
                    p % 3 == 0 ? 2 : 1, argb((int)(220 * strength), enchanted[i] && p % 2 == 0 ? ENCHANTED : color(i)));
        }
    }

    private void burst(GuiGraphicsExtractor g, int i, float cx, float cy, int w, int h, float age) {
        float life = rarities[i] == CardRarity.LEGENDARY ? 1.65F : 1.1F;
        float t = PackReveal.clamp(age / life), fade = 1 - t;
        int rgb = color(i);
        if (fade <= 0) return;
        // Two staggered shockwaves make the reveal land, then echo, instead of one particle puff.
        for(int wave=0;wave<2;wave++){
            float wt=age-wave*.16F;if(wt<0||wt>.65F)continue;
            float progress=wt/.65F;int alpha=(int)(210*(1-progress)*(1-progress));
            for(int p=0;p<32;p++){
                double angle=p*Math.PI/16;
                int px=Math.round(cx+(float)Math.cos(angle)*(w*.55F+85*progress)),py=Math.round(cy+(float)Math.sin(angle)*(h*.53F+60*progress));
                spark(g,px,py,wave==0?2:1,argb(alpha,wave==1&&enchanted[i]?ENCHANTED:rgb));
            }
        }
        if(age<.16F)g.fill(Math.round(cx)-w/2-8,Math.round(cy)-h/2-8,Math.round(cx)+w/2+8,Math.round(cy)+h/2+8,argb((int)(80*(1-age/.16F)),0xFFF4DB));
        if (particleCount(1) > 0 || minecraft.options.particles().get().name().equals("DECREASED")) {
            int rays = rarities[i] == CardRarity.LEGENDARY ? 12 : 8;
            for (int p = 0; p < rays; p++) {
                g.pose().pushMatrix(); g.pose().translate(cx, cy);
                g.pose().rotate((float)(p * Math.PI * 2 / rays + time * 0.13));
                int from = w / 2, length = Math.round((24 + 50 * t) * fade);
                g.fill(from, -2, from + length, 2, argb((int)(100 * fade * fade), enchanted[i] && p % 2 == 0 ? ENCHANTED : rgb));
                g.pose().popMatrix();
            }
        }
        int grow = Math.round(8 + 45 * t);
        g.outline(Math.round(cx) - w / 2 - grow, Math.round(cy) - h / 2 - grow, w + grow * 2, h + grow * 2, argb((int)(170 * fade * fade), rgb));
        int n = particleCount(Math.min(112, (rarities[i] == CardRarity.LEGENDARY ? 72 : 40) + (enchanted[i] ? 40 : 0)));
        // Deterministic trajectories: bounded work, no particle allocations or world emitters.
        for (int p = 0; p < n; p++) {
            double a = p * 2.39996;
            float speed = 25 + (p * 37 % 77), radius = 0.65F + age * speed / Math.max(30, w);
            int px = Math.round(cx + (float)Math.cos(a) * w * radius);
            int py = Math.round(cy + (float)Math.sin(a) * h * radius * 0.65F + age * age * 30);
            int color = p % 5 == 0 ? 0xFFF0CD : enchanted[i] && p % 2 == 0 ? ENCHANTED : rgb;
            spark(g, px, py, p % 7 == 0 ? 3 : p % 3 == 0 ? 2 : 1, argb((int)(240 * fade), color));
        }
    }

    private static void spark(GuiGraphicsExtractor g, int x, int y, int size, int color) {
        g.fill(x - size, y, x + size + 1, y + 1, color);
        g.fill(x, y - size, x + 1, y + size + 1, color);
    }
    private static int argb(int alpha, int rgb) { return Math.max(0, Math.min(255, alpha)) << 24 | rgb; }

    private void playSounds(int i) {
        float age = reveal.age(i, time), charge = reveal.charge(i);
        if (soundStage[i] == 0) {
            soundStage[i] = 1;
            playSound(SoundEvents.BOOK_PAGE_TURN, 0.85F, 0.65F);
            if (reveal.special(i)) playSound(SoundEvents.ENCHANTMENT_TABLE_USE, rarities[i] == CardRarity.LEGENDARY ? 0.55F : 0.8F, 0.55F);
        }
        if (soundStage[i] == 1 && age >= charge * 0.55F) {
            soundStage[i] = 2;
            if (reveal.special(i)) playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.45F);
        }
        if (soundStage[i] == 2 && age >= charge * 0.85F) {
            soundStage[i] = 3;
            if (enchanted[i] || rarities[i] == CardRarity.LEGENDARY) playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.35F, 0.55F);
        }
        if (soundStage[i] < 4 && reveal.burstAge(i, time) >= 0) {
            soundStage[i] = 4;
            switch (rarities[i]) {
                case RARE -> playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F, 0.65F);
                case EPIC -> { playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 0.9F); playSound(SoundEvents.PLAYER_LEVELUP, 1.45F, 0.45F); }
                case LEGENDARY -> { playSound(SoundEvents.PLAYER_LEVELUP, 0.75F, 0.9F); playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 0.8F); playSound(SoundEvents.GENERIC_EXPLODE.value(), 0.65F, 0.25F); }
                default -> { }
            }
            if (enchanted[i]) playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 1.35F, 0.8F);
        }
    }
    private void playSound(SoundEvent sound, float pitch, float volume) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }
    private int[] drawButton(GuiGraphicsExtractor g, Component label, int y, int mx, int my) {
        int w = font.width(label) + 20, x = (width - w) / 2;
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + 20;
        g.fill(x, y, x + w, y + 20, hover ? 0xFF906B3F : 0xFF543D28);
        g.outline(x, y, w, 20, 0xFFB69563);
        g.centeredText(font, label, width / 2, y + 6, 0xFFFFFFFF);
        return new int[]{x, y, w, 20};
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick);
        int active = reveal.active(time);
        if (active >= 0) {
            reveal.finishActive(time);
            soundStage[active] = 4;
            if (reveal.legendary(active)) return true;
        }
        if (inside(actionButton, event.x(), event.y())) {
            if (reveal.anyUnqueued()) reveal.queueAll(time);
            else if (reveal.allSettled(time)) onClose();
            else { reveal.skip(time); Arrays.fill(soundStage, 4); }
            return true;
        }
        for (int i = 0; i < cardRects.size(); i++) if (inside(cardRects.get(i), event.x(), event.y())) {
            if (!reveal.queued(i)) reveal.queue(i, time);
            else if (reveal.settled(i, time)) minecraft.gui.setScreen(new CardInspectScreen(cards.get(i), this));
            return true;
        }
        return true;
    }
    private static boolean inside(int[] r, double x, double y) {
        return r != null && x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3];
    }
    @Override public boolean isPauseScreen() { return false; }
}
