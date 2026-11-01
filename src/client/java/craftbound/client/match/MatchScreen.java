package craftbound.client.match;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import craftbound.card.CardClass;
import craftbound.card.CardDefinition;
import craftbound.card.Cards;
import craftbound.client.Tooltips;
import craftbound.client.card.CardRenderer;
import craftbound.item.CardItem;
import craftbound.network.MatchActionPayload;
import craftbound.sleeve.Sleeves;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
import net.minecraft.world.item.Items;

// A match, laid out like the Mobdeck prototype: a dungeon table.
// - Left: the opponent's nameplate at the top, the battle log, and your nameplate at the bottom.
// - Middle: a plank board with each hero's portrait on its edge, the opponent's hand fanned along the top,
//   and your hand fanned along the bottom.
// - Right: each side's XP bar and deck, and the big END TURN button.
// Drag a card from your hand onto the board to play it (items onto their target); drag a ready mob (green glow)
// onto an enemy mob or hero to attack. Hover anything to see the full card.
public class MatchScreen extends Screen {
    private static final int LEFT_COLUMN = 118;
    private static final int RIGHT_COLUMN = 104;
    private static final int HERO_SIZE = 32;

    // Colours from the prototype.
    private static final int MORTAR = 0xFF121315;
    private static final int BRICK_A = 0xFF2A2B2F;
    private static final int BRICK_B = 0xFF24252A;
    private static final int PLANK_A = 0xFF8E5F31;
    private static final int PLANK_B = 0xFF7F552B;
    private static final int PLANK_SEAM = 0xFF4B2F16;
    private static final int WOOD_DARK = 0xFF3A2413;
    private static final int WOOD_MID = 0xFF8A5A2B;
    private static final int WOOD_LIGHT = 0xFFB07A40;
    private static final int GOLD = 0xFFE8B84A;
    private static final int GOLD_DARK = 0xFF9C6D1C;
    private static final int GOLD_LIGHT = 0xFFFFE08A;
    private static final int PANEL = 0xFF25211C;
    private static final int PANEL_DARK = 0xFF1B1814;
    private static final int PANEL_EDGE = 0xFF4F3D27;
    private static final int TEXT = 0xFFF1E8D4;
    private static final int TEXT_DIM = 0xFFB5A88D;
    private static final int XP = 0xFF80FF20;
    private static final int XP_DARK = 0xFF2C5410;
    private static final int GLOW = 0xFF7DFF5A;
    private static final int RED = 0xFFD63A32;

    // --- What the server told us ---------------------------------------------------------------------------
    private record MinionView(int mid, Identifier id, boolean enchanted, int attack, int health, int maxHealth,
                              boolean ready, boolean shield, boolean frozen, boolean withered, boolean totem, boolean invulnerable) {
    }

    private record HandCard(int index, Identifier id, boolean enchanted, boolean playable, String target) {
    }

    private record PlayerView(String name, String deckClass, boolean ai, int health, int xp, int maxXp, int deck,
                              int handCount, List<HandCard> hand, List<MinionView> board) {
    }

    private record LogLine(String text, Identifier card, int round, String turnOf) {
    }

