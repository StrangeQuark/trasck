# Trasck Manual Test Notes

Use this file during the human manual test round to record defects, blocked flows, retest results, and follow-up work discovered while running `MANUAL_TEST_CASES.MD`.

## Session

- Date:
- Tester:
- Backend branch/commit:
- Frontend branch/commit:
- Test repo branch/commit:
- Backend URL from environment:
- Frontend URL:
- Selected workspace:
- Selected project:
- Codex CLI worker enabled: Yes / No
- Claude Code CLI worker enabled: Yes / No

## Agent Runtime Checklist

- Codex CLI installed and authenticated on the backend host.
- Claude Code CLI installed and authenticated on the backend host.
- `TRASCK_AGENTS_CLI_WORKER_ENABLED=true` set only on a trusted local test host.
- `TRASCK_CODEX_AGENT_WORKDIR` and `TRASCK_CLAUDE_CODE_AGENT_WORKDIR` point at disposable checkouts when testing edit-capable profiles.
- Provider credentials are created through the Agent page and show only redacted configured state.
- CLI run artifacts are reviewed through the Agent page ZIP download/list/delete/prune controls instead of direct filesystem access.
- Generated prompt/task/output files contain expected task context and no raw secret leakage.

## Defects

| ID | Area | Related Test Case | Severity | Status | Summary |
| --- | --- | --- | --- | --- | --- |
| DEF-001 |  |  |  | Open |  |

### DEF-001 - Title

- Status: Open / Fixed / Retest Passed / Deferred
- Severity: Critical / High / Medium / Low
- Related test case:
- Area:
- Steps to reproduce:
- Expected:
- Actual:
- Evidence:
- Fix notes:
- Retest notes:

## Retest Log

| Date | Defect ID | Result | Notes |
| --- | --- | --- | --- |

## General Notes

-
