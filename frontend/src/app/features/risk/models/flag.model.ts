// Account flag shapes, mirroring FlagDto in risk/web/AccountFlagController.java. Fraud-team data.
import { Ref } from '../../../shared/models/api.model';

export type FlagSubjectType = 'CUSTOMER' | 'SELLER';

export const FLAG_TYPES = [
  'CHARGEBACK',
  'REFUND_ABUSE',
  'FAKE_REVIEWS',
  'ACCOUNT_TAKEOVER',
  'POLICY_VIOLATION',
] as const;
export type FlagType = (typeof FLAG_TYPES)[number];

export interface AccountFlag {
  id: number;
  subjectType: FlagSubjectType;
  subjectId: number;
  flagType: FlagType;
  /** 0 to 100. */
  riskScore: number;
  note: string;
  createdBy: Ref | null;
  active: boolean;
  createdAt: string;
}
