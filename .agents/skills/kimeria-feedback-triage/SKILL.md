---
name: kimeria-feedback-triage
description: Turns a round of Edgard in Kimeria playtest or reviewer feedback into analysed findings, the author's decisions, GitHub issues and a docs/PLAYTEST_N.md record, then ticks the open issue boxes that waited for real players. Use when the author passes on feedback, playtest notes or a list of remarks (in any language) and wants them analysed, ticketed or recorded.
---

# Kimeria feedback triage

Project-owned, a thin procedure. The model to follow is [docs/PLAYTEST_1.md](../../../docs/PLAYTEST_1.md) (the first
external round, decisions in D66). Every issue is written and filed with [kimeria-issue](../kimeria-issue/SKILL.md).
Work from the repository root; links resolve relative to this file. Output is English only (AGENTS.md), whatever
language the feedback and the conversation are in.

## 1. Record the round

Collect what is known and write "not recorded" for the rest; never guess: date received, build (web or desktop, commit
or date of the build), devices and browsers, who played (external players or the author: the author's own runs are
kept apart, ROADMAP and D33), how the feedback was collected. Pick the next free number: `ls docs/PLAYTEST_*.md`.

## 2. Split and translate

- Translate each remark faithfully into English. Keep the tester's meaning, not your fix: "the branch looks like a
  dog" stays an observation.
- Separate the **observation** (what the tester saw or felt) from the **proposal** (what they suggest). A proposal is
  input for the author, not a decision.
- Split a remark holding several changes into findings; merge remarks that are one finding. Number findings `F1`,
  `F2`, ... in this document's order; elsewhere cite them as `PLAYTEST_<N> F<k>`.

## 3. Analyse each finding against the current build

For every finding, before any opinion:

- Find the thing: grep the code, `assets/`, the maps (`assets/maps/*.tmx`), the localization catalogs and the docs.
  Name the class, asset, room id or string key. Look at a frame or sprite when the remark is visual
  (`tools/` renderers, the web build of the memory note on playtesting) instead of reasoning from the file name.
- Say what the build does now and why: which decision (D-number) or rule put it there.
- Check whether it is already done, already tracked (search issues, open and closed, as kimeria-issue section 1 says)
  or a verification request ("check that room X has a hint text": check it now; it may need no issue at all).
- Classify: `bug`, `readability` (art, contrast, size), `text` (wording, tale voice), `feel` (timing, controls),
  `design change` (a mechanic, a room, a trap set), `reverses a decision`, `already done`, `unclear`.
- Name the rules the change must respect, so the decision is made knowing them: D71 traps visible, D72 and D105 rooms,
  D76 contrast tiers, D93 seen and heard, D100 to D102 monsters, D74 and D97 text, D9 input, the allocation rule. A
  removed mechanic lists every room, card, test and doc that uses it (grep the element registry in
  [ROOMS.md](../../../docs/ROOMS.md) and [ROOM_CARDS.md](../../../docs/ROOM_CARDS.md)).

## 4. Get the author's decisions

- Findings that are clear bugs or plain fixes inside existing rules need no question: write the decision as
  "Fix: ..." and say so in the report.
- Everything else (a reversed decision, a mechanic removed, a design choice with real alternatives, text tone, art
  direction) goes to the author before any issue is filed. Ask with `AskUserQuestion`, at most four questions per call,
  each self-contained (name the finding and what the build does today), the recommended option first. Batch the rest in
  further calls; never assume an answer.
- A finding the author rejects is still recorded (decision "Not adopted", with the reason) and gets no issue.

## 5. File the issues

- One issue per deliverable change, written by [kimeria-issue](../kimeria-issue/SKILL.md): the observation and its
  finding id, the analysis, the decision with its date, a checklist with the owning skill, the running-game check
  and the docs to update; labels `playtest-feedback` plus `bug` or `enhancement`.
- An existing issue that covers a finding gets a comment (and a checklist line if needed) instead of a twin.
- When the round produced more than a handful of issues, file an umbrella issue `Playtest <N>: follow-up` whose task
  list links every child, and link it from the playtest doc and from the release item for playtest fixes in
  [ROADMAP.md](../../../docs/ROADMAP.md) if one is open.

## 6. Tick what the round verified

Real players are the evidence some open boxes wait for. Find them:

```bash
gh issue list --state open --limit 200 --json number,title,body \
  --jq '.[] | select(.body | test("- \\[ \\].*(running game|in game|in-game|playtest|web build|desktop build|on a device|audition|readab)"; "i")) | "\(.number) \(.title)"'
```

Also read the open "check in the running game" collectors (for example #368) and the review-pending notes in the docs
("awaiting review", "in-game check"). For each unchecked box decide, by the rules of kimeria-issue section 5:

- a tester played that part and the box's claim held (positive report, or they clearly used it without trouble): tick
  it, with a comment citing `docs/PLAYTEST_<N>.md` and the finding;
- a tester reported a problem with it: leave it open, link the new issue in a comment;
- the part was played but nobody said anything about it: list it for the author; tick only on the author's word, with a
  comment saying it was accepted on that basis;
- the part was not in the build or not reached: leave it.

Show the list of boxes you plan to tick (issue, box, evidence) to the author before editing when any of them rests on
silence. Record the outcome in the playtest doc ("What this playtest verified").

## 7. Write the record and update the docs

- `docs/PLAYTEST_<N>.md` with the sections of PLAYTEST_1: status line; Session table; what this playtest did not
  measure; what it verified (the ticked boxes, by issue); Feedback, analysis and decisions, one table per theme
  (`# | Feedback | Analysis | Decision | Issue`); Patterns; Maintenance ("when an issue lands, update its row").
  Note the original language of the feedback.
- A D-number in [DECISIONS.md](../../../docs/DECISIONS.md) that summarizes the round and the author's direction (D66 is
  the model), plus an update line under each decision the round reversed or changed.
- A row in [docs/README.md](../../../docs/README.md) for the new playtest doc.
- Every doc note revised by a ticked box ("not yet checked in game", "awaiting review") is revised in the same change,
  and every new gap line links its issue (`tools/check_doc_gaps.py`).
- Gates before the commit: `python3 tools/check_doc_gaps.py`, `python3 tools/check_skills.py` if skills changed,
  `./gradlew :core:check` when any test-pinned doc (room cards, traps) changed.
- Commit and PR only with the author's approval (AGENTS.md "Git in this repo"); the PR body links the umbrella issue.

## 8. Report

End with: the number of findings, how many became issues (as links), how many were already tracked, rejected or
already done, the boxes ticked and on what evidence, the questions still open for the author, and what was not
verified.
