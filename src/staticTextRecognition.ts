/**
 * Static Text Recognition API
 *
 * Process text recognition on static images (from file URIs)
 * Useful for processing photos from the gallery or saved files
 *
 * @module staticTextRecognition
 */

import { NativeModules } from 'react-native';
import { Logger } from './utils/Logger';
import type {
  StaticImageOptions,
  TextRecognitionOptions,
  TextRecognitionResult,
} from './types';

const LINKING_ERROR = `Failed to load StaticTextRecognitionModule. Make sure 'react-native-vision-camera-ml-kit' is properly installed and linked.

For more information, visit: https://github.com/yourusername/react-native-vision-camera-ml-kit`;

interface StaticTextRecognitionModuleType {
  recognizeText: (
    options: StaticImageOptions & TextRecognitionOptions
  ) => Promise<TextRecognitionResult | null>;
}

const StaticTextRecognitionModule: StaticTextRecognitionModuleType =
  NativeModules.StaticTextRecognitionModule
    ? NativeModules.StaticTextRecognitionModule
    : new Proxy(
        {},
        {
          get() {
            throw new Error(LINKING_ERROR);
          },
        }
      );

/**
 * Recognize text from a static image
 *
 * @param options - Image URI and recognition options
 * @returns Promise resolving to recognition result or null if no text found
 *
 * @example
 * ```ts
 * import { recognizeTextFromImage } from 'react-native-vision-camera-ml-kit';
 *
 * const result = await recognizeTextFromImage({
 *   uri: 'file:///path/to/image.jpg',
 *   language: 'latin',
 *   orientation: 0,
 * });
 *
 * if (result) {
 *   console.log('Detected text:', result.text);
 *   console.log('Blocks:', result.blocks.length);
 * }
 * ```
 */
export async function recognizeTextFromImage(
  options: StaticImageOptions & TextRecognitionOptions
): Promise<TextRecognitionResult | null> {
  Logger.debug('Recognizing text from static image:', options);

  const startTime = performance.now();

  try {
    const result = await StaticTextRecognitionModule.recognizeText(options);

    const processingTime = performance.now() - startTime;
    Logger.performance('Static text recognition', processingTime);

    if (result && result.text) {
      Logger.debug(
        `Static text recognized: "${result.text.substring(0, 50)}${
          result.text.length > 50 ? '...' : ''
        }"`
      );
    } else {
      Logger.debug('No text detected in static image');
    }

    return result;
  } catch (error) {
    const processingTime = performance.now() - startTime;
    Logger.error('Error during static text recognition', error);
    Logger.performance('Static text recognition (error)', processingTime);
    throw error;
  }
}
