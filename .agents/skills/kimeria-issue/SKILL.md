---
name: kimeria-issue
description: Writes, files, updates and ticks Edgard in Kimeria GitHub issues in the project's format - duplicate search, an English body with context, decision and a checklist that names the owning skill, gates, the running-game check and the docs to update, labels, umbrella issues, and ticking a box only on evidence. Use whenever an issue is created, split, merged, re-scoped, ticked or closed, including issues that come out of playtest feedback or a doc gap.
---

# Kimeria issues

Project-owned, a thin procedure. The rules it serves live in [AGENTS.md](../../../AGENTS.md) ("Language", "Gaps
written into docs must be tracked", "Style system") and [docs/DECISIONS.md](../../../docs/DECISIONS.md). Work from the
repository root with `gh`; links resolve relative to this file. Feedback rounds start in
[kimeria-feedback-triage](../kimeria-feedback-triage/SKILL.md), which calls this skill for every issue it files.

## 1. Before writing

- `gh auth status`: the active account must be the one the author pushes with (`gh auth switch` if not).
- **Duplicates:** search open *and* closed issues with two or three key words each
  (`gh issue list --state all --search "<words>" --limit 20`). An open issue that already covers the work gets a
  comment with the new evidence or a new checklist item, not a twin. A closed one that the new report contradicts is
  reopened or linked from a new issue that says what changed.
- **Decisions:** grep [DECISIONS.md](../../../docs/DECISIONS.md) and the owning reference doc for the topic. A request
  that reverses an accepted decision (a D-number) or breaks a rule of AGENTS.md is not filed as plain work: it needs the
  author's decision first, and the issue then names the decision it changes and lists "update D<NN>" in its checklist.
- **Facts:** look at the code, asset or map the issue is about and name it (class, file, room id, Tiled object). An
  issue that only restates a remark makes the next agent find it all again.

## 2. Scope: one issue, one deliverable

One issue is one change that lands in one PR and can be checked on its own. Split a remark that holds two changes
(a sprite redraw and a mechanic change); merge two remarks that are one change. Work that spans systems follows the
order of [STYLE_SYSTEM.md](../../../docs/STYLE_SYSTEM.md) (card, approval, then build) and may be an umbrella issue
whose task list links the child issues (`- [ ] #NNN short title`).

## 3. Title and body

Everything in English (AGENTS.md), even when the source is not: translate, never paste the original.

**Title:** `Area: outcome`, short, no trailing period, the result not the activity. The area is the player-facing part
or system: `Intro`, `Main menu`, `Options`, `Dialogue`, `Healer`, `Hud`, a trap or monster name, a room id, `Art`,
`Sound`, `Controls`, `Docs`. Examples from the tracker: `Options: Sound and Music on/off`,
`Touch buttons: pictograms instead of text labels`, `Spider jitters around its chase target`.

**Body**, in this order, plain Markdown, no headings needed for a short issue:

```markdown
<Source and observation>: what was seen, where, on which build and device; then what the build does today and why,
naming the class, asset, room or decision.

<Decision or proposal>: what will change. Say "Decision (author, YYYY-MM-DD)" when the author chose it, "Proposal"
when nobody has yet. List rejected alternatives in one line if the author weighed them.

- [ ] <design step first when a rule asks for one: the trap card in docs/TRAPS.md, the room card, the monster card,
      EN text approved by the author> (`<owning kimeria-* skill>`)
- [ ] <implementation steps, each a checkable result> (`<owning skill>`)
- [ ] Tests: <what a test pins, by class name when known>; gates: <validate* tasks that apply>
- [ ] Check in the running game: <what a person must see or hear, desktop and/or web, device>
- [ ] Update docs/<FILE>.md (<section or D-number>)

Source: <playtest doc and finding id, or the doc line, or the PR/review that found it>.
```

Rules for the checklist:

- Every box is a result a reviewer can verify, not an activity ("the lever room shows its hint", not "look at the
  hint").
- Name the owning skill in brackets: text goes through `kimeria-game-text` (EN first, the author's approval, then all
  five catalogs), traps through `kimeria-trap-design`, rooms through `kimeria-room-design` (D72, D105), art through
  `kimeria-art-direction`, sound through `kimeria-sound`, effects through `kimeria-particles`, controls through
  `kimeria-input-flows`. A monster names its card in docs/GAME_DESIGN.md (D100).
- Per-frame code adds "no allocation per frame (`AllocationBudgetTest`)" when it touches `update`/`render`.
- **"Check in the running game" is its own box**, separate from tests, whenever the claim is about what a person sees,
  hears or understands (readability, feel, timing, wording). These are the boxes a playtest later ticks.
- **"Update docs/X" is mandatory** when the issue closes a gap written in a doc, changes a rule, or makes a doc
  describe something that is no longer true. A doc line that announces this issue as a gap links it (`#NNN`) in the
  same change (AGENTS.md, `tools/check_doc_gaps.py`).
- Never write a bare `@name`; put code and roles in backticks.

**Labels:** `bug` (the build does something wrong), `enhancement` (a new or changed behaviour), `documentation`
(docs only), plus `playtest-feedback` for anything that came from people playing. `question` for an issue that waits
for the author's decision. No new labels without the author.

## 4. Filing

Filing is outward-facing: file only when the user asked for issues to be filed, and show the list first when it
contains anything the user has not seen (a split, a merge, an assumption). Write each body to a file in the scratchpad
and pass it with `--body-file`, so quotes and backticks survive the shell:

```bash
gh issue create --title "Area: outcome" --body-file "$SCRATCH/issue-1.md" --label enhancement --label playtest-feedback
```

Record every created number (the command prints the URL) and report them as links. For a batch, create the children
first, then the umbrella with their numbers, then add `Part of #<umbrella>` to each child if the user wants the back
links.

## 5. Updating and ticking

- Edit a body only from its current text: `gh issue view N --json body --jq .body > file`, change the exact line,
  `gh issue edit N --body-file file`. Never rebuild a body from memory; someone may have edited it.
- **Tick a box only on evidence** that the box's own claim is true: a merged PR, a test, a captured frame, or a person
  who played and reported it. Add a comment in the same step that says what the evidence is and where it is recorded
  (`Checked in playtest 2 (docs/PLAYTEST_2.md, 2026-11-21): no tester had trouble reading the intro pages.`).
- **Silence is weak evidence.** If players simply did not complain about the thing the box asks about, do not tick it
  on your own: list it for the author, and tick it only on the author's word, with a comment saying the box was
  accepted by the author on that basis (the form PLAYTEST_1 used for #130, #131, #300).
- A report of a problem with the thing the box asks about is the opposite of evidence: leave the box open and link the
  new issue from a comment.
- Close an issue only when every box is ticked or explicitly dropped with a reason, and the doc notes it tracked are
  removed or revised in the same PR (AGENTS.md). Prefer `Closes #N` in the PR over closing by hand.
