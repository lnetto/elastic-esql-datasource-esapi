/*
 * Elasticsearch API connector for ES|QL Data Federation.
 */
package org.elasticsearch.xpack.esql.datasource.esapi;

import org.elasticsearch.xpack.esql.core.type.DataType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Against real Elasticsearch 9.5.4 responses (src/test/resources). */
class JsonRowsTests {

    static Object fixture(String name) throws IOException {
        return Json.parse(Files.readString(Path.of("src/test/resources", name)));
    }

    static Map<String, DataType> types(JsonRows.Table t) {
        Map<String, DataType> m = new HashMap<>();
        t.columns().forEach(c -> m.put(c.name(), c.type()));
        return m;
    }

    @Test
    void nodesStatsBecomesOneRowPerNode() throws IOException {
        JsonRows.Table t = JsonRows.toTable(fixture("nodes-stats.json"), null);
        assertEquals(1, t.rows().size());
        Map<String, DataType> types = types(t);
        assertEquals(DataType.KEYWORD, types.get("_key"));
        assertEquals(DataType.LONG, types.get("jvm.mem.heap_used_percent"));
        assertEquals(DataType.KEYWORD, types.get("name"));
        assertTrue(t.rows().get(0).get("_key").toString().length() > 10);
        // explicit wildcard path gives the same rows
        assertEquals(t.rows().size(), JsonRows.toTable(fixture("nodes-stats.json"), "nodes.*").rows().size());
    }

    @Test
    void catIsAList() throws IOException {
        JsonRows.Table t = JsonRows.toTable(fixture("cat-indices.json"), null);
        assertTrue(t.rows().size() > 5);
        assertEquals(DataType.KEYWORD, types(t).get("index"));
        assertEquals(DataType.KEYWORD, types(t).get("docs.count"), "_cat sends numbers as strings");
    }

    @Test
    void singleObjectIsOneRow() throws IOException {
        JsonRows.Table t = JsonRows.toTable(fixture("authenticate.json"), null);
        assertEquals(1, t.rows().size());
        assertEquals("elastic", t.rows().get(0).get("username"));
        assertEquals(List.of("superuser"), t.rows().get(0).get("roles"));
    }

    @Test
    void config() {
        EsApiConfig c = EsApiConfig.parse("esapi://_cat/indices?h=index", Map.of());
        assertEquals("/_cat/indices?h=index&format=json", c.apiPath());
        assertEquals("/_nodes/stats", EsApiConfig.parse("esapi:///_nodes/stats", Map.of()).apiPath());
        assertEquals("{\"size\":0}", EsApiConfig.parse("esapi://x/_search", Map.of("body", Map.of("size", 0))).body());
        assertNull(EsApiConfig.parse("esapi://x", Map.of("path", " ")).path());
        assertThrows(IllegalArgumentException.class, () -> EsApiConfig.parse("http://x", Map.of()));
        // * and ? are percent-encoded in resources (ES|QL reads them as globs) and decoded here
        assertEquals("/*/_ilm/explain?expand_wildcards=all",
            EsApiConfig.parse("esapi://%2A/_ilm/explain", Map.of("params", Map.of("expand_wildcards", "all"))).apiPath());
        assertEquals("/_security/api_key?owner=true", EsApiConfig.parse("esapi://_security/api_key%3Fowner=true", Map.of()).apiPath());
        assertThrows(IllegalArgumentException.class, () -> EsApiConfig.parse("esapi://x", Map.of("params", "a=b")));
    }
}
