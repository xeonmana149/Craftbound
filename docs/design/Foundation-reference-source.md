# Start here
This is a working design reference for rebuilding Craftbound's Foundation set. Clear card text is a release requirement, not finishing polish. A player should know the target, timing, result and duration without guessing what the author meant.

CURRENT means verified in the local Craftbound source on 2 November 2026. PROPOSAL means a design option, not a promise that the Workshop can implement it. VANILLA INDEX means an exact identifier extracted from the installed Minecraft 26.3 registries or game data. A Minecraft object does not automatically become a Craftbound card or effect.

Use the Workshop's Design Reference tab while editing. Search for a class, keyword, object or mechanic. The generated text is the authority for what an effect configuration describes; custom prose cannot create new rules. Use the current-card appendix as a snapshot, not a replacement for the live editor.

## What this reference covers
The appendices enumerate every registered vanilla item, block, entity type, status effect and potion definition in this installed version, plus vanilla enchantments and biomes. Technical entities, air and unobtainable blocks remain in the index for completeness. Block states, individual enchanted-item combinations, every possible mob appearance and every crafting recipe are not separate registry entries. The mechanics chapters are a broad design taxonomy, not a claim to enumerate every emergent Minecraft interaction.

## Decisions for this rebuild
- Dismantle is removed from gameplay. A block must justify its board slot through ongoing value, a passive or a supported payoff. Harvest and Mine remain distinct actions.
- Ranged attacks can reach blocks behind enemy mobs. Shield takes priority. Both sides deal combat damage normally.
- Keep card identity separate from rarity and cosmetic finish. An Enchanted collectible is cosmetic; an Enchantment card adds gameplay effects to a tool.
- Preserve existing stable IDs and collector order. Retire old cards through the supported deletion flag; append new definitions. Do not physically reorder or strip the original entries from JSON.
- Do not add all proposed keywords at once. Foundation needs a small teachable vocabulary and functioning decks before novelty.

# Wording standard
CURRENT IMPLEMENTATION: Generated Action and Enchantment effects no longer begin with redundant Play. Board-card play effects retain Spawn. End-turn and attack triggers identify the timing more clearly. Draw, Give and Summon use singular/plural wording. Legacy timing labels and generated timing phrases are bold in card rendering; bold text alone does not make something a hover keyword.

## The four questions every rule must answer
Who or what is affected? When does it happen? What changes and by how much? When does that change end? Add a condition, choice or frequency limit only when the game actually enforces it. Never rely on an illustration to communicate a restriction.

| Avoid | Prefer | Why |
| Play: Draw 1 card(s). | Draw 1 card. | An Action already resolves when played. |
| End turn: heal 2. | At the end of your turn: Restore 2 Health to your hero. | States whose turn and the recipient. |
| After attack: summon wolf. | After your hero attacks: Summon Wolf Pup. | Tools trigger on the hero's attack. |
| Give a friendly mob +1/+1, while blocks are legal targets. | Give a friendly mob +1/+1, or a friendly block +2 Health. | Separate the two target branches. |
| Freeze an enemy. | Freeze an enemy mob or hero. | A broad word must not conceal target restrictions. |
| Gets stronger near fish. | After you play another Aquatic mob, this gains +1 Attack until the end of the turn. | Says what triggers and when the bonus expires. |
| Has a Totem. | Give a friendly mob or block a Totem of Undying. | Use the named effect consistently and explain it on inspect. |
| Your units have +1 Attack. | Your other Illager mobs have +1 Attack. | Other excludes the source; Illager limits recipients. |
| Draw a random card. | Draw 1 card. / Add a random card from [Cod, Salmon] to your hand. | Drawing your deck differs from generating a card. |
| +2 Attack and +0 Durability. | +2 Attack. | Omit attributes that do not change. |

## Vocabulary and grammar
Use Attack, Health, Durability and XP consistently. Use mob for an attacking board creature and block for a non-attacking board permanent. Use hero for a player avatar in the match, not player when the effect could be confused with the Minecraft character outside the GUI. Say mob or block when both are legal; say mob or hero when blocks are excluded. Avoid character unless its meaning is defined identically in every tooltip.

Play means spending a card from hand. Summon means creating a board entity; it does not imply Spawn triggers. Draw means taking a card from your deck. Add means creating or receiving a card in hand. Return means moving an existing board card to its owner's hand; current tokens disappear instead. Destroy, remove, harvest and mine are not interchangeable.

For a permanent stat buff, write Give a friendly mob +1/+1. For a temporary effect, include until the end of the turn. Do not say base stats return: temporary Attack must expire while unrelated permanent buffs remain. A maximum-Health increase and healing are different effects; +2 Health increases current and maximum Health in the current implementation.

## Labels are not all keywords
Shield, Sprint, Ranged, Ignore Shield, Spawn, Drop and Cleanse currently have keyword tooltip support. Block is a card type, not a player-facing keyword. After attacking and At the end of your turn are timing phrases, not extra mechanics to memorize. Grazer, Battle-hardened and Schooling are passive implementation choices; their full rules are currently written out. Harvest and Mine are actions/events, not generic substitutes for death.

For Foundation, omit Play from one-shot cards; retain Spawn and Drop because their timing matters. Keep timing phrases bold for scanning. Do not replace a clear sentence with a new keyword unless enough cards repeat the same rule to justify the teaching cost.

## Length and layout budget
Aim for one main idea, one sentence or two short clauses, and roughly 12-30 rules words on ordinary cards. These are design targets, not automatic truncation limits. Longer text is acceptable when necessary to specify targets correctly. If a common card needs three unrelated effects, simplify its design before shrinking its font. Read it at actual collection-card size, not only in the large inspect panel.

Never shorten by omitting enemy, other, random, until, or once per turn. Those words change gameplay. Remove filler instead: in order to, will then, a total of, the amount of and unnecessary Play prefixes.

## Wording review before Save
- Compare the sentence with the actual target selector, Board kind, Type, event scope and self-exclusion settings.
- Check whether a trigger observes played cards, summoned mobs, real healing or attempts that do nothing.
- For multiple effects, check execution order and whether all chosen effects share one legal target.
- For repeated effects, state the limit only if the engine enforces it. A sentence saying once per turn does not create a limit.
- Check one versus many: Draw 1 card; Draw 2 cards. Use copies of a card name instead of inventing a plural for its title.
- Inspect generated tokens and attached tool text too. The parent card alone is not the complete experience.
- Keep a custom description only when it describes exactly the same rules as the structured effects.

# Current rules and terminology
CURRENT: Normal matches start at 30 hero Health, use 30-card decks, allow seven board slots per side shared by mobs and blocks, and allow ten cards in hand. Regular deck limits are two copies per identity and one for Legendary cards, counting normal and Enchanted together. XP increases and refills on your turns up to the usual ten maximum; temporary XP effects can provide additional spendable XP. PvP turns have a 75-second timer, with the fuse during the final 15 seconds. Card Master games are untimed.

## Card kind versus mob type
Player-facing kind should be Mob, Block, Action, Tool or Enchantment. Mob type should describe an affiliation or family such as Undead or Illager. These solve different questions. Internally, a Block currently uses kind MOB plus the BLOCK keyword. The editor presents a block choice, but changing the underlying kind of an established ID is protected. Treat this as a storage detail, not wording to expose to players.

