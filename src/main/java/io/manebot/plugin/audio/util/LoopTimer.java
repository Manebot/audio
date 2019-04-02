package io.manebot.plugin.audio.util;

import java.util.logging.Logger;

/**
 * A timer with drift protection.
 */
public class LoopTimer {
    private final long interval;
    private final Object lock;
    private long wake, time, wait;

    public LoopTimer(long interval, Object lock) {
        this.interval = interval * 1_000_000;
        this.lock = lock;
        this.time = wake = System.nanoTime();
    }

    public LoopTimer(long interval) {
        this(interval, new Object());
    }

    public synchronized void sleep() throws InterruptedException {
        // Get timestamp
        time = System.nanoTime();

        // Find the time we need to wake up at
        wake += interval;

        long maxDelay = time - (interval * 30);
        if (wake < maxDelay) {
            Logger.getGlobal().warning(
                    "LoopTimer falling behind: " + ((maxDelay - wake) / 1_000_000D)
                    + "ms behind; abruptly resetting timer..."
            );

            wake = time;
        }

        // Calculate how long we will need to wait
        wait = (wake - time);

        // Wait until the specified time
        sleepFor(wait);

        while (wake > System.nanoTime()) {
            ; //consume cycles
        }
    }

    private void sleepFor(long nanos) throws InterruptedException {
        if (nanos > 0) {
            long elapsed = 0, t0, waitMillis;
            double waitTime;
            int waitNanos;

            while (elapsed < nanos) {
                t0 = System.nanoTime();
                waitTime = nanos / 1_000_000D;
                waitMillis = (long) Math.floor(waitTime);
                waitNanos = (int) ((waitTime - waitMillis) * 1_000_000);

                lock.wait(waitMillis, waitNanos);

                elapsed += System.nanoTime() - t0;
            }
        }
    }
}
