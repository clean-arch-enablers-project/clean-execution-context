/**
 * Carries correlation, actor, and local execution information across application operations.
 * An {@link com.cae.clean_execution_context.ExecutionContext} owns an
 * {@link com.cae.clean_execution_context.InnerFlowUnit}, which tracks
 * {@link com.cae.clean_execution_context.StepUnit steps} and holds resources scoped to that workflow.
 *
 * <p>Create a context for each local workflow and carry its correlation ID into related workflows.
 * Callers coordinate the lifecycle and finish resource registration before completing the flow.
 * Recorded input, output, and exceptions may contain sensitive application data.
 */
package com.cae.clean_execution_context;
