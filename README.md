# elastic-esql-datasource-esapi

An **ES|QL Data Federation** connector that turns Elasticsearch's own REST APIs into tables.
`FROM <dataset>` calls a cluster API **as the user running the query** and returns the JSON as rows.
No credentials to configure.

```esql
FROM esapi_cat_indices
| EVAL mb = ROUND(TO_LONG(store.size) / 1048576.0, 1)
| SORT mb DESC | KEEP index, health, mb | LIMIT 5
```

> **Experimental.** ES|QL Data Federation is experimental in Elasticsearch 9.5 and this plugin uses its
> internal SPI. Each zip works with exactly one Elasticsearch version. Community project, not an official
> Elastic product.

## 1. Install the plugin

Use `dist/esql-datasource-esapi-0.1.0-es9.5.4.zip`, or build it:

```bash
./build-plugin.sh    # -> dist/esql-datasource-esapi-0.1.0-es9.5.4.zip
```

**Self-managed**, on every node, then restart them one by one:

```bash
bin/elasticsearch-plugin install file:///path/to/esql-datasource-esapi-0.1.0-es9.5.4.zip
echo 'esql.federation.enabled: true' >> config/elasticsearch.yml
```

**Docker**, with the plugin baked into the image:

```bash
docker build -f docker/Dockerfile -t elasticsearch-esql-esapi:9.5.4 .
```

Run it with `-e esql.federation.enabled=true`.

**Elastic Cloud Hosted:**

1. **Deployments → Extensions → Create extension**: type *plugin*, version `9.5.4`, upload the zip.
2. **Edit deployment → Elasticsearch → Manage plugins and extensions**: enable it.
3. **Elasticsearch user settings**: `esql.federation.enabled: true`. Save (rolling restart).

Needs a licence with ES|QL Data Federation (Enterprise or trial). Check with `GET _cat/plugins`.

## 2. Create the data source

Kibana's Data Federation UI can't create this type, so use **Dev Tools**:

```
PUT _query/data_source/esapi
{"type":"es_api","description":"Elastic API Endpoint"}
```

It has no settings: every call runs as whoever runs the query.

## 3. Create the datasets

| Dataset | API | What you get |
|---|---|---|
| `esapi_cluster_health` | `_cluster/health` | Cluster status, node and shard counts (1 row) |
| `esapi_cluster_stats` | `_cluster/stats` | Cluster-wide index, node, JVM and OS totals (1 row) |
| `esapi_cluster_settings` | `_cluster/settings` | Persistent and transient cluster settings (1 row) |
| `esapi_cluster_pending_tasks` | `_cluster/pending_tasks` | Cluster-state changes waiting to run. ⚪ *Usually empty* |
| `esapi_health_report` | `_health_report` | One row per health indicator (disk, shards, ILM, …) |
| `esapi_license` | `_license` | Licence type, status and expiry (1 row) |
| `esapi_nodes` | `_nodes` | Node info: roles, versions, settings, plugins |
| `esapi_nodes_stats` | `_nodes/stats` | All node statistics (very wide; use `KEEP`) |
| `esapi_nodes_stats_jvm_os` | `_nodes/stats/jvm,os` | Node JVM heap/GC and OS CPU/memory |
| `esapi_cat_indices` | `_cat/indices` | One row per index: health, docs, size in bytes |
| `esapi_cat_shards` | `_cat/shards` | One row per shard: state, node, size |
| `esapi_cat_nodes` | `_cat/nodes` | One row per node: heap, RAM, CPU, load, roles |
| `esapi_cat_allocation` | `_cat/allocation` | Disk use and shard count per node |
| `esapi_cat_health` | `_cat/health` | Cluster health as a single row |
| `esapi_cat_aliases` | `_cat/aliases` | One row per alias |
| `esapi_cat_thread_pool` | `_cat/thread_pool` | Active, queued and rejected per thread pool per node |
| `esapi_cat_recovery` | `_cat/recovery` | Shard recoveries, including finished ones |
| `esapi_cat_segments` | `_cat/segments` | One row per Lucene segment |
| `esapi_cat_plugins` | `_cat/plugins` | Plugins installed on each node |
| `esapi_cat_master` | `_cat/master` | The elected master node |
| `esapi_cat_nodeattrs` | `_cat/nodeattrs` | Custom node attributes |
| `esapi_cat_fielddata` | `_cat/fielddata` | Fielddata memory per field per node. ⚪ *Empty until fielddata is loaded* |
| `esapi_cat_templates` | `_cat/templates` | Index templates (summary) |
| `esapi_cat_component_templates` | `_cat/component_templates` | Component templates (summary) |
| `esapi_cat_tasks` | `_cat/tasks` | Running tasks (summary) |
| `esapi_cat_repositories` | `_cat/repositories` | Snapshot repositories |
| `esapi_cat_transforms` | `_cat/transforms` | Transforms (summary). ⚪ *Empty without transforms* |
| `esapi_cat_ml_jobs` | `_cat/ml/anomaly_detectors` | ML anomaly detection jobs (summary). ⚪ *Empty without ML jobs* |
| `esapi_cat_ml_datafeeds` | `_cat/ml/datafeeds` | ML datafeeds (summary). ⚪ *Empty without datafeeds* |
| `esapi_index_stats` | `_stats` | One row per index: full indexing, search and store stats |
| `esapi_data_streams` | `_data_stream` | One row per data stream: backing indices, lifecycle, template |
| `esapi_index_templates` | `_index_template` | Index templates in full (wide) |
| `esapi_component_templates` | `_component_template` | Component templates in full (wide) |
| `esapi_ingest_pipelines` | `_ingest/pipeline` | One row per ingest pipeline, key in `_key` |
| `esapi_ilm_explain` | `*/_ilm/explain` | ILM phase, action and step for every index |
| `esapi_ilm_policies` | `_ilm/policy` | One row per ILM policy |
| `esapi_slm_policies` | `_slm/policy` | One row per snapshot lifecycle policy |
| `esapi_snapshot_repositories` | `_snapshot` | Snapshot repositories with their settings |
| `esapi_tasks` | `_tasks` | Running tasks in detail |
| `esapi_transforms` | `_transform` | Transform configurations. ⚪ *Empty without transforms* |
| `esapi_transform_stats` | `_transform/_stats` | Transform state and progress. ⚪ *Empty without transforms* |
| `esapi_ml_jobs` | `_ml/anomaly_detectors` | ML anomaly detection job configurations. ⚪ *Empty without ML jobs* |
| `esapi_ml_datafeeds` | `_ml/datafeeds` | ML datafeed configurations. ⚪ *Empty without datafeeds* |
| `esapi_security_authenticate` | `_security/_authenticate` | Who you are: user, roles, realm (1 row) |
| `esapi_security_api_keys` | `_security/api_key` | Your own API keys |
| `esapi_security_users` | `_security/user` | Native users |
| `esapi_security_roles` | `_security/role` | Roles, name in `_key` |
| `esapi_kibana_tasks` | `.kibana_task_manager/_search` | Kibana Task Manager tasks (up to 200) |

