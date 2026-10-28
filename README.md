# Vianshield

A lightweight, personal-use Android firewall and VPN application.

## Development status

This branch imports the Android baseline from the previous Vianshield sing-box trial and trims the project to the files required for the Android app. **The project has not yet been verified by a successful CI build.** A green GitHub Actions run and device tests are required before treating the APK as usable.

## Product priorities

1. Reliable VPN start/stop, recovery, and truthful connection state.
2. Persistent per-app rules where **Block has highest priority** over Direct, VPN, ad-filter exemptions, and speed-limit exemptions.
3. DNS provider selection and DNS-based ad/tracker filtering.
4. An allow-list that bypasses ad filtering only; it must never unblock a blocked app.
5. One global socket-buffer-based speed limit with per-app exemptions.
6. Screen-aware per-app blocking within Android's supported enforcement limits.
7. Low resource use, local-first storage, diagnostics, and recovery.

## App structure

The current imported trial contains a minimal home screen and a separate Logs & diagnostics screen. It is a foundation, not yet the complete four-tab product UI. The intended tabs are **Main**, **Firewall**, **WireGuard**, and **Settings**, with a light-only theme and sky-blue accents.

## Repository layout

- `app/` — Android source, resources, Gradle module, and tests.
- `docs/PRODUCT_SPEC.md` — engine-independent product requirements.
- `docs/ENGINE_ARCHITECTURE.md` — engine boundaries and implementation strategy.
- `.github/workflows/build-vianshield.yml` — builds upstream sing-box `libbox.aar`, then checks that the generated debug APK contains both `armeabi-v7a` and `arm64-v8a`.

## Build notes

The workflow uses Gradle 9.3.1 through `gradle/actions/setup-gradle`; the imported ZIP did not contain the Gradle wrapper scripts or wrapper JAR. The native `libbox.aar` is built in CI and is not committed to source control.

The package name and application ID are still carried over from the AI Studio trial and must be finalized before a release. Signing keys and secrets must not be committed.

## Development principles

- Prefer a narrow, purpose-built engine over recreating a general-purpose proxy platform.
- Study proven components including sing-box, Go tunnel, Firestack, and BitProxy where useful and license-compatible.
- Keep UI, preferences, and firewall policy independent from the underlying engine wherever practical.
- Never claim universal blocking outside Android's active protection/lockdown mechanisms without evidence.
- Test IPv4, IPv6, DNS, app switching, background traffic, network changes, crashes, restarts, and boot behavior.
- No accounts, telemetry, analytics, ads, subscriptions, or cloud services unless explicitly approved.
