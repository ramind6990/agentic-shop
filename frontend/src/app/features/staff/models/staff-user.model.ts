// Staff and seller accounts, mirroring UserDto in identity/dto/UserDtos.java.
import { Ref } from '../../../shared/models/api.model';
import { UserStatus } from '../../customers/models/customer.model';

/** Roles a non-customer account can have. A SELLER user always belongs to a seller. */
export const STAFF_ROLES = ['SELLER', 'SUPPORT_T1', 'SUPPORT_T2', 'FINANCE', 'FRAUD', 'ADMIN'] as const;
export type StaffRole = (typeof STAFF_ROLES)[number];

/** Row in GET /api/users. */
export interface StaffUser {
  id: number;
  fullName: string;
  email: string;
  role: StaffRole;
  /** Only for SELLER users. */
  seller: Ref | null;
  status: UserStatus;
  createdAt: string;
}
