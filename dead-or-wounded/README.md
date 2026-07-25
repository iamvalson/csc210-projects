# Dead or Wounded

A multiplayer-capable number-guessing game (Mastermind/Bulls-and-Cows style)
with a Java backend and a small HTML/CSS/JS frontend.

Each game generates a secret number with unique digits. Players submit
guesses of the same length and get back:

- **Dead** — a digit that's correct *and* in the correct position
- **Wounded** — a digit that's correct but in the wrong position

A game ends when a player guesses all digits correctly (`WON`) or runs out
of attempts (`LOST`).

## Architecture

```
game/
├── Main.java              entry point — builds config, starts both servers
├── core/                  game rules, no I/O, no framework dependencies
│   ├── GameSession.java        single game's state machine + scoring
│   ├── GameSessionManager.java session registry (create/lookup/remove)
│   ├── Player.java
│   ├── GuessResult.java
│   └── SecretNumberGenerator.java
├── api/                   protocol-agnostic application layer
│   ├── GameController.java     orchestrates core/ for both transports
│   ├── HttpApiServer.java      JSON-over-HTTP adapter (for the frontend)
│   └── dto/                    request/response records
└── server/                 raw TCP transport
    ├── GameServer.java         accept loop + thread pool
    ├── ClientHandler.java      per-connection line-protocol parser
    └── ServerConfig.java       CLI args / defaults
```

`core/` has no knowledge of sockets, HTTP, or threads beyond what it needs
for its own thread-safety — both transports (raw TCP and HTTP) go through
the same `GameController`, so a game is playable interchangeably from
either. The project intentionally has **zero external dependencies** — the
HTTP layer uses only `com.sun.net.httpserver` (bundled in the JDK), so
`pom.xml` stays dependency-free.

## Protocols

### Raw TCP (line protocol), default port `5050`

Connect with any raw TCP client (e.g. `ncat localhost 5050`):

| Command | Example | Response |
|---|---|---|
| `NEW <name>` | `NEW Alice` | `OK SESSION <id> SECRET_LENGTH 4 MAX_ATTEMPTS 10` |
| `GUESS <sessionId> <guess>` | `GUESS <id> 1234` | `RESULT DEAD 1 WOUNDED 2 ATTEMPTS 3/10 STATUS IN_PROGRESS` |
| `HISTORY <sessionId>` | `HISTORY <id>` | one guess per line |
| `QUIT` | `QUIT` | closes the connection |

### HTTP + JSON, default port `8081`

Used by `frontend/`. CORS is open (`Access-Control-Allow-Origin: *`).

| Endpoint | Method | Body | Response |
|---|---|---|---|
| `/api/new` | POST | `{"playerName": "Alice"}` | `{"sessionId": "...", "secretLength": 4, "maxAttempts": 10}` |
| `/api/guess` | POST | `{"sessionId": "...", "guess": "1234"}` | `{"deadCount": 1, "woundedCount": 2, "attemptsUsed": 3, "maxAttempts": 10, "status": "IN_PROGRESS"}` |
| `/api/history/<sessionId>` | GET | — | `["Guess: 1234, Dead: 1, Wounded: 2, Timestamp: ..."]` |

Both transports share the same `GameSessionManager`, so they operate on
the same set of live sessions.

## Building and running

Requires JDK 17+. No external dependencies.

```bash
mvn clean package
java -jar target/dead-or-wounded-server.jar
```

Or without Maven, straight from the JDK:

```bash
javac -d target/classes $(find src/main/java -name "*.java")
java -cp target/classes game.Main
```

### Config flags

All optional, passed as `--flag value` to `Main`:

| Flag | Default | Meaning |
|---|---|---|
| `--port` | `5050` | raw TCP port |
| `--httpPort` | `8081` | HTTP JSON API port |
| `--threadPoolSize` | `50` | max concurrent TCP client connections |
| `--secretNumberLength` | `4` | digits in the secret number |
| `--maxAttempts` | `10` | guesses allowed before `LOST` |

There is no turn timer — sessions stay open indefinitely until won, lost,
or the process restarts (sessions are in-memory only).

## Frontend

`frontend/` is a static, build-free HTML/CSS/JS page. With the backend
running, just open `frontend/index.html` in a browser — it talks to the
HTTP API on `localhost:8081`.

## Testing manually

With the server running, use `ncat` (or any TCP client) against the raw
protocol:

```
ncat localhost 5050
NEW Alice
GUESS <sessionId> 1234
HISTORY <sessionId>
QUIT
```

Or `curl` against the HTTP API:

```bash
curl -X POST http://localhost:8081/api/new -H "Content-Type: application/json" -d '{"playerName":"Alice"}'
curl -X POST http://localhost:8081/api/guess -H "Content-Type: application/json" -d '{"sessionId":"<id>","guess":"1234"}'
curl http://localhost:8081/api/history/<id>
```

## Concurrency

The server is built to handle many players connected and guessing at the
same time, safely. Concurrency shows up in four places:

### 1. `GameServer` — a thread per connection

```java
private final ExecutorService clientPool = Executors.newFixedThreadPool(config.getThreadPoolSize());
...
clientPool.submit(new ClientHandler(clientSocket, controller));
```

Every accepted `Socket` is handed to a fixed thread pool as a
`ClientHandler` task. This lets many players stay connected and blocked
on socket reads simultaneously without one slow/idle client stalling the
accept loop or any other client.

`running` is `volatile` because it's written by the shutdown-hook thread
and read by the accept loop thread on every iteration — `volatile`
guarantees that write becomes visible to the loop without a lock. `stop()`
is `synchronized` because it can be triggered from more than one place
(the JVM shutdown hook, or an exception path) and must not run twice
concurrently (e.g. double-closing the socket).

### 2. `GameSessionManager` — a concurrent session registry

```java
private final ConcurrentHashMap<String, GameSession> sessions = new ConcurrentHashMap<>();
```

Every client-handler thread (and every HTTP request thread) creates,
looks up, and removes sessions through this map concurrently. A plain
`HashMap` is not safe under concurrent mutation (it can corrupt its
internal structure or lose entries); `ConcurrentHashMap` gives thread-safe
`put`/`get`/`remove` without needing an external lock for the registry
itself.

### 3. `GameSession` — a lock around each game's mutable state

```java
private final ReentrantLock lock = new ReentrantLock();

public GuessResult submitGuess(String guess) {
    lock.lock();
    try { ... } finally { lock.unlock(); }
}
```

A single session's `status` and `history` can be touched from more than
one thread — for example, two requests for the same `sessionId` arriving
on different connections (one over TCP, one over HTTP) at nearly the same
time. The `ReentrantLock` makes each `submitGuess` (check status → score →
mutate `history`/`status`) atomic, so two concurrent guesses can't
interleave and leave the session in an inconsistent state (e.g. both
"winning" or the attempt count skipping/double-counting). `getStatus()`
and `getHistorySnapshot()` also take the lock so readers never see a
partial update.

### 4. `HttpApiServer` — a request thread pool

```java
httpServer.setExecutor(Executors.newCachedThreadPool());
```

The JDK's `HttpServer` runs handlers on the calling thread by default;
a cached thread pool is supplied so multiple `/api/*` requests can be
served concurrently instead of one at a time, mirroring how the raw TCP
side already handles concurrent clients via `clientPool`.

---

Earlier versions of this project also had a fifth mechanism: a
`ScheduledExecutorService`-backed `TurnTimer` that auto-expired a session
if a player didn't guess in time. It's been removed — games no longer
have a turn timeout, only a max-attempts limit.
