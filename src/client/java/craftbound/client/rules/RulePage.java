package craftbound.client.rules;

import craftbound.card.BoosterPacks;
import craftbound.card.CardClass;
import craftbound.card.DeckRules;
import craftbound.card.MobType;
import craftbound.game.HeroPowers;
import craftbound.game.Match;
import craftbound.mastery.MasteryProgress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import static craftbound.client.rules.RuleChapter.*;

/** Short articles. [PAGE|words] links to a topic, **words** gives emphasis without a link. */
enum RulePage {
    START(BASICS, "Your first match",
            "Each hero starts with " + Match.START_HEALTH + " [HEALTH|Health]. Bring the opposing hero to zero to win.",
            "**Before sitting down**   Build a [DECKS|deck] in your binder. Bring it to a card table, choose your deck and opponent, then press Ready.",
            "**On your turn**   Spend [XP|XP] to play cards. Drag a [ROWS|mob or block] onto the board. Attack with ready mobs, then press End Turn."),
    TABLE(BASICS, "At the table",
            "A standard card table has **Player Vs Player**, **Card Master** and [TRIALS|The Trials] tabs. [RELAY|Team Duos] and [COMMANDER|Commander] have their own tables. Every table lets you open your Collection, make a New Deck or edit a saved deck before playing.",
            "**Pause**   Open the match menu and request a pause. The other player must agree. The Card Master agrees automatically. Return to that table and confirm to resume.",
            "**Watch**   Right-click a table where others are playing. Pick a player's view or follow the turn. Hands stay hidden unless their owner accepts your request.",
            "**Concede**   Ends the match as a loss. See [MASTERY|Mastery] for reward eligibility."),
    RELAY(BASICS, "Team Duos (2 vs 2)",
            "Sit at a **Team Duos table**. The player beside you is your teammate; the two players opposite are your opponents. Each player brings their own deck. Each team starts with **30 shared Health**.",
            "**Play together**   Both partners can act throughout their team's turn. Each has their own XP, hand, deck, tool, hero power and board. The starting team skips its first draw. On later turns, both active players draw.",
            "**Your side**   You control your own 7 mob slots and 3 block slots. Effects that say **your** refer to your cards. Click an enemy’s name beside their hero to focus effects that name one enemy. Effects that hit **each enemy hero** hit both and lower their shared Health twice.",
            "**End the turn**   Mark yourself ready when finished. Your team passes once both partners are ready or the 75-second timer runs out.",
            "**Win together**   A team loses at zero Health or when either partner must draw from an empty deck. Conceding ends the game for both teammates. A disconnected player has 90 seconds to rejoin."),
    TRIALS(BASICS, "The Trials (solo expedition)",
            "**Start**   At a standard card table choose **The Trials**. Pick a class and draft a small borrowed deck. The Copper Depths starts you with 10 cards and two lives.",
            "**Map**   Scroll with the wheel or drag empty parchment to pan. Use Fit to see every route. Choose a connected room, then travel. A branch commits you for a few rooms before the next crossroads. Mystery events reveal themselves when entered. Your Health, lives, Emeralds and boons stay at the top; hover Deck for a list or click it to inspect cards.",
            "**Build your deck**   Fights earn Emeralds and an optional card; elites also offer a charm. Visit a villager to buy, remove or train cards. Events may duplicate or exchange a card. Training changes only the selected copy for this run. Open Deck for a scrollable five-column collection with art, copy counts, highlighted upgrades and an XP curve. Card choices preview how that curve would change. Your Health carries between wins; camps can heal you.",
            "**Finish**   Your progress saves automatically. Defeat the final guardian for boosters. A first clear with a class also records a stamp in binder Mastery and awards that class's configured [LOOKS|cosmetics]."),
    COMMANDER(BASICS, "Four-player Commander",
            "**Build**   At the Commander table, pick a collectible legendary mob you own. A class commander uses its own class. A Neutral commander lets you choose any class. Save 40 different cards including your commander. Normal and Enchanted copies share the same limit. Four players must ready at the same table.",
            "**Play**   Everyone starts with 40 Health, five cards and a commander outside the deck. First seat is random; turns rotate in order. Your field stays below the center line. Click one of the three rival portraits at the top to view their field. You can switch rivals while aiming, then click a highlighted target to commit. Shield protects only its owner. Random and area effects can reach all living opponents; effects naming one enemy hero use your selected rival, or the next living rival.",
            "**Command**   Cast your commander for its normal XP. After it leaves play you can cast it again for 2 more XP each time, capped at 10. A Totem revival keeps it on the board. Generated copies are ordinary mobs.",
            "**Battle log**   A round gives each surviving player one turn in seat order. Open the Battle log to browse rounds. Each turn shows plays, targets and outcomes. Turn on Details for draws, statuses and stat changes.",
            "**Time**   Each turn lasts 120 seconds. A TNT fuse warns you during the final 20. The fuse burns along the center line. Your hand, XP, Battle log and End Turn control stay at the bottom.",
            "**Win**   Reach zero Health or take 21 combat damage from the same commander and you are out. Healing does not erase that tally. The last hero wins. Close and right-click the table to return; disconnecting for 90 seconds forfeits your seat. This is a live format without a pause vote."),
    TURNS(TURN, "How a turn works",
            "**Start**   Your start-of-turn effects resolve. Your maximum [XP] grows and refills, your attackers ready up, and you [HAND|draw a card]. The first player skips the first draw.",
            "**Play**   Choose the order: play cards, [ATTACK|attack], or use your [POWERS|hero power]. You can leave XP unspent.",
            "**End**   Press End Turn. Your end-of-turn effects resolve before your opponent starts. In PvP you have " + Match.TURN_TICKS / 20 + " seconds. A fuse warns you in the final 15. Card Master turns are untimed."),
    OPENING(TURN, "Opening hand",
            "A coin toss chooses who goes first. The first player gets **4 cards** and the second gets **5**.",
            "**Mulligan**   Select any opening cards you want to swap, then confirm once. Replacements are drawn before the cards you returned are shuffled back in.",
            "The second player also gets a Bottle o' Enchanting after the swap. Play it for 1 extra [XP] that turn."),
    XP(TURN, "XP",
            "The green number on a card is its price. Spend match XP to play it. Every [POWERS|hero power] also costs " + Match.POWER_COST + " XP.",
            "Your first turn normally starts with 1 XP. Each turn your maximum grows by 1, up to " + Match.MAX_XP + ", then your pool refills. Unspent XP does not carry over.",
            "**Example**   At 3/3 XP, playing a 2-XP card leaves 1/3. On your next turn you normally refill to 4/4. Gaining maximum XP grows future refills. Gaining XP just for this turn does not."),
    HAND(TURN, "Draw, hand & Hunger",
            "Drawing moves the top card of your deck into your hand. Adding a card by an effect can create one without using your deck. Both count as a card entering your hand.",
            "Your hand holds **" + Match.MAX_HAND + " cards**. If another card is drawn or given to you while it is full, that card is lost for this match.",
            "**Hunger**   Each attempted draw from an empty deck damages your hero. The first deals 1, the next 2, then 3, and so on."),
    HEALTH(TURN, "Health & healing",
            "Your portrait shows your hero's current Health. At zero your hero loses. If both heroes fall in the same resolution, the match is a draw.",
            "Damage stays until healed. Ordinary healing restores missing Health, up to the target's maximum. A mob or block at zero Health dies.",
            "**Well Fed**   Excess healing also increases maximum Health. For example, a 2/3 Health target receiving 2 Well Fed healing finishes at 4/4 Health."),
    POWERS(TURN, "Hero powers",
            "The button beside your hero is your class power. Use it **once on your turn** for " + Match.POWER_COST + " [XP]. It refreshes on your next turn.",
            "For an aimed power, drag from the button and release on a legal target. A summoning power needs room in its [ROWS|row].",
            "Each class has its own power. Open [HEROES|Classes] to see what yours does and how to build around it."),
    ROWS(BOARD, "The two rows",
            "Each player has a front row for **" + Match.MAX_MOBS + " mobs** and a back row for **" + Match.MAX_BLOCKS + " blocks**. The counter beside each row shows how many spaces are used.",
            "Drag a mob or block out of your hand and release over the play area. It goes to its own row. A full row stops you from playing or summoning another card into it.",
            "Front-row mobs protect blocks from melee attacks. The enemy hero is still reachable unless a mob or block has [SHIELD|Shield]."),
    ATTACK(BOARD, "Attacking",
            "Drag from a ready mob or armed hero to an enemy. Each normally attacks once on your turn. New mobs wait until your next turn unless they have [SPRINT|Sprint].",
            "When two mobs fight, each deals its Attack to the other. For example, a 3/2 attacking a 2/3 deals 3 and takes 2. Both die.",
            "A defending hero deals no return damage. A hero attacking a mob takes that mob's Attack. [TOOLS|Tools] let heroes attack."),
    SHIELD(BOARD, "Shield & Ignore Shield",
            "**Shield on a mob** forces every attack onto enemy Shield mobs first, including attacks on blocks and the hero. If two mobs have Shield, choose either.",
            "**Shield on a block** only guards its hero and the other blocks. Front-row mobs can still be attacked. The Shield block stays in the back row, so clear the front row or use [SPRINT|Ranged] to break it, then the hero is open.",
            "**Ignore Shield** lets that attacker pass any Shield, including to hit the hero. A melee attacker still cannot reach a back-row block while the front row stands.",
            "Shield changes attack targets. An [ACTIONS|Action] or hero power follows its own targeting text."),
    SPRINT(BOARD, "Sprint & Ranged",
            "**Sprint** lets a new mob attack an enemy mob or reachable block on the turn you play it. It must wait until your next turn to attack a hero.",
            "**Ranged** reaches back-row blocks even with enemy mobs in front. [SHIELD|Shield] still takes priority. Ranged mobs deal and receive normal combat damage.",
            "For example, with a Zombie in front of an enemy block, a Ranged mob can hit either. Give the Zombie Shield and it must be attacked first."),
    KINDS(CARDS, "Card types",
            "[MOBS|Mobs] fight in front. [BLOCKS|Blocks] stay behind them and produce effects.",
            "[ACTIONS|Actions] resolve when played. [TOOLS|Tools] equip your hero. [ENCHANTMENTS|Enchantments] improve the equipped tool.",
            "Read the bottom label. A mob may show a family such as Undead there. Family bonuses are explained under [FAMILIES|Mob types]."),
    MOBS(CARDS, "Mobs",
            "A mob stays in the front row. Read its two numbers as **Attack / Health**. A 3/2 deals 3 combat damage and has 2 Health.",
            "Play it from your hand for its [XP] cost. A [TIMING|Spawn] effect happens when you play it. Summoning the same mob with an effect does not repeat Spawn. Playing a mob also triggers any [BADOMEN|Bad Omen] on your hero.",
            "A mob with positive Attack can usually fight from your next turn. Read [ATTACK|Attacking] for trades and [FAMILIES|Mob types] for synergies."),
    BLOCKS(CARDS, "Blocks",
            "A block occupies one back-row space and has Health. It cannot attack or retaliate with Attack. Its own effect can still damage something that attacks it. A block with [SHIELD|Shield] guards your hero and other blocks.",
            "Read the timing. An end-of-turn block works on its owner's turns. An **Activate** ability fires when an effect triggers it, such as the [REDSTONER|Redstoner's power].",
            "To remove a protected block, use [SPRINT|Ranged], clear the front row, or play an effect that names blocks. Blocks can also be [WITHER|Withered] if the effect allows a block target."),
    ACTIONS(CARDS, "Actions & targets",
            "An Action does what its text says when you play it, then leaves your hand. It uses no board space.",
            "**Friendly** means your side. **Enemy** means the opponent's side. A mob-only effect cannot target a block or hero. If it says mob, block or hero, all three are eligible.",
            "Choose a highlighted target when asked. **Random** lets the game choose among eligible targets. **All** affects every eligible target."),
    TOOLS(CARDS, "Tools & Durability",
            "Equipping a tool puts it beside your portrait and replaces your previous tool. Its numbers are **Attack / Durability**.",
            "A 3/2 tool lets your hero hit for 3 and lasts for two attacks. Each hero attack spends 1 Durability. At zero, the tool breaks.",
            "You can attack the turn you equip a tool. Replacing it does not give a second hero attack. [ENCHANTMENTS|Enchantments] can add stats or abilities."),
    ENCHANTMENTS(CARDS, "Enchantments",
            "Play an Enchantment on your equipped [TOOLS|tool]. Its text may grant Attack, extra Durability, or an ability such as drawing a card after your tool gets a kill.",
            "The upgrade belongs to that tool. It goes away when the tool breaks, is destroyed, or is replaced.",
            "An **Enchanted copy** is a collectible finish. It plays the same as the normal copy and shares its [DECKS|deck copy limit]."),
    FAMILIES(CARDS, "Mob types & synergy",
            "A family is a tag that other cards can name. If an effect says “other friendly Illagers”, it helps your other Illager mobs. It skips the source and every other family.",
            "Block cards can also carry tags such as Crop or Ore. Check both the card type and the family the effect asks for.",
            "Use the binder's **Mob Types** filter to find cards to pair. For examples, read [PASSIVES|Passives] or [HEROES|Classes]."),
    EFFECTS(ABILITIES, "Reading an effect",
            "Read a card in three parts: **when** it happens, **what** changes, and **who** is affected.",
            "“At the end of your turn, give a random friendly mob +1 Health” fires as your turn ends. One of your mobs gets the bonus. A block or hero cannot receive it.",
            "“Then” gives the order. “For each” counts the matching cards in play. “Other” leaves out the source."),
    TIMING(ABILITIES, "When abilities fire",
            "**Spawn** fires when you play the card from hand. **Drop** (death) fires when the card dies. **Activate** needs another effect to trigger it.",
            "**Start / end of your turn** refers to the owner's turn. An enemy gets a turn to destroy a block before its next start-of-turn effect. [WITHER|Wither] also damages affected cards at the start of their owner's turn.",
            "**After attacking** follows the hit. **After dealing damage** needs damage to get through. A kill needs the target to die. “If it survives” also requires the attacker to live through the fight.",
            "Other cards listen for a card entering a hand, an Action being played, healing, mining, or harvesting. Check whose event the card names."),
    PASSIVES(ABILITIES, "Passives & auras",
            "**Aura**   The source lends stats to matching allies while it stays in play. When it leaves, they lose the bonus.",
            "**Armor** reduces each incoming hit by the amount printed. **Battle-Hardened** gains Attack after surviving damage, once per turn.",
            "**Grazer** heals itself at the end of your turn if you control a Crop. **Schooling** gains Attack for this turn after you play another Aquatic mob."),
    STATUS(ABILITIES, "Freeze, Wither & Cleanse",
            "**Frozen** stops a mob or hero from attacking and makes it skip its next attack turn. A frozen hero can still play cards and use its power.",
            "[WITHER|Withered] deals 1 damage to a mob or block at the start of each of its owner's turns. Heroes cannot be Withered. **Weakened** lowers Attack for the duration the card names.",
            "**Cleanse** removes applied benefits and harmful effects, including granted stats and keywords. Printed abilities and bonuses still supplied by an [PASSIVES|aura] remain. Cleanse does not heal damage.",
            "On a hero, Cleanse also removes [BADOMEN|Bad Omen], preventing its pending raid."),
    WITHER(ABILITIES, "Wither",
            "**Targets**   Wither makes a mob or block **Withered**. Heroes cannot be Withered. The card's targeting text still decides whether it can affect mobs, blocks or both.",
            "**Damage**   A Withered card takes 1 damage at the start of each of its owner's turns, after other start-of-turn abilities resolve. This can kill it. [PASSIVES|Armor] can reduce the damage.",
            "**No stacking**   Applying Wither again does not increase its damage. It does not wear off at the end of the turn.",
            "**Remove it**   [STATUS|Cleanse] removes Wither. [HEALTH|Healing] restores Health but does not remove the effect."),
    PROTECTION(ABILITIES, "Protection & revival",
            "**Invulnerable** prevents damage until the start of the owner's next turn. A direct destroy effect can still remove the card.",
            "**Totem of Undying** summons the card afresh once after it dies, if its row has space. Its [TIMING|Drop] resolves first.",
            "A hero can also carry a harmful [BADOMEN|Bad Omen] mark. Read its page for the raid trigger and how to remove it."),
    BADOMEN(ABILITIES, "Bad Omen",
            "**One mark per hero**   Bad Omen threatens your next mob play. The card that applied it names the raider. Further Bad Omen effects do not stack or replace the existing mark.",
            "**Trigger**   Playing a mob consumes the mark, including casting a [COMMANDER|commander]. Playing a block does not. Mobs summoned by effects or hero powers do not trigger it either.",
            "**Raid**   Your mob's play effects and resulting deaths resolve first. Then the player who applied Bad Omen summons the named raider into their own [ROWS|mob row]. The raider's [TIMING|Spawn] does not fire.",
            "**No room**   If that player's mob row is full, no raider appears and the mark is still spent. An eliminated player receives no raider.",
            "**Remove it**   Use [STATUS|Cleanse] on the marked hero before playing a mob. This removes the mark and prevents the raid."),
    CHANGES(ABILITIES, "Moving & changing cards",
            "**Return to hand** lifts a card off the board. A returned token disappears. A returned collectible needs a free [HAND|hand slot].",
            "**Transform** replaces a board card with a fresh form. A spent attack stays spent.",
            "**Mine** removes an Ore block and resolves its Drop. **Harvest** removes a Crop and resolves its Harvest effect. Check the card for the payoff."),
    HEROES(CLASSES, "Choose a class",
            "Your class gives you a [POWERS|hero power] and access to its class cards. Every class can also use Neutral cards.",
            "Pick a class below to see its power and a way to build around it."),
    FIGHTER(CardClass.FIGHTER, "Put pressure on with fast mobs and Illagers. Use your [TOOLS|tool] to remove a defender so your mobs can hit the hero."),
    BUILDER(CardClass.BUILDER, "Set up [BLOCKS|blocks] behind your front row. Give your Wall a friendly mob to strengthen before you end the turn."),
    FARMER(CardClass.FARMER, "Protect your Crop through the enemy turn. Extra maximum [XP] lets you play expensive cards earlier."),
    FISHERMAN(CardClass.FISHERMAN, "Build an Aquatic board. The fish from your power benefits from Aquatic [PASSIVES|auras]. Schooling specifically rewards playing an Aquatic mob from hand."),
    ALCHEMIST(CardClass.ALCHEMIST, "Control the board with potions and splash effects. One point of damage can finish a wounded target and save a friendly attacker."),
    MINER(CardClass.MINER, "Turn spare [XP] into Iron Ore, then spend the Ore on a mob worth growing. The power puts a card in your hand, so leave room."),
    CHEF(CardClass.CHEF, "Keep a sturdy mob alive across several trades with [HEALTH|healing]. Pair it with cards that reward actual Health restored."),
    TAMER(CardClass.TAMER, "Build a pack with Wolf Pups and Tameable mobs. Buff several allies together, and keep an eye on your available [ROWS|front-row spaces]."),
    REDSTONER(CardClass.REDSTONER, "Play a [BLOCKS|block] with Activate first. You can then pay for the power on the turn when that block's effect helps most."),
    DECKS(COLLECTION, "Building a deck",
            "Use **Add inventory cards** to file your cards. Choose **New Deck**, pick a [HEROES|class], and add exactly " + DeckRules.DECK_SIZE + " cards. Use cards from that class and Neutral.",
            "A deck can include up to two copies of a card, or one [RARITY|Legendary]. Normal and Enchanted count together. Generated tokens cannot go in your starting deck.",
            "Saved decks reuse the copies in your collection. Shift-click a card in the collection to take a copy out for trading. If that leaves a deck short, replace the missing copy before playing.",
            "The **XP curve** counts your cards at each cost. Include cheap plays so you can spend XP in the opening turns."),
    RARITY(COLLECTION, "Rarity & packs",
            "The gem shows rarity: **Common** is gray, **Rare** blue, **Epic** purple, and **Legendary** gold. Legendary art also has a special border.",
            "A Foundation pack contains " + BoosterPacks.CARDS_PER_PACK + " cards. Fortune improves rarity rolls. Luck improves the chance of an Enchanted finish.",
            "Enchanted copies have the same rules and stats. Use the binder's Normal and Enchanted filters to browse your versions."),
    MASTERY(COLLECTION, "Class Mastery",
            "Mastery belongs to the class you play. Open **Mastery** in the binder to see your progress. Match [XP] is separate and starts fresh every game.",
            "Once a match reaches its sixth individual turn, a win against another player earns **25 points** and a completed loss earns **10**. Practice against the table's Card Master earns **10** for a win and **4** for a loss. A draw or conceding earns you none.",
            "Every " + MasteryProgress.POINTS_PER_LEVEL + " points gains a level, up to " + MasteryProgress.MAX_LEVEL + ". At levels 2, 4, 6, 8 and 10, you can receive missing copies of a Common from that class. Mastery also unlocks [LOOKS|board designs]."),
    LOOKS(COLLECTION, "Card backs & boards",
            "Equip a card back for your saved deck. Card backs come from The Trials. Use the reward item to permanently unlock it.",
            "Equip a board in the binder's **Boards** tab. At match start, either player's choice has an equal chance to appear for everyone.",
            "Classic and Spruce Lodge are available from the start. Reach [MASTERY|Mastery] 2 in any class for Amethyst Grotto and level 3 for Ocean Monument.");

