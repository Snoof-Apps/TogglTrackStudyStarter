package snoof.widget.toggltrackstarter;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import okhttp3.*;
import org.json.JSONObject;

public class WidgetSettingsActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "TOGGL_PREFS";
    int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    // --- HELPER METHODS FOR THE WORKER ---
    public static String getToken(Context context, int widgetId) {
        return context.getSharedPreferences(PREFS_NAME, 0).getString("token_" + widgetId, null);
    }

    public static int getWorkspaceId(Context context, int widgetId) {
        return context.getSharedPreferences(PREFS_NAME, 0).getInt("ws_" + widgetId, -1);
    }
    // -------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        setContentView(R.layout.activity_settings);

        mAppWidgetId = getIntent().getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        EditText etToken = findViewById(R.id.et_api_token);
        findViewById(R.id.btn_save_manual).setOnClickListener(v -> {
            String token = etToken.getText().toString().trim();
            if (!token.isEmpty()) {
                fetchProfileAndSave(token);
            } else {
                Toast.makeText(this, "Please paste a token", Toast.LENGTH_SHORT).show();
            }
        });

        checkExistingAccount();
    }

    private void fetchProfileAndSave(String token) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url("https://api.track.toggl.com/api/v9/me")
                .addHeader("Authorization", Credentials.basic(token, "api_token"))
                .build();

        new Thread(() -> {
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    JSONObject json = new JSONObject(response.body().string());

                    String name = json.getString("fullname");
                    String imageUrl = json.optString("image_url", "");
                    // Get the default workspace ID for this user
                    int workspaceId = json.getInt("default_workspace_id");

                    saveData(token, name, imageUrl, workspaceId);

                    runOnUiThread(() -> {
                        showProfile(name, imageUrl);
                        Toast.makeText(this, "Connected: " + name, Toast.LENGTH_SHORT).show();
                        findViewById(R.id.profile_section).postDelayed(this::finishSetup, 1500);
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "Invalid Token!", Toast.LENGTH_LONG).show());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void saveData(String token, String name, String image, int wsId) {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, 0).edit();
        editor.putString("token_" + mAppWidgetId, token);
        editor.putString("name_" + mAppWidgetId, name);
        editor.putString("image_" + mAppWidgetId, image);
        editor.putInt("ws_" + mAppWidgetId, wsId); // Saved for the Worker to use
        editor.apply();
    }

    private void showProfile(String name, String url) {
        findViewById(R.id.profile_section).setVisibility(View.VISIBLE);
        ((TextView)findViewById(R.id.tv_username)).setText(name);
        if (url != null && !url.isEmpty()) {
            Glide.with(this).load(url).circleCrop().into((ImageView)findViewById(R.id.iv_profile_pic));
        }
    }

    private void checkExistingAccount() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, 0);
        String name = prefs.getString("name_" + mAppWidgetId, null);
        if (name != null) {
            showProfile(name, prefs.getString("image_" + mAppWidgetId, ""));
        }
    }

    private void finishSetup() {
        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
        setResult(RESULT_OK, resultValue);

        // Refresh the widget immediately with the new data
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        StudyWidget.updateAppWidget(this, appWidgetManager, mAppWidgetId, 0);

        finish();
    }
}