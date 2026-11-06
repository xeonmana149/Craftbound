package craftbound.game;

import craftbound.card.CardCatalog;
import craftbound.card.CardCatalog.*;
import craftbound.card.Cards;
import craftbound.card.Keyword;
import java.util.*;
import net.minecraft.resources.Identifier;

/** Reusable effects authored in the card catalogue; the server remains authoritative. */
public final class CardEffects {
    public enum PlayPreview { NONE, POSSIBLE, AFFECTED }

    /** Catalogue-only compatibility helper; live UI uses EffectPreview with current health/status values. */
    public static PlayPreview previewOnPlay(Identifier source, Identifier target, boolean friendly) {
        var preview = EffectPreview.forTarget(new EffectPreview.Source(source, 0, -1, false, 1),
                new EffectPreview.Subject(EffectPreview.Kind.BOARD, target, friendly ? 0 : 1, 1,
                        0, 1, false, false, false, false));
        return preview == null ? PlayPreview.NONE : preview.possible() ? PlayPreview.POSSIBLE : PlayPreview.AFFECTED;
    }

    static boolean matches(Effect e, craftbound.card.CardDefinition definition) {
        boolean block = definition.keywords().contains(craftbound.card.Keyword.BLOCK);
        return (e.boardKind() == BoardKind.BOTH || (e.boardKind() == BoardKind.BLOCK) == block)
                && (e.type() == null || definition.mobType() == e.type());
    }

    private static final List<Keyword> RANDOM_GRANTS = List.of(Keyword.SHIELD, Keyword.SPRINT, Keyword.RANGED, Keyword.IGNORE_SHIELD);

    // Keywords a Grant keyword effect could still add: never one the target already has, and blocks only take Shield.
    public static List<Keyword> grantOptions(Effect e, java.util.function.Predicate<Keyword> has, boolean block) {
        List<Keyword> pool = e.randomKeyword() ? RANDOM_GRANTS : e.keyword() == null ? List.of() : List.of(e.keyword());
        return pool.stream().filter(k -> !has.test(k) && (!block || k == Keyword.SHIELD)).toList();
    }
    static List<Keyword> grantOptions(Effect e, Minion minion) {
        return grantOptions(e, minion::has, minion.has(Keyword.BLOCK));
    }

    // An effect's optional boost: scale value when its condition holds. Target conditions only apply with a real target.
    static int boostValue(Match m, int side, Effect e, int value, Target target) {
        Boost b = e.boost();
        if (b == null || value == 0 || b.when().perTarget() && (target == null || target.isNone())) return value;
        return boostHolds(m, side, b, target) ? b.apply(value) : value;
    }
    static boolean boostHolds(Match m, int side, Boost b, Target target) {
        java.util.function.Predicate<Minion> fits = mob -> !mob.isDead() && (b.card() != null ? mob.card.id().equals(b.card())
                : (b.kind() == BoardKind.BOTH || (b.kind() == BoardKind.BLOCK) == mob.has(Keyword.BLOCK))
                        && (b.type() == null || mob.definition().mobType() == b.type()));
        switch (b.when()) {
            case YOU_CONTROL, ENEMY_CONTROLS, ANYONE_CONTROLS -> {
                for (int seat = 0; seat < m.players.length; seat++) {
                    if (!m.inPlay(seat)) continue;
                    if (b.when() == BoostWhen.YOU_CONTROL && seat != side || b.when() == BoostWhen.ENEMY_CONTROLS && !m.isEnemy(side, seat)) continue;
                    if (m.player(seat).board.stream().anyMatch(fits)) return true;
                }
                return false;
            }
            default -> {
                if (target == null || target.isNone()) return false;
                if (target.hero()) {
                    var hero = m.player(target.side());
                    return b.when() == BoostWhen.TARGET_FROZEN ? hero.frozen : b.when() == BoostWhen.TARGET_DAMAGED && hero.health < hero.maxHealth;
                }
                Minion mob = m.player(target.side()).minion(target.minionId());
                if (mob == null || mob.isDead()) return false;
                return switch (b.when()) {
                    case TARGET_IS -> fits.test(mob);
                    case TARGET_FROZEN -> mob.frozen;
                    case TARGET_WITHERED -> mob.withered;
                    case TARGET_DAMAGED -> mob.health < mob.maxHealth;
                    default -> false;
                };
            }
        }
    }
    // Amplify element passives on your board (e.g. Netherrack: Fire effects deal double damage). Bonuses add, then the biggest multiplier applies once.
    static int amplify(Match m, int side, CardRef card, int damage) {
        var entry = CardCatalog.entry(card.id());
        if (entry == null || entry.element() == null || damage <= 0) return damage;
        int plus = 0, times = 1;
        for (Minion source : m.player(side).board) {
            var amp = source.isDead() ? null : CardCatalog.entry(source.card.id());
            if (amp == null || amp.passive() != Passive.AMPLIFY_ELEMENT || amp.ampElement() != entry.element()) continue;
            switch (amp.ampHow()) {
                case PLUS -> plus += amp.passiveAmount();
                case DOUBLE -> times = Math.max(times, 2);
                case TRIPLE -> times = Math.max(times, 3);
            }
        }
        return (damage + plus) * times;
    }

