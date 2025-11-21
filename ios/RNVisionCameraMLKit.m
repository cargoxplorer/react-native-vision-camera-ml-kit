//
//  RNVisionCameraMLKit.m
//  react-native-vision-camera-ml-kit
//

#import <Foundation/Foundation.h>
#import <VisionCamera/FrameProcessorPlugin.h>
#import <VisionCamera/FrameProcessorPluginRegistry.h>
#import <VisionCamera/Frame.h>

// Import the Swift header
#if __has_include("react_native_vision_camera_mlkit_plugin/react_native_vision_camera_mlkit_plugin-Swift.h")
#import "react_native_vision_camera_mlkit_plugin/react_native_vision_camera_mlkit_plugin-Swift.h"
#else
#import "react_native_vision_camera_mlkit_plugin-Swift.h"
#endif

// Register TextRecognitionPlugin
@interface TextRecognitionPlugin (FrameProcessorPluginLoader)
@end

@implementation TextRecognitionPlugin (FrameProcessorPluginLoader)
+ (void)load {
    [FrameProcessorPluginRegistry addFrameProcessorPlugin:@"scanTextV2"
        withInitializer:^FrameProcessorPlugin*(VisionCameraProxyHolder* proxy, NSDictionary* options) {
        return [[TextRecognitionPlugin alloc] initWithProxy:proxy withOptions:options];
    }];
}
@end

// Register BarcodeScanningPlugin
@interface BarcodeScanningPlugin (FrameProcessorPluginLoader)
@end

@implementation BarcodeScanningPlugin (FrameProcessorPluginLoader)
+ (void)load {
    [FrameProcessorPluginRegistry addFrameProcessorPlugin:@"scanBarcode"
        withInitializer:^FrameProcessorPlugin*(VisionCameraProxyHolder* proxy, NSDictionary* options) {
        return [[BarcodeScanningPlugin alloc] initWithProxy:proxy withOptions:options];
    }];
}
@end

// Export Static Modules to React Native
#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_MODULE(StaticTextRecognitionModule, NSObject)
RCT_EXTERN_METHOD(recognizeText:(NSDictionary *)options
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
+ (BOOL)requiresMainQueueSetup { return NO; }
@end

@interface RCT_EXTERN_MODULE(StaticBarcodeScannerModule, NSObject)
RCT_EXTERN_METHOD(scanBarcode:(NSDictionary *)options
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
+ (BOOL)requiresMainQueueSetup { return NO; }
@end
