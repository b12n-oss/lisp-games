# mesh-instancing-jolt

Ten thousand lit cubes in a single draw call, from
[jolt](https://github.com/jolt-lang/jolt) over
[net.b12n/raylib](https://github.com/jlt-commons/raylib-jlt) on `jolt.ffi`.

```sh
bb info                          # every task here
bb mesh                          # run it
bb mesh 5 out.png 40 2000        # five seconds, shot frame 40, 2000 instances
bb mesh 5 out.png 40 2000 spin   # rebuild every matrix every frame
bb bench [n]                     # spin mode, the number worth comparing
bb check                         # compile the namespace, no window
```

Or `jolt -M:mesh` directly, which is what the tasks wrap.

## Two modes, and only one of them is a benchmark

raylib's own example builds its matrices once and orbits the camera, so per
frame the CPU does nothing and the fps describes your GPU. `spin` rebuilds
every matrix every frame, which is the runtime's own contribution.

| Mode | Instances | build | draw | fps |
|---|---|---|---|---|
| static | 10000 | 0.0 ms | 0.5 ms | 113 |
| spin | 10000 | 26.9 ms | 0.3 ms | 35 |
| spin | 2000 | 5.4 ms | 0.2 ms | 115 |

`draw` barely moves, because one draw call is one draw call. Everything that
changes is `build`.

## What this port has to do differently

Less than you might expect, because the raylib-jlt wrapper already carries the
struct-by-value work. `Mesh` and `Material` cross into `DrawMeshInstanced` by
value and the wrapper hides it.

`matrix-array-set!` is the piece worth reading. It writes one 64-byte Matrix at
an index, field by field rather than through a struct layout, because the
buffer is an array of matrices rather than a single struct. raylib stores a
Matrix as `m0 m4 m8 m12` then `m1 m5 m9 m13`, so the translation column lands
at word offsets 3, 7 and 11. That is the same trap every port here has to get
right, spelled differently.

## Credit

The example is raylib's own,
[`examples/shaders/shaders_mesh_instancing.c`](https://github.com/raysan5/raylib/blob/master/examples/shaders/shaders_mesh_instancing.c),
by Ramon Santamaria ([@raysan5](https://github.com/raysan5)) and contributors,
zlib licensed. This port came by way of the `mesh-instancing` example in
[jlt-commons/raylib-jlt](https://github.com/jlt-commons/raylib-jlt), itself a
port of the same C original. The run arguments and the `spin` mode are new
here, which is the alteration zlib asks be marked.

Notices in full are in [NOTICE.md](../../NOTICE.md).
