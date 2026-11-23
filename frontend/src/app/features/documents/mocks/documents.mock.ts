// Mock documents until /api/documents is wired. Texts follow the backend seed (db/seed/V108).
import { DocumentDetail, DocumentSection, DocumentSummary, VersionDetail } from '../models/document.model';

export const MOCK_DOCUMENTS: DocumentSummary[] = [
  { id: 1, code: 'return-policy', type: 'PUBLIC_POLICY', title: 'Return & Refund Policy', minRole: 'CUSTOMER', seller: null },
  { id: 2, code: 'shipping-policy', type: 'PUBLIC_POLICY', title: 'Shipping Policy', minRole: 'CUSTOMER', seller: null },
  { id: 3, code: 'warranty-policy', type: 'PUBLIC_POLICY', title: 'Warranty Policy', minRole: 'CUSTOMER', seller: null },
  { id: 4, code: 'seller-fee-schedule', type: 'PUBLIC_POLICY', title: 'Seller Fee Schedule', minRole: 'SELLER', seller: null },
  {
    id: 5, code: 'support-sop', type: 'SUPPORT_SOP',
    title: 'Customer Support Standard Operating Procedures', minRole: 'SUPPORT_T1', seller: null,
  },
  { id: 6, code: 'fraud-playbook', type: 'FRAUD_PLAYBOOK', title: 'Fraud & Risk Playbook', minRole: 'FRAUD', seller: null },
  {
    id: 47, code: 'contract-maple-street-electronics', type: 'SELLER_CONTRACT',
    title: 'Seller Agreement - Maple Street Electronics LLC', minRole: 'FINANCE',
    seller: { id: 41, name: 'Maple Street Electronics' },
  },
  {
    id: 64, code: 'contract-harbor-tech-supply', type: 'SELLER_CONTRACT',
    title: 'Seller Agreement - Harbor Tech Supply Inc.', minRole: 'FINANCE',
    seller: { id: 112, name: 'Harbor Tech Supply' },
  },
  {
    id: 71, code: 'contract-trailhead-outdoor-co', type: 'SELLER_CONTRACT',
    title: 'Seller Agreement - Trailhead Outdoor Co.', minRole: 'FINANCE',
    seller: { id: 266, name: 'Trailhead Outdoor Co.' },
  },
];

const returnPolicy = MOCK_DOCUMENTS[0];
const sop = MOCK_DOCUMENTS[4];

const nonReturnable: DocumentSection = {
  ordinal: 5, heading: 'Non-returnable items', minRole: 'CUSTOMER',
  body: 'Opened personal-care products, food and beverages, digital books and audiobooks cannot be returned unless they arrived damaged or defective.',
};
const startReturn = (prepaid: boolean): DocumentSection => ({
  ordinal: 2, heading: 'How to start a return', minRole: 'CUSTOMER',
  body: `Open the order in your account, choose the item and select a reason. You will receive a ${prepaid ? 'prepaid ' : ''}return label by email. Drop the parcel off within 7 days of opening the return.`,
});

