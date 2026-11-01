package craftbound.network;

import craftbound.Craftbound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

// Sent by the binder screen's Disenchant extras button (after you confirm).
public record DisenchantExtrasPayload(boolean mainHand) implements CustomPacketPayload {
    public static final Type<DisenchantExtrasPayload> TYPE = new Type<>(Craftbound.id("disenchant_extras"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DisenchantExtrasPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, DisenchantExtrasPayload::mainHand,
            DisenchantExtrasPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
