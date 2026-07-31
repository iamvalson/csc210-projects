# Fleet Vehicle Tracker (Java Swing)

A working version of the *Java Concurrency in Practice* vehicle tracker
example (Ch. 4), wired up to a small Swing GUI so you can actually watch
it run: a "view thread" rendering vehicle positions, and independent
"updater threads" moving them around, all sharing one thread-safe data
model.

Vehicles now travel real, road-snapped routes across the **University
of Lagos (Akoka) campus**, drawn over a real Mapbox map background,
and disappear when they reach their destination (optionally
reappearing later to start over) — see "The map layer" below.

## Files

| File                                   | Role |
|-----------------------------------------|------|
| `Point.java`                            | Immutable pixel point — safe to hand out without copying. |
| `MutablePoint.java`                     | Mutable pixel point — **not** thread-safe by itself (Listing 4.5). |
| `VehicleTracker.java`                   | Interface both strategies below implement. |
| `MonitorVehicleTracker.java`            | **Monitor pattern**: one lock guards a `Map<String, MutablePoint>`; every read defensively copies (Listing 4.4). |
| `DelegatingVehicleTracker.java`         | **Delegation**: thread safety comes from `ConcurrentHashMap` + immutable `Point`; no copying needed on read (the "relaxed encapsulation" version from later in the chapter). |
| `VehicleSimulator.java`                 | The original random-walk updater threads. Left in place for reference/comparison; no longer wired up by default. |
| `VehicleTrackerPanel.java`              | Swing `JPanel` — the view; now also paints a map background image. |
| `VehicleTrackerApp.java`                | Wires it all together; entry point; defines the campus routes. |
| `GeoPoint.java`                         | An immutable real-world lat/lng coordinate (as opposed to `Point`'s pixel coordinates). |
| `MapProjector.java`                     | Converts lat/lng into the pixel coordinates that line up with the static map image. Provider-agnostic. |
| `PolylineDecoder.java`                  | Decodes the road-snapped path the Directions API returns. Provider-agnostic. |
| `MapboxStaticMapClient.java`            | Fetches the campus background image from the Mapbox Static Images API. |
| `MapboxDirectionsClient.java`           | Fetches a real road route between two campus points from the Mapbox Directions API. |
| `VehicleRoute.java`                     | A route as a sequence of real-world points, walked by distance so speed stays even. |
| `RouteFollowingVehicleSimulator.java`   | The updater: moves vehicles along real routes, removes them on arrival, optionally respawns them. |

## The map layer

`VehicleTrackerApp` defines four short trips between real, named
locations on the UNILAG campus (main gate, gate house, Faculty of
Engineering, Senate Building, sports center, engineering lecture
theatre). On startup it:

1. Fetches a **Mapbox Static Images** picture of the campus as the
   panel's background.
2. Calls the **Mapbox Directions API** for each trip to get a real,
   road-snapped path (decoded from the polyline it returns).
3. Hands those routes to `RouteFollowingVehicleSimulator`, which moves
   each vehicle along its route and calls `tracker.removeVehicle(id)`
   the moment it arrives — that's the "disappears at its destination"
   behavior. By default it also respawns the vehicle at the route's
   start after a few seconds so the demo keeps running; set
   `RESPAWN_DELAY_MS = -1` in `VehicleTrackerApp` if you want vehicles
   to vanish for good instead.

The Google-vs-Mapbox choice only lives in two files
(`MapboxStaticMapClient.java` and `MapboxDirectionsClient.java`) —
everything else (`GeoPoint`, `MapProjector`, `VehicleRoute`,
`PolylineDecoder`, both trackers, the panel) doesn't know or care
which mapping provider is behind them.

### Getting a Mapbox access token

1. Sign up at [mapbox.com](https://www.mapbox.com/) and go to your
   [account page](https://account.mapbox.com/).
2. Copy your **default public token** (starts with `pk.`), or create a
   new one — either works for the Static Images and Directions APIs
   used here.
3. Set it as an environment variable before running the app:

**macOS/Linux:**
```bash
export MAPBOX_ACCESS_TOKEN="pk.your-token-here"
```

**Windows PowerShell:**
```powershell
$env:MAPBOX_ACCESS_TOKEN = "pk.your-token-here"
```

**Windows Command Prompt:**
```cmd
set MAPBOX_ACCESS_TOKEN=pk.your-token-here
```

Don't hardcode the token in source or commit it anywhere. Check
Mapbox's current pricing page for their free-tier limits before
relying on this beyond a demo — those numbers change over time.

### Running without a token

The app still runs with no token set — it just falls back to
straight-line "routes" between the same real campus coordinates over
a plain background, so you can see the motion-and-disappearing
mechanics with zero setup. It also falls back per-trip if a specific
Directions call fails, and falls back to a plain background if the
Static Images fetch fails — check stderr for what happened.

If a route comes back empty in `driving` mode (some internal campus
paths aren't classified as roads), try switching `DIRECTIONS_PROFILE`
in `VehicleTrackerApp` to `"walking"`.

If the dots look slightly offset from the roads once you're on a real
map, try nudging `ZOOM` by ±1 — Mapbox's zoom levels are meant to line
up with the same convention other providers use, but it's the first
thing worth checking if something looks a few pixels off.

### Config knobs (all in `VehicleTrackerApp.java`)

- `MAP_CENTER`, `ZOOM`, `LOGICAL_SIZE`, `SCALE` — the map frame.
- `TRIPS` — which vehicle travels between which two campus points.
- `SPEED_METERS_PER_TICK` — how fast vehicles move.
- `RESPAWN_DELAY_MS` — pause before a vehicle reappears; `-1` disables
  respawning entirely.

## Compile & run

No build tool needed — everything's in the default package. Requires
**JDK 11+** (the map/route clients use `java.net.http.HttpClient`).

```bash
javac *.java
java VehicleTrackerApp
```

You'll see a window with colored dots (the vehicles) following real
roads across the UNILAG campus map, each moved by its own background
thread, vanishing as each one arrives.

## The two strategies, side by side

Both `MonitorVehicleTracker` and `DelegatingVehicleTracker` implement
the exact same `VehicleTracker` interface, so `VehicleTrackerApp`,
`VehicleTrackerPanel`, and the simulators don't need to know or care
which one is in use. Flip the flag at the top of
`VehicleTrackerApp.java` to switch:

```java
private static final boolean USE_DELEGATING_TRACKER = false; // try true
```

**MonitorVehicleTracker** — the classic monitor pattern. All state
(`Map<String, MutablePoint>`) is private and guarded by `synchronized`.
Since `MutablePoint` isn't thread-safe on its own, a live one can never
be returned to a caller — `getLocations()` builds a brand-new map of
brand-new `Point` copies every time, while holding the lock. That
guarantees callers see a **consistent snapshot** of the whole fleet at
one instant, at the cost of an allocation on every read.

**DelegatingVehicleTracker** — instead of managing a lock by hand, it
delegates to a `ConcurrentHashMap`, and switches the value type to the
*immutable* `Point`. An immutable object can never be caught
mid-mutation, so it's always safe to publish directly — `getLocations()`
just returns an unmodifiable view of the live map, no copying. "Moving"
a vehicle means atomically swapping in a new `Point` via `replace()`.
The trade-off: that view can change while a caller is still iterating
it, so two vehicles read in the same pass aren't guaranteed to be from
the same instant. Cheaper, but a weaker consistency guarantee — exactly
the trade-off the book calls out.

Both now also support `addVehicle(id, x, y)` (bring a new/respawning
vehicle onto the map) and `removeVehicle(id)` (make one disappear),
alongside the original `setLocation` (move a vehicle that must already
exist — still strict, still throws on an unknown id).

## Where the threads actually are

- **View thread**: `VehicleTrackerApp` starts a `javax.swing.Timer`
  that fires every 100ms. Swing timers always fire on the Event
  Dispatch Thread, so `panel.repaint()` — and the `getLocations()`
  call inside `paintComponent` — are always safe with respect to
  Swing's single-thread rule.
- **Updater threads**: `RouteFollowingVehicleSimulator` runs one
  scheduled task per vehicle on a background thread pool, each
  periodically calling `setLocation(...)` (and `removeVehicle`/
  `addVehicle` at the ends of a trip). These never touch a Swing
  component directly — only the thread-safe tracker — so no
  `SwingUtilities.invokeLater` is needed on that side at all. That
  decoupling is the whole point of making the data model thread-safe
  in its own right: the GUI layer and the update layer don't need to
  coordinate directly with each other, only with the shared,
  thread-safe model.
- **Network calls** (fetching the map image and the routes) happen
  once, synchronously, in `main()` before the GUI or any updater
  thread starts — so there's no concurrency concern around them at
  all.

## Extending it

- Add more `Trip` entries in `VehicleTrackerApp` for a bigger fleet.
- Swap the campus for anywhere else — just change `MAP_CENTER` and the
  named `GeoPoint` constants.
- Add a dispatcher control (e.g. a text field + button calling
  `tracker.setLocation(...)`) to move a vehicle manually from the EDT —
  it's just another writer, and the tracker is already safe for that.
- Draw the route itself (not just the moving dot) by keeping each
  `VehicleRoute`'s waypoints around and projecting all of them once,
  then drawing a polyline in `paintComponent`.
