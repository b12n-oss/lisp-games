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
| [architecture.md](architecture.md) | Eight standalone projects, one registry, and why nothing is shared. |
| [contributing.md](contributing.md) | The gate, the credit rule, and what a measurement has to state. |
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
Apple M1 Pro, in September 2026, with the jolt rows re-measured in November.
Where a figure appears, a port printed it. Where a range appears, repeated
runs gave a range and it seemed dishonest to quote the best one.

The versions in play were babashka 1.13.220, Clojure 1.12 on a JDK 24, jank
0.1-alpha with `raylib-sys 2026.09-3`, jolt 0.8.26, and raylib 6.0 from
Homebrew. Every port was measured optimised: the jank figures come from its
`-O3` binary rather than from `lein run`, which builds at `-O0`.

The jolt rows were first measured on 0.8.10, then again on 0.8.11 in September
and on 0.8.26 in November. Each time the old and new release binaries took
turns on the same machine: three 8-second runs each, per configuration, with
the order flipped every round. A cell changes only when the new release came
out different in every paired run. 0.8.11 moved one cell, `spin` build at
10000 instances, from 27.4 to 26.9 ms. 0.8.26 moved two: helitorus draw at 900
rings went from 3.3 to 3.8 ms, and that same `spin` cell went back to 27.4.
Everything else, compute included, stayed within 0.1 ms. The fps cells were
left alone because they drift with the machine. On the November afternoon
0.8.11 printed 106 fps at 900 rings, against the ~95 it printed in September.

A sweep of helitorus at 900 across every release in between shows more than
the two endpoints do. Compute fell from 5.1 to 4.6 ms in 0.8.13 and held there
through 0.8.19. Then two changes gave that back and slowed draw as well. Timed
on their own, best of seven runs of a million calls each:

| jolt | `aset-double` | foreign call, `:float` args | foreign call, `:long` arg |
|---|---|---|---|
| 0.8.19 | 30.9 ns | 18.7 ns | 15.1 ns |
| 0.8.20 | 35.7 ns | 18.6 ns | 15.1 ns |
| 0.8.21 | 36.0 ns | 22.2 ns | 15.3 ns |

0.8.20 made a primitive array coerce what it stores, and helitorus computes
entirely through `aset-double` and `aset-int`. 0.8.21 let a boxed `Float`
reach a `:float` or `:double` foreign position, and helitorus draws through
`rlVertex2f`, which takes two floats and runs six times per quad. Both came
from the work that made a float a `java.lang.Float`. The commits were matched
to the slowdowns by title and by the release they shipped in, not bisected one
at a time. What bought the 0.8.13 gain is still unknown, and the `spin` change
is under 2% and was not chased further.

That matters more than it sounds. Two of the numbers in this repo were wrong by
factors of eleven and thirty before anyone checked them, and both times the
configuration, not the runtime, was the thing at fault.
