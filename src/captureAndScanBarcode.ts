/**
 * Photo Capture Helper for Barcode Scanning
 *
 * Provides a convenient way to capture a photo and scan barcodes in one operation
 *
 * @module captureAndScanBarcode
 */

import type { Camera } from 'react-native-vision-camera';
import { Logger } from './utils/Logger';
import { scanBarcodeFromImage } from './staticBarcodeScanning';
import type { BarcodeScanningOptions, BarcodeScanningResult } from './types';

/**
 * Options for capture and scan operation
 */
export interface CaptureAndScanBarcodeOptions extends BarcodeScanningOptions {
  /**
   * Flash mode to use during capture
   * @default 'off'
   */
  flash?: 'on' | 'off' | 'auto';

  /**
   * Enable shutter sound
   * @default true
   */
  enableShutterSound?: boolean;
}

/**
 * Capture a photo and scan barcodes from it
 *
 * This is a convenience function that combines photo capture with barcode scanning.
 * Useful for implementing a "snap and scan" feature.
 *
 * @param camera - Reference to the Camera component
 * @param options - Capture and scanning options
 * @returns Promise resolving to scanning result or null if no barcodes found
 *
 * @example
 * ```tsx
 * import { Camera } from 'react-native-vision-camera';
 * import { captureAndScanBarcode, BarcodeFormat } from 'react-native-vision-camera-ml-kit';
 *
 * function MyComponent() {
 *   const camera = useRef<Camera>(null);
 *
 *   const handleCapture = async () => {
 *     if (!camera.current) return;
 *
 *     try {
 *       const result = await captureAndScanBarcode(camera.current, {
 *         formats: [BarcodeFormat.QR_CODE],
 *         flash: 'auto',
 *       });
 *
 *       if (result && result.barcodes.length > 0) {
 *         console.log('Barcodes:', result.barcodes);
 *       }
 *     } catch (error) {
 *       console.error('Capture failed:', error);
 *     }
 *   };
 *
 *   return (
 *     <View>
 *       <Camera ref={camera} />
 *       <Button onPress={handleCapture} title="Scan Barcode" />
 *     </View>
 *   );
 * }
 * ```
 */
export async function captureAndScanBarcode(
  camera: Camera,
  options: CaptureAndScanBarcodeOptions = {}
): Promise<BarcodeScanningResult | null> {
  Logger.debug('Capturing photo and scanning barcode', options);

  const startTime = performance.now();

  try {
    // Capture photo
    const photo = await camera.takePhoto({
      flash: options.flash || 'off',
      enableShutterSound: options.enableShutterSound ?? true,
    });

    const captureTime = performance.now() - startTime;
    Logger.performance('Photo capture', captureTime);
    Logger.debug(`Photo captured: ${photo.path}`);

    // Scan barcodes from captured photo
    const result = await scanBarcodeFromImage({
      uri: `file://${photo.path}`,
      formats: options.formats,
      orientation: 0, // Photos are typically auto-rotated
    });

    const totalTime = performance.now() - startTime;
    Logger.performance('Capture and scan total', totalTime);

    return result;
  } catch (error) {
    const totalTime = performance.now() - startTime;
    Logger.error('Error during capture and scan', error);
    Logger.performance('Capture and scan (error)', totalTime);
    throw error;
  }
}
