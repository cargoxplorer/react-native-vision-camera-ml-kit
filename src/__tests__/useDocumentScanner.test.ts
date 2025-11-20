/**
 * Unit tests for useDocumentScanner hook
 * Following TDD: Tests written BEFORE implementation
 */

import { useDocumentScanner } from '../documentScanner';
import { DocumentScannerMode } from '../types';
import {
  mockVisionCameraProxy,
  mockPlugin,
} from './__mocks__/VisionCameraProxy';

// Create a mock state tracker for useMemo
const mockMemoState = {
  memoizedValue: null as any,
  mockLastDeps: [] as any[],
};

jest.mock('react', () => ({
  ...jest.requireActual('react'),
  useMemo: (factory: () => any, deps: any[]) => {
    // Simulate useMemo behavior: only call factory if deps changed
    const depsChanged =
      mockMemoState.mockLastDeps.length === 0 ||
      deps.length !== mockMemoState.mockLastDeps.length ||
      deps.some((dep, i) => dep !== mockMemoState.mockLastDeps[i]);

    if (depsChanged) {
      mockMemoState.memoizedValue = factory();
      mockMemoState.mockLastDeps = deps;
    }
    return mockMemoState.memoizedValue;
  },
}));

describe('useDocumentScanner', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockVisionCameraProxy.initFrameProcessorPlugin.mockReturnValue(mockPlugin);
    mockMemoState.memoizedValue = null;
    mockMemoState.mockLastDeps = [];
  });

  it('should create document scanner plugin', () => {
    const result = useDocumentScanner();

    expect(result).toBeDefined();
    expect(result.scanDocument).toBeDefined();
    expect(typeof result.scanDocument).toBe('function');
  });

  it('should pass options to plugin creator', () => {
    const options = { mode: DocumentScannerMode.FULL, pageLimit: 5 };
    useDocumentScanner(options);

    expect(mockVisionCameraProxy.initFrameProcessorPlugin).toHaveBeenCalledWith(
      'scanDocument',
      { mode: 'full', pageLimit: 5 }
    );
  });

  it('should work without options', () => {
    const result = useDocumentScanner();

    expect(result).toBeDefined();
    expect(mockVisionCameraProxy.initFrameProcessorPlugin).toHaveBeenCalledWith(
      'scanDocument',
      {}
    );
  });

  it('should memoize plugin instance with same options', () => {
    const options = { mode: DocumentScannerMode.BASE };

    const result1 = useDocumentScanner(options);
    expect(
      mockVisionCameraProxy.initFrameProcessorPlugin
    ).toHaveBeenCalledTimes(1);

    const result2 = useDocumentScanner(options);
    expect(
      mockVisionCameraProxy.initFrameProcessorPlugin
    ).toHaveBeenCalledTimes(1);

    expect(result1).toBe(result2);
  });

  it('should recreate plugin when options change', () => {
    const options1 = { mode: DocumentScannerMode.BASE };
    const options2 = { mode: DocumentScannerMode.FULL };

    useDocumentScanner(options1);
    expect(
      mockVisionCameraProxy.initFrameProcessorPlugin
    ).toHaveBeenCalledTimes(1);

    // Reset memoization
    mockMemoState.mockLastDeps = [];

    useDocumentScanner(options2);
    expect(
      mockVisionCameraProxy.initFrameProcessorPlugin
    ).toHaveBeenCalledTimes(2);
  });
});