    private int you;
    private int turn;
    private boolean myTurn;
    private boolean over;
    private int winner;
    private final List<LogLine> log = new ArrayList<>();
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
    private int boardLeft, boardRight, boardTop, boardBottom, middle, handTop;
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
        this.turn = root.get("turn").getAsInt();
        this.myTurn = root.get("myTurn").getAsBoolean();
        this.over = root.get("over").getAsBoolean();
        this.winner = root.get("winner").getAsInt();
        this.log.clear();
        for (JsonElement element : root.getAsJsonArray("log")) {
            JsonObject line = element.getAsJsonObject();
            String card = line.get("card").getAsString();
            this.log.add(new LogLine(line.get("text").getAsString(), card.isEmpty() ? null : Identifier.parse(card),
                    line.get("round").getAsInt(), line.get("turnOf").getAsString()));
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
            this.players[side] = new PlayerView(p.get("name").getAsString(), p.get("cls").getAsString(), p.get("ai").getAsBoolean(),
                    p.get("health").getAsInt(), p.get("xp").getAsInt(), p.get("maxXp").getAsInt(), p.get("deck").getAsInt(),
                    p.get("handCount").getAsInt(), hand, board);
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

    // --- Layout and drawing -------------------------------------------------------------------------------

    private float handScale() {
        return Math.min(0.62F, this.height * 0.34F / CardRenderer.HEIGHT);
    }

    private void layout() {
        this.boardLeft = LEFT_COLUMN + 10;
        this.boardRight = this.width - RIGHT_COLUMN - 4;
        this.boardTop = 18;
        this.boardBottom = this.height - 44;
        this.middle = (this.boardTop + this.boardBottom) / 2;
        this.handTop = this.height - Math.round(CardRenderer.HEIGHT * handScale() * 0.6F);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        layout();
        drawBricks(g);
        drawBoardFrame(g);
        this.minionSpots.clear();
        this.handSpots.clear();

        int centerX = (this.boardLeft + this.boardRight) / 2;
        drawOpponentHand(g, centerX);
        this.heroSpots[1 - this.you] = drawHero(g, them(), 1 - this.you, centerX - HERO_SIZE / 2, this.boardTop + 8, mouseX, mouseY);
        this.heroSpots[this.you] = drawHero(g, me(), this.you, centerX - HERO_SIZE / 2, this.boardBottom - 8 - HERO_SIZE, mouseX, mouseY);

        int theirRowTop = this.boardTop + 8 + HERO_SIZE + 6;
        int myRowBottom = this.boardBottom - 8 - HERO_SIZE - 10;
        drawRow(g, them(), 1 - this.you, theirRowTop, this.middle - 12, mouseX, mouseY);
        drawRow(g, me(), this.you, this.middle + 12, myRowBottom, mouseX, mouseY);
        drawBanner(g, centerX);

        drawNameplate(g, them(), 1 - this.you, 6, 6);
        drawNameplate(g, me(), this.you, 6, this.height - 38);
        drawLog(g, 6, 44, LEFT_COLUMN - 6, this.height - 44 - 44);
        drawRightColumn(g, mouseX, mouseY);
        drawHand(g, mouseX, mouseY);

        drawInteraction(g, mouseX, mouseY);
        drawPreview(g, mouseX, mouseY);
        if (this.over) {
            drawResult(g);
        }
    }

    // A dark brick wall behind everything.
    private void drawBricks(GuiGraphicsExtractor g) {
        g.fill(0, 0, this.width, this.height, MORTAR);
        int brick = 24;
        for (int row = 0; row * brick / 2 < this.height; row++) {
            int y = row * brick / 2;
            int offset = row % 2 == 0 ? 0 : brick / 2;
            for (int x = -offset; x < this.width; x += brick) {
                g.fill(x + 1, y + 1, x + brick - 1, y + brick / 2 - 1, (row + x / brick) % 2 == 0 ? BRICK_A : BRICK_B);
            }
        }
    }

    // The board: horizontal planks inside a gold-trimmed wooden frame, split down the middle.
    private void drawBoardFrame(GuiGraphicsExtractor g) {
        int x0 = this.boardLeft;
        int x1 = this.boardRight;
        int y0 = this.boardTop;
        int y1 = this.boardBottom;
        g.fill(x0 - 9, y0 - 9, x1 + 9, y1 + 9, 0xFF000000);
        g.fill(x0 - 8, y0 - 8, x1 + 8, y1 + 8, WOOD_DARK);
        g.fill(x0 - 6, y0 - 6, x1 + 6, y1 + 6, WOOD_LIGHT);
        g.fill(x0 - 5, y0 - 5, x1 + 5, y1 + 5, WOOD_MID);
        g.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, WOOD_DARK);
        for (int y = y0, plank = 0; y < y1; y += 18, plank++) {
            g.fill(x0, y, x1, Math.min(y + 17, y1), plank % 2 == 0 ? PLANK_A : PLANK_B);
            g.fill(x0, Math.min(y + 17, y1), x1, Math.min(y + 18, y1), PLANK_SEAM);
        }
        g.outline(x0, y0, x1 - x0, y1 - y0, GOLD_DARK);
        g.fill(x0 + 8, this.middle - 1, x1 - 8, this.middle + 1, GOLD_DARK);
    }

    // The banner on the middle line: whose turn, and which round.
    private void drawBanner(GuiGraphicsExtractor g, int centerX) {
        Component text = this.awaitingTarget != null ? Component.translatable("match_ui.craftbound.pick_target")
                : Component.translatable(this.myTurn ? "match_ui.craftbound.your_turn" : "match_ui.craftbound.their_turn",
                them().name(), (this.turn + 1) / 2);
        int width = this.font.width(text) + 18;
        int x = centerX - width / 2;
        g.fill(x - 1, this.middle - 9, x + width + 1, this.middle + 9, 0xFF000000);
        g.fillGradient(x, this.middle - 8, x + width, this.middle + 8, 0xFF6B4423, WOOD_DARK);
        g.outline(x + 1, this.middle - 7, width - 2, 14, GOLD_DARK);
        g.centeredText(this.font, text, centerX, this.middle - 4, this.myTurn ? GOLD_LIGHT : 0xFFFFB4A8);
    }

