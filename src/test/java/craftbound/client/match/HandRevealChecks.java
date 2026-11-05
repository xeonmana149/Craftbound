package craftbound.client.match;

import craftbound.game.TrialTraining;
import craftbound.sleeve.Sleeves;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.resources.Identifier;

/** Headless reveal lifecycle: privacy timing, event batching and bounded card entrance/exit. */
public final class HandRevealChecks {
    public static void run() {
        var overlay = new HandPickOverlay();
        var id = Identifier.parse("craftbound:reveal_test");
        var back = new Sleeves.Design(1, 2, 3, 4, 5);
        pickerLifecycle(id);
        deckPicker();
        require(!overlay.peeking(0), "An unused overlay must not reveal a hand");
        require(HandPickOverlay.revealDuration(false) == 3500
                && HandPickOverlay.revealDuration(true) == 3920, "Eye intro must not shorten the normal peek window");

        overlay.reveal("Opponent", 0, id, true, TrialTraining.NONE, 1000, true, back);
        overlay.reveal("Ignored continuation title", 1, id, false, TrialTraining.NONE, 1100, false, null);
        require(!overlay.peeking(999) && overlay.peeking(1000), "Reveal appeared before its scheduled start");
        require(overlay.peeking(4919) && !overlay.peeking(4920), "Reveal batch extended its timer per card");
        require(cards(overlay) == 2, "Reveal events must accumulate into one hand");
        require(read(overlay, "peekBack") == back && read(overlay, "peekEnder").equals(true)
                && read(overlay, "peekName").equals("Opponent"), "Continuation events replaced the reveal's style or owner");

        overlay.reveal("Empty opponent", -1, null, false, TrialTraining.NONE, 6000, true, back);
        require(cards(overlay) == 0 && read(overlay, "peekEmpty").equals(true),
                "An empty hand retained cards from the previous reveal");
        require(read(overlay, "peekName").equals("Empty opponent") && overlay.peeking(6000),
                "Empty hand must still display its own explanation");

        overlay.reveal("Normal opponent", 0, id, false, TrialTraining.NONE, 11000);
        require(cards(overlay) == 1 && read(overlay, "peekEmpty").equals(false)
                && read(overlay, "peekEnder").equals(false) && read(overlay, "peekBack") == null,
                "A normal reveal inherited the previous Eye animation or empty state");
        require(overlay.peeking(14499) && !overlay.peeking(14500), "Normal reveal duration changed");
        MatchState state = new MatchState();
        require(overlay.covering(state, 11000), "An active reveal should block hover previews");
        state.over = true;
        require(!overlay.covering(state, 11000), "A revealed hand must not cover the match result");

        for (int count = 1; count <= 10; count++) for (int i = 0; i < count; i++) {
            var start = HandPickOverlay.enderFrame(0, i, count);
            require(!start.face() && start.travel() == 0, "Eye reveal must begin with the victim's card backs");
            var settled = HandPickOverlay.enderFrame(HandPickOverlay.ENDER_INTRO_MS, i, count);
            require(settled.face() && settled.travel() == 1 && settled.scale() == 1
                    && settled.flip() == 1 && settled.fan() == 0, "Every card must finish its flip before the reading window");
            for (long age : new long[]{500, 1500, 3000})
                require(HandPickOverlay.enderFrame(age, i, count).equals(settled), "Cards must remain still while being read");
            var end = HandPickOverlay.enderFrame(HandPickOverlay.revealDuration(true), i, count);
            require(end.scale() == 0 && end.travel() == 0, "Reveal must collapse before removal");
            for (long age = 0; age <= 3920; age += 37) {
                var frame = HandPickOverlay.enderFrame(age, i, count);
                require(Float.isFinite(frame.travel()) && frame.travel() >= 0 && frame.travel() <= 1
                        && Float.isFinite(frame.scale()) && frame.scale() >= 0 && frame.scale() <= 1
                        && Float.isFinite(frame.flip()) && frame.flip() > 0 && frame.flip() <= 1,
                        "Reveal geometry must stay finite and inside its source/destination bounds");
            }
        }
        require(HandPickOverlay.enderFrame(100, 0, 10).travel()
                > HandPickOverlay.enderFrame(100, 9, 10).travel(), "Large hands should enter with a bounded stagger");
        System.out.println("PASS: picker animation gating, countdown and click lifecycle; private hand reveal batching, timing, reset states and staggered flip/collapse.");
    }

    private static void deckPicker() {
        var root=com.google.gson.JsonParser.parseString("""
            {"you":3,"over":false,"players":[{"name":"A"},{"name":"B"},{"name":"C"},{"name":"D"}],
             "choice":{"action":"CHOOSE_FROM_DECK","owner":3,"victim":3,"chooser":3,"count":1,
             "ticks":600,"source":"craftbound:furnace","cards":[{"id":"craftbound:furnace","ench":true,"training":"TEMPER"}]}}
            """).getAsJsonObject();
        var state=MatchState.choiceView(root);
        var overlay=new HandPickOverlay();overlay.sync(state,1000);
        require(state.sides.length==4 && state.sides[3].name.equals("D") && overlay.picking(state),
                "Fourth-seat deck chooser must use the same picker without duel-only indices");
        require(state.choice.cards().getFirst().enchanted && state.choice.cards().getFirst().training==TrialTraining.TEMPER,
                "Deck choice preview must retain the actual copy's finish and training");
        layoutPicker(overlay);
        require(overlay.click(state,320,190)==0 && overlay.click(state,320,190)==-1,"Deck choice sends one selection");
        root.addProperty("you",1);root.getAsJsonObject("choice").remove("cards");
        state=MatchState.choiceView(root);overlay.sync(state,2000);
        require(!overlay.picking(state)&&overlay.waiting(state)&&overlay.click(state,320,190)==-1,
                "Other seats receive no offer cards or clickable hitboxes");
        root.remove("choice");state=MatchState.choiceView(root);overlay.sync(state,2100);
        require(!overlay.picking(state)&&!overlay.waiting(state),"Resolved deck choice clears overlay");
    }

