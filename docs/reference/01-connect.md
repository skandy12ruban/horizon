# 1 · Connect to the TV

## Walk the user through it

1. **Developer options:** Settings → Device Preferences (or System) → About → press OK on
   **Build** seven times until it says "You are now a developer".
2. **Debugging:** Settings → Device Preferences → Developer options → turn on **USB debugging**
   and, if present, **Network debugging** / **ADB over network** / **Wireless debugging**.
3. **IP address:** Settings → Network & Internet → (the Wi-Fi network) → IP address.
   You can also find it in the router, or try `dns-sd -B _adb-tls-connect._tcp` on a Mac.

## Connect

```bash
scripts/connect.sh 192.168.0.6
```

The first time, the TV shows **"Allow USB debugging?"** — the user must select *Always allow from
this computer* and OK. Run the script again if it says `NOT CONNECTED`.

The script saves the TV's address in `.tv-serial`; every other script targets it automatically.

### Android 11+ "Wireless debugging" with a pairing code

Some TVs only offer pairing: Developer options → Wireless debugging → *Pair device with pairing
code*. Then:

```bash
$ANDROID_HOME/platform-tools/adb pair <ip>:<pairing-port>     # enter the 6-digit code
scripts/connect.sh <ip>:<connect-port>                         # the port shown on the main screen
```

## Troubleshooting

- `Operation timed out`: the TV is asleep or off. Wake it with the remote and retry. Deep standby
  drops Wi-Fi; on some TVs turn on *Network standby* / *Wake on LAN* so it stays reachable.
- `unauthorized`: the prompt on the TV wasn't accepted. Revoke and retry: Developer options →
  *Revoke USB debugging authorizations*, then `scripts/connect.sh` again.
- `adb root` will fail on retail TVs ("cannot run as root in production builds"). That's expected:
  everything in this project works with the normal shell user — no root, no bootloader unlock.
