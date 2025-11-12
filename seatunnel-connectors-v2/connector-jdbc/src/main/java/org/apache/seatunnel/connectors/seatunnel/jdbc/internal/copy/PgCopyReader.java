package org.apache.seatunnel.connectors.seatunnel.jdbc.internal.copy;

import org.apache.seatunnel.api.table.type.SeaTunnelRow;

import java.io.Closeable;

/** Abstraction for PostgreSQL COPY readers. */
public interface PgCopyReader extends Closeable {
    boolean hasNext();

    SeaTunnelRow next();
}
