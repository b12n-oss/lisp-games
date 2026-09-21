# mesh-instancing-jank

Ten thousand lit cubes in a single draw call, from
[jank](https://jank-lang.org) over its `cpp/` interop.

```sh
bb release             # build the -O3 binary first
bb run-release         # run it
bb mesh                # run via lein, a DEBUG -O0 build
bb check               # compile the namespace
```

Takes `[seconds] [out.png] [frame] [instances] [mode]`, where mode is
`spin`, `spin-raymath` or omitted for static.

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

## Two matrix paths, measured against each other

This port ships both, because "compute it in jank" and "let C compute it" is
the one decision that actually matters here and it deserved a number rather
than an opinion.

```sh
bb ab            # runs spin then spin-raymath back to back
bb ab 2000       # at a different instance count
```

| Instances | | build | draw | fps |
|---|---|---|---|---|
| 10000 | `spin` (jank) | 6.3 to 6.8 ms | 0.3 ms | 114 |
| 10000 | `spin-raymath` (C) | 1.9 to 2.5 ms | 0.3 ms | 116 |
| 2000 | `spin` (jank) | 1.2 ms | 0.2 ms | 114 |
| 2000 | `spin-raymath` (C) | 0.4 ms | 0.2 ms | 115 |

Ranges rather than single figures because that is what repeated runs gave.
Six readings of each at 10000: `spin` 6.3, 6.4, 6.5, 6.8; `spin-raymath` 1.9,
2.2, 2.4, 2.4, 2.5. The raymath side is the noisier of the two, which is worth
knowing before reading much into a single run.

**Letting raymath do it is roughly 3x faster.** Same binary, same scatter, same
everything but the sixteen floats. `spin-raymath` calls `MatrixRotate`,
`MatrixTranslate` and `MatrixMultiply` and assigns whole 64-byte Matrix values;
`spin` computes the same rotation in jank and writes floats one at a time.

They really do compute the same matrix. Both were evaluated in C against the
same axis, angle and translation: sixteen slots identical, worst difference
6e-8, which is float rounding. So this is an A/B rather than two different
programs.

Worth noticing where raymath lands: around 2 ms is roughly what the Clojure
port gets computing in Clojure. At that point neither is doing arithmetic worth
measuring and both are mostly just moving 640 KB into memory, which is the
floor this game has.

**Which to use.** For ordinary jank code, raymath, and it is already on the
include path via raylib-sys so it costs no new dependency. `glm-sys` in
[jank-lang/commons](https://github.com/jank-lang/commons) is the better option
for a maths-heavy project, being a real library rather than raylib's
convenience header. The hand-rolled path exists here so the comparison against
Clojure, jolt and babashka measures jank rather than C, since those three all
compute the rotation in-language.

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
