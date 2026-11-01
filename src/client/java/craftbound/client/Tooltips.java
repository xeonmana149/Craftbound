package craftbound.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

// Shows a tooltip with long lines wrapped, so it comes out roughly square instead of one long strip.
public class Tooltips {
    private static final int MAX_WIDTH = 170;

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
