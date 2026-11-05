package craftbound.game;

import java.util.ArrayList;
import java.util.List;

/** Public, chronological history. Capture board names while the affected units still exist. */
public final class CommanderHistory {
    public enum Kind { TURN, ACTION, OUTCOME, DETAIL }
    public record Entry(int round, int turn, int owner, Kind kind, String text) {}
    private final List<Entry> entries = new ArrayList<>();
    private int round, turn, owner = -1;

    public int round() { return round; }
    public int size() { return entries.size(); }
    public List<Entry> slice(int from, int to) { return List.copyOf(entries.subList(from,to)); }

    void begin(int seat, int turn, String name) {
        if (owner < 0 || seat <= owner) round++;
        this.owner = seat;
        this.turn = turn;
        add(Kind.TURN, name);
    }
    void add(Kind kind, String text) {
        if (owner >= 0) entries.add(new Entry(round, turn, owner, kind, text));
    }

    private static String unit(Match match, int seat, int mid) {
        var player = match.player(seat);
        if (mid == Match.HERO) return player.name + "’s hero";
        var mob = player.minion(mid);
        return player.name + "’s " + (mob == null ? "unit" : Match.name(mob.card));
    }
    private static String aimedAt(Match m, Target t) {
        return t == null || t.isNone() ? "" : " on " + unit(m,t.side(),t.hero() ? Match.HERO : t.minionId());
    }
    private static String signed(int value) { return value > 0 ? "+" + value : Integer.toString(value); }
    private static String status(int id) {
        return switch (id) {
            case MatchEvent.STATUS_FROZEN -> "was frozen";
            case MatchEvent.STATUS_WITHERED -> "was withered";
            case MatchEvent.STATUS_TOTEM -> "gained a Totem of Undying";
            case MatchEvent.STATUS_INVULNERABLE -> "became invulnerable";
            case MatchEvent.STATUS_CLEANSED -> "was cleansed";
            case MatchEvent.STATUS_WEAKENED -> "was weakened";
            case MatchEvent.STATUS_SHIELD -> "gained Shield";
            case MatchEvent.STATUS_SPRINT -> "gained Sprint";
            case MatchEvent.STATUS_RANGED -> "gained Ranged";
            case MatchEvent.STATUS_IGNORE_SHIELD -> "gained Ignore Shield";
            default -> null;
        };
    }

    void record(CommanderMatch m, MatchEvent.Type type, int side, int mid, CardRef card, int amount, int extra, Target target) {
        if (owner < 0 || side < 0 || side >= m.players.length) return;
        var player = m.player(side);
        String who = player.name;
        // Only name a card in cases where it is publicly on the board or has been played.
        switch (type) {
            case PLAY -> {
                String verb = amount < 0 ? " summoned commander " : " played ";
                add(Kind.ACTION, who + verb + Match.name(card) + aimedAt(m,target) + ".");
            }
            case POWER -> add(Kind.ACTION, who + " used " + HeroPowers.of(player).displayName().getString() + aimedAt(m,target) + ".");
            case ATTACK -> {
                if (target != null && !target.isNone())
                    add(Kind.ACTION, unit(m,side,mid) + " attacked " + unit(m,target.side(),target.hero() ? Match.HERO : target.minionId()) + ".");
            }
            case DAMAGE, HEAL -> {
                var mob = mid == Match.HERO ? null : player.minion(mid);
                int health = Math.max(0,mob == null ? player.health : mob.health);
                String change = type == MatchEvent.Type.HEAL ? " recovered " + amount + " Health"
                        : amount == 0 ? " took no damage" : " took " + amount + " damage";
                var source = m.animationSource;
                String cause = source != null && !source.isNone() && !source.hero()
                        && m.player(source.side()).minion(source.minionId()) != null
                        ? " from " + unit(m,source.side(),source.minionId()) : "";
                add(Kind.OUTCOME, unit(m,side,mid) + change + cause + " (" + health + " Health).");
            }
            case DEATH -> add(Kind.OUTCOME, who + "’s " + Match.name(card) + " was destroyed.");
            case HUNGER -> add(Kind.ACTION, who + " had an empty deck and triggered Hunger.");
            case BREAK -> add(Kind.ACTION, who + "’s " + Match.name(card) + " broke.");
            case BOUNCE -> add(Kind.ACTION, who + "’s " + Match.name(card) + " returned to hand.");
            case REMOVE -> add(Kind.DETAIL, who + "’s " + Match.name(card) + " left the board.");
            case SUMMON, TRANSFORM -> add(Kind.DETAIL, who + (type == MatchEvent.Type.SUMMON ? " summoned " : " transformed a unit into ")
                    + Match.name(card) + " (" + amount + "/" + extra + ").");
            case EQUIP -> add(Kind.DETAIL, who + " equipped " + Match.name(card) + " (" + amount + " Attack, " + extra + " Durability).");
            case TNT_DETONATE -> add(Kind.ACTION, who + "’s " + Match.name(card) + " detonated.");
            case ENCHANT -> add(Kind.DETAIL, who + " enchanted their tool with " + Match.name(card)
                    + " (" + amount + " Attack, " + extra + " Durability).");
            case TOOL_UPGRADE -> add(Kind.DETAIL, who + "’s tool now has " + amount + " Attack and " + extra + " Durability.");
            case BUFF -> {
                if (amount != 0 || extra != 0) add(Kind.DETAIL, unit(m,side,mid) + " received " + signed(amount) + " Attack / " + signed(extra) + " Health.");
            }
            case STATUS -> {
                String effect = status(amount);
                if (effect != null) add(Kind.DETAIL, unit(m,side,mid) + " " + effect + ".");
            }
            case HERO_ATTACK -> add(Kind.DETAIL, who + "’s hero has " + amount + " Attack.");
            case TURN, XP -> add(Kind.DETAIL, who + " has " + amount + "/" + extra + " XP.");
            case DRAW -> add(Kind.DETAIL, who + " drew a card.");
            case GIVE -> add(Kind.DETAIL, who + " gained a card in hand.");
            case BURN -> add(Kind.DETAIL, who + " lost a card because their hand was full.");
            case HAND_REVEAL -> {
                if (amount <= 0 && target != null && !target.isNone())
                    add(Kind.DETAIL, who + " looked at " + m.player(target.side()).name + "’s hand.");
            }
            case HAND_DISCARD -> add(Kind.DETAIL, who + " discarded a card.");
            case HAND_STEAL -> add(Kind.DETAIL, who + " stole a card from " + m.player(target.side()).name + "’s hand.");
            case HAND_SHUFFLE -> add(Kind.DETAIL, who + " shuffled a card from hand into their deck.");
            default -> { }
        }
    }
}
