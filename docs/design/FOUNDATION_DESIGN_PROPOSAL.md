# Craftbound Foundation Design Proposal

Readable Page: https://chatgpt.com/space/page_801307e7ec1081919e6025adcda99068

Craftbound should launch Foundation with four distinct classes, useful low-rarity cards, and a small vocabulary of rules. Its strongest identity is already present: buildings occupy fighting space, crops can become cards, tools wear out, and mobs leave useful drops.

My recommendation is to keep the **180-card target**, allocate **30 cards to each of the four classes and 60 to Neutral**, and launch with **one legendary per class**. This changes the earlier Neutral allocation and is a proposal, not a catalogue edit. Keeping both 90 Neutrals and 30 per class would require 210 cards.

Prioritise class decks, readable combat and authored NPC opponents. Keep biomes cosmetic or confined to optional PvE encounters for Foundation. Put class mastery ahead of a public ranked ladder, and give collectibles cosmetic prestige without stronger combat stats.

Prepared 2 November 2026 for the current Craftbound project. Proposed numbers below are playtest starting points. Current implementation facts, proposed content and later ideas are distinguished throughout.

For a quick read, start with the class identities, legendary table and practical Foundation sequence. The Neutral roster and passive bank are reference sections for later Workshop sessions.

| Class | What it should do best | Weakness to preserve |
|---|---|---|
| Fighter | Combat trades, tools, coordinated Illagers | Limited recovery and passive value |
| Builder | Durable structures and protected engines | Slow setup and crowded board space |
| Farmer | Animals, growth and converting Crops into resources | Limited immediate damage and hard removal |
| Fisherman | Aquatic schools, catches and selective repositioning | Small bodies and dependence on partners |

## Foundation scope and card allocation

The earlier 180-card agreement covered Fighter, Builder and Farmer: 90 class cards plus 90 Neutrals. Fisherman changes that arithmetic. The three workable options are:

| Plan | Neutral | Class cards | Total | Assessment |
| --- | ---: | ---: | ---: | --- |
| Recommended | 60 | 30 per class | 180 | Preserves the total and gives each class enough identity |
| Preserve 90 Neutral | 90 | 30 per class | 210 | More generic options, but 30 more designs to test |
| Preserve 180 and 90 Neutral | 90 | 22 or 23 per class | 180 | Uneven class counts and less room for two class strategies |

Sixty Neutrals remain a substantial shared pool. At two copies per ordinary card, a class does not need 90 shared designs to complete a 30-card deck. It needs reliable early plays, interaction, a little recovery and a few flexible finishers.

Do **not** make rarity counts or costs numerically even. Make useful roles available at accessible rarities, and give every class a playable curve. Equal numbers of one-cost and ten-cost cards would produce a poor set.

| Allocation | Common | Rare | Epic | Legendary | Total |
| --- | ---: | ---: | ---: | ---: | ---: |
| Each class | 14 | 10 | 5 | 1 | 30 |
| Four classes | 56 | 40 | 20 | 4 | 120 |
| Neutral | 28 | 20 | 8 | 4 | 60 |
| Foundation | 84 | 60 | 28 | 8 | 180 |

Rarity should control scarcity, presentation and how unusual a design is. It should not mean that an Epic is simply a better Common. The strongest ordinary two-drop in a class can be Common. A large vanilla Ravager does not need to be Epic.

A useful provisional cost budget across the whole set is: **2 cards at 0 XP, 20 at 1, 38 at 2, 38 at 3, 30 at 4, 22 at 5, 14 at 6, 8 at 7, and 8 at 8–10**. That totals 180. These are design slots, not final restrictions; zero-cost collectible cards should be rare exceptions. Generated tokens are outside the 180.

A provisional kind budget is:

| Group | Mobs | Blocks | Items | Tools | Enchantments |
| --- | ---: | ---: | ---: | ---: | ---: |
| Fighter | 18 | 2 | 6 | 2 | 2 |
| Builder | 12 | 10 | 4 | 2 | 2 |
| Farmer | 16 | 7 | 3 | 2 | 2 |
| Fisherman | 18 | 4 | 4 | 2 | 2 |
| Neutral | 38 | 10 | 12 | 0 | 0 |
| Total | 102 | 33 | 29 | 8 | 8 |

A tool created by a card can be a token without consuming a collectible tool slot. Treat these kind counts as a budgeting exercise: a design must earn its slot through gameplay, not merely fill a spreadsheet cell.

Each class should have **one straightforward starter strategy and one overlapping alternate strategy**. Support those two properly before adding a third. Aim for at least 12 viable choices at 1–3 XP per class, counting its Neutrals, and two accessible ways to recover from losing the board.

## What the current project already tells us

The bundled catalogue contains **63 definitions: 53 collectibles and 10 tokens**. The saved development runtime catalogue contains **64 definitions: 54 collectibles and 10 tokens**, including your new Zombie Fisherman. The table below describes the bundled baseline used for the main automated comparison:

| Group | Cards |
| --- | ---: |
| Neutral | 19 |
| Fighter | 7 |
| Builder | 7 |
| Farmer | 10 |
| Fisherman | 10 |

The runtime version adds one Rare to Fisherman, bringing that class to 11 collectibles. Its existing cards otherwise match the bundled definitions.

In the bundled baseline there are 17 Commons, 18 Rares, 14 Epics and 4 Legendaries. Thirty-one of the 53 collectibles cost 2 or 3 XP; only two cost 6 or 7. The sample pool therefore needs a much broader Common base and more useful middle-to-late plays, not simply more premium effects.

Current match rules are 20 hero Health, 30-card decks, seven board spaces, ten cards in hand, up to ten normal XP, two copies per ordinary card and one per legendary. Both mobs and blocks occupy the seven spaces. Normal and enchanted copies share the same deck limit. Tools have Attack and durability; a hero attack spends one durability.

The existing effect system already supports typed and mob/block targeting, Spawn, Drop, end-of-turn effects, attack-related effects, buffs, healing, draws, generated cards, tool enchantments, Frozen, Withered, Totems and invulnerability. Its explicit continuous passive choices are currently attack auras and damage reduction. A card described below as a “passive idea” may instead use a triggered effect; the distinction matters when implementing it.

Three rules deserve particularly clear wording:

- **Spawn means played from hand.** A summon or Totem resurrection does not replay Spawn.
- **Drop means death.** Harvesting/removing a crop and returning a card to hand do not trigger Drop. A bounced token disappears under the current rules.
- **Blocks never attack or retaliate normally.** Their Attack stays zero. An explicit thorn effect still damages an attacker.

These are useful foundations. Keep them consistent across card text, previews, the Workshop and NPC decisions.

The current Master chooses a class and builds a deck at runtime. Its action policy generally plays the most expensive affordable card, uses its power, then attacks. Several target and play-value decisions still identify specific cards. New catalogue effects can therefore work correctly on the server while the AI uses them poorly.

Class mastery, collector grading and a PvP rating ladder are proposed systems below; they should not be described as already implemented.

## Class strengths and weaknesses

### Fighter

**Promise:** win the board through combat, then convert surviving attackers and a good tool into pressure.

Primary strengths are efficient trades, Sprint, Illagers that fight together, and reliable tools. Its second strategy can be a smaller number of strong attackers backed by enchantments. Those strategies share tools and combat cards, so a beginner can gradually change the deck instead of replacing it.

Its weaknesses should be limited healing, limited passive card generation, and needing a board or a tool to get its best results. A Fighter should not also receive the best healing engine, cheap unrestricted removal and easy mass draw.

Craft currently equips a 1-Attack, 1-durability sword for 2 XP. Keep it as a useful fallback rather than the best play every turn. The player's Health is a real resource when trading with a tool.

Good class card directions include an Illager body that becomes useful immediately, a mob that rewards its own attacks, a modest combat draw effect, tool upgrades and one tool-dependent board clear. Keep direct hero-damage spells scarce.

