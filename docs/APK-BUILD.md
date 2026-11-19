# Android APK build

The repository includes a GitHub Actions workflow at `.github/workflows/android-apk.yml`.

It builds the current Open Jarvis Android application with Gradle and uploads the debug APK as the `Open-Jarvis-Debug-APK` workflow artifact. The artifact is available from the Actions run for 14 days.

Important: this build compiles the current repository source. It does not by itself add the separate Ahenk TTS GPU service or integrate Ahenk into the Android voice pipeline. That integration must be completed and tested separately before this APK can be described as the Ahenk-powered JARVIS build.
