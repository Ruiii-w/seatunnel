package org.apache.seatunnel.connectors.seatunnel.postgres.copy.source;

import org.apache.seatunnel.api.source.SourceSplit;

import lombok.Getter;

import java.io.Serializable;

public class PostgresCopySourceSplit implements SourceSplit, Serializable {
    private final String splitId;
    @Getter private final String sql;
    @Getter private final Object chunkStart;
    @Getter private final Object chunkEnd;
    @Getter private final String splitColumn;

    // 原有构造函数，保持向后兼容
    public PostgresCopySourceSplit(String splitId, String sql) {
        this.splitId = splitId;
        this.sql = sql;
        this.chunkStart = null;
        this.chunkEnd = null;
        this.splitColumn = null;
    }

    // 新的构造函数，支持分片范围
    public PostgresCopySourceSplit(
            String splitId, String sql, Object chunkStart, Object chunkEnd, String splitColumn) {
        this.splitId = splitId;
        this.sql = sql;
        this.chunkStart = chunkStart;
        this.chunkEnd = chunkEnd;
        this.splitColumn = splitColumn;
    }

    @Override
    public String splitId() {
        return splitId;
    }

    public boolean hasChunkRange() {
        return chunkStart != null || chunkEnd != null;
    }
}
