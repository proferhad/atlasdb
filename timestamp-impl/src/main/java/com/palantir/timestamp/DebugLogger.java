/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
 *
 * Licensed under the BSD-3 License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.timestamp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * This is a logger intended for use tracking down problems arising from
 * https://github.com/palantir/atlasdb/issues/1000.  To activate this logging
 * please follow instructions on
 * http://palantir.github.io/atlasdb/html/configuration/logging.html#debug-logging-for-multiple-timestamp-services-error
 */
@SuppressFBWarnings("SLF4J_LOGGER_SHOULD_BE_PRIVATE")
public final class DebugLogger {
    // TODO(nziebart): move remaining logging calls into this class
    public static final Logger logger = LoggerFactory.getLogger(DebugLogger.class);

    private DebugLogger() {
        // Logging utility class
    }

    public static void handedOutTimestamps(TimestampRange range) {
        long count = range.getUpperBound() - range.getLowerBound() + 1L;
        logger.trace("Handing out {} timestamps, taking us to {}.", count, range.getUpperBound());
    }

    public static void createdPersistentTimestamp() {
        logger.info("Creating PersistentTimestamp object on thread {}."
                        + " If you are running embedded AtlasDB, this should only happen once."
                        + " If you are using Timelock, this should happen once per client per leadership election",
                Thread.currentThread().getName());
    }

    public static void willStoreNewUpperLimit(long newLimit) {
        logger.trace("storing new upper limit: {}.", newLimit);
    }

    public static void didStoreNewUpperLimit(long newLimit) {
        logger.trace("Stored; upper limit is now {}.", newLimit);
    }

}
