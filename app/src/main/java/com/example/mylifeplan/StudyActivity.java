package com.example.mylifeplan;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class StudyActivity extends AppCompatActivity {

    public static class StudyTask {
        public String id;
        public String title;
        public String notes;
        public String deadline;
        public long deadlineEpochMillis = 0; // Точное время дедлайна в миллисекундах
        public int targetMinutesPerDay;
        public String[] reminderTimes = new String[7]; // e.g. "10:00" для Mo..Su
        public boolean soundEnabled = true;

        public StudyTask(String id, String title, String notes, String deadline, int targetMinutes) {
            this.id = id;
            this.title = title;
            this.notes = notes;
            this.deadline = deadline;
            this.targetMinutesPerDay = targetMinutes;
        }
    }

    private TextView tvCoinsScore;
    private TextView tvCalendarMonth;
    private LinearLayout daysRowContainer;
    private RecyclerView rvBlocks;

    private final List<StudyTask> taskList = new ArrayList<>();
    private TaskAdapter taskAdapter;

    private int weekOffset = 0;
    private double coinsBalance = 0.0;

    private CountDownTimer activeTimer = null;
    private boolean isTimerRunning = false;
    private long timerMillisLeft = 0;
    private long timerTotalMillis = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_study);

        tvCoinsScore = findViewById(R.id.tv_coins_score);
        tvCalendarMonth = findViewById(R.id.tv_calendar_month);
        daysRowContainer = findViewById(R.id.days_row_container);
        rvBlocks = findViewById(R.id.rv_study_blocks);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        SharedPreferences prefs = getSharedPreferences("MyLifePlanPrefs", Context.MODE_PRIVATE);
        coinsBalance = prefs.getFloat("coins", 25.0f);
        tvCoinsScore.setText(String.format(Locale.US, "%.2f", coinsBalance));

        findViewById(R.id.btn_prev_week).setOnClickListener(v -> {
            weekOffset--;
            updateCalendarStrip();
        });

        findViewById(R.id.btn_next_week).setOnClickListener(v -> {
            weekOffset++;
            updateCalendarStrip();
        });

        rvBlocks.setLayoutManager(new LinearLayoutManager(this));
        taskAdapter = new TaskAdapter(taskList, this::openActiveTimerDialog, this::showTaskParamsDialog);
        rvBlocks.setAdapter(taskAdapter);

        loadSavedTasks();
        findViewById(R.id.btn_add_block).setOnClickListener(v -> showTaskParamsDialog(null));
        updateCalendarStrip();
    }

    /**
     * Лента календаря: дни кликабельны и показывают расписание по нажатию
     */
    private void updateCalendarStrip() {
        daysRowContainer.removeAllViews();
        SharedPreferences prefs = getSharedPreferences("MyLifePlanPrefs", Context.MODE_PRIVATE);

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.WEEK_OF_YEAR, weekOffset);
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);

        SimpleDateFormat sdfMonth = new SimpleDateFormat("MMMM yyyy", Locale.US);
        tvCalendarMonth.setText(sdfMonth.format(cal.getTime()));

        SimpleDateFormat sdfDayNumber = new SimpleDateFormat("d", Locale.US);
        SimpleDateFormat sdfFullDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String[] dayNames = {"Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"};

        int dailyGoalMinutes = 0;
        for (StudyTask t : taskList) {
            dailyGoalMinutes += t.targetMinutesPerDay;
        }
        if (dailyGoalMinutes == 0) dailyGoalMinutes = 30;

        for (int i = 0; i < 7; i++) {
            final int dayIndex = i;
            final String dateKey = sdfFullDate.format(cal.getTime());
            final String displayDay = dayNames[i] + ", " + sdfDayNumber.format(cal.getTime()) + " " + tvCalendarMonth.getText();
            final int goalForDay = dailyGoalMinutes;

            LinearLayout dayCell = new LinearLayout(this);
            dayCell.setOrientation(LinearLayout.VERTICAL);
            dayCell.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            dayCell.setGravity(android.view.Gravity.CENTER);

            TextView tvName = new TextView(this);
            tvName.setText(dayNames[i]);
            tvName.setTextColor(0xFF8A9BA8);
            tvName.setTextSize(12f);
            dayCell.addView(tvName);

            TextView tvNum = new TextView(this);
            tvNum.setText(sdfDayNumber.format(cal.getTime()));
            tvNum.setTextSize(13f);
            tvNum.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dpToPx(30), dpToPx(30));
            lp.topMargin = dpToPx(4);
            tvNum.setLayoutParams(lp);

            int minutesSpent = prefs.getInt("focus_minutes_" + dateKey, 0);

            // Зеленый ободок только если выполнено >= нормы!
            if (minutesSpent >= dailyGoalMinutes && minutesSpent > 0) {
                tvNum.setBackgroundResource(R.drawable.bg_success_day);
                tvNum.setTextColor(0xFFFFFFFF);
            } else {
                tvNum.setBackgroundResource(R.drawable.bg_selected_day);
                tvNum.setTextColor(0xFF3D4A54);
            }

            dayCell.addView(tvNum);

            // КЛИК ПО ДНЮ: открывает мини-страницу с планами на этот день!
            dayCell.setOnClickListener(v -> showDayScheduleDialog(dayIndex, displayDay, dateKey, goalForDay));

            daysRowContainer.addView(dayCell);
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
    }

    /**
     * Мини-страница: детальный просмотр планов на выбранный день
     */
    private void showDayScheduleDialog(int dayOfWeekIndex, String titleText, String dateKey, int goalMinutes) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_day_details, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();

        TextView tvTitle = view.findViewById(R.id.tv_day_details_title);
        TextView tvProgress = view.findViewById(R.id.tv_day_details_progress);
        LinearLayout containerTasks = view.findViewById(R.id.container_day_tasks);
        Button btnClose = view.findViewById(R.id.btn_close_day_details);

        tvTitle.setText(titleText);

        SharedPreferences prefs = getSharedPreferences("MyLifePlanPrefs", Context.MODE_PRIVATE);
        int spent = prefs.getInt("focus_minutes_" + dateKey, 0);
        tvProgress.setText("⏱ Набрано сегодня: " + spent + " мин / " + goalMinutes + " мин нормы");

        containerTasks.removeAllViews();
        boolean hasTasks = false;

        for (StudyTask task : taskList) {
            String timeForDay = task.reminderTimes[dayOfWeekIndex];
            if (timeForDay != null) {
                hasTasks = true;
                int startM = parseTimeToMinutes(timeForDay);
                int endM = startM + task.targetMinutesPerDay;
                String timeSlot = timeForDay + " – " + formatMinutesToTime(endM);

                TextView itemTv = new TextView(this);
                itemTv.setText("📌 " + timeSlot + " : " + task.title + " (" + task.targetMinutesPerDay + " мин)");
                itemTv.setTextColor(0xFF2E3A44);
                itemTv.setTextSize(14f);
                itemTv.setPadding(dpToPx(4), dpToPx(6), dpToPx(4), dpToPx(6));
                containerTasks.addView(itemTv);
            }
        }

        if (!hasTasks) {
            TextView emptyTv = new TextView(this);
            emptyTv.setText("На этот день пока нет фиксированных дел. Отличный день для отдыха! 🌿🐾");
            emptyTv.setTextColor(0xFF8A9BA8);
            emptyTv.setTextSize(13f);
            containerTasks.addView(emptyTv);
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    /**
     * Защита от наложения дедлайнов: интервалы [Start; End) не должны пересекаться
     */
    private boolean checkTimeConflict(int dayIdx, int newStartMin, int newDurationMin, String currentTaskId) {
        int newEndMin = newStartMin + newDurationMin;

        for (StudyTask task : taskList) {
            if (task.id.equals(currentTaskId)) continue; // Пропускаем редактируемую задачу

            String existingTime = task.reminderTimes[dayIdx];
            if (existingTime != null) {
                int existStart = parseTimeToMinutes(existingTime);
                int existEnd = existStart + task.targetMinutesPerDay;

                // Пересечение интервалов: StartA < EndB && StartB < EndA
                if (newStartMin < existEnd && existStart < newEndMin) {
                    Toast.makeText(this, "Конфликт времени! С " + existingTime + " до " + formatMinutesToTime(existEnd) +
                            " уже занято задачей: \"" + task.title + "\"", Toast.LENGTH_LONG).show();
                    return true;
                }
            }
        }
        return false;
    }

    private void openActiveTimerDialog(StudyTask task) {
        timerTotalMillis = task.targetMinutesPerDay * 60 * 1000L;
        timerMillisLeft = timerTotalMillis;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_active_timer, null);
        builder.setView(view);
        builder.setCancelable(false);
        AlertDialog timerDialog = builder.create();

        TextView tvTitle = view.findViewById(R.id.tv_timer_task_title);
        TextView tvDigits = view.findViewById(R.id.tv_modal_timer_digits);
        ProgressBar pbTimer = view.findViewById(R.id.pb_modal_timer);
        Button btnPauseResume = view.findViewById(R.id.btn_timer_pause_resume);
        Button btnFinish = view.findViewById(R.id.btn_timer_finish_early);
        Button btnCancel = view.findViewById(R.id.btn_timer_cancel);

        tvTitle.setText(task.title);
        pbTimer.setMax((int) (timerTotalMillis / 1000));
        pbTimer.setProgress((int) (timerMillisLeft / 1000));

        Runnable updateDisplay = () -> {
            int mins = (int) (timerMillisLeft / 1000) / 60;
            int secs = (int) (timerMillisLeft / 1000) % 60;
            tvDigits.setText(String.format(Locale.US, "%02d:%02d", mins, secs));
            pbTimer.setProgress((int) (timerMillisLeft / 1000));
        };
        updateDisplay.run();

        activeTimer = new CountDownTimer(timerMillisLeft, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timerMillisLeft = millisUntilFinished;
                updateDisplay.run();
            }

            @Override
            public void onFinish() {
                isTimerRunning = false;
                completeFocusSession(task.targetMinutesPerDay);
                playNotificationSound();
                timerDialog.dismiss();
            }
        }.start();
        isTimerRunning = true;

        btnPauseResume.setOnClickListener(v -> {
            if (isTimerRunning) {
                if (activeTimer != null) activeTimer.cancel();
                isTimerRunning = false;
                btnPauseResume.setText("Resume");
            } else {
                activeTimer = new CountDownTimer(timerMillisLeft, 1000) {
                    @Override
                    public void onTick(long millisUntilFinished) {
                        timerMillisLeft = millisUntilFinished;
                        updateDisplay.run();
                    }
                    @Override
                    public void onFinish() {
                        isTimerRunning = false;
                        completeFocusSession(task.targetMinutesPerDay);
                        playNotificationSound();
                        timerDialog.dismiss();
                    }
                }.start();
                isTimerRunning = true;
                btnPauseResume.setText("Pause");
            }
        });

        btnFinish.setOnClickListener(v -> {
            if (activeTimer != null) activeTimer.cancel();
            isTimerRunning = false;
            int minsSpent = (int) ((timerTotalMillis - timerMillisLeft) / 1000) / 60;
            if (minsSpent < 1) minsSpent = 1;
            completeFocusSession(minsSpent);
            playNotificationSound();
            timerDialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> {
            if (activeTimer != null) activeTimer.cancel();
            isTimerRunning = false;
            timerDialog.dismiss();
        });

        timerDialog.show();
    }

    private void completeFocusSession(int minutesSpent) {
        double earned = minutesSpent / 60.0;
        coinsBalance += earned;

        SharedPreferences prefs = getSharedPreferences("MyLifePlanPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putFloat("coins", (float) coinsBalance);

        String todayKey = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        int curr = prefs.getInt("focus_minutes_" + todayKey, 0);
        editor.putInt("focus_minutes_" + todayKey, curr + minutesSpent);
        editor.apply();

        tvCoinsScore.setText(String.format(Locale.US, "%.2f", coinsBalance));
        updateCalendarStrip();
        Toast.makeText(this, "Сессия записана! +" + String.format(Locale.US, "%.2f", earned) + " ⭐ начислено!", Toast.LENGTH_SHORT).show();
    }

    private void showTaskParamsDialog(StudyTask existingTask) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_task_params, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        EditText etTitle = dialogView.findViewById(R.id.et_dialog_title);
        EditText etNotes = dialogView.findViewById(R.id.et_dialog_notes);
        Button btnDeadline = dialogView.findViewById(R.id.btn_pick_deadline);
        Button btnTarget = dialogView.findViewById(R.id.btn_pick_daily_target);
        CheckBox cbSound = dialogView.findViewById(R.id.cb_sound_notification);

        Button[] dayButtons = new Button[]{
                dialogView.findViewById(R.id.btn_day_mon), dialogView.findViewById(R.id.btn_day_tue),
                dialogView.findViewById(R.id.btn_day_wed), dialogView.findViewById(R.id.btn_day_thu),
                dialogView.findViewById(R.id.btn_day_fri), dialogView.findViewById(R.id.btn_day_sat),
                dialogView.findViewById(R.id.btn_day_sun)
        };
        String[] dayLabels = {"Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"};
        String[] chosenTimes = new String[7];

        final int[] chosenTarget = {60};
        final String[] chosenDeadline = {"No deadline"};
        final long[] chosenEpoch = {0L};

        if (existingTask != null) {
            etTitle.setText(existingTask.title);
            etNotes.setText(existingTask.notes);
            chosenDeadline[0] = existingTask.deadline;
            chosenTarget[0] = existingTask.targetMinutesPerDay;
            chosenEpoch[0] = existingTask.deadlineEpochMillis;
            btnDeadline.setText("Due: " + existingTask.deadline);
            btnTarget.setText("Target: " + existingTask.targetMinutesPerDay + " min");
            cbSound.setChecked(existingTask.soundEnabled);

            for (int i = 0; i < 7; i++) {
                chosenTimes[i] = existingTask.reminderTimes[i];
                if (chosenTimes[i] != null) {
                    dayButtons[i].setBackgroundResource(R.drawable.bg_success_day);
                    dayButtons[i].setTextColor(0xFFFFFFFF);
                    dayButtons[i].setText(chosenTimes[i]);
                }
            }
        }

        cbSound.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                playNotificationSound();
                Toast.makeText(this, "Тест звука 🐾🔔", Toast.LENGTH_SHORT).show();
            }
        });

        // Выбор времени с проверкой наложения
        for (int i = 0; i < 7; i++) {
            final int dayIdx = i;
            dayButtons[i].setOnClickListener(v -> {
                if (chosenTimes[dayIdx] == null) {
                    new TimePickerDialog(this, (view1, hourOfDay, minute) -> {
                        int startMinutes = hourOfDay * 60 + minute;
                        String taskId = existingTask != null ? existingTask.id : "";

                        // ПРОВЕРКА НАЛОЖЕНИЯ ПО ВРЕМЕНИ
                        if (checkTimeConflict(dayIdx, startMinutes, chosenTarget[0], taskId)) {
                            return; // Не даем поставить на занятый слот!
                        }

                        String timeStr = String.format(Locale.US, "%02d:%02d", hourOfDay, minute);
                        chosenTimes[dayIdx] = timeStr;
                        dayButtons[dayIdx].setBackgroundResource(R.drawable.bg_success_day);
                        dayButtons[dayIdx].setTextColor(0xFFFFFFFF);
                        dayButtons[dayIdx].setText(timeStr);
                    }, 12, 0, true).show();
                } else {
                    chosenTimes[dayIdx] = null;
                    dayButtons[dayIdx].setBackgroundResource(R.drawable.bg_button_white);
                    dayButtons[dayIdx].setTextColor(0xFF3D4A54);
                    dayButtons[dayIdx].setText(dayLabels[dayIdx]);
                }
            });
        }

        btnDeadline.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(this, (view1, year, month, dayOfMonth) -> {
                new TimePickerDialog(this, (view2, hourOfDay, minute) -> {
                    Calendar picked = Calendar.getInstance();
                    picked.set(year, month, dayOfMonth, hourOfDay, minute, 0);
                    chosenEpoch[0] = picked.getTimeInMillis();
                    chosenDeadline[0] = (month + 1) + "/" + dayOfMonth + " " + String.format(Locale.US, "%02d:%02d", hourOfDay, minute);
                    btnDeadline.setText("Due: " + chosenDeadline[0]);
                }, 18, 0, true).show();
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });

        btnTarget.setOnClickListener(v -> {
            chosenTarget[0] = (chosenTarget[0] >= 180) ? 30 : chosenTarget[0] + 30;
            btnTarget.setText("Target: " + chosenTarget[0] + " min");
        });

        dialogView.findViewById(R.id.btn_save_params).setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            if (title.isEmpty()) title = "Untitled Goal";
            String notes = etNotes.getText().toString().trim();

            StudyTask savedTask;
            if (existingTask != null) {
                existingTask.title = title;
                existingTask.notes = notes;
                existingTask.deadline = chosenDeadline[0];
                existingTask.deadlineEpochMillis = chosenEpoch[0];
                existingTask.targetMinutesPerDay = chosenTarget[0];
                existingTask.soundEnabled = cbSound.isChecked();
                System.arraycopy(chosenTimes, 0, existingTask.reminderTimes, 0, 7);
                savedTask = existingTask;
            } else {
                StudyTask newTask = new StudyTask(String.valueOf(System.currentTimeMillis()), title, notes, chosenDeadline[0], chosenTarget[0]);
                newTask.deadlineEpochMillis = chosenEpoch[0];
                newTask.soundEnabled = cbSound.isChecked();
                System.arraycopy(chosenTimes, 0, newTask.reminderTimes, 0, 7);
                taskList.add(newTask);
                savedTask = newTask;
            }

            taskAdapter.notifyDataSetChanged();
            saveTasks();
            updateCalendarStrip();
            scheduleTripleReminders(savedTask);
            dialog.dismiss();
        });

        dialog.show();
    }

    /**
     * Ставит каскадные напоминания: за 1 час, за 30 минут и в момент дедлайна
     */
    @SuppressLint("ScheduleExactAlarm")
    private void scheduleTripleReminders(StudyTask task) {
        if (task.deadlineEpochMillis <= System.currentTimeMillis()) return;

        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        long deadline = task.deadlineEpochMillis;
        long oneHourBefore = deadline - 60 * 60 * 1000L;
        long thirtyMinsBefore = deadline - 30 * 60 * 1000L;

        // 1. Напоминание за 1 час
        if (oneHourBefore > System.currentTimeMillis()) {
            setAlarm(am, oneHourBefore, task.title, "one_hour", (task.id + "_1h").hashCode());
        }

        // 2. Напоминание за 30 минут
        if (thirtyMinsBefore > System.currentTimeMillis()) {
            setAlarm(am, thirtyMinsBefore, task.title, "thirty_mins", (task.id + "_30m").hashCode());
        }

        // 3. В момент дедлайна!
        setAlarm(am, deadline, task.title, "at_deadline", (task.id + "_exact").hashCode());
    }

    @SuppressLint("ScheduleExactAlarm")
    private void setAlarm(AlarmManager am, long triggerAtMillis, String title, String msgType, int requestCode) {
        Intent intent = new Intent(this, ReminderReceiver.class);
        intent.putExtra("task_title", title);
        intent.putExtra("msg_type", msgType);

        PendingIntent pi = PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
        }
    }

    private void playNotificationSound() {
        try {
            MediaPlayer player = MediaPlayer.create(this, R.raw.cat_bell);
            if (player != null) {
                player.start();
                player.setOnCompletionListener(MediaPlayer::release);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int parseTimeToMinutes(String timeStr) {
        try {
            String[] parts = timeStr.split(":");
            return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        } catch (Exception e) {
            return 0;
        }
    }

    private String formatMinutesToTime(int totalMinutes) {
        int h = (totalMinutes / 60) % 24;
        int m = totalMinutes % 60;
        return String.format(Locale.US, "%02d:%02d", h, m);
    }

    private void saveTasks() {
        try {
            JSONArray arr = new JSONArray();
            for (StudyTask t : taskList) {
                JSONObject obj = new JSONObject();
                obj.put("id", t.id);
                obj.put("title", t.title);
                obj.put("notes", t.notes);
                obj.put("deadline", t.deadline);
                obj.put("epoch", t.deadlineEpochMillis);
                obj.put("target", t.targetMinutesPerDay);
                obj.put("sound", t.soundEnabled);

                JSONArray timesArr = new JSONArray();
                for (String tm : t.reminderTimes) timesArr.put(tm == null ? "" : tm);
                obj.put("times", timesArr);
                arr.put(obj);
            }
            getSharedPreferences("MyLifePlanPrefs", Context.MODE_PRIVATE)
                    .edit().putString("saved_study_tasks", arr.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadSavedTasks() {
        taskList.clear();
        String json = getSharedPreferences("MyLifePlanPrefs", Context.MODE_PRIVATE)
                .getString("saved_study_tasks", null);
        if (json != null) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    StudyTask t = new StudyTask(
                            obj.getString("id"),
                            obj.getString("title"),
                            obj.optString("notes", ""),
                            obj.optString("deadline", "Soon"),
                            obj.optInt("target", 60)
                    );
                    t.deadlineEpochMillis = obj.optLong("epoch", 0L);
                    t.soundEnabled = obj.optBoolean("sound", true);
                    JSONArray timesArr = obj.optJSONArray("times");
                    if (timesArr != null) {
                        for (int j = 0; j < Math.min(7, timesArr.length()); j++) {
                            String s = timesArr.getString(j);
                            t.reminderTimes[j] = s.isEmpty() ? null : s;
                        }
                    }
                    taskList.add(t);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            taskList.add(new StudyTask("1", "Algorithms & Practice", "Solve DP tasks", "Oct 24 18:00", 60));
        }
        taskAdapter.notifyDataSetChanged();
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private static class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.ViewHolder> {
        private final List<StudyTask> list;
        private final OnItemClick onRun;
        private final OnItemClick onSettings;

        interface OnItemClick { void click(StudyTask task); }

        TaskAdapter(List<StudyTask> list, OnItemClick onRun, OnItemClick onSettings) {
            this.list = list;
            this.onRun = onRun;
            this.onSettings = onSettings;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_study_block, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            StudyTask task = list.get(position);
            holder.tvTitle.setText(task.title);
            holder.tvNotes.setText(task.notes.isEmpty() ? "No notes added" : task.notes);
            holder.chipDeadline.setText("🗓 " + task.deadline);
            holder.chipDuration.setText("⏱ Goal: " + task.targetMinutesPerDay + "m");

            holder.btnRun.setOnClickListener(v -> onRun.click(task));
            holder.btnSettings.setOnClickListener(v -> onSettings.click(task));
        }

        @Override
        public int getItemCount() { return list.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvNotes, chipDeadline, chipDuration;
            ImageButton btnRun, btnSettings;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tv_block_title);
                tvNotes = itemView.findViewById(R.id.tv_block_notes);
                chipDeadline = itemView.findViewById(R.id.chip_deadline);
                chipDuration = itemView.findViewById(R.id.chip_duration);
                btnRun = itemView.findViewById(R.id.btn_run_timer);
                btnSettings = itemView.findViewById(R.id.btn_edit_params);
            }
        }
    }
}