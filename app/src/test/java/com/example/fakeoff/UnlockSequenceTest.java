package com.example.fakeoff;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UnlockSequenceTest {
    @Test
    public void unlocksAfterThreeTapsThenThreeVolumeDownPresses() {
        UnlockSequence sequence = new UnlockSequence(10_000);
        sequence.tap(100);
        sequence.tap(200);
        sequence.tap(300);
        assertFalse(sequence.volumeDown(400));
        assertFalse(sequence.volumeDown(500));
        assertTrue(sequence.volumeDown(600));
    }

    @Test
    public void ignoresVolumePressesThatDoNotBelongToValidSubsequence() {
        UnlockSequence sequence = new UnlockSequence(10_000);
        sequence.tap(100);
        assertFalse(sequence.volumeDown(150));
        sequence.tap(200);
        sequence.tap(300);
        assertFalse(sequence.volumeDown(400));
        assertFalse(sequence.volumeDown(500));
        assertTrue(sequence.volumeDown(600));
    }

    @Test
    public void sequenceExpires() {
        UnlockSequence sequence = new UnlockSequence(10_000);
        sequence.tap(0);
        sequence.tap(100);
        sequence.tap(200);
        assertFalse(sequence.volumeDown(10_001));
    }

    @Test
    public void ignoresExtraTapsWhileFindingSubsequence() {
        UnlockSequence sequence = new UnlockSequence(10_000);
        sequence.tap(0);
        sequence.tap(1);
        sequence.tap(2);
        assertFalse(sequence.volumeDown(3));
        sequence.tap(4);
        assertFalse(sequence.volumeDown(5));
        assertTrue(sequence.volumeDown(6));
    }

    @Test
    public void movingWindowDropsOnlyExpiredInputs() {
        UnlockSequence sequence = new UnlockSequence(10_000);
        sequence.tap(0); // This input expires, but the later valid sequence remains.
        sequence.tap(2_000);
        sequence.tap(3_000);
        sequence.tap(4_000);
        assertFalse(sequence.volumeDown(11_000));
        assertFalse(sequence.volumeDown(11_500));
        assertTrue(sequence.volumeDown(12_000));
    }
}
