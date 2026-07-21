package com.example.mylifeplan;

import android.app.TimePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class StudyActivity extends AppCompatActivity {

    private TextView tvTimer;
    private TextView tvCoinsScore;
    private ProgressBar pbTimer;
    private ImageButton btnStartPause; // Изменено на ImageButton

    private CountDownTimer countDownTimer;
    private boolean timerRunning = false;

    // Время фокусировки по умолчанию: 30 минут (в миллисекундах)
    private long START_TIME_IN_MILLIS = 1800000;
    private long timeLeftInMillis = START_TIME_IN_MILLIS;

    private double coinsBalance = 0.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_study);

        tvTimer = findViewById(R.id.tv_timer);
        tvCoinsScore = findViewById(R.id.tv_coins_score);
        pbTimer = findViewById(R.id.pb_timer);
        btnStartPause = findViewById(R.id.btn_start_pause);
        ImageButton btnReset = findViewById(R.id.btn_reset); // Изменено на ImageButton
        ImageButton btnBack = findViewById(R.id.btn_back);

        // Загрузка глобального сохраненного баланса монет
        SharedPreferences prefs = getSharedPreferences("MyLifePlanPrefs", Context.MODE_PRIVATE);
        coinsBalance = prefs.getFloat("coins", 0.0f);

        // Выводим только число (так как иконка звездной монеты находится в самой верстке)
        tvCoinsScore.setText(String.format(Locale.US, "%.2f", coinsBalance));

        pbTimer.setMax((int) (START_TIME_IN_MILLIS / 1000));
        pbTimer.setProgress((int) (START_TIME_IN_MILLIS / 1000));

        btnBack.setOnClickListener(v -> finish());

        // Нажатие на цифры таймера для установки времени (шаг 30 минут, от 30 мин до 8 ч)
        tvTimer.setOnClickListener(v -> {
            if (timerRunning) {
                Toast.makeText(this, "Нельзя изменить настройки во время работы таймера!", Toast.LENGTH_SHORT).show();
                return;
            }

            int currentHours = (int) (START_TIME_IN_MILLIS / 1000) / 3600;
            int currentMinutes = (int) ((START_TIME_IN_MILLIS / 1000) % 3600) / 60;

            TimePickerDialog timePickerDialog = new TimePickerDialog(this, (view, hourOfDay, minuteOfHour) -> {

                // Валидация шага: минуты должны быть строго 00 или 30
                if (minuteOfHour != 0 && minuteOfHour != 30) {
                    Toast.makeText(this, "Время можно ставить только с шагом в 30 минут (например, 1:00, 1:30, 2:00)", Toast.LENGTH_LONG).show();
                    return;
                }

                int totalMinutes = (hourOfDay * 60) + minuteOfHour;

                // Валидация диапазона: строго от 30 минут до 8 часов (480 минут)
                if (totalMinutes < 30) {
                    Toast.makeText(this, "Минимальное время фокусировки — 30 минут", Toast.LENGTH_SHORT).show();
                } else if (totalMinutes > 480) {
                    Toast.makeText(this, "Максимальное время фокусировки — 8 часов", Toast.LENGTH_SHORT).show();
                } else {
                    START_TIME_IN_MILLIS = totalMinutes * 60 * 1000L;
                    timeLeftInMillis = START_TIME_IN_MILLIS;

                    pbTimer.setMax(totalMinutes * 60);
                    pbTimer.setProgress(totalMinutes * 60);
                    updateCountDownText();
                    Toast.makeText(this, "Время установлено: " + totalMinutes + " мин.", Toast.LENGTH_SHORT).show();
                }
            }, currentHours, currentMinutes, true);

            timePickerDialog.setTitle("Время фокуса (шаг 30 мин, от 30 мин до 8 ч)");
            timePickerDialog.show();
        });

        // Кнопка Старт/Пауза
        btnStartPause.setOnClickListener(v -> {
            if (timerRunning) {
                pauseTimer(); // Ставит на паузу
            } else {
                startTimer(); // Запускает или возобновляет
            }
        });

        // Кнопка Сброс
        btnReset.setOnClickListener(v -> resetTimer());

        updateCountDownText();
    }

    private void startTimer() {
        countDownTimer = new CountDownTimer(timeLeftInMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeLeftInMillis = millisUntilFinished;
                updateCountDownText();
            }

            @Override
            public void onFinish() {
                timerRunning = false;

                // Переключаем картинку обратно на кнопку СТАРТА [2]
                btnStartPause.setImageResource(R.drawable.start);

                // Начисление монет: минуты / 60.0 (30 мин = 0.5, 1 ч = 1.0, 7.5 ч = 7.5)
                double minutes = (double) (START_TIME_IN_MILLIS / 1000) / 60.0;
                double coinsEarned = minutes / 60.0;
                coinsBalance += coinsEarned;

                // Мгновенное сохранение баланса в SharedPreferences через .commit() [1.1.5]
                SharedPreferences prefs = getSharedPreferences("MyLifePlanPrefs", Context.MODE_PRIVATE);
                prefs.edit().putFloat("coins", (float) coinsBalance).commit();

                tvCoinsScore.setText(String.format(Locale.US, "%.2f", coinsBalance));
                Toast.makeText(StudyActivity.this, "Сессия окончена! Вы заработали " + String.format(Locale.US, "%.2f", coinsEarned) + " монет!", Toast.LENGTH_LONG).show();

                resetTimer();
            }
        }.start();

        timerRunning = true;

        // Меняем картинку кнопки на иконку ПАУЗЫ при старте [2]
        btnStartPause.setImageResource(R.drawable.pause);
    }

    private void pauseTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel(); // Останавливает таймер, сохраняя время в timeLeftInMillis
        }
        timerRunning = false;

        // Переключаем картинку кнопки на иконку СТАРТА при паузе [2]
        btnStartPause.setImageResource(R.drawable.start);
    }

    private void resetTimer() {
        pauseTimer();
        timeLeftInMillis = START_TIME_IN_MILLIS;
        updateCountDownText();
    }

    private void updateCountDownText() {
        int hours = (int) (timeLeftInMillis / 1000) / 3600;
        int minutes = (int) ((timeLeftInMillis / 1000) % 3600) / 60;
        int seconds = (int) (timeLeftInMillis / 1000) % 60;

        String timeLeftFormatted;
        if (hours > 0) {
            timeLeftFormatted = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            timeLeftFormatted = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
        tvTimer.setText(timeLeftFormatted);
        pbTimer.setProgress((int) (timeLeftInMillis / 1000));
    }
}