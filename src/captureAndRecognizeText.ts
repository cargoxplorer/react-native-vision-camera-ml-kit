/**
 * Photo Capture Helper for Text Recognition
 *
 * Provides a convenient way to capture a photo and recognize text in one operation
 *
 * @module captureAndRecognizeText
 */

import type { Camera } from 'react-native-vision-camera';
import { Logger } from './utils/Logger';
import { recognizeTextFromImage } from './staticTextRecognition';
import type { TextRecognitionOptions, TextRecognitionResult } from './types';

/**
 * Options for capture and recognize operation
 */
export interface CaptureAndRecognizeTextOptions extends TextRecognitionOptions {
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
 * Capture a photo and recognize text from it
 *
 * This is a convenience function that combines photo capture with text recognition.
 * Useful for implementing a "snap and scan" feature.
 *
 * @param camera - Reference to the Camera component
 * @param options - Capture and recognition options
 * @returns Promise resolving to recognition result or null if no text found
 *
 * @example
 * ```tsx
 * import { Camera } from 'react-native-vision-camera';
 * import { captureAndRecognizeText } from 'react-native-vision-camera-ml-kit';
 *
 * function MyComponent() {
 *   const camera = useRef<Camera>(null);
 *
 *   const handleCapture = async () => {
 *     if (!camera.current) return;
 *
 *     try {
 *       const result = await captureAndRecognizeText(camera.current, {
 *         language: 'latin',
 *         flash: 'auto',
 *       });
 *
 *       if (result) {
 *         console.log('Text:', result.text);
 *       }
 *     } catch (error) {
 *       console.error('Capture failed:', error);
 *     }
 *   };
 *
 *   return (
 *     <View>
 *       <Camera ref={camera} />
 *       <Button onPress={handleCapture} title="Capture & Scan" />
 *     </View>
 *   );
 * }
 * ```
 */
export async function captureAndRecognizeText(
  camera: Camera,
  options: CaptureAndRecognizeTextOptions = {}
): Promise<TextRecognitionResult | null> {
  Logger.debug('Capturing photo and recognizing text', options);

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

    // Recognize text from captured photo
    const result = await recognizeTextFromImage({
      uri: `file://${photo.path}`,
      language: options.language,
      orientation: 0, // Photos are typically auto-rotated
    });

    const totalTime = performance.now() - startTime;
    Logger.performance('Capture and recognize total', totalTime);

    return result;
  } catch (error) {
    const totalTime = performance.now() - startTime;
    Logger.error('Error during capture and recognize', error);
    Logger.performance('Capture and recognize (error)', totalTime);
    throw error;
  }
}
