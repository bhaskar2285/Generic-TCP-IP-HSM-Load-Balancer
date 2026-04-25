package com.isc.hsm.transparentlb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "hsm.lb")
public class LbProperties {

    private String algorithm = "ROUND_ROBIN";

    private QueueConfig queue = new QueueConfig();
    private JmsConfig jms = new JmsConfig();
    private PoolConfig pool = new PoolConfig();
    private HealthConfig health = new HealthConfig();

    // Raw comma-separated node list: id:host:port:weight,...
    private String nodes = "";

    public List<NodeEntry> parsedNodes() {
        List<NodeEntry> result = new ArrayList<>();
        if (nodes == null || nodes.isBlank()) return result;
        for (String token : nodes.split(",")) {
            String[] parts = token.trim().split(":");
            if (parts.length < 3) continue;
            String id = parts[0];
            String host = parts[1];
            int port = Integer.parseInt(parts[2]);
            int weight = parts.length >= 4 ? Integer.parseInt(parts[3]) : 1;
            result.add(new NodeEntry(id, host, port, weight));
        }
        return result;
    }

    public record NodeEntry(String id, String host, int port, int weight) {}

    public static class QueueConfig {
        private String inbound = "hsm.transparent.lb.in";
        private String control = "hsm.transparent.lb.control";
        public String getInbound() { return inbound; }
        public void setInbound(String v) { inbound = v; }
        public String getControl() { return control; }
        public void setControl(String v) { control = v; }
    }

    public static class JmsConfig {
        private int concurrentConsumers = 20;
        private int maxConcurrentConsumers = 100;
        public int getConcurrentConsumers() { return concurrentConsumers; }
        public void setConcurrentConsumers(int v) { concurrentConsumers = v; }
        public int getMaxConcurrentConsumers() { return maxConcurrentConsumers; }
        public void setMaxConcurrentConsumers(int v) { maxConcurrentConsumers = v; }
    }

    public static class PoolConfig {
        private int maxTotal = 20;
        private int minIdle = 2;
        private long maxWaitMs = 3000;
        private int socketTimeoutMs = 10000;
        private int connectTimeoutMs = 3000;
        public int getMaxTotal() { return maxTotal; }
        public void setMaxTotal(int v) { maxTotal = v; }
        public int getMinIdle() { return minIdle; }
        public void setMinIdle(int v) { minIdle = v; }
        public long getMaxWaitMs() { return maxWaitMs; }
        public void setMaxWaitMs(long v) { maxWaitMs = v; }
        public int getSocketTimeoutMs() { return socketTimeoutMs; }
        public void setSocketTimeoutMs(int v) { socketTimeoutMs = v; }
        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(int v) { connectTimeoutMs = v; }
    }

    public static class HealthConfig {
        private long intervalMs = 5000;
        private String commandHex = "0008303030304e4f3030";
        public long getIntervalMs() { return intervalMs; }
        public void setIntervalMs(long v) { intervalMs = v; }
        public String getCommandHex() { return commandHex; }
        public void setCommandHex(String v) { commandHex = v; }
    }

    // Getters / setters
    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String v) { algorithm = v; }
    public String getNodes() { return nodes; }
    public void setNodes(String v) { nodes = v; }
    public QueueConfig getQueue() { return queue; }
    public void setQueue(QueueConfig v) { queue = v; }
    public JmsConfig getJms() { return jms; }
    public void setJms(JmsConfig v) { jms = v; }
    public PoolConfig getPool() { return pool; }
    public void setPool(PoolConfig v) { pool = v; }
    public HealthConfig getHealth() { return health; }
    public void setHealth(HealthConfig v) { health = v; }
}
