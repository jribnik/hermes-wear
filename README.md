# Hermes Wear

A Wear OS companion app for [Hermes Agent](https://github.com/NousResearch/Hermes-Agent) — your AI assistant on your wrist. Built for the **Pixel Watch 4** (and any Wear OS 3.0+ smartwatch) running Wear OS.

Hermes Wear lets you talk to your Hermes Agent via voice, receive responses, and — critically — approve or deny shell command authorizations right from your watch.

## Features

| Feature | Description |
|---------|-------------|
| 🎤 **Voice Input** | Speak to Hermes using on-device speech recognition |
| 💬 **Conversation UI** | Scrollable message history with Hermes, optimized for round watch screens |
| ✅ **Approval Prompts** | Receive, approve, or deny shell command authorization requests with large tap targets |
| ⏰ **Auto-Timeout** | Approval requests auto-deny after the configured timeout |
| 🔔 **Notifications** | Get notified on your watch for new messages and approval requests |
| 🔌 **Persistent Connection** | Background service with WebSocket (or HTTP long-poll fallback) keeps you connected |
| 🧩 **Complication** | Add Hermes as a watch face complication for one-tap access |
| 🌙 **Dark Theme** | OLED-optimized dark theme saves battery on your watch screen |

## Architecture

```
┌─────────────────────────────────────┐
│           Pixel Watch 4             │
│  ┌───────────────────────────────┐  │
│  │     Hermes Wear App           │  │
│  │  ┌──────────┐ ┌────────────┐  │  │
│  │  │Main UI   │ │Background  │  │  │
│  │  │(Compose) │ │Service     │  │  │
│  │  └────┬─────┘ └─────┬──────┘  │  │
│  │       │             │         │  │
│  │  ┌────┴─────────────┴──────┐  │  │
│  │  │    HermesApiClient      │  │  │
│  │  │  (WebSocket + HTTP)     │  │  │
│  │  └───────────┬─────────────┘  │  │
│  └──────────────┼────────────────┘  │
└─────────────────┼───────────────────┘
                  │ HTTP/WS
                  ▼
┌─────────────────────────────────────┐
│        Hermes Gateway API           │
│     (Running on Jake's Mac)         │
│  ┌───────────────────────────────┐  │
│  │  WebSocket:  /ws/watch        │  │
│  │  POST:       /api/message     │  │
│  │  POST:       /api/approval/.. │  │
│  │  GET:        /api/poll/watch  │  │
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘
```

### Key Components

- **`HermesApiClient`** — Manages WebSocket connection and HTTP requests to the Hermes Gateway
- **`HermesRepository`** — Manages message/approval state and mediates between API and UI
- **`HermesViewModel`** — Android ViewModel providing reactive UI state
- **`HermesConnectionService`** — Foreground service keeping the connection alive
- **`HermesComplicationService`** — Watch face complication data provider
- **`BootReceiver`** — Auto-starts the connection service on watch boot

## Requirements

### Development
- **Android Studio** Hedgehog (2023.1.1) or later
- **JDK 17**
- **Gradle 8.5** (included via wrapper)
- **Android SDK 34** with Wear OS system images

### Watch
- **Wear OS 3.0+** (API 30+)
- Internet connection (Wi-Fi or LTE)
- Microphone (for voice input)

### Hermes Gateway
- Hermes Agent running with the Gateway API enabled
- Network accessible from the watch (same Wi-Fi network, or via Tailscale/VPN)
- WebSocket endpoint at `/ws/watch`
- HTTP endpoints at `/api/message` and `/api/approval/respond`
- HTTP long-poll endpoint at `/api/poll/watch`

## Project Structure

```
hermes-wear/
├── app/
│   ├── build.gradle.kts          # App module build configuration
│   ├── proguard-rules.pro         # ProGuard rules for release builds
│   └── src/main/
│       ├── AndroidManifest.xml    # App manifest with permissions & services
│       ├── java/com/hermes/wear/
│       │   ├── HermesWearApp.kt               # Application class
│       │   ├── data/
│       │   │   ├── model/Models.kt            # Data classes (Message, Approval, etc.)
│       │   │   ├── network/HermesApiClient.kt # WebSocket + HTTP client
│       │   │   └── repository/
│       │   │       ├── HermesRepository.kt    # State management
│       │   │       └── PreferenceHelper.kt    # SharedPreferences wrapper
│       │   ├── service/
│       │   │   ├── HermesConnectionService.kt # Background connection service
│       │   │   └── BootReceiver.kt            # Auto-start on boot
│       │   ├── complication/
│       │   │   └── HermesComplicationService.kt # Complication data provider
│       │   └── ui/
│       │       ├── MainActivity.kt            # Entry point + navigation
│       │       ├── HermesViewModel.kt         # UI state management
│       │       ├── theme/Theme.kt             # Color palette & Material theme
│       │       ├── screens/
│       │       │   ├── ConversationScreen.kt  # Main conversation UI
│       │       │   ├── ApprovalScreen.kt      # Approve/Deny prompt UI
│       │       │   └── SettingsScreen.kt      # Connection settings
│       │       └── components/
│       │           └── MessageComponents.kt   # Message bubbles & indicators
│       └── res/
│           ├── values/strings.xml, colors.xml
│           ├── drawable/ic_hermes_complication.xml
│           ├── mipmap-*/ic_launcher.xml       # Adaptive launcher icons
│           └── xml/wear.xml                   # Wear OS manifest extras
├── build.gradle.kts               # Root build configuration
├── settings.gradle.kts            # Project settings
├── gradle.properties              # Gradle properties
├── gradle/wrapper/
│   └── gradle-wrapper.properties  # Gradle wrapper config
└── README.md                      # This file
```

## Build & Deploy

### 1. Configure the Hermes Server URL

Before building, update the default server URL in `HermesApiClient.kt` or set it from the watch Settings screen after installation.

By default, the app connects to `http://10.0.0.100:8080` — update this to match your Hermes Gateway address.

**Option A: Pre-configure in code** (for a sealed build)
Edit `app/src/main/java/com/hermes/wear/data/repository/PreferenceHelper.kt`:
```kotlin
const val DEFAULT_SERVER_URL = "http://192.168.1.X:8080"  // Your Mac's IP
```

**Option B: Configure on the watch** (recommended)
After installing, open Settings → enter your server URL → tap Connect.

### 2. Build with Android Studio

```bash
# Clone or open the project
cd ~/projects/hermes-wear

# On macOS/Linux, make the gradlew wrapper executable
chmod +x gradlew

# Build debug APK
./gradlew assembleDebug

# Or build release APK (you need a signing key)
./gradlew assembleRelease
```

The APK will be at:
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

### 3. Deploy to Pixel Watch 4

#### Via ADB over Wi-Fi

```bash
# Enable ADB debugging on the watch:
# Settings → Developer Options → ADB Debugging (ON)
# Settings → Developer Options → Debug over Wi-Fi (ON)
# Note the IP address shown (e.g., 192.168.1.50:5555)

# Connect from your Mac
adb connect 192.168.1.50:5555

# Verify connection
adb devices

# Install the app
adb install app/build/outputs/apk/debug/app-debug.apk

# Launch the app
adb shell am start -n com.hermes.wear/.ui.MainActivity
```

#### Via Android Studio

1. Open the project in Android Studio
2. Select `Run → Edit Configurations → Wear OS`
3. Ensure your watch is connected via ADB
4. Click the Run button (▶️) and select your watch

### 4. Signing for Release

Create a keystore:
```bash
keytool -genkey -v -keystore hermes-wear.keystore \
  -alias hermes-wear -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass your_password -keypass your_password
```

Add to `app/build.gradle.kts`:
```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("../hermes-wear.keystore")
            storePassword = "your_password"
            keyAlias = "hermes-wear"
            keyPassword = "your_password"
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

## Using the App

### First Launch
1. Open Hermes Wear from your watch's app launcher
2. You'll see "Tap to connect" — tap it
3. If the default server URL is wrong, go to Settings (⚙️) to change it
4. Once connected, "● Connected" appears at the top

### Sending Messages
- Tap the **🎤 Speak** button and speak your message
- Voice recognition runs on-device (Google Speech Services)
- Your message appears and Hermes responds

### Handling Approvals
When Hermes needs to run a shell command:
1. Your watch vibrates with a double-pulse pattern
2. A full-screen approval prompt appears showing:
   - Risk level (Low 🟢 / Medium 🟡 / High 🟠 / Critical 🔴)
   - The command Hermes wants to run
   - A description of what it does
   - A countdown timer (auto-denies when expired)
3. Tap **✕ Deny** or **✓ Approve**
4. The decision is sent back to Hermes immediately

### Notifications
- **Connection status**: Persistent notification when connected (low priority, silent)
- **New messages**: High-priority notification with vibration
- **Approval requests**: High-priority notification that bypasses Do Not Disturb

### Complication
Add the Hermes complication to your watch face:
1. Long-press your watch face → Customize
2. Tap a complication slot → find "Hermes"
3. Now you can tap the complication to open Hermes Wear instantly

## Hermes Gateway API Contract

The watch expects these endpoints on your Hermes Gateway:

### WebSocket: `ws://<host>:<port>/ws/watch`
Real-time bidirectional communication. Messages are JSON:

```json
{
  "type": "message",
  "message": {
    "id": "msg_123",
    "text": "Hello from Hermes!",
    "sender": "hermes",
    "timestamp": 1712345678000
  }
}
```

```json
{
  "type": "approval",
  "approval": {
    "id": "appr_456",
    "command": "rm -rf /tmp/cache",
    "description": "Cleaning up temporary build artifacts",
    "risk_level": "low",
    "timestamp": 1712345678000,
    "timeout_seconds": 60
  }
}
```

### POST `/api/message`
Send a message from the watch:
```json
{
  "text": "What's the weather?",
  "platform": "wear_os",
  "sender_id": "pixel_watch_4",
  "timestamp": 1712345678000
}
```

### POST `/api/approval/respond`
Respond to an approval request:
```json
{
  "approval_id": "appr_456",
  "decision": "approve",
  "platform": "wear_os",
  "timestamp": 1712345678000
}
```

### GET `/api/poll/watch?client_id=pixel_watch_4`
HTTP long-poll fallback endpoint. Returns JSON payloads in the same format as WebSocket messages.

## Troubleshooting

| Issue | Solution |
|-------|----------|
| **Can't connect** | Verify the Mac's IP is correct and port is open. Try `curl http://MAC_IP:8080/api/poll/watch` from the Mac |
| **Voice input doesn't work** | Ensure Google app is installed on the watch and microphone permissions granted |
| **Notifications not appearing** | Check notification permissions in watch Settings → Apps → Hermes Wear |
| **App disconnects frequently** | Check Wi-Fi stability on the watch. The service will auto-reconnect |
| **Approval countdown too fast** | Adjust `timeout_seconds` in the Hermes Gateway configuration |
| **Build fails** | Ensure you have Android SDK 34 and Wear OS system images installed in SDK Manager |

## Configuration

The following settings can be changed from the watch's Settings screen or by editing `PreferenceHelper.kt`:

| Setting | Default | Description |
|---------|---------|-------------|
| `server_url` | `http://10.0.0.100:8080` | Hermes Gateway API base URL |
| `sender_id` | `pixel_watch_4` | Unique identifier for this watch |
| `auto_connect` | `true` | Auto-connect on app launch and boot |
| `enable_notifications` | `true` | Show notifications for messages/approvals |
| `vibrate_on_message` | `true` | Vibrate on new messages |
| `vibrate_on_approval` | `true` | Vibrate on approval requests (stronger pattern) |

## License

MIT License — see [Hermes Agent](https://github.com/NousResearch/Hermes-Agent) for the parent project license.

---

Built for the **Hermes Agent** ecosystem by Nous Research.
