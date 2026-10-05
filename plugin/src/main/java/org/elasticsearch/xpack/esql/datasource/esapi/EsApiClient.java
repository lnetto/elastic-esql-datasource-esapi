/*
 * Elasticsearch API connector for ES|QL Data Federation.
 */
package org.elasticsearch.xpack.esql.datasource.esapi;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Calls an Elasticsearch REST endpoint of this cluster as the user running the query: the request is
 * dispatched in memory through the node's REST controller, in the thread context that already carries
 * the user's authentication. Elasticsearch authorises it exactly as if they had made it with curl;
 * no credentials are stored or forwarded.
 */
final class EsApiClient {

    /** A response fetched while planning, reused by the same user's execution right after (one call per query). */
    private record Recent(Object json, long expiresAt) {}

    private static final Map<String, Recent> RECENT = new ConcurrentHashMap<>();
    static final long RECENT_MILLIS = 10_000;

    private EsApiClient() {}

    /** The parsed JSON response; throws with Elasticsearch's own error (e.g. security_exception) on failure. */
    static Object call(EsApiConfig config, boolean forPlanning) {
        // per caller (a digest of who they are, so one user never gets another's response) and per call
        String identity = EsApiNode.identity();
        String key = sha256(String.valueOf(identity)) + "|" + config.apiPath() + "|" + config.body();
        long now = System.currentTimeMillis();
        if (forPlanning == false) {
            Recent r = RECENT.remove(key);
            if (r != null && r.expiresAt() > now) {
                return r.json();
            }
        }
        InternalRest.Response resp = InternalRest.get(EsApiNode.restController(), EsApiNode.threadContext(), config.apiPath(), config.body(),
            config.timeout());
        if (resp.status() / 100 != 2) {
            throw new IllegalStateException("GET " + config.apiPath() + " answered HTTP " + resp.status() + ": " + reason(resp.body()));
        }
        Object json = Json.parse(resp.body());
        if (forPlanning) {
            RECENT.put(key, new Recent(json, now + RECENT_MILLIS));
        }
        return json;
    }

    private static String sha256(String s) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Elasticsearch's error: "type: reason" when the body is an error document. */
    static String reason(String body) {
        try {
            if (Json.parse(body) instanceof Map<?, ?> m && m.get("error") instanceof Map<?, ?> err) {
                return err.get("type") + ": " + err.get("reason");
            }
        } catch (IllegalArgumentException e) {
            // not JSON
        }
        return body.length() > 500 ? body.substring(0, 500) + "…" : body;
    }
}
