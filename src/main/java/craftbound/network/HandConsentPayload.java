package craftbound.network;
import craftbound.Craftbound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public record HandConsentPayload(String request,String viewer,int sharing) implements CustomPacketPayload {
 public static final Type<HandConsentPayload> TYPE=new Type<>(Craftbound.id("hand_consent"));
 public static final StreamCodec<RegistryFriendlyByteBuf,HandConsentPayload> CODEC=StreamCodec.composite(
  ByteBufCodecs.STRING_UTF8,HandConsentPayload::request,ByteBufCodecs.STRING_UTF8,HandConsentPayload::viewer,
  ByteBufCodecs.VAR_INT,HandConsentPayload::sharing,HandConsentPayload::new);
 public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
