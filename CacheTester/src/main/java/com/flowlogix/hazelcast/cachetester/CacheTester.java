package com.flowlogix.hazelcast.cachetester;

import com.hazelcast.cache.HazelcastCachingProvider;
import com.hazelcast.cluster.Address;
import com.hazelcast.cluster.ClusterState;
import com.hazelcast.cluster.Member;
import com.hazelcast.collection.ISet;
import com.hazelcast.config.Config;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.cp.FencedLockConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceNotActiveException;
import com.hazelcast.cp.CPSubsystemManagementService;
import com.hazelcast.cp.event.CPGroupAvailabilityEvent;
import com.hazelcast.cp.event.CPGroupAvailabilityListener;
import com.hazelcast.cp.event.CPMembershipEvent;
import com.hazelcast.cp.event.CPMembershipListener;
import com.hazelcast.cp.lock.FencedLock;
import com.hazelcast.map.IMap;
import com.hazelcast.spi.discovery.DiscoveryNode;
import java.io.Serializable;
import java.net.InetAddress;
import java.time.Duration;
import java.util.Iterator;
import java.util.Objects;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.StreamSupport;
import javax.cache.Cache;
import javax.cache.Cache.Entry;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.spi.CachingProvider;
import static com.hazelcast.cp.CPGroup.METADATA_CP_GROUP_NAME;
import static com.hazelcast.spi.properties.ClusterProperty.DISCOVERY_SPI_ENABLED;
import static com.hazelcast.spi.properties.ClusterProperty.HEARTBEAT_INTERVAL_SECONDS;
import static com.hazelcast.spi.properties.ClusterProperty.JCACHE_PROVIDER_TYPE;
import static com.hazelcast.spi.properties.ClusterProperty.MAX_JOIN_SECONDS;
import static com.hazelcast.spi.properties.ClusterProperty.MAX_NO_HEARTBEAT_SECONDS;
import static com.hazelcast.spi.properties.ClusterProperty.MERGE_FIRST_RUN_DELAY_SECONDS;
import static com.hazelcast.spi.properties.ClusterProperty.MERGE_NEXT_RUN_DELAY_SECONDS;
import static com.hazelcast.spi.properties.ClusterProperty.SOCKET_CONNECT_TIMEOUT_SECONDS;
import static com.hazelcast.spi.properties.ClusterProperty.WAIT_SECONDS_BEFORE_JOIN;

/**
 *
 * @author lprimak
 */
public class CacheTester {
    static final int hzBasePort = Integer.getInteger("hz.base.port", 5710);
    static final String availabilityStructureName = "hz/test/cp/availmap";
    private CacheManager cacheManager;
    private static HazelcastInstance hzInst;
    private static final Lock resetLock = new ReentrantLock();

    public static void main(String[] args) {
        CacheTester tester = new CacheTester();
        try {
            tester.setup();
            tester.run();
        } finally {
            tester.teardown();
        }
    }

    private Config getConfig() {
        Config config = new Config();

        if (Boolean.getBoolean("hz.logging")) {
            Logger rootLogger = LogManager.getLogManager().getLogger("");
            rootLogger.setLevel(Level.FINE);
            for (Handler h : rootLogger.getHandlers()) {
                h.setLevel(Level.FINE);
            }
        }
//        config.setProperty(WAIT_SECONDS_BEFORE_JOIN_ASYNC.getName(), Boolean.FALSE.toString());
        config.setProperty(MAX_NO_HEARTBEAT_SECONDS.getName(), "5");
        config.setProperty(SOCKET_CONNECT_TIMEOUT_SECONDS.getName(), "5");
        config.setProperty(HEARTBEAT_INTERVAL_SECONDS.getName(), "1");
        config.setProperty(MERGE_FIRST_RUN_DELAY_SECONDS.getName(), "15");
        config.setProperty(MERGE_NEXT_RUN_DELAY_SECONDS.getName(), "15");

        config.setProperty(WAIT_SECONDS_BEFORE_JOIN.getName(), "1");
        config.setProperty(MAX_JOIN_SECONDS.getName(), "5");

        config.getCPSubsystemConfig().addLockConfig(new FencedLockConfig("my/lock").disableReentrancy());
        config.getCPSubsystemConfig().setSessionHeartbeatIntervalSeconds(1);
        if (Boolean.getBoolean("hz.raft")) {
            if (Boolean.getBoolean("hz.raft.leader.heartbeat")) {
                config.getCPSubsystemConfig()
                        .getRaftAlgorithmConfig()
                        .setLeaderHeartbeatPeriodInMillis(Duration.ofSeconds(1).toMillis());
            }
            config.getCPSubsystemConfig()
                    .setSessionTimeToLiveSeconds(5)
//                    .setMissingCPMemberAutoRemovalSeconds(10)
                    .setGroupSize(Integer.getInteger("hz.group.size",3))
                    .setCPMemberCount(Integer.getInteger("hz.member.count",3));
        }

        NetworkConfig networkConfig = config.getNetworkConfig();
        networkConfig.getJoin().getMulticastConfig().setEnabled(false);
        networkConfig.setPublicAddress(InetAddress.getLoopbackAddress().getHostAddress())
                .setPort(Integer.getInteger("hz.port", 5710)).setPortAutoIncrement(false);

        if (Boolean.getBoolean("hz.discovery.spi")) {
            config.setProperty(DISCOVERY_SPI_ENABLED.getName(), "true");
            networkConfig.getJoin().getDiscoveryConfig().setDiscoveryServiceProvider(MyDiscoveryService::new);
        } else {
            var tcpIpConfig = networkConfig.getJoin().getTcpIpConfig();
            tcpIpConfig.setEnabled(true);
            var ds = new MyDiscoveryService(null);
            ds.start();
            StreamSupport.stream(ds.discoverNodes().spliterator(), false)
                    .map(DiscoveryNode::getPrivateAddress)
                    .forEach(address -> tcpIpConfig.addMember(String.format("%s:%d",
                            address.getHost(), address.getPort())));
            ds.destroy();
        }
        return config;
    }

