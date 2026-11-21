package com.cae.clean_execution_context;

import java.util.List;

/** Identity and access scopes supplied by the application for a user or system. */
public interface Actor {

    /**
     * Returns the actor's application-defined identifier.
     * @return the actor identifier
     */
    String getId();

    /**
     * Returns the scopes associated with this actor.
     * @return the actor's scopes
     */
    List<String> getScopes();

}
