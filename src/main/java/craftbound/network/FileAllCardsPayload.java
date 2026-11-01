package craftbound.network;

import craftbound.Craftbound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

// Sent by the binder screen's "File all cards" button.
public record FileAllCardsPayload(boolean mainHand) implements CustomPacketPayload {
    public static final Type<FileAllCardsPayload> TYPE = new Type<>(Craftbound.id("file_all_cards"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FileAllCardsPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, FileAllCardsPayload::mainHand,
            FileAllCardsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
