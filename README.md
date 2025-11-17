# react-native-vision-camera-ml-kit

React Native Vision Camera frame processor plugin for Google ML Kit integration.

## Features

- 📝 **Text Recognition v2** - On-device OCR with support for multiple scripts (Latin, Chinese, Devanagari, Japanese, Korean)
- 📄 **Document Scanner** - High-quality document digitization with edge detection and processing (Android only)
- 📊 **Barcode Scanning** - Fast barcode and QR code detection with structured data extraction

## Installation

```bash
yarn add react-native-vision-camera-ml-kit
```

### Peer Dependencies

This library requires:
- `react-native-vision-camera` >= 4.0.0
- `react-native-worklets-core` >= 1.0.0

```bash
yarn add react-native-vision-camera react-native-worklets-core
```

### iOS Setup

Run pod install:

```bash
cd ios && pod install
```

### Android Setup

No additional steps required. ML Kit models will be downloaded automatically on first use.

## Quick Start

```typescript
import { Camera } from 'react-native-vision-camera';
import { useTextRecognition } from 'react-native-vision-camera-ml-kit';
import { useFrameProcessor } from 'react-native-vision-camera';
import { Worklets } from 'react-native-worklets-core';

function App() {
  const { scanText } = useTextRecognition({ language: 'latin' });

  const onTextDetected = React.useMemo(
    () => Worklets.createRunOnJS((text: string) => {
      console.log('Detected:', text);
    }),
    []
  );

  const frameProcessor = useFrameProcessor((frame) => {
    'worklet';
    const result = scanText(frame);
    if (result?.text) {
      onTextDetected(result.text);
    }
  }, [scanText, onTextDetected]);

  return <Camera frameProcessor={frameProcessor} />;
}
```

## Documentation

(Coming soon)

## License

MIT

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md)
