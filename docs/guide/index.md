# Guide

Small graphical programs, each written once per Clojure-family runtime and kept
side by side. The games are a pretext. What the repo is actually for is putting
numbers on what changes when the same Clojure crosses into C four different
ways, and on what does not change at all.

Two games so far, and they disagree with each other in a useful way.

New here? [getting-started.md](getting-started.md) has one of them on screen in
a couple of minutes.

| Page | What it covers |
|---|---|
| [getting-started.md](getting-started.md) | Install raylib, pick a port, run it, run it unattended. |
| [reading-the-numbers.md](reading-the-numbers.md) | **Read this before quoting anything.** Modes, instance counts, and the four ways a measurement here has already been wrong. |
| [helitorus.md](helitorus.md) | A helix around a torus. Arithmetic over primitive buffers. |
| [mesh-instancing.md](mesh-instancing.md) | Ten thousand cubes in one draw call. Marshalling into foreign memory. |
| [runtimes.md](runtimes.md) | One section per runtime: how it reaches C, and what it will not carry. |
| [credit.md](credit.md) | Whose work each game is, and the rule this repo starts with. |

## The short version

| Game | measures | spread across four runtimes |
|---|---|---|
| helitorus | arithmetic over primitive buffers | about 7x |
| mesh-instancing | writes into foreign memory | about 27x |

Same four runtimes, same ordering, very different distances. Either game alone
would have told you something misleading about the other.

## A note on what is measured here

Every number in these pages was produced by running the thing, on macOS and an
Apple M1 Pro, in September 2026. Where a figure appears, a port printed it.
Where a range appears, repeated runs gave a range and it seemed dishonest to
quote the best one.

The versions in play were babashka 1.13.220, Clojure 1.12 on a JDK 24, jank
0.1-alpha with `raylib-sys 2026.09-3`, jolt 0.8.10, and raylib 6.0 from
Homebrew. Every port was measured optimised: the jank figures come from its
`-O3` binary rather than from `lein run`, which builds at `-O0`.

That matters more than it sounds. Two of the numbers in this repo were wrong by
factors of eleven and thirty before anyone checked them, and both times the
configuration, not the runtime, was the thing at fault.
