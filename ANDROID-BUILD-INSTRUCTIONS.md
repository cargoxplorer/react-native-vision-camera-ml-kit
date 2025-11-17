# Android Build & Testing Instructions

## Build Status

✅ **Project Configuration:** Complete and valid
✅ **Dependencies:** Installed successfully
✅ **Build System:** Ready
⚠️ **Device:** Required for testing

---

## Prerequisites

To run the example app on Android, you need **one** of the following:

### Option 1: Physical Android Device (Recommended)

1. **Enable Developer Options:**
   - Go to Settings → About Phone
   - Tap "Build Number" 7 times
   - Developer Options will appear in Settings

2. **Enable USB Debugging:**
   - Settings → Developer Options
   - Enable "USB Debugging"

3. **Connect Device:**
   - Connect via USB cable
   - Allow USB debugging when prompted
   - Verify connection: `adb devices`

### Option 2: Android Emulator

1. **Install Android Studio:**
   - Download from https://developer.android.com/studio

2. **Create Virtual Device:**
   - Open Android Studio
   - Tools → Device Manager
   - Create Device → Choose device (e.g., Pixel 6)
   - Download system image (API 33 recommended)
   - Finish setup

3. **Start Emulator:**
   - Launch emulator from Device Manager
   - Or via command: `emulator -avd Pixel_6_API_33`

---

## Running the App

### Method 1: Using Expo (Recommended)

```bash
cd Q:/Dev/react-native-vision-camera-ml-kit/example

# Start the app (will launch on connected device/emulator)
npx expo run:android

# Or start Metro first, then build
npx expo start
# Press 'a' for Android
```

### Method 2: Using Yarn

```bash
cd Q:/Dev/react-native-vision-camera-ml-kit/example

# Install dependencies (already done)
npx yarn install

# Run on Android
npx yarn android
```

### Method 3: Direct Gradle Build

```bash
cd Q:/Dev/react-native-vision-camera-ml-kit/example/android

# Build and install
./gradlew assembleDebug
./gradlew installDebug

# Or combined
./gradlew installDebug
```

---

## Verification Steps

Once the app is running on your device:

### 1. Home Screen
- ✅ Verify all 3 feature cards appear
- ✅ Verify platform indicators (Android • iOS)
- ✅ Tap each card to navigate

### 2. Text Recognition
- ✅ Grant camera permission
- ✅ Point camera at text
- ✅ Verify text is detected and displayed
- ✅ Switch languages (Latin, Chinese, Japanese, Korean, Devanagari)
- ✅ Verify text changes based on language
- ✅ Check console for performance logs

### 3. Barcode Scanner
- ✅ Point camera at QR code
- ✅ Verify barcode is detected
- ✅ Check structured data (if WiFi/URL QR code)
- ✅ Toggle "QR Only" filter
- ✅ Verify filter works
- ✅ Try scanning multiple barcodes
- ✅ Check console for performance logs

### 4. Document Scanner
- ✅ Select scanner mode (BASE, BASE_WITH_FILTER, FULL)
- ✅ Tap "Scan Document"
- ✅ Verify ML Kit scanner UI launches
- ✅ Point camera at document
- ✅ Capture document
- ✅ Verify cropping/edge detection works
- ✅ Apply filters (if BASE_WITH_FILTER or FULL)
- ✅ Scan multiple pages if desired
- ✅ Verify pages and PDF appear in results
- ✅ Check console for logs

---

## Performance Testing

### Enable Performance Monitoring

The example app has debug logging enabled by default. Check the console/logcat for:

```
[MLKit:DEBUG] Creating text recognition plugin with options: {language: 'latin'}
[MLKit:DEBUG] ⏱️  Text recognition plugin initialization: 12.34ms
[MLKit:DEBUG] Processing frame: 1920x1080, rotation: 90
[MLKit:DEBUG] Text detected: 245 characters, 3 blocks
[MLKit:DEBUG] ⏱️  Text recognition frame processing: 15.67ms
```

### Performance Targets

- **Frame Processing:** <16ms (60fps)
- **Plugin Initialization:** <100ms
- **Static Processing:** <1000ms

### View Logs

**Android (Logcat):**
```bash
# Filter for ML Kit logs
adb logcat | grep "MLKit"

# Or use Android Studio Logcat window
```

---

## Troubleshooting

### No Device Found

**Error:** `No Android connected device found`

**Solution:**
1. Connect Android device via USB
2. Enable USB debugging
3. Verify with `adb devices`
4. Or start an Android emulator

### Build Errors

**Clean and rebuild:**
```bash
cd example
npx yarn clean:android
npx yarn android
```

### Camera Permission Denied

**Solution:**
- Uninstall app
- Reinstall
- Grant camera permission when prompted
- Or manually enable in device settings

### ML Kit Models Not Downloading

**Solution:**
- Ensure device has internet connection
- First use requires model download
- Check device storage space
- Check Google Play Services is up to date

### Slow Performance

**Check:**
1. Device specifications (older devices may be slower)
2. Console logs for processing times
3. Try reducing camera resolution
4. Try format filtering for barcodes
5. Check for other apps using camera

---

## Build Output Verification

### What Should Happen

1. **Expo Prebuild:**
   - Creates `android/` directory
   - Generates native Android project
   - Configures React Native

2. **Gradle Build:**
   - Compiles Kotlin code
   - Links ML Kit dependencies
   - Builds APK

3. **Installation:**
   - Installs APK on device/emulator
   - Launches app

4. **Runtime:**
   - App starts
   - Shows home screen
   - Features work as expected

### Expected Console Output

```
✔ Created native directory
✔ Updated package.json
✔ Prebuild finished
▸ Building...
▸ Running Gradle...
BUILD SUCCESSFUL in 45s
▸ Installing app...
▸ Starting app...
```

---

## Manual Testing Checklist

### Text Recognition
- [ ] Camera works
- [ ] Text detected in real-time
- [ ] Language switching works
- [ ] All 5 languages tested
- [ ] Performance <20ms per frame
- [ ] Results displayed correctly

### Barcode Scanning
- [ ] QR codes detected
- [ ] EAN-13 barcodes detected
- [ ] Filter toggle works
- [ ] Multiple barcodes detected
- [ ] Structured data extracted (WiFi, URL, Contact)
- [ ] Performance <20ms per frame

### Document Scanner
- [ ] Scan button launches UI
- [ ] Document detection works
- [ ] Edge detection accurate
- [ ] All 3 modes tested
- [ ] Multi-page works
- [ ] PDF generated
- [ ] Gallery import works
- [ ] Cancel handling works

---

## Next Steps After Testing

Once you've verified the Android app works:

1. **Document Issues:** Note any bugs or performance problems
2. **Continue to Phase 7:** Implement iOS support
3. **Optimize:** Based on real device performance
4. **Polish UI:** Improve example app if needed

---

## Current Status

✅ **Android implementation:** 100% complete
✅ **Example app:** Created and configured
✅ **Build system:** Working
⚠️ **Testing:** Requires Android device/emulator

**Ready for device testing!**
