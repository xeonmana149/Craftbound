package craftbound.client.match;

import craftbound.client.binder.BinderTheme;
import craftbound.client.card.CardRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Leather tabs and parchment selections used at the card table. */
final class TableBookButton extends Button {
    boolean selected;
    TableBookButton(int x, int y, int w, int h, Component label, Runnable action) {
        super(x, y, w, h, label, b -> action.run(), DEFAULT_NARRATION);
    }

    @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float tick) {
        boolean over = active && isHoveredOrFocused();
        g.fill(getX(), getY(), getRight(), getBottom(), selected ? BinderTheme.PAGE : over ? 0xFF805232 : 0xFF48291C);
        g.outline(getX(), getY(), getWidth(), getHeight(), over || selected ? BinderTheme.GOLD : 0xFF996E47);
        if (selected) g.fill(getX()+2, getBottom()-3, getRight()-2, getBottom()-1, 0xFFC49452);
        CardRenderer.fitText(g, Minecraft.getInstance().font, getMessage(), getX()+getWidth()/2,
                getY()+(getHeight()-8)/2, getWidth()-6,
                !active ? 0xFFBEA993 : selected ? BinderTheme.TEXT_DARK : 0xFFFFF1D7, true, false);
    }
}
