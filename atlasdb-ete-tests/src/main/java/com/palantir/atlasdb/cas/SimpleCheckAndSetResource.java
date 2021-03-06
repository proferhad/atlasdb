/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
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
package com.palantir.atlasdb.cas;

import com.google.common.base.Optional;

public class SimpleCheckAndSetResource implements CheckAndSetResource {
    private CheckAndSetClient checkAndSetClient;

    public SimpleCheckAndSetResource(CheckAndSetClient checkAndSetClient) {
        this.checkAndSetClient = checkAndSetClient;
    }

    @Override
    public void set(Optional<Long> value) {
        checkAndSetClient.set(value);
    }

    @Override
    public Long get() {
        return checkAndSetClient.get().orNull();
    }

    @Override
    public boolean checkAndSet(CheckAndSetValueUpdate valueUpdate) {
        return checkAndSetClient.checkAndSet(valueUpdate.getOldValue(), valueUpdate.getNewValue());
    }
}
