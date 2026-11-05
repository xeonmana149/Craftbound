# Craftbound Card Workshop

Double-click **Open Card Workshop.cmd** in Windows File Explorer. It starts a local Workshop helper and opens your browser. Node.js is required (already installed on this development machine); no account or npm install is needed.
Run the launcher from Windows File Explorer. A file link in a chat may show the file instead of executing it. The launcher opens your default browser.
The Craftbound creative tab also includes **Creative Card Binder**, prefilled with a full playset of every collectible in normal and foil versions. It is a prefilled ordinary binder, not an infinite source; take a fresh one from the creative tab when needed.

## First use
1. Build/restart the mod once for the new catalogue system.
2. It creates `config/craftbound/cards.json`. IntelliJ's development client uses `run/config/craftbound/cards.json`.
3. The launcher automatically opens the development client's `run/config/craftbound/cards.json`. **Save** and **Ctrl+S** overwrite that file. On a standalone page in a browser with file-picker support, Save asks you to select the existing active `run/config/craftbound/cards.json` once, then overwrites that same file for the rest of the session. This preserves your current draft without moving to a different page. Browsers without that support must export the draft with Save as…, import it in the launched Workshop, then Save.
4. Edit stats, effects, keywords, art item, class, rarity or type. **New card** and **Duplicate card** append a card and automatically generate its ID from its name (for example, Reef Keeper becomes `craftbound:reef_keeper`). Duplicate names receive a numeric suffix. Editing the ID manually stops automatic updates for that card. Saved/imported card IDs stay fixed when their names change.
5. **Save**, then run `/craftbound cards reload` in Minecraft. Finish all ongoing matches first. Requires cheats/operator permission.
6. Get the selected card using the command beside its preview. `/craftbound cards testdeck farmer` gives a legal 30-card deck and a binder containing its cards. Keep the binder in your inventory.

The preview shows layout and live rules text; artwork is a placeholder. The game renders the Minecraft item selected in the Art field. This version selects existing Minecraft item art, not custom PNG uploads.

**Associated cards** beneath the live preview shows cards summoned, added to hand, transformed into, or included in a random catch/loot pool. It follows unsaved effect edits immediately. Browse results with the arrows; **Edit this card** opens the linked definition, including tokens. Each preview shows the trigger, action and copies; duplicate links share one preview. Random-class generation explains its variable pool instead of implying a fixed card. Artwork remains a placeholder, as in the main preview.

## Deleting and restoring cards
Use **Delete card** beside Duplicate. New unsaved drafts are removed completely. Saved cards are marked deleted: they leave active collection, booster, vendor and deck-building pools, while their definitions and collector numbers remain reserved for existing items and saved matches. **Show deleted cards** reveals them; select one and use **Restore card**, then Save. Deletion is blocked while another active card references the card, while a hero power needs it, or if it is the last active collectible of its rarity. Nothing changes in Minecraft until Save and `/craftbound cards reload`. Restart the mod once to install deletion support before using it.

## XP curves
The chart below the Workshop card preview follows the class of the selected card and updates as you edit XP or class. It counts unique collectible definitions, excludes tokens, and can include Neutral cards. The in-game deck builder replaces the instruction footer with a compact 0–6 / 7+ curve counting every normal and foil copy in the actual deck. Average XP uses exact costs, including cards above 7. Library search and filters do not alter deck totals.

## Balance view
Edit XP, attack, health/durability, class, type and rarity directly in the table. Compare class, rarity, kind, type and cost distributions. Counts exclude tokens. The Foundation target starts at 180 but is editable. These counts do not determine booster odds or prove competitive balance.

