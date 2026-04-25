package com.isc.hsm.transparentlb.node;

import com.isc.hsm.transparentlb.config.LbProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class ThalesNodeRegistry {

    private static final Logger log = LoggerFactory.getLogger(ThalesNodeRegistry.class);

    private final LbProperties props;
    private final List<ThalesNodePool> nodePools = new ArrayList<>();

    public ThalesNodeRegistry(LbProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void init() {
        for (LbProperties.NodeEntry entry : props.parsedNodes()) {
            ThalesNode node = new ThalesNode(entry.id(), entry.host(), entry.port(), entry.weight());
            ThalesNodePool pool = new ThalesNodePool(node, props);
            nodePools.add(pool);
            log.info("Registered Thales node: {}", node);
        }
        if (nodePools.isEmpty()) {
            log.warn("No Thales nodes configured — check hsm.lb.nodes in application.properties");
        }
    }

    public List<ThalesNodePool> getAllPools() {
        return Collections.unmodifiableList(nodePools);
    }

    public List<ThalesNodePool> getHealthyPools() {
        return nodePools.stream().filter(p -> p.getNode().isHealthy()).toList();
    }

    @PreDestroy
    public void shutdown() {
        nodePools.forEach(ThalesNodePool::close);
    }
}
