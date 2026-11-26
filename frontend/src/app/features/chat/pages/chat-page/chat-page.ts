import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  afterRenderEffect,
  computed,
  inject,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';

import { AnsweredBox } from '../../components/answered-box/answered-box';
import { Composer } from '../../components/composer/composer';
import { ConversationList } from '../../components/conversation-list/conversation-list';
import { InteractionBox } from '../../components/interaction-box/interaction-box';
import { ReplyView } from '../../components/reply-view/reply-view';
import { ChatSocket } from '../../data-access/chat-socket';
import { ChatStore } from '../../state/chat.store';

/**
 * Support chat with the AI assistant: the user's conversations on the left (next to the app's own navigation), the
 * open conversation and the composer on the right. The page owns the socket: it connects when the page opens and
 * closes when it is left.
 */
@Component({
  selector: 'app-chat-page',
  imports: [RouterLink, ConversationList, Composer, ReplyView, InteractionBox, AnsweredBox],
  providers: [ChatSocket, ChatStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './chat-page.html',
  styleUrl: './chat-page.scss',
})
export class ChatPage {
  protected readonly store = inject(ChatStore);
  private readonly scroller = viewChild<ElementRef<HTMLElement>>('scroller');

  protected readonly connection = computed(() => {
    switch (this.store.socketStatus()) {
      case 'connecting':
        return 'Connecting…';
      case 'reconnecting':
        return 'Reconnecting…';
      case 'closed':
        return 'Offline';
      default:
        return null;
    }
  });

  constructor() {
    // keep the newest message in view
    afterRenderEffect(() => {
      this.store.items();
      this.store.progress();
      const el = this.scroller()?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    });
  }
}
