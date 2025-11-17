/**
 * Barcode Scanning plugin for react-native-vision-camera-ml-kit
 *
 * Provides on-device barcode scanning with support for:
 * - 1D formats: Codabar, Code 39, Code 93, Code 128, EAN-8, EAN-13, ITF, UPC-A, UPC-E
 * - 2D formats: Aztec, Data Matrix, PDF417, QR Code
 * - Structured data extraction: WiFi, URLs, Contacts, Calendar events, etc.
 *
 * @module barcodeScanning
 */

import { VisionCameraProxy } from 'react-native-vision-camera';
import { useMemo } from 'react';
import { Logger } from './utils/Logger';
import type {
  Frame,
  BarcodeScanningOptions,
  BarcodeScanningPlugin,
  BarcodeScanningResult,
} from './types';

const PLUGIN_NAME = 'scanBarcode';

const LINKING_ERROR = `Failed to initialize Barcode Scanner plugin. Make sure 'react-native-vision-camera-ml-kit' is properly installed and linked.

For more information, visit: https://github.com/yourusername/react-native-vision-camera-ml-kit`;

/**
 * Create a Barcode Scanner frame processor plugin
 *
 * @param options - Configuration options for barcode scanning
 * @returns Barcode scanner plugin with scanBarcode function
 *
 * @example
 * ```ts
 * import { createBarcodeScannerPlugin, BarcodeFormat } from 'react-native-vision-camera-ml-kit';
 *
 * // Scan all barcode formats
 * const plugin = createBarcodeScannerPlugin();
 *
 * // Scan only QR codes
 * const qrPlugin = createBarcodeScannerPlugin({
 *   formats: [BarcodeFormat.QR_CODE]
 * });
 *
 * const frameProcessor = useFrameProcessor((frame) => {
 *   'worklet';
 *   const result = plugin.scanBarcode(frame);
 *   if (result?.barcodes.length > 0) {
 *     console.log('Detected barcodes:', result.barcodes);
 *   }
 * }, [plugin]);
 * ```
 */
export function createBarcodeScannerPlugin(
  options: BarcodeScanningOptions = {}
): BarcodeScanningPlugin {
  Logger.debug(`Creating barcode scanner plugin with options:`, options);

  const startTime = performance.now();

  // Initialize the frame processor plugin
  const plugin = VisionCameraProxy.initFrameProcessorPlugin(PLUGIN_NAME, {
    ...options,
  });

  if (!plugin) {
    Logger.error('Failed to initialize barcode scanner plugin');
    throw new Error(LINKING_ERROR);
  }

  const initTime = performance.now() - startTime;
  Logger.performance('Barcode scanner plugin initialization', initTime);

  return {
    /**
     * Scan barcodes from a camera frame
     *
     * @worklet
     * @param frame - The camera frame to process
     * @returns Scanning result with barcodes array, or null if no barcodes found
     */
    scanBarcode: (frame: Frame): BarcodeScanningResult | null => {
      'worklet';

      const frameStartTime = performance.now();

      try {
        const result = plugin.call(frame) as unknown as BarcodeScanningResult | null;

        const processingTime = performance.now() - frameStartTime;
        Logger.performance('Barcode scanning frame processing', processingTime);

        if (result && result.barcodes && result.barcodes.length > 0) {
          Logger.debug(
            `Barcodes detected: ${result.barcodes.length} barcode(s)`
          );
          result.barcodes.forEach((barcode, index) => {
            Logger.debug(
              `  [${index + 1}] ${barcode.format}: ${barcode.displayValue.substring(0, 50)}${
                barcode.displayValue.length > 50 ? '...' : ''
              }`
            );
          });
        }

        return result;
      } catch (error) {
        const processingTime = performance.now() - frameStartTime;
        Logger.error('Error during barcode scanning', error);
        Logger.performance(
          'Barcode scanning frame processing (error)',
          processingTime
        );
        return null;
      }
    },
  };
}

/**
 * React hook for barcode scanning
 *
 * Creates and memoizes a barcode scanner plugin instance.
 * The plugin is recreated only when options change.
 *
 * @param options - Configuration options for barcode scanning
 * @returns Barcode scanner plugin with scanBarcode function
 *
 * @example
 * ```tsx
 * import { useBarcodeScanner, BarcodeFormat } from 'react-native-vision-camera-ml-kit';
 * import { useFrameProcessor } from 'react-native-vision-camera';
 *
 * function MyComponent() {
 *   const { scanBarcode } = useBarcodeScanner({
 *     formats: [BarcodeFormat.QR_CODE, BarcodeFormat.EAN_13]
 *   });
 *
 *   const frameProcessor = useFrameProcessor((frame) => {
 *     'worklet';
 *     const result = scanBarcode(frame);
 *     // Process result...
 *   }, [scanBarcode]);
 *
 *   return <Camera frameProcessor={frameProcessor} />;
 * }
 * ```
 */
export function useBarcodeScanner(
  options?: BarcodeScanningOptions
): BarcodeScanningPlugin {
  return useMemo(
    () => createBarcodeScannerPlugin(options),
    [options]
  );
}
