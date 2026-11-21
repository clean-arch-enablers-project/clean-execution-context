<p align="center">CAE Artifact</p>

---

<br>

### Clean Execution Context

A context that accompanies application work, connecting execution identity, actor information, recorded operations, and resources shared within a flow.

<br>

#### Start here

Read the [concept guide](concept/README.md) to understand the problem, vocabulary, and intended behavior. Then choose a language project from the [implementation checklist](#implementations) below to explore its code and available usage instructions.

<br>

#### Repository structure

| Path | Purpose |
| --- | --- |
| [`concept/`](concept/) | Language-independent explanation of the concept, starting with [`concept/README.md`](concept/README.md). |
| [`implementations/`](implementations/) | Language-specific project folders; see the checklist below for their current status. |
| [`LICENSE`](LICENSE) | Apache License 2.0 terms for this repository. |

<br>

#### CONCEPT and IMPLEMENTATIONS

**CONCEPT** explains what the component means and how it should behave, independently of a programming language or framework. It lives in [`concept/`](concept/).

**IMPLEMENTATIONS** translate that shared meaning into each language's native APIs and conventions. Source code, tests, build configuration, and language-specific usage documentation belong to the individual implementation project. This separation keeps the conceptual guide shared while allowing each implementation to evolve with its own tooling.

<br>

#### Implementations

Checked items have implementation code in this repository. Unchecked items are reserved language folders containing only `.gitkeep`; they are placeholders, with no usable implementation yet. A check indicates code is present, not that a release has been published.

- [x] [Java](implementations/java/) — Execution identity, actor, flow and step tracking, and flow-scoped resources.
- [ ] [C#](implementations/csharp/) — Placeholder; implementation not yet available.
- [ ] [Go](implementations/go/) — Placeholder; implementation not yet available.
- [ ] [Python](implementations/python/) — Placeholder; implementation not yet available.

Licensed under [Apache License 2.0](LICENSE).

<br>
<br>

---

<p align="center">clean software, made easy.</p>
