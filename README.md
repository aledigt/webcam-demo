# Webcam Demo

Простое демо работы с веб-камерой в браузере на чистом HTML и JavaScript (`navigator.mediaDevices.getUserMedia`).

Возможности: включение и выключение камеры, выбор камеры, зеркальный режим, CSS-фильтры, снимки с сохранением в PNG.

Живая версия: https://aledigt.github.io/webcam-demo/

## Android APK

Download: https://github.com/aledigt/webcam-demo/releases/latest/download/webcam-demo.apk

Native Android app (Kotlin + CameraX, Material 3) in `android/`: preview, front/back switch, live colour filters, photo capture to Pictures/WebcamDemo, in-app gallery of recent shots.
Built and release-signed by GitHub Actions (`.github/workflows/android.yml`). Installing requires allowing installs from unknown sources.
If you installed v1 (debug-signed WebView build), uninstall it once before installing v2.
