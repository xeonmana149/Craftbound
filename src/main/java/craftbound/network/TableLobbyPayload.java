package craftbound.network;

import craftbound.Craftbound;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

// Server -> client: open the card table's lobby (pick a deck and an opponent), or update it.
// - waiting: the name of a player waiting at this table for an opponent, or "".
// - error: a translation key saying why you can't play yet, or "".
// - close: you're now waiting (for the Card Master or a player), so close the lobby.
public record TableLobbyPayload(BlockPos table, String waiting, String error, boolean close, boolean developerAccess, int tableMode) implements CustomPacketPayload {
    public TableLobbyPayload(BlockPos table,String waiting,String error,boolean close,boolean developerAccess) {
        this(table,waiting,error,close,developerAccess,0);
    }
    public static final Type<TableLobbyPayload> TYPE = new Type<>(Craftbound.id("table_lobby"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TableLobbyPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TableLobbyPayload::table,
            ByteBufCodecs.STRING_UTF8, TableLobbyPayload::waiting,
            ByteBufCodecs.STRING_UTF8, TableLobbyPayload::error,
            ByteBufCodecs.BOOL, TableLobbyPayload::close,
            ByteBufCodecs.BOOL, TableLobbyPayload::developerAccess,
            ByteBufCodecs.VAR_INT, TableLobbyPayload::tableMode,
            TableLobbyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
