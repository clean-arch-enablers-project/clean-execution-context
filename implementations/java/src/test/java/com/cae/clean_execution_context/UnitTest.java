package com.cae.clean_execution_context;

import com.cae.clean_mapped_exceptions.specifics.InternalMappedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UnitTest {
    @Test
    void rejectsOutcomeAndLatencyQueriesBeforeCompletion() {
        var step = new StepUnit();
        assertNull(step.getStartTime());
        assertNull(step.getEndTime());
        assertThrows(InternalMappedException.class, step::complete);
        assertThrows(InternalMappedException.class, step::calculateLatency);
        assertThrows(InternalMappedException.class, step::wasSuccessful);

        step.setIdAndStartTracking("send", Direction.OUTBOUND);
        var startedAt = step.getStartTime();
        assertNotNull(startedAt);
        assertNull(step.getEndTime());
        assertThrows(InternalMappedException.class, step::calculateLatency);
        assertThrows(InternalMappedException.class, step::wasSuccessful);
        step.complete();
        assertEquals(startedAt, step.getStartTime());
        assertNotNull(step.getEndTime());
        assertTrue(step.wasSuccessful());
        assertEquals(step.calculateLatency(), step.calculateLatency());
    }

    @Test
    void recordsInputOutputAndTheOriginalFailure() {
        var step = new StepUnit();
        var failure = new IllegalStateException("Connection unavailable");
        step.setIdAndStartTracking("save", Direction.OUTBOUND, "invoice");
        step.complete("partial result", failure);

        assertEquals("save", step.getId());
        assertEquals(Direction.OUTBOUND, step.getDirection());
        assertEquals("invoice", step.getInput().orElseThrow());
        assertEquals("partial result", step.getOutput().orElseThrow());
        assertSame(failure, step.getException().orElseThrow());
        assertFalse(step.wasSuccessful());

        var lookup = new StepUnit();
        lookup.setIdAndStartTracking("lookup", Direction.LOCAL);
        lookup.complete(failure);
        assertTrue(lookup.getOutput().isEmpty());
        assertSame(failure, lookup.getException().orElseThrow());
    }
}
