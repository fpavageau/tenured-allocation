/*
 * Copyright 2013 Frank Pavageau
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.github.fpavageau;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Frank Pavageau
 */
public class TenuredAllocation {
    private final MemoryPoolMXBean eden = getMemoryPool("Eden");
    private final MemoryPoolMXBean tenured = getMemoryPool("Old");
    private final GarbageCollectorMXBean[] edenCollectors = getGarbageCollectors(eden);
    private final Map<String, Long> gcCounts = new HashMap<>();


    public static void main(String[] args) {
        int size = 64 * 1024;
        if (args.length > 0) {
            size = Integer.parseInt(args[0]);
        }
        new TenuredAllocation().run(size);
    }


    public TenuredAllocation() {
        System.out.println("Tracking memory usage with " + eden.getName() + " and " + tenured.getName());
    }


    public void run(int size) {
        updateGcCounts(); // Pre-allocate the map

        while (true) {
            boolean ok = size < eden.getUsage().getCommitted();

            tryAllocateBytes(size, 5);

            if (ok) {
                size *= 2;
            } else {
                break;
            }
        }

        System.out.println("Allocation size (" + size + ") greater than Eden capacity (" +
                           eden.getUsage().getCommitted() + ")");
    }


    private void tryAllocateBytes(int size, int retries) {
        for (int i = retries; i > 0; i--) { // Retry several times in case a GC happens in the middle of the test
            long tenuredUsed = tenured.getUsage().getUsed();
            updateGcCounts();

            allocateBytes(size);

            if (compareGcCounts()) {
                printResult(size, tenuredUsed);
                return; // No need to try again
            } else if (i == 1) {
                System.err.println("Can't allocate " + size + " bytes without triggering a GC");
            }
        }
    }


    private static void allocateBytes(int size) {
        byte[] bytes = new byte[size];
        // Do something with those bytes, so the allocation is not eliminated by the JIT
        int sum = 0;
        for (byte b : bytes) {
            sum += b;
        }
        if (sum != 0) {
            throw new IllegalStateException("Unexpected sum: " + sum);
        }
    }


    /**
     * Updates the current GC state (how many times each collector has run).
     */
    private void updateGcCounts() {
        for (GarbageCollectorMXBean gc : edenCollectors) {
            gcCounts.put(gc.getName(), gc.getCollectionCount());
        }
    }


    /**
     * Checks if a GC has occurred.
     *
     * @return true if no GC occurred, false otherwise
     */
    private boolean compareGcCounts() {
        for (GarbageCollectorMXBean gc : edenCollectors) {
            if (gc.getCollectionCount() != gcCounts.get(gc.getName())) {
                System.out.println("GC in " + gc.getName());
                return false;
            }
        }
        return true;
    }


    private void printResult(int size, long tenuredUsed) {
        if (tenured.getUsage().getUsed() != tenuredUsed) {
            System.out.println("Direct allocation in Tenured: " + size + " (Eden capacity: " +
                               eden.getUsage().getCommitted() + ")");
        } else {
            System.out.println("Allocation in Eden: " + size + " (capacity: " + eden.getUsage().getCommitted() + ")");
        }
    }


    private static MemoryPoolMXBean getMemoryPool(String name) {
        MemoryPoolMXBean pool = null;
        for (MemoryPoolMXBean bean : ManagementFactory.getMemoryPoolMXBeans()) {
            if (bean.getName().contains(name)) {
                pool = bean;
                break;
            }
        }
        if (pool == null) {
            throw new IllegalStateException("No memory pool matching " + name);
        }
        return pool;
    }


    private static GarbageCollectorMXBean[] getGarbageCollectors(MemoryPoolMXBean pool) {
        Collection<GarbageCollectorMXBean> gcs = new ArrayList<>();
        Set<String> memManagers = new HashSet<>(Arrays.asList(pool.getMemoryManagerNames()));
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (memManagers.contains(gc.getName())) {
                gcs.add(gc);
            }
        }
        return gcs.toArray(new GarbageCollectorMXBean[gcs.size()]);
    }
}
