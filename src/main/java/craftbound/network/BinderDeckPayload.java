package craftbound.network;

import craftbound.Craftbound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

// Stable deck IDs prevent a delayed edit from affecting another saved deck.
public record BinderDeckPayload(int location, String deckId, int action, String value, boolean enchanted)
        implements CustomPacketPayload {
    public BinderDeckPayload(boolean mainHand, String deckId, int action, String value, boolean enchanted) { this(mainHand ? 1 : 0, deckId, action, value, enchanted); }
    public static final int CREATE=0, ADD=1, REMOVE=2, RENAME=3, CLASS=4, SLEEVE=5, CLEAR=6, DELETE=7, IMPORT=8;
    public static final Type<BinderDeckPayload> TYPE = new Type<>(Craftbound.id("binder_deck"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BinderDeckPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BinderDeckPayload::location,
            ByteBufCodecs.stringUtf8(40), BinderDeckPayload::deckId,
            ByteBufCodecs.VAR_INT, BinderDeckPayload::action,
            ByteBufCodecs.stringUtf8(256), BinderDeckPayload::value,
            ByteBufCodecs.BOOL, BinderDeckPayload::enchanted, BinderDeckPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
