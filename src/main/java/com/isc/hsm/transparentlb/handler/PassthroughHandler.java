package com.isc.hsm.transparentlb.handler;

import com.isc.hsm.transparentlb.lb.LoadBalancerSelector;
import com.isc.hsm.transparentlb.node.ThalesNodePool;
import com.isc.hsm.transparentlb.node.ThalesNodeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class PassthroughHandler {

    private static final Logger log = LoggerFactory.getLogger(PassthroughHandler.class);

    private final ThalesNodeRegistry registry;
    private final LoadBalancerSelector lbSelector;

    public PassthroughHandler(ThalesNodeRegistry registry, LoadBalancerSelector lbSelector) {
        this.registry = registry;
        this.lbSelector = lbSelector;
    }

    /**
     * Receives raw Thales command bytes, forwards to a selected healthy node,
     * and returns the raw response bytes.
     */
    public byte[] handle(byte[] rawCommand) throws Exception {
        List<ThalesNodePool> healthy = registry.getHealthyPools();
        if (healthy.isEmpty()) {
            // Fall back to all nodes if health checker hasn't run yet (startup)
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
        log.debug("Routing {} bytes to node {}", rawCommand.length, pool.getNode().getId());
        long t0 = System.currentTimeMillis();
        try {
            byte[] response = pool.send(rawCommand);
            log.debug("Node {} replied {} bytes in {}ms",
                pool.getNode().getId(), response.length, System.currentTimeMillis() - t0);
            return response;
        } catch (Exception e) {
            log.error("Node {} failed after {}ms: {}", pool.getNode().getId(),
                System.currentTimeMillis() - t0, e.getMessage());
            throw e;
        }
    }
}