The collection calls these categories Card Types and families Mob Types. Both filters stack with Keywords, XP, rarity, search and Normal/Enchanted selection. A Block cannot attack or ordinarily retaliate; Attack buffs do not give it usable Attack. Triggered damage such as Thorns can still make a Cactus dangerous. Design blocks to remain useful after their initial effect.

CURRENT mob-type field values: Animal, Undead, Arthropod, Villager, Illager, Ender, Nether, Golem, Slime, Crop, Aquatic, Ore and Tameable. Crop and Ore are resource families usually placed on blocks. A card currently has one type field, so a Drowned cannot simultaneously be Undead and Aquatic through this field. Do not write dual-type synergies as if supported. Multi-type tags are a possible future feature.

## Core keyword glossary
| Term | Current behavior | Design caution |
| Shield | Only mobs can have Shield. Attacks must target a Shield mob first unless the attacker has Ignore Shield. | Does not redirect every Action effect. Stacking several durable Shield cards can stall games. |
| Sprint | A newly played/summoned mob can attack enemy board cards immediately, but not the enemy hero that turn. | Its later hero attacks follow normal rules. Avoid efficient removal attached to an oversized body. |
| Ranged | Can attack blocks behind the front row. Shield still takes priority. | Ranged attackers and defenders trade damage normally, including triggered effects. |
| Ignore Shield | This attacker may attack any enemy despite Shield. On a tool, applies to its hero. Can also be granted to a mob. | Does not bypass Frozen, first-turn/Sprint restrictions, retaliation or damage protection. No extra attacks. |
| Spawn | A board card's effect when played from hand. | Ordinary summon, transform and Totem revival do not imply Spawn. |
| Drop | Resolves when the board card dies. Mining an Ore also explicitly runs its Drop. | Returning, transforming and harvesting do not trigger Drop. |
| Cleanse | Removes all applied buffs and debuffs from the target. | Keeps printed abilities, equipped tools and ongoing auras. Does not heal damage. |

## Status effects and attached effects
| Effect | Current behavior | Wording implication |
| Frozen | Prevents attacks, including hero tool attacks. Start/end-turn bookkeeping makes the affected owner's attack opportunity be skipped. | Do not say one second or a real-time duration. Spell and power use are not generally frozen. |
| Withered | Takes 1 damage at the start of each owner's turn until removed. | The authoring rules exclude heroes. Use mob or eligible board target, not any enemy hero. |
| Invulnerable | Prevents damage until the start of the owner's next turn. | Does not necessarily prevent returning, transforming or mining. |
| Totem of Undying | On death, after Drop, summon a fresh copy of the card if a slot is available. | A flag, not a stack of extra lives. Buffs and the consumed Totem do not carry over to the fresh copy. |
| Negative Attack | A debuff reduces effective Attack and can be cleansed. | Say Reduce ... Attack or Give ... -N Attack; distinguish it from damage. |
| Well Fed | Healing beyond missing Health increases maximum Health and also fills that increase. | At full Health it can still have a real benefit. Ordinary Heal at full Health does nothing. |
| Granted keyword | Shield, Sprint, Ranged or Ignore Shield can be added to eligible board targets. | Ensure the target kind and existing status make the effect useful. |

## Passives already supported
| Passive | Current rule | Best use / risk |
| Armor | Reduce each incoming damage instance by the configured amount, down to zero. | Strong against repeated small hits; distinguish from temporary extra Health. |
| Type-filtered aura | Other friendly mobs of the selected type have extra Attack and/or Health while the source remains. Choose Aura: Attack and Health and set separate bonuses; 0 disables either stat. | Excludes itself and blocks. Health auras stack, affect later summons, and raise current and maximum Health. Losing an aura lowers the maximum and caps current Health; it does not deal damage. |
| Grazer | At the end of your turn, if you control a Crop, heal this mob by the configured amount. | Links animals with agriculture without a new resource. Must have enough Crop access. |
| Battle-hardened | After surviving actual damage, gain Attack, at most once per global turn. | Fully prevented damage is not a trigger. Cheap repeat healing can make it snowball. |
| Schooling | After you play another Aquatic mob, this gains Attack until the current turn ends. | Playing is not summoning. The bonus is on the listener, not automatically the newly played fish. |

# Effect maker capability map
UPGRADE_TOOL (Improve equipped tool) lets blocks such as Anvil add Attack and/or Durability to the controlling hero's equipped tool. Either bonus may be 0; at least one must be positive. It can run on Spawn, End turn or Activate and does nothing when no tool is equipped. It does not attach an Enchantment or its triggers. ENCHANT_TOOL remains for Enchantment cards.

Random targets: Random enemy and Random friendly choose one eligible character on that side, including the hero only when supported by the action and filters. Random block, Random mob and Random hero choose from either side. Mob and block choices enforce their board kind; mob-type filters exclude heroes. No eligible candidates means no effect. Random friendly mob / block is the board-only friendly option.

For an Ender Chest that generates class cards, choose When: End turn, Action: Add random card from your class, Amount: 1. The text is “At the end of your turn: Add a random card from your class to your hand.” GIVE_RANDOM_CLASS uses the controlling player's class, even on a Neutral chest. It creates a normal collectible, excluding Neutral cards, tokens and deleted entries. It does not draw from the deck. An empty class pool does nothing; a full hand burns the generated card.

CURRENT: Effects resolve in their authored order. Choose a trigger, an action, a target and appropriate filters. Multiple fields that sound similar can refer to different things: eventType filters what caused the event; type filters the recipient; countType filters what is counted.

| Trigger | Meaning | Example wording |
| PLAY | Card played from hand; displayed as Spawn for board cards, omitted on one-shot Actions. | Spawn: Draw 1 card. |
| DROP | Board death; also explicitly called by Mine. | Drop: Add Coal to your hand. |
| END_TURN | Owner's end of turn while the source remains relevant. | At the end of your turn: Restore 1 Health to your hero. |
| AFTER_ATTACK | After this mob attacks, or after the hero attacks with the tool. | After your hero attacks: Draw 1 card. |
| DAMAGE_DEALT | A qualifying damage event by the source; not prevented zero damage. Choose Damaged mob or hero to include hero attack hits; the legacy board-only target excludes heroes. Mob retaliation can trigger this too. | After this deals damage: Freeze the damaged mob or hero. |
| ATTACKED | When the source is attacked; useful for explicit thorn damage. | When attacked: Deal 1 damage to the attacker. |
| HARVEST | Crop reaped through the harvest path. Separate from death. | Harvest: Add Wheat to your hand. |
| ON_KILL | A qualifying kill credited to the source or tool. | After your tool kills a mob or block: Draw 1 card. |
| AFTER_SURVIVING_KILL | A non-block mob kills its target with its own attack and remains alive on the board after retaliation, thorns and Death effects. Excludes defending, hero targets and kills caused by other abilities. | After this attacks and destroys a mob or block, if it survives: Deal 2 damage to the enemy hero. |
| CARD_ADDED | A card successfully enters a hand, including draws and generated cards. | After a card enters your hand: ... |
| ITEM_PLAYED | An Action is played. Do not assume Enchantment or Tool cards are Actions. | After you play an Action: ... |
| MINED | An Ore is consumed by Mine. | After you mine an Ore: ... |
| HEALED | Actual healing occurs, including supported Well Fed growth. | After this is healed: ... |
| DISMANTLE | RETIRED. Old schema/packet identity is reserved; no gameplay action remains. | Do not author new cards with this trigger. |

