# Plate-Trace

Full-screen camera app that reads vehicle license plates on-device, saves a cropped image
of each one, and keeps a browsable history.

## Build & run
1. Open this folder in **Android Studio** (Koala 2024.1 or newer) and let Gradle sync.
2. Plug in a phone (Android 7.0 / API 24+) with USB debugging on and press **Run**.
   Or from a terminal: `./gradlew installDebug`.
3. Allow camera access on first launch.

Everything runs offline: the text-recognition model is bundled in the APK.

## Using it
- The whole screen is the camera. Point it at vehicles; plates are captured automatically.
- The round bubble (top right) shows how many plates are stored and pulses when one is saved.
  Under it, the plate number flashes briefly: green for a new plate, dark for a repeat.
- Tap the bubble to slide out the list (newest first, with search). Tap a plate to see every
  saved image and when it was seen. Tap an image to view it full screen. The trash icon
  deletes a plate and its images.

## How detection works
| Step | Where |
|---|---|
| Camera frames (1080p) → ML Kit text recognition | `scan/PlateAnalyzer.kt` |
| Keep text that looks like a plate (4–8 letters/digits, has a digit, wide box) | `scan/PlateTextFilter.kt` |
| Require the same reading in 2 frames; don't re-record while it stays in view; record a new sighting after 15 s out of view | `scan/PlateTracker.kt` |
| Crop the plate (text box + margin), save JPEG + Room row | `data/PlateRepository.kt`, `data/PlateDao.kt` |

Images are stored in the app's private storage (`files/plate_crops/`); the database is `plates.db`.

### Tuning
- **Your region's plate format:** add a regex to `REGION_PATTERNS` in `PlateTextFilter.kt`
  (e.g. UK `^[A-Z]{2}[0-9]{2}[A-Z]{3}$`). This removes most false positives such as
  phone numbers on vans or shop signs.
- **Strictness / repeat timing:** constructor values in `PlateTracker.kt`
  (`confirmFrames`, `confirmWindowMs`, `cooldownMs`).
- **Crop margin:** `padX` / `padY` in `cropAround()` in `PlateAnalyzer.kt`.

## Limitations
- This uses general-purpose OCR with plate rules, not a dedicated plate-detection model.
  It works well at close-to-medium range in daylight; expect misses at long range, at night,
  on motion blur, and on stylised or two-line plates. For higher accuracy, swap in a
  TFLite plate detector before the OCR step in `PlateAnalyzer`.
- Similar characters (O/0, I/1, B/8) can occasionally produce two entries for one plate.
- Check local laws before recording plates of other people's vehicles.
