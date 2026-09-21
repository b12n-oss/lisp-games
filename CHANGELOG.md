# Changelog

All notable changes to this project are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- The repository, laid out as one directory per game and one per runtime
  inside it (`<game>/<game>-<runtime>/`), so a game can gain runtimes and the
  repo can gain games without either disturbing the other.
- `helitorus`, a helix of n windings around a torus swept into a tube, ported
  from Michiel Borkent's (@borkdude) `examples/helitorus.clj` in babashka/ffi.
  The babashka port runs: about 115 fps on an M1 Pro at the default resolution,
  with 5.1 ms of compute and 2.4 ms of draw per frame. The geometry, painter
  ordering, backface test, lighting and palette are unchanged from the
  original. What the port adds is a namespace with a `-main`, the unattended
  run arguments and a batch flush before the screenshot.
- A jolt port of helitorus, by way of the `helitorus` example in
  jlt-commons/raylib-jlt, which is itself a port of the same original. Same
  geometry, typed primitive arrays (`^double/1`, `^int/1`) and the wrapper's
  rlgl surface. On an M1 Pro it runs the arithmetic about three and a half
  times faster than babashka: 1.5 ms compute and 1.0 ms draw against 5.1 and
  2.3 at the default resolution, where both still hold 115 fps because neither
  is the bottleneck. At 900 rings babashka falls to roughly 35 fps and jolt
  holds about 95.
- A Clojure/JVM port of helitorus over raylib-clj, which binds raylib with
  coffi on Panama. Structs arrive as maps rather than packed integers, and the
  six rlgl calls raylib-clj does not bind are declared in the example itself
  with coffi's `defcfn`. It is the fastest of the three: 0.7 ms compute and
  0.3 ms draw at the default resolution, and the only one still holding the
  full frame cap at 900 rings.
- A jank port of helitorus over its `cpp/` interop. jank has no primitive
  arrays (`double-array`, `int-array` and `aget` all throw `TODO: port ...`,
  checked against upstream on 2026-09-20), so the ten buffers are malloc'd
  natives carried as `cpp/box` values and unboxed at each use site. A native
  pointer cannot be a jank fn parameter, which is the same constraint the
  Pac-Man port meets from the other side.
- **Measure jank's release binary, not `lein run`.** The `:base` profile
  compiles at `-O0`: 14.0 ms compute per frame against 1.2 at `-O3`, eleven
  times. The `-O0` figure would rank jank last of the four when it is second.
  `bb shot` and `bb run-release` both use the binary.
- **The array type hints in the Clojure port are load-bearing.** Without
  `^"[D"` / `^"[I"` every `aget` resolves through `clojure.lang.RT`
  reflectively, which measured 21 ms compute and 78 ms draw, thirty times
  slower and worse than babashka. The hint must be the array class: `^doubles`
  on a `def` resolves to `clojure.core/doubles`, the function, and the
  namespace will not compile.
- `NOTICE.md`, which names the author of every original and reproduces its
  licence. The rule the file states, and that this repo is starting with rather
  than retrofitting: a port lands with its credit in the same commit.
- EPL-2.0 licence, with the ported work's own notices kept in `NOTICE.md`.
- A committed still of the babashka port under `docs/demos/`, so reading the
  repo needs no capture toolchain.
