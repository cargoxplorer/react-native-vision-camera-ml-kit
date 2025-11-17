/**
 * Performance monitoring utilities
 * Helps track and optimize frame processing times
 */

import { Logger, LogLevel } from './Logger';
import { PERFORMANCE_THRESHOLDS } from '../constants';

/**
 * Performance metrics tracker
 */
class PerformanceMonitor {
  private metrics: Map<string, number[]> = new Map();
  private enabled: boolean = false;

  /**
   * Enable performance monitoring
   * Only tracks when log level is DEBUG
   */
  enable(): void {
    this.enabled = Logger.getLogLevel() <= LogLevel.DEBUG;
  }

  /**
   * Disable performance monitoring
   */
  disable(): void {
    this.enabled = false;
  }

  /**
   * Record a performance metric
   */
  record(operation: string, durationMs: number): void {
    if (!this.enabled) return;

    if (!this.metrics.has(operation)) {
      this.metrics.set(operation, []);
    }

    const metrics = this.metrics.get(operation)!;
    metrics.push(durationMs);

    // Keep only last 100 measurements
    if (metrics.length > 100) {
      metrics.shift();
    }

    // Warn if frame processing is slow
    if (operation.includes('frame processing')) {
      if (durationMs > PERFORMANCE_THRESHOLDS.FRAME_PROCESSING_ERROR) {
        Logger.warn(
          `Slow frame processing: ${durationMs.toFixed(2)}ms (target: <${PERFORMANCE_THRESHOLDS.FRAME_PROCESSING_WARNING}ms)`
        );
      }
    }
  }

  /**
   * Get statistics for an operation
   */
  getStats(operation: string): {
    count: number;
    avg: number;
    min: number;
    max: number;
    p95: number;
  } | null {
    const metrics = this.metrics.get(operation);
    if (!metrics || metrics.length === 0) return null;

    const sorted = [...metrics].sort((a, b) => a - b);
    const sum = sorted.reduce((acc, val) => acc + val, 0);

    return {
      count: sorted.length,
      avg: sum / sorted.length,
      min: sorted[0],
      max: sorted[sorted.length - 1],
      p95: sorted[Math.floor(sorted.length * 0.95)],
    };
  }

  /**
   * Get all performance statistics
   */
  getAllStats(): Record<string, ReturnType<typeof this.getStats>> {
    const result: Record<string, ReturnType<typeof this.getStats>> = {};

    for (const [operation] of this.metrics) {
      result[operation] = this.getStats(operation);
    }

    return result;
  }

  /**
   * Clear all metrics
   */
  clear(): void {
    this.metrics.clear();
  }

  /**
   * Log performance summary
   */
  logSummary(): void {
    if (!this.enabled || this.metrics.size === 0) {
      Logger.debug('No performance metrics recorded');
      return;
    }

    Logger.debug('=== Performance Summary ===');
    for (const [operation, stats] of Object.entries(this.getAllStats())) {
      if (stats) {
        Logger.debug(
          `${operation}: avg=${stats.avg.toFixed(2)}ms, p95=${stats.p95.toFixed(2)}ms, min=${stats.min.toFixed(2)}ms, max=${stats.max.toFixed(2)}ms (n=${stats.count})`
        );
      }
    }
    Logger.debug('==========================');
  }
}

export const performanceMonitor = new PerformanceMonitor();

/**
 * Measure execution time of a function
 */
export function measure<T>(operation: string, fn: () => T): T {
  const startTime = performance.now();

  try {
    const result = fn();
    const duration = performance.now() - startTime;

    Logger.performance(operation, duration);
    performanceMonitor.record(operation, duration);

    return result;
  } catch (error) {
    const duration = performance.now() - startTime;
    Logger.performance(`${operation} (error)`, duration);
    performanceMonitor.record(`${operation} (error)`, duration);
    throw error;
  }
}

/**
 * Measure execution time of an async function
 */
export async function measureAsync<T>(
  operation: string,
  fn: () => Promise<T>
): Promise<T> {
  const startTime = performance.now();

  try {
    const result = await fn();
    const duration = performance.now() - startTime;

    Logger.performance(operation, duration);
    performanceMonitor.record(operation, duration);

    return result;
  } catch (error) {
    const duration = performance.now() - startTime;
    Logger.performance(`${operation} (error)`, duration);
    performanceMonitor.record(`${operation} (error)`, duration);
    throw error;
  }
}
