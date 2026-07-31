//
//  BarcodeScanningPlugin.swift
//  react-native-vision-camera-ml-kit
//

import Foundation
import VisionCamera
import MLKitVision
import MLKitCommon
import MLKitBarcodeScanning
import CoreImage
import CoreMedia

/// Normalized scan region (0..1) in the upright frame, or in a cover-fitted
/// preview view when viewport dims are set
struct ScanRegionSpec {
    let left: CGFloat
    let top: CGFloat
    let width: CGFloat
    let height: CGFloat
    let viewportWidth: CGFloat?
    let viewportHeight: CGFloat?

    static func parse(_ value: Any?) -> ScanRegionSpec? {
        guard let map = value as? [AnyHashable: Any],
              let left = (map["left"] as? NSNumber)?.doubleValue,
              let top = (map["top"] as? NSNumber)?.doubleValue,
              let width = (map["width"] as? NSNumber)?.doubleValue,
              let height = (map["height"] as? NSNumber)?.doubleValue else {
            return nil
        }
        if width <= 0 || height <= 0 {
            Logger.warn("Invalid scanRegion: width and height must be positive. Got width=\(width), height=\(height)")
            return nil
        }
        return ScanRegionSpec(
            left: CGFloat(left),
            top: CGFloat(top),
            width: CGFloat(width),
            height: CGFloat(height),
            viewportWidth: (map["viewportWidth"] as? NSNumber).map { CGFloat($0.doubleValue) },
            viewportHeight: (map["viewportHeight"] as? NSNumber).map { CGFloat($0.doubleValue) }
        )
    }
}

@objc(BarcodeScanningPlugin)
public class BarcodeScanningPlugin: FrameProcessorPlugin {

    private var scanner: BarcodeScanner!
    private var detectInvertedBarcodes: Bool = false
    private var tryRotations: Bool = true
    private var scanRegion: ScanRegionSpec?
    private var warnedInvalidRegion = false

    public override init(proxy: VisionCameraProxyHolder, options: [AnyHashable: Any]! = [:]) {
        super.init(proxy: proxy, options: options)

        Logger.info("Initializing barcode scanner")

        // Extract options
        detectInvertedBarcodes = options["detectInvertedBarcodes"] as? Bool ?? false
        if detectInvertedBarcodes {
            Logger.warn("⚠️ Inverted barcode detection ENABLED - adds processing time per frame when no barcodes found. Only enable if you specifically need white-on-black barcodes.")
        }

        tryRotations = options["tryRotations"] as? Bool ?? true
        if !tryRotations {
            Logger.info("90 degree rotation attempts DISABLED - only using current camera rotation")
        }

        scanRegion = ScanRegionSpec.parse(options["scanRegion"])
        if let region = scanRegion {
            Logger.info("Scan region enabled: \(region) — decoding restricted to this area")
        }

        // Parse formats
        let formats = options["formats"] as? [String]
        let scannerOptions = createScannerOptions(formats: formats)

        scanner = BarcodeScanner.barcodeScanner(options: scannerOptions)
        Logger.info("Barcode scanner initialized successfully")
    }

    deinit {
        // Clean up ML Kit resources when plugin is deallocated
        // Swift ARC will handle the deallocation, but we log for debugging
        Logger.debug("BarcodeScanningPlugin deallocating - ML Kit scanner resources will be freed")
        // Note: ML Kit resources are automatically freed by ARC when scanner is deallocated
    }

    private func createScannerOptions(formats: [String]?) -> BarcodeScannerOptions {
        guard let formats = formats, !formats.isEmpty else {
            Logger.info("No format filter specified, scanning all barcode formats")
            return BarcodeScannerOptions(formats: .all)
        }

        Logger.debug("Parsing \(formats.count) barcode format(s) from options")

        var combinedFormats: BarcodeFormat = []
        for formatString in formats {
            if let parsedFormat = parseBarcodeFormat(formatString) {
                combinedFormats.insert(parsedFormat)
                Logger.debug("Successfully parsed format: '\(formatString)'")
            } else {
                Logger.error("FAILED to parse barcode format: '\(formatString)'")
            }
        }

        if combinedFormats.isEmpty {
            Logger.error("No valid barcode formats could be parsed! Falling back to all formats")
            return BarcodeScannerOptions(formats: .all)
        }

        Logger.info("Scanning barcode format(s) with combined mask")
        return BarcodeScannerOptions(formats: combinedFormats)
    }

