/**
 * Unit tests for Text Recognition v2
 * Following TDD: Tests written BEFORE implementation
 */

import { createTextRecognitionPlugin } from '../textRecognition';
import { TextRecognitionScript } from '../types';
import { mockVisionCameraProxy, mockPlugin } from './__mocks__/VisionCameraProxy';

describe('createTextRecognitionPlugin', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockVisionCameraProxy.initFrameProcessorPlugin.mockReturnValue(mockPlugin);
  });

  describe('plugin initialization', () => {
    it('should create plugin with default options', () => {
      const plugin = createTextRecognitionPlugin();

      expect(mockVisionCameraProxy.initFrameProcessorPlugin).toHaveBeenCalledWith(
        'scanTextV2',
        {}
      );
      expect(plugin).toBeDefined();
      expect(plugin.scanText).toBeDefined();
    });

    it('should create plugin with Latin script option', () => {
      const plugin = createTextRecognitionPlugin({
        language: TextRecognitionScript.LATIN,
      });

      expect(mockVisionCameraProxy.initFrameProcessorPlugin).toHaveBeenCalledWith(
        'scanTextV2',
        { language: 'latin' }
      );
      expect(plugin).toBeDefined();
    });

    it('should create plugin with Chinese script option', () => {
      createTextRecognitionPlugin({ language: TextRecognitionScript.CHINESE });

      expect(mockVisionCameraProxy.initFrameProcessorPlugin).toHaveBeenCalledWith(
        'scanTextV2',
        { language: 'chinese' }
      );
    });

    it('should create plugin with Devanagari script option', () => {
      createTextRecognitionPlugin({
        language: TextRecognitionScript.DEVANAGARI,
      });

      expect(mockVisionCameraProxy.initFrameProcessorPlugin).toHaveBeenCalledWith(
        'scanTextV2',
        { language: 'devanagari' }
      );
    });

    it('should create plugin with Japanese script option', () => {
      createTextRecognitionPlugin({
        language: TextRecognitionScript.JAPANESE,
      });

      expect(mockVisionCameraProxy.initFrameProcessorPlugin).toHaveBeenCalledWith(
        'scanTextV2',
        { language: 'japanese' }
      );
    });

    it('should create plugin with Korean script option', () => {
      createTextRecognitionPlugin({ language: TextRecognitionScript.KOREAN });

      expect(mockVisionCameraProxy.initFrameProcessorPlugin).toHaveBeenCalledWith(
        'scanTextV2',
        { language: 'korean' }
      );
    });

    it('should accept custom language string', () => {
      createTextRecognitionPlugin({ language: 'custom-lang' });

      expect(mockVisionCameraProxy.initFrameProcessorPlugin).toHaveBeenCalledWith(
        'scanTextV2',
        { language: 'custom-lang' }
      );
    });
  });

  describe('error handling', () => {
    it('should throw error when plugin initialization fails', () => {
      mockVisionCameraProxy.initFrameProcessorPlugin.mockReturnValue(null);

      expect(() => createTextRecognitionPlugin()).toThrow(
        "Failed to initialize Text Recognition plugin. Make sure 'react-native-vision-camera-ml-kit' is properly installed and linked."
      );
    });

    it('should throw error when plugin is undefined', () => {
      mockVisionCameraProxy.initFrameProcessorPlugin.mockReturnValue(undefined);

      expect(() => createTextRecognitionPlugin()).toThrow();
    });
  });

  describe('scanText function', () => {
    it('should return a scanText function', () => {
      const plugin = createTextRecognitionPlugin();

      expect(typeof plugin.scanText).toBe('function');
    });

    it('should call native plugin with frame', () => {
      const plugin = createTextRecognitionPlugin();
      const mockFrame = { width: 1920, height: 1080 } as any;

      mockPlugin.call.mockReturnValue({
        text: 'Hello World',
        blocks: [],
      });

      const result = plugin.scanText(mockFrame);

      expect(mockPlugin.call).toHaveBeenCalledWith(mockFrame);
      expect(result).toEqual({
        text: 'Hello World',
        blocks: [],
      });
    });

    it('should handle null result from native', () => {
      const plugin = createTextRecognitionPlugin();
      const mockFrame = { width: 1920, height: 1080 } as any;

      mockPlugin.call.mockReturnValue(null);

      const result = plugin.scanText(mockFrame);

      expect(result).toBeNull();
    });

    it('should handle empty text result', () => {
      const plugin = createTextRecognitionPlugin();
      const mockFrame = { width: 1920, height: 1080 } as any;

      mockPlugin.call.mockReturnValue({
        text: '',
        blocks: [],
      });

      const result = plugin.scanText(mockFrame);

      expect(result).toEqual({
        text: '',
        blocks: [],
      });
    });

    it('should return properly structured result with blocks', () => {
      const plugin = createTextRecognitionPlugin();
      const mockFrame = { width: 1920, height: 1080 } as any;

      const mockResult = {
        text: 'Hello World',
        blocks: [
          {
            text: 'Hello',
            frame: { x: 10, y: 20, width: 100, height: 50 },
            cornerPoints: [
              { x: 10, y: 20 },
              { x: 110, y: 20 },
              { x: 110, y: 70 },
              { x: 10, y: 70 },
            ],
            lines: [],
          },
        ],
      };

      mockPlugin.call.mockReturnValue(mockResult);

      const result = plugin.scanText(mockFrame);

      expect(result).toEqual(mockResult);
      expect(result?.blocks).toHaveLength(1);
      expect(result?.blocks[0].text).toBe('Hello');
    });
  });
});
