package craftbound.deck;

import com.mojang.serialization.Codec;
import craftbound.card.DeckRules;
import craftbound.game.CommanderMatch;

public enum DeckFormat {
    DUEL("1v1"), DUOS("2v2"), COMMANDER("Commander");
    public static final Codec<DeckFormat> CODEC = Codec.STRING.comapFlatMap(value -> {
        try { return com.mojang.serialization.DataResult.success(valueOf(value)); }
        catch (IllegalArgumentException e) { return com.mojang.serialization.DataResult.error(()->"Unknown deck format: "+value); }
    }, DeckFormat::name);
    public final String title;
    DeckFormat(String title) { this.title=title; }
    public int size() { return this==COMMANDER ? CommanderMatch.DECK_SIZE : DeckRules.DECK_SIZE; }
    public static DeckFormat table(int mode) { return mode==2 ? COMMANDER : mode==1 ? DUOS : DUEL; }
}
