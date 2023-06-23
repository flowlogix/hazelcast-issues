/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.flowlogix.hazelcast.cachetester;

import com.hazelcast.cluster.Address;
import com.hazelcast.spi.discovery.DiscoveryNode;
import com.hazelcast.spi.discovery.SimpleDiscoveryNode;
import com.hazelcast.spi.discovery.integration.DiscoveryService;
import com.hazelcast.spi.discovery.integration.DiscoveryServiceSettings;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author lprimak
 */
public class MyDiscoveryService implements DiscoveryService {
    private static final List<DiscoveryNode> nodes = new ArrayList<>();

    public MyDiscoveryService(DiscoveryServiceSettings settings) {
    }

    @Override
    public void start() {
        try {
            nodes.add(new SimpleDiscoveryNode(new Address("127.0.0.1", 5710)));
            nodes.add(new SimpleDiscoveryNode(new Address("127.0.0.1", 5711)));
            nodes.add(new SimpleDiscoveryNode(new Address("127.0.0.1", 5712)));
            nodes.add(new SimpleDiscoveryNode(new Address("127.0.0.1", 5713)));
            nodes.add(new SimpleDiscoveryNode(new Address("127.0.0.1", 5714)));
            nodes.add(new SimpleDiscoveryNode(new Address("127.0.0.1", 5715)));
        } catch (UnknownHostException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public Iterable<DiscoveryNode> discoverNodes() {
        return Collections.unmodifiableList(nodes);
    }

    @Override
    public void destroy() {
        nodes.clear();
    }

    @Override
    public Map<String, String> discoverLocalMetadata() {
        return Collections.unmodifiableMap(new HashMap<>());
    }
}
