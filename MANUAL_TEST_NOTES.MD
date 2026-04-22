# Trasck Manual Test Notes

Use this file during human manual testing to capture defects, rough edges, and retest results without overloading `NEXT_STEPS.MD`.

## Test Context

- Backend URL:
- Frontend URL:
- Backend profile:
- Database state: fresh disposable / persistent local
- Browser:
- Tester:
- Date:

## Agent Runtime Checklist

- Codex CLI installed and authenticated on the backend host.
- Claude Code CLI installed and authenticated on the backend host.
- `TRASCK_AGENTS_CLI_WORKER_ENABLED=true` set only on a trusted local test host.
- `TRASCK_CODEX_AGENT_WORKDIR` and `TRASCK_CLAUDE_CODE_AGENT_WORKDIR` point at disposable checkouts when testing edit-capable profiles.
- Provider credentials are created through the Agent page and show only redacted configured state.
- Agent task run directories under `TRASCK_AGENTS_CLI_WORKER_WORKSPACE_ROOT` are reviewed for expected prompt/task/output files and no raw secret leakage.

## Findings

| ID | Area | Steps To Reproduce | Expected | Actual | Severity | Status | Retest Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| MT-001 |  |  |  |  |  | open |  |

## Retest Log

| Date | Finding ID | Result | Notes |
| --- | --- | --- | --- |
