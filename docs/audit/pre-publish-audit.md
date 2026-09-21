# Pre-publish audit

Run 2026-09-21, against `b12n-oss/lisp-games` at the tip of `main`, before
any decision to make the repository public. Every check below was run, not
just the ones that seemed likely to hit, because "we ran it and found
nothing" is the deliverable rather than a formality.

**Result: no blockers. Nothing found in the working tree or in history.**

## What was checked

| # | Check | Result |
|---|---|---|
| 1 | Other private repo names added anywhere in history | only `b12n-oss`, 17 times, which is the public org |
| 2a | `$HOME` in the working tree | 0 files |
| 2b | `$HOME` anywhere in history (pickaxe) | 0 commits |
| 2c | Any `/Users/<name>` or `/home/<name>` shape | 0 files |
| 3 | Secret patterns across all history | 0 matches |
| 4 | Private planning-store path (`b12n-sp-docs`) | 0 in tree, 0 in history |
| 5 | Local-only branches never pushed | none; `main` only, and it matches `origin` |
| 6 | `CLAUDE.md` / `AGENTS.md` tracked on any ref | 0 commits |

Check 3 covered `AKIA…`, `ghp_…`, `github_pat_…`, `xox[baprs]-…`,
`AIza…`, `-----BEGIN … PRIVATE KEY-----`, and the literals `api_key=`,
`secret=` and `password=`.

## Two notes on method

**A blank result is not a pass.** The first run of checks 1 and 2 printed
nothing and looked clean. It was not: the repo-name grep had errored on its
pattern, and the `|| echo "none"` fallbacks never fired because `head`
masked the exit code upstream of them. Both were re-run printing explicit
counts, which is what the table above reports. A check that cannot
distinguish "found nothing" from "did not run" has not been run.

**History, not just the tip.** Checks 1, 2b, 3, 4 and 6 use `--all` and the
pickaxe, because a plain edit-and-commit leaves content readable via
`git show <old-sha>:<path>` in every ancestor. Nothing here needed it, but a
hit inside history would have required its own `filter-repo` pass rather
than a cleanup commit.

## Why check 6 passes without a rewrite

`CLAUDE.md` and `AGENTS.md` are symlinks into the private planning store at
`~/dev/b12n-sp-docs/b12n-oss/lisp-games/`. They were **never** tracked in
this repository, so there is no history to rewrite.

They are excluded through `.git/info/exclude` rather than `.gitignore`. That
distinction is the point: `.gitignore` is itself tracked, so an entry there
would advertise to every reader that the files exist. `.git/info/exclude` is
local-only, and the published tree carries no sign of the convention.

## Scope

This audit covers what would become readable on making the repository
public. It does not cover the quality of the code or the accuracy of the
measurements; `docs/guide/reading-the-numbers.md` is where the second of
those is addressed.
