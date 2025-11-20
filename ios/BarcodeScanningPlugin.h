//
//  BarcodeScanningPlugin.h
//  react-native-vision-camera-ml-kit
//
//  Barcode Scanning Frame Processor Plugin
//  Performs on-device barcode scanning using Google ML Kit
//  Supports 1D and 2D formats with structured data extraction
//

#import <Foundation/Foundation.h>
#import <VisionCamera/FrameProcessorPlugin.h>
#import <VisionCamera/FrameProcessorPluginRegistry.h>
#import <VisionCamera/Frame.h>

@interface BarcodeScanningPlugin : FrameProcessorPlugin

@end
