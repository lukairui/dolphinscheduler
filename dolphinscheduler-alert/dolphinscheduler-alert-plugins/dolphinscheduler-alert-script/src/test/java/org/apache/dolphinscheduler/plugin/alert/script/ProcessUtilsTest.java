/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.dolphinscheduler.plugin.alert.script;

import org.junit.jupiter.api.Test;

/**
 * ProcessUtilsTest
 */
public class ProcessUtilsTest {

    private static final String rootPath = System.getProperty("user.dir");

    private static final String shellFilPath =
            rootPath + "/dolphinscheduler-alert-plugins/dolphinscheduler-alert-script/src/test/script/shell/test.sh";

    private String[] cmd = {"/bin/sh", "-c", shellFilPath + " -t 1"};

    @Test
    public void testExecuteScript() {
        int code = ProcessUtils.executeScript(cmd);
        assert code != -1;
    }
}
