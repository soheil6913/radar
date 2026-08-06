# Gold Radar X20 (com.soheil.federal)

**Gold Radar X20** is an advanced 3D ground scanner, magnetometer radar, and cavity/metal visualization system for Android.

## 🚀 Features
- **3D Ground Scan Visualizer**: Real-time Interactive 3D mesh rendering (WebGL / Three.js & Jetpack Compose Canvas) with heightmap color contours.
- **Hardware Integration**: Support for USB-Serial (CH340/FTDI/CP2102) and Bluetooth magnetometers (Gold Radar X20, FMG3, FLC100, HMC5883L).
- **AI Target Analysis**: Automated Gemini AI target depth, mineralization, and cavity classification.
- **Export & Share**: Share scan reports as formatted text, CSV (Surfer/Excel compatible), or JSON.

---

## 🛠 Project Configuration
- **Application ID**: `com.soheil.federal`
- **Application Name**: `Gold Radar X20`
- **Minimum SDK**: `24` (Android 7.0)
- **Target SDK**: `34` / `36`
- **Language**: Kotlin + Jetpack Compose

---

## 🔑 Release Keystore Setup
The release keystore is included in the project root as `release.jks`.
- **Keystore File**: `release.jks`
- **Keystore Password**: `123456`
- **Key Alias**: `release`
- **Key Password**: `123456`

To generate a Base64 string for GitHub Actions Secret `KEYSTORE_BASE64`:
```bash
base64 -w 0 release.jks > keystore_base64.txt
```

---

## 🤖 GitHub Actions Workflow
The workflow file `.github/workflows/build-apk.yml` automatically compiles and uploads a signed Release APK on every push to `main` / `master`.

### GitHub Secrets (Optional):
1. `KEYSTORE_BASE64` - Base64 encoded string of `release.jks`
2. `STORE_PASSWORD` - `123456`
3. `KEY_ALIAS` - `release`
4. `KEY_PASSWORD` - `123456`

---

## 📦 Building Locally

```bash
# Clone repository
git clone <repository_url>
cd gold-radar-x20

# Grant execute permissions
chmod +x gradlew

# Build Release APK
./gradlew assembleRelease

# The output APK is saved to:
# app/build/outputs/apk/release/app-release.apk
```
