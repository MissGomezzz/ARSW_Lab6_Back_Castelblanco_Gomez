package co.edu.eci.blueprints.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.fasterxml.jackson.databind.JsonNode;

import co.edu.eci.blueprints.dto.BlueprintUpdate;
import co.edu.eci.blueprints.dto.DrawEvent;
import co.edu.eci.blueprints.dto.WsError;
import co.edu.eci.blueprints.model.Point;

/**
 * End-to-end STOMP test over a real WebSocket: login, CONNECT with JWT, SUBSCRIBE, SEND, broadcast.
 *
 * <p>The broker registers subscriptions asynchronously, so the tests wait until the subscription
 * is registered before sending.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BlueprintDrawRealtimeTest {

    private static final long TIMEOUT_SECONDS = 5;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private SimpleBrokerMessageHandler broker;

    @Autowired
    private SimpUserRegistry userRegistry;

    private WebSocketStompClient stompClient;
    private StompSession session;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    @AfterEach
    void tearDown() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        stompClient.stop();
    }

    @Test
    void drawEventIsBroadcastToTopicAndPersisted() throws Exception {
        String token = login();
        session = connect(token);

        String topic = "/topic/blueprints.john.house";
        CompletableFuture<BlueprintUpdate> received = new CompletableFuture<>();
        session.subscribe(topic, frameHandler(BlueprintUpdate.class, received));
        awaitCondition(() -> hasBrokerSubscription(topic));

        Point point = new Point(123, 45);
        session.send("/app/draw", new DrawEvent("john", "house", point, "client-A"));

        BlueprintUpdate update = received.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(update.author()).isEqualTo("john");
        assertThat(update.name()).isEqualTo("house");
        assertThat(update.points()).containsExactly(point);
        assertThat(update.clientId()).isEqualTo("client-A");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<JsonNode> response = rest.exchange(
                "/api/v1/blueprints/john/house", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode points = response.getBody().path("data").path("points");
        JsonNode last = points.get(points.size() - 1);
        assertThat(last.path("x").asInt()).isEqualTo(point.x());
        assertThat(last.path("y").asInt()).isEqualTo(point.y());
    }

    @Test
    void rapidDrawEventsKeepClickOrderInBroadcastAndPersistence() throws Exception {
        String token = login();
        session = connect(token);

        String topic = "/topic/blueprints.jane.pool";
        int count = 30;
        List<Point> received = new CopyOnWriteArrayList<>();
        CountDownLatch allReceived = new CountDownLatch(count);
        session.subscribe(topic, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return BlueprintUpdate.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.addAll(((BlueprintUpdate) payload).points());
                allReceived.countDown();
            }
        });
        awaitCondition(() -> hasBrokerSubscription(topic));

        List<Point> sent = IntStream.range(0, count).mapToObj(i -> new Point(i, i * 2)).toList();
        sent.forEach(p -> session.send("/app/draw", new DrawEvent("jane", "pool", p, "client-C")));

        assertThat(allReceived.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(received).containsExactlyElementsOf(sent);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        JsonNode points = rest.exchange("/api/v1/blueprints/jane/pool", HttpMethod.GET,
                        new HttpEntity<>(headers), JsonNode.class)
                .getBody().path("data").path("points");
        List<Point> persistedTail = new ArrayList<>();
        for (int i = points.size() - count; i < points.size(); i++) {
            persistedTail.add(new Point(points.get(i).path("x").asInt(), points.get(i).path("y").asInt()));
        }
        assertThat(persistedTail).containsExactlyElementsOf(sent);
    }

    @Test
    void invalidDrawEventIsReportedOnUserErrorQueue() throws Exception {
        session = connect(login());

        CompletableFuture<WsError> error = new CompletableFuture<>();
        session.subscribe("/user/queue/errors", frameHandler(WsError.class, error));
        // "/user/queue/errors" is resolved by the server to "/queue/errors-user{serverSessionId}"
        awaitCondition(() -> userRegistry
                .findSubscriptions(s -> "/user/queue/errors".equals(s.getDestination()))
                .stream()
                .anyMatch(s -> hasBrokerSubscription("/queue/errors-user" + s.getSession().getId())));

        session.send("/app/draw", new DrawEvent("john", "house", new Point(-5, 10), "client-B"));

        WsError wsError = error.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(wsError.message()).contains("x must be >= 0");
    }

    @Test
    void connectWithoutTokenIsRejected() throws Exception {
        CompletableFuture<String> outcome = new CompletableFuture<>();

        stompClient.connectAsync(wsUrl(), new WebSocketHttpHeaders(), new StompHeaders(),
                new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession s, StompHeaders connectedHeaders) {
                        outcome.complete("CONNECTED");
                    }

                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return byte[].class; // ERROR body is text/plain; skip JSON conversion
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        // Only ERROR frames reach the session handler before CONNECTED
                        outcome.complete("ERROR " + headers.getFirst("message"));
                    }

                    @Override
                    public void handleException(StompSession s, StompCommand command, StompHeaders headers,
                                                byte[] payload, Throwable exception) {
                        outcome.complete(StompCommand.ERROR.equals(command) ? "ERROR" : "EXCEPTION");
                    }

                    @Override
                    public void handleTransportError(StompSession s, Throwable exception) {
                        outcome.complete("ERROR");
                    }
                });

        // The ERROR frame is tagged so clients can stop reconnecting and log the user out.
        assertThat(outcome.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).startsWith("ERROR unauthorized:");
    }

    private String login() {
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                "/auth/login", HttpMethod.POST,
                new HttpEntity<>(Map.of("username", "student", "password", "student123")),
                new ParameterizedTypeReference<>() { });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("access_token");
    }

    private StompSession connect(String token) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);
        return stompClient.connectAsync(wsUrl(), new WebSocketHttpHeaders(), connectHeaders,
                        new StompSessionHandlerAdapter() { })
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private String wsUrl() {
        return "ws://localhost:" + port + "/ws-blueprints";
    }

    private boolean hasBrokerSubscription(String destination) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        accessor.setDestination(destination);
        Message<byte[]> probe = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return !broker.getSubscriptionRegistry().findSubscriptions(probe).isEmpty();
    }

    private static void awaitCondition(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(10);
        }
        fail("Condition not met within " + TIMEOUT_SECONDS + "s");
    }

    private static <T> StompFrameHandler frameHandler(Class<T> type, CompletableFuture<T> sink) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return type;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                sink.complete(type.cast(payload));
            }
        };
    }
}
