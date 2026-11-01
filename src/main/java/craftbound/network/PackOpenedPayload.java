package craftbound.network;

import craftbound.Craftbound;
import craftbound.card.BoosterPacks;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Sent from the server after it opens a pack, so the client can play the reveal. The cards are already in
// your inventory by then; the screen only shows what you got. `jackpot` marks an all-Legendary pack.
// `fresh` says, for each pull, whether it's a card you didn't own at all before (it gets a "New!" tag).
public record PackOpenedPayload(List<BoosterPacks.Pull> pulls, boolean jackpot, List<Boolean> fresh) implements CustomPacketPayload {
    public static final Type<PackOpenedPayload> TYPE = new Type<>(Craftbound.id("pack_opened"));

    private static final StreamCodec<RegistryFriendlyByteBuf, BoosterPacks.Pull> PULL_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, BoosterPacks.Pull::card,
            ByteBufCodecs.BOOL, BoosterPacks.Pull::enchanted,
            BoosterPacks.Pull::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, PackOpenedPayload> CODEC = StreamCodec.composite(
            PULL_CODEC.apply(ByteBufCodecs.list(16)), PackOpenedPayload::pulls,
            ByteBufCodecs.BOOL, PackOpenedPayload::jackpot,
            ByteBufCodecs.BOOL.apply(ByteBufCodecs.list(16)), PackOpenedPayload::fresh,
            PackOpenedPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
