# React Native Vision Camera ML Kit - Project Plan

**Project Start Date:** 2025-11-17
**Target Platform Priority:** Android → iOS
**Development Approach:** TDD (Test-Driven Development)

---

## Project Overview

Standalone React Native Vision Camera plugin integrating Google ML Kit with three core features:

1. ✅ **Text Recognition v2** (Android + iOS)
2. ✅ **Barcode Scanning** (Android + iOS)
3. ✅ **Document Scanner** (Android only)

**Key Requirements:**
- Custom logger with configurable log levels
- Frame processors + static image APIs + photo capture helpers
- Unit test coverage >80%
- Performance: <16ms frame processing time

---

## Phase Status

| Phase | Status | Progress |
|-------|--------|----------|
| Phase 1: Project Setup & Infrastructure | 🟢 Complete | 100% |
| Phase 2: Text Recognition v2 (Android) | 🟢 Complete | 100% |
| Phase 3: Barcode Scanning (Android) | 🟢 Complete | 100% |
| Phase 4: Document Scanner (Android) | 🟢 Complete | 100% |
| Phase 5: Integration & Polish (Android) | 🟢 Complete | 100% |
| Phase 6: Example App (Android) | ⚪ Not Started | 0% |
| Phase 7: iOS Implementation | ⚪ Not Started | 0% |
| Phase 8: Documentation & Release | ⚪ Not Started | 0% |

**Legend:** 🟢 Complete | 🟡 In Progress | ⚪ Not Started | 🔴 Blocked

---

## Phase 1: Project Setup & Infrastructure (Android)

**Status:** 🟢 Complete
**Started:** 2025-11-17
**Completed:** 2025-11-17

### 1.1 Initialize Project Structure ✅
- [x] Create project directory
- [x] Create package.json with dependencies
- [x] Configure TypeScript (tsconfig.json, tsconfig.build.json)
- [x] Configure Babel (babel.config.js)
- [x] Set up ESLint and Prettier
- [x] Create .gitignore
- [x] Create project-plan.md

### 1.2 Android Native Setup ✅
- [x] Create android/ directory structure
- [x] Configure build.gradle with ML Kit dependencies
- [x] Set up AndroidManifest.xml
- [x] Create Package registration class
- [x] Create native Logger utility

### 1.3 Custom Logger Implementation ✅
- [x] Create src/utils/Logger.ts
- [x] Implement log levels (DEBUG, INFO, WARN, ERROR)
- [x] Implement setLogLevel() API
- [x] Write comprehensive unit tests for logger
- [x] Create android/utils/Logger.kt for native logging

### 1.4 Testing Infrastructure ✅
- [x] Configure Jest with coverage thresholds
- [x] Create mock utilities for VisionCameraProxy
- [x] Create mock utilities for NativeModules
- [x] Create test setup file
- [x] Create jest.config.js

### 1.5 TypeScript Types Foundation ✅
- [x] Create comprehensive src/types.ts
- [x] Define Frame type exports
- [x] Define Text Recognition types (blocks, lines, elements, symbols)
- [x] Define Barcode Scanning types (formats, structured data)
- [x] Define Document Scanner types (modes, pages)
- [x] Add JSDoc comments throughout

### 1.6 Documentation ✅
- [x] Create README.md
- [x] Create CLAUDE.md for AI assistance
- [x] Create CONTRIBUTING.md with guidelines
- [x] Document TDD approach

---

## Phase 2: Text Recognition v2 (Android - TDD)

**Status:** 🟢 Complete
**Started:** 2025-11-17
**Completed:** 2025-11-17

### Tasks ✅
- [x] Write unit tests for plugin initialization
- [x] Write unit tests for error handling
- [x] Create src/textRecognition.ts
- [x] Implement createTextRecognitionPlugin()
- [x] Implement useTextRecognition() hook
- [x] Create TextRecognitionPlugin.kt
- [x] Implement frame processor with all language support
- [x] Create StaticTextRecognitionModule.kt
- [x] Implement static image API (recognizeTextFromImage)
- [x] Implement photo capture helper (captureAndRecognizeText)
- [x] Register plugin and module in Package
- [x] Export all APIs from index.ts

### Deliverables
- **Frame Processor:** `scanTextV2` plugin supporting Latin, Chinese, Devanagari, Japanese, Korean
- **Static Image API:** `recognizeTextFromImage()` for processing saved images
- **Photo Capture Helper:** `captureAndRecognizeText()` for snap-and-scan functionality
- **Full type safety:** Comprehensive TypeScript types
- **Logging:** Performance tracking and debugging support
- **Build:** ✅ Compiles successfully (CommonJS, ES Modules, TypeScript definitions)
- **Tests:** ✅ 40 tests passing
- **Type Check:** ✅ No TypeScript errors

---

## Phase 3: Barcode Scanning (Android - TDD)

**Status:** 🟢 Complete
**Started:** 2025-11-17
**Completed:** 2025-11-17

### Tasks ✅
- [x] Write comprehensive unit tests (21 tests)
- [x] Create src/barcodeScanning.ts
- [x] Implement createBarcodeScannerPlugin()
- [x] Implement useBarcodeScanner() hook
- [x] Create BarcodeScanningPlugin.kt with format filtering
- [x] Implement frame processor with all formats (1D and 2D)
- [x] Implement structured data extraction (WiFi, Contact, URL, etc.)
- [x] Create StaticBarcodeScannerModule.kt
- [x] Implement static image API (scanBarcodeFromImage)
- [x] Implement photo capture helper (captureAndScanBarcode)
- [x] Register plugin and module in Package
- [x] Export all APIs from index.ts
- [x] All 61 tests passing

