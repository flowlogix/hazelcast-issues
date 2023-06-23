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
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import static com.flowlogix.hazelcast.cachetester.CacheTester.hzBasePort;

/**
 *
 * @author lprimak
 */
public class MyDiscoveryService implements DiscoveryService {
    private final List<DiscoveryNode> nodes = new ArrayList<>();

    public MyDiscoveryService(DiscoveryServiceSettings settings) {
    }

    @Override
    public void start() {
        var loopback = InetAddress.getLoopbackAddress();
        IntStream.rangeClosed(hzBasePort, hzBasePort + 20).forEach(port ->
                nodes.add(new SimpleDiscoveryNode(new Address(loopback, port))));
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