## Files and saving
- Bundled source of truth: `src/main/resources/craftbound/cards.json`.
- Runtime editable copy: `config/craftbound/cards.json`, or `run/config/craftbound/cards.json` for the development client.
- Browser starter copy: `tools/card-editor/defaults.js`. The launcher loads the active runtime file automatically.
- To ship changes, use **Save as…** to update the bundled source file as well, then rebuild. Existing runtime files are deliberately not overwritten.
- Normal **Save** overwrites the launcher's fixed active file, or the active cards.json explicitly selected in a standalone page. Importing/exporting another file does not redirect this destination. **Save as…** exports a separate copy.
- Launcher saves keep the previous version in `run/config/craftbound/cards.json.workshop-backup.json`. Validation and a temporary-file replacement protect against partial writes. The server listens only on loopback and requires a launch-specific token. Standalone saves validate against the selected file and commit through the browser's writable-file API; they do not create this extra backup.
- Closing a launched tab does not stop the small local helper; it exits when its Node process or Windows session ends. Browser recovery drafts belong to their original page/origin: export an unsaved draft before switching to a different Workshop address.
- A browser-local recovery draft does not modify your JSON files.
- Commands are server-side. The server sends its validated catalogue to players on joining and reloading. Remote-server rules do not overwrite local files.

## Effects
Choose a trigger (Play/Spawn, Drop, End turn, After attack, Damage dealt, Attacked), action, target, amount and optional mob type.
Examples:
- Spawn → Buff → Friendly mobs → Aquatic → +1/+1 → Exclude self.
- Drop → Give → Self → Rotten Flesh → 1 copy.
- After attack on a hoe → Summon → Self → Wheat → 1 copy.
- End turn → Heal → Friendly hero → 2.

SELF on Draw, XP, Give and Summon means the owner; on mob effects it means the source mob.
Chosen targets are selected when playing; Damage dealt targets the damaged mob, and Attacked targets the attacker (including a hero).
Summoning does not trigger Spawn. Full boards and hands follow normal match rules.
Mobs and blocks are separate target categories. Choose Mobs only, Blocks only, or Both for each effect.
Attack buffs must target mobs only. Blocks always have zero attack; health buffs increase current and maximum health, while Heal only restores missing health.
Composter buffs another random friendly mob (+1/+1), excluding blocks. Bone Meal gives a mob +1/+1 or a block +2 Health.
Passives also include Grazer (heal with a friendly Crop), Battle-hardened (gain Attack once each turn after surviving damage), and Schooling (gain temporary Attack when another friendly Aquatic is played). New-card templates provide editable examples, plus Milk Bucket cleansing and Cactus Thorns.
Sprint allows immediate attacks against enemy mobs and blocks, but not the enemy hero until the owner's next turn.
Effects execute in order. Type filters allow one mob type per card. ANIMAL represents the existing animal/passive-mob grouping; not every passive Minecraft mob must belong to that type.
Automatic text follows effect settings. Existing cards retain their original wording until edited; changing an effect enables automatic text.
New fundamental mechanics or new playable classes still require Java support. The editor currently exposes the classes implemented by the game.
The eight playable classes are Fighter, Builder, Farmer, Fisherman, Alchemist, Miner, Chef and Tamer. Enchantment cards are class-specific spells that upgrade your currently equipped tool; they cannot be played without a tool and do not refresh its attack for the turn. Their Health bonus field is durability.
Starter enchantments: Sharpness (Fighter), Efficiency (Builder), Unbreaking (Farmer), Lure (Fisherman).
Fisherman's Cast power adds a 1 XP 1/1 Caught Cod to hand. Its starter cards include Aquatic support, a fishing rod, Conduit and Elder Guardian.

Alchemist starts with eight collectible cards and a Minor Harming Potion token. Its Brew power costs 2 XP to create the 0 XP potion; Flame is its starter tool enchantment.

For Cooked Porkchop, choose **New from template → Cooked Porkchop**, or Play → Heal → **Chosen board card or hero (either side)** → **Mobs only**. The example restores 4 Health for 2 XP. This chosen target supports healing and damage, including either hero; Mobs only excludes blocks. Choose Both if an effect should also work on blocks. Hero-inclusive targets support Heal, Damage, Cleanse and Freeze. They cannot have a mob-type filter or Wither. Changing a chosen Play target updates the other chosen Play effects to the same target side. Templates are drafts until saved; they do not automatically join the set.

