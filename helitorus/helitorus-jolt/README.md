# helitorus-jolt

A helix of n windings around a torus, swept into a tube, drawn with
[raylib](https://www.raylib.com) from [jolt](https://github.com/jolt-lang/jolt)
over [net.b12n/raylib](https://github.com/jlt-commons/raylib-jlt) on `jolt.ffi`.

```sh
bb info                          # every task here
bb helitorus                     # run it
bb helitorus 10                  # quit after ten seconds of frame time
bb helitorus 3 out.png 40        # quit after three, capture frame 40
bb helitorus 3 out.png 40 900    # the same, starting at resolution 900
bb shot                          # run 3 seconds, write helitorus.png
bb check                         # compile the namespace, no window
bb doctor                        # check jolt and libraylib
```

Or `jolt -M:helitorus` directly, which is what the tasks wrap.

Drag to turn, wheel to zoom, LEFT and RIGHT change the winding count, UP and
DOWN change the resolution along the spine.

## Prerequisites

`jolt` 0.8.0 or newer, which `deps.edn` declares as a floor so an older runtime
refuses rather than writing to the wrong place, and a system raylib 6.0 or
newer:

```sh
brew install raylib       # macOS
```

The raylib bindings come from the `lib/` subtree of `jlt-commons/raylib-jlt`,
pinned by sha. `:jolt/native` for libraylib is declared over there and
inherited here, which is why this `deps.edn` says nothing about where the
library lives.

## What this port does

`jolt.ffi` passes structs by value, so the packed-uint `Color` here is a
choice, the same one the babashka port makes and for the same reason: a
four-byte all-integer struct rides in a single register on both ABIs, so
packing is the identical memory with no map built per draw call.

The surface goes out through rlgl immediate mode, **one batch per ring**, which
is not optional. rlgl cannot flush inside an open `rlBegin`/`rlEnd`, and the
whole figure overflows the vertex buffer in one go. A port that collapses the
batches renders part of the torus and drops the rest.

Primitive arrays hold the projected surface, with `^double/1` and `^int/1`
hints on every one and `aset-double` / `aset-int` rather than plain `aset`, so
the writes compile to array stores instead of a generic path. This is the same
concern as in the babashka port, answered with jolt's own type-hint syntax.

Backface culling is switched off, because this example decides visibility
itself by the sign of a 2D cross product in screen space. That is not a winding
rule, and raylib's cull drops exactly the faces the test keeps, so leaving it
on renders the surface inside out.

## What it costs

Measured on an M1 Pro, against the babashka port running the same geometry:

| Resolution | | compute | draw | fps |
|---|---|---|---|---|
| 260 (default) | jolt | 1.5 ms | 1.0 ms | 115 |
| | babashka | 5.1 ms | 2.3 ms | 115 |
| 900 (max) | jolt | 5.0 ms | 3.3 ms | ~95 |
| | babashka | 18.0 ms | 8.3 ms | ~35 |

jolt runs the arithmetic about three and a half times faster. The part worth
noticing is the top half of that table, where both runtimes sit at exactly the
same frame rate, because at 260 rings neither one is the bottleneck. You have
to push to 900 before the difference shows up as something a viewer would see.

## Credit

The program is **Michiel Borkent**'s ([@borkdude](https://github.com/borkdude)).
It originated as
[`examples/helitorus.clj`](https://github.com/babashka/ffi/blob/main/examples/helitorus.clj)
in [babashka/ffi](https://github.com/babashka/ffi), and this port arrived by
way of the `helitorus` example in
[jlt-commons/raylib-jlt](https://github.com/jlt-commons/raylib-jlt), which is
itself a port of it. The geometry, painter ordering, backface test, lighting
and palette are unchanged from the original all the way down that chain. What
this port adds is the unattended run arguments and a HUD line echoed to stdout.

babashka/ffi is MIT licensed, and the notice is reproduced in
[NOTICE.md](../../NOTICE.md) at the root of this repository.
