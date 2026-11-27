/** Contracts of the ai2 chat (REST + WebSocket), mirroring the backend, and the view items the page renders. */

// ---------------------------------------------------------------- conversations (REST)

export type ConversationStatus = 'OPEN' | 'CLOSED';

export interface ConversationSummary {
  id: number;
  /** The start of the first message; null for a conversation without one. */
  title: string | null;
  status: ConversationStatus;
  updatedAt: string;
}

export interface ConversationDetail extends ConversationSummary {
  /** Null when no run is active. */
  activeRun: ActiveRun | null;
  messages: TimelineMessage[];
}

export interface ActiveRun {
  runId: string;
  status: 'RUNNING' | 'PAUSED';
  pendingInteraction: PendingInteraction | null;
}

export type InteractionStatus =
  'PENDING' | 'ANSWERED' | 'DISMISSED' | 'APPROVED' | 'DENIED' | 'NOT_ANSWERED';

export interface TimelineMessage {
  id: number;
  runId: string | null;
  kind: 'USER' | 'ASSISTANT' | 'INTERACTION';
  text: string | null;
  /** INTERACTION: the question or approval. */
  payload: PendingInteraction | null;
  /** ASSISTANT: what the UI renders (null for replies stored before it existed). */
  reply: ReplyEnvelope | null;
  interactionStatus: InteractionStatus | null;
  answer: HumanAnswer | null;
}

// ---------------------------------------------------------------- questions and answers

export interface ChoiceOption {
  value: string;
  label: string;
  description?: string | null;
}

export type PendingInteraction =
  | {
      type: 'choice';
      interactionId: string;
      question: string;
      options: ChoiceOption[];
      multiSelect: boolean;
    }
  | { type: 'input'; interactionId: string; question: string }
  | { type: 'yesNo'; interactionId: string; question: string }
  | { type: 'approval'; interactionId: string; title: string; detail: string | null };

export type HumanAnswer =
  | { type: 'choice'; values: string[] }
  | { type: 'input'; text: string }
  | { type: 'yesNo'; yes: boolean }
  | { type: 'approval'; approved: boolean }
  | { type: 'dismissed' };

export function questionOf(interaction: PendingInteraction): string {
  return interaction.type === 'approval' ? interaction.title : interaction.question;
}

/** The answer as the user would say it. */
export function describeAnswer(interaction: PendingInteraction, answer: HumanAnswer): string {
  switch (answer.type) {
    case 'choice': {
      const options = interaction.type === 'choice' ? interaction.options : [];
      return answer.values.map((v) => options.find((o) => o.value === v)?.label ?? v).join(', ');
    }
    case 'input':
      return answer.text;
    case 'yesNo':
      return answer.yes ? 'Yes' : 'No';
    case 'approval':
      return answer.approved ? 'Approved' : 'Denied';
    case 'dismissed':
      return 'Dismissed';
  }
}

// ---------------------------------------------------------------- the reply

/** ANSWERED / REFUSED come from the agent; FAILED and STOPPED are stored by the system for a run without a reply. */
export type ReplyStatus = 'ANSWERED' | 'REFUSED' | 'FAILED' | 'STOPPED';

export interface ReplyEnvelope {
  status: ReplyStatus;
  /** Markdown. */
  text: string;
  /** What is shown next to the text; absent for a text-only reply. */
  block?: ReplyBlock;
  suggestedReplies: SuggestionReply[];
}

export interface SuggestionReply {
  label: string;
  text: string;
}

/** The data a reply shows; `type` says which. */
export type ReplyBlock =
  | { type: 'PRODUCT_LIST'; items: ProductItem[] }
  | { type: 'PRODUCT_DETAIL'; product: ProductDetail }
  | { type: 'REVIEW_LIST'; productId: number; items: Review[] }
  | { type: 'ORDER_LIST'; items: OrderItem[] }
  | { type: 'ORDER_DETAIL'; order: OrderDetail }
  | { type: 'ORDER_CANCELLATION'; cancellation: OrderCancellation }
  | { type: 'ADDRESS_CHANGE'; change: { orderNumber: string; shippingAddress: ChatAddress } }
  | { type: 'TRACKING'; tracking: Tracking }
  | { type: 'SHIPMENT_LIST'; items: ShipmentRow[] }
  | { type: 'RETURN_LIST'; items: ReturnItem[] }
  | { type: 'REFUND_LIST'; items: RefundItem[] }
  | { type: 'TICKET_LIST'; items: TicketItem[] }
  | { type: 'TICKET_DETAIL'; ticket: TicketDetail }
  | { type: 'CUSTOMER_LIST'; items: CustomerRow[] }
  | { type: 'CUSTOMER_DETAIL'; customer: CustomerDetail }
  | { type: 'ADDRESS_LIST'; items: ChatAddress[] };

