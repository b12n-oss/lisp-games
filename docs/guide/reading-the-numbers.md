# Reading the numbers

This page exists because this repo has been wrong four times, and every time
the measurement was at fault rather than the runtime. If you take one thing
from the guide, take this.

## Say which mode and which size

Neither game has a single number.

**helitorus** takes a resolution, the number of rings along the spine. At 260
all four runtimes sit at exactly the same frame rate, because none of them is
the bottleneck there. At 900 they separate and babashka falls to about a third
of the others. An fps from this game without its resolution beside it means
nothing.

**mesh-instancing** takes an instance count *and* a mode. `static` is raylib's
own shape: the matrices are built once and the camera orbits, so per frame the
CPU does almost nothing and what you measured is your GPU. `spin` rebuilds
every matrix every frame. The two differ by a factor of six at ten thousand
cubes.

Read the `draw` column first in any table here. It barely moves, whatever the
runtime and whatever the count, because one draw call is one draw call.
Everything that changes is `build` or `compute`, and that is the part a runtime
comparison is entitled to quote.

## The four times this went wrong

Worth reading as a set, because the shape repeats.

**Reflection, on the JVM.** The Clojure port of helitorus first measured 21 ms
compute and 78 ms draw. Every `aget` was resolving through `clojure.lang.RT`
for want of an array type hint. With `^"[D"` and `^"[I"`: 0.7 ms and 0.3 ms.
Thirty times on one, over two hundred on the other.

**An optimisation level, on jank.** The jank port of helitorus first measured
14 ms compute. That was `lein run`, whose `:base` profile builds at `-O0`. The
`-O3` binary does it in 1.2 ms. Eleven times, and the difference between
ranking jank last of the four and second.

**A batch that had not flushed.** A probe reported a clean call and a black
frame, which looked exactly like broken struct marshalling. raylib defers
batched geometry until `EndDrawing`, so a screenshot taken before that captures
only the cleared background. `rlDrawRenderBatchActive` first, and the picture
is there.

**A struct layout that disagreed.** The Clojure port of mesh-instancing ran at
114 fps, reported plausible build and draw timings, and drew one cube at the
origin instead of ten thousand, because the material silently kept the default
shader. Nothing in the numbers said so. Only the screenshot did.

## The tell

Three of those four had the same signature: **the runtime came out slower than
the interpreter.** babashka is an interpreter. If a compiled runtime measures
worse than it, the measurement is wrong, not the runtime. That one heuristic
would have caught the reflection bug and the optimisation-level bug
immediately.

The fourth had no signature at all in the numbers, which is the other lesson:
**look at the render.** Every port here writes a PNG on a chosen frame for
exactly that reason.

```sh
bb play helitorus/jolt 5 out.png 300
```

## What the two games actually measure

They are not the same test twice, which is the argument for having both.

helitorus spends its frame on arithmetic over primitive buffers, then submits
a vertex at a time through rlgl. It separates the runtimes by how fast they do
maths, and they land within about 7x of each other.

mesh-instancing spends its frame writing sixteen floats per instance into one
block of foreign memory, then hands it over in a single call. It separates the
runtimes by how fast their FFI writes memory, and there the spread is about
27x.

Same ordering, very different distances. The FFIs are further apart at moving
bytes than the languages are at arithmetic.

## One more, for jank specifically

The jank port of mesh-instancing ships two matrix paths: `spin` computes the
rotation in jank, `spin-raymath` calls raylib's C helpers. They are about 3x
apart, and both were checked in C to produce an identical matrix, sixteen slots
agreeing to within 6e-8.

The cross-runtime tables quote the in-language path, because the other three
runtimes compute the rotation themselves and a comparison against C would not
be a comparison. For ordinary jank code, raymath is the better choice and it
costs no new dependency.

```sh
cd mesh-instancing/mesh-instancing-jank && bb ab
```
