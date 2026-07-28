# Dead or Wounded

A multiplayer-capable number-guessing game (Mastermind/Bulls-and-Cows style)
with a Java backend and a Java Swing desktop GUI.

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
│   ├── GameSession.java        single-player game's state machine + scoring
│   ├── GameSessionManager.java single-player session registry
│   ├── RaceSession.java        shared-secret multiplayer race (AtomicReference + ConcurrentHashMap)
│   ├── RaceSessionManager.java race registry, generates shareable race codes
│   ├── GuessScorer.java        Dead/Wounded scoring + format validation (shared by both session types)
│   ├── Player.java
│   ├── GuessResult.java
│   ├── RaceOutcome.java / RaceStatus.java
│   └── SecretNumberGenerator.java
├── api/                   protocol-agnostic application layer
│   ├── GameController.java     orchestrates core/ single-player sessions
│   ├── RaceController.java     orchestrates core/ race sessions
│   ├── HttpApiServer.java      JSON-over-HTTP adapter (network clients)
│   └── dto/                    request/response records
├── server/                 raw TCP transport (single-player only)
│   ├── GameServer.java         accept loop + thread pool
│   ├── ClientHandler.java      per-connection line-protocol parser
│   └── ServerConfig.java       CLI args / defaults
└── gui/                   desktop UI
    ├── GameGui.java            single-player Swing client, plays directly against core/ in-process
    └── RaceGui.java            multiplayer Swing client, talks to a running game.Main over HTTP
