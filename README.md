# Photto

Android için **fotoğraf piksellerinden sayı üreten** uygulama.

Çektiğiniz fotoğrafın piksellerinden 6 temel metrik hesaplar ve bunları
seçtiğiniz aralığa ölçekleyerek size **N adet sayı** verir.

## Hesaplama

Fotoğrafın tüm pikselleri taranır (büyük görsellerde örneklenir) ve şu 6 temel
metrik hesaplanır — her biri `0-255` aralığında:

| # | Metrik | Açıklama |
|---|--------|----------|
| 1 | Kırmızı | Ortalama R kanalı |
| 2 | Yeşil   | Ortalama G kanalı |
| 3 | Mavi    | Ortalama B kanalı |
| 4 | Parlaklık | BT.601 luminance: `0.299·R + 0.587·G + 0.114·B` |
| 5 | Doygunluk | HSV S kanalı ortalaması |
| 6 | Kontrast | Parlaklığın standart sapması |

Bu 6 değer, kullanıcının seçtiği `[min, max]` aralığına lineer olarak
ölçeklenir. Eğer sayı adedi 6'dan fazla seçilirse metrikler döngüsel olarak
tekrar eder (modül 6).

## Varsayılan ayarlar

- Sayı adedi: **6**
- Minimum değer: **0**
- Maksimum değer: **60**

Tümü Ayarlar ekranından değiştirilebilir.

## Teknik özellikler

- **Dil:** Kotlin 1.9.24
- **Build:** Gradle 8.7, AGP 8.5.2
- **Min SDK:** 24 (Android 7.0) — cihazların %98+'i
- **Target SDK:** 34 (Android 14)
- **Kamera:** CameraX (önizleme + çekim)
- **UI:** Material 3 (Light/Dark otomatik)
- **Paket:** `com.mscsi.photto`

## Ekranlar

### Ana Ekran
- Canlı kamera önizlemesi
- **Çek** butonu — fotoğrafı yakalar, pikselleri işler
- Sonuç: yatay kaydırılabilir sayı kartları + detaylı istatistik
- **Ayarlar** butonu

### Ayarlar Ekranı
- **Sayı Adedi** (1–18 arası)
- **Minimum Değer** (0–255 arası)
- **Maksimum Değer** (0–255 arası)
- Kaydet / Sıfırla

## GitHub Actions ile derleme

Repo'yu GitHub'a bastığınızda `.github/workflows/build.yml` otomatik olarak
çalışır:

1. JDK 17 kurar
2. Android SDK + build-tools 34 kurar
3. Gradle 8.7 kurar
4. `gradle wrapper` ile wrapper'ı üretir (jar repo'da tutulmaz)
5. `assembleDebug` ve `assembleRelease` çalıştırır
6. Her iki APK'yı artifact olarak yükler

**APK indirme:**
GitHub repo sayfasında **Actions** sekmesi → ilgili workflow run → en altta
**Artifacts** bölümünden `photto-debug-apk` veya `photto-release-apk` indirilebilir.

> ⚠️ Release APK, debug keystore ile imzalanır (CI'da test edilebilir). Play
> Store'a yüklemek için kendi keystore dosyanızı kullanın.

## Lokal derleme

Repo'yu klonladıktan sonra:

```bash
# 1. Android SDK'nın yüklü olduğundan emin olun (ANDROID_HOME)
# 2. local.properties'i oluşturun:
echo "sdk.dir=$HOME/Android/Sdk" > local.properties

# 3. Wrapper'ı üretin (Gradle sistemde yüklü olmalı):
gradle wrapper --gradle-version 8.7

# 4. Derleyin:
./gradlew assembleDebug

# 5. APK konumu:
# app/build/outputs/apk/debug/app-debug.apk
```

Alternatif: Android Studio ile açın, SDK'yı seçin, **Run**'a basın.

## Proje yapısı

```
photto-android/
├── .github/workflows/build.yml     # GitHub Actions workflow
├── app/
│   ├── build.gradle.kts            # Uygulama modülü
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/mscsi/photto/
│       │   ├── MainActivity.kt        # Kamera + UI
│       │   ├── SettingsActivity.kt    # Ayarlar ekranı
│       │   └── data/
│       │       ├── PixelCalculator.kt # Piksel hesaplama çekirdeği
│       │       └── SettingsManager.kt # Ayar yönetimi
│       └── res/
│           ├── layout/                # activity_main, activity_settings, item_number
│           ├── values/                # strings, colors, themes (TR)
│           ├── values-night/          # Karanlık tema
│           ├── drawable/              # Vektör ikonlar + chip arka planı
│           ├── mipmap-*/              # Launcher ikonları (PNG)
│           └── mipmap-anydpi-v26/     # Adaptive icon (API 26+)
├── build.gradle.kts                  # Root build
├── settings.gradle.kts
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
└── README.md
```

## İzinler

- `android.permission.CAMERA` — fotoğraf çekmek için (Runtime permission)
- `<uses-feature android.hardware.camera.any>` — kamera gereklidir

Başka hiçbir izin istenmez. İnternet, depolama, konum vb. yoktur.

## Lisans

MIT