## Actions already available
Buff now includes an optional Also grant keyword choice: Shield, Sprint, Ranged or Ignore Shield. Example: Chosen friendly / Mobs only / Illager / +2 Attack / +2 Health / Sprint generates “Give a friendly Illager mob +2/+2 and Sprint.” Both parts affect the same target, including when chosen randomly. Use the Illager Rally template as a starting point. Other combinations still use separate ordered effects; chosen Play effects reuse the player's selected target. For-each scales stats, while the keyword is granted once; a zero count skips the whole effect.

For Looting, choose New from template → Looting. It is a 2-XP Rare Fighter Enchantment with one effect: On kill / Draw / Self / 1 card. Triggered abilities attach automatically; no dummy stat upgrade is required. The text reads “Your tool gains: After this destroys a mob or block, draw 1 card.” It requires an equipped tool and can draw from a kill on the tool's last durability. Replacing or breaking the tool removes its attached ability. Templates create editable drafts, never add cards to the running set automatically.

BUFF changes board stats; DAMAGE deals damage; HEAL restores missing Health; WELL_FED permits overhealing; DRAW takes from the deck; GAIN_XP provides spendable XP this turn; SUMMON creates a specified board card; GIVE adds a specified card to hand; GIVE_RANDOM samples a fixed authored card list; BOUNCE returns a board card; TOTEM grants the resurrection flag; INVULNERABLE prevents damage temporarily; FREEZE and WITHER apply their statuses; TOOL_DAMAGE deals damage based on equipped tool Attack; DESTROY_TOOL removes the enemy tool; ENCHANT_TOOL modifies/marks the equipped tool; CLEANSE removes applied buffs and debuffs; MINE consumes an Ore and pays its Drop; GRANT_KEYWORD adds a supported keyword; TRANSFORM replaces a board card with a new form.

## Targeting and event details
Chosen friendly/enemy/any board targets are narrowed with Board kind: Mob, Block or Both. A chosen character target includes either hero, with its board portion narrowed separately. Choose Mob plus the character target for a food card that heals any mob or either hero but not blocks. Chef's Feed instead heals any mob, block or hero. Random enemy can include the enemy hero; it is not synonymous with random enemy mob.

SELF means the source board card or the appropriate hero for tool effects, subject to action restrictions. EVENT refers to the triggering target where supported. Observed event scopes choose friendly, enemy, any or self; self is useful for healing listeners. Do not use self for events with no matching board/hero subject, such as a card entering hand, and expect it to fire. The current validator can accept some configurations that have no useful trigger.

For-each fields multiply supported numeric amounts by a live board count. Count kind, type, side and excluding the source are independent settings. It is not a general expression language: arbitrary comparisons, conditional branches, limits and variable card references are not supported just because they can be written in text. Inspect each action before attaching a count; tool enchant stat changes and tool-derived damage do not automatically become fully general multiplied expressions.

## Important boundaries
- Generic Choose one / discover-three is a proposal. Miner's two-option hero power is bespoke and does not prove a generic card choice UI exists.
- Tool Enchantments attach non-Play triggers automatically. ENCHANT_TOOL is optional for an ability-only enchantment; use it when also granting stats. Legacy explicit 0/0 attachments still work. Replacing/breaking the tool ends its attached effects.
- Transform creates a fresh form, retains the identity needed for its board slot, and does not refresh a spent attack. New Sprint may affect readiness. It is not a buff preserving all statuses.
- Draw and Add can overflow the ten-card hand. Repeated generation engines need limits and hand-pressure testing.
- There are loop guards. They are emergency protection, not permission to design infinite automatic loops.
- A custom sentence cannot implement a once-per-turn cap, a new target restriction, a secret condition or a player choice.

# Blocks after Dismantle
DESIGN POLICY: A block should create an interesting decision after it is played. Removing Dismantle makes this a card-design obligation. Paying XP to remove your own weak card was covering up blocks that had already finished their job.

## Four useful block shapes
| Shape | Example concept | Current support |
| Ongoing engine | Bookshelf: At the end of your turn, draw 1 card. | END_TURN + DRAW. Very strong; cost and Health need testing. |
| Passive structure | Banner: Your other Illager mobs have +1 Attack. | Typed Attack aura; blocks can be aura sources. |
| Defensive hazard | Cactus: When attacked, deal 1 damage to the attacker. | ATTACKED + DAMAGE; no ordinary block retaliation needed. |
| Consumable resource | Ore pays Drop when mined; Crop pays Harvest when reaped. | Mine and Harvest exist, with distinct trigger paths. |

## Bookshelf choices
PROPOSAL A, simplest: remove the one-shot draw-two identity and make Bookshelf a modest recurring card source. Keep it fragile enough that the opponent can interact. PROPOSAL B: Spawn draws one card and an ongoing narrow benefit rewards a defined deck. PROPOSAL C, future: two book charges, then remove itself. Charges and generic self-expiry need code; do not imply they already exist.

If a block is only a one-time effect with no ongoing purpose, make that concept an Action instead. If preserving its physical block identity matters, pair the entry effect with a relevant passive or resource payoff. Do not make every block Shield solely to force the opponent to clear it.

## Block review checklist
What does this do on my next turn? Why would an opponent spend an attack or removal on it? Can I use its payoff myself? How often does it strand a board slot? Does its Health produce a real interaction window? Could a deck fill all seven slots and become unable to progress? Can two copies compound into an oppressive engine? Remove or redesign any block with no convincing answers.

# Class identities
PROPOSAL FRAMEWORK: Give each class two primary strengths, one supporting strength and at least two weaknesses it cannot erase cheaply. Neutral cards should provide basic playability without replacing a class's distinctive payoff. Classes share core rules; their incentives, resource flow and efficient answers should differ.

| Class | Primary plan | Distinct resource / constraint | Deliberate weakness |
| Fighter | Combat trades and tools | Tool durability, damaged survivors, kill rewards | Limited broad healing and passive long-game engines |
| Builder | Defensive board engines | Board space and vulnerable structures | Slow conversion of defense into lethal damage |
| Farmer | Crop growth and harvest | Timing harvest against keeping Crops in play | Setup can be disrupted; weak instant removal |
| Fisherman | Catch generation and Aquatic tempo | Hand additions and sequencing fish | Weak unconditional hard removal; hand overflow |
| Alchemist | Potions and controlled bursts | Action sequencing, preparation and finite resources | Fragile board, weaker sustained tools |
| Miner | Invest in Ore, convert it into tools/value | Board investment followed by mining | Delayed payoff; resource hands can lack interaction |
| Chef | Sustain a surviving board | Real healing, Health growth and food | Must establish bodies; limited burst damage |
| Tamer | Companions and board development | Small bodies, type support and upgrades | Board clears; weak independent face burn |
| Redstoner | Activate contraptions | XP and a surviving Block with an Activate ability | Weak without a machine; vulnerable to board removal |

## Nine classes change the set arithmetic
The catalogue still records a Foundation target of 180. The earlier 90 Neutral + three classes x 30 plan does not cover nine classes. Nine x 30 + 90 Neutral would be 360 collectible cards. This guide does not silently change the target. One possible 180-card first pass is 45 Neutral + 15 per class; expand only after each class has a coherent playable deck. Tokens are additional definitions, not collectible slots.

