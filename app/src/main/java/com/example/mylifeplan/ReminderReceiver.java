package com.example.mylifeplan;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.NotificationCompat;

public class ReminderReceiver extends BroadcastReceiver {

    public static final String CHANNEL_ID = "cat_deadlines_channel";

    @Override
    public void onReceive(Context context, Intent intent) {
        String taskTitle = intent.getStringExtra("task_title");
        if (taskTitle == null || taskTitle.isEmpty()) {
            taskTitle = "Goal";
        }

        String messageType = intent.getStringExtra("msg_type");
        if (messageType == null) {
            messageType = "";
        }

        String contentText;
        switch (messageType) {
            case "one_hour":
                contentText = "Через 1 час у вас запланировано: \"" + taskTitle + "\". Не откладывайте! ⭐";
                break;
            case "thirty_mins":
                contentText = "До дедлайна \"" + taskTitle + "\" осталось 30 минут! Лучше приготовиться или начать раньше 🐾";
                break;
            case "at_deadline":
                contentText = "Время вышло! Наступил дедлайн по \"" + taskTitle + "\"! ⏰🔔";
                break;
            default:
                contentText = "Котик напоминает: проверьте ваши планы на сегодня! 🐾";
                break;
        }

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Kitten Deadlines & Focus",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Reminders for your tasks and deadlines");
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.star_coin_ico)
                .setContentTitle("MyLifePlan: Напоминание 🐾")
                .setContentText(contentText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(contentText))
                .setAutoCancel(true)
                .setSound(soundUri)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        if (manager != null) {
            manager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }
}