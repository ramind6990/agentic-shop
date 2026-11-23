import { HttpErrorResponse } from '@angular/common/http';

/** RFC 9457 error body returned by the backend. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
}

/** What the UI needs to know about a failed request. */
export interface ApiError {
  /** HTTP status; 0 when the server could not be reached. */
  status: number;
  message: string;
}

export function toApiError(error: unknown): ApiError {
  if (error instanceof HttpErrorResponse) {
    const body = (
      error.error !== null && typeof error.error === 'object' ? error.error : null
    ) as ProblemDetail | null;
    // 0: network error. A 5xx without a JSON body comes from the dev-server proxy, not the backend
    // (the backend always answers with a ProblemDetail), so the backend is not reachable.
    if (error.status === 0 || (error.status >= 500 && body === null)) {
      return { status: error.status, message: 'Cannot reach the server. Is the backend running?' };
    }
    return {
      status: error.status,
      message:
        body?.detail ?? body?.title ?? `Request failed (${error.status} ${error.statusText})`,
    };
  }
  return { status: -1, message: 'Something went wrong while loading data.' };
}
