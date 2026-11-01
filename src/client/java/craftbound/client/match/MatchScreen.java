package craftbound.client.match;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import craftbound.card.CardClass;
import craftbound.card.CardDefinition;
import craftbound.card.Cards;
import craftbound.card.Keyword;
import craftbound.client.Tooltips;
import craftbound.client.card.CardRenderer;
import craftbound.client.match.MatchState.Event;
import craftbound.client.match.MatchState.HandCard;
import craftbound.client.match.MatchState.LogLine;
import craftbound.client.match.MatchState.Mob;
import craftbound.client.match.MatchState.Side;
import craftbound.game.HeroPowers;
import craftbound.game.Match;
import craftbound.game.MatchEvent;
import craftbound.item.CardItem;
import craftbound.network.MatchActionPayload;
import craftbound.sleeve.Sleeves;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// A match, laid out like Hearthstone:
// - Each hero sits on the edge of the table: the opponent's at the top, yours just above your hand. The tool the
//   hero holds is on its left and the class power on its right.
// - Mobs stand in two rows across the middle. Your hand fans along the bottom; theirs hangs face down at the top.
// - XP orbs fill up like mana (bottom right for you, top right for them), END TURN and both decks are on the
//   right, and the battle log is a strip of icons on the left: hover one to see what happened.
// Drag a card onto the board to play it (items onto their target); drag a ready mob (green glow), or your hero
// when it holds a tool, onto an enemy to attack. Click the power to use it. Esc opens the menu.
// The server sends what happened as a list of events; the screen plays them one by one with animations and
// sounds, changing what it shows step by step, then lines up with the server's state.
public class MatchScreen extends Screen {
    private static final int MOB_GAP = 6;
    private static final int COIN_TIME = 2600;
    private static final int COIN_LAND = 1600;
    private static final int MY_BANNER = 1300;
    private static final int THEIR_BANNER = 900;
    private static final int GAME_OVER_DELAY = 1100;

    // Colours of the table, from the prototype.
    private static final int MORTAR = 0xFF121315;
    private static final int BRICK_A = 0xFF2A2B2F;
    private static final int BRICK_B = 0xFF24252A;
    private static final int PLANK_A = 0xFF8E5F31;
    private static final int PLANK_B = 0xFF7F552B;
    private static final int PLANK_SEAM = 0xFF4B2F16;
    private static final int PANEL = 0xFF25211C;
    private static final int PANEL_DARK = 0xFF1B1814;
    private static final int PANEL_EDGE = 0xFF4F3D27;

    private static long nextUid = 1;
    private final Random random = new Random();

    // --- What's shown, and the updates still to animate ------------------------------------------------------
    private MatchState shown;

    private record Update(MatchState state, List<Event> events) {
    }

    private final ArrayDeque<Update> updates = new ArrayDeque<>();
    private Update playing;
    private int nextEvent;
    private long beatEnds;
    private long now;
    private long lastFrame;
    private float dt;
    // After you make a move, the screen waits for the server's answer before letting you make another.
    private boolean awaitingReply;
    private long sentAt;
    private long hiddenUid = -1;

    // --- Animation --------------------------------------------------------------------------------------------
    private interface Drawer {
        void draw(GuiGraphicsExtractor g, float t);
    }

    private record Effect(long start, int duration, boolean top, Drawer drawer) {
    }

    private record Scheduled(long at, Runnable action) {
    }

    private static final class Particle {
        float x, y, vx, vy, size, life, age, gravity;
        int color;
    }

    // Where a card in a hand is drawn right now; it eases towards its place in the fan each frame.
    private static final class Anim {
        float x, y, scale = 1.0F, rotation;
        boolean placed;
        // A drawn card first stops here, big, so you can see what you got.
        long holdUntil;
        float holdX, holdY, holdScale = 1.0F;
        // When a face-down card flips face up (-1: it's already face up).
        long flipAt = -1;
    }

    private record Lunge(int side, int mid, float dx, float dy, long start, int duration) {
    }

    private final List<Effect> effects = new ArrayList<>();
    private final List<Scheduled> scheduled = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final Map<Long, Anim> anims = new HashMap<>();
    private final Map<Integer, Float> mobX = new HashMap<>();
    private final Map<Integer, Long> arrived = new HashMap<>();
    private final Map<String, Long> shakes = new HashMap<>();
    private final Map<String, Long> flashes = new HashMap<>();
    private final long[] xpRefilled = {-1, -1};
    private Lunge lunge;
    private long screenShakeStart = -1;
    private float screenShakeStrength;
    private long coinStart = -1;
    private int coinFirst;
    private long bannerStart = -1;
    private boolean bannerMine;
    private long gameOverStart = -1;
    private long endTurnPressed = -1;
    private Identifier lastPlayed;
    private float[] lastBounce;
    private long lastBounceAt = -1;
    private float lastDropX, lastDropY;

    // --- Mulligan ---------------------------------------------------------------------------------------------
    private final boolean[] swapPicks = new boolean[Match.MAX_HAND];
    private boolean mulliganSent;
    private int swappedMask;
    private long swappedAt = -1;
    private final List<int[]> mulliganSpots = new ArrayList<>();
    private int[] confirmButton;

    // --- Where things are on screen (worked out each frame) ---------------------------------------------------
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

    private final List<Spot> mobSpots = new ArrayList<>();
    private final List<Spot> handSpots = new ArrayList<>();
    private final Spot[] heroSpots = new Spot[2];
    private final int[][] toolSpots = new int[2][];
    private final int[][] powerSpots = new int[2][];
    private final int[][] xpSpots = new int[2][];
    private final int[][] deckSpots = new int[2][];
    private final int[][] nameplateSpots = new int[2][];
    private record HistorySpot(int x, int y, int size, LogLine line) {
    }

    private final List<HistorySpot> historySpots = new ArrayList<>();
    private int[] endTurnButton;
    private int[] menuButton;
    private final List<int[]> menuButtons = new ArrayList<>();

    private float handScale, backScale, deckScale;
    private int handW, handH, handTop, heroSize, toolSize, centerX, oppHeroY, myHeroY;
    private int boardLeft, boardRight, boardTop, boardBottom, middle, mobW, mobH, theirRowY, myRowY;
    private int historyIcon, deckW, deckH, deckX, theirDeckY, myDeckY, orbSize;

    // --- What you're doing with the mouse ---------------------------------------------------------------------
    private HandCard dragging;
    private Spot attacker;
    // A mob that picks a target (the Enderman) waits here for you to click one.
    private HandCard awaitingTarget;
    private Spot pendingSpot;
    private int hoveredHand = -1;
    private boolean menuOpen;
    private boolean confirmConcede;

    public MatchScreen(String json) {
        super(Component.translatable("match_ui.craftbound.title"));
        this.now = millis();
        List<Event> events = new ArrayList<>();
        MatchState state = MatchState.parse(JsonParser.parseString(json).getAsJsonObject(), events);
        // Start from the server's state; the first update's events (the coin toss) only add animations.
        this.shown = state.copy();
        for (HandCard card : this.shown.me().hand) {
            card.uid = nextUid++;
        }
        for (int i = 0; i < this.shown.them().handCount; i++) {
            this.shown.them().backs.add(nextUid++);
        }
        this.mulliganSent = this.shown.me().mulliganDone;
        this.updates.add(new Update(state, events));
    }

    public void update(String json) {
        List<Event> events = new ArrayList<>();
        MatchState state = MatchState.parse(JsonParser.parseString(json).getAsJsonObject(), events);
        this.updates.add(new Update(state, events));
        this.awaitingReply = false;
        this.hiddenUid = -1;
    }

    private static long millis() {
        return System.nanoTime() / 1_000_000L;
    }

    // --- Playing the server's events --------------------------------------------------------------------------

    private void advance() {
        int guard = 0;
        while (this.now >= this.beatEnds && guard++ < 500) {
            if (this.playing == null) {
                if (this.updates.isEmpty()) {
                    return;
                }
                this.playing = this.updates.poll();
                this.nextEvent = 0;
            }
            if (this.nextEvent < this.playing.events().size()) {
                // When moves pile up (the Card Master is quick), play them faster to catch up.
                float speed = this.updates.size() >= 2 ? 0.5F : 1.0F;
                this.beatEnds = this.now + Math.round(playNext() * speed);
            } else {
                lineUp(this.playing.state());
                this.playing = null;
            }
        }
    }

    // Plays the next event. Hits that land at the same moment (a Sonic Boom, a Creeper blast) play together.
    private int playNext() {
        List<Event> events = this.playing.events();
        Event first = events.get(this.nextEvent++);
        int longest = play(first, 0);
        int group = group(first.type());
        int order = 1;
        while (group > 0 && this.nextEvent < events.size() && group(events.get(this.nextEvent).type()) == group) {
            longest = Math.max(longest, play(events.get(this.nextEvent++), order++));
        }
        return longest;
    }

    private static int group(String type) {
        return switch (type) {
            case "DAMAGE", "HEAL", "BUFF", "STATUS" -> 1;
            case "DEATH", "REMOVE" -> 2;
            default -> 0;
        };
    }

    // Copies the server's state, keeping each hand card's animation where the card is still the same.
    private void lineUp(MatchState next) {
        MatchState state = next.copy();
        for (int s = 0; s < 2; s++) {
            Side old = this.shown.sides[s];
            Side fresh = state.sides[s];
            List<HandCard> unused = new ArrayList<>(old.hand);
            for (int i = 0; i < fresh.hand.size(); i++) {
                HandCard card = fresh.hand.get(i);
                HandCard same = null;
                if (i < old.hand.size() && unused.contains(old.hand.get(i)) && sameCard(old.hand.get(i), card)) {
                    same = old.hand.get(i);
                }
                for (int j = 0; same == null && j < unused.size(); j++) {
                    if (sameCard(unused.get(j), card)) {
                        same = unused.get(j);
                    }
                }
                if (same != null) {
                    card.uid = same.uid;
                    unused.remove(same);
                } else {
                    card.uid = nextUid++;
                }
            }
            for (int i = 0; i < fresh.handCount; i++) {
                fresh.backs.add(i < old.backs.size() ? old.backs.get(i) : nextUid++);
            }
        }
        boolean wasOver = this.shown.over;
        this.shown = state;
        if (!this.shown.myTurn) {
            this.dragging = null;
            this.attacker = null;
            this.awaitingTarget = null;
        }
        if (state.over && !wasOver) {
            gameOver();
        }
    }

    private static boolean sameCard(HandCard a, HandCard b) {
        return a.id.equals(b.id) && a.enchanted == b.enchanted;
    }

