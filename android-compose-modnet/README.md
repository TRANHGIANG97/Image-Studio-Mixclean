# ModNet Compose

Android Compose app that lets you pick an image and run the MODNet `int8` TFLite model locally.

## What it does

- Opens the system image picker
- Runs `modnet_int8.tflite` from `app/src/main/assets`
- Shows the original image and the transparent cutout result

## Build

```powershell
cd C:\Users\Toshiba\Desktop\modnet\android-compose-modnet
.\gradlew.bat assembleDebug
```

## Model

The app uses `app/src/main/assets/modnet_int8.tflite`, which was exported from the MODNet checkpoint in this workspace.

## Notes

- The model expects `512 x 512` RGB input.
- Input is quantized `int8` in `NHWC` layout.
- The app rescales and center-crops the chosen image before inference.
