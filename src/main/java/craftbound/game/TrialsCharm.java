package craftbound.game;

import craftbound.card.Cards;

/** A charm consumes one charge at most once on its owner's turn, across pauses too. */
final class TrialsCharm {
    static void afterPlay(Match match, int side, CardRef card, Minion summoned) {
        var player = match.player(side);
        if (match.over || match.current != side || player.trialsCharms == null) return;
        if (player.trialsCharmTurn != match.turn) {
            player.trialsCharmTurn = match.turn;
            player.spentTrialsCharms = 0;
        }
        for (int i = 0; i < player.trialsCharms.size(); i++) {
            TrialCharmSpec charm = TrialCharmSpec.find(player.trialsCharms.get(i));
            int bit = 1 << i;
            if (charm == null || (player.spentTrialsCharms & bit) != 0 || !charm.matches(Cards.get(card.id()))) continue;
            player.spentTrialsCharms |= bit;
            int amount = charm.amount();
            match.log(player.name + "'s " + charm.title() + " triggers.", card);
            switch (charm.action()) {
                case BUFF_ATTACK, BUFF_HEALTH -> {
                    if (summoned != null && !summoned.isDead() && player.board.contains(summoned))
                        match.buff(summoned, charm.action() == TrialCharmSpec.Action.BUFF_ATTACK ? amount : 0,
                                charm.action() == TrialCharmSpec.Action.BUFF_HEALTH ? amount : 0);
                }
                case DAMAGE_ENEMY -> match.damageHero(match.opponentSide(side), amount);
                case TOOL_ATTACK -> {
                    if (player.tool != null && player.tool.card.equals(card)) {
                        player.tool.attack += amount;
                        match.event(MatchEvent.Type.TOOL_UPGRADE, side, Match.HERO, card, player.tool.attack, player.tool.uses, null);
                    }
                }
                case DRAW -> { for (int draw = 0; draw < amount && !match.over; draw++) match.draw(side); }
                case HEAL_SELF -> match.healHero(side, amount);
            }
            match.resolveDeaths();
            match.checkWinner();
            if (match.over) break;
        }
    }
    private TrialsCharm() {}
}
