// PostgresCopySourceFactory.java
package org.apache.seatunnel.connectors.seatunnel.postgres.copy.source;

import org.apache.seatunnel.api.configuration.util.OptionRule;
import org.apache.seatunnel.api.source.SeaTunnelSource;
import org.apache.seatunnel.api.source.SourceSplit;
import org.apache.seatunnel.api.table.connector.TableSource;
import org.apache.seatunnel.api.table.factory.Factory;
import org.apache.seatunnel.api.table.factory.TableSourceFactory;
import org.apache.seatunnel.api.table.factory.TableSourceFactoryContext;
import org.apache.seatunnel.connectors.seatunnel.postgres.copy.config.PostgresCopyOptions;

import com.google.auto.service.AutoService;

import java.io.Serializable;

@AutoService(Factory.class)
public class PostgresCopySourceFactory implements TableSourceFactory {
    public static final String IDENTIFIER = "PostgresCopySource";

    @Override
    public String factoryIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public OptionRule optionRule() {
        return PostgresCopyOptions.optionRule();
    }

    //    @Override
    //    public PostgresCopySource createSource(TableFactoryContext context) {
    //        // 这里把用户传入的选项交给 Source
    //        return new PostgresCopySource(context);
    //    }

    @Override
    public <T, SplitT extends SourceSplit, StateT extends Serializable>
            TableSource<T, SplitT, StateT> createSource(TableSourceFactoryContext context) {
        return () -> (SeaTunnelSource<T, SplitT, StateT>) new PostgresCopySource(context);
        //        return TableSourceFactory.super.createSource(context);
    }

    @Override
    public Class<? extends SeaTunnelSource> getSourceClass() {
        return PostgresCopySource.class;
    }
}
