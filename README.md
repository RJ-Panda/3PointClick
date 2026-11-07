# 3PointClick 🖱️📱

> **Turn an old Android tablet (7.1.1+) into a responsive, multi-touch 3-finger trackpad for your PC over ADB USB or WiFi.**

Communicates over a high-performance, low-latency ADB bridge on **port 8888** — the **exact same bridge and port used by VirtualHit**, allowing you to switch between both apps without reconfiguring ADB tunnels.

---

## 🖐️ Gesture Control

| Gesture / Finger Position | Relative to Pointer Finger | Host PC Mouse Action |
|---|---|---|
| **Pointer Finger** (1st touch / middle) | — Reference Point — | Hardware cursor movement (`dx, dy`) |
| **Left Click** (Index finger) | Touch arrives with $X < X_{pointer}$ | Left mouse button press / release |
| **Right Click** (Ring finger) | Touch arrives with $X > X_{pointer}$ | Right mouse button press / release |
| **Drag & Drop** | Hold left click + move pointer | Click & Drag |
| **Settings** | **Double-tap `⚙ SETTINGS` button** (top-left) | Adjust sensitivity & connection settings |

---

## 🎮 Linux Kernel uinput (Zero Clipping & Gaming Ready)

Unlike basic X11 mouse emulation which clips to arbitrary window boxes on **Wayland** and fails in games, `server.py` utilizes Linux kernel `/dev/uinput` via `evdev` to create a true virtual USB hardware mouse:
- **Full Screen & Multi-Monitor:** Reaches all corners without clipping or warping.
- **Native Wayland & X11:** Completely bypasses XWayland coordinate boundaries.
- **Game & FPS Support:** Seamlessly drives camera and cursor in fullscreen games, Steam, Proton, and Wine.

---

## 🚀 Quick Start Guide

### 1. Host Server Setup (PC)

1. Connect your Android tablet via USB with **USB Debugging** enabled.
2. Start the server:
   ```bash
   cd server
   python3 server.py
   ```
   *(The server will automatically detect your ADB device, configure `adb reverse tcp:8888 tcp:8888`, and initialize the uinput virtual mouse).*

   **Optional CLI options:**
   ```bash
   python3 server.py --scale 1.5      # Change cursor speed scaling
   python3 server.py --verbose        # Print live event stream
   python3 server.py --no-adb         # Skip automatic ADB invocation
   python3 server.py --port 8888      # Change listening port
   ```

---

### 2. Android App

The APK is pre-built in:
```
android/app/build/outputs/apk/debug/app-debug.apk
```
To install or update to your tablet:
```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```
Or start directly:
```bash
adb shell am start -n com.threepointclick/.MainActivity
```
- The top-center banner turns green: `Connected to PC (Port 8888)`.
- Use the **top-left `⚙ SETTINGS` button** (requires a **double tap**) to configure sensitivity or change ports.
