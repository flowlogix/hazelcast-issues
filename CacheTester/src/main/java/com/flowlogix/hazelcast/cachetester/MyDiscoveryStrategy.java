package com.flowlogix.hazelcast.cachetester;

import com.hazelcast.cluster.Member;
import com.hazelcast.internal.partition.membergroup.DefaultMemberGroup;
import com.hazelcast.logging.ILogger;
import com.hazelcast.spi.discovery.AbstractDiscoveryStrategy;
import com.hazelcast.spi.discovery.DiscoveryNode;
import com.hazelcast.spi.partitiongroup.MemberGroup;
import com.hazelcast.spi.partitiongroup.PartitionGroupStrategy;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class MyDiscoveryStrategy extends AbstractDiscoveryStrategy {
    private final MyDiscoveryService discoveryService = new MyDiscoveryService();

    public MyDiscoveryStrategy(ILogger logger, Map<String, Comparable> properties) {
        super(logger, properties);
        discoveryService.start();
    }

    @Override
    public Iterable<DiscoveryNode> discoverNodes() {
        return discoveryService.discoverNodes();
    }

    @Override
    public PartitionGroupStrategy getPartitionGroupStrategy(Collection<? extends Member> allMembers) {
        getLogger().info("Getting Partition Strategy");
        MemberGroup memberGroup = new DefaultMemberGroup();
        allMembers.forEach(memberGroup::addMember);
        return () -> Set.of(memberGroup);
    }
}
