/**
 * Standard API response envelope for consistent responses across all endpoints.
 */

export interface ApiSuccessResponse<T = unknown> {
  success: true;
  data?: T;
  message?: string;
  meta?: {
    total?: number;
    page?: number;
    pageSize?: number;
  };
}

export interface ApiErrorResponse {
  success: false;
  error: string;
  code?: string;
  details?: unknown;
}

export type ApiResponse<T = unknown> = ApiSuccessResponse<T> | ApiErrorResponse;

/**
 * HTTP status codes used across the API.
 */
export const HttpStatus = {
  OK: 200,
  CREATED: 201,
  BAD_REQUEST: 400,
  NOT_FOUND: 404,
  CONFLICT: 409,
  INTERNAL_SERVER_ERROR: 500,
} as const;

/**
 * Create a success response object.
 */
export function success<T>(data?: T, message?: string, meta?: ApiSuccessResponse['meta']): ApiSuccessResponse<T> {
  return { success: true, ...(data !== undefined && { data }), ...(message && { message }), ...(meta && { meta }) };
}

/**
 * Create an error response object.
 */
export function error(message: string, code?: string, details?: unknown): ApiErrorResponse {
  return { success: false, error: message, ...(code && { code }), ...(details !== undefined && { details }) };
}
