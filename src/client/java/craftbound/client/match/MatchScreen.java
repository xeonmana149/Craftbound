package craftbound.client.match;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import craftbound.card.CardDefinition;
import craftbound.card.Cards;
import craftbound.client.Tooltips;
import craftbound.client.card.CardRenderer;
import craftbound.item.CardItem;
import craftbound.network.MatchActionPayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

// A match against the Card Master (or, later, another player). Everything shown comes from the server.
// - Drag a card from your hand onto the board to play it. Items that need a target are dropped on the target;
//   a mob that picks a target (like the Enderman) asks for one after you place it.
// - Drag one of your ready mobs (green outline) onto an enemy mob or the enemy hero to attack.
// - Hover anything to see the full card. End Turn is on the right.
public class MatchScreen extends Screen {
    private static final int TOKEN_WIDTH = 46;
    private static final int TOKEN_HEIGHT = 60;
    private static final float HAND_SCALE = 0.42F;
    private static final int HERO_WIDTH = 120;
    private static final int HERO_HEIGHT = 26;

    private static final int BACKGROUND = 0xFF2A1A12;
    private static final int FELT = 0xFF2E5A34;
    private static final int FELT_DARK = 0xFF234628;
    private static final int WOOD = 0xFF6B4423;
    private static final int WOOD_DARK = 0xFF3A1C12;

    // --- What the server told us ---------------------------------------------------------------------------
    private record MinionView(int mid, Identifier id, boolean enchanted, int attack, int health, int maxHealth,
                              boolean ready, boolean shield, boolean frozen, boolean withered, boolean totem, boolean invulnerable) {
    }

    private record HandCard(int index, Identifier id, boolean enchanted, boolean playable, String target) {
    }

    private record PlayerView(String name, int health, int xp, int maxXp, int deck, int handCount,
                              List<HandCard> hand, List<MinionView> board) {
    }

    private int you;
    private boolean myTurn;
    private boolean over;
    private int winner;
    private final List<String> log = new ArrayList<>();
    private final PlayerView[] players = new PlayerView[2];

