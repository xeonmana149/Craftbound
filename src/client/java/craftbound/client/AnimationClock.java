package craftbound.client;

/** Presentation time only. Server actions, input and turn clocks use real time. */
public final class AnimationClock {
    private static final long START = System.nanoTime();
    public static long nanos() { return (System.nanoTime() - START) / 5 * 4; }
    public static long millis() { return nanos() / 1_000_000L; }
    public static int realDuration(int milliseconds) { return Math.round(milliseconds * .8F); }
    private AnimationClock() {}
}
