# Getting started

## Install raylib

Every port links against a system raylib 6.0 or newer, and none of them bundle
it.

```sh
brew install raylib                   # macOS
sudo pacman -S raylib                 # Arch, and your distro's equivalent
```

## Pick a port and run it

babashka is the fastest to get on screen, because it has no build step at all.

```sh
cd helitorus/helitorus-babashka && bb helitorus
```

Drag to turn, wheel to zoom, LEFT and RIGHT change the winding count, UP and
DOWN the resolution.

For the other game:

```sh
cd mesh-instancing/mesh-instancing-babashka && bb mesh
```

From the repository root, `bb ports` lists every port and whether it is built,
and `bb play <id>` runs one by name.

```sh
bb ports
bb play helitorus/jolt
```

## Run it with nobody at the keyboard

Every port takes the same shape of arguments, which is what makes the whole
repo scriptable.

```sh
bb helitorus 10                  # quit after ten seconds of game time
bb helitorus 3 out.png 40        # quit after three, capture frame 40
bb helitorus 3 out.png 40 900    # the same, at resolution 900
```

mesh-instancing takes an instance count and a mode in the last two slots:

```sh
bb mesh 5 out.png 40 2000        # 2000 cubes, matrices built once
bb mesh 5 out.png 40 2000 spin   # 2000 cubes, rebuilt every frame
```

The deadline counts **game time**, summed from the clamped per-frame dt, rather
than wall time. That matters for any runtime that pays a one-off cost before
the first frame, which would otherwise spend its whole budget there and quit
looking like a program that does not work.

## The sweeps

From the root, across every port at once:

```sh
bb check-all      # load or compile all of them, the fast gate
bb shot-all       # run each for a few seconds and capture a PNG
bb doctor-all     # which toolchain is missing where
```

## Per-runtime prerequisites

babashka needs `bb` **1.13.220**, because these use the `babashka.ffi` built
into bb. That namespace is present in 1.13.220 and absent in 1.12.212, both
checked, so anything between the two is untested.

Clojure needs a JDK 22 or newer, because coffi sits on Panama. Three JVM flags
are load-bearing and `deps.edn` sets all three.

jank needs `jank` and a Leiningen. The first run compiles raylib-sys natively
and takes a few minutes. Every `lein` invocation needs `--disable-sandbox` on
macOS, which has no `bwrap`; without it the build aborts with an error that
never mentions jank.

jolt needs `jolt` 0.8.0 or newer. The raylib bindings come from
`jlt-commons/raylib-jlt`, pinned by sha.

None of the four needs any of the others.

## If you are going to quote a number

Read [reading-the-numbers.md](reading-the-numbers.md) first. For jank in
particular, measure the `-O3` binary rather than `lein run`:

```sh
cd helitorus/helitorus-jank && bb release && bb run-release
```