⚪ Can be empty: the dataset then has no rows and a single `_empty` column ([Empty datasets](#empty-datasets)).

Paste into Dev Tools and run all. Each dataset is one API; the name follows the path
(`_cat/indices` → `esapi_cat_indices`).

```
PUT _query/dataset/esapi_cluster_health
{"data_source":"esapi","resource":"esapi://_cluster/health","description":"Cluster status, node and shard counts (1 row)"}
PUT _query/dataset/esapi_cluster_stats
{"data_source":"esapi","resource":"esapi://_cluster/stats","description":"Cluster-wide index, node, JVM and OS totals (1 row)"}
PUT _query/dataset/esapi_cluster_settings
{"data_source":"esapi","resource":"esapi://_cluster/settings","description":"Persistent and transient cluster settings (1 row)","settings":{"params":{"flat_settings":true}}}
PUT _query/dataset/esapi_cluster_pending_tasks
{"data_source":"esapi","resource":"esapi://_cluster/pending_tasks","description":"Cluster-state changes waiting to run"}
PUT _query/dataset/esapi_health_report
{"data_source":"esapi","resource":"esapi://_health_report","description":"One row per health indicator (disk, shards, ILM, …)","settings":{"path":"indicators.*"}}
PUT _query/dataset/esapi_license
{"data_source":"esapi","resource":"esapi://_license","description":"Licence type, status and expiry (1 row)","settings":{"path":"license"}}
PUT _query/dataset/esapi_nodes
{"data_source":"esapi","resource":"esapi://_nodes","description":"Node info: roles, versions, settings, plugins"}
PUT _query/dataset/esapi_nodes_stats
{"data_source":"esapi","resource":"esapi://_nodes/stats","description":"All node statistics (very wide; use KEEP)"}
PUT _query/dataset/esapi_nodes_stats_jvm_os
{"data_source":"esapi","resource":"esapi://_nodes/stats/jvm%2Cos","description":"Node JVM heap/GC and OS CPU/memory"}
PUT _query/dataset/esapi_cat_indices
{"data_source":"esapi","resource":"esapi://_cat/indices","description":"One row per index: health, docs, size in bytes","settings":{"params":{"bytes":"b","expand_wildcards":"all"}}}
PUT _query/dataset/esapi_cat_shards
{"data_source":"esapi","resource":"esapi://_cat/shards","description":"One row per shard: state, node, size","settings":{"params":{"bytes":"b"}}}
PUT _query/dataset/esapi_cat_nodes
{"data_source":"esapi","resource":"esapi://_cat/nodes","description":"One row per node: heap, RAM, CPU, load, roles","settings":{"params":{"bytes":"b","full_id":true}}}
PUT _query/dataset/esapi_cat_allocation
{"data_source":"esapi","resource":"esapi://_cat/allocation","description":"Disk use and shard count per node","settings":{"params":{"bytes":"b"}}}
PUT _query/dataset/esapi_cat_health
{"data_source":"esapi","resource":"esapi://_cat/health","description":"Cluster health as a single row"}
PUT _query/dataset/esapi_cat_aliases
{"data_source":"esapi","resource":"esapi://_cat/aliases","description":"One row per alias"}
PUT _query/dataset/esapi_cat_thread_pool
{"data_source":"esapi","resource":"esapi://_cat/thread_pool","description":"Active, queued and rejected per thread pool per node"}
PUT _query/dataset/esapi_cat_recovery
{"data_source":"esapi","resource":"esapi://_cat/recovery","description":"Shard recoveries, including finished ones","settings":{"params":{"bytes":"b","active_only":false}}}
PUT _query/dataset/esapi_cat_segments
{"data_source":"esapi","resource":"esapi://_cat/segments","description":"One row per Lucene segment","settings":{"params":{"bytes":"b"}}}
PUT _query/dataset/esapi_cat_plugins
{"data_source":"esapi","resource":"esapi://_cat/plugins","description":"Plugins installed on each node"}
PUT _query/dataset/esapi_cat_master
{"data_source":"esapi","resource":"esapi://_cat/master","description":"The elected master node"}
PUT _query/dataset/esapi_cat_nodeattrs
{"data_source":"esapi","resource":"esapi://_cat/nodeattrs","description":"Custom node attributes"}
PUT _query/dataset/esapi_cat_fielddata
{"data_source":"esapi","resource":"esapi://_cat/fielddata","description":"Fielddata memory per field per node","settings":{"params":{"bytes":"b"}}}
PUT _query/dataset/esapi_cat_templates
{"data_source":"esapi","resource":"esapi://_cat/templates","description":"Index templates (summary)"}
PUT _query/dataset/esapi_cat_component_templates
{"data_source":"esapi","resource":"esapi://_cat/component_templates","description":"Component templates (summary)"}
PUT _query/dataset/esapi_cat_tasks
{"data_source":"esapi","resource":"esapi://_cat/tasks","description":"Running tasks (summary)"}
PUT _query/dataset/esapi_cat_repositories
{"data_source":"esapi","resource":"esapi://_cat/repositories","description":"Snapshot repositories"}
PUT _query/dataset/esapi_cat_transforms
{"data_source":"esapi","resource":"esapi://_cat/transforms","description":"Transforms (summary)"}
PUT _query/dataset/esapi_cat_ml_jobs
{"data_source":"esapi","resource":"esapi://_cat/ml/anomaly_detectors","description":"ML anomaly detection jobs (summary)","settings":{"params":{"bytes":"b"}}}
PUT _query/dataset/esapi_cat_ml_datafeeds
{"data_source":"esapi","resource":"esapi://_cat/ml/datafeeds","description":"ML datafeeds (summary)"}
PUT _query/dataset/esapi_index_stats
{"data_source":"esapi","resource":"esapi://_stats","description":"One row per index: full indexing, search and store stats","settings":{"params":{"expand_wildcards":"all"},"path":"indices.*"}}
PUT _query/dataset/esapi_data_streams
{"data_source":"esapi","resource":"esapi://_data_stream","description":"One row per data stream: backing indices, lifecycle, template","settings":{"params":{"expand_wildcards":"all"}}}
PUT _query/dataset/esapi_index_templates
{"data_source":"esapi","resource":"esapi://_index_template","description":"Index templates in full (wide)"}
PUT _query/dataset/esapi_component_templates
{"data_source":"esapi","resource":"esapi://_component_template","description":"Component templates in full (wide)"}
PUT _query/dataset/esapi_ingest_pipelines
{"data_source":"esapi","resource":"esapi://_ingest/pipeline","description":"One row per ingest pipeline, key in _key","settings":{"path":"*"}}
PUT _query/dataset/esapi_ilm_explain
{"data_source":"esapi","resource":"esapi://%2A/_ilm/explain","description":"ILM phase, action and step for every index","settings":{"params":{"expand_wildcards":"all"},"path":"indices.*"}}
PUT _query/dataset/esapi_ilm_policies
{"data_source":"esapi","resource":"esapi://_ilm/policy","description":"One row per ILM policy","settings":{"path":"*"}}
PUT _query/dataset/esapi_slm_policies
{"data_source":"esapi","resource":"esapi://_slm/policy","description":"One row per snapshot lifecycle policy","settings":{"path":"*"}}
PUT _query/dataset/esapi_snapshot_repositories
{"data_source":"esapi","resource":"esapi://_snapshot","description":"Snapshot repositories with their settings","settings":{"path":"*"}}
PUT _query/dataset/esapi_tasks
{"data_source":"esapi","resource":"esapi://_tasks","description":"Running tasks in detail","settings":{"params":{"group_by":"none","detailed":true}}}
PUT _query/dataset/esapi_transforms
{"data_source":"esapi","resource":"esapi://_transform","description":"Transform configurations","settings":{"params":{"size":1000},"path":"transforms"}}
PUT _query/dataset/esapi_transform_stats
{"data_source":"esapi","resource":"esapi://_transform/_stats","description":"Transform state and progress","settings":{"params":{"size":1000},"path":"transforms"}}
PUT _query/dataset/esapi_ml_jobs
{"data_source":"esapi","resource":"esapi://_ml/anomaly_detectors","description":"ML anomaly detection job configurations","settings":{"path":"jobs"}}
PUT _query/dataset/esapi_ml_datafeeds
{"data_source":"esapi","resource":"esapi://_ml/datafeeds","description":"ML datafeed configurations","settings":{"path":"datafeeds"}}
PUT _query/dataset/esapi_security_authenticate
{"data_source":"esapi","resource":"esapi://_security/_authenticate","description":"Who you are: user, roles, realm (1 row)"}
PUT _query/dataset/esapi_security_api_keys
{"data_source":"esapi","resource":"esapi://_security/api_key","description":"Your own API keys","settings":{"params":{"owner":true}}}
PUT _query/dataset/esapi_security_users
{"data_source":"esapi","resource":"esapi://_security/user","description":"Native users","settings":{"path":"*"}}
PUT _query/dataset/esapi_security_roles
{"data_source":"esapi","resource":"esapi://_security/role","description":"Roles, name in _key","settings":{"path":"*"}}
PUT _query/dataset/esapi_kibana_tasks
{"data_source":"esapi","resource":"esapi://.kibana_task_manager/_search","description":"Kibana Task Manager tasks (up to 200)","settings":{"body":{"size":200}}}
```

Then `FROM esapi_cluster_health`, `FROM esapi_nodes_stats_jvm_os`, `FROM esapi_cat_shards`, …

### Empty datasets

A dataset whose API has nothing to return (no transforms, no ML jobs, no pending tasks) gives no rows
and a single null `_empty` column: the columns come from the response, so they're unknown until there
is data. `COUNT(*)` works on it; naming a column such as `id` fails until there is data.

```esql
FROM esapi_transforms                          // _empty, no rows
FROM esapi_transforms | STATS n = COUNT(*)     // n = 0
```

### Your own

```
PUT _query/dataset/esapi_cat_shards_logs
{"data_source":"esapi","resource":"esapi://_cat/shards/logs-%2A","description":"Shards of the logs-* indices","settings":{"params":{"bytes":"b"}}}
```

| setting | |
|---|---|
| `resource` | `esapi://<API path>`. Percent-encode `*` `?` `,` (`%2A` `%3F` `%2C`) |
| `params` | query-string parameters |
| `body` | request body, sent with GET (for `_search`) |
| `path` | where the rows are, if not found automatically (dots, `*` = every value, key in `_key`) |
| `timeout` | seconds, default `60` |

Rows are found automatically: one per element of a list (any `_cat`), per entry of an object of objects
(`_nodes/stats`), per hit of a search, per row of an ES|QL response; anything else is one row. Nested
objects become dotted columns. `_cat` returns numbers as strings, so `TO_LONG` them.

## 4. Access

Datasets are authorised by name like indices. One role entry covers them all:

```
"indices": [{ "names": ["esapi_*"], "privileges": ["read"] }]
```

Then each API checks its own privileges as usual: a user without `monitor` gets the same
`security_exception` from `FROM esapi_nodes_stats` as from `GET _nodes/stats`. Only GET is ever sent.

## Repository

| path | |
|---|---|
| `plugin/` | the plugin (Java 21, ES 9.5.4) and its tests |
| `scripts/extract-es-jars.sh` | pulls the SPI jars out of the stock ES image |