## Validation and compatibility
A rejected reload leaves the previous catalogue unchanged. Keep existing IDs, card kinds, token flags and collectible order; append new cards. This protects saved inventories and binder numbers.
The loader validates fields, bounds, enums, duplicate IDs, referenced cards, summon kinds, effect/target compatibility and booster rarity coverage.
An effect-resolution limit ends runaway death-effect chains in a draw.
Reloading while any match is active is blocked, so live matches cannot change rules halfway through.

## Maintainer checks
Run `node tools/card-editor/server-checks.cjs` for temporary-file save checks, `node tools/card-editor/checks.cjs` for browser-model validation and migration checks, and `gradlew checkCards` for Java rule, targeting, packet and deck checks. See MODLOG.md for the verification record.

## Token references
A token’s **Used by** section appears beneath its basic fields. It lists every card whose current draft effects reference that token, with trigger, action and quantity. Click a source to edit it; library filters clear so it stays visible. **Other sources** also identifies built-in hero powers and the second-player bonus. Card links update from the current catalogue without needing a save.

## Clickable validation errors
Every listed error opens the affected card and scrolls to its highlighted field, including a particular effect row. All issues remain available in the scrollable error list. Missing originals and changed identity fields have explicit repair buttons; collector-order errors explain the expected number. “Add all missing original cards” merges originals into the draft and restores required order while preserving edits on cards already present. Repairs stay in your draft until Save. They do not replace your whole catalogue.

The saved runtime catalogue was valid when the reported Alchemist errors were investigated. An older recovery draft/import can be missing newly added cards. Click an error to inspect it before choosing a repair.

## New class foundations
- Miner: ten starter collectibles plus five tokens. Excavate (2 XP) mines a friendly Ore or equips a 1/2 Wooden Pickaxe. Mine removes a friendly Ore block and triggers its Drop; it does not resurrect it through Totem. Dismantle is retired and cannot remove blocks. Coal stores a one-turn XP resource, Gold draws, and Iron/Diamond enchant a held tool. Stone Pickaxe mines after attacks.
- Chef: four starter collectibles. Feed (2 XP) selects any non-block mob or either hero and heals 2. Click the power, then its target; right-click or Escape cancels without spending XP.
- Tamer: four starter collectibles plus Wolf Pup. Call Pup (2 XP) summons a 1/1 Tameable Wolf Pup. Pack Call supports Tameable mobs.
- These are starter implementations, not complete 30-card class sets. Test with /craftbound cards testdeck miner, chef or tamer.
- The current catalogue has 97 definitions, including 80 collectibles. Collector IDs/order are preserved; the Foundation planning target is unchanged.

## Cleanse, Frozen and Mastery
Cleanse removes all buffs and debuffs from the target, including granted keywords, stat changes, Totems, invulnerability, Frozen, Withered and Bad Omen. Printed abilities, equipped tools and continuous auras from other cards remain. Heroes can be Frozen to block tool attacks; powers and card plays still work. Withered cannot target heroes. Cleansing never resets a used attack.

Open Mastery from the binder's Mastery button or the deck builder's Mastery strip. The overview shows all eight classes, points, levels and the next Common-card reward milestone. Scroll or use the arrows to see more classes; Back/Escape returns to the previous screen. Hover a class for reward eligibility and earning rules.

## Design Reference and wording
For Anvil and other blocks, use **Action: Improve equipped tool**, **Bonus attack: your amount**, **Bonus durability: 0**. This works with Spawn/End turn/Activate and does not attach an Enchantment. Set Attack to 0 for a Durability-only repair. At least one bonus must be positive. With no equipped tool the block remains playable and the upgrade does nothing. Enchant tool remains the action for actual Enchantment cards.

**Affects** includes Random enemy, Random friendly, Random block, Random mob and Random hero. Enemy/Friendly select from that side, including its hero only when supported by the action and filters. Random mob/block/hero select from either side; mob and block choices fix the board-kind filter. A mob-type filter removes heroes from consideration. Each effect picks one eligible target; no candidates means no effect. Use Random friendly mob / block for the existing explicitly board-only friendly choice.

