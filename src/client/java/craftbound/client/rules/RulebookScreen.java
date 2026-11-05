package craftbound.client.rules;

import com.mojang.blaze3d.platform.InputConstants;
import craftbound.client.binder.BinderTheme;
import static craftbound.client.binder.BinderTheme.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** A pocket handbook. Only the open article is laid out; links share Minecraft's wrapped text metrics. */
public final class RulebookScreen extends Screen {
    private static final int INK = TEXT_DARK;
    private final Screen binder;
    private RulePage page = RulePage.START;
    private final ArrayDeque<Place> history = new ArrayDeque<>();
    private final List<FormattedCharSequence> lines = new ArrayList<>();
    private final List<LineLink> links = new ArrayList<>();
    private record Place(RulePage page, int line) {}
    private record LineLink(Button button, int row) {}

    private int left, top, bookWidth, bookHeight, spine, textX, textY, textWidth, lineStep, visibleLines;
    private int firstLine;
    private Button scrollUp, scrollDown;

    public RulebookScreen(Screen binder) {
        super(Component.literal("Craftbound Rulebook"));
        this.binder = binder;
    }

    @Override protected void init() {
        bookWidth = Math.min(520, width - 10);
        bookHeight = Math.min(310, height - 10);
        left = (width - bookWidth) / 2;
        top = (height - bookHeight) / 2;
        spine = left + Math.clamp(bookWidth / 4, 77, 115);
        textX = spine + 12;
        textY = top + 44;
        textWidth = left + bookWidth - 13 - textX;
        lineStep = font.lineHeight + 4;
        visibleLines = Math.max(1, (top + bookHeight - 33 - textY) / lineStep);
        int footer = top + bookHeight - 24;

        int tabStep = Math.min(24, (footer - top - 39) / RuleChapter.values().length);
        for (RuleChapter chapter : RuleChapter.values()) {
            button(chapter.label, left + 5, top + 32 + chapter.ordinal() * tabStep, spine - left - 11,
                    tabStep - 2, chapter == page.chapter, () -> visit(chapter.pages().getFirst()));
        }
        button("Binder", left + 5, footer, spine - left - 11, 18, false, this::onClose);

        lines.clear();
        links.clear();
        for (Component paragraph : page.content()) addParagraph(paragraph);

        var chapterPages = page.chapter.pages();
        if (page == chapterPages.getFirst() && chapterPages.size() > 1) {
            addParagraph(Component.literal("In this chapter").withStyle(style -> style.withBold(true)));
            var index = Component.empty();
            for (int i = 1; i < chapterPages.size(); i++) {
                if (i > 1) index.append("   ·   ");
                RulePage entry = chapterPages.get(i);
                index.append(RulePage.link(entry, entry.title));
            }
            addParagraph(index);
        }
        if (!lines.isEmpty()) lines.removeLast(); // No paragraph gap after the last line.

        button("Back", textX, footer, 38, 18, false, this::back).active = !history.isEmpty();
        var all = RulePage.values();
        button("Next >", left + bookWidth - 58, footer, 50, 18, false,
                () -> visit(all[page.ordinal() + 1])).active = page.ordinal() + 1 < all.length;
        scrollUp = button("Up", textX + 42, footer, 26, 18, false, () -> scroll(-visibleLines));
        scrollDown = button("Down", textX + 70, footer, 34, 18, false, () -> scroll(visibleLines));
        scroll(0);
    }

    private void addParagraph(Component text) {
        for (FormattedCharSequence line : font.split(text, textWidth)) {
            addLinks(line, lines.size());
            lines.add(line);
        }
        lines.add(FormattedCharSequence.EMPTY);
    }

