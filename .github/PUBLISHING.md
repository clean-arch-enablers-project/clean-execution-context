# Publishing the Java artifact

The [publishing workflow](workflows/publish-java.yml) runs after pushes to `main`, including merged pull requests. Java verification runs on pull requests targeting `main`. [Feature branch automation](FEATURE_PRS.md) creates those pull requests after feature branch pushes. Publication requires a run on `main`.

On a push, the workflow compares the Java POM's group, artifact, and version with the pre-push commit. A new Java implementation or changed coordinates are release candidates. Unchanged coordinates skip publication, so ordinary code or documentation merges do not attempt to overwrite an existing version. Maintain the Java version deliberately in the release PR.

The accepted version formats are `1.0.0-RC1`, `1.0.0-RC2`, and final versions such as `1.0.0`. Snapshot versions are rejected. Each version is immutable once published to Maven Central.

Before building, a Central lookup skips a version that is already published. Only a confirmed HTTP 404 permits a new publication; authentication, rate-limit, server, and network errors fail the workflow instead. Sonatype also validates version uniqueness during upload.

## One-time setup

In this repository's **Settings → Secrets and variables → Actions**, configure these secrets, or grant this repository access to equivalent organization secrets:

| Secret | Value |
| --- | --- |
| `MAVEN_CENTRAL_USERNAME` | Username portion of a Sonatype Central Portal publishing token |
| `MAVEN_CENTRAL_PASSWORD` | Password portion of that publishing token |
| `MAVEN_GPG_KEY` | ASCII-armored exported OpenPGP private signing key, including its header and footer |
| `MAVEN_GPG_PASSPHRASE` | Signing key passphrase; leave unset only for an unencrypted key |

The Portal token must have publishing permission for `com.clean-arch-enablers`. Ensure its namespace is verified and that the signing public key is available through a keyserver supported by Central.

Keep credential values in GitHub Secrets. The workflow uses `setup-java` to generate Maven server settings that reference environment variables. The GPG plugin reads signing material directly from environment variables using its BC signer; the workflow does not commit keys or put passphrases in Maven arguments.

The workflow does not configure GitHub Secrets or change repository protection settings. Protect `main` according to the project's review policy; publication runs for direct pushes to `main` as well as PR merges.

## Publication sequence

Verification and publishing each use a fresh Maven repository without restoring a Maven cache. Workflows check out only their own repository and never install dependencies from source. Maven must resolve dependencies from their configured published repositories; an unpublished or unavailable dependency fails the build.

1. Test the release-decision script and determine whether publication is needed.
2. Set up Java 25 and Maven server configuration.
3. Run `clean verify` with a fresh, isolated Maven repository. This checks unit tests, full coverage, compiler warnings, and Javadoc.
4. Activate the Maven `release` profile, sign the verified JAR, sources, Javadoc and POM, and deploy with Sonatype's Central Publishing Maven plugin.
5. Wait for Central to report the deployment as published.

Both verification and publishing resolve dependencies from Maven Central. The `clean-execution-context` artifact depends on `clean-mapped-exceptions`; publish the required Mapped Exceptions version before publishing Clean Execution Context. Missing or unavailable dependency versions fail verification.

The deploy step skips running unit tests a second time after successful verification. It retains the verification results and coverage data; signing and publication cannot run when verification fails. Test and coverage reports are uploaded even on failure. Deployment credentials are supplied only to the signing/publishing step.

Concurrent publishing runs within one repository are serialized, and a running publication is not canceled by a newer push. The artifact repositories have independent workflows; they do not automatically coordinate dependency publication.

## Retry and first publication

Use **Actions → Publish Java to Maven Central → Run workflow**, selecting `main`, to retry an unpublished version after fixing credentials, service failures, or dependency availability. Manual execution on a feature branch skips the publishing job.

Manual execution is also useful when the workflow is introduced after the Java version is already on `main`: it checks the current version without requiring a new version change. Already published versions still skip deployment.

If Sonatype has accepted a deployment but it is still processing, inspect the Central Portal before retrying. Coordinate versions cannot be overwritten. After changing released Java code, update to a new version before the next publication.

No Maven Central publication is performed just by committing these files to a feature branch.

References: [Sonatype Maven publishing](https://central.sonatype.org/publish/publish-portal-maven/), [Central requirements](https://central.sonatype.org/publish/requirements/), [Maven GPG signing](https://maven.apache.org/plugins/maven-gpg-plugin/sign-mojo.html).
