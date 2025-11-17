package com.rnvisioncameramlkit

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager
import com.mrousavy.camera.frameprocessors.FrameProcessorPluginRegistry
import com.rnvisioncameramlkit.utils.Logger

class RNVisionCameraMLKitPackage : ReactPackage {
  companion object {
    init {
      Logger.info("Registering ML Kit frame processor plugins")

      // Text Recognition v2
      FrameProcessorPluginRegistry.addFrameProcessorPlugin("scanTextV2") { proxy, options ->
        TextRecognitionPlugin(proxy, options)
      }

      // Barcode Scanning
      FrameProcessorPluginRegistry.addFrameProcessorPlugin("scanBarcode") { proxy, options ->
        BarcodeScanningPlugin(proxy, options)
      }

      // Additional plugins will be registered here:
      // FrameProcessorPluginRegistry.addFrameProcessorPlugin("scanDocument") { proxy, options ->
      //   DocumentScannerPlugin(proxy, options)
      // }

      Logger.info("ML Kit frame processor plugins registered successfully")
    }
  }

  override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> {
    return listOf(
      StaticTextRecognitionModule(reactContext),
      StaticBarcodeScannerModule(reactContext),
      DocumentScannerModule(reactContext)
    )
  }

  override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> {
    return emptyList()
  }
}