    // Plays one event: changes what's shown, starts its animation and sound, and says how long until the next.
    private int play(Event e, int order) {
        boolean sound = order < 3;
        int s = Math.max(0, Math.min(1, e.side()));
        boolean mine = s == this.shown.you;
        Side side = this.shown.sides[s];
        switch (e.type()) {
            case "COIN" -> {
                this.coinStart = this.now;
                this.coinFirst = s;
                MatchSounds.play("entity.item.pickup", 1.3F, 0.6F);
                schedule(COIN_LAND, () -> {
                    MatchSounds.play("block.note_block.bell", this.coinFirst == this.shown.you ? 1.2F : 0.8F, 0.8F);
                    MatchSounds.play("block.amethyst_block.chime", 1.0F, 0.6F);
                });
                return COIN_TIME;
            }
            case "MULLIGAN" -> {
                side.mulliganDone = true;
                if (mine) {
                    // Show the new cards now so the swapped ones can flip over to them.
                    Side fresh = this.playing.state().sides[s];
                    int count = side.hand.size();
                    side.hand.clear();
                    for (int i = 0; i < count && i < fresh.hand.size(); i++) {
                        HandCard card = fresh.hand.get(i).copy();
                        card.uid = nextUid++;
                        side.hand.add(card);
                    }
                    this.swappedMask = e.amount();
                    this.swappedAt = this.now;
                    return e.amount() == 0 ? 200 : 700;
                }
                MatchSounds.play("item.book.page_turn", 1.2F, 0.4F);
                return 200;
            }
            case "BEGIN" -> {
                this.shown.playing = true;
                // The starting hand flies down from the mulligan row into the fan.
                List<HandCard> hand = this.shown.me().hand;
                for (int i = 0; i < hand.size(); i++) {
                    float[] slot = mulliganSlot(i, hand.size());
                    Anim anim = new Anim();
                    anim.placed = true;
                    anim.x = slot[0];
                    anim.y = slot[1];
                    anim.scale = slot[2] / this.handScale;
                    this.anims.put(hand.get(i).uid, anim);
                }
                for (long uid : this.shown.them().backs) {
                    Anim anim = new Anim();
                    anim.placed = true;
                    anim.x = this.centerX;
                    anim.y = this.height * 0.3F;
                    this.anims.put(uid, anim);
                }
                MatchSounds.play("item.book.put", 1.0F, 0.7F);
                return 700;
            }
            case "TURN" -> {
                this.shown.current = s;
                this.shown.myTurn = mine;
                this.shown.turn++;
                side.xp = e.amount();
                side.maxXp = e.extra();
                side.powerUsed = false;
                this.xpRefilled[s] = this.now;
                this.bannerStart = this.now;
                this.bannerMine = mine;
                if (mine) {
                    MatchSounds.play("block.bell.use", 1.0F, 0.6F);
                } else {
                    MatchSounds.play("block.note_block.chime", 0.7F, 0.4F);
                }
                return mine ? 900 : 600;
            }
            case "DRAW", "GIVE" -> {
                boolean draw = e.type().equals("DRAW");
                if (draw) {
                    side.deck = Math.max(0, side.deck - 1);
                }
                float[] from = draw ? deckCenter(s) : giveOrigin();
                if (mine && e.card() == null) {
                    return 0;
                }
                if (mine) {
                    HandCard card = new HandCard();
                    card.uid = nextUid++;
                    card.id = e.card();
                    card.enchanted = e.enchanted();
                    side.hand.add(card);
                    Anim anim = new Anim();
                    anim.placed = true;
                    anim.scale = draw ? this.deckScale / this.handScale : 0.6F;
                    anim.x = from[0] - this.handW * anim.scale / 2.0F;
                    anim.y = from[1] - this.handH * anim.scale / 2.0F;
                    // It pops out big on the right of the board for a moment, then joins your hand.
                    anim.holdUntil = this.now + (draw ? 560 : 420);
                    anim.holdScale = 1.55F;
                    anim.holdX = this.boardRight - this.handW * 1.55F - 12;
                    anim.holdY = this.middle - this.handH * 1.55F / 2.0F;
                    anim.flipAt = draw ? this.now + 170 : -1;
                    this.anims.put(card.uid, anim);
                    if (sound) {
                        MatchSounds.play(draw ? "item.book.page_turn" : "entity.item.pickup", 1.0F, 0.7F);
                    }
                    return draw ? 720 : 520;
                }
                long uid = nextUid++;
                side.backs.add(uid);
                side.handCount++;
                Anim anim = new Anim();
                anim.placed = true;
                anim.scale = (draw ? this.deckScale : this.handScale * 0.6F) / this.backScale;
                anim.x = from[0] - CardRenderer.WIDTH * this.backScale * anim.scale / 2.0F;
                anim.y = from[1] - CardRenderer.HEIGHT * this.backScale * anim.scale / 2.0F;
                this.anims.put(uid, anim);
                if (sound) {
                    MatchSounds.play(draw ? "item.book.page_turn" : "entity.item.pickup", 1.15F, 0.45F);
                }
                return 380;
            }
            case "BURN" -> {
                side.deck = Math.max(0, side.deck - 1);
                Identifier id = e.card();
                boolean enchanted = e.enchanted();
                float[] from = deckCenter(s);
                effect(0, 1150, true, (g, t) -> {
                    float fly = MatchArt.easeOut(t / 0.3F);
                    float scale = this.handScale * (0.5F + 0.7F * fly) * (t > 0.8F ? 1.0F - (t - 0.8F) / 0.2F : 1.0F);
                    float x = lerp(from[0], this.centerX, fly) - CardRenderer.WIDTH * scale / 2.0F;
                    float y = lerp(from[1], this.middle, fly) - CardRenderer.HEIGHT * scale / 2.0F;
                    if (id != null) {
                        CardRenderer.draw(g, this.font, CardItem.of(id, enchanted), Math.round(x), Math.round(y), scale);
                    }
                    if (t > 0.35F) {
                        // It burns from the bottom up.
                        float burn = MatchArt.clamp((t - 0.35F) / 0.45F);
                        int w = Math.round(CardRenderer.WIDTH * scale);
                        int h = Math.round(CardRenderer.HEIGHT * scale);
                        int top = Math.round(y + h * (1.0F - burn));
                        g.fill(Math.round(x), top, Math.round(x) + w, Math.round(y) + h, 0xD0301008);
                        g.fill(Math.round(x), top - 2, Math.round(x) + w, top, 0xFFFF8A20);
                    }
                    MatchArt.outlined(g, this.font, Component.translatable("match_ui.craftbound.burned"), this.centerX,
                            y - 12, 0xFFFF8A60, 1.0F, true);
                });
                schedule(420, () -> {
                    MatchSounds.play("block.fire.extinguish", 1.0F, 0.6F);
                    particles(this.centerX, this.middle, 30, 0xFFFF8A20, 70, -40, 2);
                });
                return 1150;
            }
            case "HUNGER" -> {
                float[] at = heroCenter(s);
                floatText(Component.translatable("match_ui.craftbound.hunger").getString(), at[0], at[1] - this.heroSize * 0.7F, 0xFFD6A73A, 1.2F);
                return 450;
            }
            case "PLAY" -> {
                CardDefinition card = e.card() == null ? null : Cards.get(e.card());
                side.xp = Math.max(0, side.xp - (card == null ? 0 : card.cost()));
                this.lastPlayed = e.card();
                if (mine) {
                    int index = e.amount();
                    if (index >= 0 && index < side.hand.size()) {
                        side.hand.remove(index);
                    }
                    if (card != null && !card.isMob()) {
                        // The card shrinks into whatever it was used on.
                        float[] from = {this.lastDropX, this.lastDropY};
                        float[] to = e.aimed() ? targetCenter(e) : from;
                        Identifier id = e.card();
                        boolean enchanted = e.enchanted();
                        effect(0, 340, false, (g, t) -> {
                            float scale = this.handScale * 0.85F * (1.0F - MatchArt.easeIn(t) * 0.9F);
                            float x = lerp(from[0], to[0], MatchArt.easeOut(t)) - CardRenderer.WIDTH * scale / 2.0F;
                            float y = lerp(from[1], to[1], MatchArt.easeOut(t)) - CardRenderer.HEIGHT * scale / 2.0F;
                            CardRenderer.draw(g, this.font, CardItem.of(id, enchanted), Math.round(x), Math.round(y), scale);
                        });
                        if (!card.isTool()) {
                            MatchSounds.item(e.card());
                            schedule(300, () -> particles(to[0], to[1], 14, 0xFFFFF0A0, 60, 0, 2));
                        }
                        return 320;
                    }
                    return 40;
                }
                // Their card is shown big for a moment, so you can read what they played.
                if (!side.backs.isEmpty()) {
                    side.backs.remove(Math.max(0, Math.min(side.backs.size() - 1, e.amount())));
                }
                side.handCount = Math.max(0, side.handCount - 1);
                reveal(e.card(), e.enchanted(), s, e.aimed() ? targetCenter(e) : null);
                if (card != null && !card.isMob() && !card.isTool()) {
                    Identifier id = e.card();
                    schedule(750, () -> MatchSounds.item(id));
                }
                MatchSounds.play("item.book.page_turn", 0.8F, 0.5F);
                return 1050;
            }
            case "POWER" -> {
                side.xp = Math.max(0, side.xp - this.shown.powerCost);
                side.powerUsed = true;
                this.flashes.put("power:" + s, this.now);
                float[] at = powerCenter(s);
                particles(at[0], at[1], 16, MatchArt.GOLD_LIGHT, 60, 0, 2);
                MatchSounds.play("block.amethyst_block.chime", 1.2F, 0.8F);
                return mine ? 300 : 650;
            }
            case "SUMMON" -> {
                Mob mob = new Mob();
                mob.mid = e.mid();
                mob.id = e.card();
                mob.enchanted = e.enchanted();
                mob.attack = e.amount();
                mob.health = e.extra();
                mob.maxHealth = e.extra();
                CardDefinition card = Cards.get(e.card());
                if (card != null) {
                    mob.shield = card.keywords().contains(Keyword.SHIELD);
                    mob.block = card.keywords().contains(Keyword.BLOCK);
                }
                side.board.add(mob);
                this.arrived.put(mob.mid, this.now);
                // It lands in its spot; the others slide over to make room.
                this.mobX.put(mob.mid, slotX(side.board.size() - 1, side.board.size() + (mine && this.awaitingTarget != null ? 1 : 0)));
                if (sound && e.card() != null) {
                    MatchSounds.summon(e.card());
                }
                int cost = card == null ? 0 : card.cost();
                int mid = mob.mid;
                schedule(160, () -> {
                    float[] at = mobCenter(s, mid);
                    particles(at[0], at[1] + this.mobH / 2.0F, 14, 0xFFB09070, 50, 60, 2);
                    if (cost >= 7) {
                        screenShake(5, 420);
                        MatchSounds.play("block.anvil.land", 0.6F, 0.35F);
                    }
                });
                return 440;
            }
            case "EQUIP" -> {
                MatchState.Tool tool = new MatchState.Tool();
                tool.id = e.card();
                tool.enchanted = e.enchanted();
                tool.attack = e.amount();
                tool.uses = e.extra();
                CardDefinition card = Cards.get(e.card());
                tool.ranged = card != null && card.keywords().contains(Keyword.RANGED);
                side.tool = tool;
                this.flashes.put("tool:" + s, this.now);
                if (e.card() != null) {
                    MatchSounds.equip(e.card());
                }
                return 420;
            }
            case "BREAK" -> {
                float[] at = toolCenter(s);
                particles(at[0], at[1], 18, 0xFF9AA1A8, 80, 120, 2);
                side.tool = null;
                MatchSounds.play("entity.item.break", 1.0F, 0.8F);
                return 360;
            }
            case "ATTACK" -> {
                float[] from = actorCenter(s, e.mid());
                float[] to = targetCenter(e);
                Mob mob = e.mid() == Match.HERO ? null : side.mob(e.mid());
                boolean ranged = e.mid() == Match.HERO ? side.tool != null && side.tool.ranged
                        : mob != null && mob.card() != null && mob.card().keywords().contains(Keyword.RANGED);
                if (ranged) {
                    boolean shulker = mob != null && mob.id.equals(Cards.SHULKER);
                    projectile(new ItemStack(shulker ? Items.SHULKER_SHELL : Items.ARROW), from, to, 230);
                    MatchSounds.play(shulker ? "entity.shulker.shoot" : "entity.arrow.shoot", 1.0F, 0.7F);
                    return 230;
                }
                this.lunge = new Lunge(s, e.mid(), to[0] - from[0], to[1] - from[1], this.now, 440);
                MatchSounds.play("entity.player.attack.sweep", 1.3F, 0.3F);
                schedule(175, () -> MatchSounds.play("entity.player.attack.strong", 1.0F, 0.7F));
                return 175;
            }
            case "SHOOT" -> {
                projectile(new ItemStack(Items.ARROW), actorCenter(s, e.mid()), targetCenter(e), 250);
                MatchSounds.play("block.dispenser.launch", 1.0F, 0.6F);
                return 250;
            }
            case "DAMAGE" -> {
                boolean hero = e.mid() == Match.HERO;
                float[] at = actorCenter(s, e.mid());
                String key = hero ? "h:" + s : "m:" + e.mid();
                if (e.amount() == 0) {
                    floatText(Component.translatable("match_ui.craftbound.blocked").getString(), at[0], at[1] - 8, MatchArt.GOLD_LIGHT, 1.0F);
                    if (sound) {
                        MatchSounds.play("item.shield.block", 1.0F, 0.6F);
                    }
                    return 360;
                }
                if (hero) {
                    side.health -= e.amount();
                    this.flashes.put(key, this.now);
                    if (sound) {
                        MatchSounds.play(side.ai ? "entity.wandering_trader.hurt" : "entity.player.hurt", 1.0F, 0.7F);
                    }
                } else {
                    Mob mob = side.mob(e.mid());
                    if (mob != null) {
                        mob.health -= e.amount();
                        if (sound) {
                            MatchSounds.mob(mob.id, "hurt");
                        }
                    }
                }
                this.shakes.put(key, this.now);
                splat(at[0], at[1], "-" + e.amount(), 0xFFC81E1E);
                particles(at[0], at[1], 8, 0xFFC81E1E, 70, 80, 2);
                return 430;
            }
            case "HEAL" -> {
                side.health += e.amount();
                float[] at = heroCenter(s);
                splat(at[0], at[1], "+" + e.amount(), 0xFF2FA83A);
                particles(at[0], at[1], 12, 0xFF7DFF5A, 50, -30, 2);
                if (sound) {
                    MatchSounds.play("block.amethyst_block.chime", 1.4F, 0.6F);
                }
                return 400;
            }
            case "BUFF" -> {
                Mob mob = side.mob(e.mid());
                if (mob != null) {
                    mob.attack += e.amount();
                    mob.health += e.extra();
                    mob.maxHealth += e.extra();
                    float[] at = mobCenter(s, e.mid());
                    floatText("+" + e.amount() + "/+" + e.extra(), at[0], at[1] - 6, 0xFF7DFF5A, 1.2F);
                    particles(at[0], at[1], 12, 0xFF7DFF5A, 50, -40, 2);
                    if (sound) {
                        MatchSounds.play("entity.experience_orb.pickup", 1.3F, 0.5F);
                    }
                }
                return 400;
            }
            case "STATUS" -> {
                Mob mob = side.mob(e.mid());
                if (mob == null) {
                    return 0;
                }
                float[] at = mobCenter(s, e.mid());
                switch (e.amount()) {
                    case MatchEvent.STATUS_FROZEN -> {
                        mob.frozen = true;
                        particles(at[0], at[1], 16, 0xFFBDEBFF, 60, 20, 2);
                        MatchSounds.play("block.powder_snow.place", 1.0F, 0.8F);
                    }
                    case MatchEvent.STATUS_WITHERED -> {
                        mob.withered = true;
                        particles(at[0], at[1], 16, 0xFF202020, 50, -20, 2);
                        MatchSounds.play("entity.wither.ambient", 1.4F, 0.25F);
                    }
                    case MatchEvent.STATUS_TOTEM -> {
                        mob.totem = true;
                        particles(at[0], at[1], 18, 0xFFFFD34A, 70, 0, 2);
                        MatchSounds.play("block.beacon.power_select", 1.2F, 0.5F);
                    }
                    case MatchEvent.STATUS_INVULNERABLE -> {
                        mob.invulnerable = true;
                        particles(at[0], at[1], 18, 0xFFFFF27A, 70, 0, 2);
                        MatchSounds.play("entity.player.levelup", 1.6F, 0.4F);
                    }
                    default -> {
                    }
                }
                return 320;
            }
            case "XP" -> {
                side.xp = e.amount();
                this.xpRefilled[s] = this.now;
                MatchSounds.play("entity.experience_orb.pickup", 1.0F, 0.6F);
                return 320;
            }
            case "DEATH", "REMOVE", "BOUNCE" -> {
                Mob mob = side.mob(e.mid());
                if (mob == null) {
                    return 0;
                }
                float[] at = mobCenter(s, e.mid());
                float x = this.mobX.getOrDefault(mob.mid, at[0] - this.mobW / 2.0F);
                float y = rowY(s);
                Mob ghost = mob.copy();
                side.board.remove(mob);
                this.mobX.remove(mob.mid);
                String type = e.type();
                ghost(ghost, s, x, y, type);
                if (type.equals("DEATH")) {
                    particles(at[0], at[1], 22, 0xFF8A8A8A, 80, 90, 2);
                    if (sound) {
                        MatchSounds.mob(ghost.id, "death");
                    }
                    if (ghost.id.equals(Cards.CREEPER) || ghost.id.equals(Cards.CHARGED_CREEPER)) {
                        schedule(120, () -> {
                            MatchSounds.play("entity.generic.explode", 1.0F, 0.8F);
                            screenShake(7, 500);
                            particles(at[0], at[1], 45, 0xFFFFB040, 140, 40, 3);
                            particles(at[0], at[1], 25, 0xFF606060, 100, -20, 3);
                        });
                    }
                    return 460;
                }
                if (type.equals("BOUNCE")) {
                    this.lastBounce = at;
                    this.lastBounceAt = this.now;
                    if (Cards.ENDERMAN.equals(this.lastPlayed)) {
                        MatchSounds.play("entity.enderman.teleport", 1.0F, 0.7F);
                    }
                    particles(at[0], at[1], 18, 0xFFB45AFF, 60, -10, 2);
                    return 420;
                }
                particles(at[0], at[1], 12, 0xFFD6C25A, 50, 60, 2);
                if (sound) {
                    MatchSounds.play("block.crop.break", 1.0F, 0.7F);
                }
                return 300;
            }
            default -> {
                return 0;
            }
        }
    }

