/*
 * Copyright 2020 Hazelcast, Inc..
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.flowlogix.hazelcast.cachetester;

import com.hazelcast.cache.HazelcastCachingProvider;
import com.hazelcast.config.Config;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.cp.FencedLockConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cp.lock.FencedLock;
import java.util.Iterator;
import java.util.Objects;
import java.util.Scanner;
import javax.cache.Cache;
import javax.cache.Cache.Entry;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.spi.CachingProvider;
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
    private CacheManager cacheManager;
    private HazelcastInstance hzInst;

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
            config.getCPSubsystemConfig()
                    .setSessionTimeToLiveSeconds(5)
                    .setGroupSize(Integer.getInteger("hz.group.size",3))
                    .setCPMemberCount(Integer.getInteger("hz.member.count",3));
        }

        NetworkConfig networkConfig = config.getNetworkConfig();
        networkConfig.getJoin().getMulticastConfig().setEnabled(false);

        config.setProperty(DISCOVERY_SPI_ENABLED.getName(), "true");
        networkConfig.getJoin().getDiscoveryConfig().setDiscoveryServiceProvider(MyDiscoveryService::new);
        networkConfig.setPublicAddress("127.0.0.1")
                .setPort(Integer.getInteger("hz.port", 5710))
                .setPortAutoIncrement(false);
        return config;
    }

    private void setup() {
        System.setProperty(JCACHE_PROVIDER_TYPE.getName(), "server");
        hzInst = Hazelcast.newHazelcastInstance(getConfig());
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
