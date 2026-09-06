package com.example.fakeoff;

import java.util.ArrayDeque;
import java.util.Deque;

/** Finds three taps followed by three volume-down presses in a moving time window. */
final class UnlockSequence {
    private enum Input {
        TAP,
        VOLUME_DOWN
    }

    private static final Input[] PATTERN = {
            Input.TAP,
            Input.TAP,
            Input.TAP,
            Input.VOLUME_DOWN,
            Input.VOLUME_DOWN,
            Input.VOLUME_DOWN
    };

    private static final class TimedInput {
        final Input input;
        final long timestamp;

        TimedInput(Input input, long timestamp) {
            this.input = input;
            this.timestamp = timestamp;
        }
    }

    private final long timeoutMillis;
    private final Deque<TimedInput> inputs = new ArrayDeque<>();

    UnlockSequence(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    void tap(long now) {
        record(Input.TAP, now);
    }

    boolean volumeDown(long now) {
        record(Input.VOLUME_DOWN, now);
        if (containsPattern()) {
            reset();
            return true;
        }
        return false;
    }

    void reset() {
        inputs.clear();
    }

    private void record(Input input, long now) {
        inputs.addLast(new TimedInput(input, now));
        while (!inputs.isEmpty() && now - inputs.peekFirst().timestamp > timeoutMillis) {
            inputs.removeFirst();
        }
    }

    private boolean containsPattern() {
        int patternIndex = 0;
        for (TimedInput timedInput : inputs) {
            if (timedInput.input == PATTERN[patternIndex]) {
                patternIndex++;
                if (patternIndex == PATTERN.length) {
                    return true;
                }
            }
        }
        return false;
    }
}