    // --- Effects ----------------------------------------------------------------------------------------------

    private void effect(int delay, int duration, boolean top, Drawer drawer) {
        this.effects.add(new Effect(this.now + delay, duration, top, drawer));
    }

    private void schedule(int delay, Runnable action) {
        this.scheduled.add(new Scheduled(this.now + delay, action));
    }

    private void runScheduled() {
        List<Scheduled> due = new ArrayList<>();
        for (Scheduled entry : this.scheduled) {
            if (this.now >= entry.at()) {
                due.add(entry);
            }
        }
        this.scheduled.removeAll(due);
        due.forEach(entry -> entry.action().run());
    }

    private void drawEffects(GuiGraphicsExtractor g, boolean top) {
        for (Effect effect : new ArrayList<>(this.effects)) {
            if (effect.top() != top || this.now < effect.start()) {
                continue;
            }
            float t = (this.now - effect.start()) / (float) effect.duration();
            if (t >= 1.0F) {
                this.effects.remove(effect);
                continue;
            }
            effect.drawer().draw(g, t);
        }
    }

    private void particles(float x, float y, int count, int color, float speed, float gravity, int size) {
        for (int i = 0; i < count; i++) {
            Particle p = new Particle();
            double angle = this.random.nextDouble() * Math.PI * 2;
            float velocity = speed * (0.3F + this.random.nextFloat() * 0.7F);
            p.x = x;
            p.y = y;
            p.vx = (float) Math.cos(angle) * velocity;
            p.vy = (float) Math.sin(angle) * velocity - speed * 0.3F;
            p.size = size + (this.random.nextFloat() < 0.3F ? 1 : 0);
            p.life = 0.45F + this.random.nextFloat() * 0.4F;
            p.gravity = gravity * 3;
            p.color = color;
            this.particles.add(p);
        }
    }

    private void drawParticles(GuiGraphicsExtractor g) {
        this.particles.removeIf(p -> p.age >= p.life);
        for (Particle p : this.particles) {
            p.age += this.dt;
            p.vy += p.gravity * this.dt;
            p.x += p.vx * this.dt;
            p.y += p.vy * this.dt;
            float alpha = 1.0F - p.age / p.life;
            int x = Math.round(p.x);
            int y = Math.round(p.y);
            g.fill(x, y, x + Math.round(p.size), y + Math.round(p.size), MatchArt.alpha(p.color, alpha));
        }
    }

    // A hit splat: a burst with the number on it.
    private void splat(float x, float y, String text, int color) {
        float radius = Math.max(8, this.mobH * 0.26F);
        effect(0, 1000, false, (g, t) -> {
            float grow = MatchArt.pop(t * 5);
            float fade = t > 0.7F ? 1.0F - (t - 0.7F) / 0.3F : 1.0F;
            MatchArt.burst(g, x, y, radius * grow, MatchArt.alpha(color, fade), 0.3F);
            MatchArt.outlined(g, this.font, text, x, y - 5.5F, MatchArt.alpha(0xFFFFFFFF, fade), 1.5F, true);
        });
    }

    private void floatText(String text, float x, float y, int color, float scale) {
        effect(0, 1000, false, (g, t) -> {
            float fade = t > 0.6F ? 1.0F - (t - 0.6F) / 0.4F : 1.0F;
            MatchArt.outlined(g, this.font, text, x, y - 16 * MatchArt.easeOut(t), MatchArt.alpha(color, fade), scale, true);
        });
    }

    // Something flying from one place to another, like an arrow.
    private void projectile(ItemStack item, float[] from, float[] to, int duration) {
        float angle = (float) Math.atan2(to[1] - from[1], to[0] - from[0]);
        effect(0, duration, false, (g, t) -> {
            float x = lerp(from[0], to[0], t);
            float y = lerp(from[1], to[1], t);
            g.pose().pushMatrix();
            g.pose().translate(x, y);
            // Minecraft's arrow icon points up and to the right.
            g.pose().rotate(angle + (float) (Math.PI / 4));
            g.pose().scale(1.3F, 1.3F);
            g.item(item, -8, -8);
            g.pose().popMatrix();
        });
    }

    // A mob leaving the board: it crumbles (death), pops (harvest) or flies off to its owner's hand (bounce).
    private void ghost(Mob mob, int side, float x, float y, String how) {
        boolean toHand = how.equals("BOUNCE");
        float handY = side == this.shown.you ? this.height : -this.mobH;
        effect(0, 480, false, (g, t) -> {
            float scale;
            float dy = 0;
            if (toHand) {
                scale = 1.0F - 0.5F * t;
                dy = (handY - y) * MatchArt.easeIn(t);
            } else if (how.equals("REMOVE")) {
                scale = 1.0F + 0.2F * t;
                dy = -12 * t;
            } else {
                scale = 1.0F - 0.8F * MatchArt.easeIn(t);
            }
            drawMob(g, mob, side, x, y + dy, scale, false, -1);
            int w = Math.round(this.mobW * scale);
            int h = Math.round(this.mobH * scale);
            int left = Math.round(x + (this.mobW - w) / 2.0F);
            int top = Math.round(y + dy + (this.mobH - h) / 2.0F);
            int fade = how.equals("DEATH") ? 0x9A1A1A1A : 0x70FFFFFF;
            g.fill(left, top, left + w, top + h, MatchArt.alpha(fade, MatchArt.clamp(t * 2)));
        });
    }

    // The other player's card, shown big beside the board for a moment.
    private void reveal(Identifier id, boolean enchanted, int side, float[] target) {
        if (id == null) {
            return;
        }
        String who = this.shown.sides[side].name;
        float scale = Math.min(0.95F, (this.boardBottom - this.boardTop + 30) / (float) CardRenderer.HEIGHT);
        int w = Math.round(CardRenderer.WIDTH * scale);
        int h = Math.round(CardRenderer.HEIGHT * scale);
        int x = this.boardLeft + 8;
        int y = this.middle - h / 2;
        effect(0, 1150, true, (g, t) -> {
            float size = t < 0.12F ? MatchArt.pop(t / 0.12F) : t > 0.88F ? 1.0F - MatchArt.easeIn((t - 0.88F) / 0.12F) : 1.0F;
            g.pose().pushMatrix();
            g.pose().translate(x + w / 2.0F, y + h / 2.0F);
            g.pose().scale(size, size);
            g.pose().translate(-w / 2.0F, -h / 2.0F);
            g.fill(-3, -3, w + 3, h + 3, 0x90000000);
            CardRenderer.draw(g, this.font, CardItem.of(id, enchanted), 0, 0, scale);
            g.pose().popMatrix();
            if (size > 0.9F) {
                MatchArt.outlined(g, this.font, Component.translatable("match_ui.craftbound.plays", who.toUpperCase(Locale.ROOT)),
                        x + w / 2.0F, y - 12, MatchArt.GOLD_LIGHT, 1.0F, true);
            }
            if (target != null && t > 0.45F && t < 0.88F) {
                MatchArt.arrow(g, x + w, y + h / 2, Math.round(target[0]), Math.round(target[1]), MatchArt.RED, this.now);
            }
        });
    }

    private void screenShake(float strength, int duration) {
        this.screenShakeStart = this.now;
        this.screenShakeStrength = strength;
        this.flashes.put("screen", this.now + duration);
    }

    private float[] screenShakeOffset() {
        Long end = this.flashes.get("screen");
        if (this.screenShakeStart < 0 || end == null || this.now >= end) {
            return new float[]{0, 0};
        }
        float t = (this.now - this.screenShakeStart) / (float) Math.max(1, end - this.screenShakeStart);
        float amount = this.screenShakeStrength * (1.0F - t);
        return new float[]{(float) Math.sin(this.now * 0.09) * amount, (float) Math.cos(this.now * 0.11) * amount};
    }

    private float[] shake(String key) {
        Long start = this.shakes.get(key);
        if (start == null || this.now - start > 320) {
            return new float[]{0, 0};
        }
        float t = (this.now - start) / 320.0F;
        float amount = 3.0F * (1.0F - t);
        return new float[]{(float) Math.sin((this.now - start) * 0.12) * amount, 0};
    }

    private float[] lungeOffset(int side, int mid) {
        if (this.lunge == null || this.lunge.side() != side || this.lunge.mid() != mid) {
            return new float[]{0, 0};
        }
        float t = (this.now - this.lunge.start()) / (float) this.lunge.duration();
        if (t >= 1.0F) {
            this.lunge = null;
            return new float[]{0, 0};
        }
        // Wind up, smash into the target (40% of the way through), then step back.
        float f = t < 0.4F ? MatchArt.easeIn(t / 0.4F) * 0.82F : 0.82F * (1.0F - MatchArt.easeOut((t - 0.4F) / 0.6F));
        return new float[]{this.lunge.dx() * f, this.lunge.dy() * f};
    }

    private float pulse(long since, int duration) {
        return since < 0 || this.now - since > duration ? 0 : 1.0F - (this.now - since) / (float) duration;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    // --- Positions --------------------------------------------------------------------------------------------

    private void layout() {
        int w = this.width;
        int h = this.height;
        this.handScale = Math.min(0.75F, h * 0.30F / CardRenderer.HEIGHT);
        this.handW = Math.round(CardRenderer.WIDTH * this.handScale);
        this.handH = Math.round(CardRenderer.HEIGHT * this.handScale);
        this.handTop = h - Math.round(this.handH * 0.62F);
        this.heroSize = Math.max(28, Math.round(h * 0.13F));
        this.toolSize = Math.round(this.heroSize * 0.68F);
        this.centerX = w / 2;
        this.oppHeroY = Math.max(20, Math.round(h * 0.075F));
        this.myHeroY = this.handTop - this.heroSize - 3;
        this.boardTop = this.oppHeroY + this.heroSize + 5;
        this.boardBottom = this.myHeroY - 5;
        this.middle = (this.boardTop + this.boardBottom) / 2;
        this.historyIcon = Math.max(14, Math.round(h / 20.0F));
        this.boardLeft = this.historyIcon + 18;
        this.boardRight = w - 78;
        int rowSpace = (this.boardBottom - this.boardTop) / 2 - 7;
        this.mobH = Math.max(26, Math.min(rowSpace, Math.round(h * 0.19F)));
        this.mobW = Math.round(this.mobH * 0.8F);
        // Six mobs have to fit across the board.
        int widest = (this.boardRight - this.boardLeft - 5 * MOB_GAP - 8) / 6;
        if (this.mobW > widest) {
            this.mobW = widest;
            this.mobH = Math.round(widest / 0.8F);
        }
        this.theirRowY = this.middle - 4 - this.mobH;
        this.myRowY = this.middle + 4;
        this.endTurnButton = new int[]{w - 72, this.middle - 12, 64, 24};
        this.backScale = Math.max(0.15F, h * 0.13F / CardRenderer.HEIGHT);
        this.deckScale = Math.max(0.14F, h * 0.12F / CardRenderer.HEIGHT);
        this.deckW = Math.round(CardRenderer.WIDTH * this.deckScale);
        this.deckH = Math.round(CardRenderer.HEIGHT * this.deckScale);
        this.deckX = w - 40 - this.deckW / 2;
        this.theirDeckY = this.middle - 22 - this.deckH;
        this.myDeckY = this.middle + 22;
        this.orbSize = Math.max(6, Math.round(h / 42.0F));
        this.menuButton = new int[]{w - 18, 4, 14, 14};
    }

    private int rowY(int side) {
        return side == this.shown.you ? this.myRowY : this.theirRowY;
    }

    private int heroY(int side) {
        return side == this.shown.you ? this.myHeroY : this.oppHeroY;
    }

    private float slotX(int index, int count) {
        float total = count * this.mobW + (count - 1) * MOB_GAP;
        return this.centerX - total / 2.0F + index * (this.mobW + MOB_GAP);
    }

    private float[] heroCenter(int side) {
        return new float[]{this.centerX, heroY(side) + this.heroSize / 2.0F};
    }

    private float[] mobCenter(int side, int mid) {
        float y = rowY(side) + this.mobH / 2.0F;
        Float x = this.mobX.get(mid);
        if (x != null) {
            return new float[]{x + this.mobW / 2.0F, y};
        }
        Side s = this.shown.sides[side];
        int index = s.indexOf(mid);
        if (index >= 0) {
            return new float[]{slotX(index, s.board.size()) + this.mobW / 2.0F, y};
        }
        return new float[]{this.centerX, y};
    }

    private float[] actorCenter(int side, int mid) {
        return mid == Match.HERO ? heroCenter(side) : mobCenter(side, mid);
    }

    private float[] targetCenter(Event e) {
        if (!e.aimed()) {
            return new float[]{this.centerX, this.middle};
        }
        return e.targetHero() ? heroCenter(e.targetSide()) : mobCenter(e.targetSide(), e.targetMid());
    }

    private float[] deckCenter(int side) {
        return new float[]{this.deckX + this.deckW / 2.0F, (side == this.shown.you ? this.myDeckY : this.theirDeckY) + this.deckH / 2.0F};
    }

    private float[] toolCenter(int side) {
        return new float[]{this.centerX - this.heroSize / 2.0F - 6 - this.toolSize / 2.0F, heroY(side) + this.heroSize - this.toolSize / 2.0F};
    }

    private float[] powerCenter(int side) {
        return new float[]{this.centerX + this.heroSize / 2.0F + 6 + this.toolSize / 2.0F, heroY(side) + this.heroSize - this.toolSize / 2.0F};
    }

    // Where a card given by an effect comes from: the mob that was just sent back, or the middle of the board.
    private float[] giveOrigin() {
        if (this.lastBounce != null && this.now - this.lastBounceAt < 1500) {
            return this.lastBounce;
        }
        return new float[]{this.centerX, this.middle};
    }

    // Where card i of n sits in the mulligan row: x, y and scale.
    private float[] mulliganSlot(int i, int n) {
        float scale = Math.min(0.95F, Math.min((this.width - 60) / (n * (CardRenderer.WIDTH + 14.0F)), this.height * 0.42F / CardRenderer.HEIGHT));
        float w = CardRenderer.WIDTH * scale;
        float gap = 14 * scale;
        float total = n * w + (n - 1) * gap;
        return new float[]{this.width / 2.0F - total / 2.0F + i * (w + gap), this.height * 0.5F - CardRenderer.HEIGHT * scale / 2.0F, scale};
    }

    private Anim ease(long uid, float x, float y, float scale, float rotation) {
        Anim anim = this.anims.computeIfAbsent(uid, k -> new Anim());
        if (!anim.placed) {
            anim.x = x;
            anim.y = y;
            anim.scale = scale;
            anim.rotation = rotation;
            anim.placed = true;
        }
        if (this.now < anim.holdUntil) {
            x = anim.holdX;
            y = anim.holdY;
            scale = anim.holdScale;
            rotation = 0;
        }
        float k = 1.0F - (float) Math.exp(-this.dt * 11);
        anim.x += (x - anim.x) * k;
        anim.y += (y - anim.y) * k;
        anim.scale += (scale - anim.scale) * k;
        anim.rotation += (rotation - anim.rotation) * k;
        return anim;
    }

    // --- Drawing ----------------------------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        long time = millis();
        this.dt = this.lastFrame == 0 ? 0 : Math.min(0.1F, (time - this.lastFrame) / 1000.0F);
        this.lastFrame = time;
        this.now = time;
        layout();
        advance();
        runScheduled();
        this.mobSpots.clear();
        this.handSpots.clear();
        this.historySpots.clear();

        float[] shake = screenShakeOffset();
        g.pose().pushMatrix();
        g.pose().translate(shake[0], shake[1]);
        drawTable(g);
        drawHistory(g);
        for (int s = 0; s < 2; s++) {
            drawDeck(g, s);
            drawXp(g, s);
            drawNameplate(g, s);
            drawHero(g, s, mouseX, mouseY);
        }
        drawEndTurn(g, mouseX, mouseY);
        drawMenuButton(g, mouseX, mouseY);
        drawRow(g, 1 - this.shown.you, mouseX, mouseY);
        drawRow(g, this.shown.you, mouseX, mouseY);
        drawLungingMob(g);
        drawEffects(g, false);
        drawParticles(g);
        drawTheirHand(g);
        if (this.shown.playing) {
            drawMyHand(g, mouseX, mouseY);
        }
        drawInteraction(g, mouseX, mouseY);
        g.pose().popMatrix();

        boolean overlay = coinShowing() || !this.shown.playing || this.menuOpen || gameOverShowing();
        if (!overlay) {
            drawPreview(g, mouseX, mouseY);
        }
        drawEffects(g, true);
        drawBanner(g);
        if (coinShowing()) {
            drawCoin(g);
        } else if (!this.shown.playing && !this.shown.over) {
            drawMulligan(g, mouseX, mouseY);
        }
        if (gameOverShowing()) {
            drawGameOver(g);
        }
        if (this.menuOpen) {
            drawMenu(g, mouseX, mouseY);
        }
        if (!overlay) {
            drawTooltips(g, mouseX, mouseY);
        }
    }

