/*
 * Elasticsearch API connector for ES|QL Data Federation.
 */
package org.elasticsearch.xpack.esql.datasource.esapi;

import org.elasticsearch.common.ValidationException;
import org.elasticsearch.xpack.esql.datasources.metadata.DataSourceSetting;
import org.elasticsearch.xpack.esql.datasources.spi.DataSourceValidator;

import java.util.HashMap;
import java.util.Map;

/**
 * CRUD-time validator for the {@code es_api} data source type. Registering it is what makes
 * {@code PUT /_query/data_source} accept {@code "type": "es_api"}. Connection settings
 * The data source needs no settings: calls run as the querying user.
 */
final class EsApiDataSourceValidator implements DataSourceValidator {

    @Override
    public String type() {
        return EsApiConfig.TYPE;
    }

    @Override
    public Map<String, DataSourceSetting> validateDatasource(Map<String, Object> datasourceSettings) {
        if (datasourceSettings == null || datasourceSettings.isEmpty()) {
            return Map.of();
        }
        ValidationException errors = new ValidationException();
        Map<String, DataSourceSetting> out = new HashMap<>();
        for (Map.Entry<String, Object> e : datasourceSettings.entrySet()) {
            if (EsApiConfig.CONFIG_KEYS.contains(e.getKey()) == false) {
                errors.addValidationError("unknown setting [" + e.getKey() + "] for data source type [es_api]; recognised: "
                    + EsApiConfig.CONFIG_KEYS);
                continue;
            }
            // Not marked secret: on 9.5.4 secret settings reach connectors still encrypted (see README).
            out.put(e.getKey(), new DataSourceSetting(e.getValue(), false));
        }
        if (errors.validationErrors().isEmpty() == false) {
            throw errors;
        }
        return out;
    }

    @Override
    public Map<String, Object> validateDataset(
        Map<String, DataSourceSetting> datasourceSettings,
        String resource,
        Map<String, Object> datasetSettings
    ) {
        ValidationException errors = new ValidationException();
        if (EsApiConfig.handles(resource) == false) {
            errors.addValidationError("dataset resource must be esapi://<API path>, got [" + resource + "]");
        }
        Map<String, Object> merged = new HashMap<>();
        if (datasourceSettings != null) {
            datasourceSettings.forEach((k, v) -> merged.put(k, v.rawValue()));
        }
        if (datasetSettings != null) {
            for (Map.Entry<String, Object> e : datasetSettings.entrySet()) {
                if (EsApiConfig.CONFIG_KEYS.contains(e.getKey()) == false) {
                    errors.addValidationError("unknown dataset setting [" + e.getKey() + "] for data source type [es_api]; recognised: "
                        + EsApiConfig.CONFIG_KEYS);
                }
                merged.put(e.getKey(), e.getValue());
            }
        }
        if (errors.validationErrors().isEmpty()) {
            try {
                EsApiConfig.parse(resource, merged);
            } catch (IllegalArgumentException e) {
                errors.addValidationError(e.getMessage());
            }
        }
        if (errors.validationErrors().isEmpty() == false) {
            throw errors;
        }
        return datasetSettings == null ? Map.of() : Map.copyOf(datasetSettings);
    }
}
