# Fleet Vehicle Tracker (Java Swing)

A working version of the *Java Concurrency in Practice* vehicle tracker
example (Ch. 4), wired up to a small Swing GUI so you can actually watch
it run: a "view thread" rendering vehicle positions, and independent
"updater threads" moving them around, all sharing one thread-safe data
model.

## Files

| File                          | Role |
|--------------------------------|------|
| `Point.java`                   | Immutable point — safe to hand out without copying. |
| `MutablePoint.java`            | Mutable point — **not** thread-safe by itself (Listing 4.5). |
| `VehicleTracker.java`          | Interface both strategies below implement. |
| `MonitorVehicleTracker.java`   | **Monitor pattern**: one lock guards a `Map<String, MutablePoint>`; every read defensively copies (Listing 4.4). |
| `DelegatingVehicleTracker.java`| **Delegation**: thread safety comes from `ConcurrentHashMap` + immutable `Point`; no copying needed on read (the "relaxed encapsulation" version from later in the chapter). |
| `VehicleSimulator.java`        | Background threads standing in for GPS feeds / dispatcher input. |
| `VehicleTrackerPanel.java`     | Swing `JPanel` — the view. |
| `VehicleTrackerApp.java`       | Wires it all together; entry point. |

## Compile & run

No build tool needed — everything's in the default package.

```bash
javac *.java
java VehicleTrackerApp
```

You'll see a window with colored dots (the vehicles) drifting around
randomly, each one moved by its own background thread.

## The two strategies, side by side

Both `MonitorVehicleTracker` and `DelegatingVehicleTracker` implement
the exact same `VehicleTracker` interface, so `VehicleTrackerApp`,
`VehicleTrackerPanel`, and `VehicleSimulator` don't need to know or
care which one is in use. Flip the flag at the top of
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

## Where the threads actually are

- **View thread**: `VehicleTrackerApp` starts a `javax.swing.Timer`
  that fires every 100ms. Swing timers always fire on the Event
  Dispatch Thread, so `panel.repaint()` — and the `getLocations()`
  call inside `paintComponent` — are always safe with respect to
  Swing's single-thread rule.
- **Updater threads**: `VehicleSimulator` runs one scheduled task per
  vehicle on a background thread pool, each periodically calling
  `setLocation(...)`. These never touch a Swing component directly —
  only the thread-safe tracker — so no `SwingUtilities.invokeLater` is
  needed on that side at all. That decoupling is the whole point of
  making the data model thread-safe in its own right: the GUI layer
  and the update layer don't need to coordinate directly with each
  other, only with the shared, thread-safe model.

## Extending it

- Swap the random-walk simulator for a real GPS/network feed — nothing
  else needs to change, since it only depends on `VehicleTracker`.
- Add a dispatcher control (e.g. a text field + button calling
  `tracker.setLocation(...)`) to move a vehicle manually from the EDT —
  it's just another writer, and the tracker is already safe for that.