    public override func callback(_ frame: Frame, withArguments arguments: [AnyHashable: Any]?) -> Any? {
        let startTime = Date()

        do {
            let orientation = frame.orientation
            let baseOrientation = getOrientation(orientation: orientation)

            if Logger.isDebugEnabled() {
                Logger.debug("Processing frame: \(frame.width)x\(frame.height), orientation: \(orientation.rawValue)")
            }

            // Build list of orientations to try
            let orientations: [UIImage.Orientation] = tryRotations
                ? [baseOrientation, rotateOrientation90(baseOrientation)]
                : [baseOrientation]

            var barcodes: [Barcode] = []

            // Clone the camera buffer to UIImage to release the original buffer immediately
            // This prevents buffer exhaustion issues when ML Kit processing takes longer than camera frame rate
            guard let baseImage = ImageUtils.imageFromSampleBuffer(frame.buffer) else {
                Logger.error("Failed to clone sample buffer to image")
                return nil
            }

            let region = ScanRegionSpec.parse(arguments?["scanRegion"]) ?? scanRegion

            // Crop to the scan region so ML Kit never sees barcodes outside it
            var workingImage = baseImage
            var cropOffsetX: CGFloat = 0
            var cropOffsetY: CGFloat = 0
            if let region = region {
                let rawWidth = baseImage.size.width
                let rawHeight = baseImage.size.height
                guard let crop = Self.computeCropRect(
                    region: region,
                    rawWidth: rawWidth,
                    rawHeight: rawHeight,
                    rotationDegrees: Self.rotationDegrees(for: baseOrientation)
                ) else {
                    if !warnedInvalidRegion {
                        warnedInvalidRegion = true
                        Logger.warn("scanRegion resolves to an empty area — skipping decode. Region: \(region), frame: \(rawWidth)x\(rawHeight)")
                    }
                    return nil
                }
                if crop.minX > 0 || crop.minY > 0 || crop.width < rawWidth || crop.height < rawHeight {
                    guard let cgImage = baseImage.cgImage, let croppedCG = cgImage.cropping(to: crop) else {
                        Logger.error("Failed to crop frame to scan region")
                        return nil
                    }
                    workingImage = UIImage(cgImage: croppedCG, scale: 1.0, orientation: .up)
                    cropOffsetX = crop.minX
                    cropOffsetY = crop.minY
                }
            }

            // 1. Try normal image at current orientation
            let visionImage = VisionImage(image: workingImage)
            visionImage.orientation = orientations[0]
            barcodes = try scanner.results(in: visionImage)

            if !barcodes.isEmpty {
                if Logger.isDebugEnabled() {
                    Logger.debug("Found \(barcodes.count) barcode(s) at orientation \(orientations[0].rawValue)")
                }
            } else if tryRotations && orientations.count > 1 {
                // 2. Try normal image at 90 degree rotation
                if Logger.isDebugEnabled() {
                    Logger.debug("No barcodes at orientation \(orientations[0].rawValue), trying \(orientations[1].rawValue)")
                }
                visionImage.orientation = orientations[1]
                barcodes = try scanner.results(in: visionImage)

                if !barcodes.isEmpty && Logger.isDebugEnabled() {
                    Logger.debug("Found \(barcodes.count) barcode(s) at orientation \(orientations[1].rawValue)")
                }
            }

            // 3. If no barcodes found and inverted detection is enabled, try inverted images
            if barcodes.isEmpty && detectInvertedBarcodes {
                if Logger.isDebugEnabled() {
                    Logger.debug("No barcodes in normal image, attempting inverted image scan...")
                }

                let invertStartTime = Date()

                if let workingCG = workingImage.cgImage,
                   let invertedCI = ImageUtils.invertImage(CIImage(cgImage: workingCG)),
                   let invertedImage = ImageUtils.uiImageFromCIImage(invertedCI) {
                    // Try inverted at current orientation
                    let invertedVisionImage = VisionImage(image: invertedImage)
                    invertedVisionImage.orientation = orientations[0]
                    barcodes = try scanner.results(in: invertedVisionImage)

                    if !barcodes.isEmpty {
                        if Logger.isDebugEnabled() {
                            Logger.debug("Found \(barcodes.count) barcode(s) in inverted image at orientation \(orientations[0].rawValue)")
                        }
                    } else if tryRotations && orientations.count > 1 {
                        // Try inverted at 90 degree rotation
                        if Logger.isDebugEnabled() {
                            Logger.debug("No barcodes in inverted at \(orientations[0].rawValue), trying \(orientations[1].rawValue)")
                        }
                        invertedVisionImage.orientation = orientations[1]
                        barcodes = try scanner.results(in: invertedVisionImage)

                        if !barcodes.isEmpty && Logger.isDebugEnabled() {
                            Logger.debug("Found \(barcodes.count) barcode(s) in inverted image at orientation \(orientations[1].rawValue)")
                        }
                    }
                }

                let invertTime = Int64(Date().timeIntervalSince(invertStartTime) * 1000)
                Logger.performance("Inverted image scan", durationMs: invertTime)
            }

            let processingTime = Int64(Date().timeIntervalSince(startTime) * 1000)
            Logger.performance("Barcode scanning processing", durationMs: processingTime)

            if barcodes.isEmpty {
                if Logger.isDebugEnabled() {
                    Logger.debug("No barcodes detected in frame (tried all orientations)")
                }
                return nil
            }

            if Logger.isDebugEnabled() {
                Logger.debug("Barcodes detected: \(barcodes.count) barcode(s)")
            }

            // iOS ML Kit returns coordinates in the source image space, so the
            // crop offset shifts them back to the full frame regardless of rotation
            let result: [String: Any] = ["barcodes": processBarcodes(barcodes, offsetX: cropOffsetX, offsetY: cropOffsetY)]
            return result

        } catch {
            let processingTime = Int64(Date().timeIntervalSince(startTime) * 1000)
            Logger.error("Exception during barcode scanning: \(error.localizedDescription)")
            Logger.performance("Barcode scanning processing (error)", durationMs: processingTime)
            return nil
        }
    }
    
