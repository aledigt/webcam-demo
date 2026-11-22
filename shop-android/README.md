# eVision Camera Shop — Android APK

WebView-обёртка PWA [https://shop.evision.tech](https://shop.evision.tech) («eVision Camera Shop», страница отдаёт версию 1.4.4). Каталог `android/` и webcam-demo не изменяются: у этого приложения свой пакет `io.github.aledigt.evisionshop`.

Сайт не публикует `/.well-known/assetlinks.json` (ответ 404), поэтому Trusted Web Activity и привязка к подписи сайта недоступны без правки продакшена. Релизная подпись в репозиторий не кладётся. Собирается устанавливаемый debug-APK.

## Сборка

Нужны JDK 17+ и Android SDK (compileSdk 34, build-tools 34.0.0). `ANDROID_HOME` должен указывать на SDK.

```bash
cd shop-android
chmod +x gradlew
./gradlew --no-daemon testDebugUnitTest assembleDebug
```

APK: `shop-android/app/build/outputs/apk/debug/app-debug.apk`.

Проверка подписи и состава:

```bash
APK=shop-android/app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_HOME/build-tools/34.0.0/apksigner" verify --print-certs "$APK"
"$ANDROID_HOME/build-tools/34.0.0/aapt" dump permissions "$APK"
"$ANDROID_HOME/build-tools/34.0.0/aapt" dump badging "$APK"
```

GitHub Actions (`.github/workflows/shop-android.yml`) запускает те же тесты и `assembleDebug`, проверяет APK и загружает артефакт `evision-shop-apk`. Релиз webcam-demo workflow не публикует и секреты подписи не использует.

Debug-ключ создаёт сам Android Gradle на машине сборки и в git не попадает. Сборки с разных машин нельзя поставить друг поверх друга: удалите предыдущую установку. Это не подпись для магазина приложений.

Установка вручную: разрешите установку из неизвестных источников и откройте APK. Через adb, если подпись совпадает с уже установленной:

```bash
adb install -r shop-android/app/build/outputs/apk/debug/app-debug.apk
```

## Поведение

- Стартовый адрес — `https://shop.evision.tech/`. В WebView остаются только `https://shop.evision.tech` и `https://cloud.evision.tech` (кнопка подключения к eVision Cloud и возврат по `redirect_uri`).
- Другой HTTPS (например, ссылка на навык Яндекса в облаке) открывается во внешнем браузере.
- `http`, `javascript:`, `file:`, `content:` и `intent:` в WebView не загружаются. Cleartext выключен, доверяются системные CA, ошибки TLS не подавляются, смешанный контент запрещён, сторонние cookie выключены, JavaScript-мост не добавляется.
- Назад: если у WebView есть история (типично возврат из облака в магазин), выполняется `goBack()`. С корневой страницы магазина назад закрывает приложение.
- Ориентация — ландшафт, как в манифесте PWA. Поле номера заказа поднимается вместе с клавиатурой.
- Камера запрашивается до загрузки страницы: превью (`getUserMedia`, только видео) и QR (`html5-qrcode`) используют её. Странице выдаётся только `VIDEO_CAPTURE`. Микрофон не запрашивается: текущая страница включает микрофон в `getUserMedia` как `audio: false` и скрывает кнопку микрофона.
- `localStorage` включён (идентификатор устройства и настройки). Service worker сайта не отключается.
- Выбор файла на стороне облака (CSV) идёт через системный диалог. Скачивание по HTTPS с этих двух хостов кладётся в «Загрузки».
- В Android WebView обычно нет `webkitSpeechRecognition`, а страница создаёт его безусловно. На документ магазина до скриптов страницы ставится локальная заглушка, чтобы камера и остальной скрипт не падали. Голосовые команды при этом не работают. На WebView 105+ заглушка ставится через `DOCUMENT_START_SCRIPT`. На более старом WebView тот же скрипт вставляется в HTML документа магазина; проверка сертификата не отключается.

Полноценная проверка камеры и QR возможна только на устройстве с камерой: эмулятор без камеры приложение ставит (`camera.any` не обязательна), но съёмка не стартует.
