package craftbound.card;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.resources.Identifier;

/** Immutable, validated catalogue. A bad file never partially replaces the running rules. */
public final class CardCatalog {
    public static final int MAX_JSON = 250_000;
    public enum Trigger { PLAY, DROP, END_TURN, AFTER_ATTACK, DAMAGE_DEALT, ATTACKED, HARVEST, ON_KILL, CARD_ADDED, ITEM_PLAYED, MINED, HEALED, DISMANTLE, START_TURN, ACTIVATE, AFTER_SURVIVING_KILL }
    public enum Action { BUFF, DAMAGE, HEAL, DRAW, CHOOSE_FROM_DECK, GAIN_XP, SUMMON, GIVE, BOUNCE, RETURN_AND_COPY, TOTEM, INVULNERABLE, FREEZE, WITHER, TOOL_DAMAGE, DESTROY_TOOL, ENCHANT_TOOL, CLEANSE, MINE, GRANT_KEYWORD, GIVE_RANDOM, WELL_FED, TRANSFORM, GAIN_MAX_XP, DESTROY, WEAKEN_UNTIL_NEXT_TURN, GIVE_RANDOM_CLASS, UPGRADE_TOOL, BAD_OMEN,
        REVEAL_HAND, DISCARD_FROM_HAND, STEAL_FROM_HAND, SHUFFLE_FROM_HAND, PICK_ONE, BREW_POTION }
    /** Who picks the card for Discard/Steal/Shuffle from hand. YOU = the effect's controller. */
    public enum HandChooser { YOU, OPPONENT, RANDOM }
    /** Effects that act on the enemy hero's hand. */
    public static boolean handAction(Action action) {
        return action == Action.REVEAL_HAND || handPick(action);
    }
    /** Hand effects that move a card and so need someone (or chance) to pick it. */
    public static boolean handPick(Action action) {
        return action == Action.DISCARD_FROM_HAND || action == Action.STEAL_FROM_HAND || action == Action.SHUFFLE_FROM_HAND;
    }
    public enum Gate { ALWAYS, TOOL_EQUIPPED, HERO_HURT, EMPTY_HAND }
    public enum CastFx { AUTO, PLAIN, EGG, POTION, EYE_OF_ENDER, ARROW, FOX }
    public enum DeathFx { AUTO, PLAIN, SPLIT, EXPLODE, CRACKS }
    public enum ArrivalFx { AUTO, EMBERS, FROST, ARCANE, PLAIN, BLOCK, LEGENDARY, WARDEN, WITHER, ENDER_DRAGON }
    public enum Aim { SELF, FRIENDLY_MOBS, ENEMY_MOBS, ALL_MOBS, FRIENDLY_HERO, ENEMY_HERO, RANDOM_FRIENDLY_MOB, RANDOM_ENEMY, CHOSEN_FRIENDLY, CHOSEN_ENEMY, CHOSEN_ANY, CHOSEN_CHARACTER, EVENT, KILLER, RANDOM_FRIENDLY, RANDOM_BLOCK, RANDOM_MOB, RANDOM_HERO, EACH_ENEMY_HERO, EACH_HERO }
    public static boolean eachHero(Aim aim) { return aim == Aim.EACH_ENEMY_HERO || aim == Aim.EACH_HERO; }
    public static boolean randomTarget(Aim aim) {
        return aim == Aim.RANDOM_ENEMY || aim == Aim.RANDOM_FRIENDLY_MOB || aim == Aim.RANDOM_FRIENDLY
                || aim == Aim.RANDOM_BLOCK || aim == Aim.RANDOM_MOB || aim == Aim.RANDOM_HERO;
    }
    public static boolean canAffectHero(Action action) {
        return List.of(Action.DAMAGE, Action.HEAL, Action.WELL_FED, Action.CLEANSE, Action.FREEZE,
                Action.DESTROY_TOOL, Action.BAD_OMEN, Action.TOOL_DAMAGE).contains(action) || handAction(action);
    }
    public enum Passive { NONE, AURA_ATTACK, ARMOR, GRAZER, BATTLE_HARDENED, SCHOOLING, AURA_STATS, NEGATE_KEYWORD, AMPLIFY_ELEMENT }
    // Keywords a Negate keyword passive can switch off on enemy cards.
    public static final List<Keyword> NEGATABLE = List.of(Keyword.SHIELD, Keyword.SPRINT, Keyword.RANGED, Keyword.IGNORE_SHIELD);
    public enum BoardKind { MOB, BLOCK, BOTH }
    public enum Scope { SELF, FRIENDLY, ENEMY, ANY }
    // Elements tag any card (Fire, Frost...). Append only: saved cards store the names.
    public enum Element { FIRE, FROST, WITHER, POISON, LIGHTNING, EXPLOSIVE, WATER, MAGIC, ENDER;
        public String displayName() { return title(name()); }
    }
    // When an effect's optional boost applies. TARGET_* conditions are checked for each target.
    public enum BoostWhen { YOU_CONTROL, ENEMY_CONTROLS, ANYONE_CONTROLS, TARGET_IS, TARGET_FROZEN, TARGET_WITHERED, TARGET_DAMAGED;
        public boolean perTarget() { return name().startsWith("TARGET_"); }
        public boolean needsMatch() { return this == YOU_CONTROL || this == ENEMY_CONTROLS || this == ANYONE_CONTROLS || this == TARGET_IS; } }
    public enum BoostHow { DOUBLE, TRIPLE, PLUS }
    /** "If ..." on an effect: scales its number when the condition holds. card, or else kind + optional type, says what must match. */
    public record Boost(BoostWhen when, Identifier card, MobType type, BoardKind kind, BoostHow how, int amount) {
        public int apply(int value) { return switch (how) { case DOUBLE -> value * 2; case TRIPLE -> value * 3; case PLUS -> value + (value < 0 ? -amount : amount); }; }
    }
    public static final List<Action> BOOSTABLE = List.of(Action.DAMAGE, Action.TOOL_DAMAGE, Action.HEAL, Action.WELL_FED, Action.BUFF,
            Action.WEAKEN_UNTIL_NEXT_TURN, Action.DRAW, Action.GAIN_XP);
    public enum CountScope { NONE, FRIENDLY, ENEMY, ALL }
    public record Effect(Trigger trigger, Action action, Aim target, BoardKind boardKind, MobType type, int amount,
                         int attack, int health, Identifier card, boolean excludeSelf, Keyword keyword, List<Identifier> pool,
                         CountScope countScope, BoardKind countKind, MobType countType, boolean countExcludeSelf,
                         Scope eventScope, MobType eventType, Gate gate, HandChooser chooser, boolean randomKeyword, Boost boost) {
        public Effect(Trigger trigger, Action action, Aim target, BoardKind boardKind, MobType type, int amount,
                      int attack, int health, Identifier card, boolean excludeSelf, Keyword keyword, List<Identifier> pool,
                      CountScope countScope, BoardKind countKind, MobType countType, boolean countExcludeSelf,
                      Scope eventScope, MobType eventType, Gate gate, HandChooser chooser, boolean randomKeyword) {
            this(trigger, action, target, boardKind, type, amount, attack, health, card, excludeSelf, keyword, pool,
                    countScope, countKind, countType, countExcludeSelf, eventScope, eventType, gate, chooser, randomKeyword, null);
        }
        public Effect(Trigger trigger, Action action, Aim target, BoardKind boardKind, MobType type, int amount,
                      int attack, int health, Identifier card, boolean excludeSelf, Keyword keyword, List<Identifier> pool,
                      CountScope countScope, BoardKind countKind, MobType countType, boolean countExcludeSelf,
                      Scope eventScope, MobType eventType, Gate gate, HandChooser chooser) {
            this(trigger, action, target, boardKind, type, amount, attack, health, card, excludeSelf, keyword, pool,
                    countScope, countKind, countType, countExcludeSelf, eventScope, eventType, gate, chooser, false);
        }
        public Effect(Trigger trigger, Action action, Aim target, BoardKind boardKind, MobType type, int amount,
                      int attack, int health, Identifier card, boolean excludeSelf, Keyword keyword, List<Identifier> pool,
                      CountScope countScope, BoardKind countKind, MobType countType, boolean countExcludeSelf,
                      Scope eventScope, MobType eventType, Gate gate) {
            this(trigger, action, target, boardKind, type, amount, attack, health, card, excludeSelf, keyword, pool,
                    countScope, countKind, countType, countExcludeSelf, eventScope, eventType, gate, HandChooser.YOU);
        }
        public Effect(Trigger trigger, Action action, Aim target, BoardKind boardKind, MobType type, int amount,
                      int attack, int health, Identifier card, boolean excludeSelf) {
            this(trigger, action, target, boardKind, type, amount, attack, health, card, excludeSelf,
                    null, List.of(), CountScope.NONE, BoardKind.BOTH, null, false, defaultScope(trigger), null, Gate.ALWAYS);
        }
    }
    public static boolean observed(Trigger t) {
        return t == Trigger.CARD_ADDED || t == Trigger.ITEM_PLAYED || t == Trigger.MINED || t == Trigger.HEALED;
    }
    public static Scope defaultScope(Trigger t) { return t == Trigger.HEALED ? Scope.SELF : Scope.FRIENDLY; }
    public record Entry(CardDefinition definition, String name, String text, boolean autoText,
                        Passive passive, int passiveAmount, List<Effect> effects, MobType auraType, boolean deleted, int auraHealth, CastFx castFx, DeathFx deathFx, ArrivalFx arrivalFx, BoardKind auraKind, Keyword negateKeyword,
                        Element element, Element ampElement, BoostHow ampHow, boolean potion, int uses) {
        public Entry(CardDefinition definition, String name, String text, boolean autoText,
                     Passive passive, int passiveAmount, List<Effect> effects, MobType auraType, boolean deleted, int auraHealth, CastFx castFx, DeathFx deathFx, ArrivalFx arrivalFx, BoardKind auraKind, Keyword negateKeyword,
                     Element element, Element ampElement, BoostHow ampHow, boolean potion) {
            this(definition, name, text, autoText, passive, passiveAmount, effects, auraType, deleted, auraHealth, castFx, deathFx, arrivalFx, auraKind, negateKeyword, element, ampElement, ampHow, potion, 0);
        }
        public Entry(CardDefinition definition, String name, String text, boolean autoText,
                     Passive passive, int passiveAmount, List<Effect> effects, MobType auraType, boolean deleted, int auraHealth, CastFx castFx, DeathFx deathFx, ArrivalFx arrivalFx, BoardKind auraKind, Keyword negateKeyword,
                     Element element, Element ampElement, BoostHow ampHow) {
            this(definition, name, text, autoText, passive, passiveAmount, effects, auraType, deleted, auraHealth, castFx, deathFx, arrivalFx, auraKind, negateKeyword, element, ampElement, ampHow, false);
        }
        public Entry(CardDefinition definition, String name, String text, boolean autoText,
                     Passive passive, int passiveAmount, List<Effect> effects, MobType auraType, boolean deleted, int auraHealth, CastFx castFx, DeathFx deathFx, ArrivalFx arrivalFx, BoardKind auraKind, Keyword negateKeyword) {
            this(definition, name, text, autoText, passive, passiveAmount, effects, auraType, deleted, auraHealth, castFx, deathFx, arrivalFx, auraKind, negateKeyword, null, null, BoostHow.DOUBLE);
        }
        public Entry(CardDefinition definition, String name, String text, boolean autoText,
                     Passive passive, int passiveAmount, List<Effect> effects, MobType auraType, boolean deleted, int auraHealth, CastFx castFx, DeathFx deathFx, ArrivalFx arrivalFx, BoardKind auraKind) {
            this(definition, name, text, autoText, passive, passiveAmount, effects, auraType, deleted, auraHealth, castFx, deathFx, arrivalFx, auraKind, null, null, null, BoostHow.DOUBLE);
        }
        public Entry(CardDefinition definition, String name, String text, boolean autoText,
                     Passive passive, int passiveAmount, List<Effect> effects) {
            this(definition, name, text, autoText, passive, passiveAmount, effects, null, false, 0, CastFx.AUTO, DeathFx.AUTO, ArrivalFx.AUTO, BoardKind.MOB);
        }
        public boolean hasAura() { return passive == Passive.AURA_ATTACK || passive == Passive.AURA_STATS; }
        public int auraHealthBonus() { return passive == Passive.AURA_STATS ? auraHealth : 0; }
        public boolean auraMatches(CardDefinition card) {
            boolean block = card.keywords().contains(Keyword.BLOCK);
            return card.isMob() && (auraKind == BoardKind.BOTH || (auraKind == BoardKind.BLOCK) == block)
                    && (auraType == null || auraType == card.mobType());
        }
    }
    public record State(Map<Identifier, Entry> entries, String json) {}
    private static final State BUILTIN = parse(bundledJson());
    private static volatile State current = BUILTIN;

