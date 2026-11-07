#!/usr/bin/env python3
"""
3PointClick Server
TCP listener on port 8888 (over ADB reverse) that receives touch events
from an Android tablet and controls the host PC mouse cursor.

On Linux: Uses evdev /dev/uinput to emulate a true kernel hardware mouse,
bypassing Wayland/XWayland coordinate clipping restrictions and working
seamlessly across multi-monitor setups and full-screen games.
Fallback: Uses pynput for cross-platform compatibility.
"""

import sys
import os
import time
import json
import socket
import signal
import argparse
import subprocess
import threading

DEFAULT_PORT = 8888
DEFAULT_SCALE = 1.0
WATCHDOG_TIMEOUT = 10.0  # seconds without message before releasing held buttons


def setup_adb_forwarding(port):
    """
    Automatically detects connected Android devices via ADB
    and establishes 'adb reverse' (fallback to 'adb forward').
    Shares the same port (8888) and mechanism as VirtualHit.
    """
    print("[*] Checking connected Android devices via ADB...")
    try:
        adb_bin = "adb"
        for candidate in ["adb", "/home/linuxbrew/.linuxbrew/bin/adb"]:
            if subprocess.run(["which", candidate], capture_output=True).returncode == 0:
                adb_bin = candidate
                break

        res = subprocess.run([adb_bin, "devices"], capture_output=True, text=True, check=True)
        lines = [line.strip() for line in res.stdout.strip().split('\n')[1:] if line.strip()]
        devices = [line.split()[0] for line in lines if 'device' in line]

        if not devices:
            print("[!] Warning: No ADB devices detected!")
            print("    Make sure USB Debugging is ON and the device is plugged in.")
            print(f"    Will still listen on port {port}...")
            return False

        print(f"[+] Found Android device: {devices[0]}")
        print(f"[*] Setting up '{adb_bin} reverse tcp:{port} tcp:{port}'...")
        rev_res = subprocess.run([adb_bin, "reverse", f"tcp:{port}", f"tcp:{port}"], capture_output=True, text=True)
        if rev_res.returncode == 0:
            print(f"[+] ADB Reverse tunnel established on tcp:{port}!")
            return True
        else:
            print("[*] 'adb reverse' not supported or failed, trying 'adb forward'...")
            fwd_res = subprocess.run([adb_bin, "forward", f"tcp:{port}", f"tcp:{port}"], capture_output=True, text=True)
            if fwd_res.returncode == 0:
                print(f"[+] ADB Forward tunnel established on tcp:{port}!")
                return True
            else:
                print(f"[!] ADB forwarding failed: {fwd_res.stderr.strip()}")
                return False
    except FileNotFoundError:
        print("[!] Warning: 'adb' executable not found.")
        print(f"    Listening on port {port} regardless.")
        return False
    except Exception as err:
        print(f"[!] ADB invocation error: {err}")
        return False


