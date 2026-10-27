# Vianshield — Product Specification

**Purpose:** Personal-use Android firewall and VPN. This is engine-independent product behavior; engine implementation details belong in a separate architecture document.

## Product goals

Prioritize reliable firewall behavior, strong per-app internet blocking, stable VPN connectivity and recovery, low memory/CPU use, and simple controls. Do not add commercial infrastructure, accounts, subscriptions, analytics, telemetry, advertisements, or cloud services unless explicitly approved. Prefer local processing and storage.

## Priorities

### P0 — Essential
- Stable VPN start, stop, restart, and reconnection.
- Correct per-app internet blocking.
- Persistent rules and preferences.
- Safe VPN failure and network-change handling.
- Truthful connection status and recovery.
- Correct Android VPN permission and foreground-service lifecycle.

### P1 — Core
- WireGuard `.conf` import and management.
- Per-app VPN routing and Direct routing where technically supported.
- DNS provider selection.
- DNS-based ad/tracker filtering.
- Per-app exemption from ad filtering.
- Correct interaction between blocking, routing, DNS, and filtering.

### P2 — Important
- One global speed limit with per-app exemptions.
- Optional screen-aware per-app internet blocking.
- Lightweight background event capture and Log Keeper.
- Resource optimization and actionable blocked-app notifications.

### P3 — Additional
- Theme selection (initial release: light theme only, sky-blue accents).
- Local backup/restore, diagnostics, app-list display preferences, and UI polish.

Do not delay core firewall correctness for lower-priority features.

## UI structure

Four bottom-navigation tabs:
1. **Main** — master protection, global speed limit, DNS provider, ad/tracker filtering, allow-list shortcut, concise status.
2. **Firewall** — searchable installed-app list, app icon/name, internet-blocking status, Block All, system-app visibility, sorting, and per-app details.
3. **WireGuard** — import/manage `.conf` profiles and show clear connection state.
4. **Settings** — secondary configuration, diagnostics, logs, backup/restore, and app preferences.

Keep the main screen minimal. Large or advanced controls belong on their own pages.

## Master protection behavior

When starting: request VPN permission if needed; start the engine/services; apply saved firewall policy as early as possible; restore DNS, filtering, routing, and speed-limit settings; report Ready only after required components are operational.

When stopping: stop the engine and unnecessary workers; release descriptors and network resources; preserve rules, preferences, configurations, and retained logs. Repeated start/stop must not create duplicate services, leaked resources, reconnect loops, or stale status.

Never show Connected when the engine or tunnel has failed.

## Per-app firewall policy

Each installed app has a policy:
- **Default:** documented default routing behavior.
- **VPN:** route through the selected VPN configuration when supported.
- **Direct:** bypass the Vianshield VPN route when supported and use the normal network path; do not promise support on every Android version/configuration.
- **Block internet:** prevent internet access to the extent enforceable by the active architecture.

**BLOCK HAS HIGHEST PRIORITY.** Block must remain effective regardless of VPN/Direct selection, speed-limit exemption, or ad-filter allow-list membership. A Direct rule must never override Block. An ad-filter exemption must never unblock an app. A speed-limit exemption must never override Block.

Persist rules across process termination, device restarts, and protection restarts. Apply saved rules before reporting Ready wherever technically possible.

## Strong-blocking acceptance tests

Test blocking over IPv4 and IPv6, DNS, background traffic, network changes, VPN restarts/reconnections, alternate network paths, device startup, and protection startup. Define behavior when the app is stopped, VPN crashes, engine fails to start, device restarts, network changes, or protection is reconnecting.

Investigate Android Always-on VPN and lockdown where appropriate, clearly explaining user action and implications. If Android cannot guarantee a behavior without lockdown or another system-supported mechanism, disclose the limitation. Never label an unenforced rule as protected.

## DNS and ad filtering

Provide a selector for ordinary DNS providers (for example, supported Google/Cloudflare-style resolvers) and DNS-based ad/tracker filtering.

The **ad-filter allow-list is an app exemption list**: apps selected there are allowed to bypass ad filtering. It is not a domain block list. The exemption affects filtering only and must not bypass internet blocking, alter VPN/Direct policy, or bypass speed limiting unless a separate setting explicitly says so.

## Speed limiting

Provide one global traffic limit with per-app exemptions. Exemption affects speed limiting only; it must not bypass blocking, routing, DNS policy, or ad filtering. Use genuine traffic shaping if feasible. Socket-buffer size alone is not a reliable bandwidth limiter. Test upload and download behavior and exempt-app attribution.

## Screen-aware blocking

Optional per-app behavior may restrict internet access when the screen is off. Distinguish screen state from foreground/visible app state. Define and test transitions, idle behavior, and app lifecycle. If Android restrictions prevent reliable foreground detection or enforcement, implement the most reliable supported alternative and document limitations without compromising VPN stability.

## Reliability, diagnostics, and resource use

Capture actionable operational errors and relevant lifecycle/network events with bounded, lightweight logging. Keep status truthful. Recover safely from process/service failure, network switching, and tunnel errors. Avoid unbounded logs, duplicate workers, leaks, and aggressive restart loops. Keep local data local unless explicitly approved otherwise.

## Definition of done

A feature is complete only when its behavior is implemented and tested—not when a UI control exists. Each change should include relevant unit/integration tests, build evidence, known limitations, and reproducible device-test steps.
