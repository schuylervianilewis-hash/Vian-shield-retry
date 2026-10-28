# Vianshield UI shell

A deliberately small Android shell to establish a reliable APK pipeline before integrating the VPN/firewall engine.

## Included
- Minimal light-only home screen with a single Start/Stop toggle.
- Log Keeper page with local event persistence and a clear-log action.
- Settings page containing clearly marked placeholders.
- Sky-blue accents and no native VPN/engine dependency.

## Safety / prototype status
The toggle is a UI demo only. The app does **not** establish a VPN, filter DNS, block applications, or protect traffic. The screen states this explicitly. Do not treat this APK as a working firewall.

## Build
Open in Android Studio or run `gradle assembleDebug` with Gradle 8.9 and JDK 17. The GitHub Actions workflow builds and uploads the debug APK.
