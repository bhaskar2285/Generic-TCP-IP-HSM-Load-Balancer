package com.isc.hsm.transparentlb.handler;

import com.isc.hsm.transparentlb.lb.LoadBalancerSelector;
import com.isc.hsm.transparentlb.node.ThalesNodePool;
import com.isc.hsm.transparentlb.node.ThalesNodeRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/hsm-lb")
public class StatusController {

    private final ThalesNodeRegistry registry;
    private final LoadBalancerSelector lbSelector;

    public StatusController(ThalesNodeRegistry registry, LoadBalancerSelector lbSelector) {
        this.registry = registry;
        this.lbSelector = lbSelector;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        List<ThalesNodePool> all = registry.getAllPools();
        List<Map<String, Object>> nodes = all.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getNode().getId());
            m.put("host", p.getNode().getHost());
            m.put("port", p.getNode().getPort());
            m.put("weight", p.getNode().getWeight());
            m.put("healthy", p.getNode().isHealthy());
            m.put("activeConnections", p.getNumActive());
            m.put("idleConnections", p.getNumIdle());
            m.put("totalRequests", p.getNode().getTotalRequests());
            m.put("totalErrors", p.getNode().getTotalErrors());
            m.put("lastHealthCheckMs", p.getNode().getLastHealthCheckMs());
            return m;
        }).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("algorithm", lbSelector.get().name());
        result.put("totalNodes", all.size());
        result.put("healthyNodes", registry.getHealthyPools().size());
        result.put("nodes", nodes);
        return result;
    }
}
