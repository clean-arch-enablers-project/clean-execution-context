package com.cae.clean_execution_context;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionContextTest {
    private final Actor actor = new Actor() {
        @Override
        public String getId() {
            return "operator";
        }

        @Override
        public List<String> getScopes() {
            return List.of("invoice:write");
        }
    };

    @Test
    void relatedContextsCarryTheSameIdsAndOwnSeparateInnerFlows() {
        var inbound = ExecutionContext.of("billing/monthly", "gateway:request-42", actor);
        var local = ExecutionContext.of(inbound.getOuterFlowId().orElseThrow(), inbound.getCorrelationId(),
                inbound.getActor().orElseThrow());

        assertEquals("gateway:request-42", local.getCorrelationId());
        assertEquals("billing/monthly", local.getOuterFlowId().orElseThrow());
        assertSame(actor, local.getActor().orElseThrow());
        assertEquals("operator", local.getActor().orElseThrow().getId());
        assertEquals(List.of("invoice:write"), local.getActor().orElseThrow().getScopes());
        assertNotSame(inbound.getInnerFlow(), local.getInnerFlow());

        var received = inbound.startInnerFlowAsInbound("submit-invoice", "invoice-42");
        var work = local.startInnerFlowAsLocal("prepare-lines", "invoice-42");
        assertSame(inbound.getInnerFlow(), received);
        assertSame(local.getInnerFlow(), work);
        assertEquals(Direction.INBOUND, received.getDirection());
        assertEquals(Direction.LOCAL, work.getDirection());
        assertEquals("invoice-42", received.getInput().orElseThrow());
        assertEquals("invoice-42", work.getInput().orElseThrow());
        received.complete("receipt-7");
        work.complete();
        assertTrue(received.wasSuccessful());
        assertTrue(work.getOutput().isEmpty());
    }

    @Test
    void createsContextsWithOptionalActorsAndOuterFlowIds() {
        var correlationOnly = ExecutionContext.of("custom-id");
        var withActor = ExecutionContext.of("custom-id", actor);
        var randomWithActor = ExecutionContext.ofRandomCorrelationId(actor);
        var randomWithOuterFlow = ExecutionContext.ofRandomCorrelationId("billing", null);

        assertTrue(correlationOnly.getActor().isEmpty());
        assertTrue(correlationOnly.getOuterFlowId().isEmpty());
        assertEquals("custom-id", withActor.getCorrelationId());
        assertSame(actor, withActor.getActor().orElseThrow());
        assertTrue(withActor.getOuterFlowId().isEmpty());
        assertSame(actor, randomWithActor.getActor().orElseThrow());
        assertTrue(randomWithActor.getOuterFlowId().isEmpty());
        assertEquals("billing", randomWithOuterFlow.getOuterFlowId().orElseThrow());
        assertFalse(randomWithActor.getCorrelationId().isBlank());
        assertNotEquals(randomWithActor.getCorrelationId(), randomWithOuterFlow.getCorrelationId());

        var inbound = correlationOnly.startInnerFlowAsInbound("incoming");
        var local = withActor.startInnerFlowAsLocal("local");
        assertEquals(Direction.INBOUND, inbound.getDirection());
        assertEquals(Direction.LOCAL, local.getDirection());
        assertTrue(inbound.getInput().isEmpty());
        assertTrue(local.getInput().isEmpty());
    }
}
