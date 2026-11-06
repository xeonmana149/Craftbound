package craftbound.game;

import craftbound.card.CardCatalog;
import craftbound.card.CardCatalog.*;
import craftbound.card.Cards;
import craftbound.card.Keyword;
import net.minecraft.resources.Identifier;

/** Read-only previews of authored effects against the currently visible state; never consumes RNG. */
public final class EffectPreview {
    public enum Kind { BOARD, HERO, TOOL }
    public record Source(Identifier card, int side, int mid, boolean ongoing, int toolAttack, java.util.List<Subject> board) {
        public Source(Identifier card, int side, int mid, boolean ongoing, int toolAttack) { this(card, side, mid, ongoing, toolAttack, null); }
    }
    public record Subject(Kind kind, Identifier card, int side, int mid, int health, int maxHealth,
                          boolean invulnerable, boolean frozen, boolean withered, boolean totem, int attackDebuff, boolean badOmen, boolean appliedBenefit) {
        public Subject(Kind kind, Identifier card, int side, int mid, int health, int maxHealth,
                       boolean invulnerable, boolean frozen, boolean withered, boolean totem, int attackDebuff, boolean badOmen) {
            this(kind, card, side, mid, health, maxHealth, invulnerable, frozen, withered, totem, attackDebuff, badOmen, false);
        }
        public Subject(Kind kind, Identifier card, int side, int mid, int health, int maxHealth,
                       boolean invulnerable, boolean frozen, boolean withered, boolean totem, int attackDebuff) {
            this(kind, card, side, mid, health, maxHealth, invulnerable, frozen, withered, totem, attackDebuff, false);
        }
        public Subject(Kind kind, Identifier card, int side, int mid, int health, int maxHealth,
                       boolean invulnerable, boolean frozen, boolean withered, boolean totem) {
            this(kind, card, side, mid, health, maxHealth, invulnerable, frozen, withered, totem, 0);
        }
        public static Subject hero(int side, int health) { return hero(side, health, false); }
        public static Subject hero(int side, int health, boolean frozen) {
            return new Subject(Kind.HERO, null, side, Match.HERO, health, Match.START_HEALTH, false, frozen, false, false);
        }
        public static Subject tool(int side, Identifier card) {
            return new Subject(Kind.TOOL, card, side, Match.HERO, 0, 0, false, false, false, false);
        }
    }
    public record Highlight(boolean harmful, boolean possible) {}

