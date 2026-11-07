# 3PointClick — Project Prompt

> **Goal:** Repurpose an old Android 7.1.1 tablet as a wireless three-finger trackpad for a PC,
> communicating over an ADB (USB or WiFi) bridge. No internet, no third-party cloud — just a
> dumb touch sensor feeding cursor events to the host machine.

---

## 1. Concept Overview

The tablet screen becomes a multi-touch sensor surface. Three simultaneous fingers map to specific
mouse actions:

| Finger role | Touch position relative to the **pointer finger** | PC action |
|---|---|---|
| **Pointer** (middle finger / first touch) | — reference point — | Move cursor |
| **Left-click finger** (index / any touch to the LEFT of the pointer) | x < pointer.x | Left mouse button click |
| **Right-click finger** (ring / any touch to the RIGHT of the pointer) | x > pointer.x | Right mouse button click |

Touch arrival order determines the **pointer finger**: whichever finger touches the screen first
(or the finger with the middle index when multiple arrive simultaneously) becomes the pointer.
Subsequent touches are evaluated against the pointer's X coordinate to decide left vs. right click.

---

## 2. System Architecture

```
┌─────────────────────────────────────────┐        USB / ADB-WiFi
│            Android Tablet               │ ───────────────────────► PC
│  ┌──────────────────────────────────┐   │
│  │   3PointClick Android App        │   │        ADB reverse / forward
│  │  - FullScreen touch surface      │   │
│  │  - Multi-touch event parser      │   │
│  │  - Pointer/L-click/R-click logic │   │
│  │  - JSON/text event serialiser    │   │
│  │  - TCP socket client → port 9000 │   │
│  └──────────────────────────────────┘   │
└─────────────────────────────────────────┘
                    │
              ADB bridge
        (adb reverse tcp:9000 tcp:9000)
                    │
┌─────────────────────────────────────────┐
│           PC — Python Server            │
│  ┌──────────────────────────────────┐   │
│  │   server.py                      │   │
│  │  - TCP listener on port 9000     │   │
│  │  - JSON event parser             │   │
│  │  - pyinput / pynput mouse driver │   │
│  │    • MOVE  → mouse.position      │   │
│  │    • LEFT  → click left          │   │
│  │    • RIGHT → click right         │   │
│  └──────────────────────────────────┘   │
└─────────────────────────────────────────┘
```

---

## 3. Android App (`3PointClick`)

