package com.jobproof.infrastructure.queue;

import java.net.InetAddress;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Identity of this JVM in shared work queues: host, process id and a random suffix, so two
 * processes on one host (API and worker) and a restarted process never share an identity.
 */
@Component
public class WorkerIdentity {
    private final String id;

    public WorkerIdentity() {
        this.id = hostname() + "#" + ProcessHandle.current().pid() + "#" + UUID.randomUUID().toString().substring(0, 8);
    }

    public String id() {
        return id;
    }

    private static String hostname() {
        String fromEnv = System.getenv("HOSTNAME");
        if (fromEnv != null && !fromEnv.isBlank()) return truncate(fromEnv.trim());
        try {
            return truncate(InetAddress.getLocalHost().getHostName());
        } catch (Exception exception) {
            return "unknown-host";
        }
    }

    private static String truncate(String value) {
        return value.length() > 60 ? value.substring(0, 60) : value;
    }
}
