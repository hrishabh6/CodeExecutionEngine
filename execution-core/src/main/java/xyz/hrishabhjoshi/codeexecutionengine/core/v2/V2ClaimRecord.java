package xyz.hrishabhjoshi.codeexecutionengine.core.v2;

public record V2ClaimRecord(
        String runnerId,
        String attemptToken,
        long attemptGeneration,
        long leaseExpiryEpochSeconds) {

    public String encode() {
        return runnerId + '|' + attemptToken + '|' + attemptGeneration + '|' + leaseExpiryEpochSeconds;
    }

    public static V2ClaimRecord decode(String raw) {
        String[] parts = raw.split("\\|", 4);
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid claim record");
        }
        return new V2ClaimRecord(
                parts[0],
                parts[1],
                Long.parseLong(parts[2]),
                Long.parseLong(parts[3]));
    }
}
