/*
 * Elasticsearch API connector for ES|QL Data Federation.
 */
package org.elasticsearch.xpack.esql.datasource.esapi;

import org.elasticsearch.xpack.esql.core.expression.Attribute;
import org.elasticsearch.xpack.esql.core.expression.Nullability;
import org.elasticsearch.xpack.esql.core.expression.ReferenceAttribute;
import org.elasticsearch.xpack.esql.core.tree.Source;
import org.elasticsearch.xpack.esql.datasources.spi.ConfigKeyValidator;
import org.elasticsearch.xpack.esql.datasources.spi.Connector;
import org.elasticsearch.xpack.esql.datasources.spi.ConnectorFactory;
import org.elasticsearch.xpack.esql.datasources.spi.SimpleSourceMetadata;
import org.elasticsearch.xpack.esql.datasources.spi.SourceMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Resolves an API dataset's columns by calling the API as the querying user (the response is kept a
 * few seconds and reused by that query's execution, so a query makes one call).
 */
final class EsApiConnectorFactory implements ConnectorFactory {

    @Override
    public String type() {
        return EsApiConfig.TYPE;
    }

    @Override
    public boolean canHandle(String location) {
        return EsApiConfig.handles(location);
    }

    @Override
    public void validateConfig(String location, Map<String, Object> config) {
        ConfigKeyValidator.check(EsApiConfig.effective(config), List.of(EsApiConfig.CONFIG_KEYS));
        EsApiConfig.parse(location, config);
    }

    @Override
    public SourceMetadata resolveMetadata(String location, Map<String, Object> rawConfig) {
        EsApiConfig config = EsApiConfig.parse(location, rawConfig);
        JsonRows.Table table = JsonRows.toTable(EsApiClient.call(config, true), config.path());
        if (table.columns().isEmpty()) {
            throw new IllegalStateException("[" + config.apiPath() + "] returned no rows" + (config.path() == null ? "" : " at [path] "
                + config.path()) + ": the columns come from the response, so they're unknown while it's empty"
                + (config.path() == null ? " (or point [path] at the rows)" : ""));
        }
        List<Attribute> attributes = new ArrayList<>(table.columns().size());
        for (JsonRows.Column c : table.columns()) {
            attributes.add(new ReferenceAttribute(Source.EMPTY, null, c.name(), c.type(), Nullability.TRUE, null, false));
        }
        // sourceType must be the URI scheme: the operator registries key connectors by scheme.
        return new SimpleSourceMetadata(attributes, EsApiConfig.SCHEME, location, null, null, Map.of(), EsApiConfig.resolved(location, rawConfig));
    }

    @Override
    public Connector open(Map<String, Object> rawConfig) {
        return new EsApiConnector(EsApiConfig.parse(null, rawConfig));
    }
}
