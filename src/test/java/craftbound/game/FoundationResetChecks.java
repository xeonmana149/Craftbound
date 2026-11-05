package craftbound.game;

import craftbound.card.*;
import craftbound.binder.BinderContents;
import craftbound.vendor.CardVendor;
import com.google.gson.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

final class FoundationResetChecks {
    static void run() {
        var baseline = CardCatalog.builtin();
        CardCatalog.install(baseline);
        WorkshopChecks.ok(baseline.entries().size() == 9 && Cards.collectible().isEmpty(), "Reset keeps only nine system tokens");
        WorkshopChecks.ok(baseline.entries().values().stream().allMatch(e -> e.definition().token()), "No old collectibles shipped");
        WorkshopChecks.ok(Cards.collectorTotal() == 0, "Collector numbers restart at one");
        CardCatalog.validateReplacement(baseline, baseline);
        BinderContents.fullCollection();
        WorkshopChecks.ok(CardVendor.lineup(1, 1, 1).isEmpty(), "Empty vendor lineup is safe");
        var empty = BoosterPacks.open(RandomSource.create(1), 0, 0);
        WorkshopChecks.ok(empty.pulls().isEmpty() && !empty.jackpot(), "Empty pack returns no cards");
        var root = JsonParser.parseString(baseline.json()).getAsJsonObject();
        var card = root.getAsJsonArray("cards").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(c -> c.get("id").getAsString().equals("craftbound:wolf_pup")).findFirst().orElseThrow().deepCopy();
        card.addProperty("id", "craftbound:first_new_card");card.addProperty("token", false);card.addProperty("rarity", "EPIC");
        root.getAsJsonArray("cards").add(card);
        var fresh = CardCatalog.parse(root.toString());
        CardCatalog.validateReplacement(baseline, fresh);CardCatalog.install(fresh);
        var id = Identifier.parse("craftbound:first_new_card");
        WorkshopChecks.ok(Cards.collectorNumber(id) == 1, "First new collectible is number one");
        for (int i = 0; i < 100; i++) {
            var pack = BoosterPacks.open(RandomSource.create(i), 0, 0);
            WorkshopChecks.ok(pack.pulls().size() == 5 && pack.pulls().stream().allMatch(p -> p.card().equals(id)) && !pack.jackpot(), "Partial set pack fallback");
        }
        root.remove("workshopEpoch");
        boolean rejected = false;
        try {CardCatalog.validateReplacement(baseline, CardCatalog.parse(root.toString()));} catch (IllegalArgumentException ex) {rejected = true;}
        WorkshopChecks.ok(rejected, "Stale Workshop catalogues cannot restore pre-reset cards");
        CardCatalog.install(baseline);
    }
}