    public static boolean canTarget(Identifier card, Identifier target) {
        var definition = Cards.get(target);
        return definition != null && canTarget(card, target, definition.keywords()::contains);
    }
    // A chosen target is legal when some chosen effect fits it; a Grant keyword effect also needs something left to grant.
    public static boolean canTarget(Identifier card, Identifier target, java.util.function.Predicate<Keyword> has) {
        var definition = Cards.get(target);
        var entry = CardCatalog.entry(card);
        return definition != null && entry != null && entry.effects().stream().anyMatch(e ->
                e.trigger() == Trigger.PLAY && e.target().name().startsWith("CHOSEN") && matches(e, definition)
                        && (e.action() != Action.GRANT_KEYWORD || !grantOptions(e, has, definition.keywords().contains(Keyword.BLOCK)).isEmpty()));
    }
    public static boolean canTargetHero(Identifier card) {
        var entry = CardCatalog.entry(card);
        return entry != null && entry.effects().stream().anyMatch(e ->
                e.trigger() == Trigger.PLAY && e.target() == Aim.CHOSEN_CHARACTER);
    }

    /** A Lingering potion hits every target, so it needs no chosen one. */
    public static Target.Kind targetKind(CardRef card) {
        return card.brew().lingering() ? Target.Kind.NONE : targetKind(card.id());
    }

    public static Target.Kind targetKind(Identifier card) {
        var entry = CardCatalog.entry(card);
        if (entry != null) for (Effect e : entry.effects()) if (e.trigger() == Trigger.PLAY) {
            switch (e.target()) {
                case CHOSEN_CHARACTER: return Target.Kind.ANY_CHARACTER;
                case CHOSEN_ANY: return Target.Kind.ANY_MOB;
                case CHOSEN_FRIENDLY: return Target.Kind.FRIENDLY_MOB;
                case CHOSEN_ENEMY: return Target.Kind.ENEMY_MOB;
                default: break;
            }
        }
        return Target.Kind.NONE;
    }

