package com.flowlogix.hazelcast.cachetester;

import com.hazelcast.config.properties.PropertyDefinition;
import com.hazelcast.logging.ILogger;
import com.hazelcast.spi.discovery.DiscoveryNode;
import com.hazelcast.spi.discovery.DiscoveryStrategy;
import com.hazelcast.spi.discovery.DiscoveryStrategyFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class MyDiscoveryStrategyFactory implements DiscoveryStrategyFactory {
    private static final Collection<PropertyDefinition> PROPERTIES = List.of();

    @Override
    public Class<? extends DiscoveryStrategy> getDiscoveryStrategyType() {
        return MyDiscoveryStrategy.class;
    }

    @Override
    public DiscoveryStrategy newDiscoveryStrategy(DiscoveryNode discoveryNode, ILogger logger, Map<String, Comparable> properties) {
        return new MyDiscoveryStrategy(logger, properties);
    }

    @Override
    public Collection<PropertyDefinition> getConfigurationProperties() {
        return PROPERTIES;
    }
}
