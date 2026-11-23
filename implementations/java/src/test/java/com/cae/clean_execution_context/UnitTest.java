package com.cae.clean_execution_context;

import com.cae.clean_mapped_exceptions.specifics.InternalMappedException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

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

    @Test
    void recordsExecutionAnnotationsOnTheUnitInOrder() {
        var context = ExecutionContext.of("request-42");
        var flow = context.startInnerFlowAsLocal("checkout");
        var step = flow.addOutboundStepOf("stock-lookup", "SKU-42");
        flow.addAnnotation("autoverify: passed");
        step.addAnnotation("autocache: miss");
        var firstAnnotations = step.getAnnotations();
        step.addAnnotations(List.of("autoretry: attempt 1", "autocache: stored"));
        step.complete(42);
        flow.complete();

        assertEquals(List.of(step), flow.getSteps());
        assertEquals(List.of("autoverify: passed"), flow.getAnnotations());
        assertEquals(List.of("autocache: miss", "autoretry: attempt 1", "autocache: stored"), step.getAnnotations());
        assertEquals(List.of("autocache: miss"), firstAnnotations);
        assertThrows(UnsupportedOperationException.class, () -> firstAnnotations.add("another note"));
    }

    @Test
    void clearsAnnotationsWhenAnExistingUnitStartsAgain() {
        var step = new StepUnit();
        step.setIdAndStartTracking("lookup", Direction.OUTBOUND, "SKU-42");
        step.addAnnotation("autocache: hit");
        step.complete(42);

        step.setIdAndStartTracking("refresh", Direction.LOCAL);
        assertTrue(step.getAnnotations().isEmpty());
        step.addAnnotation("autoretry: attempt 1");
        step.complete();

        step.setIdAndStartTracking("lookup", Direction.OUTBOUND, "SKU-97");
        assertTrue(step.getAnnotations().isEmpty());
    }

    @Test
    void rejectsNullAnnotationsBeforeAppendingAnyOfABatch() {
        var step = new StepUnit();
        step.setIdAndStartTracking("lookup", Direction.OUTBOUND);
        step.addAnnotation("autocache: hit");

        assertThrows(InternalMappedException.class, () -> step.addAnnotation(null));
        assertThrows(InternalMappedException.class, () -> step.addAnnotations(null));
        assertThrows(InternalMappedException.class, () -> step.addAnnotations(Arrays.asList("first", null)));
        assertEquals(List.of("autocache: hit"), step.getAnnotations());
    }
}
