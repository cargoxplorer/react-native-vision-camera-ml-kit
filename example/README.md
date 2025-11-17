# ML Kit Example App

Example application demonstrating all features of `react-native-vision-camera-ml-kit`.

## Features Demonstrated

1. **Text Recognition v2**
   - Real-time camera OCR
   - 5 language scripts (Latin, Chinese, Devanagari, Japanese, Korean)
   - Live language switching
   - Display of detected text blocks and lines

2. **Barcode Scanning**
   - Real-time barcode detection
   - All 1D and 2D formats
   - Format filtering (QR-only mode)
   - Structured data display (WiFi, URLs, Contacts)
   - Multi-barcode detection

3. **Document Scanner** (Android Only)
   - ML Kit Document Scanner UI
   - Three scanner modes (BASE, BASE_WITH_FILTER, FULL)
   - Multi-page scanning
   - PDF generation
   - Gallery import
   - Preview of scanned pages

## Getting Started

### Prerequisites

- Node.js >= 18
- Yarn
- Android Studio (for Android)
- Xcode (for iOS, macOS only)

### Installation

```bash
# From the example directory
yarn install

# iOS only
cd ios && pod install && cd ..
```

### Running the App

```bash
# Start Metro bundler
yarn start

# Run on Android
yarn android

# Run on iOS (macOS only)
yarn ios
```

## App Structure

```
example/
├── app/
│   ├── _layout.tsx              # Root layout with navigation
│   ├── index.tsx                # Home screen with feature list
│   ├── text-recognition.tsx    # Text Recognition demo
│   ├── barcode-scanner.tsx     # Barcode Scanner demo
│   └── document-scanner.tsx    # Document Scanner demo
├── package.json
├── app.json                     # Expo configuration
├── babel.config.js
└── tsconfig.json
```

## Screens

### Home Screen

Lists all available features with navigation buttons.

### Text Recognition Screen

- Camera preview with real-time text detection
- Language selector (5 scripts)
- Live display of detected text
- Block/line count statistics

### Barcode Scanner Screen

- Camera preview with real-time barcode detection
- Toggle between "All Formats" and "QR Only"
- Display of all detected barcodes
- Structured data extraction (WiFi, URLs, Contacts, etc.)
- Support for up to 10 barcodes per frame

### Document Scanner Screen

- Scanner mode selector (BASE, BASE_WITH_FILTER, FULL)
- Launch button for ML Kit scanner UI
- Display of scanned pages with preview images
- PDF generation info
- Android-only notice for iOS users

## Permissions

The app requires camera permissions:

**Android:** Declared in app.json, requested at runtime

**iOS:** Configured via expo-vision-camera plugin

## Performance

All screens include:
- Debug logging (enabled by default)
- Performance metrics (visible in console)
- Efficient frame processing

To monitor performance:
```typescript
import { performanceMonitor, Logger, LogLevel } from 'react-native-vision-camera-ml-kit';

Logger.setLogLevel(LogLevel.DEBUG);
performanceMonitor.enable();

// After using features
performanceMonitor.logSummary();
```

## Troubleshooting

### Android Build Issues

```bash
# Clean build
yarn clean:android
cd android && ./gradlew clean && cd ..

# Rebuild
yarn android
```

### iOS Build Issues

```bash
# Clean build
yarn clean:ios

# Reinstall pods
cd ios && pod deintegrate && pod install && cd ..

# Rebuild
yarn ios
```

### Camera Not Working

1. Check permissions in device settings
2. Restart the app
3. Check console for errors

## Testing on Device

### Android

1. Enable USB debugging on your device
2. Connect via USB
3. Run `yarn android`
4. Test all features:
   - Text Recognition with different languages
   - Barcode scanning with QR codes and other formats
   - Document scanning with multi-page documents

### iOS

1. Connect iOS device
2. Run `yarn ios`
3. Test Text Recognition and Barcode Scanning
4. Document Scanner will show "not supported" message

## License

MIT
