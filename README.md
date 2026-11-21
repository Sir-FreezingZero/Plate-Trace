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
The whole screen is the camera, with one row of controls along the bottom:

| Control | What it does |
|---|---|
| ⚙ (bottom left) | Opens the Settings page: how soon the same plate is saved again, saved-data summary, delete all plates. Recording keeps running while it's open |
| Big round button (centre) | Start / stop recording. Plates are only captured while recording; a red timer shows at the top |
| Photo button (left end of the bar) | Pick a photo from your gallery and scan it for plates (handy for testing without driving) |
| Other bar buttons | Spare, not used yet |
| ☰ (bottom right) | Opens the Plates page: a timeline of saved plates grouped by day (or sorted by most seen), with search. Tap one for its car and plate shots and sighting history. Recording keeps running while the page is open |

When a plate is saved its number flashes at the top (green = new plate, dark = seen before) and the ☰ button pulses.

## How detection works
| Step | Where |
|---|---|
| Camera frames (1080p) → ML Kit text recognition | `scan/PlateAnalyzer.kt` |
| Keep text that looks like a plate (4–8 letters/digits, has a digit, wide box) | `scan/PlateTextFilter.kt` |
| Match reads fuzzily (OCR spells the same plate slightly differently frame to frame); require 2 matching reads; don't re-record while it stays in view; record a new sighting after it's been unseen for the Settings delay | `scan/PlateTracker.kt` |
| Crop the plate (text box + margin) and a wider shot of the back of the car, save JPEGs + Room row | `data/PlateRepository.kt`, `data/PlateDao.kt` |

Images are stored in the app's private storage (`files/plate_crops/`); the database is `plates.db`.

### Tuning
- **Your region's plate format:** add a regex to `REGION_PATTERNS` in `PlateTextFilter.kt`
  (e.g. UK `^[A-Z]{2}[0-9]{2}[A-Z]{3}$`). This removes most false positives such as
  phone numbers on vans or shop signs.
- **Strictness / repeat timing:** constructor values in `PlateTracker.kt`
  (`confirmFrames`, `confirmWindowMs`, `cooldownMs`).
- **Crop margin:** `padX` / `padY` in `cropAround()` in `scan/BitmapUtils.kt`.
- **Vehicle shot size:** the `6.05f` (car width ÷ plate-text width) and `0.8f` (height ÷ width) in `cropVehicle()` in the same file.

## Limitations
- This uses general-purpose OCR with plate rules, not a dedicated plate-detection model.
  It works well at close-to-medium range in daylight; expect misses at long range, at night,
  on motion blur, and on stylised or two-line plates. For higher accuracy, swap in a
  TFLite plate detector before the OCR step in `PlateAnalyzer`.
- The car shot is estimated from where the plate is (there is no vehicle detector), so very close or very far cars may be framed loosely.
- Similar characters (O/0, I/1, B/8) can occasionally produce two entries for one plate.
- Check local laws before recording plates of other people's vehicles.