export type BlockType = ReplyBlock['type'];

/** A block of one type. */
export type BlockOf<T extends BlockType> = Extract<ReplyBlock, { type: T }>;

/** A saved address (with id and label) or an order's delivery address. Absent fields are omitted. */
export interface ChatAddress {
  id?: number;
  label?: string;
  recipient: string;
  line1: string;
  line2: string | null;
  city: string;
  region: string | null;
  postalCode: string;
  country: string;
  defaultAddress?: boolean;
}

export interface ProductItem {
  id: number;
  sku?: string;
  title: string;
  brand: string;
  category?: string;
  price: number;
  listPrice: number | null;
  currency: string;
  rating: number | null;
  ratingCount: number;
  inStock: boolean;
  thumbnailUrl: string;
}

export interface ProductDetail {
  card: ProductItem;
  description: string | null;
  seller: string;
  categoryPath: string[];
  attributes: { name: string; value: string }[];
  delisted: boolean;
  availableUnits: number;
  restockEta: string | null;
  /** Stars → number of reviews. */
  ratingDistribution: Record<string, number>;
}

export interface Review {
  id: number;
  rating: number;
  title: string | null;
  body: string | null;
  authorName: string;
  verifiedPurchase: boolean;
  helpfulCount: number;
  createdAt: string;
}

export interface OrderLine {
  itemId?: number;
  productId: number;
  title: string;
  quantity: number;
  unitPrice: number;
  thumbnailUrl: string;
}

export interface OrderItem {
  orderNumber: string;
  placedAt: string;
  status: string;
  total: number;
  currency: string;
  seller?: string;
  lines: OrderLine[];
  shippingCity: string;
  trackingNumber: string | null;
  estimatedDelivery: string | null;
}

/** Whether an action is allowed now and, when not, why. */
export interface Verdict {
  allowed: boolean;
  reason?: string;
}

export interface OrderDetail {
  card: OrderItem;
  customerId: number;
  customer: string;
  shippingAddress: ChatAddress;
  subtotal: number;
  shippingFee: number;
  tax: number;
  payments: { method: string; status: string; amount: number }[];
  shipments: ShipmentInfo[];
  returns: {
    id: number;
    itemId: number;
    quantity: number;
    reason: string;
    status: string;
    openedAt: string;
  }[];
  refunds: {
    id: number;
    amount: number;
    status: string;
    reason: string | null;
    createdAt: string;
  }[];
  actions: {
    cancel: Verdict;
    changeAddress: Verdict;
    returnItems: Verdict;
    returnDeadline: string | null;
    returnableQuantities: Record<string, number>;
  };
}

export interface ShipmentInfo {
  carrier: string;
  trackingNumber: string;
  status: string;
  shippedAt: string | null;
  deliveredAt: string | null;
  estimatedDelivery: string | null;
}

export interface OrderCancellation {
  orderNumber: string;
  status: string;
  cancelledAt: string;
  refundAmount: number;
  currency: string;
  refundMethod: string | null;
  refundExpectedBy: string | null;
}

export interface Tracking {
  orderNumber: string;
  orderStatus: string;
  shipments: (ShipmentInfo & { eventCount: number; events: TrackingEvent[] })[];
}

export interface TrackingEvent {
  occurredAt: string;
  type: string;
  location: string | null;
  description: string | null;
}

export interface ShipmentRow extends ShipmentInfo {
  orderNumber: string;
  customerId: number;
  customer: string;
  seller: string;
}

export interface ReturnItem {
  id: number;
  orderNumber: string;
  itemId: number;
  product: string;
  quantity: number;
  reason: string;
  status: string;
  openedAt: string;
  resolvedAt: string | null;
  customerNote: string | null;
  resolutionNote: string | null;
}

