/**
 * react-native-vision-camera-ml-kit
 *
 * React Native Vision Camera plugin for Google ML Kit integration
 *
 * Features:
 * - Text Recognition v2 (OCR)
 * - Barcode Scanning
 * - Document Scanner (Android only)
 *
 * @packageDocumentation
 */

// Export types
export * from './types';

// Export logger
export { Logger, LogLevel } from './utils/Logger';

// Text Recognition v2
export {
  createTextRecognitionPlugin,
  useTextRecognition,
} from './textRecognition';
export { recognizeTextFromImage } from './staticTextRecognition';
export {
  captureAndRecognizeText,
  type CaptureAndRecognizeTextOptions,
} from './captureAndRecognizeText';

// Barcode Scanning
export {
  createBarcodeScannerPlugin,
  useBarcodeScanner,
} from './barcodeScanning';
export { scanBarcodeFromImage } from './staticBarcodeScanning';
export {
  captureAndScanBarcode,
  type CaptureAndScanBarcodeOptions,
} from './captureAndScanBarcode';

// Document Scanner (Android only - UI-based, not a frame processor)
export { launchDocumentScanner } from './launchDocumentScanner';

// Document Scanner frame processor (experimental - limited functionality)
// Note: For best results, use launchDocumentScanner() instead
export {
  createDocumentScannerPlugin,
  useDocumentScanner,
} from './documentScanner';