    private void addLinks(FormattedCharSequence line, int row) {
        // Walk visual glyphs so that wrapped and bold terms have the same hit area as their ink.
        class Run {
            float x, start;
            String destination;
            void change(String next) {
                if (java.util.Objects.equals(destination, next)) return;
                if (destination != null) {
                    RulePage target = RulePage.valueOf(destination);
                    int from = Math.round(start);
                    var link = new Button(textX + from, 0, Math.max(1, Math.round(x) - from), lineStep,
                            Component.literal(target.title), b -> visit(target), message -> message.get()) {
                        @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float tick) {
                            if (isHoveredOrFocused()) g.outline(getX() - 1, getY(), getWidth() + 2, getHeight(), 0xFF347462);
                        }
                    };
                    links.add(new LineLink(addRenderableWidget(link), row));
                }
                start = x;
                destination = next;
            }
        }
        var run = new Run();
        line.accept((index, style, codePoint) -> {
            run.change(style.getInsertion());
            run.x += font.getSplitter().stringWidth(FormattedCharSequence.codepoint(codePoint, style));
            return true;
        });
        run.change(null);
    }

    private Button button(String name, int x, int y, int w, int h, boolean selected, Runnable action) {
        return addRenderableWidget(new BookButton(x, y, w, h, Component.literal(name), action, selected));
    }

    private final class BookButton extends Button {
        private final boolean selected;
        BookButton(int x, int y, int w, int h, Component label, Runnable action, boolean selected) {
            super(x, y, w, h, label, button -> action.run(), DEFAULT_NARRATION);
            this.selected = selected;
        }
        @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float tick) {
            boolean over = active && isHoveredOrFocused();
            g.fill(getX(), getY(), getRight(), getBottom(), selected ? PAGE : over ? 0xFF805635 : 0xFF44291C);
            g.outline(getX(), getY(), getWidth(), getHeight(), over || selected ? GOLD : WOOD_DARK);
            if(selected) g.fill(getX()+2,getBottom()-3,getRight()-2,getBottom()-1,0xFFC99C5C);
            g.text(font, getMessage(), getX() + (getWidth() - font.width(getMessage())) / 2,
                    getY() + (getHeight() - font.lineHeight) / 2 + 1,
                    selected ? INK : active ? 0xFFFFEDCF : 0xFFB2A08A, false);
        }
    }

    private void scroll(int delta) {
        int max = Math.max(0, lines.size() - visibleLines);
        firstLine = Math.clamp(firstLine + delta, 0, max);
        scrollUp.visible = scrollDown.visible = max > 0;
        scrollUp.active = firstLine > 0;
        scrollDown.active = firstLine < max;
        for (LineLink link : links) {
            int visibleRow = link.row - firstLine;
            link.button.visible = visibleRow >= 0 && visibleRow < visibleLines;
            link.button.setY(textY + visibleRow * lineStep - 2);
            if (!link.button.visible && link.button.isFocused()) setFocused(null);
        }
    }

    private void visit(RulePage next) {
        if (next == page) { scroll(-firstLine); return; }
        if (history.size() >= 64) history.removeLast();
        history.push(new Place(page, firstLine));
        page = next;
        firstLine = 0;
        rebuildWidgets();
    }

    private void back() {
        if (history.isEmpty()) return;
        Place place = history.pop();
        page = place.page;
        firstLine = place.line;
        rebuildWidgets();
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float tick) {
        int right = left + bookWidth, bottom = top + bookHeight;
        g.fill(0, 0, width, height, 0xDA17130F);
        BinderTheme.cover(g,left,top,bookWidth,bookHeight);
        g.fill(spine-2,top+7,right-7,bottom-27,WOOD_DARK);
        g.fill(spine, top + 6, right - 6, bottom - 30, PAGE);
        g.fill(spine, top + 6, spine + 3, bottom - 29, 0xFFC5AD84);
        g.fill(textX, top + 35, right - 12, top + 36, 0xFFCCB58E);
        g.text(font, "RULEBOOK", left + 9, top + 12, GOLD, false);
        g.text(font, page.chapter.label + "  /  " + (page.ordinal() + 1), textX, top + 8, 0xFF70563A, false);
        g.text(font, Component.literal(page.title).withStyle(style -> style.withBold(true)), textX, top + 21, INK, false);
        int end = Math.min(lines.size(), firstLine + visibleLines);
        for (int row = firstLine; row < end; row++) {
            g.text(font, lines.get(row), textX, textY + (row - firstLine) * lineStep, INK, false);
        }
        if (lines.size() > visibleLines) {
            int track = visibleLines * lineStep;
            int length = Math.max(5, track * visibleLines / lines.size());
            int offset = (track - length) * firstLine / (lines.size() - visibleLines);
            g.fill(right - 8, textY, right - 6, textY + track, 0xFFD2C2A2);
            g.fill(right - 8, textY + offset, right - 6, textY + offset + length, 0xFF846745);
        }
        super.extractRenderState(g, mx, my, tick);
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical == 0) return super.mouseScrolled(x, y, horizontal, vertical);
        scroll(vertical > 0 ? -3 : 3);
        return true;
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_PAGEUP || event.key() == InputConstants.KEY_PAGEDOWN) {
            scroll(event.key() == InputConstants.KEY_PAGEUP ? -visibleLines : visibleLines);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override public void onClose() { minecraft.gui.setScreen(binder); }
    @Override public boolean isPauseScreen() { return false; }
}
