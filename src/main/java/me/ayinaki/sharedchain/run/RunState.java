package me.ayinaki.sharedchain.run;

/**
 * Represents the current status of the challenge run.
 */
public enum RunState {
    IDLE,
    RUNNING,
    STARTING,
    FINISHED,
    WIPED,
    RESETTING
}
