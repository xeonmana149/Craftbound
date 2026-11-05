package craftbound.network;
import craftbound.Craftbound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Clears the list only; the cards remain in the player's binders. */
public record ClearDeckPayload(boolean mainHand) implements CustomPacketPayload {
    public static final Type<ClearDeckPayload> TYPE = new Type<>(Craftbound.id("clear_deck"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClearDeckPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, ClearDeckPayload::mainHand, ClearDeckPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