# Fighter
CURRENT POWER: Craft costs 2 XP, once on your turn. Equip a Wooden Sword with 1 Attack and 1 Durability, replacing any equipped tool. Unused durability persists across turns. It does not grant another attack or bypass Frozen. Ordinary retaliation still damages your hero.

DESIGN IDENTITY: Win board trades, then turn surviving attackers and remaining tool durability into pressure. The question should be which trade advances my plan, not how much unavoidable face damage can I chain.

Strengths: efficient single-target combat, useful tools, on-kill enchantments and resilient attackers. Supporting theme: Illager formations and Battle-hardened survivors. Weaknesses: broad recovery, endless hand generation and efficient answers to everything without attacking. Hero Health is a combat resource; do not give Fighter every healing tool it needs for free.

## Suggested packages
- Tool combat: two dependable low/mid-cost tools, one premium tool, a small number of enchantments, and mobs that still work when no tool is drawn.
- Illager pressure: early Illagers, one typed aura and a payoff for maintaining the board. Keep Ranged Pillagers fragile.
- Survivors: Battle-hardened on a modest body plus carefully priced protection. Avoid repeating free self-damage/heal loops.

## Card concepts, not new shipped cards
Looting: attach an on-kill card-generation effect to a tool. Fire Aspect: add a modest after-damage effect, with explicit targets. Weapon Rack: choose one of three tools is attractive, but requires generic choice support. A current-code alternative adds a random tool from a short list; this is a different experience and must say random. Shield Bash could reward an existing defensive board, but a count based on keyword rather than type needs additional filtering support.

Legendary proposals: Raid Captain, an Illager aura leader with a clear vulnerable body; Veteran Weaponsmith, a powerful tool/enchantment payoff that does not require a new resource. Do not make a class playable only after drawing its Legendary.

# Builder
CURRENT POWER: Build costs 2 XP, once on your turn, and summons a 0/2 Cobblestone Wall. At the end of your turn it gives a random friendly mob +1 Health. Requires a board slot.

DESIGN IDENTITY: Assemble structures that protect a modest engine and convert time into a board advantage. Builder should care about what occupies each of its seven slots more than any other class.

Strengths: defensive blocks, Health support, Shield granting and persistent auras. Supporting theme: Golems, redstone, repairs and Villager infrastructure. Weaknesses: burst damage and cheap repeated card generation. Its payoffs should be interactable; a collection of invulnerable engines is frustrating rather than clever.

## Suggested packages
- Fortifications: a few Shield blocks, one repair effect and mobs that supply the actual win condition.
- Workshop engines: one or two structures with different jobs, not five versions of draw every turn.
- Golem support: resilient neutral bodies plus a class payoff; remember one current type per card.

## Card concepts
Bookshelf becomes ongoing support or an Action-like one-shot concept. Redstone Torch can be an Attack aura source. Dispenser is a visible end-turn damage engine. Piston is board disruption through returning a target. Scaffolding as a temporary discounted slot or replacement structure needs new rules; keep it as a proposal. Repairing blocks should increase Health or heal, not grant useless Attack.

Legendary proposals: Master Architect, a large but removable structure-support mob; Ancient Golem, a board-stabilizing threat whose value is visible and finite. Avoid a Legendary that makes all friendly blocks untargetable or permanently invulnerable.

# Farmer
CURRENT POWER: Sow costs 2 XP, once on your turn. Summon a 0/2 Crop. At the start of your next turn, destroy it and gain +1 maximum XP, up to the normal cap of 10. It must survive until that turn. This replaces the old Sow/Reap toggle. Authored Harvest effects remain available separately.

DESIGN IDENTITY: Choose when to cash in the farm. Crops are useful while growing, but harvest provides the payoff. Animals and Villagers support the engine rather than replacing it.

Strengths: sustained board growth, Crop generation and delayed card advantage. Supporting theme: Grazer Animals and targeted buffs. Weaknesses: immediate large removal and burst face damage. The opponent should be able to damage the farm before it pays out.

## Suggested packages
- Crop cycle: cheap Crop access, one meaningful Harvest token, one payoff for controlling Crops and one for actually reaping them.
- Grazing herd: Animals recover while a Crop remains, creating tension with harvesting all Crops.
- Hoe combat: a tool plants Crops on an appropriate event. Verify slots before rewarding every kill with another block.

## Card concepts
Iron Hoe: After your tool kills a mob or block, summon Wheat. Seed Store: a modest Crop generator. Composter: buff an actual mob, not a random block. Bone Meal: explicit mob +1/+1 or block +2 Health branches. A Crop with an ongoing benefit plus Harvest reward creates a real timing decision. A growth-stage mechanic could be appealing later; current generic multi-turn counters are not provided.

Legendary proposals: Ancient Sniffer, a finite burst of distinctive Crop resources; Master Farmer, a visible farm engine with a fair removal window. Do not double all Harvest effects indiscriminately before testing draw loops and full hands.

# Fisherman
CURRENT POWER: What a Catch! costs 2 XP, once on your turn. Summon a random Aquatic token: Cod (1/3, no ability), Salmon (2/1, no ability), or Pufferfish (1/1). Each has an equal chance; requires a free board slot. Pufferfish's Death effect gives the enemy mob that killed it -2 Attack until the end of that mob's controller's next turn. A spell or hero kill has no mob to weaken.

DESIGN IDENTITY: Establish Aquatic bodies, then sequence catches and buffs for a tempo turn. The power summons directly; it does not count as playing a card or adding one to hand. Fishing tools and authored cards can support the separate hand-generation package.

Strengths: predictable generation, Aquatic support and temporary Attack bursts. Supporting theme: fishing tools and fixed catch tables. Weaknesses: reliable hard removal and durable solo threats. Avoid making every generated catch also draw from the deck; the class should pay something for its resource flow.

## Suggested packages
- Schooling: modest Aquatic listeners plus enough cheap fish to activate them. Playing an Aquatic from hand matters; mass summon is different.
- Catch engine: a card-added listener plus a fishing tool that generates from a small pool. Include the actual pool in inspect/reference.
- Coastal control: a few defensive Aquatics and carefully priced Freeze, without copying Alchemist's whole removal kit.

## Card concepts
Luck of the Sea: attach a fixed-list random Give trigger to a rod. Fishing Net: a finite batch of small catches. Shoal Leader: Schooling on a fragile body. Treasure Catch: a rare generated utility item, not an unrestricted random collectible. Guardian can be a ranged defender with modest Attack and a meaningful weakness; do not automatically combine Ranged, Shield and strong control.

Legendary proposals: Old Sea Angler, a catch-table payoff; Elder Guardian, an expensive Aquatic stabilizer with limited control. Card-added triggers can observe both drawing and generation; if the design means catches only, the engine needs a more specific event filter or a deliberate type restriction.

# Alchemist
CURRENT POWER: Throw Potion costs 2 XP, once on your turn. Deal 1 damage to any mob, block or hero. This is a direct hero power, not an Action played from hand, so it does not trigger Action-played listeners.

DESIGN IDENTITY: Prepare and combine potions to solve a board problem. The exciting turn is a sequence of finite Actions, not a permanent freeze lock.

Strengths: direct board damage, negative Attack, Freeze and Action-played synergies. Supporting theme: fragile Witch / brewing engines and selective mobility through Sprint. Weaknesses: durable minion curves, efficient weapon combat and large repeatable healing.

