package org.apache.seatunnel.connectors.seatunnel.jdbc.internal.copy;

import org.apache.seatunnel.api.table.type.BasicType;
import org.apache.seatunnel.api.table.type.PrimitiveByteArrayType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class PgCopyUtilsTest {

    @Test
    public void testParseValueStringNullAndEmpty() {
        assertNull(PgCopyUtils.parseValue(null, BasicType.STRING_TYPE));
        assertNull(PgCopyUtils.parseValue("\\N", BasicType.STRING_TYPE));
        assertEquals("", PgCopyUtils.parseValue("", BasicType.STRING_TYPE));
    }

    @Test
    public void testParseValueBytesHex() {
        byte[] bytes =
                (byte[]) PgCopyUtils.parseValue("\\x01020A", PrimitiveByteArrayType.INSTANCE);
        assertArrayEquals(new byte[] {1, 2, 10}, bytes);
    }

    @Test
    public void testParseValueBytesEscapedHex() {
        byte[] bytes =
                (byte[]) PgCopyUtils.parseValue("\\\\xFF00", PrimitiveByteArrayType.INSTANCE);
        assertArrayEquals(new byte[] {(byte) 0xFF, 0}, bytes);
    }

    @Test
    public void testParseValueBytesBase64Fallback() {
        byte[] bytes = (byte[]) PgCopyUtils.parseValue("AQI=", PrimitiveByteArrayType.INSTANCE);
        assertArrayEquals(new byte[] {1, 2}, bytes);
    }
}
