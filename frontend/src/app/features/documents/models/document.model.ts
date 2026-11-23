// Document (RAG corpus) shapes, mirroring docs/dto/DocumentDtos.java in the backend.
import { Ref } from '../../../shared/models/api.model';

export const DOCUMENT_TYPES = ['PUBLIC_POLICY', 'SUPPORT_SOP', 'SELLER_CONTRACT', 'FRAUD_PLAYBOOK'] as const;
export type DocumentType = (typeof DOCUMENT_TYPES)[number];

export const DOCUMENT_TYPE_LABELS: Record<DocumentType, string> = {
  PUBLIC_POLICY: 'Public policy',
  SUPPORT_SOP: 'Support SOP',
  SELLER_CONTRACT: 'Seller contract',
  FRAUD_PLAYBOOK: 'Fraud playbook',
};

/** Lowest role allowed to read a document or section. */
export type Role = 'CUSTOMER' | 'SELLER' | 'SUPPORT_T1' | 'SUPPORT_T2' | 'FINANCE' | 'FRAUD' | 'ADMIN';

/** Row in the document list (GET /api/documents). */
export interface DocumentSummary {
  id: number;
  code: string;
  type: DocumentType;
  title: string;
  minRole: Role;
  /** Set for per-seller documents (contracts). */
  seller: Ref | null;
}

export interface VersionSummary {
  versionNo: number;
  /** ISO date, inclusive. */
  effectiveFrom: string;
  /** ISO date, exclusive; null for the current version. */
  effectiveTo: string | null;
  summary: string;
}

/** GET /api/documents/{code} */
export interface DocumentDetail extends DocumentSummary {
  versions: VersionSummary[];
}

export interface DocumentSection {
  ordinal: number;
  heading: string;
  /** Markdown. */
  body: string;
  /** Section-level ACL: some SOP sections are Tier-2 only. */
  minRole: Role;
}

/** GET /api/documents/{code}/versions/{n} and /effective?at= */
export interface VersionDetail {
  code: string;
  title: string;
  type: DocumentType;
  versionNo: number;
  effectiveFrom: string;
  effectiveTo: string | null;
  summary: string;
  sections: DocumentSection[];
}
