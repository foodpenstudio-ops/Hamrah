# Hamrah

Hamrah is an early Android prototype for nearby offline communication.

## First test
1. Build the APK with the GitHub Actions workflow.
2. Install it on two Android phones that have Google Play services.
3. Open the app on both phones and allow the requested nearby/Bluetooth permissions.
4. Tap **Find Nearby Devices** on both phones and keep the app open.
5. When connected, send a short test message.

## Important limitations
- This is an experimental prototype, not a finished or security-audited messenger.
- Nearby Connections uses available local radios and Google Play services; it does not provide worldwide communication without infrastructure.
- This first version does not yet implement encryption, user approval, message history storage, background relaying, or reliable SOS delivery.
- Emergency Help is only a text message to currently connected peers. It does not contact emergency services.
- Test only with trusted devices and non-sensitive messages.

## Build
GitHub Actions runs `gradle assembleDebug` and publishes the debug APK as a workflow artifact.