```

`core/` has no knowledge of sockets, HTTP, or threads beyond what it needs
for its own thread-safety. Single-player games (`GameSession`) go through
`GameController`, over raw TCP, HTTP, or in-process from `GameGui`.
Races (`RaceSession`) go through `RaceController`, over HTTP only, from
one or more `RaceGui` instances — see [Race mode](#race-mode-lan-multiplayer)
below. The project intentionally has **zero external dependencies** — the
HTTP layer uses only `com.sun.net.httpserver` (bundled in the JDK) and the
GUIs use only `javax.swing` / `java.net.http`, so `pom.xml` stays
dependency-free.

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

Used by the GUIs (`GameGui` calls the game engine in-process instead, but
`RaceGui` uses this over the network). CORS is open
(`Access-Control-Allow-Origin: *`).

**Single-player:**

| Endpoint | Method | Body | Response |
|---|---|---|---|
| `/api/new` | POST | `{"playerName": "Alice"}` | `{"sessionId": "...", "secretLength": 4, "maxAttempts": 10}` |
| `/api/guess` | POST | `{"sessionId": "...", "guess": "1234"}` | `{"deadCount": 1, "woundedCount": 2, "attemptsUsed": 3, "maxAttempts": 10, "status": "IN_PROGRESS", "secretNumber": null}` |
| `/api/history/<sessionId>` | GET | — | `["Guess: 1234, Dead: 1, Wounded: 2, Timestamp: ..."]` |

`secretNumber` in the `/api/guess` response is `null` while the game is
`IN_PROGRESS`, and filled in once it's `WON` or `LOST`.

**Race (multiplayer)** — see [Race mode](#race-mode-lan-multiplayer):

| Endpoint | Method | Body | Response |
|---|---|---|---|
| `/api/race/new` | POST | `{"playerName": "Alice"}` | `{"raceId": "Z9FRT", "playerName": "Alice", "secretLength": 4, "maxAttempts": 10}` |
| `/api/race/join` | POST | `{"raceId": "Z9FRT", "playerName": "Bob"}` | same shape as `/api/race/new` |
| `/api/race/guess` | POST | `{"raceId": "Z9FRT", "playerName": "Alice", "guess": "1234"}` | `{"deadCount":1,"woundedCount":2,"attemptsUsed":3,"maxAttempts":10,"status":"IN_PROGRESS","secretNumber":null,"winnerName":null}` |
| `/api/race/status/<raceId>` | GET | — | `{"raceId":"Z9FRT","secretLength":4,"maxAttempts":10,"winnerName":null,"secretNumber":null,"leaderboard":[{"playerName":"Alice","attemptsUsed":3},{"playerName":"Bob","attemptsUsed":1}]}` |

`status` on a race guess is one of `IN_PROGRESS`, `WON` (this guess was
the winning one), `LOST` (this player used up their own attempts without
winning — the race continues for everyone else), or `RACE_OVER` (someone
else already won). `secretNumber`/`winnerName` stay `null` until the race
has a winner.

The single-player and race registries are independent — a `sessionId`
from `/api/new` and a `raceId` from `/api/race/new` are never
interchangeable.

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

## GUI

There are two Swing clients, for the two ways to play:

| | `GameGui` | `RaceGui` |
|---|---|---|
| Mode | Solo | Multiplayer race (LAN) |
| Needs a server running? | No | Yes (`game.Main`) |
| Talks to | Its own in-process engine | `HttpApiServer` over HTTP |

### `GameGui` — solo play

Plays directly against the core game engine in its own process (its own
`GameSessionManager`), so it needs no server running — just launch it on
its own:

```bash
mvn clean package
java -cp target/classes game.gui.GameGui
```

Or without Maven:

```bash
javac -d target/classes $(find src/main/java -name "*.java")
java -cp target/classes game.gui.GameGui
```

It accepts the same `--secretNumberLength` / `--maxAttempts` flags as
`Main` (see [Config flags](#config-flags)); `--port` / `--httpPort` /
`--threadPoolSize` are accepted but unused since it doesn't open any
network ports.

### `RaceGui` — play against a friend

See [Race mode](#race-mode-lan-multiplayer) below.

## Race mode (LAN multiplayer)

Instead of everyone getting their own private secret, a **race**
generates one secret and lets several players guess against it at the
same time — first to guess it fully `Dead` wins.

### The concurrency problem this raises

A sequential, single-player game has nothing concurrent about it. A race
does, because now several threads (one per incoming HTTP request, from
different players, arriving close together) all touch the *same* shared
game state:

- **The secret itself is safe with no locking at all** — `RaceSession`
  sets it once at construction and never mutates it, so every thread can
  read it freely.
- **"Who won?" is the dangerous shared, mutable bit.** Two players could
  both submit a fully-correct guess in nearly the same instant. This is
  solved with `java.util.concurrent.atomic.AtomicReference<String>` and
  `compareAndSet`:

  ```java
  boolean iWon = winner.compareAndSet(null, playerName);
  ```

  Exactly one thread's CAS can ever succeed, no matter how close the
  timing — the loser of that race gets `RACE_OVER` instead of `WON`,
  deterministically, with no lock required. This was stress-tested with
  2000 trials of two real threads racing the same correct guess
  simultaneously; exactly one `WON` and one `RACE_OVER` came out every
  single time.
- **The shared leaderboard** (attempts per player) is a
  `ConcurrentHashMap<String, AtomicInteger>`, since every player's
  request thread reads and increments it concurrently.
- **Cooperative cancellation** — a race has no long-lived per-player
  thread to interrupt (each guess is a discrete HTTP request, not a
  blocking loop), so "stop the losers promptly" instead means: every
  `submitGuess` checks the winner reference *first* and short-circuits to
  `RACE_OVER` if it's already set, and `RaceGui` polls
  `/api/race/status/<raceId>` roughly once a second so even a player who
  isn't actively guessing finds out the race ended on the next poll,
  without needing to submit a guess of their own.

### Running a race

**1. One person hosts the server**, on whichever machine will stay on
during the race:

```bash
mvn clean package
java -jar target/dead-or-wounded-server.jar
```

On startup it prints the addresses it's reachable at, e.g.:

```
HTTP API server started on port 8081
Reachable on your network at:
  http://192.168.1.23:8081
