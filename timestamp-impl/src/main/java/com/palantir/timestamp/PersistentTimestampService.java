/**
 * Copyright 2015 Palantir Technologies
 * <p>
 * Licensed under the BSD-3 License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://opensource.org/licenses/BSD-3-Clause
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.timestamp;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.concurrent.ThreadSafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.palantir.common.concurrent.PTExecutors;
import com.palantir.common.remoting.ServiceNotAvailableException;
import com.palantir.common.time.Clock;
import com.palantir.common.time.SystemClock;
import com.palantir.exception.PalantirInterruptedException;

@ThreadSafe
public class PersistentTimestampService implements TimestampService {
    static final long ALLOCATION_BUFFER_SIZE = 1000 * 1000;
    private static final Logger log = LoggerFactory.getLogger(PersistentTimestampService.class);
    private static final int MAX_REQUEST_RANGE_SIZE = 10 * 1000;
    private static final int ONE_MINUTE_IN_MILLIS = 60000;

    private final PersistentUpperLimit persistentUpperLimit;

    private final AtomicLong lastReturnedTimestamp;

    private final ExecutorService executor;

    private Clock clock;
    private long lastAllocatedTime;
    private volatile Throwable previousAllocationFailure = null;

    protected PersistentTimestampService(PersistentUpperLimit persistentUpperLimit, Clock clock) {
        this.persistentUpperLimit = persistentUpperLimit;
        this.clock = clock;

        executor = PTExecutors.newSingleThreadExecutor(PTExecutors.newThreadFactory("Timestamp allocator", Thread.NORM_PRIORITY, true));
        lastAllocatedTime = clock.getTimeMillis();
        lastReturnedTimestamp = new AtomicLong(persistentUpperLimit.get());
    }

    public static PersistentTimestampService create(TimestampBoundStore tbs) {
        return new PersistentTimestampService(new PersistentUpperLimit(tbs, new SystemClock()), new SystemClock());
    }

    @Override
    public long getFreshTimestamp() {
        return getFreshTimestamps(1).getLowerBound();
    }

    @Override
    public synchronized TimestampRange getFreshTimestamps(int numTimestampsRequested) {
        int numTimestampsToHandOut = cleanUpTimestampRequest(numTimestampsRequested);
        long newTimestamp = lastReturnedTimestamp.get() + numTimestampsToHandOut;

        ensureWeHaveEnoughTimestampsToHandOut(newTimestamp);
        TimestampRange handedOut = handOut(newTimestamp);
        asynchronouslyTopUpTimestampPool();
        return handedOut;
    }

    /**
     * Fast forwards the timestamp to the specified one so that no one can be served fresh timestamps prior
     * to it from now on.
     *
     * Sets the upper limit in the TimestampBoundStore as well as increases the minimum timestamp that can
     * be allocated from this instantiation of the TimestampService moving forward.
     *
     * The caller of this is responsible for not using any of the fresh timestamps previously served to it,
     * and must call getFreshTimestamps() to ensure it is using timestamps after the fastforward point.
     *
     * @param timestamp
     */
    public synchronized void fastForwardTimestamp(long timestamp) {
        long upperLimit = timestamp + ALLOCATION_BUFFER_SIZE;
        persistentUpperLimit.increaseToAtLeast(upperLimit);

        // Prevent ourselves from serving any of the bad (read: pre-fastForward) timestamps
        setToAtLeast(lastReturnedTimestamp, timestamp);
    }

    private static void setToAtLeast(AtomicLong toAdvance, long val) {
        while (true) {
            long oldUpper = toAdvance.get();
            if (val <= oldUpper || toAdvance.compareAndSet(oldUpper, val)) {
                return;
            }
        }
    }

    private int cleanUpTimestampRequest(int numTimestampsRequested) {
        Preconditions.checkArgument(numTimestampsRequested > 0,
                "Number of timestamps requested must be greater than zero, was %s",
                numTimestampsRequested);

        return Math.min(numTimestampsRequested, MAX_REQUEST_RANGE_SIZE);
    }

    private void asynchronouslyTopUpTimestampPool() {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                topUpTimestampPool();
            }
        });
    }

    private TimestampRange handOut(long newTimestamp) {
        lastReturnedTimestamp.set(newTimestamp);
        return TimestampRange.createInclusiveRange(lastReturnedTimestamp.get() + 1, newTimestamp);
    }

    private synchronized void ensureWeHaveEnoughTimestampsToHandOut(long newTimestamp) {
        while(persistentUpperLimit.get() < newTimestamp) {
            allocateMoreTimestamps();
        }
    }

    private void handleAllocationFailure(Throwable failure) {
        if (failure instanceof MultipleRunningTimestampServiceError) {
            throw new ServiceNotAvailableException("This server is no longer valid because another is running.", failure);
        }

        if (failure != null) {
            throw new RuntimeException("failed to allocate more timestamps", failure);
        }

        if (Thread.interrupted()) {
            Thread.currentThread().interrupt();
            throw new PalantirInterruptedException("Interrupted while waiting for timestamp allocation.");
        }

        if (previousAllocationFailure != null
                && failure.getClass().equals(previousAllocationFailure.getClass())) {
            // QA-75825: don't keep logging error if we keep failing to allocate.
            log.info("Throwable while allocating timestamps.", failure);
        } else {
            log.error("Throwable while allocating timestamps.", failure);
        }

        previousAllocationFailure = failure;
    }

    private synchronized void topUpTimestampPool() {
        if (shouldTopUpTimestampPool()) {
            allocateMoreTimestamps();
        }
    }

    private synchronized void allocateMoreTimestamps() {
        verifyThisIsTheOnlyRunningServer();
        try {
            long newLimit = lastReturnedTimestamp.get() + ALLOCATION_BUFFER_SIZE;
            persistentUpperLimit.increaseToAtLeast(newLimit);
            lastAllocatedTime = clock.getTimeMillis();
        } catch(Throwable e) {
            handleAllocationFailure(e);
        }
    }

    private void verifyThisIsTheOnlyRunningServer() {
        if (previousAllocationFailure instanceof MultipleRunningTimestampServiceError) {
            throw new ServiceNotAvailableException("This server is no longer valid because another is running.", previousAllocationFailure);
        }
    }

    private boolean shouldTopUpTimestampPool() {
        return exceededHalfOfBuffer() || haveNotAllocatedForOneMinute();
    }

    private boolean exceededHalfOfBuffer() {
        long remainingTimestamps = persistentUpperLimit.get() - lastReturnedTimestamp.get();
        return remainingTimestamps <= ALLOCATION_BUFFER_SIZE / 2;
    }

    private boolean haveNotAllocatedForOneMinute() {
        return lastAllocatedTime + ONE_MINUTE_IN_MILLIS < clock.getTimeMillis();
    }
}
