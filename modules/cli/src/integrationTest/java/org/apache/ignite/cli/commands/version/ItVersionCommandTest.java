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

package org.apache.ignite.cli.commands.version;

import static org.junit.jupiter.api.Assertions.assertAll;

import org.apache.ignite.cli.commands.CliCommandTestIntegrationBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ItVersionCommandTest extends CliCommandTestIntegrationBase {

    @Test
    @DisplayName("Should print cli version that is got from pom.xml")
    void printVersion() {
        // When
        execute("--version");

        // Then
        assertAll(
                this::assertExitCodeIsZero,
                this::assertErrOutputIsEmpty,
                () -> assertOutputContains("Apache Ignite CLI ver")
        );
    }
}
