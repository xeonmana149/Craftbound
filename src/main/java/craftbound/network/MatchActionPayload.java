package craftbound.network;

import craftbound.Craftbound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

// Something you want to do in your match. The server checks it against the rules before doing it.
// - PLAY: `index` is the card's position in your hand; the target is what it's aimed at (if anything).
// - ATTACK: `index` is your attacking mob's id; the target is the enemy mob or hero.
// - END_TURN and CONCEDE need nothing else.
public record MatchActionPayload(int action, int index, boolean targetHero, int targetSide, int targetMinion)
        implements CustomPacketPayload {
    public static final int PLAY = 0;
    public static final int ATTACK = 1;
    public static final int END_TURN = 2;
    public static final int CONCEDE = 3;

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
