package com.cae.clean_execution_context;

import com.cae.clean_mapped_exceptions.specifics.InternalMappedException;

import java.util.Optional;

/**
 * Records the timing and outcome of one workflow or step.
 * Callers start the unit, perform the work, and complete it once.
 */
public abstract class Unit extends TimeTracker {

    /** The identifier of the recorded work. */
    protected String id;
    /** The direction of the recorded work. */
    protected Direction direction;
    /** The execution failure, or null if absent. */
    protected Exception exception;
    /** The recorded input, or null if absent. */
    protected Object input;
    /** The recorded output, or null if absent. */
    protected Object output;

    /** Creates a unit that is awaiting its start. */
    public Unit() {
    }

    /**
     * Sets the unit's identity and starts tracking without recording input.
     * @param id the identifier of the work
     * @param direction the direction of the work
     */
    public void setIdAndStartTracking(String id, Direction direction){
        this.id = id;
        this.direction = direction;
        this.exception = null;
        this.input = null;
        this.output = null;
        this.startTracking();
    }

    /**
     * Sets the unit's identity, records input, and starts tracking.
     * @param id the identifier of the work
     * @param direction the direction of the work
     * @param input the input, or null if absent
     */
    public void setIdAndStartTracking(String id, Direction direction, Object input){
        this.id = id;
        this.direction = direction;
        this.input = input;
        this.exception = null;
        this.output = null;
        this.startTracking();
    }

    /**
     * Completes tracking and runs the unit's finishing hook.
     * @throws InternalMappedException if tracking has yet to start
     */
    public void complete(){
        this.endTracking();
        this.runCustomFinishingHook();
    }

    /**
     * Completes tracking, records output, and runs the unit's finishing hook.
     * @param output the output, or null if absent
     * @throws InternalMappedException if tracking has yet to start
     */
    public void complete(Object output){
        this.endTracking();
        this.output = output;
        this.runCustomFinishingHook();
    }

    /**
     * Completes tracking, records output and the execution exception, and runs the finishing hook.
     * @param output the output, or null if absent
     * @param exception the execution failure, or null for success
     * @throws InternalMappedException if tracking has yet to start
     */
    public void complete(Object output, Exception exception){
        this.endTracking();
        this.output = output;
        this.exception = exception;
        this.runCustomFinishingHook();
    }
    /**
     * Completes tracking, records the execution exception, and runs the finishing hook.
     * @param exception the execution failure, or null for success
     * @throws InternalMappedException if tracking has yet to start
     */
    public void complete(Exception exception){
        this.endTracking();
        this.exception = exception;
        this.runCustomFinishingHook();
    }

    /** Runs subclass-specific work after the end time and outcome have been recorded. */
    protected void runCustomFinishingHook(){}

    /**
     * Returns the elapsed time of the work.
     * @return the elapsed time in milliseconds
     * @throws InternalMappedException if tracking is incomplete
     */
    public Long calculateLatency(){
        return this.getLatency();
    }

    /**
     * Checks the recorded outcome of the unit.
     * @return whether the unit completed without a recorded exception
     * @throws InternalMappedException if tracking is incomplete
     */
    public boolean wasSuccessful(){
        if (this.isComplete())
            return this.exception == null;
        throw new InternalMappedException(
                "Couldn't check whether execution was successful",
                "It wasn't complete"
        );
    }


    /**
     * Returns the identifier of the work.
     * @return the unit ID, or null before it is set
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the direction of the work.
     * @return the direction, or null before it is set
     */
    public Direction getDirection() {
        return direction;
    }

    /**
     * Returns the original execution exception.
     * @return the recorded exception, or empty if absent
     */
    public Optional<Exception> getException() {
        return Optional.ofNullable(exception);
    }

    /**
     * Returns the recorded input.
     * @return the input, or empty if absent
     */
    public Optional<Object> getInput() {
        return Optional.ofNullable(input);
    }

    /**
     * Returns the recorded output.
     * @return the output, or empty if absent
     */
    public Optional<Object> getOutput() {
        return Optional.ofNullable(output);
    }
}
