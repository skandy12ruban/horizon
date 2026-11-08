#!/opt/homebrew/bin/python3
"""Little Office: a read-only glance at Claude Code and Codex activity for the Steroid TV launcher.

Serves GET /status on the local network (private addresses only) with, per recent session:
project, agent, state (working / needs_you / finished / idle) and one short sentence.
No transcripts, commands, file lists or logs ever leave this Mac.
Edit ~/.little-office/config.json to choose pinned projects:
  {"pinned": ["My App", "Website"],            <- project names as the TV shows them
   "names": {"my-app-folder": "My App"}}       <- optional: folder-name fragment -> display name
"""
import glob
import json
import os
import re
import subprocess
import threading
import time
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from ipaddress import ip_address

HOME = os.path.expanduser("~")
CONFIG = os.path.join(HOME, ".little-office", "config.json")
PORT = 8765
RECENT = 6 * 3600          # sessions touched in the last 6 hours are worth a glance
WORKING_QUIET = 150        # a session silent this long is no longer "working"...
TOOL_QUIET = 20 * 60       # ...unless it's waiting on a long-running tool
MAX_EVENTS = 50

events = []                # [{"id","at","agent","project","kind","text"}]
last_state = {}            # session key -> state
lock = threading.Lock()


def config():
    try:
        with open(CONFIG) as f:
            return json.load(f)
    except Exception:
        return {"pinned": [], "names": {}}


def parse_ts(s):
    try:
        return datetime.fromisoformat(s.replace("Z", "+00:00")).timestamp()
    except Exception:
        return None


def tail_lines(path, max_bytes=400_000):
    with open(path, "rb") as f:
        f.seek(0, 2)
        size = f.tell()
        f.seek(max(0, size - max_bytes))
        data = f.read().decode("utf-8", "ignore")
    lines = data.splitlines()
    if size > max_bytes:
        lines = lines[1:]  # first line is probably cut
    out = []
    for l in lines:
        try:
            out.append(json.loads(l))
        except Exception:
            pass
    return out


def sentence(text, limit=90):
    if not text:
        return ""
    text = re.sub(r"[`*_#>\[\]]", "", text).strip()
    text = re.sub(r"\s+", " ", text)
    m = re.match(r"(.+?[.!?])(\s|$)", text)
    s = m.group(1) if m else text
    return s if len(s) <= limit else s[: limit - 1].rstrip() + "…"


def project_name(cwd, names):
    if not cwd:
        return "Unknown"
    base = os.path.basename(cwd.rstrip("/")) or cwd
    for key, label in names.items():
        if key.lower() in cwd.lower():
            return label
    if re.match(r"^\d{4}-\d{2}-\d{2}$", base) or "scratch" in cwd:
        return "Scratch"
    return base.replace("-", " ").replace("_", " ").strip().title() if base.islower() else base.replace("-", " ")


# ---------- Titles (cached: transcripts can be large) ----------

_titles = {}


def claude_title(path, mtime):
    hit = _titles.get(path)
    if hit and hit[0] == mtime:
        return hit[1]
    title = None
    with open(path, "rb") as f:
        for raw in f:
            if b"title" not in raw[:120]:
                continue
            try:
                d = json.loads(raw)
            except Exception:
                continue
            if d.get("type") in ("custom-title", "ai-title", "summary"):
                title = d.get("customTitle") or d.get("aiTitle") or d.get("title") or d.get("summary") or title
    _titles[path] = (mtime, title)
    return title


def codex_titles():
    names = {}
    try:
        with open(os.path.join(HOME, ".codex", "session_index.jsonl")) as f:
            for l in f:
                try:
                    d = json.loads(l)
                    names[d["id"]] = d.get("thread_name")
                except Exception:
                    pass
    except Exception:
        pass
    return names


# ---------- Claude Code ----------

def claude_sessions(now, names):
    out = []
    for path in glob.glob(os.path.join(HOME, ".claude", "projects", "*", "*.jsonl")):
        mtime = os.path.getmtime(path)
        if now - mtime > RECENT:
            continue
        rows = [r for r in tail_lines(path) if r.get("type") in ("user", "assistant")]
        rows = [r for r in rows if not r.get("isSidechain")]
        if not rows:
            continue
        cwd = next((r.get("cwd") for r in reversed(rows) if r.get("cwd")), "")
        last = rows[-1]
        msg = last.get("message", {}) or {}
        content = msg.get("content")
        blocks = content if isinstance(content, list) else []
        kinds = [b.get("type") for b in blocks]
        quiet = now - mtime

        # the most recent thing it said or did, as one sentence
        activity = ""
        for r in reversed(rows[-40:]):
            m = r.get("message", {}) or {}
            for b in reversed(m.get("content") if isinstance(m.get("content"), list) else []):
                if b.get("type") == "tool_use":
                    inp = b.get("input") or {}
                    d = inp.get("description") or inp.get("summary") or inp.get("action_summary")
                    if d:
                        activity = sentence(d); break
                if b.get("type") == "text" and r.get("type") == "assistant" and b.get("text", "").strip():
                    activity = sentence(b["text"]); break
            if activity:
                break

        asks = False
        if last.get("type") == "assistant":
            for b in blocks:
                if b.get("type") == "tool_use" and b.get("name") in ("AskUserQuestion", "ExitPlanMode"):
                    asks = True
                if b.get("type") == "tool_use" and b.get("name", "").endswith("wrap_up") and (b.get("input") or {}).get("ask"):
                    asks = True
            text = " ".join(b.get("text", "") for b in blocks if b.get("type") == "text").strip()
            if msg.get("stop_reason") == "end_turn" and text.endswith("?"):
                asks = True

        if last.get("type") == "assistant" and "tool_use" in kinds:
            # A pending tool looks the same whether it's running long or awaiting approval,
            # so only an explicit question counts as "needs you".
            state = "needs_you" if asks else "working" if quiet < TOOL_QUIET else "idle"
        elif last.get("type") == "user":
            state = "working" if quiet < WORKING_QUIET else "idle"
        else:
            state = "needs_you" if asks else "finished"
        title = claude_title(path, mtime)
        project = project_name(cwd, names)
        if project == "Scratch" and title:
            project = title
        out.append({"key": "claude:" + path, "agent": "Claude", "project": project, "title": title or "",
                    "state": state, "text": activity or (title or ""), "at": mtime})
    return out


