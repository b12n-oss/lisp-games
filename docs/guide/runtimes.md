# The four runtimes

One section each: how it reaches C, what it will not carry, and what it costs.
Both games are in the tables on their own pages;
[reading-the-numbers.md](reading-the-numbers.md) says how to quote them.

## The short comparison

| | reaches C via | structs by value | primitive arrays |
|---|---|---|---|
| babashka | `babashka.ffi`, built into bb | yes, as maps | `aset-double` over plain `aset` |
| Clojure/JVM | raylib-clj, coffi on Panama | yes, as maps | `^"[D"` / `^"[I"` on the defs |
| jank | `cpp/` interop, the real header | yes, transparently | **none at all** |
| jolt | `jolt.ffi` via net.b12n/raylib | yes, `:by-value` plus a pointer | `^double/1` / `^int/1` |

Every one of the four carries structs by value. That is worth stating plainly,
because the sibling repo's documentation claimed otherwise about two of them
until September 2026.

## babashka

`babashka.ffi` ships inside bb, so a port is a source file and nothing else.
No build step, no dependencies, fastest of the four to get on screen.

It is not scalar-only. It passes structs by value in both directions,
including a 120-byte `Mesh` returned from `GenMeshCube`. Where a port here
packs a `Color` into a uint anyway, that is a choice rather than a workaround:
a four-byte all-integer struct rides in a single register on both ABIs, so the
packed int is the same memory with no map built per call.

The cost is that the game logic runs on an interpreter. At helitorus's default
resolution that is invisible, since all four runtimes hit the frame cap. At 900
rings it is a third of the frame rate, and in mesh-instancing's `spin` mode it
is the slowest of the four by a wide margin.

Needs bb **1.13.220**; `babashka.ffi` is absent in 1.12.212.

## Clojure on the JVM

raylib-clj binds raylib with coffi on Panama, so structs arrive as ordinary
Clojure maps and nothing is packed. The comfortable end of the four.

It is also the fastest, and by more in mesh-instancing than in helitorus,
because `mem/write-float` compiles to a direct store and that game is mostly
writes.

Two things to know. The array type hints are load-bearing: without `^"[D"` and
`^"[I"` every `aget` resolves reflectively, which measured thirty times slower.
And where raylib-clj does not bind a call, coffi's `defcfn` takes the C symbol
and the types and nothing else, so extending the binding inside your own file
is a few lines. The mesh-instancing port declares seven that way.

Needs a JDK 22 or newer, and three JVM flags that are all mandatory.

## jank

jank includes the real header, so `Mesh` and `Material` cross by value with no
ceremony at all. Of the four FFIs it is the one that never has to think about
struct layout.

What it does not have is primitive arrays. `double-array`, `int-array` and
`aget` all exist in `clojure.core.jank` and all throw `TODO: port ...`,
verified against upstream at 2026-09-20. Buffers are therefore `malloc`'d
natives, cast to `(:* double)` or `(:* float)`, and carried as `cpp/box`
values, because a native pointer cannot be a jank fn's parameter: arguments
arrive boxed and `cpp/aget` will not subscript them. That is the same
constraint as the one a jank fn meets from the other side, where it may not
*return* a native value.

**Measure the `-O3` binary.** The `:base` profile builds at `-O0` and the
difference is eleven times. `bb release` then `bb run-release`.

There is no JVM, so no `Math/*`, no `parse-long` and no `format`. C maths comes
from `math.h`, the clock from `GetTime`, integer parsing from `TextToInteger`.

## jolt

`jolt.ffi` through `net.b12n/raylib`, which is a real wrapper rather than a raw
binding, so most of the struct work is already done. It passes structs by value
with `[:by-value [:struct ...]]` on the parameter and a pointer to an allocated
layout buffer as the argument.

It sits second in helitorus and third in mesh-instancing, which is the one
place the two games disagree about ordering between neighbours. The wrapper
writes matrices field by field at word offsets, and that is a lot of individual
writes.

Needs `jolt` 0.8.0 or newer; the bindings are pinned by sha.

## One layout note that applies to all four

raylib declares `Matrix` as `m0 m4 m8 m12` then `m1 m5 m9 m13`, so in
**declaration order** the translation column sits at slots 3, 7 and 11. Every
port here has to get that right, and getting it wrong stacks every instance at
the origin, which is indistinguishable from several other bugs.
