<p align="center">CAE Implementation</p>

---

<br>

### Clean Execution Context for Java

Use Clean Execution Context on Java 25 to correlate work across applications and record what happened in each local workflow. An `ExecutionContext` carries the correlation ID, optional outer-flow ID, and optional actor. Its `InnerFlowUnit` records the workflow and holds its `StepUnit` records and shared resources.

Maven automatically adds Clean Mapped Exceptions as a transitive dependency when your application declares this artifact.

<br>

#### Add to your Java application

Add the following dependency to the `<dependencies>` section of your application's `pom.xml`.

```xml
<dependency>
    <groupId>com.clean-arch-enablers</groupId>
    <artifactId>clean-execution-context</artifactId>
    <version>1.0.0-RC3</version>
</dependency>
```

<br>

#### Create and propagate a context

The context factories and getters implement the distinction between [execution and flow identity](../../concept/README.md#execution-identity-versus-flow-identity) and carry the optional [actor](../../concept/README.md#actor).

| API in `com.cae.clean_execution_context` | Use |
| --- | --- |
| `ExecutionContext.of(String outerFlowId, String correlationId, Actor actor)` | Create a context with the supplied outer-flow ID, correlation ID, and actor. |
| `ExecutionContext.of(String correlationId, Actor actor)` | Create a context with the supplied correlation ID and actor. |
| `ExecutionContext.of(String correlationId)` | Create a context with only the supplied correlation ID. |
| `ExecutionContext.ofRandomCorrelationId(String outerFlowId, Actor actor)` | Create a context with the supplied outer-flow ID and actor and a generated correlation ID. |
| `ExecutionContext.ofRandomCorrelationId(Actor actor)` | Create a context with the supplied actor and a generated correlation ID. |
| `getCorrelationId()`, `getOuterFlowId()`, `getActor()` | Read the identifiers and optional actor to carry forward. |
| `getInnerFlow()` | Get the context's local flow, including before it starts. |
| `Actor.getId()`, `Actor.getScopes()` | Supply an application-defined actor ID and its scopes. |

Suppose creating a user starts with an account workflow and continues with a workspace workflow. Both belong to the same outer flow. They use the same correlation ID for this request, and each application gets its own inner flow to record its work.

```java
import com.cae.clean_execution_context.Actor;
import com.cae.clean_execution_context.ExecutionContext;
import java.util.List;

Actor operator = new Actor() {
    @Override
    public String getId() {
        return "operator-1";
    }

    @Override
    public List<String> getScopes() {
        return List.of("accounts:create");
    }
};

record WorkspaceRequested(String outerFlowId, String correlationId, String accountId) {}

// Account application: record the work completed here.
var sendingContext = ExecutionContext.ofRandomCorrelationId("new-user", operator);
var accountFlow = sendingContext.startInnerFlowAsInbound("create-account");
String accountId = "account-42";
accountFlow.complete(accountId);

// Send the account ID and the execution IDs to the workspace application.
var event = new WorkspaceRequested(sendingContext.getOuterFlowId().orElseThrow(),
        sendingContext.getCorrelationId(), accountId);

// Workspace application: continue the same execution in a fresh context.
var receivingContext = ExecutionContext.of(event.outerFlowId(), event.correlationId(), operator);
var workspaceFlow = receivingContext.startInnerFlowAsInbound("provision-workspace", event.accountId());
workspaceFlow.complete("workspace-42");

System.out.println(sendingContext.getCorrelationId().equals(receivingContext.getCorrelationId())); // true
System.out.println(accountFlow.getId()); // create-account
System.out.println(receivingContext.getInnerFlow().getId()); // provision-workspace
Actor actor = receivingContext.getActor().orElseThrow();
String actorId = actor.getId();
List<String> scopes = actor.getScopes();
```

The outer-flow ID identifies the process across requests. The correlation ID identifies this particular execution. If your application already has a correlation ID, pass it as a string. You can also create a context with no outer-flow ID:

```java
import com.cae.clean_execution_context.ExecutionContext;

var context = ExecutionContext.of("gateway/request:42");
var contextWithoutOuterFlow = ExecutionContext.ofRandomCorrelationId(null);
```

If the application has an actor and no outer-flow ID, use `ExecutionContext.of("gateway/request:42", operator)`. Both IDs accept application-defined strings. The random factories generate a UUID string for convenience. Pass `null` for an absent actor.

<br>

#### Start the workflow and track its steps

The two unit types represent the [inner flow and its steps](../../concept/README.md#inner-flow-and-steps).

| API | Use |
| --- | --- |
| `ExecutionContext.startInnerFlowAsInbound(String id)` or `(String id, Object input)` | Start the local workflow as work entering the application. |
| `ExecutionContext.startInnerFlowAsLocal(String id)` or `(String id, Object input)` | Start work initiated within the application. |
| `InnerFlowUnit.addLocalStepOf(String id)` or `(String id, Object input)` | Start and record a local operation. |
| `InnerFlowUnit.addOutboundStepOf(String id)` or `(String id, Object input)` | Start and record an operation directed toward a dependency. |
| `InnerFlowUnit.getSteps()` | Read the live list of steps in insertion order. |
| `new InnerFlowUnit()`, `new StepUnit()` | Create a unit directly when application code owns its lifecycle. |
| `Unit.setIdAndStartTracking(String id, Direction direction)` or `(String id, Direction direction, Object input)` | Start a directly created unit and optionally record input. |

Inside the workspace application, the incoming request starts an inbound flow. Validating the request is a local step. Writing to a repository is an outbound step. The calls below record those operations as they finish.

```java
import com.cae.clean_execution_context.ExecutionContext;
import com.cae.clean_execution_context.InnerFlowUnit;
import com.cae.clean_execution_context.StepUnit;

var context = ExecutionContext.of("new-user", "gateway/request:42", null);
InnerFlowUnit flow = context.startInnerFlowAsInbound("provision-workspace", "account-42");

flow.addLocalStepOf("validate-request").complete();
StepUnit save = flow.addOutboundStepOf("save-workspace", "account-42");
save.complete("workspace-42");
flow.complete("workspace-42");

System.out.println(flow.getSteps().stream().map(StepUnit::getId).toList());
// [validate-request, save-workspace]
```

For work initiated inside the application, use a local flow. Record input only for the operations where it helps explain the execution.

```java
import com.cae.clean_execution_context.ExecutionContext;

var context = ExecutionContext.of("job:retry-provisioning-42");
var flow = context.startInnerFlowAsLocal("reconcile-workspace", "workspace-42");
flow.addLocalStepOf("build-update", "workspace-42").complete("access-policy");
flow.addOutboundStepOf("refresh-workspace-cache").complete();
flow.complete();
```

`Direction.INBOUND` describes work entering the application. `Direction.LOCAL` describes internal work. `Direction.OUTBOUND` describes work directed toward a dependency. `InnerFlowUnit` and `StepUnit` inherit their outcome API from `Unit` and their timing behavior from `TimeTracker`. To track a standalone operation, create a `StepUnit` and call `setIdAndStartTracking("write-audit", Direction.OUTBOUND)` before completing it.

<br>

#### Complete units and read outcomes

The completion and query methods expose the [common execution metadata](../../concept/README.md#common-execution-metadata) shared by workflows and steps.

| API on `Unit` | Use |
| --- | --- |
| `complete()` | Record completion without an output or failure. |
| `complete(Object output)` | Record completion with output. |
| `complete(Exception exception)` | Record completion with the original failure. |
| `complete(Object output, Exception exception)` | Record both output and the original failure, such as a partial result. |
| `getId()`, `getDirection()` | Read which operation ran and its direction. |
| `getInput()`, `getOutput()`, `getException()` | Read optional input, output, and the original failure. |
| `addAnnotation(String)`, `addAnnotations(List<String>)` | Append text notes about the current unit, such as a cache miss or a retry attempt. |
| `getAnnotations()` | Read an immutable snapshot of the unit's notes in insertion order. |
| `wasSuccessful()` | Check whether a completed unit recorded an exception. |
| `calculateLatency()` | Read elapsed time in milliseconds after completion. |
| `getStartTime()`, `getEndTime()` | Read the recorded start and end timestamps. A timestamp is null until its event occurs. |

```java
import com.cae.clean_execution_context.ExecutionContext;

import java.util.List;

var context = ExecutionContext.of("gateway/request:42");
var flow = context.startInnerFlowAsInbound("provision-workspace", "account-42");

var lookup = flow.addOutboundStepOf("directory-lookup", "account-42");
lookup.addAnnotations(List.of("autocache: miss", "autoretry: attempt 1"));
lookup.complete(new IllegalStateException("Directory unavailable"));

// The application recovers using the cached account.
lookup.addAnnotation("fallback: cached account");
flow.addOutboundStepOf("save-workspace", "account-42").complete("workspace-42");
flow.complete("workspace-42");

// The failed lookup remains visible even though provisioning succeeded.
String operation = flow.getId();
var direction = flow.getDirection();
long elapsedMillis = flow.calculateLatency();
System.out.println(operation + " (" + direction + ") took " + elapsedMillis + " ms");
System.out.println("Recorded from " + flow.getStartTime() + " to " + flow.getEndTime());
System.out.println("Input: " + flow.getInput().orElseThrow()); // account-42
System.out.println("Output: " + flow.getOutput().orElseThrow()); // workspace-42
System.out.println("Flow succeeded: " + flow.wasSuccessful()); // true
System.out.println("Lookup succeeded: " + lookup.wasSuccessful()); // false
Exception lookupFailure = lookup.getException().orElseThrow();
List<String> lookupAnnotations = lookup.getAnnotations();

// Use the two-argument overload when a failed operation also has partial output.
var migration = ExecutionContext.of("job:47").startInnerFlowAsLocal("migrate-workspace");
migration.complete("12 files migrated", new IllegalStateException("Storage unavailable"));
```

Start each unit before completing it and finish the flow after its steps. A step can fail while the workflow recovers and succeeds. Record only the input, output, and causes your application needs. Those objects may contain sensitive data.

<br>

#### Share resources and run finishing hooks

The resource API implements [inner-flow shared resources and completion hooks](../../concept/README.md#inner-flow-shared-resources).

| API on `InnerFlowUnit` | Use |
| --- | --- |
| `addSharedResource(String id, Object resource, Consumer<Boolean> hook)` | Register a value and a hook that receives the workflow outcome. |
| `addSharedResource(String id, Object resource, Runnable onSuccess, Runnable onFailure)` | Register a value with separate hooks for success and failure. |
| `getSharedResource(String id)` | Get the resource wrapper, if registered. |
| `getSharedResource(String id, Class<R> type)` | Get the underlying value as the requested type. A missing ID or null value returns an empty `Optional`. An incompatible value throws `ClassCastException`. |
| `isFinishingHookRunning()` | Check whether resource hooks are currently being processed. |
| `finishingHookWasSuccessful()` | Check the hook result after `complete(...)` returns. |
| `getFinishingHookException()` | Inspect collected hook failures, if any. |

Use a named resource when two steps need the same instance. This example uses an in-memory list to stand in for staged transaction writes. The success hook commits the staged writes, and the failure hook discards them.

```java
import com.cae.clean_execution_context.ExecutionContext;
import com.cae.clean_execution_context.InnerFlowSharedResource;
import java.util.ArrayList;
import java.util.List;

record WorkspaceTransaction(List<String> pending, List<String> committed) {
    void commit() {
        committed.addAll(pending);
        pending.clear();
    }

    void rollback() {
        pending.clear();
    }
}

var context = ExecutionContext.of("gateway/request:42");
var flow = context.startInnerFlowAsInbound("provision-workspace");
var audit = new ArrayList<String>();

var begin = flow.addOutboundStepOf("begin-transaction");
var transaction = new WorkspaceTransaction(new ArrayList<>(), new ArrayList<>());
flow.addSharedResource("transaction", transaction, transaction::commit, transaction::rollback);
flow.addSharedResource("audit", audit, succeeded -> audit.add("Flow succeeded: " + succeeded));
begin.complete();

// A later step retrieves the exact resource that the first step registered.
var save = flow.addOutboundStepOf("save-workspace", "workspace-42");
WorkspaceTransaction sharedTransaction = flow.getSharedResource("transaction", WorkspaceTransaction.class)
        .orElseThrow();
sharedTransaction.pending().add("workspace-42");
save.complete("workspace-42");

// The untyped overload returns the wrapper when you need it.
InnerFlowSharedResource registered = flow.getSharedResource("transaction").orElseThrow();
System.out.println(registered.getActualResource() == sharedTransaction); // true

flow.complete("workspace-42");
System.out.println(transaction.committed()); // [workspace-42]
System.out.println(audit); // [Flow succeeded: true]
System.out.println(flow.isFinishingHookRunning()); // false
System.out.println(flow.finishingHookWasSuccessful()); // true
```

Resource IDs are scoped to the flow. A duplicate ID keeps the first resource and its hooks. Finish registering resources before completing the flow. The flow runs the hooks using its recorded outcome and collects hook exceptions by resource ID in `FinishingHookMappedException`. That exception is available separately from the original workflow failure.

`InnerFlowSharedResource` can also be constructed directly when application code manages the hooks itself.

| API | Use |
| --- | --- |
| `InnerFlowSharedResource.of(Object resource, Consumer<Boolean> hook)` | Pair a resource with one outcome-aware hook. |
| `InnerFlowSharedResource.of(Object resource, Runnable onSuccess, Runnable onFailure)` | Pair a resource with one hook per outcome. |
| `new InnerFlowSharedResource(Object resource, Consumer<Boolean> hook, Runnable onSuccess, Runnable onFailure)` | Supply callbacks directly. A generic hook takes precedence when present. |
| `getActualResource()`, `runHook(boolean succeeded)` | Access the value or run the applicable hook. A missing applicable hook throws `InternalMappedException`. |
| `new FinishingHookMappedException(String publicMessage, Map<String, Exception> causes)` | Represent hook failures collected by application code. |
| `FinishingHookMappedException.getCauses()` | Read failures keyed by resource ID. |

If saving a workspace succeeds but releasing a connection fails, report each outcome separately:

```java
import com.cae.clean_execution_context.ExecutionContext;
import com.cae.clean_execution_context.FinishingHookMappedException;

var context = ExecutionContext.of("gateway/request:42");
var flow = context.startInnerFlowAsInbound("provision-workspace");
flow.addSharedResource("repository-connection", new Object(), succeeded -> {
    throw new IllegalStateException("Connection close failed"); // Simulate a cleanup failure.
});
flow.addOutboundStepOf("save-workspace").complete("workspace-42");
flow.complete("workspace-42");

System.out.println("Workflow succeeded: " + flow.wasSuccessful()); // true
System.out.println("Hooks succeeded: " + flow.finishingHookWasSuccessful()); // false
FinishingHookMappedException hookFailure = flow.getFinishingHookException().orElseThrow();
Exception connectionFailure = hookFailure.getCauses().get("repository-connection");
System.out.println(connectionFailure.getMessage()); // Connection close failed
```

<br>
<br>

---

<p align="center">clean software, made easy.</p>
