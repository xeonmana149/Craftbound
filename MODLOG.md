# Craftbound card editing

2026-11-02 — Workshop associated-card layout previews:
- Added live linked-card previews below the selected card for Summon, Give, Transform and random loot/catch pools. Reuses the main face layout, follows unsaved edits, merges repeated references and displays timing/copies. Arrows page one face at a time to keep large pools inexpensive. Edit opens linked cards/tokens; missing/deleted references remain visible. Random class generation explains that its pool depends on the controlling hero. No authored catalogue changes.
- Verified Workshop model checks and isolated Edge preview: summon count, pool paging, live reference changes, token navigation, dynamic class note and 900px layout. Preview screenshot inspected; IDE validation passed. Only temporary catalogues used for checks.

2026-11-02 — Workshop validator refresh and keyword alignment:
- Save now reloads the same model.js served to the editor, preventing a long-running host from rejecting newly supported passives/actions. Validation, backups and authentication remain enforced. A temporary-catalogue regression replaces the rules while the host is running and checks both rejection and recovery.
- Full card faces center one or two standalone keywords horizontally and vertically, retaining separate lines, bold styling and correctly positioned hover regions. Longer abilities retain their normal layout. Workshop previews match. Block remains a card kind, excluded from generated keyword prose.
- Verified offline build (122,007 card assertions plus existing face/table/vendor checks), server reload regression and isolated Edge keyword layout checks. Restarted the user's stale host at its existing port/token without reloading the tab; clicked Save and confirmed Banner AURA_STATS and Ender Chest GIVE_RANDOM_CLASS persisted successfully. Live game layout still requires a client restart.

2026-11-02 — Anvil tool upgrades and random targeting:
- Added Improve equipped tool (UPGRADE_TOOL) for blocks/mobs/items and triggered tool effects. Independent nonnegative Attack/Durability bonuses permit either stat to be 0; at least one must be positive. Blocks can be played without a tool and their upgrade then does nothing. Actual Enchantment cards retain ENCHANT_TOOL and attachment rules. TOOL_UPGRADE synchronizes absolute tool/hero Attack and uses a small anvil-sound/spark effect without adding enchantment entries. User-authored catalogue unchanged.
- Affects now exposes Random enemy, Random friendly, Random block, Random mob and Random hero. Enemy/Friendly stay on one side and only include heroes when eligible for the action/type filter. Mob/Block/Hero span both sides; fixed-kind choices set/lock the matching board filter. Mob-only actions filter heroes out of mixed random pools. One eligible candidate is selected per effect; empty pools do nothing. Read-only previews identify possibilities without consuming RNG. Existing friendly board-only option remains available with clearer naming.
- Updated Java/Workshop validation, generated wording, controls, previews and Design Reference. Enum additions appended on the server to preserve existing ordinal identities. No card definitions/IDs were rewritten.
- Verification: offline build passed with 122,007 card assertions and existing face/table/vendor checks. Added exact random pools, single-target selection, both-side reachability, empty pools, mob-only buffs, preview eligibility, tool Attack-only/Durability-only changes, absent tools and stat event checks. New and existing Workshop model checks passed. Real isolated Edge tests authored/saved all five random choices and Anvil +2 Attack/+0 Durability, reloaded them, and verified the active catalogue stayed unchanged. No live multiplayer test performed.
- Save drafts before reopening Workshop via its launcher to refresh server validation; restart client/server with the rebuilt mod for new gameplay effects.

2026-11-02 — Snow Golem damage effects against heroes:
- Actual attack damage to a hero now dispatches DAMAGE_DEALT for the attacking mob or equipped tool/enchantments. Workshop accepts explicit Damaged mob or hero targeting for that trigger, labels the old board-only option clearly and generates “After this deals damage: Freeze the damaged mob or hero.” Type filters are cleared/disabled for the character target. Board-kind filtering can exclude blocks while allowing heroes.
- Existing DAMAGE_DEALT CHOSEN_ANY targets remain board-only. Hero Wither stays rejected; prevented damage does not trigger Freeze. Uses existing hero Frozen status, visuals and attack-lock lifecycle. Ranged Snow Golem can hit heroes and mobs without retaliation; non-Ranged damage effects retain defensive retaliation behavior. No authored cards changed.
- Offline build passed with 120,473 rule assertions plus render/table/vendor checks. Added hero/mob Freeze, duplicate status, next-turn attack lock, invulnerability, block exclusion, legacy targeting and equipped-tool checks. Model validation and isolated Edge authoring/save/reload passed; active catalogue verified unchanged. Design Reference rebuilt. No live multiplayer test performed.
- Save drafts and reopen Workshop via launcher for the new validator/UI; restart Minecraft/server using the rebuilt mod.

2026-11-02 — Surviving attack-kill trigger:
- Added AFTER_SURVIVING_KILL, displayed in Workshop as After attacking, killing and surviving. Available on non-block mobs, with matching Java/Workshop validation and generated bold timing text. Ravager setup: Damage, Enemy hero, Amount 2.
- The exact attack hit must kill the enemy board target; secondary triggered damage does not qualify. Check the original attacker is alive and still on its board after retaliation, thorns, After attack and Death resolution. Defensive kills, hero hits, resurrection and transformed replacements do not qualify. Existing On kill and After attack behavior remains available. Trigger enum value appended to preserve prior ordinal identities.
- Offline build passed: 120,461 card-rule assertions plus card-face, table and vendor checks. New tests cover mob/block kills, nonlethal hits, mutual deaths, thorns, death explosions, Totems on either combatant, defensive kills, invulnerability, ranged survival and indirect kills. Workshop model check and isolated Edge creation/save/reload passed; active user cards were not modified. Design Reference rebuilt. No live multiplayer test performed.
- Save drafts and reopen Workshop with its launcher to load the new validator. Restart Minecraft/server with the rebuilt mod for the gameplay trigger.

2026-11-02 — Attack/Health auras, class card generation and binder defaults:
- Workshop adds Aura: Attack and Health with independent Attack/Health bonuses and optional mob-type filtering. Old AURA_ATTACK definitions stay compatible; optional auraHealth defaults to zero. Generated text omits unused stats. Bonuses affect other friendly non-block mobs, including later summons. Blocks can be aura sources.
- Continuous Health is tracked separately from permanent buffs. Gaining it raises current/max Health; losing it caps current Health to the reduced maximum without dealing damage, triggering healing, or reviving dead mobs. Stacked sources, source removal/death/transform and saved matches reconcile without duplicate application. Absolute aura events keep match/spectator stats synchronized. Zero-base-Attack mobs can attack while an aura provides positive Attack. Yellow beneficiary previews include Health-only auras.
- GIVE_RANDOM_CLASS is available as Add random card from your class in the effect maker. End turn + Amount 1 generates a normal, active collectible matching the controlling hero's class, excluding Neutral, tokens and deleted cards. It uses the existing GIVE/BURN and hand-added trigger paths; an empty class pool does nothing. Updated Workshop guide and Design Reference, without adding or modifying authored catalogue entries.
- Binder opens with Normal checked and Enchanted unchecked; users can enable either version filter. Reset continues clearing filters to show both.
- Verification: offline build passed with 120,443 card-rule assertions, card-face/table checks and 40,035 vendor assertions. Added aura lifecycle/snapshot/combat/preview and class-generation checks. New and existing Workshop model/mechanics checks passed. Isolated Edge test created, configured, saved and reloaded both features through the real controls and verified the active user catalogue remained byte-for-byte unchanged. No live multiplayer playtest was performed.
- Save the current draft before reopening Open Card Workshop.cmd to load the new server validator and controls. Restart Minecraft/client and server with the rebuilt JAR for the new rules.

2026-11-02 — Binder default and authored mob artwork:
- Combined binder collection opens on Neutral instead of Fighter. Editing a saved deck still uses its own class rules.
- Shared card artwork resolver accepts mob IDs such as minecraft:zombie by resolving their spawn-egg item when no exact item exists. Actual item choices still take precedence. This fixes the four imported cards showing paper without rewriting their authored definitions; binder, inspect, match and stand rendering share the resolver. Workshop field label now explains that item or mob IDs are accepted.
- Added registry-backed checks for Zombie, Skeleton, Spider and Enderman shorthand plus preserving Cod's real item artwork.

2026-11-02 — Foundation hero powers and individual power presentation:
- Implemented the chosen nine-class baseline: Fighter Battle Ready (+2 Attack this turn, works without a tool); Builder 0/2 Shield Wall; Farmer 0/2 Crop that must survive to its next turn for +1 maximum XP; Fisherman What a Catch! (equal Cod 1/3, Salmon 2/1, Pufferfish 1/1); Alchemist targeted 1 damage; Miner generated 1-XP Iron Ore Item (+1/+1 to a friendly mob); Chef targeted heal 2 including Blocks; Tamer 1/1 Pup; additional Redstoner Power Up for a friendly Block's Activate ability. All cost 2 XP, once per turn. Retired power enum slots remain reserved.
- Pufferfish Death records its actual enemy mob killer, including retaliation. Its -2 Attack expires at the end of that controller's next turn and survives pause/save. Cleanse removes the timed penalty without later restoring unrelated debuffs. Fighter's temporary attack is separate from tool stats and respects Frozen, once-per-turn attacks and expiry. Crop XP remains capped at 10.
- Added START_TURN / ACTIVATE triggers, GAIN_MAX_XP / DESTROY / WEAKEN_UNTIL_NEXT_TURN actions and KILLER targeting to Java + Workshop validation, text and controls. Updated token links and live Design Reference. Redstoner has class color, deck selection and mastery support; no collectible cards were added.
- Each power has a bounded GUI signature and vanilla sounds: fishing line/bobber/splash/retrieve; thrown potion/shatter; rising wall bricks/shield; seeds/planting; food/hearts; Fighter combat pulse; pickaxe/ore pickup; redstone circuit/piston; bone toss/pup call. The serial event queue delays results until the gesture lands. Shared MatchScreen presentation also serves spectators.
- Bundled/runtime/Workshop catalogues still have zero collectibles and eight required tokens: Experience Bottle, Wall, Crop, Wolf Pup, Cod, Salmon, Pufferfish and Iron Ore. New epoch foundation-hero-powers-20261102 rejects stale drafts. Pre-power system-token copies are in backups/hero-powers-before-20261102; the original full catalogue remains in the earlier Foundation reset backup.
- Verification: offline build and 120,267 rule assertions passed, including new powers, real Pufferfish combat/retaliation, temporary effects, snapshots and targeting. Workshop model checks and an isolated Edge session validate Redstoner Block/Activate creation, save/reload and updated reference serving; active catalogue stays empty. Real-client render tests exercise all nine signatures and verify every chosen vanilla sound ID. No live multiplayer session or subjective audio playback review was performed. Existing vendor-model UV warnings are unrelated to these power effects.
- Install the rebuilt normal JAR (not sources) on both client and server; also copy the current run/config/craftbound/cards.json into external installations' config/craftbound/cards.json for the Foundation reset and new system tokens. Restart Minecraft/server and the Workshop after changing the baseline.

2026-11-02 — Foundation reset and separate destruction animations:
- User-authorized reset removes every collectible and optional token from bundled cards.json, run/config/craftbound/cards.json and Workshop defaults. Exactly eight system tokens remain: Wolf Pup, Excavated Pickaxe, Crafted Sword, Cobblestone Wall, Wheat, Caught Cod, Minor Harming Potion and Experience Bottle. New collector numbering starts at 1. Art, Ideas, class powers and authoring mechanics are retained.
- Exact pre-reset copies are in backups/foundation-before-reset-20261102-195643. Old catalogue examples survive only in test resources, excluded from the shipped mod. Existing saved decks are not rewritten into replacement cards. Restart Minecraft for this reset; external installations need the new JAR and current config/craftbound/cards.json.
- Workshop starts empty with generated tokens hidden. A reset epoch isolates old browser drafts and rejects stale pre-reset saves. Saving empty/incomplete sets is allowed. Packs neither consume themselves nor sell while the catalogue is empty; partial rarity pools fall back to available collectibles. Vendor singles remain empty until cards are authored.
- Hero defeat uses irregular pixel-shaped portrait shards of varied sizes and directions, with bounded travel in a medium area. Blocks retain their board slot while vanilla crack stages build, then vanish in a short dust poof; they do not use portrait fragments.
- Validation: offline build, 107,913 card assertions, 40,035 vendor assertions, card-face/table/fragment checks and isolated Minecraft rendering passed. Workshop model/save/server checks and a real browser check cover empty save, retained tokens, first new card creation/save/reload, without adding test cards to the active catalogue.

2026-11-02 — Collection categories, reusable gameplay effects, spectator UI and Workshop planning:
- Collection/deck filters now distinguish Keywords, Mob Types and Card Types (Mobs, Blocks, Items, Tools, Enchantments). Block is removed from keyword choices and keyword rule boxes while its legacy catalogue marker remains compatible. Groups combine with XP, rarity, search and Normal/Enchanted toggles; selections within a group use OR. Reset is last and clears every group. No card IDs, collectible counts or authored definitions changed.
- Added reusable card-to-board arrival, block placement/break cracks and clipped fragments, potion arc/shatter, ranged trail, explosion, slime split profile, keyword pulses and legendary arrival signatures. Defeated hero portraits crack then scatter into sixteen clipped pieces before the result banner. Shared event source metadata lets generated cards/tokens travel from their actual source, including destroyed Drop sources. GUI particle budgets and fixed-size fragment grids avoid per-frame textures or world particle entities.
- Epic/Legendary pack reveals have extra rune/seal anticipation, staggered shockwaves and brighter Enchanted accents. Another left-click advances ordinary/Epic reveals; Legendary buildup consumes that click to skip itself. Skip never reveals untouched cards. Pack contents/awards are unchanged.
- Spectating embeds the actual MatchScreen read-only, with player perspective switches, Follow turn and hand-permission controls. Public event streams hide hand additions without consent; switching perspective or revoking consent discards queued private visuals. A minimum logical canvas prevents spectator controls from compressing the board rows.
- Reference now mounts bounded pages instead of thousands of catalogue entries. Search still indexes the full guide; chapter/content areas scroll independently. Fixed the iframe/main grid sizing that prevented mouse-wheel scrolling. All 1,658 item entries remain reachable across 35 pages. Added Ideas with class/stage/tags, concept, synergy and balance notes; local-host autosave writes workshop-ideas.json separately with atomic replacement, backups and stale-write protection.
- Validation: offline Gradle build and rule/render harnesses passed; isolated real-client screenshots cover both collection filter layouts, Card Types/Keywords popups, pack rarities, combat morph/cracks/fragments/potions/explosions/portrait shatter and spectator UI. Browser tests verify real wheel scrolling inside the Reference iframe, search/pagination, unsaved card draft preservation, Ideas autosave/reload and unchanged cards.json. Slime splitting is wired for authored Slime/Magma Cube Drop summons; there is no bundled Slime card, so that profile has not been visually sampled. No multiplayer play session or live audio review was performed.

