/**
 * Copyright 2016 Palantir Technologies
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
package com.palantir.atlasdb.calcite;

import java.util.Map;
import java.util.stream.Collectors;

import org.apache.calcite.schema.Table;
import org.apache.calcite.schema.impl.AbstractSchema;

import com.palantir.atlasdb.api.AtlasDbService;

public class AtlasSchema extends AbstractSchema {
    private final AtlasDbService service;

    public AtlasSchema(AtlasDbService service) {
        this.service = service;
    }

    @Override
    protected Map<String, Table> getTableMap() {
        return service.getAllTableNames().stream()
                .collect(Collectors.toMap(
                        name -> name,
                        name -> AtlasTable.create(service, name)
                ));
    }
}
