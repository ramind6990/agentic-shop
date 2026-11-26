package com.rmrx.agenticshop.ai2.events.ws;

import com.rmrx.agenticshop.ai2.conversation.ConversationConflictException;
import com.rmrx.agenticshop.ai2.conversation.ConversationService;
import com.rmrx.agenticshop.ai2.conversation.RunOutcome;
import com.rmrx.agenticshop.ai2.events.ChatResponse;
import com.rmrx.agenticshop.ai2.events.ChatResponse.ErrorCode;
import com.rmrx.agenticshop.ai2.events.RunEventBus;
import com.rmrx.agenticshop.ai2.events.sse.SseRunEventTransport;
import com.rmrx.agenticshop.ai2.events.ws.ClientMessage.ChatRequest;
import com.rmrx.agenticshop.ai2.events.ws.ClientMessage.SubscribeRequest;
import com.rmrx.agenticshop.app.common.exception.NotFoundException;
import jakarta.annotation.PreDestroy;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

/**
 * The ai2 chat socket. The handshake already authenticated the user ({@link WsHandshakeInterceptor}); every frame acts
 * as that user only. A {@code chat} frame sends a message or answers the paused run ({@link ConversationService}, which
 * checks the conversation's ownership), is acknowledged with a PROGRESS {@code ACCEPTED}, and the started run's events
 * follow on this socket. A {@code subscribe} frame follows a run the user owns. Everything sent is a
 * {@link ChatResponse}; a refused frame gets an ERROR with its requestId and the socket stays open.
 */
@Component("ai2ChatSocketHandler")
public class ChatSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatSocketHandler.class);

    private final ConversationService conversations;
    private final RunEventBus bus;
    private final Validator validator;
    private final Ai2WsProperties props;
    private final ObjectReader reader;
    private final ObjectWriter writer;
    private final Map<String, ClientSession> clients = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timers = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("ai2-ws-timers").factory());
    private final ExecutorService io = Executors.newVirtualThreadPerTaskExecutor();

    public ChatSocketHandler(ConversationService conversations, RunEventBus bus, Validator validator,
                             Ai2WsProperties props, JsonMapper mapper) {
        this.conversations = conversations;
        this.bus = bus;
        this.validator = validator;
        this.props = props;
        this.reader = mapper.readerFor(ClientMessage.class);
        this.writer = mapper.writerFor(ChatResponse.class);
    }

    @PreDestroy
    void shutdown() {
        timers.shutdownNow();
        io.shutdownNow();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (!(session.getAttributes().get(WsUser.ATTRIBUTE) instanceof WsUser user)) {
            session.close(CloseStatus.POLICY_VIOLATION); // never without the handshake interceptor
            return;
        }
        ClientSession client = new ClientSession(session, user, writer, bus, props);
        clients.put(session.getId(), client);
        client.start(timers, io);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        ClientSession client = clients.get(session.getId());
        if (client == null) return;
        client.touch();
        ClientMessage request;
        try {
            request = reader.readValue(message.getPayload());
        } catch (JacksonException e) {
            log.debug("ai2 socket {}: unreadable frame: {}", session.getId(), e.getOriginalMessage());
            client.refuse(null, ErrorCode.BAD_REQUEST, "Not a valid message: expected a JSON object of type chat or subscribe.");
            return;
        }
        Set<ConstraintViolation<ClientMessage>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            client.refuse(request.requestId(), ErrorCode.BAD_REQUEST, violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage()).sorted().collect(Collectors.joining("; ")));
            return;
        }
        try {
            switch (request) {
                case ChatRequest chat -> chat(client, chat);
                case SubscribeRequest subscribe -> subscribe(client, subscribe);
            }
        } catch (ConversationConflictException e) {
            client.refuse(request.requestId(), ErrorCode.CONFLICT, e.getMessage());
        } catch (NotFoundException e) {
            client.refuse(request.requestId(), ErrorCode.NOT_FOUND, e.getMessage());
        } catch (RuntimeException e) {
            log.error("ai2 socket request {} of user {} failed", request.requestId(), client.userId(), e);
            client.refuse(request.requestId(), ErrorCode.INTERNAL, "The request could not be processed.");
        }
    }

    private void chat(ClientSession client, ChatRequest chat) {
        RunOutcome outcome = chat.answering()
                ? conversations.answer(client.userId(), chat.conversationId(), chat.interactionId(), chat.answer())
                : conversations.sendMessage(client.userId(), chat.conversationId(), chat.text());
        switch (outcome) {
            case RunOutcome.Running running -> {
                client.send(ChatResponse.accepted(chat.requestId(), running.conversationId(), running.runId()));
                client.follow(running.runId(), 0);
            }
            case RunOutcome.Abandoned abandoned -> client.send(ChatResponse.refused(chat.requestId(),
                    abandoned.conversationId(), abandoned.runId(), ErrorCode.INTERACTION_CLOSED,
                    "That question is no longer open; send a new message.", false));
            case RunOutcome other -> throw new IllegalStateException("unexpected outcome " + other);
        }
    }

    private void subscribe(ClientSession client, SubscribeRequest subscribe) {
        Optional<Long> owner = bus.ownerOf(subscribe.runId());
        if (owner.isPresent() && !Objects.equals(owner.get(), client.userId())) {
            throw new NotFoundException("Run", subscribe.runId());
        }
        long afterSeq = subscribe.afterSeq() == null ? 0 : subscribe.afterSeq();
        if (owner.isEmpty() || !client.follow(subscribe.runId(), afterSeq)) {
            client.refuse(subscribe.requestId(), ErrorCode.RUN_NOT_LIVE, SseRunEventTransport.NOT_LIVE_MESSAGE);
        }
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        ClientSession client = clients.get(session.getId());
        if (client != null) client.touch();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        ClientSession client = clients.remove(session.getId());
        if (client != null) client.release();
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("ai2 socket {} transport error: {}", session.getId(), exception.toString());
    }
}
