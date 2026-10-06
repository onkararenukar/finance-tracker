# SMS Sync Agent

Reads new bank transaction alerts from your Mac's Messages app and feeds
them into the finance tracker pipeline - no manual statement upload
needed for anything your bank already texts you.

**This runs directly on your Mac, not in Docker.** See the top of
`pom.xml` for why (macOS privacy protections and Docker's VM sandboxing
make a containerized version impossible).

## One-time setup

1. **Grant Full Disk Access.** System Settings → Privacy & Security →
   Full Disk Access → add the app you'll run this from (Terminal.app,
   iTerm, or your IDE). Fully quit and reopen that app afterward - macOS
   won't apply the permission to an already-running process.

2. **Install fswatch for instant sync** (optional but strongly recommended):
   ```bash
   brew install fswatch
   ```
   Without it, the agent falls back to polling `chat.db` every
   `sync.interval.seconds` (default 120s) instead of reacting the moment
   a message arrives. With it, new bank alerts are picked up and
   uploaded within seconds - see `ChatDbWatcher`'s javadoc for why plain
   Java can't do this on its own (macOS ships no native FSEvents-backed
   `WatchService`, only a polling fallback).

3. **Build the jar** (from the repo root):
   ```bash
   mvn -pl sms-sync-agent -am package
   ```

4. Run it once so it generates a default config file:
   ```bash
   java -jar sms-sync-agent/target/sms-sync-agent.jar
   ```
   This creates `~/.finance-tracker/agent.properties`. Edit it if
   ingestion-service isn't running at the default `http://localhost:8081`.

## Running

```bash
java -jar sms-sync-agent/target/sms-sync-agent.jar
```

On startup it logs whether instant (fswatch) sync is active. Leave it
running (a `screen`/`tmux` session, or a `launchd` LaunchAgent plist if
you want it to survive reboots - not included here, but a natural next
step). It checkpoints progress to `~/.finance-tracker/sms-agent-state.json`
so restarts never reprocess old messages.

## How detection works

`BankMessageFilter` requires BOTH bank-like language (debited, credited,
UPI, account/balance references, or a recognized bank sender ID) AND a
recognizable amount (`Rs.`/`INR` + digits) before treating a message as
a transaction alert - tuned for high precision so your regular texts
never end up in the pipeline. See `BankMessageFilterTest` for the exact
cases it's designed to catch and reject, and extend
`KNOWN_BANK_KEYWORDS` in that class for any bank not already listed.

## Privacy note

This agent only reads `chat.db` locally and only uploads messages that
pass the bank-transaction filter above - the raw text of non-bank
messages never leaves your Mac. The uploaded CSV batches contain
whatever's in the matched message bodies (sender ID, timestamp, message
text), which is the same information already visible in Messages.app.
