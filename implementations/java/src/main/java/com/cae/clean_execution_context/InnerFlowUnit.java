package com.cae.clean_execution_context;

import java.util.*;
import java.util.function.Consumer;

/**
 * Records a local workflow, its steps, and its shared resources.
 * Finish registering resources before completing the flow.
 */
public class InnerFlowUnit extends Unit{

    private final List<StepUnit> steps = new ArrayList<>();
    private final Map<String, InnerFlowSharedResource> sharedResources = new HashMap<>();
    private boolean finishingHookRunning = false;
    private FinishingHookMappedException finishingHookException;

    /** Creates an inner flow that is awaiting its start. */
    public InnerFlowUnit() {
    }

    /**
     * Starts and records a local step without input.
     * @param stepId the identifier of the operation
     * @return the started step
     */
    public StepUnit addLocalStepOf(String stepId){
        var newStep = new StepUnit();
        newStep.setIdAndStartTracking(stepId, Direction.LOCAL);
        this.steps.add(newStep);
        return newStep;
    }

    /**
     * Starts and records an outbound step without input.
     * @param stepId the identifier of the operation
     * @return the started step
     */
    public StepUnit addOutboundStepOf(String stepId){
        var newStep = new StepUnit();
        newStep.setIdAndStartTracking(stepId, Direction.OUTBOUND);
        this.steps.add(newStep);
        return newStep;
    }

    /**
     * Starts and records a local step with input.
     * @param stepId the identifier of the operation
     * @param stepInput the step input, or null if absent
     * @return the started step
     */
    public StepUnit addLocalStepOf(String stepId, Object stepInput){
        var newStep = new StepUnit();
        newStep.setIdAndStartTracking(stepId, Direction.LOCAL, stepInput);
        this.steps.add(newStep);
        return newStep;
    }

    /**
     * Starts and records an outbound step with input.
     * @param stepId the identifier of the operation
     * @param stepInput the step input, or null if absent
     * @return the started step
     */
    public StepUnit addOutboundStepOf(String stepId, Object stepInput){
        var newStep = new StepUnit();
        newStep.setIdAndStartTracking(stepId, Direction.OUTBOUND, stepInput);
        this.steps.add(newStep);
        return newStep;
    }

    /**
     * Registers a resource with a hook that receives the workflow outcome.
     * If the ID is already registered, the first resource and its hook are retained.
     * @param resourceId the resource's identifier within this flow
     * @param actualResource the object shared by the flow's operations
     * @param genericHook the hook that receives true on success and false on failure
     */
    public synchronized void addSharedResource(
            String resourceId,
            Object actualResource,
            Consumer<Boolean> genericHook){
        if (!this.sharedResources.containsKey(resourceId)){
            var sharedResource = InnerFlowSharedResource.of(actualResource, genericHook);
            this.sharedResources.put(resourceId, sharedResource);
        }
    }

    /**
     * Registers a resource with separate hooks for success and failure.
     * If the ID is already registered, the first resource and its hooks are retained.
     * @param resourceId the resource's identifier within this flow
     * @param actualResource the object shared by the flow's operations
     * @param specificHookForSuccess the hook for a successful flow
     * @param specificHookForFailure the hook for a failed flow
     */
    public synchronized void addSharedResource(
            String resourceId,
            Object actualResource,
            Runnable specificHookForSuccess,
            Runnable specificHookForFailure){
        if (!this.sharedResources.containsKey(resourceId)){
            var sharedResource = InnerFlowSharedResource.of(
                    actualResource,
                    specificHookForSuccess,
                    specificHookForFailure
            );
            this.sharedResources.put(resourceId, sharedResource);
        }
    }

    /** Runs the registered resource hooks and records their exceptions by resource ID. */
    @Override
    protected void runCustomFinishingHook(){
        this.finishingHookRunning = true;
        var errors = new HashMap<String, Exception>();
        this.sharedResources.forEach((resourceId, sharedResource) -> {
            try{
                sharedResource.runHook(this.wasSuccessful());
            } catch (Exception e){
                errors.put(resourceId, e);
            }
        });
        if (!errors.isEmpty()){
            this.finishingHookException = new FinishingHookMappedException(
                    "Inner flow '"
                    + this.id
                    + "' could not run its finishing hook successfully, "
                    + errors.size() + " errors occurred.",
                    errors
            );
        }
        this.finishingHookRunning = false;
    }

    /**
     * Checks whether resource finishing hooks are being processed.
     * @return whether hook processing is marked as running
     */
    public boolean isFinishingHookRunning(){
        return this.finishingHookRunning;
    }

    /**
     * Checks the hook result after a normal return from {@link #complete()} or another completion overload.
     * @return whether tracking is complete, hook processing is idle, and no hook exception is recorded
     */
    public boolean finishingHookWasSuccessful(){
        return this.isComplete() && (!this.finishingHookRunning) && this.finishingHookException == null;
    }

    /**
     * Returns the steps recorded in this flow.
     * @return the flow's live step list in insertion order
     */
    public List<StepUnit> getSteps(){
        return this.steps;
    }

    /**
     * Returns diagnostic information about resource hook failures.
     * @return the aggregate hook exception, or empty if none has been recorded
     */
    public Optional<FinishingHookMappedException> getFinishingHookException(){
        return Optional.ofNullable(this.finishingHookException);
    }

    /**
     * Looks up a shared resource and its finishing behavior by ID.
     * @param resourceId the resource's identifier within this flow
     * @return the registered resource wrapper, or empty if the ID is absent
     */
    public synchronized Optional<InnerFlowSharedResource> getSharedResource(String resourceId){
        return Optional.ofNullable(this.sharedResources.get(resourceId));
    }

    /**
     * Looks up the actual resource value and casts it to the requested type.
     * @param resourceId the resource's identifier within this flow
     * @param resourceType the non-null class to cast the resource to
     * @param <R> the resource type
     * @return the resource value, or empty if the ID or value is absent
     * @throws ClassCastException if the value is incompatible with the requested type
     */
    public synchronized  <R> Optional<R> getSharedResource(String resourceId, Class<R> resourceType){
        return Optional.ofNullable(this.sharedResources.get(resourceId))
                .map(InnerFlowSharedResource::getActualResource)
                .map(resourceType::cast);
    }

}
