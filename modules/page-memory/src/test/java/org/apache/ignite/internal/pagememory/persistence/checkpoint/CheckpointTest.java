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

package org.apache.ignite.internal.pagememory.persistence.checkpoint;

import static org.apache.ignite.internal.pagememory.persistence.checkpoint.IgniteConcurrentMultiPairQueue.EMPTY;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.apache.ignite.internal.pagememory.FullPageId;
import org.apache.ignite.internal.pagememory.persistence.PageMemoryImpl;
import org.apache.ignite.lang.IgniteBiTuple;
import org.junit.jupiter.api.Test;

/**
 * For {@link Checkpoint} testing.
 */
public class CheckpointTest {
    @Test
    void testHasDelta() {
        CheckpointProgressImpl progress = mock(CheckpointProgressImpl.class);

        assertFalse(new Checkpoint(EMPTY, progress).hasDelta());

        IgniteBiTuple<PageMemoryImpl, FullPageId[]> biTuple = new IgniteBiTuple<>(
                mock(PageMemoryImpl.class),
                new FullPageId[]{new FullPageId(0, 1)}
        );

        assertTrue(new Checkpoint(new IgniteConcurrentMultiPairQueue<>(List.of(biTuple)), progress).hasDelta());
    }
}
