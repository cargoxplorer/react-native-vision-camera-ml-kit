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

// Feature exports will be added as they are implemented:
// export { createBarcodeScannerPlugin, useBarcodeScanner } from './barcodeScanning';
// export { createDocumentScannerPlugin, useDocumentScanner } from './documentScanner';
