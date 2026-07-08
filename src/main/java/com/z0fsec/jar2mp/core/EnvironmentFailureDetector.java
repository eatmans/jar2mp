package com.z0fsec.jar2mp.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Detects whether a runtime startup failure is caused by a missing external
 * infrastructure dependency (Redis, MySQL, Kafka, etc.) rather than a bug in
 * the restored application code.  Environment failures are not penalised in
 * the restoration score.
 */
public class EnvironmentFailureDetector {

    private static final List<String> DEFAULT_PATTERNS = Collections.unmodifiableList(Arrays.asList(
            // Redis
            "redisconnectionexception",
            "unable to connect to redis server",
            "jedisconnectionexception",
            // MySQL / generic JDBC
            "communications link failure",
            "access denied for user",
            "unknown database",
            "unable to acquire jdbc connection",
            // Generic network
            "connectexception: connection refused",
            "connection refused",
            "connection timed out",
            // Kafka
            "org.apache.kafka",
            "kafkaexception",
            // Nacos
            "nacosexception",
            "nacos server did not start",
            // MongoDB
            "com.mongodb",
            "mongoexception",
            // Zookeeper
            "org.apache.zookeeper",
            // RabbitMQ
            "com.rabbitmq"
    ));

    private final List<String> patterns;

    public EnvironmentFailureDetector() {
        this(Collections.<String>emptyList());
    }

    /**
     * @param extraPatterns additional lower-case substrings to treat as
     *                      environment failures (merged with the defaults)
     */
    public EnvironmentFailureDetector(List<String> extraPatterns) {
        if (extraPatterns == null || extraPatterns.isEmpty()) {
            this.patterns = DEFAULT_PATTERNS;
        } else {
            List<String> merged = new java.util.ArrayList<>(DEFAULT_PATTERNS);
            for (String p : extraPatterns) {
                if (p != null && !p.trim().isEmpty()) {
                    merged.add(p.trim().toLowerCase(Locale.ROOT));
                }
            }
            this.patterns = Collections.unmodifiableList(merged);
        }
    }

    /**
     * Returns true when the combined output of the smoke run contains at least
     * one pattern that indicates an external environment dependency is missing.
     */
    public boolean isEnvironmentFailure(RuntimeSmokeRunner.SmokeRunResult smokeResult) {
        if (smokeResult == null) {
            return false;
        }
        String combined = (safe(smokeResult.getFailureMessage()) + "\n"
                + safe(smokeResult.getStdout()) + "\n"
                + safe(smokeResult.getStderr())).toLowerCase(Locale.ROOT);
        for (String pattern : patterns) {
            if (combined.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
