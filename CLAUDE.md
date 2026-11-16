# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Sketch AR (package `com.tracecam`) is an Android tracing app: the user picks a reference image, and it is drawn semi-transparently over a live camera preview so it can be traced onto paper. Kotlin only, Jetpack Compose + Material 3, single activity, minSdk 26, compile/target SDK 37.

## Commands

```sh
./gradlew assembleDebug                 # build the debug APK
./gradlew installDebug                  # build and install on the connected device
./gradlew testDebugUnitTest             # run all JVM unit tests
./gradlew testDebugUnitTest --tests 'com.tracecam.cv.CornerOrderingTest'              # one class
./gradlew testDebugUnitTest --tests 'com.tracecam.cv.CornerOrderingTest.rejectsWrongNumberOfCorners'  # one test
./gradlew lintDebug                     # Android lint
adb shell am start -n com.tracecam/.MainActivity   # launch on device
```

- Build setup: Gradle Kotlin DSL with a version catalog (`gradle/libs.versions.toml`); add every dependency there. AGP 9 has Kotlin built in, so do **not** apply `org.jetbrains.kotlin.android`; only the Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`) is applied.
- The Gradle daemon needs JDK 17+. `gradle/gradle-daemon-jvm.properties` makes Gradle pick an installed JDK 17 automatically, so `./gradlew` works even when the default `java` is older.
- `./gradlew :app:tasks --all` currently fails (AGP's Compose Preview task can't be created). Run tasks by name instead.
- Unit tests run on the JVM and **cannot load OpenCV's native library**. Only pure Kotlin logic is unit-testable. Anything calling OpenCV has to be checked on a device.

## Architecture

### Navigation
`ui/SketchArApp.kt` holds a `NavHost` with string routes `home` and `trace?imageUri={imageUri}`. The picked image's content Uri is passed URL-encoded as a route argument (`Routes.trace(uri)`). The home screen asks for camera permission *after* an image is picked and navigates whatever the answer. The trace screen handles a denied permission itself: Try Again, then Open Settings once Android stops showing the dialog.

### Overlay model (trace screen)
`TraceViewModel` exposes a single `StateFlow<TraceUiState>`, so all overlay state survives rotation. The key field is `overlayMatrix`: 9 floats of a 3×3 `android.graphics.Matrix` mapping the image **normalised to the unit square** (0,0)–(1,1) onto screen pixels.
- A matrix rather than scale/rotate/offset values because Auto-fit produces a perspective warp.
- Because it's normalised, the decoded bitmap's resolution doesn't matter. The overlay and its outline version (different pixel sizes) share the same matrix.
- Gestures (`detectTransformGestures`) post-multiply onto the matrix, pivoting at the gesture centroid. `onViewSize` re-centres it after rotation.
- `TraceOverlay` draws with `nativeCanvas.drawBitmap(bitmap, matrix, paint)`, pre-applying the flip and the 1/width, 1/height normalisation. Bitmaps are loaded with Coil using `allowHardware(false)` for this reason.
- `locked` disables gestures. `flipped` and `outlineMode` only affect drawing.

### Auto-fit pipeline
Auto-fit detects paper in the camera feed and warps the overlay onto it:
1. `CameraPreview` binds Preview and a ~640×480 `ImageAnalysis` (KEEP_ONLY_LATEST, own executor) in a `UseCaseGroup` with the `PreviewView`'s **viewport**. Sharing the viewport makes each `ImageProxy.cropRect` match exactly what's visible on screen, so only that region is searched.
2. `camera/PaperAnalyzer` drops frames immediately unless a detection was requested. It tries up to 15 frames, using the Y plane directly as grayscale, and always closes the `ImageProxy`.
3. `cv/PaperDetector` finds the paper: blur, Canny, dilate, contours, `approxPolyDP`, then the largest convex quadrilateral covering at least 10% of the area.
4. `cv/ImageToViewMapper` maps the corners to screen coordinates: subtract the crop, rotate by `rotationDegrees`, then apply FILL_CENTER scaling.
5. The ViewModel **re-orders the corners in screen space** (camera-buffer "top-left" is rotated relative to the screen). It then calls `Matrix.setPolyToPoly` from the unit square to the four corners and sets `locked = true`.

Detection needs contrast: white paper on a white desk fails because its edges are too faint. Paper on a darker surface works. "Fixes" based on convex hulls were tried and rejected because they latch onto background clutter.

### Package boundaries
- `cv/`: computer-vision logic. It must stay free of `android.*` imports (only OpenCV, Java and Kotlin) so it remains plain JVM code. Pure-Kotlin pieces such as `orderCorners` and `ImageToViewMapper` are unit-tested in `app/src/test`.
- `imaging/`: Android glue for OpenCV. `OpenCv.isLoaded` lazily calls `OpenCVLoader.initLocal()`; check it before any OpenCV call. `createOutlineBitmap` converts bitmaps to and from `Mat`.
- `camera/`: CameraX analyzer.
- `ui/`: Compose screens, the theme and permissions.

### OpenCV 5 notes
The dependency is `org.opencv:opencv` 5.x from Maven Central (all ABIs, which is why the debug APK is about 200 MB). In OpenCV 5, `approxPolyDP`, `arcLength`, `contourArea` and `isContourConvex` are in `org.opencv.geometry.Geometry`, not `Imgproc`. `findContours`, `Canny` and similar are still in `Imgproc`.

## UI conventions
- The theme (`ui/theme/Theme.kt`) is a fixed light palette with no dynamic colour or dark mode:
  - background `#F7F0DC`
  - top app bar `#B4C299` (also `secondaryContainer`)
  - primary/button `#C274B5`
  - black text
- The app is edge-to-edge with dark system bar icons. Screens handle their own insets.
- Icons are local vector drawables in `res/drawable`. Material icon libraries aren't a dependency.

## Device testing
The test device is a Galaxy Z Flip (two displays), so `adb exec-out screencap` needs `-d <display id>` (from `dumpsys SurfaceFlinger --display-id`), and taps need `input -d 0`. On this device the system photo picker needs a tap on **Done** after selecting an image.
