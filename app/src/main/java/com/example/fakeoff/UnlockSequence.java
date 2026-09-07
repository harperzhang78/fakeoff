package com.example.fakeoff;

import java.util.ArrayDeque;
import java.util.Deque;

/** Finds six continuous matching inputs inside a moving time window. */
final class UnlockSequence {
    private enum Input {
        TAP,
        VOLUME_DOWN,
        OTHER
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
        if (matchesPattern()) {
            reset();
            return true;
        }
        return false;
    }

    void otherInput(long now) {
        record(Input.OTHER, now);
    }

    void reset() {
        inputs.clear();
    }

    private void record(Input input, long now) {
        inputs.addLast(new TimedInput(input, now));
        while (!inputs.isEmpty() && now - inputs.peekFirst().timestamp > timeoutMillis) {
            inputs.removeFirst();
        }
        while (inputs.size() > PATTERN.length) {
            inputs.removeFirst();
        }
    }

    private boolean matchesPattern() {
        if (inputs.size() != PATTERN.length) {
            return false;
        }

        int patternIndex = 0;
        for (TimedInput timedInput : inputs) {
            if (timedInput.input != PATTERN[patternIndex]) {
                return false;
            }
            patternIndex++;
        }
        return true;
    }
}