    // A dark brick wall, with the wooden table in the middle. Each hero sits on its edge.
    private void drawTable(GuiGraphicsExtractor g) {
        g.fill(0, 0, this.width, this.height, MORTAR);
        int brick = 24;
        for (int row = 0; row * brick / 2 < this.height; row++) {
            int y = row * brick / 2;
            int offset = row % 2 == 0 ? 0 : brick / 2;
            for (int x = -offset; x < this.width; x += brick) {
                g.fill(x + 1, y + 1, x + brick - 1, y + brick / 2 - 1, (row + x / brick) % 2 == 0 ? BRICK_A : BRICK_B);
            }
        }
        int x0 = this.boardLeft;
        int x1 = this.boardRight;
        int y0 = this.oppHeroY + this.heroSize / 2;
        int y1 = this.myHeroY + this.heroSize / 2;
        g.fill(x0 - 9, y0 - 9, x1 + 9, y1 + 9, 0xFF000000);
        g.fill(x0 - 8, y0 - 8, x1 + 8, y1 + 8, MatchArt.WOOD_DARK);
        g.fill(x0 - 6, y0 - 6, x1 + 6, y1 + 6, MatchArt.WOOD_LIGHT);
        g.fill(x0 - 5, y0 - 5, x1 + 5, y1 + 5, MatchArt.WOOD_MID);
        g.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, MatchArt.WOOD_DARK);
        for (int y = y0, plank = 0; y < y1; y += 18, plank++) {
            g.fill(x0, y, x1, Math.min(y + 17, y1), plank % 2 == 0 ? PLANK_A : PLANK_B);
            g.fill(x0, Math.min(y + 17, y1), x1, Math.min(y + 18, y1), PLANK_SEAM);
        }
        g.outline(x0, y0, x1 - x0, y1 - y0, MatchArt.GOLD_DARK);
        g.fill(x0 + 8, this.middle - 1, x1 - 8, this.middle + 1, MatchArt.GOLD_DARK);
    }

    // The battle log as a strip of icons down the left, newest at the top. Your actions have green edges,
    // theirs red. Hover one to see what happened.
    private void drawHistory(GuiGraphicsExtractor g) {
        int size = this.historyIcon;
        int x = 6;
        int top = this.oppHeroY;
        int bottom = this.myHeroY + this.heroSize;
        // A book at the top says what the strip is.
        g.fill(x - 1, top - 1, x + size + 1, top + size + 1, 0xFF000000);
        g.fill(x, top, x + size, top + size, PANEL);
        drawItem(g, new ItemStack(Items.WRITABLE_BOOK), x + size / 2.0F, top + size / 2.0F, size * 0.8F / 16.0F);
        this.historySpots.add(new HistorySpot(x, top, size, null));
        // Work out whose turn each line was in, oldest first.
        List<LogLine> lines = new ArrayList<>();
        List<Boolean> mineList = new ArrayList<>();
        boolean mine = false;
        for (LogLine line : this.shown.log) {
            if (!line.turnOf().isEmpty()) {
                mine = line.turnOf().equals(this.shown.me().name);
                lines.add(line);
                mineList.add(mine);
                continue;
            }
            lines.add(line);
            mineList.add(mine);
        }
        int cursor = top + size + 6;
        for (int i = lines.size() - 1; i >= 0 && cursor + size <= bottom; i--) {
            LogLine line = lines.get(i);
            if (!line.turnOf().isEmpty()) {
                g.fill(x, cursor, x + size, cursor + 1, MatchArt.GOLD_DARK);
                cursor += 4;
                continue;
            }
            int edge = mineList.get(i) ? 0xFF3F8A2E : 0xFF8A2E2E;
            g.fill(x - 1, cursor - 1, x + size + 1, cursor + size + 1, 0xFF000000);
            g.fill(x, cursor, x + size, cursor + size, edge);
            g.fill(x + 1, cursor + 1, x + size - 1, cursor + size - 1, 0xFF2B2C31);
            CardDefinition card = line.card() == null ? null : Cards.get(line.card());
            ItemStack icon = card == null ? new ItemStack(Items.PAPER) : CardRenderer.placeholderArt(line.card(), card);
            drawItem(g, icon, x + size / 2.0F, cursor + size / 2.0F, (size - 4) / 16.0F);
            this.historySpots.add(new HistorySpot(x, cursor, size, line));
            cursor += size + 3;
        }
    }

    private void drawItem(GuiGraphicsExtractor g, ItemStack stack, float centerX, float centerY, float scale) {
        g.pose().pushMatrix();
        g.pose().translate(centerX, centerY);
        g.pose().scale(scale, scale);
        g.item(stack, -8, -8);
        g.pose().popMatrix();
    }

    // A face-down pile; it gets thinner as the deck runs out.
    private void drawDeck(GuiGraphicsExtractor g, int s) {
        Side side = this.shown.sides[s];
        int y = s == this.shown.you ? this.myDeckY : this.theirDeckY;
        this.deckSpots[s] = new int[]{this.deckX - 2, y - 4, this.deckW + 6, this.deckH + 6};
        if (side.deck <= 0) {
            g.outline(this.deckX, y, this.deckW, this.deckH, 0x80FFFFFF);
            return;
        }
        int layers = Math.min(4, (side.deck + 4) / 5);
        for (int i = layers; i > 0; i--) {
            g.fill(this.deckX + i, y - i, this.deckX + i + this.deckW, y - i + this.deckH, 0xFF000000);
            g.fill(this.deckX + i, y - i, this.deckX + i + this.deckW - 1, y - i + this.deckH - 1, MatchArt.WOOD_DARK);
        }
        CardRenderer.drawBack(g, Sleeves.get(side.sleeve), this.deckX, y, this.deckScale);
        MatchArt.outlined(g, this.font, String.valueOf(side.deck), this.deckX + this.deckW / 2.0F, y + this.deckH / 2.0F - 4, 0xFFFFFFFF, 1.0F, true);
    }

    // XP orbs, Hearthstone-mana style: one orb on your first turn, one more each turn, up to 10. Hovering or
    // dragging a card makes the orbs it would use blink (red if you can't afford it).
    private void drawXp(GuiGraphicsExtractor g, int s) {
        Side side = this.shown.sides[s];
        int orb = this.orbSize;
        int step = orb + 3;
        boolean mine = s == this.shown.you;
        int right = mine ? this.width - 10 : this.width - 26;
        int y = mine ? this.height - orb - 9 : 9;
        int rowLeft = right - Match.MAX_XP * step;
        int cells = Math.max(side.maxXp, side.xp);
        int full = Math.min(side.xp, side.maxXp);
        int cost = mine ? previewCost() : -1;
        boolean blink = (this.now / 260) % 2 == 0;
        for (int i = 0; i < cells; i++) {
            // Orbs past your max only show while they hold bonus XP (from a Bottle o' Enchanting).
            boolean holds = i < full || (i >= side.maxXp && i < side.xp);
            MatchArt.Orb state = i < full ? MatchArt.Orb.FULL : i < side.maxXp ? MatchArt.Orb.SPENT : MatchArt.Orb.BONUS;
            // Orbs light up one after another when they refill.
            if (holds && this.xpRefilled[s] >= 0 && this.now - this.xpRefilled[s] < i * 45L) {
                state = MatchArt.Orb.SPENT;
            }
            if (cost > 0 && holds && blink) {
                if (cost > side.xp) {
                    state = MatchArt.Orb.MISSING;
                } else if (rankFromTop(i, full, side) < cost) {
                    state = MatchArt.Orb.PREVIEW;
                }
            }
            MatchArt.orb(g, rowLeft + i * step, y, orb, state);
        }
        String number = side.xp <= side.maxXp ? side.xp + "/" + side.maxXp : String.valueOf(side.xp);
        int color = cost > side.xp ? 0xFFFF5A4A : side.xp > side.maxXp ? MatchArt.GOLD_LIGHT : MatchArt.XP;
        float scale = Math.max(1.0F, orb / 7.0F);
        int textWidth = Math.round(this.font.width(number) * scale);
        MatchArt.outlined(g, this.font, number, rowLeft - 6 - textWidth, y + orb / 2.0F - 4 * scale, color, scale, false);
        this.xpSpots[s] = new int[]{rowLeft - 8 - textWidth, y - 3, right - rowLeft + textWidth + 10, orb + 6};
    }

    // How far an XP-holding orb is from the end of the filled orbs (0 = the last one), for the cost preview.
    private static int rankFromTop(int index, int full, Side side) {
        if (index >= side.maxXp) {
            return side.xp - 1 - index;
        }
        int bonus = Math.max(0, side.xp - side.maxXp);
        return bonus + (full - 1 - index);
    }

    // The XP the card you're hovering or dragging costs, or the power's cost while hovering it (-1 for none).
    private int previewCost() {
        if (!this.shown.myTurn) {
            return -1;
        }
        HandCard card = this.dragging != null ? this.dragging : this.awaitingTarget;
        if (card == null && this.hoveredHand >= 0 && this.hoveredHand < this.shown.me().hand.size()) {
            card = this.shown.me().hand.get(this.hoveredHand);
        }
        if (card != null) {
            CardDefinition definition = Cards.get(card.id);
            return definition == null ? -1 : definition.cost();
        }
        if (this.hoveringPower) {
            return this.shown.powerCost;
        }
        return -1;
    }

    private boolean hoveringPower;

    private void drawEndTurn(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int[] b = this.endTurnButton;
        boolean hover = inside(b, mouseX, mouseY);
        int dy = this.now - this.endTurnPressed < 140 ? 1 : 0;
        if (this.shown.myTurn && this.shown.playing && !this.shown.over) {
            boolean done = nothingLeftToDo();
            float glow = done ? 0.55F + 0.45F * (float) Math.sin(this.now / 220.0) : 0;
            int topColor = done ? (hover ? 0xFF8CF06A : 0xFF6BD34A) : (hover ? 0xFFFFE07A : 0xFFFFD25A);
            int bottomColor = done ? 0xFF2E8A1E : 0xFFE2A52A;
            MatchArt.button(g, this.font, Component.translatable("match_ui.craftbound.end_turn"), b[0], b[1] + dy, b[2], b[3],
                    topColor, bottomColor, 0xFFFFFFFF, glow);
        } else {
            MatchArt.button(g, this.font, Component.translatable("match_ui.craftbound.enemy_turn"), b[0], b[1], b[2], b[3],
                    0xFF4A4844, 0xFF33312E, 0xFF9E978B, 0);
        }
    }

    // Nothing left to play, attack with or use: END TURN turns green.
    private boolean nothingLeftToDo() {
        Side me = this.shown.me();
        return me.hand.stream().noneMatch(card -> card.playable) && me.board.stream().noneMatch(mob -> mob.ready)
                && !me.heroReady && !me.powerUsable;
    }

    private void drawMenuButton(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int[] b = this.menuButton;
        boolean hover = inside(b, mouseX, mouseY);
        g.fill(b[0] - 1, b[1] - 1, b[0] + b[2] + 1, b[1] + b[3] + 1, 0xFF000000);
        g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], hover ? PANEL_EDGE : PANEL);
        for (int i = 0; i < 3; i++) {
            g.fill(b[0] + 3, b[1] + 3 + i * 3, b[0] + b[2] - 3, b[1] + 4 + i * 3, MatchArt.TEXT);
        }
    }

    // A nameplate beside each hero, the same for both players: name, class and how many cards they have.
    private void drawNameplate(GuiGraphicsExtractor g, int s) {
        Side side = this.shown.sides[s];
        int right = this.centerX - this.heroSize / 2 - this.toolSize - 14;
        int width = Math.min(116, right - (this.historyIcon + 16));
        if (width < 50) {
            return;
        }
        int height = 30;
        int x = right - width;
        int y = heroY(s) + (this.heroSize - height) / 2;
        this.nameplateSpots[s] = new int[]{x, y, width, height};
        boolean active = this.shown.playing && !this.shown.over && this.shown.current == s;
        g.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF000000);
        g.fillGradient(x, y, x + width, y + height, PANEL, PANEL_DARK);
        g.outline(x + 1, y + 1, width - 2, height - 2, active ? MatchArt.GOLD : PANEL_EDGE);
        CardRenderer.fitText(g, this.font, Component.literal(side.name).withStyle(ChatFormatting.BOLD), x + 5, y + 4, width - 10, MatchArt.TEXT, false);
        String chip = side.cardClass.displayName().getString().toUpperCase(Locale.ROOT);
        int chipWidth = Math.round(this.font.width(chip) * 0.6F) + 6;
        g.fill(x + 5, y + 16, x + 5 + chipWidth, y + 25, classColor(side.cardClass));
        CardRenderer.fitText(g, this.font, Component.literal(chip), x + 8, y + 17, chipWidth - 6, 0xFFFFFFFF, false);
        Component meta = Component.translatable("match_ui.craftbound.meta", side.handCount, side.deck);
        CardRenderer.fitText(g, this.font, meta, x + 9 + chipWidth, y + 17, width - chipWidth - 13, MatchArt.TEXT_DIM, false);
    }

    private static int classColor(CardClass cardClass) {
        return switch (cardClass) {
            case FIGHTER -> 0xFFB23A2E;
            case BUILDER -> 0xFF3F62B0;
            case FARMER -> 0xFF4E8A2A;
            case NEUTRAL -> 0xFF5D5E66;
        };
    }

    // A hero: portrait (the player's face), health heart, and the attack badge while holding a tool on their turn.
    private void drawHero(GuiGraphicsExtractor g, int s, int mouseX, int mouseY) {
        Side side = this.shown.sides[s];
        int size = this.heroSize;
        int x = this.centerX - size / 2;
        int y = heroY(s);
        this.heroSpots[s] = new Spot(x, y, size, size, s, Match.HERO, -1);
        boolean mine = s == this.shown.you;
        float[] shake = shake("h:" + s);
        float[] lunge = lungeOffset(s, Match.HERO);
        g.pose().pushMatrix();
        g.pose().translate(shake[0] + lunge[0], shake[1] + lunge[1]);
        if (mine && side.heroReady && canAct() && this.attacker == null && this.dragging == null) {
            g.fill(x - 4, y - 4, x + size + 4, y + size + 4, 0x807DFF5A);
            g.outline(x - 3, y - 3, size + 6, size + 6, MatchArt.GLOW);
        }
        if (heroTargetable(s)) {
            boolean over = this.heroSpots[s].contains(mouseX, mouseY);
            g.outline(x - 4, y - 4, size + 8, size + 8, over ? MatchArt.GOLD_LIGHT : MatchArt.RED);
            g.outline(x - 3, y - 3, size + 6, size + 6, over ? MatchArt.GOLD_LIGHT : MatchArt.RED);
        }
        MatchArt.octagon(g, x - 3, y - 3, size + 6, 0xFF000000);
        MatchArt.octagon(g, x - 2, y - 2, size + 4, MatchArt.GOLD);
        MatchArt.octagon(g, x, y, size, classColor(side.cardClass));
        MatchArt.octagon(g, x + 2, y + 2, size - 4, 0xFF2B2C31);
        drawFace(g, side, x + 4, y + 4, size - 8);
        float flash = pulse(this.flashes.getOrDefault("h:" + s, -1L), 260);
        if (flash > 0) {
            MatchArt.octagon(g, x, y, size, MatchArt.alpha(0xFFFF2020, flash * 0.7F));
        }
        int heart = Math.max(12, Math.round(size * 0.42F));
        int healthColor = side.health < Match.START_HEALTH ? 0xFFFF5A5A : 0xFFFFFFFF;
        MatchArt.heart(g, this.font, x + size - heart * 3 / 4, y + size - heart * 4 / 5, heart, side.health, healthColor);
        if (side.tool != null && this.shown.current == s && this.shown.playing) {
            int badge = Math.max(11, Math.round(size * 0.36F));
            MatchArt.attack(g, this.font, x - badge / 4, y + size - badge * 4 / 5, badge, side.tool.attack, 0xFFFFFFFF);
        }
        g.pose().popMatrix();
        drawTool(g, s, x - this.toolSize - 6, y + size - this.toolSize);
        drawPower(g, s, x + size + 6, y + size - this.toolSize, mouseX, mouseY);
    }

    // The player's skin face, or the Card Master's spawn egg.
    private void drawFace(GuiGraphicsExtractor g, Side side, int x, int y, int size) {
        if (!side.ai && side.uuid != null && this.minecraft != null && this.minecraft.getConnection() != null) {
            PlayerInfo info = this.minecraft.getConnection().getPlayerInfo(side.uuid);
            if (info != null) {
                PlayerFaceExtractor.extractRenderState(g, info.getSkin(), x, y, size);
                return;
            }
        }
        ItemStack face = new ItemStack(side.ai ? Items.WANDERING_TRADER_SPAWN_EGG : Items.PLAYER_HEAD);
        drawItem(g, face, x + size / 2.0F, y + size / 2.0F, size / 16.0F);
    }

    // The tool the hero holds, with its attack and how many attacks it has left. An empty socket when none.
    private void drawTool(GuiGraphicsExtractor g, int s, int x, int y) {
        Side side = this.shown.sides[s];
        int size = this.toolSize;
        this.toolSpots[s] = new int[]{x, y, size, size};
        MatchArt.octagon(g, x - 1, y - 1, size + 2, 0xFF000000);
        MatchArt.octagon(g, x, y, size, 0xFF2A2A2E);
        if (side.tool == null) {
            return;
        }
        float equip = this.flashes.containsKey("tool:" + s) ? MatchArt.pop((this.now - this.flashes.get("tool:" + s)) / 300.0F) : 1.0F;
        g.pose().pushMatrix();
        g.pose().translate(x + size / 2.0F, y + size / 2.0F);
        g.pose().scale(equip, equip);
        g.pose().translate(-size / 2.0F, -size / 2.0F);
        MatchArt.octagon(g, 0, 0, size, MatchArt.GOLD_DARK);
        MatchArt.octagon(g, 2, 2, size - 4, 0xFF5D5E66);
        CardDefinition card = Cards.get(side.tool.id);
        if (card != null) {
            drawItem(g, CardRenderer.placeholderArt(side.tool.id, card), size / 2.0F, size / 2.0F, (size - 6) / 16.0F);
        }
        int badge = Math.max(10, Math.round(size * 0.42F));
        MatchArt.attack(g, this.font, -badge / 3, size - badge * 3 / 4, badge, side.tool.attack, 0xFFFFFFFF);
        MatchArt.uses(g, this.font, size - badge * 2 / 3, size - badge * 3 / 4, badge, side.tool.uses);
        g.pose().popMatrix();
    }

    // The class power: glows when you can use it, greys out once used this turn.
    private void drawPower(GuiGraphicsExtractor g, int s, int x, int y, int mouseX, int mouseY) {
        Side side = this.shown.sides[s];
        int size = this.toolSize;
        this.powerSpots[s] = new int[]{x, y, size, size};
        boolean mine = s == this.shown.you;
        if (mine) {
            this.hoveringPower = side.power != HeroPowers.Power.NONE && inside(this.powerSpots[s], mouseX, mouseY) && this.dragging == null;
        }
        if (side.power == HeroPowers.Power.NONE) {
            return;
        }
        boolean usable = mine && side.powerUsable && canAct();
        float flip = this.flashes.containsKey("power:" + s) ? MatchArt.clamp((this.now - this.flashes.get("power:" + s)) / 350.0F) : 1.0F;
        float squish = Math.max(0.08F, Math.abs((float) Math.cos(flip * Math.PI)));
        if (usable) {
            float glow = 0.6F + 0.4F * (float) Math.sin(this.now / 250.0);
            MatchArt.octagon(g, x - 4, y - 4, size + 8, MatchArt.alpha(0xFF7DFF5A, glow * 0.7F));
        }
        g.pose().pushMatrix();
        g.pose().translate(x + size / 2.0F, y + size / 2.0F);
        g.pose().scale(squish, 1.0F);
        g.pose().translate(-size / 2.0F, -size / 2.0F);
        MatchArt.octagon(g, -1, -1, size + 2, 0xFF000000);
        MatchArt.octagon(g, 0, 0, size, MatchArt.GOLD);
        MatchArt.octagon(g, 2, 2, size - 4, darker(classColor(side.cardClass)));
        drawItem(g, new ItemStack(BuiltInRegistries.ITEM.getValue(side.power.icon())), size / 2.0F, size / 2.0F, (size - 8) / 16.0F);
        if (side.powerUsed) {
            MatchArt.octagon(g, 0, 0, size, 0xA0000000);
        }
        g.pose().popMatrix();
        int gem = Math.max(9, Math.round(size * 0.38F));
        MatchArt.cost(g, this.font, x + size / 2 - gem / 2, y - gem / 2, gem, this.shown.powerCost);
    }

    private static int darker(int color) {
        int r = ((color >> 16) & 0xFF) * 3 / 5;
        int gr = ((color >> 8) & 0xFF) * 3 / 5;
        int b = (color & 0xFF) * 3 / 5;
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }

    // A row of mobs, centred. Mobs slide when one arrives or leaves.
    private void drawRow(GuiGraphicsExtractor g, int s, int mouseX, int mouseY) {
        Side side = this.shown.sides[s];
        int y = rowY(s);
        boolean pending = s == this.shown.you && this.awaitingTarget != null;
        int count = side.board.size() + (pending ? 1 : 0);
        float k = 1.0F - (float) Math.exp(-this.dt * 12);
        for (int i = 0; i < side.board.size(); i++) {
            Mob mob = side.board.get(i);
            float target = slotX(i, count);
            float x = this.mobX.getOrDefault(mob.mid, target);
            x += (target - x) * k;
            this.mobX.put(mob.mid, x);
            Spot spot = new Spot(Math.round(x), y, this.mobW, this.mobH, s, mob.mid, -1);
            this.mobSpots.add(spot);
            if (this.lunge != null && this.lunge.side() == s && this.lunge.mid() == mob.mid) {
                continue;
            }
            float[] shake = shake("m:" + mob.mid);
            drawMob(g, mob, s, x + shake[0], y + shake[1], 1.0F, spot.contains(mouseX, mouseY), this.arrived.getOrDefault(mob.mid, -1L));
        }
        if (pending) {
            CardDefinition card = Cards.get(this.awaitingTarget.id);
            Mob ghost = new Mob();
            ghost.mid = -2;
            ghost.id = this.awaitingTarget.id;
            ghost.enchanted = this.awaitingTarget.enchanted;
            ghost.attack = card.attack();
            ghost.health = card.health();
            ghost.maxHealth = card.health();
            ghost.shield = card.keywords().contains(Keyword.SHIELD);
            float x = slotX(count - 1, count);
            this.pendingSpot = new Spot(Math.round(x), y, this.mobW, this.mobH, s, -2, -1);
            drawMob(g, ghost, s, x, y, 1.0F, false, -1);
            g.outline(Math.round(x) - 3, y - 3, this.mobW + 6, this.mobH + 6, MatchArt.GOLD_LIGHT);
        } else if (s == this.shown.you) {
            this.pendingSpot = null;
        }
    }

    // The attacking mob is drawn after both rows so it flies over everything.
    private void drawLungingMob(GuiGraphicsExtractor g) {
        if (this.lunge == null || this.lunge.mid() == Match.HERO) {
            return;
        }
        int side = this.lunge.side();
        Mob mob = this.shown.sides[side].mob(this.lunge.mid());
        if (mob == null) {
            this.lunge = null;
            return;
        }
        float[] offset = lungeOffset(side, mob.mid);
        float x = this.mobX.getOrDefault(mob.mid, (float) this.centerX);
        drawMob(g, mob, side, x + offset[0], rowY(side) + offset[1], 1.08F, false, -1);
    }

    // A mob on the board: its art in a frame, attack bottom-left and a heart with its health bottom-right.
    // A Shield mob has a silver plate behind it; Blocks show no attack (they never attack).
    private void drawMob(GuiGraphicsExtractor g, Mob mob, int side, float x, float y, float scale, boolean hovered, long arrivedAt) {
        CardDefinition card = mob.card();
        if (card == null) {
            return;
        }
        int w = this.mobW;
        int h = this.mobH;
        float pop = 1.0F;
        float flash = 0;
        if (arrivedAt >= 0 && this.now - arrivedAt < 320) {
            float t = (this.now - arrivedAt) / 320.0F;
            pop = 1.35F - 0.35F * MatchArt.easeOut(t);
            flash = 1.0F - t;
        }
        boolean mine = side == this.shown.you;
        g.pose().pushMatrix();
        g.pose().translate(x + w / 2.0F, y + h / 2.0F);
        g.pose().scale(scale * pop, scale * pop);
        g.pose().translate(-w / 2.0F, -h / 2.0F);
        if (mine && mob.ready && canAct() && this.attacker == null && this.dragging == null && mob.mid > 0) {
            g.fill(-4, -4, w + 4, h + 4, 0x807DFF5A);
            g.outline(-3, -3, w + 6, h + 6, MatchArt.GLOW);
        }
        Spot probe = new Spot(Math.round(x), Math.round(y), w, h, side, mob.mid, -1);
        if (mob.mid > 0 && isValidTarget(probe)) {
            int color = hovered ? MatchArt.GOLD_LIGHT : MatchArt.RED;
            g.outline(-5, -5, w + 10, h + 10, color);
            g.outline(-4, -4, w + 8, h + 8, color);
        }
        if (mob.shield) {
            g.fill(-5, -5, w + 5, h + 7, 0xFF000000);
            g.fillGradient(-4, -4, w + 4, h + 6, 0xFFE2E7EC, 0xFF7E858C);
            g.fill(w / 2 - 3, h + 6, w / 2 + 3, h + 9, 0xFF7E858C);
        }
        boolean legendary = card.rarity() == craftbound.card.CardRarity.LEGENDARY;
        g.fill(-1, -1, w + 1, h + 1, 0xFF000000);
        g.fill(0, 0, w, h, legendary ? MatchArt.GOLD : mob.enchanted ? 0xFFB57AE0 : 0xFF6B6C72);
        int inset = legendary ? 3 : 2;
        if (mob.block) {
            g.fillGradient(inset, inset, w - inset, h - inset, 0xFF4A4B52, 0xFF2E2F34);
        } else {
            g.fillGradient(inset, inset, w - inset, h - inset, 0xFF6FC9E2, 0xFF5BA0C8);
            g.fill(inset, h - inset - h / 5, w - inset, h - inset, 0xFF6ABF33);
        }
        if (!mine) {
            g.fill(inset, inset, w - inset, h - inset, 0x30400000);
        }
        drawItem(g, CardRenderer.placeholderArt(mob.id, card), w / 2.0F, h / 2.0F - 1, (w - inset * 2 - 4) / 16.0F);
        if (mob.enchanted) {
            CardRenderer.drawDiagonalShimmer(g, inset, inset, w - inset * 2, h - inset * 2, 6, 0x70);
        }
        if (mob.frozen) {
            g.fill(inset, inset, w - inset, h - inset, 0x7090D8FF);
            g.outline(inset, inset, w - inset * 2, h - inset * 2, 0xFFE0F6FF);
        }
        if (mob.withered) {
            g.fill(inset, inset, w - inset, h - inset, 0x70101010);
        }
        if (mob.invulnerable) {
            float glow = 0.6F + 0.4F * (float) Math.sin(this.now / 200.0);
            g.outline(0, 0, w, h, MatchArt.alpha(0xFFFFE65A, glow));
            g.outline(1, 1, w - 2, h - 2, MatchArt.alpha(0xFFFFE65A, glow));
        }
        if (flash > 0) {
            g.fill(0, 0, w, h, MatchArt.alpha(0xFFFFFFFF, flash * 0.8F));
        }
        // Little icons along the top for what's on the mob.
        int icon = Math.max(7, w / 5);
        int iconX = inset + 1;
        if (mob.totem) {
            drawItem(g, new ItemStack(Items.TOTEM_OF_UNDYING), iconX + icon / 2.0F, inset + icon / 2.0F + 1, icon / 16.0F);
            iconX += icon + 1;
        }
        if (mob.withered) {
            drawItem(g, new ItemStack(Items.WITHER_ROSE), iconX + icon / 2.0F, inset + icon / 2.0F + 1, icon / 16.0F);
        }
        if (card.keywords().contains(Keyword.DROP)) {
            drawItem(g, new ItemStack(Items.SKELETON_SKULL), w / 2.0F, h - inset - icon / 2.0F, icon / 16.0F);
        }
        int badge = Math.max(10, Math.round(w * 0.36F));
        int baseAttack = card.attack();
        if (!mob.block) {
            int attackColor = mob.attack > baseAttack ? 0xFF7DFF5A : 0xFFFFFFFF;
            MatchArt.attack(g, this.font, -badge / 3, h - badge * 3 / 4, badge, mob.attack, attackColor);
        }
        int heart = Math.max(11, Math.round(w * 0.4F));
        int healthColor = mob.health < mob.maxHealth ? 0xFFFF5A5A : mob.maxHealth > card.health() ? 0xFF7DFF5A : 0xFFFFFFFF;
        MatchArt.heart(g, this.font, w - heart * 2 / 3, h - heart * 3 / 4, heart, mob.health, healthColor);
        g.pose().popMatrix();
    }

    // The other player's hand: card backs hanging off the top edge.
    private void drawTheirHand(GuiGraphicsExtractor g) {
        Side side = this.shown.them();
        int count = side.backs.size();
        int w = Math.round(CardRenderer.WIDTH * this.backScale);
        int h = Math.round(CardRenderer.HEIGHT * this.backScale);
        float step = Math.min(w * 0.72F, this.width * 0.4F / Math.max(1, count));
        Sleeves.Design design = Sleeves.get(side.sleeve);
        for (int i = 0; i < count; i++) {
            float offset = i - (count - 1) / 2.0F;
            float x = this.centerX + offset * step - w / 2.0F;
            float y = -h * 0.45F + Math.abs(offset) * 1.2F;
            Anim anim = ease(side.backs.get(i), x, y, 1.0F, (float) Math.toRadians(-offset * 4));
            float scale = this.backScale * anim.scale;
            g.pose().pushMatrix();
            g.pose().translate(anim.x + w / 2.0F, anim.y + h / 2.0F);
            g.pose().rotate(anim.rotation);
            g.pose().translate(-CardRenderer.WIDTH * scale / 2.0F, -CardRenderer.HEIGHT * scale / 2.0F);
            CardRenderer.drawBack(g, design, 0, 0, scale);
            g.pose().popMatrix();
        }
    }

    // Your hand, fanned along the bottom and overlapping the board. The card under the mouse is drawn big later.
    private void drawMyHand(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        List<HandCard> hand = this.shown.me().hand;
        int count = hand.size();
        float available = Math.min(this.width * 0.5F, count * this.handW * 0.8F);
        float step = count <= 1 ? 0 : Math.min(this.handW * 0.8F, (available - this.handW) / (count - 1));
        float total = step * (count - 1) + this.handW;
        float startX = this.centerX - total / 2.0F;
        int hovered = -1;
        boolean free = this.dragging == null && this.attacker == null && this.awaitingTarget == null && !this.menuOpen;
        for (int i = 0; i < count; i++) {
            int x = Math.round(startX + i * step);
            int width = i == count - 1 ? this.handW : Math.round(step);
            Spot spot = new Spot(x, this.handTop, width, this.height - this.handTop, this.shown.you, -1, i);
            this.handSpots.add(spot);
            if (free && spot.contains(mouseX, mouseY)) {
                hovered = i;
            }
        }
        this.hoveredHand = hovered;
        for (int i = 0; i < count; i++) {
            HandCard card = hand.get(i);
            float offset = i - (count - 1) / 2.0F;
            float x = startX + i * step;
            float y = this.handTop + Math.abs(offset) * 2.5F;
            Anim anim = ease(card.uid, x, y, 1.0F, (float) Math.toRadians(offset * 3));
            if (card == this.dragging || card == this.awaitingTarget || card.uid == this.hiddenUid || i == hovered) {
                continue;
            }
            drawHandCard(g, card, anim);
        }
    }

    private void drawHandCard(GuiGraphicsExtractor g, HandCard card, Anim anim) {
        float scale = this.handScale * anim.scale;
        float width = CardRenderer.WIDTH * scale;
        float height = CardRenderer.HEIGHT * scale;
        boolean back = false;
        float flip = 1.0F;
        if (anim.flipAt > 0) {
            float t = (this.now - anim.flipAt) / 200.0F;
            if (t < 0) {
                back = true;
            } else if (t < 1) {
                flip = Math.max(0.05F, Math.abs(1.0F - 2.0F * t));
                back = t < 0.5F;
            }
        }
        g.pose().pushMatrix();
        g.pose().translate(anim.x + this.handW * anim.scale / 2.0F, anim.y + this.handH * anim.scale / 2.0F);
        g.pose().rotate(anim.rotation);
        g.pose().scale(flip, 1.0F);
        g.pose().translate(-width / 2.0F, -height / 2.0F);
        if (back) {
            CardRenderer.drawBack(g, Sleeves.get(this.shown.me().sleeve), 0, 0, scale);
        } else {
            boolean playable = card.playable && canAct();
            if (playable) {
                g.fill(-3, -3, Math.round(width) + 3, Math.round(height) + 3, 0x807DFF5A);
                g.outline(-2, -2, Math.round(width) + 4, Math.round(height) + 4, MatchArt.GLOW);
            }
            CardRenderer.draw(g, this.font, CardItem.of(card.id, card.enchanted), 0, 0, scale);
            if (!playable && this.shown.myTurn) {
                g.fill(0, 0, Math.round(width), Math.round(height), 0x50000000);
            }
        }
        g.pose().popMatrix();
    }

    // The card being dragged, or the aiming arrow for an attack or a Spawn target.
    private void drawInteraction(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (this.dragging != null) {
            float scale = this.handScale * 0.9F;
            int w = Math.round(CardRenderer.WIDTH * scale);
            int h = Math.round(CardRenderer.HEIGHT * scale);
            boolean overBoard = mouseY < this.handTop - 4;
            boolean aims = !this.dragging.target.equals("NONE") && !Cards.get(this.dragging.id).isMob();
            if (aims && overBoard) {
                // A targeted item: a small card at the mouse and an arrow from your hero.
                Spot hero = this.heroSpots[this.shown.you];
                MatchArt.arrow(g, hero.centerX(), hero.y(), mouseX, mouseY, MatchArt.RED, this.now);
            } else {
                CardRenderer.draw(g, this.font, CardItem.of(this.dragging.id, this.dragging.enchanted), mouseX - w / 2, mouseY - h / 2, scale);
            }
        }
        if (this.attacker != null) {
            MatchArt.arrow(g, this.attacker.centerX(), this.attacker.centerY(), mouseX, mouseY, MatchArt.RED, this.now);
        }
        if (this.awaitingTarget != null && this.pendingSpot != null) {
            MatchArt.arrow(g, this.pendingSpot.centerX(), this.pendingSpot.centerY(), mouseX, mouseY, MatchArt.RED, this.now);
        }
    }

    // Hovering a card in your hand lifts it up big; hovering a mob shows its card in the middle of the board;
    // hovering a log icon shows the card it was about.
    private void drawPreview(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (this.dragging != null || this.attacker != null || this.awaitingTarget != null) {
            return;
        }
        if (this.hoveredHand >= 0 && this.hoveredHand < this.shown.me().hand.size() && this.hoveredHand < this.handSpots.size()) {
            HandCard card = this.shown.me().hand.get(this.hoveredHand);
            Spot spot = this.handSpots.get(this.hoveredHand);
            float scale = Math.min(1.05F, this.height * 0.5F / CardRenderer.HEIGHT);
            int w = Math.round(CardRenderer.WIDTH * scale);
            int h = Math.round(CardRenderer.HEIGHT * scale);
            int x = Math.max(this.boardLeft, Math.min(this.width - w - 90, spot.x() + this.handW / 2 - w / 2));
            int y = this.height - h - 4;
            if (card.playable && canAct()) {
                g.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0x807DFF5A);
                g.outline(x - 2, y - 2, w + 4, h + 4, MatchArt.GLOW);
            }
            CardRenderer.draw(g, this.font, CardItem.of(card.id, card.enchanted), x, y, scale);
            drawCardExtras(g, card.id, x, y, w, scale);
            return;
        }
        for (Spot spot : this.mobSpots) {
            if (!spot.contains(mouseX, mouseY)) {
                continue;
            }
            Mob mob = this.shown.sides[spot.side()].mob(spot.mid());
            if (mob == null) {
                return;
            }
            float scale = Math.min(1.0F, (this.boardBottom - this.boardTop + 20) / (float) CardRenderer.HEIGHT);
            int w = Math.round(CardRenderer.WIDTH * scale);
            int h = Math.round(CardRenderer.HEIGHT * scale);
            int x = this.centerX - w / 2;
            int y = this.middle - h / 2;
            CardRenderer.draw(g, this.font, CardItem.of(mob.id, mob.enchanted), x, y, scale);
            drawCardExtras(g, mob.id, x, y, w, scale);
            return;
        }
        for (HistorySpot spot : this.historySpots) {
            if (spot.line() == null || spot.line().card() == null || Cards.get(spot.line().card()) == null
                    || !(mouseX >= spot.x() && mouseX < spot.x() + spot.size() && mouseY >= spot.y() && mouseY < spot.y() + spot.size())) {
                continue;
            }
            float scale = Math.min(0.7F, this.height * 0.42F / CardRenderer.HEIGHT);
            int h = Math.round(CardRenderer.HEIGHT * scale);
            int y = Math.max(4, Math.min(this.height - h - 4, spot.y() - h / 2));
            CardRenderer.draw(g, this.font, CardItem.of(spot.line().card()), spot.x() + spot.size() + 8, y, scale);
            return;
        }
    }

    // Keyword rules to the left of a big card and the cards it creates to the right, like the inspect screen.
    private void drawCardExtras(GuiGraphicsExtractor g, Identifier id, int x, int y, int width, float scale) {
        CardDefinition card = Cards.get(id);
        if (card == null) {
            return;
        }
        int boxWidth = 120;
        int boxX = x - 8 - boxWidth;
        int boxY = y + 8;
        for (Keyword keyword : card.keywords()) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            lines.addAll(this.font.split(keyword.displayName().copy().withStyle(ChatFormatting.GOLD), boxWidth - 10));
            lines.addAll(this.font.split(keyword.description(), boxWidth - 10));
            int boxHeight = lines.size() * this.font.lineHeight + 10;
            g.fill(boxX - 1, boxY - 1, boxX + boxWidth + 1, boxY + boxHeight + 1, 0xFF000000);
            g.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xF014110D);
            g.outline(boxX, boxY, boxWidth, boxHeight, MatchArt.GOLD_DARK);
            int textY = boxY + 5;
            for (FormattedCharSequence line : lines) {
                g.text(this.font, line, boxX + 5, textY, MatchArt.TEXT);
                textY += this.font.lineHeight;
            }
            boxY += boxHeight + 6;
        }
        float relatedScale = scale * 0.6F;
        int relatedHeight = Math.round(CardRenderer.HEIGHT * relatedScale);
        int relatedX = x + width + 10;
        int relatedY = y + 14;
        for (var related : card.related()) {
            g.text(this.font, Component.translatable("card_ui.craftbound.creates"), relatedX, relatedY - 10, MatchArt.GOLD_LIGHT);
            CardRenderer.draw(g, this.font, CardItem.of(related.card()), relatedX, relatedY, relatedScale);
            relatedY += relatedHeight + 16;
        }
    }

    // Tooltips for the small things: heroes, tools, powers, XP, decks and the log.
    private void drawTooltips(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (this.dragging != null || this.attacker != null) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        for (int s = 0; s < 2; s++) {
            Side side = this.shown.sides[s];
            if (inside(this.toolSpots[s], mouseX, mouseY) && side.tool != null) {
                lines.add(Cards.name(side.tool.id).copy().withStyle(ChatFormatting.GOLD));
                lines.add(Component.translatable("match_ui.craftbound.tool_tip", side.tool.attack, side.tool.uses).withStyle(ChatFormatting.GRAY));
                if (side.tool.ranged) {
                    lines.add(Keyword.RANGED.description().copy().withStyle(ChatFormatting.GRAY));
                }
            } else if (inside(this.powerSpots[s], mouseX, mouseY) && side.power != HeroPowers.Power.NONE) {
                lines.add(side.power.displayName().copy().withStyle(ChatFormatting.GOLD));
                lines.add(side.power.description().copy().withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("match_ui.craftbound.power_cost", this.shown.powerCost).withStyle(ChatFormatting.GREEN));
                if (side.powerUsed) {
                    lines.add(Component.translatable("match_ui.craftbound.power_used").withStyle(ChatFormatting.DARK_GRAY));
                }
            } else if (inside(this.xpSpots[s], mouseX, mouseY)) {
                lines.add(Component.translatable("match_ui.craftbound.xp_tip", side.xp, side.maxXp).withStyle(ChatFormatting.GREEN));
                lines.add(Component.translatable("match_ui.craftbound.xp_tip.desc").withStyle(ChatFormatting.GRAY));
            } else if (inside(this.deckSpots[s], mouseX, mouseY)) {
                lines.add(Component.translatable("match_ui.craftbound.deck_tip", side.deck).withStyle(ChatFormatting.GOLD));
                if (side.deck == 0) {
                    lines.add(Component.translatable("match_ui.craftbound.deck_tip.empty").withStyle(ChatFormatting.GRAY));
                }
            } else if (this.heroSpots[s] != null && this.heroSpots[s].contains(mouseX, mouseY)) {
                lines.add(Component.literal(side.name).withStyle(ChatFormatting.GOLD));
                lines.add(Component.translatable("match_ui.craftbound.hero_tip", side.health, Match.START_HEALTH).withStyle(ChatFormatting.RED));
                if (s == this.shown.you && side.heroReady) {
                    lines.add(Component.translatable("match_ui.craftbound.hero_ready").withStyle(ChatFormatting.GREEN));
                }
            }
        }
        for (Spot spot : this.mobSpots) {
            if (!spot.contains(mouseX, mouseY)) {
                continue;
            }
            Mob mob = this.shown.sides[spot.side()].mob(spot.mid());
            if (mob == null) {
                break;
            }
            if (mob.frozen) lines.add(Component.translatable("match_ui.craftbound.status.frozen").withStyle(ChatFormatting.AQUA));
            if (mob.withered) lines.add(Component.translatable("match_ui.craftbound.status.withered").withStyle(ChatFormatting.GRAY));
            if (mob.totem) lines.add(Component.translatable("match_ui.craftbound.status.totem").withStyle(ChatFormatting.GOLD));
            if (mob.invulnerable) lines.add(Component.translatable("match_ui.craftbound.status.invulnerable").withStyle(ChatFormatting.YELLOW));
            if (spot.side() == this.shown.you && !mob.ready && this.shown.myTurn && !mob.block) {
                lines.add(Component.translatable("match_ui.craftbound.status.not_ready").withStyle(ChatFormatting.GRAY));
            }
            break;
        }
        for (HistorySpot spot : this.historySpots) {
            if (mouseX >= spot.x() && mouseX < spot.x() + spot.size() && mouseY >= spot.y() && mouseY < spot.y() + spot.size()) {
                if (spot.line() == null) {
                    lines.add(Component.translatable("match_ui.craftbound.log").withStyle(ChatFormatting.GOLD));
                    lines.add(Component.translatable("match_ui.craftbound.log.desc").withStyle(ChatFormatting.GRAY));
                } else {
                    lines.add(Component.literal(spot.line().text()));
                }
                break;
            }
        }
        if (inside(this.menuButton, mouseX, mouseY)) {
            lines.add(Component.translatable("match_ui.craftbound.menu"));
        }
        if (!lines.isEmpty()) {
            Tooltips.show(g, this.font, lines, mouseX, mouseY);
        }
    }

    // "YOUR TURN" (big, gold) or "<NAME>'S TURN" (smaller) across the middle.
    private void drawBanner(GuiGraphicsExtractor g) {
        if (this.bannerStart < 0) {
            return;
        }
        int duration = this.bannerMine ? MY_BANNER : THEIR_BANNER;
        float t = (this.now - this.bannerStart) / (float) duration;
        if (t >= 1.0F) {
            this.bannerStart = -1;
            return;
        }
        float size = t < 0.15F ? MatchArt.pop(t / 0.15F) : t > 0.85F ? 1.0F - MatchArt.easeIn((t - 0.85F) / 0.15F) : 1.0F;
        Component text = this.bannerMine ? Component.translatable("match_ui.craftbound.your_turn")
                : Component.translatable("match_ui.craftbound.their_turn", this.shown.sides[this.shown.current].name.toUpperCase(Locale.ROOT));
        float scale = (this.bannerMine ? 3.0F : 1.8F) * size;
        int textWidth = this.font.width(text);
        int bannerWidth = Math.round((textWidth + 24) * scale);
        int bannerHeight = Math.round(18 * scale);
        int x = this.centerX - bannerWidth / 2;
        int y = this.middle - bannerHeight / 2;
        if (size > 0.05F) {
            g.fill(x - 2, y - 2, x + bannerWidth + 2, y + bannerHeight + 2, 0xFF000000);
            g.fillGradient(x, y, x + bannerWidth, y + bannerHeight, 0xFF6B4423, MatchArt.WOOD_DARK);
            g.outline(x + 2, y + 2, bannerWidth - 4, bannerHeight - 4, MatchArt.GOLD);
            MatchArt.outlined(g, this.font, text, this.centerX, this.middle - 4 * scale, this.bannerMine ? MatchArt.GOLD_LIGHT : 0xFFFFB4A8, scale, true);
        }
    }

    private boolean coinShowing() {
        return this.coinStart >= 0 && this.now - this.coinStart < COIN_TIME;
    }

    // The coin toss: a coin with both players' faces spins, then lands on whoever goes first.
    private void drawCoin(GuiGraphicsExtractor g) {
        long t = this.now - this.coinStart;
        float fade = t > COIN_TIME - 300 ? (COIN_TIME - t) / 300.0F : 1.0F;
        g.fill(0, 0, this.width, this.height, MatchArt.alpha(0xC0000000, fade));
        boolean youFirst = this.coinFirst == this.shown.you;
        // It ends on your face (an even number of half turns) if you go first.
        double finalAngle = (youFirst ? 10 : 11) * Math.PI;
        double angle;
        float lift;
        if (t < COIN_LAND) {
            float p = t / (float) COIN_LAND;
            angle = MatchArt.easeOut(p) * finalAngle;
            lift = (float) Math.sin(p * Math.PI) * this.height * 0.2F;
        } else {
            angle = finalAngle;
            float bounce = (t - COIN_LAND) / 250.0F;
            lift = bounce < 1 ? (float) Math.sin(bounce * Math.PI) * 6 : 0;
        }
        float squish = Math.max(0.06F, Math.abs((float) Math.cos(angle)));
        boolean yourFace = Math.cos(angle) >= 0;
        int radius = Math.round(this.height * 0.1F);
        float cx = this.width / 2.0F;
        float cy = this.height * 0.42F - lift;
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(squish, 1.0F);
        MatchArt.octagon(g, -radius - 2, -radius - 2, radius * 2 + 4, 0xFF000000);
        MatchArt.octagon(g, -radius, -radius, radius * 2, MatchArt.GOLD);
        MatchArt.octagon(g, -radius + 3, -radius + 3, radius * 2 - 6, yourFace ? 0xFF3F8A2E : 0xFF8A2E2E);
        drawFace(g, yourFace ? this.shown.me() : this.shown.them(), -radius + 7, -radius + 7, radius * 2 - 14);
        g.pose().popMatrix();
        if (t >= COIN_LAND) {
            Component title = youFirst ? Component.translatable("match_ui.craftbound.you_go_first")
                    : Component.translatable("match_ui.craftbound.they_go_first", this.shown.them().name);
            MatchArt.outlined(g, this.font, title, cx, this.height * 0.42F + radius + 14, MatchArt.alpha(MatchArt.GOLD_LIGHT, fade), 2.0F, true);
            Component subtitle = youFirst ? Component.translatable("match_ui.craftbound.first_hint")
                    : Component.translatable("match_ui.craftbound.second_hint");
            MatchArt.outlined(g, this.font, subtitle, cx, this.height * 0.42F + radius + 36, MatchArt.alpha(MatchArt.TEXT, fade), 1.0F, true);
        } else {
            MatchArt.outlined(g, this.font, Component.translatable("match_ui.craftbound.coin_toss"), cx, this.height * 0.18F,
                    MatchArt.alpha(MatchArt.TEXT, fade), 1.5F, true);
        }
    }

    // The mulligan: your starting cards in a row. Click the ones you want to swap, then Confirm.
    private void drawMulligan(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.fill(0, 0, this.width, this.height, 0xC0000000);
        Side me = this.shown.me();
        boolean first = this.shown.you == 0;
        MatchArt.outlined(g, this.font, Component.translatable("match_ui.craftbound.mulligan_title"), this.width / 2.0F, this.height * 0.08F,
                MatchArt.GOLD_LIGHT, 2.0F, true);
        MatchArt.outlined(g, this.font, Component.translatable(first ? "match_ui.craftbound.first_hint" : "match_ui.craftbound.second_hint"),
                this.width / 2.0F, this.height * 0.08F + 22, MatchArt.TEXT, 1.0F, true);
        int count = me.hand.size();
        this.mulliganSpots.clear();
        boolean choosing = !me.mulliganDone && !this.mulliganSent;
        for (int i = 0; i < count; i++) {
            HandCard card = me.hand.get(i);
            float[] slot = mulliganSlot(i, count);
            int w = Math.round(CardRenderer.WIDTH * slot[2]);
            int h = Math.round(CardRenderer.HEIGHT * slot[2]);
            int x = Math.round(slot[0]);
            int y = Math.round(slot[1]);
            boolean hover = choosing && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
            if (hover) {
                y -= 6;
            }
            this.mulliganSpots.add(new int[]{x, y, w, h});
            float flip = 1.0F;
            boolean back = false;
            if (this.swappedAt >= 0 && (this.swappedMask & (1 << i)) != 0) {
                float t = (this.now - this.swappedAt) / 600.0F;
                if (t < 1) {
                    flip = Math.max(0.05F, Math.abs(1.0F - 2.0F * t));
                    back = t < 0.5F;
                }
            }
            g.pose().pushMatrix();
            g.pose().translate(x + w / 2.0F, y);
            g.pose().scale(flip, 1.0F);
            g.pose().translate(-w / 2.0F, 0);
            if (back) {
                CardRenderer.drawBack(g, Sleeves.get(me.sleeve), 0, 0, slot[2]);
            } else {
                CardRenderer.draw(g, this.font, CardItem.of(card.id, card.enchanted), 0, 0, slot[2]);
            }
            g.pose().popMatrix();
            if (choosing && this.swapPicks[i]) {
                g.fill(x, y, x + w, y + h, 0x80300000);
                int size = Math.min(w, h) / 3;
                int cx = x + w / 2;
                int cy = y + h / 2;
                for (int d = -size; d <= size; d++) {
                    g.fill(cx + d - 2, cy + d - 2, cx + d + 3, cy + d + 3, 0xFFD63A32);
                    g.fill(cx + d - 2, cy - d - 2, cx + d + 3, cy - d + 3, 0xFFD63A32);
                }
                MatchArt.outlined(g, this.font, Component.translatable("match_ui.craftbound.swap"), cx, y + h - 14, 0xFFFFFFFF, 1.0F, true);
            }
        }
        // Going second, the Bottle o' Enchanting waits at the end of the row.
        if (!first && count > 0) {
            float[] slot = mulliganSlot(count - 1, count);
            float scale = slot[2] * 0.6F;
            int x = Math.round(slot[0] + CardRenderer.WIDTH * slot[2] + 18 * slot[2]);
            int y = Math.round(slot[1] + CardRenderer.HEIGHT * slot[2] * 0.2F);
            if (x + CardRenderer.WIDTH * scale < this.width - 4) {
                CardRenderer.draw(g, this.font, CardItem.of(Cards.EXPERIENCE_BOTTLE), x, y, scale);
                MatchArt.outlined(g, this.font, Component.translatable("match_ui.craftbound.bonus_card"), x + CardRenderer.WIDTH * scale / 2.0F,
                        y - 12, MatchArt.GOLD_LIGHT, 1.0F, true);
            }
        }
        float[] last = mulliganSlot(0, Math.max(1, count));
        int buttonY = Math.round(last[1] + CardRenderer.HEIGHT * last[2] + 14);
        if (choosing) {
            int buttonWidth = 100;
            this.confirmButton = new int[]{this.width / 2 - buttonWidth / 2, buttonY, buttonWidth, 22};
            boolean hover = inside(this.confirmButton, mouseX, mouseY);
            MatchArt.button(g, this.font, Component.translatable("match_ui.craftbound.confirm"), this.confirmButton[0], buttonY, buttonWidth, 22,
                    hover ? 0xFFFFE07A : 0xFFFFD25A, 0xFFE2A52A, 0xFFFFFFFF, 0);
            MatchArt.outlined(g, this.font, Component.translatable("match_ui.craftbound.mulligan_hint"), this.width / 2.0F, buttonY + 30,
                    MatchArt.TEXT_DIM, 1.0F, true);
        } else {
            this.confirmButton = null;
            String dots = ".".repeat((int) (this.now / 400 % 4));
            MatchArt.outlined(g, this.font, Component.translatable("match_ui.craftbound.waiting_for", this.shown.them().name).getString() + dots,
                    this.width / 2.0F, buttonY + 6, MatchArt.TEXT, 1.0F, true);
        }
        Component status = this.shown.them().mulliganDone ? Component.translatable("match_ui.craftbound.opponent_ready", this.shown.them().name)
                : Component.translatable("match_ui.craftbound.opponent_choosing", this.shown.them().name);
        MatchArt.outlined(g, this.font, status, this.width / 2.0F, this.height - 16, this.shown.them().mulliganDone ? 0xFF7DFF5A : MatchArt.TEXT_DIM, 1.0F, true);
    }

    private void gameOver() {
        this.gameOverStart = this.now;
        boolean won = this.shown.winner == this.shown.you;
        boolean draw = this.shown.winner < 0;
        int loser = draw ? -1 : 1 - this.shown.winner;
        if (loser >= 0) {
            float[] at = heroCenter(loser);
            for (int i = 0; i < 4; i++) {
                schedule(i * 180, () -> {
                    this.shakes.put("h:" + loser, this.now);
                    particles(at[0], at[1], 22, 0xFF8A8A8A, 120, 80, 3);
                    MatchSounds.play("block.stone.break", 0.8F, 0.6F);
                });
            }
        }
        schedule(GAME_OVER_DELAY, () -> {
            if (draw) {
                MatchSounds.play("block.note_block.bass", 0.8F, 0.8F);
            } else if (won) {
                MatchSounds.play("ui.toast.challenge_complete", 1.0F, 0.8F);
                MatchSounds.play("entity.player.levelup", 1.0F, 0.6F);
            } else {
                MatchSounds.play("block.beacon.deactivate", 1.0F, 0.8F);
            }
        });
    }

    private boolean gameOverShowing() {
        return this.shown.over && this.gameOverStart >= 0 && this.now - this.gameOverStart >= GAME_OVER_DELAY;
    }

    private void drawGameOver(GuiGraphicsExtractor g) {
        float t = MatchArt.clamp((this.now - this.gameOverStart - GAME_OVER_DELAY) / 400.0F);
        g.fill(0, 0, this.width, this.height, MatchArt.alpha(0xB0000000, t));
        String key = this.shown.winner < 0 ? "match_ui.craftbound.draw" : this.shown.winner == this.shown.you ? "match_ui.craftbound.victory" : "match_ui.craftbound.defeat";
        float scale = 4.0F * MatchArt.pop(t);
        MatchArt.outlined(g, this.font, Component.translatable(key), this.width / 2.0F, this.height / 2.0F - 24 - 4 * scale,
                this.shown.winner == this.shown.you ? MatchArt.GOLD_LIGHT : MatchArt.TEXT, scale, true);
        if (t >= 1) {
            MatchArt.outlined(g, this.font, Component.translatable("match_ui.craftbound.click_to_leave"), this.width / 2.0F, this.height / 2.0F + 20,
                    MatchArt.TEXT, 1.0F, true);
        }
    }

    // Esc: keep playing, concede, or put the match away (right-click the table or the Card Master to come back).
    private void drawMenu(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.fill(0, 0, this.width, this.height, 0xA0000000);
        int width = 140;
        int height = 22;
        int x = this.width / 2 - width / 2;
        int y = this.height / 2 - 44;
        this.menuButtons.clear();
        String[] keys = {"match_ui.craftbound.resume", this.confirmConcede ? "match_ui.craftbound.concede_confirm" : "match_ui.craftbound.concede",
                "match_ui.craftbound.leave_table"};
        for (int i = 0; i < keys.length; i++) {
            int[] button = {x, y + i * (height + 8), width, height};
            this.menuButtons.add(button);
            boolean hover = inside(button, mouseX, mouseY);
            int top = i == 1 ? (hover ? 0xFFC0503A : 0xFF9A3A2A) : (hover ? 0xFFFFE07A : 0xFFFFD25A);
            int bottom = i == 1 ? 0xFF6A2418 : 0xFFE2A52A;
            MatchArt.button(g, this.font, Component.translatable(keys[i]), x, button[1], width, height, top, bottom, 0xFFFFFFFF, 0);
        }
        MatchArt.outlined(g, this.font, Component.translatable("match_ui.craftbound.leave_hint"), this.width / 2.0F, y + 3 * (height + 8) + 4,
                MatchArt.TEXT_DIM, 1.0F, true);
    }

    // --- Rules the screen needs to know (the server checks them again) ----------------------------------------

    private boolean canAct() {
        if (this.awaitingReply && this.now - this.sentAt > 3000) {
            this.awaitingReply = false;
        }
        return this.shown.playing && this.shown.myTurn && !this.shown.over && this.playing == null && this.updates.isEmpty()
                && !this.awaitingReply && !this.menuOpen;
    }

    private boolean enemyHasShield() {
        return this.shown.them().board.stream().anyMatch(mob -> mob.shield);
    }

    private boolean heroTargetable(int side) {
        return this.attacker != null && side != this.shown.you && !enemyHasShield();
    }

    // Whether this mob is a place you could aim what you're doing right now.
    private boolean isValidTarget(Spot spot) {
        if (this.attacker != null) {
            Mob mob = this.shown.sides[spot.side()].mob(spot.mid());
            return spot.side() != this.shown.you && mob != null && (!enemyHasShield() || mob.shield);
        }
        HandCard aiming = this.awaitingTarget != null ? this.awaitingTarget : this.dragging;
        return aiming != null && isValidTargetFor(aiming, spot);
    }

    private boolean isValidTargetFor(HandCard card, Spot spot) {
        return switch (card.target) {
            case "ANY_MOB" -> true;
            case "FRIENDLY_MOB" -> spot.side() == this.shown.you;
            case "ENEMY_MOB" -> spot.side() != this.shown.you;
            default -> false;
        };
    }

    // --- Input ------------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        boolean left = event.button() == InputConstants.MOUSE_BUTTON_LEFT;
        if (this.menuOpen) {
            if (left) {
                for (int i = 0; i < this.menuButtons.size(); i++) {
                    if (!inside(this.menuButtons.get(i), mouseX, mouseY)) {
                        continue;
                    }
                    if (i == 0) {
                        this.menuOpen = false;
                    } else if (i == 1) {
                        if (this.confirmConcede) {
                            send(MatchActionPayload.CONCEDE, 0, null);
                            this.menuOpen = false;
                        }
                        this.confirmConcede = !this.confirmConcede;
                        return true;
                    } else {
                        this.onClose();
                    }
                    this.confirmConcede = false;
                    return true;
                }
            }
            return true;
        }
        if (this.shown.over) {
            if (gameOverShowing() && this.now - this.gameOverStart > GAME_OVER_DELAY + 400) {
                this.onClose();
            }
            return true;
        }
        if (left && inside(this.menuButton, mouseX, mouseY)) {
            this.menuOpen = true;
            return true;
        }
        if (coinShowing()) {
            // Click to skip the toss.
            this.coinStart = this.now - COIN_TIME;
            return true;
        }
        if (!this.shown.playing) {
            return clickMulligan(mouseX, mouseY, left);
        }
        if (!canAct()) {
            return true;
        }
        // The Enderman is on the board waiting for a target: left-click an enemy mob to pick it. Clicking anything
        // else, or right-clicking, cancels and the card goes back to your hand (nothing was played yet).
        if (this.awaitingTarget != null) {
            if (left) {
                for (Spot spot : this.mobSpots) {
                    if (spot.contains(mouseX, mouseY) && isValidTarget(spot)) {
                        this.lastDropX = this.pendingSpot == null ? (float) mouseX : this.pendingSpot.centerX();
                        this.lastDropY = this.pendingSpot == null ? (float) mouseY : this.pendingSpot.centerY();
                        this.hiddenUid = this.awaitingTarget.uid;
                        send(MatchActionPayload.PLAY, this.shown.me().hand.indexOf(this.awaitingTarget), spot);
                        this.awaitingTarget = null;
                        return true;
                    }
                }
            }
            this.awaitingTarget = null;
            return true;
        }
        if (!left) {
            return true;
        }
        if (inside(this.endTurnButton, mouseX, mouseY)) {
            this.endTurnPressed = this.now;
            MatchSounds.play("ui.button.click", 1.0F, 0.6F);
            send(MatchActionPayload.END_TURN, 0, null);
            return true;
        }
        int you = this.shown.you;
        if (inside(this.powerSpots[you], mouseX, mouseY)) {
            if (this.shown.me().powerUsable) {
                send(MatchActionPayload.POWER, 0, null);
            } else {
                MatchSounds.play("entity.villager.no", 1.0F, 0.4F);
            }
            return true;
        }
        // Pick up a card from your hand (the top-most card under the mouse wins).
        for (int i = this.handSpots.size() - 1; i >= 0; i--) {
            Spot spot = this.handSpots.get(i);
            if (spot.contains(mouseX, mouseY) && spot.handIndex() < this.shown.me().hand.size()) {
                HandCard card = this.shown.me().hand.get(spot.handIndex());
                if (card.playable) {
                    this.dragging = card;
                    MatchSounds.play("item.book.page_turn", 1.3F, 0.3F);
                }
                return true;
            }
        }
        // Pick up one of your ready mobs, or your hero holding a tool, to attack with.
        for (Spot spot : this.mobSpots) {
            if (spot.side() == you && spot.contains(mouseX, mouseY)) {
                Mob mob = this.shown.me().mob(spot.mid());
                if (mob != null && mob.ready) {
                    this.attacker = spot;
                }
                return true;
            }
        }
        Spot hero = this.heroSpots[you];
        if (hero != null && hero.contains(mouseX, mouseY) && this.shown.me().heroReady) {
            this.attacker = hero;
        }
        return true;
    }

    private boolean clickMulligan(double mouseX, double mouseY, boolean left) {
        Side me = this.shown.me();
        if (!left || me.mulliganDone || this.mulliganSent) {
            return true;
        }
        for (int i = 0; i < this.mulliganSpots.size(); i++) {
            if (inside(this.mulliganSpots.get(i), mouseX, mouseY)) {
                this.swapPicks[i] = !this.swapPicks[i];
                MatchSounds.play("ui.button.click", this.swapPicks[i] ? 0.8F : 1.2F, 0.4F);
                return true;
            }
        }
        if (inside(this.confirmButton, mouseX, mouseY)) {
            int mask = 0;
            for (int i = 0; i < me.hand.size(); i++) {
                if (this.swapPicks[i]) {
                    mask |= 1 << i;
                }
            }
            this.mulliganSent = true;
            MatchSounds.play("ui.button.click", 1.0F, 0.6F);
            ClientPlayNetworking.send(new MatchActionPayload(MatchActionPayload.MULLIGAN, mask, false, -1, -1));
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (this.dragging != null) {
            HandCard card = this.dragging;
            this.dragging = null;
            if (!canAct()) {
                return true;
            }
            int index = this.shown.me().hand.indexOf(card);
            Spot target = null;
            for (Spot spot : this.mobSpots) {
                if (spot.contains(mouseX, mouseY) && isValidTargetFor(card, spot)) {
                    target = spot;
                }
            }
            boolean overBoard = mouseY < this.handTop - 4 && mouseX >= this.boardLeft && mouseX < this.boardRight;
            boolean isMob = Cards.get(card.id).isMob();
            this.lastDropX = (float) mouseX;
            this.lastDropY = (float) mouseY;
            if (target != null) {
                this.hiddenUid = card.uid;
                send(MatchActionPayload.PLAY, index, target);
            } else if (overBoard && card.target.equals("NONE")) {
                this.hiddenUid = card.uid;
                send(MatchActionPayload.PLAY, index, null);
            } else if (overBoard && isMob) {
                // A mob that picks a target: ask for one if there's anything to pick, otherwise just play it.
                boolean anyTarget = this.mobSpots.stream().anyMatch(spot -> isValidTargetFor(card, spot));
                if (anyTarget) {
                    this.awaitingTarget = card;
                    MatchSounds.summon(card.id);
                } else {
                    this.hiddenUid = card.uid;
                    send(MatchActionPayload.PLAY, index, null);
                }
            }
            return true;
        }
        if (this.attacker != null) {
            Spot from = this.attacker;
            this.attacker = null;
            if (!canAct()) {
                return true;
            }
            for (Spot spot : this.mobSpots) {
                if (spot.side() != this.shown.you && spot.contains(mouseX, mouseY)) {
                    send(MatchActionPayload.ATTACK, from.mid(), spot);
                    return true;
                }
            }
            Spot hero = this.heroSpots[1 - this.shown.you];
            if (hero != null && hero.contains(mouseX, mouseY)) {
                send(MatchActionPayload.ATTACK, from.mid(), hero);
            }
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            // Esc cancels picking a target, then opens or closes the menu (it doesn't leave the match).
            if (this.awaitingTarget != null || this.dragging != null || this.attacker != null) {
                this.awaitingTarget = null;
                this.dragging = null;
                this.attacker = null;
            } else if (this.shown.over) {
                this.onClose();
            } else {
                this.menuOpen = !this.menuOpen;
                this.confirmConcede = false;
            }
            return true;
        }
        return super.keyPressed(event);
    }

    // Sends a move to the server. `target` is a mob or hero spot, or null for none.
    private void send(int action, int index, Spot target) {
        this.awaitingReply = true;
        this.sentAt = this.now;
        if (target == null) {
            ClientPlayNetworking.send(new MatchActionPayload(action, index, false, -1, -1));
        } else {
            ClientPlayNetworking.send(new MatchActionPayload(action, index, target.mid() == Match.HERO, target.side(), target.mid()));
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
