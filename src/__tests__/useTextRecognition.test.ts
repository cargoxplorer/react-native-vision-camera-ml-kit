/**
 * Unit tests for useTextRecognition hook
 * Following TDD: Tests written BEFORE implementation
 */

import { useTextRecognition } from '../textRecognition';
import { TextRecognitionScript } from '../types';
import { mockVisionCameraProxy, mockPlugin } from './__mocks__/VisionCameraProxy';

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

describe('useTextRecognition', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockVisionCameraProxy.initFrameProcessorPlugin.mockReturnValue(mockPlugin);
    mockMemoState.memoizedValue = null;
    mockMemoState.mockLastDeps = [];
  });

  it('should create text recognition plugin', () => {
    const result = useTextRecognition();

    expect(result).toBeDefined();
    expect(result.scanText).toBeDefined();
    expect(typeof result.scanText).toBe('function');
  });

  it('should pass options to plugin creator', () => {
    const options = { language: TextRecognitionScript.CHINESE };
    useTextRecognition(options);

    expect(mockVisionCameraProxy.initFrameProcessorPlugin).toHaveBeenCalledWith(
      'scanTextV2',
      options
    );
  });

  it('should work without options', () => {
    const result = useTextRecognition();

    expect(result).toBeDefined();
    expect(mockVisionCameraProxy.initFrameProcessorPlugin).toHaveBeenCalledWith(
      'scanTextV2',
      {}
    );
  });

  it('should memoize plugin instance with same options', () => {
    const options = { language: TextRecognitionScript.LATIN };

    const result1 = useTextRecognition(options);
    expect(mockVisionCameraProxy.initFrameProcessorPlugin).toHaveBeenCalledTimes(
      1
    );

    const result2 = useTextRecognition(options);
    expect(mockVisionCameraProxy.initFrameProcessorPlugin).toHaveBeenCalledTimes(
      1
    );

    expect(result1).toBe(result2);
  });

  it('should recreate plugin when options change', () => {
    const options1 = { language: TextRecognitionScript.LATIN };
    const options2 = { language: TextRecognitionScript.CHINESE };

    useTextRecognition(options1);
    expect(mockVisionCameraProxy.initFrameProcessorPlugin).toHaveBeenCalledTimes(
      1
    );

    // Reset memoization
    mockMemoState.mockLastDeps = [];

    useTextRecognition(options2);
    expect(mockVisionCameraProxy.initFrameProcessorPlugin).toHaveBeenCalledTimes(
      2
    );
  });
});
