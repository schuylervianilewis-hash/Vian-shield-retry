# Vianshield — Engine Architecture and Reuse Strategy

This document is implementation-oriented. The product requirements are defined in [PRODUCT_SPEC.md](PRODUCT_SPEC.md).

## Goal

Build a focused Vianshield networking/firewall engine rather than a feature-for-feature replacement for sing-box. Reuse suitable, license-compatible components and proven Android integration patterns from sing-box, Go tunnel, Firestack, BitProxy, and the previous Vianshield trial. Do not copy unrelated features or add multiple full engines without a demonstrated need.

## Proposed boundaries

- **Android app/UI (Kotlin/Compose):** screens, app inventory, user settings, permission UX, notifications, local persistence.
- **Policy layer:** canonical per-app rules and precedence. Block is absolute and evaluated before routing or exemptions.
- **Android VPN/service adapter:** Android `VpnService`, TUN lifecycle, foreground service, network callbacks, permission and shutdown handling.
- **Engine adapter:** narrow interface for starting/stopping the chosen packet engine, applying configuration, reporting state, and exposing diagnostics.
- **DNS/filter subsystem:** resolver choice, blocklist loading/updating, query policy, and app-specific filter exemptions.
- **Traffic shaping subsystem:** global upload/download limit with per-app exemption where reliable app attribution is available.
- **Diagnostics/recovery:** bounded logs, crash/error reporting, lifecycle state machine, cleanup and bounded retry/backoff.

Keep user-visible settings and persisted firewall policy engine-independent wherever practical.

## Required policy precedence

1. **Block internet** — deny the app's traffic before lower-priority routing/filter/speed decisions.
2. **Routing selection** — VPN, Direct, or Default, only where Android and the engine can enforce it.
3. **DNS/ad-filter policy** — selected DNS provider and filtering; app allow-list exempts filtering only.
4. **Speed policy** — global limit unless that app has an explicit speed exemption.
5. **Diagnostics** — record relevant decisions without claiming visibility into traffic that bypasses the VPN.

A Direct route may leave the VPN engine's enforcement path. The implementation must not claim the engine can block traffic after it has been excluded. Investigate supported Android enforcement and lockdown options and document the resulting guarantee precisely.

## Reuse evaluation checklist

For each candidate component (sing-box, Go tunnel, Firestack, BitProxy, prior Vianshield source):
- Identify the exact code/API that provides the capability.
- Check license, maintenance state, Android/ABI compatibility, and dependency cost.
- Trace configuration, startup/shutdown, file descriptors, callbacks, error handling, and resource cleanup.
- Separate reusable code from product UI, unrelated features, and assumptions that do not match this spec.
- Add tests around the reused boundary before adapting it.
- Avoid maintaining two competing engines unless there is a specific tested requirement.

## Initial implementation sequence

1. Inventory the previous Vianshield Android trial; identify build pipeline, application ID, package structure, VPN service, engine coupling, UI, tests, and known defects.
2. Establish reproducible Android builds and artifact publishing in GitHub Actions.
3. Define a narrow engine interface and truthful lifecycle/state machine.
4. Implement and test core VPN start/stop/recovery.
5. Implement canonical persisted per-app policy and verify Block precedence.
6. Implement DNS selection and DNS-based filtering, including app-level filtering exemptions.
7. Evaluate real traffic shaping for the global limit and per-app exemptions.
8. Add screen-aware blocking only after the core path is stable.
9. Test on real Android devices across IPv4/IPv6, DNS, Wi-Fi/mobile transitions, background traffic, service death, reboot, and protection restarts.

## Build/CI principles

- Build on GitHub Actions; do not depend on Google AI Studio as the build environment.
- Pin versions and record upstream engine versions/commits.
- Build required Android ABIs deliberately and label APK artifacts accurately.
- Never publish secrets, signing keys, local paths, or private configuration files.
- Treat CI build success as necessary but insufficient; runtime behavior still requires device tests.

## Open technical questions

- Which exact previous-trial components can be retained without preserving problematic engine coupling?
- Which parts of the chosen packet engine can be reused versus implemented as Vianshield-specific policy?
- What reliable mechanism can enforce Block for apps excluded from the VPN path?
- What is the least-complex real traffic-shaping implementation that can attribute traffic to apps and exempt selected apps?
- Which service recovery guarantees are possible across supported Android versions and OEM behavior?
