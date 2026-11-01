package craftbound.network;

import craftbound.Craftbound;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

// Client -> server, from the card table's lobby.
// - CARD_MASTER / PLAYER: you're ready to play the Deck Case in inventory slot `slot` against that opponent.
// - LEAVE: stop waiting.
public record TableActionPayload(BlockPos table, int action, int slot) implements CustomPacketPayload {
    public static final int CARD_MASTER = 0;
    public static final int PLAYER = 1;
    public static final int LEAVE = 2;

    public static final Type<TableActionPayload> TYPE = new Type<>(Craftbound.id("table_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TableActionPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TableActionPayload::table,
            ByteBufCodecs.VAR_INT, TableActionPayload::action,
            ByteBufCodecs.VAR_INT, TableActionPayload::slot,
            TableActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
