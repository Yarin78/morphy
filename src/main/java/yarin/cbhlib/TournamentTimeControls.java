package yarin.cbhlib;

import java.util.EnumSet;

public class TournamentTimeControls
{
    // Normal has no own flag, it's implicit
    public enum TournamentTimeControl {
        Blitz,
        Rapid,
        Corresp
    }

    private EnumSet<TournamentTimeControl> timeControls;

    public static TournamentTimeControls decode(int value) {
        TournamentTimeControls timeControls = new TournamentTimeControls();
        for (TournamentTimeControls.TournamentTimeControl timeControl : TournamentTimeControl.values()) {
            if (((1<<timeControl.ordinal()) & value) > 0) {
                timeControls.add(timeControl);
            }
        }
        return timeControls;
    }

    public int encode() {
        int value = 0;
        for (TournamentTimeControls.TournamentTimeControl timeControl : timeControls) {
            value |= timeControl.ordinal();
        }
        return value;
    }

    public int size() {
        return timeControls.size();
    }

    public TournamentTimeControls() {
        timeControls = EnumSet.noneOf(TournamentTimeControls.TournamentTimeControl.class);
    }

    public TournamentTimeControls(EnumSet<TournamentTimeControls.TournamentTimeControl> tournamentTimeControls) {
        this.timeControls = tournamentTimeControls;
    }

    public void add(TournamentTimeControls.TournamentTimeControl timeControl) {
        timeControls.add(timeControl);
    }

    public boolean contains(TournamentTimeControls.TournamentTimeControl timeControl) {
        return timeControls.contains(timeControl);
    }}
