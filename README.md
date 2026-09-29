# Hermes Wear

A Wear OS companion app for a self-hosted [Hermes Agent](https://github.com/NousResearch/Hermes-Agent): talk to your agent by voice from your wrist, read its replies, and approve or deny tool-call requests. Written for a Pixel Watch (Wear OS 3+, API 30+).

## How it works

The app is **HTTP-only**. It keeps no background connection and runs no service. Everything goes through the Hermes Gateway API Server's OpenAI-compatible Responses endpoint.

- **Send:** `POST {server}/v1/responses` with `{"model": "hermes-agent", "input": "<text>"}`, authenticated with `Authorization: Bearer <api key>`.
- **Replies:** `output` items of type `message` are shown as Hermes messages.
- **Approvals:** `output` items of type `function_call` open the approval screen. Approve/Deny sends a follow-up `/v1/responses` call whose input is the text `approve <call_id>` or `deny <call_id>`. This text form has not been verified against the gateway; check it before relying on it.
- **Reachability:** a `HEAD` request to the server root, used for the connection indicator.
- **Complication:** a short-text watch-face complication that opens the app.

### Code map (`app/src/main/java/com/hermes/wear/`)

| Path | Role |
|------|------|
| `data/network/HermesApiClient.kt` | OkHttp client for `/v1/responses` and the health check |
| `data/repository/HermesRepository.kt` | Conversation and pending-approval state |
| `data/repository/PreferenceHelper.kt` | Server URL and API key in SharedPreferences |
| `data/model/Models.kt` | Message, approval and Responses API types |
| `ui/` | Compose for Wear OS: `MainActivity`, `HermesViewModel`, conversation / approval / settings screens |
| `complication/HermesComplicationService.kt` | Complication data source |

## Configuration

There are no usable defaults: no server URL or API key is committed to source.

- **Server URL:** on the watch, open Settings and tap the URL chip. It cycles through a small list of local/LAN presets defined in `ui/screens/SettingsScreen.kt`; edit that list for your own gateway. Cleartext HTTP is allowed (`res/xml/network_security_config.xml`) because the gateway is expected to be on a trusted network.
- **API key:** on the watch, open Settings, tap the *API Key* chip, type the key into the masked field and tap *Save key* (*Clear key* removes it). It is stored in the `api_key` SharedPreferences entry (plaintext app-private storage), is applied to the running client immediately, and is never displayed or logged; the chip only shows whether a key is set. The entry UI is a plain masked text field and has not been tested on a physical watch.

## Build

Requirements: JDK 17, Android SDK 34 (the Gradle wrapper is 8.7). Clone the repo and run:

```bash
./gradlew assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/app-debug.apk` (application id `com.hermes.wear.debug`). `./gradlew assembleRelease` needs a signing config of your own; keystores are gitignored.

## Install on a watch

Enable Developer options, then ADB debugging and Debug over Wi-Fi on the watch, and:

```bash
adb connect <watch-ip>:<port>
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.hermes.wear.debug/com.hermes.wear.ui.MainActivity
```

## Permissions

`INTERNET`, `RECORD_AUDIO` and `VIBRATE` are declared in the manifest. Voice input uses the system speech recognizer (`RecognizerIntent`).

Note: `app/src/main/res/xml/wear.xml` declares `com.google.android.wearable.standalone`, but nothing references it (the manifest has no such meta-data), so the app is not currently marked as standalone.

## License

No license file is included in this repository.