    static String whyCantPlay(Match match, int side, Identifier card) {
        if (Cards.get(card).kind() == craftbound.card.CardKind.ENCHANTMENT && match.player(side).tool == null)
            return "equip a tool before enchanting";
        for (Effect e : CardCatalog.entry(card).effects()) if (e.trigger() == Trigger.PLAY) {
            if (e.action() == Action.ENCHANT_TOOL && match.player(side).tool == null) return "equip a tool before enchanting";
            if (e.action() == Action.UPGRADE_TOOL && e.trigger() == Trigger.PLAY && !Cards.get(card).isMob()
                    && match.player(side).tool == null) return "equip a tool before improving it";
            if (e.action() == Action.TOOL_DAMAGE) {
                if (match.player(side).tool == null) return "you need a tool";
                boolean any = CardCatalog.eachHero(e.target());
                for (int rival=0; !any && rival<match.players.length; rival++)
                    if (match.isEnemy(side, rival) && match.inPlay(rival) && !match.player(rival).board.isEmpty()) { any=true; break; }
                if (!any) return "nothing to hit";
            }
            if (e.action() == Action.DESTROY_TOOL
                    && possibleTargets(match, side, null, e, Target.NONE).stream().noneMatch(t -> match.player(t.side()).tool != null))
                return "nothing has a tool to destroy";
        }
        return null;
    }
    static void onSpawn(Match m, int side, Minion source, Target target) { run(m, side, source.card, source, Trigger.PLAY, target); }
    static void onDrop(Match m, int side, Minion source) { run(m, side, source.card, source, Trigger.DROP, Target.NONE); }
    static void onItem(Match m, int side, CardRef card, Target target) {
        Tool equipped = m.player(side).tool;
        var entry = CardCatalog.entry(card.id());
        boolean potion = entry != null && entry.potion();
        java.util.Map<Minion, String> before = new java.util.IdentityHashMap<>();
        if (potion) for (int s = 0; s < m.players.length; s++) for (Minion mob : m.player(s).board) before.put(mob, mob.lastingState());
        run(m, side, card, null, Trigger.PLAY, target);
        // Mark every mob the potion left a lasting change on (Strength, Swiftness, Weakness...).
        if (potion) for (int s = 0; s < m.players.length; s++) for (Minion mob : m.player(s).board) {
            String was = before.get(mob);
            if (was == null || was.equals(mob.lastingState()) || mob.isDead()) continue;
            // lastingState: attack/maxHealth/debuff/timedWeaknesses/keywords/flags. If only the two weakness parts
            // moved, the mark wears off with the weakness.
            String[] a = was.split("/", 6), b = mob.lastingState().split("/", 6);
            boolean onlyWeakness = a[0].equals(b[0]) && a[1].equals(b[1]) && a[4].equals(b[4]) && a[5].equals(b[5]);
            if (mob.potions == null) mob.potions = new ArrayList<>();
            mob.potions.removeIf(p -> p.id().equals(card.id().toString()));
            mob.potions.add(new Minion.PotionMark(card.id().toString(), card.brew().bits(), onlyWeakness));
        }
        var printed = Cards.get(card.id()).keywords();
        boolean tridentEnchant = printed.contains(Keyword.LOYALTY) || printed.contains(Keyword.CHANNELING);
        if (Cards.get(card.id()).kind() == craftbound.card.CardKind.ENCHANTMENT && equipped != null
                && m.player(side).tool == equipped && (tridentEnchant || CardCatalog.entry(card.id()).effects().stream().anyMatch(e -> e.trigger() != Trigger.PLAY))) {
            // Trigger-only enchantments attach without a dummy stat-upgrade effect.
            // Keep legacy explicit attachments from adding a second visual entry/event.
            if (CardCatalog.entry(card.id()).effects().stream().noneMatch(e -> e.action() == Action.ENCHANT_TOOL)) {
                equipped.enchantments.add(new Tool.Enchantment(card, 0, 0));
                m.log(m.player(side).name + " enchants " + Match.name(equipped.card) + " with " + Match.name(card) + ".", card);
                m.event(MatchEvent.Type.ENCHANT, side, Match.HERO, card, equipped.attack, equipped.uses, null);
            }
            equipped.triggerEnchantments.add(card);
        }
    }

    static void onToolTrigger(Match m, int side, Tool tool, Trigger trigger, Target target) {
        if (tool == null) return;
        run(m, side, tool.card, null, trigger, target);
        for (CardRef enchantment : List.copyOf(tool.triggerEnchantments)) run(m, side, enchantment, null, trigger, target);
    }

