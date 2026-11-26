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
  | 'PENDING'
  | 'ANSWERED'
  | 'DISMISSED'
  | 'APPROVED'
  | 'DENIED'
  | 'NOT_ANSWERED';

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
  | { type: 'choice'; interactionId: string; question: string; options: ChoiceOption[]; multiSelect: boolean }
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

export type BlockType = 'NONE' | 'PRODUCT_LIST' | 'ORDER_LIST';

export interface ReplyEnvelope {
  status: 'ANSWERED' | 'REFUSED';
  text: string;
  blockType: BlockType;
  productList?: ProductItem[];
  orderList?: OrderItem[];
  suggestedReplies: SuggestionReply[];
}

export interface SuggestionReply {
  label: string;
  text: string;
}

export interface ProductItem {
  id: number;
  title: string;
  brand: string;
  price: number;
  listPrice: number | null;
  currency: string;
  rating: number | null;
  ratingCount: number;
  inStock: boolean;
  thumbnailUrl: string;
}

export interface OrderLine {
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
  lines: OrderLine[];
  shippingCity: string;
  trackingNumber: string | null;
  estimatedDelivery: string | null;
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
    stage: 'TOOL_STARTED' | 'TOOL_FINISHED';
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
