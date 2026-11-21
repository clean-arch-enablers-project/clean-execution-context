package com.cae.clean_execution_context;

import java.util.Optional;
import java.util.UUID;

/** Holds a correlation ID, optional outer-flow ID and actor, and one local inner flow. */
public class ExecutionContext {

    private ExecutionContext(String outerFlowId, InnerFlowUnit innerFlow, String correlationId, Actor actor) {
        this.outerFlowId = outerFlowId;
        this.innerFlow = innerFlow;
        this.correlationId = correlationId;
        this.actor = actor;
    }

    /**
     * Creates a context with caller-supplied identifiers and a fresh inner flow.
     * @param outerFlowId the outer-flow identifier, or null if absent
     * @param correlationId the identifier of this execution
     * @param actor the actor, or null if absent
     * @return a new context
     */
    public static ExecutionContext of(
            String outerFlowId,
            String correlationId,
            Actor actor) {
        return new ExecutionContext(
                outerFlowId,
                new InnerFlowUnit(),
                correlationId,
                actor
        );
    }

    /**
     * Creates a context with a generated correlation ID and a fresh inner flow.
     * @param outerFlowId the outer-flow identifier, or null if absent
     * @param actor the actor, or null if absent
     * @return a new context with a randomly generated UUID string as its correlation ID
     */
    public static ExecutionContext ofRandomCorrelationId(
            String outerFlowId,
            Actor actor) {
        return new ExecutionContext(
                outerFlowId,
                new InnerFlowUnit(),
                UUID.randomUUID().toString(),
                actor
        );
    }

    /**
     * Creates a context with an actor and a caller-supplied correlation ID.
     * @param correlationId the identifier of this execution
     * @param actor the actor, or null if absent
     * @return a new context with an empty outer-flow ID
     */
    public static ExecutionContext of(
            String correlationId,
            Actor actor) {
        return new ExecutionContext(
                null,
                new InnerFlowUnit(),
                correlationId,
                actor
        );
    }

    /**
     * Creates a context with only a caller-supplied correlation ID.
     * @param correlationId the identifier of this execution
     * @return a new context with an empty outer-flow ID and actor
     */
    public static ExecutionContext of(String correlationId) {
        return new ExecutionContext(
                null,
                new InnerFlowUnit(),
                correlationId,
                null
        );
    }

    /**
     * Creates a context without an outer-flow ID and generates a correlation ID.
     * @param actor the actor, or null if absent
     * @return a new context with a randomly generated UUID string as its correlation ID
     */
    public static ExecutionContext ofRandomCorrelationId(Actor actor) {
        return new ExecutionContext(
                null,
                new InnerFlowUnit(),
                UUID.randomUUID().toString(),
                actor
        );
    }

    private final String outerFlowId;
    private final InnerFlowUnit innerFlow;
    private final String correlationId;
    private final Actor actor;

    /**
     * Returns the identifier of the outer flow.
     * @return the outer-flow ID, or empty if absent
     */
    public Optional<String> getOuterFlowId() {
        return Optional.ofNullable(this.outerFlowId);
    }

    /**
     * Returns the inner flow associated with this context.
     * @return the local flow, which may still be awaiting its start
     */
    public InnerFlowUnit getInnerFlow() {
        return this.innerFlow;
    }

    /**
     * Returns the identifier carried across operations of this execution.
     * @return the supplied or generated correlation ID
     */
    public String getCorrelationId() {
        return this.correlationId;
    }

    /**
     * Returns the actor associated with this execution.
     * @return the actor, or empty if absent
     */
    public Optional<Actor> getActor() {
        return Optional.ofNullable(this.actor);
    }

    /**
     * Starts this context's inner flow as inbound work without recording input.
     * @param innerFlowId the identifier of the local workflow
     * @return this context's inner flow
     */
    public InnerFlowUnit startInnerFlowAsInbound(String innerFlowId){
        this.innerFlow.setIdAndStartTracking(innerFlowId, Direction.INBOUND);
        return this.innerFlow;
    }

    /**
     * Starts this context's inner flow as inbound work and records its input.
     * @param innerFlowId the identifier of the local workflow
     * @param input the workflow input, or null if absent
     * @return this context's inner flow
     */
    public InnerFlowUnit startInnerFlowAsInbound(String innerFlowId, Object input){
        this.innerFlow.setIdAndStartTracking(innerFlowId, Direction.INBOUND, input);
        return this.innerFlow;
    }

    /**
     * Starts this context's inner flow as local work without recording input.
     * @param innerFlowId the identifier of the local workflow
     * @return this context's inner flow
     */
    public InnerFlowUnit startInnerFlowAsLocal(String innerFlowId){
        this.innerFlow.setIdAndStartTracking(innerFlowId, Direction.LOCAL);
        return this.innerFlow;
    }

    /**
     * Starts this context's inner flow as local work and records its input.
     * @param innerFlowId the identifier of the local workflow
     * @param input the workflow input, or null if absent
     * @return this context's inner flow
     */
    public InnerFlowUnit startInnerFlowAsLocal(String innerFlowId, Object input){
        this.innerFlow.setIdAndStartTracking(innerFlowId, Direction.LOCAL, input);
        return this.innerFlow;
    }

}
