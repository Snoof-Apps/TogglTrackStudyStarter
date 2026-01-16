package snoof.widget.toggltrackstarter;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import okhttp3.*;
import org.json.JSONObject;
import java.time.Instant;

public class TogglWorker extends Worker {
    private static final String BASE_URL = "https://api.track.toggl.com/api/v9";
    private final OkHttpClient client = new OkHttpClient();

    public TogglWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        int widgetId = getInputData().getInt("widgetId", -1);

        // --- FIXED: Reading directly from SharedPreferences ---
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("TOGGL_PREFS", Context.MODE_PRIVATE);
        String token = prefs.getString("token_" + widgetId, null);
        int workspaceId = prefs.getInt("ws_" + widgetId, -1);

        if (token == null || workspaceId == -1) return Result.failure();

        try {
            Long runningId = null;
            long startTimeMillis = 0;

            // 1. Check if a timer is currently running
            Request checkReq = new Request.Builder()
                    .url(BASE_URL + "/me/time_entries/current")
                    .addHeader("Authorization", Credentials.basic(token, "api_token"))
                    .build();

            try (Response response = client.newCall(checkReq).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String data = response.body().string();
                    if (!data.equals("null")) {
                        JSONObject json = new JSONObject(data);
                        runningId = json.getLong("id");
                        startTimeMillis = Instant.parse(json.getString("start")).toEpochMilli();
                    }
                }
            }

            // 2. Toggle Logic (Start if nothing running, Stop if found)
            if (runningId != null) {
                stopTimer(token, workspaceId, runningId);
                startTimeMillis = 0; // Signal to widget that it's stopped
            } else {
                startTimer(token, workspaceId);
                startTimeMillis = System.currentTimeMillis(); // Signal start time
            }

            // 3. Update the Widget UI visually
            AppWidgetManager mgr = AppWidgetManager.getInstance(getApplicationContext());
            StudyWidget.updateAppWidget(getApplicationContext(), mgr, widgetId, startTimeMillis);

            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Result.failure();
        }
    }

    private void startTimer(String token, int workspaceId) throws Exception {
        String json = "{\"created_with\":\"Snoof\",\"description\":\"Study\",\"duration\":-1,\"start\":\"" + Instant.now().toString() + "\",\"workspace_id\":" + workspaceId + "}";
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(BASE_URL + "/workspaces/" + workspaceId + "/time_entries")
                .post(body)
                .addHeader("Authorization", Credentials.basic(token, "api_token"))
                .build();
        client.newCall(request).execute();
    }

    private void stopTimer(String token, int workspaceId, long timerId) throws Exception {
        // v9 API requires /workspaces/{workspace_id}/time_entries/{id}/stop
        Request request = new Request.Builder()
                .url(BASE_URL + "/workspaces/" + workspaceId + "/time_entries/" + timerId + "/stop")
                .patch(RequestBody.create("", null))
                .addHeader("Authorization", Credentials.basic(token, "api_token"))
                .build();
        client.newCall(request).execute();
    }
}