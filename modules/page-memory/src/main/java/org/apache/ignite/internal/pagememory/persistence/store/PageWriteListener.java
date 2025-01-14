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

package org.apache.ignite.internal.pagememory.persistence.store;

import java.nio.ByteBuffer;

/**
 * Each page write attempt to a {@link PageStore} may be covered by such listener.
 *
 * <p>If it is necessary, a page data can be handled by another process prior to actually written to the PageStore.
 */
@FunctionalInterface
public interface PageWriteListener {
    /**
     * Callback before writing the page to the store.
     *
     * @param pageId Handled page id.
     * @param buf Buffer with page data.
     */
    void accept(long pageId, ByteBuffer buf);
}