Concrete design seeds:
- **Raid Drummer:** a small Illager whose attack gives another friendly Illager +1 Attack.
- **Sparring Partner:** a durable, low-Attack mob that helps another friendly mob survive a trade.
- **Weapon Rack:** a fragile block that provides one known tool, not an endless supply.
- **Field Rations:** modest recovery conditional on combat; not a repeatable healing engine.
- **Iron Axe:** strong against blocks, if conditional damage is added later; do not make it ignore Shield by default.
- **Battle Standard:** a temporary or fragile attack support piece rather than a permanent extra-attack engine.

For its 30 slots, reserve roughly eight for Illager combat, six for tools and their support, six for generally useful combat bodies, five for interaction, four for flexible bridges, and one for its legendary. Those categories describe purpose and can overlap with the kind budget.

**Risk:** tools and enchantments create large *potential* damage. A full-durability Diamond Sword plus Sharpness represents two 7-damage attacks, or 14 over two attacks—not 14 damage in one action. At 20 starting Health that needs careful counterplay, even with the costs, retaliation and Shield restrictions.

### Builder

**Promise:** invest in a structure, protect it, then make that structure help you win.

Builder should have the best block durability, block support and controlled production. Its alternate strategy is Golems and Villagers protected by a smaller infrastructure package. A Builder deck must contain attackers and a way to finish; an entire row of zero-Attack buildings is a self-inflicted stalemate.

Weaknesses should be slow development, spending board spaces on non-attackers, and vulnerability to deliberate engine removal or being outpaced. Builder should not casually turn its whole board into invulnerable, endlessly healing walls.

Build creates a 0/2 Shield wall for 2 XP. This supplies defence without spending a card, but also consumes one of seven spaces. That tension is excellent. Do not remove it by introducing a free separate building row in Foundation.

Good directions:
- **Stone Mason:** Spawn gives another friendly block +2 Health.
- **Repair Golem:** a modest body that restores Health to another block at turn end.
- **Stonecutter:** a single-use construction benefit rather than unlimited cost reduction.
- **Iron Golem:** a substantial defender that makes protected buildings useful.
- **Redstone Lamp:** a low-output, predictable engine that teaches turn-end timing.
- **Demolition Charge:** targeted block interaction, priced to avoid replacing all mob removal.

Keep attack auras narrow and fragile. Beacon is interesting because it needs buildings *and* mobs sharing seven slots. Two Beacons should impose a real opportunity cost, not grant a free universal damage bonus.

Its 30 slots should support about eight infrastructure designs, six Golem/Villager bodies, five protection or recovery cards, four construction interactions, four broadly useful cards, two flexible slots and one legendary. Avoid giving every building both an immediate card back and recurring value.

The current Bow deserves a specific review: repeatable Ranged hero attacks avoid ordinary retaliation. Keep its lower Attack and limited durability meaningful rather than treating it as an ordinary sword with a free keyword.

### Farmer

**Promise:** cultivate a board, keep living things healthy, and choose when to turn crops into resources.

Farmer's primary strategy is Animals supported by Crops. Its alternate strategy is a slower crop-and-value deck with a few durable threats. Villagers such as Shepherd connect both. Crops should make Animals better, not merely be anonymous draw tickets.

Strengths are Health buffs, recovery, small bodies, and converting earlier investment into cards. Weaknesses are poor immediate damage, limited unconditional removal and losing value when crops are destroyed before harvest.

The current power deliberately alternates Sow and Reap: using Sow enables Reap while crops remain. Generated crops alone do not necessarily enable Reap before that first Sow. Make the button and tooltip explain this rather than expecting players to discover the state machine.

Good directions:
- **Mooshroom:** a sturdy Animal with a small healing role.
- **Scarecrow:** a defensive block that protects a crop board without also generating cards.
- **Sprinkler:** slow, bounded crop growth.
- **Seed Pouch:** establish crops, but pay for both the resources and board spaces.
- **Harvest Basket:** turn a crop into a specified benefit; it should not count as killing it.
- **Farmhand:** a modest Villager that rewards keeping an Animal alive.

Do not equate Health growth with healing: increasing maximum Health permanently can repeatedly protect an engine and push it out of removal range. Composter already gives +1/+1 every turn. A full suite of cheap Composter equivalents would make ignoring one turn fatal.

Minecraft's actual composting loop turns plant matter into bone meal. Craftbound could eventually express that as “harvest a crop, receive a small growth item,” which is more distinctive than yet another random +1/+1 building. Keep the current Composter for baseline tests; treat that conversion as an alternative design, not an extra effect stapled onto it. [Minecraft Composter](https://www.minecraft.net/en-us/article/block-month--composter)

Allocate its 30 slots around eight Animal cards, six crop or harvest cards, five growth/healing cards, four Villager bridges, four tools/enchantments, two interaction slots and one legendary. Ensure at least one accessible recovery play works from an empty board.

### Fisherman

**Promise:** develop an Aquatic school, manage a steady stream of small catches, and win through efficient exchanges.

Fisherman should own Aquatic density, modest card generation, selective disruption and resilient schools. Its alternate strategy is a slower fishing/tool value deck. It should not become a second Farmer that only differs in creature names.

Distinguish them through resource flow: Farmer invests **on the board**, then harvests; Fisherman produces **in hand**, then pays to deploy. Crops cost space before they pay out. Catches cost hand space and future XP.

Weaknesses should be small individual bodies, dependence on Aquatic partners, limited hard removal and vulnerability to losing several small cards together. Its best full reset should not be cheap or available on every turn.

Cast currently costs 2 XP to add a 1-cost 1/1 Caught Cod. That is 3 XP in total to acquire and deploy it, split across actions. Evaluate the full class before buffing the power: changing Caught Cod to cost zero would also strengthen Fishing Rod, Lure and School of Fish.

Good directions:
- **Pufferfish:** a small Aquatic with an explicit thorn effect, without inventing Poison.
- **Axolotl:** help a wounded Aquatic recover after trading.
- **Bucket of Cod:** a small targeted body-generation spell.
- **Reel In:** return a friendly Aquatic, with an explicit replay cost.
- **Kelp Bed:** a fragile block that helps a school survive rather than generating infinite fish.
- **Trident:** a more expensive tool option whose limited durability prevents constant free trades.

