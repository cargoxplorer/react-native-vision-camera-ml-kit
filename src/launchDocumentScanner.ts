/**
 * Document Scanner UI Launcher
 *
 * Launches Google ML Kit's document scanner UI for high-quality document capture
 *
 * NOTE: This is Android-only. iOS is not supported by Google ML Kit.
 * NOTE: This launches a UI, it's not a frame processor plugin.
 *
 * @module launchDocumentScanner
 */

import { NativeModules, Platform } from 'react-native';
import { Logger } from './utils/Logger';
import type { DocumentScannerOptions, DocumentScanningResult } from './types';

const LINKING_ERROR = `Failed to load DocumentScannerModule. Make sure 'react-native-vision-camera-ml-kit' is properly installed and linked.

NOTE: Document Scanner is Android-only.

For more information, visit: https://github.com/yourusername/react-native-vision-camera-ml-kit`;

const PLATFORM_ERROR = `Document Scanner is only available on Android. iOS is not supported by Google ML Kit.`;

interface DocumentScannerModuleType {
  scanDocument: (
    options: DocumentScannerOptions
  ) => Promise<DocumentScanningResult | null>;
}

const DocumentScannerModule: DocumentScannerModuleType =
  NativeModules.DocumentScannerModule
    ? NativeModules.DocumentScannerModule
    : new Proxy(
        {},
        {
          get() {
            throw new Error(LINKING_ERROR);
          },
        }
      );

/**
 * Launch ML Kit's document scanner UI
 *
 * This function launches Google's document scanner interface which provides:
 * - Automatic document detection
 * - Edge detection and cropping
 * - Auto-rotation
 * - Image filters (BASE_WITH_FILTER mode)
 * - ML-powered cleaning (FULL mode)
 * - Multi-page scanning
 * - Gallery import
 *
 * @param options - Scanner configuration options
 * @returns Promise resolving to scanning result or null if cancelled
 *
 * @platform Android
 *
 * @example
 * ```ts
 * import { launchDocumentScanner, DocumentScannerMode } from 'react-native-vision-camera-ml-kit';
 *
 * async function scanDocuments() {
 *   try {
 *     const result = await launchDocumentScanner({
 *       mode: DocumentScannerMode.FULL,
 *       pageLimit: 5,
 *       galleryImportEnabled: true,
 *     });
 *
 *     if (result && result.pages.length > 0) {
 *       console.log('Scanned pages:', result.pages);
 *       result.pages.forEach(page => {
 *         console.log(`Page ${page.pageNumber}: ${page.uri}`);
 *       });
 *
 *       // PDF is also available
 *       if (result.pdfUri) {
 *         console.log('PDF:', result.pdfUri);
 *       }
 *     }
 *   } catch (error) {
 *     if (error.code === 'SCAN_CANCELLED') {
 *       console.log('User cancelled the scan');
 *     } else {
 *       console.error('Scan error:', error);
 *     }
 *   }
 * }
 * ```
 */
export async function launchDocumentScanner(
  options: DocumentScannerOptions = {}
): Promise<DocumentScanningResult | null> {
  // Check platform
  if (Platform.OS !== 'android') {
    Logger.error('Document Scanner is Android-only');
    throw new Error(PLATFORM_ERROR);
  }

  Logger.debug('Launching document scanner with options:', options);

  const startTime = performance.now();

  try {
    const result = await DocumentScannerModule.scanDocument(options);

    const processingTime = performance.now() - startTime;
    Logger.performance('Document scanner session', processingTime);

    if (result && result.pages && result.pages.length > 0) {
      Logger.debug(`Document scanner completed: ${result.pageCount} page(s)`);
    } else {
      Logger.debug('Document scanner returned no pages (cancelled?)');
    }

    return result;
  } catch (error: any) {
    const processingTime = performance.now() - startTime;
    Logger.performance('Document scanner session (error)', processingTime);

    if (error.code === 'SCAN_CANCELLED') {
      Logger.info('Document scan cancelled by user');
    } else {
      Logger.error('Error during document scanning', error);
    }

    throw error;
  }
}