/** Amounts are in the order's currency. */
export interface RefundItem {
  id: number;
  orderNumber: string;
  amount: number;
  status: string;
  reason: string | null;
  returnId: number | null;
  createdAt: string;
}

export interface TicketItem {
  ticketNumber: string;
  subject: string;
  category: string;
  status: string;
  priority: string;
  tier: string;
  orderNumber?: string;
  customerId?: number;
  customer?: string;
  assignee?: string;
  createdAt: string;
  updatedAt: string;
}

export interface TicketDetail {
  ticket: TicketItem;
  messageCount: number;
  messages: {
    author: string;
    authorName: string;
    body: string;
    internal: boolean;
    createdAt: string;
  }[];
}

export interface CustomerRow {
  id: number;
  fullName: string;
  email: string;
  status: string;
  createdAt: string;
}

export interface CustomerDetail extends CustomerRow {
  phone: string | null;
  addresses: ChatAddress[];
  orderCount: number;
  returnCount: number;
  ticketCount: number;
}

// ---------------------------------------------------------------- token usage

export interface TokenTotals {
  calls: number;
  inputTokens: number;
  cachedInputTokens: number;
  outputTokens: number;
  reasoningTokens: number;
  totalTokens: number;
}

export type UsageIssuer = 'USER' | 'SYSTEM';

export interface MemoryState {
  summarized: boolean;
  summaryChars: number;
  maxSummaryChars: number;
  hardLimitChars: number;
  messagesSinceSummary: number;
  summarizeAfter: number;
  keepRecent: number;
}

export interface ConversationUsage {
  conversationId: number;
  total: TokenTotals;
  byIssuer: Record<UsageIssuer, TokenTotals>;
  /** What `total` is measured against (configured on the server). */
  tokenLimit: number;
  /** The history the next message starts from (summary + messages after it), estimated in tokens. */
  context: {
    historyTokens: number;
    /** The summary alone; 0 without one. */
    summaryTokens: number;
    /** What `historyTokens` is measured against (configured on the server). */
    tokenLimit: number;
    memory: MemoryState;
  };
}

// ---------------------------------------------------------------- WebSocket frames

export type ClientMessage =
  | {
      type: 'chat';
      requestId: string;
      conversationId: number | null;
      text?: string;
      interactionId?: string;
      answer?: HumanAnswer;
    }
  | { type: 'cancel'; requestId: string; conversationId: number };

export type ErrorCode =
  | 'BAD_REQUEST'
  | 'NOT_FOUND'
  | 'CONFLICT'
  | 'RUN_NOT_LIVE'
  | 'INTERACTION_CLOSED'
  | 'CANCELLED'
  | 'AGENT_ERROR'
  | 'INTERNAL';

/** The one message the server sends: `type` says which payload is set. */
export interface ChatResponse {
  type: 'INPUT' | 'PROGRESS' | 'REPLY' | 'INTERACTION' | 'ERROR';
  /** INPUT: the request that sent it; ERROR without seq: the refused request. */
  requestId?: string;
  conversationId?: number;
  runId?: string;
  /** Set on run events; absent on refusals. */
  seq?: number;
  input?: { text?: string; answered?: PendingInteraction; answer?: HumanAnswer };
  progress?: {
    stage: 'TOOL_STARTED' | 'TOOL_FINISHED' | 'RETRYING' | 'THINKING';
    label?: string;
    toolName?: string;
    success?: boolean;
  };
  reply?: ReplyEnvelope;
  interaction?: PendingInteraction;
  error?: { code: ErrorCode; message: string; retryable: boolean };
}

// ---------------------------------------------------------------- what the page shows

export type InteractionState = 'open' | 'answered' | 'dismissed' | 'not-answered' | 'closed';

export type ChatItem =
  | { kind: 'user'; key: string; runId: string | null; text: string }
  | { kind: 'reply'; key: string; runId: string | null; reply: ReplyEnvelope }
  | {
      kind: 'interaction';
      key: string;
      runId: string | null;
      interaction: PendingInteraction;
      state: InteractionState;
      answer: HumanAnswer | null;
    }
  | {
      kind: 'error';
      key: string;
      runId: string | null;
      message: string;
      retryText: string | null;
      /** A stop the user asked for: shown as a quiet note, not a warning. */
      stopped?: boolean;
    };