    // A hero: an octagonal portrait on the board's edge, with a heart showing their health.
    private Spot drawHero(GuiGraphicsExtractor g, PlayerView player, int side, int x, int y, int mouseX, int mouseY) {
        Spot spot = new Spot(x, y, HERO_SIZE, HERO_SIZE, side, -1, -1);
        boolean targetable = this.attacker != null && side != this.you && !enemyHasShield();
        int frame = targetable ? (spot.contains(mouseX, mouseY) ? GOLD_LIGHT : RED) : GOLD;
        drawOctagon(g, x - 2, y - 2, HERO_SIZE + 4, frame);
        drawOctagon(g, x, y, HERO_SIZE, 0xFF7AA7C9);
        ItemStack face = new ItemStack(player.ai() ? Items.VILLAGER_SPAWN_EGG : Items.PLAYER_HEAD);
        g.pose().pushMatrix();
        g.pose().translate(x + HERO_SIZE / 2.0F, y + HERO_SIZE / 2.0F);
        g.pose().scale(1.6F, 1.6F);
        g.item(face, -8, -8);
        g.pose().popMatrix();
        drawHeart(g, x + HERO_SIZE - 6, y + HERO_SIZE - 10, player.health(), player.health() < 20);
        return spot;
    }

    // A square with its corners cut off.
    private static void drawOctagon(GuiGraphicsExtractor g, int x, int y, int size, int color) {
        int cut = size / 4;
        for (int row = 0; row < size; row++) {
            int inset = row < cut ? cut - row : row >= size - cut ? row - (size - cut) + 1 : 0;
            g.fill(x + inset, y + row, x + size - inset, y + row + 1, color);
        }
    }

    // A red heart with a number in it (darker when hurt), like the prototype's health badges.
    private void drawHeart(GuiGraphicsExtractor g, int x, int y, int value, boolean hurt) {
        int color = hurt ? 0xFF7A1A14 : 0xFFD63A32;
        int[][] rows = {{1, 3, 6, 8}, {0, 9}, {0, 9}, {0, 9}, {1, 8}, {2, 7}, {3, 6}, {4, 5}};
        g.fill(x - 1, y - 1, x + 11, y + 9, 0xFF000000);
        for (int r = 0; r < rows.length; r++) {
            int[] span = rows[r];
            for (int i = 0; i + 1 < span.length; i += 2) {
                g.fill(x + span[i], y + r, x + span[i + 1] + 1, y + r + 1, color);
            }
        }
        String text = String.valueOf(value);
        g.text(this.font, text, x + 5 - this.font.width(text) / 2, y, hurt ? 0xFFFF2A1A : 0xFFFFFFFF);
    }

    // A row of mobs, centred, or "NO MOBS" when it's empty.
    private void drawRow(GuiGraphicsExtractor g, PlayerView player, int side, int top, int bottom, int mouseX, int mouseY) {
        int height = Math.min(64, bottom - top);
        int width = Math.round(height * 0.78F);
        int y = top + (bottom - top - height) / 2;
        int centerX = (this.boardLeft + this.boardRight) / 2;
        if (player.board().isEmpty()) {
            g.centeredText(this.font, Component.translatable("match_ui.craftbound.no_mobs"), centerX, y + height / 2 - 4, 0x8C281608);
            return;
        }
        int gap = 10;
        int count = player.board().size();
        int x = centerX - (count * width + (count - 1) * gap) / 2;
        for (MinionView minion : player.board()) {
            Spot spot = new Spot(x, y, width, height, side, minion.mid(), -1);
            this.minionSpots.add(spot);
            drawMinion(g, minion, spot, mouseX, mouseY);
            x += width + gap;
        }
    }

