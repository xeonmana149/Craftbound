package craftbound.client.card;

/** Short, frame-rate independent ease-out shared by card and card-back inspection. */
public final class InspectAnimation {
    private long started;
    public void restart() { started = 0; }
    public float progress() {
        long now = craftbound.client.AnimationClock.nanos();
        if (started == 0) started = now;
        float t = Math.min(1F, (now - started) / 180_000_000F);
        return 1F - (1F - t) * (1F - t) * (1F - t);
    }
    public float scale() { return 0.72F + 0.28F * progress(); }
}
