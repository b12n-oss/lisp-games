# Contributing

## The gate

```sh
bb check-all
```

Every port loads or compiles, no window opens. That is the fast gate and it
should be green before anything is committed.

A rendered frame is the second gate, and it is not optional here. Three of the
bugs this repo has shipped ran at full speed, reported plausible timings, and
drew the wrong picture. Numbers alone do not tell you a port works.

```sh
bb shot-all          # every port writes a PNG
```

Open them.

## Adding a runtime to an existing game

1. Create `<game>/<game>-<runtime>/` with its own build file and a `bb.edn`
   carrying the same `info` / `check` / `shot` / `doctor` tasks the others
   have.
2. Add an entry to the `ports` vector at the top of the root `bb.edn`. That is
   the only list; everything else walks it.
3. Take the same run arguments as the other ports, and count the deadline in
   game time.
4. Put a header comment at the top of the source naming the original and its
   author. See below.

## Adding a game

A new game is a new `<game>/` directory with at least one runtime inside it, a
`README.md` naming the original and its author, an entry in the root README's
Games table, and a section in `NOTICE.md` reproducing whatever licence the
original came under.

## Credit is not a follow-up task

**Every game here is somebody else's work, and a port lands with its credit in
the same commit.** Not afterwards, and not only in the root README:

1. A section in `NOTICE.md` with the licence in full.
2. The author named in the game's README and the runtime's README.
3. A header comment at the top of every source file, because a directory
   copied out on its own takes no README with it.
4. A row in the root README's Games table.

[credit.md](credit.md) says why this is written down rather than assumed.

## If you are adding a measurement

Read [reading-the-numbers.md](reading-the-numbers.md) first, then:

- **State the mode and the size.** A `static` fps and a `spin` fps are not the
  same measurement, and neither is a figure without its resolution or instance
  count.
- **Measure the optimised build.** For jank that means the `-O3` binary from
  `bb release`, not `lein run`.
- **Quote a range if repeated runs gave one.** Picking the best run is not
  reporting.
- **Be suspicious if a compiled runtime beats nothing.** If it comes out
  slower than babashka, the measurement is wrong.

## Style

Match the file you are editing. The ports are deliberately verbose in their
comments where something is non-obvious or was measured, and deliberately
plain everywhere else. A comment that says what a measurement was, or why a
construct is the way it is, earns its place; one that restates the code does
not.

## Regenerating the previews

`bb record` re-records `docs/demos/*.gif` from `scripts/demo_manifest.edn`.
It needs `screen-grab` and `cgevent`, internal tools that are not public yet,
so most contributors cannot run it and do not need to: every GIF is committed.

## The docs site

```sh
bb site:build     # into _site/
bb site:serve     # build, then serve
```

It needs a checkout of
[jlt-commons/docs-engine](https://github.com/jlt-commons/docs-engine) at
`$DOCS_ENGINE`, `../docs-engine`, or `~/dev/jlt-commons/docs-engine`. CI
checks the engine out itself and does not use that search.
