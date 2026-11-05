package craftbound.network;

import craftbound.Craftbound;
import craftbound.game.CommanderHistory.Entry;
import craftbound.game.CommanderHistory.Kind;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CommanderHistoryPayload(String session, int offset, List<Entry> entries) implements CustomPacketPayload {
    public static final int PAGE_SIZE = 64;
    public static final Type<CommanderHistoryPayload> TYPE = new Type<>(Craftbound.id("commander_history"));
    private static final StreamCodec<RegistryFriendlyByteBuf, Entry> ENTRY = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Entry::round, ByteBufCodecs.VAR_INT, Entry::turn, ByteBufCodecs.VAR_INT, Entry::owner,
            ByteBufCodecs.VAR_INT.map(i -> Kind.values()[i], Kind::ordinal), Entry::kind,
            ByteBufCodecs.STRING_UTF8, Entry::text, Entry::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, CommanderHistoryPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(128), CommanderHistoryPayload::session, ByteBufCodecs.VAR_INT, CommanderHistoryPayload::offset,
            ENTRY.apply(ByteBufCodecs.list(PAGE_SIZE)), CommanderHistoryPayload::entries, CommanderHistoryPayload::new);
    @Override public Type<CommanderHistoryPayload> type() { return TYPE; }
}
