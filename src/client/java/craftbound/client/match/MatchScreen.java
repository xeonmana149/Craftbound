package craftbound.client.match;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import craftbound.card.CardClass;
import craftbound.card.CardDefinition;
import craftbound.card.Cards;
import craftbound.card.Keyword;
import craftbound.client.Tooltips;
import craftbound.client.card.CardRenderer;
import craftbound.game.TrialTraining;
import craftbound.client.AnimationClock;
import craftbound.client.card.CardDragMotion;
import craftbound.client.match.MatchState.Event;
import craftbound.client.match.MatchState.HandCard;
import craftbound.client.match.MatchState.LogLine;
import craftbound.client.match.MatchState.Mob;
import craftbound.client.match.MatchState.Side;
import craftbound.game.HeroPowers;
import craftbound.game.EffectPreview;
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
public class MatchScreen extends Screen implements LocalLayoutPreview {
    // Space between mobs; wide enough that one mob's heart never touches the next one's attack badge.
    private static final int MOB_GAP = 18;
    private static final int ROW_GAP = 16;
    private static final int COIN_TIME = 2600;
    private static final int COIN_LAND = 1600;
    private static final int MY_BANNER = 1300;
    private static final int THEIR_BANNER = 900;
    private static final int GAME_OVER_DELAY = 1120, RESULT_FADE = 680;
    private static final int NAMEPLATE_HEIGHT = 15;
    private static final int HAND_COUNT_HEIGHT = 10;

    // Colours of the table, from the prototype.
    private static final int MORTAR = 0xFF121315;
    private static final int PANEL = 0xFF25211C;
    private static final int PANEL_DARK = 0xFF1B1814;
    private static final int PANEL_EDGE = 0xFF4F3D27;

    private static long nextUid = 1;
    private final Random random = new Random();

    // --- What's shown, and the updates still to animate ------------------------------------------------------
    private MatchState shown;
    private boolean readOnly;
    private Screen previewReturn;
    @Override public Screen layoutPreviewParent(){return previewReturn;}

