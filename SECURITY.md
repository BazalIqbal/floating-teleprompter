# Security

## Reporting a vulnerability
Please report security problems privately through GitHub's **Security › Report a vulnerability** on this repository, not in a public issue. You'll get a reply as soon as possible.

## What the app can access, and why
| Access | Why | Limits |
| --- | --- | --- |
| Display over other apps | Shows the script over your camera app | The panel only takes touches inside itself; when locked it takes none |
| Microphone | Voice mode follows what you say | Uses Android's speech recognizer only while the script is on screen in Voice mode; nothing is recorded or stored |
| Accessibility service (optional, off by default) | Lets volume keys and Bluetooth clickers control the script while the camera app is open | Reads key presses only, never screen content, and only while the script is on screen; at all other times keys pass straight through |
| Internet | Only for the optional "Polish with Claude" | Nothing is sent until you tap Polish; the script goes to Anthropic over HTTPS (cleartext traffic is disabled) |

## Data
- Scripts and settings stay on the phone. Android cloud backup and device-to-device transfer are disabled for the app's data; use **Settings › Export** for backups.
- Your Anthropic API key is encrypted with an AES-GCM key held in Android Keystore, and it is never exported, logged or shown in full.
- There are no accounts, analytics, ads or third-party trackers.

## Inputs from other apps
- **Shared text** (Android Share menu) is treated as plain text and capped at 100,000 characters.
- **Backup files** must be Prompter backups, are size-limited (10 MB) and are parsed strictly; invalid entries are skipped.

## Builds
- CI runs with read-only permissions, uses no secrets, and checks that the Gradle wrapper is an official release.
- Release builds are minified with R8. Signing keys are never stored in this repository.