class UInputMouseBackend:
    """Linux kernel uinput mouse: zero clipping on Wayland, full game support."""
    def __init__(self, scale=1.0, verbose=False):
        import evdev
        from evdev import ecodes as e

        self.e = e
        self.scale = scale
        self.verbose = verbose
        self.left_held = False
        self.right_held = False
        self.lock = threading.Lock()

        cap = {
            e.EV_REL: [e.REL_X, e.REL_Y],
            e.EV_KEY: [e.BTN_LEFT, e.BTN_RIGHT, e.BTN_MIDDLE]
        }
        self.ui = evdev.UInput(cap, name="3PointClick Virtual Mouse", vendor=0x1234, product=0x9999, version=1)
        print("[+] Created Linux uinput kernel mouse (Wayland & Game native support).")

    def move(self, dx, dy):
        scaled_x = int(round(dx * self.scale))
        scaled_y = int(round(dy * self.scale))
        if scaled_x != 0 or scaled_y != 0:
            if self.verbose:
                print(f"[MOVE] dx={scaled_x}, dy={scaled_y}")
            with self.lock:
                self.ui.write(self.e.EV_REL, self.e.REL_X, scaled_x)
                self.ui.write(self.e.EV_REL, self.e.REL_Y, scaled_y)
                self.ui.syn()

    def left_down(self):
        with self.lock:
            if not self.left_held:
                if self.verbose:
                    print("[CLICK] LEFT DOWN")
                self.ui.write(self.e.EV_KEY, self.e.BTN_LEFT, 1)
                self.ui.syn()
                self.left_held = True

    def left_up(self):
        with self.lock:
            if self.left_held:
                if self.verbose:
                    print("[CLICK] LEFT UP")
                self.ui.write(self.e.EV_KEY, self.e.BTN_LEFT, 0)
                self.ui.syn()
                self.left_held = False

    def right_down(self):
        with self.lock:
            if not self.right_held:
                if self.verbose:
                    print("[CLICK] RIGHT DOWN")
                self.ui.write(self.e.EV_KEY, self.e.BTN_RIGHT, 1)
                self.ui.syn()
                self.right_held = True

    def right_up(self):
        with self.lock:
            if self.right_held:
                if self.verbose:
                    print("[CLICK] RIGHT UP")
                self.ui.write(self.e.EV_KEY, self.e.BTN_RIGHT, 0)
                self.ui.syn()
                self.right_held = False

    def release_all(self):
        with self.lock:
            if self.left_held:
                print("[SAFETY] Releasing held LEFT button")
                self.ui.write(self.e.EV_KEY, self.e.BTN_LEFT, 0)
                self.left_held = False
            if self.right_held:
                print("[SAFETY] Releasing held RIGHT button")
                self.ui.write(self.e.EV_KEY, self.e.BTN_RIGHT, 0)
                self.right_held = False
            self.ui.syn()

    def close(self):
        self.release_all()
        try:
            self.ui.close()
        except Exception:
            pass


class PynputMouseBackend:
    """Fallback mouse driver using pynput."""
    def __init__(self, scale=1.0, verbose=False):
        from pynput.mouse import Controller, Button
        self.mouse = Controller()
        self.Button = Button
        self.scale = scale
        self.verbose = verbose
        self.left_held = False
        self.right_held = False
        self.lock = threading.Lock()
        print("[+] Created pynput mouse backend.")

    def move(self, dx, dy):
        scaled_x = int(round(dx * self.scale))
        scaled_y = int(round(dy * self.scale))
        if scaled_x != 0 or scaled_y != 0:
            if self.verbose:
                print(f"[MOVE] dx={scaled_x}, dy={scaled_y}")
            self.mouse.move(scaled_x, scaled_y)

    def left_down(self):
        with self.lock:
            if not self.left_held:
                if self.verbose:
                    print("[CLICK] LEFT DOWN")
                self.mouse.press(self.Button.left)
                self.left_held = True

    def left_up(self):
        with self.lock:
            if self.left_held:
                if self.verbose:
                    print("[CLICK] LEFT UP")
                self.mouse.release(self.Button.left)
                self.left_held = False

    def right_down(self):
        with self.lock:
            if not self.right_held:
                if self.verbose:
                    print("[CLICK] RIGHT DOWN")
                self.mouse.press(self.Button.right)
                self.right_held = True

    def right_up(self):
        with self.lock:
            if self.right_held:
                if self.verbose:
                    print("[CLICK] RIGHT UP")
                self.mouse.release(self.Button.right)
                self.right_held = False

    def release_all(self):
        with self.lock:
            if self.left_held:
                print("[SAFETY] Releasing held LEFT button")
                self.mouse.release(self.Button.left)
                self.left_held = False
            if self.right_held:
                print("[SAFETY] Releasing held RIGHT button")
                self.mouse.release(self.Button.right)
                self.right_held = False

    def close(self):
        self.release_all()


def create_mouse_driver(scale=1.0, verbose=False):
    # Try evdev uinput first on Linux
    if sys.platform.startswith("linux"):
        try:
            return UInputMouseBackend(scale=scale, verbose=verbose)
        except Exception as err:
            print(f"[!] Warning: Could not initialize uinput ({err}). Falling back to pynput.")

    try:
        return PynputMouseBackend(scale=scale, verbose=verbose)
    except Exception as err:
        print(f"[!] Fatal: Could not initialize mouse backend: {err}")
        sys.exit(1)


