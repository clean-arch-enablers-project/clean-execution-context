# Automatic feature pull requests

Pushes to `feature/*` branches automatically open a pull request from that branch into `main`. Nested names such as `feature/customer/search` are included.

Each repository uses its built-in `GITHUB_TOKEN` with `contents: read` and `pull-requests: write`. No additional repository secret or source checkout is needed.

Before running the workflow, enable **Settings → Actions → General → Workflow permissions → Allow GitHub Actions to create and approve pull requests**. Organization policy must also permit this setting.

The workflow reuses an existing open PR for the same source branch and `main`. Later pushes update its commits through GitHub's normal branch tracking; the workflow preserves the existing title and description. Branches without changes to propose and branch deletion events are skipped. Creation is serialized per branch, and a concurrent manual PR is reused.

The Java verification workflow runs for pull requests targeting `main` that change Java implementation or CI files. Opening or reopening a matching PR, or pushing additional commits to it, triggers verification of the proposed merge. Verification can also be run manually through GitHub Actions. GitHub may require workflow approval for pull-request events created using `GITHUB_TOKEN`; approve the pending run from the PR when requested.

The workflow file must be present on the pushed feature branch. Once merged into `main`, new feature branches created from `main` inherit it.

See [GitHub Actions repository settings](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/enabling-features-for-your-repository/managing-github-actions-settings-for-a-repository) and [workflow event behavior](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/trigger-a-workflow).
