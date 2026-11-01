package craftbound.network;

import craftbound.Craftbound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

// Client -> server: give the Deck Case in your hand a name. An empty name goes back to the default ("Farmer Deck").
public record DeckRenamePayload(boolean mainHand, String name) implements CustomPacketPayload {
    public static final int MAX_LENGTH = 32;

    public static final Type<DeckRenamePayload> TYPE = new Type<>(Craftbound.id("deck_rename"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DeckRenamePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, DeckRenamePayload::mainHand,
            ByteBufCodecs.stringUtf8(MAX_LENGTH * 4), DeckRenamePayload::name,
            DeckRenamePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
