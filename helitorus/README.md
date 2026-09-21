# helitorus

A helix of n windings around a torus, swept into a tube, turned by the mouse.

![helitorus in babashka](../docs/demos/helitorus-babashka.png)

## Credit

The program is **Michiel Borkent**'s ([@borkdude](https://github.com/borkdude)).
Every port here descends from
[`examples/helitorus.clj`](https://github.com/babashka/ffi/blob/main/examples/helitorus.clj)
in [babashka/ffi](https://github.com/babashka/ffi), which is where the geometry,
the per-ring painter ordering, the backface test, the lighting and the palette
all came from. The ports keep the maths unchanged and vary only in how each
runtime reaches raylib.

His header credits it one step further back, to a Scittle demo drawing the same
figure to a 2D canvas. babashka/ffi is MIT licensed and the notice is in
[NOTICE.md](../NOTICE.md).

## Ports

| Runtime | Directory | Binding | Status |
|---|---|---|---|
| babashka | [`helitorus-babashka/`](helitorus-babashka) | `babashka.ffi` | runs |
| jolt | [`helitorus-jolt/`](helitorus-jolt) | `net.b12n/raylib`, `jolt.ffi` | runs |
| Clojure on the JVM | [`helitorus-clojure/`](helitorus-clojure) | raylib-clj, coffi over Panama | runs |
| jank | `helitorus-jank/` | `cpp/` interop | not written yet |

| | |
|---|---|
| **babashka** <br> ![babashka](../docs/demos/helitorus-babashka.png) | **jolt** <br> ![jolt](../docs/demos/helitorus-jolt.png) |
| **Clojure/JVM** <br> ![clojure](../docs/demos/helitorus-clojure.png) | |

## What makes this one worth porting

Pac-Man barely works the interpreter. A few hundred map updates per frame, a
60 FPS cap, and every runtime looks the same. helitorus does not let anyone off
that lightly. Every frame it walks the whole spine, builds a ring of twelve
points around each spine point, projects all of them, shades them, then sorts
the rings back to front. At the default resolution that is 260 rings and 3120
vertices rebuilt from scratch, sixty or more times a second, in Clojure rather
than in C.

So this is where the runtimes should actually separate, and the HUD is built to
show it: compute milliseconds and draw milliseconds are reported apart, because
only the first of them is the language's problem. Measured numbers beat guesses
about which runtime would struggle.

Three ports in, on an M1 Pro:

| Resolution | | compute | draw | fps |
|---|---|---|---|---|
| 260 (default) | Clojure | 0.7 ms | 0.3 ms | 115 |
| | jolt | 1.5 ms | 1.0 ms | 115 |
| | babashka | 5.1 ms | 2.3 ms | 115 |
| 900 (max) | Clojure | 2.4 ms | 1.0 ms | 116 |
| | jolt | 5.0 ms | 3.3 ms | ~95 |
| | babashka | 18.0 ms | 8.3 ms | ~35 |

The ordering is unsurprising once you see it: a JIT with primitive array stores,
then an AOT-compiled runtime, then an interpreter. What is worth noticing is
the top third, where all three sit at exactly the same frame rate, because at
260 rings none of them is the bottleneck. It takes 900 rings before the gap
becomes something a viewer would see, and even there babashka is still drawing
a moving picture. That is further than the interpreter is usually given credit
for.

One number in that table was wrong by a factor of thirty before it was checked.
The Clojure port first measured 21 ms compute and 78 ms draw, slower than
babashka, which is backwards and was the clue. Every `aget` was resolving
reflectively for want of an array type hint. See
[helitorus-clojure/README.md](helitorus-clojure/README.md); the lesson
generalises, since each runtime has its own spelling of this same trap.

## Running it

```sh
cd helitorus-babashka && bb helitorus
```

Drag to turn, wheel to zoom, LEFT and RIGHT change the winding count, UP and
DOWN change the resolution along the spine.

## Notes for the ports still to come

Two things in the babashka source are the parts that will not move over
unchanged.

**Primitive arrays.** The surface is held in `double-array` and `int-array`
buffers written with `aset-double` and `aset-int`, deliberately rather than with
plain `aset`, because on babashka a plain `aset` on a primitive array goes
through `java.lang.reflect.Array` and costs about 6.7 microseconds a write
against roughly 37 nanoseconds typed. That comment is in the original and it is
worth keeping. jolt and jank will each need their own answer here.

**One rlgl batch per ring.** rlgl cannot flush inside an open
`rlBegin`/`rlEnd`, and the whole surface overflows the vertex buffer in one go,
so `draw!` opens and closes a batch per ring. Any port that collapses that into
a single batch will render part of the figure and drop the rest.
