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

package org.apache.seatunnel.connectors.doris.sink.writer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Properties;

public class DorisStreamLoadTest {

    @Test
    public void testEscapeString() {
        // 1. Normal characters (Should not change)
        Assertions.assertEquals("abc", DorisStreamLoad.escapeString("abc"));
        Assertions.assertEquals("123", DorisStreamLoad.escapeString("123"));
        Assertions.assertEquals("|", DorisStreamLoad.escapeString("|"));
        Assertions.assertEquals(",", DorisStreamLoad.escapeString(","));

        // 2. Control characters (Should be escaped to \xHH)
        // SOH (Start of Header) -> \x01
        Assertions.assertEquals("\\x01", DorisStreamLoad.escapeString("\u0001"));
        // Null -> \x00
        Assertions.assertEquals("\\x00", DorisStreamLoad.escapeString("\u0000"));
        // Unit Separator -> \x1f
        Assertions.assertEquals("\\x1f", DorisStreamLoad.escapeString("\u001f"));
        // Delete -> \x7f
        Assertions.assertEquals("\\x7f", DorisStreamLoad.escapeString("\u007f"));
        // Newline -> \x0a (Required for HTTP Headers)
        Assertions.assertEquals("\\x0a", DorisStreamLoad.escapeString("\n"));

        // 3. Mixed characters
        Assertions.assertEquals("a\\x01b", DorisStreamLoad.escapeString("a\u0001b"));
        Assertions.assertEquals("\\x01\\x02", DorisStreamLoad.escapeString("\u0001\u0002"));

        // 4. Extended ASCII / Non-printable (> 126)
        // \u0080 (128) -> \x80
        Assertions.assertEquals("\\x80", DorisStreamLoad.escapeString("\u0080"));
    }

    @Test
    public void testEscapeProperties() {
        Properties props = new Properties();

        // Setup: Use typical invisible characters
        // line_delimiter = \n (standard)
        props.setProperty(LoadConstants.LINE_DELIMITER_KEY, "\n");
        // column_separator = \u0001 (standard Hive/Hadoop delimiter)
        props.setProperty(LoadConstants.FIELD_DELIMITER_KEY, "\u0001");

        // Execute
        DorisStreamLoad.escapeProperties(props);

        // Verify
        // \n should become \x0a to avoid HTTP header injection/parsing issues
        Assertions.assertEquals("\\x0a", props.getProperty(LoadConstants.LINE_DELIMITER_KEY));
        // \u0001 should become \x01
        Assertions.assertEquals("\\x01", props.getProperty(LoadConstants.FIELD_DELIMITER_KEY));

        // Test with normal delimiters (should remain unchanged)
        Properties normalProps = new Properties();
        normalProps.setProperty(LoadConstants.FIELD_DELIMITER_KEY, ",");
        DorisStreamLoad.escapeProperties(normalProps);
        Assertions.assertEquals(",", normalProps.getProperty(LoadConstants.FIELD_DELIMITER_KEY));
    }
}
