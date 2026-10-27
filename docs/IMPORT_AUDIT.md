# Android Baseline Import Audit

## Included in the import branch

- Gradle settings, root build configuration, version catalog, and application module configuration.
- Android manifest, VPN service, lifecycle controller, sing-box adapter contract and bridge.
- WireGuard `.conf` parser and sing-box JSON builder.
- Operational logging, log export, crash reporting, diagnostic state model, main screen, and a dedicated log keeper screen.
- Required string/color/theme resources and a source-based vector shield icon.
- GitHub Actions workflow to build sing-box `libbox.aar` and one universal debug APK.

## Intentionally excluded

- The 474 KB generated launcher JPEG and pre-rendered PNG mipmap icons. They were replaced by the compact vector shield drawable, which also supplies the VPN notification icon.
- `receipts/RECEIPTS_001.md`, a large generated/provenance receipt that is not needed to compile the app.
- `metadata.json` and local/IDE-generated files.
- `.env`, signing files, and any local secret material. No credentials were found by the initial secret-pattern scan.
- A prebuilt `libbox.aar`; CI generates it from the selected upstream sing-box tag instead of tracking a native binary.

## Important known gaps

- The project still uses the prior trial's `com.example` namespace and `com.aistudio.vianshield.vxnpqk` application ID. Choose a stable package ID before production publishing.
- The ZIP did not include `gradlew`, `gradlew.bat`, or `gradle-wrapper.jar`; the CI workflow installs Gradle 9.3.1 directly. A standard wrapper should be added when it can be generated and verified.
- The source baseline's WireGuard profile conversion has not yet been audited against the exact pinned sing-box configuration schema; successful JSON generation alone is not proof of a working tunnel.
- The universal APK workflow explicitly checks for both ARMv7 and ARM64 native library entries. If upstream's selected build target does not generate both, CI should fail rather than silently shipping a single-ABI APK.
- No successful CI build or real-device test is claimed by this import.
