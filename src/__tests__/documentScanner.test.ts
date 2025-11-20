/**
 * Unit tests for Document Scanner
 * Following TDD: Tests written BEFORE implementation
 */

import { createDocumentScannerPlugin } from '../documentScanner';
import { DocumentScannerMode } from '../types';
import {
  mockVisionCameraProxy,
  mockPlugin,
} from './__mocks__/VisionCameraProxy';

describe('createDocumentScannerPlugin', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockVisionCameraProxy.initFrameProcessorPlugin.mockReturnValue(mockPlugin);
  });

  describe('plugin initialization', () => {
    it('should create plugin with default options', () => {
      const plugin = createDocumentScannerPlugin();

      expect(
        mockVisionCameraProxy.initFrameProcessorPlugin
      ).toHaveBeenCalledWith('scanDocument', {});
      expect(plugin).toBeDefined();
      expect(plugin.scanDocument).toBeDefined();
    });

    it('should create plugin with BASE mode', () => {
      createDocumentScannerPlugin({
        mode: DocumentScannerMode.BASE,
      });

      expect(
        mockVisionCameraProxy.initFrameProcessorPlugin
      ).toHaveBeenCalledWith('scanDocument', { mode: 'base' });
    });

    it('should create plugin with BASE_WITH_FILTER mode', () => {
      createDocumentScannerPlugin({
        mode: DocumentScannerMode.BASE_WITH_FILTER,
      });

      expect(
        mockVisionCameraProxy.initFrameProcessorPlugin
      ).toHaveBeenCalledWith('scanDocument', { mode: 'baseWithFilter' });
    });

    it('should create plugin with FULL mode', () => {
      createDocumentScannerPlugin({
        mode: DocumentScannerMode.FULL,
      });

      expect(
        mockVisionCameraProxy.initFrameProcessorPlugin
      ).toHaveBeenCalledWith('scanDocument', { mode: 'full' });
    });

    it('should create plugin with page limit', () => {
      createDocumentScannerPlugin({
        pageLimit: 5,
      });

      expect(
        mockVisionCameraProxy.initFrameProcessorPlugin
      ).toHaveBeenCalledWith('scanDocument', { pageLimit: 5 });
    });

    it('should create plugin with gallery import disabled', () => {
      createDocumentScannerPlugin({
        galleryImportEnabled: false,
      });

      expect(
        mockVisionCameraProxy.initFrameProcessorPlugin
      ).toHaveBeenCalledWith('scanDocument', { galleryImportEnabled: false });
    });

    it('should create plugin with all options', () => {
      createDocumentScannerPlugin({
        mode: DocumentScannerMode.FULL,
        pageLimit: 10,
        galleryImportEnabled: true,
      });

      expect(
        mockVisionCameraProxy.initFrameProcessorPlugin
      ).toHaveBeenCalledWith('scanDocument', {
        mode: 'full',
        pageLimit: 10,
        galleryImportEnabled: true,
      });
    });
  });

  describe('error handling', () => {
    it('should throw error when plugin initialization fails', () => {
      mockVisionCameraProxy.initFrameProcessorPlugin.mockReturnValue(
        null as any
      );

      expect(() => createDocumentScannerPlugin()).toThrow(
        "Failed to initialize Document Scanner plugin. Make sure 'react-native-vision-camera-ml-kit' is properly installed and linked."
      );
    });

    it('should throw error when plugin is undefined', () => {
      mockVisionCameraProxy.initFrameProcessorPlugin.mockReturnValue(
        undefined as any
      );

      expect(() => createDocumentScannerPlugin()).toThrow();
    });
  });

  describe('scanDocument function', () => {
    it('should return a scanDocument function', () => {
      const plugin = createDocumentScannerPlugin();

      expect(typeof plugin.scanDocument).toBe('function');
    });

    it('should call native plugin with frame', () => {
      const plugin = createDocumentScannerPlugin();
      const mockFrame = { width: 1920, height: 1080 } as any;

      mockPlugin.call.mockReturnValue({
        pages: [
          {
            uri: 'file:///path/to/document.jpg',
            pageNumber: 1,
          },
        ],
        pageCount: 1,
      });

      const result = plugin.scanDocument(mockFrame);

      expect(mockPlugin.call).toHaveBeenCalledWith(mockFrame);
      expect(result).toBeDefined();
      expect(result?.pages).toHaveLength(1);
    });

    it('should handle null result from native', () => {
      const plugin = createDocumentScannerPlugin();
      const mockFrame = { width: 1920, height: 1080 } as any;

      mockPlugin.call.mockReturnValue(null);

      const result = plugin.scanDocument(mockFrame);

      expect(result).toBeNull();
    });

    it('should handle empty pages array', () => {
      const plugin = createDocumentScannerPlugin();
      const mockFrame = { width: 1920, height: 1080 } as any;

      mockPlugin.call.mockReturnValue({
        pages: [],
        pageCount: 0,
      });

      const result = plugin.scanDocument(mockFrame);

      expect(result).toEqual({
        pages: [],
        pageCount: 0,
      });
    });

    it('should return single page result', () => {
      const plugin = createDocumentScannerPlugin();
      const mockFrame = { width: 1920, height: 1080 } as any;

      const mockResult = {
        pages: [
          {
            uri: 'file:///path/to/document.jpg',
            pageNumber: 1,
            originalSize: { width: 2000, height: 3000 },
            processedSize: { width: 1600, height: 2400 },
          },
        ],
        pageCount: 1,
      };

      mockPlugin.call.mockReturnValue(mockResult);

      const result = plugin.scanDocument(mockFrame);

      expect(result).toEqual(mockResult);
      expect(result?.pages[0].pageNumber).toBe(1);
      expect(result?.pageCount).toBe(1);
    });

    it('should handle multiple pages', () => {
      const plugin = createDocumentScannerPlugin();
      const mockFrame = { width: 1920, height: 1080 } as any;

      const mockResult = {
        pages: [
          {
            uri: 'file:///path/to/page1.jpg',
            pageNumber: 1,
          },
          {
            uri: 'file:///path/to/page2.jpg',
            pageNumber: 2,
          },
          {
            uri: 'file:///path/to/page3.jpg',
            pageNumber: 3,
          },
        ],
        pageCount: 3,
      };

      mockPlugin.call.mockReturnValue(mockResult);

      const result = plugin.scanDocument(mockFrame);

      expect(result?.pages).toHaveLength(3);
      expect(result?.pageCount).toBe(3);
      expect(result?.pages[0].pageNumber).toBe(1);
      expect(result?.pages[2].pageNumber).toBe(3);
    });

    it('should include image dimensions when provided', () => {
      const plugin = createDocumentScannerPlugin();
      const mockFrame = { width: 1920, height: 1080 } as any;

      const mockResult = {
        pages: [
          {
            uri: 'file:///path/to/document.jpg',
            pageNumber: 1,
            originalSize: { width: 3000, height: 4000 },
            processedSize: { width: 2400, height: 3200 },
          },
        ],
        pageCount: 1,
      };

      mockPlugin.call.mockReturnValue(mockResult);

      const result = plugin.scanDocument(mockFrame);

      expect(result?.pages[0].originalSize).toEqual({
        width: 3000,
        height: 4000,
      });
      expect(result?.pages[0].processedSize).toEqual({
        width: 2400,
        height: 3200,
      });
    });
  });
});
