# Atlas Scans – Android

Android companion app for **Atlas Scans**, mirroring the iOS functionality while remaining compliant with the AtlasContracts schema.

---

## Technology Stack

| Concern | Technology |
|---|---|
| Language | Kotlin 1.9 |
| UI | Jetpack Compose (Material 3) |
| Spatial scanning | ARCore – Depth API + Plane Detection |
| Photo capture | CameraX |
| Audio / transcription | MediaRecorder + Google SpeechRecognizer |
| Persistence | Room Database |
| Serialisation | Kotlinx.serialization (JSON) |
| Navigation | `HorizontalPager` (swipe between screens) |
| State management | `ViewModel` scoped to the Activity |

---

## Features

### 1 – Room Scan (ARCore)
- ARCore Depth API accumulates a point cloud of the room.
- Plane detection identifies floors, ceilings, and walls.
- All data is converted to the `CapturedRoomScanV2` contract format (right-handed, metres).

### 2 – Photo Capture & Pin Drop (CameraX + ARCore)
- High-resolution JPEG capture via CameraX.
- Long-press the camera preview to place a spatial anchor via ARCore hit-test.
- Each pin stores world-space coordinates and an optional label.

### 3 – Voice Notes (MediaRecorder + SpeechRecognizer)
- Audio is recorded via `MediaRecorder` with Pause/Resume (API 24+).
- Google `SpeechRecognizer` provides real-time transcription.
- **The raw audio file is deleted after recording** – only the transcript string is included in the export bundle, satisfying the contract constraint.

### 4 – Summary & Export
- `SummaryScreen` lists all captured assets (scan, photos, voice notes).
- Individual items can be deleted before export.
- JSON export is shared via an Android share-sheet Intent (for Atlas Mind or any other recipient).

### 5 – Single Session Workflow
- Swipe left/right between all four screens via `HorizontalPager`.
- A `ViewModel` scoped to the Activity prevents data loss on rotation or accidental swipe.
- The current draft is persisted to a Room Database so work-in-progress survives process death.

---

## Contract Compliance

- All IDs are `java.util.UUID` v4 strings.
- Coordinates are in metres, right-handed (matches ARCore and the AtlasContracts spec).
- JSON is produced by `kotlinx.serialization` to guarantee schema compatibility with the TypeScript/Swift contracts.
- Audio files are **never** included in the export bundle.

---

## Build Requirements

| Requirement | Version |
|---|---|
| Android Studio | Hedgehog (2023.1) or later |
| Gradle | 8.4 |
| Android Gradle Plugin | 8.2.2 |
| Kotlin | 1.9.22 |
| Min SDK | 26 (Android 8) |
| Target SDK | 34 (Android 14) |
| ARCore | Device must support ARCore depth sensing |

### Build steps

```bash
# Clone the repo
git clone https://github.com/martinbibb-cmd/Atlas-scans-adroid.git
cd Atlas-scans-adroid

# Build debug APK
./gradlew assembleDebug

# Run unit tests (no device required)
./gradlew test

# Install on a connected device
./gradlew installDebug
```

### Required Permissions

The app requests the following permissions at runtime:

| Permission | Purpose |
|---|---|
| `CAMERA` | AR camera feed + photo capture |
| `RECORD_AUDIO` | Voice note recording |
| `ACCESS_FINE_LOCATION` | ARCore Geospatial API |

### Hardware Check

On first launch the app calls `ArCoreApk.getInstance().checkAvailability()`. If the device does not support ARCore depth sensing, the scan screen displays a graceful fallback message and the rest of the app remains fully functional.

---

## Project Structure

```
app/src/main/java/com/atlasscans/android/
├── MainActivity.kt               # Single Activity + HorizontalPager host
├── data/
│   ├── models/
│   │   ├── ContractModels.kt     # SessionCaptureV2, CapturedRoomScanV2, etc.
│   │   └── DatabaseModels.kt     # Room entity (CaptureSessionDraft)
│   ├── database/
│   │   ├── AppDatabase.kt
│   │   └── CaptureSessionDao.kt
│   └── repository/
│       └── SessionRepository.kt
├── ui/
│   ├── screens/
│   │   ├── ScanScreen.kt         # ARCore depth scan
│   │   ├── PhotoCaptureScreen.kt # CameraX + pin drop
│   │   ├── VoiceNoteScreen.kt    # MediaRecorder + SpeechRecognizer
│   │   └── SummaryScreen.kt      # Review + JSON export
│   └── theme/
│       ├── Color.kt
│       └── Theme.kt
├── utils/
│   ├── ArCoreConverter.kt        # ARCore → CapturedRoomScanV2
│   └── ArSessionManager.kt       # ARCore session lifecycle
└── viewmodel/
    └── SessionViewModel.kt
```

