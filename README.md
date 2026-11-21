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
| ⚙ (bottom left) | Opens the Settings page: plate format, how soon the same plate is saved again, car shot framing, saved-data summary, delete all plates. Recording keeps running while it's open |
| Big round button (centre) | Tap to start / stop recording (a red timer shows at the top). The icon shows the mode: video camera or photo camera. **Press and hold** to switch between video and photo mode |
| Photo button (left end of the bar) | Pick a photo from your gallery and scan it for plates (handy for testing without driving) |
| Other bar buttons | Spare, not used yet |
| ☰ (bottom right) | Opens the Plates page: a timeline of saved plates grouped by day (or sorted by most seen, or only watched plates), with search. Tap a bell to watch a plate, or the star to keep it from being auto-deleted. Swipe a plate left to delete it (Undo for 4 seconds). Press and hold a plate to select several, then Merge (keep one plate number, move the others' sightings into it) or Delete. Tap one for its car and plate shots and sighting history. Recording keeps running while the page is open |

When a plate is saved its number flashes at the top (green = new plate, dark = seen before) and the ☰ button pulses.

## How detection works
Each camera frame (while recording) goes through:

| Step | What happens | Where |
|---|---|---|
| 1. Find | A plate **detector** (YOLOv9-tiny, 512 px) finds license plates in the frame | `scan/PlateEngine.kt` |
| 2. Crop | Each plate is cropped with a small margin (this is the saved plate image) | `scan/BitmapUtils.kt` |
| 3. Read | A plate **reader** (fast-plate-ocr `cct-s-v2-global`) reads the characters from the crop only | `scan/PlateEngine.kt` |
| 4. Check | The text must fit the **Plate format** setting | `scan/PlateTextFilter.kt` |
| 5. Agree | Saved as soon as 3 frames read exactly the same text (about a second, while still in view). If they never agree, a per-character vote picks the text when the plate leaves view. Saved once while in view, again after the out-of-view delay | `scan/PlateTracker.kt` |
| 6. Save | The clearest plate crop + a car shot centred on the plate (black fill if it runs off the frame) | `data/PlateRepository.kt` |

Both models run on the phone with ONNX Runtime, work offline, and are bundled in `app/src/main/assets`. They come from [fast-alpr](https://github.com/ankandrew/fast-alpr) by ankandrew (MIT licence; copies in `assets/licenses`). The Android pre/post-processing was checked against the reference Python implementation and gives identical results on test images.

Images are stored in the app's private storage (`files/plate_crops/`); the database is `plates.db`.

### Settings
- **Plate format:** *US plates* (default: needs letters and numbers, so phone numbers are ignored), *Only specific formats* (tick formats such as ABC 1234 or 1ABC234, or add your own with L = letter, 0 = number, * = either; look-alike characters like 0/O and 8/B are corrected to fit), or *Any plate-like text*.
- **Detection:** *Plate detection confidence* (how sure it must be that a box is a plate before reading it) and *Reading confidence* (how sure it must be of every character), 5–95%, default 40%.
- **Read plates from:** *Video* (live 1080p feed, several images a second; default) or *Photos* (a full-resolution photo every 0.5–3 s; sharper and reads further on many phones, but fewer images, so fast-passing cars may be missed). In photo mode a single clear read is enough to save a car that only appears in one photo.
- **Matching readings to save:** 2–5 frames must read exactly the same text before a plate is saved (default 3).
- **Save the same plate again after:** how long a plate must be out of view before it's saved again.
- **Auto-delete old plates:** when on, plates not seen for the set time (1 hour to 90 days; type e.g. `5h` or `10d`) are deleted with their images, checked at startup and every 10 minutes. Plates with an alert (bell) or a star are always kept.
- **Show live readings:** while recording, draws a box around every plate the detector finds, with a strip on top showing the plate number read in that frame and a strip underneath showing status only: `2/3` (matching readings), `saved`, `unsure`, or `—` (not a plate format). White while reading, green once saved, amber when rejected. Also shows the image size being read and how long each one takes. Useful for checking why a plate is misread.
- **Car shot framing:** how far the car photo zooms out around the plate (2×–9× the plate's width), with a preview. The plate is always centred; the plate crop is unaffected.

### Duplicate misreads
Nothing is merged. A plate's detail page lists **similar plates** (look-alike characters, or one or two characters off) as likely misreads; tap one to open that plate.

### Watch list
Tap the bell on any plate (in the list or on its detail page) to watch it. When a watched plate is seen again the phone vibrates, an amber banner appears over everything, and a notification with the car photo is posted (Android 13+ asks for notification permission the first time). Use the **Watched** filter on the Plates page to see only watched plates.

To watch for a plate you **haven't seen yet**, tap the **eye** icon on the Plates page and type the plate number. It's listed under *Waiting for* in the Watched view; the first time it's seen (allowing look-alike characters such as 0/O), you get the alert and it becomes a normal watched plate. Typing a plate you've already saved just turns on its bell.

### Tuning in code
- **Save timing:** `agreeReads` (matching reads needed to save), `minReads`, `settleMs`, `maxWaitMs` in `PlateTracker.kt`.
- **Plate crop margin:** `PLATE_PAD_X` / `PLATE_PAD_Y` in `scan/PlateEngine.kt`.


## Limitations
- Plates need to be roughly 75+ px wide in the 1080p camera frame to be found (about 2–3 car lengths away on most phones). Far-away plates are missed.
- Each frame takes a few hundred milliseconds on a mid-range phone, so very fast-moving plates may be missed.
- A plate is saved after about a second in view (3 matching reads). Plates seen only briefly are saved when they leave view, if they were read at least twice.
- The car shot is framed from the plate's position and size (there is no car detector), so its framing is approximate.
- Check local laws before recording plates of other people's vehicles.
