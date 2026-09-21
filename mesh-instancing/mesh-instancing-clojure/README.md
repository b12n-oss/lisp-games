# mesh-instancing-clojure

Ten thousand lit cubes in a single draw call, from Clojure on the JVM over
[raylib-clj](https://github.com/b12n-oss/raylib-clj), which binds raylib with
coffi on Panama.

```sh
bb mesh                          # run it
bb mesh 5 out.png 40 2000        # five seconds, shot frame 40, 2000 instances
bb mesh 5 out.png 40 2000 spin   # rebuild every matrix every frame
bb check                         # load the namespace, no window
```

Or `clojure -M:mesh` directly. A JDK 22 or newer, because coffi sits on
Panama, and the three JVM flags in `deps.edn` are all load-bearing.

## Two modes, and only one of them is a benchmark

| Mode | Instances | build | draw | fps |
|---|---|---|---|---|
| static | 10000 | 0.0 ms | 0.4 ms | 114 |
| spin | 10000 | 1.9 ms | 0.4 ms | 110 |
| spin | 2000 | 0.7 ms | 0.2 ms | 106 |

This port is a long way ahead of the others on `build`, further than its lead
in [helitorus](../../helitorus). The work here is mostly writing floats into
foreign memory, and `mem/write-float` compiles to a direct store, so the JIT
has very little in its way.

## What this port has to do differently

raylib-clj binds most of raylib but not this corner, so the port declares
**seven** calls itself with coffi's `defcfn`: `LoadMaterialDefault`,
`DrawMeshInstanced`, `LoadShaderFromMemory`, `GetShaderLocation`,
`SetShaderValue`, `UnloadShader` and `TakeScreenshot`. That is the same escape
hatch the Pac-Man port in raylib-pacman uses for its two.

**It also declares its own `::shader` and `::material` aliases**, and that
choice is the whole story of getting this port working.

raylib-clj's `::shader` stores `int *locs` as two ints, `:locs-lo` and
`:locs-hi`, and its `::material` FLATTENS the nested Shader into `:shader-id`
and `:shader-locs`. Both are valid layouts. Neither lets you hand a Shader
straight into a Material, which is the one thing this example needs.

The first attempt used raylib-clj's `::material` and did
`(assoc mat :shader sh)`. That adds a key the serializer does not know, so the
material silently kept the **default** shader, which has no
`instanceTransform` attribute, so all ten thousand cubes rendered on top of
each other at the origin. It ran at 114 fps and reported plausible timings
throughout.

Declaring nested aliases fixes that, but then raylib-clj's own shader
functions can no longer be used on them: passing a nested shader to
`rcs/get-shader-location` looks for `:locs-lo`, finds nil, and throws an
unhelpful `Cannot invoke java.lang.Character.charValue()`. So the uniform
helpers are local too. **Pick one representation of a struct and use it all
the way through.**

The matrix buffer is written with raw `mem/write-float` at word offsets rather
than through the `::matrix` alias, because it is an array of matrices rather
than one struct. raylib's `m0 m4 m8 m12` ordering puts the translation at
slots 3, 7 and 11.

One Clojure detail: `matrix-set!` takes nine arguments and so cannot carry
`^double` hints, since Clojure supports primitive args only up to arity 4.
Hinting them fails with "fns taking primitives support only 4 or fewer args".

## Credit

The example is raylib's own,
[`examples/shaders/shaders_mesh_instancing.c`](https://github.com/raysan5/raylib/blob/master/examples/shaders/shaders_mesh_instancing.c),
by Ramon Santamaria ([@raysan5](https://github.com/raysan5)) and contributors,
zlib licensed. The lighting and fog shaders follow the jolt port in
[jlt-commons/raylib-jlt](https://github.com/jlt-commons/raylib-jlt). The run
arguments and the `spin` mode are new here.

Notices in full are in [NOTICE.md](../../NOTICE.md).
