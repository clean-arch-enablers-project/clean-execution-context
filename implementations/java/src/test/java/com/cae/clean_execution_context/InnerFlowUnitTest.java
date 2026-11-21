package com.cae.clean_execution_context;

import com.cae.clean_mapped_exceptions.specifics.InternalMappedException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InnerFlowUnitTest {
    @Test
    void recordsLocalAndOutboundStepsInStartOrder() {
        var flow = startedFlow();
        var validate = flow.addLocalStepOf("validate");
        var calculate = flow.addLocalStepOf("calculate", "invoice");
        var notify = flow.addOutboundStepOf("notify");
        var save = flow.addOutboundStepOf("save", "invoice");

        assertEquals(List.of(validate, calculate, notify, save), flow.getSteps());
        assertEquals(List.of(Direction.LOCAL, Direction.LOCAL, Direction.OUTBOUND, Direction.OUTBOUND),
                flow.getSteps().stream().map(Unit::getDirection).toList());
        assertEquals(List.of("validate", "calculate", "notify", "save"),
                flow.getSteps().stream().map(Unit::getId).toList());
        assertTrue(validate.getInput().isEmpty());
        assertTrue(notify.getInput().isEmpty());
        assertEquals("invoice", calculate.getInput().orElseThrow());
        assertEquals("invoice", save.getInput().orElseThrow());

        validate.complete();
        calculate.complete("total");
        notify.complete(new IllegalStateException("Notification unavailable"));
        save.complete("receipt");
        flow.complete("receipt");
        assertFalse(notify.wasSuccessful());
        assertTrue(flow.wasSuccessful());
    }

    @Test
    void unwrapsTypedResourcesAndRetainsTheFirstRegistrationForEachId() {
        var flow = startedFlow();
        var events = new ArrayList<String>();
        var connection = new StringBuilder("connection");
        flow.addSharedResource("connection", connection, succeeded -> events.add("close " + succeeded));
        flow.addSharedResource("connection", new Object(), succeeded -> fail("Duplicate generic hook ran"));
        flow.addSharedResource("transaction", "transaction", () -> events.add("commit"),
                () -> fail("Rollback on success"));
        flow.addSharedResource("transaction", new Object(), () -> fail("Duplicate success hook ran"),
                () -> fail("Duplicate failure hook ran"));

        assertSame(connection, flow.getSharedResource("connection").orElseThrow().getActualResource());
        assertSame(connection, flow.getSharedResource("connection", CharSequence.class).orElseThrow());
        assertEquals("transaction", flow.getSharedResource("transaction", String.class).orElseThrow());
        assertTrue(flow.getSharedResource("absent").isEmpty());
        assertTrue(flow.getSharedResource("absent", String.class).isEmpty());
        assertThrows(ClassCastException.class, () -> flow.getSharedResource("connection", Integer.class));

        flow.complete();
        assertEquals(2, events.size());
        assertTrue(events.containsAll(List.of("close true", "commit")));
    }

    @Test
    void reportsWhetherFinishingHooksAreRunningOrSucceeded() {
        var flow = startedFlow();
        var calls = new ArrayList<Boolean>();
        assertFalse(flow.isFinishingHookRunning());
        assertFalse(flow.finishingHookWasSuccessful());
        assertTrue(flow.getFinishingHookException().isEmpty());
        flow.addSharedResource("connection", new Object(), succeeded -> {
            assertTrue(flow.isFinishingHookRunning());
            assertFalse(flow.finishingHookWasSuccessful());
            calls.add(succeeded);
        });

        flow.complete();
        assertEquals(List.of(true), calls);
        assertFalse(flow.isFinishingHookRunning());
        assertTrue(flow.finishingHookWasSuccessful());
        assertTrue(flow.getFinishingHookException().isEmpty());
    }

    @Test
    void keepsHookFailuresByResourceIdSeparateFromTheWorkflowFailure() {
        var flow = startedFlow();
        var original = new IllegalStateException("Invoice failed");
        var rollbackFailure = new IllegalStateException("Rollback failed");
        var releaseFailure = new IllegalStateException("Release failed");
        var calls = new ArrayList<Boolean>();
        flow.addSharedResource("transaction", new Object(), () -> fail("Unexpected commit"),
                () -> { throw rollbackFailure; });
        flow.addSharedResource("connection", new Object(), succeeded -> { throw releaseFailure; });
        flow.addSharedResource("audit", new Object(), calls::add);

        flow.complete(original);

        assertFalse(flow.wasSuccessful());
        assertSame(original, flow.getException().orElseThrow());
        assertFalse(flow.isFinishingHookRunning());
        assertFalse(flow.finishingHookWasSuccessful());
        assertEquals(List.of(false), calls);
        var hooks = flow.getFinishingHookException().orElseThrow();
        assertEquals(Map.of("transaction", rollbackFailure, "connection", releaseFailure), hooks.getCauses());
    }

    @Test
    void genericHookTakesPrecedenceAndMissingSpecificHooksAreReported() {
        var calls = new ArrayList<Boolean>();
        var resource = new InnerFlowSharedResource("value", calls::add,
                () -> fail("Generic hook takes precedence"), () -> fail("Generic hook takes precedence"));
        resource.runHook(true);
        resource.runHook(false);
        assertEquals(List.of(true, false), calls);

        var missingHooks = InnerFlowSharedResource.of("value", null, null);
        assertThrows(InternalMappedException.class, () -> missingHooks.runHook(true));
        assertThrows(InternalMappedException.class, () -> missingHooks.runHook(false));
    }

    @Test
    void runsAFailureSpecificHookWhenTheWorkflowFails() {
        var flow = startedFlow();
        var calls = new ArrayList<String>();
        flow.addSharedResource("transaction", new Object(), () -> calls.add("commit"), () -> calls.add("rollback"));

        flow.complete(new IllegalStateException("Invoice failed"));

        assertEquals(List.of("rollback"), calls);
        assertFalse(flow.wasSuccessful());
        assertTrue(flow.finishingHookWasSuccessful());
    }

    private static InnerFlowUnit startedFlow() {
        return ExecutionContext.of("correlation-42").startInnerFlowAsInbound("submit-invoice");
    }
}
