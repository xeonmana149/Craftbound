# Foundation design reference

Open **Design Reference** in Card Workshop, or open `tools/card-editor/reference.html` directly. The searchable reference works offline. `Craftbound-Foundation-Reference.pdf` is the printable edition.

## Foundation card ideas

[Open the searchable field notes](../../tools/card-editor/foundation-ideas.html), or use **Ideas → Browse the Foundation card ideas** in Card Workshop. Each of the nine classes has 40 candidates, two Legendary designs and deck notes. Workshop support labels describe what you can author with the present engine. Nothing is imported into the catalogue.

The editable chapters are in [foundation-ideas/](foundation-ideas/). Regenerate the standalone book with `python -X utf8 tools/card-editor/build-foundation-ideas.py` after editing them.

## Editing and rebuilding

- Edit `Foundation-reference-source.md` for wording standards, class guidance, current rules and proposed mechanics. Keep current capabilities and proposals explicitly separate.
- `Minecraft-26.3-reference-data.json` is a versioned snapshot extracted from the project's Minecraft registries and vanilla data resources. It contains every registered item, block, entity, effect and potion, plus enchantments, biomes and structures. Editorial browsing categories are not Minecraft tags.
- The current-card appendix is regenerated from the bundled `src/main/resources/craftbound/cards.json` using the Workshop's description generator. It deliberately does not read or overwrite `run/config/craftbound/cards.json`.
- After changing rules, update the narrative and regenerate. The guide is a dated snapshot, not a live rules engine.

From the project root, with Python's `reportlab` package and Node available, run:

```powershell
python -X utf8 tools/card-editor/build-reference.py
```

Set `CRAFTBOUND_NODE` to a Node executable path if Node is not on PATH. The script updates the Workshop HTML, PDF, registry snapshot and manifest. Recheck PDF pagination and Workshop search after regeneration. Restart the local Workshop launcher if its server predates the new reference route.

## Boundaries

The guide documents existing effect-maker support and labels ideas that need new code. Wording advice is advisory; it does not silently rewrite custom card descriptions or implement an effect just because its text mentions one. Stable collectible IDs and ordering remain unchanged. Dismantle's legacy enum and packet IDs are reserved for compatibility, but the action is disabled.