    public static State current() { return current; }
    public static State builtin() { return BUILTIN; }
    public static Entry entry(Identifier id) { return current.entries().get(id); }
    public static boolean hasTntFuse(Identifier id) {
        Entry card = entry(id);
        if (card == null || !card.definition().keywords().contains(Keyword.BLOCK)
                || !(id.getPath().equals("tnt") || "minecraft:tnt".equals(card.definition().artItem()))) return false;
        return card.effects().stream().anyMatch(f -> f.trigger() == Trigger.START_TURN && f.action() == Action.DESTROY
                && f.gate()==Gate.ALWAYS && f.target() == Aim.SELF && !f.excludeSelf() && f.boardKind() != BoardKind.MOB);
    }
    public static void install(State state) { current = state; }
    public static boolean passive(Identifier id, Passive passive) {
        Entry e = entry(id);
        return e != null && e.passive() == passive;
    }
    public static int passiveAmount(Identifier id) { return entry(id).passiveAmount(); }

    private static String bundledJson() {
        try (InputStream in = CardCatalog.class.getResourceAsStream("/craftbound/cards.json")) {
            if (in == null) throw new IllegalStateException("Missing bundled card catalogue");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) { throw new IllegalStateException(e); }
    }

    public static State parse(String json) {
        if (json.length() > 1_000_000) throw new IllegalArgumentException("Catalogue exceeds 1,000,000 characters");
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (integer(root, "version", 1, 1) != 1) throw new IllegalArgumentException("Unsupported catalogue version");
        JsonArray cards = root.getAsJsonArray("cards");
        if (cards != null && cards.asList().stream().noneMatch(c -> c.isJsonObject()
                && c.getAsJsonObject().has("id") && "craftbound:crafted_sword".equals(c.getAsJsonObject().get("id").getAsString()))) {
            for (JsonElement token : JsonParser.parseString(bundledJson()).getAsJsonObject().getAsJsonArray("cards"))
                if ("craftbound:crafted_sword".equals(token.getAsJsonObject().get("id").getAsString())) cards.add(token.deepCopy());
        }
        if (cards == null || cards.isEmpty() || cards.size() > 1000) throw new IllegalArgumentException("Expected 1–1000 cards");
        Map<Identifier, Entry> entries = new LinkedHashMap<>();
        for (JsonElement element : cards) {
            JsonObject c = element.getAsJsonObject();
            String label = string(c, "id", 120);
            try {
                Identifier id = Identifier.parse(label);
                if (!id.getNamespace().equals("craftbound")) throw new IllegalArgumentException("Use the craftbound: namespace");
                if (entries.containsKey(id)) throw new IllegalArgumentException("Duplicate ID");
                CardKind kind = enumeration(CardKind.class, c, "kind");
                boolean spell = kind == CardKind.ITEM || kind == CardKind.ENCHANTMENT;
                CardClass cardClass = enumeration(CardClass.class, c, "class");
                if (kind == CardKind.ENCHANTMENT && cardClass == CardClass.NEUTRAL) throw new IllegalArgumentException("Enchantments must belong to a class");
                CardRarity rarity = enumeration(CardRarity.class, c, "rarity");
                int cost = integer(c, "cost", 0, 10), attack = integer(c, "attack", 0, 100);
                int health = integer(c, "health", spell ? 0 : 1, 100);
                if (spell && (attack != 0 || health != 0)) throw new IllegalArgumentException("Actions and Enchantments must have 0 attack and health");
                String typeName = string(c, "type", 40);
                MobType type = typeName.isEmpty() ? null : MobType.valueOf(typeName);
                if (type != null && kind != CardKind.MOB) throw new IllegalArgumentException("Only mobs can have a mob type");
                List<Keyword> keywords = new ArrayList<>();
                for (JsonElement k : c.getAsJsonArray("keywords")) {
                    Keyword keyword = Keyword.valueOf(k.getAsString());
                    if (keywords.contains(keyword)) throw new IllegalArgumentException("Duplicate keyword");
                    keywords.add(keyword);
                }
                if (keywords.contains(Keyword.BLOCK) && (kind != CardKind.MOB || attack != 0))
                    throw new IllegalArgumentException("Blocks must be mobs with 0 attack");
                if (kind != CardKind.MOB && keywords.stream().anyMatch(k -> k != Keyword.RANGED && k != Keyword.IGNORE_SHIELD && k != Keyword.REPAIR
                        && k != Keyword.THROW && k != Keyword.LOYALTY && k != Keyword.CHANNELING))
                    throw new IllegalArgumentException("Tools support Ranged, Ignore Shield, Repair and Throw; other combat keywords need a mob");
                if (kind != CardKind.TOOL && keywords.contains(Keyword.THROW)) throw new IllegalArgumentException("Throw is a tool keyword");
                if (kind != CardKind.ENCHANTMENT && (keywords.contains(Keyword.LOYALTY) || keywords.contains(Keyword.CHANNELING)))
                    throw new IllegalArgumentException("Loyalty and Channeling are enchantment keywords");
                if (kind != CardKind.TOOL && keywords.contains(Keyword.REPAIR))
                    throw new IllegalArgumentException("Repair is a tool keyword");
                if (spell && keywords.stream().anyMatch(k -> kind != CardKind.ENCHANTMENT || k != Keyword.LOYALTY && k != Keyword.CHANNELING))
                    throw new IllegalArgumentException("Actions and Enchantments cannot have combat keywords");
                String name = string(c, "name", 80), text = string(c, "text", 1200), art = string(c, "art", 120);
                if (name.isBlank()) throw new IllegalArgumentException("Name is empty");
                if (!art.isEmpty()) Identifier.parse(art);
                Passive passive = enumeration(Passive.class, c, "passive");
                int passiveAmount = integer(c, "passiveAmount", 0, 100);
                int auraHealth = c.has("auraHealth") ? integer(c, "auraHealth", 0, 100) : 0;
                // Negate keyword reuses the aura filters to pick which enemies it affects; it defaults to mobs and blocks.
                BoardKind auraKind = optionalEnum(BoardKind.class, c, "auraKind", passive == Passive.NEGATE_KEYWORD ? BoardKind.BOTH : BoardKind.MOB);
                if ((passive == Passive.AURA_ATTACK || passive == Passive.AURA_STATS)
                        && auraKind != BoardKind.MOB && passiveAmount > 0)
                    throw new IllegalArgumentException("Auras affecting blocks can only grant Health; set Attack bonus to 0");
                if (passive != Passive.NONE && kind != CardKind.MOB) throw new IllegalArgumentException("Passives require a mob");
                Keyword negateKeyword = passive == Passive.NEGATE_KEYWORD ? optionalEnum(Keyword.class, c, "negateKeyword", null) : null;
                Element cardElement = optionalEnum(Element.class, c, "element", null);
                // Potions are the Actions a Brewing Stand can upgrade.
                boolean potion = c.has("potion") && bool(c, "potion");
                // Uses: a board card is destroyed after its triggered abilities have run this many times (0 = no limit).
                int uses = c.has("uses") ? integer(c, "uses", 0, 20) : 0;
                if (uses > 0 && kind != CardKind.MOB) throw new IllegalArgumentException("Only mobs and blocks can have limited uses");
                if (potion && kind != CardKind.ITEM) throw new IllegalArgumentException("Only Actions can be potions");
                Element ampElement = passive == Passive.AMPLIFY_ELEMENT ? optionalEnum(Element.class, c, "ampElement", null) : null;
                BoostHow ampHow = optionalEnum(BoostHow.class, c, "ampHow", BoostHow.DOUBLE);
                if (passive == Passive.AMPLIFY_ELEMENT && ampElement == null) throw new IllegalArgumentException("Amplify element needs an element");
                if (passive == Passive.AMPLIFY_ELEMENT && ampHow == BoostHow.PLUS && passiveAmount < 1) throw new IllegalArgumentException("Amplify +damage needs an amount of at least 1");
                if (passive == Passive.NEGATE_KEYWORD && !NEGATABLE.contains(negateKeyword))
                    throw new IllegalArgumentException("Negate keyword needs Shield, Sprint, Ranged or Ignore Shield");
                if ((passive == Passive.BATTLE_HARDENED || passive == Passive.SCHOOLING) && keywords.contains(Keyword.BLOCK))
                    throw new IllegalArgumentException("Attack-growth passives require a non-block mob");
                List<Effect> effects = new ArrayList<>();
                List<CardDefinition.Related> related = new ArrayList<>();
                JsonArray effectJson = c.getAsJsonArray("effects");
                if (effectJson == null || effectJson.size() > 16) throw new IllegalArgumentException("Expected at most 16 effects");
                Aim chosen = null;
                for (JsonElement x : effectJson) {
                    JsonObject e = x.getAsJsonObject();
                    Trigger trigger = enumeration(Trigger.class, e, "trigger");
                    Action action = enumeration(Action.class, e, "action");
                    Aim target = enumeration(Aim.class, e, "target");
                    String filter = string(e, "type", 40), card = string(e, "card", 120);
                    // Grant keyword can roll a random combat keyword: "keyword": "RANDOM".
                    boolean randomKeyword = e.has("keyword") && e.get("keyword").isJsonPrimitive() && "RANDOM".equals(e.get("keyword").getAsString());
                    Effect effect = new Effect(trigger, action, target, enumeration(BoardKind.class, e, "boardKind"), filter.isEmpty() ? null : MobType.valueOf(filter),
                            integer(e, "amount", 1, 100), integer(e, "attack", action == Action.BUFF ? -100 : 0, 100), integer(e, "health", 0, 100),
                            card.isEmpty() ? null : Identifier.parse(card), bool(e, "excludeSelf"),
                            randomKeyword ? null : optionalEnum(Keyword.class, e, "keyword", null), pool(e),
                            optionalEnum(CountScope.class, e, "countScope", CountScope.NONE),
                            optionalEnum(BoardKind.class, e, "countKind", BoardKind.BOTH),
                            optionalEnum(MobType.class, e, "countType", null), e.has("countExcludeSelf") && bool(e, "countExcludeSelf"),
                            optionalEnum(Scope.class, e, "eventScope", defaultScope(trigger)), optionalEnum(MobType.class, e, "eventType", null),optionalEnum(Gate.class,e,"gate",Gate.ALWAYS),
                            optionalEnum(HandChooser.class, e, "chooser", HandChooser.YOU), randomKeyword, boost(e));
                    if (effect.boost() != null) {
                        Boost b = effect.boost();
                        if (!BOOSTABLE.contains(action)) throw new IllegalArgumentException("Boosts work on Damage, Tool damage, Heal, Well Fed, Buff, Weaken, Draw and Gain XP");
                        if (b.when().perTarget() && (action == Action.DRAW || action == Action.GAIN_XP)) throw new IllegalArgumentException("Target conditions need an action that hits targets");
                        if (b.how() == BoostHow.PLUS && (b.amount() < 1 || b.amount() > 20)) throw new IllegalArgumentException("Boost bonus must be 1 to 20");
                    }
                    if (randomKeyword && action != Action.GRANT_KEYWORD) throw new IllegalArgumentException("Only Grant keyword can pick a random keyword");
                    if(effect.gate()!=Gate.ALWAYS && trigger==Trigger.PLAY) throw new IllegalArgumentException("Timing conditions require a non-Play trigger");
                    if (e.has("chooser") && !string(e, "chooser", 40).isEmpty() && !handPick(action)) throw new IllegalArgumentException("Only Discard, Steal and Shuffle from hand choose a card");
                    if (handAction(action)) {
                        if (target != Aim.ENEMY_HERO && target != Aim.EACH_ENEMY_HERO)
                            throw new IllegalArgumentException("Hand effects target the enemy hero or each enemy hero");
                        if (effect.type() != null || effect.excludeSelf()) throw new IllegalArgumentException("Hand effects cannot filter mobs");
                        if (action == Action.REVEAL_HAND ? effect.amount() != 1 : effect.amount() > 10)
                            throw new IllegalArgumentException(action == Action.REVEAL_HAND ? "Reveal hand uses amount 1" : "Hand effects move at most 10 cards");
                    }
                    if (action == Action.ENCHANT_TOOL && (kind != CardKind.ENCHANTMENT || trigger != Trigger.PLAY)) throw new IllegalArgumentException("Enchant tool requires an Enchantment played from hand");
                    if (action == Action.UPGRADE_TOOL && effect.attack() == 0 && effect.health() == 0)
                        throw new IllegalArgumentException("Improve equipped tool needs a positive Attack or Durability bonus; the other may be 0");
                    if (target == Aim.RANDOM_MOB && effect.boardKind() != BoardKind.MOB || target == Aim.RANDOM_BLOCK && effect.boardKind() != BoardKind.BLOCK)
                        throw new IllegalArgumentException("Random mob/block must use its matching board card kind");
                    if (action == Action.ENCHANT_TOOL && effect.attack() == 0 && effect.health() == 0
                            && effectJson.asList().stream().map(JsonElement::getAsJsonObject).noneMatch(f -> !f.get("trigger").getAsString().equals("PLAY"))) throw new IllegalArgumentException("Enchantment must improve attack or durability");
                    if (action == Action.MINE && (effect.boardKind() != BoardKind.BLOCK || effect.type() != MobType.ORE
                            || (target != Aim.CHOSEN_FRIENDLY && target != Aim.RANDOM_FRIENDLY_MOB)
                            || (trigger != Trigger.PLAY && trigger != Trigger.AFTER_ATTACK)))
                        throw new IllegalArgumentException("Mine requires a friendly Ore block on Play or After attack");
                    if (trigger == Trigger.ACTIVATE && !keywords.contains(Keyword.BLOCK)) throw new IllegalArgumentException("Activate requires a block");
                    if (trigger == Trigger.AFTER_SURVIVING_KILL && (kind != CardKind.MOB || keywords.contains(Keyword.BLOCK)))
                        throw new IllegalArgumentException("After attacking, killing and surviving requires a non-block mob");
                    if (target == Aim.KILLER && trigger != Trigger.DROP) throw new IllegalArgumentException("Killer target requires Death/Drop");
                    if (action == Action.WEAKEN_UNTIL_NEXT_TURN && effect.boardKind() != BoardKind.MOB) throw new IllegalArgumentException("Temporary weakening requires a mob");
                    boolean aimed = target.name().startsWith("CHOSEN");
                    if (action == Action.BUFF && effect.attack() != 0 && effect.boardKind() != BoardKind.MOB)
                        throw new IllegalArgumentException("Attack buffs must target mobs only; use a separate health effect for blocks");
                    if (action == Action.RETURN_AND_COPY && effect.amount() > 10)
                        throw new IllegalArgumentException("Return and copy allows at most 10 extra copies");
                    if (action == Action.CHOOSE_FROM_DECK && (target != Aim.SELF || effect.excludeSelf() || effect.amount() > 3))
                        throw new IllegalArgumentException("Choose from deck uses SELF, 1–3 options, and no exclude-self filter");
                    boolean generator = action == Action.DRAW || action == Action.GAIN_XP || action == Action.GAIN_MAX_XP || action == Action.GIVE || action == Action.GIVE_RANDOM || action == Action.GIVE_RANDOM_CLASS || action == Action.SUMMON || action == Action.ENCHANT_TOOL || action == Action.UPGRADE_TOOL || action == Action.PICK_ONE || action == Action.BREW_POTION;
                    if (generator && target != Aim.SELF) throw new IllegalArgumentException(action + " uses SELF (its owner)");
                    if (generator && effect.amount() > 10) throw new IllegalArgumentException("Draw/summon/give/XP amount cannot exceed 10");
                    if (generator && (effect.type() != null || effect.excludeSelf())) throw new IllegalArgumentException("Owner effects cannot filter mobs");
                    if ((action == Action.GIVE || action == Action.SUMMON || action == Action.TRANSFORM || action == Action.BAD_OMEN) != (effect.card() != null))
                        throw new IllegalArgumentException("Give/Summon/Transform require a card ID");
                    if (trigger == Trigger.PLAY && aimed) {
                        if (chosen != null && chosen != target) throw new IllegalArgumentException("All chosen Play targets must use the same side");
                        chosen = target;
                    }
                    if (aimed && trigger != Trigger.PLAY && trigger != Trigger.DAMAGE_DEALT && trigger != Trigger.ATTACKED)
                        throw new IllegalArgumentException("This trigger has no chosen target");
                    if ((trigger == Trigger.DAMAGE_DEALT || trigger == Trigger.ATTACKED) && target != Aim.CHOSEN_ANY && target != Aim.EVENT
                            && !(trigger == Trigger.DAMAGE_DEALT && target == Aim.CHOSEN_CHARACTER))
                        throw new IllegalArgumentException("Damage/attacked effects must target the affected character");
                    if (kind == CardKind.ITEM && trigger != Trigger.PLAY) throw new IllegalArgumentException("Actions only trigger when played");
                    if ((trigger == Trigger.HARVEST || trigger == Trigger.DISMANTLE || trigger == Trigger.DROP || trigger == Trigger.ATTACKED)
                            && kind != CardKind.MOB) throw new IllegalArgumentException("This trigger requires a mob or block");
                    if (trigger == Trigger.HARVEST && type != MobType.CROP) throw new IllegalArgumentException("Harvest requires a Crop");
                    if (trigger == Trigger.DISMANTLE && !keywords.contains(Keyword.BLOCK)) throw new IllegalArgumentException("Dismantle requires a block");
                    if (!effect.randomKeyword() && (action == Action.GRANT_KEYWORD || action == Action.BUFF && effect.keyword() != null) && (effect.keyword() != Keyword.SHIELD && effect.keyword() != Keyword.SPRINT && effect.keyword() != Keyword.RANGED && effect.keyword() != Keyword.IGNORE_SHIELD))
                        throw new IllegalArgumentException("Grant keyword requires Shield, Sprint, Ranged or Ignore Shield");
                    if (effect.keyword() != null && effect.keyword() != Keyword.SHIELD && effect.boardKind() != BoardKind.MOB)
                        throw new IllegalArgumentException("Sprint/Ranged/Ignore Shield grants require mobs only");
                    if (action != Action.GRANT_KEYWORD && action != Action.BUFF && effect.keyword() != null) throw new IllegalArgumentException("Only Buff and Grant keyword use keyword");
                    if ((action == Action.GIVE_RANDOM || action == Action.PICK_ONE) != !effect.pool().isEmpty()) throw new IllegalArgumentException("Random Give and Pick one need a card list");
                    if (action == Action.PICK_ONE && (effect.pool().size() < 2 || effect.pool().size() > 3 || effect.pool().stream().distinct().count() != effect.pool().size()))
                        throw new IllegalArgumentException("Pick one needs 2 or 3 different cards to choose from");
                    if (effect.countScope() != CountScope.NONE && !List.of(Action.BUFF, Action.DAMAGE, Action.HEAL, Action.WELL_FED, Action.DRAW, Action.GIVE, Action.GIVE_RANDOM, Action.GIVE_RANDOM_CLASS, Action.SUMMON, Action.GAIN_XP).contains(action))
                        throw new IllegalArgumentException("For each requires a numeric action");
                    if (!observed(trigger) && (e.has("eventScope") && effect.eventScope() != defaultScope(trigger) || effect.eventType() != null))
                        throw new IllegalArgumentException("Event filters require a hand-added, action-played, mined or healed trigger");
                    if (target == Aim.EVENT && trigger != Trigger.AFTER_ATTACK && trigger != Trigger.HEALED && trigger != Trigger.DAMAGE_DEALT && trigger != Trigger.ATTACKED)
                        throw new IllegalArgumentException("Event target requires healed, damage dealt or attacked");
                    boolean heroTarget = target == Aim.CHOSEN_CHARACTER || target == Aim.FRIENDLY_HERO || target == Aim.ENEMY_HERO || target == Aim.RANDOM_HERO || eachHero(target)
                            || (trigger == Trigger.ATTACKED && aimed);
                    if (heroTarget && action != Action.DAMAGE && action != Action.HEAL && action != Action.WELL_FED && action != Action.CLEANSE && action != Action.FREEZE && action != Action.DESTROY_TOOL && action != Action.BAD_OMEN
                            && !handAction(action) && !(eachHero(target) && action == Action.TOOL_DAMAGE))
                        throw new IllegalArgumentException("This action requires a mob target");
                    if (action == Action.BAD_OMEN && ((target != Aim.ENEMY_HERO && target != Aim.EACH_ENEMY_HERO) || effect.amount() != 1 || effect.excludeSelf()))
                        throw new IllegalArgumentException("Bad Omen marks the enemy hero and summons one mob");
                    if (action == Action.DESTROY_TOOL && target != Aim.ENEMY_HERO && !eachHero(target))
                        throw new IllegalArgumentException("Destroy tool requires the enemy hero, each enemy hero or each hero");
                    if (effect.type() != null && action != Action.CHOOSE_FROM_DECK && ((heroTarget && target != Aim.EVENT) || target == Aim.SELF))
                        throw new IllegalArgumentException("Type filters require a mob target group");
                    if (!generator && action != Action.CHOOSE_FROM_DECK && target == Aim.SELF && kind != CardKind.MOB)
                        throw new IllegalArgumentException("This Self effect needs a mob");
                    effects.add(effect);
                    if (action == Action.CLEANSE && !keywords.contains(Keyword.CLEANSE)) keywords.add(Keyword.CLEANSE);
                    if (action == Action.WITHER && !keywords.contains(Keyword.WITHER)) keywords.add(Keyword.WITHER);
                    if (action == Action.BAD_OMEN) {
                        if (!keywords.contains(Keyword.BAD_OMEN)) keywords.add(Keyword.BAD_OMEN);
                        related.add(new CardDefinition.Related(Keyword.BAD_OMEN, effect.card()));
                    }
                    if ((trigger == Trigger.PLAY || trigger == Trigger.DROP) && kind == CardKind.MOB) {
                        Keyword k = trigger == Trigger.PLAY ? Keyword.SPAWN : Keyword.DROP;
                        if (!keywords.contains(k)) keywords.add(k);
                        if (effect.card() != null) related.add(new CardDefinition.Related(k, effect.card()));
                    }
                }
                if (kind == CardKind.ENCHANTMENT && !keywords.contains(Keyword.LOYALTY) && !keywords.contains(Keyword.CHANNELING)
                        && effects.stream().noneMatch(e -> e.action() == Action.ENCHANT_TOOL || e.trigger() != Trigger.PLAY))
                    throw new IllegalArgumentException("An enchantment needs a tool stat bonus or an attached triggered ability");
                if (uses > 0 && effects.stream().noneMatch(CardCatalog::spendsUse))
                    throw new IllegalArgumentException("Limited uses need a triggered ability (not Spawn or Drop) to count");
                CardDefinition definition = new CardDefinition(kind, cardClass, rarity, cost, attack, health, type,
                        List.copyOf(keywords), List.copyOf(related), bool(c, "token"), art.isEmpty() ? null : art);
                entries.put(id, new Entry(definition, name, text, bool(c, "autoText"), passive, passiveAmount, List.copyOf(effects), optionalEnum(MobType.class, c, "auraType", null), c.has("deleted") && bool(c, "deleted"), auraHealth, optionalEnum(CastFx.class,c,"castFx",CastFx.AUTO), optionalEnum(DeathFx.class,c,"deathFx",DeathFx.AUTO), optionalEnum(ArrivalFx.class,c,"arrivalFx",ArrivalFx.AUTO), auraKind, negateKeyword, cardElement, ampElement, ampHow, potion, uses));
            } catch (RuntimeException e) { throw new IllegalArgumentException(label + ": " + e.getMessage(), e); }
        }
        for (var pair : entries.entrySet()) for (Effect effect : pair.getValue().effects()) {
            if (effect.card() != null) {
                Entry ref = entries.get(effect.card());
                if (ref != null && ref.deleted() && !pair.getValue().deleted()) throw new IllegalArgumentException(pair.getKey() + ": referenced card is deleted " + effect.card());
                if (ref == null) throw new IllegalArgumentException(pair.getKey() + ": Unknown referenced card " + effect.card());
                if (effect.action() == Action.BAD_OMEN && (!ref.definition().isMob() || ref.definition().keywords().contains(Keyword.BLOCK)))
                    throw new IllegalArgumentException(pair.getKey() + ": Bad Omen requires a mob");
                if ((effect.action() == Action.SUMMON || effect.action() == Action.TRANSFORM) && !ref.definition().isMob())
                    throw new IllegalArgumentException(pair.getKey() + ": Summon requires a mob");
            }
            if (effect.boost() != null && effect.boost().card() != null && !entries.containsKey(effect.boost().card()))
                throw new IllegalArgumentException(pair.getKey() + ": Unknown boost card " + effect.boost().card());
            for (Identifier choice : effect.pool()) if (!entries.containsKey(choice) || !pair.getValue().deleted() && entries.get(choice).deleted())
                throw new IllegalArgumentException(pair.getKey() + ": Unknown pool card " + choice);
        }
        for (String required : List.of("wolf_pup","cobblestone_wall","wheat","caught_cod","caught_salmon","caught_pufferfish","iron_ore_token","experience_bottle","crafted_sword")) {
            Entry e = entries.get(Identifier.parse("craftbound:" + required));
            if (e != null && e.deleted()) throw new IllegalArgumentException(required + ": required by a hero power or match setup");
        }
        String compact = root.toString();
        if (compact.length() > MAX_JSON) throw new IllegalArgumentException("Catalogue too large");
        return new State(Collections.unmodifiableMap(entries), compact);
    }

