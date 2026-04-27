package com.isc.hsm.transparentlb.health;

import com.isc.hsm.transparentlb.config.LbProperties;
import com.isc.hsm.transparentlb.node.ThalesNodePool;
import com.isc.hsm.transparentlb.node.ThalesNodeRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.Socket;

@Component
public class NodeHealthChecker {

    private static final Logger log = LoggerFactory.getLogger(NodeHealthChecker.class);

    private final ThalesNodeRegistry registry;
    private final LbProperties props;
    private final MeterRegistry meters;
    private final byte[] healthCommandBytes;

    public NodeHealthChecker(ThalesNodeRegistry registry, LbProperties props, MeterRegistry meters) {
        this.registry = registry;
        this.props = props;
        this.meters = meters;
        this.healthCommandBytes = hexToBytes(props.getHealth().getCommandHex());
    }

    @Scheduled(fixedDelayString = "${hsm.lb.health.interval-ms:20000}")
    public void checkAll() {
        for (ThalesNodePool pool : registry.getAllPools()) {
            if (!pool.getNode().isEnabled()) {
                log.debug("Node {} is disabled — skipping health check", pool.getNode().getId());
                continue;
            }
            boolean alive = probe(pool);
            boolean wasHealthy = pool.getNode().isHealthy();
            pool.getNode().setHealthy(alive);

            meters.gauge("hsm.lb.node.healthy",
                java.util.List.of(io.micrometer.core.instrument.Tag.of("node", pool.getNode().getId())),
                alive ? 1.0 : 0.0);

            if (wasHealthy != alive) {
                log.warn("Node {} health changed: {} -> {}", pool.getNode().getId(), wasHealthy, alive);
            } else {
                log.debug("Node {} health: {}", pool.getNode().getId(), alive);
            }
        }
    }

    private boolean probe(ThalesNodePool pool) {
        int connectTimeout = Math.min(props.getPool().getConnectTimeoutMs(), 5000);
        int readTimeout = Math.min(props.getPool().getSocketTimeoutMs(), 5000);
        try (Socket s = new Socket()) {
            s.setSoTimeout(readTimeout);
            s.setTcpNoDelay(true);
            s.connect(new InetSocketAddress(pool.getNode().getHost(), pool.getNode().getPort()), connectTimeout);
            s.getOutputStream().write(healthCommandBytes);
            s.getOutputStream().flush();

            // Wire format: [2-byte length][4-byte header][NP][00][...]
            // NP command response: "NP" is at wire offset 6-7
            byte[] buf = new byte[32];
            int n = s.getInputStream().read(buf);
            if (n < 8) return false;
            int bodyLen = ((buf[0] & 0xFF) << 8) | (buf[1] & 0xFF);
            return bodyLen > 0 && buf[6] == 'N' && buf[7] == 'P';
        } catch (Exception e) {
            log.debug("Health probe failed for node {}: {}", pool.getNode().getId(), e.getMessage());
            return false;
        }
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                 + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