def handle_client(client_sock, client_addr, driver, verbose):
    print(f"\n[+] Connected to tablet client: {client_addr[0]}:{client_addr[1]}")
    buffer = ""
    last_msg_time = time.time()

    def watchdog():
        nonlocal last_msg_time
        while True:
            time.sleep(1.0)
            if client_sock.fileno() == -1:
                break
            if time.time() - last_msg_time > WATCHDOG_TIMEOUT:
                if driver.left_held or driver.right_held:
                    print("[!] Watchdog: Client silent for > 10s. Releasing held buttons.")
                    driver.release_all()

    watchdog_thread = threading.Thread(target=watchdog, daemon=True)
    watchdog_thread.start()

    try:
        while True:
            data = client_sock.recv(4096)
            if not data:
                print(f"[-] Client {client_addr[0]} disconnected.")
                break

            last_msg_time = time.time()
            buffer += data.decode("utf-8", errors="ignore")

            while "\n" in buffer:
                line, buffer = buffer.split("\n", 1)
                line = line.strip()
                if not line:
                    continue

                try:
                    event = json.loads(line)
                    ev_type = event.get("type")

                    if ev_type == "MOVE":
                        dx = event.get("dx", 0)
                        dy = event.get("dy", 0)
                        driver.move(dx, dy)

                    elif ev_type == "LEFT":
                        action = event.get("action")
                        if action == "DOWN":
                            driver.left_down()
                        elif action == "UP":
                            driver.left_up()

                    elif ev_type == "RIGHT":
                        action = event.get("action")
                        if action == "DOWN":
                            driver.right_down()
                        elif action == "UP":
                            driver.right_up()

                    elif ev_type == "PING":
                        if verbose:
                            print("[PING] Keepalive received")

                except json.JSONDecodeError:
                    if verbose:
                        print(f"[!] Invalid JSON received: {line}")

    except (ConnectionResetError, BrokenPipeError):
        print(f"[-] Connection reset by {client_addr[0]}")
    finally:
        driver.release_all()
        try:
            client_sock.close()
        except Exception:
            pass


def main():
    parser = argparse.ArgumentParser(description="3PointClick PC Host Server")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT, help=f"Server listening port (default: {DEFAULT_PORT})")
    parser.add_argument("--scale", type=float, default=DEFAULT_SCALE, help=f"Cursor speed scaling factor (default: {DEFAULT_SCALE})")
    parser.add_argument("--verbose", "-v", action="store_true", help="Print detailed event logs")
    parser.add_argument("--no-adb", action="store_true", help="Skip automatic ADB reverse/forward tunnel setup")

    args = parser.parse_args()

    print("=" * 60)
    print(" 3PointClick Host Server")
    print(" Multi-touch Android Trackpad Driver")
    print(f" Port: {args.port} (compatible with VirtualHit bridge)")
    print("=" * 60)

    if not args.no_adb:
        setup_adb_forwarding(args.port)

    driver = create_mouse_driver(scale=args.scale, verbose=args.verbose)

    server_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        server_sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    except AttributeError:
        pass

    def signal_handler(sig, frame):
        print("\n[*] Shutting down 3PointClick server...")
        driver.close()
        try:
            server_sock.close()
        except Exception:
            pass
        sys.exit(0)

    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)

    server_sock.bind(("0.0.0.0", args.port))
    server_sock.listen(1)

    print(f"[+] Server listening on 0.0.0.0:{args.port}...")
    print("[*] Waiting for 3PointClick Android app connection...")

    try:
        while True:
            client_sock, client_addr = server_sock.accept()
            try:
                client_sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
            except AttributeError:
                pass
            handle_client(client_sock, client_addr, driver, args.verbose)
            print("[*] Waiting for new connection...")
    except KeyboardInterrupt:
        signal_handler(None, None)


if __name__ == "__main__":
    main()
