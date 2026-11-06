package craftbound.client.match;

import craftbound.client.card.CardRenderer;
import craftbound.client.match.MatchState.Choice;
import craftbound.client.match.MatchState.HandCard;
import craftbound.game.TrialTraining;
import craftbound.sleeve.Sleeves;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

// Hand effects on the match screen:
// - The picker: when a Discard/Steal/Shuffle effect asks you to choose, the hand being picked from is laid out
//   face-up. Click a card to pick it. If time runs out the server picks at random.
// - The waiting banner: the other player is picking.
// - The peek: a Reveal hand effect shows the other player's hand for a few seconds.
final class HandPickOverlay {
    static final int PEEK_MS = 3500;
    static final int ENDER_INTRO_MS = 420;
    private static final int ENDER_OUTRO_MS = 240;

    private record Peeked(Identifier id, boolean enchanted, TrialTraining training) {}

    private final List<int[]> spots = new ArrayList<>();
    private Choice shownChoice;
    private long choiceReceived = -1;
    private boolean animating;
    private boolean sent;
    private final List<Peeked> peek = new ArrayList<>();
    private String peekName = "";
    private boolean peekEmpty;
    private long peekStart = -1;
    private boolean peekEnder;
    private Sleeves.Design peekBack;

    /** Call when a new state is shown, so the countdown and click guard follow the latest pick. */
    void sync(MatchState state, long now) {
        sync(state, now, false);
    }

    /** The pending choice waits for its preceding effects, while ordinary hand reveals remain visible. */
    void sync(MatchState state, long now, boolean animating) {
        this.animating = animating;
        if (state.choice != this.shownChoice) {
            this.shownChoice = state.choice;
            this.choiceReceived = -1;
            this.sent = false;
            this.spots.clear();
        }
        if (!picking(state)) this.spots.clear();
        // Start once the picker can be seen; hiding the same choice again must not restart its timer.
        else if (this.choiceReceived < 0) this.choiceReceived = now;
    }

    boolean picking(MatchState state) {
        Choice c = state.choice;
        return !this.animating && c != null && !state.over && c.chooser() == state.you && !c.cards().isEmpty();
    }

    boolean waiting(MatchState state) {
        return !this.animating && state.choice != null && !state.over && !picking(state);
    }

    /** Blocks hover previews and tooltips while the picker or peek covers the board. */
    boolean covering(MatchState state, long now) {
        return !state.over && (picking(state) || peeking(now));
    }

    static int revealDuration(boolean ender) { return PEEK_MS + (ender ? ENDER_INTRO_MS : 0); }

    boolean peeking(long now) {
        return this.peekStart >= 0 && now >= this.peekStart
                && now - this.peekStart < revealDuration(this.peekEnder);
    }

    /** One HAND_REVEAL event: index 0 (or -1 for an empty hand) starts a new peek. */
    void reveal(String victimName, int index, Identifier id, boolean enchanted, TrialTraining training, long now) {
        reveal(victimName, index, id, enchanted, training, now, false, null);
    }

    void reveal(String victimName, int index, Identifier id, boolean enchanted, TrialTraining training,
                long now, boolean ender, Sleeves.Design back) {
        if (index <= 0 || this.peekStart < 0) {
            this.peek.clear();
            this.peekName = victimName;
            this.peekEmpty = index < 0;
            this.peekStart = now;
            this.peekEnder = ender;
            this.peekBack = back;
        }
        if (index >= 0 && id != null) this.peek.add(new Peeked(id, enchanted, training));
    }

