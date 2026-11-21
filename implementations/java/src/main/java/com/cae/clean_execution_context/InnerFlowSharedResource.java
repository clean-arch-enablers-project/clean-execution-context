package com.cae.clean_execution_context;

import com.cae.clean_mapped_exceptions.specifics.InternalMappedException;

import java.util.Optional;
import java.util.function.Consumer;

/** Pairs a shared resource with the callbacks used to finish it. */
public class InnerFlowSharedResource {

    private final Object actualResource;
    private final Consumer<Boolean> genericHook;
    private final Runnable specificHookForSuccess;
    private final Runnable specificHookForFailure;

    /**
     * Creates a shared resource with finishing callbacks.
     * A supplied generic hook takes precedence over both outcome-specific hooks.
     * @param actualResource the object shared by the flow's operations
     * @param genericHook the hook for either outcome, or null to use the outcome-specific hooks
     * @param specificHookForSuccess the hook for success when there is no generic hook
     * @param specificHookForFailure the hook for failure when there is no generic hook
     */
    public InnerFlowSharedResource(
            Object actualResource,
            Consumer<Boolean> genericHook,
            Runnable specificHookForSuccess,
            Runnable specificHookForFailure) {
        this.actualResource = actualResource;
        this.genericHook = genericHook;
        this.specificHookForSuccess = specificHookForSuccess;
        this.specificHookForFailure = specificHookForFailure;
    }

    /**
     * Creates a shared resource with a hook that receives the outcome.
     * @param actualResource the object shared by the flow's operations
     * @param genericHook the hook that receives true on success and false on failure
     * @return the shared resource wrapper
     */
    public static InnerFlowSharedResource of(
            Object actualResource,
            Consumer<Boolean> genericHook) {
        return new InnerFlowSharedResource(
                actualResource,
                genericHook,
                null,
                null
        );
    }

    /**
     * Creates a shared resource with separate hooks for success and failure.
     * @param actualResource the object shared by the flow's operations
     * @param specificHookForSuccess the hook to run on success
     * @param specificHookForFailure the hook to run on failure
     * @return the shared resource wrapper
     */
    public static InnerFlowSharedResource of(
            Object actualResource,
            Runnable specificHookForSuccess,
            Runnable specificHookForFailure) {
        return new InnerFlowSharedResource(
                actualResource,
                null,
                specificHookForSuccess,
                specificHookForFailure
        );
    }

    /**
     * Runs the generic hook if supplied, or the hook for the specified outcome.
     * @param successfulExecution whether the flow succeeded
     * @throws InternalMappedException if there is no hook for the specified outcome
     */
    public void runHook(boolean successfulExecution){
        if (this.genericHook != null)
            this.genericHook.accept(successfulExecution);
        else if (successfulExecution){
            var hookToRun = Optional.ofNullable(this.specificHookForSuccess)
                    .orElseThrow(() -> new InternalMappedException(
                        "Couldn't run hook for successful execution",
                        "Neither a generic nor a specific hook for successful executions was provided"
                    ));
            hookToRun.run();
        }
        else{
            var hookToRun = Optional.ofNullable(this.specificHookForFailure)
                    .orElseThrow(() -> new InternalMappedException(
                        "Couldn't run hook for failure execution",
                        "Neither a generic nor a specific hook for failed executions was provided"
                    ));
            hookToRun.run();
        }
    }

    /**
     * Returns the value shared with the flow's operations.
     * @return the actual resource object
     */
    public Object getActualResource(){
        return this.actualResource;
    }


}
