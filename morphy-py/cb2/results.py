"""Game results and line evaluations. Which value means what is borrowed from
the older format (v1), see format/v2/1-game-headers.md; the way a result
is shown follows the
Java command line tool (ResultsColumn).
"""

# Result code -> text. Code 3 is missing: it means the game is not finished, in
# which case the line evaluation says how the position is evaluated instead.
NOT_FINISHED = 3
RESULT_TEXT = {
    0: "0-1",
    1: "½-½",
    2: "1-0",
    4: "-:+",  # black wins on forfeit
    5: "=:=",  # draw on forfeit
    6: "+:-",  # white wins on forfeit
    7: "0-0",  # both players lost
}

# NAG number -> text, for the NAGs that are line evaluations.
LINE_EVALUATION_TEXT = {
    10: "=", 11: "=", 12: "=",
    13: "unclear",
    14: "+/=", 15: "=/+",
    16: "+/-", 17: "-/+",
    18: "+-", 19: "-+",
    20: "+-", 21: "-+",
    **dict.fromkeys(range(30, 36), "dev adv"),
    **dict.fromkeys(range(36, 40), "w/ initiative"),
    **dict.fromkeys(range(40, 42), "w/ attack"),
    **dict.fromkeys(range(44, 48), "w/ comp"),
    **dict.fromkeys(range(132, 136), "w/ counter"),
    **dict.fromkeys(range(136, 140), "zeitnot"),
    146: "N",
}


def format_result(result_code, nag):
    """The result as text. For a game that isn't finished this is the line
    evaluation given by nag ("" if there is none). A value that isn't known
    is shown as "?" and the value."""
    if result_code == NOT_FINISHED:
        if nag == 0:
            return ""
        return LINE_EVALUATION_TEXT.get(nag, f"?{nag}")
    return RESULT_TEXT.get(result_code, f"?{result_code}")
