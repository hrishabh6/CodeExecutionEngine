package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.harness;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ComplexityProfileTimingStats {

    private ComplexityProfileTimingStats() {
    }

    public record Aggregate(long medianElapsedNs, long madElapsedNs, long minElapsedNs, long maxElapsedNs, int sampleCount) {
    }

    public static Aggregate fromSamples(List<Long> samplesNs) {
        if (samplesNs == null || samplesNs.isEmpty()) {
            return new Aggregate(0, 0, 0, 0, 0);
        }
        List<Long> sorted = new ArrayList<>(samplesNs);
        Collections.sort(sorted);
        long min = sorted.getFirst();
        long max = sorted.getLast();
        long median = median(sorted);
        long mad = mad(sorted, median);
        return new Aggregate(median, mad, min, max, sorted.size());
    }

    private static long median(List<Long> sorted) {
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        return (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
    }

    private static long mad(List<Long> sorted, long median) {
        List<Long> deviations = new ArrayList<>(sorted.size());
        for (long value : sorted) {
            deviations.add(Math.abs(value - median));
        }
        Collections.sort(deviations);
        return median(deviations);
    }
}
