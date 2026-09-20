# Writing the format documentation

Each format has a `spec/` and a `notes/` directory, and they have different
jobs. Keep them apart.

## `spec/` — the specification

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
- **Keep `spec/UNKNOWNS.md` in step.** It lists every **unknown** in that
  specification. Resolving one means deleting its entry; finding one means
  adding an entry beside the **unknown** itself.

## `notes/` — the working notes

Everything the specification may not say: what each fact rests on, which
database showed it, what was tried and failed, and what is still open. One file
per specification file, with the same name, so the pair is easy to keep
together. Each ends with the open questions for its own subject.

Before removing evidence from a specification, make sure the notes already hold
it, and move it there if they do not.

## Both

Figures live in `spec/img/`, drawn by the `make.py` beside them; run it to
regenerate them. Anchors are GitHub-style: a heading's link is its text
lowercased with punctuation dropped and **each** space turned into a hyphen, so
`` ## `.2lgd` — the games ``  is `#2lgd--the-games`.
