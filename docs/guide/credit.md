# Credit

**Every game in this repository is somebody else's work.** This page says
whose, and the rule the repo runs on.

## The rule

A port lands with its credit **in the same commit**. Not afterwards, and not
only in the root README. For each new port that means:

1. A section in `NOTICE.md` naming the author and reproducing the original's
   licence in full.
2. The author named in the game's `README.md` and in the runtime's `README.md`.
3. A header comment at the top of every source file, because a directory
   copied out on its own takes no README with it.
4. A row in the root README's Games table.

## Why it is written down

The sibling repo [raylib-pacman](https://github.com/b12n-oss/raylib-pacman)
got this wrong. It linked the `babashka/ffi` example it came from but never
named Michiel Borkent, and it took him asking in a Slack thread before the
credit went in. This repo starts with the rule rather than retrofitting it.

## helitorus

From `examples/helitorus.clj` in
[babashka/ffi](https://github.com/babashka/ffi/blob/main/examples/helitorus.clj),
by **Michiel Borkent** ([@borkdude](https://github.com/borkdude)), MIT.

The helix wound around a torus, the swept tube, the per-ring painter ordering,
the 2D cross-product backface test, the lighting and the hsl palette all came
from there. The ports keep the maths unchanged and vary only in how each
runtime reaches raylib.

His own header credits it one step further back, to a Scittle demo drawing the
same figure to a 2D canvas. We have not been able to check that file directly,
so that is his attribution repeated rather than one we verified.

## mesh-instancing

From `examples/shaders/shaders_mesh_instancing.c` in
[raylib](https://github.com/raysan5/raylib/blob/master/examples/shaders/shaders_mesh_instancing.c),
by **Ramon Santamaria** ([@raysan5](https://github.com/raysan5)) and
contributors, zlib.

Unlike helitorus this one is raylib's own example rather than anyone's port of
anything, so the lineage stops there. The lighting and fog shaders follow the
jolt port in
[jlt-commons/raylib-jlt](https://github.com/jlt-commons/raylib-jlt), itself a
port of the same C original, and the jank `cpp/` idioms follow
[b12n-oss/raylib-jnk](https://github.com/b12n-oss/raylib-jnk).

zlib asks two things that survive being ported: that the origin is not
misrepresented, and that altered versions are plainly marked. The header of
each source file is that marking. What was altered here is the run arguments,
the `spin` mode, and in the jank port the choice to compute the matrices in
jank rather than through raymath.

## raylib

Every port links against [raylib](https://www.raylib.com), zlib/libpng
licensed. raylib is not bundled, and each example loads whatever copy the
system provides.

## This repository

EPL-2.0 for its own work. Each ported original keeps the licence it arrived
under, and `NOTICE.md` carries every one of those notices in full.
