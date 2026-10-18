package io.github.jopika.gtmultiblocklocator.client;

/** One cue tied to a particular client world. GUI time never consumes its lifetime. */
public final class PendingCue<W, T> {
    private W world;
    private T target;
    private boolean pending;
    private long started;
    private long durationNanos;

    public void select(W world, T target, long durationNanos) {
        this.world = world;
        this.target = target;
        pending = true;
        this.durationNanos = durationNanos;
    }

    public T visible(W currentWorld, boolean screenOpen, long now) {
        if (currentWorld == null || world != currentWorld) clear();
        if (target == null) return null;
        if (pending && !screenOpen) {
            pending = false;
            started = now;
        }
        if (!pending && now - started >= durationNanos) clear();
        return pending || screenOpen ? null : target;
    }

    public void clear() {
        world = null;
        target = null;
        pending = false;
    }
}
