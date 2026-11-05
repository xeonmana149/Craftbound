package craftbound.client.card;

import craftbound.card.CardCatalog;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;

/** Render-thread cache epoch. A catalogue, language, font or resource reload invalidates every card cache. */
public final class CardRenderEpoch {
    private static Object catalogue, language, font;
    private static long resources = -1, generation;
    public static long current(Font currentFont) {
        Object currentCatalogue = CardCatalog.current(), currentLanguage = Language.getInstance();
        long currentResources = CardFrames.generation();
        if (catalogue != currentCatalogue || language != currentLanguage || font != currentFont || resources != currentResources) {
            catalogue = currentCatalogue;
            language = currentLanguage;
            font = currentFont;
            resources = currentResources;
            generation++;
        }
        return generation;
    }
    private CardRenderEpoch() {}
}
