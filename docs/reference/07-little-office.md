# 7 · Little Office (optional)

A small card on the TV showing whether **Claude Code** and **Codex** on the user's Mac are working
or need them — so they can keep an eye on work while watching something.

```
Little Office                      ← card on the home screen
Codex · 2 working · 1 needs you
Claude · All quiet
Forma — rendering video.
```

Selecting it opens a glance panel (Needs you · Pinned · Other activity — one sentence each, no
drill-down, commands, files or logs). When an agent asks a question or finishes, a small grouped
notice appears over whatever is playing; OK shows why. If the Mac stops answering, the card says
"Last seen 12 minutes ago" — never a stale "Working".

## Ask first

It's optional. Ask: *do you use Claude Code or Codex on this Mac, and would you like this?* Explain
what it shares: **project names, states and one short sentence per session**, served only to
devices on the home network (private addresses). It reads, never changes, these local files:
- Claude Code: `~/.claude/projects/*/*.jsonl` (+ the session title)
- Codex: `~/.codex/sessions/**/rollout-*.jsonl` and `~/.codex/session_index.jsonl` (thread titles)

If they decline: set `"littleOffice": false` in `config/features.json` (the card, service and
overlay permission are then unused).

## Install (macOS)

Ask which projects to pin (names as they appear — folder names or Codex thread titles), then:

```bash
little-office/install.sh "Project A" "Project B"
```

This copies `office.py` to `~/.little-office/`, writes `config.json`, and registers a
**LaunchAgent** (`com.steroid.littleoffice`) so it starts at login and restarts if it stops. Tell
the user it's a login item. If macOS asks whether Python may accept incoming connections, they
should allow it (the TV needs to reach port 8765).

Customise later in `~/.little-office/config.json`:
```json
{"pinned": ["My App", "Website"], "names": {"my-app-folder": "My App"}}
```
Remove with `little-office/uninstall.sh`.

## How it decides states

| | working | needs you | finished |
|---|---|---|---|
| Claude | last entry is a tool call/result and the file changed recently (≤ 20 min for long tools) | the turn ended with a question, `AskUserQuestion`, or an ask in the wrap-up | turn ended normally |
| Codex | `task_started` without `task_complete` | last agent message ends with a question | `task_complete` |

A tool waiting for permission looks identical to a long-running tool in the logs, so it shows as
"Working", not "Needs you". Activity sentence = the tool's description (e.g. "Build launcher and
install") or the latest message, first sentence.

## TV side

- Discovery: the Mac advertises `_littleoffice._tcp` with `dns-sd`; the TV finds it with
  `NsdManager` and remembers the address.
- The launcher needs `android:usesCleartextTraffic="true"` (plain HTTP on the LAN) — without it
  Android silently blocks the requests.
- `OfficeService` polls every 20 s. Events are grouped for 30 s, then at most one notice per 5 min.
  The notice is a `TYPE_APPLICATION_OVERLAY` window (permission granted by `install.sh` via
  `appops set … SYSTEM_ALERT_WINDOW allow`), shown 9 s; OK opens the panel, any other key dismisses.
- On first contact all existing events are treated as history (otherwise nothing new is ever seen).

## Test

```bash
curl -s http://127.0.0.1:8765/test-event     # local-only: injects one sample "needs you"
```
The notice appears within ~1 minute (unless the TV's screensaver is on — it draws above overlays).