    public static Highlight forTarget(Source source, Subject target) {
        if (source == null || target == null) return null;
        var entry = CardCatalog.entry(source.card());
        if (entry == null) return null;
        boolean friendly = source.side() == target.side();
        boolean self = source.mid() > 0 && friendly && source.mid() == target.mid();
        var definition = target.card() == null ? null : Cards.get(target.card());
        Highlight result = null;
        if (!source.ongoing() && friendly && target.kind() == Kind.TOOL && target.card() != null
                && entry.definition().kind() == craftbound.card.CardKind.ENCHANTMENT
                && entry.effects().stream().anyMatch(e -> e.trigger() != Trigger.PLAY))
            result = new Highlight(false, false);
        if (!source.ongoing() && friendly && target.kind() == Kind.BOARD && definition != null
                && entry.definition().mobType() == craftbound.card.MobType.AQUATIC
                && !entry.definition().keywords().contains(Keyword.BLOCK)
                && CardCatalog.passive(target.card(), Passive.SCHOOLING)
                && CardCatalog.passiveAmount(target.card()) > 0) result = new Highlight(false, false);
        if (target.kind() == Kind.BOARD && definition != null && friendly && !self
                && entry.hasAura() && (entry.passiveAmount() > 0 || entry.auraHealthBonus() > 0)
                && entry.auraMatches(definition)) result = new Highlight(false, false);
        for (Effect e : entry.effects()) {
            if (e.trigger() != (source.ongoing() ? Trigger.END_TURN : Trigger.PLAY)) continue;
            if (e.countScope() != CountScope.NONE && source.board() != null && source.board().stream().noneMatch(b ->
                    b.health() > 0 && (e.countScope() == CountScope.ALL || (e.countScope() == CountScope.FRIENDLY) == (b.side() == source.side()))
                    && (!e.countExcludeSelf() || b.side() != source.side() || b.mid() != source.mid())
                    && b.card() != null && (e.countType() == null || Cards.get(b.card()).mobType() == e.countType())
                    && (e.countKind() == BoardKind.BOTH || (e.countKind() == BoardKind.BLOCK) == Cards.get(b.card()).keywords().contains(Keyword.BLOCK)))) continue;
            boolean toolEffect = e.action() == Action.ENCHANT_TOOL || e.action() == Action.UPGRADE_TOOL || e.action() == Action.DESTROY_TOOL;
            if (toolEffect != (target.kind() == Kind.TOOL)) continue;
            if (e.action() == Action.ENCHANT_TOOL || e.action() == Action.UPGRADE_TOOL) {
                if (!friendly || target.card() == null) continue;
            } else {
                boolean aimed = switch (e.target()) {
                    case SELF -> self;
                    case EVENT, KILLER -> false;
                    case FRIENDLY_MOBS, RANDOM_FRIENDLY_MOB -> friendly && target.kind() == Kind.BOARD;
                    case ENEMY_MOBS -> !friendly && target.kind() == Kind.BOARD;
                    case ALL_MOBS -> target.kind() == Kind.BOARD;
                    case FRIENDLY_HERO -> friendly && target.kind() != Kind.BOARD;
                    case ENEMY_HERO, EACH_ENEMY_HERO -> !friendly && target.kind() != Kind.BOARD;
                    case EACH_HERO -> target.kind() != Kind.BOARD;
                    case RANDOM_ENEMY -> !friendly;
                    case RANDOM_FRIENDLY -> friendly;
                    case RANDOM_MOB -> target.kind() == Kind.BOARD && definition != null && !definition.keywords().contains(Keyword.BLOCK);
                    case RANDOM_BLOCK -> target.kind() == Kind.BOARD && definition != null && definition.keywords().contains(Keyword.BLOCK);
                    case RANDOM_HERO -> target.kind() == Kind.HERO;
                    case CHOSEN_FRIENDLY -> friendly && target.kind() == Kind.BOARD;
                    case CHOSEN_ENEMY -> !friendly && target.kind() == Kind.BOARD;
                    case CHOSEN_ANY -> target.kind() == Kind.BOARD;
                    case CHOSEN_CHARACTER -> target.kind() == Kind.BOARD || target.kind() == Kind.HERO;
                };
                if (!aimed || e.excludeSelf() && self) continue;
                if (target.kind() == Kind.BOARD) {
                    if (definition == null || !CardEffects.matches(e, definition)) continue;
                } else if (e.type() != null || !CardCatalog.canAffectHero(e.action())) continue;
            }
            boolean changes = switch (e.action()) {
                case HEAL -> e.amount() > 0 && target.health() < target.maxHealth();
                case BAD_OMEN -> !target.badOmen();
                case CLEANSE -> target.appliedBenefit() || target.invulnerable() || target.totem() || target.badOmen() || target.frozen() || target.withered() || target.attackDebuff() > 0;
                case DAMAGE -> e.amount() > 0 && !target.invulnerable();
                case TOOL_DAMAGE -> source.toolAttack() > 0 && !target.invulnerable();
                case BUFF -> e.attack() != 0 || e.health() > 0
                        || e.keyword() != null && definition != null && !definition.keywords().contains(e.keyword());
                case WELL_FED -> target.maxHealth() < 1000;
                case GRANT_KEYWORD -> definition != null && (e.randomKeyword() || !definition.keywords().contains(e.keyword()));
                case TRANSFORM -> definition != null && !target.card().equals(e.card());
                case TOTEM -> !target.totem();
                case INVULNERABLE -> !target.invulnerable();
                case FREEZE -> !target.frozen();
                case WITHER -> !target.withered();
                case BOUNCE, RETURN_AND_COPY, MINE -> true;
                case ENCHANT_TOOL, UPGRADE_TOOL, DESTROY_TOOL -> target.card() != null;
                default -> false;
            };
            if (!changes) continue;
            boolean harmful = switch (e.action()) {
                case BUFF -> e.attack() < 0;
                case CLEANSE -> target.appliedBenefit() || target.invulnerable() || target.totem();
                case TRANSFORM -> !friendly;
                case DAMAGE, TOOL_DAMAGE, BOUNCE, RETURN_AND_COPY, FREEZE, WITHER, DESTROY_TOOL, BAD_OMEN -> true;
                default -> false;
            };
            boolean possible = source.ongoing() || CardCatalog.randomTarget(e.target()) || switch (e.target()) {
                case RANDOM_ENEMY, RANDOM_FRIENDLY_MOB, CHOSEN_CHARACTER, CHOSEN_ANY, CHOSEN_FRIENDLY, CHOSEN_ENEMY -> true;
                default -> false;
            };
            // Do not present an effect that also harms this target as a purely beneficial play.
            if (result == null || harmful && !result.harmful()
                    || harmful == result.harmful() && !possible) result = new Highlight(harmful, possible);
        }
        return result;
    }
    private EffectPreview() {}
}
