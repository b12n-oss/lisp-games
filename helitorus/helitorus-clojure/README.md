# helitorus-clojure

A helix of n windings around a torus, swept into a tube, drawn with
[raylib](https://www.raylib.com) from Clojure on the JVM over
[raylib-clj](https://github.com/b12n-oss/raylib-clj), which binds raylib with
coffi on Panama.

```sh
bb info                          # every task here
bb helitorus                     # run it
bb helitorus 10                  # quit after ten seconds of frame time
bb helitorus 3 out.png 40        # quit after three, capture frame 40
bb helitorus 3 out.png 40 900    # the same, starting at resolution 900
bb shot                          # run 3 seconds, write helitorus.png
bb check                         # load the namespace, no window
bb doctor                        # check the JDK and libraylib
```

Or `clojure -M:helitorus` directly, which is what the tasks wrap.

Drag to turn, wheel to zoom, LEFT and RIGHT change the winding count, UP and
DOWN change the resolution along the spine.

## Prerequisites

A JDK 22 or newer, because coffi sits on Panama, and a system raylib 6.0 or
newer:

```sh
brew install raylib       # macOS
```

Three JVM flags are load-bearing and `deps.edn` sets all three:
`--enable-native-access=ALL-UNNAMED`, without which Panama refuses the
downcall; `-XstartOnFirstThread`, because macOS puts GLFW's event loop on
thread 0; and `-Djava.library.path`, which is where libraylib is found. On
Linux drop `-XstartOnFirstThread`, since the JVM rejects it outright.

## What this port does

raylib-clj hands structs across as ordinary Clojure maps, so a `Color` is
`{:r :g :b :a}` and nothing is packed into an integer. That is the comfortable
end of the four ports.

What it does not bind is rlgl, raylib's lower-level immediate-mode layer, and
this program lives there. So the six calls it needs are declared in the source
with coffi's own `defcfn`, which wants the C symbol, the argument types and the
return type and nothing else. That is the same escape hatch the Pac-Man port in
[raylib-pacman](https://github.com/b12n-oss/raylib-pacman) uses for its two
calls, and it is worth seeing how little it costs to extend a binding.

One wrinkle follows from the maps: `rlColor4ub` takes four separate bytes, so
the palette stays as three int arrays and is submitted component by component
rather than as a `Color`.

The surface goes out **one rlgl batch per ring**, which is not optional here or
in any other port. rlgl cannot flush inside an open `rlBegin`/`rlEnd`, and the
whole figure overflows the vertex buffer in one go.

## The type hints are load-bearing

The array declarations carry `^"[D"` and `^"[I"`, and this is the single most
important thing in the file. Without them `aget` cannot resolve at the call
site and every read goes through `clojure.lang.RT` reflectively. Measured on
this exact file:

| | compute | draw | fps |
|---|---|---|---|
| unhinted | 21.0 ms | 78.0 ms | 20 |
| hinted | 0.7 ms | 0.3 ms | 115 |

Thirty times on compute and over two hundred on draw. It is the JVM's spelling
of the concern the original's own comment raises about `aset` on babashka, and
the one the jolt port answers with `^double/1` and `^int/1`.

Note the hint has to be the array class. `^doubles` on a `def` resolves to
`clojure.core/doubles`, the function, and the namespace fails to compile with
`Unable to resolve classname`. `^"[D"` is the form that works.

`clojure -M:check` is a plain load gate and will not tell you about this.
`(set! *warn-on-reflection* true)` will, and a clean file reports nothing.

## What it costs

Measured on an M1 Pro, against the other two ports running the same geometry:

| Resolution | | compute | draw | fps |
|---|---|---|---|---|
| 260 (default) | Clojure | 0.7 ms | 0.3 ms | 115 |
| | jolt | 1.5 ms | 1.0 ms | 115 |
| | babashka | 5.1 ms | 2.3 ms | 115 |
| 900 (max) | Clojure | 2.4 ms | 1.0 ms | 116 |
| | jolt | 5.0 ms | 3.3 ms | ~95 |
| | babashka | 18.0 ms | 8.3 ms | ~35 |

The JVM is the fastest of the three and is the only one that still holds the
full frame cap at 900 rings. That is a JIT with primitive array stores doing
what it is good at. The gap costs a slower start and three mandatory flags,
which is the trade the whole repo exists to show.

## Credit

The program is **Michiel Borkent**'s ([@borkdude](https://github.com/borkdude)).
This port descends from
[`examples/helitorus.clj`](https://github.com/babashka/ffi/blob/main/examples/helitorus.clj)
in [babashka/ffi](https://github.com/babashka/ffi), by way of the babashka port
next door. The geometry, painter ordering, backface test, lighting and palette
are his and unchanged; what changed is the layer underneath.

babashka/ffi is MIT licensed, and the notice is reproduced in
[NOTICE.md](../../NOTICE.md) at the root of this repository.
