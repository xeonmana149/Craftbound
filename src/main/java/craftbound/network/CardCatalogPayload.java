package craftbound.network;
import craftbound.Craftbound;
import craftbound.card.CardCatalog;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CardCatalogPayload(String json) implements CustomPacketPayload {
    public static final Type<CardCatalogPayload> TYPE = new Type<>(Craftbound.id("card_catalog"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CardCatalogPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(CardCatalog.MAX_JSON), CardCatalogPayload::json, CardCatalogPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
