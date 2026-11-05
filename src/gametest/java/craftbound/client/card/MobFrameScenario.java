package craftbound.client.card;

import craftbound.Craftbound;
import craftbound.card.CardCatalog;
import craftbound.card.Cards;
import craftbound.item.CardItem;
import craftbound.test.TestCatalogue;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

public final class MobFrameScenario {
    public static void check(ClientGameTestContext context) {
        var original = CardCatalog.current();
        try {
            TestCatalogue.install();
            for (String name : List.of("zombie", "creeper", "enderman", "warden")) {
                var id = Craftbound.id(name);
                var rarity = Cards.get(id).rarity().name().toLowerCase(java.util.Locale.ROOT);
                context.runOnClient(mc -> {
                    var frame = CardFrames.forCard(Cards.get(id));
                    if (frame == null || !frame.texture().getPath().endsWith("base_mob_" + rarity + ".png"))
                        throw new AssertionError("Wrong printed frame for " + id);
                    mc.gui.setScreen(new CardInspectScreen(CardItem.of(id)));
                });
                context.waitTicks(15);
                context.takeScreenshot("craftbound-mob-frame-" + rarity);
            }
        } finally {
            context.runOnClient(mc -> mc.gui.setScreen(null));
            CardCatalog.install(original);
        }
    }

    private MobFrameScenario() {}
}
