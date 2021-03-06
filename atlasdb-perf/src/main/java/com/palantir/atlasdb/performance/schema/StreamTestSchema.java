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
package com.palantir.atlasdb.performance.schema;

import java.io.File;

import com.palantir.atlasdb.keyvalue.api.Namespace;
import com.palantir.atlasdb.schema.AtlasSchema;
import com.palantir.atlasdb.schema.stream.StreamStoreDefinitionBuilder;
import com.palantir.atlasdb.table.description.Schema;
import com.palantir.atlasdb.table.description.TableDefinition;
import com.palantir.atlasdb.table.description.ValueType;

public final class StreamTestSchema implements AtlasSchema {
    private static final Schema STREAM_TEST_SCHEMA = generateSchema();

    private StreamTestSchema() {
        // Uninstantiable
    }

    private static Schema generateSchema() {
        Schema schema = new Schema("StreamTest",
                StreamTestSchema.class.getPackage().getName() + ".generated",
                Namespace.DEFAULT_NAMESPACE);

        schema.addTableDefinition("blobs", new TableDefinition() {
            {
                javaTableName("KeyValue");

                rangeScanAllowed();

                rowName();
                rowComponent("key", ValueType.STRING);

                columns();
                column("streamId", "s", ValueType.VAR_LONG);
            }
        });

        schema.addStreamStoreDefinition(new StreamStoreDefinitionBuilder("blob", "Value", ValueType.VAR_LONG)
                .inMemoryThreshold(1024 * 1024)
                .build());

        return schema;
    }

    public static Schema getSchema() {
        return STREAM_TEST_SCHEMA;
    }

    public static void main(String[]  args) throws Exception {
        STREAM_TEST_SCHEMA.renderTables(new File("src/main/java"));
    }

    @Override
    public Schema getLatestSchema() {
        return STREAM_TEST_SCHEMA;
    }

    @Override
    public Namespace getNamespace() {
        return Namespace.DEFAULT_NAMESPACE;
    }
}
