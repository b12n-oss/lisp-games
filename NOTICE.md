# Notice

This repository is under the Eclipse Public License 2.0, in [LICENSE](LICENSE).
Every game in it is a port of somebody else's work, so this file records where
each one came from and reproduces the licence it arrived under.

The rule for this repo: **a port lands with its credit, in the same commit.**
Not afterwards, and not only in the root README. Each game directory carries the
attribution, each runtime's README repeats it, and each source file opens with
it, because a directory copied out on its own takes no README with it.

## helitorus

From `examples/helitorus.clj` in
[babashka/ffi](https://github.com/babashka/ffi/blob/main/examples/helitorus.clj),
written by **Michiel Borkent** ([@borkdude](https://github.com/borkdude)) and
committed as
[`1034e56`](https://github.com/babashka/ffi/commit/1034e56) on 2026-08-28.

That example is where all of it came from: the helix wound around a torus, the
swept tube, the per-ring painter ordering, the 2D cross-product backface test,
the lighting and the hsl palette. The ports here keep the maths unchanged and
change only how each runtime reaches raylib.

The jolt port arrived by way of the `helitorus` example in
[jlt-commons/raylib-jlt](https://github.com/jlt-commons/raylib-jlt), which is
itself a port of the same original. Its twenty-one shared constants and the
`hsl(337..349, 100..78%, 17..72%)` palette are how you can check that for
yourself.

His own header credits it one step further back, to a Scittle demo that drew the
same figure to a 2D canvas (`scittle/resources/public/helitorus.html`). We have
not been able to check that file directly, so this is his attribution repeated
rather than one we verified.

babashka/ffi is MIT licensed:

```
MIT License

Copyright © 2026 Michiel Borkent

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## mesh-instancing

From `examples/shaders/shaders_mesh_instancing.c` in
[raylib](https://github.com/raysan5/raylib/blob/master/examples/shaders/shaders_mesh_instancing.c),
by **Ramon Santamaria** ([@raysan5](https://github.com/raysan5)) and
contributors. Unlike helitorus, this one is raylib's own example rather than
anyone's port of anything: it is not in babashka/ffi, and the lineage stops
here.

The instancing idea, the shader structure and the ten thousand cubes come from
that original. The lighting and fog shaders here follow the jolt port in
[jlt-commons/raylib-jlt](https://github.com/jlt-commons/raylib-jlt), which is
itself a port of the same C example.

What the ports here added is a second mode. raylib builds its matrices once and
orbits the camera, which measures a GPU; `spin` rebuilds every matrix every
frame, which measures the runtime. Say which mode a number came from.

raylib's examples are zlib/libpng licensed, and zlib asks two things that
survive being ported: that the origin is not misrepresented, and that altered
versions are plainly marked. The header of each source file is that marking and
has to stay accurate.

```
Copyright (c) 2013-2026 Ramon Santamaria (@raysan5)

This software is provided "as-is", without any express or implied warranty. In no event
will the authors be held liable for any damages arising from the use of this software.

Permission is granted to anyone to use this software for any purpose, including commercial
applications, and to alter it and redistribute it freely, subject to the following restrictions:

  1. The origin of this software must not be misrepresented; you must not claim that you
  wrote the original software. If you use this software in a product, an acknowledgment
  in the product documentation would be appreciated but is not required.

  2. Altered source versions must be plainly marked as such, and must not be misrepresented
  as being the original software.

  3. This notice may not be removed or altered from any source distribution.
```

## raylib

Every port links against [raylib](https://www.raylib.com), which is zlib/libpng
licensed. raylib is not bundled here, and each example loads whatever copy the
system provides.

## A sibling repo, and why this file exists

[b12n-oss/raylib-pacman](https://github.com/b12n-oss/raylib-pacman) does the
same thing for Pac-Man, and it also began from one of Michiel Borkent's
babashka/ffi examples. That repo linked the example but never named him, and it
took him asking before the credit went in. This file is here so that does not
happen twice.
