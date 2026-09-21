# helitorus-babashka

A helix of n windings around a torus, swept into a tube, drawn with
[raylib](https://www.raylib.com) from [babashka](https://babashka.org) over the
built-in `babashka.ffi`. No build step and no dependencies beyond `bb` itself.

```sh
bb info                          # every task here
bb helitorus                     # run it
bb helitorus 10                  # quit after ten seconds of frame time
bb helitorus 3 out.png 40        # quit after three, capture frame 40
bb helitorus 3 out.png 40 400    # the same, starting at resolution 400
bb shot                          # run 3 seconds, write helitorus.png
bb check                         # load the namespace, no window
bb doctor                        # is libraylib where the FFI can find it
```

Drag to turn, wheel to zoom, LEFT and RIGHT change the winding count, UP and
DOWN change the resolution along the spine.

## Prerequisites

`bb` 1.13.220, because this uses the `babashka.ffi` built into babashka. That
namespace is present in 1.13.220 and absent in 1.12.212, both checked, so
anything between the two is untested. Plus a system raylib 6.0 or newer:

```sh
brew install raylib       # macOS
```

`bb doctor` prints the path `babashka.ffi` actually resolved, which is the
quickest way to tell a missing library from a wrong one.

## What this port does

Everything crosses to C as a scalar, which is a choice rather than a limit.
`babashka.ffi` passes structs by value perfectly well, so the packed-uint
`Color` is here because a four-byte all-integer struct rides in one register on
both ABIs anyway, and packing saves building a map on every draw call.

The surface goes out through rlgl immediate mode, **one batch per ring**. That
part is not optional: rlgl cannot flush inside an open `rlBegin`/`rlEnd`, and
the whole figure overflows the vertex buffer if you try to emit it in one go.
A port that collapses the batches renders part of the torus and silently drops
the rest.

Primitive arrays hold the projected surface, written with `aset-double` and
`aset-int` rather than plain `aset`. The original's comment explains why and it
is worth repeating: on babashka a plain `aset` against a primitive array goes
through `java.lang.reflect.Array` at roughly 6.7 microseconds a write, against
about 37 nanoseconds typed. In a loop this size that difference is the whole
frame.

## What it costs

The HUD reports compute and draw milliseconds separately, because only the
first is the language's problem. On an M1 Pro at the default resolution of 260
rings, babashka holds about 115 fps with 5.1 ms of compute and 2.4 ms of draw
per frame, moving roughly 360k vertices a second.

That is the number worth having. The interpreter gets further on this than you
would guess from the fact that it is an interpreter.

## Credit

The program is **Michiel Borkent**'s ([@borkdude](https://github.com/borkdude)).
This port began from
[`examples/helitorus.clj`](https://github.com/babashka/ffi/blob/main/examples/helitorus.clj)
in [babashka/ffi](https://github.com/babashka/ffi), and the geometry, the
painter ordering, the backface test, the lighting and the palette are all his
and unchanged. What this port adds is a namespace with a `-main`, the
unattended run arguments and a batch flush before the screenshot.

babashka/ffi is MIT licensed, and the notice is reproduced in
[NOTICE.md](../../NOTICE.md) at the root of this repository.
