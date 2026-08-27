package com.geostrata.geology;

/** Stable economic ore grades in ascending concentration order. */
public enum OreGrade {
    POOR("poor", 1, 0, 1),
    MEDIUM("medium", 2, 1, 2),
    RICH("rich", 4, 2, 4),
    MASSIVE("massive", 8, 4, 8);

    private final String id;
    private final int baseYield;
    private final int experienceMin;
    private final int experienceMax;

    OreGrade(String id, int baseYield, int experienceMin, int experienceMax) {
        this.id = id;
        this.baseYield = baseYield;
        this.experienceMin = experienceMin;
        this.experienceMax = experienceMax;
    }

    public String id() {
        return id;
    }

    public int baseYield() {
        return baseYield;
    }

    public int experienceMin() {
        return experienceMin;
    }

    public int experienceMax() {
        return experienceMax;
    }
}
