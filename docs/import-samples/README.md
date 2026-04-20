# Import Samples

These files are representative inputs for manual import testing. They are intentionally small enough to paste into the import parser UI while still exercising transforms, value lookups, type/status translations, conflict handling, reruns, and guarded completion.

## Files

- `sample.csv`: generic CSV rows with source IDs, titles that need trimming/collapse transforms, source type/status labels, visibility values, and descriptions.
- `jira-issues.json`: Jira-shaped issue payload using `key`, `fields.summary`, `fields.issuetype.name`, `fields.status.name`, `fields.description`, and `fields.security`.
- `rally-artifacts.json`: Rally-shaped artifact payload using `_refObjectUUID`, `FormattedID`, `Name`, `_type`, `ScheduleState`, `Description`, and `Visibility`.

## Suggested Mapping Rules

Use these mappings as a starting point when creating import mapping templates:

```json
{
  "title": ["title", "fields.summary", "Name"],
  "typeKey": ["type", "fields.issuetype.name", "_type"],
  "statusKey": ["status", "fields.status.name", "ScheduleState"],
  "descriptionMarkdown": ["description", "fields.description", "Description"]
}
```

Suggested defaults:

```json
{
  "descriptionMarkdown": "Imported through Trasck"
}
```

Suggested transform preset:

```json
{
  "title": [
    { "function": "trim" },
    { "function": "replace", "target": "Imported: ", "replacement": "" },
    { "function": "collapse_whitespace" }
  ],
  "descriptionMarkdown": [
    { "function": "trim" }
  ]
}
```

Suggested lookup examples:

- Source field `visibility`, `fields.security`, or `Visibility`
- Source value `Public`
- Target field `visibility`
- Target value `"public"`

Suggested type translations:

- `Story`, `User Story`, and `HierarchicalRequirement` -> `story`
- `Bug` and `Defect` -> `bug`

Suggested status translations:

- `To Do`, `Defined`, and `Open` -> `open`
- `In Progress` and `In-Progress` -> `in_progress`
- `Accepted`, `Done`, and `Closed` -> `done`

## Manual Conflict Flow

1. Parse one sample and materialize with `updateExisting=false`.
2. Parse or edit the same source records with changed titles/descriptions.
3. Materialize again with `updateExisting=false` to create open conflicts instead of duplicates.
4. Preview filtered open conflicts, confirm `RESOLVE FILTERED CONFLICTS`, and resolve them as `update_existing`.
5. Rerun materialization with the source run's snapshot, or override `updateExisting=true` to apply staged updates.
6. To test guarded completion, leave at least one conflict open and complete the job with `COMPLETE WITH OPEN CONFLICTS` plus an audit reason.
