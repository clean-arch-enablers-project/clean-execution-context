package com.cae.clean_execution_context;

import com.cae.clean_mapped_exceptions.specifics.InternalMappedException;

import java.util.Map;

/** Reports exceptions raised while finishing an inner flow's shared resources. */
public class FinishingHookMappedException extends InternalMappedException {

    /**
     * Hook failures indexed by resource identifier.
     * @serial the hook failures keyed by resource ID
     */
    private final Map<String, Exception> causes;

    /**
     * Creates a mapped exception for the collected hook failures.
     * @param briefPublicMessage the public description of the failure
     * @param causes the failures keyed by resource ID
     */
    public FinishingHookMappedException(String briefPublicMessage, Map<String, Exception> causes) {
        super(
                briefPublicMessage
        );
        this.causes = causes;
    }

    /**
     * Returns the collected hook failures.
     * @return the map supplied when this exception was created
     */
    public Map<String, Exception> getCauses() {
        return this.causes;
    }

}
