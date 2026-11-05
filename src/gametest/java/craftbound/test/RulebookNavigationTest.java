package craftbound.test;

import com.mojang.blaze3d.platform.InputConstants;
import craftbound.client.deck.DeckCaseScreen;
import craftbound.client.rules.RulebookScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

/** Exercise the binder entry, a wrapped article link, navigation history and return to the same binder. */
final class RulebookNavigationTest {
    static void check(ClientGameTestContext context) {
        var original = new java.util.concurrent.atomic.AtomicReference<DeckCaseScreen>();
        context.runOnClient(mc -> {
            DeckCaseScreen binder = (DeckCaseScreen) mc.gui.screen();
            original.set(binder);
            click(binder, binder.width - 31, 21);
            if (!(mc.gui.screen() instanceof RulebookScreen book)) throw new AssertionError("Rules button did not open the book");
            press(book, "XP");
            press(book, "Hero powers");
            press(book, "Back");
            press(book, "Back");
            button(book, "Health & healing");
        });
        context.waitTicks(3);
        context.takeScreenshot("craftbound-rulebook-start");
        context.runOnClient(mc -> {
            Screen book = mc.gui.screen();
            press(book, "Classes");
            scrollToAndPress(book, "Fighter");
            button(book, "Tools & Durability");
            press(book, "Combat");
            scrollToAndPress(book, "Shield & Ignore Shield");
        });
        context.waitTicks(3);
        context.takeScreenshot("craftbound-rulebook-shield");
        context.runOnClient(mc -> {
            Screen book = mc.gui.screen();
            press(book, "First steps");
            int visited = 1;
            while (button(book, "Next >").active) {
                press(book, "Next >");
                if (++visited > 100) throw new AssertionError("Next page loop");
            }
            if (visited < 30) throw new AssertionError("Missing rulebook chapters");
            press(book, "Binder");
            if (mc.gui.screen() != original.get()) throw new AssertionError("Lost original binder");
        });
    }

    private static Button button(Screen book, String name) {
        return book.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> b.getMessage().getString().equals(name) && b.visible).findFirst()
                .orElseThrow(() -> new AssertionError("Missing visible rulebook button: " + name));
    }

    private static void scrollToAndPress(Screen book, String name) {
        for (int i = 0; i < 25; i++) {
            if (book.children().stream().anyMatch(c -> c instanceof Button b && b.visible && b.getMessage().getString().equals(name))) {
                press(book, name);
                return;
            }
            book.mouseScrolled(book.width / 2.0, book.height / 2.0, 0, -1);
        }
        throw new AssertionError("Unreachable link: " + name);
    }

    private static void press(Screen book, String name) {
        Button b = button(book, name);
        click(book, b.getX() + b.getWidth() / 2.0, b.getY() + b.getHeight() / 2.0);
    }
    private static void click(Screen screen, double x, double y) {
        var event = new MouseButtonEvent(x, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        if (!screen.mouseClicked(event, false)) throw new AssertionError("Missed control at " + x + ", " + y);
        screen.mouseReleased(event);
    }
    private RulebookNavigationTest() {}
}
