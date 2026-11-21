package com.cae.clean_execution_context;


import com.cae.clean_mapped_exceptions.specifics.InternalMappedException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Records the start and end of a timed operation and calculates its elapsed time. */
public abstract class TimeTracker {

    /** The most recent start time, or null before tracking starts. */
    protected Instant startTime;
    /** The most recent end time, or null before completion. */
    protected Instant endTime;
    /** The computed elapsed time in milliseconds, or null before calculation. */
    protected Long latency;

    /** Creates a tracker that is awaiting its start. */
    public TimeTracker() {
    }

    /** Records a start time and clears the previous end time and cached latency. */
    protected void startTracking(){
        this.startTime = Instant.now();
        this.endTime = null;
        this.latency = null;
    }

    /**
     * Records the end time.
     * @throws InternalMappedException if tracking has yet to start
     */
    protected void endTracking(){
        if (this.hasStarted())
            this.endTime = Instant.now();
        else
            throw new InternalMappedException(
                "Couldn't end tracking",
                "It hasn't started yet"
            );
    }

    /**
     * Checks for a recorded start time.
     * @return whether tracking has started
     */
    protected boolean hasStarted(){
        return this.startTime != null;
    }

    /**
     * Checks for a recorded end time.
     * @return whether the end time has been recorded
     */
    protected boolean isComplete(){
        return this.endTime != null;
    }

    /**
     * Calculates and caches the elapsed time.
     * @return the elapsed time in milliseconds
     * @throws InternalMappedException if tracking is incomplete
     */
    protected Long getLatency(){
        if (this.isComplete())
            return Optional.ofNullable(this.latency)
                    .orElseGet(() -> {
                        this.latency = Duration.between(this.startTime, this.endTime).toMillis();
                        return this.latency;
                    });
        throw new InternalMappedException(
                "Couldn't calculate latency",
                "Either the startTime or the endTime was null (or even both). Make sure to try it only after having the tracking complete"
        );
    }

}
