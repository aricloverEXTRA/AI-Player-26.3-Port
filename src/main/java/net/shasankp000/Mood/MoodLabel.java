package net.shasankp000.Mood;

/**
 * Enumeration of all discrete mood states the bot can inhabit.
 * The ordinal is used only for serialisation; never rely on numeric value for logic.
 */
public enum MoodLabel {
    NEUTRAL,
    HAPPY,
    SAD,
    ANGRY,
    FEARFUL,
    EXCITED,
    BORED
}