    /** Preserve all existing saved card identities and binder numbers when editing a live catalogue. */
    public static void validateReplacement(State previous, State next) {
        var oldRoot = JsonParser.parseString(previous.json()).getAsJsonObject();
        var newRoot = JsonParser.parseString(next.json()).getAsJsonObject();
        if (oldRoot.has("workshopEpoch") && !oldRoot.get("workshopEpoch").equals(newRoot.get("workshopEpoch")))
            throw new IllegalArgumentException("This catalogue predates the Foundation reset. Use the current Workshop catalogue.");
        List<Identifier> oldOrder = previous.entries().entrySet().stream().filter(e -> !e.getValue().definition().token()).map(Map.Entry::getKey).toList();
        List<Identifier> newOrder = next.entries().entrySet().stream().filter(e -> !e.getValue().definition().token()).map(Map.Entry::getKey).toList();
        if (newOrder.size() < oldOrder.size() || !newOrder.subList(0, oldOrder.size()).equals(oldOrder))
            throw new IllegalArgumentException("Keep existing collectible IDs in order; append new cards");
        previous.entries().forEach((id, old) -> {
            Entry e = next.entries().get(id);
            if (e == null || e.definition().kind() != old.definition().kind() || e.definition().token() != old.definition().token())
                throw new IllegalArgumentException(id + ": existing cards must retain their ID, kind and token flag");
        });
    }

