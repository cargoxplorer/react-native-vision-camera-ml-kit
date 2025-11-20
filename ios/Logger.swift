//
//  Logger.swift
//  react-native-vision-camera-ml-kit
//
//  Custom logger for RNVisionCameraMLKit with configurable log levels
//  to control verbosity and minimize performance impact.
//

import Foundation
import os.log

@objc public class Logger: NSObject {
    public static let TAG = "RNVisionCameraMLKit"

    @objc public enum LogLevel: Int {
        case debug = 0
        case info = 1
        case warn = 2
        case error = 3
        case none = 4

        var name: String {
            switch self {
            case .debug: return "DEBUG"
            case .info: return "INFO"
            case .warn: return "WARN"
            case .error: return "ERROR"
            case .none: return "NONE"
            }
        }

        var osLogType: OSLogType {
            switch self {
            case .debug: return .debug
            case .info: return .info
            case .warn: return .default
            case .error: return .error
            case .none: return .fault
            }
        }
    }

    private static var currentLogLevel: LogLevel = .debug
    private static let logLock = NSLock()

    /// Set the current log level. Logs below this level will be ignored.
    @objc public static func setLogLevel(_ level: LogLevel) {
        logLock.lock()
        currentLogLevel = level
        logLock.unlock()
        info("Log level set to: \(level.name)")
    }

    /// Get the current log level
    @objc public static func getLogLevel() -> LogLevel {
        logLock.lock()
        defer { logLock.unlock() }
        return currentLogLevel
    }

    /// Check if debug logging is enabled (for conditional log checks to avoid string allocations)
    @objc public static func isDebugEnabled() -> Bool {
        logLock.lock()
        defer { logLock.unlock() }
        return currentLogLevel.rawValue <= LogLevel.debug.rawValue
    }

    /// Log a debug message
    @objc public static func debug(_ message: String, tag: String = TAG) {
        logLock.lock()
        let shouldLog = currentLogLevel.rawValue <= LogLevel.debug.rawValue
        logLock.unlock()

        if shouldLog {
            os_log("%{public}@", log: OSLog(subsystem: tag, category: "Debug"), type: .debug, message)
        }
    }

    /// Log an info message
    @objc public static func info(_ message: String, tag: String = TAG) {
        logLock.lock()
        let shouldLog = currentLogLevel.rawValue <= LogLevel.info.rawValue
        logLock.unlock()

        if shouldLog {
            os_log("%{public}@", log: OSLog(subsystem: tag, category: "Info"), type: .info, message)
        }
    }

    /// Log a warning message
    @objc public static func warn(_ message: String, tag: String = TAG) {
        logLock.lock()
        let shouldLog = currentLogLevel.rawValue <= LogLevel.warn.rawValue
        logLock.unlock()

        if shouldLog {
            os_log("%{public}@", log: OSLog(subsystem: tag, category: "Warning"), type: .default, message)
        }
    }

    /// Log an error message
    @objc public static func error(_ message: String, error: Error? = nil, tag: String = TAG) {
        logLock.lock()
        let shouldLog = currentLogLevel.rawValue <= LogLevel.error.rawValue
        logLock.unlock()

        if shouldLog {
            let errorMessage = error != nil ? "\(message): \(error!.localizedDescription)" : message
            os_log("%{public}@", log: OSLog(subsystem: tag, category: "Error"), type: .error, errorMessage)
        }
    }

    /// Log performance metrics (DEBUG level)
    @objc public static func performance(_ message: String, durationMs: Int64, tag: String = TAG) {
        debug("⏱️ \(message): \(durationMs)ms", tag: tag)
    }
}
