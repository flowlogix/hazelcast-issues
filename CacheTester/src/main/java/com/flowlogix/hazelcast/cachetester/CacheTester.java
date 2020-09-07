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
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.TcpIpConfig;
import com.hazelcast.core.Hazelcast;
import java.util.Objects;
import java.util.Scanner;
import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.spi.CachingProvider;

/**
 *
 * @author lprimak
 */
public class CacheTester {
    private CacheManager cacheManager;

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
        NetworkConfig networkConfig = config.getNetworkConfig();
        networkConfig.setJoin(new JoinConfig().setTcpIpConfig(new TcpIpConfig().setEnabled(true)
                .addMember("10.0.1.3:5701").addMember("10.0.1.3:5702").setConnectionTimeoutSeconds(1)));
        return config;
    }

    private void setup() {
        System.setProperty("hazelcast.jcache.provider.type", "server");
        String instanceName = Hazelcast.newHazelcastInstance(getConfig()).getName();
        CachingProvider provider = Caching.getCachingProvider();
        cacheManager = provider.getCacheManager(null, null, HazelcastCachingProvider.propertiesByInstanceName(instanceName));
    }

    private void teardown() {
        Hazelcast.shutdownAll();
    }

    private void help() {
        System.out.println("<c> to create cache, <d> to destroy, <p> to print");
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
}