### Deliverables
- **Frame Processor:** `scanBarcode` plugin supporting all 1D and 2D formats
- **Format Filtering:** Optional format restriction for improved performance
- **Structured Data:** Automatic extraction of WiFi, URLs, Contacts, Calendar events, Driver licenses
- **Static Image API:** `scanBarcodeFromImage()` for processing saved images
- **Photo Capture Helper:** `captureAndScanBarcode()` for snap-and-scan functionality
- **Full type safety:** Comprehensive TypeScript types for all barcode formats and data structures
- **Logging:** Performance tracking and debugging support

---

## Phase 4: Document Scanner (Android Only - TDD)

**Status:** 🟢 Complete
**Started:** 2025-11-17
**Completed:** 2025-11-17

### Tasks ✅
- [x] Write comprehensive unit tests (21 tests)
- [x] Create src/documentScanner.ts (frame processor API)
- [x] Implement createDocumentScannerPlugin()
- [x] Implement useDocumentScanner() hook
- [x] Create DocumentScannerModule.kt (UI-based scanner)
- [x] Implement ML Kit Document Scanner integration
- [x] Support all scanner modes (BASE, BASE_WITH_FILTER, FULL)
- [x] Create launchDocumentScanner() function (recommended API)
- [x] Handle multi-page scanning
- [x] PDF generation support
- [x] Gallery import support
- [x] Register module in Package
- [x] Export all APIs from index.ts
- [x] All 82 tests passing

### Deliverables
- **UI-Based Scanner:** `launchDocumentScanner()` - Launches ML Kit's document scanner UI (recommended)
- **Frame Processor:** `scanDocument` plugin (experimental, limited functionality)
- **Scanner Modes:** BASE, BASE_WITH_FILTER, FULL (with ML-powered cleaning)
- **Multi-page:** Configurable page limit
- **Gallery Import:** Optional gallery import for existing documents
- **PDF Output:** Automatic PDF generation with scanned pages
- **Full type safety:** Comprehensive TypeScript types
- **Logging:** Performance tracking and debugging support
- **Platform:** Android only (iOS not supported by Google)

---

## Phase 5: Integration & Polish (Android)

**Status:** 🟢 Complete
**Started:** 2025-11-17
**Completed:** 2025-11-17

### Tasks ✅
- [x] Create constants.ts with centralized config
- [x] Implement comprehensive error handling utilities
- [x] Create MLKitError class with error codes
- [x] Implement performance monitoring utilities
- [x] Add performanceMonitor for tracking metrics
- [x] Standardize error messages across all features
- [x] Export utilities from index.ts
- [x] Update README with comprehensive documentation
- [x] Add API reference for all features
- [x] Document error codes and troubleshooting
- [x] All 82 tests passing
- [x] Build successful

### Deliverables
- **Error Handling:** Standardized MLKitError class with error codes
- **Performance Monitoring:** Built-in performance tracking utilities
- **Constants:** Centralized configuration and error messages
- **Comprehensive README:** Full API documentation with examples
- **Utilities:** Logger, error handling, performance monitoring
- **Type Safety:** All utilities fully typed
- **Production Ready:** Robust error handling and logging

---

## Phase 6: Example App (Android)

**Status:** ⚪ Not Started

### Tasks
- [ ] Create example/ directory
- [ ] Set up Expo project
- [ ] Create Text Recognition demo
- [ ] Create Barcode Scanning demo
- [ ] Create Document Scanner demo
- [ ] Manual testing protocol
- [ ] Document test results

---

## Phase 7: iOS Implementation

**Status:** ⚪ Not Started

### Tasks
- [ ] Create ios/ directory structure
- [ ] Create podspec file
- [ ] Implement TextRecognitionPlugin.swift
- [ ] Implement BarcodeScanningPlugin.swift
- [ ] Static image modules for iOS
- [ ] Photo capture helpers for iOS
- [ ] iOS example app testing

**Note:** Document Scanner skipped for iOS (Google limitation)

---

## Phase 8: Documentation & Release

**Status:** ⚪ Not Started

### Tasks
- [ ] Write README.md
- [ ] Write API documentation
- [ ] Create CLAUDE.md
- [ ] Create CONTRIBUTING.md
- [ ] Set up GitHub Actions CI/CD
- [ ] Prepare npm package
- [ ] Publish v1.0.0

---

## Dependencies

### Latest Versions (as of 2025-11-17)
- react-native-vision-camera: ^4.7.3
- react-native-worklets-core: ^1.6.2
- react-native: 0.81.4
- react: 19.1.0

### ML Kit (Android)
- Text Recognition: com.google.android.gms:play-services-mlkit-text-recognition:19.0.1
- Document Scanner: com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1
- Barcode Scanning: com.google.mlkit:barcode-scanning:17.3.0

### ML Kit (iOS)
- GoogleMLKit/TextRecognition >= 8.0.0
- GoogleMLKit/BarcodeScanning >= 7.0.0

---

## Decisions Log

| Date | Decision | Rationale |
|------|----------|-----------|
| 2025-11-17 | Android-first development | Allows thorough testing before iOS implementation |
| 2025-11-17 | Skip iOS for Document Scanner | Google doesn't support Document Scanner on iOS |
| 2025-11-17 | Custom logger implementation | Better control over performance and log levels |
| 2025-11-17 | TDD approach with unit tests first | Ensures code quality and prevents regressions |
| 2025-11-17 | Include all API types (frame/static/photo) | Provides maximum flexibility for users |

---

## Known Issues

None yet.

---

## Performance Benchmarks

Will be added during Phase 5.

---

## Notes

- Using Yarn as package manager (workspace monorepo)
- Main branch will be `main`
- Coverage threshold set to 80% for all metrics
- Minimum Android SDK: 21
- Minimum iOS version: 16.0 (due to ML Kit requirements)
