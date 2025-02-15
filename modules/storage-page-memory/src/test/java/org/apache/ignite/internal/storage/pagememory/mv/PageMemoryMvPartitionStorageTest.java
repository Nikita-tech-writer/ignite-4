/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.storage.pagememory.mv;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.apache.ignite.configuration.schemas.store.UnknownDataStorageConfigurationSchema;
import org.apache.ignite.configuration.schemas.table.HashIndexConfigurationSchema;
import org.apache.ignite.configuration.schemas.table.TableConfiguration;
import org.apache.ignite.internal.configuration.testframework.ConfigurationExtension;
import org.apache.ignite.internal.configuration.testframework.InjectConfiguration;
import org.apache.ignite.internal.pagememory.configuration.schema.UnsafeMemoryAllocatorConfigurationSchema;
import org.apache.ignite.internal.pagememory.io.PageIoRegistry;
import org.apache.ignite.internal.schema.BinaryRow;
import org.apache.ignite.internal.storage.AbstractMvPartitionStorageTest;
import org.apache.ignite.internal.storage.RowId;
import org.apache.ignite.internal.storage.pagememory.AbstractPageMemoryTableStorage;
import org.apache.ignite.internal.storage.pagememory.PageMemoryStorageEngine;
import org.apache.ignite.internal.storage.pagememory.configuration.schema.PageMemoryDataStorageChange;
import org.apache.ignite.internal.storage.pagememory.configuration.schema.PageMemoryDataStorageConfigurationSchema;
import org.apache.ignite.internal.storage.pagememory.configuration.schema.PageMemoryDataStorageView;
import org.apache.ignite.internal.storage.pagememory.configuration.schema.PageMemoryStorageEngineConfiguration;
import org.apache.ignite.internal.storage.pagememory.configuration.schema.PageMemoryStorageEngineConfigurationSchema;
import org.apache.ignite.internal.testframework.WorkDirectory;
import org.apache.ignite.internal.testframework.WorkDirectoryExtension;
import org.apache.ignite.internal.tx.Timestamp;
import org.apache.ignite.internal.util.Cursor;
import org.apache.ignite.internal.util.IgniteUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ConfigurationExtension.class)
@ExtendWith(WorkDirectoryExtension.class)
class PageMemoryMvPartitionStorageTest extends AbstractMvPartitionStorageTest<PageMemoryMvPartitionStorage> {
    private final PageIoRegistry ioRegistry = new PageIoRegistry();

    {
        ioRegistry.loadFromServiceLoader();
    }

    @InjectConfiguration(polymorphicExtensions = UnsafeMemoryAllocatorConfigurationSchema.class)
    private PageMemoryStorageEngineConfiguration engineConfig;

    @InjectConfiguration(
            name = "table",
            polymorphicExtensions = {
                    HashIndexConfigurationSchema.class,
                    UnknownDataStorageConfigurationSchema.class,
                    PageMemoryDataStorageConfigurationSchema.class
            }
    )
    private TableConfiguration tableCfg;

    private PageMemoryStorageEngine engine;

    private AbstractPageMemoryTableStorage table;

    private int nextPageIndex = 100;

    @WorkDirectory
    private Path workDir;

    @BeforeEach
    void setUp() throws Exception {
        engine = new PageMemoryStorageEngine("test", engineConfig, ioRegistry, workDir, null);

        engine.start();

        tableCfg.change(c -> c.changeDataStorage(dsc -> dsc.convert(PageMemoryDataStorageChange.class)))
                .get(1, TimeUnit.SECONDS);

        assertEquals(
                PageMemoryStorageEngineConfigurationSchema.DEFAULT_DATA_REGION_NAME,
                ((PageMemoryDataStorageView) tableCfg.dataStorage().value()).dataRegion()
        );

        table = engine.createTable(tableCfg);
        table.start();

        storage = table.createMvPartitionStorage(partitionId());
    }

