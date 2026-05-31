package com.lumoxu.cof.service;

public class ComputerTickOutcome {

    public boolean changed;
    public boolean played;
    public boolean rang;
    public boolean justFinished;

    public static ComputerTickOutcome none() {
        return new ComputerTickOutcome();
    }

    public static ComputerTickOutcome of(boolean changed, boolean played, boolean rang) {
        ComputerTickOutcome outcome = new ComputerTickOutcome();
        outcome.changed = changed;
        outcome.played = played;
        outcome.rang = rang;
        return outcome;
    }
}