    private static void pickerLifecycle(Identifier id) {
        var overlay = new HandPickOverlay();
        var state = new MatchState();
        var card = new MatchState.HandCard();
        card.id = id;
        state.you = 0;
        state.choice = new MatchState.Choice("DISCARD_FROM_HAND", 0, 1, 0, 1, 300, id, List.of(card));

        overlay.sync(state, 1000, true);
        require(!overlay.picking(state) && !overlay.waiting(state) && !overlay.covering(state, 1000),
                "A choice included in an animating state must not show the picker or waiting banner");
        require(read(overlay, "choiceReceived").equals(-1L), "A hidden choice started its countdown");
        layoutPicker(overlay);
        require(overlay.click(state, 320, 190) == -1, "An animating picker accepted a stale card hitbox");
        overlay.sync(state, 1500, true);
        require(spots(overlay) == 0 && read(overlay, "choiceReceived").equals(-1L),
                "A hidden choice retained hitboxes or started its countdown on a later frame");

        overlay.sync(state, 2040, false);
        require(overlay.picking(state) && !overlay.waiting(state) && overlay.covering(state, 2040),
                "The chooser's picker did not appear after animation completed");
        require(read(overlay, "choiceReceived").equals(2040L), "The countdown did not start when the picker appeared");
        require(overlay.click(state, 320, 190) == -1, "A newly visible picker reused hidden hitboxes before layout");
        layoutPicker(overlay);
        overlay.sync(state, 2200, true);
        require(spots(overlay) == 0 && overlay.click(state, 320, 190) == -1,
                "Hiding a visible picker left its card clickable");
        overlay.sync(state, 2500, false);
        require(read(overlay, "choiceReceived").equals(2040L), "The same pending choice restarted its countdown");
        layoutPicker(overlay);
        var visibleChoice = state.choice;
        state.choice = new MatchState.Choice("DISCARD_FROM_HAND", 0, 1, 0, 1, 300, id, List.of(card));
        require(overlay.click(state, 320, 190) == -1, "An unsynced choice accepted another choice's active hitboxes");
        state.choice = visibleChoice;
        require(overlay.click(state, 320, 190) == 0 && overlay.click(state, 320, 190) == -1,
                "A visible choice must accept exactly one card selection");
        overlay.sync(state, 2600);
        require(overlay.click(state, 320, 190) == -1, "Sync reset the sent guard for the same pending choice");

        state.choice = new MatchState.Choice("DISCARD_FROM_HAND", 0, 1, 0, 1, 300, id, List.of(card));
        require(overlay.click(state, 320, 190) == -1, "A new unsynced choice accepted the previous choice's hitboxes");
        overlay.sync(state, 3000, true);
        require(spots(overlay) == 0 && read(overlay, "choiceReceived").equals(-1L),
                "A replacement choice inherited the previous choice's layout or timer");
        overlay.sync(state, 4000);
        layoutPicker(overlay);
        require(overlay.click(state, 320, 190) == 0, "A replacement choice inherited the previous choice's sent guard");

        state.you = 1;
        state.choice = new MatchState.Choice("DISCARD_FROM_HAND", 0, 1, 0, 1, 300, id, List.of());
        overlay.sync(state, 5000, true);
        require(!overlay.picking(state) && !overlay.waiting(state), "The victim saw a waiting banner before the projectile finished");
        overlay.sync(state, 6040, false);
        require(!overlay.picking(state) && overlay.waiting(state) && overlay.click(state, 320, 190) == -1,
                "The victim must see only the waiting banner after animation");

        overlay.reveal("Opponent", 0, id, false, TrialTraining.NONE, 7000);
        overlay.sync(state, 7000, true);
        require(overlay.peeking(7000) && overlay.covering(state, 7000),
                "Animation gating suppressed an ordinary HAND_REVEAL peek");
        state.over = true;
        overlay.sync(state, 7100, false);
        require(!overlay.picking(state) && !overlay.waiting(state) && !overlay.covering(state, 7100)
                && overlay.click(state, 320, 190) == -1, "A pending picker covered or accepted input after game over");
    }

    /** Populate the same card bounds as drawing, without requiring a client renderer. */
    private static void layoutPicker(HandPickOverlay overlay) {
        try {
            var method = HandPickOverlay.class.getDeclaredMethod("layoutRow", int.class, int.class, int.class);
            method.setAccessible(true);
            method.invoke(overlay, 1, 640, 360);
        } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }

    private static int spots(HandPickOverlay overlay) { return ((List<?>) read(overlay, "spots")).size(); }

    private static int cards(HandPickOverlay overlay) { return ((List<?>) read(overlay, "peek")).size(); }
    private static Object read(Object object, String name) {
        try { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
        catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private HandRevealChecks() {}
}
