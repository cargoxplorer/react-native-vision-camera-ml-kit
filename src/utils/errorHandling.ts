/**
 * Error handling utilities
 * Provides consistent error handling across the library
 */

import { Logger } from './Logger';

/**
 * Error codes used throughout the library
 */
export enum ErrorCode {
  // Plugin initialization errors
  PLUGIN_INIT_FAILED = 'PLUGIN_INIT_FAILED',
  MODULE_NOT_FOUND = 'MODULE_NOT_FOUND',

  // Processing errors
  FRAME_PROCESSING_ERROR = 'FRAME_PROCESSING_ERROR',
  IMAGE_PROCESSING_ERROR = 'IMAGE_PROCESSING_ERROR',
  IMAGE_LOAD_ERROR = 'IMAGE_LOAD_ERROR',

  // Platform errors
  PLATFORM_NOT_SUPPORTED = 'PLATFORM_NOT_SUPPORTED',

  // Document scanner specific
  SCANNER_BUSY = 'SCANNER_BUSY',
  SCAN_CANCELLED = 'SCAN_CANCELLED',
  SCAN_FAILED = 'SCAN_FAILED',
  NO_ACTIVITY = 'NO_ACTIVITY',

  // Generic errors
  INVALID_ARGUMENT = 'INVALID_ARGUMENT',
  UNEXPECTED_ERROR = 'UNEXPECTED_ERROR',
}

/**
 * Custom error class for ML Kit operations
 */
export class MLKitError extends Error {
  code: ErrorCode;
  originalError?: Error;

  constructor(code: ErrorCode, message: string, originalError?: Error) {
    super(message);
    this.name = 'MLKitError';
    this.code = code;
    this.originalError = originalError;

    // Maintains proper stack trace for where our error was thrown
    if (Error.captureStackTrace) {
      Error.captureStackTrace(this, MLKitError);
    }
  }
}

/**
 * Create a standardized error
 */
export function createError(
  code: ErrorCode,
  message: string,
  originalError?: Error
): MLKitError {
  const error = new MLKitError(code, message, originalError);
  Logger.error(`[${code}] ${message}`, originalError);
  return error;
}

/**
 * Handle and log errors consistently
 */
export function handleError(
  error: unknown,
  context: string,
  fallbackCode: ErrorCode = ErrorCode.UNEXPECTED_ERROR
): MLKitError {
  if (error instanceof MLKitError) {
    Logger.error(`Error in ${context}:`, error);
    return error;
  }

  if (error instanceof Error) {
    const mlkitError = createError(
      fallbackCode,
      `${context}: ${error.message}`,
      error
    );
    return mlkitError;
  }

  const mlkitError = createError(fallbackCode, `${context}: ${String(error)}`);
  return mlkitError;
}

/**
 * Validate required parameters
 */
export function validateRequired(
  value: unknown,
  paramName: string
): asserts value {
  if (value === null || value === undefined) {
    throw createError(ErrorCode.INVALID_ARGUMENT, `${paramName} is required`);
  }
}

/**
 * Validate URI format
 */
export function validateUri(uri: string): void {
  if (!uri || typeof uri !== 'string') {
    throw createError(
      ErrorCode.INVALID_ARGUMENT,
      'URI must be a non-empty string'
    );
  }

  if (
    !uri.startsWith('file://') &&
    !uri.startsWith('content://') &&
    !uri.startsWith('/')
  ) {
    Logger.warn(
      `URI may be invalid: ${uri}. Expected file://, content://, or absolute path.`
    );
  }
}

/**
 * Check if error is a cancellation
 */
export function isCancellationError(error: unknown): boolean {
  if (error instanceof MLKitError) {
    return error.code === ErrorCode.SCAN_CANCELLED;
  }

  if (error && typeof error === 'object' && 'code' in error) {
    return error.code === 'SCAN_CANCELLED';
  }

  return false;
}
