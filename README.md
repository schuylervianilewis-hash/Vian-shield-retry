# Vianshield

A lightweight, personal-use Android firewall and VPN application.

## Development status

This repository is being initialized from the previous Vianshield Android trial. The intended approach is to retain useful Android UI, VPN-service lifecycle, diagnostics, tests, and CI build scaffolding while replacing or simplifying the networking engine as the custom Vianshield architecture is validated.

**Do not treat any feature as complete merely because a UI control exists.** Firewall behavior must be tested on Android devices.

## Product priorities

1. Stable VPN start/stop, restart, reconnection, and truthful connection status.
2. Persistent per-app firewall rules; **Block has highest priority** over Direct routing, VPN routing, ad-filter exemptions, and speed-limit exemptions.
3. DNS provider selection and DNS-based ad/tracker filtering.
4. An app allow-list that exempts selected apps from ad filtering only; it must never unblock them.
5. One global speed limit with per-app exemptions.
6. Screen-aware per-app blocking where Android permits reliable enforcement.
7. Low resource use, local-first storage, diagnostics, and recovery.

## App structure

Four main tabs: **Main**, **Firewall**, **WireGuard**, and **Settings**. Use a light theme with sky-blue accents and keep the primary screen minimal.

## Repository layout

- `app/` — Android application source and resources.
- `docs/PRODUCT_SPEC.md` — product requirements and acceptance principles.
- `docs/ENGINE_ARCHITECTURE.md` — engine boundaries and implementation strategy.
- `.github/workflows/` — Android build and test automation.

## Development principles

- Prefer a narrow, purpose-built engine over recreating a general-purpose proxy platform.
- Reuse and study proven components (including sing-box, Go tunnel, Firestack, and BitProxy) where appropriate and license-compatible.
- Keep UI, preferences, and firewall policy independent of the engine wherever practical.
- Never claim universal blocking outside Android's active protection/lockdown mechanisms without evidence.
- Test IPv4, IPv6, DNS, app switching, background traffic, network changes, crashes, restarts, and boot behavior.
- No accounts, telemetry, analytics, ads, subscriptions, or cloud services unless explicitly approved.

## Build

The Android Gradle project and CI workflow will be added from the previous Vianshield trial baseline, then validated and adjusted for this repository. See the workflow runs for actual build results; a successful source commit alone does not mean the APK has been tested.