    private static <E extends Enum<E>> E optionalEnum(Class<E> type, JsonObject o, String key, E fallback) {
        if (!o.has(key)) return fallback;
        String value = string(o, key, 40);
        return value.isEmpty() ? fallback : Enum.valueOf(type, value);
    }
    private static List<Identifier> pool(JsonObject e) {
        if (!e.has("pool")) return List.of();
        JsonArray values = e.getAsJsonArray("pool");
        if (values.size() > 32) throw new IllegalArgumentException("At most 32 pool entries");
        List<Identifier> result = new ArrayList<>();
        for (JsonElement value : values) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Pool entries must be card IDs");
            result.add(Identifier.parse(value.getAsString()));
        }
        return List.copyOf(result);
    }

    private static String string(JsonObject o, String key, int max) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(key + " must be text");
        String s = e.getAsString();
        if (s.length() > max) throw new IllegalArgumentException(key + " is too long");
        return s;
    }
    private static boolean bool(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException(key + " must be true/false");
        return e.getAsBoolean();
    }
    private static int integer(JsonObject o, String key, int min, int max) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(key + " must be an integer");
        int n = e.getAsBigDecimal().intValueExact();
        if (n < min || n > max) throw new IllegalArgumentException(key + " must be " + min + "–" + max);
        return n;
    }
    private static <E extends Enum<E>> E enumeration(Class<E> type, JsonObject o, String key) { return Enum.valueOf(type, string(o, key, 40)); }

    public static String description(Entry e) {
        if (!e.autoText()) return e.text();
        List<String> lines = new ArrayList<>();
        // An action's element is printed on its type label instead of in the rules text.
        if (e.element() != null && e.definition().kind() != CardKind.ITEM) lines.add(title(e.element().name()) + ".");
        e.definition().keywords().stream().filter(k -> k != Keyword.SPAWN && k != Keyword.DROP && k != Keyword.CLEANSE && k != Keyword.BAD_OMEN && k != Keyword.WITHER && k != Keyword.BLOCK).forEach(k -> lines.add(title(k.name()) + "."));
        if (e.passive() == Passive.ARMOR) lines.add("Reduce incoming damage by " + e.passiveAmount() + ".");
        if (e.hasAura()) {
            String stats = e.passiveAmount() == 0 ? "+" + e.auraHealthBonus() + " Health"
                    : e.auraHealthBonus() == 0 ? "+" + e.passiveAmount() + " Attack"
                    : "+" + e.passiveAmount() + " Attack and +" + e.auraHealthBonus() + " Health";
            lines.add("Your other " + (e.auraType() == null ? "" : title(e.auraType().name()) + " ")  + (e.auraKind() == BoardKind.BLOCK ? "blocks" : e.auraKind() == BoardKind.BOTH ? "mobs and blocks" : "mobs") + " have " + stats + ".");
        }
        if (e.passive() == Passive.AMPLIFY_ELEMENT && e.ampElement() != null) lines.add("Your " + title(e.ampElement().name()) + " effects deal "
                + (e.ampHow() == BoostHow.TRIPLE ? "triple" : e.ampHow() == BoostHow.PLUS ? "+" + e.passiveAmount() : "double") + " damage.");
        if (e.passive() == Passive.NEGATE_KEYWORD && e.negateKeyword() != null) lines.add("Enemy " + (e.auraType() == null ? "" : title(e.auraType().name()) + " ")
                + (e.auraKind() == BoardKind.BLOCK ? "blocks" : e.auraKind() == BoardKind.MOB ? "mobs" : "mobs and blocks") + " lose " + title(e.negateKeyword().name()) + " while this is in play.");
        if (e.passive() == Passive.GRAZER) lines.add("At the end of your turn, if you control a Crop, restore " + e.passiveAmount() + " Health to this mob.");
        if (e.passive() == Passive.BATTLE_HARDENED) lines.add("After this survives damage, gain +" + e.passiveAmount() + " Attack. Once per turn.");
        if (e.passive() == Passive.SCHOOLING) lines.add("After you play another Aquatic mob, this gains +" + e.passiveAmount() + " Attack until the end of the turn.");
        String previousTrigger = null;
        for (Effect f : e.effects()) {
            // A statless attachment is implicit in "Your tool gains"; do not repeat it on the card.
            if (f.action() == Action.ENCHANT_TOOL && f.attack() == 0 && f.health() == 0
                    && e.effects().stream().anyMatch(other -> other.trigger() != Trigger.PLAY)) continue;
            String target = targetText(f);
            String ref = f.card() == null ? "" : current.entries().containsKey(f.card()) ? current.entries().get(f.card()).name() : f.card().getPath();
            String action = switch (f.action()) {
                case BUFF -> "Give " + target + " " + (f.attack() == 0 && f.health() == 0 && f.keyword() != null ? ""
                        : f.attack() == 0 ? "+" + f.health() + " Health"
                        : f.health() == 0 ? signed(f.attack()) + " Attack" : signed(f.attack()) + "/+" + f.health())
                        + (f.keyword() == null ? "" : (f.attack() != 0 || f.health() != 0 ? " and " : "") + title(f.keyword().name()));
                case DAMAGE -> "Deal " + f.amount() + " damage to " + target;
                case HEAL -> "Restore " + f.amount() + " Health to " + target;
                case WELL_FED -> "Well Fed: restore " + f.amount() + " Health to " + target + "; excess healing increases maximum Health";
                case GRANT_KEYWORD -> "Give " + target + " " + (f.randomKeyword() ? "a random keyword it doesn't have" : title(f.keyword().name()));
                case TRANSFORM -> "Transform " + target + " into " + ref;
                case GIVE_RANDOM -> "Add " + f.amount() + (f.amount() == 1 ? " random card from [" : " random cards from [") + f.pool().stream().map(id -> current.entries().containsKey(id) ? current.entries().get(id).name() : id.getPath()).collect(java.util.stream.Collectors.joining(", ")) + "] to your hand";
                case BAD_OMEN -> "Give " + target + " Bad Omen";
                case BREW_POTION -> "Brew a potion in your hand: add Potency, Duration or Lingering";
                case PICK_ONE -> "Pick one of [" + f.pool().stream().map(id -> current.entries().containsKey(id) ? current.entries().get(id).name() : id.getPath()).collect(java.util.stream.Collectors.joining(", ")) + "] to add to your hand";
                case CLEANSE -> "Cleanse " + target;
                case MINE -> "Mine " + target + ": remove it and trigger its Drop";
                case CHOOSE_FROM_DECK -> "Choose one of up to " + f.amount() + " different "
                        + (f.type() == null ? "" : title(f.type().name()) + " ")
                        + (f.boardKind() == BoardKind.BLOCK ? "blocks" : f.boardKind() == BoardKind.MOB ? "mobs" : "mobs or blocks")
                        + " from your deck and draw it";
                case DRAW -> "Draw " + f.amount() + (f.amount() == 1 ? " card" : " cards");
                case GAIN_XP -> "Gain " + f.amount() + " XP this turn";
                case GAIN_MAX_XP -> "Gain +" + f.amount() + " maximum XP";
                case DESTROY -> "Destroy " + target;
                case WEAKEN_UNTIL_NEXT_TURN -> "Give " + target + " -" + f.amount() + " Attack until the end of its controller's next turn";
                case SUMMON -> "Summon " + (f.amount() == 1 ? ref : f.amount() + " copies of " + ref);
                case GIVE -> "Add " + (f.amount() == 1 ? ref : f.amount() + " copies of " + ref) + " to your hand";
                case GIVE_RANDOM_CLASS -> "Add " + (f.amount() == 1 ? "a random card" : f.amount() + " random cards") + " from your class to your hand";
                case BOUNCE -> "Return " + target + " to its owner's hand";
                case RETURN_AND_COPY -> "Return " + target + " to its owner's hand, then add "
                        + (f.amount() == 1 ? "a copy" : f.amount() + " copies") + " of it to your hand";
                case TOTEM -> "Give " + target + " a Totem of Undying";
                case INVULNERABLE -> "Make " + target + " invulnerable until its owner's next turn";
                case FREEZE -> "Freeze " + target;
                case WITHER -> "Wither " + target;
                case TOOL_DAMAGE -> "Deal your tool's attack as damage to " + target;
                case ENCHANT_TOOL, UPGRADE_TOOL -> f.attack() == 0 && f.health() == 0 ? "Enchant your equipped tool" : "Give your equipped tool " + (f.attack() == 0 ? "" : "+" + f.attack() + " Attack") + (f.attack() != 0 && f.health() != 0 ? " and " : "") + (f.health() == 0 ? "" : "+" + f.health() + " Durability");
                case DESTROY_TOOL -> "Destroy " + target + "'s tool";
                case REVEAL_HAND, DISCARD_FROM_HAND, STEAL_FROM_HAND, SHUFFLE_FROM_HAND -> handText(f);
            };
            String trigger = triggerText(e, f) + switch(f.gate()){
                case ALWAYS -> ""; case TOOL_EQUIPPED -> ", if you have a tool"; case HERO_HURT -> ", if your hero is hurt"; case EMPTY_HAND -> ", if your hand is empty";};
            if (f.countScope() != CountScope.NONE) action += " for each " + (f.countScope() == CountScope.ALL ? "" : f.countScope().name().toLowerCase(Locale.ROOT) + " ")
                    + (f.countType() == null ? "" : title(f.countType().name()) + " ")
                    + (f.countKind() == BoardKind.BLOCK ? "block" : f.countKind() == BoardKind.MOB ? "mob" : "mob or block")
                    + (f.countExcludeSelf() ? " other than this" : "");
            action += boostText(f.boost());
            if (e.definition().kind() == CardKind.ENCHANTMENT && f.trigger() != Trigger.PLAY) trigger = "Your tool gains: " + trigger;
            if (!trigger.isEmpty() && trigger.equals(previousTrigger)) {
                String previous = lines.removeLast();
                String next = action.startsWith("Well Fed") ? action : Character.toLowerCase(action.charAt(0)) + action.substring(1);
                lines.add(previous.substring(0, previous.length() - 1) + ", then " + next + ".");
                continue;
            }
            previousTrigger = trigger;
            if (e.definition().kind() == CardKind.ENCHANTMENT && f.trigger() == Trigger.ON_KILL && f.gate()==Gate.ALWAYS) {
                lines.add("Your tool gains: After this destroys a mob or block, " + Character.toLowerCase(action.charAt(0)) + action.substring(1) + ".");
                continue;
            }
            lines.add((trigger.isEmpty() ? "" : trigger + ": ") + action + ".");
        }
        if (e.uses() > 0) lines.add(e.uses() == 1 ? "After 1 use, this is destroyed." : "After " + e.uses() + " uses, this is destroyed.");
        return String.join(" ", lines);
    }
    /** Which triggers count as a use of a card with limited uses: everything but Spawn (play) and Drop (death). */
    public static boolean spendsUse(Effect e) { return e.trigger() != Trigger.PLAY && e.trigger() != Trigger.DROP; }
    private static Boost boost(JsonObject e) {
        if (!e.has("boost") || e.get("boost").isJsonNull()) return null;
        if (!e.get("boost").isJsonObject()) throw new IllegalArgumentException("boost must be an object");
        JsonObject b = e.getAsJsonObject("boost");
        String card = b.has("card") ? string(b, "card", 120) : "";
        BoostHow how = optionalEnum(BoostHow.class, b, "how", BoostHow.DOUBLE);
        return new Boost(enumeration(BoostWhen.class, b, "when"), card.isEmpty() ? null : Identifier.parse(card),
                optionalEnum(MobType.class, b, "type", null), optionalEnum(BoardKind.class, b, "kind", BoardKind.BOTH),
                how, how == BoostHow.PLUS ? integer(b, "amount", 0, 100) : 0);
    }
    // " (doubled if you control Netherrack)" - kept word for word in step with the Workshop's model.js.
    private static String boostText(Boost b) {
        if (b == null) return "";
        String change = switch (b.how()) { case DOUBLE -> "doubled"; case TRIPLE -> "tripled"; case PLUS -> "+" + b.amount(); };
        String noun = b.kind() == BoardKind.BLOCK ? "block" : b.kind() == BoardKind.MOB ? "mob" : "mob or block";
        String plural = b.kind() == BoardKind.BLOCK ? "blocks" : b.kind() == BoardKind.MOB ? "mobs" : "mobs or blocks";
        String typed = b.type() == null ? "" : title(b.type().name()) + " ";
        String named = b.card() == null ? null : current.entries().containsKey(b.card()) ? current.entries().get(b.card()).name() : b.card().getPath();
        String one = named != null ? named : ("AEIOU".indexOf((typed + noun).charAt(0)) >= 0 ? "an " : "a ") + typed + noun;
        String condition = switch (b.when()) {
            case YOU_CONTROL -> "if you control " + one;
            case ENEMY_CONTROLS -> "if an enemy controls " + one;
            case ANYONE_CONTROLS -> "if " + one + " is in play";
            case TARGET_IS -> "against " + (named != null ? named : typed + plural);
            case TARGET_FROZEN -> "against Frozen targets";
            case TARGET_WITHERED -> "against Withered targets";
            case TARGET_DAMAGED -> "against damaged targets";
        };
        return " (" + change + " " + condition + ")";
    }
    private static String triggerText(Entry e, Effect f) {
        boolean tool = e.definition().kind() == CardKind.TOOL || e.definition().kind() == CardKind.ENCHANTMENT;
        String owner = switch (f.eventScope()) { case FRIENDLY -> "you"; case ENEMY -> "your opponent"; case ANY -> "either player"; case SELF -> "you"; };
        String typed = f.eventType() == null ? "" : title(f.eventType().name()) + " ";
        return switch (f.trigger()) {
            case PLAY -> e.definition().isMob() ? "Spawn" : "";
            case END_TURN -> "At the end of your turn";
            case START_TURN -> "At the start of your turn";
            case ACTIVATE -> "Activate";
            case AFTER_ATTACK -> tool ? "After your hero attacks" : "After attacking";
            case DAMAGE_DEALT -> tool ? "After your hero deals attack damage" : "After this deals damage";
            case ATTACKED -> "When attacked";
            case ON_KILL -> tool ? "After your tool kills a mob or block" : "After this kills a mob or block";
            case AFTER_SURVIVING_KILL -> "After this attacks and destroys a mob or block, if it survives";
            case CARD_ADDED -> "After a " + typed + "card enters " + (f.eventScope() == Scope.ENEMY ? "your opponent's" : f.eventScope() == Scope.ANY ? "either player's" : "your") + " hand";
            case ITEM_PLAYED -> "After " + owner + (f.eventScope() == Scope.FRIENDLY || f.eventScope() == Scope.SELF ? " play" : " plays") + " an Action";
            case MINED -> "After " + owner + (f.eventScope() == Scope.FRIENDLY || f.eventScope() == Scope.SELF ? " mine" : " mines") + " " + (typed.isEmpty() ? "an Ore" : "a " + typed + "block");
            case HEALED -> f.eventScope() == Scope.SELF ? (tool ? "After your hero is healed" : "After this is healed") : "After " + (f.eventScope() == Scope.FRIENDLY ? "a friendly " : f.eventScope() == Scope.ENEMY ? "an enemy " : "a ") + (typed.isEmpty() ? "mob, block or hero" : typed + "mob") + " is healed";
            default -> title(f.trigger().name());
        };
    }
    // Kept word-for-word with the Workshop's handText in tools/card-editor/model.js.
    static String handText(Effect f) {
        boolean each = f.target() == Aim.EACH_ENEMY_HERO;
        String whose = each ? "each opponent's" : "your opponent's";
        String they = each ? "Each opponent" : "Your opponent";
        String cards = f.amount() == 1 ? "a card" : f.amount() + " cards";
        String randomCards = f.amount() == 1 ? "a random card" : f.amount() + " random cards";
        return switch (f.action()) {
            case REVEAL_HAND -> "Look at " + whose + " hand";
            case DISCARD_FROM_HAND -> switch (f.chooser()) {
                case YOU -> "Look at " + whose + " hand and discard " + cards + " from it";
                case OPPONENT -> they + " discards " + cards + " of their choice";
                case RANDOM -> they + " discards " + randomCards;
            };
            case STEAL_FROM_HAND -> switch (f.chooser()) {
                case YOU -> "Look at " + whose + " hand and steal " + cards + " from it";
                case OPPONENT -> they + " gives you " + cards + " of their choice from their hand";
                case RANDOM -> "Steal " + randomCards + " from " + whose + " hand";
            };
            case SHUFFLE_FROM_HAND -> switch (f.chooser()) {
                case YOU -> "Look at " + whose + " hand and shuffle " + cards + " from it into their deck";
                case OPPONENT -> they + " shuffles " + cards + " of their choice from their hand into their deck";
                case RANDOM -> "Shuffle " + randomCards + " from " + whose + " hand into their deck";
            };
            default -> "";
        };
    }
    private static String signed(int value) { return value >= 0 ? "+" + value : Integer.toString(value); }
    private static String targetText(Effect f) {
        String unit = switch (f.boardKind()) { case MOB -> "mob"; case BLOCK -> "block"; case BOTH -> "mob or block"; };
        String plural = switch (f.boardKind()) { case MOB -> "mobs"; case BLOCK -> "blocks"; case BOTH -> "mobs and blocks"; };
        String type = f.type() == null ? "" : title(f.type().name()) + " ";
        String other = f.excludeSelf() ? "other " : "";
        return switch (f.target()) {
            case SELF -> "this card";
            case EVENT -> f.trigger() == Trigger.AFTER_ATTACK ? "its target" : "the triggering character";
            case KILLER -> "the enemy mob that killed this";
            case FRIENDLY_MOBS -> other + "friendly " + type + plural;
            case ENEMY_MOBS -> (f.action() == Action.DESTROY ? "all enemy " : "enemy ") + type + plural;
            case ALL_MOBS -> "all " + other + type + plural;
            case RANDOM_FRIENDLY_MOB -> "a random " + other + "friendly " + type + unit;
            case RANDOM_ENEMY -> "a random enemy" + (f.type() == null && canAffectHero(f.action()) ? "" : " " + type + unit);
            case RANDOM_FRIENDLY -> "a random " + other + "friendly " + (f.type() == null && canAffectHero(f.action()) ? "character" : type + unit);
            case RANDOM_MOB -> "a random " + other + type + "mob on either side";
            case RANDOM_BLOCK -> "a random " + other + type + "block on either side";
            case RANDOM_HERO -> "a random hero";
            case FRIENDLY_HERO -> "your hero";
            case ENEMY_HERO -> "the enemy hero";
            case EACH_ENEMY_HERO -> "each enemy hero";
            case EACH_HERO -> "each hero";
            case CHOSEN_FRIENDLY -> "a friendly " + type + unit;
            case CHOSEN_ENEMY -> "an enemy " + type + unit;
            case CHOSEN_CHARACTER -> (f.trigger() == Trigger.DAMAGE_DEALT ? "the damaged " : "a chosen ") + unit + " or hero";
            case CHOSEN_ANY -> (f.trigger() == Trigger.ATTACKED ? "the attacker" : f.trigger() == Trigger.DAMAGE_DEALT ? "the damaged " : "a chosen ") + (f.trigger() == Trigger.ATTACKED ? "" : type + unit);
        };
    }
    private static String title(String s) { if (s.equals("IGNORE_SHIELD")) return "Ignore Shield"; String t = s.toLowerCase(Locale.ROOT).replace('_', ' '); return Character.toUpperCase(t.charAt(0)) + t.substring(1); }
    private CardCatalog() {}
}