2026-11-02 — Pack reveal presentation and reference scrolling:
- Continuing the existing Fabric 26.3 client GUI route. Pack pulls and server awards are unchanged; presentation uses a serial timeline so jackpot effects cannot stack.
- Epic/Legendary anticipation, foreground card focus, rarity halos, inward sparks, burst rays and Enchanted violet accents use bounded GUI geometry. Respects particle settings and provides Skip animations. Vanilla enchanting, amethyst and level-up sounds build/release anticipation.
- Reference browsing displays one chapter at a time; search still spans all chapters. Off-screen catalogue groups skip rendering and immutable search text is cached; no scroll listeners. Generator retains these optimisations.
- Verified offline build, pack timeline regressions (including queue/skip), and isolated real-client rendering of unopened, Epic, Legendary, Enchanted Legendary and jackpot samples. Reviewed screenshots and softened the initial stacked-border glow. Particle emission is bounded at 112 burst sparks for the single active card and respects Minimal/Decreased settings. Browser checks confirm single-chapter browsing, global search, links and unsaved draft preservation; no user catalogue or world was modified.

2026-11-02 — Foundation reference, wording and retiring Dismantle:
- Removed Dismantle's gameplay controls, AI behavior and server action. Legacy packet/schema IDs remain reserved and inert so old data cannot accidentally invoke a different action. Harvest and Mine remain separate.
- Generated text omits redundant Play on Items/Enchantments, spells out turn/attack ownership, uses correct singular/plural wording and identifies observed event scopes. Timing labels are bold in the game and Workshop. Existing custom descriptions and collector IDs are preserved.
- Added a searchable Design Reference tab to the Workshop, contextual links beside each card preview, and advisory wording/design checks. Switching tabs preserves unsaved edits. Includes class identities, powers, synergies, current rules, proposals, neutral concepts and complete Minecraft 26.3 registry indexes.
- Reference sources and printable PDF live in docs/design. New proposed mechanics are reference material, not silently enabled actions. No active user catalogue was overwritten.
- Verified full offline Gradle build (107,228 card assertions, 40,035 vendor assertions and card-face/table checks), Workshop checks, and isolated browser navigation/search with unsaved edits preserved. Rendered and visually reviewed the 102-page PDF; all registry identifiers are present. Live Minecraft visuals were not rechecked for this change.

2026-11-02 — Hide collection footer on Card Backs:
- Removed the bottom collection summary/action panel and deck XP curve from the Card Backs tab. Page controls and card-back inspection/equipping remain available; normal card tabs retain their footer.

2026-11-02 — Paged card-back collection:
- Card backs now use a responsive collection-sized grid with fixed per-page capacity, previous/next controls and mouse-wheel paging. New designs add pages instead of growing the grid or shrinking every existing design.
- Reserved two-line name labels, white hover outlines, existing inspection/equip controls, and a card-back ownership footer replace the unrelated class-card counts on that tab.

2026-11-02 — Card Master chair approach and opponent reveal:
- Card Master arrivals now use the table's actual persistent chairs rather than planning a new pair from the player's approach direction. The assigned NPC chair stays fixed while the player walks around; an already seated player's choice still takes precedence. Added all-direction chair-selection regressions.
- Opponent card-play popups are 10% smaller and inset another 3.5% of screen width from the left edge.

2026-11-02 — Resume confirmation and repeat pauses:
- Right-clicking a paused table or its waiting Card Master opens a world confirmation. Not now/Escape leaves the saved match untouched; PvP resumes only after both participants confirm nearby within 60 seconds. Opening a prompt revokes that player's previous ready vote.
- Successful resumes reset the pause-request quota for both players without changing the turn or remaining time. Added repeated snapshot/resume regression coverage.
- World card inspections restore the cursor position saved on closing the previous inspection, scaled for window size changes. Binder/pack inspection retains its existing cursor behavior.

2026-11-02 — Simplified attack badge:
- Replaced sword/bow artwork with a gold pixel circle and centred attack number. Uses the heart's same 9x9 pixel grid and number layout across match, tool/hero badges and the physical tabletop. Ranged rules remain unchanged.

2026-11-02 — Table rendering, spectating, PvP timing, and inspection:
- Replaced layered world-space table graphics with a cached GUI render target and one surface quad. Isolated the GUI model-view matrix so item artwork stays face-on, corrected framebuffer V orientation, and aligned each owner's dashboard/cards to their chair. Smaller tabletop mobs have wider gaps and clear the hero/status area. Each side has its own fixed turn label.
- Added 75-second PvP turns and a burning TNT-style fuse in the final 15 seconds. AI games remain untimed. Expiry uses normal end-turn resolution; saved pauses preserve time, and pause requests have a per-player/per-turn limit.
- Right-clicking an active table opens read-only spectating. Hand identities stay server-private unless their human owner accepts that spectator's request. Owners can deny/revoke; grants expire when the watch/match ends or pauses. Added pure timer/consent/privacy regressions.
- Match board uses a muted grey-green mat. Damage badges separate the small sword/bow from the number, and Drop no longer adds skull overlays. Binder card backs have wrapped labels and an inspect view; card/card-back inspection eases in over 180 ms. Display labels consistently say Card Back; saved identifiers remain unchanged.
- Full build runs the existing Java harnesses (107,229 card-rule assertions, 160 card faces, seating/orientation checks, and 40,035 vendor assertions). Added an isolated Fabric real-client render test with screenshots in build/run/clientGameTest/screenshots. Verified item artwork, facing, card-back inspection and non-overlapping binder labels on the GPU. Actual multi-client PvP/consent interaction still needs a multiplayer playtest. Existing vendor placeholder UV bake warnings are unrelated and remain.
- Build output for installation: build/libs/craftbound-1.0.0.jar, requiring Minecraft 26.3, Fabric Loader 0.20.1+, Java 25 and Fabric API (built with 0.165.0+26.3). Designer cards remain separate in run/config/craftbound/cards.json; the server synchronizes its catalogue to joining clients.

2026-11-02 — Paused opponent persistence and older-table redraw:
- Pausing now leaves the Card Master standing at its table rather than starting the 50-tick departure timer. Waiting opponents and their table links save with the world; resuming reuses the same NPC. Right-clicking a waiting Master resumes through the normal participant checks. Failed seating preserves the waiting opponent. Finished games still depart normally; abandoned active NPCs clean up after reload.
- Table maintenance repairs legacy paused AI saves whose opponent already disappeared, after neighbouring entity chunks have loaded. It recovers an existing waiting NPC before spawning one and keeps private snapshots/NPC links out of client update tags.
- Client receipt of a board's first/cleared state explicitly dirties the table's render section, covering old tables whose block entity was created after the section was compiled. Increased tabletop layer separation to avoid float precision collapsing the printed layers. Verified the paused public board already exists in the user's world save; no world files were modified directly.
- Validation: checkCards, checkCardFaces and checkTableWorld, including public packet privacy, glyph winding and distinct tabletop layers at underground/normal/high elevations. No live GPU verification; Minecraft must restart to load the changes.

2026-11-02 — Workshop deletion, saved table pauses, and public tabletop boards:
- Workshop now has Delete card / Restore card and Show deleted. Unsaved drafts are removed; existing definitions use an optional deleted flag, preserving collector order, old items and paused-match references. Active collections, booster/vendor/creative pools and deck admission exclude deleted entries. References, mandatory hero-power tokens and last-of-rarity pools are guarded. Existing decks with deleted entries are rejected instead of starting with silently omitted cards. No user card definitions were deleted during implementation.
- Replaced Put the match away with Pause game. AI agrees immediately. PvP prompts only the opponent, freezes action processing pending approval, allows cancellation and expires after 30 seconds. Accepted pauses dismount players and remove the active AI task without awarding rewards. Full private snapshots live in the individual table block entity and persist with world/chunk saves. Both humans must return and right-click the same table within 60 seconds; AI matches resume on the owner's click. Original UUIDs, proximity, seat availability and active-match conflicts are checked. Breaking the table removes its saved game. Existing tables upgrade on interaction/maintenance.
- Added a public live board on the physical tabletop, including both boards, heroes, stats, tools, XP, sleeve-coloured hidden hands and paused state. It reuses match badges, Shield/status effects and card artwork. Clients receive an explicit public projection, never private hands, ordered decks, logs or private events. The table's block update tag excludes its saved-match field. Projection extraction is cached at 10 Hz within 24 blocks; server updates occur on match actions. This is a public board projection, not a capture of the interactive screen: it updates state directly rather than replaying the screen's attack/hand animations.
- Validation: Java/client compilation; rule/snapshot/deletion assertions; table geometry and full 14-mob projection with shared effects; 160 shared card faces; seating/world checks; 40035 vendor checks; Workshop model, deletion, validation navigation and Save regressions. Snapshot checks cover mulligans, exact deck/hand order, enchanted tools/triggers, statuses/buffs, next IDs and RNG continuation. Match RNG is owned rather than sharing/reseeding a world entity's RNG. No live multiplayer or GPU visual verification performed; restart Minecraft for new classes and refresh Workshop after protecting unsaved edits.

2026-11-02: Existing Fabric 26.3 / Java 25 project at C:/Users/jorda/Dev/Craftbound.
Route: JSON card catalogue, reusable server-side effects, server-authoritative catalogue synchronization, offline browser editor.
Keep identifiers and collectible ordering stable. Reject invalid reloads atomically and block reload while matches are active.
Source of truth: src/main/resources/craftbound/cards.json; editable runtime override: config/craftbound/cards.json.
Validation: IDE compilation plus headless rules/catalogue regression tests and browser editor checks. No save or game profile changes required.

Implemented:
- JSON catalogue and offline Card Workshop, live layout preview, editable balance table, filters, duplication, file save/import and recovery draft.
- Configurable triggers/actions, type and Mob/Block/Both filters; strict validation and atomic replacement. Server catalogue sync and operator-only reload/give/testdeck commands.
- 63 definitions / 53 collectibles / 10 tokens. Foundation target remains 180; the full set is not yet populated.
- Fisherman class and Cast power, Aquatic pool, signature tools, four class-specific enchantments, Villager/Illager/Undead/Animal support cards.
- Composter excludes blocks; Bone Meal gives mobs +1/+1 or blocks +2 Health. Heal restores missing health without increasing maximum health.
- Sprint attacks board targets immediately and heroes only from the owner's next turn; server, AI, client highlights and release checks share the restriction.
- Clear deck next to Done and Change class above the deck list; changing class keeps compatible cards and never consumes binder contents.
- Item parchment/octagonal art, block stone braces, kind/subtype labels, neutral names and legendary gold art frames. Blocks retain an attack badge showing 0 in hand and on the board; items/enchantments have no stats.

Verification:
- IntelliJ build successful.
- Java harness: 50,530 assertions, including 1,000 legal 30-card AI decks across four classes, migrated effects, type filters, buffs/healing, Sprint, enchantments, class switching, client targeting, catalogue packet round trip and bounded cyclic effects.
- Node model checks: starter JSON parity, invalid edits, type synergy, tool effects, generated descriptions and JSON round trip.
- Browser automation refused local file URLs. No alternate browser route was attempted. Browser visual interaction and live Minecraft rendering remain unverified; the client needs one restart for the new packet, class and command registrations.
- Test-only Unsafe allocation skips graphical Screen construction; it is not used in production code.

## Binder browsing and workshop IDs — 2026-11-02
- Binder class tabs: Neutral, Fighter, Builder, Farmer and Fisherman. Common/Rare/Epic/Legendary buttons jump to fresh rarity spreads; cards sort by XP then name. Wheel/arrows turn pages within the selected class. Empty rarity sections remain reachable. The layout scales to fit smaller GUI sizes.
- Collector numbers still come from the original catalogue order. Normal and enchanted copies continue sharing a pocket. NEW indicators follow the new browsing order, with a marker on class tabs containing new cards.
- New/duplicated workshop cards derive collision-free IDs from their names. Manual ID edits stop autofill; saved/imported IDs stay fixed. Unsaved draft recovery retains autofill state, and effect references follow renamed draft IDs.
- Verification: IntelliJ build passed; Java checks passed 50,955 assertions, including multi-spread rarity grouping, jump destinations, ordering, empty sections and unchanged collector numbers. Node checks passed ID generation/reference updates and existing catalogue validation checks. Live Minecraft rendering and browser interaction were not verified.

## Shield readability and hero attack layers
- Shield board cards now have a pointed iron rim with rivets and a wood-and-iron shield badge. Legal-target outlines follow the shield silhouette, with a bright hover outline; Shield attack restrictions are unchanged.
- Attacking hero portraits and stat badges render after both mob rows in an explicit GUI stratum. Tool and power sockets remain anchored on the table, and damage effects remain above the portrait. Applies to both player and opponent attacks.
- IntelliJ build passed. These visual changes still need a live-match check after restarting the development client.

## Invulnerability visibility
- Replaced the faint outline with a translucent golden barrier, moving enchantment glints and a closed gold lock on affected board cards. Stats and target highlights draw above the barrier; Shield retains its separate pointed frame/badge.
- Hover status explicitly says the card takes no damage until its next turn. The animated client state clears invulnerability on the owner's TURN event so the indicator does not linger during queued start-of-turn damage.
- IntelliJ build passed; live-match visual verification remains outstanding.

