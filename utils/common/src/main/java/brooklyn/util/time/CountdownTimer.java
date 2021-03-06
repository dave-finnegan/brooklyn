package brooklyn.util.time;

import java.util.concurrent.TimeUnit;

import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Stopwatch;

public class CountdownTimer {

    Stopwatch stopwatch = Stopwatch.createUnstarted();
    Duration limit;
    
    private CountdownTimer(Duration limit) {
        this.limit = limit;
    }
    
    /** starts the timer, either initially or if {@link #pause()}d; no-op if already running */
    public synchronized CountdownTimer start() {
        if (!stopwatch.isRunning()) stopwatch.start();
        return this;
    }

    /** pauses the timer, if running; no-op if not running */
    public synchronized CountdownTimer pause() {
        if (stopwatch.isRunning()) stopwatch.stop();
        return this;
    }

    /** returns underlying stopwatch, which caller can inspect for more details or modify */
    public Stopwatch getStopwatch() {
        return stopwatch;
    }
    
    /** how much total time this timer should run for */
    public Duration getLimit() {
        return limit;
    }

    /** return how long the timer has been running (longer than limit if {@link #isExpired()}) */
    public Duration getDurationElapsed() {
        return Duration.nanos(stopwatch.elapsed(TimeUnit.NANOSECONDS));
    }
    
    /** returns how much time is left (negative if {@link #isExpired()}) */
    public Duration getDurationRemaining() {
        return Duration.millis(limit.toMilliseconds() - stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    /** true iff the timer has been running for the given time */
    public boolean isExpired() {
        return stopwatch.elapsed(TimeUnit.MILLISECONDS) > limit.toMilliseconds();
    }
    
    // --- constructor methods
    
    public static CountdownTimer newInstanceStarted(Duration duration) {
        return new CountdownTimer(duration).start();
    }

    public static CountdownTimer newInstancePaused(Duration duration) {
        return new CountdownTimer(duration).pause();
    }

    /** block (on this object) until completed 
     * @throws InterruptedException */
    public synchronized void waitForExpiry() throws InterruptedException {
        while (waitOnForExpiry(this)) {};
    }

    /** as {@link #waitForExpiry()} but catches and wraps InterruptedException as unchecked RuntimeInterruptedExcedption */
    public synchronized void waitForExpiryUnchecked() {
        waitOnForExpiryUnchecked(this);
    }

    /** block on the given argument until the timer is completed or the object receives a notified;
     * callers must be synchronized on the waitTarget
     * @return true if the object is notified (or receives a spurious wake), false if the duration is expired 
     * @throws InterruptedException */
    public boolean waitOnForExpiry(Object waitTarget) throws InterruptedException {
        Duration remainder = getDurationRemaining();
        if (remainder.toMilliseconds() <= 0) 
            return false;
        waitTarget.wait(remainder.toMilliseconds());
        return true;
    }
    /** as {@link #waitOnForExpiry(Object)} but catches and wraps InterruptedException as unchecked RuntimeInterruptedExcedption */
    public boolean waitOnForExpiryUnchecked(Object waitTarget) {
        try {
            return waitOnForExpiry(waitTarget);
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        }        
    }
    
}
