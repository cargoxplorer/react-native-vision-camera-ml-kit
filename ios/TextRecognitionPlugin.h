//
//  TextRecognitionPlugin.h
//  react-native-vision-camera-ml-kit
//
//  Text Recognition v2 Frame Processor Plugin
//  Performs on-device text recognition using Google ML Kit
//  Supports multiple scripts: Latin, Chinese, Devanagari, Japanese, Korean
//

#import <Foundation/Foundation.h>
#import <VisionCamera/FrameProcessorPlugin.h>
#import <VisionCamera/FrameProcessorPluginRegistry.h>
#import <VisionCamera/Frame.h>

@interface TextRecognitionPlugin : FrameProcessorPlugin

@end
