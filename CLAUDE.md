# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

React Native Vision Camera frame processor plugin for Google ML Kit integration with three core features:

1. **Text Recognition v2** - On-device OCR with 5 language scripts (Latin, Chinese, Devanagari, Japanese, Korean)
2. **Barcode Scanning** - 1D/2D barcode detection with structured data extraction
3. **Document Scanner** - ML-powered document digitization (Android only)

**Key Architecture:**
- **Frame processors** for real-time camera processing (60fps target)
- **Static image APIs** for processing saved images
- **Photo capture helpers** for one-shot capture + process workflows
- **Custom logger** with configurable log levels for performance monitoring
- **Native implementations** in Kotlin (Android) and Swift (iOS - pending)
- **Integration** with react-native-vision-camera v4 and react-native-worklets-core

## Development Commands

### Library Development
```bash
# Install dependencies (MUST use Yarn - this is a workspace monorepo)
yarn

# Build the library (TypeScript → CommonJS/ES Modules/Type defs)
yarn prepare
# Or directly: npx react-native-builder-bob build

# Type checking
yarn typecheck

# Linting
yarn lint
yarn lint --fix

# Run tests
yarn test
yarn test:coverage

# Clean build artifacts
yarn clean
```

### Example App (Android)
```bash
cd example

# Install dependencies
yarn

# Start Metro bundler (with cache reset if needed)
yarn start
yarn start --reset-cache

# Run on Android
yarn android

# Clean Android build
yarn clean:android
```

### Native Development (Android)

**Important:** After modifying Kotlin files, you must rebuild:

```bash
# From example/android directory
./gradlew clean
./gradlew :react-native-vision-camera-ml-kit:compileDebugKotlin

# Or full build
./gradlew assembleDebug
```

**Common issue:** If Metro can't resolve the module, rebuild the library:
```bash
# From root directory
npx react-native-builder-bob build
cd example && yarn start --reset-cache
```

## Architecture

### Code Organization

```
src/
├── index.ts                      # Main entry point
├── types.ts                      # Comprehensive TypeScript types
├── constants.ts                  # Error messages and config
├── utils/
│   ├── Logger.ts                 # Custom logger with log levels
│   ├── errorHandling.ts          # MLKitError class and error codes
│   └── performance.ts            # Performance monitoring utilities
├── textRecognition.ts            # Text recognition frame processor + hook
├── staticTextRecognition.ts      # Static image text recognition
├── captureAndRecognizeText.ts    # Photo capture helper
├── barcodeScanning.ts            # Barcode frame processor + hook
├── staticBarcodeScanning.ts      # Static image barcode scanning
├── captureAndScanBarcode.ts      # Photo capture helper
├── documentScanner.ts            # Document scanner frame processor (experimental)
├── launchDocumentScanner.ts      # UI-based document scanner (recommended)
└── __tests__/                    # Jest tests (TDD approach)

android/src/main/java/com/rnvisioncameramlkit/
├── RNVisionCameraMLKitPackage.kt         # Package registration
├── TextRecognitionPlugin.kt              # Text recognition frame processor
├── StaticTextRecognitionModule.kt        # Static text recognition
├── BarcodeScanningPlugin.kt              # Barcode scanning frame processor
├── StaticBarcodeScannerModule.kt         # Static barcode scanning
├── DocumentScannerModule.kt              # UI-based document scanner
└── utils/
    └── Logger.kt                         # Native logger
```

### Frame Processor Architecture

Vision Camera's plugin system with worklets:
1. Plugins initialized via `VisionCameraProxy.initFrameProcessorPlugin(name, options)`
2. JavaScript creates plugin instances (e.g., `useTextRecognition()` hook)
3. Native plugins process camera frames in real-time on worklet thread
4. Results returned as worklet-compatible objects (must be serializable)
5. All frame processor functions MUST include `'worklet'` directive

**Key pattern:**
```typescript
// JavaScript (src/textRecognition.ts)
const plugin = VisionCameraProxy.initFrameProcessorPlugin('scanTextV2', options);
const scanText = (frame: Frame) => {
  'worklet'; // REQUIRED
  return plugin?.call(frame) as TextRecognitionResult | null;
};

// Native (TextRecognitionPlugin.kt)
class TextRecognitionPlugin : FrameProcessorPlugin() {
  override fun callback(frame: Frame, arguments: Map<String, Any>?): Any? {
    // Process frame and return serializable result
  }
}
```

### API Patterns

Each feature provides **three API surfaces**:

1. **Frame Processor** - Real-time camera processing
   - Plugin creation: `createTextRecognitionPlugin(options)`
   - React hook: `useTextRecognition(options)`
   - Returns function: `(frame: Frame) => Result | null`

2. **Static Image API** - Process saved images
   - Function: `recognizeTextFromImage(options)`
   - Returns: `Promise<Result>`

3. **Photo Capture Helper** - One-shot capture + process
   - Function: `captureAndRecognizeText(cameraRef, options)`
   - Returns: `Promise<Result>`

### Build System

- **TypeScript → JavaScript**: react-native-builder-bob
  - Outputs: `lib/commonjs/`, `lib/module/`, `lib/typescript/`
  - Configured in `package.json` → `react-native-builder-bob` section
- **Android Native**: Gradle with Kotlin 1.9.25
- **Test Runner**: Jest with react-native preset
- **Package Manager**: Yarn (workspace monorepo)

## ML Kit Integration

### Android Dependencies (android/build.gradle)