    // --- Where things are on screen (worked out each frame) -----------------------------------------------
    private record Spot(int x, int y, int width, int height, int side, int mid, int handIndex) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }

        int centerX() {
            return x + width / 2;
        }

        int centerY() {
            return y + height / 2;
        }
    }

    private final List<Spot> minionSpots = new ArrayList<>();
    private final List<Spot> handSpots = new ArrayList<>();
    private final Spot[] heroSpots = new Spot[2];
    private int fieldLeft, fieldRight, fieldTop, fieldBottom, handTop;
    private int[] endTurnButton;
    private int[] concedeButton;
    private boolean confirmConcede;

    // --- What you're doing with the mouse -----------------------------------------------------------------
    private HandCard draggingCard;
    private Spot attacker;
    // A mob that picks a target (the Enderman) waits here for you to click one.
    private HandCard awaitingTarget;

    public MatchScreen(String json) {
        super(Component.translatable("match_ui.craftbound.title"));
        update(json);
    }

    public void update(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        this.you = root.get("you").getAsInt();
        this.myTurn = root.get("myTurn").getAsBoolean();
        this.over = root.get("over").getAsBoolean();
        this.winner = root.get("winner").getAsInt();
        this.log.clear();
        for (JsonElement line : root.getAsJsonArray("log")) {
            this.log.add(line.getAsString());
        }
        JsonArray players = root.getAsJsonArray("players");
        for (int side = 0; side < 2; side++) {
            JsonObject p = players.get(side).getAsJsonObject();
            List<HandCard> hand = new ArrayList<>();
            if (p.has("hand")) {
                JsonArray cards = p.getAsJsonArray("hand");
                for (int i = 0; i < cards.size(); i++) {
                    JsonObject c = cards.get(i).getAsJsonObject();
                    hand.add(new HandCard(i, Identifier.parse(c.get("id").getAsString()), c.get("ench").getAsBoolean(),
                            c.get("playable").getAsBoolean(), c.get("target").getAsString()));
                }
            }
            List<MinionView> board = new ArrayList<>();
            for (JsonElement element : p.getAsJsonArray("board")) {
                JsonObject m = element.getAsJsonObject();
                board.add(new MinionView(m.get("mid").getAsInt(), Identifier.parse(m.get("id").getAsString()),
                        m.get("ench").getAsBoolean(), m.get("atk").getAsInt(), m.get("hp").getAsInt(), m.get("maxHp").getAsInt(),
                        m.get("ready").getAsBoolean(), m.get("shield").getAsBoolean(), m.get("frozen").getAsBoolean(),
                        m.get("withered").getAsBoolean(), m.get("totem").getAsBoolean(), m.get("invulnerable").getAsBoolean()));
            }
            this.players[side] = new PlayerView(p.get("name").getAsString(), p.get("health").getAsInt(), p.get("xp").getAsInt(),
                    p.get("maxXp").getAsInt(), p.get("deck").getAsInt(), p.get("handCount").getAsInt(), hand, board);
        }
        // Anything you were in the middle of no longer matches the new state.
        if (!this.myTurn || this.over) {
            this.draggingCard = null;
            this.attacker = null;
            this.awaitingTarget = null;
        }
    }

    private PlayerView me() {
        return this.players[this.you];
    }

    private PlayerView them() {
        return this.players[1 - this.you];
    }

    // --- Drawing ------------------------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, BACKGROUND);
        this.fieldLeft = 128;
        this.fieldRight = this.width - 92;
        this.fieldTop = 8 + HERO_HEIGHT + 6;
        this.handTop = this.height - Math.round(CardRenderer.HEIGHT * HAND_SCALE) - 4;
        this.fieldBottom = this.handTop - HERO_HEIGHT - 10;
        int middle = (this.fieldTop + this.fieldBottom) / 2;

        // The felt table, split into their half and yours.
        g.fill(this.fieldLeft - 4, this.fieldTop - 4, this.fieldRight + 4, this.fieldBottom + 4, WOOD);
        g.fill(this.fieldLeft, this.fieldTop, this.fieldRight, this.fieldBottom, FELT);
        g.fill(this.fieldLeft, middle - 1, this.fieldRight, middle + 1, FELT_DARK);

        this.minionSpots.clear();
        this.handSpots.clear();
        int fieldCenter = (this.fieldLeft + this.fieldRight) / 2;
        this.heroSpots[1 - this.you] = drawHero(g, them(), 1 - this.you, fieldCenter - HERO_WIDTH / 2, 8, mouseX, mouseY);
        this.heroSpots[this.you] = drawHero(g, me(), this.you, fieldCenter - HERO_WIDTH / 2, this.fieldBottom + 6, mouseX, mouseY);
        drawBoard(g, them(), 1 - this.you, this.fieldTop + (middle - this.fieldTop - TOKEN_HEIGHT) / 2, mouseX, mouseY);
        drawBoard(g, me(), this.you, middle + (this.fieldBottom - middle - TOKEN_HEIGHT) / 2, mouseX, mouseY);
        drawOpponentHand(g);
        drawHand(g, mouseX, mouseY);
        drawLog(g);
        drawSideButtons(g, mouseX, mouseY);

        // Whose turn, in the middle of the table.
        Component banner = this.awaitingTarget != null ? Component.translatable("match_ui.craftbound.pick_target")
                : Component.translatable(this.myTurn ? "match_ui.craftbound.your_turn" : "match_ui.craftbound.their_turn", them().name());
        int bannerWidth = this.font.width(banner) + 16;
        g.fill(fieldCenter - bannerWidth / 2, middle - 7, fieldCenter + bannerWidth / 2, middle + 7, WOOD_DARK);
        g.centeredText(this.font, banner, fieldCenter, middle - 4, this.myTurn ? 0xFFFFD27A : 0xFFE8D6B0);

        drawInteraction(g, mouseX, mouseY);
        drawPreview(g, mouseX, mouseY);
        if (this.over) {
            drawResult(g);
        }
    }

    private Spot drawHero(GuiGraphicsExtractor g, PlayerView player, int side, int x, int y, int mouseX, int mouseY) {
        Spot spot = new Spot(x, y, HERO_WIDTH, HERO_HEIGHT, side, -1, -1);
        boolean targetable = this.attacker != null && side != this.you && heroAttackable();
        g.fill(x, y, x + HERO_WIDTH, y + HERO_HEIGHT, WOOD);
        g.outline(x, y, HERO_WIDTH, HERO_HEIGHT, targetable ? 0xFFFF4A3A : WOOD_DARK);
        if (targetable && spot.contains(mouseX, mouseY)) {
            g.outline(x - 1, y - 1, HERO_WIDTH + 2, HERO_HEIGHT + 2, 0xFFFF4A3A);
        }
        CardRenderer.fitText(g, this.font, Component.literal(player.name()), x + 4, y + 3, HERO_WIDTH - 50, 0xFFFFFFFF, false);
        String health = "❤ " + player.health();
        g.text(this.font, health, x + HERO_WIDTH - 4 - this.font.width(health), y + 3, 0xFFFF5A4A);
        g.text(this.font, Component.translatable("match_ui.craftbound.xp", player.xp(), player.maxXp()), x + 4, y + 15, 0xFF7DFF5A);
        String deck = Component.translatable("match_ui.craftbound.deck", player.deck()).getString();
        g.text(this.font, deck, x + HERO_WIDTH - 4 - this.font.width(deck), y + 15, 0xFFB5A88D);
        return spot;
    }

    private void drawBoard(GuiGraphicsExtractor g, PlayerView player, int side, int y, int mouseX, int mouseY) {
        int count = player.board().size();
        int gap = 6;
        int x = (this.fieldLeft + this.fieldRight) / 2 - (count * TOKEN_WIDTH + (count - 1) * gap) / 2;
        for (MinionView minion : player.board()) {
            Spot spot = new Spot(x, y, TOKEN_WIDTH, TOKEN_HEIGHT, side, minion.mid(), -1);
            this.minionSpots.add(spot);
            drawMinion(g, minion, spot, mouseX, mouseY);
            x += TOKEN_WIDTH + gap;
        }
    }

    // A mob on the board: its art, attack (gold) and health (red), and outlines for what's going on with it.
    private void drawMinion(GuiGraphicsExtractor g, MinionView minion, Spot spot, int mouseX, int mouseY) {
        CardDefinition card = Cards.get(minion.id());
        int x = spot.x();
        int y = spot.y();
        g.fill(x, y, x + TOKEN_WIDTH, y + TOKEN_HEIGHT, 0xFF7A4434);
        g.fill(x + 3, y + 3, x + TOKEN_WIDTH - 3, y + TOKEN_HEIGHT - 16, 0xFF5BC8E0);
        g.pose().pushMatrix();
        g.pose().translate(x + TOKEN_WIDTH / 2.0F, y + (TOKEN_HEIGHT - 13) / 2.0F);
        g.pose().scale(1.75F, 1.75F);
        g.item(CardRenderer.placeholderArt(minion.id(), card), -8, -8);
        g.pose().popMatrix();
        if (minion.enchanted()) {
            CardRenderer.drawDiagonalShimmer(g, x, y, TOKEN_WIDTH, TOKEN_HEIGHT, 8, 0x60);
        }
        if (minion.frozen()) {
            g.fill(x, y, x + TOKEN_WIDTH, y + TOKEN_HEIGHT, 0x6080C8FF);
        }
        // Attack and health.
        g.fill(x, y + TOKEN_HEIGHT - 13, x + 14, y + TOKEN_HEIGHT, 0xFFE0A21B);
        g.centeredText(this.font, String.valueOf(minion.attack()), x + 7, y + TOKEN_HEIGHT - 11, 0xFFFFFFFF);
        g.fill(x + TOKEN_WIDTH - 14, y + TOKEN_HEIGHT - 13, x + TOKEN_WIDTH, y + TOKEN_HEIGHT, 0xFFC8322B);
        g.centeredText(this.font, String.valueOf(minion.health()), x + TOKEN_WIDTH - 7, y + TOKEN_HEIGHT - 11,
                minion.health() < minion.maxHealth() ? 0xFFFFB0A8 : 0xFFFFFFFF);
        // Little markers along the top: Totem and Withered.
        if (minion.totem()) {
            g.text(this.font, "T", x + 3, y + 3, 0xFFFFD27A);
        }
        if (minion.withered()) {
            g.text(this.font, "W", x + TOKEN_WIDTH - 9, y + 3, 0xFF8A8A8A);
        }
        // Outlines: Shield (silver), Golden Apple (gold), ready to attack (green), a valid target (red).
        if (minion.shield()) {
            g.outline(x - 2, y - 2, TOKEN_WIDTH + 4, TOKEN_HEIGHT + 4, 0xFFDDE6EE);
            g.outline(x - 3, y - 3, TOKEN_WIDTH + 6, TOKEN_HEIGHT + 6, 0xFFDDE6EE);
        }
        if (minion.invulnerable()) {
            g.outline(x - 1, y - 1, TOKEN_WIDTH + 2, TOKEN_HEIGHT + 2, 0xFFFFD700);
        }
        boolean mine = spot.side() == this.you;
        if (mine && minion.ready() && this.myTurn && this.attacker == null && this.draggingCard == null) {
            g.outline(x, y, TOKEN_WIDTH, TOKEN_HEIGHT, 0xFF7DFF5A);
        }
        if (isValidTarget(spot)) {
            int color = spot.contains(mouseX, mouseY) ? 0xFFFFFFFF : 0xFFFF4A3A;
            g.outline(x - 1, y - 1, TOKEN_WIDTH + 2, TOKEN_HEIGHT + 2, color);
            g.outline(x, y, TOKEN_WIDTH, TOKEN_HEIGHT, color);
        }
    }

    // The other player's hand: card backs in a row next to their hero.
    private void drawOpponentHand(GuiGraphicsExtractor g) {
        int x = (this.fieldLeft + this.fieldRight) / 2 + HERO_WIDTH / 2 + 8;
        for (int i = 0; i < them().handCount(); i++) {
            g.fill(x + i * 9, 8, x + i * 9 + 14, 8 + 20, 0xFF3B4048);
            g.outline(x + i * 9, 8, 14, 20, 0xFFA0692A);
        }
    }

    // Your hand along the bottom. Cards you can play now have a green outline.
    private void drawHand(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        List<HandCard> hand = me().hand();
        int cardWidth = Math.round(CardRenderer.WIDTH * HAND_SCALE);
        int cardHeight = Math.round(CardRenderer.HEIGHT * HAND_SCALE);
        int areaWidth = this.fieldRight - this.fieldLeft;
        int step = hand.size() <= 1 ? cardWidth : Math.min(cardWidth + 4, (areaWidth - cardWidth) / (hand.size() - 1));
        int total = hand.isEmpty() ? 0 : step * (hand.size() - 1) + cardWidth;
        int x = (this.fieldLeft + this.fieldRight) / 2 - total / 2;
        for (HandCard card : hand) {
            Spot spot = new Spot(x, this.handTop, step < cardWidth && card.index() < hand.size() - 1 ? step : cardWidth,
                    cardHeight, this.you, -1, card.index());
            this.handSpots.add(spot);
            if (card != this.draggingCard) {
                boolean hovered = spot.contains(mouseX, mouseY) && this.draggingCard == null && this.attacker == null;
                int y = hovered ? this.handTop - 6 : this.handTop;
                CardRenderer.draw(g, this.font, CardItem.of(card.id(), card.enchanted()), x, y, HAND_SCALE);
                if (!card.playable() || !this.myTurn) {
                    g.fill(x, y, x + cardWidth, y + cardHeight, 0x70000000);
                } else {
                    g.outline(x - 1, y - 1, cardWidth + 2, cardHeight + 2, 0xFF7DFF5A);
                }
            }
            x += step;
        }
    }

    // The battle log on the left, newest at the bottom.
    private void drawLog(GuiGraphicsExtractor g) {
        int x = 6;
        int width = 112;
        g.fill(x, 8, x + width, this.height - 8, 0xFF1E1410);
        g.outline(x, 8, width, this.height - 16, WOOD_DARK);
        g.text(this.font, Component.translatable("match_ui.craftbound.log"), x + 4, 12, 0xFFFFD27A);
        float scale = 0.75F;
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (String entry : this.log) {
            lines.addAll(this.font.split(Component.literal(entry), Math.round((width - 8) / scale)));
        }
        int maxLines = (int) ((this.height - 40) / (this.font.lineHeight * scale));
        int start = Math.max(0, lines.size() - maxLines);
        g.pose().pushMatrix();
        g.pose().translate(x + 4, 24);
        g.pose().scale(scale, scale);
        int y = 0;
        for (int i = start; i < lines.size(); i++) {
            g.text(this.font, lines.get(i), 0, y, 0xFFE8D6B0);
            y += this.font.lineHeight;
        }
        g.pose().popMatrix();
    }

    private void drawSideButtons(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = this.fieldRight + 10;
        int width = this.width - x - 6;
        int middle = (this.fieldTop + this.fieldBottom) / 2;
        this.endTurnButton = new int[]{x, middle - 14, width, 28};
        boolean hover = inside(this.endTurnButton, mouseX, mouseY);
        int color = !this.myTurn ? 0xFF3D3B38 : hover ? 0xFFFFE07A : 0xFFE2A52A;
        g.fill(x, middle - 14, x + width, middle + 14, color);
        g.outline(x, middle - 14, width, 28, WOOD_DARK);
        g.centeredText(this.font, Component.translatable("match_ui.craftbound.end_turn"), x + width / 2, middle - 4,
                this.myTurn ? 0xFF3A2306 : 0xFF8E877B);

        this.concedeButton = new int[]{x, 8, width, 14};
        boolean concedeHover = inside(this.concedeButton, mouseX, mouseY);
        g.fill(x, 8, x + width, 22, concedeHover ? 0xFF8A3A2A : 0xFF5A2A1E);
        g.centeredText(this.font, Component.translatable(this.confirmConcede ? "match_ui.craftbound.concede_confirm"
                : "match_ui.craftbound.concede"), x + width / 2, 11, 0xFFFFFFFF);
    }

    // The card being dragged, or the aiming line for an attack.
    private void drawInteraction(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (this.draggingCard != null) {
            int width = Math.round(CardRenderer.WIDTH * HAND_SCALE);
            int height = Math.round(CardRenderer.HEIGHT * HAND_SCALE);
            CardRenderer.draw(g, this.font, CardItem.of(this.draggingCard.id(), this.draggingCard.enchanted()),
                    mouseX - width / 2, mouseY - height / 2, HAND_SCALE);
        }
        if (this.attacker != null) {
            drawArrow(g, this.attacker.centerX(), this.attacker.centerY(), mouseX, mouseY);
        }
    }

    // A dotted red aiming line with a square head.
    private static void drawArrow(GuiGraphicsExtractor g, int fromX, int fromY, int toX, int toY) {
        double length = Math.hypot(toX - fromX, toY - fromY);
        for (double d = 0; d < length; d += 5) {
            int px = (int) (fromX + (toX - fromX) * d / length);
            int py = (int) (fromY + (toY - fromY) * d / length);
            g.fill(px - 1, py - 1, px + 2, py + 2, 0xFFE04030);
        }
        g.fill(toX - 3, toY - 3, toX + 4, toY + 4, 0xFFE04030);
    }

    // Hovering a hand card or a mob shows the full card on the right.
    private void drawPreview(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (this.draggingCard != null || this.attacker != null) {
            return;
        }
        ItemStack card = null;
        List<Component> status = new ArrayList<>();
        for (Spot spot : this.handSpots) {
            if (spot.contains(mouseX, mouseY)) {
                HandCard hand = me().hand().get(spot.handIndex());
                card = CardItem.of(hand.id(), hand.enchanted());
            }
        }
        for (Spot spot : this.minionSpots) {
            if (spot.contains(mouseX, mouseY)) {
                MinionView minion = findMinion(spot);
                if (minion != null) {
                    card = CardItem.of(minion.id(), minion.enchanted());
                    if (minion.frozen()) status.add(Component.translatable("match_ui.craftbound.status.frozen").withStyle(ChatFormatting.AQUA));
                    if (minion.withered()) status.add(Component.translatable("match_ui.craftbound.status.withered").withStyle(ChatFormatting.GRAY));
                    if (minion.totem()) status.add(Component.translatable("match_ui.craftbound.status.totem").withStyle(ChatFormatting.GOLD));
                    if (minion.invulnerable()) status.add(Component.translatable("match_ui.craftbound.status.invulnerable").withStyle(ChatFormatting.YELLOW));
                    if (spot.side() == this.you && !minion.ready() && this.myTurn) {
                        status.add(Component.translatable("match_ui.craftbound.status.not_ready").withStyle(ChatFormatting.GRAY));
                    }
                }
            }
        }
        if (card == null) {
            return;
        }
        float scale = Math.min(1.0F, (this.height - 20) / (float) CardRenderer.HEIGHT);
        int width = Math.round(CardRenderer.WIDTH * scale);
        int height = Math.round(CardRenderer.HEIGHT * scale);
        int x = mouseX < this.width / 2 ? this.fieldRight - width - 4 : this.fieldLeft + 4;
        int y = (this.height - height) / 2;
        CardRenderer.draw(g, this.font, card, x, y, scale);
        if (!status.isEmpty()) {
            Tooltips.show(g, this.font, status, mouseX, mouseY);
        }
    }

    private void drawResult(GuiGraphicsExtractor g) {
        g.fill(0, 0, this.width, this.height, 0xA0000000);
        String key = this.winner < 0 ? "match_ui.craftbound.draw" : this.winner == this.you ? "match_ui.craftbound.victory" : "match_ui.craftbound.defeat";
        g.pose().pushMatrix();
        g.pose().translate(this.width / 2.0F, this.height / 2.0F - 20);
        g.pose().scale(3.0F, 3.0F);
        g.centeredText(this.font, Component.translatable(key), 0, -4, this.winner == this.you ? 0xFFFFD27A : 0xFFE8D6B0);
        g.pose().popMatrix();
        g.centeredText(this.font, Component.translatable("match_ui.craftbound.click_to_leave"), this.width / 2, this.height / 2 + 14, 0xFFE8D6B0);
    }

    // --- Rules the screen needs to know (the server checks them again) -------------------------------------

    private MinionView findMinion(Spot spot) {
        for (MinionView minion : this.players[spot.side()].board()) {
            if (minion.mid() == spot.mid()) {
                return minion;
            }
        }
        return null;
    }

    private boolean enemyHasShield() {
        return them().board().stream().anyMatch(MinionView::shield);
    }

    private boolean heroAttackable() {
        return !enemyHasShield();
    }

    // Whether this mob is a place you could aim what you're doing right now.
    private boolean isValidTarget(Spot spot) {
        if (this.attacker != null) {
            MinionView minion = findMinion(spot);
            return spot.side() != this.you && minion != null && (!enemyHasShield() || minion.shield());
        }
        HandCard aiming = this.awaitingTarget != null ? this.awaitingTarget : this.draggingCard;
        if (aiming == null) {
            return false;
        }
        return switch (aiming.target()) {
            case "ANY_MOB" -> true;
            case "FRIENDLY_MOB" -> spot.side() == this.you;
            case "ENEMY_MOB" -> spot.side() != this.you;
            default -> false;
        };
    }

    // --- Input ------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (this.over) {
            this.onClose();
            return true;
        }
        if (inside(this.concedeButton, mouseX, mouseY)) {
            if (this.confirmConcede) {
                send(MatchActionPayload.CONCEDE, 0, null);
            }
            this.confirmConcede = !this.confirmConcede;
            return true;
        }
        this.confirmConcede = false;
        if (!this.myTurn) {
            return true;
        }
        // Waiting for the Enderman's target: left-click picks it, right-click plays it without one.
        if (this.awaitingTarget != null) {
            if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                send(MatchActionPayload.PLAY, this.awaitingTarget.index(), null);
                this.awaitingTarget = null;
                return true;
            }
            for (Spot spot : this.minionSpots) {
                if (spot.contains(mouseX, mouseY) && isValidTarget(spot)) {
                    send(MatchActionPayload.PLAY, this.awaitingTarget.index(), spot);
                    this.awaitingTarget = null;
                    return true;
                }
            }
            return true;
        }
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return true;
        }
        if (inside(this.endTurnButton, mouseX, mouseY)) {
            send(MatchActionPayload.END_TURN, 0, null);
            return true;
        }
        // Pick up a card from your hand (the top-most card under the mouse is the last one drawn).
        for (int i = this.handSpots.size() - 1; i >= 0; i--) {
            Spot spot = this.handSpots.get(i);
            if (spot.contains(mouseX, mouseY)) {
                HandCard card = me().hand().get(spot.handIndex());
                if (card.playable()) {
                    this.draggingCard = card;
                }
                return true;
            }
        }
        // Pick up one of your ready mobs to attack with.
        for (Spot spot : this.minionSpots) {
            if (spot.side() == this.you && spot.contains(mouseX, mouseY)) {
                MinionView minion = findMinion(spot);
                if (minion != null && minion.ready()) {
                    this.attacker = spot;
                }
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (this.draggingCard != null) {
            HandCard card = this.draggingCard;
            this.draggingCard = null;
            Spot target = null;
            for (Spot spot : this.minionSpots) {
                if (spot.contains(mouseX, mouseY) && isValidTargetFor(card, spot)) {
                    target = spot;
                }
            }
            boolean overTable = mouseY < this.handTop - 4;
            boolean isMob = Cards.get(card.id()).isMob();
            if (target != null) {
                send(MatchActionPayload.PLAY, card.index(), target);
            } else if (overTable && card.target().equals("NONE")) {
                send(MatchActionPayload.PLAY, card.index(), null);
            } else if (overTable && isMob) {
                // A mob that picks a target: ask for one if there's anything to pick, otherwise just play it.
                boolean anyTarget = this.minionSpots.stream().anyMatch(spot -> isValidTargetFor(card, spot));
                if (anyTarget) {
                    this.awaitingTarget = card;
                } else {
                    send(MatchActionPayload.PLAY, card.index(), null);
                }
            }
            return true;
        }
        if (this.attacker != null) {
            Spot from = this.attacker;
            this.attacker = null;
            for (Spot spot : this.minionSpots) {
                if (spot.side() != this.you && spot.contains(mouseX, mouseY)) {
                    send(MatchActionPayload.ATTACK, from.mid(), spot);
                    return true;
                }
            }
            Spot hero = this.heroSpots[1 - this.you];
            if (hero != null && hero.contains(mouseX, mouseY)) {
                send(MatchActionPayload.ATTACK, from.mid(), hero);
            }
            return true;
        }
        return super.mouseReleased(event);
    }

    private boolean isValidTargetFor(HandCard card, Spot spot) {
        return switch (card.target()) {
            case "ANY_MOB" -> true;
            case "FRIENDLY_MOB" -> spot.side() == this.you;
            case "ENEMY_MOB" -> spot.side() != this.you;
            default -> false;
        };
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // Esc while picking a target cancels that instead of closing the match.
        if (event.isEscape() && this.awaitingTarget != null) {
            this.awaitingTarget = null;
            return true;
        }
        return super.keyPressed(event);
    }

    // Sends a move to the server. `target` is a mob or hero spot, or null for none.
    private void send(int action, int index, Spot target) {
        if (target == null) {
            ClientPlayNetworking.send(new MatchActionPayload(action, index, false, -1, -1));
        } else {
            ClientPlayNetworking.send(new MatchActionPayload(action, index, target.mid() < 0, target.side(), target.mid()));
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
