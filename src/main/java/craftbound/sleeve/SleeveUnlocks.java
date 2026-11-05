package craftbound.sleeve;

import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

// Immutable collection data, separate from the player attachment lifecycle.
public record SleeveUnlocks(Set<Identifier> ids) {
    public static final SleeveUnlocks EMPTY = new SleeveUnlocks(Set.of());
    public static final Codec<SleeveUnlocks> CODEC = Identifier.CODEC.listOf()
            .xmap(list -> new SleeveUnlocks(Set.copyOf(list)), value -> List.copyOf(value.ids));
    public static final StreamCodec<ByteBuf, SleeveUnlocks> STREAM_CODEC = Identifier.STREAM_CODEC
            .apply(ByteBufCodecs.list()).map(list -> new SleeveUnlocks(Set.copyOf(list)), value -> List.copyOf(value.ids));

    public SleeveUnlocks {
        ids = Set.copyOf(ids);
    }

    public boolean owns(Identifier id) {
        return Sleeves.CLASSIC.equals(id) || ids.contains(id);
    }

    public SleeveUnlocks with(Identifier id) {
        if (!Sleeves.ids().contains(id) || owns(id)) return this;
        Set<Identifier> updated = new HashSet<>(ids);
        updated.add(id);
        return new SleeveUnlocks(updated);
    }

    public List<Identifier> rewardOptions(Set<Identifier> unredeemed) {
        return Sleeves.ids().stream().filter(id -> !Sleeves.isTrial(id) && !owns(id) && !unredeemed.contains(id)).toList();
    }
}
