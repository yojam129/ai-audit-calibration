# Sentinel Nacos rules

Publish the three JSON files to Nacos using group `AI_AUDIT` (or `NACOS_GROUP`) and
their filenames as Data IDs. Keep namespace consistent with `NACOS_NAMESPACE`.

- `gateway-service-gw-api-group.json`: stable API groups for import and AI endpoints.
- `gateway-service-gw-flow.json`: gateway QPS limits.
- `gateway-service-degrade.json`: slow-call circuit-breaking examples.

The values are safe demo baselines, not production capacity claims. Load testing must determine
production thresholds. Secrets and Nacos credentials remain environment variables.

Publish or refresh all rules from the repository root:

```powershell
.\scripts\Publish-SentinelRules.ps1 -EnvironmentFile .env.local
```
