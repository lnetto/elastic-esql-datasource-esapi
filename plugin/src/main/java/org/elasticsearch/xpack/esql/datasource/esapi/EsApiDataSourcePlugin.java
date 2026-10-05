/*
 * Elasticsearch API connector for ES|QL Data Federation.
 */
package org.elasticsearch.xpack.esql.datasource.esapi;

import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.features.NodeFeature;
import org.elasticsearch.plugins.ActionPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.rest.RestHandler;
import org.elasticsearch.xpack.esql.datasources.spi.ConnectorFactory;
import org.elasticsearch.xpack.esql.datasources.spi.DataSourcePlugin;
import org.elasticsearch.xpack.esql.datasources.spi.DataSourceValidator;
import org.elasticsearch.xpack.esql.datasources.spi.StorageProviderFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Registers the Elasticsearch API connector for ES|QL: {@code FROM <dataset>} calls a REST endpoint of
 * this cluster (e.g. {@code _nodes/stats}, {@code _cat/indices}, {@code _security/api_key}) as the user
 * running the query, and returns the JSON as rows. Scheme {@code esapi://}, data source type {@code es_api}.
 *
 * <p>Requires {@code esql.federation.enabled: true} on every node.
 */
public class EsApiDataSourcePlugin extends Plugin implements DataSourcePlugin, ActionPlugin {

    private static final Set<String> SCHEMES = Set.of(EsApiConfig.SCHEME);

    @Override
    public Collection<?> createComponents(PluginServices services) {
        EsApiNode.init(services.threadPool().getThreadContext(), services.xContentRegistry());
        return List.of();
    }

    /** Registers nothing: this is only how a plugin gets hold of the node's REST controller to dispatch through. */
    @Override
    public Collection<RestHandler> getRestHandlers(
        RestHandlersServices services,
        Supplier<DiscoveryNodes> nodesInCluster,
        Predicate<NodeFeature> clusterSupportsFeature
    ) {
        EsApiNode.init(services.restController());
        return List.of();
    }

    @Override
    public Set<String> supportedSchemes() {
        return SCHEMES;
    }

    @Override
    public Map<String, StorageProviderFactory> storageProviders(Settings settings) {
        return Map.of(EsApiConfig.SCHEME, StorageProviderFactory.noConfigKeys(EsApiStorageProvider::new));
    }

    @Override
    public Set<String> supportedConnectorSchemes() {
        return SCHEMES;
    }

    @Override
    public Map<String, ConnectorFactory> connectors(Settings settings) {
        return Map.of(EsApiConfig.TYPE, new EsApiConnectorFactory());
    }

    @Override
    public Map<String, DataSourceValidator> datasourceValidators(Settings settings) {
        DataSourceValidator validator = new EsApiDataSourceValidator();
        return Map.of(validator.type(), validator);
    }
}