For Snow Golem, add **Ranged**, then **When: Damage dealt**, **Action: Freeze**, **Affects: Damaged mob or hero**, **Board card kind: Mobs only**. Leave the mob-type filter empty. It automatically affects the character hit, with no play-time target selection. Prevented damage does not trigger it. Ranged avoids retaliation; non-Ranged damage effects can also trigger on retaliation. The legacy Damaged mob / block target excludes heroes. Wither still cannot target heroes.

For Ravager's survival payoff, choose **When: After attacking, killing and surviving**, **Action: Damage**, **Affects: Enemy hero**, **Amount: 2**. This trigger is for non-block mobs. It checks survival after retaliation, thorns and Death effects; resurrection or transformation does not count as surviving. Ordinary On kill remains available for effects that also work while defending or without a survival requirement.

For a continuous stat passive, select **Aura: Attack and Health**. Set **Aura Attack bonus**, **Aura Health bonus** and optionally **Aura affects this mob type**. Use 0 for either bonus to grant only the other stat. It affects other friendly mobs, excludes blocks, and ends when the source leaves the board. Existing Attack-only aura cards remain compatible.

For Ender Chest: select **Card kind: Block**, add an effect with **When: End turn**, **Action: Add random card from your class**, **Amount: 1**. It generates from the controlling player's class, excluding Neutral, tokens and deleted cards. This is generation, not drawing; classes with no collectibles produce nothing until you author their set. The automatic description spells out the rule. Auras and class generation do not require hand-editing JSON.

The Design Reference tab contains the searchable Foundation guide and exact Minecraft 26.3 indexes. Context links beside the preview open wording examples, the selected class identity and effect limits. Draft edits stay in memory when switching tabs. Wording advice is advisory, not an invented rules validator. Automatic description omits redundant Play labels, spells out turn ownership and bolds timing phrases. New mechanic concepts in the reference are proposals, not supported effect actions.

Choose a chapter from the reference's left navigation. Only that chapter is displayed during browsing; search finds matches across the whole guide. This keeps scrolling lightweight. Clear search to return to your selected chapter.

Large chapters and search results now use small pages. Scroll the content area, then use Previous/Next at the bottom; the chapter list scrolls independently. All entries remain searchable. `python build-reference.py --html-only` rebuilds the browser guide without rebuilding the PDF.

## Ideas and planning
Use **Ideas** for card concepts, class identities, synergies, mechanics and set plans. Each note has class, stage, tags, concept/wording, synergy/deck plan and balance questions. Notes autosave separately to `run/config/craftbound/workshop-ideas.json`; they never modify playable cards or collector IDs. Archive keeps old ideas recoverable. Export notes downloads a portable JSON copy. The local host keeps a backup and rejects stale writes from another tab. A standalone page keeps browser notes and offers export; reopen the launcher after updating the server to enable file autosave.


## Artwork and attack-target effects (2026-11-02)
New cards match Minecraft artwork from their name automatically, including spacing aliases (Stone Sword), size prefixes (Small Slime), and common names (Wood Fence, Polar Cub). This uses the local Minecraft 26.3 registry reference. Manual artwork edits turn matching off; tick Match artwork to card name automatically to enable it again. Custom resource IDs are preserved. An unrecognised name needs a manual artwork choice. Existing invalid vanilla IDs/paper are repaired when there is a clear match.

For Snow Golem: When = After attack, Action = Freeze, Affects = Attack target (mob, block or hero). Generated text says "After attacking: Freeze its target." Targets that died are skipped. This does not freeze friendly mobs or fire while defending.

Fighter's 2-XP Craft power equips a 1-Attack/1-Durability Wooden Sword. Old catalogues/drafts get the required token appended automatically, without changing collector numbers. Recovered drafts merge external changes only into fields untouched since their saved baseline; unsaved edits remain authoritative.


## Tool abilities and combined buffs

