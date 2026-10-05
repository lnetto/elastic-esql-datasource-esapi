/*
 * Elasticsearch API connector for ES|QL Data Federation.
 */
package org.elasticsearch.xpack.esql.datasource.esapi;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A dataset: which Elasticsearch API to call and where the rows are in its JSON.
 *
 * <pre>
 *   resource  esapi://_nodes/stats              path of the API, on this cluster; percent-encode * and ? (%2A, %3F):
 *                                               ES|QL would read them as a file pattern
 *   params    {"bytes": "b"}                    query-string parameters
 *   body      {"query": {...}}                  optional request body (sent with GET, as _search accepts)
 *   path      nodes.*                           where the rows are (default: found automatically)
 * </pre>
 */
record EsApiConfig(String location, String apiPath, String body, String path, Duration timeout) {

    static final String TYPE = "es_api";
    static final String SCHEME = "esapi";
    static final String LOCATION_KEY = "location";
    static final Set<String> CONFIG_KEYS = Set.of("body", "path", "params", "timeout");

    static boolean handles(String location) {
        return location != null && location.startsWith(SCHEME + "://") && location.length() > (SCHEME + "://").length();
    }

    static Map<String, Object> effective(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new HashMap<>();
        if (config.get("_datasource") instanceof Map<?, ?> ds) {
            for (Map.Entry<?, ?> e : ds.entrySet()) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        for (Map.Entry<String, Object> e : config.entrySet()) {
            if (e.getKey().startsWith("_") == false) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    static EsApiConfig parse(String location, Map<String, Object> rawConfig) {
        Map<String, Object> config = effective(rawConfig);
        if (location == null) {
            location = Objects.toString(config.get(LOCATION_KEY), null);
        }
        if (handles(location) == false) {
            throw new IllegalArgumentException("es_api dataset resource must be esapi://<API path>, e.g. esapi://_nodes/stats, got ["
                + location + "]");
        }
        // %2A / %3F etc. are decoded here: ES|QL treats a literal * or ? in a resource as a file glob
        String api = "/" + java.net.URLDecoder.decode(location.substring((SCHEME + "://").length()).replace("+", "%2B"),
            java.nio.charset.StandardCharsets.UTF_8).replaceFirst("^/+", "");
        if (config.get("params") instanceof Map<?, ?> params && params.isEmpty() == false) {
            StringBuilder q = new StringBuilder();
            for (Map.Entry<?, ?> e : params.entrySet()) {
                q.append(q.length() == 0 && api.contains("?") == false ? "?" : "&")
                    .append(java.net.URLEncoder.encode(String.valueOf(e.getKey()), java.nio.charset.StandardCharsets.UTF_8))
                    .append('=')
                    .append(java.net.URLEncoder.encode(String.valueOf(e.getValue()), java.nio.charset.StandardCharsets.UTF_8));
            }
            api += q;
        } else if (config.get("params") != null && config.get("params") instanceof Map<?, ?> == false) {
            throw new IllegalArgumentException("[params] must be an object of query-string parameters, got [" + config.get("params") + "]");
        }
        // _cat answers text unless asked for JSON
        if (api.startsWith("/_cat") && api.contains("format=") == false) {
            api += (api.contains("?") ? "&" : "?") + "format=json";
        }
        Object b = config.get("body");
        String body = b == null ? null : b instanceof Map<?, ?> || b instanceof List<?> ? Json.write(b) : b.toString();
        if (body != null && body.isBlank()) {
            body = null;
        }
        String path = Objects.toString(config.get("path"), null);
        long timeout = 60;
        Object t = config.get("timeout");
        if (t != null) {
            try {
                timeout = t instanceof Number n ? n.longValue() : Long.parseLong(t.toString().trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("[timeout] must be a number of seconds, got [" + t + "]");
            }
        }
        return new EsApiConfig(location, api, body, path == null || path.isBlank() ? null : path, Duration.ofSeconds(Math.max(1, timeout)));
    }

    static Map<String, Object> resolved(String location, Map<String, Object> rawConfig) {
        Map<String, Object> config = effective(rawConfig);
        Map<String, Object> out = new HashMap<>();
        for (String key : CONFIG_KEYS) {
            Object v = config.get(key);
            if (v != null) {
                out.put(key, v);
            }
        }
        out.put(LOCATION_KEY, location);
        return out;
    }
}
