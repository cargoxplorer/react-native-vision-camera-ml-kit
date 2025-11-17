/**
 * Constants used throughout the library
 * Centralized error messages and configuration
 */

/**
 * Error messages for plugin initialization failures
 */
export const LINKING_ERRORS = {
  TEXT_RECOGNITION: `Failed to initialize Text Recognition plugin. Make sure 'react-native-vision-camera-ml-kit' is properly installed and linked.

For more information, visit: https://github.com/yourusername/react-native-vision-camera-ml-kit`,

  BARCODE_SCANNER: `Failed to initialize Barcode Scanner plugin. Make sure 'react-native-vision-camera-ml-kit' is properly installed and linked.

For more information, visit: https://github.com/yourusername/react-native-vision-camera-ml-kit`,

  DOCUMENT_SCANNER: `Failed to initialize Document Scanner plugin. Make sure 'react-native-vision-camera-ml-kit' is properly installed and linked.

NOTE: Document Scanner is Android-only. iOS is not supported by Google ML Kit.

For more information, visit: https://github.com/yourusername/react-native-vision-camera-ml-kit`,

  STATIC_MODULE: (moduleName: string) =>
    `Failed to load ${moduleName}. Make sure 'react-native-vision-camera-ml-kit' is properly installed and linked.

For more information, visit: https://github.com/yourusername/react-native-vision-camera-ml-kit`,
};

/**
 * Platform-specific error messages
 */
export const PLATFORM_ERRORS = {
  DOCUMENT_SCANNER_IOS: `Document Scanner is only available on Android. iOS is not supported by Google ML Kit.`,
};

/**
 * Plugin names for frame processors
 */
export const PLUGIN_NAMES = {
  TEXT_RECOGNITION: 'scanTextV2',
  BARCODE_SCANNER: 'scanBarcode',
  DOCUMENT_SCANNER: 'scanDocument',
};

/**
 * Native module names
 */
export const MODULE_NAMES = {
  STATIC_TEXT_RECOGNITION: 'StaticTextRecognitionModule',
  STATIC_BARCODE_SCANNER: 'StaticBarcodeScannerModule',
  DOCUMENT_SCANNER: 'DocumentScannerModule',
};

/**
 * Performance thresholds (in milliseconds)
 */
export const PERFORMANCE_THRESHOLDS = {
  FRAME_PROCESSING_WARNING: 16, // 60fps target
  FRAME_PROCESSING_ERROR: 33, // 30fps minimum
  PLUGIN_INITIALIZATION: 100,
  STATIC_PROCESSING: 1000,
};

/**
 * Default configuration values
 */
export const DEFAULTS = {
  TEXT_RECOGNITION: {
    LANGUAGE: 'latin',
  },
  BARCODE_SCANNER: {
    FORMATS: [], // Empty array = all formats
  },
  DOCUMENT_SCANNER: {
    MODE: 'full',
    PAGE_LIMIT: 1,
    GALLERY_IMPORT_ENABLED: true,
  },
};