## Suggested packages
- Potion control: small targeted damage, one area-damage option, one debuff and a limited resource engine.
- Action sequencing: a fragile listener rewards playing Actions. Give it a modest payoff and test the 0-XP potion chain.
- Brewing support: a block that continues doing something after Spawn. Hand overflow and the cost of occupying a slot are real constraints.

## Card concepts
Splash Harming: damage enemy mobs and blocks; explicitly state hero exclusion. Potion of Weakness: negative Attack that Cleanse can remove. Potion of Swiftness: give a mob Sprint. Brewing Stand: ongoing potion generation with carefully chosen Health. Lingering Cloud as a timed area effect would need duration/zone support; use an ordinary block engine only if that difference is acceptable.

Legendary proposals: Master Brewer, a finite potion choice/catch payoff (choice needs code); Coven Witch, an Action-played engine with a hard, implemented cap if the design calls for one. Do not rely on custom text to enforce once per turn.

# Miner
CURRENT POWER: Prospect costs 2 XP, once on your turn. Add an Iron Ore to your hand: a 1-XP Action that gives a friendly mob +1/+1. Requires room in hand. This replaces the old mine-or-pickaxe choice. Authored Mine effects remain available separately; the generated Iron Ore is an Action, not an Ore Block.

DESIGN IDENTITY: Put value into visible Ore, then convert it into spendable resources or improved tools. This is investment and conversion; Builder is persistence and defense.

Strengths: Ore payoffs, tools, mining events and resource conversion. Supporting theme: for-each Ore amounts and tool durability. Weaknesses: immediate pressure, resource-only hands and removal before conversion. Do not make the class require an Ore, a pickaxe and an enchantment just to take an ordinary turn.

## Suggested packages
- Mining loop: enough playable tools and Mine access to support the Ore count. Test hands with Ore but no tool, and tool but no Ore.
- Refinement: Coal as temporary XP, Gold as cards, Iron/Diamond as tool improvements. Each resource should have a distinct use.
- Investment payoff: a moderate reward when you mine, plus one payoff for choosing to keep Ore on the board.

## Card concepts
Stone Pickaxe: after attacking, mine an eligible Ore through its configured target behavior. Ore-rich effects count Ore blocks, not arbitrary board cards. Furnace / blast-furnace ideas can convert resources over time; a generic inventory crafting recipe is not already a match mechanic. Silk Touch returning an Ore instead of paying Drop is different from Mine and may need a custom rule.

Legendary proposals: Deep Delver, a strong but finite mining payoff; Ancient Forge, a tool-support engine with an exposed board slot. Keep Legendary rewards from simply generating more Ore forever. No additional Miner cards were added as part of this reference work.

# Chef
CURRENT POWER: Feed costs 2 XP, once on your turn, and restores 2 Health to any mob, block or hero. Ordinary full-Health healing has no effect.

DESIGN IDENTITY: Keep an established board alive, then turn real healing into useful growth. Chef rewards careful trades and feeding survivors, not endless passive hero healing.

Strengths: efficient healing, Health buffs and real-heal listeners. Supporting theme: Villagers, food Actions and Well Fed. Weaknesses: burst removal and rebuilding an empty board. Health by itself should not become a universal win condition.

## Suggested packages
- Sustaining board: sturdy early mobs with modest Attack, food and one healing payoff.
- Feast: limited Well Fed effects let healing matter at full Health. Price their permanent growth separately from ordinary healing.
- Village kitchen: Villager support with distinct jobs, avoiding every Villager merely granting more Health.

## Card concepts
Cooked Porkchop: restore a clear amount to a mob or hero. Milk Bucket: Cleanse an explicitly eligible target. Village Cook: a simple heal-on-Spawn body. Hearty Stew: a larger finite recovery effect. An after-healed listener gains modest Attack or generates a food card, but test loops if generated food heals the listener again.

Legendary proposals: Village Head Chef, a finite feast payoff; Mooshroom Matron, a food engine tied to board presence. Never make required basic healing inaccessible behind a Legendary. Chef is not obliged to copy every Priest mechanic; theft and resurrection would add identities the current class does not need.

# Redstoner
CURRENT POWER: Power Up costs 2 XP, once on your turn. Trigger a friendly Block's Activate ability. Only living friendly Blocks with an authored Activate effect can be selected. A plain Wall is not a valid target.

DESIGN IDENTITY: Build machines, then spend XP to choose which one works now. Builder keeps structures alive; Redstoner turns a surviving contraption into a controlled payoff.

Strengths: flexible repeatable effects, mechanical sequencing and choosing the right machine for the turn. Weaknesses: setup costs, limited board slots and losing an engine before activation. Keep machine payoffs modest enough that repeated activations stay fair.

PROPOSALS: a Dispenser with Activate damage to a random enemy; a Hopper with Activate card generation; a Comparator rewarding a specific board state. These are design ideas, not new collectible cards. In Workshop, choose Block and the Activate trigger, then an automatic, random or self-targeted effect. A second manual target is not supported by this power.

# Tamer
CURRENT POWER: Call Pup costs 2 XP, once on your turn, and summons a 1/1 Wolf Pup. Requires a board slot.

DESIGN IDENTITY: Establish companions, protect a few of them, and upgrade a small board into meaningful attackers. Tamer's identity is caring for persistent allies, not treating every creature as disposable face damage.

Strengths: companion generation, Tameable auras and transformation/upgrades. Supporting theme: Animals, bones, fish and saddles. Weaknesses: board clears, full-board congestion and limited independent damage from hand.

## Suggested packages
- Pack: pups plus a modest Tameable Attack aura. The aura is vulnerable and does not buff itself.
- Training: targeted buffs and a pup-to-wolf transform. Transform is a fresh form, not an extra attack reset.
- Utility companions: Cat, Parrot or Allay concepts supply different support roles. Their real Minecraft labels do not force all of them into the same current type field.

## Card concepts
Pack Leader: your other Tameable mobs have +1 Attack. Bone: a small targeted companion buff. Training Whistle: a transform into a defined Wolf token. Stable: ongoing support that does not simply fill every slot with pups. A pet bond, adjacency rule or mount system would need extra code; do not introduce three such systems into Foundation.

Legendary proposals: Ancient Pack Leader, a powerful but removable aura body; Loyal Companion, a unique transform line with clear token inspection. Avoid a win condition that requires a randomly generated specific pet.

# Tribes and cross-class synergies
CURRENT: The type field is single-valued. Type names are design categories, not a complete biological taxonomy. PROPOSAL: Keep each supported tribe dense enough for a deck and allow neutral cards to bridge two class plans through effects rather than inventing many tiny unsupported tribes.

| Type | Natural design hooks | Classes / bridges | Main risk |
| Animal | Grazing, food, modest stats, companions | Farmer, Chef, Tamer | Becoming generic buffs with no reason to choose the tribe |
| Undead | Drops, persistence, damage over time | Neutral foundation; selected class support | Free recurring value and excessive Withered |
| Arthropod | Small swarms, webs, fragile control | Neutral, limited Alchemist support | Too few members for a full package |
| Villager | Cooperation, repair, trade, food | Builder, Farmer, Chef | Every support body stacking the same buff |
| Illager | Formation, tools, aggressive trades | Fighter | Ranged plus defensive stacking |
| Ender | Return, transform, displacement | Neutral premium utility | Repeated bounce denying play |
| Nether | Tough bodies, drops, dangerous resources | Fighter / Alchemist touches | Theme-only grouping with no actual synergy |
| Golem | Durable protection and construction | Builder | Defensive stalls without a win condition |
| Slime | Splitting and small-body generation | Neutral | Seven-slot congestion; free repeated death value |
| Crop | Harvest and grazing conditions | Farmer, food bridge to Chef | Dead block slots without harvest access |
| Aquatic | Schooling, catches, temporary pressure | Fisherman | Ambiguous overlap with Undead Drowned |
| Ore | Mine and conversion | Miner | Too much investment, too few ways to cash in |
| Tameable | Pack auras and training | Tamer | Assuming Animal and Tameable are both on a card |

