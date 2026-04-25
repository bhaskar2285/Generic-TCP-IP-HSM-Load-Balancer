package com.isc.hsm.transparentlb.node;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ThalesNode {

    private final String id;
    private final String host;
    private final int port;
    private final int weight;

    private final AtomicBoolean healthy = new AtomicBoolean(false);
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    private volatile long lastHealthCheckMs = 0;

    public ThalesNode(String id, String host, int port, int weight) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.weight = weight;
    }

    public String getId() { return id; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public int getWeight() { return weight; }
    public boolean isHealthy() { return healthy.get(); }
    public void setHealthy(boolean v) { healthy.set(v); lastHealthCheckMs = System.currentTimeMillis(); }
    public int getActiveConnections() { return activeConnections.get(); }
    public void incrementActive() { activeConnections.incrementAndGet(); }
    public void decrementActive() { activeConnections.decrementAndGet(); }
    public void recordRequest() { totalRequests.incrementAndGet(); }
    public void recordError() { totalErrors.incrementAndGet(); }
    public long getTotalRequests() { return totalRequests.get(); }
    public long getTotalErrors() { return totalErrors.get(); }
    public long getLastHealthCheckMs() { return lastHealthCheckMs; }

    @Override
    public String toString() {
        return id + "(" + host + ":" + port + " healthy=" + healthy.get() + " active=" + activeConnections.get() + ")";
    }
}