    /** The hand position you clicked, or -1. Only one pick is sent per state from the server. */
    int click(MatchState state, double mouseX, double mouseY) {
        if (!picking(state) || state.choice != this.shownChoice || this.sent) return -1;
        for (int i = 0; i < this.spots.size(); i++) {
            int[] r = this.spots.get(i);
            if (mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3]) {
                this.sent = true;
                return i;
            }
        }
        return -1;
    }

    void draw(GuiGraphicsExtractor g, Font font, MatchState state, int width, int height, int mouseX, int mouseY, long now) {
        this.spots.clear();
        Choice c = state.choice;
        if (picking(state)) {
            drawPicker(g, font, state, c, width, height, mouseX, mouseY, now);
            return;
        }
        if (waiting(state)) {
            String who = state.sides[c.chooser()].name;
            MatchArt.outlined(g, font, who + " is choosing a card...", width / 2.0F, height * 0.06F,
                    MatchArt.GOLD_LIGHT, 1.2F, true);
        }
        if (!state.over && peeking(now)) drawPeek(g, font, width, height, now);
    }

    private void drawPicker(GuiGraphicsExtractor g, Font font, MatchState state, Choice c, int width, int height,
                            int mouseX, int mouseY, long now) {
        g.fill(0, 0, width, height, 0xC0000000);
        boolean own = c.victim() == state.you;
        String victim = state.sides[c.victim()].name, owner = state.sides[c.owner()].name;
        String title = switch (c.action()) {
            case "CHOOSE_FROM_DECK" -> "Choose a card from your deck to draw";
            case "PICK_ONE" -> "Pick one to add to your hand";
            case "BREW_POTION" -> c.count() == 1 ? "Brewing Stand: choose a potion to brew" : "Brewing Stand: choose how to brew it";
            case "DISCARD_FROM_HAND" -> own ? "Choose a card to discard" : "Choose a card from " + victim + "'s hand to discard";
            case "STEAL_FROM_HAND" -> own ? "Choose a card to give to " + owner : "Choose a card to steal from " + victim + "'s hand";
            default -> own ? "Choose a card to shuffle into your deck" : "Choose a card from " + victim + "'s hand to shuffle into their deck";
        };
        MatchArt.outlined(g, font, title, width / 2.0F, height * 0.1F, MatchArt.GOLD_LIGHT, 1.6F, true);
        int seconds = Math.max(0, (int) Math.ceil(c.ticks() / 20.0 - (now - this.choiceReceived) / 1000.0));
        String hint = (c.count() > 1 ? c.count() + " cards left Â· " : "") + seconds + "s, then a random card";
        MatchArt.outlined(g, font, hint, width / 2.0F, height * 0.1F + 20, MatchArt.TEXT, 1.0F, true);
        List<HandCard> cards = c.cards();
        layoutRow(cards.size(), width, height);
        for (int i = 0; i < cards.size(); i++) {
            int[] r = this.spots.get(i);
            boolean hover = !this.sent && mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3];
            int y = hover ? r[1] - 8 : r[1];
            float scale = r[2] / (float) CardRenderer.WIDTH;
            HandCard card = cards.get(i);
            CardRenderer.drawTrial(g, font, card.id, card.enchanted, card.training, card.brew, r[0], y, scale);
            if (hover) g.outline(r[0] - 2, y - 2, r[2] + 4, r[3] + 4, MatchArt.GOLD);
        }
    }

    private void drawPeek(GuiGraphicsExtractor g, Font font, int width, int height, long now) {
        long age = now - this.peekStart;
        float t = age / (float) revealDuration(this.peekEnder);
        float fade = this.peekEnder
                ? Math.min(MatchArt.clamp(age / 180F), MatchArt.clamp((revealDuration(true) - age) / (float) ENDER_OUTRO_MS))
                : t < 0.08F ? t / 0.08F : t > 0.88F ? (1.0F - t) / 0.12F : 1.0F;
        g.fill(0, 0, width, height, MatchArt.alpha(0x90000000, fade));
        MatchArt.outlined(g, font, this.peekName + "'s hand", width / 2.0F, height * 0.1F,
                MatchArt.alpha(this.peekEnder ? 0xFF9CE7CE : MatchArt.GOLD_LIGHT, fade), 1.6F, true);
        if (this.peekEmpty || this.peek.isEmpty()) {
            MatchArt.outlined(g, font, "Empty", width / 2.0F, height * 0.45F, MatchArt.alpha(MatchArt.TEXT, fade), 1.4F, true);
            return;
        }
        layoutRow(this.peek.size(), width, height);
        for (int i = 0; i < this.peek.size(); i++) {
            int[] r = this.spots.get(i);
            Peeked card = this.peek.get(i);
            if (this.peekEnder) drawEnderCard(g, font, card, r, i, this.peek.size(), width, height, age);
            else CardRenderer.drawTrial(g, font, card.id(), card.enchanted(), card.training(),
                    r[0], r[1], r[2] / (float) CardRenderer.WIDTH);
        }
        this.spots.clear();
    }

    record RevealFrame(float travel, float scale, float flip, float fan, boolean face) {}

    /** A short, staggered fan/flip followed by the normal reading window, then a compact collapse. */
    static RevealFrame enderFrame(long age, int index, int count) {
        float delay = count <= 1 ? 0 : 120F * index / (count - 1);
        float enter = MatchArt.clamp((age - delay) / (ENDER_INTRO_MS - delay));
        float leave = MatchArt.clamp((age - revealDuration(true) + ENDER_OUTRO_MS) / (float) ENDER_OUTRO_MS);
        float out = leave * leave;
        float travel = MatchArt.easeOut(enter) * (1 - out);
        float scale = (.6F + .4F * MatchArt.easeOut(enter)) * (1 - out);
        float flip = Math.max(.04F, Math.abs((float) Math.cos(enter * Math.PI)));
        float fan = 1 - MatchArt.easeOut(enter) + out;
        return new RevealFrame(travel, scale, flip, fan, enter >= .5F && leave < .72F);
    }

    private void drawEnderCard(GuiGraphicsExtractor g, Font font, Peeked card, int[] r, int index,
                               int count, int width, int height, long age) {
        RevealFrame frame = enderFrame(age, index, count);
        if (frame.scale() <= 0) return;
        float offset = index - (count - 1) / 2F;
        float fromX = width / 2F + offset * 9, fromY = height * .055F + Math.abs(offset) * 2;
        float x = fromX + (r[0] + r[2] / 2F - fromX) * frame.travel();
        float y = fromY + (r[1] + r[3] / 2F - fromY) * frame.travel()
                - 12 * (float) Math.sin(frame.travel() * Math.PI);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().rotate(offset * .055F * frame.fan());
        g.pose().scale(frame.scale() * frame.flip(), frame.scale());
        float scale = r[2] / (float) CardRenderer.WIDTH;
        if (frame.face())
            CardRenderer.drawTrial(g, font, card.id(), card.enchanted(), card.training(), -r[2] / 2, -r[3] / 2, scale);
        else CardRenderer.drawBack(g, this.peekBack == null ? Sleeves.get(Sleeves.CLASSIC) : this.peekBack,
                -r[2] / 2, -r[3] / 2, scale);
        int color = index % 2 == 0 ? 0xFF74DCC4 : 0xFFB89BE8;
        g.outline(-r[2] / 2 - 1, -r[3] / 2 - 1, r[2] + 2, r[3] + 2,
                MatchArt.alpha(color, .55F * frame.scale()));
        g.pose().popMatrix();
    }

    // Cards in one centred row, as large as fits (up to two rows' worth would be 10 cards; one row fits).
    private void layoutRow(int count, int width, int height) {
        this.spots.clear();
        if (count <= 0) return;
        int gap = 6;
        float byWidth = (width - 32 - gap * (count - 1)) / (float) (count * CardRenderer.WIDTH);
        float byHeight = height * 0.55F / CardRenderer.HEIGHT;
        float scale = Math.max(0.2F, Math.min(1.0F, Math.min(byWidth, byHeight)));
        int w = Math.round(CardRenderer.WIDTH * scale), h = Math.round(CardRenderer.HEIGHT * scale);
        int total = count * w + (count - 1) * gap;
        int x = (width - total) / 2, y = Math.round(height * 0.5F - h / 2.0F + 10);
        for (int i = 0; i < count; i++) this.spots.add(new int[]{x + i * (w + gap), y, w, h});
    }
}
