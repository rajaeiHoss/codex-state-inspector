# codex-state-inspector

A small CLI for inspecting local Codex Desktop/CLI state.

## Goals

- Snapshot a Codex state directory, usually `~/.codex`.
- Diff two snapshots structurally instead of relying on raw minified JSON diffs.
- Inspect `state_5.sqlite` tables and `.codex-global-state.json` workspace state.
- Later: migrate state between macOS users by rewriting absolute paths safely.

## Build

```bash
mvn -q package
```

## Run

```bash
java -jar inspector-cli/target/inspector-cli-0.1.0-SNAPSHOT.jar --help
```

## Commands

### Snapshot

```bash
java -jar inspector-cli/target/inspector-cli-0.1.0-SNAPSHOT.jar snapshot \
  --source ~/.codex \
  --output ~/Desktop/codex-snapshot-before.json
```

### Diff

```bash
java -jar inspector-cli/target/inspector-cli-0.1.0-SNAPSHOT.jar diff \
  --before ~/Desktop/codex-snapshot-before.json \
  --after ~/Desktop/codex-snapshot-after.json \
  --semantic
```

### Inspect

```bash
java -jar inspector-cli/target/inspector-cli-0.1.0-SNAPSHOT.jar inspect \
  --source ~/.codex
```

### Tree, Doctor, SQLite, and Rollout

```bash
java -jar inspector-cli/target/inspector-cli-0.1.0-SNAPSHOT.jar tree --source ~/.codex --depth 3
java -jar inspector-cli/target/inspector-cli-0.1.0-SNAPSHOT.jar doctor --source ~/.codex
java -jar inspector-cli/target/inspector-cli-0.1.0-SNAPSHOT.jar sqlite --source ~/.codex tables
java -jar inspector-cli/target/inspector-cli-0.1.0-SNAPSHOT.jar rollout --source ~/.codex --thread-id <threadId>
java -jar inspector-cli/target/inspector-cli-0.1.0-SNAPSHOT.jar report --source ~/.codex --output codex-state-report.md
java -jar inspector-cli/target/inspector-cli-0.1.0-SNAPSHOT.jar watch --source ~/.codex
```

### Repair

Preview stale workspace cleanup:

```bash
java -jar inspector-cli/target/inspector-cli-0.1.0-SNAPSHOT.jar repair --source ~/.codex missing-workspaces
```

Apply cleanup after quitting Codex:

```bash
java -jar inspector-cli/target/inspector-cli-0.1.0-SNAPSHOT.jar repair --source ~/.codex missing-workspaces --apply
```

### Migrate

Preview absolute path prefix migration:

```bash
java -jar inspector-cli/target/inspector-cli-0.1.0-SNAPSHOT.jar migrate \
  --source ~/.codex \
  --from-prefix /Users/old \
  --to-prefix /Users/new \
  plan
```

Apply migration after reviewing the plan and quitting Codex:

```bash
java -jar inspector-cli/target/inspector-cli-0.1.0-SNAPSHOT.jar migrate \
  --source ~/.codex \
  --from-prefix /Users/old \
  --to-prefix /Users/new \
  apply
```

## Notes

Do not run migration or direct edits on a live Codex directory. First quit Codex and take a backup.
