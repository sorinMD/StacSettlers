package soc.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple utility for reporting elapsed time since its creation or last reset
 * @author MD
 */
public class Timer {

    private static final String DEFAULT_TIMER_NAME = ".~DEFAULT_TIMER~.";
    private final Map<String, Long> timerMap = new HashMap<>();
    private long oldTime;

    public Timer() {
        reset(DEFAULT_TIMER_NAME);
    }

    public long elapsed() {
        return elapsed(DEFAULT_TIMER_NAME);
    }

    public void reset() {
        reset(DEFAULT_TIMER_NAME);
    }

    public void reset(String name) {
        timerMap.put(name, System.currentTimeMillis());
    }

    public void start() {
        reset(DEFAULT_TIMER_NAME);
    }

    public void start(String name) {
        reset(name);
    }

    public long elapsed(String name) {
        return System.currentTimeMillis() - timerMap.get(name);
    }

    public String toString() {
        return toString(DEFAULT_TIMER_NAME);
    }

    public String toString(String name) {
        return elapsed(name) + " ms elapsed";
    }

    public static void main(String[] args) {
        Timer t = new Timer();
        System.out.println("ms elasped: " + t.elapsed());
    }
}


