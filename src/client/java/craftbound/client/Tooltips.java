package craftbound.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

// Shows a tooltip with long lines wrapped, so it comes out roughly square instead of one long strip.
public class Tooltips {
    private static final int MAX_WIDTH = 160;

    /** Vanilla inventory tooltips use Components; retain every styled span while wrapping. */
    public static void wrapCardTooltip(Font font, List<Component> lines, int availableWidth) {
        int width = Math.max(40, Math.min(MAX_WIDTH, availableWidth));
        List<Component> wrapped = new ArrayList<>();
        for (Component source : lines) {
            if (source.getString().isEmpty()) { wrapped.add(source); continue; }
            for (var part : font.getSplitter().splitLines(source, width, net.minecraft.network.chat.Style.EMPTY)) {
                var line = Component.empty();
                part.visit((style, text) -> {
                    line.append(Component.literal(text).setStyle(style));
                    return java.util.Optional.<Void>empty();
                }, net.minecraft.network.chat.Style.EMPTY);
                wrapped.add(line);
            }
        }
        lines.clear();
        lines.addAll(wrapped);
    }

    public static void show(GuiGraphicsExtractor g, Font font, List<Component> lines, int mouseX, int mouseY) {
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : lines) {
            if (line.getString().isEmpty()) {
                wrapped.add(FormattedCharSequence.EMPTY);
            } else {
                wrapped.addAll(font.split(line, MAX_WIDTH));
            }
        }
        g.setTooltipForNextFrame(font, wrapped, mouseX, mouseY);
    }
}
