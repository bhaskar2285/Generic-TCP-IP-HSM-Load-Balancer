package com.isc.hsm.transparentlb.health;

import com.isc.hsm.transparentlb.config.LbProperties;
import com.isc.hsm.transparentlb.node.ThalesNodePool;
import com.isc.hsm.transparentlb.node.ThalesNodeRegistry;
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
    private final byte[] healthCommandBytes;

    public NodeHealthChecker(ThalesNodeRegistry registry, LbProperties props) {
        this.registry = registry;
        this.props = props;
        this.healthCommandBytes = hexToBytes(props.getHealth().getCommandHex());
    }

    @Scheduled(fixedDelayString = "${hsm.lb.health.interval-ms:5000}")
    public void checkAll() {
        for (ThalesNodePool pool : registry.getAllPools()) {
            boolean alive = probe(pool);
            boolean wasHealthy = pool.getNode().isHealthy();
            pool.getNode().setHealthy(alive);
            if (wasHealthy != alive) {
                log.warn("Node {} health changed: {} -> {}", pool.getNode().getId(), wasHealthy, alive);
            } else {
                log.debug("Node {} health: {}", pool.getNode().getId(), alive);
            }
        }
    }

    private boolean probe(ThalesNodePool pool) {
        try (Socket s = new Socket()) {
            s.setSoTimeout(props.getPool().getSocketTimeoutMs());
            s.connect(new InetSocketAddress(pool.getNode().getHost(), pool.getNode().getPort()),
                      props.getPool().getConnectTimeoutMs());
            s.getOutputStream().write(healthCommandBytes);
            s.getOutputStream().flush();

            // Read at least 2 bytes of response — any reply = alive
            byte[] buf = new byte[64];
            int n = s.getInputStream().read(buf);
            return n > 0;
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
