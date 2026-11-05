package craftbound.client.mastery;

import craftbound.card.CardClass;
import craftbound.client.Tooltips;
import craftbound.client.card.CardRenderer;
import craftbound.mastery.ClassMastery;
import craftbound.mastery.MasteryProgress;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Arrays;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Read-only overview of the owner's synced class progression. */
public final class MasteryScreen extends Screen {
    private final Screen parent;
    private int row, columns, visibleRows, totalRows;
    private final List<CardClass> classes = Arrays.stream(CardClass.values()).filter(c -> c != CardClass.NEUTRAL).toList();
    public MasteryScreen(Screen parent) { super(Component.literal("Class Mastery"));this.parent = parent; }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.fill(0, 0, this.width, this.height, 0xED211710);
        g.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFD27A);
        int left = 18, top = 45, areaWidth = this.width - 36;
        this.columns = this.width >= 540 ? 3 : this.width >= 340 ? 2 : 1;
        this.totalRows = (this.classes.size() + this.columns - 1) / this.columns;
        this.visibleRows = Math.max(1, Math.min(this.totalRows, (this.height - 93) / 64));
        this.row = Math.clamp(this.row, 0, Math.max(0, this.totalRows - this.visibleRows));
        int cellWidth = (areaWidth - (this.columns - 1) * 8) / this.columns;
        var progress = this.minecraft.player == null ? MasteryProgress.EMPTY : ClassMastery.get(this.minecraft.player);
        List<Component> tooltip = null;
        g.centeredText(this.font, "100 points per level · Maximum level 30", this.width / 2, 27, 0xFFDCC9AB);
        for (int r = 0; r < this.visibleRows; r++) for (int column = 0; column < this.columns; column++) {
            int i = (this.row + r) * this.columns + column;if(i >= this.classes.size()) continue;
            CardClass cls = this.classes.get(i);int level = progress.level(cls), points = progress.progress(cls);
            int x = left + column * (cellWidth + 8), y = top + r * 64;
            boolean hover = mouseX >= x && mouseX < x + cellWidth && mouseY >= y && mouseY < y + 58;
            g.fill(x, y, x + cellWidth, y + 58, hover ? 0xFF694730 : 0xFF513524);
            g.outline(x, y, cellWidth, 58, hover ? 0xFFFFD27A : 0xFF8B6846);
            CardRenderer.fitText(g, this.font, cls.displayName().copy().append(" · Lv " + level).append(craftbound.game.TrialsRewards.cleared(minecraft.player,cls) ? " · Trials \u2713" : ""), x + 7, y + 6, cellWidth - 14, 0xFFFFD27A, false);
            g.fill(x + 7, y + 21, x + cellWidth - 7, y + 29, 0xFF201911);
            int filled = level == 30 ? cellWidth - 14 : Math.round((cellWidth - 14) * points / 100F);
            g.fill(x + 7, y + 21, x + 7 + filled, y + 29, 0xFF76B348);
            g.text(this.font, level == 30 ? "MAX" : points + " / 100 points", x + 7, y + 33, 0xFFF2E3C2);
            int next = 0;for(int milestone : new int[]{2,4,6,8,10}) if(milestone > level) { next = milestone;break; }
            CardRenderer.fitText(g, this.font, Component.literal(next > 0 ? "Next card reward: level " + next : "Card reward milestones complete"),
                    x + 7, y + 45, cellWidth - 14, 0xFFDCC9AB, false);
            if (hover) tooltip = List.of(cls.displayName().copy().withStyle(ChatFormatting.GOLD),
                    Component.literal("Level " + level + " · " + progress.points(cls) + " total points"),
                    Component.literal("Win: +25 · Completed loss: +10"),
                    Component.literal("Matches must reach turn 6. Conceding earns no points."),
                    Component.literal("Levels 2/4/6/8/10 top up a missing Common playset."),
                    Component.literal("Rewards check cards and binders you carry."),
                    Component.literal("No stat bonuses from Mastery."));
        }
        int bottom = this.height - 28;
        button(g, "Back", 18, bottom, 50, mouseX, mouseY);
        if (this.totalRows > this.visibleRows) {
            button(g, "<", this.width - 68, bottom, 22, mouseX, mouseY);
            button(g, ">", this.width - 42, bottom, 22, mouseX, mouseY);
            CardRenderer.fitText(g, this.font, Component.literal("Scroll for more classes"), this.width / 2, bottom + 6, Math.max(40, this.width - 160), 0xFFDCC9AB, true);
        }
        if (tooltip != null) Tooltips.show(g, this.font, tooltip, mouseX, mouseY);
    }
    private void button(GuiGraphicsExtractor g, String text, int x, int y, int w, int mx, int my) {
        g.fill(x, y, x + w, y + 20, mx >= x && mx < x + w && my >= y && my < y + 20 ? 0xFF9B7041 : 0xFF694730);
        g.outline(x, y, w, 20, 0xFFC7A16A);g.centeredText(this.font, text, x + w / 2, y + 6, 0xFFF2E3C2);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if(event.button() == InputConstants.MOUSE_BUTTON_LEFT && event.y() >= this.height - 28 && event.y() < this.height - 8) {
            if(event.x() >= 18 && event.x() < 68) { onClose();return true; }
            if(this.totalRows > this.visibleRows && event.x() >= this.width - 68 && event.x() < this.width - 20) {
                this.row = Math.clamp(this.row + (event.x() < this.width - 44 ? -1 : 1), 0, this.totalRows - this.visibleRows);return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if(vertical != 0) {this.row = Math.clamp(this.row + (vertical > 0 ? -1 : 1), 0, Math.max(0, this.totalRows - this.visibleRows));return true;}
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public void onClose() { this.minecraft.gui.setScreen(this.parent); }
    @Override public boolean isPauseScreen() { return false; }
}