    public MatchScreen layoutPreview(Screen parent) {
        previewReturn = parent;
        readOnly = true;
        return this;
    }

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
        String sprite;
        int frames = 1;
    }

    // Where a card in a hand is drawn right now; it eases towards its place in the fan each frame.
    private static final class Anim {
        float x, y, scale = 1.0F, rotation;
        boolean placed;
        // A drawn card first stops here, big, so you can see what you got.
        long holdUntil;
        // A cancelled targeting card flies over the hand before becoming hoverable again.
        long returnUntil;
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
    private TrialsScreen trialsJournal;
    private MatchState.CharmSlot hoveredCharm;
    private long gameOverStart = -1;

    void returnToAfterResult(TrialsScreen journal) {
        trialsJournal = journal;
    }

    @Override public void onClose() {
        if (previewReturn != null) { minecraft.gui.setScreen(previewReturn); return; }
        if (trialsJournal == null) super.onClose();
        else minecraft.gui.setScreen(trialsJournal);
    }
    private long endTurnPressed = -1;
    private Identifier lastPlayed;
    private float[] lastBounce;
    private long lastBounceAt = -1;
    private float lastDropX, lastDropY;
    private final Map<Integer, float[]> departed = new java.util.LinkedHashMap<>();
    private final Map<Integer, Long> materializing = new HashMap<>();
    private final Map<Integer, Long> stealingTools = new HashMap<>();
    private float[] playedFrom;
    private int eyeRevealSide = -1, eyeRevealTarget = -1;
    private Identifier arrivingCard;
    private int shatteredHero = -1;
    private long shatterAt;


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
    private final int[][] nameplateSpots = new int[2][];
    private record HistorySpot(int x, int y, int size, LogLine line) {
    }

    private final List<HistorySpot> historySpots = new ArrayList<>();
    private int[] endTurnButton;
    private final List<int[]> menuButtons = new ArrayList<>();

    private float handScale, backScale, deckScale;
    private int handW, handH, handTop, heroSize, toolSize, centerX, oppHeroY, myHeroY;
    private int boardLeft, boardRight, boardTop, boardBottom, middle, mobW, mobH, mobGap, theirRowY, myRowY;
    private int historyIcon, deckW, deckH, deckX, theirDeckY, myDeckY, orbSize;

    // --- What you're doing with the mouse ---------------------------------------------------------------------
    private HandCard dragging;
    private final CardDragMotion dragMotion = new CardDragMotion();
    private Spot attacker;
    // A mob that picks a target (the Enderman) waits here for you to click one.
    private HandCard awaitingTarget;
    private boolean aimingPower;
    private boolean choosingExcavate;
    private boolean aimingExcavate;
    private int[] excavateMineButton, excavateToolButton, excavateCancelButton;
    private Spot pendingSpot;
    private int hoveredHand = -1;
    // Right-click pins one card's inspect open (a hand card by uid, or a mob), so its notes can be hovered.
    private long pinnedHandUid = -1;
    private int pinnedSide = -1, pinnedMid = -1;
    private EffectPreview.Source previewSource;
    // Inline inspection stays on this screen so incoming match updates keep flowing.
    private MatchCardHover cardHover;

    private MatchCardHover cardHover() {
        if (cardHover == null) cardHover = new MatchCardHover();
        return cardHover;
    }

    // Picking a card for a Discard/Steal/Shuffle effect, and the Reveal hand peek.
    // Made on first use: the screen checks build MatchScreen without running its constructor.
    private HandPickOverlay handPicks;
    private HandPickOverlay handPicks() { if (this.handPicks == null) this.handPicks = new HandPickOverlay(); return this.handPicks; }
    private boolean menuOpen;
    private boolean confirmConcede;
    private int pauseRequestedBy=-1;
    private int clockTurn=-1,clockSide=-1,clockTicks=-1;
    private boolean clockHeld;
    private long clockReceived;
    public void clock(craftbound.network.TurnClockPayload p) {
        clockTurn=p.turn();clockSide=p.side();clockTicks=p.ticks();clockHeld=p.held();clockReceived=millis();
    }
    private void readClock(com.google.gson.JsonObject state) {
        clock(new craftbound.network.TurnClockPayload(state.get("turn").getAsInt(),state.get("current").getAsInt(),
            state.has("clockTicks")?state.get("clockTicks").getAsInt():-1,
            state.has("pauseRequest")&&state.get("pauseRequest").getAsInt()>=0));
    }

    public MatchScreen(String json) {
        super(Component.translatable("match_ui.craftbound.title"));
        this.now = AnimationClock.millis();
        // Parse once: readClock and MatchState.parse only read the object.
        var root = JsonParser.parseString(json).getAsJsonObject();
        this.readOnly = root.has("spectator");
        readClock(root);
        List<Event> events = new ArrayList<>();
        MatchState state = MatchState.parse(root, events);
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
        var pauseState=JsonParser.parseString(json).getAsJsonObject();
        readClock(pauseState);
        this.pauseRequestedBy=pauseState.has("pauseRequest")?pauseState.get("pauseRequest").getAsInt():-1;
        List<Event> events = new ArrayList<>();
        MatchState state = MatchState.parse(pauseState, events);
        this.updates.add(new Update(state, events));
        this.awaitingReply = false;
        this.hiddenUid = -1;
    }

    private static long millis() {
        return System.nanoTime() / 1_000_000L;
    }
    protected long animationTime() { return AnimationClock.millis(); }

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
                // Every beat shares its clock with its scheduled impact/removal. Shortening
                // only the queue delay would let later results overtake projectiles or cracks.
                this.beatEnds = this.now + playNext();
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
        this.materializing.entrySet().removeIf(e->e.getValue()<=this.now);
        this.stealingTools.entrySet().removeIf(e->e.getValue()<=this.now);
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
            this.aimingPower = false;
            this.choosingExcavate = false;
            this.aimingExcavate = false;
        }
        if (state.over && !wasOver) {
            gameOver();
        }
    }

    private static boolean sameCard(HandCard a, HandCard b) {
        return a.id.equals(b.id) && a.enchanted == b.enchanted && a.training == b.training && a.brew == b.brew;
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
                // Match the server's turn-start status expiry before queued damage plays.
                for (Mob mob : side.board) {
                    mob.invulnerable = false;
                    // Frozen remains visible through the skipped turn; snapshot/cleanse clears it.
                }
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
            case "IMPALE" -> {
                // A thrown Trident sticks in a mob (amount 1), or comes back out of it (amount 0).
                Mob stuck = e.targetSide() >= 0 && e.targetSide() < 2 ? this.shown.sides[e.targetSide()].mob(e.targetMid()) : null;
                if (e.amount() == 1) {
                    side.heroAttack = Math.max(0, side.heroAttack - (side.tool == null ? 0 : side.tool.attack));
                    side.tool = null;
                    if (stuck != null) stuck.impaled = e.card();
                    MatchSounds.play("item.trident.hit", 1.0F, 0.8F);
                    return 250;
                }
                if (stuck != null) stuck.impaled = null;
                MatchSounds.play("item.trident.return", 1.0F, 0.8F);
                return 300;
            }
            case "CHANNEL" -> {
                float[] at = targetCenter(e);
                ripple(at, 0xFFBFE6FF, 60, 450);
                effect(0, 300, false, (g, t) -> g.fill(Math.round(at[0] - 2), 0, Math.round(at[0] + 2), Math.round(at[1]), MatchArt.alpha(0xFFE8F6FF, 1 - t)));
                particles(at[0], at[1], 24, 0xFFBFE6FF, 90, -40, 2);
                screenShake(3, 300);
                MatchSounds.play("item.trident.thunder", 1.0F, 0.7F);
                return 450;
            }
            case "BREW" -> {
                // A Brewing Stand upgraded one of your potions; the hand card now shows its brewed text.
                if (mine && e.amount() >= 0 && e.amount() < side.hand.size()) side.hand.get(e.amount()).brew = e.extra();
                MatchSounds.play("block.brewing_stand.brew", 1.0F, 0.8F);
                return mine ? 500 : 250;
            }
            case "DRAW", "GIVE" -> {
                boolean draw = e.type().equals("DRAW");
                if (draw) {
                    side.deck = Math.max(0, side.deck - 1);
                }
                float[] from = draw ? deckCenter(s) : effectOrigin(e);
                if (mine) side.handCount++;
                if (mine && e.card() == null) {
                    // A denied hand still animates the public movement of a face-down card.
                    if (this.readOnly) {
                        float[] to = myHandOrigin(side, side.handCount - 1);
                        effect(0,380,false,(g,t)->CardRenderer.drawBack(g,Sleeves.get(side.sleeve),
                                Math.round(lerp(from[0],to[0],MatchArt.easeOut(t))-this.handW/2F),
                                Math.round(lerp(from[1],to[1],MatchArt.easeOut(t))-this.handH/2F),this.handScale));
                    }
                    return 380;
                }
                if (mine) {
                    HandCard card = new HandCard();
                    card.uid = nextUid++;
                    card.id = e.card();
                    card.enchanted = e.enchanted();
                card.training = e.training();
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
                        CardRenderer.drawTrial(g, this.font, id, enchanted, e.training(), Math.round(x), Math.round(y), scale);
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
            case "TNT_DETONATE" -> {
                Mob tnt = side.mob(e.mid());
                if (tnt == null) return 0;
                tnt.detonated = true;
                float[] at = mobCenter(s, tnt.mid);
                float rowY = laneY(1 - s, true) + this.mobH / 2F;
                float left = this.boardLeft + 6, right = this.boardRight - 6;
                effect(0, 140, true, (g,t) -> g.fill(Math.round(at[0]-mobW/2F), Math.round(at[1]-mobH/2F),
                        Math.round(at[0]+mobW/2F), Math.round(at[1]+mobH/2F), MatchArt.alpha(0xFFFFF2CA,t)));
                schedule(140, () -> {
                    if (sound) MatchSounds.play("entity.generic.explode", 1.05F, .72F);
                    screenShake(3, 250);
                    particles(at[0], at[1], 16, 0xFFFFAC46, 65, -24, 2);
                    ripple(at, 0xFFFFC562, 42, 260);
                });
                effect(140, 330, true, (g,t) -> {
                    float travel = Math.min(1F, t * 2.5F);
                    float cx = at[0] + (this.centerX-at[0])*travel, cy = at[1] + (rowY-at[1])*travel;
                    float spread = Math.max(0F, (t-.4F)/.6F);
                    int color = MatchArt.alpha(0xFFFFB24F, 1F-t);
                    g.fill(Math.round(cx+(left-cx)*spread)-2, Math.round(cy)-2,
                            Math.round(cx+(right-cx)*spread)+2, Math.round(cy)+2, color);
                });
                return 470;
            }
            case "EFFECT" -> {
                this.eyeRevealSide = this.eyeRevealTarget = -1;
                Event next = this.playing != null && this.nextEvent < this.playing.events().size()
                        ? this.playing.events().get(this.nextEvent) : null;
                float[] from = effectOrigin(e);
                if (EyeOfEnderAnimation.starts(e, next)) {
                    this.eyeRevealSide = s;
                    this.eyeRevealTarget = next.targetSide();
                    // PLAY already flew the Eye to the hand. This only styles the resulting reveal.
                    return 0;
                }
                ripple(from, 0xFFE6DDBA, 12, 180);
                return 140;
            }
            case "PLAY" -> {
                CardDefinition card = e.card() == null ? null : Cards.get(e.card());
                side.xp = Math.max(0, side.xp - (card == null ? 0 : card.cost()));
                this.lastPlayed = e.card();
                this.arrivingCard = card != null && card.isMob() ? e.card() : null;
                this.playedFrom = mine ? new float[]{this.lastDropX > 0 ? this.lastDropX : this.centerX,
                        this.lastDropY > 0 ? this.lastDropY : this.handTop} : opponentHandOrigin(side, e.amount());
                if (mine && this.readOnly) this.playedFrom = myHandOrigin(side, e.amount());
                if (mine) {
                    side.handCount = Math.max(0, side.handCount - 1);
                    int index = e.amount();
                    if (index >= 0 && index < side.hand.size()) {
                        side.hand.remove(index);
                    }
                    int castDuration = castAnimationDuration(e.card());
                    if (castDuration > 0) {
                        animateCardCast(e, this.playedFrom.clone(), sound);
                        return castDuration;
                    }
                    if (card != null && !card.isMob()) {
                        // The card shrinks into whatever it was used on.
                        float[] from = this.playedFrom.clone();
                        float[] to = e.aimed() ? targetCenter(e) : from;
                        Identifier id = e.card();
                        boolean enchanted = e.enchanted();
                        effect(0, 340, false, (g, t) -> {
                            float scale = this.handScale * 0.85F * (1.0F - MatchArt.easeIn(t) * 0.9F);
                            float x = lerp(from[0], to[0], MatchArt.easeOut(t)) - CardRenderer.WIDTH * scale / 2.0F;
                            float y = lerp(from[1], to[1], MatchArt.easeOut(t)) - CardRenderer.HEIGHT * scale / 2.0F;
                            CardRenderer.drawTrial(g, this.font, id, enchanted, e.training(), Math.round(x), Math.round(y), scale);
                        });
                        if (!card.isTool() && CombatVfx.castFx(e.card()) == craftbound.card.CardCatalog.CastFx.AUTO) {
                            MatchSounds.item(e.card());
                            schedule(300, () -> {
                                particles(to[0], to[1], 7, 0xFFFFF0A0, 40, 0, 2);
                                cardParticles(e.card(), to[0], to[1]);
                            });
                        }
                        return 320;
                    }
                    return 40;
                }
                // Capture the played card before removing it and closing the gap in their hand.
                float[] handOrigin = opponentHandOrigin(side, e.amount());
                if (!side.backs.isEmpty()) {
                    side.backs.remove(Math.max(0, Math.min(side.backs.size() - 1, e.amount())));
                }
                side.handCount = Math.max(0, side.handCount - 1);
                reveal(e.card(), e.enchanted(), e.training(), s);
                if (e.aimed()) {
                    animateOpponentTarget(e, handOrigin);
                }
                int castDuration = castAnimationDuration(e.card());
                if (castDuration > 0) {
                    schedule(750, () -> {
                        animateCardCast(e, handOrigin, sound);
                        // A late frame must not let the result or hand picker overtake the flight.
                        this.beatEnds = Math.max(this.beatEnds, this.now + castDuration);
                    });
                } else if (card != null && !card.isMob() && !card.isTool()
                        && CombatVfx.castFx(e.card()) == craftbound.card.CardCatalog.CastFx.AUTO) {
                    Identifier id = e.card();
                    schedule(750, () -> {
                        float[] at = e.aimed() ? targetCenter(e) : new float[]{this.centerX, this.middle};
                        MatchSounds.item(id); cardParticles(id, at[0], at[1]); ripple(at, 0xFFC995FF, 38, 420);
                    });
                }
                MatchSounds.play("item.book.page_turn", 0.8F, 0.5F);
                return Math.max(1150, 750 + castDuration);
            }
            case "POWER" -> {
                side.xp = Math.max(0, side.xp - this.shown.powerCost);
                side.powerUsed = true;
                this.flashes.put("power:" + s, this.now);
                return animatePower(e, side);
            }
            case "SUMMON" -> {
                Mob mob = new Mob();
                mob.mid = e.mid();
                mob.id = e.card();
                mob.enchanted = e.enchanted();
                mob.training = e.training();
                mob.attack = e.amount();
                mob.health = e.extra();
                mob.maxHealth = e.extra();
                CardDefinition card = Cards.get(e.card());
                if (card != null) {
                    mob.shield = mob.training.keyword() == Keyword.SHIELD || card.keywords().contains(Keyword.SHIELD);
                    mob.ignoreShield = card.keywords().contains(Keyword.IGNORE_SHIELD);
                    mob.ranged = card.keywords().contains(Keyword.RANGED);
                    mob.sprint = mob.training.keyword() == Keyword.SPRINT || card.keywords().contains(Keyword.SPRINT);
                    mob.block = card.keywords().contains(Keyword.BLOCK);
                }
                mob.litTnt = craftbound.card.CardCatalog.hasTntFuse(mob.id);
                if (sound && mob.litTnt) schedule(330, () -> MatchSounds.play("entity.tnt.primed", 1.15F, 0.58F));
                side.board.add(mob);
                this.arrived.put(mob.mid, this.now);
                // It lands in its spot; the others slide over to make room.
                this.mobX.put(mob.mid, laneX(s, side.board.size() - 1, mob.block));
                float[] landing = mobCenter(s, mob.mid);
                float[] origin = e.originSide() >= 0 ? effectOrigin(e)
                        : e.card().equals(this.arrivingCard) && this.playedFrom != null ? this.playedFrom : landing;
                boolean fromHand = e.originSide() < 0 && e.card().equals(this.arrivingCard);
                materialize(mob.copy(), s, origin, landing, fromHand);
                if (fromHand) this.arrivingCard = null;
                var arrival = CombatVfx.arrivalFx(mob.id);
                if (sound && arrival != craftbound.card.CardCatalog.ArrivalFx.PLAIN) {
                    schedule(330, () -> {
                        if (arrival == craftbound.card.CardCatalog.ArrivalFx.BLOCK)
                            MatchSounds.play("block.stone.place", 1F, .65F);
                        else MatchSounds.summon(e.card());
                    });
                }
                switch (arrival) {
                    case EMBERS, FROST, ARCANE -> schedule(300, () -> {
                        int tint = switch (arrival) {
                            case EMBERS -> 0xFFFF963E;
                            case FROST -> 0xFFA1E9FF;
                            default -> 0xFFCBA0FF;
                        };
                        ripple(landing,tint,26,380); particles(landing[0],landing[1],14,tint,32,-15,1);
                    });
                    case LEGENDARY -> legendaryArrival("", landing);
                    case WARDEN -> legendaryArrival("warden", landing);
                    case WITHER -> legendaryArrival("wither", landing);
                    case ENDER_DRAGON -> legendaryArrival("ender_dragon", landing);
                    case AUTO -> {
                        if (card != null && card.rarity() == craftbound.card.CardRarity.LEGENDARY)
                            legendaryArrival(e.card().getPath(), landing);
                    }
                    default -> {}
                }
                if (mob.shield) shieldPulse(landing);
                if (mob.sprint) effect(350,400,false,(g,t)->CombatVfx.streaks(g,landing[0],landing[1],0xFFFFE39B,t));
                int cost = card == null ? 0 : card.cost();
                int mid = mob.mid;
                if (arrival != craftbound.card.CardCatalog.ArrivalFx.PLAIN) schedule(340, () -> {
                    float[] at = mobCenter(s, mid);
                    particles(at[0], at[1] + this.mobH / 2.0F, 8, 0xFFB09070, 40, 60, 2);
                    if (arrival == craftbound.card.CardCatalog.ArrivalFx.AUTO) {
                        cardParticles(e.card(), at[0], at[1]);
                        if (cost >= 7) {
                            screenShake(5, 420);
                            MatchSounds.play("block.anvil.land", 0.6F, 0.35F);
                        }
                    }
                });
                return 500;
            }
            case "EQUIP" -> {
                side.heroAttack = Math.max(0, side.heroAttack - (side.tool == null ? 0 : side.tool.attack)) + e.amount();
                MatchState.Tool tool = new MatchState.Tool();
                tool.id = e.card();
                tool.enchanted = e.enchanted();
                tool.attack = e.amount();
                tool.uses = e.extra();
                CardDefinition card = Cards.get(e.card());
                tool.ranged = card != null && (card.keywords().contains(Keyword.RANGED) || card.keywords().contains(Keyword.THROW));
                side.tool = tool;
                this.flashes.put("tool:" + s, this.now);
                if (e.card() != null) {
                    MatchSounds.equip(e.card());
                }
                return 420;
            }
            case "TOOL_UPGRADE" -> {
                if (side.tool != null) {
                    side.heroAttack = Math.max(0, side.heroAttack + e.amount() - side.tool.attack);
                    side.tool.attack = e.amount(); side.tool.uses = e.extra();
                    this.flashes.put("tool:" + s, this.now);
                    float[] at = toolCenter(s);
                    particles(at[0], at[1], 8, 0xFFFFD775, 24, -20, 1);
                    if (sound) MatchSounds.play("block.anvil.use", 1.2F, 0.4F);
                }
                return 300;
            }
            case "ENCHANT" -> {
                if (side.tool != null && e.card() != null) {
                    side.heroAttack = Math.max(0, side.heroAttack + e.amount() - side.tool.attack);
                    side.tool.enchantments.add(new craftbound.game.Tool.Enchantment(
                            new craftbound.game.CardRef(e.card(), e.enchanted()),
                            e.amount() - side.tool.attack, e.extra() - side.tool.uses));
                    side.tool.attack = e.amount();
                    side.tool.uses = e.extra();
                    this.flashes.put("tool:" + s, this.now);
                    float[] at = toolCenter(s);
                    particles(at[0], at[1], 10, 0xFFBF83FF, 28, -20, 1);
                    ripple(at, 0xFFD7A1FF, 40, 600);
                    effect(0,600,false,(g,t)->{
                        for(int i=0;i<8;i++){double a=i*Math.PI/4+t*5;int xx=Math.round(at[0]+(float)Math.cos(a)*(30-18*t)),yy=Math.round(at[1]+(float)Math.sin(a)*(30-18*t));g.outline(xx-2,yy-2,5,5,MatchArt.alpha(0xFFE6B5FF,1-t));}
                    });
                    if (sound) MatchSounds.play("block.enchantment_table.use", 1.1F, 0.5F);
                }
                return 420;
            }
            case "BREAK" -> {
                float[] at = toolCenter(s);
                // Use the causal source, never lastPlayed: later durability breaks are unrelated.
                Mob thief=e.originSide()>=0 && e.originSide()<2 && e.originSide()!=s && e.originMid()>0
                        ? this.shown.sides[e.originSide()].mob(e.originMid()) : null;
                if (side.tool!=null && thief!=null && CombatVfx.fox(thief.id))
                    return stealTool(thief,e.originSide(),s,sound);
                side.heroAttack = Math.max(0, side.heroAttack - (side.tool == null ? 0 : side.tool.attack));
                side.tool = null;
                if (Cards.LAVA_BUCKET.equals(this.lastPlayed)) {
                    // Melted by a Lava Bucket: it fizzles away in orange.
                    particles(at[0], at[1], 26, 0xFFFF7A1A, 70, -60, 2);
                    particles(at[0], at[1], 10, 0xFF606060, 40, -90, 2);
                    MatchSounds.play("block.lava.extinguish", 1.0F, 0.8F);
                    return 500;
                }
                particles(at[0], at[1], 18, 0xFF9AA1A8, 80, 120, 2);
                MatchSounds.play("entity.item.break", 1.0F, 0.8F);
                return 360;
            }
            case "ATTACK" -> {
                float[] from = actorCenter(s, e.mid());
                float[] to = targetCenter(e);
                Mob mob = e.mid() == Match.HERO ? null : side.mob(e.mid());
                if (e.extra() == MatchEvent.ATTACK_REPAIR) {
                    this.lunge = new Lunge(s, e.mid(), to[0] - from[0], to[1] - from[1], this.now, 440);
                    schedule(175, () -> MatchSounds.play("block.anvil.use", 1.3F, 0.45F));
                    return 175;
                }
                boolean ranged = e.mid() == Match.HERO ? side.tool != null && side.tool.ranged
                        : mob != null && mob.ranged;
                if (ranged && e.mid() == Match.HERO && side.tool != null && Cards.get(side.tool.id) != null
                        && Cards.get(side.tool.id).keywords().contains(Keyword.THROW)) {
                    projectile(CardRenderer.placeholderArt(side.tool.id, Cards.get(side.tool.id)), from, to, 230);
                    MatchSounds.play("item.trident.throw", 1.0F, 0.7F);
                    return 230;
                }
                if (ranged) {
                    boolean shulker = mob != null && mob.id.equals(Cards.SHULKER);
                    boolean snowGolem = mob != null && (mob.id.getPath().equals("snow_golem")
                            || "minecraft:snow_golem".equals(Cards.get(mob.id).artItem()));
                    projectile(new ItemStack(shulker ? Items.SHULKER_SHELL : snowGolem ? Items.SNOWBALL : Items.ARROW), from, to, 230);
                    MatchSounds.play(shulker ? "entity.shulker.shoot" : snowGolem ? "entity.snow_golem.shoot" : "entity.arrow.shoot", 1.0F, 0.7F);
                    return 230;
                }
                this.lunge = new Lunge(s, e.mid(), to[0] - from[0], to[1] - from[1], this.now, 440);
                MatchSounds.play("entity.player.attack.sweep", 1.3F, 0.3F);
                schedule(175, () -> MatchSounds.play("entity.player.attack.strong", 1.0F, 0.7F));
                return 175;
            }
            case "SHOOT" -> {
                int pause = Math.max(0, Math.min(500, e.extra()));
                float[] from = e.mid() == Match.HERO ? toolCenter(s) : actorCenter(s, e.mid());
                float[] to = targetCenter(e);
                // Schedule the visual from this beat's clock, so a slow frame cannot
                // start the arrow late while its damage is already being displayed.
                projectile(new ItemStack(Items.ARROW), from, to, 250, pause);
                schedule(pause, () -> MatchSounds.play(pause > 0 ? "entity.arrow.shoot" : "block.dispenser.launch", 1.0F, 0.6F));
                schedule(pause + 250, () -> MatchSounds.play("entity.arrow.hit", 1.2F, 0.45F));
                return pause + 250;
            }
            case "DAMAGE" -> {
                boolean hero = e.mid() == Match.HERO;
                float[] at = actorCenter(s, e.mid());
                String key = hero ? "h:" + s : "m:" + e.mid();
                if (e.amount() == 0) {
                    shieldPulse(at);
                    floatText(Component.translatable("match_ui.craftbound.blocked").getString(), at[0], at[1] - 8, MatchArt.GOLD_LIGHT, 1.0F);
                    if (sound) {
                        MatchSounds.play("item.shield.block", 1.0F, 0.6F);
                    }
                    return 360;
                }
                if (hero) {
                    side.health -= e.amount();
                    this.flashes.put(key, this.now);
                    if (sound || side.ai) {
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
                ripple(at, 0xFFFFD3A5, Math.min(40, 15 + e.amount()*3), 260);
                splat(at[0], at[1], "-" + e.amount(), 0xFFC81E1E);
                particles(at[0], at[1], 8, 0xFFC81E1E, 70, 80, 2);
                return 430;
            }
            case "HEAL" -> {
                if (e.mid() == Match.HERO) {
                    if (e.extra() > 0) side.maxHealth = e.extra();
                    side.health = Math.min(side.maxHealth, side.health + e.amount());
                }
                else {
                    Mob healed = side.mob(e.mid());
                    if (healed != null) {
                        if (e.extra() > 0) healed.maxHealth = e.extra();
                        healed.health = Math.min(healed.maxHealth, healed.health + e.amount());
                    }
                }
                float[] at = e.mid() == Match.HERO ? heroCenter(s) : mobCenter(s, e.mid());
                ripple(at,0xFF84FFAC,30,550);
                splat(at[0], at[1], "+" + e.amount(), 0xFF2FA83A);
                vanillaParticles(at[0], at[1], 5, "heart", 1, 0xFFFFFFFF, 22, -7);
                if (sound) {
                    MatchSounds.play("block.amethyst_block.chime", 1.4F, 0.6F);
                }
                return 400;
            }
            case "TRANSFORM" -> {
                Mob old = side.mob(e.mid());
                if (old != null) {
                    Mob fresh = new Mob();
                    fresh.mid = old.mid; fresh.id = e.card(); fresh.litTnt = craftbound.card.CardCatalog.hasTntFuse(fresh.id); fresh.enchanted = e.enchanted();
                    fresh.attack = e.amount(); fresh.health = e.extra(); fresh.maxHealth = e.extra();
                    fresh.faceReady = old.faceReady;
                    CardDefinition definition = fresh.card();
                    fresh.block = definition.keywords().contains(Keyword.BLOCK);
                    fresh.shield = definition.keywords().contains(Keyword.SHIELD);
                    fresh.ignoreShield = definition.keywords().contains(Keyword.IGNORE_SHIELD);
                    fresh.ranged = definition.keywords().contains(Keyword.RANGED);
                    fresh.sprint = definition.keywords().contains(Keyword.SPRINT);
                    side.board.set(side.indexOf(old.mid), fresh);
                    float[] at = mobCenter(s, fresh.mid);
                    particles(at[0], at[1], 14, 0xFFD6C1FF, 35, -15, 2);
                }
                return 400;
            }
            case "AURA_ATTACK" -> {
                Mob mob = side.mob(e.mid());
                if (mob != null) mob.attack = e.amount();
                return 0;
            }
            case "AURA_HEALTH" -> {
                Mob mob = side.mob(e.mid());
                if (mob != null) { mob.health = e.amount(); mob.maxHealth = e.extra(); }
                return 0;
            }
            case "BUFF" -> {
                Mob mob = side.mob(e.mid());
                if (mob != null) {
                    mob.attack += e.amount();
                    mob.health += e.extra();
                    mob.maxHealth += e.extra();
                    float[] at = mobCenter(s, e.mid());
                    String buffText = e.amount() < 0 ? e.amount() + " Attack"
                            : e.amount() == 0 ? "+" + e.extra() + " Health"
                            : e.extra() == 0 ? "+" + e.amount() + " Attack" : "+" + e.amount() + "/+" + e.extra();
                    floatText(buffText, at[0], at[1] - 6, e.amount() < 0 ? 0xFFB9CBD8 : 0xFF7DFF5A, 1.2F);
                    particles(at[0], at[1], 12, 0xFF7DFF5A, 50, -40, 2);
                    if (sound) {
                        MatchSounds.play("entity.experience_orb.pickup", 1.3F, 0.5F);
                    }
                }
                return 400;
            }
            case "STATUS" -> {
                if (e.mid() == Match.HERO) {
                    float[] at = heroCenter(s);
                    if (e.amount() == MatchEvent.STATUS_FROZEN) {
                        side.frozen = true;side.heroReady = false;
                        vanillaParticles(at[0], at[1], 10, "spark_", 8, 0xFFBDEBFF, 30, 10);
                        if (sound) MatchSounds.play("block.powder_snow.place", 1.0F, 0.8F);
                    } else if (e.amount() == MatchEvent.STATUS_CLEANSED) {
                        side.frozen = false;
                        side.badOmen = null;
                        side.maxHealth = Match.START_HEALTH;
                        side.health = Math.min(side.health, side.maxHealth);
                        side.heroAttack = side.tool == null ? 0 : side.tool.attack;
                        particles(at[0], at[1], 18, 0xFFF2F5E9, 55, -35, 2);
                        floatText("Cleansed", at[0], at[1] - 6, 0xFFF2F5E9, 1.0F);
                        if (sound) MatchSounds.play("entity.generic.drink", 1.1F, 0.5F);
                    }
                    return 350;
                }
                Mob mob = side.mob(e.mid());
                if (mob == null) {
                    return 0;
                }
                float[] at = mobCenter(s, e.mid());
                int statusColor = switch(e.amount()) {
                    case MatchEvent.STATUS_FROZEN -> 0xFFBDEBFF;
                    case MatchEvent.STATUS_WITHERED, MatchEvent.STATUS_WEAKENED -> 0xFF986EB0;
                    case MatchEvent.STATUS_CLEANSED -> 0xFFF0FFF3;
                    case MatchEvent.STATUS_TOTEM, MatchEvent.STATUS_INVULNERABLE -> 0xFFFFD96C;
                    default -> 0xFFBECEED;
                };
                ripple(at,statusColor,36,500);
                if(e.amount()==MatchEvent.STATUS_CLEANSED) effect(0,450,false,(g,t)->{
                    int yy=Math.round(at[1]+this.mobH*(.5F-t));g.fill(Math.round(at[0]-this.mobW/2F),yy,Math.round(at[0]+this.mobW/2F),yy+3,MatchArt.alpha(0xFFE8FFF3,1-t));
                });
                switch (e.amount()) {
                    case MatchEvent.STATUS_CLEANSED -> {
                        mob.potions = List.of();
                        mob.frozen = false;
                        mob.withered = mob.totem = mob.invulnerable = mob.appliedBenefit = false;
                        var printed = mob.card().keywords();
                        mob.shield = printed.contains(Keyword.SHIELD);
                        mob.sprint = printed.contains(Keyword.SPRINT);
                        mob.ranged = printed.contains(Keyword.RANGED);
                        mob.ignoreShield = printed.contains(Keyword.IGNORE_SHIELD);
                        mob.attackDebuff = 0;
                        particles(at[0], at[1], 18, 0xFFF2F5E9, 55, -35, 2);
                        floatText("Cleansed", at[0], at[1] - 6, 0xFFF2F5E9, 1.0F);
                        MatchSounds.play("entity.generic.drink", 1.1F, 0.5F);
                    }
                    case MatchEvent.STATUS_WEAKENED -> mob.attackDebuff = e.extra();
                    case MatchEvent.STATUS_SHIELD -> { mob.shield = true; shieldPulse(at); }
                    case MatchEvent.STATUS_USES -> {
                        mob.usesLeft = e.extra();
                        floatText(e.extra() == 0 ? "Used up" : e.extra() + (e.extra() == 1 ? " use left" : " uses left"),
                                at[0], at[1] - 6, 0xFFFFD27A, 1.0F);
                    }
                    case MatchEvent.STATUS_NEGATED -> {
                        Keyword lost = Keyword.values()[e.extra()];
                        switch (lost) {
                            case SHIELD -> mob.shield = false;
                            case SPRINT -> mob.sprint = false;
                            case RANGED -> mob.ranged = false;
                            case IGNORE_SHIELD -> mob.ignoreShield = false;
                            default -> { }
                        }
                        floatText("No " + lost.displayName().getString(), at[0], at[1] - 6, 0xFFB9A58C, 1.0F);
                    }
                    case MatchEvent.STATUS_SPRINT -> { mob.sprint = true; mob.ready = e.extra() != 0;
                        effect(0,450,false,(g,t)->CombatVfx.streaks(g,at[0],at[1],0xFFFFE39B,t)); }
                    case MatchEvent.STATUS_RANGED -> mob.ranged = true;
                    case MatchEvent.STATUS_IGNORE_SHIELD -> {
                        mob.ignoreShield = true;
                        ripple(at, 0xFFD9E8FF, 22, 320);
                        floatText("Ignore Shield", at[0], at[1]-8, 0xFFD9E8FF, 1);
                    }
                    case MatchEvent.STATUS_FROZEN -> {
                        mob.frozen = true;
                        vanillaParticles(at[0], at[1], 10, "spark_", 8, 0xFFBDEBFF, 30, 10);
                        MatchSounds.play("block.powder_snow.place", 1.0F, 0.8F);
                    }
                    case MatchEvent.STATUS_WITHERED -> {
                        mob.withered = true;
                        vanillaParticles(at[0], at[1], 10, "generic_", 8, 0xFF555555, 25, -10);
                        MatchSounds.play("entity.wither.ambient", 1.4F, 0.25F);
                    }
                    case MatchEvent.STATUS_TOTEM -> {
                        mob.totem = true;
                        vanillaParticles(at[0], at[1], 10, "glint", 1, 0xFF67E992, 30, -10);
                        particles(at[0], at[1], 8, 0xFFFFD34A, 70, 0, 2);
                        MatchSounds.play("block.beacon.power_select", 1.2F, 0.5F);
                    }
                    case MatchEvent.STATUS_INVULNERABLE -> {
                        mob.invulnerable = true;
                        vanillaParticles(at[0], at[1], 10, "glint", 1, 0xFFFFF27A, 32, 0);
                        MatchSounds.play("entity.player.levelup", 1.6F, 0.4F);
                    }
                    default -> {
                    }
                }
                return 320;
            }
            case "HERO_ATTACK" -> { side.heroAttack = e.amount(); return 0; }
            case "XP" -> {
                side.xp = e.amount();
                side.maxXp = e.extra();
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
                float y = laneY(s, mob.block);
                Mob ghost = mob.copy();
                this.departed.put(mob.mid, at);
                while(this.departed.size()>128) this.departed.remove(this.departed.keySet().iterator().next());
                if (e.type().equals("DEATH") && ghost.litTnt && CombatVfx.automaticDeath(ghost.id)) {
                    side.board.remove(mob);
                    this.mobX.remove(mob.mid);
                    if (ghost.detonated) return 0;
                    if (sound) MatchSounds.play("block.fire.extinguish", 1.6F, .32F);
                    vanillaParticles(at[0], at[1], 8, "generic_", 8, 0xFF92908A, 22, -12);
                    return 220;
                }
                if (e.type().equals("DEATH") && CombatVfx.cracks(ghost.id)) {
                    // Keep its board slot until the final crack stage, then remove it in one small poof.
                    final int cracking = AnimationClock.realDuration(448);
                    effect(0,cracking,false,(g,t)->CombatVfx.cracks(g,x,y,this.mobW,mobH,t));
                    if (sound) {
                        schedule(AnimationClock.realDuration(80),()->MatchSounds.mob(ghost.id,"hurt"));
                        schedule(AnimationClock.realDuration(248),()->MatchSounds.mob(ghost.id,"hurt"));
                    }
                    schedule(cracking,()->{
                        side.board.remove(mob);this.mobX.remove(mob.mid);
                        if(sound) MatchSounds.mob(ghost.id,"death");
                        int first=this.particles.size();
                        vanillaParticles(at[0],at[1],8,"generic_",8,0xFFC8C2B6,24,-3);
                        particles(at[0],at[1],10,0xFF8E887C,30,8,1);
                        for(int i=first;i<this.particles.size();i++)this.particles.get(i).life=(.24F+this.random.nextFloat()*.12F)*.8F;
                    });
                    return cracking+AnimationClock.realDuration(96);
                }
                side.board.remove(mob);
                this.mobX.remove(mob.mid);
                String type = e.type();
                if(type.equals("DEATH") && CombatVfx.splits(ghost.id)) {
                    effect(0,440,false,(g,t)->{
                        for(int half=0;half<2;half++) {
                            float dx=(half==0?-1:1)*22*t;
                            g.pose().pushMatrix();g.pose().translate(dx,-12*(float)Math.sin(t*Math.PI));
                            int xx=Math.round(x+half*this.mobW/2F);g.enableScissor(xx,Math.round(y),xx+this.mobW/2+1,Math.round(y)+this.mobH);
                            drawMob(g,ghost,s,x,y,1,false,-1);g.disableScissor();g.pose().popMatrix();
                        }
                    });
                    MatchSounds.play("entity.slime.squish",.8F,.65F);
                } else ghost(ghost, s, x, y, type);
                if (type.equals("DEATH")) {
                    boolean plainDeath = craftbound.card.CardCatalog.entry(ghost.id) != null
                            && craftbound.card.CardCatalog.entry(ghost.id).deathFx() == craftbound.card.CardCatalog.DeathFx.PLAIN;
                    if (!plainDeath) {
                        particles(at[0], at[1], 22, 0xFF8A8A8A, 80, 90, 2);
                        if (sound) MatchSounds.mob(ghost.id, "death");
                    }
                    if (CombatVfx.explodes(ghost.id)) {
                        ripple(at,0xFFFFD36A,90,600);
                        effect(100,250,false,(g,t)->MatchArt.burst(g,at[0],at[1],Math.round(8+30*t),MatchArt.alpha(0xFFFFE8A8,1-t),0));
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
                return ghost.block && ghost.card().mobType() == craftbound.card.MobType.ORE ? AnimationClock.realDuration(300) : 300;
            }
            case "HAND_REVEAL" -> {
                // One event per card. PLAY completed the Eye throw before any hand result or choice.
                boolean last = e.amount() < 0 || e.amount() >= e.extra() - 1;
                boolean ender = s == this.eyeRevealSide && e.targetSide() == this.eyeRevealTarget;
                if (last) this.eyeRevealSide = this.eyeRevealTarget = -1;
                // Spectator events contain redacted IDs, even when following the caster's perspective.
                if (mine && !this.readOnly) {
                    Side victim = e.aimed() ? this.shown.sides[Math.max(0, Math.min(1, e.targetSide()))] : null;
                    this.handPicks().reveal(victim == null ? "" : victim.name, e.amount(), e.card(),
                            e.enchanted(), e.training(), this.now, ender,
                            victim == null ? null : Sleeves.get(victim.sleeve));
                    if (last && sound) MatchSounds.play(ender ? EyeOfEnderAnimation.REVEAL_SOUND : "item.spyglass.use", 1.0F, 0.8F);
                    return last ? HandPickOverlay.revealDuration(ender) : 0;
                }
                if (!this.readOnly && e.amount() <= 0 && e.targetSide() == this.shown.you) {
                    float[] at = heroCenter(this.shown.you);
                    floatText("Your hand was revealed", at[0], at[1] - this.heroSize * 0.7F, MatchArt.GOLD_LIGHT, 1.0F);
                    if (sound) MatchSounds.play(ender ? EyeOfEnderAnimation.REVEAL_SOUND : "item.spyglass.use", 1.0F, 0.6F);
                }
                return last ? 600 : 0;
            }
            case "HAND_DISCARD" -> {
                // side's card leaves their hand face-up and burns in the middle of the table.
                float[] from = takeFromHand(s, e.amount());
                Identifier id = e.card();
                boolean enchanted = e.enchanted();
                TrialTraining training = e.training();
                effect(0, 900, true, (g, t) -> {
                    float fly = MatchArt.easeOut(Math.min(1.0F, t / 0.3F));
                    float scale = this.handScale * (0.6F + 0.4F * fly) * (t > 0.8F ? 1.0F - (t - 0.8F) / 0.2F : 1.0F);
                    float x = lerp(from[0], this.centerX, fly) - CardRenderer.WIDTH * scale / 2.0F;
                    float y = lerp(from[1], this.middle, fly) - CardRenderer.HEIGHT * scale / 2.0F;
                    if (id != null) CardRenderer.drawTrial(g, this.font, id, enchanted, training, Math.round(x), Math.round(y), scale);
                    if (t > 0.35F) {
                        float burn = MatchArt.clamp((t - 0.35F) / 0.45F);
                        int w = Math.round(CardRenderer.WIDTH * scale), h = Math.round(CardRenderer.HEIGHT * scale);
                        int top = Math.round(y + h * (1.0F - burn));
                        g.fill(Math.round(x), top, Math.round(x) + w, Math.round(y) + h, 0xD0301008);
                        g.fill(Math.round(x), top - 2, Math.round(x) + w, top, 0xFFFF8A20);
                    }
                    MatchArt.outlined(g, this.font, "Discarded", this.centerX, y - 12, 0xFFFF8A60, 1.0F, true);
                });
                schedule(320, () -> {
                    MatchSounds.play("block.fire.extinguish", 1.0F, 0.6F);
                    particles(this.centerX, this.middle, 24, 0xFFFF8A20, 60, -40, 2);
                });
                return 900;
            }
            case "HAND_STEAL" -> {
                // side takes a card from the other hand. You see its face if it's yours now (or was yours).
                int victim = Math.max(0, Math.min(1, e.targetSide()));
                float[] from = takeFromHand(victim, e.amount());
                if (sound) MatchSounds.play("entity.item.pickup", 0.8F, 0.7F);
                if (e.extra() != 1) {
                    floatText("Hand full: the stolen card is lost", from[0], from[1], 0xFFFF8A60, 1.0F);
                    return 600;
                }
                side.handCount++;
                if (mine && e.card() != null) {
                    HandCard card = new HandCard();
                    card.uid = nextUid++;
                    card.id = e.card();
                    card.enchanted = e.enchanted();
                    card.training = e.training();
                    side.hand.add(card);
                    Anim anim = new Anim();
                    anim.placed = true;
                    anim.scale = 0.6F;
                    anim.x = from[0] - this.handW * anim.scale / 2.0F;
                    anim.y = from[1] - this.handH * anim.scale / 2.0F;
                    anim.holdUntil = this.now + 420;
                    anim.holdScale = 1.55F;
                    anim.holdX = this.boardRight - this.handW * 1.55F - 12;
                    anim.holdY = this.middle - this.handH * 1.55F / 2.0F;
                    this.anims.put(card.uid, anim);
                    return 620;
                }
                if (!mine) {
                    long uid = nextUid++;
                    side.backs.add(uid);
                    Anim anim = new Anim();
                    anim.placed = true;
                    anim.scale = this.handScale * 0.6F / this.backScale;
                    anim.x = from[0] - CardRenderer.WIDTH * this.backScale * anim.scale / 2.0F;
                    anim.y = from[1] - CardRenderer.HEIGHT * this.backScale * anim.scale / 2.0F;
                    this.anims.put(uid, anim);
                }
                return 520;
            }
            case "HAND_SHUFFLE" -> {
                // side's card flies from their hand back into their deck.
                float[] from = takeFromHand(s, e.amount());
                float[] to = deckCenter(s);
                Identifier id = e.card();
                boolean enchanted = e.enchanted();
                TrialTraining training = e.training();
                Sleeves.Design back = Sleeves.get(side.sleeve);
                side.deck++;
                effect(0, 520, true, (g, t) -> {
                    float p = MatchArt.easeOut(t);
                    float scale = this.handScale * 0.6F * (1.0F - 0.45F * p);
                    int x = Math.round(lerp(from[0], to[0], p) - CardRenderer.WIDTH * scale / 2.0F);
                    int y = Math.round(lerp(from[1], to[1], p) - CardRenderer.HEIGHT * scale / 2.0F);
                    if (id != null && t < 0.5F) CardRenderer.drawTrial(g, this.font, id, enchanted, training, x, y, scale);
                    else CardRenderer.drawBack(g, back, x, y, scale);
                });
                if (sound) MatchSounds.play("item.book.page_turn", 0.7F, 0.6F);
                return 560;
            }
            default -> {
                return 0;
            }
        }
    }

    // Removes the card at index from side's hand (a face-down back for the other player) and returns
    // where it was, for the card's animation to start from.
    private float[] takeFromHand(int s, int index) {
        Side side = this.shown.sides[s];
        float[] origin;
        if (s == this.shown.you) {
            origin = myHandOrigin(side, index);
            if (index >= 0 && index < side.hand.size()) side.hand.remove(index);
        } else {
            origin = opponentHandOrigin(side, index);
            if (!side.backs.isEmpty()) side.backs.remove(Math.max(0, Math.min(side.backs.size() - 1, index)));
        }
        side.handCount = Math.max(0, side.handCount - 1);
        return origin;
    }

    // --- Effects ----------------------------------------------------------------------------------------------

    private float[] effectOrigin(Event e) {
        if(e.originSide()>=0) {
            if(e.originMid()==Match.HERO) return heroCenter(e.originSide());
            if(this.shown.sides[e.originSide()].mob(e.originMid())!=null) return mobCenter(e.originSide(),e.originMid());
            if(this.departed.containsKey(e.originMid())) return this.departed.get(e.originMid());
        }
        return giveOrigin();
    }
    private void ripple(float[] at,int color,int radius,int duration) {
        if(this.effects.size()>64) return;
        effect(0,duration,false,(g,t)->CombatVfx.ring(g,at[0],at[1],5+radius*MatchArt.easeOut(t),color,t));
    }
    private void throwEgg(float[] from, float[] to, boolean sound) {
        effect(0, EggCastAnimation.FLIGHT, true,
                (g,t) -> EggCastAnimation.fly(g, from[0], from[1], to[0], to[1], t));
        effect(EggCastAnimation.FLIGHT, EggCastAnimation.SPLAT, true,
                (g,t) -> EggCastAnimation.splat(g, to[0], to[1], t));
        if (sound) {
            EggCastAnimation.launchSound();
            schedule(EggCastAnimation.FLIGHT, EggCastAnimation::impactSound);
        }
    }
    // One bounded signature per power. The event queue holds the result until the gesture lands.
    private int animatePower(Event e, Side side) {
        int s = e.side();
        float[] from = powerCenter(s), hero = heroCenter(s);
        int n = side.board.size();
        boolean block = side.power == HeroPowers.Power.BUILD || side.power == HeroPowers.Power.SOW;
        float[] landing = {laneX(s, n, block) + mobW/2F, laneY(s, block) + mobH/2F};
        float[] target = e.targetSide() >= 0 ? targetCenter(e) : landing;
        switch (side.power) {
            case CRAFT -> {
                powerArc(new ItemStack(Items.WOODEN_SWORD), from, toolCenter(s), 330, 24, 1);
                MatchSounds.play("block.wood.place", 1.2F, .55F);
                schedule(310, () -> MatchSounds.play("entity.item.pickup", 1.1F, .4F));
                return 350;
            }
            case FISH -> {
                MatchSounds.play("entity.fishing_bobber.throw",1.15F,.65F);
                effect(0,650,false,(g,t)->{
                    float travel=Math.min(1,t/.48F),x=lerp(from[0],landing[0],travel);
                    float y=lerp(from[1],landing[1],travel)-30*(float)Math.sin(travel*Math.PI);
                    if(t>.48F)y+=2*(float)Math.sin(t*24);
                    for(int i=0;i<28;i++){float q=i/27F;int px=Math.round(lerp(from[0],x,q)),py=Math.round(lerp(from[1],y,q)+6*(float)Math.sin(q*Math.PI));g.fill(px,py,px+1,py+1,0xFFD5D7C9);}
                    int bx=Math.round(x),by=Math.round(y);g.fill(bx-3,by-4,bx+3,by+4,0xFF282A2B);g.fill(bx-2,by-3,bx+2,by,0xFFF9EED6);g.fill(bx-2,by,bx+2,by+3,0xFFE85342);
                });
                schedule(310,()->{ripple(landing,0xFF69D7EB,27,320);MatchSounds.play("entity.fishing_bobber.splash",1.3F,.55F);});
                schedule(560,()->{MatchSounds.play("entity.fishing_bobber.retrieve",1.2F,.6F);vanillaParticles(landing[0],landing[1],10,"generic_",8,0xFF82E7FF,32,-30);});
                return 650;
            }
            case THROW_POTION -> {
                powerArc(new ItemStack(Items.SPLASH_POTION),from,target,360,42,5);
                MatchSounds.play("entity.splash_potion.throw",1,.65F);
                schedule(360,()->{MatchSounds.play("entity.splash_potion.break",1,.65F);ripple(target,0xFFC17CFF,24,300);vanillaParticles(target[0],target[1],14,"spell_",8,0xFFCC79EE,36,-10);});
                return 380;
            }
            case BUILD -> {
                MatchSounds.play("block.stone.place",.85F,.6F);
                effect(0,420,false,(g,t)->{
                    for(int i=0;i<6;i++){float start=i*.07F,p=MatchArt.clamp((t-start)/.5F);int x=Math.round(landing[0]-15+(i%3)*10),y=Math.round(landing[1]+8-(i/3)*10-20*(1-MatchArt.easeOut(p)));
                        if(t>=start){g.fill(x,y,x+9,y+8,0xFF858D90);g.fill(x,y,x+9,y+2,0xFFBBC2C4);}}
                });
                return 430;
            }
            case SOW -> {
                powerArc(new ItemStack(Items.WHEAT_SEEDS),from,landing,340,24,2);
                MatchSounds.play("item.hoe.till",1.2F,.6F);
                schedule(320,()->{ripple(landing,0xFFA4D967,23,380);vanillaParticles(landing[0],landing[1],12,"glint",1,0xFFB1E970,25,-30);MatchSounds.play("block.grass.place",1.3F,.5F);});return 380;
            }
            case FEED -> {
                powerArc(new ItemStack(Items.COOKED_PORKCHOP),from,target,320,28,0);
                MatchSounds.play("entity.generic.eat",1.2F,.5F);
                schedule(310,()->{ripple(target,0xFFFF99A8,25,380);vanillaParticles(target[0],target[1],8,"heart",1,0xFFFFFFFF,18,-35);MatchSounds.play("entity.player.burp",1.3F,.25F);});return 350;
            }
            case STRIKE -> {
                effect(0,430,false,(g,t)->CombatVfx.streaks(g,hero[0],hero[1],0xFFFFBA72,t));
                ripple(hero,0xFFFF9E63,28,430);floatText("+2",hero[0],hero[1]-18,0xFFFFD582,1.15F);
                MatchSounds.play("entity.player.attack.sweep",.8F,.6F);
                schedule(170,()->MatchSounds.play("entity.player.attack.strong",1.15F,.5F));return 330;
            }
            case PROSPECT -> {
                effect(0,380,false,(g,t)->{g.pose().pushMatrix();g.pose().translate(from[0],from[1]-10);g.pose().rotate(-.6F+1.7F*(float)Math.sin(t*Math.PI));drawItem(g,new ItemStack(Items.IRON_PICKAXE),0,0,1.7F);g.pose().popMatrix();});
                schedule(210,()->{MatchSounds.play("block.stone.break",1.3F,.65F);particles(from[0],from[1],12,0xFFCEAA8C,35,60,2);});
                schedule(330,()->{powerArc(new ItemStack(Items.RAW_IRON),from,s == this.shown.you ? new float[]{this.centerX,this.handTop+12} : opponentHandOrigin(side,side.handCount),250,18,1);MatchSounds.play("entity.item.pickup",1.15F,.5F);});return 580;
            }
            case ACTIVATE -> {
                MatchSounds.play("block.lever.click",.8F,.65F);
                effect(0,330,false,(g,t)->{for(int i=0;i<34*t;i++){float q=i/34F;int x=Math.round(lerp(from[0],target[0],q)),y=Math.round(lerp(from[1],target[1],q)+(i%6<3?3:-3));g.fill(x-1,y-1,x+2,y+2,i>34*t-5?0xFFFFCF91:0xFFCF3828);}});
                schedule(300,()->{ripple(target,0xFFFF5F36,25,330);particles(target[0],target[1],10,0xFFFF6655,30,-20,1);MatchSounds.play("block.piston.extend",1.3F,.45F);});return 360;
            }
            case CALL_PUP -> {
                powerArc(new ItemStack(Items.BONE),from,landing,310,25,3);
                MatchSounds.play("entity.wolf.ambient",1.6F,.45F);
                schedule(270,()->{for(int i=0;i<4;i++){final int step=i;schedule(step*40,()->{float x=landing[0]-24+step*8,y=landing[1]+(step%2==0?5:-4);ripple(new float[]{x,y},0xFFFFD9AF,5,180);});}});
                schedule(390,()->MatchSounds.play("block.grass.step",1.3F,.4F));return 440;
            }
            default -> {ripple(from,MatchArt.GOLD_LIGHT,20,300);MatchSounds.play("block.amethyst_block.chime",1.2F,.5F);return 300;}
        }
    }

    private int stealTool(Mob thief,int thiefSide,int victimSide,boolean sound) {
        Side victim=this.shown.sides[victimSide];
        var stolen=victim.tool;
        ItemStack artwork=CardRenderer.placeholderArt(stolen.id,Cards.get(stolen.id));
        if(stolen.enchanted || !stolen.enchantments.isEmpty())
            artwork.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE,true);
        float[] home=actorCenter(thiefSide,thief.mid),target=toolCenter(victimSide);
        Mob ghost=thief.copy();
        this.stealingTools.put(thief.mid,this.now+ToolTheftMotion.DURATION);
        if(sound)MatchSounds.play("entity.fox.ambient",1.35F,.45F);
        effect(0,ToolTheftMotion.DURATION,true,(g,t)->{
            var frame=ToolTheftMotion.sample(t);
            float x=lerp(home[0],target[0],frame.travel()),y=lerp(home[1],target[1],frame.travel())-frame.lift();
            float tilt=(target[0]>=home[0]?1:-1)*.12F*(float)Math.sin(frame.travel()*Math.PI);
            g.pose().pushMatrix();g.pose().translate(x,y);g.pose().rotate(tilt);
            drawMob(g,ghost,thiefSide,-this.mobW/2F,-this.mobH/2F,1,false,-1);
            if(frame.carrying() && frame.toolScale()>0){
                g.pose().pushMatrix();g.pose().translate(this.mobW*.28F,this.mobH*.14F);
                g.pose().rotate(-.45F);drawItem(g,artwork,0,0,1.5F*frame.toolScale());g.pose().popMatrix();
            }
            g.pose().popMatrix();
        });
        schedule(ToolTheftMotion.GRAB,()->{
            if(victim.tool==stolen){victim.heroAttack=Math.max(0,victim.heroAttack-stolen.attack);victim.tool=null;}
            particles(target[0],target[1],8,0xFFFFD58A,25,-15,1);
            if(sound)MatchSounds.play("entity.item.pickup",1.4F,.55F);
        });
        schedule(ToolTheftMotion.HOME,()->{
            particles(home[0],home[1],6,0xFFE8BA82,18,-12,1);
            if(sound)MatchSounds.play("entity.fox.eat",1.3F,.35F);
        });
        return ToolTheftMotion.DURATION;
    }

    private void powerArc(ItemStack item,float[] from,float[] to,int duration,float height,float spin) {
        effect(0,duration,false,(g,t)->{float x=lerp(from[0],to[0],t),y=lerp(from[1],to[1],t)-height*(float)Math.sin(t*Math.PI);
            g.pose().pushMatrix();g.pose().translate(x,y);g.pose().rotate(t*spin);drawItem(g,item,0,0,1.4F);g.pose().popMatrix();});
    }

    private void shieldPulse(float[] at) {
        effect(0,420,false,(g,t)->{int w=Math.round(this.mobW+10+18*t),h=Math.round(this.mobH+14+18*t);
            MatchArt.shieldOutline(g,Math.round(at[0]-w/2F),Math.round(at[1]-h/2F),w,h,0,MatchArt.alpha(0xFFDCEEFF,1-t),2);});
    }
    /** Chosen visuals run on PLAY, even when the effect creates a choice instead of an EFFECT event. */
    static int castAnimationDuration(Identifier id) {
        if (CombatVfx.enderEye(id)) return EyeOfEnderAnimation.DURATION_MS;
        if (CombatVfx.egg(id)) return EggCastAnimation.FLIGHT;
        if (CombatVfx.potion(id)) return 400;
        if (CombatVfx.arrow(id)) return 360;
        // Automatic Fox theft waits for the actual TOOL_BREAK event and carries that tool home.
        if (CombatVfx.castFx(id) == craftbound.card.CardCatalog.CastFx.FOX) return ToolTheftMotion.DURATION;
        return 0;
    }

    private void animateCardCast(Event e, float[] from, boolean sound) {
        float[] target = e.aimed() ? targetCenter(e) : new float[]{this.centerX, this.middle};
        if (CombatVfx.enderEye(e.card())) {
            throwEnderEye(from, handCenter(1 - e.side()), sound);
        } else if (CombatVfx.egg(e.card())) {
            throwEgg(heroCenter(e.side()), target, sound);
        } else if (CombatVfx.potion(e.card())) {
            throwPotion(e.card(), from, target, 400);
        } else if (CombatVfx.arrow(e.card())) {
            projectile(new ItemStack(Items.ARROW), from, target, 360);
            if (sound) MatchSounds.play("entity.arrow.shoot", 1.2F, .65F);
            schedule(360, () -> {
                ripple(target, 0xFFFFD49A, 12, 160);
                if (sound) MatchSounds.play("entity.arrow.hit", 1.15F, .5F);
            });
        } else if (CombatVfx.castFx(e.card()) == craftbound.card.CardCatalog.CastFx.FOX) {
            // A reusable cosmetic dash. Removing or stealing a tool still requires a real card effect.
            ItemStack art = CardRenderer.placeholderArt(e.card(), Cards.get(e.card()));
            float[] to = e.aimed() ? target : toolCenter(1 - e.side());
            if (sound) MatchSounds.play("entity.fox.ambient", 1.35F, .45F);
            effect(0, ToolTheftMotion.DURATION, true, (g,t) -> {
                var frame = ToolTheftMotion.sample(t);
                drawItem(g, art, lerp(from[0],to[0],frame.travel()),
                        lerp(from[1],to[1],frame.travel())-frame.lift(), 1.7F);
            });
            schedule(ToolTheftMotion.GRAB, () -> particles(to[0],to[1],8,0xFFE8BA82,22,-12,1));
        }
    }

    private void throwPotion(Identifier id,float[] from,float[] to,int duration) {
        CardDefinition card=Cards.get(id);ItemStack art=CardRenderer.placeholderArt(id,card);
        ItemStack item = art.is(Items.POTION) || art.is(Items.SPLASH_POTION) || art.is(Items.LINGERING_POTION)
                ? art : new ItemStack(Items.SPLASH_POTION);
        int color=id.getPath().contains("heal")?0xFFFF7BB4:id.getPath().contains("slow")?0xFFB7E4FF:0xFFC17CFF;
        MatchSounds.play("entity.splash_potion.throw",1,.65F);
        effect(0,duration,false,(g,t)->{float x=lerp(from[0],to[0],t),y=lerp(from[1],to[1],t)-55*(float)Math.sin(t*Math.PI);
            g.pose().pushMatrix();g.pose().translate(x,y);g.pose().rotate(t*5);drawItem(g,item,0,0,1.4F);g.pose().popMatrix();});
        schedule(duration,()->{MatchSounds.play("entity.splash_potion.break",1,.75F);ripple(to,color,48,450);
            vanillaParticles(to[0],to[1],18,"spell_",8,color,60,-15);particles(to[0],to[1],12,0xFFD9F3FF,85,110,1);});
    }
    private void materialize(Mob mob,int side,float[] from,float[] to,boolean fromHand) {
        this.materializing.put(mob.mid,this.now+500);
        effect(0,500,false,(g,t)->{
            float travel=MatchArt.easeOut(Math.min(1,t/.7F));float cx=lerp(from[0],to[0],travel),cy=lerp(from[1],to[1],travel);
            if(fromHand && t<.58F){float scale=lerp(this.handScale,this.mobW/(float)CardRenderer.WIDTH,travel);
                // Fold the rules panel away while retaining the recognisable card face.
                float fold=1-MatchArt.clamp((t-.28F)/.3F)*.45F;
                g.pose().pushMatrix();g.pose().translate(cx,cy);g.pose().scale(1,fold);
                CardRenderer.drawTrial(g,this.font,mob.id,mob.enchanted,mob.training,Math.round(-CardRenderer.WIDTH*scale/2),Math.round(-CardRenderer.HEIGHT*scale/2),scale);g.pose().popMatrix();
            }else{
                float land=MatchArt.clamp((t-.58F)/.42F),scale=fromHand?lerp(.8F,1,MatchArt.easeOut(land)):lerp(.3F,1,MatchArt.easeOut(t));
                boolean blockLanding = CombatVfx.arrivalFx(mob.id) == craftbound.card.CardCatalog.ArrivalFx.BLOCK
                        || mob.block && CombatVfx.arrivalFx(mob.id) != craftbound.card.CardCatalog.ArrivalFx.PLAIN;
                float y=cy-mobH/2F-(blockLanding?12*(1-land):0);
                drawMob(g,mob,side,cx-this.mobW/2F,y,scale,false,-1);
                if(land<.3F && fromHand)g.fill(Math.round(cx-this.mobW/2F),Math.round(y),Math.round(cx+this.mobW/2F),Math.round(y)+mobH,MatchArt.alpha(0xFFFFF3CF,(.3F-land)*1.8F));
            }
        });
    }
    private void legendaryArrival(String signature,float[] at) {
        // Dedicated signatures can be extended here without changing combat or event timing.
        int color=switch(signature){case "warden"->0xFF4DD9D2;case "wither"->0xFFAA8CC8;case "ender_dragon"->0xFFD280FF;default->0xFFFFD575;};
        for(int i=0;i<3;i++){final int n=i;schedule(300+i*140,()->ripple(at,color,45+n*20,650));}
        if(signature.equals("warden"))schedule(330,()->MatchSounds.play("entity.warden.sonic_boom",1,.4F));
        if(signature.equals("ender_dragon"))schedule(330,()->vanillaParticles(at[0],at[1],22,"generic_",8,color,70,-20));
    }
    private void portraitFragments(float x,float y,int size,int delay,int duration,Drawer face) {
        var shards=PortraitFragments.create(this.random.nextLong());
        float radius=Math.clamp(size*1.65F,42F,70F);
        effect(delay,duration,false,(g,t)->{
            for(var shard:shards){
                float age=t/shard.lifetime();
                if(age>=1)continue;
                float travel=1-(float)Math.pow(1-age,3);
                float shrink=1-MatchArt.easeOut(MatchArt.clamp((age-.55F)/.45F));
                float cx=x+shard.x()*size,cy=y+shard.y()*size;
                float dx=Math.clamp(cx+shard.dx()*radius,6F,this.width-6F)-cx;
                float dy=Math.clamp(cy+shard.dy()*radius,6F,this.height-6F)-cy;
                g.pose().pushMatrix();g.pose().translate(cx+dx*travel,cy+dy*travel);
                g.pose().scale(shrink,shrink);g.pose().translate(-cx,-cy);
                for(var patch:shard.patches()){
                    int left=Math.round(x+patch.x()*size/(float)PortraitFragments.GRID);
                    int top=Math.round(y+patch.y()*size/(float)PortraitFragments.GRID);
                    int right=Math.round(x+(patch.x()+patch.width())*size/(float)PortraitFragments.GRID);
                    int bottom=Math.round(y+(patch.y()+patch.height())*size/(float)PortraitFragments.GRID);
                    g.enableScissor(left,top,right,bottom);face.draw(g,t);g.disableScissor();
                }
                g.pose().popMatrix();
            }
        });
    }

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
        String setting = this.minecraft.options.particles().get().name();
        if (setting.equals("MINIMAL")) return;
        if (setting.equals("DECREASED")) count = Math.max(1, count / 2);
        count = Math.min(count, Math.max(0, 240 - this.particles.size()));
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

    private void vanillaParticles(float x, float y, int count, String sprite, int frames, int color, float speed, float gravity) {
        int start = this.particles.size();
        particles(x, y, count, color, speed, gravity, 4);
        for (int i = start; i < this.particles.size(); i++) {
            Particle particle = this.particles.get(i);
            particle.sprite = sprite;
            particle.frames = frames;
            particle.size = 4 + this.random.nextFloat() * 3;
        }
    }

    private void cardParticles(Identifier id, float x, float y) {
        if (id == null) return;
        CardDefinition card = Cards.get(id);
        if (card == null) return;
        String name = id.getPath();
        if (name.contains("blaze") || name.contains("flame") || name.contains("lava") || name.contains("furnace")) {
            vanillaParticles(x, y, 10, "flame", 1, 0xFFFFFFFF, 28, -8);
            vanillaParticles(x, y, 5, "generic_", 8, 0xFF9A9A9A, 22, -12);
        } else if (card.cardClass() == CardClass.ALCHEMIST) {
            int color = name.contains("healing") ? 0xFFFF8CC9 : name.contains("slowness") ? 0xFF9EDFFF
                    : name.contains("decay") ? 0xFF8EB951 : 0xFFC17CFF;
            vanillaParticles(x, y, 12, "spell_", 8, color, 32, -8);
        } else if (card.mobType() == craftbound.card.MobType.AQUATIC) {
            vanillaParticles(x, y, 10, "splash_", 4, 0xFF84D9FF, 32, 15);
            vanillaParticles(x, y, 4, "bubble", 1, 0xFFFFFFFF, 20, -10);
        } else if (card.mobType() == craftbound.card.MobType.ENDER) {
            vanillaParticles(x, y, 12, "generic_", 8, 0xFFC17CFF, 28, -12);
        } else if (card.kind() == craftbound.card.CardKind.ENCHANTMENT) {
            vanillaParticles(x, y, 12, "enchanted_hit", 1, 0xFFC8A5FF, 28, -10);
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
            if (p.sprite == null) {
                g.fill(x, y, x + Math.round(p.size), y + Math.round(p.size), MatchArt.alpha(p.color, alpha));
            } else {
                String frame = p.frames == 1 ? "" : String.valueOf(Math.min(p.frames - 1, (int) (p.age / p.life * p.frames)));
                Identifier texture = Identifier.withDefaultNamespace("textures/particle/" + p.sprite + frame + ".png");
                int size = Math.round(p.size);
                g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, texture, x - size / 2, y - size / 2,
                        0.0F, 0.0F, size, size, 8, 8, 8, 8, MatchArt.alpha(p.color, alpha * 0.85F));
            }
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
        projectile(item, from, to, duration, 0);
    }

    private void projectile(ItemStack item, float[] from, float[] to, int duration, int delay) {
        float angle = (float) Math.atan2(to[1] - from[1], to[0] - from[0]);
        effect(delay, duration, false, (g, t) -> {
            float x = lerp(from[0], to[0], t);
            float y = lerp(from[1], to[1], t);
            for(int i=1;i<=5;i++){
                float tail=Math.max(0,t-i*.025F);int tx=Math.round(lerp(from[0],to[0],tail)),ty=Math.round(lerp(from[1],to[1],tail));
                g.fill(tx-1,ty-1,tx+2,ty+2,MatchArt.alpha(0xFFFFECC3,(6-i)/8F));
            }
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
        boolean mined = how.equals("REMOVE") && mob.block && mob.card().mobType() == craftbound.card.MobType.ORE;
        effect(0, mined ? AnimationClock.realDuration(480) : 480, false, (g, t) -> {
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
            int h = Math.round(mobH * scale);
            int left = Math.round(x + (this.mobW - w) / 2.0F);
            int top = Math.round(y + dy + (mobH - h) / 2.0F);
            int fade = how.equals("DEATH") ? 0x9A1A1A1A : 0x70FFFFFF;
            g.fill(left, top, left + w, top + h, MatchArt.alpha(fade, MatchArt.clamp(t * 2)));
        });
    }

    // The other player's card, shown big beside the board for a moment.
    private void reveal(Identifier id, boolean enchanted, TrialTraining training, int side) {
        if (id == null) {
            return;
        }
        String who = this.shown.sides[side].name;
        // Center the reveal over the left quarter of the play area, clear of the history strip.
        float scale = Math.min(1.0F, this.height * 0.5F / CardRenderer.HEIGHT) * 0.9F;
        int w = Math.round(CardRenderer.WIDTH * scale);
        int h = Math.round(CardRenderer.HEIGHT * scale);
        int x = Math.max(8, Math.min(Math.round(this.width * 0.275F - w / 2.0F), this.width - w - 8));
        int y = this.middle - h / 2;
        effect(0, 1400, true, (g, t) -> {
            float size = t < 0.12F ? MatchArt.pop(t / 0.12F) : t > 0.88F ? 1.0F - MatchArt.easeIn((t - 0.88F) / 0.12F) : 1.0F;
            g.pose().pushMatrix();
            g.pose().translate(x + w / 2.0F, y + h / 2.0F);
            g.pose().scale(size, size);
            g.pose().translate(-w / 2.0F, -h / 2.0F);
            CardRenderer.drawTrial(g, this.font, id, enchanted, training, 0, 0, scale);
            g.pose().popMatrix();
            if (size > 0.9F) {
                MatchArt.outlined(g, this.font, Component.translatable("match_ui.craftbound.plays", who.toUpperCase(Locale.ROOT)),
                        x + w / 2.0F, y - 12, MatchArt.GOLD_LIGHT, 1.0F, true);
            }
        });
    }

    // The readable reveal on the left is only a preview. The actual cast comes from the card in their hand.
    private void animateOpponentTarget(Event event, float[] origin) {
        if (event.card() == null) return;
        float[] target = targetCenter(event);
        Sleeves.Design sleeve = Sleeves.get(this.shown.sides[event.side()].sleeve);
        float castScale = Math.min(0.60F, this.backScale * 1.5F);
        float castY = Math.max(CardRenderer.HEIGHT * castScale / 2.0F + 6, origin[1] + 12);
        int color = event.targetSide() == event.side() ? MatchArt.GLOW : MatchArt.RED;
        effect(0, 1100, true, (g, t) -> {
            float lift = MatchArt.easeOut(MatchArt.clamp(t / 0.25F));
            float vanish = t > 0.88F ? 1 - MatchArt.easeIn((t - 0.88F) / 0.12F) : 1;
            float scale = lerp(origin[2], castScale, lift) * vanish;
            float cx = origin[0];
            float cy = lerp(origin[1], castY, lift);
            float rotation = origin[3] * (1 - lift);
            float flip = Math.max(0.06F, Math.abs(1 - lift * 2));
            float w = CardRenderer.WIDTH * scale;
            float h = CardRenderer.HEIGHT * scale;
            if (t >= 0.25F && t < 0.90F) {
                MatchArt.arrow(g, Math.round(cx), Math.round(cy + h / 2),
                        Math.round(target[0]), Math.round(target[1]), color, this.now);
            }
            g.pose().pushMatrix();
            g.pose().translate(cx, cy);
            g.pose().rotate(rotation);
            g.pose().scale(flip, 1);
            g.pose().translate(-w / 2, -h / 2);
            if (lift < 0.5F) {
                CardRenderer.drawBack(g, sleeve, 0, 0, scale);
            } else {
                CardRenderer.drawTrial(g, this.font, event.card(), event.enchanted(), event.training(), 0, 0, scale);
            }
            g.pose().popMatrix();
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
        this.handScale = 0.72F * Math.min(0.75F, h * 0.30F / CardRenderer.HEIGHT);
        this.handW = Math.round(CardRenderer.WIDTH * this.handScale);
        this.handH = Math.round(CardRenderer.HEIGHT * this.handScale);
        // Lower the resting fan, but keep its art and name visible on tall screens.
        this.handTop = h - Math.max(Math.round(this.handH * 0.61F),
                Math.round(this.handH * 0.82F - h * 0.05F));
        this.heroSize = Math.round(1.05F * Math.max(22, h * 0.077F));
        this.toolSize = Math.round(this.heroSize * 0.68F);
        this.centerX = w / 2;
        this.orbSize = Math.max(6, Math.round(h / 42.0F));
        // Keep the table frame below the top HUD (name on the left, hand count and XP on the right).
        int topBand = theirXpY() + this.orbSize + 5;
        this.oppHeroY = Math.max(Math.round(h * 0.078F) + 2, topBand + 9 - this.heroSize / 2);
        this.boardBottom = this.handTop - this.heroSize - 6;
        this.myHeroY = this.boardBottom + 4; // Portrait frame starts four pixels above the face.
        this.boardTop = this.oppHeroY + this.heroSize + 5;
        this.middle = (this.boardTop + this.boardBottom) / 2;
        this.historyIcon = Math.max(14, Math.round(h / 20.0F));
        // The same margin on both sides, so the table sits in the middle: the battle log on the left, END TURN
        // and the decks on the right.
        this.deckScale = Math.max(0.14F, h * 0.12F / CardRenderer.HEIGHT);
        this.deckW = Math.round(CardRenderer.WIDTH * this.deckScale);
        this.deckH = Math.round(CardRenderer.HEIGHT * this.deckScale);
        int side = Math.max(64, Math.max(this.deckW, this.historyIcon) + 24);
        this.boardLeft = side;
        this.boardRight = w - side;
        int rowSpace = Math.max(16, (boardBottom - boardTop - ROW_GAP) / 2 - 8);
        int boardWidth = boardRight - boardLeft;
        mobGap = Math.clamp(boardWidth / 32, 4, MOB_GAP);
        int widest = Math.max(1, (boardWidth - 66 - (Match.MAX_MOBS - 1) * mobGap) / Match.MAX_MOBS);
        float artRatio = CardRenderer.ART_WIDTH / (float) CardRenderer.ART_HEIGHT;
        mobW = Math.max(1, Math.min(widest, Math.round((rowSpace / 2 - 4) * artRatio) + 4));
        mobH = Math.max(1, Math.round((mobW - 4) / artRatio) + 4);
        this.theirRowY = this.middle - ROW_GAP / 2 - this.mobH;
        this.myRowY = this.middle + ROW_GAP / 2;
        // END TURN and the decks are centred in the space right of the table's frame, never over it.
        int rightSpace = w - (this.boardRight + 10);
        float rightCenter = this.boardRight + 10 + rightSpace / 2.0F;
        int buttonWidth = rightSpace - 8;
        this.endTurnButton = new int[]{Math.round(rightCenter - buttonWidth / 2.0F), this.middle - 10, buttonWidth, 20};
        this.backScale = Math.max(0.15F, h * 0.13F / CardRenderer.HEIGHT);
        this.deckX = Math.round(rightCenter - this.deckW / 2.0F);
        this.theirDeckY = this.middle - 20 - this.deckH;
        this.myDeckY = this.middle + 20;
    }

    private boolean pendingBlock() {
        return awaitingTarget != null && Cards.get(awaitingTarget.id).keywords().contains(Keyword.BLOCK);
    }

    private boolean overPlayArea(double x, double y) {
        return y < handTop - 4 && x >= boardLeft && x < boardRight;
    }

    private Spot placementRow(int side, boolean block) {
        boolean own = side == shown.you;
        int split = own ? myRowY + mobH + 2 : theirRowY - 2;
        int top = own ? (block ? split : middle + 2) : (block ? boardTop : split);
        int bottom = own ? (block ? boardBottom : split) : (block ? split : middle - 2);
        return new Spot(boardLeft + 5, top, boardRight - boardLeft - 10, Math.max(0, bottom - top), side, -1, -1);
    }

    private void drawPlacementRow(GuiGraphicsExtractor g, int side, boolean block, int mouseX, int mouseY) {
        Spot row = placementRow(side, block);
        if (row.height() < 1 || row.width() < 1) return;
        CardDefinition card = dragging == null ? null : Cards.get(dragging.id);
        boolean placing = card != null && card.isMob();
        int count = rowCount(side, block);
        int limit = Match.rowLimit(block);
        boolean valid = count < limit && placing && side == shown.you && card.keywords().contains(Keyword.BLOCK) == block;
        boolean full = count >= limit;
        boolean hover = valid && overPlayArea(mouseX, mouseY);
        int fill = block ? 0x55414A54 : 0x202F4438;
        int rim = block ? 0xFF707978 : 0xFF58665B;
        if (valid || full) {
            fill = valid ? (hover ? 0x90517F55 : 0x50517F55) : 0xA0833738;
            rim = valid ? 0xFF9DE3A3 : 0xFFFF8581;
        }
        g.fill(row.x(), row.y(), row.x()+row.width(), row.y()+row.height(), fill);
        g.outline(row.x(), row.y(), row.width(), row.height(), rim);
        MatchArt.outlined(g, font, block ? "BLOCKS" : "MOBS", row.x()+3, row.y()+2,
                valid || full ? rim : 0xFFBDC5BE, .65F, false);
        MatchArt.outlined(g, font, count + "/" + limit, row.x()+3, row.y()+9,
                count >= limit ? 0xFFFFCF86 : 0xFFE2E7E2, .65F, false);
    }

    private int rowCount(int side, boolean block) {
        int count = 0;
        for (Mob mob : shown.sides[side].board) if (mob.block == block) count++;
        return count;
    }

    private int laneY(int side, boolean block) {
        Spot row = placementRow(side, block);
        return row.y() + (row.height() - mobH) / 2;
    }

    private float laneX(int side, int index, boolean block) {
        List<Mob> board = shown.sides[side].board;
        int before = 0, count = 0;
        for (int i = 0; i < board.size(); i++) {
            if (board.get(i).block != block) continue;
            count++;
            if (i < index) before++;
        }
        if (index == board.size() || side == shown.you && awaitingTarget != null && pendingBlock() == block) count++;
        return slotX(before, count);
    }

    private int heroY(int side) {
        return side == this.shown.you ? this.myHeroY : this.oppHeroY;
    }

    private float slotX(int index, int count) {
        float total = count * this.mobW + (count - 1) * mobGap;
        return this.centerX - total / 2.0F + index * (this.mobW + mobGap);
    }

    private float[] heroCenter(int side) {
        return new float[]{this.centerX, heroY(side) + this.heroSize / 2.0F};
    }

    private float[] mobCenter(int side, int mid) {
        Mob mob = this.shown.sides[side].mob(mid);
        float y = laneY(side, mob != null && mob.block) + mobH / 2.0F;
        Float x = this.mobX.get(mid);
        if (x != null) {
            return new float[]{x + this.mobW / 2.0F, y};
        }
        Side s = this.shown.sides[side];
        int index = s.indexOf(mid);
        if (index >= 0) {
            return new float[]{laneX(side, index, mob.block) + this.mobW / 2.0F, y};
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

    private float[] handCenter(int sideIndex) {
        Side side = this.shown.sides[sideIndex];
        boolean local = sideIndex == this.shown.you;
        int count = Math.max(1, local ? side.handCount : side.backs.size());
        float[] left = local ? myHandOrigin(side, (count - 1) / 2) : opponentHandOrigin(side, (count - 1) / 2);
        float[] right = local ? myHandOrigin(side, count / 2) : opponentHandOrigin(side, count / 2);
        return new float[]{(left[0] + right[0]) / 2F, (left[1] + right[1]) / 2F};
    }

    private void throwEnderEye(float[] from, float[] to, boolean sound) {
        // Keep the Eye visible over the hand, whose fan is partly outside the screen.
        // Its pulse can reach the edge along with the card backs.
        float sx = Math.max(28, Math.min(this.width - 28, from[0]));
        float sy = Math.max(28, Math.min(this.height - 28, from[1]));
        float tx = Math.max(40, Math.min(this.width - 40, to[0]));
        float ty = Math.max(16, Math.min(this.height - 16, to[1]));
        ItemStack eye = new ItemStack(Items.ENDER_EYE);
        if (sound) MatchSounds.play(EyeOfEnderAnimation.LAUNCH_SOUND, 1.0F, .7F);
        effect(0, EyeOfEnderAnimation.DURATION_MS, true, (g, t) ->
                EyeOfEnderAnimation.draw(new craftbound.client.card.CardCanvas.Gui(g), eye,
                        t * EyeOfEnderAnimation.DURATION_MS, sx, sy, tx, ty));
        schedule(EyeOfEnderAnimation.ARRIVE_MS, () -> {
            if (sound) MatchSounds.play(EyeOfEnderAnimation.ARRIVE_SOUND, .85F, .45F);
            particles(tx, ty, 12, 0xFF79E4C7, 24, -4, 1);
        });
    }

    private float[] deckCenter(int side) {
        return new float[]{this.deckX + this.deckW / 2.0F, (side == this.shown.you ? this.myDeckY : this.theirDeckY) + this.deckH / 2.0F};
    }

    private int socketY(int side) {
        // The player's three outer frames share the line beneath the block row.
        return side == this.shown.you ? this.boardBottom + 1 : this.oppHeroY + this.heroSize - this.toolSize;
    }

    private float[] toolCenter(int side) {
        return new float[]{this.centerX - this.heroSize / 2.0F - 6 - this.toolSize / 2.0F, socketY(side) + this.toolSize / 2.0F};
    }

    private float[] powerCenter(int side) {
        return new float[]{this.centerX + this.heroSize / 2.0F + 6 + this.toolSize / 2.0F, socketY(side) + this.toolSize / 2.0F};
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
        long time = animationTime();
        // Slow the visual clock after impact; game rules, networking and other screens keep real time.
        if(gameOverStart >= 0 && shown.over && shown.winner >= 0)
            time -= Math.round(.58 * Math.clamp(time-gameOverStart-140L, 0L, 1400L));
        this.dt = this.lastFrame == 0 ? 0 : Math.min(0.1F, (time - this.lastFrame) / 1000.0F);
        this.lastFrame = time;
        this.now = time;
        layout();
        // Finish scheduled impacts (or start a delayed cast) before advancing its event beat.
        runScheduled();
        advance();
        runScheduled();
        this.handPicks().sync(this.shown, time, this.playing != null || !this.updates.isEmpty());
        this.mobSpots.clear();
        this.handSpots.clear();
        this.historySpots.clear();
        this.hoveredHand = -1;
        if (!canAct() || !this.shown.me().powerUsable) {
            this.aimingPower = false;
            this.aimingExcavate = false;
            this.choosingExcavate = false;
        }
        if (this.shown.playing) updateHandHover(mouseX, mouseY);
        this.previewSource = null;
        // Hover previews also help planning before there is enough XP or while waiting for your turn.
        if (this.shown.playing && !this.shown.over && !this.menuOpen && this.attacker == null && !this.aimingPower && !this.aimingExcavate && !this.choosingExcavate
                && this.playing == null && this.updates.isEmpty() && !this.awaitingReply) {
            HandCard preview = this.awaitingTarget != null ? this.awaitingTarget : this.dragging;
            if (preview == null && this.hoveredHand >= 0) preview = this.shown.me().hand.get(this.hoveredHand);
            if (preview != null) {
                this.previewSource = effectSource(preview.id, this.shown.you, -1, false);
            } else {
                // Inspect ongoing effects from a board card, without replaying its Spawn effect.
                for (int side = 0; side < 2; side++) {
                    List<Mob> board = this.shown.sides[side].board;
                    for (int i = 0; i < board.size(); i++) {
                        Mob mob = board.get(i);
                        float targetX = laneX(side, i, mob.block);
                        float x = this.mobX.getOrDefault(mob.mid, targetX);
                        x += (targetX - x) * (1.0F - (float) Math.exp(-this.dt * 12));
                        if (mouseX >= x && mouseX < x + this.mobW && mouseY >= laneY(side, mob.block) && mouseY < laneY(side, mob.block) + mobH)
                            this.previewSource = effectSource(mob.id, side, mob.mid, true);
                    }
                }
            }
        }

        // The brick surround covers the entire viewport, even at the peak of an impact.
        drawBrickBackdrop(g);
        float[] shake = screenShakeOffset();
        g.pose().pushMatrix();
        g.pose().translate(shake[0], shake[1]);
        drawTable(g);
        for (int side = 0; side < 2; side++) {
            drawPlacementRow(g, side, false, mouseX, mouseY);
            drawPlacementRow(g, side, true, mouseX, mouseY);
        }
        drawHistory(g);
        for (int s = 0; s < 2; s++) {
            drawDeck(g, s);
            drawXp(g, s);
            drawNameplate(g, s);
            drawHeroSockets(g, s, mouseX, mouseY);
            if (!isLungingHero(s)) drawHero(g, s, mouseX, mouseY);
        }
        drawEndTurn(g, mouseX, mouseY);
        if(clockTurn==shown.turn && clockSide==shown.current && clockTicks>=0 && !shown.over) {
            float seconds=Math.max(0,clockTicks/20F-(clockHeld?0:(millis()-clockReceived)/1000F));
            TurnFuse.draw(new craftbound.client.card.CardCanvas.Gui(g),font,boardLeft+16,
                endTurnButton[0]-25,middle,seconds,now);
        }
        drawRow(g, 1 - this.shown.you, mouseX, mouseY);
        drawRow(g, this.shown.you, mouseX, mouseY);
        for (int s = 0; s < 2; s++) if (!isLungingHero(s)) drawHeroHealth(g, s);
        drawLungingMob(g);
        drawLungingHero(g, mouseX, mouseY);
        drawEffects(g, false);
        drawParticles(g);
        drawTheirHand(g);
        if (this.shown.playing) {
            drawMyHand(g, mouseX, mouseY);
        }
        for (int s = 0; s < 2; s++) {
            drawHandCount(g, s);
        }
        drawTrialsCharms(g, mouseX, mouseY);
        drawInteraction(g, mouseX, mouseY);
        g.pose().popMatrix();

        boolean overlay = this.shown.over || coinShowing() || !this.shown.playing || this.menuOpen || gameOverShowing() || this.choosingExcavate
                || this.handPicks().covering(this.shown, time);
        if (!overlay) {
            drawPreview(g, mouseX, mouseY);
        }
        drawEffects(g, true);
        drawBanner(g);
        if (this.shown.playing) this.handPicks().draw(g, this.font, this.shown, this.width, this.height, mouseX, mouseY, time);
        if (coinShowing()) {
            drawCoin(g);
        } else if (!this.readOnly && !this.shown.playing && !this.shown.over) {
            drawMulligan(g, mouseX, mouseY);
        }
        if (gameOverShowing()) {
            drawGameOver(g);
        }
        if (this.menuOpen) {
            drawMenu(g, mouseX, mouseY);
        }
        if (this.choosingExcavate) drawExcavate(g, mouseX, mouseY);
        if (!overlay) {
            drawTooltips(g, mouseX, mouseY);
        }
        if(!this.readOnly) HandConsentOverlay.draw(g,font,width);
        if(previewReturn != null) {
            String hint = width < 480 ? "Preview" : "Preview - Esc: settings";
            int x = width - font.width(hint) - 7;
            g.fill(x-3,4,width-4,17,0xDD1B1814);
            g.text(font,hint,x,7,0xFFFFE2A2,false);
        }
    }

    // A dark brick wall, with the wooden table in the middle. Each hero sits on its edge.
    private void drawBrickBackdrop(GuiGraphicsExtractor g) {
        g.fill(0, 0, this.width, this.height, MORTAR);
        int brick = 24;
        for (int row = 0; row * brick / 2 < this.height; row++) {
            int y = row * brick / 2;
            int offset = row % 2 == 0 ? 0 : brick / 2;
            for (int x = -offset; x < this.width; x += brick) {
                g.fill(x + 1, y + 1, x + brick - 1, y + brick / 2 - 1, (row + x / brick) % 2 == 0 ? shown.boardDesign.brick : shown.boardDesign.alternateBrick);
            }
        }
    }

    private void drawTable(GuiGraphicsExtractor g) {
        int x0 = this.boardLeft;
        int x1 = this.boardRight;
        int y0 = this.oppHeroY + this.heroSize / 2;
        int y1 = this.myHeroY + this.heroSize / 2;
        g.fill(x0 - 9, y0 - 9, x1 + 9, y1 + 9, 0xFF000000);
        g.fill(x0 - 8, y0 - 8, x1 + 8, y1 + 8, MatchArt.WOOD_DARK);
        g.fill(x0 - 6, y0 - 6, x1 + 6, y1 + 6, MatchArt.WOOD_LIGHT);
        g.fill(x0 - 5, y0 - 5, x1 + 5, y1 + 5, MatchArt.WOOD_MID);
        g.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, MatchArt.WOOD_DARK);
        g.fill(x0,y0,x1,y1,shown.boardDesign.mat);
        BoardTrim.draw(new craftbound.client.card.CardCanvas.Gui(g), shown.boardDesign, x0+3,y0+3,x1-x0-6,y1-y0-6);
        g.outline(x0,y0,x1-x0,y1-y0,0xFF323C37);
        g.outline(x0+3,y0+3,x1-x0-6,y1-y0-6,0xFF737D74);
        g.fill(x0+8,this.middle,x1-8,this.middle+1,0xFF424C46);
    }

    // The battle log as a strip of icons down the left, newest at the top. Your actions have green edges,
    // theirs red. Hover one to see what happened.
    private void drawHistory(GuiGraphicsExtractor g) {
        int size = this.historyIcon;
        // Centred in the margin left of the table.
        int x = (this.boardLeft - 10 - size) / 2;
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

    // Potion particles drifting up through a mob that is under a potion, in that potion's colour.
    private void potionSwirl(GuiGraphicsExtractor g, Mob mob, int w, int h) {
        for (int i = 0; i < mob.potions.size(); i++) {
            int color = potionColor(mob.potions.get(i).id());
            boolean brewed = mob.potions.get(i).brew() != 0;
            int count = brewed ? 7 : 5;
            for (int k = 0; k < count; k++) {
                double seed = mob.mid * 0.37 + i * 0.61 + k * 1.73;
                double phase = (this.now / 1600.0 + k / (double) count + i * 0.13) % 1.0;
                float px = (float) (w * (0.15 + 0.7 * (0.5 + 0.5 * Math.sin(seed * 12.9898))) + Math.sin(phase * 6.28 + seed) * 2);
                float py = (float) (h * (0.95 - 0.85 * phase));
                int size = brewed && k % 3 == 0 ? 3 : 2;
                g.fill(Math.round(px), Math.round(py), Math.round(px) + size, Math.round(py) + size, MatchArt.alpha(color, (float) (0.9 * (1 - phase))));
            }
        }
    }

    static int potionColor(Identifier id) {
        String path = id.getPath();
        if (path.contains("strength")) return 0xFFFFC23A;
        if (path.contains("swift") || path.contains("speed")) return 0xFF7CCFF0;
        if (path.contains("heal") || path.contains("regen")) return 0xFFFF6FA8;
        if (path.contains("fire")) return 0xFFFF9A3A;
        if (path.contains("weak") || path.contains("slow")) return 0xFF9AA0A6;
        if (path.contains("poison")) return 0xFF6FCB3A;
        if (path.contains("harm")) return 0xFF9B3A5A;
        if (path.contains("invis")) return 0xFFE6ECF2;
        return 0xFFC08CFF;
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
        // Yours in the bottom-right corner; theirs in the top-right, both below their hand counter.
        int right = this.width - 8;
        int y = mine ? myXpY() : theirXpY();
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
    }

    // XP stays on the right at both ends of the board.
    private int myXpY() {
        return this.height - this.orbSize - 8;
    }

    private int theirXpY() {
        // The nameplate is on the opposite side; reserve only the counter and its margins.
        return 10 + HAND_COUNT_HEIGHT + 5;
    }

    // Matching counters on the right beside each hand, centred above each player's XP orbs.
    // Both use the same column and reserve two digits so drawing the tenth card never shifts the icon.
    // Red means the next draw would burn.
    private void drawHandCount(GuiGraphicsExtractor g, int s) {
        Side side = this.shown.sides[s];
        boolean mine = s == this.shown.you;
        int count = this.readOnly ? side.handCount : mine ? side.hand.size() : side.backs.size();
        int y = (mine ? myXpY() : theirXpY()) - 5 - HAND_COUNT_HEIGHT;
        String text = String.valueOf(count);
        int numberWidth = this.font.width(String.valueOf(Match.MAX_HAND));
        int badgeWidth = 10 + numberWidth;
        int iconX = handCountX(badgeWidth);
        int textX = iconX + 10 + (numberWidth - this.font.width(text)) / 2;
        g.fill(iconX - 3, y - 3, iconX + badgeWidth + 3, y + HAND_COUNT_HEIGHT + 3, PANEL_DARK);
        g.outline(iconX - 3, y - 3, badgeWidth + 6, HAND_COUNT_HEIGHT + 6, PANEL_EDGE);
        MatchArt.handIcon(g,iconX,y);
        MatchArt.outlined(g, this.font, text, textX, y + 1, count >= Match.MAX_HAND ? 0xFFFF5A4A : MatchArt.TEXT, 1.0F, false);
    }

    // One anchor for both players: the opponent's icon lines up exactly with yours, regardless of count.
    private int handCountX(int badgeWidth) {
        int xpWidth = Match.MAX_XP * (this.orbSize + 3);
        return this.width - 8 - (xpWidth + badgeWidth) / 2;
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

    // Player names share the left edge: yours at the bottom, the opponent's at the top.
    // The class is the colour round their portrait; hand and deck sizes are on the table.
    private void drawNameplate(GuiGraphicsExtractor g, int s) {
        Side side = this.shown.sides[s];
        Component name = Component.literal(side.name).withStyle(ChatFormatting.BOLD);
        int width = Math.min(Math.max(60, Math.round(this.width * 0.23F)), this.font.width(name) + 12);
        int height = NAMEPLATE_HEIGHT;
        boolean mine = s == this.shown.you;
        int x = 6;
        int y = mine ? this.height - 6 - height : 4;
        this.nameplateSpots[s] = new int[]{x, y, width, height};
        // A gold edge while it's their turn.
        boolean active = this.shown.playing && !this.shown.over && this.shown.current == s;
        g.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF000000);
        g.fillGradient(x, y, x + width, y + height, PANEL, PANEL_DARK);
        g.outline(x + 1, y + 1, width - 2, height - 2, active ? MatchArt.GOLD : PANEL_EDGE);
        CardRenderer.fitText(g, this.font, name, x + width / 2, y + 3, width - 10, MatchArt.TEXT, true);
    }

    // The deck's name: what the player called it, or its class ("Farmer Deck").
    private static Component deckName(Side side) {
        return side.deckName.isEmpty() ? Component.translatable("deck_ui.craftbound.deck_title", side.cardClass.displayName())
                : Component.literal(side.deckName);
    }

    private void drawTrialsCharms(GuiGraphicsExtractor g, int mx, int my) {
        hoveredCharm = null;
        if (readOnly || shown.me().charms.isEmpty()) return;
        int[] plate = nameplateSpots[shown.you];
        int x = plate[0], y = plate[1] - 20;
        for (var slot : shown.me().charms) {
            boolean over = mx >= x && mx < x + 18 && my >= y && my < y + 18;
            if (over) hoveredCharm = slot;
            g.fill(x, y, x + 18, y + 18, PANEL_DARK);
            g.outline(x, y, 18, 18, over ? MatchArt.GOLD_LIGHT : slot.spent() ? PANEL_EDGE : MatchArt.GOLD);
            g.item(slot.charm().icon(), x + 1, y + 1);
            if (slot.spent()) g.fill(x + 1, y + 1, x + 17, y + 17, 0x990D0804);
            x += 20;
        }
    }

    private static int classColor(CardClass cardClass) {
        return cardClass.frameColor();
    }

    // A hero: portrait (the player's face), health heart, and the attack badge while holding a tool on their turn.
    private void drawHero(GuiGraphicsExtractor g, int s, int mouseX, int mouseY) {
        Side side = this.shown.sides[s];
        int size = this.heroSize;
        int x = this.centerX - size / 2;
        int y = heroY(s);
        this.heroSpots[s] = new Spot(x, y, size, size, s, Match.HERO, -1);
        if(this.shatteredHero==s && this.now>=this.shatterAt) return;
        boolean mine = s == this.shown.you;
        float[] shake = shake("h:" + s);
        float[] lunge = lungeOffset(s, Match.HERO);
        g.pose().pushMatrix();
        g.pose().translate(shake[0] + lunge[0], shake[1] + lunge[1]);
        if (mine && side.heroReady && canAct() && this.attacker == null && this.dragging == null) {
            g.fill(x - 4, y - 4, x + size + 4, y + size + 4, 0x807DFF5A);
            g.outline(x - 3, y - 3, size + 6, size + 6, MatchArt.GLOW);
        }
        // The border's colour says the class: red Fighter, blue Builder, green Farmer.
        int classColor = classColor(side.cardClass);
        MatchArt.octagon(g, x - 4, y - 4, size + 8, 0xFF000000);
        MatchArt.octagon(g, x - 3, y - 3, size + 6, lighter(classColor));
        MatchArt.octagon(g, x - 2, y - 2, size + 4, classColor);
        MatchArt.octagon(g, x + 2, y + 2, size - 4, 0xFF000000);
        MatchArt.octagon(g, x + 3, y + 3, size - 6, 0xFF2B2C31);
        drawFace(g, side, x + 4, y + 4, size - 8);
        float flash = pulse(this.flashes.getOrDefault("h:" + s, -1L), 260);
        if (flash > 0) {
            MatchArt.octagon(g, x, y, size, MatchArt.alpha(0xFFFF2020, flash * 0.7F));
        }
        int badge = Math.max(13, Math.round(size * 0.40F));
        int statusY = y - badge / 2 - 9;
        if (side.badOmen != null) MatchArt.outlined(g, this.font, Keyword.BAD_OMEN.displayName(),
                x + size / 2F, statusY - (side.frozen ? 9 : 0), 0xFFCCB3FF, 0.75F, true);
        if (side.frozen) {
            MatchArt.octagon(g, x, y, size, 0x6659BFE8);
            MatchArt.octagonOutline(g, x - 2, y - 2, size + 4, 0xFFE1F8FF, 2);
            int ice = Math.max(4, size / 5);
            g.fill(x + size / 3, y + 3, x + size / 3 + 2, y + ice + 5, 0xCCDDF8FF);
            g.fill(x + size * 2 / 3, y + size - ice - 3, x + size * 2 / 3 + 2, y + size - 3, 0xCCDDF8FF);
            MatchArt.outlined(g, this.font, "Frozen", x + size / 2F, statusY, 0xFFCEEDFF, 0.75F, true);
        }
        if (heroTargetable(s)) {
            boolean hovered = this.heroSpots[s].contains(mouseX, mouseY);
            MatchArt.octagonOutline(g, x - 6, y - 6, size + 12,
                    hovered ? 0xFFFF7063 : MatchArt.RED, 2);
            if (hovered) {
                MatchArt.octagonOutline(g, x - 4, y - 4, size + 8, 0xFFFFFFFF, 1);
            }
        }
        int badgeY = y + size - badge * 3 / 4;
        int healthColor = side.health < side.maxHealth ? 0xFFFF5A5A : 0xFFFFFFFF;
        MatchArt.heart(g, this.font, x + (size - badge) / 2, y - badge / 2, badge, side.health, healthColor);
        if (side.heroAttack > 0 && this.shown.current == s && this.shown.playing) {
            MatchArt.attack(g, this.font, x - badge / 4, badgeY, badge, side.heroAttack, 0xFFFFFFFF, side.tool != null && side.tool.ranged);
        }
        if (!heroTargetable(s)) {
            EffectPreview.Highlight preview = this.aimingPower
                    ? (this.shown.me().power == HeroPowers.Power.THROW_POTION ? new EffectPreview.Highlight(true, true) : this.shown.me().power == HeroPowers.Power.FEED && side.health < side.maxHealth ? new EffectPreview.Highlight(false, true) : null)
                    : EffectPreview.forTarget(this.previewSource, new EffectPreview.Subject(EffectPreview.Kind.HERO, null, s, Match.HERO, side.health, side.maxHealth, false, side.frozen, false, false, 0, side.badOmen != null, side.maxHealth > Match.START_HEALTH || side.heroAttack > (side.tool == null ? 0 : side.tool.attack)));
            boolean selected = isValidTarget(this.heroSpots[s]) && this.heroSpots[s].contains(mouseX, mouseY);
            if (preview != null && selected) preview = new EffectPreview.Highlight(preview.harmful(), false);
            drawRoundPreview(g, x, y, size, preview);
            if (preview != null && selected) MatchArt.octagonOutline(g, x - 3, y - 3, size + 6, 0xFFFFFFFF, 1);
        }
        g.pose().popMatrix();
    }

    // The hero's health heart, drawn again above the board: a Shield frame on the block row reaches over it.
    private void drawHeroHealth(GuiGraphicsExtractor g, int s) {
        if (this.shatteredHero == s && this.now >= this.shatterAt) return;
        Side side = this.shown.sides[s];
        int size = this.heroSize, x = this.centerX - size / 2, y = heroY(s);
        int badge = Math.max(13, Math.round(size * 0.40F));
        float[] shake = shake("h:" + s);
        float[] lunge = lungeOffset(s, Match.HERO);
        g.pose().pushMatrix();
        g.pose().translate(shake[0] + lunge[0], shake[1] + lunge[1]);
        MatchArt.heart(g, this.font, x + (size - badge) / 2, y - badge / 2, badge, side.health,
                side.health < side.maxHealth ? 0xFFFF5A5A : 0xFFFFFFFF);
        g.pose().popMatrix();
    }

    // Sockets stay on the table while the portrait and its stat badges attack above the board.
    private void drawHeroSockets(GuiGraphicsExtractor g, int side, int mouseX, int mouseY) {
        int x = this.centerX - this.heroSize / 2;
        int y = socketY(side);
        drawTool(g, side, x - this.toolSize - 6, y);
        drawPower(g, side, x + this.heroSize + 6, y, mouseX, mouseY);
    }

    private boolean isLungingHero(int side) {
        return this.lunge != null && this.lunge.side() == side && this.lunge.mid() == Match.HERO;
    }

    private void drawLungingHero(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (this.lunge == null || this.lunge.mid() != Match.HERO) return;
        // An explicit stratum also keeps the portrait's item/skin render above mob artwork.
        g.nextStratum();
        drawHero(g, this.lunge.side(), mouseX, mouseY);
        g.nextStratum(); // Damage numbers and effects still appear above the attacking portrait.
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
        boolean enchanted = side.tool.enchanted || !side.tool.enchantments.isEmpty();
        MatchArt.octagon(g, 0, 0, size, enchanted ? 0xFFBD83EF : MatchArt.GOLD_DARK);
        MatchArt.octagon(g, 2, 2, size - 4, 0xFF5D5E66);
        CardDefinition card = Cards.get(side.tool.id);
        if (card != null) {
            ItemStack icon = CardRenderer.placeholderArt(side.tool.id, card);
            if (enchanted) icon.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            drawItem(g, icon, size / 2.0F, size / 2.0F, (size - 6) / 16.0F);
        }
        if (!side.tool.enchantments.isEmpty()) {
            // A persistent rune distinguishes applied enchantments from the cosmetic finish.
            int rune = size - 7;
            g.fill(rune - 1, 2, rune + 2, 9, 0xFF211129);
            g.fill(rune - 3, 4, rune + 4, 7, 0xFF211129);
            g.fill(rune, 3, rune + 1, 8, 0xFFE7BFFF);
            g.fill(rune - 2, 5, rune + 3, 6, 0xFFE7BFFF);
        }
        var toolPreview = EffectPreview.forTarget(this.previewSource, EffectPreview.Subject.tool(s, side.tool.id));
        drawRoundPreview(g, 0, 0, size, toolPreview);
        if (toolPreview != null) g.nextStratum();
        int badge = Math.max(10, Math.round(size * 0.42F));
        MatchArt.attack(g, this.font, -badge / 4, size - badge * 3 / 4, badge, side.tool.attack, 0xFFFFFFFF, side.tool.ranged);
        MatchArt.uses(g, this.font, size - badge * 3 / 4, size - badge * 3 / 4, badge, side.tool.uses);
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
        // Keep the ready highlight within the same outline as the tool socket.
        g.pose().pushMatrix();
        g.pose().translate(x + size / 2.0F, y + size / 2.0F);
        g.pose().scale(squish, 1.0F);
        g.pose().translate(-size / 2.0F, -size / 2.0F);
        MatchArt.octagon(g, -1, -1, size + 2, 0xFF000000);
        MatchArt.octagon(g, 0, 0, size, MatchArt.GOLD);
        MatchArt.octagon(g, 2, 2, size - 4, darker(classColor(side.cardClass)));
        if (usable) {
            float glow = 0.75F + 0.25F * (float) Math.sin(this.now / 250.0);
            MatchArt.octagonOutline(g, 0, 0, size, MatchArt.alpha(0xFF7DFF5A, glow), 2);
        }
        drawItem(g, new ItemStack(BuiltInRegistries.ITEM.getValue(side.power.icon())), size / 2F, size / 2F, (size - 8) / 16.0F);
        if (side.powerUsed) {
            MatchArt.octagon(g, 0, 0, size, 0xA0000000);
        }
        g.pose().popMatrix();
        if (usable) g.nextStratum();
        int gem = Math.max(9, Math.round(size * 0.38F));
        MatchArt.cost(g, this.font, x + (size - gem) / 2, mine ? y + size - gem / 2 : y - gem / 2, gem, this.shown.powerCost);
    }

    private static int lighter(int color) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + 70);
        int gr = Math.min(255, ((color >> 8) & 0xFF) + 70);
        int b = Math.min(255, (color & 0xFF) + 70);
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
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
        boolean pending = s == this.shown.you && this.awaitingTarget != null;
        int count = side.board.size() + (pending ? 1 : 0);
        float k = 1.0F - (float) Math.exp(-this.dt * 12);
        for (int i = 0; i < side.board.size(); i++) {
            Mob mob = side.board.get(i);
            int y = laneY(s, mob.block);
            float target = laneX(s, i, mob.block);
            float x = this.mobX.getOrDefault(mob.mid, target);
            x += (target - x) * k;
            this.mobX.put(mob.mid, x);
            Spot spot = new Spot(Math.round(x), y, this.mobW, mobH, s, mob.mid, -1);
            this.mobSpots.add(spot);
            if(this.stealingTools.getOrDefault(mob.mid,0L)>this.now)continue;
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
            ghost.training = this.awaitingTarget.training;
            ghost.ranged = ghost.card().keywords().contains(Keyword.RANGED);
            ghost.sprint = ghost.training.keyword() == Keyword.SPRINT || ghost.card().keywords().contains(Keyword.SPRINT);
            ghost.attack = ghost.training.attack(card);
            ghost.health = ghost.training.health(card);
            ghost.maxHealth = ghost.health;
            ghost.shield = ghost.training.keyword() == Keyword.SHIELD || card.keywords().contains(Keyword.SHIELD);
            ghost.ignoreShield = card.keywords().contains(Keyword.IGNORE_SHIELD);
            ghost.block = pendingBlock();
            int y = laneY(s, ghost.block);
            float x = laneX(s, count - 1, ghost.block);
            this.pendingSpot = new Spot(Math.round(x), y, this.mobW, mobH, s, -2, -1);
            drawMob(g, ghost, s, x, y, 1.0F, false, -1);
            g.outline(Math.round(x) - 3, y - 3, this.mobW + 6, mobH + 6, MatchArt.GOLD_LIGHT);
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
        drawMob(g, mob, side, x + offset[0], laneY(side, mob.block) + offset[1], 1.08F, false, -1);
    }

    // A mob on the board: its art in a frame, attack bottom-left and a heart with its health bottom-right.
    // Shield uses a pointed iron frame and a wooden shield badge. Blocks retain a zero attack badge.
    private void drawMob(GuiGraphicsExtractor g, Mob mob, int side, float x, float y, float scale, boolean hovered, long arrivedAt) {
        if(arrivedAt>=0 && this.materializing.getOrDefault(mob.mid,0L)>this.now) return;
        CardDefinition card = mob.card();
        if (card == null) {
            return;
        }
        int w = this.mobW;
        int h = mobH;
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
        if (mob.shield) {
            MatchArt.shieldFrame(g, w, h);
            if (mine && mob.ready && canAct() && this.attacker == null && this.dragging == null && mob.mid > 0) {
                MatchArt.shieldOutline(g, -6, -6, w + 12, h + 12, 0, MatchArt.GLOW, 1);
            }
        }
        boolean legendary = card.rarity() == craftbound.card.CardRarity.LEGENDARY;
        g.fill(-1, -1, w + 1, h + 1, 0xFF000000);
        g.fill(0, 0, w, h, legendary ? MatchArt.GOLD : mob.enchanted ? 0xFFB57AE0 : card.cardClass().frameColor());
        int inset = legendary ? 3 : 2;
        CardRenderer.drawBoardArt(new craftbound.client.card.CardCanvas.Gui(g), mob.id, card,
                inset, inset, w - inset * 2, h - inset * 2);
        if (!mine) g.fill(inset, inset, w - inset, h - inset, 0x30400000);
        if (legendary) {
            CardRenderer.drawLegendaryArtFrame(g, 0, 0, w, h);
        } else if (mob.block) {
            for (int xx : new int[]{0, w - 6}) for (int yy : new int[]{0, h - 6}) {
                g.fill(xx, yy, xx + 6, yy + 6, 0xFFB7BBAE);
                g.fill(xx + 2, yy + 2, xx + 4, yy + 4, 0xFF424B45);
            }
        }
        if (mob.enchanted) {
            CardRenderer.drawDiagonalShimmer(g, inset, inset, w - inset * 2, h - inset * 2, 6, 0x70);
        }
        if (mob.litTnt && !mob.detonated) {
            int fx = w - 8, fy = 5;
            g.fill(fx-4, fy+1, fx, fy+3, 0xFF50392A);
            g.fill(fx-1, fy-1, fx+2, fy+2, 0xFFF47B31);
            if (((this.now / 150 + mob.mid) & 1) == 0)
                g.fill(fx, fy-2, fx+2, fy, 0xFFFFE68E);
        }
        if (mob.frozen) MatchArt.frozenCoating(g, w, h, this.now, mob.mid);
        if (mob.withered) MatchArt.witherDecay(g, w, h, this.now, mob.mid);
        if (!mob.potions.isEmpty()) potionSwirl(g, mob, w, h);
        if (flash > 0) {
            g.fill(0, 0, w, h, MatchArt.alpha(0xFFFFFFFF, flash * 0.8F));
        }
        if (mob.invulnerable) MatchArt.invulnerableBarrier(g, w, h, this.now);
        if (mob.totem) MatchArt.totemBlessing(g, w, h, this.now, mob.mid);
        // Little icons along the top for what's on the mob.
        int icon = Math.max(7, w / 5);
        int iconX = inset + 1;
        if (mob.totem) {
            drawItem(g, new ItemStack(Items.TOTEM_OF_UNDYING), iconX + icon / 2.0F, inset + icon / 2.0F + 1, icon / 16.0F);
            iconX += icon + 1;
        }
        if (mob.withered) {
            // Stack secondary statuses on the left when the lock occupies the top-right corner.
            int iconY = inset + 1;
            if (mob.invulnerable && mob.totem) { iconX = inset + 1; iconY += icon + 1; }
            drawItem(g, new ItemStack(Items.WITHER_ROSE), iconX + icon / 2.0F, iconY + icon / 2.0F, icon / 16.0F);
        }
        if (mob.invulnerable) MatchArt.invulnerableLock(g, w - 10, 2);
        // One small bottle per potion on the mob, down the left edge.
        for (int i = 0; i < mob.potions.size() && i < 3; i++) {
            var potion = mob.potions.get(i);
            float py = inset + (icon + 1) * (i + 1) + icon / 2.0F + 1;
            drawItem(g, CardRenderer.placeholderArt(potion.id(), Cards.get(potion.id())), inset + 1 + icon / 2.0F, py, icon / 16.0F);
            if (potion.brew() != 0) g.fill(inset + icon - 1, Math.round(py - icon / 2.0F), inset + icon + 1, Math.round(py - icon / 2.0F) + 2, 0xFF7DFF5A);
        }
        int badge = Math.min(Math.max(12, Math.round(h * 0.34F)), Math.max(8, w * 2 / 3));
        int badgeY = h - badge * 3 / 4;
        int baseAttack = card.attack();
        int shownAttack = mob.block ? 0 : mob.attack;
        int attackColor = shownAttack > baseAttack ? 0xFF7DFF5A : 0xFFFFFFFF;
        if (!mob.block) MatchArt.attack(g, this.font, -badge / 4, badgeY, badge, shownAttack, attackColor, mob.ranged);
        int healthColor = mob.health < mob.maxHealth ? 0xFFFF5A5A : mob.maxHealth > card.health() ? 0xFF7DFF5A : 0xFFFFFFFF;
        MatchArt.heart(g, this.font, w - badge * 3 / 4, badgeY, badge, mob.health, healthColor);
        if (mob.shield) MatchArt.shieldBadge(g, w / 2 - 5, -8);
        // Limited uses: a small counter where the Attack badge would be (blocks) or at the top-left (mobs).
        if (mob.usesLeft >= 0) {
            int ux = mob.block ? -badge / 4 : -4, uy = mob.block ? badgeY + badge / 4 : -4, us = Math.max(9, badge * 2 / 3);
            g.fill(ux, uy, ux + us, uy + us, 0xFF3A2A1A);
            g.outline(ux, uy, us, us, 0xFFFFD27A);
            MatchArt.outlined(g, this.font, String.valueOf(mob.usesLeft), ux + us / 2.0F, uy + (us - 8) / 2.0F + 1, 0xFFFFE9B0, 1.0F, true);
        }
        // A thrown Trident sticks out of the mob's top-right corner.
        if (mob.impaled != null) {
            g.pose().pushMatrix();
            g.pose().translate(w - 4, 2);
            g.pose().rotate((float) (Math.PI * 0.75));
            g.pose().scale(1.2F, 1.2F);
            g.item(CardRenderer.placeholderArt(mob.impaled, Cards.get(mob.impaled)), -8, -8);
            g.pose().popMatrix();
        }
        // Last, outside the whole frame: the Shield plate used to paint over the targeting outline.
        Spot probe = new Spot(Math.round(x), Math.round(y), w, h, side, mob.mid, -1);
        if (mob.mid > 0 && this.attacker != null && isValidTarget(probe)) {
            if (mob.shield) {
                MatchArt.shieldOutline(g, -7, -7, w + 14, h + 14, 0,
                        hovered ? 0xFFFF7063 : MatchArt.RED, 2);
                if (hovered) MatchArt.shieldOutline(g, -5, -5, w + 10, h + 10, 0, 0xFFFFFFFF, 1);
            } else {
                drawTargetHighlight(g, -4, -4, w + 8, h + 8, hovered);
            }
        } else if (mob.mid > 0 && (this.previewSource != null || this.aimingPower || this.aimingExcavate)) {
            EffectPreview.Highlight preview = this.aimingExcavate
                    ? (validExcavateSpot(probe) ? new EffectPreview.Highlight(false, true) : null)
                    : this.aimingPower
                    ? (validFeedSpot(probe) ? this.shown.me().power == HeroPowers.Power.THROW_POTION ? new EffectPreview.Highlight(true, true) : this.shown.me().power == HeroPowers.Power.ACTIVATE || mob.health < mob.maxHealth ? new EffectPreview.Highlight(false, true) : null : null)
                    : EffectPreview.forTarget(this.previewSource,
                    new EffectPreview.Subject(EffectPreview.Kind.BOARD, mob.id, side, mob.mid, mob.health, mob.maxHealth,
                            mob.invulnerable, mob.frozen, mob.withered, mob.totem, mob.attackDebuff, false, mob.appliedBenefit));
            if (preview != null) {
                boolean selected = hovered && isValidTarget(probe);
                drawPlayPreview(g, w, h, mob.shield, selected ? new EffectPreview.Highlight(preview.harmful(), false) : preview);
                if (selected) {
                    if (mob.shield) MatchArt.shieldOutline(g, -5, -5, w + 10, h + 10, 0, 0xFFFFFFFF, 1);
                    else g.outline(-2, -2, w + 4, h + 4, 0xFFFFFFFF);
                }
            }
        }
        g.pose().popMatrix();
    }

    private EffectPreview.Source effectSource(Identifier card, int side, int mid, boolean ongoing) {
        var tool = this.shown.sides[side].tool;
        List<EffectPreview.Subject> board = new ArrayList<>();
        for (int owner = 0; owner < 2; owner++) for (Mob mob : this.shown.sides[owner].board)
            board.add(new EffectPreview.Subject(EffectPreview.Kind.BOARD, mob.id, owner, mob.mid, mob.health, mob.maxHealth,
                    mob.invulnerable, mob.frozen, mob.withered, mob.totem, mob.attackDebuff, false, mob.appliedBenefit));
        return new EffectPreview.Source(card, side, mid, ongoing, tool == null ? 0 : tool.attack, board);
    }

    private int previewColor(EffectPreview.Highlight preview) {
        return MatchArt.alpha(preview.harmful() ? 0xFFFF5147 : 0xFFFFD84A,
                0.8F + 0.2F * (float) Math.sin(this.now / 220.0));
    }

    private void drawRoundPreview(GuiGraphicsExtractor g, int x, int y, int size, EffectPreview.Highlight preview) {
        if (preview == null) return;
        MatchArt.octagonOutline(g, x - 5, y - 5, size + 10, previewColor(preview), 2, preview.possible());
    }

    private void drawPlayPreview(GuiGraphicsExtractor g, int width, int height, boolean shield, EffectPreview.Highlight preview) {
        int edge = shield ? 7 : 4;
        int top = -edge;
        int w = width + edge * 2, h = height + edge * 2;
        int color = previewColor(preview);
        if (preview.possible()) {
            // Broken gold corners mean eligible, not a promise that a random/selected effect hits all of them.
            int length = Math.min(6, w / 3);
            for (int x : new int[]{-edge, width + edge - length}) {
                g.fill(x, top, x + length, top + 2, color);
                g.fill(x, top + h - 2, x + length, top + h, color);
            }
            for (int x : new int[]{-edge, width + edge - 2}) {
                g.fill(x, top, x + 2, top + length, color);
                g.fill(x, top + h - length, x + 2, top + h, color);
            }
        } else if (shield) {
            MatchArt.shieldOutline(g, -edge, top, w, h, 0, color, 2);
        } else {
            g.outline(-edge, top, w, h, color);
            g.outline(-edge + 1, top + 1, w - 2, h - 2, color);
        }
    }

    private void drawTargetHighlight(GuiGraphicsExtractor g, int x, int y, int width, int height, boolean hovered) {
        int color = hovered ? 0xFFFF7063 : MatchArt.RED;
        g.outline(x, y, width, height, color);
        g.outline(x + 1, y + 1, width - 2, height - 2, color);
        if (hovered) {
            // A third bright edge makes the target under the cursor distinct from the other legal targets.
            g.outline(x + 2, y + 2, width - 4, height - 4, 0xFFFFFFFF);
        }
    }

    // The enemy fan mirrors the local arc at the opposite screen edge.
    private float[] theirHandSlot(int index, int count) {
        int w = Math.round(CardRenderer.WIDTH * this.backScale);
        int h = Math.round(CardRenderer.HEIGHT * this.backScale);
        float step = Math.min(w * 0.62F, this.width * 0.36F / Math.max(1, count));
        float apart = Math.min(6.0F, 42.0F / Math.max(1, count));
        HandFan fan = HandFan.at(index, count, step, apart);
        return new float[]{this.centerX + (float)fan.x() - w / 2.0F, -h * 0.42F - (float)fan.drop(), -(float)fan.angle()};
    }

    // Centre, scale and rotation of that exact card, including a draw/hand movement still in progress.
    private float[] myHandOrigin(Side side, int index) {
        if (index >= 0 && index < side.hand.size()) {
            Anim anim = this.anims.get(side.hand.get(index).uid);
            if (anim != null && anim.placed) return new float[]{anim.x + this.handW / 2F, anim.y + this.handH / 2F};
        }
        int count = Math.max(1, side.handCount);
        float step = Math.min(this.handW * .55F, this.width * .45F / count);
        return new float[]{this.centerX + (Math.clamp(index,0,count-1) - (count-1)/2F)*step, this.handTop + this.handH/2F};
    }

    private float[] opponentHandOrigin(Side side, int handIndex) {
        int count = side.backs.size();
        int index = Math.max(0, Math.min(Math.max(0, count - 1), handIndex));
        Anim anim = count == 0 ? null : this.anims.get(side.backs.get(index));
        float[] slot = theirHandSlot(index, Math.max(1, count));
        float halfW = Math.round(CardRenderer.WIDTH * this.backScale) / 2.0F;
        float halfH = Math.round(CardRenderer.HEIGHT * this.backScale) / 2.0F;
        if (anim != null && anim.placed) {
            return new float[]{anim.x + halfW, anim.y + halfH, this.backScale * anim.scale, anim.rotation};
        }
        return new float[]{slot[0] + halfW, slot[1] + halfH, this.backScale, slot[2]};
    }

    private void drawTheirHand(GuiGraphicsExtractor g) {
        Side side = this.shown.them();
        int count = side.backs.size();
        int w = Math.round(CardRenderer.WIDTH * this.backScale);
        int h = Math.round(CardRenderer.HEIGHT * this.backScale);
        Sleeves.Design design = Sleeves.get(side.sleeve);
        for (int i = 0; i < count; i++) {
            float[] slot = theirHandSlot(i, count);
            Anim anim = ease(side.backs.get(i), slot[0], slot[1], 1.0F, slot[2]);
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
    private void updateHandHover(int mouseX, int mouseY) {
        List<HandCard> hand = this.shown.me().hand;
        int count = hand.size();
        float available = Math.min(this.width * 0.5F, count * this.handW * 0.8F);
        float step = count <= 1 ? 0 : Math.min(this.handW * 0.8F, (available - this.handW) / (count - 1));
        float total = step * (count - 1) + this.handW;
        float startX = this.centerX - total / 2.0F;
        int hovered = -1;
        boolean free = this.dragging == null && this.attacker == null && this.awaitingTarget == null && !this.aimingPower && !this.aimingExcavate && !this.choosingExcavate && !this.menuOpen;
        for (int i = 0; i < count; i++) {
            int x = Math.round(startX + i * step);
            int width = i == count - 1 ? this.handW : Math.round(step);
            Spot spot = new Spot(x, this.handTop, width, this.height - this.handTop, this.shown.you, -1, i);
            this.handSpots.add(spot);
            if (free && spot.contains(mouseX, mouseY) && !isReturning(hand.get(i))) {
                hovered = i;
            }
        }
        this.hoveredHand = hovered;
    }

    private void drawMyHand(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        List<HandCard> hand = this.shown.me().hand;
        if(this.readOnly && hand.isEmpty() && this.shown.me().handCount>0){
            int n=this.shown.me().handCount;float step=Math.min(this.handW*.55F,this.width*.45F/Math.max(1,n));
            for(int i=0;i<n;i++)CardRenderer.drawBack(g,Sleeves.get(this.shown.me().sleeve),Math.round(this.centerX-(n-1)*step/2-this.handW/2F+i*step),this.handTop,this.handScale);
            return;
        }
        int count = hand.size();
        float available = Math.min(this.width * 0.5F, count * this.handW * 0.8F);
        float step = count <= 1 ? 0 : Math.min(this.handW * 0.8F, (available - this.handW) / (count - 1));
        float startX = this.centerX - (step * (count - 1) + this.handW) / 2.0F;
        int hovered = this.hoveredHand;
        // A smooth fan: the middle cards highest, the outer ones dropping and tilting away.
        float apart = Math.min(5.0F, 32.0F / Math.max(1, count));
        for (int i = 0; i < count; i++) {
            HandCard card = hand.get(i);
            HandFan fan = HandFan.at(i, count, Math.max(1.0F, step), apart);
            float x = count <= 1 ? startX : this.centerX + (float)fan.x() - this.handW / 2.0F;
            float y = this.handTop + (float)fan.drop();
            Anim anim = ease(card.uid, x, y, 1.0F, (float)fan.angle());
            if (card == this.dragging || card == this.awaitingTarget || card.uid == this.hiddenUid || i == hovered || isReturning(card)) {
                continue;
            }
            drawHandCard(g, card, anim);
        }
        // Returning cards stay above the rest of the fan throughout their journey back from the board.
        for (HandCard card : hand) {
            if (isReturning(card)) {
                drawHandCard(g, card, this.anims.get(card.uid));
            }
        }
    }

    private boolean isReturning(HandCard card) {
        Anim anim = this.anims.get(card.uid);
        return anim != null && this.now < anim.returnUntil;
    }

    private void cancelPendingTarget() {
        HandCard card = this.awaitingTarget;
        if (card == null) return;
        // The card never left the real hand. Start its existing hand animation at the preview's board slot.
        int index = this.shown.me().board.size();
        float centerX = this.pendingSpot == null ? laneX(this.shown.you, index, pendingBlock()) + this.mobW / 2.0F : this.pendingSpot.centerX();
        float centerY = this.pendingSpot == null ? laneY(this.shown.you, pendingBlock()) + mobH / 2.0F : this.pendingSpot.centerY();
        returnToHand(card, centerX, centerY, Math.min(this.mobW / (float) Math.max(1, this.handW), this.mobH / (float) Math.max(1, this.handH)));
        this.awaitingTarget = null;
        this.pendingSpot = null;
    }

    private void returnToHand(HandCard card, float centerX, float centerY, float scale) {
        Anim anim = this.anims.computeIfAbsent(card.uid, k -> new Anim());
        anim.scale = scale;
        anim.x = centerX - this.handW * anim.scale / 2.0F;
        anim.y = centerY - this.handH * anim.scale / 2.0F;
        anim.rotation = 0;
        anim.placed = true;
        anim.holdUntil = 0;
        anim.flipAt = -1;
        anim.returnUntil = this.now + 450;
        this.hoveredHand = -1;
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
            CardRenderer.drawTrial(g, this.font, card.id, card.enchanted, card.training, card.brew, 0, 0, scale);
            if (!playable && this.shown.myTurn) {
                g.fill(0, 0, Math.round(width), Math.round(height), 0x50000000);
            }
        }
        g.pose().popMatrix();
    }

    // The card being dragged, or the aiming arrow for an attack or a Spawn target.
    private void drawInteraction(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (this.aimingPower || this.aimingExcavate) {
            int[] power = this.powerSpots[this.shown.you];
            if (power != null) MatchArt.arrow(g, power[0] + power[2] / 2, power[1] + power[3] / 2, mouseX, mouseY, 0xFFFFD84A, this.now);
            MatchArt.outlined(g, this.font, this.aimingExcavate ? "Excavate: select a friendly Ore · Right-click to cancel" : this.shown.me().power.displayName().getString() + (this.shown.me().power == HeroPowers.Power.ACTIVATE ? ": drag to a friendly Activate Block" : ": drag to a mob, block or hero") + " · Release to use", this.centerX, this.middle, MatchArt.GOLD_LIGHT, 0.8F, true);
        }
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
                this.dragMotion.begin(g, mouseX, mouseY, w);
                CardRenderer.draw(g, this.font, CardItem.of(this.dragging.id, this.dragging.enchanted), -w / 2, -h / 2, scale);
                CardDefinition held = Cards.get(this.dragging.id);
                if (held.isMob() && overBoard) {
                    boolean block = held.keywords().contains(Keyword.BLOCK);
                    int count = rowCount(shown.you, block);
                    int limit = Match.rowLimit(block);
                    boolean valid = count < limit && overPlayArea(mouseX, mouseY);
                    int color = valid ? 0xFF9DE3A3 : 0xFFFF8581;
                    g.outline(-w/2-1, -h/2-1, w+2, h+2, color);
                    if (!valid) MatchArt.outlined(g, font, count >= limit ? (block ? "Block" : "Mob") + " row full (" + count + "/" + limit + ")" : "Release over the board",
                            0, h/2+2, color, .8F, true);
                }
                g.pose().popMatrix();
            }
        }
        if (this.attacker != null) {
            MatchArt.arrow(g, this.attacker.centerX(), this.attacker.centerY(), mouseX, mouseY, MatchArt.RED, this.now);
        }
        if (this.awaitingTarget != null && this.pendingSpot != null) {
            MatchArt.arrow(g, this.pendingSpot.centerX(), this.pendingSpot.centerY(), mouseX, mouseY, MatchArt.RED, this.now);
        }
    }

    // Hovering shows a card beside the thing you hover, never over the middle of the board:
    // - a card in your hand lifts up big;
    // - a mob: its card next to it, with any status it has written under the card;
    // - a hero's tool or class power: its card above it (yours) or below it (theirs);
    // - a battle log icon: the card it was about, with what happened written under it.
    // Keyword explanations sit beside the enlarged card, as in Hearthstone.
    private void drawPreview(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        MatchCardHover hover = cardHover();
        if (this.dragging != null || this.attacker != null || this.awaitingTarget != null
                || this.aimingPower || this.aimingExcavate || this.choosingExcavate) {
            hover.clear();
            return;
        }
        hover.prepare(font, width, height, shown);
        if (drawPinned(hover, g, mouseX, mouseY)) return;
        hover.pinned(false);
        if (hoveredHand >= 0 && hoveredHand < shown.me().hand.size() && hoveredHand < handSpots.size()) {
            HandCard card = shown.me().hand.get(hoveredHand);
            hover.hand(g, card.id, card.enchanted, card.training, card.brew, handSpots.get(hoveredHand).x() + handW / 2,
                    boardLeft, card.playable && canAct(), mouseX, mouseY);
            return;
        }
        if (hover.drawRetained(g, mouseX, mouseY)) return;
        for (int s = 0; s < 2; s++) {
            Side side = shown.sides[s];
            if (inside(toolSpots[s], mouseX, mouseY) && side.tool != null) {
                hover.tool(g, side.tool, toolSpots[s], s == shown.you);
                return;
            }
            if (inside(powerSpots[s], mouseX, mouseY) && side.power != HeroPowers.Power.NONE) {
                hover.power(g, side.power, side.cardClass, shown.powerCost, side.powerUsed, powerSpots[s], s == shown.you);
                return;
            }
        }
        for (Spot spot : mobSpots) {
            if (!spot.contains(mouseX, mouseY)) continue;
            Mob mob = shown.sides[spot.side()].mob(spot.mid());
            hover.unit(g, mob, spot.side() == shown.you && shown.myTurn,
                    new int[]{spot.x(), spot.y(), spot.width(), spot.height()}, mouseX, mouseY);
            return;
        }
        for (HistorySpot spot : this.historySpots) {
            if (spot.line() == null || spot.line().card() == null || Cards.get(spot.line().card()) == null
                    || !(mouseX >= spot.x() && mouseX < spot.x() + spot.size() && mouseY >= spot.y() && mouseY < spot.y() + spot.size())) {
                continue;
            }
            // The card, with what happened written in a box under it (not over it).
            float scale = Math.min(0.7F, this.height * 0.42F / CardRenderer.HEIGHT);
            int w = Math.round(CardRenderer.WIDTH * scale);
            int h = Math.round(CardRenderer.HEIGHT * scale);
            int boxWidth = Math.max(w, 120);
            List<FormattedCharSequence> lines = this.font.split(Component.literal(spot.line().text()), boxWidth - 8);
            int boxHeight = lines.size() * this.font.lineHeight + 7;
            int x = spot.x() + spot.size() + 8;
            int y = Math.max(4, Math.min(this.height - h - boxHeight - 8, spot.y() - (h + boxHeight) / 2));
            hover.history(g, spot.line().card(), x, y, boxWidth, scale, lines, mouseX, mouseY);
            return;
        }
    }

    private boolean pinActive() { return this.pinnedHandUid >= 0 || this.pinnedSide >= 0; }

    private void unpin() { this.pinnedHandUid = -1; this.pinnedSide = this.pinnedMid = -1; }

    // The pinned card stays open, wherever the pointer goes, until it is unpinned or leaves.
    private boolean drawPinned(MatchCardHover hover, GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (!pinActive()) return false;
        if (this.pinnedHandUid >= 0) {
            var hand = shown.me().hand;
            for (int i = 0; i < hand.size() && i < handSpots.size(); i++) {
                HandCard card = hand.get(i);
                if (card.uid != this.pinnedHandUid) continue;
                hover.pinned(true);
                hover.hand(g, card.id, card.enchanted, card.training, card.brew, handSpots.get(i).x() + handW / 2,
                        boardLeft, card.playable && canAct(), mouseX, mouseY);
                return true;
            }
        } else {
            for (Spot spot : mobSpots) {
                if (spot.side() != this.pinnedSide || spot.mid() != this.pinnedMid) continue;
                Mob mob = shown.sides[spot.side()].mob(spot.mid());
                if (mob == null) break;
                hover.pinned(true);
                hover.unit(g, mob, spot.side() == shown.you && shown.myTurn,
                        new int[]{spot.x(), spot.y(), spot.width(), spot.height()}, mouseX, mouseY);
                return true;
            }
        }
        unpin();
        return false;
    }

    // Right-click a hand card or a mob to pin its inspect; again (or anywhere else) to let it go.
    private boolean togglePin(double mouseX, double mouseY) {
        if (hoveredHand >= 0 && hoveredHand < shown.me().hand.size()) {
            long uid = shown.me().hand.get(hoveredHand).uid;
            boolean same = this.pinnedHandUid == uid;
            unpin();
            if (!same) this.pinnedHandUid = uid;
            return true;
        }
        for (Spot spot : mobSpots) {
            if (!spot.contains(mouseX, mouseY) || shown.sides[spot.side()].mob(spot.mid()) == null) continue;
            boolean same = this.pinnedSide == spot.side() && this.pinnedMid == spot.mid();
            unpin();
            if (!same) { this.pinnedSide = spot.side(); this.pinnedMid = spot.mid(); }
            return true;
        }
        if (pinActive()) { unpin(); return true; }
        return false;
    }

    private boolean overHandSlot(double x, double y) {
        for (Spot spot : handSpots) if (spot.contains(x,y)) return true;
        return false;
    }

    private boolean associatedPreviewValid() {
        return cardHover().valid(shown, width, height)
                && !this.menuOpen && !this.shown.over && this.shown.playing && !coinShowing()
                && !this.choosingExcavate && this.dragging == null && this.attacker == null
                && this.awaitingTarget == null && !this.aimingPower && !this.aimingExcavate
                && !this.handPicks().covering(this.shown, this.now);
    }

    // Cursor tooltips cover deck names and log lines without cards. Card previews have their own side notes.
    private void drawTooltips(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (associatedPreviewValid() && cardHover().overAssociatedPreview(mouseX, mouseY)) return;
        if (this.dragging != null || this.attacker != null) {
            return;
        }
        if (hoveredCharm != null) {
            var charm = hoveredCharm.charm();
            String status = hoveredCharm.spent() ? "Spent this turn" : shown.myTurn ? "Ready" : "Ready on your turn";
            Tooltips.show(g, font, List.of(Component.literal(charm.title()).withStyle(ChatFormatting.GOLD),
                    Component.literal(charm.description()), Component.literal(status).withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
            return;
        }
        List<Component> lines = new ArrayList<>();
        for (Spot spot : this.mobSpots) if (spot.side() == this.shown.you && spot.contains(mouseX, mouseY)) {
            Mob mob = this.shown.me().mob(spot.mid());
        }
        for (int s = 0; s < 2; s++) {
            var foe = this.shown.sides[s].trialFoe;
            if (foe != null && !foe.abilityName().isBlank() && this.heroSpots[s] != null && this.heroSpots[s].contains(mouseX, mouseY)) {
                lines.add(Component.literal(foe.name()).withStyle(foe.boss()?ChatFormatting.GOLD:ChatFormatting.AQUA));
                lines.add(Component.literal(foe.abilityName()).withStyle(ChatFormatting.BOLD));
                lines.add(Component.literal(foe.abilityText()));
                if (foe.horn() >= 0) lines.add(Component.literal("Next horn in " + foe.horn() + " enemy turn" + (foe.horn()==1?"":"s") + ".").withStyle(ChatFormatting.YELLOW));
            }
            var omen = this.shown.sides[s].badOmen;
            if (omen != null && this.heroSpots[s] != null && this.heroSpots[s].contains(mouseX, mouseY)) {
                lines.add(Keyword.BAD_OMEN.displayName().copy().withStyle(ChatFormatting.LIGHT_PURPLE));
                lines.add(Keyword.BAD_OMEN.description());
                lines.add(Component.translatable("keyword.craftbound.bad_omen.summons", Cards.name(omen)));
            }
            if (inside(this.nameplateSpots[s], mouseX, mouseY)) {
                lines.add(Component.translatable("match_ui.craftbound.deck_name", deckName(this.shown.sides[s])).withStyle(ChatFormatting.GRAY));
            }
        }
        for (HistorySpot spot : this.historySpots) {
            boolean over = mouseX >= spot.x() && mouseX < spot.x() + spot.size() && mouseY >= spot.y() && mouseY < spot.y() + spot.size();
            if (over && spot.line() != null && (spot.line().card() == null || Cards.get(spot.line().card()) == null)) {
                lines.add(Component.literal(spot.line().text()));
            }
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
                CardRenderer.drawTrial(g, this.font, card.id, card.enchanted, card.training, card.brew, 0, 0, slot[2]);
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
            this.shatteredHero=loser;this.shatterAt=this.now+140;
            int size=this.heroSize,x=Math.round(at[0]-size/2F),y=Math.round(at[1]-size/2F);
            effect(0,140,false,(g,t)->MatchArt.octagonOutline(g,x-3,y-3,size+6,MatchArt.alpha(0xFFFFE2B4,t),2));
            portraitFragments(x-4,y-4,size+8,140,820,(g,t)->{
                MatchArt.octagon(g,x-4,y-4,size+8,0xFF1A1512);
                MatchArt.octagon(g,x-2,y-2,size+4,classColor(this.shown.sides[loser].cardClass));
                drawFace(g,this.shown.sides[loser],x+4,y+4,size-8);
            });
            schedule(140,()->{screenShake(4,310);MatchSounds.play("entity.generic.explode",.52F,.8F); ripple(at,0xFFFFC991,52,500);
                int first=this.particles.size();
                particles(at[0],at[1],18,0xFFE5B96F,55,0,1);
                for(int i=first;i<this.particles.size();i++)this.particles.get(i).life=.25F+this.random.nextFloat()*.15F;
            });
        }
        if(!draw) schedule(255,()->MatchSounds.play("block.deepslate.break",.53F,.58F));
        schedule(GAME_OVER_DELAY, () -> {
            if (draw) {
                MatchSounds.play("block.note_block.bass", 0.8F, 0.8F);
            } else if (won) {
                MatchSounds.play("ui.toast.challenge_complete", 1.0F, 0.8F);
                schedule(280,()->MatchSounds.play("block.amethyst_block.chime", 1.42F, .55F));
            } else {
                MatchSounds.play("block.beacon.deactivate", .74F, .7F);
                schedule(300,()->MatchSounds.play("block.note_block.bass",.59F,.6F));
            }
        });
    }

    private boolean gameOverShowing() {
        return this.shown.over && this.gameOverStart >= 0 && this.now - this.gameOverStart >= GAME_OVER_DELAY;
    }

    private void drawGameOver(GuiGraphicsExtractor g) {
        float t = MatchArt.clamp((this.now - this.gameOverStart - GAME_OVER_DELAY) / (float)RESULT_FADE);
        g.fill(0, 0, this.width, this.height, MatchArt.alpha(0xB0000000, t));
        String key = this.shown.winner < 0 ? "match_ui.craftbound.draw" : this.shown.winner == this.shown.you ? "match_ui.craftbound.victory" : "match_ui.craftbound.defeat";
        boolean draw=shown.winner<0, won=!draw&&shown.winner==shown.you;
        float cx=width*.5F,cy=height*.5F;
        int ink=draw?0xFFCFCDC6:won?0xFFFFD979:0xFFE29A92;
        float age=(now-gameOverStart-GAME_OVER_DELAY)/1000F;
        float spread=Math.min(width*.32F,130)*MatchArt.easeOut(t);
        for(int sign:new int[]{-1,1}){
            int edge=Math.round(cx+sign*spread),mid=Math.round(cx);
            g.fill(Math.min(edge,mid),(int)cy+9,Math.max(edge,mid),(int)cy+10,MatchArt.alpha(ink,.65F*t));
        }
        if(!draw && age<2.8F) for(int i=0;i<(won?38:20);i++){
            float u=age-(i%9)*.045F;if(u<=0 || u>2.2F)continue;
            float vx=(i%2==0?1:-1)*(15+(i*31)%95);
            float px=cx+vx*u,py=won?cy+6-(25+i*17%68)*u+24*u*u:cy-20+(8+i*13%24)*u*u;
            int sz=i%5==0?2:1;
            g.fill((int)px,(int)py,(int)px+sz,(int)py+sz,MatchArt.alpha(i%3==0?0xFFFFE9C3:ink,Math.max(0,1-u/2.2F)*t));
        }
        float scale = (won?3.6F:3.2F) * (.82F+.18F*MatchArt.easeOut(t));
        MatchArt.outlined(g, this.font, Component.translatable(key), this.width / 2.0F, this.height / 2.0F - 24 - 4 * scale,
                MatchArt.alpha(ink,t), scale, true);
        if (t >= 1) {
            MatchArt.outlined(g, this.font, Component.translatable("match_ui.craftbound.click_to_leave"), this.width / 2.0F, this.height / 2.0F + 20,
                    MatchArt.TEXT, 1.0F, true);
        }
    }

    // Esc: keep playing, concede, or request a shared pause saved at the physical table.
    private void drawExcavate(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.nextStratum();
        g.fill(0, 0, this.width, this.height, 0xB0000000);
        int width = Math.min(288, this.width - 16), height = 144;
        int x = (this.width - width) / 2, y = Math.max(4, (this.height - height) / 2);
        g.fill(x, y, x + width, y + height, MatchArt.WOOD_DARK);
        g.outline(x, y, width, height, MatchArt.GOLD);
        CardRenderer.fitText(g, this.font, Component.literal("Excavate · Choose one · " + this.shown.powerCost + " XP"),
                x + 10, y + 10, width - 20, MatchArt.GOLD_LIGHT, false);
        boolean ore = hasExcavatableOre();
        this.excavateMineButton = new int[]{x + 10, y + 30, width - 20, 23};
        this.excavateToolButton = new int[]{x + 10, y + 73, width - 20, 23};
        this.excavateCancelButton = new int[]{x + width / 2 - 42, y + 116, 84, 20};
        MatchArt.button(g, this.font, Component.literal(ore ? "Mine a friendly Ore" : "Mine · No friendly Ore"),
                x + 10, y + 30, width - 20, 23, ore ? 0xFF8D6938 : 0xFF555555,
                MatchArt.WOOD_DARK, 0xFFFFFFFF, ore && inside(this.excavateMineButton, mouseX, mouseY) ? 1 : 0);
        CardRenderer.fitText(g, this.font, Component.literal("Remove the chosen Ore and trigger its Drop."),
                x + 12, y + 57, width - 24, MatchArt.TEXT, false);
        MatchArt.button(g, this.font, Component.literal("Equip a Wooden Pickaxe · 1 / 2"),
                x + 10, y + 73, width - 20, 23, 0xFF8D6938,
                MatchArt.WOOD_DARK, 0xFFFFFFFF, inside(this.excavateToolButton, mouseX, mouseY) ? 1 : 0);
        CardRenderer.fitText(g, this.font, Component.literal(this.shown.me().tool == null
                        ? "1 Attack · 2 Durability · No extra ability."
                        : "Replaces your current tool and its enchantments."),
                x + 12, y + 100, width - 24, this.shown.me().tool == null ? MatchArt.TEXT : 0xFFFFCB73, false);
        MatchArt.button(g, this.font, Component.literal("Cancel"), this.excavateCancelButton[0], y + 116, 84, 20,
                0xFF635244, MatchArt.WOOD_DARK, 0xFFFFFFFF, inside(this.excavateCancelButton, mouseX, mouseY) ? 1 : 0);
    }

    private void drawMenu(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.fill(0, 0, this.width, this.height, 0xA0000000);
        int width = 140;
        int height = 22;
        int x = this.width / 2 - width / 2;
        int y = this.height / 2 - 44;
        this.menuButtons.clear();
        String[] keys = {"match_ui.craftbound.resume", this.confirmConcede ? "match_ui.craftbound.concede_confirm" : "match_ui.craftbound.concede",
                this.pauseRequestedBy==this.shown.you?"match_ui.craftbound.cancel_pause":"match_ui.craftbound.leave_table"};
        for (int i = 0; i < keys.length; i++) {
            int[] button = {x, y + i * (height + 8), width, height};
            this.menuButtons.add(button);
            boolean hover = inside(button, mouseX, mouseY);
            int top = i == 1 ? (hover ? 0xFFC0503A : 0xFF9A3A2A) : (hover ? 0xFFFFE07A : 0xFFFFD25A);
            int bottom = i == 1 ? 0xFF6A2418 : 0xFFE2A52A;
            MatchArt.button(g, this.font, Component.translatable(keys[i]), x, button[1], width, height, top, bottom, 0xFFFFFFFF, 0);
        }
        MatchArt.outlined(g, this.font, Component.translatable(this.pauseRequestedBy>=0?"match_ui.craftbound.pause_waiting":"match_ui.craftbound.leave_hint"), this.width / 2.0F, y + 3 * (height + 8) + 4,
                MatchArt.TEXT_DIM, 1.0F, true);
    }

    // --- Rules the screen needs to know (the server checks them again) ----------------------------------------

    private boolean canAct() {
        if(this.readOnly) return false;
        if (this.awaitingReply && this.now - this.sentAt > 3000) {
            this.awaitingReply = false;
        }
        return this.shown.playing && this.shown.myTurn && !this.shown.over && this.playing == null && this.updates.isEmpty()
                && !this.awaitingReply && !this.menuOpen;
    }

    // Mirrors Match.shieldStops: Shield mobs guard everything, Shield blocks guard the hero and other blocks.
    private boolean shieldStops(Mob target) {
        boolean shieldMob = false, shieldBlock = false;
        for (Mob m : this.shown.them().board) {
            if (!m.shield) continue;
            if (m.block) shieldBlock = true; else shieldMob = true;
        }
        if (shieldMob && !(target != null && target.shield && !target.block)) return true;
        return shieldBlock && (target == null || target.block && !target.shield);
    }

    private boolean heroTargetable(int side) {
        return this.attacker != null && canAct() && isAttackTarget(this.heroSpots[side]);
    }

    // Share the Shield rule between highlights and release handling; spells keep their own target rules.
    private boolean isAttackTarget(Spot spot) {
        return isAttackTargetFor(this.attacker, spot);
    }

    private boolean attackerIgnoresShield(Spot source) {
        if (source == null) return false;
        if (source.mid() == Match.HERO) {
            var tool = this.shown.me().tool;
            var definition = tool == null ? null : Cards.get(tool.id);
            return definition != null && definition.keywords().contains(Keyword.IGNORE_SHIELD);
        }
        Mob mob = this.shown.me().mob(source.mid());
        return mob != null && mob.ignoreShield;
    }

    // A Throw tool (Trident) must stick in a mob, so it can't be thrown at a hero.
    private boolean heroThrows() {
        var tool = this.shown.me().tool;
        var definition = tool == null ? null : Cards.get(tool.id);
        return definition != null && definition.keywords().contains(Keyword.THROW);
    }

    private boolean isAttackTargetFor(Spot source, Spot spot) {
        if (spot != null && spot.side() == this.shown.you) {
            // A Repair tool can be used on your own damaged blocks.
            if (source == null || source.mid() != Match.HERO || spot.mid() == Match.HERO) return false;
            var tool = this.shown.me().tool;
            var definition = tool == null ? null : Cards.get(tool.id);
            Mob mob = this.shown.me().mob(spot.mid());
            return definition != null && definition.keywords().contains(Keyword.REPAIR)
                    && mob != null && mob.block && mob.health > 0 && mob.health < mob.maxHealth;
        }
        if (spot == null) {
            return false;
        }
        if (spot.mid() == Match.HERO) {
            if (source != null && source.mid() == Match.HERO && heroThrows()) return false;
            if (source != null && source.mid() != Match.HERO) {
                Mob attacking = this.shown.me().mob(source.mid());
                if (attacking == null || !attacking.faceReady) return false;
            }
            return attackerIgnoresShield(source) || !shieldStops(null);
        }
        Mob mob = this.shown.sides[spot.side()].mob(spot.mid());
        if (mob == null || !attackerIgnoresShield(source) && shieldStops(mob)) return false;
        if (!mob.block || this.shown.them().board.stream().noneMatch(m -> !m.block && m.health > 0)) return true;
        if (source == null) return false;
        var me = this.shown.me();
        if (source.mid() == Match.HERO) return me.tool != null && me.tool.ranged;
        Mob attacking = me.mob(source.mid());
        return attacking != null && attacking.ranged;
    }

    // Whether this mob is a place you could aim what you're doing right now.
    private boolean isValidTarget(Spot spot) {
        if (this.aimingExcavate) return canAct() && validExcavateSpot(spot);
        if (this.aimingPower) return canAct() && validFeedSpot(spot);
        if (this.attacker != null) {
            return canAct() && isAttackTarget(spot);
        }
        HandCard aiming = this.awaitingTarget != null ? this.awaitingTarget : this.dragging;
        return aiming != null && isValidTargetFor(aiming, spot);
    }

    private boolean validExcavateSpot(Spot spot) {
        if (spot == null || spot.side() != this.shown.you || spot.mid() == Match.HERO) return false;
        Mob mob = this.shown.me().mob(spot.mid());
        return mob != null && mob.health > 0 && mob.block && mob.card() != null
                && mob.card().mobType() == craftbound.card.MobType.ORE;
    }

    private boolean hasExcavatableOre() {
        return this.shown.me().board.stream().anyMatch(m -> m.health > 0 && m.block
                && m.card() != null && m.card().mobType() == craftbound.card.MobType.ORE);
    }

    private boolean validFeedSpot(Spot spot) {
        if (spot == null) return false;
        boolean activate = this.shown.me().power == HeroPowers.Power.ACTIVATE;
        if (spot.mid() == Match.HERO) return !activate;
        Mob mob = this.shown.sides[spot.side()].mob(spot.mid());
        if (mob == null || mob.health <= 0) return false;
        return !activate || spot.side() == this.shown.you && mob.block
                && craftbound.card.CardCatalog.entry(mob.id).effects().stream().anyMatch(e -> e.trigger() == craftbound.card.CardCatalog.Trigger.ACTIVATE);
    }

    private boolean isValidTargetFor(HandCard card, Spot spot) {
        if (spot == null) return false;
        if (spot.mid() == Match.HERO) return card.target.equals("ANY_CHARACTER")
                && craftbound.game.CardEffects.canTargetHero(card.id);
        Mob target = this.shown.sides[spot.side()].mob(spot.mid());
        if (target == null || !craftbound.game.CardEffects.canTarget(card.id, target.id, k -> switch (k) {
            case SHIELD -> target.shield;
            case SPRINT -> target.sprint;
            case RANGED -> target.ranged;
            case IGNORE_SHIELD -> target.ignoreShield;
            default -> target.card() != null && target.card().keywords().contains(k);
        })) return false;
        return switch (card.target) {
            case "ANY_MOB", "ANY_CHARACTER" -> true;
            case "FRIENDLY_MOB" -> spot.side() == this.shown.you;
            case "ENEMY_MOB" -> spot.side() != this.shown.you;
            default -> false;
        };
    }

    // --- Input ------------------------------------------------------------------------------------------------

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (associatedPreviewValid() && !overHandSlot(x,y) && cardHover().scroll(x, y, vertical)) return true;
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (associatedPreviewValid() && !overHandSlot(event.x(),event.y()) && cardHover().click(event.x(), event.y(),
                event.button() == InputConstants.MOUSE_BUTTON_LEFT)) return true;
        cardHover().clear();
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && !this.readOnly && !this.menuOpen && this.shown.playing && !this.shown.over
                && this.dragging == null && this.attacker == null && this.awaitingTarget == null && !this.aimingPower
                && !this.aimingExcavate && !this.choosingExcavate && !this.handPicks().picking(this.shown)
                && togglePin(event.x(), event.y())) return true;
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) unpin();
        if(this.readOnly) return true;
        if(HandConsentOverlay.click(event,width))return true;
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
                        send(MatchActionPayload.REQUEST_PAUSE, 0, null);
                        this.menuOpen = true;
                    }
                    this.confirmConcede = false;
                    return true;
                }
            }
            return true;
        }
        if (this.shown.over) {
            if (gameOverShowing() && this.now - this.gameOverStart > GAME_OVER_DELAY + RESULT_FADE) {
                this.onClose();
            }
            return true;
        }
        // An update can arrive between render and input; never reuse a stale picker hitbox.
        this.handPicks().sync(this.shown, this.now, this.playing != null || !this.updates.isEmpty());
        // A hand effect is waiting for you to pick a card: clicks only pick.
        if (this.handPicks().picking(this.shown)) {
            int pick = left ? this.handPicks().click(this.shown, mouseX, mouseY) : -1;
            if (pick >= 0) send(MatchActionPayload.CHOOSE_CARD, pick, null);
            return true;
        }
        if (this.handPicks().peeking(this.now)) return true;
        if (coinShowing()) {
            // Click to skip the toss.
            this.coinStart = this.now - COIN_TIME;
            return true;
        }
        if (this.choosingExcavate) {
            if (!left || inside(this.excavateCancelButton, mouseX, mouseY)) this.choosingExcavate = false;
            else if (canAct() && this.shown.me().powerUsable) {
                if (inside(this.excavateMineButton, mouseX, mouseY) && hasExcavatableOre()) {
                    this.choosingExcavate = false;
                    this.aimingExcavate = true;
                } else if (inside(this.excavateToolButton, mouseX, mouseY)) {
                    this.choosingExcavate = false;
                    send(MatchActionPayload.POWER, 0, null);
                }
            }
            return true;
        }
        if (!this.shown.playing) {
            return clickMulligan(mouseX, mouseY, left);
        }
        if (!canAct()) {
            return true;
        }
        if (this.aimingExcavate) {
            if (!left) this.aimingExcavate = false;
            else for (Spot spot : this.mobSpots) {
                if (spot.contains(mouseX, mouseY) && validExcavateSpot(spot)) {
                    this.aimingExcavate = false;
                    send(MatchActionPayload.POWER, 0, spot);
                    break;
                }
            }
            return true;
        }
        if (this.aimingPower) {
            // Feed commits on release, never on a second click.
            if (!left) this.aimingPower = false;
            return true;
        }
        // The Enderman is on the board waiting for a target: left-click an enemy mob to pick it. Clicking anything
        // else, or right-clicking, cancels and the card goes back to your hand (nothing was played yet).
        if (this.awaitingTarget != null) {
            if (left) {
                java.util.List<Spot> choices = new java.util.ArrayList<>(this.mobSpots);
                for (Spot hero : this.heroSpots) if (hero != null) choices.add(hero);
                for (Spot spot : choices) {
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
            cancelPendingTarget();
            return true;
        }
        if (!left) return true;
        if (inside(this.endTurnButton, mouseX, mouseY)) {
            this.endTurnPressed = this.now;
            MatchSounds.play("ui.button.click", 1.0F, 0.6F);
            send(MatchActionPayload.END_TURN, 0, null);
            return true;
        }
        int you = this.shown.you;
        if (inside(this.powerSpots[you], mouseX, mouseY)) {
            if (this.shown.me().powerUsable) {
                if (this.shown.me().power == HeroPowers.Power.EXCAVATE) this.choosingExcavate = true;
                else if (HeroPowers.targeted(this.shown.me().power)) this.aimingPower = true;
                else send(MatchActionPayload.POWER, 0, null);
            } else {
                MatchSounds.play("block.note_block.bass", 0.7F, 0.4F);
            }
            return true;
        }
        // Pick up a card from your hand (the top-most card under the mouse wins).
        for (int i = this.handSpots.size() - 1; i >= 0; i--) {
            Spot spot = this.handSpots.get(i);
            if (spot.contains(mouseX, mouseY) && spot.handIndex() < this.shown.me().hand.size()) {
                HandCard card = this.shown.me().hand.get(spot.handIndex());
                if (card.playable && !isReturning(card)) {
                    this.dragging = card;
                    this.dragMotion.reset((float) mouseX, (float) mouseY);
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

    // Ends the gesture even on an invalid release, so Feed cannot stay attached to the cursor.
    private Spot releaseFeed(int button, double mouseX, double mouseY) {
        if (!this.aimingPower) return null;
        this.aimingPower = false;
        if (button != InputConstants.MOUSE_BUTTON_LEFT || !canAct() || !this.shown.me().powerUsable
                || !HeroPowers.targeted(this.shown.me().power)) return null;
        for (Spot spot : this.mobSpots)
            if (spot.contains(mouseX, mouseY) && validFeedSpot(spot)) return spot;
        for (Spot spot : this.heroSpots)
            if (spot != null && spot.contains(mouseX, mouseY) && validFeedSpot(spot)) return spot;
        return null;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (this.aimingPower) {
            Spot target = releaseFeed(event.button(), mouseX, mouseY);
            if (target != null) send(MatchActionPayload.POWER, 0, target);
            return true;
        }
        if (this.dragging != null) {
            HandCard card = this.dragging;
            this.dragging = null;
            if (!canAct()) {
                return true;
            }
            int index = this.shown.me().hand.indexOf(card);
            CardDefinition definition = Cards.get(card.id);
            boolean isMob = definition.isMob();
            boolean block = definition.keywords().contains(Keyword.BLOCK);
            if (isMob && (rowCount(shown.you, block) >= Match.rowLimit(block) || !overPlayArea(mouseX, mouseY))) {
                returnToHand(card, (float) mouseX, (float) mouseY, .9F);
                return true;
            }
            Spot target = null;
            for (Spot spot : this.mobSpots) {
                if (!isMob && spot.contains(mouseX, mouseY) && isValidTargetFor(card, spot)) {
                    target = spot;
                }
            }
            for (Spot spot : this.heroSpots)
                if (!isMob && spot != null && spot.contains(mouseX, mouseY) && isValidTargetFor(card, spot)) target = spot;
            boolean overBoard = overPlayArea(mouseX, mouseY);
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
                boolean anyTarget = this.mobSpots.stream().anyMatch(spot -> isValidTargetFor(card, spot))
                        || java.util.Arrays.stream(this.heroSpots).anyMatch(spot -> isValidTargetFor(card, spot));
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
                if (spot.contains(mouseX, mouseY) && isAttackTargetFor(from, spot)) {
                    send(MatchActionPayload.ATTACK, from.mid(), spot);
                    return true;
                }
            }
            Spot hero = this.heroSpots[1 - this.shown.you];
            if (hero != null && hero.contains(mouseX, mouseY) && isAttackTargetFor(from, hero)) {
                send(MatchActionPayload.ATTACK, from.mid(), hero);
            }
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (previewReturn != null && event.isEscape()) { onClose(); return true; }
        if (event.isEscape() && (this.choosingExcavate || this.aimingExcavate)) {
            this.choosingExcavate = false;
            this.aimingExcavate = false;
            return true;
        }
        if (event.isEscape()) {
            // Esc cancels picking a target, then opens or closes the menu (it doesn't leave the match).
            if (this.awaitingTarget != null || this.dragging != null || this.attacker != null || this.aimingPower) {
                cancelPendingTarget();
                this.aimingPower = false;
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
        if (previewReturn != null) return;
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
