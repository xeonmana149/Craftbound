package craftbound.board;

import craftbound.card.CardClass;
import craftbound.mastery.MasteryProgress;
import java.util.Locale;
import net.minecraft.util.RandomSource;

public enum BoardDesign {
    CLASSIC("Classic", 1, 0xFF565E59, 0xFF2A2B2F, 0xFF24252A, 0xFFAE885A),
    SPRUCE("Spruce Lodge", 1, 0xFF465A50, 0xFF45372C, 0xFF3D3028, 0xFFC39A60),
    AMETHYST("Amethyst Grotto", 2, 0xFF535065, 0xFF393543, 0xFF32303C, 0xFFBBA6D6),
    PRISMARINE("Ocean Monument", 3, 0xFF416064, 0xFF28464A, 0xFF254043, 0xFF8CC5B6),
    TRIAL_FIGHTER("Raid Hall", CardClass.FIGHTER),
    TRIAL_BUILDER("Keystone Keep", CardClass.BUILDER),
    TRIAL_FARMER("Harvest Court", CardClass.FARMER),
    TRIAL_FISHERMAN("Coral Landing", CardClass.FISHERMAN),
    TRIAL_ALCHEMIST("Brewing Chamber", CardClass.ALCHEMIST),
    TRIAL_MINER("Copper Gallery", CardClass.MINER),
    TRIAL_CHEF("Hearth Kitchen", CardClass.CHEF),
    TRIAL_TAMER("Wolf Hollow", CardClass.TAMER),
    TRIAL_REDSTONER("Signal Tower", CardClass.REDSTONER);

    public final String title;
    public final int masteryLevel, mat, brick, alternateBrick, accent;

    BoardDesign(String title, int masteryLevel, int mat, int brick, int alternateBrick, int accent) {
        this.title = title;
        this.masteryLevel = masteryLevel;
        this.mat = mat;
        this.brick = brick;
        this.alternateBrick = alternateBrick;
        this.accent = accent;
    }

    BoardDesign(String title, CardClass hue) {
        this(title, 0, mix(hue.darkColor(), 0xFF62615A), hue.darkColor(),
                mix(hue.darkColor(), 0xFF332E27), hue.lightColor());
    }
    private static int mix(int a, int b) { return 0xFF000000 | (((a & 0x00FEFEFE) + (b & 0x00FEFEFE)) >>> 1); }
    public boolean trialReward() { return masteryLevel == 0; }

    public String id() { return name().toLowerCase(Locale.ROOT); }

    public static BoardDesign fromId(String id) {
        for (BoardDesign design : values()) if (design.id().equals(id)) return design;
        return CLASSIC;
    }

    public boolean unlockedBy(MasteryProgress mastery) {
        if (trialReward()) return false;
        if (masteryLevel <= 1) return true;
        for (CardClass cls : CardClass.values()) {
            if (cls != CardClass.NEUTRAL && mastery.level(cls) >= masteryLevel) return true;
        }
        return false;
    }

    public static BoardDesign choose(BoardDesign first, BoardDesign second, RandomSource random) {
        return random.nextBoolean() ? first : second;
    }
}
