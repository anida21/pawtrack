package com.pawtrack.health.support;

/** Minimal valid request bodies (health.yaml) for tests that only need "some record". */
public final class Bodies {

    public static final String VACCINATION = """
        {"vaccine":"Rabies","administeredOn":"2026-01-10"}""";
    public static final String VET_VISIT = """
        {"visitDate":"2026-01-10T09:30:00Z","reason":"Checkup"}""";
    public static final String MEDICATION = """
        {"name":"Drontal","dosage":"1 tablet","startDate":"2026-01-10","frequency":"ONCE"}""";
    public static final String WEIGHT = """
        {"measuredOn":"2026-01-10","weightKg":12.5}""";

    private Bodies() {
    }
}
