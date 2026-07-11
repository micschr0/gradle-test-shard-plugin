# CI Workflow Permissions Specification

## Purpose

Enforce a consistent top-level `permissions: {}` convention across all GitHub Actions workflows in this repository, with per-job `permissions:` that opt-in to specific scopes only. This prevents permission creep and reduces attack surface for potential CI vulnerabilities.

## Requirements

### Requirement: Top-Level Permissions Lockdown

Every GitHub Actions workflow file MUST start with a top-level `permissions: {}` declaration that explicitly denies all permissions by default.

#### Scenario: Workflow has empty top-level permissions

- GIVEN a workflow file at `.github/workflows/ci.yml`
- WHEN the file is parsed
- THEN it contains `permissions: {}` at the top level

#### Scenario: Workflow mirrors pattern across all three workflows

- GIVEN all workflow files (ci.yml, codeql.yml, release.yml)
- WHEN each file is examined
- THEN all contain `permissions: {}` at the top level

### Requirement: Per-Job Permissions Opt-In

Each job in a workflow MUST opt-in to specific permissions only if needed, never inheriting from the top-level block.

#### Scenario: No permissions opt-in for generic jobs

- GIVEN a job that only runs checks (detekt, shellcheck, actionlint)
- WHEN the job is defined
- THEN it does NOT include a `permissions:` section (relies on top-level `permissions: {}`)

#### Scenario: Workflow-dispatch jobs opt-in to read access

- GIVEN a `workflow_dispatch` job that reads the repository
- WHEN the job is defined
- THEN it includes `permissions: contents: read`

#### Scenario: Check jobs rely on top-level permissions

- GIVEN a check job that runs `gradle check`
- WHEN the job is defined
- THEN it does NOT duplicate `permissions: contents: read` (inherits from top-level)

### Requirement: Checkout Step Uses Persist-Credentials: False

Every `actions/checkout` step MUST include `persist-credentials: false` to prevent sensitive tokens from being checked out and cached.

#### Scenario: Checkout step has persist-credentials disabled

- GIVEN an `actions/checkout` step in any workflow
- WHEN the step is parsed
- THEN it contains `with:` and `persist-credentials: false` within that block

#### Scenario: Pattern consistent across all workflows

- GIVEN all `actions/checkout` invocations
- WHEN each is examined
- THEN all include `persist-credentials: false` in the checkout configuration

### Requirement: Permissions Consistency

The system MUST ensure that top-level `permissions: {}` and per-job opt-ins are consistent across all workflows.

#### Scenario: All workflows follow same pattern

- GIVEN all workflow files in `.github/workflows/`
- WHEN each is examined for permission declarations
- THEN each has top-level `permissions: {}` with per-job opt-ins only where needed
