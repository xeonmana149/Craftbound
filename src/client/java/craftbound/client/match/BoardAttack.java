package craftbound.client.match;

import com.google.gson.JsonObject;
import craftbound.card.Cards;
import craftbound.game.Match;
import java.util.List;
import net.minecraft.resources.Identifier;
import static craftbound.client.match.MultiplayerBoardLayout.*;

/** Keep the public participants and their original positions even if the attack removes a card. */
record BoardAttack(End from, End to, String caption) {
    record End(int side, int slot, int count, boolean block, String subject) {
        Point point(List<Seat> seats, Mat mat, int home, int width) {
            for (var seat : seats) if (seat.owner() == side) {
                Point anchor = slot < 0 ? seat.hero() : seat.unit(block, count, slot);
                return mat.screen(anchor.x(), anchor.y());
            }
            Rect tab = rivalTab(width, home, side);
            return new Point(tab.cx(), tab.y() + tab.h() - 1);
        }
    }

    static BoardAttack read(JsonObject before, JsonObject path) {
        int sourceSide = path.get("from").getAsInt(), targetSide = path.get("to").getAsInt();
        var players = before.getAsJsonArray("players");
        var attacker = players.get(sourceSide).getAsJsonObject();
        var defender = players.get(targetSide).getAsJsonObject();
        End from = end(attacker, sourceSide, path.get("source").getAsInt());
        End to = end(defender, targetSide, path.get("target").getAsInt());
        String caption = attacker.get("name").getAsString() + "’s " + from.subject() + " attacked "
                + defender.get("name").getAsString() + "’s " + to.subject();
        return new BoardAttack(from, to, caption);
    }

    private static End end(JsonObject owner, int side, int mid) {
        if (mid == Match.HERO) return new End(side, -1, 0, false, "hero");
        int[] counts = new int[2];
        int slot = -1, kind = 0;
        String subject = "unit";
        for (var raw : owner.getAsJsonArray("board")) {
            var unit = raw.getAsJsonObject();
            int row = unit.get("block").getAsBoolean() ? 1 : 0;
            if (unit.get("mid").getAsInt() == mid) {
                slot = counts[row];
                kind = row;
                var id = Identifier.tryParse(unit.get("id").getAsString());
                if (id != null && Cards.get(id) != null) subject = Cards.name(id).getString();
            }
            counts[row]++;
        }
        return new End(side, slot, counts[kind], kind == 1, subject);
    }
}
