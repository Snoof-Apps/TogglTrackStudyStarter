package snoof.widget.toggltrackstarter;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.widget.RemoteViews;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

public class StudyWidget extends AppWidgetProvider {

    public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId, long startTimeMillis) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.study_widget_layout);

        // Logic for Chronometer & Button Text
        if (startTimeMillis > 0) {
            views.setTextViewText(R.id.btn_toggle, "Stop Study");
            // Formula: base = current tick - (current time - start time)
            long base = android.os.SystemClock.elapsedRealtime() - (System.currentTimeMillis() - startTimeMillis);
            views.setChronometer(R.id.widget_chronometer, base, null, true);
        } else {
            views.setTextViewText(R.id.btn_toggle, "Start Study");
            views.setChronometer(R.id.widget_chronometer, android.os.SystemClock.elapsedRealtime(), null, false);
        }

        Intent intent = new Intent(context, StudyWidget.class);
        intent.setAction("TOGGLE_STUDY");
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        views.setOnClickPendingIntent(R.id.btn_toggle, pendingIntent);
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            triggerWorker(context, appWidgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if ("TOGGLE_STUDY".equals(intent.getAction())) {
            int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
            triggerWorker(context, widgetId);
        }
    }

    private void triggerWorker(Context context, int widgetId) {
        Data data = new Data.Builder().putInt("widgetId", widgetId).build();
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(TogglWorker.class)
                .setInputData(data)
                .build();
        WorkManager.getInstance(context).enqueue(work);
    }
}