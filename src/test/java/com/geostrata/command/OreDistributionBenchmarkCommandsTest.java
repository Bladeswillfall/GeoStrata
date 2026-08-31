package com.geostrata.command;

import com.geostrata.geology.OreGrade;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class OreDistributionBenchmarkCommandsTest {
    @Test
    void groupsGradesByStoredHostAndWritesZeroes() {
        OreDistributionBenchmarkCommands.MaterialStats stats =
                new OreDistributionBenchmarkCommands.MaterialStats();
        stats.record(sample("shale", OreGrade.MEDIUM), false, 0, 0L, 0L, 0, 13);
        stats.record(sample("shale", OreGrade.MEDIUM), false, 0, 0L, 1L, 0, 13);
        stats.record(sample("quartzite", OreGrade.RICH), false, 0, 0L, 2L, 0, 13);

        JsonObject hosts = stats.toJson().getAsJsonObject("gradesByHost");
        JsonObject shale = hosts.getAsJsonObject("shale");
        JsonObject quartzite = hosts.getAsJsonObject("quartzite");

        assertEquals(0L, shale.get("poor").getAsLong());
        assertEquals(2L, shale.get("medium").getAsLong());
        assertEquals(0L, shale.get("rich").getAsLong());
        assertEquals(0L, shale.get("massive").getAsLong());
        assertEquals(1L, quartzite.get("rich").getAsLong());
        assertEquals(0L, quartzite.get("massive").getAsLong());
    }

    private static OreDistributionBenchmarkCommands.OreSample sample(String host, OreGrade grade) {
        return new OreDistributionBenchmarkCommands.OreSample("iron", grade, true, host);
    }
}