## On-play previews and seven-card boards
- Hovering or dragging a hand card outlines matching board recipients in yellow, using catalogue trigger, side, mob/block and type filters. Existing copies of the played card remain eligible for "other" effects. Immediate attack auras are included; Drop/end-turn effects and generated cards are not incorrectly treated as on-play board buffs.
- Solid outlines show automatic recipients; broken gold corners show eligible random/chosen recipients. Active targeting retains its stronger target outline. Hand hover is resolved before drawing board rows so previews use the current frame's card.
- Raised the shared board cap to seven per player, including blocks. Existing summon, play and summon-power checks use the shared limit. Both row layouts now derive their width from that limit and reserve room for outer highlights.
- IntelliJ build and 50,992 Java assertions passed, including actual Drowned recipients versus preview recipients, type/side filters, seventh plays on both boards, eighth-card rejection without spending, items on full boards and capped multi-summons. Live Minecraft visuals remain unverified.

## Benefit, harm and ongoing-effect previews
- Added a shared read-only EffectPreview evaluator using catalogue effects plus visible health/status state. Yellow marks buffs, healing and protection; red marks damage, removal and harmful statuses. Full-health healing targets, existing protection/statuses and invulnerable damage targets do not falsely glow. Random/chosen/future effects use broken outlines; the selected valid target becomes solid with a white edge.
- Hero healing/damage previews follow the portrait shape. Enchantments highlight the equipped friendly tool; Lava Bucket highlights the opposing tool rather than its hero. Empty sockets do not glow.
- Hovering an existing board card previews its current aura and eligible end-of-turn recipients, including Composter, Furnace and Dispenser. These previews preserve side/type/block/self exclusions and never replay Spawn effects or consume randomness. Green readiness cues remain unchanged.
- IntelliJ build passed and Java checks passed 51,017 assertions, covering the added preview categories and the existing rule suite. Live Minecraft rendering remains unverified; restart the client to load the changed renderers.

## Persistent Totem protection effect
- Board cards carrying a Totem now show breathing emerald light and continuously rising green-and-gold motes along their edges, in addition to the existing Totem icon. The effect follows the card's transform during attacks and is tied directly to its Totem status.
- Applying a Totem gives a matching green-and-gold particle burst. Stat badges, Shield badges and targeting outlines remain above the effect. The emerald wisps are distinct from invulnerability's gold barrier/lock and do not add another readiness outline.
- IntelliJ build passed. Live-match visual verification remains outstanding.

## Frozen and Withered card effects
- Frozen board cards now have cyan ice coating, angular frost corners, branching ice cracks and drifting snow. Withered cards show a pulsing charcoal tint, creeping decay veins and rising ash/smoke.
- Both effects follow the card's animation transform and can render together. Stat badges, protection icons and targeting outlines remain above them. Their drawing is bounded and derived from the current status flags without accumulating particle objects.
- Client Frozen visuals now clear on the owner's TURN event, matching server status expiry. IntelliJ build passed; live-match visual verification remains outstanding.


## 2026-11-02 — Foundation mechanics, Alchemist and editing workflow
- Normal matches now start at 30 Health. Right-click a friendly block during your turn to confirm Dismantle for 1 XP; removal bypasses Drop and Totem. AI can clear spent blocks on a full board. Thorns remains an ATTACKED effect independent of ordinary block retaliation.
- Added Grazer, Battle-hardened and Schooling passives. Schooling buffs its source when another friendly Aquatic is played and expires only that temporary Attack at turn end. Added Cleanse for Frozen/Withered; cleansing a skipped freeze cannot grant a second attack. Frozen visuals now remain through the turn actually skipped.
- Added stackable keyword/type deck filters (OR within a group, AND across groups/search/XP/rarity), basic persistent per-class Mastery (100 points per level, maximum 30; win 25, completed loss 10 after turn 6, conceder 0; carried Common playset rewards at levels 2/4/6/8/10).
- Added Alchemist with Brew (2 XP: create a 0 XP Minor Harming Potion), eight starter collectibles, a tool and class enchantment. Runtime, bundled and Workshop starter catalogues contain 73 definitions / 62 collectibles. Preserved runtime-only Zombie Fisherman and all prior collector numbers. Runtime backup: run/config/craftbound/backups/cards.before-alchemist.20261102.json. Foundation target remains 180 pending a five-class allocation decision.
- Binder mini cards have a separate lower count footer, centered rarity gem, and attack/health badges on bodies/tools; items retain a type plaque. NEW sits at the top right. Native card item tooltips wrap at 160 GUI pixels while retaining bold/colour spans. These changes have not been visually checked in a live client.
- Match particles reuse vanilla particle textures for aquatic, potion, fire, healing, Ender, enchantment and status events in the GUI. They respect the particle setting and a bounded particle pool. They are GUI effects, not world particle entities.
- Added CHOSEN_CHARACTER (any allowed board body or either hero) for authored healing/damage, with server validation, client targeting, hero-shaped beneficial/harmful previews and AI selection. Cooked Porkchop template restores 4 Health to any mob or hero for 2 XP and excludes blocks. Other editable templates: Meadow Cow/Grazer, Vindicator/Battle-hardened, Shoal Leader/Schooling, Milk Bucket/Cleanse and Cactus/Thorns. Templates are not automatically added to the collectible set.
- Workshop launcher now starts a loopback Node helper. Normal Save/Ctrl+S always replaces run/config/craftbound/cards.json; Save as exports without redirecting normal Save. The helper validates against the current and bundled catalogue, keeps cards.json.workshop-backup.json, and uses temporary-file replacement. Authenticated requests, fixed asset routes, Origin/Host checks and 1 MB limit restrict access. No external packages. Reopen via Open Card Workshop.cmd in File Explorer; direct index.html supports exports only.
- Added live class XP curve beneath the Workshop preview, with optional Neutral inclusion and tokens excluded. In-game deck XP curve replaces the click/drag instruction footer, preserves the right-hand deck list and pager, counts every normal/foil copy, uses 0–6 and 7+ bins, and calculates the average from actual costs. Collection filters do not change the deck curve.
- Verification: IntelliJ build passed (existing test-only Unsafe warnings). Java suite passed 63,713 assertions, including 1,250 AI decks, new mechanics, Alchemist, both-side hero/mob targeting and deck curve counts. Workshop model and JavaScript syntax checks passed. Localhost save regression passed against temporary files only: repeated overwrite, previous-version backup, rejected-save preservation, token/origin/host restrictions and size limit. Launcher parsed successfully; browser launch and live Minecraft visuals remain unverified. The localhost test required running outside the tool's network sandbox.
- Restart the Minecraft development client for changed Java/UI rules, reopen the Workshop through its launcher, and use /craftbound cards reload between matches after saving card data.
- Still design-only: Choose One/tool offers and biome playmat unlocks. Shared custom-PNG artwork loading is not implemented; existing UI paths render the catalogue's Minecraft item-art selection. Alchemist is an eight-card starter pool, not a finished 30-card class set.

## 2026-11-02 — Workshop token references
- Token pages now show Used by beneath the basic fields, with clickable source cards and trigger/action/quantity details derived from the current draft. Navigation clears library filters; no edits are discarded.
- Built-in hero powers and the second-player Bottle o’ Enchanting bonus are shown as Other sources, including tokens with no card references. Non-token pages hide the section.


## 2026-11-02 — Clickable errors, class foundations, hero statuses and Mastery overview
- Workshop validation now retains card index, effect index and field metadata while keeping the existing string validation API for save compatibility. Every error is a keyboard-accessible button that navigates, clears filters, scrolls/focuses and highlights its field. Missing cards open a diagnostic view. Explicit repairs restore one/all missing originals, protected kind/token fields or collector order without replacing edits on existing cards; Save remains explicit.
- Checked the reported Alchemist errors against disk: the 73-definition runtime catalogue was valid with none of those cards missing. A stale recovered/imported draft was the likely cause; browser contents were not inspected.
- Added Miner (10 collectibles / 5 tokens), Chef (4 collectibles), and Tamer (4 collectibles / 1 token), preserving all prior IDs and collector order. Catalogue now 97 definitions / 80 collectibles. Added class colours, selection, AI decks, Workshop options, Mastery, Ore/Tameable types and token-source links. Runtime backups: cards.before-miner.20261102.json and cards.before-chef-tamer.20261102.json under run/config/craftbound/backups.
- Miner focuses on delayed resource conversion. Prospect creates Coal Seam; Mine consumes a friendly Ore block and triggers Drop without damage, retaliation or Totem resurrection. Coal yields temporary XP, Gold draws, Iron/Diamond improve tools. Stone Pickaxe mines after attacking. Dismantling Ore deliberately pays no resources. AI saves Coal until it can fund a larger card.
- Chef Feed uses the existing targeted action payload: click power then any non-block mob or either hero to restore 2 Health for 2 XP. Server checks target/turn/XP/once-per-turn before spending. Right-click/Escape cancels targeting. AI heals friendly wounded targets. Tamer Call Pup summons a 1/1 Tameable Wolf Pup for 2 XP and respects the seven-card cap.
- Cleanse is now a derived rules keyword, with bold text/hover explanation “Removes harmful effects”. Card text says Cleanse rather than enumerating statuses. Mixed hero/board Cleanse and Freeze are supported; Withered remains board-only. Milk template supports any allowed board body or hero. Chosen Play effect targets stay synchronized in the Workshop.
- Heroes track Frozen through the turn whose attack is skipped; it blocks weapon attacks even after equipping another tool. Cleanse clears it without resetting used attacks. Hero powers and card plays remain available. Hero snapshots/events include Frozen and portrait frost feedback. Existing board Cleanse behavior retains buffs, Totem and invulnerability.
- Added a read-only Mastery overview reachable from binder and deck builder, with all eight classes, levels, points, progress bars, next reward milestones and earning/reward tooltips. Supports scrolling, arrows and return to parent. Existing saved progress defaults new classes to zero; no combat bonuses.
- Verification: IntelliJ build and Java rules passed 101,725 assertions including 2,000 legal AI deck constructions. Workshop model, JavaScript syntax, save-flow and headless error-navigation checks passed; exact field focus, clearing resolved highlights and preserving edits during repairs were checked. Existing test-only Unsafe warnings remain. New visuals and live play have not been inspected in Minecraft. Restart the development client and refresh/reopen Workshop for updated code.
- Scope stays barebones for Chef/Tamer: no new mastery economy, full class set, or claim of competitive balance. Foundation target remains unchanged.

## 2026-11-02 — Enchanted shimmer performance
- Replaced the shared shimmer's nested 2-pixel fill loops with one GUI render state containing two clipped gradient polygons. At the full-size card's busiest sweep position the old path submitted 1,281 rectangles per card; the replacement submits one state with a bounded mesh (at most 32 vertices, observed peak 24 in checks). This removes the per-tile GUI state allocation, sorting and overlap processing from deck-builder pages, binder pockets, pack reveals and in-match enchanted cards.
- Retained the purple diagonal sweep, timing, rest period and caller-specific opacity/band width. The mesh clips in local card coordinates before applying a captured pose, preserving tilted/dragged cards; it also retains the current scissor and transformed bounds for GUI ordering. No texture uploads, new shaders or resource reload dependencies.
- Verification: IntelliJ build passed. Standalone ShimmerMeshChecks passed 294,456 assertions across full cards, mini cards, board-sized cards, enlarged cards and edge cases, checking bounded vertex counts, clipped area, gradient interpolation, opacity and vanilla GUI winding. This is a submission-count improvement, not a measured FPS result; live visual/performance verification remains outstanding. Restart Minecraft to load the changed client renderer.

## 2026-11-02 — Wider deck-builder collection and XP footer
- Collection columns now respond to available width at the height-limited card size, instead of stopping at four. Columns distribute across the collection area through its right edge; page capacity, card hitboxes and labels use the same layout.
- XP curve fills the collection width in a dedicated dark panel with a gold top separator and contrasting border. Reserved vertical space keeps card counts above it and page controls below it; chart bins and hover regions span the panel. Scrolling over the chart no longer changes the collection page.
- IntelliJ build passed. Live Minecraft layout remains unverified; restart the client to load the screen changes.

## 2026-11-02 — Combined collection and saved decks
- Right-clicking a binder now opens the collection/deck hub. My Decks supports up to 32 named lists per physical binder, New Deck class selection, editing, sleeves, clearing and two-click deletion. Existing album pages remain accessible and return to the hub. Existing deck cases remain supported; Import deck cases copies lists into the binder and skips identical imports. Legacy cases return their physical cards using the existing migration before import.
- Normal and Enchanted versions occupy adjacent collection slots, with Enchanted on the right. Even column/page sizes keep each pair together. Click/drag/inspect uses the exact selected variant; missing variants are dimmed. A single centered ×2 ownership label replaces crowded owned/foil/in-deck labels; single copies and Legendaries have no footer count. Exact owned/in-deck counts remain in hover text; NEW moved to the top-right corner. Both versions still share the normal deck copy limit.
- Cards remain physical binder contents. Add inventory cards files loose cards; Shift-click a collection slot withdraws one exact-variant card for trading. Deck lists never reserve, consume or create physical cards. Trading a needed copy makes every affected list unplayable until replaced. The overview shows the held binder's collection; deck editing/validation can use all carried binders as before.
- Added persistent/networked saved-deck component and validated binder editing packets using stable UUIDs. Match lobby lists saved binder decks alongside legacy cases and supports scrolling. Ready/waiting match state carries binder inventory slot plus stable deck ID, revalidating current ownership before Card Master arrival or player pairing. Names, sleeves and exact variants reach the match through the existing deck-choice path.
- Verification: IntelliJ build passed; regression harness passed 101,745 assertions, including saved component JSON/network round trips, edit/delete isolation, capacity, packet IDs/variants, shared ownership, trading/removing/restoring enchanted copies, and combined copy limits. Language JSON parsed successfully. No live client/server UI or multiplayer session was exercised. Restart Minecraft (and update/restart a dedicated server if used) for the component and protocol changes.

