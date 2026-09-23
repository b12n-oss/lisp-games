# Changelog

All notable changes to this project are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Changed

- **jolt ports re-measured on jolt 0.8.11.** Both release binaries ran
  interleaved on one machine, three runs per configuration. helitorus at 260
  and 900, and mesh-instancing `static`, did not move. mesh-instancing `spin`
  went 27.4 to 26.9 ms build at 10000 instances and 5.5 to 5.4 at 2000, the
  first consistent across every paired run. The tables now quote the 0.8.11
  medians. Both ports compile unchanged under 0.8.11's stricter private-var
  rules.

### Added

- `mesh-instancing`, raylib's own shaders example: ten thousand lit cubes in a
  single draw call. The babashka port runs. This one leans on struct-by-value
  hard, since a `Mesh` is 120 bytes returned by value, a `Material` is 40, and
  `DrawMeshInstanced` takes both plus a pointer to the matrix array. Every
  layout size was checked against a C program built with the same header.
- jolt, Clojure and jank ports of mesh-instancing, so both games now cover all
  four runtimes. The `spin` build column at 10000 instances: Clojure 1.9 ms,
  jank 6.5, jolt 27.2, babashka 50.5.
- **The jank port ships two matrix paths and measures them against each
  other.** `spin` computes the rotation in jank; `spin-raymath` calls
  raylib's `MatrixRotate`/`MatrixTranslate`/`MatrixMultiply`. Same binary,
  same scatter, roughly 3x apart: 6.3-6.8 ms against 1.9-2.5 at 10000
  instances. Both were checked in C to produce an identical matrix, sixteen
  slots matching to within 6e-8, so it is an A/B rather than two programs.
  `bb ab` runs them back to back. Nothing in jank-lang/commons does this
  arithmetic in jank; `glm-sys` and raymath both do it in C++.
- **The two games rank the runtimes with very different spreads.** helitorus
  puts its work in arithmetic and the four sit within about 7x of each other.
  mesh-instancing puts its work in writes into foreign memory, and there the
  spread is 27x. Same ordering, very different distances, which is the
  argument for having both.
- **mesh-instancing ships two modes, and only `spin` is a benchmark.** raylib
  builds its matrices once and orbits the camera, so per frame the CPU does
  nothing and the number measures a GPU. `spin` rebuilds every matrix every
  frame. babashka at 10000 instances: `static` 115 fps with 0.0 ms build,
  `spin` 20 fps with 50.5 ms build, while `draw` stays at 0.4 ms in both
  because one draw call is one draw call.
- **Listed, 2026-09-21.** The project is in
  [awesome-jolt](https://github.com/jlt-commons/awesome-jolt) under Applications
  and Libraries, beside its sibling raylib-pacman, and carries a card on
  <https://b12n-oss.github.io/>. Both of those are ours, so neither is an
  independent endorsement. What they add is a route to the repo for somebody
  looking for Jolt projects rather than for this one by name.
- **Public, 2026-09-21.** Repository visibility flipped, then GitHub Pages
  enabled as its own separate decision, then `PUBLISH_SITE` set and the
  workflow's `push`/`pull_request` triggers restored by hand. The site serves
  at <https://b12n-oss.github.io/lisp-games/>. Both gates stay in the workflow,
  so unsetting the variable stops deploying without editing anything.
- Open-source preparation: `docs/guide/architecture.md` and
  `contributing.md` complete the guide, and `.github/workflows/site.yml`
  builds the site on `workflow_dispatch` only, because a private repo is
  billed for every Actions run. Its deploy job stays double-gated on the
  `PUBLISH_SITE` variable and on Pages being enabled, neither of which is.
- `docs/audit/pre-publish-audit.md`, recording all six leak checks against
  the working tree and full history. No blockers: no private repo names, no
  home paths, no secret patterns, no planning-store paths, no unpushed
  branches, and the agent-instruction files were never tracked on any ref.
- A documentation site, built from `docs/guide/` by
  [docs-engine](https://github.com/jlt-commons/docs-engine) the way the sibling
  raylib-pacman does it. `bb site:build`, `bb site:serve`, `bb site:clean`.
  Seven pages; `reading-the-numbers.md` is the one that matters, since it
  collects every way a measurement here has already been wrong.
  **Nothing publishes.** No site repo, no Pages, and `docs/site.edn` says so.
- An animated preview per port under `docs/demos/`, eight in all, recorded
  with the internal `screen-grab` over `cgevent` and committed, so neither the
  docs build nor either README gallery needs a capture toolchain. `bb record`
  regenerates them from `scripts/demo_manifest.edn`, which reads the same
  registry every other task walks. The stills they replace are gone.
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
