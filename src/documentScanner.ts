/**
 * Document Scanner plugin for react-native-vision-camera-ml-kit
 *
 * Provides on-device document scanning with:
 * - Automatic document detection and capture
 * - Edge detection and cropping
 * - Auto-rotation
 * - Filters and ML-powered cleaning (FULL mode)
 *
 * NOTE: This feature is Android-only. iOS is not supported by Google ML Kit.
 *
 * @module documentScanner
 */

import { VisionCameraProxy } from 'react-native-vision-camera';
import { useMemo } from 'react';
import { Logger } from './utils/Logger';
import type {
  Frame,
  DocumentScannerOptions,
  DocumentScannerPlugin,
  DocumentScanningResult,
} from './types';

const PLUGIN_NAME = 'scanDocument';

const LINKING_ERROR = `Failed to initialize Document Scanner plugin. Make sure 'react-native-vision-camera-ml-kit' is properly installed and linked.

NOTE: Document Scanner is Android-only. iOS is not supported by Google ML Kit.

For more information, visit: https://github.com/yourusername/react-native-vision-camera-ml-kit`;

/**
 * Create a Document Scanner frame processor plugin
 *
 * @param options - Configuration options for document scanning
 * @returns Document scanner plugin with scanDocument function
 *
 * @platform Android
 *
 * @example
 * ```ts
 * import { createDocumentScannerPlugin, DocumentScannerMode } from 'react-native-vision-camera-ml-kit';
 *
 * // Full mode with ML-powered cleaning
 * const plugin = createDocumentScannerPlugin({
 *   mode: DocumentScannerMode.FULL,
 *   pageLimit: 5,
 * });
 *
 * const frameProcessor = useFrameProcessor((frame) => {
 *   'worklet';
 *   const result = plugin.scanDocument(frame);
 *   if (result?.pages.length > 0) {
 *     console.log('Scanned documents:', result.pages);
 *   }
 * }, [plugin]);
 * ```
 */
export function createDocumentScannerPlugin(
  options: DocumentScannerOptions = {}
): DocumentScannerPlugin {
  Logger.debug(`Creating document scanner plugin with options:`, options);

  const startTime = performance.now();

  // Initialize the frame processor plugin
  const plugin = VisionCameraProxy.initFrameProcessorPlugin(PLUGIN_NAME, {
    ...options,
  });

  if (!plugin) {
    Logger.error('Failed to initialize document scanner plugin');
    throw new Error(LINKING_ERROR);
  }

  const initTime = performance.now() - startTime;
  Logger.performance('Document scanner plugin initialization', initTime);

  return {
    /**
     * Scan document from a camera frame
     *
     * @worklet
     * @param frame - The camera frame to process
     * @returns Scanning result with document pages, or null if no document found
     */
    scanDocument: (frame: Frame): DocumentScanningResult | null => {
      'worklet';
      try {
        const result = plugin.call(
          frame
        ) as unknown as DocumentScanningResult | null;
        return result;
      } catch (e) {
        // If the native plugin throws, return null instead of propagating
        // an error from the worklet context.
        return null;
      }
    },
  };
}

/**
 * React hook for document scanning
 *
 * Creates and memoizes a document scanner plugin instance.
 * The plugin is recreated only when options change.
 *
 * @param options - Configuration options for document scanning
 * @returns Document scanner plugin with scanDocument function
 *
 * @platform Android
 *
 * @example
 * ```tsx
 * import { useDocumentScanner, DocumentScannerMode } from 'react-native-vision-camera-ml-kit';
 * import { useFrameProcessor } from 'react-native-vision-camera';
 *
 * function MyComponent() {
 *   const { scanDocument } = useDocumentScanner({
 *     mode: DocumentScannerMode.FULL,
 *     pageLimit: 10,
 *   });
 *
 *   const frameProcessor = useFrameProcessor((frame) => {
 *     'worklet';
 *     const result = scanDocument(frame);
 *     // Process result...
 *   }, [scanDocument]);
 *
 *   return <Camera frameProcessor={frameProcessor} />;
 * }
 * ```
 */
export function useDocumentScanner(
  options?: DocumentScannerOptions
): DocumentScannerPlugin {
  return useMemo(() => createDocumentScannerPlugin(options), [options]);
}
