/**
 * Centralized API client for admin_web.
 *
 * Features:
 * - Generic TypeScript inference for response types.
 * - Standardized error handling: extracts `error` field from JSON body.
 * - 30-second timeout to prevent hanging requests.
 * - Supports GET, POST, PUT, DELETE with typed body/response.
 */

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
    const headers = new Headers(options.headers);
    if (!(options.body instanceof FormData) && !headers.has('Content-Type')) {
      headers.set('Content-Type', 'application/json');
    }

    const res = await fetch(url, {
      ...options,
      signal: controller.signal,
      headers,
    });

    // Handle no-content responses (e.g. 204)
    if (res.status === 204 || res.headers.get('content-length') === '0') {
      return undefined as TResponse;
    }

    const data = await res.json().catch(() => ({}));

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