    final RuleChapter chapter;
    final String title;
    private final List<String> paragraphs;
    private final CardClass hero;
    private static final Pattern MARKUP = Pattern.compile("\\[([A-Z]+)(?:\\|([^\\]]+))?\\]|\\*\\*(.+?)\\*\\*");

    RulePage(RuleChapter chapter, String title, String... paragraphs) {
        this.chapter = chapter;
        this.title = title;
        this.paragraphs = List.of(paragraphs);
        this.hero = null;
    }

    RulePage(CardClass hero, String advice) {
        this.chapter = CLASSES;
        this.title = hero.name().charAt(0) + hero.name().substring(1).toLowerCase(java.util.Locale.ROOT);
        this.hero = hero;
        this.paragraphs = List.of(advice);
    }

    List<Component> content() {
        List<Component> result = new ArrayList<>();
        if (hero != null) {
            var power = HeroPowers.of(hero);
            result.add(Component.empty().append(power.displayName().copy().withStyle(style -> style.withBold(true)))
                    .append("  ·  ").append(link(XP, Match.POWER_COST + " XP")));
            result.add(power.description());
        }
        for (String paragraph : paragraphs) result.add(format(paragraph));
        if (this == FAMILIES) {
            result.add(Component.literal(Arrays.stream(MobType.values())
                    .map(type -> type.displayName().getString()).collect(Collectors.joining(" · "))));
        }
        return result;
    }

    private static Component format(String paragraph) {
        MutableComponent result = Component.empty();
        var matcher = MARKUP.matcher(paragraph);
        int end = 0;
        while (matcher.find()) {
            result.append(paragraph.substring(end, matcher.start()));
            if (matcher.group(1) == null) {
                result.append(Component.literal(matcher.group(3)).withStyle(style -> style.withBold(true)));
            } else {
                var page = valueOf(matcher.group(1));
                result.append(link(page, matcher.group(2) == null ? page.title : matcher.group(2)));
            }
            end = matcher.end();
        }
        return result.append(paragraph.substring(end));
    }

    static Component link(RulePage page, String label) {
        return Component.literal(label).withStyle(style -> style.withBold(true).withUnderlined(true)
                .withColor(0x1B6554).withInsertion(page.name()));
    }
}

enum RuleChapter {
    BASICS("First steps"), TURN("Turns & XP"), BOARD("Combat"), CARDS("Card types"),
    ABILITIES("Effects"), CLASSES("Classes"), COLLECTION("Collection");

    final String label;
    RuleChapter(String label) { this.label = label; }

    List<RulePage> pages() {
        return Arrays.stream(RulePage.values()).filter(page -> page.chapter == this).toList();
    }
}
