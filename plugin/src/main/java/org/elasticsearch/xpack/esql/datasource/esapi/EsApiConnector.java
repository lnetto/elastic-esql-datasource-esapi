/*
 * Elasticsearch API connector for ES|QL Data Federation.
 */
package org.elasticsearch.xpack.esql.datasource.esapi;

import org.elasticsearch.xpack.esql.datasources.spi.Connector;
import org.elasticsearch.xpack.esql.datasources.spi.ExternalSplit;
import org.elasticsearch.xpack.esql.datasources.spi.FormatReader;
import org.elasticsearch.xpack.esql.datasources.spi.QueryRequest;
import org.elasticsearch.xpack.esql.datasources.spi.ResultCursor;
import org.elasticsearch.xpack.esql.datasources.spi.Split;

/** Calls the API as the querying user and hands the response's rows to ES|QL. */
final class EsApiConnector implements Connector {

    private final EsApiConfig config;

    EsApiConnector(EsApiConfig config) {
        this.config = config;
    }

    @Override
    public ResultCursor execute(QueryRequest request, Split split) {
        JsonRows.Table table = JsonRows.toTable(EsApiClient.call(config, false), config.path());
        int limit = request.rowLimit();
        return new EsApiResultCursor(table.rows(), request.attributes(), request.blockFactory(), request.batchSize(),
            limit == FormatReader.NO_LIMIT || limit < 0 ? -1 : limit);
    }

    @Override
    public ResultCursor execute(QueryRequest request, ExternalSplit split) {
        return execute(request, Split.SINGLE);
    }

    @Override
    public void close() {}

    @Override
    public String toString() {
        return "EsApiConnector[" + config.apiPath() + "]";
    }
}
