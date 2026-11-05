package craftbound.mastery;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import craftbound.card.CardClass;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Per-class collection progression. Never changes match stats or card rules. */
public record MasteryProgress(int fighter, int builder, int farmer, int fisherman, int alchemist, int miner, int chef, int tamer, int redstoner) {
    public static final int POINTS_PER_LEVEL = 100, MAX_LEVEL = 30, MAX_POINTS = 2900;
    // PvP (and, later, village Card Master duels) pay full Mastery. Practice against the table's Card Master
    // pays less, so it's for testing decks rather than the fastest way to level.
    public static final int DUEL_WIN = 25, DUEL_LOSS = 10, PRACTICE_WIN = 10, PRACTICE_LOSS = 4;

    public static int matchPoints(boolean won, boolean practice) {
        return practice ? (won ? PRACTICE_WIN : PRACTICE_LOSS) : (won ? DUEL_WIN : DUEL_LOSS);
    }
    public static final MasteryProgress EMPTY = new MasteryProgress(0, 0, 0, 0, 0, 0, 0, 0, 0);
    public static final Codec<MasteryProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(0, MAX_POINTS).optionalFieldOf("fighter", 0).forGetter(MasteryProgress::fighter),
            Codec.intRange(0, MAX_POINTS).optionalFieldOf("builder", 0).forGetter(MasteryProgress::builder),
            Codec.intRange(0, MAX_POINTS).optionalFieldOf("farmer", 0).forGetter(MasteryProgress::farmer),
            Codec.intRange(0, MAX_POINTS).optionalFieldOf("fisherman", 0).forGetter(MasteryProgress::fisherman),
            Codec.intRange(0, MAX_POINTS).optionalFieldOf("alchemist", 0).forGetter(MasteryProgress::alchemist),
            Codec.intRange(0, MAX_POINTS).optionalFieldOf("miner", 0).forGetter(MasteryProgress::miner),
            Codec.intRange(0, MAX_POINTS).optionalFieldOf("chef", 0).forGetter(MasteryProgress::chef),
            Codec.intRange(0, MAX_POINTS).optionalFieldOf("tamer", 0).forGetter(MasteryProgress::tamer),
            Codec.intRange(0, MAX_POINTS).optionalFieldOf("redstoner", 0).forGetter(MasteryProgress::redstoner)
    ).apply(i, MasteryProgress::new));
    public static final StreamCodec<ByteBuf, MasteryProgress> STREAM_CODEC = new StreamCodec<>() {
        public MasteryProgress decode(ByteBuf buffer) { return new MasteryProgress(ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer)); }
        public void encode(ByteBuf buffer, MasteryProgress value) {
            ByteBufCodecs.VAR_INT.encode(buffer,value.fighter());
            ByteBufCodecs.VAR_INT.encode(buffer,value.builder());
            ByteBufCodecs.VAR_INT.encode(buffer,value.farmer());
            ByteBufCodecs.VAR_INT.encode(buffer,value.fisherman());
            ByteBufCodecs.VAR_INT.encode(buffer,value.alchemist());
            ByteBufCodecs.VAR_INT.encode(buffer,value.miner());
            ByteBufCodecs.VAR_INT.encode(buffer,value.chef());
            ByteBufCodecs.VAR_INT.encode(buffer,value.tamer());
            ByteBufCodecs.VAR_INT.encode(buffer,value.redstoner());
        }
    };
    public MasteryProgress {
        fighter = clamp(fighter); builder = clamp(builder); farmer = clamp(farmer); fisherman = clamp(fisherman); alchemist = clamp(alchemist); miner = clamp(miner); chef = clamp(chef); tamer = clamp(tamer); redstoner = clamp(redstoner);
    }
    private static int clamp(int n) { return Math.max(0, Math.min(MAX_POINTS, n)); }
    public int points(CardClass c) {
        return switch(c) { case FIGHTER -> fighter; case BUILDER -> builder; case FARMER -> farmer; case FISHERMAN -> fisherman; case ALCHEMIST -> alchemist; case MINER -> miner; case CHEF -> chef; case TAMER -> tamer; case REDSTONER -> redstoner; default -> 0; };
    }
    public int level(CardClass c) { return 1 + points(c) / POINTS_PER_LEVEL; }
    public int progress(CardClass c) { return points(c) % POINTS_PER_LEVEL; }
    public MasteryProgress earn(CardClass c, int amount) {
        int n = clamp(points(c) + Math.max(0, Math.min(MAX_POINTS, amount)));
        return switch(c) {
            case FIGHTER -> new MasteryProgress(n, builder, farmer, fisherman, alchemist, miner, chef, tamer, redstoner);
            case BUILDER -> new MasteryProgress(fighter, n, farmer, fisherman, alchemist, miner, chef, tamer, redstoner);
            case FARMER -> new MasteryProgress(fighter, builder, n, fisherman, alchemist, miner, chef, tamer, redstoner);
            case FISHERMAN -> new MasteryProgress(fighter, builder, farmer, n, alchemist, miner, chef, tamer, redstoner);
            case ALCHEMIST -> new MasteryProgress(fighter, builder, farmer, fisherman, n, miner, chef, tamer, redstoner);
            case MINER -> new MasteryProgress(fighter, builder, farmer, fisherman, alchemist, n, chef, tamer, redstoner);
            case CHEF -> new MasteryProgress(fighter, builder, farmer, fisherman, alchemist, miner, n, tamer, redstoner);
            case TAMER -> new MasteryProgress(fighter, builder, farmer, fisherman, alchemist, miner, chef, n, redstoner);
            case REDSTONER -> new MasteryProgress(fighter, builder, farmer, fisherman, alchemist, miner, chef, tamer, n);
            default -> this;
        };
    }
}
