package org.apache.seatunnel.connectors.seatunnel.postgres.copy.source;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Objects;

import static org.apache.seatunnel.shade.com.google.common.base.Preconditions.checkArgument;

@Data
@EqualsAndHashCode
public class ChunkRange implements Serializable {
    private final Object chunkStart;
    private final Object chunkEnd;

    public static ChunkRange all() {
        return new ChunkRange(null, null);
    }

    public static ChunkRange of(Object chunkStart, Object chunkEnd) {
        return new ChunkRange(chunkStart, chunkEnd);
    }

    private ChunkRange(Object chunkStart, Object chunkEnd) {
        if (chunkStart != null || chunkEnd != null) {
            checkArgument(
                    !Objects.equals(chunkStart, chunkEnd),
                    "Chunk start %s shouldn't be equal to chunk end %s",
                    chunkStart,
                    chunkEnd);
        }
        this.chunkStart = chunkStart;
        this.chunkEnd = chunkEnd;
    }
}