    private void setup() {
        System.setProperty(JCACHE_PROVIDER_TYPE.getName(), "server");
        var config = getConfig();
        hzInst = Hazelcast.newHazelcastInstance(config);
        if (!config.isLiteMember() && config.getCPSubsystemConfig().getCPMemberCount() > 0
                && Boolean.parseBoolean(System.getProperty("hz.autoupgrade", "true"))) {
            CPSubsystemManagementService managementService = hzInst.getCPSubsystem().getCPSubsystemManagementService();
            if (managementService.isDiscoveryCompleted()) {
                Executors.newSingleThreadExecutor().submit(() -> {
                    try {
                        int maxJoinWait = Integer.parseInt(config.getProperty(MAX_JOIN_SECONDS.getName())) * 10 + 10;
                        for (int ii = 0; ii < maxJoinWait; ++ii) {
                            if (hzInst.getCluster().getClusterState() == ClusterState.ACTIVE) {
                                break;
                            }
                            TimeUnit.MILLISECONDS.sleep(100);
                        }
                        ISet<Member> cpMembersToReset = hzInst.getSet(availabilityStructureName);
                        if (!cpMembersToReset.isEmpty()) {
                            System.err.println("**** Client: CP Reset schedule is detected ...");
                            var fn = (Serializable & Runnable) () -> {
                                resetLock.lock();
                                try {
                                    try {
                                        hzInst.getCPSubsystem().getCPSubsystemManagementService()
                                                .getCPGroup(METADATA_CP_GROUP_NAME).toCompletableFuture().get(1, TimeUnit.SECONDS);
                                        System.err.println("Not performing CP Reset: Metadata is healthy");
                                    } catch (CompletionException | InterruptedException | ExecutionException |
                                             TimeoutException e) {
                                        System.err.println("Performing CP Reset ...");
                                        hzInst.getCPSubsystem().getCPSubsystemManagementService().reset().toCompletableFuture().join();
                                    }
                                    hzInst.getSet(availabilityStructureName).clear();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                finally {
                                    resetLock.unlock();
                                }
                            };
                            hzInst.getExecutorService(availabilityStructureName).executeOnMembers(fn, cpMembersToReset);
                        }
                        var localMember = hzInst.getCluster().getLocalMember();
                        IMap<Address, UUID> map = hzInst.getMap(availabilityStructureName);
                        UUID uuid = map.get(localMember.getAddress());
                        if (uuid != null || managementService.getCPMembers().toCompletableFuture().join()
                                .size() < config.getCPSubsystemConfig().getCPMemberCount()) {
                            if (uuid != null) {
                                try {
                                    managementService.removeCPMember(uuid).toCompletableFuture().join();
                                } catch (CompletionException e) {
                                }
                                map.remove(localMember.getAddress());
                            }
                            System.out.println("Promoting to CP Member");
                            managementService.promoteToCPMember();
                        }
                    } catch (HazelcastInstanceNotActiveException e) {
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                });
            }
        }
        hzInst.getCPSubsystem().addGroupAvailabilityListener(new CPGroupAvailabilityListener() {
            @Override
            public void availabilityDecreased(CPGroupAvailabilityEvent cpGroupAvailabilityEvent) {
                System.err.println("**** availabilityDecreased: " + cpGroupAvailabilityEvent);
                if (cpGroupAvailabilityEvent.isMetadataGroup()) {
                    var map = hzInst.getMap(availabilityStructureName);
                    cpGroupAvailabilityEvent.getUnavailableMembers().forEach(member -> {
                        map.put(member.getAddress(), member.getUuid());
                    });
                }
            }

            @Override
            public void majorityLost(CPGroupAvailabilityEvent cpGroupAvailabilityEvent) {
                System.err.println("**** majorityLost: " + cpGroupAvailabilityEvent);
                if (cpGroupAvailabilityEvent.isMetadataGroup()) {
                    hzInst.getSet(availabilityStructureName).add(hzInst.getCluster().getLocalMember());
                    System.err.println("**** Scheduled CP Reset (majorityLost) ***");
                }
            }
        });
        hzInst.getCPSubsystem().addMembershipListener(new CPMembershipListener() {
            @Override
            public void memberAdded(CPMembershipEvent cpMembershipEvent) {
                System.err.println("**** memberAdded: " + cpMembershipEvent);
                hzInst.getMap(availabilityStructureName).remove(cpMembershipEvent.getMember().getAddress());
            }

            @Override
            public void memberRemoved(CPMembershipEvent cpMembershipEvent) {
                System.err.println("**** memberRemoved: " + cpMembershipEvent);
                try {
                    if (!cpMembershipEvent.getMember().equals(hzInst.getCPSubsystem()
                            .getCPSubsystemManagementService().getLocalCPMember())) {
                        hzInst.getCPSubsystem().getCPSubsystemManagementService()
                                .getCPGroup(METADATA_CP_GROUP_NAME).toCompletableFuture().get(1, TimeUnit.SECONDS);
                    }
                } catch (CompletionException | InterruptedException | ExecutionException | TimeoutException e) {
                    if (e.getCause() instanceof IllegalStateException) {
                        hzInst.getSet(availabilityStructureName).add(hzInst.getCluster().getLocalMember());
                        System.err.println("**** Scheduled CP Reset ***");
                    }
                }
            }
        });
        CachingProvider provider = Caching.getCachingProvider();
        cacheManager = provider.getCacheManager(null, null, HazelcastCachingProvider.propertiesByInstanceName(hzInst.getName()));
    }

    private void teardown() {
        Hazelcast.shutdownAll();
    }

    private void help() {
        System.out.println("<c> to create cache, <d> to destroy, <p> to print, <l> to lock, <u> to unlock");
        System.out.println("blank to exit <h> for help, anything else to put in cache ...");
    }

    private void run() {
        help();
        try(Scanner scanner = new Scanner(System.in)) {
            outer:
            while (true) {
                try {
                    String line = scanner.nextLine();
                    switch (line) {
                        case "c":
                            cacheManager.createCache("myCache",
                                    new MutableConfiguration<String, String>().setTypes(String.class, String.class));
                            break;
                        case "d":
                            cacheManager.destroyCache("myCache");
                            break;
                        case "":
                            break outer;
                        case "p": {
                            Cache<String, String> cache = cacheManager.getCache("myCache", String.class, String.class);
                            checkCreated(cache);
                            System.out.println("cache: " + cache.get("hello"));
                            // test the iterator to know it doesn't fail
                            Iterator<Entry<String, String>> iterator = cache.iterator();
                            while (iterator.hasNext()) {
                                iterator.next().getKey();
                            }
                            FencedLock lock = getLock();
                            System.out.printf("Lock is %s\n", lock.isLocked() ? "Locked" : "Unlocked");
                            break;
                        }
                        case "l": {
                            FencedLock lock = getLock();
                            System.out.printf("Locking ... was %s\n", lock.isLocked() ? "Locked" : "Unlocked");
                            lock.lock();
                            System.out.println("Locked");
                            break;
                        }
                        case "u": {
                            FencedLock lock = getLock();
                            System.out.printf("Unlocking ... was %s\n", lock.isLocked() ? "Locked" : "Unlocked");
                            if (lock.isLockedByCurrentThread()) {
                                lock.unlock();
                            }
                            if (!lock.isLocked()) {
                                System.out.println("Unlocked");
                            }
                            break;
                        }
                        case "h": {
                            help();
                            break;
                        }
                        default: {
                            Cache<String, String> cache = cacheManager.getCache("myCache", String.class, String.class);
                            checkCreated(cache);
                            cache.put("hello", line);
                            System.out.println("cache updated");
                            break;
                        }
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
    }

    private void checkCreated(Cache<String, String> cache) {
        Objects.requireNonNull(cache, "Cache not created");
    }

    private FencedLock getLock() {
        return hzInst.getCPSubsystem().getLock("my/lock");
    }
}