    // MARK: - Image Processing
    
    /// Rotate orientation by 90 degrees clockwise
    private func rotateOrientation90(_ orientation: UIImage.Orientation) -> UIImage.Orientation {
        switch orientation {
        case .up: return .right
        case .right: return .down
        case .down: return .left
        case .left: return .up
        case .upMirrored: return .rightMirrored
        case .rightMirrored: return .downMirrored
        case .downMirrored: return .leftMirrored
        case .leftMirrored: return .upMirrored
        @unknown default: return .right
        }
    }
    
    /// Degrees the raw buffer must be rotated clockwise to appear upright
    private static func rotationDegrees(for orientation: UIImage.Orientation) -> Int {
        switch orientation {
        case .up, .upMirrored: return 0
        case .right, .rightMirrored: return 90
        case .down, .downMirrored: return 180
        case .left, .leftMirrored: return 270
        @unknown default: return 0
        }
    }

    /// Raw-buffer crop rect for a scan region defined in the upright frame
    /// (or in a cover-fitted preview view when viewport dims are present).
    /// Returns nil when the region resolves to an empty area.
    static func computeCropRect(
        region: ScanRegionSpec,
        rawWidth: CGFloat,
        rawHeight: CGFloat,
        rotationDegrees: Int
    ) -> CGRect? {
        let rot = ((rotationDegrees % 360) + 360) % 360
        let uprightW = (rot == 90 || rot == 270) ? rawHeight : rawWidth
        let uprightH = (rot == 90 || rot == 270) ? rawWidth : rawHeight

        var l = region.left
        var t = region.top
        var r = region.left + region.width
        var b = region.top + region.height

        if let vw = region.viewportWidth, let vh = region.viewportHeight, vw > 0, vh > 0 {
            // Map view-relative → frame-relative through the cover-fit crop
            let scale = max(vw / uprightW, vh / uprightH)
            let padX = (uprightW * scale - vw) / 2
            let padY = (uprightH * scale - vh) / 2
            l = (l * vw + padX) / scale / uprightW
            t = (t * vh + padY) / scale / uprightH
            r = (r * vw + padX) / scale / uprightW
            b = (b * vh + padY) / scale / uprightH
        }

        l = min(max(l, 0), 1)
        t = min(max(t, 0), 1)
        r = min(max(r, 0), 1)
        b = min(max(b, 0), 1)
        if r <= l || b <= t {
            return nil
        }

        // Upright-normalized → raw buffer space (inverse of the display rotation)
        let rawL: CGFloat
        let rawT: CGFloat
        let rawR: CGFloat
        let rawB: CGFloat
        switch rot {
        case 90:
            rawL = t; rawT = 1 - r; rawR = b; rawB = 1 - l
        case 180:
            rawL = 1 - r; rawT = 1 - b; rawR = 1 - l; rawB = 1 - t
        case 270:
            rawL = 1 - b; rawT = l; rawR = 1 - t; rawB = r
        default:
            rawL = l; rawT = t; rawR = r; rawB = b
        }

        let left = min(max(floor(rawL * rawWidth), 0), rawWidth - 1)
        let top = min(max(floor(rawT * rawHeight), 0), rawHeight - 1)
        let right = min(max(ceil(rawR * rawWidth), left + 1), rawWidth)
        let bottom = min(max(ceil(rawB * rawHeight), top + 1), rawHeight)
        return CGRect(x: left, y: top, width: right - left, height: bottom - top)
    }

