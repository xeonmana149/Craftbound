package craftbound.network;
import craftbound.Craftbound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public record TurnClockPayload(int turn,int side,int ticks,boolean held) implements CustomPacketPayload {
    public static final Type<TurnClockPayload> TYPE=new Type<>(Craftbound.id("turn_clock"));
    public static final StreamCodec<RegistryFriendlyByteBuf,TurnClockPayload> CODEC=StreamCodec.composite(
        ByteBufCodecs.VAR_INT,TurnClockPayload::turn,ByteBufCodecs.VAR_INT,TurnClockPayload::side,
        ByteBufCodecs.VAR_INT,TurnClockPayload::ticks,ByteBufCodecs.BOOL,TurnClockPayload::held,TurnClockPayload::new);
    public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