For a modest tribal package, begin with several independently playable members, two enablers and one or two payoffs across useful XP costs. This is a testing heuristic, not a guaranteed ratio. A payoff with no supporting early game is a trap card. Rare types can remain flavorful without forcing a full deck archetype in Foundation.

# Neutral foundation ideas
PROPOSALS ONLY. These are roles and Minecraft hooks, not final balanced cards. Costs and stats require testing against class tools and powers. Neutral cards should teach common game patterns, provide ordinary curve bodies and offer limited interaction. They should not supply the strongest version of every class ability.

## Baseline board roles
| Concept | Simple role | Design guardrail |
| Pig | Low-cost plain Animal | A readable baseline for comparing buffs. |
| Cow | Solid early Animal | Optional Grazer variant belongs in a deliberate package. |
| Sheep | Low-Attack durable Animal | Health matters without adding a paragraph. |
| Chicken | Cheap fragile body | Do not automatically add a free draw. |
| Rabbit | Fast board-trading body with Sprint | Poor face pressure on the summon turn. |
| Wolf | Mid-cost plain companion | Differentiate from Wolf Pup and class payoffs. |
| Zombie | Ordinary Undead with a small food Drop | Account for the resource in its cost. |
| Skeleton | Fragile Ranged attacker | Can shoot past the front row to reach blocks. |
| Spider | Plain efficient board attacker | Avoid copying Sprint onto every agile mob. |
| Cave Spider | Small debuff attacker | Withered is stronger than a cosmetic poison theme. |
| Villager | Neutral baseline support body | One clear subtype makes tribe access legible. |
| Iron Golem | Expensive defensive body | Avoid making every class's defense equally efficient. |
| Snow Golem | Fragile control body | Repeated Freeze needs strict balance. |
| Slime | Finite split into smaller tokens | Test board space and repeated Totems. |
| Magma Cube | More aggressive split variant | Different role, not merely larger Slime. |
| Turtle | Durable slow body | A defensive option without endless healing. |
| Goat | Mid-cost trading body | A push/adjacency mechanic would need code. |
| Polar Bear | Sturdy late midgame body | Vanilla stats can be useful; not every card needs text. |
| Panda | A second stat-distribution benchmark | Avoid random personalities in the first set. |
| Ravager | Large plain finisher | A fair comparison point for complicated top-end cards. |

## Interaction and resources
| Concept | Simple role | Design guardrail |
| Arrow | Small single-board damage Action | State mob/block and hero exclusions. |
| Snowball | Modest Freeze or debuff Action | Repeated cheap freeze can prevent counterplay. |
| Milk Bucket | Cleanse | Removes granted abilities; keeps printed abilities. |
| Bread | Small hero or board heal | Specify targets; weaker than Chef's best food. |
| Cooked Porkchop | Larger food heal | The any-mob-or-hero configuration is supported. |
| Golden Apple | Premium temporary protection | Permanent Health and invulnerability are different. |
| Ender Pearl | Return a board target | Tokens vanish under current return rules. |
| Lava Bucket | Tool destruction | Tech should have a cost in tool-free matchups. |
| Shield | Defensive support or tool theme | A held shield system is not automatically supported. |
| Experience Bottle | Temporary XP | Watch early power spikes; does not raise permanent max XP. |
| Bundle | Card draw/generation theme | Do not assume it can preserve chosen cards without code. |
| Compass | Predictable resource selection | Generic search/tutor is a proposed action. |
| Map | Small draw effect | Keep simple; biome gameplay can wait. |
| Fishing Rod | Limited catch generation | Keep Fisherman's strongest fishing rewards class-specific. |
| Shears | Utility interaction theme | Choose an actual supported effect, not vague shear text. |
| Totem of Undying | One resurrection flag | Cannot stack extra lives by repeating the buff. |

## Blocks with a continuing job
| Concept | Simple role | Design guardrail |
| Cactus | Damage attackers | Does not need Attack or normal retaliation. |
| Sweet Berry Bush | Small defensive thorn block | Make it distinct from Cactus in cost/role. |
| Campfire | Small ongoing recovery | Broad automatic healing should not erase Chef's identity. |
| Furnace | Slow recurring support | Keep the payoff tied to an explicit event. |
| Bookshelf | Fragile value engine | Remove its dead-slot problem instead of restoring Dismantle. |
| Lantern | Modest support aura | No invented vision rule in the foundation set. |
| Beehive | Finite token/Drop concept | Unlimited end-turn summons easily clog the board. |
| Chest | Clearly defined delayed resource | Pure Spawn value needs a second continuing purpose. |
| Redstone Torch | Visible board aura | Attack auras do not grant blocks useful Attack. |
| Piston | Return-on-Spawn plus a later job, or an Action concept | A one-shot permanent needs a reason to remain. |
| TNT | Delayed damage concept | Generic countdown/self-expiry needs implementation. |
| Beacon | Expensive aura payoff | Symmetric-looking art must not imply symmetric rules. |

## Neutral Legendary direction
Keep only a few broad, memorable threats initially. Ender Dragon can be a large finisher with a finite Drop; Wither can pressure both boards with an explicit cost; Warden can clear a constrained board. None should be the automatic best card for every deck. Class Legendaries should express a plan; Neutral Legendaries should offer a distinctive alternative, not fill every class weakness at once.

# Minecraft systems as card inspiration
PROPOSALS. Each row translates a recognizable Minecraft loop into a possible card decision. The implementation notes prevent attractive themes from becoming misleading Workshop text.

