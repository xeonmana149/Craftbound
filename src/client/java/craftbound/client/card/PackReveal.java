package craftbound.client.card;

import craftbound.card.CardRarity;
import java.util.Arrays;

/** One celebration at a time, even when clicking quickly or revealing a jackpot. */
public final class PackReveal {
    public static final float FLIP = 0.42F;
    public static final float RETURN = 0.30F;
    private final CardRarity[] rarities;
    private final boolean[] enchanted;
    private final float[] starts;

    public PackReveal(CardRarity[] rarities, boolean[] enchanted) {
        this.rarities = rarities.clone();
        this.enchanted = enchanted.clone();
        this.starts = new float[rarities.length];
        Arrays.fill(starts, Float.POSITIVE_INFINITY);
    }

    public float charge(int i) {
        return switch (rarities[i]) {
            case COMMON -> 0.02F;
            case RARE -> 0.06F;
            case EPIC -> 0.75F;
            case LEGENDARY -> 1.20F;
        } + (enchanted[i] ? 0.35F : 0F);
    }
    public boolean special(int i) { return enchanted[i] || rarities[i].ordinal() >= CardRarity.EPIC.ordinal(); }
    public boolean legendary(int i) { return rarities[i] == CardRarity.LEGENDARY; }
    /** Finish only the current reveal; never reveal untouched cards or strand the queued cards. */
    public int finishActive(float now) {
        int i = active(now);
        if (i < 0) return -1;
        float remaining = starts[i] + duration(i) + 0.08F - now;
        starts[i] = now - duration(i) - 1;
        for (int j = 0; j < starts.length; j++) if (j != i && starts[j] > now && queued(j)) starts[j] -= remaining;
        return i;
    }
    public float hold(int i) { return (rarities[i] == CardRarity.LEGENDARY ? 1.05F : special(i) ? 0.65F : 0.12F) + (enchanted[i] ? 0.20F : 0F); }
    public float duration(int i) { return charge(i) + FLIP + hold(i) + RETURN; }
    public boolean queued(int i) { return Float.isFinite(starts[i]); }
    public float age(int i, float now) { return queued(i) ? now - starts[i] : -1F; }
    public float burstAge(int i, float now) { return age(i, now) - charge(i) - FLIP / 2; }
    public float flip(int i, float now) { return clamp((age(i, now) - charge(i)) / FLIP); }
    public boolean settled(int i, float now) { return queued(i) && age(i, now) >= duration(i); }
    public int active(float now) {
        for (int i = 0; i < starts.length; i++) if (queued(i) && age(i, now) >= 0 && !settled(i, now)) return i;
        return -1;
    }
    public boolean allSettled(float now) {
        for (int i = 0; i < starts.length; i++) if (!settled(i, now)) return false;
        return true;
    }
    public boolean anyUnqueued() {
        for (float start : starts) if (!Float.isFinite(start)) return true;
        return false;
    }
    public void queue(int i, float now) {
        if (queued(i)) return;
        float next = now;
        for (int j = 0; j < starts.length; j++) if (queued(j)) next = Math.max(next, starts[j] + duration(j) + 0.08F);
        starts[i] = next;
    }
    public void queueAll(float now) { for (int i = 0; i < starts.length; i++) queue(i, now); }
    public void skip(float now) { for (int i = 0; i < starts.length; i++) starts[i] = now - duration(i) - 1; }
    public float focus(int i, float now) {
        if (!special(i) || age(i, now) < 0 || settled(i, now)) return 0;
        return smooth(clamp(age(i, now) / 0.25F)) * smooth(clamp((duration(i) - age(i, now)) / RETURN));
    }
    public static float clamp(float n) { return Math.max(0, Math.min(1, n)); }
    public static float smooth(float n) { return n * n * (3 - 2 * n); }
}
