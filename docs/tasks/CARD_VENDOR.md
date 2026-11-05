# Task: Card Vendor (two-block vending machine)

A placeable vending machine that sells **booster packs** and a **daily rotating lineup of 6 single cards** for emeralds.
Design agreed with the project owner on 2026-11-02. Implement it as a self-contained feature; don't change match rules,
`cards.json`, or the existing binder, deck and pack systems beyond what's listed here.

## Project facts (read before starting)
- Fabric, Minecraft **26.3**, Java 25, Mojang mappings (`Identifier`, not `ResourceLocation`). Split sources:
  `src/main/java` (common) and `src/client/java` (client).
- GUI API names in 26.3: `Screen.extractRenderState(GuiGraphicsExtractor, mouseX, mouseY, partialTick)`;
  `minecraft.gui.setScreen(...)`.
- **Mouse buttons use SDL numbering**: compare against `InputConstants.MOUSE_BUTTON_LEFT` / `MOUSE_BUTTON_RIGHT`,
  never 0/1.
- Recipe `category` must be a valid 26.3 value (`misc`, `building`, `redstone`, `equipment`, `blocks`, `food`).
  An invalid one stops worlds loading.
- Existing patterns to copy:
  - Block and BlockItem registration: `block/ModBlocks.register(...)`.
  - Block entity: `CardDisplayBlock` / `CardDisplayBlockEntity` (registration, saving and loading).
  - Payloads: `network/ModNetworking` (register types, server receivers) and `CraftboundClient` (client receivers).
  - Cards: `Cards.collectible()`, `Cards.get(id)` (`rarity()`, `token()`), `CardItem.of(id, enchanted)`.
  - Packs: `ModItems.BOOSTER_PACK`.
  - Drawing cards: `client/card/CardRenderer.draw(g, font, stack, x, y, scale)`.
  - Buttons and outlined text: `client/match/MatchArt`.
- Read `MODLOG.md` and add an entry for this work when done.

## The block
- ID `craftbound:card_vendor`. It's **two blocks tall**, like a door:
  - Properties: `HALF` (lower/upper, use `BlockStateProperties.DOUBLE_BLOCK_HALF`) and horizontal `FACING`, facing the placer.
  - **Placing** requires the space above to be replaceable and inside the build height, then places the upper half
    (follow `DoorBlock` / `DoublePlantBlock`).
  - **Breaking** either half removes both and drops one item. Only the lower half has a loot table entry; handle
    creative breaking of the upper half like doors do.
  - **`PushReaction.BLOCK`**, so pistons can't split it.
- **Block entity on the lower half only.** Right-clicking either half opens the vendor; the upper half forwards to the
  lower.
- The upper half (the window) emits light level 7.
- Recipe (shaped, category `misc`): iron ingots around, **emerald block** in the centre, **glass** top middle,
  **redstone** bottom middle. Gives 1 Card Vendor.
- Add it to the Craftbound creative tab. Lang name: "Card Vendor".

### Placeholder assets (the owner will replace them with art)
- Blockstate with `half` and `facing` variants, pointing at **`craftbound:block/card_vendor_lower`** and
  **`craftbound:block/card_vendor_upper`**.
- Make those two models simple placeholders built from existing vanilla textures (e.g. iron block, glass, emerald
  block) so it renders today. Keep the model file names stable; the owner will swap in a Blockbench model
  (16×32×16) with a 64×64 texture later.
- Item model for the block.

## What it sells
One constants class (e.g. `vendor/CardVendor.java`) holds every number, so the owner can tune them:

| Product | Price (emeralds) |
|---|---|
| Booster Pack | **5**, unlimited purchases (villagers charge 4; this is the always-open price) |
| Common single | 2 |
| Rare single | 5 |
| Epic single | 12 |
| Legendary single | 30 |
| Enchanted single | ×3 the rarity price |

### Daily lineup of 6 singles
- **Deterministic, not stored:** generate from `world seed`, the lower half's `BlockPos.asLong()` and the
  **day number** (restocks at dawn; sleeping skips to the next lineup). Every player sees the same six cards at a
  given machine; different machines differ.
- Slots: **3 Common, 2 Rare, 1 Epic** (with a **10% chance that slot is Legendary** instead).
- Each slot has a **5% chance to be an Enchanted copy**.
- No tokens and no duplicate card IDs within a lineup. Neutral and class cards are mixed.
- If a rarity pool is empty (catalogue edits), fall back to the nearest rarity that has cards.
- Use the **current** catalogue (`Cards.collectible()`) each time, iterating in its stable order so results are
  reproducible.
- **Purchases are per player, per slot, per day:** each player can buy each slot once per day. Store this in the
  block entity, keyed by player UUID and the day number. Drop records from earlier days when saving, so it never
  grows.

## Networking (the server decides everything)
- **S→C `VendorStatePayload`:** block pos, current day, lineup (6 × card ID, enchanted, price), which slots this
  player already bought today, pack price, and ticks until the next restock. Sent when the screen opens and after
  every purchase.
- **C→S `VendorBuyPayload`:** block pos and slot (`-1` = booster pack, `0..5` = singles).
- **Server checks** on buy:
  - The player is within 8 blocks.
  - The position is a vendor lower half.
  - The slot is valid, and the lineup is recomputed for the current day.
  - It isn't already bought today by this player.
  - The player carries enough emeralds, counted across all inventory stacks.
- **On success:** remove the emeralds, give the item with `placeItemBackInInventory(..., Prediction.SERVER_ONLY)`
  (drops at their feet if the inventory is full), play the villager trade sound, then resend the state.
- **On failure:** resend the state and play `entity.villager.no`.

## The screen (client)
Follow the owner's UI rules:
- **nothing overlaps anything**;
- **no tooltips covering a card** (put text under or beside it);
- keep text short.

Layout:
- **Left:** the booster pack (item rendered large), its price with an emerald icon, and a **Buy** button.
- **Right:** the 6 singles as full cards (`CardRenderer.draw`), in a row or a 3×2 grid, scaled to fit the GUI.
  - Under each card: its price with an emerald icon.
  - Bought slots are greyed out with a "SOLD" stamp.
- **Hovering** a card shows it larger beside the grid, not as a tooltip.
- **Clicking** a card (left button) buys it.
- **Top:** "Card Vendor", plus a restock countdown ("New cards in 6:42", counted down on the client from the
  ticks value).
- **Bottom:** "You have: N emeralds".
- Unaffordable prices show in red.
- Feedback: the villager trade sound on success, and the bought card briefly flies toward the inventory corner
  (optional, keep it short).

## Out of scope (later tasks)
- Generating vendors in villages or card halls.
- Redstone output, e.g. a comparator pulse on restock.
- Selling cards back to the machine.

## Done when
1. It compiles (`gradlew compileJava compileClientJava processResources`) and the world loads with the recipe.
2. It places as two blocks, breaks as one item, can't be pushed by pistons, and either half opens the screen.
3. The lineup is identical for two players at the same machine on the same day, differs between machines, and
   changes at dawn.
4. A player can't buy the same slot twice in one day; another player still can.
5. Emeralds are taken correctly from split stacks; nothing is given when the player can't afford it.
6. Headless checks (in the WorkshopChecks style) cover lineup determinism, slot rarities, no tokens or duplicates,
   Enchanted pricing, empty-rarity fallback and purchase validation.
7. A `MODLOG.md` entry is added. The owner tests the live screen and gameplay themselves.
