# Sketch AR

Sketch AR is an Android app that helps you trace a reference image onto paper. It shows your camera feed with the image laid over it, semi-transparent, so you can follow the lines with a pencil.

It's meant for everyday reference images, such as border designs and objects, not for copying other artists' work.

## Built with

- **Kotlin** and **Jetpack Compose** (Material 3)
- **CameraX** for the live camera preview
- **OpenCV** to detect the paper and turn images into outlines
- **Coil** to load images
- **Android Photo Picker** to choose images (no storage permission needed)

## How to use it

1. Tap **Choose Image** and pick a picture.
2. Allow camera access, then point the camera at your paper.
3. Position the image:
   - Drag to move it, pinch to resize it, and twist with two fingers to rotate it.
   - Or tap **Auto-fit** to snap it onto the corners of your paper automatically.
4. Tap **Lock** so the image doesn't move while you trace.
5. Adjust as needed:
   - **Opacity** slider: make the image lighter or darker.
   - **Flip**: mirror the image.
   - **Outline**: show only the image's edges.
6. Keep the phone steady (a stand helps) and trace.

## Limitations

- **Auto-fit needs contrast.** It works best with paper on a darker surface. White paper on a white desk often can't be detected.
- **All four corners of the paper must be in view** for Auto-fit to work.
- **The phone must stay still.** The image is fixed to the screen, not to the paper, so if the phone moves you'll need to re-align (or tap Auto-fit again).
- **Android only**, version 8.0 (API 26) or newer, with a back camera.
- **Large download.** OpenCV makes the debug build about 200 MB.

## Building

Requires JDK 17 and the Android SDK.

```sh
./gradlew assembleDebug    # build
./gradlew installDebug     # install on a connected device
```