Fishing in Minecraft provides fish and occasional other finds; rods also have a pulling use. That gives the class two natural themes—resource acquisition and controlled repositioning—without requiring a complicated treasure roulette. Foundation should use named catches and predictable effects. [Minecraft Fishing Rod](https://www.minecraft.net/en-us/article/fishing-rod)

Allocate its 30 slots around ten Aquatic bodies, five catch/generation cards, four tempo interactions, four tools/enchantments, three support blocks, three flexible cards and one legendary.

Do not add permanent freeze chains as its main identity. A deck that repeatedly prevents an opponent from participating is not the kind of control Foundation needs.

### Your Zombie Fisherman prototype

The saved runtime card is a **3 XP 2/3 Rare Undead** that adds Rotten Flesh to your hand at the end of every turn you take. It is not currently Aquatic and does not generate Caught Cod.

That creates a real bridge: Drowned buffs it, while Dolphin does not. The recurring zero-cost healing can be stored until needed, unlike Furnace's immediate heal, so it may give Fisherman more recovery than the intended weakness allows. It also consumes hand space and can eventually cause overdraw.

Keep it as a test card rather than changing it automatically. Compare the current version with two deliberate alternatives: **3 XP 2/3 generating Caught Cod** for the fishing identity, or **4 XP 2/4 generating Rotten Flesh** for a slower Undead sustain engine. Those are alternatives, not extra effects to combine. Its name, stable ID and collector number should stay fixed while testing one version at a time.

## Keyword and status audit

Borrowing a familiar combat grammar is useful. Changing every name does not create originality; making the decisions around buildings, harvesting, durability and drops matter does.

| Rule | Assessment | Foundation recommendation |
| --- | --- | --- |
| Shield | Familiar forced-attack protection; functionally the Taunt role | Keep. Explain that it redirects attacks, not every spell or effect |
| Sprint | Strong interaction tool; its no-face-on-arrival restriction is essential at 20 Health | Keep the current rule. Do not add Charge beside it |
| Ranged | The most concerning existing evergreen combat advantage | Keep scarce, with lower stats or durability; do not price it like flavour text |
| Spawn | A clear play-from-hand trigger, analogous to Battlecry | Keep. Avoid putting it on almost every mob |
| Drop | A clear death trigger, analogous to Deathrattle | Keep, especially for recognisable Minecraft loot |
| Block | More useful as a card kind than a keyword in the player's mental model | Keep engine compatibility, but present “Block” clearly and consistently |
| Frozen | Useful short disruption, potentially frustrating in loops | Keep sparse; do not make repeatable denial a Common package |
| Withered | Good attrition identity and a reason to heal or cleanse | Keep one non-stacking debuff; avoid large amounts of cheap application |
| Totem | Recognisable resurrection insurance | One protection flag, consumed on resurrection; no copied buffs or replayed Spawn |
| Invulnerable | Powerful temporary protection | Keep duration explicit; do not spread it across cheap repeatable generators |

Sprint's underlying rule is directly comparable to Hearthstone Rush: it can attack opposing board units on arrival but cannot attack a hero that turn. That is an honest influence, and it serves the desired gameplay. [Blizzard explanation of Rush](https://hearthstone.blizzard.com/en-us/news/21658543/non-printable)

**Ranged needs its own test suite.** Avoiding retaliation compounds over repeated attacks, and becomes much stronger with Attack buffs, healing, protection or freezing. A low-Attack Ranged mob can still earn several free trades. The current Shulker combines Shield, Ranged and repeatable freezing; that is too much protection and denial in one small Neutral package to treat as a routine baseline.

Ranged currently skips ordinary retaliation, but explicit “when attacked” effects still run. The tooltip should say that rather than promise absolute immunity while attacking. Sweet Berry Bush should remain an understandable exception because its text names the triggered damage.

Withered deals its damage at the start of its owner's turn and persists until removed or the card leaves play. Reapplying it should not secretly increase the damage. Milk Bucket is a useful narrow future answer: remove Frozen and Withered, without a general-purpose Silence rule that also strips kinds, types, auras and every other property.

Frozen currently clears its boolean flag at turn start while the skipped attack is represented by readiness. Make the visual communicate the skipped turn, not merely the presence of that flag. This is a clarity issue worth reviewing before release.

Keep new evergreen keywords out of the first content pass. “Harvest” can first be a plainly explained action on Farmer cards: remove your own Crop without triggering Drop. Only promote an action to a keyword after several cards need it and players consistently understand the same rule.

The current keyword set is not inherently boring. It becomes boring if every card is “Spawn: gain stats.” Use roughly three families of ordinary mobs: simple bodies, one-keyword combat bodies, and one clearly timed effect. Reserve multiple abilities for designs where they genuinely interact.

## Synergy that creates choices

A good synergy card should be playable at a modest floor, become better with support, and still leave the opponent a response. Avoid cards that are worthless without one specific legendary.

| Family | Foundation role | Main home | Bridge without erasing identity |
| --- | --- | --- | --- |
| Animal | Durable bodies, healing and growth | Farmer | Neutral simple Animals let other classes use a small package |
| Aquatic | Schools, catches and selective tempo | Fisherman | A few neutral water-themed utility items, not a full neutral school |
| Illager | Combat pressure and attack rewards | Fighter | Neutral Illager bodies create choices around the Fighter payoffs |
| Golem | Durable attackers that benefit from infrastructure | Builder | Very few Neutral Golems; class cards carry the building interactions |
| Villager | Useful professions and support | Neutral, Builder and Farmer | One profession supports blocks, another Animals; not every Villager buffs every Villager |
| Undead | Useful drops and trading value | Neutral | Drowned supports a modest package available to every class |
| Crop | A board resource with harvest decisions | Farmer | One or two Neutral Crops allow experimentation |
| Ender and Nether | Distinctive individual threats | Mostly Neutral in Foundation | Do not imply complete tribal decks until there are enough bodies |
| Arthropod and Slime | Small future-facing families | Mostly Neutral | Use tags for recognition; defer large dedicated payoff suites |

The implementation currently gives each mob one type. Keep that constraint visible. Drowned is **Undead**, so Dolphin should not buff it merely because the creature lives underwater. Bee is **Arthropod**, not Animal. A future multi-type system is possible, but changing it changes dozens of target interactions and the value of broad buffs.

For a real tribal deck, start with around **8–12 distinct usable bodies across the class and Neutral pools**, including at least four that cost 1–3 XP, then provide two or three payoffs. These are design targets, not a universal formula. A lone “all Slimes get stronger” card with only one available Slime creates a false promise.

Use three layers:
1. **Enablers:** sensible bodies, crops, tools or blocks.
2. **Bridges:** cards that connect two existing resources, such as a Villager supporting Animals or a tool creating crops.
3. **Payoffs:** one larger benefit that rewards keeping the earlier investment alive.

A Farmer example is Cow plus Wheat, followed by Shepherd, then choosing between keeping the crop for support and harvesting it for resources. A Builder example is a protected Dispenser beside a Golem: remove the building and reduce future value, or remove the attacker and leave the engine running. Those are more interesting decisions than simply counting a tribe and adding +1/+1.

Do not make “control a block” and “control a Crop” identical conditions. A Crop can be converted and disappears; an ordinary building is an ongoing investment. Likewise, generated fish are not automatically the same resource as cards drawn from the deck: fishing can fill the hand without accelerating Hunger.

Keep automatic effects predictable. Where random outcomes are retained, show the eligible pool and use narrow, declared pools. Existing yellow benefit previews, red harm previews and broken candidate outlines are valuable tools for teaching these relationships.

## Passive ability bank

These are **32 candidate effects**, not 32 new keywords and not a request to put all of them into Foundation. Start with a small selection of the existing-effect options. The engine notes prevent an attractive card pitch from being mistaken for a Workshop-only edit.

“Once per turn” must be explicit and tracked per source. Re-summoning a source must not accidentally bypass a limit intended to prevent an infinite loop. Decide that rule before building repeatable resource engines.

| Idea | Example wording | Home | Implementation and scope |
| --- | --- | --- | --- |
| Thick Hide | Reduce each incoming hit by 1. | Neutral or Fighter | Existing; keep very scarce |
| Regrowth | At the end of your turn, restore 1 Health to this mob. | Farmer | Existing; Foundation |
| Thorns | When attacked, deal 1 damage to the attacker. | Farmer or Neutral | Existing; Foundation |
| Battle Aura | Your other friendly mobs have +1 Attack. | Builder | Existing; fragile source |
| Village Nurse | At turn end, restore 1 Health to your other Villagers. | Neutral | Existing; Foundation option |
| Crop Tender | At turn end, give another random friendly Crop +1 Health. | Farmer | Existing; watch engine durability |
| Reef Shelter | At turn end, restore 1 Health to your other Aquatics. | Fisherman | Existing; Foundation option |
| Conduit Growth | At turn end, give a random friendly Aquatic +1 Health. | Fisherman | Existing; already close to Conduit |
| Raid Inspiration | After this attacks, give your other Illagers +1 Attack. | Fighter | Existing; premium payoff |
| Useful Catch | After your hero attacks with this tool, add a Caught Cod. | Fisherman | Existing; already on Fishing Rod |
| Cultivating Tool | After your hero attacks with this tool, plant Wheat. | Farmer | Existing; already on Iron Hoe |
| Patient Distributor | At turn end, give another random friendly mob +1/+1. | Farmer | Existing Composter; avoid many copies |
| Guarded Workshop | While you control a block, this has +2 Attack. | Builder | New continuous condition; prototype |
| Mason | The first block you play each turn gains +1 Health. | Builder | New play-listener and once-per-turn tracking |
| Toolsmith | The first time you equip a tool each turn, draw a card. | Builder or Fighter | New equip listener; test refill loops |
| Finishing Blow | The first time this kills a mob each turn, draw a card. | Fighter | New kill attribution; prototype |
| Battle Hardened | After this survives damage, gain +1 Attack, once per turn. | Fighter | New survived-damage trigger |
| Braced | Reduce the first damage this takes each turn by 1. | Builder | New turn-scoped mitigation; alternative to permanent armour |
| Pollinator | The first Crop you play each turn restores 1 Health to your hero. | Farmer | New play listener |
| Harvest Companion | The first Crop you harvest each turn gives this +1 Health. | Farmer | New harvest listener |
| Seed Saver | Once per turn, when another friendly Crop dies, add a Wheat to hand. | Farmer | New friendly-death listener; harvest must not qualify |
| Grazer | At turn end, restore 2 Health to this if you control a Crop. | Farmer | New condition; modest payoff |
| Schooling | After you play an Aquatic, gain +1 Attack, once per turn. | Fisherman | New play listener; not per generated card |
| Patient Angler | If this survived from your previous turn, add a small catch. | Fisherman | New age tracking; later |
| Salvager | Once per turn, after another friendly block is destroyed, add a named Scrap. | Builder | New death listener and token; later |
| Village Broker | The first Item you play each turn restores 1 Health to a friendly Villager. | Neutral | New item listener; a small bridge |
| Watchful Golem | Your other Golems have +1 Attack while you control a block. | Builder | New typed conditional aura; later |
| Crop Converter | At turn end, harvest one Crop to add Bone Meal. | Farmer | New bounded harvest action; test as a Composter alternative |
| Tool Maintenance | At turn end, restore 1 durability, up to the tool's printed maximum. | Builder | Needs maximum-durability tracking; later, high loop risk |
| Redstone Relay | Repeat one adjacent block's turn-end effect once. | Builder | Expansion only; adjacency and repetition create many edge cases |
| Bodyguard | Redirect damage from an adjacent friendly mob to this. | Builder | Expansion only; damage ordering and loops |
| Fuelled Furnace | Consume a named fuel item for one stronger production cycle. | Builder | Expansion only; avoid a second resource economy in Foundation |

My first selection would be **Regrowth, Thorns, one attack aura, one typed healing engine, one crop growth engine, one combat payoff, the existing fishing/hoe tool triggers, and the existing armour card**. That gives different behaviours without expanding the basic vocabulary.

The most valuable new engine work is a small, bounded **Harvest action**. A general “first card of a kind played this turn” listener is the next candidate, but it is not required merely to reach 180 designs. Cost reduction, repeated triggers, damage redirection and adjacency should wait.

Give every recurring effect a deliberate cost: a weak body, a board slot, a condition, a turn of delay or a limited number of uses. A modest effect repeated five times is often stronger than an impressive one-time effect.

## Class legendaries

Launch with **one per class**. Keep a second concept in reserve until the first 30-card class package has a clear weakness and at least one good non-legendary deck. The names below that describe ranks or individuals are Craftbound variants, not claims that vanilla Minecraft has those named characters.

| Class | Foundation legendary | Prototype | Role and counterplay |
| --- | --- | --- | --- |
| Fighter | Raid Captain | 7 XP, 5/6 Illager. Sprint. After this attacks, give your other friendly Illagers +1 Attack. | Wins a board exchange and rewards an established raid. No immediate face attack; clear its partners or remove it before the next turn |
| Builder | Iron Sentinel | 7 XP, 4/8 Golem. Shield. Spawn: give your other friendly blocks +2 Health. | Protects an investment and supplies an actual attacker. Weak on an empty infrastructure board; bounce or remove the Sentinel |
| Farmer | Ancient Sniffer | 7 XP, 4/7 Animal. Spawn: harvest up to your two oldest Crops; summon a 3/3 Mooshroom for each harvested. | Converts farm investment into pressure. Needs the new bounded Harvest action; do not add a second choice UI |
| Fisherman | Elder Guardian | Start by testing the existing 8 XP 6/8: its enemy-board damage and Aquatic Health support are already a class payoff. | Keep as the initial reference legendary. Review its large recovery swing before adding another cheap class clear |

Ancient Sniffer's harvest removes crops without Drop and cannot consume enemy Crops. Each removed crop makes room for its replacement. If there are no Crops it remains a 4/7, so the rest of the deck must make the setup practical. “Oldest” must be shown in previews; if that ordering is too obscure, use a clearly explained deterministic rule or a simpler design before shipping.

Iron Sentinel is intentionally a straightforward first-set legendary. Being legendary does not require a paragraph of exceptions. It needs a memorable moment, a useful deck role and a recognisable identity.

Reserve concepts:
- **Fighter — Netherite Champion:** a survivor that grows after taking damage once per turn. Requires the new survived-damage trigger; avoid combining it with Sprint, armour and free draw.
- **Builder — Copper Foreman:** rewards the first block played each turn with a small construction benefit. A future alternative for a more active Builder deck.
- **Farmer — Queen Bee:** the first Crop played each turn produces one small Bee. Needs a play listener and one visible limit; keep the crop-and-swarm theme distinct from the Ancient Sniffer's conversion.
- **Fisherman — Ancient Axolotl:** a late sustain engine for other Aquatics. Prefer restoring existing Health to endlessly increasing maximum Health.

One per class plus four Neutrals gives eight Foundation legendaries. If a second per class later proves necessary, replace four planned slots rather than silently increasing the set size.

Keep **Warden, Wither and Ender Dragon Neutral** as recognisable shared chase cards, but reduce the risk that every late-game deck simply plays the same neutral bosses. The fourth Neutral legendary can be a modest, fixed-supply Wandering Trader rather than another universal board wipe.

## Sixty Neutral candidates

This is a concrete **60-card candidate roster**, not 60 additions. It retains all 19 current Neutral identities, proposes revisions to six of them, and adds 41 new designs. Its rarity counts match the 28 Common, 20 Rare, 8 Epic and 4 Legendary Neutral budget.

All costs and stat changes in this section are prototypes. “Existing” means retain the current design for the first comparison pass; “Rework” proposes a change to that existing card. Reworks keep the same stable ID and collector number. New designs append new IDs; nothing here authorises renumbering the collection.

Some repeated plain bodies are intentional because different types provide different deck-building connections. Even so, cut a card if it is only a worse version of another card *within the same relevant role*. In particular, the proposed Zombie stat change is what gives plain 2/3 bodies breathing room.

### Mobs

| Card and status | Rarity | XP | Stats and type | Draft ability or purpose |
| --- | --- | ---: | --- | --- |
| Chicken (new) | Common | 1 | 1/2 Animal | No ability; an early body for Animal support. |
| Pig (new) | Common | 2 | 2/3 Animal | No ability; the ordinary two-cost stat benchmark. |
| Sheep (new) | Common | 2 | 1/4 Animal | No ability; survives small hits but applies little pressure. |
| Wolf (new) | Common | 3 | 3/3 Animal | Spawn: give another friendly Animal +1 Attack. |
| Rabbit (new) | Common | 1 | 2/1 Animal | No ability; trades up but dies to a ping. |
| Creeper (existing) | Rare | 3 | 2/2 None | Drop: deal 2 damage to every other board card. |
| Horse (new) | Common | 4 | 4/5 Animal | No ability; a dependable middle-of-curve body. |
| Polar Bear (new) | Rare | 5 | 4/6 Animal | Shield. Defensive Animal with no generated value. |
| Charged Creeper (existing) | Epic | 5 | 3/3 None | Keep its current explosion for initial tests; watch the 4 hero damage. |
| Fox (new) | Rare | 2 | 2/2 Animal | Drop: draw a card. Small value body, not a tribe-wide payoff. |
| Zombie (rework) | Common | 2 | 2/2 Undead | Drop: add Rotten Flesh. Test -1 Health so vanilla 2/3s retain a purpose. |
| Skeleton (existing) | Common | 3 | 2/2 Undead | Ranged. Drop: add an Arrow. |
| Drowned (existing) | Rare | 3 | 3/3 Undead | Spawn: give your other friendly Undead +1 Attack. |
| Husk (new) | Common | 3 | 3/4 Undead | No ability; a larger Undead without another free resource. |
| Stray (new) | Rare | 4 | 2/4 Undead | Ranged. Do not also grant repeatable Frozen in Foundation. |
| Zombie Villager (new) | Common | 2 | 2/2 Undead | Spawn: restore 2 Health to your hero. |
| Wither Skeleton (existing) | Epic | 5 | 4/4 Undead | Keep its current Withered-on-damage effect; avoid duplicating it cheaply. |
| Phantom (new) | Rare | 4 | 4/3 Undead | Sprint. Immediate board interaction with a fragile body. |
| Villager (existing) | Common | 2 | 2/3 Villager | No ability; a simple base for profession support. |
| Librarian (existing) | Rare | 3 | 2/4 Villager | Spawn: give your other friendly Villagers +1 Health. |
| Armorer (new) | Rare | 3 | 2/3 Villager | Spawn: give a friendly mob +2 Health. |
| Cleric (new) | Common | 3 | 2/3 Villager | Spawn: restore 3 Health to a friendly mob or block. |
| Cartographer (new) | Rare | 4 | 3/3 Villager | Spawn: draw a card. Exploration value without a new map subsystem. |
| Wandering Trader (new) | Legendary | 6 | 4/5 Villager | Spawn: add a Bread and a Lantern to your hand. Fixed, readable supplies. |
| Pillager Scout (new) | Common | 3 | 3/3 Illager | Drop: add an Arrow. Craftbound variant, distinct from the Fighter's Pillager. |
| Evoker (existing) | Epic | 5 | 3/4 Illager | Keep two Vex and Totem Drop initially; audit its unusually large value bundle. |
| Ravager (rework) | Common | 7 | 7/8 Illager | No ability. Test as an accessible Common finisher instead of an Epic vanilla. |
| Vindicator Guard (new) | Rare | 4 | 3/5 Illager | Shield. Craftbound variant with a defensive role. |
| Spider (new) | Common | 2 | 2/3 Arthropod | No ability; honest stats and a future family tag. |
| Cave Spider (new) | Rare | 3 | 2/3 Arthropod | Spawn: deal 1 damage to an enemy mob or block. No Poison keyword. |
| Silverfish (new) | Common | 1 | 1/2 Arthropod | No ability; a small body, not a promise of a complete Arthropod deck. |
| Slime (new) | Rare | 3 | 2/2 Slime | Drop: summon two 1/1 Tiny Slimes. |
| Enderman (existing) | Epic | 5 | 4/4 Ender | Spawn: return an enemy mob or block to its owner's hand. |
| Shulker (rework) | Epic | 5 | 2/4 Ender | Test Ranged and Spawn: Freeze one enemy mob. Remove Shield and repeatable freezing. |
| Piglin Brute (rework) | Epic | 5 | 4/4 Nether | Test Sprint and reduce each hit by 1; less efficient than the current 4-cost package. |
| Warden (rework) | Legendary | 9 | 6/8 None | Test Shield and Spawn: deal 2 to every enemy board card; reduce universal reset power. |
| Wither (existing) | Legendary | 8 | 6/6 Undead | Keep its symmetrical Spawn blast and Nether Star Drop for initial tests. |
| Ender Dragon (existing) | Legendary | 10 | 8/8 Ender | Keep Sprint and Dragon Egg Drop; judge whether its late resource is useful enough. |

### Blocks

| Card and status | Rarity | XP | Stats and type | Draft ability or purpose |
| --- | --- | ---: | --- | --- |
| Chest (new) | Common | 2 | 0/2 Block | Shield. Drop: draw a card. A small defensive supply cache. |
| Crafting Table (new) | Rare | 3 | 0/3 Block | Spawn: add a 1-cost 2-Attack, 1-durability Stone Axe tool token to your hand. |
| Hay Bale (new) | Rare | 3 | 0/5 Block / Crop | Shield. A Neutral bridge into Farmer's harvest economy. |
| Cobblestone Barricade (new) | Common | 1 | 0/2 Block | Shield. Bought with a card; does not replace the free-card-value Builder power. |
| Bookshelf (new) | Rare | 4 | 0/4 Block | Spawn: draw 2 cards. Slow Neutral refill with a board-space cost. |
| Campfire (new) | Common | 3 | 0/4 Block | At the end of your turn, restore 1 Health to your hero. |
| Cactus (new) | Common | 3 | 0/4 Block | Shield. When attacked, deal 1 damage to the attacker. |
| Target Block (new) | Common | 3 | 0/5 Block | Shield. Drop: draw a card. |
| TNT (new) | Rare | 4 | 0/2 Block | Drop: deal 3 damage to every other board card. Can be deliberately ignited. |
| Obsidian (new) | Common | 5 | 0/9 Block | Shield. A large wall that cannot win by itself. |

A zero-Attack block needs a purpose the opponent cannot ignore indefinitely: protection, an immediate benefit, ongoing output or a deliberate conversion route. This is why the draft Cactus has Shield and Chest replaces itself when its defensive role ends. TNT is a specialised exception because Flint and Steel or an Arrow can deliberately trigger it.

### Items

| Card and status | Rarity | XP | Stats and type | Draft ability or purpose |
| --- | --- | ---: | --- | --- |
| Lava Bucket (rework) | Common | 2 | — Item | Test raising cost from 1 to 2: destroy the enemy tool. |
| Totem of Undying (existing) | Epic | 3 | — Item | Give a friendly mob or block one Totem. Resummon it when it dies. |
| Enchanted Golden Apple (existing) | Epic | 3 | — Item | Give a friendly mob or block invulnerability until your next turn. |
| Bread (new) | Common | 2 | — Item | Restore 4 Health to your hero. |
| Cooked Beef (new) | Common | 3 | — Item | Restore 6 Health to your hero. Slower emergency recovery. |
| Milk Bucket (new) | Rare | 2 | — Item | Remove Frozen and Withered from a friendly board card, then restore 2 Health. Needs a cleanse action. |
| Snowball (new) | Common | 2 | — Item | Deal 1 damage to a mob or block, then draw a card. Watch cheap cycling. |
| Flint and Steel (new) | Common | 1 | — Item | Deal 2 damage to a block on either side. Ignites TNT or removes small engines. |
| Water Bucket (new) | Rare | 1 | — Item | Return a friendly block to hand. Token blocks disappear under the existing bounce rule. |
| Fishing Net (new) | Rare | 3 | — Item | Deal 1 damage to all enemy mobs. No hero damage; a modest swarm answer. |
| Lantern (new) | Common | 1 | — Item | Give a friendly mob +2 Health. |
| Lead (new) | Rare | 2 | — Item | Return a friendly mob to hand. Save a valuable body or replay its Spawn. |

Tiny Slimes and Stone Axe are new token definitions, outside the 180 collectible count. The proposed Ancient Sniffer also needs a Mooshroom token. Keep the total token vocabulary small and show each created card in inspection tooltips.

Most of this roster uses existing effect primitives. Milk Bucket needs a specific cleanse action. No new permanent resource counter, general Silence system, adjacency rule or choice-of-three UI is needed for the other Neutral concepts.

The Neutral cards with the greatest risk of becoming automatic inclusions are Fox, Cartographer, Bookshelf, Snowball, Lava Bucket and the large bosses. Test those as a group: a set can contain individually fair cards that collectively give every class the same generic draw-and-removal package.

## Mechanics that make Craftbound feel like Minecraft

### Put the existing resource loops first

The most promising identity is a familiar sequence: **collect something, turn it into something useful, then decide whether to spend or preserve it**.

- A Zombie dies and leaves useful food.
- A Skeleton leaves ammunition that can finish a damaged target or ignite TNT.
- A crop occupies space now and becomes a card or body later.
- A tool converts durability into combat, fishing or farming output.
- A building earns value only while the opponent lets it stand.

Each loop uses an existing visible object. There is no need for hidden ore points, a nine-cell recipe interface or separate hunger, food, wood and stone currencies during combat.

Tools and enchanting also have a strong Minecraft connection: anvils repair tools and combine enchantments. For Foundation, use the existing Attack/durability model. Say “give your tool +1 durability” when that is what the engine does; do not label unlimited durability growth as restoration to a maximum that is not tracked. [Minecraft Anvil](https://www.minecraft.net/en-us/article/taking-inventory--anvil)

A good thematic rule must still produce good play. Mining a block could eventually generate a material, but rewarding every destroyed block with a free card would encourage players to farm their own cheap structures and ignore the opponent.

### Foundation candidates

**Harvest is the best new action to prototype.** Its cost is a friendly Crop already on the board. Define removal, no Drop, target eligibility and output exactly. Start with two or three cards, not an entire crafting minigame.

**Milk Bucket cleansing** is a useful narrow interaction. It deals with Frozen and Withered without deleting a card's identity. This is much easier to teach and implement correctly than “remove all text.”

**Named outputs** are preferable to large random pools: a Skeleton gives an Arrow, a fishing tool gives a Caught Cod, a crafting card gives a known simple tool. That supports anticipation and makes the Workshop easier to balance.

**World-based discovery** can distinguish the mod without changing combat at all. Different Card Masters can live in villages, near farms or at coasts; structures can introduce themed encounters and cosmetic rewards. Keep essential competitive cards obtainable through predictable alternatives so a bad world seed does not block a class.

### Biomes

Biomes are a good expansion theme, but a risky launch-wide combat system. Four classes multiplied by several biomes immediately multiply the number of interactions to test.

For Foundation:
- Let the table background and NPC identity reflect a biome.
- Keep normal PvP rules independent of the world biome, weather and time of day.
- If desired, add a clearly labelled optional PvE challenge with one displayed environmental rule. It should not affect ranked results.

For an expansion, test **one personal biome slot per player**, replacing the previous biome when a new one is played. Both sides can then see and plan around the effect. Avoid a shared biome that repeatedly overwrites the other player's strategy or depends on physically moving a table.

Possible later designs include a River that benefits the first Aquatic played each turn, Farmland that improves one harvest, and a Quarry that rewards one destroyed block. They need once-per-turn limits and clear ownership. Do not add all three to Foundation merely because their themes are attractive.

### Deliberately postpone

Keep redstone adjacency chains, repeated triggers, ore/crafting currencies, day/night transformations, weather combinations, equipment slots on every mob, enchantment rarity tiers, extensive treasure pools and multi-type cards for expansions.

Do not add a global “choose one of three random cards” mechanic merely to make every card feel clever. A small, specific choice can be good later; an entire discovery pool changes consistency and balance across the whole game.

Also postpone a separate building lane. Sharing seven spaces is currently one of Craftbound's best decisions. Removing that cost would require repricing nearly every building and crop.

## Class mastery and collection rewards

**Yes to class levels, but call the progression “Mastery” so it is not confused with the XP spent during a match.** A level-30 Farmer must have the same hero Health, starting resources, hero power and card stats as a level-1 Farmer.

Give each class a ready-to-play 30-card starter when it is unlocked. A workable Foundation allocation is six distinct class Commons and nine distinct Neutral Commons, two copies each. Sharing those nine Neutrals across the four starters gives 33 distinct starting cards, not 120 distinct cards. Select the actual lists after the new Common pool exists; the current pool is too small to pretend those four all-Common decks are already possible.

The starter should demonstrate one useful interaction, contain enough early bodies, and have a plain finisher. Do not build it around a legendary the player does not own.

A modest first progression:
- **Class unlocked:** its complete starter and one short teaching match.
- **Mastery 2, 4, 6, 8 and 10:** one predetermined class Common playset at each milestone. That uses five of the eight class Commons remaining after the six-card starter allocation.
- **Class challenge completed:** a guaranteed class Rare playset that opens the second strategy.
- **Longer class quest:** a guaranteed copy of its Foundation legendary. Make the quest about learning several class actions and completing matches, rather than a long win streak.
- **Later levels:** sleeves, portrait frames, titles, alternate card art and deck-display decorations.

These are alternative acquisition routes for cards in the same Foundation set. A gifted card still counts among the 180; do not silently create a separate “Basic” rarity or change booster eligibility without a deliberate decision. Once someone owns a playset, progression rewards should avoid handing out useless duplicate copies. Foils remain cosmetic.

Award modest Mastery for a completed loss, more for a win, and an initial bonus for beating a new NPC. Do not award unlimited progress just for repeatedly playing a one-cost card or farming a passive opponent. Local saves can remain relaxed; a competitive server must decide what results it trusts.

Let a player browse the whole reward track before choosing a class. Predictable access helps them build toward a deck. Keep random boosters as collection excitement, not the only way to obtain a class's essential answers.

## Two useful meanings of card grading

### Grade the design in the Workshop

Use an internal review rubric, separate from rarity. “Epic” should mean a suitable collection tier and degree of specificity, not “strictly better than Common.”

| Test | Question | Failure to catch |
|---|---|---|
| Baseline value | Is it reasonable when the synergy is absent? | A dead card except in one exact combo |
| Supported value | What happens with two realistic partners? | Multiplicative growth priced as a small bonus |
| Immediate impact | What happens before the opponent can respond? | A fragile engine that already paid for itself twice |
| Repeat value | What does surviving one, two or three turns produce? | Infinite resources from a cheap, durable body |
| Interaction | Can another class answer it at a sensible cost? | A lock that only one narrow card can break |
| Class identity | Does it strengthen a class without removing its weakness? | Fisherman getting the best heal, draw and removal |
| Clarity | Can the player predict targets, timing and output? | “Friendly mob” secretly affecting blocks |
| Collection role | Would a beginner or an established deck want it? | Filler with no plausible home |

Score each dimension 1–5 for review if useful, but **do not total the scores into an automatic power rating**. High repeat value is not inherently good design. A 5 for power is often a warning. Store a short verdict instead: **Ready to test**, **Needs support**, **Watch closely**, or **Rework**.

Useful Workshop additions would be a role field, intended deck tags, a short design note, and automatic warnings for extreme stats, free recurring cards, missing targets and class/type mismatches. These are authoring metadata; they need not appear on the card face.

### Grade the collectible without changing combat

A physical-card-style grading system could be fun, but randomly assigning every card another quality number would overload Foundation: it already has rarity, foil, class, type and collector number.

Start with **provenance** instead. Examples are “Won from the River Card Master,” “First edition Foundation,” or a commemorative stamp earned by winning a class challenge with that card. Display the detail on inspection and on a display stand, leaving the inventory icon readable.

If grading is added later, make it a cosmetic service with explicit criteria or an achievement—not a paid random roll and never extra Attack, Health, draws or deck-copy allowances. A high-grade card must play exactly like its ordinary copy. A separate sleeve, foil and grade should not each demand a new currency in the first release.

## Ranked play and NPC progression

Foundation needs satisfying opponents before it needs a large competitive ladder. Start with an **NPC League**: a series of Card Masters with fixed deck identities, introductory dialogue, a visible difficulty and a first-win reward. The existing random unowned sleeve reward fits that loop well.

Use a compact route such as Apprentice → Journeyman → Expert → Champion. Make the next opponent teach a different problem: playing around Shield, removing an engine, recovering from a board clear, or deciding whether to race a tool user. Difficulty should come from deck quality and decisions. If a special encounter breaks normal rules, show that rule before the match.

A future PvP ladder should use a hidden skill rating for matchmaking and a small visible rank ladder—Stone, Copper, Iron, Gold, Diamond, Netherite would fit the theme. These names are presentation; they do not define the rating maths. Start with one Foundation format, ordinary deck rules and cosmetic seasonal rewards.

**Keep NPC progress and PvP rating separate.** Beating an AI repeatedly must not raise competitive PvP rating. A local single-player world also cannot establish a trustworthy global rank: the host controls saves, cards and commands. A normal mod server can run its own server-local ladder; global competition needs trusted match recording and administration.

Do not split a small player population into several ranked formats, casual queues and rarity-restricted ladders immediately. Direct challenges plus an NPC League are enough for the first playable release. Add ranked once matches are stable, disconnect/reconnect outcomes are defined, and enough people actually want repeat competitive games.

Do not make players grind class Mastery to gain stronger ranked stats. Starter and reward access should let new players build a sensible legal deck. Collection completion is a different achievement from competitive skill.

## Authored NPC decks

Use named, versioned 30-card lists for Card Masters. Randomly selecting cards of the right class is useful for variety later, but does not reliably produce a deck with a curve, a plan or enough targets.

Each deck definition should record its class, card IDs and counts, archetype, mulligan preferences, a few important tactical rules, minimum supported catalogue version and a short player-facing description. At load time validate the 30-card total, class legality, token exclusion, copy limits and referenced IDs. Fail visibly in development and use a known legal fallback in normal play; never silently launch a 24-card deck.

Suggested difficulties:
- **Apprentice:** a simple legal list and straightforward trades. Do not secretly change draws.
- **Journeyman:** a coherent synergy deck and sensible mulligan.
- **Expert:** better sequencing, recognition of lethal, engine removal and planning around board space.
- **Champion:** a polished authored list, good decisions and perhaps one class legendary. More legendaries alone do not make better AI.

For a first implementation, add a small **NPC deck picker in the Workshop**, reusing the card search and validation. Let the author select a class, add quantities, see a cost curve and save a named list. A “test against this deck” command would make balance work much easier.

The four lists below are **legal current-pool test prototypes**, not claimed finished starter decks or proven strong decks. They deliberately contain no legendaries, but some do contain Epics. Each uses 15 distinct cards twice. The small current pool forces considerable shared Neutral support; the 180-card plan should reduce that overlap.

The suggested mulligans and plans below are design recommendations. They were **not added to the current AI** for the automated matches.

### Iron Patrol — Fighter

**Two copies of each:** Iron Sword; Diamond Sword; Potion of Strength; Vindicator; Sweeping Edge; Sharpness; Pillager; Zombie; Skeleton; Drowned; Villager; Librarian; Piglin Brute; Ravager; Enderman.

**Suggested human mulligan:** Keep Iron Sword and a usable early mob; keep an enchantment only with its tool.

**Plan:** Win efficient trades, then threaten with a tool and surviving Illagers. Watch hero Health when trading.

Cost distribution: 1 XP: 2 cards; 2 XP: 6 cards; 3 XP: 12 cards; 4 XP: 4 cards; 5 XP: 4 cards; 7 XP: 2 cards.

### Village Workshop — Builder

**Two copies of each:** Furnace; Dispenser; Beacon; Piston; Bow; Iron Pickaxe; Efficiency; Zombie; Skeleton; Drowned; Villager; Librarian; Shulker; Enderman; Ravager.

**Suggested human mulligan:** Keep Furnace or another early body; do not keep a hand of expensive engines and enchantments.

**Plan:** Protect one useful engine and keep attackers on the board. A full row of buildings is not a winning plan.

Cost distribution: 2 XP: 12 cards; 3 XP: 10 cards; 4 XP: 2 cards; 5 XP: 4 cards; 7 XP: 2 cards.

### Harvest Herd — Farmer

**Two copies of each:** Bee; Bone Meal; Sweet Berry Bush; Bountiful Harvest; Composter; Sniffer; Iron Hoe; Unbreaking; Cow; Shepherd; Zombie; Skeleton; Drowned; Villager; Librarian.

**Suggested human mulligan:** Keep an early body and a practical crop/protection play; avoid keeping several conditional buffs.

**Plan:** Build a mixed board, protect Animals, then Reap when cards are more useful than the crops' spaces.

Cost distribution: 1 XP: 4 cards; 2 XP: 14 cards; 3 XP: 10 cards; 6 XP: 2 cards.

### River School — Fisherman

**Two copies of each:** Fishing Rod; Cod; Salmon; Turtle; Dolphin; Guardian; Conduit; School of Fish; Lure; Zombie; Skeleton; Drowned; Villager; Librarian; Enderman.

**Suggested human mulligan:** Keep cheap Aquatics; keep Dolphin when the rest of the hand can establish a school.

**Plan:** Develop several bodies before a payoff. Leave hand space for catches and board space to play them.

Cost distribution: 1 XP: 2 cards; 2 XP: 10 cards; 3 XP: 12 cards; 4 XP: 2 cards; 5 XP: 4 cards.

The saved runtime catalogue also permits a River School variant replacing both Villagers with **Zombie Fisherman**. That was tested separately; preserve it as a named variant rather than making the base list ambiguous.

## What 1,200 automated matches revealed

I ran the current compiled match rules and current AI against the four lists above. These are headless design probes, not human playtests or a simulation of the proposed 180-card set. No balance values were changed.

### Baseline: 600 games

Each of the six class pairings played 100 seeded games. The match constructor handled the coin toss, deck shuffle and normal opening hands; both sides used the current AI mulligan and play decisions.

| Pair | First named class wins | Second named class wins | Mean individual player turns |
|---|---:|---:|---:|
| Fighter vs Builder | 74 | 26 | 15.32 |
| Fighter vs Farmer | 76 | 24 | 17.86 |
| Fighter vs Fisherman | 74 | 26 | 15.05 |
| Builder vs Farmer | 63 | 37 | 25.65 |
| Builder vs Fisherman | 37 | 63 | 17.99 |
| Farmer vs Fisherman | 26 | 74 | 21.40 |

Across 300 games per class, Fighter won **74.7%**, Fisherman **54.3%**, Builder **42.0%** and Farmer **29.0%**. The player who went first won 337 of 600 games, **56.2%**. Mean match length was 18.88 individual player turns—about 9.4 rounds, not 18.9 rounds. There were no draws or unfinished games.

This is a strong reason to investigate Fighter efficiency and Farmer decision-making. It is not a reason to give Farmer arbitrary raw stats immediately. The AI prefers expensive plays and has card-specific heuristics; its ability to sequence harvesting, buffs, tools and trades is uneven. The lists are also selected prototypes from a tiny catalogue, not an exhaustive search for optimal decks.

### Runtime card check: 300 more games

I loaded the saved runtime catalogue and replaced River School's two Villagers with two Zombie Fishermen. All other deck lists stayed the same.

| Fisherman opponent | Baseline Fisherman wins / 100 | Zombie Fisherman variant wins / 100 |
|---|---:|---:|
| Fighter | 26 | 24 |
| Builder | 63 | 60 |
| Farmer | 74 | 72 |

That changes Fisherman's total from 163/300 to 156/300. There was **no obvious win-rate spike** in this probe. It does not prove the card is weak: the swap changes cost, healing, tribe membership and Librarian synergy, and the current AI may not use the resource well. The repeatable-healing concern is a design question to test, not an observed broken combo.

### Tool counter check: another 300 games

The baseline opposing decks had no Lava Bucket. To check that omission, I replaced the two Librarians in Builder, Farmer and Fisherman with two Lava Buckets and reran each against the unchanged Fighter list using the same corresponding seeds and bundled catalogue.

Fighter still won **74/100 against Builder, 77/100 against Farmer and 75/100 against Fisherman**, compared with 74, 76 and 74 in the baseline. Adding the obvious counter did not resolve the imbalance in these AI games. The swap also removes bodies and Villager support, so it is not a clean measurement of the counter's standalone value.

Do not conclude either “tools are fine because counters exist” or “Lava Bucket is useless.” Inspect when the AI draws and uses it, what threats remain, and what deck space the counter costs. Its current 1-XP destruction of a heavily enchanted tool can still create a severe swing in human play even if this particular substitution did not improve the deck's results.

### What to test next

Use paired human games with both players taking each deck, then swap who starts. Review actual turns where a game becomes difficult to recover, not only the final winner.

Record opening seat, deck version, winner, turn count, dead cards in hand, cards generated, tool damage, wasted healing, crops harvested and whether a board-space lock occurred. For a suspected problem, test one changed number or one changed list at a time.

A matchup outside roughly 40–60% in a meaningful, reasonably sized test should trigger investigation; it is not an automatic pass/fail law. Different decks within one class can have different matchups. Preserve strategic strengths without giving one ordinary deck comfortable wins against everything.

Before declaring an engine too weak, make sure the AI can use it: trade before filling the board when necessary, choose a legal useful buff target, preserve a working tool instead of overwriting it blindly, and Reap when the resulting hand can actually be used. New Workshop effects need generic evaluation rather than a growing list of special cases for card IDs.

## Balance priorities before expanding the catalogue

| Priority | Risk | First useful investigation |
|---|---|---|
| 1 | Fighter wins all three baseline matchups heavily | Review efficient combat bodies, weapon sequencing and real human games before changing class-wide rules |
| 1 | Farmer cannot turn investment into pressure | Check power clarity, AI Reap decisions and an accessible empty-board recovery play |
| 1 | Ranged repeatedly preserves attackers for free | Compare cost and stats with ordinary combat cards; especially review Shulker and the Bow |
| 1 | Generic Neutral packages crowd out class choices | Give each class enough good Commons before filling late-game slots with shared bosses |
| 2 | End-turn engines snowball after paying out immediately | Measure first-turn value separately from later survival value |
| 2 | Generated zero-cost resources erase intended weaknesses | Test Rotten Flesh generation, hand limits and repeated tool triggers |
| 2 | Health growth, healing and invulnerability combine into unbreakable engines | Check all three together, not just each card in isolation |
| 2 | Cheap tool destruction punishes several cards of setup | Compare tool/enchantment investment with counter cost and the counter's dead-matchup burden |
| 2 | Repeated bounce or Totem creates confusing re-entry loops | Verify Spawn, Drop, buffs, status resets and full-board handling consistently |
| 3 | Too many fixed tribe payoffs produce identical decks | Add bridge cards and independently useful bodies; avoid requiring every member of a family |

Do not apply the Neutral reworks in this proposal as a batch and then attribute the result to one card. Keep the current catalogue as a baseline, version candidate changes, and compare deliberately. Raising Piglin Brute's cost, changing Shulker, weakening Warden and increasing Lava Bucket's cost all affect different matchups.

**Protection should have different answers.** Shield redirects ordinary attacks but does not need to stop every item effect. Armour reduces each hit. A Totem gives one fresh return after death. Invulnerability prevents damage temporarily. Do not let all four quietly mean “cannot interact with this card.” The UI needs the clear effects already being added, plus accurate hover explanations.

**At 20 hero Health, damage budgets are tight.** Count plausible tool attacks, surviving board damage and any direct damage in the same sequence. Sprint's board-only first turn is a sensible safeguard. Prefer threats that give the opponent a turn to react over cheap damage from hand that ignores all defensive investment.

**Not every card needs text.** Pig, Horse, Husk and an accessible Ravager teach the value of a body and give buffs something understandable to work with. But do not add twenty interchangeable vanilla cards just to reach 180. A plain card needs a useful cost, stat shape, type or collection role.

Neutral recovery and answers should prevent helpless matchups, while class versions remain more efficient or better integrated. Bread is a safety valve, not a reason for Fighter to become a healing deck. A Neutral block is infrastructure support, not a second full Builder class.

Tokens need the same editorial care as collectible cards. Keep their names, costs and types explicit; exclude them from booster counts and collector-number allocation. A generated Stone Axe is a proposed token, not a ninth collectible tool secretly added to the set.

## A practical Foundation sequence

### First: stabilise the existing game

Review targeting text, status lifetimes, board-space handling and tool interactions. Fix any rules/UI mismatches before adding twenty more effects that depend on them. Keep the new seven-card board limit and make sure previews, summoning effects and AI all respect it.

Establish the four authored baseline decks, record their versions, and improve the AI decisions most likely to distort class testing. Keep a small regression set for armour/thorns, Shield targeting, Sprint, crops, tools, Totem, Frozen and Withered. This proposal does not change any of those rules.

### Second: prove four class packages

Build a compact test pool of roughly 12–15 cards per class plus 30–40 Neutrals. Include the signature tool, at least one enchantment, an early curve, interaction and a way to finish. Use the class seeds and passive bank to choose the missing roles.

Get two recognisable strategies per class working with overlapping cards. A legendary may improve one of them; neither should collapse without it. Cut designs that only exist to fill a rarity column.

### Third: fill the 180-card budget

Expand to 30 per class and 60 Neutral only after the smaller pool works. Add the remaining class legendary, late-game options and sidegrade choices around established needs. Check the cost, kind and rarity budgets together.

Aim for most Commons to have zero or one short rule, Rares to provide useful glue, and Epics to offer more specialised builds. Legendary status should bring a memorable identity and moment, not a requirement for every competitive list.

Preserve all existing stable card IDs and collector numbers. Add new definitions without reordering the old collectible sequence. Rarity and class binder pages can sort differently without changing a card's number.

### Fourth: make the collection rewarding

Ship the all-Common starters, deterministic class Mastery rewards, NPC deck identities and the sleeve-unlock loop. Use card art, display stands and inspectable provenance to make a collection feel personal.

Only then decide whether the audience needs ranked seasons, a biome expansion, a larger second legendary cycle or a richer crafting system. Expansion design will be easier when Foundation leaves those spaces open.

**The intended first-set identity:** a Fighter fights for tempo with tools and a raid; a Builder gives up space now for useful structures later; a Farmer grows a board and chooses when to harvest it; a Fisherman gathers small catches and turns them into a school. Neutral cards connect those plans and supply basic answers. That is enough strategic variety for Foundation without another resource bar or a dozen new keywords.

## Evidence and reproducibility

This proposal was prepared against the local Craftbound project on 2 November 2026. The bundled catalogue has 53 collectible cards; the development runtime catalogue has 54, including Zombie Fisherman. The proposed 180-card allocation and candidate numbers have **not** been applied to either catalogue.

Project sources reviewed include the bundled and runtime `cards.json`, the card definition/catalogue model, `Keyword`, `DeckRules`, `Match`, `MatchAI`, hero powers, the Card Master deck construction and reward flow, and the existing Workshop and binder work. Current-code observations are separated from new engine hooks in the passive bank.

The three probe programs use the actual compiled match engine and AI. Pair indexes are Fighter 0, Builder 1, Farmer 2, Fisherman 3; each pairing uses seeds `20261102000 + a*10000 + b*100 + n`, for `n = 0..99`. The runtime probe selects pairings with Fisherman and loads the saved runtime catalogue; the counter probe selects Fighter pairings and changes the opposing lists. Each run stops at match completion or a guard of 200 individual player turns / 3,000 AI steps. None reached a guard.

The test harness clears pending presentation events after each AI step, without changing game state. It does not render or judge animations. Baseline and counter probes use the bundled catalogue; the runtime probe uses the saved development catalogue. Source copies and raw outputs accompany the project document so the setup can be inspected and rerun against a pinned build.

The official Minecraft and Blizzard sources linked beside the relevant design discussion support the composting, fishing, anvil and Rush comparisons. Proposed Craftbound variants, numerical balance and progression structures are design recommendations, not claims about vanilla Minecraft or proof of human competitive balance.

No gameplay code, card definitions, collector numbers, NPC decks in the running game, or rewards were changed for this proposal.