# ---------- Codex ----------

def codex_sessions(now, names):
    out = []
    titles = codex_titles()
    for path in glob.glob(os.path.join(HOME, ".codex", "sessions", "*", "*", "*", "*.jsonl")):
        mtime = os.path.getmtime(path)
        if now - mtime > RECENT:
            continue
        rows = tail_lines(path)
        cwd = ""
        try:
            with open(path) as f:
                first = json.loads(f.readline())
            cwd = (first.get("payload") or {}).get("cwd", "")
        except Exception:
            pass
        started = completed = None
        activity = ""
        last_msg = ""
        for r in rows:
            p = r.get("payload") or {}
            t = p.get("type")
            if t == "turn_context" and p.get("cwd"):
                cwd = p["cwd"]
            if t == "task_started":
                started, completed = r.get("timestamp"), None
            if t == "task_complete":
                completed = r.get("timestamp")
                last_msg = p.get("last_agent_message") or last_msg
            if t == "message" and p.get("role") == "assistant":
                txt = " ".join(c.get("text", "") for c in p.get("content", []) if isinstance(c, dict))
                if txt.strip():
                    activity = sentence(txt)
            if t == "reasoning":
                summ = " ".join(s.get("text", "") for s in (p.get("summary") or []) if isinstance(s, dict))
                if summ.strip():
                    activity = sentence(summ)
        quiet = now - mtime
        if started and not completed:
            state = "working" if quiet < TOOL_QUIET else "idle"
        else:
            state = "needs_you" if last_msg.strip().endswith("?") else "finished"
            if last_msg:
                activity = sentence(last_msg)
        sid = re.findall(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", os.path.basename(path))
        title = next((titles[i] for i in reversed(sid) if titles.get(i)), None)
        project = project_name(cwd, names)
        # Codex threads that live in a dated scratch folder are better known by their thread title
        if title and (not cwd or "/Documents/Codex/" in cwd or project in ("Unknown", "Scratch")):
            project = title
        out.append({"key": "codex:" + path, "agent": "Codex", "project": project, "title": title or "",
                    "state": state, "text": activity or (title or ""), "at": mtime})
    return out


# ---------- Snapshot + events ----------

def snapshot():
    now = time.time()
    cfg = config()
    names = cfg.get("names", {})
    sessions = claude_sessions(now, names) + codex_sessions(now, names)
    with lock:
        for s in sessions:
            prev = last_state.get(s["key"])
            if prev is not None and prev != s["state"] and s["state"] in ("needs_you", "finished"):
                events.append({"id": int(now * 1000) + len(events), "at": now, "agent": s["agent"],
                               "project": s["project"], "kind": s["state"],
                               "text": s["text"] or ("Needs your input" if s["state"] == "needs_you" else "Finished")})
            last_state[s["key"]] = s["state"]
        del events[:-MAX_EVENTS]
        evs = list(events)
    sessions.sort(key=lambda s: -s["at"])
    for s in sessions:
        s.pop("key", None)
    return {"generated_at": now, "pinned": cfg.get("pinned", []), "sessions": sessions, "events": evs}


def watcher():
    while True:
        try:
            snapshot()
        except Exception as e:
            print("snapshot failed:", e, flush=True)
        time.sleep(10)


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        try:
            private = ip_address(self.client_address[0]).is_private
        except ValueError:
            private = False
        path = self.path.split("?")[0]
        if path == "/test-event" and self.client_address[0] == "127.0.0.1":
            # local-only: inject one sample notice to check the TV's notification
            with lock:
                events.append({"id": int(time.time() * 1000), "at": time.time(), "agent": "Claude",
                               "project": "Demo project", "kind": "needs_you", "text": "Asking which design to keep"})
            self.send_response(204)
            self.end_headers()
            return
        if not private or path != "/status":
            self.send_error(404)
            return
        body = json.dumps(snapshot()).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *a):
        pass


if __name__ == "__main__":
    if not os.path.exists(CONFIG):
        os.makedirs(os.path.dirname(CONFIG), exist_ok=True)
        with open(CONFIG, "w") as f:
            json.dump({"pinned": [], "names": {}}, f, indent=2)
    snapshot()  # baseline, so existing states don't fire notifications
    threading.Thread(target=watcher, daemon=True).start()
    # Announce on the network so the TV finds this Mac even if its address changes.
    subprocess.Popen(["/usr/bin/dns-sd", "-R", "Little Office", "_littleoffice._tcp", "local", str(PORT)],
                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