const RETURN_POLICY_VERSIONS: VersionDetail[] = [
  {
    code: returnPolicy.code, title: returnPolicy.title, type: returnPolicy.type, versionNo: 3,
    effectiveFrom: '2025-10-01', effectiveTo: null,
    summary: '30-day window restored (15 days for electronics). Free return shipping again; 10% restocking fee only for change-of-mind electronics returns.',
    sections: [
      {
        ordinal: 1, heading: 'Return window', minRole: 'CUSTOMER',
        body: 'For orders placed from 1 October 2025 you can return items within **30 days of delivery**, except **electronics, which have a 15-day window**. Earlier orders follow the policy in force on their order date.',
      },
      startReturn(true),
      {
        ordinal: 3, heading: 'Return shipping and fees', minRole: 'CUSTOMER',
        body: 'Return shipping is **free** again for all returns. Electronics returned for a change of mind are subject to a **10% restocking fee**; no other restocking fees apply.',
      },
      {
        ordinal: 4, heading: 'Refunds', minRole: 'CUSTOMER',
        body: "Refunds are issued to the original payment method within 5 business days after the seller receives the item. Items that arrived damaged or defective may be refunded without being sent back, at the seller's discretion.",
      },
      nonReturnable,
    ],
  },
  {
    code: returnPolicy.code, title: returnPolicy.title, type: returnPolicy.type, versionNo: 2,
    effectiveFrom: '2024-09-01', effectiveTo: '2025-10-01',
    summary: 'Return window shortened to 14 days. Change-of-mind returns pay $6.99 return shipping; electronics change-of-mind returns pay a 15% restocking fee.',
    sections: [
      {
        ordinal: 1, heading: 'Return window', minRole: 'CUSTOMER',
        body: 'For orders placed from 1 September 2024 you can return items within **14 days of delivery**. Orders placed earlier keep the 30-day window that applied when they were placed.',
      },
      startReturn(false),
      {
        ordinal: 3, heading: 'Return shipping and fees', minRole: 'CUSTOMER',
        body: 'Returns because the item arrived damaged, defective, wrong or not as described are free. For change-of-mind and fit returns, **$6.99 return shipping** is deducted from the refund. Electronics returned for a change of mind are subject to a **15% restocking fee**.',
      },
      {
        ordinal: 4, heading: 'Refunds', minRole: 'CUSTOMER',
        body: 'Refunds are issued to the original payment method within 5 business days after the seller receives the item, minus any return shipping or restocking fees above.',
      },
      nonReturnable,
    ],
  },
  {
    code: returnPolicy.code, title: returnPolicy.title, type: returnPolicy.type, versionNo: 1,
    effectiveFrom: '2023-01-01', effectiveTo: '2024-09-01',
    summary: '30-day returns on all items, free return shipping, full refunds.',
    sections: [
      {
        ordinal: 1, heading: 'Return window', minRole: 'CUSTOMER',
        body: 'You can return most items within **30 days of delivery** for a full refund. The return window starts on the delivery date shown in your order tracking.',
      },
      startReturn(true),
      {
        ordinal: 3, heading: 'Return shipping and fees', minRole: 'CUSTOMER',
        body: 'Return shipping is **free** for all returns. No restocking fees apply.',
      },
      {
        ordinal: 4, heading: 'Refunds', minRole: 'CUSTOMER',
        body: 'Refunds are issued to the original payment method within 5 business days after the seller receives the item. Original shipping charges are refunded when the item arrived damaged, defective or not as described.',
      },
      nonReturnable,
    ],
  },
];

const SOP_VERSIONS: VersionDetail[] = [
  {
    code: sop.code, title: sop.title, type: sop.type, versionNo: 2,
    effectiveFrom: '2025-01-01', effectiveTo: null,
    summary: 'Adds goodwill credits for late deliveries and the return-dispute process.',
    sections: [
      {
        ordinal: 1, heading: 'Verifying the customer', minRole: 'SUPPORT_T1',
        body: "Before discussing an order, confirm the customer's email address and the order number. Never share order details with anyone other than the account holder. Never ask for full card numbers or passwords.",
      },
      {
        ordinal: 3, heading: 'Returns', minRole: 'SUPPORT_T1',
        body: "Apply the return policy version that was in force on the **order date**, not today's policy. Check the return window from the delivery date. Open returns on the customer's behalf when they cannot do it themselves.",
      },
      {
        ordinal: 4, heading: 'Refund limits', minRole: 'SUPPORT_T1',
        body: 'Tier-1 agents may issue refunds **up to $100** per order. Refunds above $100 must be escalated to a Tier-2 agent with a short internal note explaining the case.',
      },
      {
        ordinal: 6, heading: 'Policy overrides and exceptions', minRole: 'SUPPORT_T2',
        body: 'Tier-2 agents may approve returns up to 10 days outside the window and waive restocking fees for customers with no prior returns in the last 12 months. Every override must be recorded as an internal note with the reason. Overrides above $500 need supervisor approval.',
      },
      {
        ordinal: 8, heading: 'Escalating to the fraud team', minRole: 'SUPPORT_T2',
        body: 'Escalate to the fraud team when a customer has 3 or more returns in 90 days, asks to refund to a different payment method, or reports a lost parcel for the third time in a year. Do not tell the customer that a fraud review is in progress.',
      },
    ],
  },
  {
    code: sop.code, title: sop.title, type: sop.type, versionNo: 1,
    effectiveFrom: '2023-01-01', effectiveTo: '2025-01-01',
    summary: 'First edition of the support SOP.', sections: [],
  },
];

const VERSIONS: Record<string, VersionDetail[]> = {
  [returnPolicy.code]: RETURN_POLICY_VERSIONS,
  [sop.code]: SOP_VERSIONS,
};

/** Detail and versions for a code. Codes without mock content fall back to the return policy. */
export function mockDocument(code: string | undefined): { detail: DocumentDetail; versions: VersionDetail[] } {
  const summary = MOCK_DOCUMENTS.find((d) => d.code === code && VERSIONS[d.code]) ?? returnPolicy;
  const versions = VERSIONS[summary.code];
  return {
    detail: {
      ...summary,
      versions: versions.map(({ versionNo, effectiveFrom, effectiveTo, summary: s }) => ({
        versionNo, effectiveFrom, effectiveTo, summary: s,
      })),
    },
    versions,
  };
}
