/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.flowlogix.hazelcast.cachetester;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 *
 * @author lprimak
 */
public class SignalTest {
    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    public void testSignaller() throws InterruptedException, ExecutionException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch latch = new CountDownLatch(1);
        executor.submit(() -> {
            latch.countDown();
        });
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));

        latch.await(5, TimeUnit.SECONDS);
    }
}
