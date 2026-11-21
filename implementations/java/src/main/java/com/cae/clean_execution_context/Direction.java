package com.cae.clean_execution_context;

/** Describes the direction of the work relative to the application. */
public enum Direction {

    /** Work entering the application. */
    INBOUND,
    /** Work performed within the application. */
    LOCAL,
    /** Work directed toward a dependency. */
    OUTBOUND

}
