/**
 * Centralized API client for admin_web.
 *
 * Features:
 * - Generic TypeScript inference for response types.
 * - Standardized error handling: extracts `error` field from JSON body.
 * - 30-second timeout to prevent hanging requests.
 * - Supports GET, POST, PUT, DELETE with typed body/response.
 */

import { applyCDN, removeCDN } from './cdn-rewriter';

class ApiError extends Error {
  constructor(
    public statusCode: number,
    message: string
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

async function request<TResponse = unknown>(
  url: string,
  options: RequestInit = {}
): Promise<TResponse> {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 30_000);

  try {
    // 1. If sending request body, parse through removeCDN (which is now a no-op)
    if (options.body && typeof options.body === 'string') {
      try {
        let parsed = JSON.parse(options.body);
        parsed = removeCDN(parsed);
        options.body = JSON.stringify(parsed);
      } catch (_) {
        // Do nothing on string parse fail, keep original body
      }
    }

    const headers = new Headers(options.headers);
    const isFormData = options.body && (
      options.body instanceof FormData ||
      options.body.constructor?.name === 'FormData' ||
      (typeof options.body === 'object' && typeof (options.body as any).append === 'function')
    );

    if (isFormData) {
      headers.delete('Content-Type');
    } else if (options.body && !headers.has('Content-Type')) {
      headers.set('Content-Type', 'application/json');
    }

    console.log('[apiClient] request:', url, {
      method: options.method,
      hasBody: !!options.body,
      isFormData: !!isFormData,
      contentType: headers.get('Content-Type')
    });

    const res = await fetch(url, {
      ...options,
      signal: controller.signal,
      headers,
    });

    // Handle no-content responses (e.g. 204)
    if (res.status === 204 || res.headers.get('content-length') === '0') {
      return undefined as TResponse;
    }

    let data = await res.json().catch(() => ({}));

    // 2. If receiving response data, rewrite Supabase URLs to CDN URLs
    data = applyCDN(data);

    if (!res.ok) {
      const message =
        (data as any)?.error ||
        (data as any)?.message ||
        `Request failed with status ${res.status}`;
      throw new ApiError(res.status, message);
    }

    return data as TResponse;
  } catch (err) {
    if ((err as Error).name === 'AbortError') {
      throw new ApiError(408, 'Request timed out after 30 seconds');
    }
    throw err;
  } finally {
    clearTimeout(timeoutId);
  }
}

export const apiClient = {
  get<TResponse = unknown>(url: string, options?: RequestInit) {
    return request<TResponse>(url, { ...options, method: 'GET' });
  },

  post<TResponse = unknown>(url: string, body: unknown, options?: RequestInit) {
    return request<TResponse>(url, {
      ...options,
      method: 'POST',
      body: JSON.stringify(body),
    });
  },

  put<TResponse = unknown>(url: string, body: unknown, options?: RequestInit) {
    return request<TResponse>(url, {
      ...options,
      method: 'PUT',
      body: JSON.stringify(body),
    });
  },

  delete<TResponse = unknown>(url: string, body?: unknown, options?: RequestInit) {
    return request<TResponse>(url, {
      ...options,
      method: 'DELETE',
      body: body ? JSON.stringify(body) : undefined,
    });
  },

  /**
   * Upload multipart/form-data (e.g. file uploads).
   * Note: Do NOT set Content-Type header — browser sets it with boundary automatically.
   */
  upload<TResponse = unknown>(url: string, formData: FormData, options?: RequestInit) {
    const { headers, ...rest } = options || {};
    return request<TResponse>(url, {
      ...rest,
      method: 'POST',
      headers: { ...(headers as Record<string, string> | undefined) },
      body: formData,
    });
  },
};

export { ApiError };
