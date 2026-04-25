package com.isc.hsm.transparentlb.handler;

import com.isc.hsm.transparentlb.lb.LoadBalancerSelector;
import com.isc.hsm.transparentlb.node.ThalesNodePool;
import com.isc.hsm.transparentlb.node.ThalesNodeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class PassthroughHandler {

    private static final Logger log        = LoggerFactory.getLogger(PassthroughHandler.class);
    private static final Logger payloadLog = LoggerFactory.getLogger("com.isc.hsm.transparentlb.PAYLOAD");

    private final ThalesNodeRegistry registry;
    private final LoadBalancerSelector lbSelector;

    @Value("${hsm.lb.payload.log.enabled:false}")
    private boolean payloadLogEnabled;

    public PassthroughHandler(ThalesNodeRegistry registry, LoadBalancerSelector lbSelector) {
        this.registry = registry;
        this.lbSelector = lbSelector;
    }

    public byte[] handle(byte[] rawCommand) throws Exception {
        List<ThalesNodePool> healthy = registry.getHealthyPools();
        if (healthy.isEmpty()) {
            healthy = registry.getAllPools();
        }
        if (healthy.isEmpty()) {
            throw new IllegalStateException("No Thales nodes available");
        }

        Optional<ThalesNodePool> selected = lbSelector.get().select(healthy);
        if (selected.isEmpty()) {
            throw new IllegalStateException("Load balancer returned no node");
        }

        ThalesNodePool pool = selected.get();
        String nodeId = pool.getNode().getId();
        long t0 = System.currentTimeMillis();

        log.debug(">>> REQUEST  node={} bytes={} hex={}", nodeId, rawCommand.length, toHex(rawCommand));

        if (payloadLogEnabled) {
            payloadLog.debug(">>> REQUEST  node={} bytes={} hex={}", nodeId, rawCommand.length, toHex(rawCommand));
        }

        try {
            byte[] response = pool.send(rawCommand);
            long latency = System.currentTimeMillis() - t0;

            log.debug("<<< RESPONSE node={} bytes={} latency={}ms hex={}", nodeId, response.length, latency, toHex(response));

            if (payloadLogEnabled) {
                payloadLog.debug("<<< RESPONSE node={} bytes={} latency={}ms hex={}", nodeId, response.length, latency, toHex(response));
            }

            return response;

        } catch (Exception e) {
            log.error("!!! ERROR    node={} latency={}ms error={}", nodeId, System.currentTimeMillis() - t0, e.getMessage());
            throw e;
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02X", b));
        return sb.toString();
    }
}
