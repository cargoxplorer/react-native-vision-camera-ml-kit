/**
 * Static Barcode Scanning API
 *
 * Process barcode scanning on static images (from file URIs)
 *
 * @module staticBarcodeScanning
 */

import { NativeModules } from 'react-native';
import { Logger } from './utils/Logger';
import type {
  StaticImageOptions,
  BarcodeScanningOptions,
  BarcodeScanningResult,
} from './types';

const LINKING_ERROR = `Failed to load StaticBarcodeScannerModule. Make sure 'react-native-vision-camera-ml-kit' is properly installed and linked.

For more information, visit: https://github.com/yourusername/react-native-vision-camera-ml-kit`;

interface StaticBarcodeScannerModuleType {
  scanBarcode: (
    options: StaticImageOptions & BarcodeScanningOptions
  ) => Promise<BarcodeScanningResult | null>;
}

const StaticBarcodeScannerModule: StaticBarcodeScannerModuleType =
  NativeModules.StaticBarcodeScannerModule
    ? NativeModules.StaticBarcodeScannerModule
    : new Proxy(
        {},
        {
          get() {
            throw new Error(LINKING_ERROR);
          },
        }
      );

/**
 * Scan barcodes from a static image
 *
 * @param options - Image URI and scanning options
 * @returns Promise resolving to scanning result or null if no barcodes found
 *
 * @example
 * ```ts
 * import { scanBarcodeFromImage, BarcodeFormat } from 'react-native-vision-camera-ml-kit';
 *
 * const result = await scanBarcodeFromImage({
 *   uri: 'file:///path/to/image.jpg',
 *   formats: [BarcodeFormat.QR_CODE, BarcodeFormat.EAN_13],
 * });
 *
 * if (result && result.barcodes.length > 0) {
 *   console.log('Detected barcodes:', result.barcodes);
 *   result.barcodes.forEach(barcode => {
 *     console.log(`${barcode.format}: ${barcode.displayValue}`);
 *   });
 * }
 * ```
 */
export async function scanBarcodeFromImage(
  options: StaticImageOptions & BarcodeScanningOptions
): Promise<BarcodeScanningResult | null> {
  Logger.debug('Scanning barcode from static image:', options);

  const startTime = performance.now();

  try {
    const result = await StaticBarcodeScannerModule.scanBarcode(options);

    const processingTime = performance.now() - startTime;
    Logger.performance('Static barcode scanning', processingTime);

    if (result && result.barcodes && result.barcodes.length > 0) {
      Logger.debug(
        `Static barcodes scanned: ${result.barcodes.length} barcode(s)`
      );
    } else {
      Logger.debug('No barcodes detected in static image');
    }

    return result;
  } catch (error) {
    const processingTime = performance.now() - startTime;
    Logger.error('Error during static barcode scanning', error);
    Logger.performance('Static barcode scanning (error)', processingTime);
    throw error;
  }
}
