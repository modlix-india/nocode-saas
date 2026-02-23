# Events & Messaging

This document covers the RabbitMQ-based event system used for asynchronous communication between microservices.

## Overview

The platform uses **RabbitMQ** (via Spring AMQP) for asynchronous inter-service communication. Events are published by services when important actions occur (user created, password changed, etc.) and consumed by other services that need to react.

## Configuration

RabbitMQ connection settings are in `configfiles/application-default.yml`:

```yaml
mq:
  host: localhost
  port: 5672
  username: fincity
  password: fincity
```

Event routing configuration (defaults in `EventCreationService`):
```yaml
events:
  mq:
    exchange: events                          # Exchange name
    routingkeys: events1,events2,events3      # Routing keys (round-robin)
```

## Publishing Events

### EventCreationService

Located at `commons-mq/src/main/java/com/fincity/saas/commons/mq/events/EventCreationService.java`:

```java
@Service
public class EventCreationService {

    @Value("${events.mq.exchange:events}")
    private String exchange;

    @Value("${events.mq.routingkeys:events1,events2,events3}")
    private String routingKey;

    @Autowired
    private AmqpTemplate amqpTemplate;

    private DoublePointerNode<String> nextRoutingKey;

    @PostConstruct
    protected void init() {
        nextRoutingKey = new CircularLinkedList<>(this.routingKey.split(",")).getHead();
    }

    public Mono<Boolean> createEvent(EventQueObject queObj) {
        this.nextRoutingKey = nextRoutingKey.getNext();
        return Mono.just(queObj)
                .flatMap(q -> Mono.deferContextual(cv -> {
                    if (!cv.hasKey(LogUtil.DEBUG_KEY))
                        return Mono.just(q);
                    q.setXDebug(cv.get(LogUtil.DEBUG_KEY).toString());
                    return Mono.just(q);
                }))
                .flatMap(q -> Mono.fromCallable(() -> {
                    amqpTemplate.convertAndSend(exchange, nextRoutingKey.getItem(), q);
                    return true;
                }));
    }
}
```

Key design:
- Routing keys are cycled **round-robin** using a circular linked list (`events1` → `events2` → `events3` → `events1` → ...)
- Debug context is propagated from the reactive context to the event object
- The event is sent synchronously via `AmqpTemplate` wrapped in `Mono.fromCallable()`

### EventQueObject

Located at `commons-mq/src/main/java/com/fincity/saas/commons/mq/events/EventQueObject.java`:

```java
@Data
@Accessors(chain = true)
public class EventQueObject implements Serializable {

    private String eventName;                   // Event type identifier
    private String clientCode;                  // Client context
    private String appCode;                     // App context
    private String xDebug;                      // Debug trace ID
    private Map<String, Object> data;           // Event payload
    private ContextAuthentication authentication; // Auth context of the triggering user
}
```

### Publishing an Event

```java
// Inject the service
private final EventCreationService ecService;

// Publish an event
ecService.createEvent(
    new EventQueObject()
        .setEventName(EventNames.USER_CREATED)
        .setClientCode(clientCode)
        .setAppCode(appCode)
        .setData(Map.of("userId", userId, "email", email))
        .setAuthentication(ca))
```

## Event Names

Defined in `commons-mq/src/main/java/com/fincity/saas/commons/mq/events/EventNames.java`:

```java
public class EventNames {
    public static final String CLIENT_CREATED = "CLIENT_CREATED";
    public static final String USER_CREATED = "USER_CREATED";
    public static final String CLIENT_REGISTERED = "CLIENT_REGISTERED";
    public static final String USER_REGISTERED = "USER_REGISTERED";
    public static final String USER_PASSWORD_CHANGED = "USER_$_CHANGED";
    public static final String USER_PASSWORD_RESET_DONE = "USER_$_RESET_DONE";
    public static final String USER_RESET_PASSWORD_REQUEST = "USER_RESET_$_REQUEST";
    public static final String USER_CODE_GENERATION = "USER_CODE_GENERATION";
    public static final String USER_OTP_GENERATE = "USER_OTP_GENERATE";
    public static final String INVOICE_CREATED = "INVOICE_CREATED";
}
```

The `$` in event names is a placeholder that can be formatted using:
```java
EventNames.getEventName(EventNames.USER_PASSWORD_CHANGED, "PASSWORD")
// Returns: "USER_PASSWORD_CHANGED"
```

## Consuming Events

Services that need to react to events configure RabbitMQ listeners. Event consumers are typically in the `mq/` package of each service.

### Consumer Pattern

```java
@Component
public class EventConsumer {

    @RabbitListener(queues = "${events.mq.queue:events-queue}")
    public void handleEvent(EventQueObject event) {
        switch (event.getEventName()) {
            case EventNames.USER_CREATED:
                handleUserCreated(event);
                break;
            case EventNames.CLIENT_REGISTERED:
                handleClientRegistered(event);
                break;
            // ...
        }
    }
}
```

## Commons MQ Libraries

| Library | Group | Purpose |
|---------|-------|---------|
| `commons-mq` | `com.fincity.saas` | Gen1 — `EventCreationService`, `EventQueObject`, `EventNames` |
| `commons2-mq` | `com.modlix.saas` | Gen2 — Updated event utilities for `files`, `notification` |

## Testing with Events

In unit and integration tests, `EventCreationService` is typically mocked:

```java
// In AbstractIntegrationTest
@MockitoBean
protected EventCreationService eventCreationService;

protected void setupMockBeans() {
    lenient().when(eventCreationService.createEvent(any()))
            .thenReturn(Mono.just(true));
}
```

In unit tests:
```java
@Mock
private EventCreationService ecService;

// Verify event was published
verify(ecService).createEvent(argThat(event ->
    event.getEventName().equals(EventNames.USER_CREATED)
    && event.getClientCode().equals("TESTCLIENT")));
```

## Adding a New Event

1. Add the event name constant to `EventNames.java`
2. Publish the event in the source service using `EventCreationService`
3. Add a consumer in the target service's `mq/` package
4. Configure the queue binding in the target service's configuration
5. Mock `EventCreationService` in tests
