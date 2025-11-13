package org.apache.seatunnel.connectors.seatunnel.jdbc.internal.copy;

import org.apache.seatunnel.api.table.catalog.TableSchema;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.common.exception.CommonErrorCodeDeprecated;
import org.apache.seatunnel.connectors.seatunnel.jdbc.exception.JdbcConnectorException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public final class PgCopyBinaryReader implements PgCopyReader {
    private static final byte[] SIGNATURE = {
            'P', 'G', 'C', 'O', 'P', 'Y', '\n', (byte) 0xFF, '\r', '\n', 0
    };
    private static final LocalDate EPOCH_DATE = LocalDate.of(2000, 1, 1);
    private static final LocalDateTime EPOCH_DATETIME = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int MAX_BUFFER_SIZE = 16 * 1024 * 1024;
    private static final int FIELD_BUFFER_INIT_SIZE = 4 * 1024;
    private static final int BUFFER_POOL_SIZE = 10;

    // 直接缓冲区池
    private static final BlockingQueue<ByteBuffer> bufferPool = new ArrayBlockingQueue<>(BUFFER_POOL_SIZE);

    static {
        // 预分配直接缓冲区
        for (int i = 0; i < BUFFER_POOL_SIZE; i++) {
            bufferPool.offer(ByteBuffer.allocateDirect(BUFFER_SIZE).order(ByteOrder.BIG_ENDIAN));
        }
    }

    private final InputStream stream;
    private final ReadableByteChannel channel;
    private final SeaTunnelRowType rowType;
    private final SeaTunnelDataType<?>[] fieldTypes;

    // 使用直接缓冲区
    private ByteBuffer buffer;
    private final Deque<SeaTunnelRow> queue = new ArrayDeque<>();

    // 可复用的字段解析缓冲区
    private ByteBuffer fieldBuffer = ByteBuffer.allocateDirect(FIELD_BUFFER_INIT_SIZE).order(ByteOrder.BIG_ENDIAN);

    // 解析状态
    private int pendingFields = -1;
    private Object[] pendingValues;
    private int pendingIndex = 0;
    private int pendingFieldLen = -1;

    private boolean headerParsed = false;
    private boolean eof = false;
    private boolean bufferFromPool = false;

    public PgCopyBinaryReader(InputStream stream, TableSchema schema) {
        this.stream = stream;
        this.channel = Channels.newChannel(stream);
        this.rowType = schema.toPhysicalRowDataType();
        this.fieldTypes = rowType.getFieldTypes();

        // 从池中获取缓冲区，如果没有则创建新的直接缓冲区
        this.buffer = acquireBuffer();
        this.bufferFromPool = true;
    }

    /**
     * 从缓冲区池获取直接缓冲区
     */
    private ByteBuffer acquireBuffer() {
        ByteBuffer buffer = bufferPool.poll();
        if (buffer != null) {
            buffer.clear();
            return buffer;
        }
        // 池为空时创建新的直接缓冲区
        return ByteBuffer.allocateDirect(BUFFER_SIZE).order(ByteOrder.BIG_ENDIAN);
    }

    /**
     * 释放缓冲区回池中
     */
    private void releaseBuffer(ByteBuffer buffer) {
        if (buffer != null && buffer.isDirect()) {
            // 如果缓冲区太大，不回收以避免内存浪费
            if (buffer.capacity() <= BUFFER_SIZE * 4) {
                buffer.clear();
                bufferPool.offer(buffer);
            }
        }
    }

    @Override
    public boolean hasNext() {
        if (!queue.isEmpty()) {
            return true;
        }
        return !eof;
    }

    @Override
    public SeaTunnelRow next() {
        try {
            if (queue.isEmpty() && !eof) {
                fillAndParse();
                while (queue.isEmpty() && !eof) {
                    fillAndParse();
                }
            }
            return queue.poll();
        } catch (IOException e) {
            throw new JdbcConnectorException(
                    CommonErrorCodeDeprecated.SQL_OPERATION_FAILED, "Binary COPY read failed", e);
        }
    }

    private void fillAndParse() throws IOException {
        fillBufferBlocking();
        if (!headerParsed)
            parseHeader();

        if (headerParsed)
            parseRows();
    }

    /**
     * 使用通道读取，避免数组拷贝
     */
    private void fillBufferBlocking() throws IOException {
        boolean initial = buffer.position() == 0 && buffer.limit() == buffer.capacity();
        if (initial) {
            buffer.clear();
        } else {
            buffer.compact();
        }

        // 使用通道读取到直接缓冲区
        int bytesRead = channel.read(buffer);
        if (bytesRead == -1) {
            eof = true;
        }

        buffer.flip();

        // 如果缓冲区为空且不是初始状态，可能是压缩问题，回退到传统读取方式
        if (buffer.remaining() == 0 && !initial && !eof) {
            readUsingTraditionalMethod();
        }
    }

    /**
     * 传统读取方法作为回退方案
     */
    private void readUsingTraditionalMethod() throws IOException {
        buffer.clear();
        byte[] tempArray = new byte[buffer.remaining()];
        int bytesRead = stream.read(tempArray);
        if (bytesRead > 0) {
            buffer.put(tempArray, 0, bytesRead);
        } else if (bytesRead == -1) {
            eof = true;
        }
        buffer.flip();
    }

    /**
     * 确保缓冲区容量，使用直接缓冲区扩容
     */
    private void ensureCapacityFor(int required) {
        if (required <= buffer.capacity())
            return;
        if (required > MAX_BUFFER_SIZE) {
            throw new JdbcConnectorException(
                    CommonErrorCodeDeprecated.UNSUPPORTED_OPERATION,
                    "COPY buffer expansion exceeds max limit: required=" + required
                            + ", max=" + MAX_BUFFER_SIZE);
        }

        int newCap = calculateNewCapacity(required);
        ByteBuffer newBuf = ByteBuffer.allocateDirect(newCap).order(ByteOrder.BIG_ENDIAN);

        // 传输数据到新缓冲区
        buffer.mark();
        newBuf.put(buffer);
        newBuf.flip();

        // 释放旧缓冲区回池
        if (bufferFromPool) {
            releaseBuffer(buffer);
        }

        buffer = newBuf;
        bufferFromPool = false;
    }

    private int calculateNewCapacity(int required) {
        int newCap = buffer.capacity();
        while (newCap < required && newCap < MAX_BUFFER_SIZE) {
            newCap = Math.min(newCap << 1, MAX_BUFFER_SIZE);
        }
        return Math.max(newCap, required);
    }

    /**
     * 确保字段缓冲区容量
     */
    private void ensureFieldBufferCapacity(int required) {
        if (required <= fieldBuffer.capacity()) {
            fieldBuffer.clear();
            return;
        }

        // 创建新的字段缓冲区
        ByteBuffer newFieldBuffer = ByteBuffer.allocateDirect(required * 2).order(ByteOrder.BIG_ENDIAN);
        fieldBuffer = newFieldBuffer;
    }

    private void parseHeader() {
        if (buffer.remaining() < SIGNATURE.length + 8) {
            return;
        }

        int savedPos = buffer.position();

        for (byte b : SIGNATURE) {
            if (buffer.get() != b) {
                throw new JdbcConnectorException(
                        CommonErrorCodeDeprecated.UNSUPPORTED_OPERATION,
                        "Invalid COPY header signature");
            }
        }

        buffer.getInt(); // flags
        int extLen = buffer.getInt();
        if (extLen > 0) {
            if (buffer.remaining() < extLen) {
                buffer.position(savedPos);
                return;
            }
            buffer.position(buffer.position() + extLen);
        }
        headerParsed = true;
    }

    /**
     * 优化的行解析方法，减少缓冲区拷贝
     */
    private void parseRows() {
        while (true) {
            if (pendingFields < 0) {
                if (buffer.remaining() < 2)
                    return;
                short fields = buffer.getShort();
                if (fields == -1) {
                    eof = true;
                    return;
                }
                if (fields != rowType.getTotalFields()) {
                    throw new JdbcConnectorException(
                            CommonErrorCodeDeprecated.UNSUPPORTED_OPERATION,
                            "Column count mismatch: " + fields);
                }
                pendingFields = fields;
                pendingValues = new Object[fields];
                pendingIndex = 0;
                pendingFieldLen = -1;
            }

            while (pendingIndex < pendingFields) {
                if (pendingFieldLen < 0) {
                    if (buffer.remaining() < 4) return;
                    pendingFieldLen = buffer.getInt();
                }

                if (pendingFieldLen == -1) {
                    pendingValues[pendingIndex++] = null;
                    pendingFieldLen = -1;
                    continue;
                }

                ensureCapacityFor(pendingFieldLen);
                if (buffer.remaining() < pendingFieldLen)
                    return;

                // 优化：使用可复用的字段缓冲区，避免创建新的ByteBuffer视图
                ensureFieldBufferCapacity(pendingFieldLen);

                // 直接拷贝字段数据到字段缓冲区
                byte[] fieldData = new byte[pendingFieldLen];
                buffer.get(fieldData);
                fieldBuffer.put(fieldData);
                fieldBuffer.flip();

                try {
                    pendingValues[pendingIndex] = PgCopyUtils.parseBinaryField(
                            fieldBuffer,
                            fieldTypes[pendingIndex],
                            EPOCH_DATE,
                            EPOCH_DATETIME);
                } finally {
                    fieldBuffer.clear();
                }

                pendingIndex++;
                pendingFieldLen = -1;
            }

            queue.add(new SeaTunnelRow(pendingValues));
            pendingFields = -1;
            pendingValues = null;
            pendingIndex = 0;
            pendingFieldLen = -1;
            if (buffer.remaining() < 2)
                return;
        }
    }

    /**
     * 传统字段解析方法（保留作为参考）
     */
    private boolean parseFields(Object[] values, int fields) {
        for (int i = 0; i < fields; i++) {
            if (buffer.remaining() < 4) return false;
            int len = buffer.getInt();
            if (len == -1) {
                values[i] = null;
                continue;
            }
            if (buffer.remaining() < len) return false;

            ensureFieldBufferCapacity(len);
            byte[] fieldData = new byte[len];
            buffer.get(fieldData);
            fieldBuffer.put(fieldData);
            fieldBuffer.flip();

            try {
                values[i] = PgCopyUtils.parseBinaryField(
                        fieldBuffer, fieldTypes[i], EPOCH_DATE, EPOCH_DATETIME);
            } finally {
                fieldBuffer.clear();
            }
        }
        return true;
    }

    @Override
    public void close() throws IOException {
        try {
            // 释放缓冲区回池
            if (bufferFromPool) {
                releaseBuffer(buffer);
            }

            // 清理字段缓冲区
            if (fieldBuffer != null && fieldBuffer.isDirect()) {
                // 小缓冲区直接释放，大缓冲区不回收
                if (fieldBuffer.capacity() <= FIELD_BUFFER_INIT_SIZE * 4) {
                    // 直接缓冲区的清理由GC处理
                }
            }
        } finally {
            stream.close();
        }
    }

    /**
     * 静态工具方法：清理缓冲区池
     */
    public static void cleanupBufferPool() {
        bufferPool.clear();
    }

    /**
     * 获取缓冲区池状态（用于监控）
     */
    public static int getBufferPoolSize() {
        return bufferPool.size();
    }
}