    // MARK: - Orientation Mapping

    private func getOrientation(orientation: UIImage.Orientation) -> UIImage.Orientation {
        switch orientation {
        case .up:
            return .up
        case .left:
            return .right  // Swap left and right
        case .down:
            return .down
        case .right:
            return .left   // Swap left and right
        default:
            return .up
        }
    }

    // MARK: - Format Parsing

    private func parseBarcodeFormat(_ format: String) -> BarcodeFormat? {
        switch format.lowercased() {
        case "codabar": return .codaBar
        case "code39": return .code39
        case "code93": return .code93
        case "code128": return .code128
        case "ean8": return .EAN8
        case "ean13": return .EAN13
        case "itf": return .ITF
        case "upca": return .UPCA
        case "upce": return .UPCE
        case "aztec": return .aztec
        case "datamatrix": return .dataMatrix
        case "pdf417": return .PDF417
        case "qrcode": return .qrCode
        default:
            Logger.warn("Unknown barcode format: \(format)")
            return nil
        }
    }

    private func barcodeFormatToString(_ format: BarcodeFormat) -> String {
        switch format {
        case .codaBar: return "codabar"
        case .code39: return "code39"
        case .code93: return "code93"
        case .code128: return "code128"
        case .EAN8: return "ean8"
        case .EAN13: return "ean13"
        case .ITF: return "itf"
        case .UPCA: return "upca"
        case .UPCE: return "upce"
        case .aztec: return "aztec"
        case .dataMatrix: return "datamatrix"
        case .PDF417: return "pdf417"
        case .qrCode: return "qrcode"
        default: return "unknown"
        }
    }

    private func valueTypeToString(_ valueType: BarcodeValueType) -> String {
        switch valueType {
        case .text: return "text"
        case .URL: return "url"
        case .email: return "email"
        case .phone: return "phone"
        case .SMS: return "sms"
        case .wiFi: return "wifi"
        case .geographicCoordinates: return "geo"
        case .contactInfo: return "contact"
        case .calendarEvent: return "calendarEvent"
        case .driversLicense: return "driverLicense"
        default: return "unknown"
        }
    }

    // MARK: - Barcode Processing

    private func processBarcodes(_ barcodes: [Barcode], offsetX: CGFloat = 0, offsetY: CGFloat = 0) -> [[String: Any]] {
        var result: [[String: Any]] = []
        for barcode in barcodes {
            let processed = processBarcode(barcode, offsetX: offsetX, offsetY: offsetY)
            result.append(processed)
        }
        return result
    }

    private func processBarcode(_ barcode: Barcode, offsetX: CGFloat, offsetY: CGFloat) -> [String: Any] {
        var dict: [String: Any] = [:]

        dict["rawValue"] = barcode.rawValue ?? ""
        dict["displayValue"] = barcode.displayValue ?? ""
        dict["format"] = barcodeFormatToString(barcode.format)
        dict["valueType"] = valueTypeToString(barcode.valueType)
        dict["frame"] = processRect(barcode.frame, offsetX: offsetX, offsetY: offsetY)
        dict["cornerPoints"] = processCornerPoints(barcode.cornerPoints, offsetX: offsetX, offsetY: offsetY)

        addStructuredData(to: &dict, barcode: barcode)

        return dict
    }

    private func addStructuredData(to dict: inout [String: Any], barcode: Barcode) {
        switch barcode.valueType {
        case .wiFi:
            if let wifi = barcode.wifi {
                dict["wifi"] = processWifi(wifi)
            }
        case .URL:
            if let url = barcode.url?.url {
                dict["url"] = url
            }
        case .email:
            if let email = barcode.email?.address {
                dict["email"] = email
            }
        case .phone:
            if let phone = barcode.phone?.number {
                dict["phone"] = phone
            }
        case .SMS:
            if let sms = barcode.sms {
                dict["sms"] = processSms(sms)
            }
        case .geographicCoordinates:
            if let geo = barcode.geoPoint {
                dict["geo"] = processGeo(geo)
            }
        case .contactInfo:
            if let contact = barcode.contactInfo {
                dict["contact"] = processContact(contact)
            }
        case .calendarEvent:
            if let event = barcode.calendarEvent {
                dict["calendarEvent"] = processCalendarEvent(event)
            }
        case .driversLicense:
            if let license = barcode.driverLicense {
                dict["driverLicense"] = processDriverLicense(license)
            }
        default:
            break
        }
    }

