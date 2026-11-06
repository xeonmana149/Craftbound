# Craftbound

Craftbound is a Minecraft card game played at a card table. Bring a binder to browse your collection and build decks at the table, or start a solo Trials draft using borrowed cards.

| At the card table | How it plays |
| --- | --- |
| Player Vs Player | Challenge another player with your selected deck. |
| Card Master | Practice a duel against the AI. |
| Alternate Modes > Team Duos | Four players, two teams, 30 Health per team. Teammates act at the same time using their own cards, XP, tools and powers. Select a seat to view its field; select a rival before playing a single-enemy effect. Both partners mark Ready to end their turn early. |
| Alternate Modes > Commander | A four-player free-for-all. |
| Alternate Modes > The Trials | Draft 10 borrowed cards, then explore a scrolling expedition map. Commit to a branch for a few rooms before the next crossroads; all paths converge on the guardian. Branches have different lengths: a shorter route reaches stronger foes sooner, while a longer route offers more preparation. Manage Health and Emeralds, train individual cards, and earn class cosmetics on your first clear. |

**Releasing:** run `./gradlew releaseJar`. It writes `build/release/craftbound-<version>.jar` with your current Workshop cards (`run/config/craftbound/cards.json`) and Trials route (`trials.json`) built in, so players and servers need only the jar. Upload that file to Modrinth. The Workshop does this for you after every save; its status line says when the jar is ready.

**Running multiplayer:** Install Fabric for Minecraft 26.3, Fabric API for 26.3, and the same release jar on the server and each client. The server sends its card set to joining clients. On first launch the mod writes `config/craftbound/cards.json` and `trials.json` from the jar. When a newer jar ships, those files update automatically unless the owner edited them (the old copy is kept as `.previous`).

## Editing The Trials

Open Card Workshop from its launcher, then select **Trials**. Save writes to `run/config/craftbound/trials.json` and keeps the previous version beside it. Use `/craftbound trials reload` with operator permission between matches, or restart the server. For a separate server copy the saved file to `config/craftbound/trials.json`. A newly started expedition uses the new route and economy. Card IDs in opponent decks refer to your existing catalogue.

The map is the run hub: use the wheel to scroll or drag empty parchment in any direction. Zoom out or use **Fit** to plan the whole climb, then select a reachable room and travel. Unvisited **mystery** events hide their identity until entered. The header shows your Health, lives, Emeralds and individual boon/charm icons. Hover **Deck** for a list or click it to browse card artwork, stacked copies and green-highlighted upgrades. Trained copies form their own stacks. Hover a boon or charm for its effect; scroll the icon strip to see a long collection. You can return to the map while choosing a reward, but must finish that choice before travelling onward.

In **Trials → Route map**, **Generated branches** uses each floor’s encounter templates to make seeded corridors and crossroads. **Draw paths yourself** keeps explicit room connections. Generated routes need at least seven floors, at most three templates per floor and one final boss. Untouched old default route settings upgrade automatically, with a backup next to `trials.json`; custom routes and already-started runs keep their existing connections.

Assign first-clear card backs and boards in **Trials → Rewards**. Each class has a working palette template. To replace its artwork, use `assets/craftbound/textures/gui/trials/backs/trial_fighter.png` or `assets/craftbound/textures/gui/trials/boards/trial_fighter.png` in the mod or a resource pack. Substitute the design ID you selected. Use a portrait 120 × 168 ratio for backs and a landscape playmat for boards. Reload client resources after changing a PNG.

## Developer layout previews

ZeElectron can right-click a card table, select **Developer**, and preview **Player Vs Player**, **Team Duos (2v2)** or **Commander** without other players or a saved deck. Choose an empty, populated or full board and a seat; press **Esc** to return. These are disposable visual samples using the actual match screens, not playable multiplayer sessions. They never change the world, decks, collection or rewards.

The server controls access through **config/craftbound/developers.json**, created with ZeElectron's verified account UUID (4269df07-431c-4b6e-a5d4-d1b143c91872). Only listed UUIDs receive the Developer button; Creative and OP do not grant it. Edit the list and restart the server to change access. The local host of a source-development singleplayer run also has access so IntelliJ's temporary player names work; that exception is disabled in the packaged mod.

## Setup

For setup instructions, please see the [Fabric Documentation page](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up) related to the IDE that you are using.

## License

All rights reserved. The source is public for reference only; see [LICENSE](LICENSE). Download the mod from its official release page.