| Minecraft system | Card-game translation | Foundation approach |
| Mining and ore | Invest now, mine later, receive a material | Already supported through Ore, Mine and Drop. |
| Smelting | Convert one resource into a stronger output | Fixed Give/trigger approximation works; general recipes need code. |
| Crafting | Combine useful parts into a tool | Avoid a second inventory grid; a fixed payoff is easier to teach. |
| Tool durability | Limited attacks and enchantment investment | Supported; creates a natural time limit. |
| Repair and Mending | Preserve a tool or restore board Health | Tool stats can increase; damage-repair semantics must be explicit. |
| Farming | Grow value, harvest at a chosen moment | Crops and Harvest already create a Minecraft-specific decision. |
| Bone meal | Accelerate growth or buff living things | Separate mob Attack buffs from block Health. |
| Composting | Turn surplus into future fertility | Generic discard/sacrifice costs are not fully supported. |
| Fishing | A bounded catch table feeding your hand | Fixed-list random Give exists; show the pool. |
| Breeding | Spend food to generate a smaller companion | Summon exists; exact two-parent checks need code. |
| Taming and training | Invest in one companion; transform it | Transform and typed auras exist. |
| Food and hunger | Recover after trades, maintain survivors | Use Health first; a second hunger resource can wait. |
| Brewing | Prepare small effect Actions | Action-played triggers exist. Avoid giant random potion pools. |
| Splash versus lingering | Immediate area damage versus an ongoing engine | Area targets exist; generic timed zones do not. |
| Enchanting | Improve a specific tool and attach a behavior | Supported, with readable inspect information. |
| Redstone signals | A structure reacts to a repeated event | Use existing triggers; signal networks/adjacency can wait. |
| Pistons | Return or reposition | Return exists; positional relocation is not an established rule. |
| Hoppers | Transfer resources between places | Fixed Give/Draw is simpler than a new storage inventory. |
| Dispensers | Repeat a small visible effect | End-turn damage exists. |
| Observers | Respond to another event | Current event filters are limited; no universal event system in UI. |
| Copper oxidation | A card changes as time passes | Stage/age counters need additional implementation. |
| Waxing and scraping | Preserve or revert a transformation stage | Save for an expansion with visible stages. |
| Weather and lightning | A dramatic transformation or shared condition | Transform exists; global weather gameplay is proposed. |
| Day and night | Different timing for mobs | Prefer ordinary turns initially; global cycle adds rules. |
| Light and darkness | Reveal, protect or suppress threats | A visibility system needs new mechanics, not merely a tooltip. |
| Biomes | Board appearance and themed NPC decks | Keep cosmetic for Foundation as agreed. |
| Villager professions | Distinct support roles and tradeoffs | Good class/tribe identity without a new currency. |
| Trading and bartering | Exchange a finite resource for a bounded result | Explicit generated cards work; general exchange costs need code. |
| Raids | Waves of coordinated Illagers | Summon and typed aura work; wave counters require support. |
| Trials and omens | Visible challenge for a later payoff | Good expansion space; objective tracking needs code. |
| Portals and dimensions | Access a thematic pool or move a card | Avoid extra zones in the first set. |
| Teleportation | Return/transform/disrupt | Specify the exact action; teleport is flavor, not a rules verb. |
| End crystals | Vulnerable support structures | A removable aura source makes intuitive counterplay. |
| Sculk and vibrations | React to attacks or deaths | Current event coverage is narrower than every vibration source. |
| Souls and resurrection | Drop and Totem interactions | Fresh copies and finite triggers are easier to reason about. |
| Slime splitting | Replace one body with several small bodies | Summon-on-Drop works; test the seven-slot cap. |
| Explosions | Damage several targets, perhaps including your own | Current area damage is supported. State friendly damage explicitly. |
| Knockback and wind | Interrupt or return an enemy | Actual board movement/adjacency needs rules first. |
| Mounts and transport | Carry or upgrade a companion | Transform is a simple approximation, not a full passenger system. |
| Exploration and maps | Resource discovery or delayed draw | Avoid random generated text; use fixed pools. |
| Storage and bundles | Bank future resources | Additional reserve zones are an expansion idea. |
| Music, horns and banners | Short buffs or tribal auras | Named theme can be expressive without a new keyword. |
| Sleep and beds | Recovery with lost tempo | Generic skip-turn costs need code; ordinary Heal is simpler. |
| Fluids and buckets | Remove, protect, convert or spread effects | Keep targets finite; flooding a whole board is a new system. |
| Powder snow and ice | Freeze and vulnerability | Do not combine cheap repeat freeze with large safe attackers. |
| Honey and slime | Sticky control, healing, splitting | Decide one precise behavior per card. |
| Archaeology | Uncover a bounded reward | Fixed-list Give works; staged excavation is proposed. |
| Structures and treasure | Authored encounters and rare rewards | Strong world progression without extra match complexity. |
| Advancements and mastery | Reward learning a class | Keep match strength independent from account level. |

# Tools, equipment and enchantment roles
VANILLA INDEX lists every actual item ID later. This chapter groups their design uses. A Minecraft item can become a Craftbound Action, Tool, Enchantment or depicted art; it does not have to inherit the same combat behavior.

| Family | Minecraft objects | Suggested class ownership |
| Melee weapons | Swords, axes, mace, spears | Fighter primary; other classes get narrow utility tools. |
| Mining tools | Pickaxes, brush | Miner primary; excavating and Ore conversion. |
| Farming tools | Hoes, shovel, shears | Farmer primary; planting and harvesting themes. |
| Fishing gear | Fishing rod, trident, nets as proposed concepts | Fisherman; trident may be combat or resource support. |
| Ranged weapons | Bow, crossbow, arrows | Fighter / Fisherman accents; use the same back-row targeting rules as Ranged mobs. |
| Cooking implements | Knife and utensils as original Craftbound concepts | Chef; invented objects must not be labeled vanilla registry items. |
| Alchemical implements | Brewing stand, blaze rod, bottles | Alchemist; fragile tool or block support. |
| Companion equipment | Bone, saddle, lead, horse armor, wolf armor | Tamer; target protection and growth. |
| Construction equipment | Hammer as an original concept, axe, shovel | Builder; repairs and structure support. |
| Protective gear | Shield, helmets, chestplates, leggings, boots | Usually Actions/buffs unless a separate equipment slot is implemented. |
| Mobility | Elytra, boat, minecart, ender pearl | Sprint/return/transform approximations; not free extra turns. |
| Containers | Buckets, bottles, bundles, chest boats | Resource generation or utility; target wording matters. |

Vanilla material tiers are useful visual vocabulary, not a command to print seven nearly identical cards for every tool. Choose a few meaningful steps per class. Keep enchantment ownership class-specific where it reinforces identity: Looting and Fire Aspect for Fighter, Efficiency/Fortune for Miner, Luck of the Sea for Fisherman. Cross-class access should be intentional rather than a neutral catch-all.

## Enchantment translation examples
Sharpness becomes Attack; Unbreaking becomes Durability; Looting becomes a kill reward; Fire Aspect becomes a damage-triggered effect; Fortune becomes a mining reward; Luck of the Sea changes or rewards catches. Mending could repair through a healing/event relationship, but if it requires spending XP automatically the cost must be coded. Silk Touch, Loyalty, Channeling and Multishot need exact rules, not merely their vanilla names.

Avoid bonus-text overload: if an Enchantment adds a trigger, inspect the tool should show the trigger, not just an Enchanted title. Cosmetic Enchanted finish must remain distinguishable from gameplay enchantments.

# Candidate keywords and mechanics
PROPOSALS ONLY unless noted. The foundation should probably introduce at most one or two additional named terms after testing the existing ones. A memorable Minecraft name is useful only if the underlying rule is clear and repeated.

