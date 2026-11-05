package craftbound.network;

import craftbound.Craftbound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

// Something you want to do in your match. The server checks it against the rules before doing it.
// - PLAY: `index` is the card's position in your hand; the target is what it's aimed at (if anything).
// - ATTACK: `index` is your attacking mob's id (0 for your hero); the target is the enemy mob or hero.
// - MULLIGAN: `index` says which starting cards to swap, one bit per card (bit 0 = the first card).
// - END_TURN, CONCEDE and POWER need nothing else.
public record MatchActionPayload(int action, int index, boolean targetHero, int targetSide, int targetMinion)
        implements CustomPacketPayload {
    public static final int PLAY = 0;
    public static final int ATTACK = 1;
    public static final int END_TURN = 2;
    public static final int CONCEDE = 3;
    public static final int MULLIGAN = 4;
    public static final int POWER = 5;
    public static final int DISMANTLE = 6;
    public static final int REQUEST_PAUSE = 7;
    public static final int ACCEPT_PAUSE = 8;
    public static final int DECLINE_PAUSE = 9;
    // A hand effect asked you to pick a card: `index` is its position in the hand being picked from.
    public static final int CHOOSE_CARD = 10;

    public static final Type<MatchActionPayload> TYPE = new Type<>(Craftbound.id("match_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MatchActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MatchActionPayload::action,
            ByteBufCodecs.VAR_INT, MatchActionPayload::index,
            ByteBufCodecs.BOOL, MatchActionPayload::targetHero,
            ByteBufCodecs.VAR_INT, MatchActionPayload::targetSide,
            ByteBufCodecs.VAR_INT, MatchActionPayload::targetMinion,
            MatchActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
