# react-native-vision-camera-mlkit-plugin

[![npm version](https://img.shields.io/npm/v/react-native-vision-camera-mlkit-plugin.svg)](https://www.npmjs.com/package/react-native-vision-camera-mlkit-plugin)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

React Native Vision Camera frame processor plugin for Google ML Kit integration. Provides on-device text recognition, barcode scanning, and document scanning with high performance and comprehensive type safety.

## Features

- 📝 **Text Recognition v2** - On-device OCR with 5 script support (Latin, Chinese, Devanagari, Japanese, Korean)
- 🔡 **Vertical (stacked) text** - Reads columns of upright characters, such as container numbers painted on a door, that ML Kit cannot read on its own
- 📊 **Barcode Scanning** - Supports all 1D and 2D formats with structured data extraction (WiFi, Contact, URL, etc.)
- 📄 **Document Scanner** - Professional document digitization with ML-powered cleaning (Android only)
- ⚡ **High Performance** - Optimized for 60fps real-time processing
- 🎯 **Type Safe** - Comprehensive TypeScript definitions
- 🪝 **React Hooks** - Easy-to-use hooks API
- 📦 **Multiple APIs** - Frame processors, static image processing, and photo capture helpers
- 🐛 **Robust Error Handling** - Standardized error codes and handling
- 📊 **Performance Monitoring** - Built-in performance tracking

## Use Cases

### Barcode Scanning

- **Retail Inventory Management** - Instantly lookup products by scanning UPC/EAN barcodes. Use format filtering (`formats: [BarcodeFormat.EAN_13]`) to optimize performance for high-speed scanning in retail environments.

- **Package Tracking & Logistics** - Scan shipping labels and tracking barcodes (Code 128, Code 39) for warehouse operations. Supports multi-barcode detection (up to 10 per frame) for batch processing.

- **VIN Code Scanning** - Decode vehicle identification numbers from Code 39/Code 128 barcodes on automotive parts, registration documents, and dealer inventory for quick vehicle lookup and verification.

- **Smart WiFi Setup** - Scan QR codes to automatically extract and connect to WiFi networks. Structured data extraction provides SSID, password, and encryption type without manual parsing.

- **Digital Business Cards** - Import contacts by scanning QR codes on business cards. Structured vCard extraction automatically parses name, title, phone, email, and address fields.

### Text Recognition (OCR)

- **Receipt & Invoice Processing** - Extract merchant names, amounts, and dates from receipts for expense tracking. Hierarchical text structure (blocks → lines → elements) enables precise field extraction and layout analysis.

- **VIN Plate Recognition** - OCR vehicle identification numbers directly from vehicle plates for automotive applications, parking management, and logistics tracking. Bounding boxes enable accurate plate detection and cropping.

- **Document Digitization** - Convert printed documents to searchable digital text. Confidence scores allow quality filtering to ensure accurate text extraction, while multi-language support handles international documents.

- **License Plate Scanning** - Real-time vehicle tracking for parking lots, toll gates, and warehouse entry/exit. Use bounding boxes for precise plate location and confidence scores for validation.

- **Multilingual Product Labels** - Read ingredient lists, instructions, and product information in 5 language scripts (Latin, Chinese, Japanese, Korean, Devanagari). Perfect for international retail and e-commerce applications.

- **Shipping Container Numbers** - Read the ISO 6346 identifier painted down a container door as a vertical stack of upright characters. Set `textLayout: 'auto'` and the library detects the column and reads it as an ordinary line; see [Vertical (stacked) text](#vertical-stacked-text).

## Installation

```bash
yarn add react-native-vision-camera-mlkit-plugin react-native-vision-camera react-native-worklets-core
```

### iOS Setup

```bash
cd ios && pod install
```

Minimum iOS version: **16.0** (required by ML Kit)

### Android Setup

No additional steps required. ML Kit models will be downloaded automatically on first use.

Minimum Android SDK: **21**

## Quick Start

### Text Recognition

```typescript
import { Camera } from 'react-native-vision-camera';
import { useTextRecognition } from 'react-native-vision-camera-ml-kit';
import { useFrameProcessor } from 'react-native-vision-camera';
import { Worklets } from 'react-native-worklets-core';

function TextRecognitionExample() {
  const { scanText } = useTextRecognition({ language: 'latin' });

  const onTextDetected = Worklets.createRunOnJS((text: string) => {
    console.log('Detected:', text);
  });

  const frameProcessor = useFrameProcessor((frame) => {
    'worklet';
    const result = scanText(frame);
    if (result?.text) {
      onTextDetected(result.text);
    }
  }, [scanText]);

  return <Camera frameProcessor={frameProcessor} />;
}
```

### Barcode Scanning

```typescript
import { useBarcodeScanner, BarcodeFormat } from 'react-native-vision-camera-ml-kit';

function BarcodeScannerExample() {
  const { scanBarcode } = useBarcodeScanner({
    formats: [BarcodeFormat.QR_CODE, BarcodeFormat.EAN_13]
  });

  const frameProcessor = useFrameProcessor((frame) => {
    'worklet';
    const result = scanBarcode(frame);
    if (result?.barcodes.length > 0) {
      runOnJS(handleBarcodes)(result.barcodes);
    }
  }, [scanBarcode]);

  return <Camera frameProcessor={frameProcessor} />;
}
```

### Document Scanning (Android Only)

```typescript
import { launchDocumentScanner, DocumentScannerMode } from 'react-native-vision-camera-ml-kit';

async function scanDocument() {
  try {
    const result = await launchDocumentScanner({
      mode: DocumentScannerMode.FULL,
      pageLimit: 5,
      galleryImportEnabled: true,
    });

    if (result) {
      console.log(`Scanned ${result.pageCount} pages`);
      console.log('PDF:', result.pdfUri);
    }
  } catch (error) {
    console.error('Scan error:', error);
  }
}
```

## API Reference

### Text Recognition v2

#### Frame Processor

```typescript
import { createTextRecognitionPlugin, useTextRecognition } from 'react-native-vision-camera-ml-kit';

// Create plugin
const plugin = createTextRecognitionPlugin({ language: 'chinese' });

// Or use hook
const { scanText } = useTextRecognition({ language: 'japanese' });
```

**Supported Languages:**
- `latin` - Latin script
- `chinese` - Chinese script
- `devanagari` - Devanagari script
- `japanese` - Japanese script
- `korean` - Korean script

**Result Structure:**
```typescript
{
  text: string;
  blocks: TextBlock[];
}
```

Each `TextBlock` contains hierarchical structure: **Blocks → Lines → Elements → Symbols**

#### Static Image Recognition

```typescript
import { recognizeTextFromImage } from 'react-native-vision-camera-ml-kit';

const result = await recognizeTextFromImage({
  uri: 'file:///path/to/image.jpg',
  language: 'latin',
});
```

#### Photo Capture Helper

```typescript
import { captureAndRecognizeText } from 'react-native-vision-camera-ml-kit';

const result = await captureAndRecognizeText(cameraRef.current, {
  language: 'korean',
  flash: 'auto',
});
```

#### Vertical (stacked) text

Container numbers are painted down the door as a column of upright characters, which ML Kit
cannot read ([googlesamples/mlkit#254](https://github.com/googlesamples/mlkit/issues/254)).
`textLayout` makes the library find the column itself, re-lay the glyphs as a horizontal strip
and read that with ML Kit. Offline, no custom model.

```typescript
const { scanText } = useTextRecognition({ language: 'latin', textLayout: 'auto' });
const result = await recognizeTextFromImage({ uri, textLayout: 'stacked' });
```

| Value | Behaviour |
|-------|-----------|
| `horizontal` *(default)* | Unchanged ML Kit pass. |
| `stacked` | Normal pass plus the stacked pass on every frame. |
| `auto` | Stacked pass only when the horizontal pass found no lines. A container door always carries horizontal labels, so use `stacked` for container numbers. |

A detected column is appended to `blocks` as one `TextBlock` with `stacked: true` and a single
line. Its `text` is what ML Kit read from the strip (e.g. `CA AU9314819`); `frame` and element
frames are in the coordinates of the original image, one element per glyph. `result.text` gets
the column on its own line.

Limits: Latin only; assumes bright-on-dark or dark-on-light paint; a column needs 8+ glyphs of
roughly 12 px height in a 1080p frame (still images are searched at a 1200 px long side). On
camera frames the column search reads the luma plane directly and runs alongside the horizontal
ML Kit pass; candidate columns are ranked and at most six go through ML Kit (~50 ms each), so
`stacked` typically adds one strip recognition on top of the horizontal pass. Throttle with
`runAtTargetFps` or use `async`. Still images retry at 90/270/180 degrees when the upright pass
finds nothing.

`async: true` (frame processor only) copies the frame, returns immediately and recognizes on a
background thread; the next call that finds a finished result returns it, so poll at 2-3 fps.
The camera buffer is held for the copy only (~15 ms), which keeps the preview smooth while ML
Kit runs.

---

### Barcode Scanning

#### Frame Processor

```typescript
import { createBarcodeScannerPlugin, useBarcodeScanner } from 'react-native-vision-camera-ml-kit';

// Scan all formats
const { scanBarcode } = useBarcodeScanner();

// Scan specific formats
const { scanBarcode } = useBarcodeScanner({
  formats: [BarcodeFormat.QR_CODE]
});
```

**Supported Formats:**

**1D (Linear):** Codabar, Code 39, Code 93, Code 128, EAN-8, EAN-13, ITF, UPC-A, UPC-E

**2D:** Aztec, Data Matrix, PDF417, QR Code

**Structured Data Extraction:**
- WiFi credentials (SSID, password, encryption)
- Contact information (vCard)
- URLs
- Email addresses
- Phone numbers
- SMS
- Geographic coordinates
- Calendar events
- Driver licenses (AAMVA standard)

**Result Structure:**
```typescript
{
  barcodes: Barcode[]; // Up to 10 per frame
}
```

#### Scan Region (targeted scanning)

Restrict decoding to a region of the frame — barcodes outside it are ignored
even if perfectly readable. The frame is cropped before ML Kit processing, so
smaller regions also decode faster.

```typescript
// Static region, relative to the upright (display-oriented) frame
const { scanBarcode } = useBarcodeScanner({
  scanRegion: { left: 0.15, top: 0.3, width: 0.7, height: 0.4 },
});

// Region measured in preview-view coordinates (e.g. an overlay viewfinder).
// Pass the view size and the native side maps it through the cover crop.
// Per-call regions override the plugin-level one and don't recreate the plugin.
const frameProcessor = useFrameProcessor((frame) => {
  'worklet';
  const result = scanBarcode(frame, {
    scanRegion: {
      left: 0.15, top: 0.325, width: 0.7, height: 0.35,
      viewportWidth: 400, viewportHeight: 800,
    },
  });
}, [scanBarcode]);
```

All values are normalized fractions (0..1). Detected barcode coordinates stay
relative to the full frame. Implemented on both Android and iOS.

#### Static Image Scanning

```typescript
import { scanBarcodeFromImage } from 'react-native-vision-camera-ml-kit';

const result = await scanBarcodeFromImage({
  uri: 'file:///path/to/image.jpg',
  formats: [BarcodeFormat.QR_CODE],
});
```

#### Photo Capture Helper

```typescript
import { captureAndScanBarcode } from 'react-native-vision-camera-ml-kit';

const result = await captureAndScanBarcode(cameraRef.current, {
  formats: [BarcodeFormat.QR_CODE],
  flash: 'auto',
});
```

---

### Document Scanner (Android Only)

#### UI-Based Scanner (Recommended)

```typescript
import { launchDocumentScanner, DocumentScannerMode } from 'react-native-vision-camera-ml-kit';

const result = await launchDocumentScanner({
  mode: DocumentScannerMode.FULL,
  pageLimit: 10,
  galleryImportEnabled: true,
});
```

**Scanner Modes:**
- `BASE` - Crop, rotate, reorder
- `BASE_WITH_FILTER` - BASE + image filters
- `FULL` - BASE + ML-powered cleaning (default)

**Result Structure:**
```typescript
{
  pages: DocumentPage[];
  pageCount: number;
  pdfUri?: string; // Combined PDF
}
```

#### Frame Processor (Experimental)

```typescript
import { createDocumentScannerPlugin, useDocumentScanner } from 'react-native-vision-camera-ml-kit';

const { scanDocument } = useDocumentScanner({
  mode: DocumentScannerMode.FULL,
});
```

**Note:** For best results, use `launchDocumentScanner()` which provides the professional Google UI.

---

## Utilities

### Logger

```typescript
import { Logger, LogLevel } from 'react-native-vision-camera-ml-kit';

// Set log level
Logger.setLogLevel(LogLevel.DEBUG); // DEBUG, INFO, WARN, ERROR, NONE

// Log messages
Logger.debug('Debug message');
Logger.info('Info message');
Logger.warn('Warning message');
Logger.error('Error message', error);
Logger.performance('Operation', durationMs);
```

### Performance Monitoring

```typescript
import { performanceMonitor } from 'react-native-vision-camera-ml-kit';

// Enable monitoring (DEBUG level)
performanceMonitor.enable();

// Get statistics
const stats = performanceMonitor.getStats('Text recognition frame processing');
console.log(`Average: ${stats.avg}ms, P95: ${stats.p95}ms`);

// Log summary
performanceMonitor.logSummary();
```

### Error Handling

```typescript
import { ErrorCode, MLKitError, isCancellationError } from 'react-native-vision-camera-ml-kit';

try {
  const result = await launchDocumentScanner();
} catch (error) {
  if (isCancellationError(error)) {
    console.log('User cancelled');
  } else if (error instanceof MLKitError) {
    console.error(`[${error.code}] ${error.message}`);
  }
}
```

**Error Codes:**
- `PLUGIN_INIT_FAILED` - Plugin initialization failed
- `MODULE_NOT_FOUND` - Native module not found
- `FRAME_PROCESSING_ERROR` - Frame processing error
- `IMAGE_PROCESSING_ERROR` - Image processing error
- `IMAGE_LOAD_ERROR` - Image load error
- `PLATFORM_NOT_SUPPORTED` - Platform not supported
- `SCANNER_BUSY` - Scanner already in use
- `SCAN_CANCELLED` - User cancelled scan
- `SCAN_FAILED` - Scan failed
- `NO_ACTIVITY` - Activity not available
- `INVALID_ARGUMENT` - Invalid argument
- `UNEXPECTED_ERROR` - Unexpected error

---

## Performance

### Target Performance
- **Frame Processing:** <16ms (60fps)
- **Plugin Initialization:** <100ms
- **Static Processing:** <1000ms

### Optimization Tips

1. **Use format filtering for barcodes:**
   ```typescript
   useBarcodeScanner({ formats: [BarcodeFormat.QR_CODE] })
   ```

2. **Set appropriate log level in production:**
   ```typescript
   Logger.setLogLevel(LogLevel.WARN);
   ```

3. **Enable performance monitoring in development:**
   ```typescript
   performanceMonitor.enable();
   ```

4. **Use static APIs for non-realtime processing:**
   ```typescript
   await recognizeTextFromImage({ uri });
   ```

---

## Platform Support

| Feature | Android | iOS |
|---------|---------|-----|
| Text Recognition v2 | ✅ | ✅ |
| Vertical (stacked) text | ✅ | ✅ |
| Barcode Scanning | ✅ | ✅ |
| Document Scanner | ✅ | ❌* |

\* Document Scanner is not supported on iOS by Google ML Kit

---

## Examples

See the [example](./example) directory for a complete Expo app demonstrating all features.

---

## Troubleshooting

### Plugin Initialization Failed

Ensure you have installed and linked the library correctly:

```bash
# Reinstall dependencies
yarn install

# iOS
cd ios && pod install

# Clean and rebuild
yarn clean
```

### Slow Frame Processing

1. Check performance logs:
   ```typescript
   Logger.setLogLevel(LogLevel.DEBUG);
   ```

2. Reduce processing load:
   - Use format filtering for barcodes
   - Lower camera resolution
   - Process every N frames instead of every frame

3. Profile with performance monitor:
   ```typescript
   performanceMonitor.enable();
   performanceMonitor.logSummary();
   ```

### Document Scanner Not Working on iOS

Document Scanner is Android-only. Use Text Recognition or Barcode Scanning for iOS.

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

---

## License

MIT

---

## Credits

This library integrates [Google ML Kit](https://developers.google.com/ml-kit) with [React Native Vision Camera](https://github.com/mrousavy/react-native-vision-camera).

---

## Related Projects

- [react-native-vision-camera](https://github.com/mrousavy/react-native-vision-camera) - The powerful camera library this plugin is built for
- [react-native-worklets-core](https://github.com/margelo/react-native-worklets-core) - Enables high-performance worklets
