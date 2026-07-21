# XXL-Job setup

Create the nightly truth-feedback training task in XXL-Job Admin with these values:

- Executor: `ai-audit-scheduler`
- JobHandler: `aiTruthIncrementalTrainingJob`
- Schedule type: `CRON`
- Cron: `0 0 2 * * ?`
- Routing strategy: `FIRST`
- Blocking strategy: `SERIAL_EXECUTION`
- Timeout: `1800` seconds
- Retry count: `1`
- Job parameter: leave empty

An empty parameter makes `signal-service` select the previous calendar day in
`AI_TRAINING_ZONE` (default `Asia/Shanghai`): yesterday `00:00:00` inclusive through today
`00:00:00` exclusive. Only pending feedback where AI disagreed with an archived final truth is
sent to training. Feedback keys and the derived training key make retries idempotent.

`AI_TRAINING_SCHEDULE_ENABLED` defaults to `false` so the Spring fallback does not duplicate the
XXL-Job schedule. Set it to `true` only when XXL-Job Admin is unavailable.
