package craftbound.network;

import craftbound.Craftbound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Sent by the binder screen when you take a card out. The server does the actual moving.
public record TakeFromBinderPayload(boolean mainHand, Identifier card, boolean enchanted, boolean all) implements CustomPacketPayload {
    public static final Type<TakeFromBinderPayload> TYPE = new Type<>(Craftbound.id("take_from_binder"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TakeFromBinderPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, TakeFromBinderPayload::mainHand,
            Identifier.STREAM_CODEC, TakeFromBinderPayload::card,
            ByteBufCodecs.BOOL, TakeFromBinderPayload::enchanted,
            ByteBufCodecs.BOOL, TakeFromBinderPayload::all,
            TakeFromBinderPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