```gradle
// Text Recognition v2 (5 language scripts)
implementation 'com.google.android.gms:play-services-mlkit-text-recognition:19.0.1'
implementation 'com.google.android.gms:play-services-mlkit-text-recognition-chinese:16.0.1'
implementation 'com.google.android.gms:play-services-mlkit-text-recognition-devanagari:16.0.1'
implementation 'com.google.android.gms:play-services-mlkit-text-recognition-japanese:16.0.1'
implementation 'com.google.android.gms:play-services-mlkit-text-recognition-korean:16.0.1'

// Document Scanner (Android only - uses UI launcher)
implementation 'com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1'

// Barcode Scanning
implementation 'com.google.mlkit:barcode-scanning:17.3.0'
```

**Critical Android API details:**
- Document Scanner method is `getStartScanIntent(activity)` not `getStartIntent()`
- Text recognizer classes: `TextRecognition.getClient(options)` with script-specific options
- All ML Kit tasks return `Task<T>` - use `Tasks.await(task)` for synchronous processing

### iOS Dependencies (Future - not yet implemented)

```ruby
s.dependency "GoogleMLKit/TextRecognition", '>= 8.0.0'
s.dependency "GoogleMLKit/BarcodeScanning", '>= 7.0.0'
# Note: Document Scanner not available on iOS (Google limitation)
```

## Testing Strategy

### Test-Driven Development (TDD)

**ALWAYS follow this approach:**
1. Write unit tests for the feature FIRST (in `src/__tests__/`)
2. Run tests to verify they fail: `yarn test`
3. Implement the minimum code to pass tests
4. Refactor and optimize
5. Ensure coverage remains >80%: `yarn test:coverage`

### Test Organization

- **Unit tests**: `src/__tests__/**/*.test.ts`
- **Mocks**: `src/__tests__/__mocks__/` (VisionCameraProxy, NativeModules)
- **Setup**: `src/__tests__/setup.ts`
- **Coverage threshold**: 80% for all metrics (branches, functions, lines, statements)

### Running Tests

```bash
# All tests
yarn test

# With coverage
yarn test:coverage

# Watch mode (for development)
yarn test --watch

# Specific test file
yarn test textRecognition.test.ts
```

## Code Conventions

### TypeScript

- **Strict mode enabled**: All compiler strictness flags on
- **No unused code**: Variables, parameters, or imports
- **Explicit returns**: No implicit return types
- **Worklet directive**: MUST be included in all frame processor functions
- **Type exports**: Use `export type` for type-only exports

### Formatting (Prettier)

- Single quotes
- 2-space indentation
- Trailing commas (ES5)
- Semicolons required
- Arrow function parens always

### Commit Messages

Conventional commits format:
- `feat:` - New features
- `fix:` - Bug fixes
- `refactor:` - Code refactoring
- `docs:` - Documentation
- `test:` - Tests
- `chore:` - Tooling/dependencies

## Development Process

### Platform Priority

**Android first, then iOS:**
1. Complete and test all features on Android
2. Implement iOS after Android is validated
3. Exception: Document Scanner is Android-only (Google doesn't support iOS)

### Current Project Status

Track progress in **project-plan.md**:
- ✅ Phase 1-6: Android implementation complete (all features working)
- ⚪ Phase 7: iOS implementation pending
- ⚪ Phase 8: Documentation & release pending

### Adding New Features

1. **Update types** in `src/types.ts` with comprehensive JSDoc
2. **Write tests** in `src/__tests__/` (TDD approach)
3. **Implement JavaScript/TypeScript** layer
4. **Implement native layer** (Kotlin for Android)
5. **Register in Package** (`RNVisionCameraMLKitPackage.kt`)
6. **Export from index.ts**
7. **Verify tests pass** with `yarn test`
8. **Build and test** in example app

## Common Issues & Solutions

### Kotlin Compilation Errors

**Issue**: Unresolved references after editing Kotlin files
**Solution**: Clean and rebuild
```bash
cd example/android
./gradlew clean
./gradlew :react-native-vision-camera-ml-kit:compileDebugKotlin
```

### Metro Bundler Can't Resolve Module

**Issue**: `Unable to resolve "react-native-vision-camera-ml-kit"`
**Solution**: Rebuild the library and reset Metro cache
```bash
# From root
npx react-native-builder-bob build

# From example/
yarn start --reset-cache
```

### Type Inference Issues in Kotlin Lambdas

**Issue**: `Cannot infer a type for this parameter`
**Solution**: Add explicit type annotations
```kotlin
.addOnSuccessListener { result: GmsDocumentScanningResult ->
  // ...
}
```

### Promise.reject Overload Ambiguity

**Issue**: Multiple overloads for `Promise.reject()`
**Solution**: Extract message to variable
```kotlin
val errorMessage = "Error: ${e.message}"
promise.reject("ERROR_CODE", errorMessage, e)
```

## Performance Considerations

- **Target**: <16ms frame processing (for 60fps)
- **Logger**: Use `Logger.DEBUG` only in development; set `Logger.WARN` or `Logger.ERROR` in production
- **Performance tracking**: Use `Logger.performance()` or `performanceMonitor` utilities
- **Barcode optimization**: Use format filtering when possible
- **Frame skipping**: Consider processing every Nth frame for heavy operations

## Important Notes

- **Package manager**: MUST use Yarn (workspace monorepo)
- **Worklet directive**: Required in ALL frame processor functions
- **Native changes**: Require rebuild; JS changes hot reload
- **Builder Bob**: Outputs to `lib/` in commonjs, module, and typescript formats
- **Main branch**: `main`
- **Minimum versions**: Android SDK 21, iOS 16.0 (when implemented)
- **Reference implementation**: Q:\Dev\react-native-vision-camera-ocr-plus (for patterns)

## Project Tracking

All development progress, decisions, and issues tracked in **project-plan.md**.
- Always use TDD, Red - Green