    @AfterEach
    void tearDown() throws Exception {
        IgniteUtils.closeAll(
                storage,
                table == null ? null : table::stop,
                engine == null ? null : engine::stop
        );
    }

    @Override
    protected int partitionId() {
        // 1 instead of the default 0 to make sure that we note cases when we forget to pass the partition ID (in which
        // case it will turn into 0).
        return 1;
    }

    @SuppressWarnings("JUnit3StyleTestMethodInJUnit4Class")
    @Override
    public void testReadsFromEmpty() {
        // TODO: enable the test when https://issues.apache.org/jira/browse/IGNITE-17006 is implemented

        // Effectively, disable the test because it makes no sense for this kind of storage as attempts to read using random
        // pageIds will result in troubles.
    }

    @Test
    void abortOfInsertMakesRowIdInvalidForAddWrite() {
        RowId rowId = storage.insert(binaryRow, newTransactionId());
        storage.abortWrite(rowId);

        assertThrows(RowIdIsInvalidForModificationsException.class, () -> storage.addWrite(rowId, binaryRow2, txId));
    }

    @Test
    void abortOfInsertMakesRowIdInvalidForCommitWrite() {
        RowId rowId = storage.insert(binaryRow, newTransactionId());
        storage.abortWrite(rowId);

        assertThrows(RowIdIsInvalidForModificationsException.class, () -> storage.commitWrite(rowId, Timestamp.nextVersion()));
    }

    @Test
    void abortOfInsertMakesRowIdInvalidForAbortWrite() {
        RowId rowId = storage.insert(binaryRow, newTransactionId());
        storage.abortWrite(rowId);

        assertThrows(RowIdIsInvalidForModificationsException.class, () -> storage.abortWrite(rowId));
    }

    @Test
    void uncommittedMultiPageValuesAreReadSuccessfully() {
        BinaryRow longRow = rowStoredInFragments();
        LinkRowId rowId = storage.insert(longRow, txId);

        BinaryRow foundRow = storage.read(rowId, txId);

        assertRowMatches(foundRow, longRow);
    }

    private BinaryRow rowStoredInFragments() {
        int pageSize = engineConfig.pageSize().value();

        // A repetitive pattern of 19 different characters (19 is chosen as a prime number) to reduce probability of 'lucky' matches
        // hiding bugs.
        String pattern = IntStream.range(0, 20)
                .mapToObj(ch -> String.valueOf((char) ('a' + ch)))
                .collect(joining());

        TestValue value = new TestValue(1, pattern.repeat((int) (2.5 * pageSize / pattern.length())));
        return binaryRow(key, value);
    }

    @Test
    void committedMultiPageValuesAreReadSuccessfully() {
        BinaryRow longRow = rowStoredInFragments();

        LinkRowId rowId = storage.insert(longRow, txId);
        storage.commitWrite(rowId, Timestamp.nextVersion());

        BinaryRow foundRow = storage.read(rowId, Timestamp.nextVersion());

        assertRowMatches(foundRow, longRow);
    }

    @Test
    void uncommittedMultiPageValuesWorkWithScans() throws Exception {
        BinaryRow longRow = rowStoredInFragments();
        storage.insert(longRow, txId);

        try (Cursor<BinaryRow> cursor = storage.scan(row -> true, txId)) {
            BinaryRow foundRow = cursor.next();

            assertRowMatches(foundRow, longRow);
        }
    }

    @Test
    void committedMultiPageValuesWorkWithScans() throws Exception {
        BinaryRow longRow = rowStoredInFragments();

        LinkRowId rowId = storage.insert(longRow, txId);
        storage.commitWrite(rowId, Timestamp.nextVersion());

        try (Cursor<BinaryRow> cursor = storage.scan(row -> true, txId)) {
            BinaryRow foundRow = cursor.next();

            assertRowMatches(foundRow, longRow);
        }
    }
}
