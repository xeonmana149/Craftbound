package craftbound.card;

import craftbound.card.CardCatalog.Action;
import craftbound.card.CardCatalog.Aim;
import craftbound.card.CardCatalog.Effect;
import craftbound.card.CardCatalog.Entry;
import java.util.ArrayList;
import java.util.List;

/**
 * Brewing Stand upgrades on one potion in a hand. Each upgrade can be added once.
 * Potency: +1 to every number above 0. Duration: the potion happens again at the start of your next turn.
 * Lingering: a chosen target becomes every matching mob (enemies for harmful effects, your own for helpful ones).
 */
public record Brew(boolean potent, boolean lasting, boolean lingering) {
    public static final Brew NONE = new Brew(false, false, false);

    public enum Upgrade {
        POTENCY("Potency", "+1 to each of its numbers"),
        DURATION("Duration", "it happens again at the start of your next turn"),
        LINGERING("Lingering", "it hits every target instead of one");
        public final String label, summary;
        Upgrade(String label, String summary) { this.label = label; this.summary = summary; }
    }

    public boolean none() { return !potent && !lasting && !lingering; }
    public int bits() { return (potent ? 1 : 0) | (lasting ? 2 : 0) | (lingering ? 4 : 0); }
    public static Brew fromBits(int bits) {
        return bits == 0 ? NONE : new Brew((bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0);
    }
    public boolean has(Upgrade u) {
        return switch (u) { case POTENCY -> potent; case DURATION -> lasting; case LINGERING -> lingering; };
    }
    public Brew with(Upgrade u) {
        return new Brew(potent || u == Upgrade.POTENCY, lasting || u == Upgrade.DURATION, lingering || u == Upgrade.LINGERING);
    }
    public Brew withoutDuration() { return new Brew(potent, false, lingering); }
    public List<Upgrade> upgrades() {
        List<Upgrade> out = new ArrayList<>();
        for (Upgrade u : Upgrade.values()) if (has(u)) out.add(u);
        return out;
    }

    /** The effects this brewed potion actually runs. */
    public List<Effect> apply(List<Effect> effects) {
        if (!potent && !lingering) return effects;
        return effects.stream().map(e -> {
            Effect out = e;
            if (potent) out = potent(out);
            if (lingering) out = linger(out);
            return out;
        }).toList();
    }

    /** A copy of the catalogue entry with the brew's effects, for rules text and targeting. */
    public Entry apply(Entry e) {
        if (none()) return e;
        return new Entry(e.definition(), e.name(), e.text(), true, e.passive(), e.passiveAmount(), apply(e.effects()),
                e.auraType(), e.deleted(), e.auraHealth(), e.castFx(), e.deathFx(), e.arrivalFx(), e.auraKind(),
                e.negateKeyword(), e.element(), e.ampElement(), e.ampHow(), e.potion(), e.uses());
    }

    /** Rules text for a brewed potion: what was brewed, then its upgraded effects. */
    public String describe(Entry e) {
        if (none()) return CardCatalog.description(e);
        String upgrades = String.join(", ", upgrades().stream().map(u -> u.label).toList());
        String body = CardCatalog.description(apply(e));
        return "Brewed: " + upgrades + ". " + body + (lasting ? " It happens again at the start of your next turn." : "");
    }

    /** Upgrades the Brewing Stand can still offer for this potion: not already added, and changes something. */
    public static List<Upgrade> options(Entry entry, Brew current) {
        List<Upgrade> out = new ArrayList<>();
        if (entry == null || !entry.potion()) return out;
        List<Effect> play = entry.effects().stream().filter(e -> e.trigger() == CardCatalog.Trigger.PLAY).toList();
        if (play.isEmpty()) return out;
        if (!current.potent && !play.stream().map(Brew::potent).toList().equals(play)) out.add(Upgrade.POTENCY);
        if (!current.lasting && play.stream().anyMatch(e -> repeats(e, current.lingering))) out.add(Upgrade.DURATION);
        if (!current.lingering && play.stream().anyMatch(Brew::lingers)) out.add(Upgrade.LINGERING);
        return out;
    }

    /** Whether happening again next turn changes anything. Lasting keywords/Wither/Totem and Transform don't stack,
     *  and a destroyed or returned single target is gone by then. */
    private static boolean repeats(Effect e, boolean lingering) {
        return switch (e.action()) {
            case GRANT_KEYWORD, WITHER, TOTEM, TRANSFORM -> false;
            case BUFF -> e.attack() != 0 || e.health() != 0;
            case DESTROY, BOUNCE -> lingering || !chosen(e.target());
            default -> true;
        };
    }

    /** Lingering spreads a chosen target over mobs, so it only applies to targeted effects that can hit a mob
     *  (not hand effects or tool breaking, which act on a hero). */
    private static boolean lingers(Effect e) {
        if (!chosen(e.target())) return false;
        return switch (e.action()) {
            case REVEAL_HAND, DISCARD_FROM_HAND, STEAL_FROM_HAND, SHUFFLE_FROM_HAND, DESTROY_TOOL, TOOL_DAMAGE -> false;
            default -> true;
        };
    }

    private static boolean chosen(Aim aim) {
        return aim == Aim.CHOSEN_ENEMY || aim == Aim.CHOSEN_FRIENDLY || aim == Aim.CHOSEN_ANY || aim == Aim.CHOSEN_CHARACTER;
    }

    private static boolean harmful(Action a) {
        return a == Action.DAMAGE || a == Action.FREEZE || a == Action.WITHER || a == Action.WEAKEN_UNTIL_NEXT_TURN
                || a == Action.DESTROY || a == Action.BOUNCE || a == Action.TRANSFORM || a == Action.BAD_OMEN;
    }

    private static Effect potent(Effect e) {
        int amount = e.amount(), attack = e.attack(), health = e.health();
        switch (e.action()) {
            case BUFF, WEAKEN_UNTIL_NEXT_TURN, UPGRADE_TOOL, ENCHANT_TOOL -> {
                if (attack > 0) attack++;
                if (health > 0) health++;
            }
            case DAMAGE, TOOL_DAMAGE, HEAL, WELL_FED, DRAW, GAIN_XP, GAIN_MAX_XP, SUMMON, GIVE, GIVE_RANDOM, GIVE_RANDOM_CLASS -> {
                if (amount > 0) amount++;
            }
            default -> { return e; }
        }
        return copy(e, e.target(), amount, attack, health);
    }

    private static Effect linger(Effect e) {
        if (!lingers(e)) return e;
        Aim to = switch (e.target()) {
            case CHOSEN_ENEMY -> Aim.ENEMY_MOBS;
            case CHOSEN_FRIENDLY -> Aim.FRIENDLY_MOBS;
            case CHOSEN_ANY, CHOSEN_CHARACTER -> harmful(e.action()) ? Aim.ENEMY_MOBS : Aim.FRIENDLY_MOBS;
            default -> null;
        };
        return to == null ? e : copy(e, to, e.amount(), e.attack(), e.health());
    }

    private static Effect copy(Effect e, Aim target, int amount, int attack, int health) {
        return new Effect(e.trigger(), e.action(), target, e.boardKind(), e.type(), amount, attack, health, e.card(),
                e.excludeSelf(), e.keyword(), e.pool(), e.countScope(), e.countKind(), e.countType(), e.countExcludeSelf(),
                e.eventScope(), e.eventType(), e.gate(), e.chooser(), e.randomKeyword(), e.boost());
    }
}
