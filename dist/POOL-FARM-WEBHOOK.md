# BTFGFARM submission webhook (pool patch)

Patched signum-pool posts **every accepted** BTFGFARM plot submission to BTFGPOOL — not only when the submission beats pool-wide best.

## Files changed

- `src/main/java/burst/pool/pool/FarmSubmissionNotifier.java` (new)
- `src/main/java/burst/pool/pool/Pool.java`
- `src/main/java/burst/pool/storage/config/Props.java`
- `dist/pool.properties` (new keys)

## pool.properties

```properties
farmSubmissionAccountIds = 10183949831403529646
farmSubmissionWebhookUrl = http://127.0.0.1:9080/farm-pool-submission.php
farmSubmissionWebhookSecret = YOUR_SHARED_SECRET
```

Leave `farmSubmissionWebhookUrl` empty to disable.

## Build (recommended)

```bash
cd signum-pool-2.2.1
./gradlew shadowJar
cp build/libs/signum-pool-*.jar /root/Signum/Pool/signum-pool-v2.2.1/signum-pool.jar
```

Restart the pool. Log should show: `Farm submission webhook enabled → ...`

## JAR-only patch (no Gradle)

Not recommended — you must compile `FarmSubmissionNotifier.class` and replace `Pool.class` inside the fat JAR, matching Java 8 bytecode. Use `./gradlew shadowJar` when possible.

## BTFGPOOL receiver

1. Set `pool_submission_webhook_secret` in `farm-secrets.php` (same as pool.properties).
2. Run farm API: `cd BTFGPOOL/web && ./start-farm-api.sh` (port 9080).
3. Submissions append to `data/farm-pool-submissions.json`.

## POST body (JSON)

| Field | Meaning |
|-------|---------|
| `height` | Block height (round) |
| `accountId` | Unsigned BTFGFARM id |
| `accountRS` | Reed-Solomon address |
| `nonce` | Plot nonce (decimal string) |
| `deadlineRaw` | Calculated deadline before PoC+ adjustment |
| `deadlineEffective` | Deadline after PoC+ / commitment factor |
| `baseTarget` | Round base target |
| `improvedPoolBest` | true if this submission also beat pool-wide best |
| `userAgent` | Miner user-agent header |
| `receivedAt` | Unix seconds |

Header: `X-Farm-Webhook-Token: <secret>`
