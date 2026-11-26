import { Routes, UrlMatchResult, UrlSegment } from '@angular/router';

import { ChatPage } from './pages/chat-page/chat-page';

/**
 * /chat (a new chat) and /chat/:conversationId share one route, so moving between them keeps the page, its store and
 * its socket instead of rebuilding them.
 */
function chatMatcher(segments: UrlSegment[]): UrlMatchResult | null {
  if (segments.length > 1) return null;
  return {
    consumed: segments,
    posParams: segments.length === 1 ? { conversationId: segments[0] } : {},
  };
}

export const CHAT_ROUTES: Routes = [
  { matcher: chatMatcher, title: 'Support chat · AgenticShop', component: ChatPage },
];
