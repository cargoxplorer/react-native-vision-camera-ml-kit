/**
 * Text Recognition v2 (OCR) plugin for react-native-vision-camera-ml-kit
 *
 * Provides on-device text recognition with support for multiple scripts:
 * - Latin
 * - Chinese
 * - Devanagari
 * - Japanese
 * - Korean
 *
 * @module textRecognition
 */

import { VisionCameraProxy } from 'react-native-vision-camera';
import { useMemo } from 'react';
import { Logger } from './utils/Logger';
import type {
  Frame,
  TextRecognitionOptions,
  TextRecognitionPlugin,
  TextRecognitionResult,
} from './types';

const PLUGIN_NAME = 'scanTextV2';

const LINKING_ERROR = `Failed to initialize Text Recognition plugin. Make sure 'react-native-vision-camera-ml-kit' is properly installed and linked.

For more information, visit: https://github.com/yourusername/react-native-vision-camera-ml-kit`;

/**
 * Create a Text Recognition frame processor plugin
 *
 * @param options - Configuration options for text recognition
 * @returns Text recognition plugin with scanText function
 *
 * @example
 * ```ts
 * import { createTextRecognitionPlugin } from 'react-native-vision-camera-ml-kit';
 *
 * const plugin = createTextRecognitionPlugin({ language: 'latin' });
 *
 * const frameProcessor = useFrameProcessor((frame) => {
 *   'worklet';
 *   const result = plugin.scanText(frame);
 *   if (result?.text) {
 *     console.log('Detected text:', result.text);
 *   }
 * }, [plugin]);
 * ```
 */
export function createTextRecognitionPlugin(
  options: TextRecognitionOptions = {}
): TextRecognitionPlugin {
  Logger.debug(`Creating text recognition plugin with options:`, options);

  const startTime = performance.now();

  // Initialize the frame processor plugin
  const plugin = VisionCameraProxy.initFrameProcessorPlugin(PLUGIN_NAME, {
    ...options,
  });

  if (!plugin) {
    Logger.error('Failed to initialize text recognition plugin');
    throw new Error(LINKING_ERROR);
  }

  const initTime = performance.now() - startTime;
  Logger.performance('Text recognition plugin initialization', initTime);

  return {
    /**
     * Scan text from a camera frame
     *
     * @worklet
     * @param frame - The camera frame to process
     * @returns Recognition result with text and blocks, or null if no text found
     */
    scanText: (frame: Frame): TextRecognitionResult | null => {
      'worklet';

      const frameStartTime = performance.now();

      try {
        const result = plugin.call(frame) as TextRecognitionResult | null;

        const processingTime = performance.now() - frameStartTime;
        Logger.performance('Text recognition frame processing', processingTime);

        if (result && result.text) {
          Logger.debug(
            `Text recognized: "${result.text.substring(0, 50)}${
              result.text.length > 50 ? '...' : ''
            }"`
          );
        }

        return result;
      } catch (error) {
        const processingTime = performance.now() - frameStartTime;
        Logger.error('Error during text recognition', error);
        Logger.performance(
          'Text recognition frame processing (error)',
          processingTime
        );
        return null;
      }
    },
  };
}

/**
 * React hook for text recognition
 *
 * Creates and memoizes a text recognition plugin instance.
 * The plugin is recreated only when options change.
 *
 * @param options - Configuration options for text recognition
 * @returns Text recognition plugin with scanText function
 *
 * @example
 * ```tsx
 * import { useTextRecognition } from 'react-native-vision-camera-ml-kit';
 * import { useFrameProcessor } from 'react-native-vision-camera';
 *
 * function MyComponent() {
 *   const { scanText } = useTextRecognition({ language: 'chinese' });
 *
 *   const frameProcessor = useFrameProcessor((frame) => {
 *     'worklet';
 *     const result = scanText(frame);
 *     // Process result...
 *   }, [scanText]);
 *
 *   return <Camera frameProcessor={frameProcessor} />;
 * }
 * ```
 */
export function useTextRecognition(
  options?: TextRecognitionOptions
): TextRecognitionPlugin {
  return useMemo(
    () => createTextRecognitionPlugin(options),
    [options]
  );
}
