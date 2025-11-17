import { Stack } from 'expo-router';
import { useEffect } from 'react';
import { Logger, LogLevel } from 'react-native-vision-camera-ml-kit';

export default function RootLayout() {
  useEffect(() => {
    // Enable debug logging in development
    Logger.setLogLevel(LogLevel.DEBUG);
  }, []);

  return (
    <Stack>
      <Stack.Screen
        name="index"
        options={{
          title: 'ML Kit Examples',
          headerLargeTitle: true,
        }}
      />
      <Stack.Screen
        name="text-recognition"
        options={{
          title: 'Text Recognition',
          presentation: 'card',
        }}
      />
      <Stack.Screen
        name="barcode-scanner"
        options={{
          title: 'Barcode Scanner',
          presentation: 'card',
        }}
      />
      <Stack.Screen
        name="document-scanner"
        options={{
          title: 'Document Scanner',
          presentation: 'card',
        }}
      />
    </Stack>
  );
}
