# mesh-instancing-babashka

Ten thousand lit cubes in a single draw call, from
[babashka](https://babashka.org) over the built-in `babashka.ffi`.

![mesh instancing in babashka](../../docs/demos/mesh-instancing-babashka.png)

```sh
bb info                          # every task here
bb mesh                          # run it
bb mesh 10                       # quit after ten seconds
bb mesh 5 out.png 40             # quit after five, capture frame 40
bb mesh 5 out.png 40 2000        # the same, 2000 instances
bb mesh 5 out.png 40 2000 spin   # rebuild every matrix every frame
bb bench [n]                     # spin mode, the number worth comparing
bb check                         # load the namespace, no window
```

## Two modes, and only one of them is a benchmark

raylib's own example builds its matrices once and turns the field by orbiting
the camera. That is the right shape for showing off instancing and the wrong
shape for comparing runtimes: per frame the CPU does nothing at all, so the
number you get is about your GPU.

So there are two modes:

| | build | draw | fps |
|---|---|---|---|
| `static`, 10000 | 0.0 ms | 0.4 ms | 115 |
| `spin`, 10000 | 50.5 ms | 0.4 ms | 20 |
| `spin`, 2000 | 9.9 ms | 0.3 ms | 87 |

Read the `draw` column first. It barely moves, because one draw call is one
draw call whether it carries 2000 cubes or 10000, and the GPU does the rest.
Everything that changes is `build`, which is the per-instance arithmetic plus
the marshalling of the result into foreign memory. That is the runtime's
contribution and the only part worth comparing.

## What this port has to do differently

This example leans on `babashka.ffi` harder than anything else in the repo,
and all of it works.

A raylib `Mesh` is **120 bytes returned by value** from `GenMeshCube`, and a
`Material` is 40 from `LoadMaterialDefault`. `DrawMeshInstanced` then takes
both of those by value, plus a pointer to the matrix array. Every layout is
declared in the source as an ordinary `[:struct ...]`, and the sizes were
checked against a C program compiled with the same header rather than counted
by eye: `Mesh` 120, `Material` 40, `Matrix` 64, `Camera3D` 44, `MaterialMap`
28, all matching.

`Material` nests a `Shader` and carries a `float params[4]`, which is
`[:array :float 4]`. Swapping in the instancing shader is a plain `assoc` on
the map that came back, then passing it by value.

One thing to know about raylib's `Matrix`: it is declared `m0, m4, m8, m12`
then `m1, m5, m9, m13` and so on, so in **declaration order** the translation
column sits at slots 3, 7 and 11. Get that wrong and every cube stacks at the
origin, which is exactly what the first spike of this port did.

## Credit

The example is raylib's own,
[`examples/shaders/shaders_mesh_instancing.c`](https://github.com/raysan5/raylib/blob/master/examples/shaders/shaders_mesh_instancing.c),
by Ramon Santamaria ([@raysan5](https://github.com/raysan5)) and contributors,
zlib licensed. The lighting and fog shaders follow the jolt port in
[jlt-commons/raylib-jlt](https://github.com/jlt-commons/raylib-jlt).

Notices in full are in [NOTICE.md](../../NOTICE.md).