| Candidate | Concise definition concept | Support / recommendation |
| Harvest | An effect when this Crop is reaped. | Trigger already exists. Good candidate for consistent bold label and glossary. |
| Mine | Consume a friendly Ore and trigger its Drop. | Action already exists. Keep separate from attacking and death. |
| Thorns | When attacked, deal N damage to the attacker. | Authored through ATTACKED/DAMAGE; could become a short named keyword later. |
| Grazer | At your turn's end, heal this if you control a Crop. | Passive exists. Keep full wording until enough cards use it. |
| Battle-hardened | Gain Attack after surviving damage, once per turn. | Passive exists. Specify N and global-turn limit. |
| Schooling | Gain temporary Attack when you play another Aquatic. | Passive exists. Excellent sequencing theme; explain recipient. |
| Well Fed | Excess healing raises maximum Health. | Action exists. Define exact interaction with heal triggers. |
| Choose one | Select one listed effect when played. | Generic card UI/rules not yet implemented; high-value next feature. |
| Catch | Add a card from a specified fishing pool. | Fixed-list Give exists; do not hide an enormous pool behind a word. |
| Repair | Restore Health or Durability to a stated target. | Board healing/tool stat gain exist, but these are different operations. |
| Growth | Advance a Crop through visible stages. | Needs counters and timing rules. Save for later. |
| Fuse | After a stated number of turns, explode. | Needs duration/countdown support. Not the PvP timer. |
| Stock | A block has a limited number of uses then expires. | Useful block solution, but charges/self-expiry need code. |
| Trade | Pay a stated resource for a stated reward. | General alternate costs need code. |
| Bond | A chosen companion and source share a relationship. | Needs persistent links and loss rules. Too much for initial Foundation. |
| Ambush | A hidden or delayed response. | Needs hidden-state and reaction timing; defer. |
| Biome | Shared location affecting cards. | Keep cosmetic for now; gameplay biomes are an expansion system. |
| Silence | Remove abilities and statuses. | Not equivalent to current Cleanse; requires explicit implementation. |

## Keyword balance review
Shield and Sprint are familiar but useful because they communicate important targeting/readiness rules quickly. Ranged's new counterplay makes it a more interesting design space, but low-cost high-Attack Ranged still needs testing. Spawn is reliable immediate value; keep body stats honest. Drop forces trading decisions but becomes oppressive with repeated resurrection. Cleanse strips buffs and debuffs; printed card text remains. Block should be presented as a kind, even while the internal storage uses a keyword.

Prefer Minecraft's resource loops over keyword renaming as the source of originality: crops versus harvest, Ore versus mining, catch tables feeding a hand, tools that wear out, and enchantments that alter a specific tool. These change decisions more than adding a new word for charge or lifesteal.

# Building and testing the set
PROPOSAL PROCESS: Build one playable archetype per class before adding a second. Give each deck a way to contest the early board, a midgame plan, interaction, a finite resource plan and a way to end the game. A beautiful class identity document cannot compensate for a deck full of unspendable resources.

## Suggested 15-card class starting allocation
For a smaller first pass: eight mobs/blocks, three Actions, two Tools and two Enchantments is one possible allocation. Adapt it by class: Miner needs enough tools and Mine access; Builder needs enough attackers; Alchemist can trade some board slots for Actions. Do not force identical kind counts if that destroys identity. The current 30-card deck requirement makes neutral support essential.

## XP and rarity
Do not distribute costs and rarities evenly merely for a tidy graph. A working curve usually needs more cheap/mid-cost cards than expensive finishers. Keep enough Commons and Rares to build a functioning deck; use Epics for narrower build-arounds and one or two Legendaries per class only when the total set size supports them. Rarity is not permission for strictly superior rate.

For a 30-card test deck, try a starting curve such as 4 at 1 XP or below, 7 at 2, 6 at 3, 5 at 4, 4 at 5 and 4 at 6 or above, then change it to suit the plan. This is an illustrative baseline, not a balance verdict. Measure how often hands have meaningful legal plays at each turn.

## Playtest questions and measures
- Opening hands: how often are there no useful plays before turn three? Separate unlucky draws from structural lack of low-cost cards.
- Resource mismatch: Ore without Mine, enchantments without tools, healing without damaged targets, tribal payoffs without tribal members.
- Board health: how often do blocks strand slots? How often does seven-slot congestion stop both players progressing?
- Interaction: can the opponent remove an engine before it pays repeatedly? Does Freeze prevent play for several turns?
- End conditions: how often are wins earned through board decisions versus unavoidable hand burst or fatigue stalls?
- Economy: compare draw versus generation, Health versus healing, and one-shot buffs versus removable auras.
- Readability: ask a player to predict an effect before playing it. A wrong prediction caused by text is a defect even when the code is consistent.
- Abuse cases: Totem plus Drop, free Actions plus Action-played, card-added chains, Well Fed plus healed, and mining feedback loops.

Record deck list, catalogue revision, first/second player, turn count, result, stuck turns and misunderstood text. Swap sides and run enough matches to identify repeated patterns. AI simulations are useful smoke tests, not proof of competitive balance; AI target selection can bias results.

## World progression and NPC decks
CURRENT: Mastery exists per class, with levels and rewards; it does not alter match stats. Keep that principle. PROPOSAL: award cosmetic backs, titles and authored starter upgrades instead of permanent in-match stat bonuses. Keep card access transparent so new players can learn a class without a rare-card gate.

PROPOSAL: Author NPC decks around one clear lesson: Shield targeting, preserving a tool, harvesting at the right moment, or interrupting an engine. Add biome flavor through card choice and the board appearance, not unexplained combat bonuses. Difficulty should come from coherent lists and better decisions before giving an NPC hidden resources. Ranked play, encounter progression and card grading can wait until rules, reconnect/pause behavior and catalogue consistency are stable.

# Editing workflow and safeguards
Use Automatic description while building effects. The Workshop preview and game generate wording from the same structured rules. Custom description is only presentation: it does not change gameplay. Save in the locally launched Workshop writes the development run/config/craftbound/cards.json; Save as exports a copy. Reload the catalogue between matches with the existing command. A separately installed client/server uses its own config directory; saving the development file does not magically update a friend's server.

Before the Foundation replacement, back up the active catalogue. Keep stable IDs and existing order. New cards append. The deletion flag retires a card without reusing its number. Do not change an existing card's protected kind/token identity to force a new design into it; create a new identity when required. Keep token references valid and inspect the linked-to list before retiring a generated token.

The included current-card appendix is a snapshot of the bundled catalogue. Your active development catalogue contained one extra definition at extraction time; it has not been overwritten. Use the live Workshop to inspect your custom cards. The full vanilla index is inspiration, not an import that adds thousands of cards.

## A practical build order
1. Write a one-line identity and two weaknesses per class.
2. Choose a manageable total set size and a small supported keyword vocabulary.
3. Build neutral curve bodies and simple interaction first.
4. Build each class's enablers before its payoffs. Verify tools, Crops, Ore and catches actually connect.
5. Give every block an ongoing job or a supported consume/payoff path.
6. Review every generated sentence against targets, timing, duration and limits.
7. Test real decks, then add Epics and Legendaries to deepen a working plan.
8. Ship a versioned catalogue and keep a short balance-change log.

# Source notes and coverage
The exact vanilla indexes are extracted from the locally installed Minecraft 26.3 common/client jars and BuiltInRegistries. Item/block overlap is intentional: an item is not always a block, and a block does not always have an inventory item. English display names come from the matching game's language data, with a readable identifier fallback. Entity categories are the game's registry categories, not a complete hostile/passive classification and not Craftbound tribes.

Craftbound rules were checked against CardCatalog, CardEffects, Match, HeroPowers, Keyword, MobType, MasteryProgress and the bundled cards.json. The Workshop model is used to generate the current-card appendix. Code and custom catalogues may evolve after this snapshot; the live editor remains authoritative for the current file.

Official version context: https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3
Official brewing background: https://www.minecraft.net/en-us/article/taking-inventory--potion

The creative proposals in this guide are original card-design suggestions informed by those game objects and the project's implemented rules. They are not descriptions of vanilla Minecraft behavior, tested balance claims or a commitment to implement every listed mechanic.