    // Snapshot listeners: newly summoned units cannot retroactively observe this event.
    static void notifyEvent(Match m, Trigger trigger, int eventSide, CardRef eventCard, Target eventTarget) {
        record Listener(int side, CardRef card, Minion mob, Tool tool) {}
        List<Listener> listeners = new ArrayList<>();
        for (int side = 0; side < m.players.length; side++) {
            if (!m.inPlay(side)) continue;
            for (Minion mob : List.copyOf(m.player(side).board)) if (!mob.isDead()) listeners.add(new Listener(side, mob.card, mob, null));
            Tool tool = m.player(side).tool;
            if (tool != null) {
                listeners.add(new Listener(side, tool.card, null, tool));
                for (CardRef enchantment : List.copyOf(tool.triggerEnchantments)) listeners.add(new Listener(side, enchantment, null, tool));
            }
        }
        for (Listener l : listeners) {
            if (l.mob() != null && (l.mob().isDead() || !m.player(l.side()).board.contains(l.mob()))) continue;
            if (l.tool() != null && m.player(l.side()).tool != l.tool()) continue;
            run(m, l.side(), l.card(), l.mob(), trigger, eventTarget, e -> {
                boolean self = eventTarget != null && eventTarget.side() == l.side()
                        && (l.mob() == null ? eventTarget.hero() : !eventTarget.hero() && eventTarget.minionId() == l.mob().id);
                boolean scope = switch (e.eventScope()) {
                    case SELF -> self;
                    case FRIENDLY -> eventSide == l.side();
                    case ENEMY -> m.isEnemy(l.side(), eventSide);
                    case ANY -> true;
                };
                return scope && (e.eventType() == null || eventCard != null && Cards.get(eventCard.id()).mobType() == e.eventType());
            });
        }
    }

    static void onKill(Match m, Minion killer, Minion killed) {
        int side = m.sideOf(killer), victimSide = m.sideOf(killed);
        if (side >= 0 && victimSide >= 0) run(m, side, killer.card, killer, Trigger.ON_KILL, Target.mob(victimSide, killed.id));
    }

    static boolean gateOpen(MatchPlayer owner, Gate gate, int handSize) {
        return switch (gate) {
            case ALWAYS -> true;
            case TOOL_EQUIPPED -> owner.tool != null;
            case HERO_HURT -> owner.health < owner.maxHealth;
            case EMPTY_HAND -> handSize == 0;
        };
    }

    static int multiplier(Match m, int side, Minion source, Effect e) {
        if (e.countScope() == CountScope.NONE) return 1;
        int count = 0;
        for (int s = 0; s < m.players.length; s++) {
            if (!m.inPlay(s)) continue;
            if (e.countScope() == CountScope.FRIENDLY && s != side || e.countScope() == CountScope.ENEMY && !m.isEnemy(side, s)) continue;
            for (Minion mob : m.player(s).board) if (!mob.isDead() && (!e.countExcludeSelf() || source != mob)
                    && (e.countKind() == BoardKind.BOTH || (e.countKind() == BoardKind.BLOCK) == mob.has(craftbound.card.Keyword.BLOCK))
                    && (e.countType() == null || mob.definition().mobType() == e.countType())) count++;
        }
        return count;
    }
    static void onDamageDealt(Match m, Minion source, Minion victim) {
        int side = m.sideOf(source), victimSide = m.sideOf(victim);
        if (side >= 0 && victimSide >= 0) run(m, side, source.card, source, Trigger.DAMAGE_DEALT, Target.mob(victimSide, victim.id));
    }
    static void onMobPlayed(Match m, int side, Minion played) {
        if (played.definition().mobType() != craftbound.card.MobType.AQUATIC || played.has(craftbound.card.Keyword.BLOCK)) return;
        for (Minion source : new ArrayList<>(m.player(side).board)) {
            if (source != played && !source.isDead() && CardCatalog.passive(source.card.id(), Passive.SCHOOLING)) {
                int amount = CardCatalog.passiveAmount(source.card.id());
                source.temporaryAttack += amount;
                m.buff(source, amount, 0);
            }
        }
    }

