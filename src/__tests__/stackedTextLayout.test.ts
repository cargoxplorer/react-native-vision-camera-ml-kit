import { createTextRecognitionPlugin } from '../textRecognition';
import { recognizeTextFromImage } from '../staticTextRecognition';
import { captureAndRecognizeText } from '../captureAndRecognizeText';
import {
  mockVisionCameraProxy,
  mockPlugin,
} from './__mocks__/VisionCameraProxy';
import { mockNativeModule } from './__mocks__/NativeModules';

describe('textLayout', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockVisionCameraProxy.initFrameProcessorPlugin.mockReturnValue(mockPlugin);
    mockNativeModule.recognizeText.mockResolvedValue(null);
  });

  it.each(['horizontal', 'stacked', 'auto'] as const)(
    'passes textLayout "%s" to the frame processor plugin',
    (textLayout) => {
      createTextRecognitionPlugin({ language: 'latin', textLayout });

      expect(
        mockVisionCameraProxy.initFrameProcessorPlugin
      ).toHaveBeenCalledWith('scanTextV2', { language: 'latin', textLayout });
    }
  );

  it('forwards textLayout to the static module', async () => {
    await recognizeTextFromImage({
      uri: 'file:///door.jpg',
      textLayout: 'stacked',
    });

    expect(mockNativeModule.recognizeText).toHaveBeenCalledWith({
      uri: 'file:///door.jpg',
      textLayout: 'stacked',
    });
  });

  it('carries textLayout from capture options into recognition', async () => {
    const camera = {
      takePhoto: jest.fn().mockResolvedValue({ path: '/tmp/photo.jpg' }),
    };

    await captureAndRecognizeText(camera as never, { textLayout: 'auto' });

    expect(mockNativeModule.recognizeText).toHaveBeenCalledWith({
      uri: 'file:///tmp/photo.jpg',
      language: undefined,
      textLayout: 'auto',
      orientation: 0,
    });
  });
});