    // A mob on the board, like the prototype: a stone frame, art window, name plate, type, attack and health.
    private void drawMinion(GuiGraphicsExtractor g, MinionView minion, Spot spot, int mouseX, int mouseY) {
        CardDefinition card = Cards.get(minion.id());
        int x = spot.x();
        int y = spot.y();
        int w = spot.width();
        int h = spot.height();
        boolean mine = spot.side() == this.you;
        // Glow and outlines go behind the frame: ready to attack (green), a valid target (red/white).
        if (mine && minion.ready() && this.myTurn && this.attacker == null && this.draggingCard == null) {
            g.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0x807DFF5A);
            g.outline(x - 2, y - 2, w + 4, h + 4, GLOW);
        }
        if (isValidTarget(spot)) {
            g.outline(x - 3, y - 3, w + 6, h + 6, spot.contains(mouseX, mouseY) ? GOLD_LIGHT : RED);
            g.outline(x - 2, y - 2, w + 4, h + 4, spot.contains(mouseX, mouseY) ? GOLD_LIGHT : RED);
        }
        if (minion.shield()) {
            g.outline(x - 4, y - 4, w + 8, h + 8, 0xFF000000);
            g.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0xFFE8EDF2);
        }
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        g.fillGradient(x, y, x + w, y + h, 0xFF5D5E66, 0xFF3B3C42);
        if (!mine) {
            g.fillGradient(x, y, x + w, y + h, 0xFF6A5856, 0xFF4A3C3B);
        }
        int artBottom = y + Math.round(h * 0.55F);
        g.fill(x + 3, y + 3, x + w - 3, artBottom, 0xFF2B2C31);
        g.pose().pushMatrix();
        g.pose().translate(x + w / 2.0F, (y + 3 + artBottom) / 2.0F);
        float artScale = Math.min(1.8F, (artBottom - y - 6) / 16.0F);
        g.pose().scale(artScale, artScale);
        g.item(CardRenderer.placeholderArt(minion.id(), card), -8, -8);
        g.pose().popMatrix();
        // Name plate and type.
        g.fill(x + 1, artBottom + 1, x + w - 1, artBottom + 10, 0xFFCFCFD4);
        CardRenderer.fitText(g, this.font, Cards.name(minion.id()), x + w / 2, artBottom + 2, w - 4, 0xFF1B1B1B, true);
        if (card.mobType() != null) {
            String type = card.mobType().displayName().getString().toUpperCase(Locale.ROOT);
            CardRenderer.fitText(g, this.font, Component.literal(type), x + w / 2, artBottom + 12, w - 6, 0xFFCFCFD4, true);
        }
        if (minion.enchanted()) {
            CardRenderer.drawDiagonalShimmer(g, x, y, w, h, 8, 0x60);
        }
        if (minion.frozen()) {
            g.fill(x, y, x + w, y + h, 0x6080C8FF);
        }
        if (minion.totem()) {
            g.text(this.font, "T", x + 3, y + 3, GOLD_LIGHT);
        }
        if (minion.withered()) {
            g.text(this.font, "W", x + w - 9, y + 3, 0xFF8A8A8A);
        }
        if (minion.invulnerable()) {
            g.outline(x, y, w, h, 0xFFFFD700);
        }
        // Attack (gold, bottom-left) and health (heart, bottom-right), hanging off the corners.
        drawAttack(g, x - 4, y + h - 9, minion.attack());
        drawHeart(g, x + w - 7, y + h - 7, minion.health(), minion.health() < minion.maxHealth());
    }

    private void drawAttack(GuiGraphicsExtractor g, int x, int y, int value) {
        drawOctagon(g, x - 1, y - 1, 14, 0xFF000000);
        drawOctagon(g, x, y, 12, 0xFFE4A828);
        g.centeredText(this.font, String.valueOf(value), x + 6, y + 2, 0xFFFFFFFF);
    }

    // The opponent's hand: card backs fanned along the top edge, curving towards them.
    private void drawOpponentHand(GuiGraphicsExtractor g, int centerX) {
        int count = them().handCount();
        float scale = 0.2F;
        int width = Math.round(CardRenderer.WIDTH * scale);
        for (int i = 0; i < count; i++) {
            float offset = i - (count - 1) / 2.0F;
            g.pose().pushMatrix();
            g.pose().translate(centerX + offset * (width - 4), -6 + Math.abs(offset) * 1.5F);
            g.pose().rotate((float) Math.toRadians(-offset * 5));
            g.pose().translate(-width / 2.0F, 0);
            CardRenderer.drawBack(g, Sleeves.get(Sleeves.CLASSIC), 0, 0, scale);
            g.pose().popMatrix();
        }
    }

    // A nameplate in the corner: face, name, class chip, and XP or hand/deck counts.
    private void drawNameplate(GuiGraphicsExtractor g, PlayerView player, int side, int x, int y) {
        int width = LEFT_COLUMN - 6;
        int height = 32;
        boolean active = !this.over && ((side == this.you) == this.myTurn);
        g.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF000000);
        g.fillGradient(x, y, x + width, y + height, PANEL, PANEL_DARK);
        g.outline(x + 1, y + 1, width - 2, height - 2, active ? GOLD : PANEL_EDGE);
        g.fill(x + 4, y + 5, x + 26, y + 27, 0xFF7AA7C9);
        g.item(new ItemStack(player.ai() ? Items.VILLAGER_SPAWN_EGG : Items.PLAYER_HEAD), x + 7, y + 8);
        String name = (side == this.you ? Component.translatable("match_ui.craftbound.you").getString() : player.name()).toUpperCase(Locale.ROOT);
        CardRenderer.fitText(g, this.font, Component.literal(name).withStyle(ChatFormatting.BOLD), x + 30, y + 5, width - 34, TEXT, false);
        // Class chip.
        Component chip = player.ai() ? Component.translatable("match_ui.craftbound.card_master_chip")
                : classOf(player).displayName().copy();
        String chipText = chip.getString().toUpperCase(Locale.ROOT);
        int chipWidth = Math.round(this.font.width(chipText) * 0.6F) + 6;
        g.fill(x + 30, y + 17, x + 30 + chipWidth, y + 26, classColor(player));
        CardRenderer.fitText(g, this.font, Component.literal(chipText), x + 33, y + 18, chipWidth - 6, 0xFFFFFFFF, false);
        String meta = side == this.you
                ? Component.translatable("match_ui.craftbound.meta_you", player.hand().size(), player.deck()).getString()
                : Component.translatable("match_ui.craftbound.meta_them", player.maxXp(), player.handCount()).getString();
        CardRenderer.fitText(g, this.font, Component.literal(meta), x + 34 + chipWidth, y + 18, width - chipWidth - 38, TEXT_DIM, false);
    }

    private CardClass classOf(PlayerView player) {
        try {
            return CardClass.valueOf(player.deckClass());
        } catch (IllegalArgumentException e) {
            return CardClass.NEUTRAL;
        }
    }

    private int classColor(PlayerView player) {
        if (player.ai()) {
            return 0xFF6A3FA0;
        }
        return switch (classOf(player)) {
            case FIGHTER -> 0xFFB23A2E;
            case BUILDER -> 0xFF3F62B0;
            case FARMER -> 0xFF4E8A2A;
            default -> 0xFF5D5E66;
        };
    }

    // The battle log: newest at the top, with a header for each turn and the card's icon beside each line.
    private void drawLog(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        g.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF000000);
        g.fillGradient(x, y, x + width, y + height, PANEL, PANEL_DARK);
        g.outline(x + 1, y + 1, width - 2, height - 2, PANEL_EDGE);
        g.text(this.font, Component.translatable("match_ui.craftbound.log").withStyle(ChatFormatting.BOLD), x + 5, y + 5, TEXT);
        g.fill(x + 2, y + 16, x + width - 2, y + 17, PANEL_EDGE);
        float scale = 0.7F;
        int lineHeight = Math.round(this.font.lineHeight * scale);
        int cursor = y + 21;
        int bottom = y + height - 4;
        g.enableScissor(x + 2, y + 18, x + width - 2, bottom);
        for (int i = this.log.size() - 1; i >= 0 && cursor < bottom; i--) {
            LogLine line = this.log.get(i);
            if (!line.turnOf().isEmpty()) {
                // A turn header: "TURN 2 · YOU".
                String who = line.turnOf().equals(me().name()) ? Component.translatable("match_ui.craftbound.you").getString() : line.turnOf();
                Component header = Component.translatable("match_ui.craftbound.turn_header", line.round(), who.toUpperCase(Locale.ROOT));
                g.fill(x + 4, cursor, x + width - 4, cursor + 1, PANEL_EDGE);
                CardRenderer.fitText(g, this.font, header, x + width / 2, cursor + 3, width - 10, GOLD, true);
                cursor += 14;
                continue;
            }
            // An icon box, then the text wrapped beside it.
            int iconSize = 12;
            g.fill(x + 4, cursor, x + 4 + iconSize, cursor + iconSize, 0xFF3A3631);
            if (line.card() != null && Cards.get(line.card()) != null) {
                g.pose().pushMatrix();
                g.pose().translate(x + 4 + iconSize / 2.0F, cursor + iconSize / 2.0F);
                g.pose().scale(0.7F, 0.7F);
                g.item(CardRenderer.placeholderArt(line.card(), Cards.get(line.card())), -8, -8);
                g.pose().popMatrix();
            }
            List<FormattedCharSequence> wrapped = this.font.split(Component.literal(line.text()),
                    Math.round((width - iconSize - 14) / scale));
            g.pose().pushMatrix();
            g.pose().translate(x + iconSize + 8, cursor + 1);
            g.pose().scale(scale, scale);
            int textY = 0;
            for (FormattedCharSequence part : wrapped) {
                g.text(this.font, part, 0, textY, TEXT);
                textY += this.font.lineHeight;
            }
            g.pose().popMatrix();
            cursor += Math.max(iconSize, wrapped.size() * lineHeight) + 4;
        }
        g.disableScissor();
    }

    // Right column: their XP and deck at the top, END TURN in the middle, your deck and XP at the bottom.
    private void drawRightColumn(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = this.width - RIGHT_COLUMN + 4;
        int width = RIGHT_COLUMN - 10;
        drawXp(g, them(), x, this.boardTop + 6, width);
        drawDeck(g, them(), x, this.boardTop + 30, Component.translatable("match_ui.craftbound.their_deck"));

        // END TURN: big and gold on your turn, grey otherwise.
        this.endTurnButton = new int[]{x, this.middle - 15, width, 30};
        boolean hover = inside(this.endTurnButton, mouseX, mouseY);
        g.fill(x - 2, this.middle - 17, x + width + 2, this.middle + 17, 0xFF000000);
        if (this.myTurn) {
            g.fillGradient(x, this.middle - 15, x + width, this.middle + 15, hover ? 0xFFFFE07A : 0xFFFFD25A, hover ? 0xFFF0B33A : 0xFFE2A52A);
            g.fill(x, this.middle - 15, x + width, this.middle - 13, 0xFFFFF0B0);
            g.fill(x, this.middle + 12, x + width, this.middle + 15, 0xFFA06C12);
        } else {
            g.fill(x, this.middle - 15, x + width, this.middle + 15, 0xFF3D3B38);
        }
        Component endTurn = Component.translatable("match_ui.craftbound.end_turn").withStyle(ChatFormatting.BOLD);
        g.centeredText(this.font, endTurn, x + width / 2, this.middle - 4, this.myTurn ? 0xFF3A2306 : 0xFF8E877B);

        this.concedeButton = new int[]{x + width / 4, this.middle + 22, width / 2, 12};
        boolean concedeHover = inside(this.concedeButton, mouseX, mouseY);
        g.fill(x + width / 4, this.middle + 22, x + width * 3 / 4, this.middle + 34, concedeHover ? 0xFF8A3A2A : 0xFF4A2A1E);
        CardRenderer.fitText(g, this.font, Component.translatable(this.confirmConcede ? "match_ui.craftbound.concede_confirm"
                : "match_ui.craftbound.concede"), x + width / 2, this.middle + 24, width / 2 - 4, TEXT, true);

        drawDeck(g, me(), x, this.boardBottom - 70, Component.translatable("match_ui.craftbound.your_deck"));
        drawXp(g, me(), x, this.boardBottom - 24, width);
    }

    // A Minecraft-style XP bar with the number above it.
    private void drawXp(GuiGraphicsExtractor g, PlayerView player, int x, int y, int width) {
        String number = player.xp() + "/" + player.maxXp();
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(1.4F, 1.4F);
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            g.text(this.font, number, d[0], d[1], 0xFF000000, false);
        }
        g.text(this.font, number, 0, 0, XP, false);
        g.pose().popMatrix();
        int barY = y + 14;
        g.fill(x - 1, barY - 1, x + width + 1, barY + 7, 0xFF000000);
        g.fill(x, barY, x + width, barY + 6, 0xFF0D1A06);
        int cell = (width - 11) / 10;
        for (int i = 0; i < 10; i++) {
            int cx = x + 1 + i * (cell + 1);
            int color = i >= player.maxXp() ? 0xFF14240A : i >= player.xp() ? 0xFF4B6A2E : XP;
            g.fill(cx, barY + 1, cx + cell, barY + 5, color);
        }
    }

    // A face-down deck pile with how many cards are left.
    private void drawDeck(GuiGraphicsExtractor g, PlayerView player, int x, int y, Component label) {
        float scale = 0.22F;
        int width = Math.round(CardRenderer.WIDTH * scale);
        int height = Math.round(CardRenderer.HEIGHT * scale);
        g.fill(x + 4, y - 2, x + 4 + width, y - 2 + height, WOOD_DARK);
        g.fill(x + 2, y - 1, x + 2 + width, y - 1 + height, WOOD_DARK);
        CardRenderer.drawBack(g, Sleeves.get(Sleeves.CLASSIC), x, y, scale);
        String count = String.valueOf(player.deck());
        int boxWidth = this.font.width(count) + 8;
        g.fill(x + width - boxWidth / 2, y + height - 8, x + width + boxWidth / 2, y + height + 4, 0xFF151515);
        g.outline(x + width - boxWidth / 2, y + height - 8, boxWidth, 12, 0xFF5A5A5A);
        g.centeredText(this.font, count, x + width, y + height - 6, 0xFFFFFFFF);
        CardRenderer.fitText(g, this.font, label, x + width + 12, y + height / 2 - 4, RIGHT_COLUMN - width - 24, TEXT_DIM, false);
    }

    // Your hand, fanned along the bottom and overlapping the board. The card under the mouse rises up.
    private void drawHand(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        List<HandCard> hand = me().hand();
        float scale = handScale();
        int cardWidth = Math.round(CardRenderer.WIDTH * scale);
        int cardHeight = Math.round(CardRenderer.HEIGHT * scale);
        int count = hand.size();
        int available = this.boardRight - this.boardLeft - cardWidth;
        int step = count <= 1 ? cardWidth : Math.min(cardWidth - 6, available / (count - 1));
        int total = count == 0 ? 0 : step * (count - 1) + cardWidth;
        int startX = (this.boardLeft + this.boardRight) / 2 - total / 2;
        // Work out which card the mouse is over first: the top-most (last drawn) one wins.
        int hoveredIndex = -1;
        for (int i = 0; i < count; i++) {
            int x = startX + i * step;
            int width = i == count - 1 ? cardWidth : step;
            Spot spot = new Spot(x, this.handTop, width, this.height - this.handTop, this.you, -1, i);
            this.handSpots.add(spot);
            if (spot.contains(mouseX, mouseY) && this.draggingCard == null && this.attacker == null && this.awaitingTarget == null) {
                hoveredIndex = i;
            }
        }
        for (int i = 0; i < count; i++) {
            HandCard card = hand.get(i);
            if (card == this.draggingCard || i == hoveredIndex) {
                continue;
            }
            float offset = i - (count - 1) / 2.0F;
            int x = startX + i * step;
            int y = this.handTop + Math.round(Math.abs(offset) * 3);
            g.pose().pushMatrix();
            g.pose().translate(x + cardWidth / 2.0F, y + cardHeight / 2.0F);
            g.pose().rotate((float) Math.toRadians(offset * 3));
            g.pose().translate(-cardWidth / 2.0F, -cardHeight / 2.0F);
            drawHandCard(g, card, 0, 0, scale, cardWidth, cardHeight);
            g.pose().popMatrix();
        }
        if (hoveredIndex >= 0) {
            HandCard card = hand.get(hoveredIndex);
            int x = startX + hoveredIndex * step;
            drawHandCard(g, card, x, this.height - cardHeight - 2, scale, cardWidth, cardHeight);
        }
    }

    private void drawHandCard(GuiGraphicsExtractor g, HandCard card, int x, int y, float scale, int width, int height) {
        boolean playable = card.playable() && this.myTurn;
        if (playable) {
            g.fill(x - 3, y - 3, x + width + 3, y + height + 3, 0x807DFF5A);
            g.outline(x - 2, y - 2, width + 4, height + 4, GLOW);
        }
        CardRenderer.draw(g, this.font, CardItem.of(card.id(), card.enchanted()), x, y, scale);
        if (!playable) {
            g.fill(x, y, x + width, y + height, 0x50000000);
        }
    }

    // The card being dragged, or the aiming line for an attack.
    private void drawInteraction(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (this.draggingCard != null) {
            float scale = handScale() * 0.8F;
            int width = Math.round(CardRenderer.WIDTH * scale);
            int height = Math.round(CardRenderer.HEIGHT * scale);
            CardRenderer.draw(g, this.font, CardItem.of(this.draggingCard.id(), this.draggingCard.enchanted()),
                    mouseX - width / 2, mouseY - height / 2, scale);
        }
        if (this.attacker != null) {
            drawArrow(g, this.attacker.centerX(), this.attacker.centerY(), mouseX, mouseY);
        }
    }

    // A dashed red aiming line with a square head, like the prototype's attack arrow.
    private static void drawArrow(GuiGraphicsExtractor g, int fromX, int fromY, int toX, int toY) {
        double length = Math.hypot(toX - fromX, toY - fromY);
        for (double d = 0; d < length; d += 7) {
            double end = Math.min(length, d + 4);
            for (double e = d; e < end; e += 1) {
                int px = (int) (fromX + (toX - fromX) * e / length);
                int py = (int) (fromY + (toY - fromY) * e / length);
                g.fill(px - 1, py - 1, px + 2, py + 2, RED);
            }
        }
        g.fill(toX - 4, toY - 4, toX + 5, toY + 5, RED);
    }

    // Hovering a mob shows the full card beside the board; hovering your hand shows it larger above the hand.
    private void drawPreview(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (this.draggingCard != null || this.attacker != null) {
            return;
        }
        for (Spot spot : this.minionSpots) {
            if (!spot.contains(mouseX, mouseY)) {
                continue;
            }
            MinionView minion = findMinion(spot);
            if (minion == null) {
                return;
            }
            float scale = Math.min(1.0F, (this.boardBottom - this.boardTop - 10) / (float) CardRenderer.HEIGHT);
            int width = Math.round(CardRenderer.WIDTH * scale);
            int height = Math.round(CardRenderer.HEIGHT * scale);
            int x = spot.x() + spot.width() + 14 + width < this.boardRight ? spot.x() + spot.width() + 14 : spot.x() - 14 - width;
            int y = Math.max(this.boardTop, Math.min(spot.centerY() - height / 2, this.boardBottom - height));
            CardRenderer.draw(g, this.font, CardItem.of(minion.id(), minion.enchanted()), x, y, scale);
            List<Component> status = new ArrayList<>();
            if (minion.frozen()) status.add(Component.translatable("match_ui.craftbound.status.frozen").withStyle(ChatFormatting.AQUA));
            if (minion.withered()) status.add(Component.translatable("match_ui.craftbound.status.withered").withStyle(ChatFormatting.GRAY));
            if (minion.totem()) status.add(Component.translatable("match_ui.craftbound.status.totem").withStyle(ChatFormatting.GOLD));
            if (minion.invulnerable()) status.add(Component.translatable("match_ui.craftbound.status.invulnerable").withStyle(ChatFormatting.YELLOW));
            if (spot.side() == this.you && !minion.ready() && this.myTurn) {
                status.add(Component.translatable("match_ui.craftbound.status.not_ready").withStyle(ChatFormatting.GRAY));
            }
            if (!status.isEmpty()) {
                Tooltips.show(g, this.font, status, mouseX, mouseY);
            }
            return;
        }
    }

    private void drawResult(GuiGraphicsExtractor g) {
        g.fill(0, 0, this.width, this.height, 0xA0000000);
        String key = this.winner < 0 ? "match_ui.craftbound.draw" : this.winner == this.you ? "match_ui.craftbound.victory" : "match_ui.craftbound.defeat";
        g.pose().pushMatrix();
        g.pose().translate(this.width / 2.0F, this.height / 2.0F - 20);
        g.pose().scale(3.0F, 3.0F);
        g.centeredText(this.font, Component.translatable(key), 0, -4, this.winner == this.you ? GOLD_LIGHT : TEXT);
        g.pose().popMatrix();
        g.centeredText(this.font, Component.translatable("match_ui.craftbound.click_to_leave"), this.width / 2, this.height / 2 + 14, TEXT);
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

    // Whether this mob is a place you could aim what you're doing right now.
    private boolean isValidTarget(Spot spot) {
        if (this.attacker != null) {
            MinionView minion = findMinion(spot);
            return spot.side() != this.you && minion != null && (!enemyHasShield() || minion.shield());
        }
        HandCard aiming = this.awaitingTarget != null ? this.awaitingTarget : this.draggingCard;
        return aiming != null && isValidTargetFor(aiming, spot);
    }

    private boolean isValidTargetFor(HandCard card, Spot spot) {
        return switch (card.target()) {
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
        // Pick up a card from your hand (the top-most card under the mouse wins).
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
            boolean overBoard = mouseY < this.handTop - 4 && mouseX >= this.boardLeft && mouseX < this.boardRight;
            boolean isMob = Cards.get(card.id()).isMob();
            if (target != null) {
                send(MatchActionPayload.PLAY, card.index(), target);
            } else if (overBoard && card.target().equals("NONE")) {
                send(MatchActionPayload.PLAY, card.index(), null);
            } else if (overBoard && isMob) {
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