    static void onEndTurn(Match m, int side) {
        // Brewing waits until every other end-of-turn effect has run (a Cauldron may add the potion first).
        m.holdBrews = true;
        try {
            endTurnTriggers(m, side);
        } finally {
            m.holdBrews = false;
        }
        m.releaseBrews();
    }

    private static void endTurnTriggers(Match m, int side) {
        for (Minion source : new ArrayList<>(m.player(side).board)) {
            if (!m.over && m.player(side).board.contains(source)) {
                if (!source.isDead() && CardCatalog.passive(source.card.id(), Passive.GRAZER)
                        && m.player(side).board.stream().anyMatch(c -> !c.isDead() && c.definition().mobType() == craftbound.card.MobType.CROP)) {
                    m.heal(Target.mob(side, source.id), CardCatalog.passiveAmount(source.card.id()), false);
                }
                run(m, side, source.card, source, Trigger.END_TURN, Target.NONE);
                m.resolveDeaths();
            }
        }
        onToolTrigger(m, side, m.player(side).tool, Trigger.END_TURN, Target.NONE);
    }

    static void run(Match m, int side, CardRef card, Minion source, Trigger trigger, Target chosen) {
        run(m, side, card, source, trigger, chosen, e -> true);
    }

    private static void run(Match m, int side, CardRef card, Minion source, Trigger trigger, Target chosen,
                            java.util.function.Predicate<Effect> filter) {
        if (m.over || CardCatalog.entry(card.id()) == null) return;
        if (m.effectDepth == 0) m.effectSteps = 0;
        if (m.effectDepth >= 32) { m.effectLoop(); return; }
        m.effectDepth++;
        Target previousSource = m.animationSource;
        m.animationSource = source == null ? Target.hero(side) : Target.mob(side, source.id);
        boolean used = false;
        try {
        for (Effect e : card.brew().apply(CardCatalog.entry(card.id()).effects())) {
            if (m.over) break;
            if (e.trigger() != trigger || !filter.test(e)) continue;
            if (!gateOpen(m.player(side), e.gate(), m.player(side).hand.size())) continue;
            if (++m.effectSteps > 512) { m.effectLoop(); break; }
            int multiplier = multiplier(m, side, source, e);
            if (multiplier == 0) continue;
            if (CardCatalog.spendsUse(e)) used = true;
            int amount = boostValue(m, side, e, e.amount() * multiplier, Target.NONE);
            boolean perTarget = e.boost() != null && e.boost().when().perTarget();
            int firstEvent = m.events.size();
            switch (e.action()) {
                case UPGRADE_TOOL -> {
                    Tool tool = m.player(side).tool;
                    if (tool != null) {
                        tool.attack = Math.min(1000, tool.attack + e.attack());
                        tool.uses = Math.min(1000, tool.uses + e.health());
                        m.event(MatchEvent.Type.TOOL_UPGRADE, side, Match.HERO, card, tool.attack, tool.uses, null);
                    }
                }
                case ENCHANT_TOOL -> {
                    Tool tool = m.player(side).tool;
                    if (tool != null) {
                        tool.enchantments.add(new Tool.Enchantment(card, e.attack(), e.health()));
                        tool.attack += e.attack();
                        tool.uses += e.health();
                        m.log(m.player(side).name + " enchants " + Match.name(tool.card) + " with " + Match.name(card) + ".", card);
                        m.event(MatchEvent.Type.ENCHANT, side, Match.HERO, card, tool.attack, tool.uses, null);
                    }
                }
                case CHOOSE_FROM_DECK -> m.chooseFromDeck(side, e, card);
                case DRAW -> { for (int i = 0; i < amount && !m.over; i++) m.draw(side); }
                case GAIN_XP -> m.gainXp(side, amount, card);
                case GAIN_MAX_XP -> m.gainMaxXp(side, amount, card);
                case GIVE -> { for (int i = 0; i < amount && !m.over; i++) m.giveCard(side, new CardRef(e.card(), false)); }
                case GIVE_RANDOM_CLASS -> {
                    var heroClass = m.player(side).heroClass;
                    var pool = Cards.collectible().stream().filter(id -> heroClass != craftbound.card.CardClass.NEUTRAL
                            && Cards.get(id).cardClass() == heroClass).toList();
                    // No fallback to Neutral or tokens while a class's new set is still empty.
                    for (int i = 0; i < amount && !pool.isEmpty() && !m.over; i++)
                        m.giveCard(side, new CardRef(pool.get(m.random.nextInt(pool.size())), false));
                }
                case PICK_ONE -> m.pickOne(side, e, card);
                case BREW_POTION -> m.brewPotion(side, card);
                case GIVE_RANDOM -> { for (int i = 0; i < amount && !m.over; i++)
                    m.giveCard(side, new CardRef(e.pool().get(m.random.nextInt(e.pool().size())), false)); }
                case SUMMON -> { for (int i = 0; i < amount && !m.over; i++) m.summon(side, new CardRef(e.card(), false)); }
                default -> {
                    for (Target target : targets(m, side, source, e, chosen)) {
                        Minion victim = target.hero() ? null : m.player(target.side()).minion(target.minionId());
                        if (!target.hero() && victim == null) continue;
                        int each = perTarget ? boostValue(m, side, e, e.amount() * multiplier, target) : amount;
                        switch (e.action()) {
                            case DAMAGE, TOOL_DAMAGE -> {
                                int damage = e.action() == Action.TOOL_DAMAGE ? m.player(side).tool == null ? 0 : boostValue(m, side, e, m.player(side).tool.attack, target) : each;
                                damage = amplify(m, side, card, damage);
                                boolean followUp = (trigger == Trigger.ON_KILL || trigger == Trigger.AFTER_ATTACK
                                        || trigger == Trigger.AFTER_SURVIVING_KILL) && CardCatalog.randomTarget(e.target());
                                if (followUp && damage > 0)
                                    m.event(MatchEvent.Type.SHOOT, side, source == null ? Match.HERO : source.id,
                                            card, damage, 160, target);
                                else if (trigger == Trigger.END_TURN && e.target() == Aim.RANDOM_ENEMY && source != null)
                                    m.event(MatchEvent.Type.SHOOT, side, source.id, card, damage, 0, target);
                                boolean alive = victim != null && !victim.isDead();
                                if (victim != null && source != null) m.damage(victim, damage, source);
                                else m.damage(target, damage);
                                if (alive && victim.isDead() && source == null && (Cards.get(card.id()).isTool()
                                        || Cards.get(card.id()).kind() == craftbound.card.CardKind.ENCHANTMENT)) {
                                    // A chained kill gets its own death and projectile beat too.
                                    m.resolveDeaths();
                                    onToolTrigger(m, side, m.player(side).tool, Trigger.ON_KILL, target);
                                }
                            }
                            case HEAL, WELL_FED -> m.heal(target, each, e.action() == Action.WELL_FED);
                            case GRANT_KEYWORD -> {
                                var options = victim == null ? List.<Keyword>of() : grantOptions(e, victim);
                                if (!options.isEmpty()) m.grantKeyword(victim, options.size() == 1 ? options.getFirst() : options.get(m.random.nextInt(options.size())));
                            }
                            case TRANSFORM -> m.transform(victim, e.card());
                            case BAD_OMEN -> {
                                MatchPlayer marked = m.player(target.side());
                                if (marked.badOmenSummon == null) {
                                    marked.badOmenSummon = e.card();
                                    marked.badOmenSource = side;
                                    m.log(marked.name + " gains Bad Omen.", card);
                                }
                            }
                            case CLEANSE -> {
                                if (target.hero()) {
                                    m.cleanseHero(target.side());
                                } else {
                                    m.clearAppliedEffects(victim);
                                    m.status(victim, MatchEvent.STATUS_CLEANSED);
                                }
                            }
                            case BUFF -> {
                                if (e.attack() != 0 || e.health() != 0)
                                    m.buff(victim, boostValue(m, side, e, e.attack() * multiplier, target), boostValue(m, side, e, e.health() * multiplier, target));
                                if (e.keyword() != null) m.grantKeyword(victim, e.keyword());
                            }
                            case DESTROY -> {
                                if (source == victim && trigger == Trigger.START_TURN && CardCatalog.hasTntFuse(card.id()))
                                    m.event(MatchEvent.Type.TNT_DETONATE, side, victim.id, card, 0, 0, Target.NONE);
                                victim.health = 0;
                            }
                            case WEAKEN_UNTIL_NEXT_TURN -> m.weakenUntilNextTurn(victim, each);
                            case BOUNCE -> m.bounce(victim);
                            case RETURN_AND_COPY -> {
                                // Snapshot the exact card before bounce removes the board target.
                                // CardRef preserves cosmetics/Trials training, not combat buffs or damage.
                                CardRef copy = victim.card;
                                m.bounce(victim);
                                for (int i = 0; i < amount && !m.over; i++) m.giveCard(side, copy);
                            }
                            case MINE -> m.mine(victim);
                            case TOTEM -> { victim.totem = true; m.status(victim, MatchEvent.STATUS_TOTEM); }
                            case INVULNERABLE -> { victim.invulnerable = true; m.status(victim, MatchEvent.STATUS_INVULNERABLE); }
                            case FREEZE -> {
                                if (target.hero()) {
                                    MatchPlayer hero = m.player(target.side());
                                    if (!hero.frozen && !hero.skippedAttackFromFreeze) {
                                        hero.frozen = true;
                                        m.event(MatchEvent.Type.STATUS, target.side(), Match.HERO, null, MatchEvent.STATUS_FROZEN, 0, null);
                                    }
                                } else if (!victim.frozen && !victim.skippedAttackFromFreeze) {
                                    victim.frozen = true;
                                    m.status(victim, MatchEvent.STATUS_FROZEN);
                                }
                            }
                            case WITHER -> { if (!victim.withered) { victim.withered = true; m.status(victim, MatchEvent.STATUS_WITHERED); } }
                            case REVEAL_HAND, DISCARD_FROM_HAND, STEAL_FROM_HAND, SHUFFLE_FROM_HAND -> {
                                if (target.hero()) m.handEffect(e.action(), side, target.side(), amount, e.chooser(), card);
                            }
                            case DESTROY_TOOL -> {
                                MatchPlayer owner = m.player(target.side());
                                if (owner.tool != null) {
                                    m.event(MatchEvent.Type.BREAK, target.side(), Match.HERO, owner.tool.card, 0, 0, null);
                                    owner.tool = null;
                                }
                            }
                            default -> throw new IllegalStateException("Unexpected targeted action " + e.action());
                        }
                    }
                }
            }
            // One boundary per authored effect, not per target: an area effect still lands together.
            // Insert before nested trigger events as well, preserving their causal order.
            if (m.events.size() > firstEvent)
                m.events.add(firstEvent, new MatchEvent(MatchEvent.Type.EFFECT, side,
                        source == null ? Match.HERO : source.id, card, 0, 0, Target.NONE, m.animationSource));
        }
            // Limited uses: one use per time this card's own trigger runs; the last use destroys it.
            if (used && source != null && source.card == card && !source.isDead()) m.spendUse(side, source);
        } finally {
            m.refreshAuras(); m.effectDepth--; m.animationSource = previousSource;
            if (m.effectDepth == 0) m.pruneChoices();
        }
    }

