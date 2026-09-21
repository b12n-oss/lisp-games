# mesh-instancing-jank

Ten thousand lit cubes in a single draw call, from
[jank](https://jank-lang.org) over its `cpp/` interop.

```sh
bb release             # build the -O3 binary first
bb run-release         # run it
bb mesh                # run via lein, a DEBUG -O0 build
bb check               # compile the namespace
```

Takes `[seconds] [out.png] [frame] [instances] [spin]`.

## Measure the release binary, not `lein run`

Same as [helitorus-jank](../../helitorus/helitorus-jank): the `:base` profile
compiles at `-O0`, which is not the number to quote.

## Two modes, and only one of them is a benchmark

| Mode | Instances | build | draw | fps |
|---|---|---|---|---|
| static | 10000 | 0.0 ms | 0.4 ms | 115 |
| spin | 10000 | 6.5 ms | 0.3 ms | 115 |
| spin | 2000 | 1.1 ms | 0.2 ms | 114 |

jank holds the frame cap even while rebuilding all ten thousand matrices,
which only it and the JVM manage here.

## What this port has to do differently

Less than any other port, and that is the interesting part. **Mesh and Material
cross into `DrawMeshInstanced` by value with no ceremony at all**, because jank
has the real header and the C++ compiler knows the types. The other three each
had to describe those structs: babashka as `[:struct ...]` layouts, Clojure as
coffi aliases it had to declare itself, jolt through its wrapper. This is the
one FFI here that never had to think about it.

What it does have to do:

The matrix array is `MemAlloc` cast to `(:* float)` and written sixteen floats
at a time. raylib-jnk's port notes that `(cpp/new (:array cpp/Matrix n))` does
not compile, and a float pointer suits this port anyway.

A jank fn may not RETURN a native value, so the transform pointer travels as a
`cpp/box` and is unboxed at each use. Same constraint as helitorus.

**The matrices are computed in jank rather than by raymath**, and that is a
deliberate deviation from raylib-jnk's port, which calls `MatrixRotate` and
`MatrixMultiply`. Those are the idiomatic choice and the faster one, but they
would move the arithmetic into C and make the `build` column a measurement of
C rather than of jank. The other three ports compute the same rotation
themselves, so this one does too. If you want the fast version, raymath is
right there.

## Credit

The example is raylib's own,
[`examples/shaders/shaders_mesh_instancing.c`](https://github.com/raysan5/raylib/blob/master/examples/shaders/shaders_mesh_instancing.c),
by Ramon Santamaria ([@raysan5](https://github.com/raysan5)) and contributors,
zlib licensed. The `cpp/` interop idioms follow the port in
[b12n-oss/raylib-jnk](https://github.com/b12n-oss/raylib-jnk), and the lighting
and fog shaders the jolt port in
[jlt-commons/raylib-jlt](https://github.com/jlt-commons/raylib-jlt). The run
arguments, the `spin` mode and the jank-side matrix arithmetic are new here.

Notices in full are in [NOTICE.md](../../NOTICE.md).
