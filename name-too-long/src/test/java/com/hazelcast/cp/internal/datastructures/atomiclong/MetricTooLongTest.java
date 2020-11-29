/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.hazelcast.cp.internal.datastructures.atomiclong;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 *
 * @author lprimak
 */
public class MetricTooLongTest {
    @BeforeAll
    static void setup() {
        Logger.getLogger("").setLevel(Level.WARNING);
        Logger.getLogger("com.hazelcast").setLevel(Level.WARNING);
        Logger.getLogger("com.hazelcast.cp.CPSubsystem").setLevel(Level.SEVERE);
    }

    @Test
    void metricTooLong() throws InterruptedException {
        String name = "Payara/ejb/singleton/66493e0a-ae18-42cc-8b41-e8b2633c09f0_/66493e0a-ae18-42cc-8b41-e8b2633c09f0/ClusteredSingletonInterceptedEJB/count";
        System.out.format("*** Name length: %d, name = %s\n", name.length(), name);
        Hazelcast.newHazelcastInstance(new Config().setProperty("hazelcast.logging.type", "jdk")).getCPSubsystem().getAtomicLong(name).set(5);
        Thread.sleep(5 * 1000);
        Hazelcast.shutdownAll();
    }
}