### 3a. Target & Constraints
- **Min SDK:** API 25 (Android 7.1.1 — the tablet's OS)
- **Language:** Kotlin (or Java if necessary)
- **UI:** Single full-screen `View` with `onTouchListener`; no UI chrome; keep-awake flag set
- **Permissions:** Nothing special — only touch + network (local loopback via ADB reverse)

### 3b. Touch Logic (State Machine)

```
State: IDLE
  → finger A touches → A becomes POINTER, state = TRACKING

State: TRACKING
  → finger B touches (x < pointer.x) → emit LEFT_DOWN, state = LEFT_HELD
  → finger B touches (x > pointer.x) → emit RIGHT_DOWN, state = RIGHT_HELD
  → pointer moves → emit MOVE(dx, dy)  [relative delta]
  → pointer lifts → state = IDLE

State: LEFT_HELD
  → finger B lifts → emit LEFT_UP
  → pointer lifts → emit LEFT_UP, state = IDLE

State: RIGHT_HELD
  → finger B lifts → emit RIGHT_UP
  → pointer lifts → emit RIGHT_UP, state = IDLE
```

Edge cases:
- If a third touch arrives while in HELD state → ignored
- If pointer and click finger arrive within ~20 ms of each other, sort by X to
  determine pointer (centre-most finger wins)

### 3c. Event Protocol (newline-delimited JSON over TCP)

```json
{ "type": "MOVE",  "dx": -12, "dy": 4  }
{ "type": "LEFT",  "action": "DOWN" }
{ "type": "LEFT",  "action": "UP"   }
{ "type": "RIGHT", "action": "DOWN" }
{ "type": "RIGHT", "action": "UP"   }
{ "type": "PING" }
```

- Events are sent as UTF-8 text lines (`\n` delimited) over a persistent TCP socket
- `PING` is sent every 2 seconds to keep the connection alive and let the server detect disconnects
- The tablet connects to `127.0.0.1:9000` (loopback — ADB reverse handles the forwarding)

### 3d. Connection Flow (App side)
1. App starts → shows "Connecting…" overlay
2. Opens TCP socket to `127.0.0.1:9000` in a background coroutine
3. On success → hides overlay, enables touch capture
4. On disconnect → retries every 3 seconds, shows "Reconnecting…" overlay
5. Touch events are enqueued and sent only when connected (drops events if queue overflows)

### 3e. Sensitivity
- Movement events are sent as **relative deltas** (not absolute coordinates)
- A configurable `SPEED_MULTIPLIER` float (default `1.5`) scales the deltas before sending
- Sensitivity setting should be accessible via a long-press anywhere on the surface (simple
  dialog with a slider, range 0.5–4.0)

---

## 4. PC Python Server (`server.py`)

### 4a. Dependencies
```
pynput          # cross-platform mouse control
```
(No external broker; direct TCP socket)

### 4b. Setup Steps (README instructions for the user)
```bash
# 1. Install Python dependencies
pip install pynput

# 2. Connect tablet via USB and enable ADB
adb devices               # confirm tablet is listed

# 3. Forward the port (tablet → PC loopback)
adb reverse tcp:9000 tcp:9000

# 4. Start the server
python server.py

# 5. Launch 3PointClick app on the tablet
#    The app will auto-connect
```

### 4c. Server Behaviour
- Listens on `0.0.0.0:9000` (ADB reverse makes the tablet's `127.0.0.1:9000` reach this)
- Accepts one client at a time; old client is closed if a new one connects
- Parses newline-delimited JSON
- `MOVE`  → `mouse.move(dx * scale, dy * scale)` — scale defaults to 1.0 on server side
- `LEFT DOWN` → `mouse.press(Button.left)`
- `LEFT UP`   → `mouse.release(Button.left)`
- `RIGHT DOWN`→ `mouse.press(Button.right)`
- `RIGHT UP`  → `mouse.release(Button.right)`
- `PING` → ignored (resets disconnect watchdog timer)
- If no message received for 10 seconds → logs warning; releases any held buttons as safety measure
- Graceful Ctrl-C shutdown: releases all held buttons before exit

### 4d. CLI Arguments (optional quality-of-life)
```
python server.py [--port 9000] [--scale 1.0] [--verbose]
```

---

## 5. Project File Layout

```
3PointClick/
├── prompt.md                  ← this file
├── README.md                  ← setup & usage guide
│
├── android/                   ← Android Studio project
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/threepointclick/
│   │   │   │   ├── MainActivity.kt       ← single-activity entry point
│   │   │   │   ├── TouchSurface.kt       ← custom View with touch logic
│   │   │   │   ├── TouchEventParser.kt   ← finger-role state machine
│   │   │   │   ├── EventSender.kt        ← TCP socket + coroutine queue
│   │   │   │   └── SettingsDialog.kt     ← speed slider dialog
│   │   │   ├── res/
│   │   │   │   ├── layout/activity_main.xml
│   │   │   │   └── values/strings.xml
│   │   │   └── AndroidManifest.xml
│   │   └── build.gradle
│   └── build.gradle
│
└── server/
    ├── server.py              ← Python TCP server + pynput driver
    └── requirements.txt       ← pynput
```

---

## 6. Non-Goals (out of scope for v1)

- WiFi-direct (ADB USB is enough and simpler)
- Scroll gestures (two-finger scroll) — potential v2 feature
- Keyboard input
- Multi-monitor awareness
- macOS or Windows server (primary target is Linux PC; pynput is cross-platform so it _should_ work)

---

## 7. Open Questions / Future Considerations

1. **Coordinate scaling:** Should the server normalise tablet resolution to screen resolution, or
   keep pure relative deltas? (Current plan: relative deltas only, simpler.)
2. **Bluetooth ADB:** Possible fallback if USB is inconvenient; out of scope for now.
3. **Dead-zone:** Should small movements below a threshold be ignored to reduce jitter?
   (Good idea for v1.1.)
4. **Multi-monitor:** Server currently uses pynput's `mouse.move()` which is relative — works
   across monitors without any extra logic.

---

*Generated: 2026-10-31 — ready to build.*
