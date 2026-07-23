---
name: repo-branch-versioning
description: "Use when managing branches, versioning, pushes, pull requests, amends, force-with-lease recovery, or keeping a change branch synced with main in this repository. Always use GitHub MCP tools for GitHub-side workflow."
---

# Repo Branch and Versioning Workflow

## Purpose

Use this skill to keep every change isolated, traceable, and easy to recover.

## Rules

- Create a new branch for every change.
- Keep each branch to a single commit.
- Start every branch from the latest `main`.
- Before doing work, ensure the branch is current with `main`.
- Use GitHub MCP tools whenever GitHub-side state, PRs, or branch operations are needed.
- Do not add follow-up commits for PR fixes; amend the existing commit instead.
- After amending, push with `--force-with-lease`.

## Workflow

1. Sync local `main` from the remote source of truth.
2. Create a fresh branch for the requested change.
3. If the branch is not based on the latest `main`, rebase or recreate it before editing.
4. Make the change on that branch only.
5. Commit once with a focused message.
6. Open or update the PR using GitHub MCP.
7. If review or CI finds an issue, fix it on the same branch, amend the commit, and force-push with lease.
8. If the scope changes materially, create a new branch instead of widening the old one.

## Completion Checks

- The branch contains exactly one commit for the change.
- The branch is based on the latest `main`.
- The PR reflects the final amended commit only.
- Any recovery push used `--force-with-lease`, not a plain force push.

## Recovery Rules

- If the push or PR fails because of a fixable issue, repair the branch in place.
- If the branch drifts from `main`, refresh it before continuing.
- If a new request is unrelated, do not reuse the old branch.
