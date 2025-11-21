package org.apache.seatunnel.connectors.seatunnel.jdbc.internal.copy;

import org.apache.seatunnel.common.exception.CommonErrorCodeDeprecated;
import org.apache.seatunnel.connectors.seatunnel.jdbc.exception.JdbcConnectorException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.ByteBuffer;

/**
 * PostgreSQL NUMERIC type decoder. Decodes PostgreSQL binary numeric representation into {@link
 * BigDecimal}.
 */
public final class PgNumericDecoder {

    private static final Logger LOG = LoggerFactory.getLogger(PgNumericDecoder.class);

    private PgNumericDecoder() {}

    public static BigDecimal decode(ByteBuffer buf) {
        // ndigits: number of base-10000 digits
        int ndigits = buf.getShort() & 0xFFFF;
        int weight = buf.getShort(); // signed
        int sign = buf.getShort() & 0xFFFF; // 0x0000 positive, 0x4000 negative, 0xC000 NaN
        int dscale = buf.getShort() & 0xFFFF;

        // NaN is not supported
        if (sign == 0xC000) {
            throw new JdbcConnectorException(
                    CommonErrorCodeDeprecated.UNSUPPORTED_OPERATION,
                    "PostgreSQL NUMERIC value is NaN, not supported.");
        }

        // Zero value (ndigits == 0)
        if (ndigits == 0) {
            BigDecimal zero = BigDecimal.ZERO.setScale(dscale);
            return zero;
        }

        // Read base-10000 digits
        int[] digits = new int[ndigits];
        for (int i = 0; i < ndigits; i++) {
            digits[i] = buf.getShort() & 0xFFFF; // value in 0..9999
        }

        // Build raw integer in base-10 by concatenating base-10000 groups
        BigInteger raw = BigInteger.ZERO;
        final BigInteger BASE = BigInteger.valueOf(10000);
        for (int d : digits) {
            raw = raw.multiply(BASE).add(BigInteger.valueOf(d));
        }

        // Compute base scale: fractional digits = (ndigits - (weight + 1)) * 4
        // This can be negative when intPart > ndigits
        int intPart = weight + 1;
        int baseScale = (ndigits - intPart) * 4;

        // Exact numeric value before applying declared dscale
        BigDecimal value = new BigDecimal(raw, baseScale);

        // Apply sign
        if (sign == 0x4000) {
            value = value.negate();
        }

        // Align to declared dscale:
        // - If baseScale < dscale -> pad trailing zeros
        // - If baseScale > dscale -> truncate extra fraction digits toward zero (match previous
        // behavior)
        if (value.scale() != dscale) {
            value = value.setScale(dscale, RoundingMode.DOWN);
        }

        //        if(value.equals(new BigDecimal("123.4500000000"))){
        //
        //            LOG.info("BigDecimal value: {}", value);
        //        }

        return value;
    }
}

/*
 *
 * 假设PostgreSQL存储数值：123.45（dscale=2）, 二进制表示：ndigits=2, weight=0, sign=0, dscale=2
 *
 * digits = [123, 4500] （因为10000进制）
 *
 * raw = 123×10000 + 4500 = 1234500
 *
 * baseScale = (2 - (0+1)) × 4 = 4
 *
 * value = new BigDecimal(1234500, 4) = 123.4500
 *
 * 设置精度：123.4500 → 123.45
 */