## 2026-11-02 — Retire physical deck cases
- Removed the deck-case crafting recipe and creative-tab entry. Test-deck command now supplies one binder with its owned cards and saved deck. Removed the manual import button and case-opening client entry point; migration is automatic.
- Existing cases retire on player join and once per second in player inventories, Ender Chests, open container slots and the cursor. Blank cases disappear. Meaningful lists merge into a carried binder, deduplicating matching imported lists; no-capacity/no-binder cases become a binder in the same slot. Names, variants and sleeves are preserved. Pre-list cases return their physical cards through the existing migration, or retain the exact card contents in the replacement binder. Lists never generate owned copies.
- Dropped cases convert when their item entity loads; an otherwise empty case with an old nonclassic sleeve becomes that sleeve. Unopened/unloaded storage is not force-loaded: its cases retire when accessed. Retained the hidden item registry and internal view-stack support so older saves can load safely. Saved equipped designs remain recognized by sleeve unlock migration.
- IntelliJ build and 101,756 regression assertions passed, including empty/named cases, exact physical-card preservation, no minted copies, deduplication and full-library fallback decisions. Source and built resources no longer contain the recipe. Runtime migration has not been exercised in a live world; restart the client/server to activate it.

## 2026-11-02 — Collection class tabs and card sizing
- Binder overview now has tabs for every class, Neutral and card backs. Tabs show separate N (normal) and E (Enchanted) unique-card counts; the footer spells out both totals for the selected class. Counts use the held binder and current collectible catalogue, exclude tokens, and never count an enchanted-only card as a normal unlock. Unowned class cards now remain visible as dimmed slots.
- Removed Album Pages access and the Browse Class picker from the hub. Class selection remains for creating/changing decks. Search moved to the My Decks sidebar, tabs wrap on narrow windows, and the collection footer was shortened to recover card space.
- Enlarged collection cards at common GUI sizes by tightening card/label spacing and choosing the nearest even column count. Normal/Enchanted pairs still stay together. Added a white outline on hovered collection cards, above dimming/shimmer. Inspection now caps at 1.25× and 68% of screen height in both the collection zoom and standalone card inspector.
- IntelliJ build passed. Numerical layout spot-checks at 640×346, 640×360, 854×480 and 480×270 kept cards/counts above the footer; live visual verification remains outstanding. Restart Minecraft for the updated client UI.

## 2026-11-02 — Feed release targeting and AI Totem waste
- Feed begins aiming on left-press of the hero power and sends its heal on left-release over a valid mob or either hero. A second click no longer commits the heal. Invalid drops, non-left releases, loss of action authority and unavailable powers cancel the gesture without sending an action; existing right-click/Escape cancellation remains. Server XP/target/once-per-turn checks are unchanged.
- AI targeting for chosen Play Totem effects excludes already protected units in both card-selection and target-selection paths. It can protect an eligible block, holds the item when no useful target exists, and can protect a unit again after its Totem is consumed. Normal/Enchanted cards share this behavior; player targeting rules are unchanged.
- Build and 101,783 regression assertions passed. Added both-side AI checks plus headless Feed-release target/cancellation checks, including both heroes, both mob sides, blocks, empty space and repeated release. Live pointer interaction has not been exercised; restart Minecraft to load these changes.

## 2026-11-02 — Useful healing/freezing targets and consistent stat pixels
- AI now filters chosen healing, Freeze, Cleanse and Totem effects against current target state and effect-specific mob/block/type restrictions. Full-health healing targets and already-frozen/skipping-freeze targets contribute no value; friendly/enemy intent is respected and there is no invalid fallback. Targeted items with no useful target stay in hand, including Freeze-plus-draw potions. Pure area/random/hero healing and status spells also require at least one useful recipient. Mob bodies can still be summoned without a useful battlecry target, and other useful effects on combined cards remain eligible.
- Extracted read-only effect candidate enumeration shared by effect execution and AI checks, keeping random selection exclusively in execution so AI inspection never consumes gameplay RNG.
- Replaced the differently scaled vanilla heart/procedural diamond pair with matching 9×9 code-native pixel art. Both use identical bounds, pixel scaling, outline thickness and existing number alignment; same-color row runs keep geometry bounded. This shared path covers board and hero attack/health badges.
- IntelliJ build and 101,843 regression assertions passed, covering both sides, normal/Enchanted healing, splash healing of blocks, no healing enemies, frozen/skipped-freeze heroes and mobs, expiry, and both icon grids. Existing test-only Unsafe warnings remain. Live appearance has not been checked; restart Minecraft to load the changes.

## 2026-11-02 — Excavate choices and equipped enchantments
- Miner now uses Excavate (2 XP, once per turn): explicitly choose to mine a living friendly Ore (remove it and trigger Drop), or equip a 1/2 Wooden Pickaxe with no extra ability. The choice panel disables Mine with no Ore, warns when equipping would replace a tool, highlights only eligible Ore, and supports right-click/Escape/cancel before spending. Server validates the target before charging; both choices share the power use and replacing a tool never refreshes hero attack.
- Added only power-generated token craftbound:excavated_pickaxe; collectible count stays 80, Miner collectibles stay 10, and all existing catalogue entries/collector numbers are unchanged. Appended token to bundled/runtime/browser-default catalogues. Runtime backup: run/config/craftbound/backups/cards.before-excavate.20261102.json.
- Miner AI mines available Ore when it has hand space, otherwise equips only if it lacks a tool. Full hand/board do not block the equip option. Dismantling is unchanged.
- Equipped tools retain a list of applied enchantment sources and stat increases separately from cosmetic CardRef.enchanted. Match snapshots and client copies preserve the list; ENCHANT events append buffs without recreating the equipped tool. Replacement clears old buffs. The socket has purple edging/glint for either enchanted state and a persistent rune for applied enchantments. Hover shows current Attack/Durability, scripted abilities, the cosmetic finish label, and aggregated applied enchantments with their increases.
- IntelliJ build, 101,934 Java regression assertions and Workshop model checks passed (98 definitions / 80 collectibles). Checks cover both sides, invalid targets/no spending, mining Drop without Totem resurrection, full hand/board equip, attack limits, AI choices, stacked enchantments, wire/client copies, current-stat text, Ore highlights and headless choice/Escape/right-click cancellation. Existing test-only Unsafe warnings remain. Test input uses this version's InputConstants (SDL Escape 41, left 1/right 3).
- Live rendered appearance/pointer play has not been verified; restart Minecraft to load client/server code and the updated catalogue.

## 2026-11-02 — Foundation mechanics, collection readability and display stands
- Added Harvest, On Kill, Card Added, Item Played, Mined, Healed and Dismantle triggers; observed events support side/type filters. Harvest and Dismantle stay separate from Drop. Added type-filtered Attack auras, negative Attack debuffs cleared by Cleanse, Shield/Sprint/Ranged grants, attached tool-enchantment triggers, random Give pools, board-count multipliers, Well Fed overhealing and transforms. Workshop exposes and validates these options; random-pool references follow renames and appear in linked-card lists. Existing collectible IDs, counts and authored card data were not changed.
- Ranged now prevents ordinary retaliation both while attacking and while defending; explicit Thorns/Attacked damage still works on blocks and mobs. Combat previews, AI trade estimates, granted-keyword state and rules text follow this rule. Shared 9x9 stat art now uses a sword for melee and a bow for Ranged.
- New event chains have depth/step guards. Successful hand additions, actual positive healing and mining are distinguished from burned cards, zero healing and dismantling. Trigger attachments survive snapshots and clear on tool replacement. Transforms preserve spent attacks and do not fire Spawn/Drop; temporary Attack expiry remains distinct from permanent debuffs.
- Collection class tabs no longer carry N/E totals; footer collection totals remain. Independent Normal/Enchanted toggles stack with the existing filters, preserve variant identity, reset paging and support both hidden. Class frame palettes now distinguish crimson Fighter, cobalt Builder, green Farmer, turquoise Fisherman, violet Alchemist, ochre Miner, orange Chef, rose Tamer and muted Neutral, shared across match/table/collection and Workshop previews.
- Full card rules panels grew from 39 to 51 layout pixels by shortening the art area. Card banners use the printed base name, leaving the cosmetic Enchanted prefix on inventory items; actual names such as Enchanted Golden Apple remain intact. Generated tool enchantment text omits zero-stat bonuses.
- Replaced the display stand's generic inventory-card rendering with a class-coloured face showing the same selected art item as the card UI, wrapped printed name, rarity gem and cost/stats. A matching backed wooden easel and renderer share a 22.5-degree rearward lean and explicit cardinal facing. Updated selection shape. Existing insert, inspect, sneak-remove, save/sync and break-drop behavior is preserved.
- IntelliJ build passed; Java regression suite passed 101,993 assertions. Workshop model, save, validation-navigation and new mechanics checks passed. Existing test-only Unsafe warnings remain. No live render/pointer verification: restart Minecraft for code changes and reload Card Workshop; stand orientation, text sharpness and palette still need visual confirmation in the game.

## 2026-11-02 — Shared display cards and automatic table seating
- Display stands now consume the same CardRenderer face layout as inspection/collection through CardCanvas. WorldCardFace translates that layout into world render commands, including artwork, printed name, full rules, collector number, rarity, type plate, stats, legendary frame and enchanted shimmer. Removed the separate stand-only card design; retained the corrected easel facing/lean.
- Table matches create two visible oak chairs on clear opposite sides and automatically mount both PvP players or the player and Card Master, facing inward. Both spaces need solid ground and two clear blocks. Occupied tables and unavailable seats reject a new match without starting it. Seats are released on finish/disconnect/shutdown; table loss, orphaned seats or unavailable participants end the match without rewards. Getting out leaves an empty chair, which removes itself and ends that table match.
- CARD_SEAT deliberately has no .noSave(): verified in the local 26.3 Entity.startRiding implementation that non-serializable vehicle types are rejected. Chairs remove themselves when empty or loaded without a live table binding; they have no item drop. Added a Card Master-specific seated-leg model because vanilla VillagerModel does not pose mounted legs.
- Added checkCardFaces (160 normal/enchanted collectible faces) and checkTableWorld (all seat directions, blocked-side selection/rejection and sitting/standing pose). Live placement, mounting and world rendering remain for the owner to verify.

