# helitorus-jank

A helix of n windings around a torus, swept into a tube, drawn with
[raylib](https://www.raylib.com) from [jank](https://jank-lang.org) over its
`cpp/` interop, which includes `raylib.h` and `rlgl.h` directly.

```sh
bb info                # every task here
bb release             # build the -O3 binary (do this first)
bb run-release         # run it
bb shot                # 5 game-seconds, write helitorus.png
bb helitorus           # run via lein, a DEBUG -O0 build
bb check               # compile the namespace
bb doctor              # check jank, lein and libraylib
```

Drag to turn, wheel to zoom, LEFT and RIGHT change the winding count, UP and
DOWN change the resolution along the spine.

## Measure the release binary, not `lein run`

This matters more here than in any other port, so it goes first. The `:base`
profile compiles at `-O0`, and this program is nothing but arithmetic:

| | compute | draw | fps |
|---|---|---|---|
| `lein run`, `-O0` | 14.0 ms | 5.1 ms | 48 |
| `target/release`, `-O3` | 1.2 ms | 0.8 ms | 115 |

Eleven times on compute. Quoting the `lein run` number would put jank last of
the four runtimes here when it is in fact second, so `bb shot` and
`bb run-release` both use the binary and `bb helitorus` says in its own doc
string that it does not.

There is a separate, smaller `lein run` cost that the Pac-Man port documents
too: it compiles as it loads, so the first frames are slow. That is why the
deadline counts game time summed from clamped dt rather than wall time.

## Prerequisites

`jank`, a Leiningen, and a system raylib 6.0 or newer. raylib arrives through
`org.jank-lang.commons/raylib-sys`, which builds natively on first use and
takes a few minutes.

Every `lein` invocation needs `--disable-sandbox` on macOS, which has no
`bwrap`. Without it the build aborts with `No 'bwrap' executable found`, an
error that never mentions jank.

## What this port has to do differently

Four things, each settled by a spike rather than assumed.

**jank has no primitive arrays.** `double-array`, `int-array` and `aget` all
exist in `clojure.core.jank` and all of them throw `TODO: port ...`, which was
checked against upstream jank at 2026-09-20 rather than taken from a local
checkout. So the ten buffers this program needs are native: `malloc`, cast to
`(:* double)` or `(:* int)`, read with `cpp/aget`, written with
`(cpp/= (cpp/aget buf i) v)`.

**A native pointer cannot be a jank fn's parameter.** Arguments arrive boxed
and `cpp/aget` will not subscript them, which fails at compile time with
`invalid-cpp-operator-call`. Each buffer is therefore carried as a `cpp/box`
and unboxed with its type at the top of every fn that touches it. This is the
same constraint as the one the Pac-Man port hits from the other side, where a
jank fn may not *return* a native value.

Worth knowing alongside it: `cpp/raw` can declare C++ statics, and that looked
like the easier route, but raylib-jnk's `unicode_ranges` example warns that
each function gets its own copy of them. Boxed pointers have no such problem.

**No JVM, so no `Math/*`, no `parse-long`, no `format`.** C math comes from
`math.h`, the clock from `GetTime`, integer parsing from `TextToInteger`, and
the HUD is assembled with `str` and a by-hand rounding helper.

**`rlColor4ub` takes unsigned chars and jank will not narrow an int.** Each
component goes through `(cpp/cast (:unsigned char) ...)`. `rlColor4f` takes
floats and needs no cast, if you would rather keep colours as fractions.

The surface goes out **one rlgl batch per ring**, same as every other port.
rlgl cannot flush inside an open `rlBegin`/`rlEnd`, and the whole figure
overflows the vertex buffer in one go.

## What it costs

Measured on an M1 Pro, against the other three ports running the same
geometry, all of them optimised:

| Resolution | | compute | draw | fps |
|---|---|---|---|---|
| 260 (default) | Clojure | 0.7 ms | 0.3 ms | 115 |
| | jank | 1.2 ms | 0.8 ms | 115 |
| | jolt | 1.5 ms | 1.0 ms | 115 |
| | babashka | 5.1 ms | 2.3 ms | 115 |
| 900 (max) | Clojure | 2.4 ms | 1.0 ms | 116 |
| | jank | 4.3 ms | 2.8 ms | ~108 |
| | jolt | 5.0 ms | 3.3 ms | ~95 |
| | babashka | 18.0 ms | 8.3 ms | ~35 |

jank lands second, ahead of jolt and behind the JVM. Every C math call here is
boxed back into a jank value at the call site, because a jank fn cannot return
a native one, and that boxing is the honest cost of the port rather than
something to tune away.

## Credit

The program is **Michiel Borkent**'s ([@borkdude](https://github.com/borkdude)).
This port descends from
[`examples/helitorus.clj`](https://github.com/babashka/ffi/blob/main/examples/helitorus.clj)
in [babashka/ffi](https://github.com/babashka/ffi), by way of the babashka port
in this repo. The geometry, painter ordering, backface test, lighting and
palette are his and unchanged; what changed is the layer underneath.

babashka/ffi is MIT licensed, and the notice is reproduced in
[NOTICE.md](../../NOTICE.md) at the root of this repository.