    private static List<Target> targets(Match m, int side, Minion source, Effect e, Target chosen) {
        List<Target> targets = possibleTargets(m, side, source, e, chosen);
        if (CardCatalog.randomTarget(e.target()) && !targets.isEmpty())
            return List.of(targets.get(m.random.nextInt(targets.size())));
        return targets;
    }

    // Read-only candidate enumeration: AI evaluation must not consume random rolls.
    static List<Target> possibleTargets(Match m, int side, Minion source, Effect e, Target chosen) {
        List<Target> targets = new ArrayList<>();
        switch (e.target()) {
            case EVENT -> { if (chosen != null && !chosen.isNone()) targets.add(chosen); }
            case KILLER -> {
                if (source != null && m.inPlay(source.killerSide) && m.isEnemy(side, source.killerSide) && source.killerId > 0)
                    targets.add(Target.mob(source.killerSide, source.killerId));
            }
            case SELF -> { if (source != null && m.sideOf(source) >= 0) targets.add(Target.mob(side, source.id)); }
            case FRIENDLY_HERO -> targets.add(Target.hero(side));
            case ENEMY_HERO -> targets.add(Target.hero(m.opponentSide(side)));
            case EACH_HERO, EACH_ENEMY_HERO, RANDOM_HERO -> {
                for (int seat = 0; seat < m.players.length; seat++) {
                    if (m.inPlay(seat) && (e.target() != Aim.EACH_ENEMY_HERO || m.isEnemy(side, seat)))
                        targets.add(Target.hero(seat));
                }
            }
            case CHOSEN_CHARACTER, CHOSEN_ANY, CHOSEN_FRIENDLY, CHOSEN_ENEMY -> {
                if (!chosen.isNone() && (e.target() == Aim.CHOSEN_CHARACTER || e.target() == Aim.CHOSEN_ANY
                        || e.target() == Aim.CHOSEN_FRIENDLY && chosen.side() == side
                        || e.target() == Aim.CHOSEN_ENEMY && m.isEnemy(side, chosen.side()))) targets.add(chosen);
            }
            default -> {
                if (e.target() == Aim.RANDOM_ENEMY) for (int i=0;i<m.players.length;i++) if (m.isEnemy(side, i) && m.inPlay(i)) targets.add(Target.hero(i));
                if (e.target() == Aim.RANDOM_FRIENDLY) targets.add(Target.hero(side));
                for (int s = 0; s < m.players.length; s++) {
                    if (!m.inPlay(s)) continue;
                    if ((e.target() == Aim.FRIENDLY_MOBS || e.target() == Aim.RANDOM_FRIENDLY_MOB || e.target() == Aim.RANDOM_FRIENDLY) && s != side) continue;
                    if ((e.target() == Aim.ENEMY_MOBS || e.target() == Aim.RANDOM_ENEMY) && !m.isEnemy(side, s)) continue;
                    for (Minion minion : new ArrayList<>(m.player(s).board)) targets.add(Target.mob(s, minion.id));
                }
            }
        }
        targets.removeIf(t -> {
            // Damage-dealt Chosen any is the legacy board-only target. The explicit character
            // target (or Event) opts into hero hits without changing old mob-only abilities.
            if (t.hero() && e.trigger() == Trigger.DAMAGE_DEALT && e.target() == Aim.CHOSEN_ANY) return true;
            if (t.hero()) return e.type() != null || !CardCatalog.canAffectHero(e.action());
            Minion minion = m.player(t.side()).minion(t.minionId());
            return minion == null || minion.isDead() || e.excludeSelf() && minion == source || !matches(e, minion.definition())
                    || e.action() == Action.GRANT_KEYWORD && grantOptions(e, minion).isEmpty();
        });
        // Random damage/healing selects a shared team Health pool once. Other hero effects
        // (including Freeze and tool removal) still operate on a particular owner's hero.
        if (CardCatalog.randomTarget(e.target()) && (e.action() == Action.DAMAGE || e.action() == Action.TOOL_DAMAGE || e.action() == Action.HEAL || e.action() == Action.WELL_FED)) {
            Set<Integer> pools = new HashSet<>();
            targets.removeIf(t -> t.hero() && !pools.add(m.healthPool(t.side())));
        }
        return targets;
    }
    private CardEffects() {}
}