## 2026-11-02 — Card Vendor
- Implemented docs/tasks/CARD_VENDOR.md: craftable two-half vendor, lower-half block entity, single lower-half loot drop, creative upper-half cleanup, upper-window light 7 and IMMOVEABLE piston response (26.3's name for the old BLOCK response). Placeholder model names remain card_vendor_lower/card_vendor_upper and use vanilla textures; added item definition, recipe, creative entry and translation.
- CardVendor centralizes prices, slot counts, probabilities, buy distance and restock timing. Packs cost 5 emeralds; singles cost 2/5/12/30 by rarity with Enchanted x3. Deterministic seed/position/Overworld-clock-day selection yields three Common, two Rare and one Epic/10% Legendary, 5% Enchanted per slot, without tokens or duplicate IDs. Missing rarity pools use nearest available rarities. Catalogues smaller than six unique cards show unavailable remaining slots rather than duplicates.
- VendorPurchases tracks each player's bought-slot mask for the current day, persists it in the block entity and discards older-day entries. Server rechecks distance, lower half, slot, day, expected card/finish, stock and split-stack emerald funds before charging/giving. Stale dawn/catalogue requests refresh without purchasing a different offer. Packs are unlimited.
- Screen has separate pack, 3x2 singles and larger hover-inspection columns, prices/emerald icons below cards, SOLD stamps, balance and restock countdown. No floating tooltips cover cards. Read-only refreshes occur while open and stop on close; updates do not reopen a closed screen. Buying uses SDL left-button constants.
- Verified compileJava, compileClientJava, processResources, checkCards, checkVendor, checkCardFaces and checkTableWorld via Gradle offline: build successful, 101,993 existing game assertions, 40,035 vendor assertions and the card/seat checks all passed. Vendor checks cover 2,000 daily lineups, pricing, rarity fallback, unique IDs, per-player/day persistence, invalid/stale requests, split-stack payment and actual 26.3 recipe/loot codecs. Existing JOML/test Unsafe warnings only.
- Did not change cards.json or match rules for the vendor. As requested in the brief, the owner will check live world loading, two-half breaking/placement, purchases and screen appearance. Spectating, miniature table battles, biome themes, villager professions and card halls remain future work.

## 2026-11-02 — Stand shimmer, permanent chairs and vendor polish
- Stand shimmer now submits in a separate later render order, with clearance above the printed face's glyph/item depth. Retains the bounded shared shimmer mesh and normal world depth testing. Added a regression for the overlay submission and balanced pose; live appearance still needs confirmation.
- Table placement creates two opposite chairs where ground/headroom allow. Chairs save their table binding and remain empty between matches; matches reuse them and release riders without deleting furniture. Table removal cleans them up. Maintenance waits for neighbouring entity chunks to load before creating chairs, repairs missing/unoccupied pairs, and avoids replacing occupied seats. Existing tables gain chairs when their lobby is opened. Restored riders are released because matches are not saved; dismounting during play still invalidates the match.
- Vendor success uses vanilla dispenser dispensing plus an amethyst chime; rejection uses a lower-pitched dispenser failure click. Daily singles remain per-player purchases against a shared per-machine lineup; packs remain unlimited.
- Connected the booster pack model to the supplied textures/item/foundation_booster_pack.png; its item ID remains unchanged.
- Gradle compileJava, compileClientJava, processResources, checkCardFaces, checkTableWorld and checkVendor passed. Includes 160 card faces, the new shimmer submission check, opposite-chair geometry checks and 40,035 vendor assertions. No live visual/audio or world-reload verification; restart the client/server to load these changes.

## 2026-11-02 — Casual chair seating and binder texture
- Card chairs are pickable and right-clicking an empty chair seats a player facing the table without opening a lobby or starting a match. Sneak uses normal dismounting. Server checks reject spectators, occupied chairs, unrelated vehicles, missing tables and active matches.
- Lobby/match setup accepts participants already seated at that table and preserves their chosen sides. Other occupants are never evicted, and failed setup leaves pre-existing sitters mounted.
- Linked the binder model to the supplied textures/item/card_binder.png; verified the item-to-model-to-texture chain and processed resource file.
- compileJava, compileClientJava, processResources and existing checkTableWorld passed. Live right-click seating and starting a match from a chair still need in-game verification after restarting.

## 2026-11-02 — Card Master knees, creative binder glint and pack tear
- Replaced the Card Master's straight rotated seated legs with separate thigh/shin joints. Thighs point forward and shins hang down; a shorter seated robe with a lap fold replaces the long standing jacket only while mounted. Vanilla standing legs and robe remain unchanged when walking.
- Creative binder presets now carry the vanilla enchantment-glint override. Existing unrenamed creative presets are recognized by their translated custom-name key and also gain glint; ordinary binders retain their normal appearance.
- At the user's choice, opening a booster pack now layers vanilla SHEARS_SNIP (volume 0.85, pitch 0.85) with BOOK_PAGE_TURN (volume 0.65, pitch 0.8) for a paper-tear cue. Per-card reveals retain their existing sounds.
- compileJava, compileClientJava and checkTableWorld passed. Pose checks verify both knee bends, vertical shins, robe visibility and restoration of the standing model. Live appearance and audio mix have not been verified; restart Minecraft to load the changes.

## 2026-11-02 — Token keyword inspection, Workshop saving and search
- Collection/deck inspection now retains and displays hover regions for both the main card and its related token previews, including keyword explanations. Standalone inspection keeps tokens open briefly while the pointer crosses the gap and accepts hovering during their slide, without exposing regions hidden behind the main card.
- Fixed class/Neutral tab clicks leaving the screen's focused-child reference inconsistent with the search EditBox. Also fixed the actual wrong-thread exception in run/logs/latest.log: creative search builds tooltip text on a worker, where font wrapping could bake/upload glyphs. Inventory tooltip wrapping now runs only on the render thread; search indexing keeps original text.
- The reported Workshop message came from a standalone page without its local save service. Added browser-file-picker Save: select the existing active cards.json once, then subsequent normal saves overwrite it. Imported/exported handles cannot silently redirect Save. Validate against the selected file's existing IDs/order/kinds/token flags before writing, preserve drafts on cancellation/failure, and abort failed writable streams. Unsupported browsers explain exporting the draft and importing it through the launcher. Updated help/README; launcher backups remain unchanged, standalone saves do not create an extra backup.
- Confirmed runtime reloads succeeded with 80 collectibles and no Danny in the on-disk catalogue. The user's actual Danny definition remains in their browser draft; it was not invented or written by this change. Save-flow regression uses a separate synthetic Danny fixture and verifies append, repeat saves, cancellation, wrong files, disk identity changes and write failure.
- compileJava/compileClientJava and checkCardFaces passed (160 faces); Workshop model checks and editor-save-checks passed. No live browser interactions or in-game hover/search checks performed. Refresh the original Workshop page after preserving its draft; restart Minecraft for client fixes.


## 2026-11-02 � Ordered effects, thrown Egg, faster cracks and automatic artwork
- Stone Sword retains ON_KILL. Complete attack exchange and casualty/Death events before tool kill triggers; emit SHOOT with 160-ms windup and 250-ms arrow flight before bonus DAMAGE, including the last durability charge. Append EFFECT enum (no ordinal shifts) and emit one marker per authored effect with results; 140-ms source pulse separates abilities while each area effect groups its targets. Removed queue-only catch-up speedup that could overtake scheduled projectile impacts/block removal.
- Egg PLAY throws an egg in an arc and bursts into shell/yolk particles before damage, for both players/spectators. Snow Golem's ranged attack throws a snowball. Block cracking 560->448ms and tail 120->96ms (20% shorter), with sound timing scaled too.
- Fighter now maps to CRAFT everywhere, equips required 1/1 Wooden Sword for 2 XP, replaces existing tool, persists unused durability, and animates sword creation. Required token appended to bundled/default catalogue; Java/Workshop migrate missing token without restoring discarded collectibles.
- Runtime cards backed up to backups/cards-before-art-and-snow-golem-20261102.json. Repaired 11 paper/invalid vanilla artwork IDs, retained card order/stats, and corrected Snow Golem AFTER_ATTACK FREEZE from FRIENDLY_MOBS to EVENT. Both validators and wording support this attacked-character target, including heroes; dead targets are skipped.
- Workshop name matching uses the actual local Minecraft 26.3 registry, aliases/size prefixes; manual art disables matching, custom IDs preserved. Checkbox opts back in. Draft recovery accepts external changes to untouched fields only, preserving unsaved work. Current host serves fresh assets and hot-loads model validators; reopen launcher for changed host GET/POST preparation.
- Validation: offline Gradle build passed; 122021 card-rule assertions, vendor/table/card-face checks. Added effect order/last-durability/no-kill/AoE/Snow Golem mob+hero regression checks. Node model/server checks passed. Isolated browser verified typing names, manual override/opt-in, target label, preview and saved JSON; screenshot reviewed. Live Minecraft visual/audio timing still needs playtesting after client/server restart.


## 2026-11-02 — Ignore Shield keyword and grant effect
- Appended IGNORE_SHIELD keyword and STATUS_IGNORE_SHIELD without changing existing ordinals. Printed mobs/tools can bypass Shield; tool benefit applies to hero attacks only while equipped. GRANT_KEYWORD supports giving it to mobs, with existing persistent grantedKeywords storage. No authored cards were changed.
- Server legality and client highlights/release targeting agree; dynamic state is copied, transmitted, animated and shown in board tooltips. Transform/replacement uses the new card's keywords; ordinary turn/readiness/Frozen/Sprint and damage protection remain enforced. Multiword text is bold/tagged for keyword inspection and centered in short previews.
- AI uses server attack legality and now prioritizes available lethal hero attacks before trades, including lethal through Shield. Updated Workshop choices, validation, generated descriptions and Design Reference.
- Offline Gradle build passed: 122041 rule assertions plus vendor/table/card-face checks. New coverage includes native and granted bypass, mob/hero targets, Shield retention, first-turn Sprint, Frozen, invulnerability, spent attack, pause persistence, network state, tool replacement, AI lethal and keyword styling. Workshop model checks passed; live Minecraft hover/animation still needs playtesting after restart.

## 2026-11-02 — Looting authoring and combined stat/keyword buffs
Fox tool-theft animation: BREAK reads its existing causal origin to identify an opposing Fox (ID or fox artwork), avoiding stale last-played guesses. In MatchScreen the Fox board piece darts to the tool, grabs at 260ms, carries the actual tool artwork back, then tucks it away by 820ms; vanilla fox/pickup sounds, bounded particles and top-layer motion. Enchanted tool art keeps its glint. Enemy tool/attack display clears at grab time; server destroy-tool rules are unchanged. Ordinary breaks retain existing behavior. Added bounded motion/grab/home sequencing and server event-source regression checks; build passed with 122131 rules assertions plus rendering checks. Visual timing not yet verified in a live match. User catalogue unchanged.

Server-join follow-up: local crash report 23:24:45 identifies TableBoardFace tool artwork calling placeholderArt with a missing definition. Shared artwork lookup now falls back safely for null definition/ID; tabletop redraw naturally retries catalogue lookup. Added exact unknown-tool table rendering and subsequent known-tool recovery checks; offline build passed, including 122128 game assertions. No live world/server join exercised. Latest client log also shows the pre-Foundation 98-entry local catalogue rejected; current Workshop has 40 active collectibles. Packaged validated current saved catalogue and fixed JAR under build/distributions/Craftbound-multiplayer-20261102-2330.zip, with server/client install and backup instructions. Did not overwrite live Minecraft config or world data.

Follow-up: removed the requirement for a dummy ENCHANT_TOOL effect on ability-only enchantments. Workshop/server validators now accept a non-Play triggered ability alone. Every enchantment still requires an equipped tool; implicit attachments record the visual enchantment and emit ENCHANT exactly once, while legacy explicit attachments remain compatible. Looting template now has one On kill / Draw effect. Tool preview, help and Design Reference updated. Offline build passed (122128 assertions); model checks and isolated browser save/reload passed. No live catalogue edits.

- Added editable Looting (2-XP Rare Fighter Enchantment) and Illager Rally templates. Looting uses the existing statless ENCHANT_TOOL attachment plus ON_KILL DRAW; no user-authored catalogue entries were changed. Generated text omits the redundant attachment sentence and keeps timing bold in the Workshop and game.
- BUFF accepts an optional supported keyword. Stats and the keyword resolve on the same chosen/random/area recipients within one effect boundary. Random recipients are selected once; count multipliers scale stats, a zero count skips the effect. Sprint readiness, target filters, validation, previews and AI usefulness remain enforced. This is stats plus keyword, not arbitrary nested action lists.
- Documented setup in Workshop README and rebuilt the in-app Design Reference. Offline build and model checks passed. New game checks cover chosen/random Illager filtering, Sprint restrictions, pause persistence, Looting attachment/no-tool/nonlethal/replacement/last-durability behavior, death-before-draw order and wording. An isolated browser verified both templates, keyword editing, preview, save and reload without touching live cards. Live Minecraft visual testing remains after restart.

Commander: four-seat format on Fabric 26.3. Generalize the existing server match rules for multiple seats; isolate lobby, networking and four-seat UI from standard duels.

2026-11-03: Commander is a separate four-player table mode: 20 singleton cards including a collectible mob commander, 40 Health, 21 combat damage per commander/opponent pair, repeat casting at +2 XP (cap 10). Four participants ready their own binder decks. Watchers receive no hands. Live sessions are not saved through a server restart. Foundation card data was not changed.

The binder reserves recessed pockets on collection, card back and board design pages even when empty. A filter drawer preserves space on short screens. The rulebook shares the binder cover, trim and parchment. Verified with offline Gradle build and Fabric client game tests, including empty collection, rulebook navigation, cosmetic pages and an actual client joining and casting its commander with three simulated players.

## 2026-11-04 — Trials expedition atlas

- Replaced the horizontal journal map with a large upward-scrolling parchment atlas: visible branching paths, clear open/current/cleared markers, larger boss icon and direct Current/Boss navigation. Full-size opponent rules and event text scroll in a side pane, with compact Details pages on smaller windows.
- New default acts use versioned, seeded BRANCHING_V1 topology. Two or three committed rooms lead to shared crossroads; every route converges on one final boss. Default paths retain seven fights and six service/event stops. Existing run snapshots and manually edited authored paths are preserved. Only the exact old bundled default act upgrades, backed up as trials.pre-branching-backup.json.
- The map is the run hub. Health, lives, Marks and charm/boon icons remain in the header. Deck hover groups copies by card identity and training; click opens the inspectable deck. Removed Journal navigation, kept retirement confirmation, and prevent map browsing from bypassing mandatory choices. Resizing retains the selected/current map area.
- Unvisited event titles and descriptions are hidden in the server view until committed entry. Accepted travel animates a boot marker along the selected path for 550 ms with page-rustle and arrival chime; input stays locked until arrival. Reachable nodes pulse gently and room arrival has a brief border glow.
- Workshop supports generated versus authored routes, keeps authored links when switching, validates generated constraints, and identifies the diagram as encounter templates. Updated player rules and project guides. User-authored cards unchanged.
- Validation: offline Gradle build and full Fabric client gametests passed. New server coverage checks 250 seeds, reachability, forced corridors, single boss, seed persistence, mystery privacy and exact-default migration. Real-client scenario completes draft, travel, shops, training, mysteries, all fights and first-clear cosmetic rewards. Wide1280x720/compact640x480 screenshots reviewed after resizing the actual test window. Workshop guarded save tests and isolated Chromium authoring/save test passed.

## 2026-11-04 — Trials map and collection polish

- Added wheel scrolling, empty-parchment dragging, full-route Fit and deeper zoom. Replaced map step buttons.
- New BRANCHING_V2 routes offer two to four staggered branches of different lengths. Saved runs and customized routes stay intact.
- Renamed visible currency to Emeralds without changing save keys. Earned boons have persistent individual item icons, hover details and overflow scrolling; capped bonuses report what was actually received.
- Added visual deck stacks with artwork, separate trained variants and green upgrades. Moved red Retire away from navigation. Fixed Pufferfish text and map label backing.
- Validation: offline build and isolated real-client gametests passed; reviewed wide/compact map, full-route overview, boon strip and upgraded deck screenshots. Workshop model, server and browser checks passed.

## 2026-11-04 — Solo multiplayer layout previews

- Approach: developer settings at the existing table open local snapshots in the actual PvP, Team Duos and Commander screens. No server match, inventory mutation or rewards. Empty, populated and full layouts plus seat selection support layout inspection with one client.

- Developer controls are granted by the server's UUID allowlist, defaulting to ZeElectron (4269df07-431c-4b6e-a5d4-d1b143c91872). Creative/OP status does not bypass it. An integrated development host exception supports IntelliJ test usernames; packaged clients do not get that exception.
- Preview settings support all three modes, three board densities and each seat. Actual match screens disable gameplay/network sends and return to settings with Esc. Catalogue updates close stale previews before removed card IDs can render.
- Verification: owner allowlist persistence/revocation/malformed-file checks and isolated Minecraft preview screenshots passed. Full-board views, private-hand perspective, inspection, no-deck access, permission visibility and unchanged table/inventory state covered at wide and compact resolutions.

## 2026-11-04 — Match JSON performance pass (Claude)

- No rule, wire-format or visual changes. Card rendering files were left alone (Codex was caching there).
- `MatchView.buildJson` / `publicBoardJson` return the JsonObject; `build` / `publicBoard` still return the same strings. `publicBoard` now builds a viewer-less view without the battle log instead of building player 0's full private view (hand playability checks, log) and re-parsing it. Output fields are unchanged.
- `MatchManager.sendAll` refreshes the tabletop once per update instead of once per player; `send` uses `buildJson` instead of build-then-parse.
- `TableSpectating.view` parses the public board once (was up to three times per spectator every 5 ticks) and uses `buildJson`.
- `MatchScreen` constructor/update parse each state payload once (was three times / twice).
- Not changed, could use the same API: `CommanderTables` and `RelayTables` still call `build`/`publicBoard` then re-parse.
- Validation: `compileJava compileClientJava` passed. No tests or live play run.

## 2026-11-04 — Hand effects: reveal, discard, steal, shuffle (Claude)

- New actions, appended to `CardCatalog.Action`: `REVEAL_HAND`, `DISCARD_FROM_HAND`, `STEAL_FROM_HAND`, `SHUFFLE_FROM_HAND`. They target `ENEMY_HERO` or `EACH_ENEMY_HERO` only, with no type filter or excludeSelf. Reveal is amount 1; the others take 1–10 cards.
- New optional effect field `chooser` (`HandChooser` enum: `YOU` (default), `OPPONENT`, `RANDOM`), allowed only on discard, steal and shuffle. The Effect record gained a final component, and the old 19-argument constructor still works.
- Rules (`Match.handEffect` / `moveFromHand`):
  - A human chooser in a two-seat match gets a queued pick (`pendingChoices`). The card's other effects resolve first.
  - While a pick is open: `yourMove` is false, `endTurn` is ignored, and the turn clock stops.
  - `MatchManager` gives each pick 600 ticks, then calls `resolveChoiceRandomly`. Pausing settles open picks at random first.
  - The Card Master picks instantly (`MatchAI.pickFromHand`).
  - Commander and Relay tables (more than two seats) always pick at random, because their screens have no picker.
  - A steal into a full hand loses the card. Shuffled cards go to a random spot in the deck.
- Network: action `CHOOSE_CARD = 10` (index into the victim's hand). New events `HAND_REVEAL`, `HAND_DISCARD`, `HAND_STEAL`, `HAND_SHUFFLE`:
  - The discarded card is public.
  - Revealed, stolen and shuffled cards are shown only to the two players involved.
  - DRAW events are hidden from everyone except the drawer, spectators included.
- The state JSON has a `choice` object. Only the chooser also gets `cards`, the victim's hand.
- Client: new `HandPickOverlay` (picker, waiting banner, 3.5 s peek) and play() animations in `MatchScreen`. `MatchScreen.handPicks` is created lazily because the screen checks build MatchScreen without running its constructor.
- Workshop: actions, a "Who picks the card" select, Cards amount, help text, and wording that matches Java.
- Checks:
  - New `HandEffectChecks`, run from `WorkshopChecks.main` (one line after `DamageHeroChecks.run()`).
  - New `tools/card-editor/hand-effect-checks.cjs`.
  - `checkCards`, `checkCardFaces` and all 16 Workshop Node checks pass. Not playtested.

## 2026-11-04 — Status explanations and generated-card inspection

- Bad Omen and Wither/Withered are bold, hoverable rules words. Wrapped and scaled card text keeps its hover regions; matching rules pages explain targets, timing, stacking and Cleanse. Bad Omen explanations name the linked raider. Status keywords inferred from effects do not add duplicate standalone sentences.
- Inspection discovers explicit card outcomes from every effect timing, including end-turn GIVE (Mooshroom -> Mushroom Stew), summons, transforms, random pools and Bad Omen. Missing/deleted outcomes are excluded and duplicates are collapsed. Authored cards and game mechanics are unchanged.
- Standalone, binder/deck and multiplayer inspection show Creates previews with paging and keyword explanations. Match hover previews include a compact companion pane with generated-card keyword notes. Multiplayer inspection stays inside its live screen so state updates continue. Preview controls consume their input without acting on cards or pages underneath.
- Association lookups are cached in gameplay; text styling reuses a compiled keyword pattern. Pin hints appear only in the inspector that actually supports pinning.
- Validation: offline checkCards passed 124883 assertions; final checkCardFaces and jar passed, including status aliases, generated wording, linked raiders, wrapped/scaled hover regions, cache stability, end-turn/random outcomes and deleted/missing associations. Build logs: build/status-explanations-check.log and build/status-explanations-final.log. No new Minecraft client launched due to the recent PC lockups; live layout verification remains after restart.

## 2026-11-04 — Eye of Ender hand-reveal animation

- Eye of Ender now arcs from its played-card position to the opposing deck, trails teal/purple pixels, hovers with two scan pulses, then gives way to a staggered back-to-face hand reveal. Uses vanilla Eye launch/death and amethyst chime sounds.
- Driven by the causal EFFECT followed by HAND_REVEAL, so revealed card IDs cannot accidentally trigger a throw. The event queue waits for the Eye before showing the hand; the 420ms flip is added to the existing reveal window. Empty hands still show an explanation. Later reveals reset their content/style/timer.
- Private hand peeks remain caster-only; spectators get the public throw but no hidden cards or misleading empty peek. No rules, authored cards or server privacy changes.
- Fixed render budget: at most 48 ring fills and one Eye item per frame, 12 capped impact particles, no new textures or world particle entities. Endpoint inset keeps scan rings on screen.
- Validation: offline checkCards passed 124883 assertions; checkCardFaces passed including new HandRevealChecks and EyeOfEnderChecks (timing, batching, empty/reset states, causal matching, transforms, draw budget and vanilla sounds). Final checkCardFaces/jar passed after bounds adjustment. Logs: build/eye-of-ender-check.log and build/eye-of-ender-final.log. No additional client launched because of recent PC lockups; live visual verification pending restart.

## 2026-11-04 — Compact in-match generated-card preview

- Generated cards now sit immediately to the right of the inspected card, at 60% of its scale (capped at .48), with no dark panel or border behind them. The source shifts inward near the screen edge so the pair fits.
- Removed the single-card 1/1 footer and permanently expanded token keyword notes. Token keywords still show their explanations on hover; multiple outcomes retain arrows and mouse-wheel paging.
- Source keyword notes use a separate column, avoiding the token. Invisible interaction bounds and the hover bridge remain so moving onto the smaller card does not close it or click through.
- Validation: offline checkCardFaces and jar passed; build/token-preview-layout.log. No new Minecraft process launched; live appearance remains to be checked after restart.

## 2026-11-04 — Eye of Ender targets the hand

- Redirected the Eye flight and impact to the middle of the affected player's hand, averaging the central cards for even hands and retaining an empty-hand fallback. The lower vertical inset keeps the Eye over the partially offscreen hand instead of below it. Reveal ordering and privacy are unchanged.
- Validation: offline checkCardFaces and jar passed (build/eye-hand-target.log). No additional game client launched; live visual check pending restart.

## 2026-11-04 — Unrestricted Workshop animations and ordered hand choices

- Workshop shows play, arrival and death animation selectors for every card kind. Added selectable Eye, Arrow, Fox dash, block placement/cracks, and reusable legendary/Warden/Wither/Dragon entrances. Explicit selections override name/art/kind inference and survive save/load; explanations distinguish cosmetic choices from effects and the lifecycle needed to use each visual.
- Cast animations now run during PLAY, independently of REVEAL_HAND. Eye of Ender flies to the opponent's hand before reveal, discard, steal or other hand results; it no longer depends on an EFFECT event that interactive choices do not emit. Opponent playback extends its beat from the actual delayed launch, even after a stalled frame.
- Hand picker/waiting banner and countdown wait for event playback. Ordinary private reveal peeks remain independent, and between-frame updates invalidate stale picker input.
- Hero Health is centered atop portraits. Power XP sits centered below local power buttons and above opposing ones; multiplayer seat orientation is preserved and name labels clear the new badges. Tabletop hero Health follows the top-centered position.
- Validation: offline checkCards passed 124883 assertions; checkCardFaces and jar passed, including new cross-kind animation/save compatibility checks and picker timing/input lifecycle checks. Workshop syntax, animation, catalogue, mechanics, hand-effect and save Node checks passed. No additional Minecraft client launched due to prior PC lockups; live visual verification remains after restart.

## 2026-11-04 — Block Health auras, return-and-copy and deck selection

- Added optional auraKind (MOB default, BLOCK, BOTH) to continuous auras. Health bonuses work on blocks, exclude the source, stack, and expire on removal; Attack bonuses targeting blocks are rejected. Extended auto descriptions, previews, recipes and Workshop controls/help.
- Added RETURN_AND_COPY: snapshot the selected board card, return it using normal rules, then create the requested extra copies. Enchanted finish and persistent Trials training survive; combat damage/buffs/statuses do not. Uses ordered BOUNCE/GIVE events, normal hand limits and card-added triggers. AI avoids knowingly burning extra copies.
- Added CHOOSE_FROM_DECK: sample up to three distinct eligible mob/block IDs without changing deck order, privately offer actual copies, draw the selected entry, preserve variants/training, and leave unchosen cards in the deck. Optional kind/type filters, empty pools, AI choices, full hands, queued/stale offers and 30-second fallback are handled.
- Reused the card picker for four-seat screens and added authorized choice routes for Commander and Team Duos. Pending choices hold team readiness and turn timers; other players and spectators receive no offer identities. No authored collectible cards or IDs were changed.
- Validation: offline single-worker checkCards passed 124938 assertions, including new engine/privacy/variant/aura/save tests; checkCardFaces passed including four-seat picker lifecycle; jar built successfully. Workshop syntax and block-utility, aura-class, hand-effect, mechanics and catalogue Node checks passed. Log: build/block-utility-final.log. No Minecraft client launched; live visual testing remains pending.

## 2026-11-05 — Fixed multiplayer match screens

- Team Duos keeps the local player and partner side by side against both opposing fields. Commander keeps the home field and dock fixed while the three public rival tabs switch the field above. Switching tabs while aiming preserves the selection. The actual target click commits the move.
- The hero, tool and power remain grouped at each seat. Commander cast cost sits beside the hero. The top strip shows the turn, timer and team Health or rival portraits. A TNT fuse burns on the center seam during the warning period. The hand fans into the space between the local resource bank and Ready/End Turn. Battle log expands above its corner button. Escape opens the live match menu with a confirmed concession option.
- Four-seat snapshots include only public attacker/defender endpoints for a short attack trail. Offscreen Commander attackers or targets use the corresponding rival tab. The existing privacy-filtered log supplies history. Updated preview instructions and the in-game rulebook.
- Offline single-worker checkCards passed 124940 assertions, checkCardFaces passed, the client game scenarios compiled, and the jar built. Full rows and hitboxes were checked at 320×240, 640×360, 1280×720 and 960×320 GUI dimensions. The real-client scenario now covers selecting a targeted card, changing rivals, then committing the target. Live Minecraft playtesting remains pending. See build/fixed-match-layout-final.log.

## 2026-11-05 — Multiplayer hand, table leather and Trials launch fix

- Team Duos and Commander again show XP orbs, filled/spent and bonus states, a hand badge and a sleeve-backed deck pile for the local player and visible remote seats. The local XP meter previews the cost of a hovered or dragged card.
- The multiplayer hand uses the same circular fan geometry as the duel. End cards sit lower and tilt outward. Hover raises the card, right-click inspects it, and drag/target input uses the visible rotated card. The hand stays between the resource bank and turn button with a separate battle-history strip.
- The solo leather texture is a 32×32 opaque RGB sprite. The long 3×1 Duos table now uses one 96×32 stitched teal/russet design. The 3×3 Commander table uses one 96×96 plum design with a four-seat brass seal. Both are stored in padded 128×128 RGB PNG atlases at 32 texels per block. Placed sections use matching UV windows that rotate with their chairs. Inventory models sample the complete designs. The procedural source is tools/art/generate_table_leather.py.
- The 22:53:57 dev-client crash reported a missing TrialsScreen during packet handling. Gradle rewrote the class at 22:53:58 while the game was running. IntelliJ's Minecraft Client configuration now uses a private mod jar staged by runClient. Normal compilations can replace build/classes without removing a class from a running game's classpath.
- Verified the actual runClient classpath excludes mutable mod outputs, resolved the receive handler and all 17 Trials screen classes, forced a full ordinary client recompile, and repeated the load with an identical play-jar checksum. Offline checkCards (124940 assertions), checkCardFaces, checkTableWorld, compileGametestJava and jar passed. Reassembled both tabletop textures from their model UVs in all four facings and checked the whole-top item models. No additional Minecraft process was launched. Logs: build/table-leather-fan-final.log and build/trials-classpath-verification/check.log.

### Four-seat card hover previews

- Duels, Team Duos and Commander share the full hover card, keyword notes and pageable generated-card previews. The four-seat boards anchor these to the hand fan, mob and block rows, equipped tools, hero powers and the Commander slot. Tool previews use current Attack, Durability and applied enchantments.
- checkCards and checkCardFaces passed. The four-seat hover regression scenario compiled, and the playable JAR contains the shared renderer. The in-game scenario was not run. Build log: build/multiplayer-hover-final.log.

## 2026-11-06 — Team Health and Commander attacks

- Team Duos shows Ember and Tide's shared Health at the center of the corresponding portrait bands. The felt has a light team tint and sage corner marks indicate the active half. Shorter player labels and a reserved central space keep the hearts clear of resource panels and a full hand.
- Commander distinguishes the active player with a pulsing sage edge and the viewed rival with a gold underline. Public attacks show a named caption and a directional trail. Hidden participants are marked on their rival tabs. The developer Commander preview includes a local Preview attack button.
- Offline checkCards, checkCardFaces, compileGametestJava and jar passed. The headless checks cover mirrored team badges, clearance for a ten-card hand, nameplates and XP panels at four sizes, and attack anchors for visible and hidden rivals. The live multiplayer client was not run. Build log: build/team-hearts-commander-combat.log.

## 2026-11-06 — Deck Cases removed, practice rewards, vendor model (Claude)

- **Deck Cases are gone.** Removed the `deck_case` item, `DeckCaseItem`, `DeckCaseRetirement` (the old-case converter) and its checks, the item model and lang entry. Also removed the five held-case network messages: `ClearDeck`, `DeckEdit`, `DeckClass`, `DeckSleeve` and `DeckRename` payloads. Any Deck Case still in a world now disappears on load.
  - Saved decks are now carried by `DeckBuilding.deckStack()`, a temporary stack that is never given to a player. `DeckBuilding.whyCantPlay` now checks that the stack has `DECK_CONTENTS` instead of checking for a Deck Case.
  - Deck names come from `DeckBuilding.name(stack)`. `MAX_NAME_LENGTH` (32) moved there.
  - The `BinderDeckPayload.IMPORT` action is no longer handled. `BinderDecks.resolve` with an empty id returns empty.
  - The deck editor is still called `DeckCaseScreen`; I didn't rename it.
  - Lang: a deck with no class is now "New Deck", and the no-deck, too-small and equipped messages no longer mention Deck Cases. The full_deck advancement icon is now the binder.
  - Sleeves are kept: they're still an item and a collection.
- **Practice rewards.**
  - Practice is any duel with an AI seat. It pays reduced Class Mastery only: 10 for a win, 4 for a loss.
  - PvP keeps 25 and 10. The constants and `matchPoints` are in `MasteryProgress`.
  - Practice wins no longer award a card back. Card backs now come from The Trials, and later from village Card Masters. `SleeveCollection.awardVictory` is kept for that.
  - The rulebook's Mastery and card-back pages are updated, plus a new check that practice pays less.
- **Card vendor model.** Both halves' thin front panels now have explicit texture coordinates within 0–16. Before, the upper half failed to load ("Cannot compute translucency out of bounds"). The hidden back faces were dropped.
- **Checks:** run in a copy of the tree, because `MultiplayerBoardScreen` didn't compile at 09:30. Codex's Commander log there uses `org.lwjgl.glfw.GLFW.GLFW_KEY_*`, which isn't on the 26.3 classpath; use `InputConstants.KEY_*`. With only that patched in the copy, compile including gametests, `checkCards` (124,940) and `checkCardFaces` all pass. Not playtested.
- **Vendor pack stock (owner's call):** each player can buy 3 booster packs per vendor per day, restocking at dawn with the singles (`CardVendor.PACK_LIMIT`).
  - The count lives in bits 8–11 of the existing per-player daily record. Bits 0–5 still mark the singles, so old saves and the `VendorStatePayload` are unchanged.
  - The vendor screen shows "N left today" under the pack, and "SOLD OUT" / "Restocks tomorrow" once the player has bought all three.
  - New checks are in `VendorChecks`; `checkVendor` passes (40,043).
  - At 09:37, `checkCards` stopped at `BlockUtilityChecks.multiplayer`, then at `CommanderChecks`. Codex's in-progress change of `CommanderMatch.DECK_SIZE` from 20 to 40 doesn't match all its checks yet. That's not caused by this work.

## 2026-11-06 — Commander deck preparation and battle history

- The 1v1, Team Duos and Commander tables filter saved binder lists by their persistent format. Untagged older lists default to 1v1. Copy cards in a fresh deck copies compatible, owned cards from another list in the same binder under the destination format’s rules.
- Commander creation starts by selecting an owned collectible legendary mob. A Neutral leader allows a choice of class. Class-specific leaders select their own class. The command zone has a fixed, gold-edged row above the scrolling deck. Forty different cards include the commander. Server joining checks the saved leader, ownership and format. Incomplete lists can be saved.
- Commander’s public journal records actions and outcomes as they happen. It groups a full history into rounds and seat turns, including after elimination, and streams bounded batches to players and spectators. The round arrows and Details toggle give access to prior plays, targets, damage, healing, statuses and commander damage. Hidden hand identities stay private.
- Tool and power socket outlines render under the badges. The full offline Gradle check, client game-test compilation and JAR build passed. Coverage includes legacy saves, format and leader persistence, deck copying and limits, round rollover, private events and journal packet round-trips. The changes were not playtested in a running client. Log: build/commander-decks-and-journal-final.log.

## 2026-11-06 — Hand hover on linked cards

- Duels, Team Duos and Commander resolve the actual hand slot first. Enlarged hand art no longer holds a linked preview open. A brief directional handoff lets the pointer reach the generated card and its paging controls.
- The offline checks and JAR build passed. The client regression scenario for moving from a linked card to a neighboring slot compiled but was not run in Minecraft. Log: build/linked-hand-hover-check.log.

## 2026-11-06 — Card Master trades to survive

- Before attacking, the AI checks for a win with its ready attackers. When the opposing board threatens lethal next turn, it can sacrifice a mob, combine attackers against one threat, or clear several threats. The estimate uses visible mobs, tools, Shields, armor, Freeze and Wither.
- Match-engine checks cover defensive trades, multiple kills, Shield bypass, and taking immediate lethal. The offline Gradle checks, game-test compilation and JAR build passed. Build log: build/ai-board-lethal-check.log.

## 2026-11-06 — Repair tools and Shield blocks (Claude)

- New tool keyword `REPAIR` (appended to Keyword). With a Repair tool equipped, the hero can target one of its own damaged blocks. This heals the block by the hero's attack, capped at max Health. It uses the hero's attack and costs 1 Durability, and the last use breaks the tool. It deals no damage and takes no retaliation. The tool still attacks enemies normally. The ATTACK event carries `MatchEvent.ATTACK_REPAIR` (extra=1); MatchScreen plays a lunge with an anvil sound for it. `Match.heroRepairs`, `Match.repair` and the own-side branch in `whyCantAttack` hold the rules. In MatchScreen, `isAttackTargetFor` highlights damaged own blocks. Codex: please keep both MatchScreen spans when editing that file. MatchAI does not use Repair yet, and MultiplayerBoardScreen doesn't offer own-block targets.
- Blocks can now have Shield, printed or granted. The old strip in CardCatalog/model.js/editor.js and the exclusions in Minion.has, Match.grantKeyword and CardEffects.matches are removed. A Shield block still taunts, and it can be attacked past the front row by melee. CombatTactics line ~60 still assumes blocks behind a front row are unreachable without Ranged; Codex may want to update it for Shield blocks.
- Checks: RepairToolChecks, plus the updated "Blocks can gain Shield" check in WorkshopChecks. Offline checkCards/checkCardFaces/checkVendor pass. Workshop Node checks pass except `checks.cjs:24`, which expects 101 cards; the committed test-cards.json has 115, so this assertion was already stale at 3285fbd. Not playtested in a client.

## 2026-11-06 — Binder formats, Egg throw and Card Master board control

- My Decks in a carried binder opens on 1v1. Switching to 2v2 or Commander filters the saved lists and sets the format for New Deck. Editing returns to the same category. Tables keep their own mode.
- Egg travels straight from the casting hero to the chosen target. Damage follows the 175 ms flight in the duel animation queue. The shell and yolk splat fades over 270 ms. Four-seat boards also use the original public target position, including when a unit is removed.
- The Card Master builds an opening curve from its legal card pool. It weighs combined attacks, the cost of a sacrifice, threats to valuable allies, repeatable block effects and finishing a mob with targeted damage. Immediate wins and immediate survival take priority.
- Offline Gradle checks and the playable JAR build passed. In 108 paired headless matchups using the active Workshop catalogue, the previous and updated AI swapped seats on identical decks and seeds. The updated AI won 137 of 216 matches. Visual changes have not been playtested in a running client.

## 2026-11-06 — Random keyword grants, no duplicate grants (Claude)

- Grant keyword accepts `"keyword": "RANDOM"`. The Workshop lists it as "Random (one it doesn't have)". It is stored as the new `Effect.randomKeyword` record component; a 20-arg constructor keeps old call sites working. It rolls one of Shield, Sprint, Ranged or Ignore Shield that the target lacks. Blocks only roll Shield. Rules text: "Give X a random keyword it doesn't have". Only Grant keyword may use RANDOM; Buff may not.
- A Grant keyword effect can't target something that already has the keyword, or has every option for Random. `CardEffects.grantOptions` holds the rule. `canTarget(card, target, has)` uses live keywords: Match.validTargets uses Minion::has, and MatchScreen uses the mob's shield/sprint/ranged/ignoreShield flags. Random and all-targets grants skip those cards in `possibleTargets`. A card with other chosen effects (e.g. a buff) can still target them.
- One-line MatchAI change (line ~147): the GRANT_KEYWORD usefulness check now calls `CardEffects.grantOptions`, so the AI understands Random.
- Checks: KeywordGrantChecks.java and keyword-grant-checks.cjs. Offline checkCards/checkCardFaces/checkVendor pass, and all Workshop Node checks pass except the stale 101-card count in checks.cjs:24. Not playtested in a client.

## 2026-11-06 — Workshop rule explanations must cover every mechanic (Claude)

- Owner rule: any new keyword or mechanic must also get a rule explanation in the Workshop's "Browse rule explanations" panel. Add it to `GUIDE` in tools/card-editor/model.js, in the section for its kind: trigger, action, target, passive, keyword or chooser.
- The browser gained Keywords and "Who picks (hand effects)" sections. Keyword tick boxes show their explanation on hover. Explanations are filled in for Repair, Shield on blocks, the Random keyword grant, and the four hand actions plus their choosers, which were missing.
- `tools/card-editor/guide-checks.cjs` fails when anything in KEYWORDS, TRIGGERS, ACTIONS, TARGETS, PASSIVES or HAND_CHOOSERS has no explanation. It also fails when a Java Trigger/Action/Aim/Passive value is missing from the Workshop lists; DISMANTLE is exempt as retired.

## 2026-11-06 — Negate keyword passive (Claude)

- New passive `NEGATE_KEYWORD`, appended to the Passive enum, with a card field `"negateKeyword"`: SHIELD, SPRINT, RANGED or IGNORE_SHIELD. It is stored as the new `Entry.negateKeyword` component; the old 14-arg Entry constructor still works. While the card is on the board, enemy mobs and blocks lose that keyword, even if printed, trained or granted. Rules text: "Enemy mobs and blocks lose X while this is in play." The intended use is Soul Sand negating Sprint.
- `Minion.negatedKeywords` makes `has()` return false. `Match.refreshNegations()`, run at the end of `refreshAuras()`, recomputes it. A fresh, unattacked Sprint mob loses `ready` when negated and regains it when the source leaves. `grantKeyword` keeps a negated grant but doesn't ready the mob. New `MatchEvent.STATUS_NEGATED` (11, extra = Keyword ordinal); MatchScreen clears the flag and floats "No Sprint" etc. Restoring sends the normal STATUS_SHIELD/SPRINT/RANGED/IGNORE_SHIELD events.
- AI and CombatTactics see negation through `Minion.has`. No AI-specific changes.
- Workshop: Passive "Negate keyword" has a "Keyword enemies lose" picker. GUIDE.passive and GUIDE.negateKeyword explain it, and passive recipes carry negateKeyword. Checks: NegateKeywordChecks.java and negate-keyword-checks.cjs. All offline Gradle checks pass. Not playtested in a client.
- Follow-up: Negate keyword now reuses `auraKind` and `auraType` to choose which enemies it hits: mobs, blocks or both, optionally one mob type. auraKind defaults to BOTH for this passive. The Workshop shows "Negate affects" and "Negate affects this mob type". Rules text follows the choice, e.g. "Enemy blocks lose Shield…" or "Enemy Undead mobs lose Sprint…". Covered in NegateKeywordChecks and negate-keyword-checks.cjs.

## 2026-11-06 — Pick one action (Claude)

- New action `PICK_ONE`, appended to Action. It uses the effect's `pool` (2 or 3 different card IDs) and targets SELF. When it triggers, the owner chooses one option and it is added to their hand via `giveCard`; a full hand burns it as usual. Rules text: "Pick one of [A, B, C] to add to your hand". Example: a Cauldron block with End of turn → Pick one of three potion tokens.
- Built on the HandChoice queue: `Match.pickOne`, `HandChoice.pickOne()`, `choiceCards` returns the offers, and `resolveChoice` gives the pick. The 30-second timeout picks at random. The AI and team tables (`interactiveChoices()` false) pick at random immediately. HandPickOverlay title: "Pick one to add to your hand".
- Workshop: "Cards to choose from (Ctrl-click 2 or 3)" multi-select. Options show as linked cards, and GUIDE.action.PICK_ONE explains it. Checks: PickOneChecks.java (seat-aware because of the coin toss) and pick-one-checks.cjs. Offline Gradle checks pass. Not playtested in a client.

## 2026-11-06 — Elements, effect boosts and Amplify element (Claude)

- **Elements.** New `CardCatalog.Element` enum (append only): FIRE, FROST, WITHER, POISON, LIGHTNING, EXPLOSIVE, WATER, MAGIC. Optional card field `"element"` on any kind, stored as `Entry.element`. Auto text starts with "Fire." etc.
- **Boosts.** Optional effect field `"boost": {when, card, kind, type, how, amount}`, stored as `Effect.boost` (a `CardCatalog.Boost` record; the 21-arg Effect constructor still works). `when` is one of YOU_CONTROL, ENEMY_CONTROLS, ANYONE_CONTROLS, TARGET_IS, TARGET_FROZEN, TARGET_WITHERED or TARGET_DAMAGED; TARGET_* conditions are checked for each target. `how` is DOUBLE, TRIPLE or PLUS (amount 1–20). Boosts are allowed on `CardCatalog.BOOSTABLE`: Damage, Tool damage, Heal, Well Fed, Buff (both stats), Weaken, Draw and Gain XP. Draw and Gain XP can't use target conditions. Text adds a bracket, e.g. "Deal 3 damage to enemy mobs and blocks (doubled if you control Netherrack)." `CardEffects.boostValue` and `boostHolds` hold the logic.
- **Amplify element.** New passive `AMPLIFY_ELEMENT` with card fields `ampElement` and `ampHow` (DOUBLE/TRIPLE/PLUS; PLUS uses passiveAmount). Damage from effects on the controller's cards of that element is changed by `CardEffects.amplify`: bonuses add, then the biggest multiplier applies once. It applies after the boost. Mob and hero attacks are not affected. Text: "Your Fire effects deal double damage."
- Workshop: Element select on every card; Amplify fields; a "Boost (optional)" block on boostable effects using `data-boost` inputs that edit `f.boost`. GUIDE has `element` and `boost` sections and the AMPLIFY_ELEMENT passive; guide-checks.cjs covers them, and the browser lists Elements and Boosts. Java and JS wording must stay identical: `CardCatalog.boostText` and `model.js boostText`.
- Checks: ElementBoostChecks.java covers Flint + Netherrack 3→6, enemy Netherrack not counting, Amplify ×2 stacking with the boost, Frozen ×3 per target, +2 vs Undead, and validation. element-boost-checks.cjs covers wording and validation. AI and client damage previews don't account for boosts yet. Not playtested in a client.

## 2026-11-06 — Row-limited Shield on blocks (Claude)
- Shield on a block no longer works as a full taunt. New rule, in `Match.shieldStops(board, defender)`. A front-row mob with Shield still guards everything, including Shield blocks. A block with Shield guards only the hero and other blocks. Enemy front-row mobs stay attackable, and the Shield block itself still needs the front row cleared or a Ranged attack. The old exception for Shield blocks in the front-row check of `whyCantAttack` is gone. New failure reason: "a block with Shield must be attacked first".
- Ignore Shield gets past both kinds of Shield, as before.
- The client mirror is `MatchScreen.shieldStops(Mob)`, which replaces `enemyHasShield()`. `CombatTactics.exposedAllyValue` uses `Match.shieldStops` to judge which allies a threat can reach.
- Shield text is updated in lang and in the Workshop GUIDE. Tests: new RowShieldChecks. RepairToolChecks' Shield-wall assertions are flipped to the new rule.
- Known approximation: `CombatTactics.Incoming.faceDamage` still treats every friendly Shield as absorbing attacks before face. It doesn't account for a Shield block being out of melee reach behind the front row, so the AI slightly overestimates incoming face damage. That's conservative, so it's fine for now.
- Follow-up: Shield explanations rewritten everywhere for the row-limited rule.
  - Lang `keyword.craftbound.shield.desc` drives the hand, binder, board and inspect tooltips.
  - Workshop GUIDE text.
  - Rules book (RulePage): ROWS, the SHIELD page (now 4 paragraphs, split into a mob part and a block part) and BLOCKS. The BLOCKS page no longer says blocks can't gain Shield.
  - The Shield row in the Workshop `reference.html`.

## 2026-11-06 — Element type labels on actions, Ender element, per-kind frame folders (Claude)
- An Action (CardKind.ITEM) with an element shows the element on its bottom type label instead of "Action", like a spell school (`CardRenderer.kindLabel(id, card)`, used by the full face and the mini card). Actions without an element still say Action. For actions, the auto rules text no longer starts with "Fire."; other kinds still print the element first. Java `CardCatalog.description` and model.js `description` match.
- `Element.ENDER` appended (enum is append-only), plus `Element.displayName()`. The Workshop has ELEMENTS + a GUIDE entry for Ender, and a GUIDE.element.LABEL note explaining the label rule. The Workshop preview shows the element on an action's type plate.
- CardFrames now loads `textures/gui/card/base_<kind>/base_<kind>_<rarity>.png` (the owner moved the mob frames into `base_mob/` and added `base_enchantment/base_enchantment_common.png`). The old shared `base/` folder is still read as a fallback.
- Tests: ElementBoostChecks and element-boost-checks.cjs now expect no element prefix on actions, and Ender on other kinds.
- Follow-up (same day): card frames are now **one base per kind + one gem per rarity**.
  - **Base:** `textures/gui/card/base_<kind>/base_<kind>.png` has an empty (transparent) gem socket.
  - **Gem:** `textures/gui/card/gem/gem_<rarity>.png` is a 120x168 transparent overlay with only the 36 socket pixels (x56–63, y96–101) painted. It's drawn on top of the base, so it lands in the same spot on every kind. `CardFrames.Frame` is now `(texture, gem)`.
  - **Fallback:** per-rarity bases are still used (with no gem overlay) when a kind has no single base.
  - **Where the art came from:**
    - The gems were cut from the owner's base_mob_<rarity>.png frames.
    - base_mob.png is base_mob_common.png with the socket cleared.
    - base_enchantment.png is the owner's own gemless base (saved as base_enchantmen.png, renamed).
  - The per-rarity mob/enchantment files are now unused.
  - MobFrameScenario expects base_mob.png + gem_<rarity>.png.

## 2026-11-06 — Brewing Stand: Potency / Duration / Lingering on potions in hand (Claude)
- **Potions:** new card field `"potion": true`, Actions only. It is stored as `Entry.potion`, a new last component; the old 18-arg Entry constructor delegates with false. The type label reads "Potion", or "<Element> Potion".
- **New action `BREW_POTION`** (appended, target SELF, generator-style), usually At the end of your turn on a block. Text: "Brew a potion in your hand: add Potency, Duration or Lingering".
- **`craftbound.card.Brew(potent, lasting, lingering)`** is stored on `CardRef.brew`, a new 4th component. Old constructors default to Brew.NONE; old snapshots read null, which becomes NONE. Wire format is bits: 1 Potency, 2 Duration, 4 Lingering, written as `"brew"` beside `"training"` in `MatchView.writeTraining`. Each upgrade can be added once.
  - **Potency:** +1 to amount (damage/heal/draw/XP/summon/give...) or to attack/health above 0 (buff/weaken/tool).
  - **Lingering:** CHOSEN_ENEMY becomes ENEMY_MOBS, CHOSEN_FRIENDLY becomes FRIENDLY_MOBS. For CHOSEN_ANY/CHARACTER, harmful actions hit enemies and helpful ones hit your mobs. `CardEffects.targetKind(CardRef)` returns NONE for lingering; every caller holding a CardRef now passes the ref.
  - **Duration:** on play, `MatchPlayer.brewing` stores a BrewRepeat (the card without Duration, plus its aim). `beginTurnFor` runs it once via onItem before START_TURN triggers. A chosen mob that has left becomes Target.NONE.
  - `CardEffects.run` applies `card.brew().apply(effects)`, so brews affect every effect path.
- **Picking:** reuses HandChoice with action BREW_POTION.
  - count 1 = pick the potion (offers = distinct brewable hand cards); this step is skipped when only one potion is brewable.
  - count 2 = pick the upgrade (offers = the potion already brewed with each available upgrade; source = the original).
  - `finishBrew` replaces the hand card, logs it publicly, and emits the new `MatchEvent.Type.BREW` (amount = hand index, extra = bits). The card is hidden from the opponent like DRAW.
  - AI and team tables brew at random.
- **Client:** `HandCard.brew`, plus `MatchState.readBrew`. The BREW event updates the hand card and plays the brewing stand sound. `CardRenderer.drawTrial(..., training, brew, ...)` uses `brewText`: green "Brewed: X." then the upgraded rules. TextKey includes brew. It's used for the hand, hand hover and the pick overlay; the picker titles are "Brewing Stand: choose a potion to brew / how to brew it". Played-card flight and reveal animations still show the unbrewed face.
- **Workshop:** a Potion tick box on Actions, the BREW_POTION action, GUIDE.action.BREW_POTION, and a new GUIDE.brew section ("Potions and brewing", enforced by guide-checks). The preview label shows Potion.
- **Tests:** BrewChecks. Also: Repair is now bold on cards (Cards.RULE_WORDS) and in the Workshop text/short-rules patterns.

## 2026-11-06 — Limited uses on mobs and blocks (Claude)
- **Card field `"uses"`** (0–20, mobs/blocks only) is stored as `Entry.uses`, the new last component; the old 19-arg constructor delegates with 0.
  - Validation needs at least one effect whose trigger isn't PLAY (Spawn) or DROP: `CardCatalog.spendsUse`.
  - Rules text ends "After N uses, this is destroyed." Java and model.js match.
- **Spending uses:** in `CardEffects.run`, a run where at least one use-spending effect of the card's own trigger fired (`source.card == card`) calls `Match.spendUse`.
  - `Minion.usesSpent` goes up by 1 (it starts at 0, so old saves are safe), and a `STATUS` event is sent with the new `STATUS_USES = 12` (extra = uses left).
  - At 0 the card logs "X is used up." and is set to health 0, so Drop still fires.
- **Client:**
  - The board JSON carries `usesLeft`, read into `Mob.usesLeft`.
  - A gold counter is drawn on the board card (bottom-left on blocks, top-left on mobs).
  - Hovering shows "N uses left before it is destroyed".
  - When a use is spent, "N uses left" / "Used up" floats up.
- **Workshop:** a "Uses before destroyed" field for mobs and blocks, a new guide section "Card limits" (GUIDE.card.USES) that guide-checks enforces, and the UsesChecks test.
- Follow-up to Brewing: the Brewing Stand only offers upgrades that would change the potion (`Brew.options`).
  - **Potency:** only offered if some number is above 0 (already the case).
  - **Duration:** only offered if repeating does something (`Brew.repeats`). Not for keyword grants, Wither, Totem, Transform or a keyword-only Buff, nor for Destroy/Bounce on a single chosen target unless it is Lingering.
  - **Lingering:** only offered for a chosen-target effect that can hit a mob (`Brew.lingers`). Not for untargeted effects, hand effects, Destroy tool or Tool damage, and `linger()` leaves those unchanged.
  - A potion with nothing left to offer can't be picked for brewing.
  - BrewChecks covers Swiftness (Lingering only) and a draw potion (Potency and Duration only). The Workshop guide text is updated.

## Trident: Throw, Loyalty, Channeling (Claude)
- New keywords, appended to `Keyword`: **THROW** (tools only), **LOYALTY** and **CHANNELING** (enchantments only). They're bold rule words, have lang text, and validation in both Java and model.js. An enchantment with Loyalty or Channeling needs no other effect.
- **Throw:**
  - A hero with a Throw tool throws it at an enemy mob. It reaches the back row like Ranged, and the mob doesn't hit back. It can't target heroes ("a thrown trident needs a mob to stick in").
  - If the mob survives, the tool (with its enchantments and Durability) sticks in it (`Minion.impaled` / `impaledBy`) and the hero is unarmed.
  - When that mob leaves the board (dies, bounce, remove, transform), `Match.returnImpaled` gives it back. It re-equips if the hero is unarmed, otherwise a fresh copy goes to hand.
- **At the thrower's turn start** (`tridentsAtTurnStart`):
  - **Channeling** strikes the stuck mob and its left and right neighbours in the same row for `CHANNEL_DAMAGE` = 2 (amplified).
  - Then **Loyalty** returns the tool.
- **Events:** IMPALE (amount 1 = stuck, 0 = out) and CHANNEL. Board JSON `impaled`.
- **Client:**
  - The thrown item flies at the target, then sticks out of the mob's top-right corner.
  - Trident sounds and a lightning flash.
  - The hover explains it, and hero targets are blocked.
- **Test:** TridentChecks.

## Shield frame and hero health (Claude)
- The Shield rim on mobs and blocks is now a thin rectangle, with no pointed bottom. Targeting and preview outlines for shielded mobs match it.
- The hero's health heart is drawn again after the board rows, so a front block's frame can never cover it.

## Trials map controls (Claude)
- The mouse wheel zooms the map around the cursor; left-drag pans. The hint and tooltip were updated, and TrialsMapScenario now zooms out with the wheel to find the boss.
- Rooms you can travel to next have a pulsing gold halo, a thick gold ring and a bobbing arrow. Routes to them are gold, so they no longer blend with green Camp rooms.

## Spectator bars slimmed (Claude)
- SpectatorScreen: the top bar is 22px with 16px buttons. The bottom is one 18px row: the notice on the left, and two compact (14px, at most 150px) hand-request buttons on the right. Before, the bottom was 46px with full-width buttons covering the watched player's hand. The board gets the freed height.

## Brewing Stand waits for the Cauldron (Claude)
- `CardEffects.onEndTurn` holds every BREW_POTION until all other end-of-turn effects (and the tool's) have run, then calls `Match.releaseBrews`. So a Cauldron's potion can be brewed no matter which of the two was placed first.
- For a human with a pick still waiting (the Cauldron's Pick One), the stand queues a waiting brew (`HandChoice` with count 0, `waitingBrew()`). `pruneChoices` turns it into the real potion/upgrade pick once it reaches the front of the queue. The AI and team tables brew at once, after the triggers.
- BrewChecks covers the stand placed before a Cauldron, for both a human and the AI.

## Shorter tooltips, pinned inspect, potion marks (Claude)
- **Keyword text:**
  - `Keyword.shortDescription()` reads the new `keyword.craftbound.<k>.short` lang lines and falls back to `.desc`. `hasMore()` says whether a full rule exists.
  - The match hover notes and mob status lines use the short line.
  - The Shield `.desc` itself is shorter too.
- **Pinned inspect (duel board):**
  - Right-click a hand card or a mob to pin its preview open; right-click it again, right-click elsewhere, or left-click to let go. One pin at a time.
  - While pinned, hovering a keyword note expands it to the full rule. Unpinned notes end with a "Right-click to pin and read more." hint.
  - The 4-seat board and spectators aren't pinnable.
- **Potion marks:**
  - `CardEffects.onItem` snapshots mobs before a potion's effects and marks any mob with a lasting change (`Minion.PotionMark` id/brew/temporary).
  - A mark that is only a weakness goes when the weakness ends (`activePotions()`). Cleanse (`clearAppliedEffects`) clears all marks.
  - Board JSON `potions`.
  - Client: coloured potion particles rising through the mob (more of them if it was brewed), small potion icons down its left edge (a green pip if brewed), and a hover line "Under X (brewed: ...)".
  - BrewChecks covers Lingering marks, no mark from Healing, and Cleanse.

## Release jar with built-in cards and Trials (Claude)
- `./gradlew releaseJar` writes `build/release/craftbound-<version>.jar`. It is the normal jar with `craftbound/cards.json` replaced by the Workshop's `run/config/craftbound/cards.json`, and the Workshop's `trials.json` added as `craftbound/trials-bundle.json`. The repo's own resources (test fixtures) are unchanged.
- `TrialsContent.defaults()` uses `trials-bundle.json` when the jar has one. If it fails to parse it falls back to `sectionDefaults()` with a logged error.
- **`BundledConfig.refresh`:**
  - Writes a missing config from the jar, plus a `<file>.bundled` SHA-256 marker.
  - When a newer jar bundles different content and the file still matches its marker (never edited), it replaces the file and keeps `<file>.previous`.
  - Edited files, and files from before markers existed, are never touched. The dev `run/config` files have no marker, so the Workshop's files are safe.
  - Used for both cards.json and trials.json. Covered by BundledConfigChecks.
- README release and multiplayer notes updated.

## Workshop auto-builds the release jar (Claude)
- New `tools/card-editor/release-builder.cjs`. After every successful card or Trials save, the Workshop host runs `gradlew releaseJar --offline -q` in the background (hidden window). Output goes to `build/release-build.log`.
- Saves within 3 s share one build. A save during a build queues exactly one more.
- `GET /api/release` (token) reports idle/queued/building/ready/failed. The cards editor and Trials page append "Release jar building…/ready: <path>/failed" to the save message.
- Turn it off with `WORKSHOP_AUTO_RELEASE=0`. Covered by release-builder-checks.cjs.
