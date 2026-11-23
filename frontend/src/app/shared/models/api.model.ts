// Shapes shared by every backend endpoint (mirror common/dto in the backend).

/** Paged response: `page` starts at 0, `size` is capped at 100 by the backend. */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Postal address (order shipping snapshot, customer addresses). */
export interface Address {
  recipient: string;
  line1: string;
  line2: string | null;
  city: string;
  region: string | null;
  postalCode: string;
  /** ISO country code, e.g. US, GB, CA, DE. */
  country: string;
}

/** Compact reference to another resource. */
export interface Ref {
  id: number;
  name: string;
}