    private func processWifi(_ wifi: BarcodeWifi) -> [String: Any] {
        var dict: [String: Any] = [:]
        dict["ssid"] = wifi.ssid ?? ""
        dict["password"] = wifi.password ?? ""

        let encryptionType: String
        switch wifi.type {
        case .open: encryptionType = "open"
        case .WPA: encryptionType = "wpa"
        case .WEP: encryptionType = "wep"
        default: encryptionType = "unknown"
        }
        dict["encryptionType"] = encryptionType

        return dict
    }

    private func processSms(_ sms: BarcodeSMS) -> [String: Any] {
        var dict: [String: Any] = [:]
        dict["phoneNumber"] = sms.phoneNumber ?? ""
        dict["message"] = sms.message ?? ""
        return dict
    }

    private func processGeo(_ geo: BarcodeGeoPoint) -> [String: Any] {
        var dict: [String: Any] = [:]
        dict["latitude"] = geo.latitude
        dict["longitude"] = geo.longitude
        return dict
    }

    private func processContact(_ contact: BarcodeContactInfo) -> [String: Any] {
        var dict: [String: Any] = [:]

        if let name = contact.name {
            let firstName = name.first ?? ""
            let lastName = name.last ?? ""
            let fullName = "\(firstName) \(lastName)".trimmingCharacters(in: .whitespaces)
            if !fullName.isEmpty {
                dict["name"] = fullName
            }
        }

        if let organization = contact.organization, !organization.isEmpty {
            dict["organization"] = organization
        }

        if let phones = contact.phones, !phones.isEmpty {
            dict["phones"] = phones.compactMap { $0.number }
        }

        if let emails = contact.emails, !emails.isEmpty {
            dict["emails"] = emails.compactMap { $0.address }
        }

        if let urls = contact.urls, !urls.isEmpty {
            dict["urls"] = urls
        }

        if let addresses = contact.addresses, !addresses.isEmpty {
            dict["addresses"] = addresses.compactMap { $0.addressLines?.joined(separator: ", ") }
        }

        return dict
    }

    // Reusable DateFormatter to avoid expensive allocations per frame
    private static let calendarEventDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyyMMdd'T'HHmmss'Z'"
        formatter.timeZone = TimeZone(abbreviation: "UTC")
        return formatter
    }()

    private func processCalendarEvent(_ event: BarcodeCalendarEvent) -> [String: Any] {
        var dict: [String: Any] = [:]
        dict["summary"] = event.summary ?? ""
        dict["description"] = event.eventDescription ?? ""
        dict["location"] = event.location ?? ""

        if let start = event.start {
            dict["start"] = Self.calendarEventDateFormatter.string(from: start)
        }
        if let end = event.end {
            dict["end"] = Self.calendarEventDateFormatter.string(from: end)
        }

        return dict
    }

    private func processDriverLicense(_ license: BarcodeDriverLicense) -> [String: Any] {
        var dict: [String: Any] = [:]
        dict["firstName"] = license.firstName ?? ""
        dict["lastName"] = license.lastName ?? ""
        dict["middleName"] = license.middleName ?? ""
        dict["gender"] = license.gender ?? ""
        dict["addressStreet"] = license.addressStreet ?? ""
        dict["addressCity"] = license.addressCity ?? ""
        dict["addressState"] = license.addressState ?? ""
        dict["addressZip"] = license.addressZip ?? ""
        dict["licenseNumber"] = license.licenseNumber ?? ""
        dict["birthDate"] = license.birthDate ?? ""
        dict["issuingCountry"] = license.issuingCountry ?? ""
        return dict
    }

    // MARK: - Geometry Processing

    private func processRect(_ rect: CGRect, offsetX: CGFloat = 0, offsetY: CGFloat = 0) -> [String: CGFloat] {
        var dict: [String: CGFloat] = [:]
        dict["x"] = rect.midX + offsetX
        dict["y"] = rect.midY + offsetY
        dict["width"] = rect.width
        dict["height"] = rect.height
        return dict
    }

    private func processCornerPoints(_ cornerPoints: [NSValue]?, offsetX: CGFloat = 0, offsetY: CGFloat = 0) -> [[String: Int]] {
        guard let cornerPoints = cornerPoints else { return [] }
        var result: [[String: Int]] = []
        for pointValue in cornerPoints {
            let point = pointValue.cgPointValue
            var pointDict: [String: Int] = [:]
            pointDict["x"] = Int(point.x + offsetX)
            pointDict["y"] = Int(point.y + offsetY)
            result.append(pointDict)
        }
        return result
    }
}