Choose **New from template → Looting** for a 2-XP Rare Fighter Enchantment that attaches a draw-on-kill ability to your tool. Only one effect is needed: On kill / Draw / Self / 1 card. Enchantment abilities attach automatically; no dummy upgrade or stat bonus is required. It requires an equipped tool, remains attached until the tool breaks or is replaced, and works on the last durability too. Existing cards with an explicit Play / Enchant tool attachment still work without attaching twice.

For **Give a friendly Illager mob +2/+2 and Sprint**, use one Buff effect: Chosen friendly, Mobs only, Illager, Attack 2, Health 2, **Also grant keyword: Sprint**. The template **Illager Rally** provides this example. Buff can additionally grant Shield, Sprint, Ranged or Ignore Shield. Both parts use the same target; a random target is rolled only once. This combines stats and a keyword, not arbitrary action chains. Other actions still use separate ordered effects; chosen Play effects share the player's one selected target.

## Ignore Shield setup
Tick Ignore Shield under Keywords on a mob or tool. To grant it to a mob through an effect, select Action = Grant keyword, Keyword to grant = Ignore Shield, then the desired mob target. The Workshop selects Mobs only for this grant. It lasts while that mob remains on the board; it is not a temporary one-turn buff. The attacker can bypass Shield to attack another enemy or the enemy hero, while normal readiness, Sprint, Frozen, retaliation and protection rules remain in force. Item targeting already ignores Shield and does not need this keyword.

## Trials route layouts

The Trials editor’s **Route map → Path layout** chooses **Generated branches** or **Draw paths yourself**. With generated branches, the diagram lists encounter templates: each new run samples those templates into two to four paths with differing numbers of rooms before the next crossroads. A final boss joins every route. Templates keep their authored room titles, event links and opponent profiles. Mystery names and descriptions remain hidden from players until they visit.

Generated routes require 7–30 floors, at most four templates per floor and exactly one final boss. Manual connections are retained when switching modes but are used only by authored paths. Existing saved expeditions retain their rules snapshot, route and progress; start a new run to see route changes.

Boon entries support a **Minecraft icon** (for example minecraft:golden_apple). Earned boons appear individually across the run header, including lasting rewards from events. Names and effects are saved with the run. Currency is shown as **Emeralds**; the existing MARKS, startMarks and costMarks JSON keys remain compatible.

## Block auras, returning copies and deck choices

These are reusable Workshop mechanics; no collectible cards are added automatically.

- **Load-Bearing Arch:** Block; Passive ability **Aura: Attack and Health**; Aura Attack bonus **0**; Aura Health bonus **3**; Aura affects **Other friendly blocks**; mob-type filter **All mob types**. Other blocks gain +3 current/maximum Health while the source is in play. Separate auras stack; each excludes itself. Removing the source reduces the maximum and caps current Health, without counting as damage or healing. Existing auras without auraKind keep their original mobs-only behaviour.
- **Piston Press:** Action; When **Play**; What happens **Return and copy**; Affects **Chosen friendly**; Eligible board cards **Blocks only**; Extra copies **1**. Selects one block once, returns it, then adds one extra copy. Copies retain Enchanted finish and Trials upgrades, but not combat buffs, damage, statuses or attached Totems. Returning never triggers Drop. Tokens still follow the normal remove-instead-of-return rule; the extra copy is created separately. Cards beyond the hand limit are lost.
- **Blueprint Chest:** Block; When **Play** (printed **Spawn**); What happens **Choose from deck**; Eligible board cards **Blocks only**; Different choices to offer **3**. Add a second effect: When **Drop**, What happens **Draw**, Amount **1**. The picker offers up to three different card IDs from the actual deck. It draws exactly one selected copy; unchosen cards stay in place. Fewer eligible cards produce fewer options; none does nothing, with no Hunger. Normal/Enchanted copies of the same card occupy one option. The selected real copy keeps its finish and Trials upgrades. Optional mob-type filters are supported.

Deck offers are private to the chooser in duels, Commander and Team Duos. The game waits for the choice, with a 30-second random fallback. AI chooses automatically. A full hand burns the selected draw as usual.

Refresh the Workshop to load new authoring options. Restart Minecraft with the updated mod before loading cards that use these mechanics.
