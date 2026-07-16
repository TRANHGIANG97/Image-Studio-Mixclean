/** Helpers for PSD import progress logs shown in the admin UI. */

export type PsdImportLogger = (msg: string) => void | Promise<void>;

/** Yield to the browser so React can paint log lines during heavy sync work. */
export async function yieldToUi(): Promise<void> {
  await new Promise<void>((resolve) => {
    if (typeof requestAnimationFrame === 'function') {
      requestAnimationFrame(() => setTimeout(resolve, 0));
    } else {
      setTimeout(resolve, 0);
    }
  });
}

export function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes < 0) return '?';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
}

export function formatDurationMs(ms: number): string {
  if (ms < 1000) return `${Math.round(ms)}ms`;
  return `${(ms / 1000).toFixed(1)}s`;
}

export function createTimedPsdLogger(onLog?: PsdImportLogger, startedAt = Date.now()) {
  const log = async (msg: string) => {
    const elapsed = formatDurationMs(Date.now() - startedAt);
    await onLog?.(`[${elapsed}] ${msg}`);
    await yieldToUi();
  };

  const step = async <T>(label: string, work: () => Promise<T> | T): Promise<T> => {
    const t0 = Date.now();
    await log(`→ ${label}...`);
    try {
      const result = await work();
      await log(`✓ ${label} xong (${formatDurationMs(Date.now() - t0)})`);
      return result;
    } catch (error) {
      await log(`✗ ${label} thất bại sau ${formatDurationMs(Date.now() - t0)}: ${(error as Error)?.message || error}`);
      throw error;
    }
  };

  return { log, step, startedAt };
}
