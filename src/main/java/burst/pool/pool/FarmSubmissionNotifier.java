package burst.pool.pool;

import burst.pool.storage.config.PropertyService;
import burst.pool.storage.config.Props;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import signumj.entity.SignumAddress;
import signumj.entity.response.MiningInfo;

import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Fire-and-forget HTTP POST of every accepted farm-account submission to BTFGPOOL (or any webhook).
 * Configured via pool.properties: farmSubmissionWebhookUrl, farmSubmissionAccountIds, farmSubmissionWebhookSecret.
 */
public class FarmSubmissionNotifier {
    private static final Logger logger = LoggerFactory.getLogger(FarmSubmissionNotifier.class);

    private final String webhookUrl;
    private final String webhookSecret;
    private final Set<String> farmAccountIds;
    private final Gson gson = new Gson();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "farm-submission-webhook");
        t.setDaemon(true);
        return t;
    });

    public FarmSubmissionNotifier(PropertyService propertyService) {
        webhookUrl = trimToNull(propertyService.getString(Props.farmSubmissionWebhookUrl));
        webhookSecret = trimToNull(propertyService.getString(Props.farmSubmissionWebhookSecret));
        String ids = propertyService.getString(Props.farmSubmissionAccountIds);
        farmAccountIds = parseAccountIds(ids);
        if (webhookUrl != null) {
            logger.info("Farm submission webhook enabled → {} ({} account id(s))", webhookUrl, farmAccountIds.size());
        }
    }

    public boolean isEnabled() {
        return webhookUrl != null && !farmAccountIds.isEmpty();
    }

    public void notifyIfFarmSubmission(Submission submission, MiningInfo miningInfo, BigInteger deadlineRaw,
            BigInteger deadlineEffective, String userAgent, boolean improvedPoolBest) {
        if (!isEnabled() || submission == null || submission.getMiner() == null) {
            return;
        }
        String accountId = submission.getMiner().getID();
        if (!farmAccountIds.contains(accountId)) {
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("height", Long.toUnsignedString(miningInfo.getHeight()));
        body.addProperty("accountId", accountId);
        body.addProperty("accountRS", submission.getMiner().getFullAddress());
        body.addProperty("nonce", submission.getNonce().toString());
        body.addProperty("deadlineRaw", deadlineRaw.toString());
        body.addProperty("deadlineEffective", deadlineEffective.toString());
        body.addProperty("baseTarget", Long.toUnsignedString(miningInfo.getBaseTarget()));
        body.addProperty("improvedPoolBest", improvedPoolBest);
        body.addProperty("userAgent", userAgent == null ? "" : userAgent);
        body.addProperty("receivedAt", System.currentTimeMillis() / 1000);
        String payload = gson.toJson(body);
        executor.execute(() -> postQuietly(payload));
    }

    private void postQuietly(String payload) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(webhookUrl).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("User-Agent", "signum-pool-farm-webhook/1.0");
            if (webhookSecret != null) {
                conn.setRequestProperty("X-Farm-Webhook-Token", webhookSecret);
            }
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(bytes);
            }
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                logger.warn("Farm submission webhook HTTP {} (url={})", code, webhookUrl);
            }
            conn.disconnect();
        } catch (Exception e) {
            logger.warn("Farm submission webhook failed: {}", e.toString());
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Set<String> parseAccountIds(String value) {
        if (value == null || value.trim().isEmpty()) {
            return new HashSet<>();
        }
        return Arrays.stream(value.split("[,;\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}
