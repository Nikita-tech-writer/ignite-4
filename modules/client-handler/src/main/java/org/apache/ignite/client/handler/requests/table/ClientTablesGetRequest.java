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

package org.apache.ignite.client.handler.requests.table;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.apache.ignite.client.proto.ClientMessagePacker;
import org.apache.ignite.internal.table.TableImpl;
import org.apache.ignite.table.manager.IgniteTables;

/**
 * Client tables retrieval request.
 */
public class ClientTablesGetRequest {
    /**
     * Processes the request.
     *
     * @param out Packer.
     * @param igniteTables Ignite tables.
     * @return Future.
     */
    public static CompletableFuture<Void> process(
            ClientMessagePacker out,
            IgniteTables igniteTables
    ) {
        return igniteTables.tablesAsync().thenAccept(tables -> {
            try {
                out.packMapHeader(tables.size());

                for (var table : tables) {
                    var tableImpl = (TableImpl) table;

                    out.packUuid(tableImpl.tableId());
                    out.packString(table.tableName());
                }
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        });
    }
}