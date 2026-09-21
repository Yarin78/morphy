# Writing the format documentation

Each format has its specification in a directory of its own, `v1/` and `v2/`,
and its working notes in a `notes/` directory inside that, and they have different
jobs. Keep them apart.

## The specification (`v1/`, `v2/`)

These files say what the format **is**, and nothing else.

- **Never write about corrections.** A reading that was wrong before, a field
  that "is not what it first appears to be", a sentence guarding against a
  mistake nobody reading a specification would make — all of it goes. A reader
  should not be able to tell which parts were hard to work out.
- **Never argue for a reading.** No counts of records examined, no percentages,
  no "in every game of ...", no database named as evidence, no reasoning that
  ends in a conclusion. State the conclusion.
- **Mark what is not understood** with **unknown**, and leave it there. A likely
  meaning is given as likely in a clause, not defended.
- Figures for how common something is are data, not proof, and are welcome where
  that is the point. Keep them out of the prose otherwise.
- An example earns its place by making a layout concrete, not by showing that
  the layout was checked.
- **Keep `UNKNOWNS.md` in step.** It lists every **unknown** in that
  specification. Resolving one means deleting its entry; finding one means
  adding an entry beside the **unknown** itself.

## The working notes (`v1/notes/`, `v2/notes/`)

Everything the specification may not say: what each fact rests on, which
database showed it, what was tried and failed, and what is still open. One file
per specification file, with the same name, so the pair is easy to keep
together. Each ends with the open questions for its own subject.

Before removing evidence from a specification, make sure the notes already hold
it, and move it there if they do not.

**The notes are not in this repository.** They mention personal databases, so
they live in a private repository, `morphy-notes`, next to this checkout, and
`format/v1/notes`, `format/v2/notes` and `format/v1/old` (the superseded v1
documents) are symbolic links to it that git ignores (`ln -s
../../../morphy-notes/v1 format/v1/notes`, and the same for the others). Notes
link to the specification with relative paths (`../2-moves.md`). Never
`git add` them, and expect them to be missing in another clone. Do not put the
names of personal databases in the specifications, in code comments or in
commit messages either: describe them as "a collection of older databases".

## Both

Figures live in `img/`, drawn by the `make.py` inside it; run it to
regenerate them. Anchors are GitHub-style: a heading's link is its text
lowercased with punctuation dropped and **each** space turned into a hyphen, so
`` ## `.2lgd` — the games ``  is `#2lgd--the-games`.
