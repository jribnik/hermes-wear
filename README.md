# Hermes Wear

A Wear OS app for a self-hosted [Hermes Agent](https://github.com/NousResearch/Hermes-Agent) gateway: talk to your agent by voice from your wrist and read its replies. It runs standalone on the watch (no phone app) and was written for a Pixel Watch (Wear OS 3+, API 30+).

## How it works

The app uses plain request/response HTTP against the gateway's OpenAI-compatible API server. It keeps no background connection and runs no service.

- **Send:** `POST {server}/v1/responses` with `{"model": "hermes-agent", "input": "<text>", "conversation": "<id>"}` and `Authorization: Bearer <api key>`. One turn at a time: while a turn is in flight the action bar shows a spinner and a **Cancel** button. A turn can take minutes while the agent runs tools, so each call is capped at 5 minutes.
  - **Cancel and the timeout only stop the watch waiting.** The gateway does not abort the turn when the client disconnects: it finishes it and records it under the conversation, so the agent may have acted (run tools, changed things) and will remember the turn even though the watch never shows the reply. Such a message is labelled **No reply**, not "not sent". Re-sending straight away puts a second turn in flight from the same previous turn, so check what happened first.
- **Conversation memory:** `conversation` is a stable id (`hermes-wear-<uuid>`), created once per install and stored in prefs. The gateway maps it to one agent session, so turns share context. **Settings → New conversation** clears the on-watch history and switches to a fresh id.
- **Replies:** each `message` output item's `output_text` parts are shown as a Hermes reply.
- **Tool calls:** `function_call` output items are tools the gateway **already ran** on the server; they are replayed in the response for display only. The app shows each one as a small read-only note, "Hermes ran <tool>". There is no approve/deny step in this app, and it does not take part in Hermes' dangerous-command approval flow, which the gateway exposes for streaming runs through `POST /v1/runs/{id}/approval`.
- **Connection check:** `GET {server}/v1/models` with the same Bearer key. The gateway serves this route only to an authenticated caller, so the status chip can tell **Connected**, **Key rejected** (401/403) and **Unreachable** apart. It runs on launch, after saving the URL or key, and when you tap the chip.
- **Lifetime:** conversation state lives in memory in an app-wide object. A reply that arrives after you swipe the app away is still recorded and shown on the next launch, as long as the process is alive. Nothing is written to disk except settings.
- **Complication:** a static short-text "Hermes" watch-face complication that opens the app.

### Code map (`app/src/main/java/com/hermes/wear/`)

| Path | Role |
|------|------|
| `data/network/HermesApiClient.kt` | OkHttp client for `/v1/responses` and `/v1/models`; `ResponsesParser` (response → messages); `ServerUrl.normalize` |
| `data/repository/HermesRepository.kt` | Conversation, sending and connection state (app-scoped) |
| `data/repository/PreferenceHelper.kt` | Server URL, API key and conversation id in SharedPreferences |
| `data/model/Models.kt` | Message and Responses API types |
| `HermesWearApp.kt` | Owns the repository and its application-level coroutine scope |
| `ui/` | Compose for Wear OS: `MainActivity`, `HermesViewModel`, conversation and settings screens |
| `complication/HermesComplicationService.kt` | Complication data source |

## Configuration

Nothing is committed to source: there is no default server URL or API key.

- **Server URL:** on the watch, open Settings (⚙️), tap the URL chip, type the server root with the watch keyboard and tap **Save URL**. Example: `https://hermes.example.com`, or `http://<host>:<port>` for local testing, where the port is the gateway API server's port (`platforms.api_server.port` in the gateway config, e.g. `8080`). A trailing `/` or a pasted `/v1/responses` is stripped.
- **HTTPS is required** except for local development hosts. `res/xml/network_security_config.xml` blocks plain `http://` everywhere except `localhost`/`127.0.0.1` (e.g. with `adb reverse tcp:8080 tcp:8080`) and `10.0.2.2` (the emulator's host), because the API key travels as a Bearer token. The intended setup is an HTTPS URL, e.g. a Cloudflare tunnel in front of the gateway. If you must use a LAN address over `http://`, add that exact host to the `domain-config` in that file and rebuild; Android can't allow an IP range. Settings shows a warning when the URL entered would be blocked.
- **API key:** in Settings, tap the *API Key* chip, type the key into the masked field and tap **Save key** (**Clear key** removes it). It is applied immediately, never displayed or logged; the chip only shows whether a key is set.
- **Storage:** both are stored in plaintext in the app-private `hermes_wear_prefs` SharedPreferences file. `allowBackup="false"` keeps it out of backups, but root, or `run-as` on a debug build, can read it. Encrypting it (e.g. `androidx.security` EncryptedSharedPreferences) is a possible follow-up.

### Provisioning from a computer (debug builds only)

Typing a long key on a watch keyboard is awkward. On a **debug build** (package `com.hermes.wear.debug`; `run-as` refuses release builds) you can write the prefs file over `adb`. This replaces the whole file, so set `server_url` (the server root, without `/v1/responses`) as well as `api_key`. Leave out `conversation_id` and the app creates one. XML-escape any `&`, `<` or `>` in the key. Force-stop the app first so it doesn't overwrite the file from its in-memory copy, then relaunch:

```bash
cat > /tmp/hermes_wear_prefs.xml <<'EOF'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="server_url">https://hermes.example.com</string>
    <string name="api_key">YOUR_API_KEY</string>
</map>
EOF
adb push /tmp/hermes_wear_prefs.xml /data/local/tmp/hermes_wear_prefs.xml
adb shell am force-stop com.hermes.wear.debug
adb shell "run-as com.hermes.wear.debug sh -c 'mkdir -p shared_prefs && cp /data/local/tmp/hermes_wear_prefs.xml shared_prefs/hermes_wear_prefs.xml'"
adb shell rm /data/local/tmp/hermes_wear_prefs.xml
rm /tmp/hermes_wear_prefs.xml
```

The file name and entry names (`server_url`, `api_key`, `conversation_id`) come from `PreferenceHelper.kt`.

## Build and test

Requirements: JDK 17, Android SDK 34 (the Gradle wrapper is 8.7). Then:

```bash
./gradlew testDebugUnitTest   # JVM unit tests; the gateway is faked with an OkHttp interceptor
./gradlew assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/app-debug.apk` (application id `com.hermes.wear.debug`). `./gradlew assembleRelease` needs a signing config of your own; keystores are gitignored.

The unit tests cover the request body, response parsing, HTTP/JSON/network failures, the connection check, conversation-id reuse and rotation, the one-turn-at-a-time guard and cancellation. The Compose UI has no tests, and the app has not been exercised against a live gateway since the conversation and tool-note changes.

## Install on a watch

Enable Developer options, then ADB debugging and Debug over Wi-Fi on the watch, and:

```bash
adb connect <watch-ip>:<port>
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.hermes.wear.debug/com.hermes.wear.ui.MainActivity
```

## Permissions

Only `INTERNET`. Voice input uses the system speech recognizer (`RecognizerIntent`), which records audio under its own permission, so this app does not request `RECORD_AUDIO`. The app is marked standalone (`com.google.android.wearable.standalone` in the manifest) and needs no phone app.

## License

No license file is included in this repository.
