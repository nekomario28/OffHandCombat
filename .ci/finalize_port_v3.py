#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCRIPT = ROOT / ".ci/vanilla-client-server-e2e.sh"
FINALIZER = ROOT / ".ci/finalize_port.py"

text = SCRIPT.read_text(encoding="utf-8")
options_block = ''': > "$SERVER_CONSOLE_LOG"
: > "$CLIENT_LOG"
cat > "$CLIENT_DIR/options.txt" <<'EOF'
onboardAccessibility:false
skipMultiplayerWarning:true
narrator:0
EOF
'''
old_logs = ''': > "$SERVER_CONSOLE_LOG"
: > "$CLIENT_LOG"
'''
if options_block not in text:
    if old_logs not in text:
        raise SystemExit("vanilla client options anchor missing")
    text = text.replace(old_logs, options_block, 1)

quickplay = '''  --quickPlayPath "quickplay/log.json" \\
  --quickPlayMultiplayer "127.0.0.1:${PORT}" \\
'''
old_quickplay = '''  --quickPlayMultiplayer "127.0.0.1:${PORT}" \\
'''
if quickplay not in text:
    if old_quickplay not in text:
        raise SystemExit("vanilla client quick-play anchor missing")
    text = text.replace(old_quickplay, quickplay, 1)
SCRIPT.write_text(text, encoding="utf-8")

finalizer = FINALIZER.read_text(encoding="utf-8")
old_cleanup = '''    (ROOT / ".ci/finalize_port_v2.py").unlink(missing_ok=True)
'''
new_cleanup = '''    (ROOT / ".ci/finalize_port_v2.py").unlink(missing_ok=True)
    (ROOT / ".ci/finalize_port_v3.py").unlink(missing_ok=True)
'''
if new_cleanup not in finalizer:
    if old_cleanup not in finalizer:
        raise SystemExit("finalizer v3 cleanup anchor missing")
    finalizer = finalizer.replace(old_cleanup, new_cleanup, 1)
FINALIZER.write_text(finalizer, encoding="utf-8")