```

That `192.168.1.23:8081` (yours will differ) is what everyone else needs
to connect to. Both players must be **on the same wifi network** for this
to work out of the box.

> **First-time firewall prompt:** the first time the server starts and
> listens on a port, Windows Firewall (or macOS's firewall) will likely
> ask whether to allow Java to accept incoming connections on private
> networks — click **Allow**. If you miss the prompt and other players
> can't connect, check Windows Defender Firewall → "Allow an app through
> firewall" and make sure `java.exe` is allowed on **Private** networks.

**2. Everyone launches `RaceGui`** (see [installing on another
PC](#installing-on-another-pc) if it's not their machine):

```bash
java -cp target\classes game.gui.RaceGui
```

**3. One player hosts, the others join:**

- **Host:** enter the server address (`localhost:8081` if you're on the
  same machine as the server, otherwise the LAN address it printed, e.g.
  `192.168.1.23:8081`), your name, and click **Host New Race**. You'll be
  given a short race code (e.g. `Z9FRT`) — share that with the other
  player(s) however's convenient (chat, shout across the room, etc).
- **Joiners:** enter the *same* server address, the race code, your name,
  and click **Join Race**.

Everyone then guesses independently; the shared leaderboard (attempts per
player) updates roughly once a second for everyone, and as soon as anyone
wins, every other connected player is told who won and the secret number
within a second, whether they were actively guessing or not.

### Installing on another PC

To have a friend play from their own computer, they need:

1. **Java 17 or newer installed.** Check with `java -version` in a
   terminal. If it's missing, install a JDK (e.g. from
   [adoptium.net](https://adoptium.net)) — the installer handles adding
   `java` to their PATH.
2. **A copy of the built classes.** The simplest way: after you run
   `mvn clean package` (or the `javac` command above) on your machine,
   zip up the whole `target/classes` folder and send it to them (USB
   drive, file share, email — whatever's easiest), then have them unzip
   it anywhere. They do **not** need Maven, the source code, or to build
   anything themselves — just the compiled `.class` files and a JDK/JRE
   to run them.
3. **Run it:**

   ```bash
   java -cp path\to\classes game.gui.RaceGui
   ```

   (adjust the path to wherever they unzipped it)

Alternatively, if they're comfortable with git and want the full source:
clone/copy the whole project folder onto their machine and build it
themselves with the [Building and running](#building-and-running) steps
above — same result, just with the source included.

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

Or drive a race from two terminals, without either GUI:

```bash
curl -X POST http://localhost:8081/api/race/new -H "Content-Type: application/json" -d '{"playerName":"Alice"}'
curl -X POST http://localhost:8081/api/race/join -H "Content-Type: application/json" -d '{"raceId":"<id>","playerName":"Bob"}'
curl -X POST http://localhost:8081/api/race/guess -H "Content-Type: application/json" -d '{"raceId":"<id>","playerName":"Alice","guess":"1234"}'
curl http://localhost:8081/api/race/status/<id>
```

## Concurrency

The server is built to handle many players connected and guessing at the
same time, safely. Concurrency shows up in six places:

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
side already handles concurrent clients via `clientPool`. This same pool
also serves every `/api/race/*` request, which is what makes races
genuinely concurrent — different players' guesses run on different pool
threads at the same time, all against the same `RaceSession`.

### 5. `RaceSession` — a lock-free winner decision

```java
private final AtomicReference<String> winner = new AtomicReference<>(null);
...
boolean iWon = winner.compareAndSet(null, playerName);
```

Unlike `GameSession` (one player, so a simple lock around its state is
enough), a race has *many* players' request threads calling
`submitGuess` on the same `RaceSession` at once, racing to be the one who
solved it. `compareAndSet` makes "declare a winner" a single atomic
hardware-level operation instead of a check-then-act pair — there's no
window where two threads could both observe "no winner yet" and both
proceed to declare themselves the winner. See
[Race mode](#race-mode-lan-multiplayer) for the stress test that verifies
this holds under real contention, and for how the absence of a
long-lived per-player thread changes what "cancel the losers" means here
compared to a typical interrupt-flag pattern.

### 6. `RaceSession` / `RaceSessionManager` — concurrent leaderboard and race registry

```java
private final Map<String, AtomicInteger> attemptsByPlayer = new ConcurrentHashMap<>();
private final ConcurrentHashMap<String, RaceSession> races = new ConcurrentHashMap<>();
```

Same reasoning as `GameSessionManager` (#2), applied twice more: every
player's attempt count is a `ConcurrentHashMap` entry so concurrent
guesses from different players update the shared leaderboard safely, and
`RaceSessionManager` itself is a `ConcurrentHashMap` so multiple races can
be created/looked-up concurrently by different HTTP request threads
without corrupting the registry.

---

Earlier versions of this project also had another mechanism: a
`ScheduledExecutorService`-backed `TurnTimer` that auto-expired a session
if a player didn't guess in time. It's been removed — games no longer
have a turn timeout, only a max-attempts limit.
