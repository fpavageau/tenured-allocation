Tenured allocation test
======

Simple test case to try and find when allocations happen directly in the tenured generation of the JVM, instead of the
young generation. It should work with at least HotSpot and OpenJDK.

## What does it do?

The program allocates arrays of bytes, with an exponentially growing size, and tries to determine when the allocation
happens in Tenured by comparing the size of the generation before and after the allocation. It discards the runs where
a garbage collection happened during the allocation, with a retry policy.

## How to compile

The project uses Maven 2+, so just do:

        mvn package

## How to run

        java [options] -jar target/tenured-allocation-1.0-SNAPSHOT.jar [starting size]

The default starting size for byte allocation is 64K.

For example, specifying the size of the young and tenured generations:

        $ java -Xmn16m -Xmx64m -jar target/tenured-allocation-1.0-SNAPSHOT.jar
        Tracking memory usage with PS Old Gen
        Allocation in Eden: 65536 (capacity: 12582912)
        Allocation in Eden: 131072 (capacity: 12582912)
        Allocation in Eden: 262144 (capacity: 12582912)
        Allocation in Eden: 524288 (capacity: 12582912)
        Allocation in Eden: 1048576 (capacity: 12582912)
        Allocation in Eden: 2097152 (capacity: 12582912)
        Allocation in Eden: 4194304 (capacity: 12582912)
        Direct allocation in Tenured: 8388608 (Eden capacity: 12582912)
        Direct allocation in Tenured: 16777216 (Eden capacity: 12582912)
        Allocation size (16777216) greater than Eden capacity (12582912)

## Results

Obviously, the choice of GC algorithm should influence the result, but how much? All tests here are done using an Oracle
JDK 7 (32-bit).

### Using the throughput collector

        $ java -Xmn8m -Xmx32m -jar target/tenured-allocation-1.0-SNAPSHOT.jar
        Tracking memory usage with PS Eden Space and PS Old Gen
        Allocation in Eden: 65536 (capacity: 6291456)
        Allocation in Eden: 131072 (capacity: 6291456)
        Allocation in Eden: 262144 (capacity: 6291456)
        Allocation in Eden: 524288 (capacity: 6291456)
        Allocation in Eden: 1048576 (capacity: 6291456)
        Allocation in Eden: 2097152 (capacity: 6291456)
        Direct allocation in Tenured: 4194304 (Eden capacity: 6291456)
        Direct allocation in Tenured: 8388608 (Eden capacity: 6291456)
        Allocation size (8388608) greater than Eden capacity (6291456)

The direct allocation happens when it's larger than half the capacity of Eden (and obviously when it's larger). With a
slightly larger young generation and a fixed survivor ratio, we have:

        $ java -Xmn11m -Xmx32m -jar target/tenured-allocation-1.0-SNAPSHOT.jar
        Tracking memory usage with PS Eden Space and PS Old Gen
        Allocation in Eden: 65536 (capacity: 8650752)
        Allocation in Eden: 131072 (capacity: 8650752)
        Allocation in Eden: 262144 (capacity: 8650752)
        Allocation in Eden: 524288 (capacity: 8650752)
        Allocation in Eden: 1048576 (capacity: 8650752)
        Allocation in Eden: 2097152 (capacity: 8650752)
        GC in PS Scavenge
        GC in PS Scavenge
        Allocation in Eden: 4194304 (capacity: 8650752)
        Direct allocation in Tenured: 8388608 (Eden capacity: 8650752)
        GC in PS Scavenge
        GC in PS Scavenge
        GC in PS Scavenge
        GC in PS Scavenge
        GC in PS Scavenge
        Can't allocate 16777216 bytes without triggering a GC
        Allocation size (16777216) greater than Eden capacity (10485760)

### Using CMS

        $ java -Xmn8m -Xmx32m -XX:+UseConcMarkSweepGC -jar target/tenured-allocation-1.0-SNAPSHOT.jar
        Tracking memory usage with Par Eden Space and CMS Old Gen
        Allocation in Eden: 65536 (capacity: 6815744)
        Allocation in Eden: 131072 (capacity: 6815744)
        Allocation in Eden: 262144 (capacity: 6815744)
        Allocation in Eden: 524288 (capacity: 6815744)
        Allocation in Eden: 1048576 (capacity: 6815744)
        Allocation in Eden: 2097152 (capacity: 6815744)
        GC in ParNew
        GC in ParNew
        GC in ParNew
        GC in ParNew
        GC in ParNew
        Can't allocate 4194304 bytes without triggering a GC
        Direct allocation in Tenured: 8388608 (Eden capacity: 6815744)
        Allocation size (8388608) greater than Eden capacity (6815744)

Even though the capacity of Eden isn't reached, a GC always occurs for the size at which the throughput collector
switched to direct allocation.

### Using G1

        $ java -Xmn8m -Xmx32m -XX:+UseG1GC -jar target/tenured-allocation-1.0-SNAPSHOT.jar
        Tracking memory usage with G1 Eden Space and G1 Old Gen
        Allocation in Eden: 65536 (capacity: 9437184)
        Allocation in Eden: 131072 (capacity: 9437184)
        Allocation in Eden: 262144 (capacity: 9437184)
        Allocation in Eden: 524288 (capacity: 9437184)
        Direct allocation in Tenured: 1048576 (Eden capacity: 9437184)
        Direct allocation in Tenured: 2097152 (Eden capacity: 9437184)
        Direct allocation in Tenured: 4194304 (Eden capacity: 9437184)
        GC in G1 Young Generation
        Direct allocation in Tenured: 8388608 (Eden capacity: 8388608)
        GC in G1 Young Generation
        GC in G1 Young Generation
        GC in G1 Young Generation
        GC in G1 Young Generation
        GC in G1 Young Generation
        Can't allocate 16777216 bytes without triggering a GC
        Allocation size (16777216) greater than Eden capacity (4194304)

With G1, the threshold might be related to the size of the regions. The default seems to be 1M, as with 2M we get:

        $ java -Xmn8m -Xmx32m -XX:+UseG1GC -XX:G1HeapRegionSize=2m -jar target/tenured-allocation-1.0-SNAPSHOT.jar
        Tracking memory usage with G1 Eden Space and G1 Old Gen
        Allocation in Eden: 65536 (capacity: 10485760)
        Allocation in Eden: 131072 (capacity: 10485760)
        Allocation in Eden: 262144 (capacity: 10485760)
        Allocation in Eden: 524288 (capacity: 10485760)
        Allocation in Eden: 1048576 (capacity: 10485760)
        Direct allocation in Tenured: 2097152 (Eden capacity: 10485760)
        Direct allocation in Tenured: 4194304 (Eden capacity: 10485760)
        GC in G1 Young Generation
        Direct allocation in Tenured: 8388608 (Eden capacity: 8388608)
        GC in G1 Young Generation
        GC in G1 Young Generation
        GC in G1 Young Generation
        GC in G1 Young Generation
        GC in G1 Young Generation
        Can't allocate 16777216 bytes without triggering a GC
        Allocation size (16777216) greater than Eden capacity (6291456)

So the behavior is similar to what happens with the throughput collector, except that instead of the threshold being
half the size of eden, it's half the size of a region.

------

Licensed under the Apache License, Version 2.0
