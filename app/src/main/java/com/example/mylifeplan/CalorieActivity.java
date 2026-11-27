package com.example.mylifeplan;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CalorieActivity extends AppCompatActivity {

    private ProgressBar pbCalories;
    private TextView tvCurrentCalories;
    private TextView tvCalorieCorridor;
    private TextView tvCoinsScore;
    private TextView tvTargetDayX;

    private TextView tvEatenCaloriesSummary;
    private TextView tvBurnedCalories;

    private TextView tvWaterProgress;
    private TextView tvStepsProgress;
    private TextView btnAddWater;
    private TextView btnAddSteps;

    private TextView tvMacrosProtein, tvMacrosFat, tvMacrosCarbs;

    private RecyclerView rvMeals;
    private MealsAdapter mealsAdapter;
    private final List<MealItem> mealsList = new ArrayList<>();

    private int currentCaloriesSum = 0;
    private int currentProteinSum = 0;
    private int currentFatSum = 0;
    private int currentCarbsSum = 0;

    private int currentWaterSum = 0;
    private int currentStepsSum = 0;

    // Безопасный коридор калорий (по умолчанию нормальный здоровый диапазон)
    private int safeMinCalories = 1400;
    private int safeMaxCalories = 1750;

    private int targetProteinLimit = 110;
    private int targetFatLimit = 50;
    private int targetCarbsLimit = 150;

    // Переменные интерактивного календаря
    private String selectedDate = "";
    private String activeTodayDate = "";
    private int weekOffset = 0;
    private TextView tvCalendarMonth;
    private LinearLayout daysRowContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calorie);

        // Инициализация элементов
        pbCalories = findViewById(R.id.calorie_progress_bar);
        tvCurrentCalories = findViewById(R.id.tv_current_calories);
        tvCalorieCorridor = findViewById(R.id.tv_calorie_corridor);
        tvCoinsScore = findViewById(R.id.tv_coins_score);
        tvTargetDayX = findViewById(R.id.tv_target_day_x);

        tvEatenCaloriesSummary = findViewById(R.id.tv_eaten_calories_summary);
        tvBurnedCalories = findViewById(R.id.tv_burned_calories);

        tvWaterProgress = findViewById(R.id.tv_water_progress);
        tvStepsProgress = findViewById(R.id.tv_steps_progress);
        btnAddWater = findViewById(R.id.btn_add_water);
        btnAddSteps = findViewById(R.id.btn_add_steps);

        tvMacrosProtein = findViewById(R.id.tv_macros_protein);
        tvMacrosFat = findViewById(R.id.tv_macros_fat);
        tvMacrosCarbs = findViewById(R.id.tv_macros_carbs);

        tvCalendarMonth = findViewById(R.id.tv_calendar_month);
        daysRowContainer = findViewById(R.id.days_row_container);

        rvMeals = findViewById(R.id.meals_recycler);
        rvMeals.setLayoutManager(new LinearLayoutManager(this));

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // Навигация недели в календаре < >
        findViewById(R.id.btn_prev_week).setOnClickListener(v -> {
            weekOffset--;
            updateCalendarStrip();
        });

        findViewById(R.id.btn_next_week).setOnClickListener(v -> {
            weekOffset++;
            updateCalendarStrip();
        });

        findViewById(R.id.btn_add_meal).setOnClickListener(v -> showAddMealDialog());

        if (btnAddWater != null) btnAddWater.setOnClickListener(v -> addWater());
        if (btnAddSteps != null) btnAddSteps.setOnClickListener(v -> showAddStepsDialog());

        initDates();
        loadCalorieData();
        updateCalendarStrip();
        updateDayXBanner();
    }

    private void initDates() {
        SharedPreferences prefs = getSharedPreferences("MyLifePlanPrefs", Context.MODE_PRIVATE);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        activeTodayDate = sdf.format(new Date());
        selectedDate = activeTodayDate;
    }

    /**
     * Отображает цель и сколько дней осталось до дня X
     */
    private void updateDayXBanner() {
        SharedPreferences prefs = getSharedPreferences("MyLifePlanPrefs", Context.MODE_PRIVATE);
        String goalText = prefs.getString("user_health_goal", "Поддержание энергии и баланса");
        String targetDateStr = prefs.getString("target_day_x_date", ""); // Например, "2026-11-15"

        if (!targetDateStr.isEmpty()) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                Date targetDate = sdf.parse(targetDateStr);
                Date today = new Date();
                long diff = targetDate.getTime() - today.getTime();
                long daysLeft = diff / (1000 * 60 * 60 * 24);

                if (daysLeft >= 0) {
                    tvTargetDayX.setText("🎯 Цель: " + goalText + " • До дня X: " + daysLeft + " дн.");
                } else {
                    tvTargetDayX.setText("🎯 Цель: " + goalText + " • День X завершён ⭐");
                }
            } catch (Exception e) {
                tvTargetDayX.setText("🎯 Цель: " + goalText);
            }
        } else {
            tvTargetDayX.setText("🎯 Цель: " + goalText + " • Без дедлайна 🌿");
        }
    }

    /**
     * Интерактивный календарь с перелистыванием недель
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

        for (int i = 0; i < 7; i++) {
            final String dateKey = sdfFullDate.format(cal.getTime());

            LinearLayout dayCell = new LinearLayout(this);
            dayCell.setOrientation(LinearLayout.VERTICAL);
            dayCell.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            dayCell.setGravity(android.view.Gravity.CENTER);

            TextView tvName = new TextView(this);
            tvName.setText(dayNames[i]);
            tvName.setTextColor(0xFF8A9BA8);
            tvName.setTextSize(11f);
            dayCell.addView(tvName);

            TextView tvNum = new TextView(this);
            tvNum.setText(sdfDayNumber.format(cal.getTime()));
            tvNum.setTextSize(12f);
            tvNum.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dpToPx(28), dpToPx(28));
            lp.topMargin = dpToPx(2);
            tvNum.setLayoutParams(lp);

            // Подсветка выбранной даты и успешных дней
            if (dateKey.equals(selectedDate)) {
                tvNum.setBackgroundResource(R.drawable.bg_selected_day);
                tvNum.setTextColor(0xFFFFFFFF);
            } else {
                boolean isSuccess = prefs.getBoolean("calorie_success_" + dateKey, false);
                if (isSuccess) {
                    tvNum.setBackgroundResource(R.drawable.bg_success_day);
                    tvNum.setTextColor(0xFFFFFFFF);
                } else {
                    tvNum.setBackgroundResource(0);
                    tvNum.setTextColor(0xFF8A9BA8);
                }
            }

            dayCell.addView(tvNum);

            // Клик по дате -> переключаем день и смотрим историю питания!
            dayCell.setOnClickListener(v -> {
                saveCalorieData();
                selectedDate = dateKey;
                loadCalorieData();
                updateCalendarStrip();
                Toast.makeText(this, "День: " + selectedDate, Toast.LENGTH_SHORT).show();
            });

            daysRowContainer.addView(dayCell);
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
    }

    private void addWater() {
        if (!selectedDate.equals(activeTodayDate)) {
            Toast.makeText(this, "Воду можно отмечать только за сегодня!", Toast.LENGTH_SHORT).show();
            return;
        }
        currentWaterSum += 250;
        updateHabitsViews();
        saveCalorieData();
    }

    private void showAddStepsDialog() {
        if (!selectedDate.equals(activeTodayDate)) {
            Toast.makeText(this, "Шаги можно менять только за сегодня!", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Шаги за сегодня");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(currentStepsSum));
        input.setPadding(50, 40, 50, 40);
        builder.setView(input);

        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            String stepsStr = input.getText().toString().trim();
            if (!stepsStr.isEmpty()) {
                currentStepsSum = Integer.parseInt(stepsStr);

                // 1000 шагов = ~40 ккал
                int burnedFromSteps = (int) (currentStepsSum * 0.04);
                getSharedPreferences("MyLifePlanPrefs", Context.MODE_PRIVATE)
                        .edit().putInt("burned_calories_" + selectedDate, burnedFromSteps).apply();

                updateHabitsViews();
                updateCalorieViews();
                saveCalorieData();
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void updateHabitsViews() {
        if (tvWaterProgress != null) {
            tvWaterProgress.setText(String.format(Locale.US, "💧 %d / 2000 ml", currentWaterSum));
        }
        if (tvStepsProgress != null) {
            tvStepsProgress.setText(String.format(Locale.US, "👟 %d / 10000", currentStepsSum));
        }
    }

    private void showAddMealDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 40);

        final EditText etMealName = new EditText(this);
        etMealName.setHint("Название продукта / блюда");
        layout.addView(etMealName);

        final EditText etMealCals = new EditText(this);
        etMealCals.setHint("Калорийность (ккал)");
        etMealCals.setInputType(InputType.TYPE_CLASS_NUMBER);
        layout.addView(etMealCals);

        final EditText etProtein = new EditText(this);
        etProtein.setHint("Белки (г) - опционально");
        etProtein.setInputType(InputType.TYPE_CLASS_NUMBER);
        layout.addView(etProtein);

        final EditText etFat = new EditText(this);
        etFat.setHint("Жиры (г) - опционально");
        etFat.setInputType(InputType.TYPE_CLASS_NUMBER);
        layout.addView(etFat);

        final EditText etCarbs = new EditText(this);
        etCarbs.setHint("Углеводы (г) - опционально");
        etCarbs.setInputType(InputType.TYPE_CLASS_NUMBER);
        layout.addView(etCarbs);

        new AlertDialog.Builder(this)
                .setTitle("Добавить приём пищи")
                .setView(layout)
                .setPositiveButton("Добавить", (d, i) -> {
                    String name = etMealName.getText().toString().trim();
                    String calsStr = etMealCals.getText().toString().trim();

                    if (!name.isEmpty() && !calsStr.isEmpty()) {
                        int cals = Integer.parseInt(calsStr);
                        int p = etProtein.getText().toString().isEmpty() ? 0 : Integer.parseInt(etProtein.getText().toString());
                        int f = etFat.getText().toString().isEmpty() ? 0 : Integer.parseInt(etFat.getText().toString());
                        int c = etCarbs.getText().toString().isEmpty() ? 0 : Integer.parseInt(etCarbs.getText().toString());

                        mealsList.add(new MealItem(name, cals, p, f, c));
                        mealsAdapter.notifyItemInserted(mealsList.size() - 1);

                        currentCaloriesSum += cals;
                        currentProteinSum += p;
                        currentFatSum += f;
                        currentCarbsSum += c;

                        updateCalorieViews();
                        saveCalorieData();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void updateCalorieViews() {
        SharedPreferences prefs = getSharedPreferences("MyLifePlanPrefs", Context.MODE_PRIVATE);
        int burnedToday = prefs.getInt("burned_calories_" + selectedDate, 0);

        tvCurrentCalories.setText(String.valueOf(currentCaloriesSum));
        tvCalorieCorridor.setText(safeMinCalories + " – " + safeMaxCalories);

        pbCalories.setMax(safeMaxCalories);
        pbCalories.setProgress(Math.min(currentCaloriesSum, safeMaxCalories));

        tvEatenCaloriesSummary.setText("Eaten: " + currentCaloriesSum + " kcal");
        tvBurnedCalories.setText("Burned: " + burnedToday + " kcal");

        tvMacrosProtein.setText(String.format(Locale.US, "P: %d/%dg", currentProteinSum, targetProteinLimit));
        tvMacrosFat.setText(String.format(Locale.US, "F: %d/%dg", currentFatSum, targetFatLimit));
        tvMacrosCarbs.setText(String.format(Locale.US, "C: %d/%dg", currentCarbsSum, targetCarbsLimit));

        // День считается успешным, если калории попали в безопасный коридор:
        boolean inSafeCorridor = currentCaloriesSum >= safeMinCalories && currentCaloriesSum <= (safeMaxCalories + 100);
        prefs.edit().putBoolean("calorie_success_" + selectedDate, inSafeCorridor).apply();
    }

    private void saveCalorieData() {
        SharedPreferences prefs = getSharedPreferences("MyLifePlanPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putInt("current_cals_sum_" + selectedDate, currentCaloriesSum);
        editor.putInt("current_protein_sum_" + selectedDate, currentProteinSum);
        editor.putInt("current_fat_sum_" + selectedDate, currentFatSum);
        editor.putInt("current_carbs_sum_" + selectedDate, currentCarbsSum);
        editor.putInt("current_water_" + selectedDate, currentWaterSum);
        editor.putInt("current_steps_" + selectedDate, currentStepsSum);

        StringBuilder sb = new StringBuilder();
        for (MealItem item : mealsList) {
            sb.append(item.getName()).append("|")
                    .append(item.getCalories()).append("|")
                    .append(item.getProtein()).append("|")
                    .append(item.getFat()).append("|")
                    .append(item.getCarbs()).append(";");
        }
        editor.putString("saved_meals_" + selectedDate, sb.toString());
        editor.apply();
    }

    private void loadCalorieData() {
        SharedPreferences prefs = getSharedPreferences("MyLifePlanPrefs", Context.MODE_PRIVATE);

        // Безопасный коридор (может быть рассчитан котиком и сохранен в памяти):
        safeMinCalories = prefs.getInt("safe_min_cals", 1400);
        safeMaxCalories = prefs.getInt("safe_max_cals", 1750);

        currentCaloriesSum = prefs.getInt("current_cals_sum_" + selectedDate, 0);
        currentProteinSum = prefs.getInt("current_protein_sum_" + selectedDate, 0);
        currentFatSum = prefs.getInt("current_fat_sum_" + selectedDate, 0);
        currentCarbsSum = prefs.getInt("current_carbs_sum_" + selectedDate, 0);

        currentWaterSum = prefs.getInt("current_water_" + selectedDate, 0);
        currentStepsSum = prefs.getInt("current_steps_" + selectedDate, 0);

        updateHabitsViews();

        boolean isToday = selectedDate.equals(activeTodayDate);
        View btnAdd = findViewById(R.id.btn_add_meal);
        if (btnAdd != null) btnAdd.setVisibility(isToday ? View.VISIBLE : View.INVISIBLE);

        mealsAdapter = new MealsAdapter(mealsList, isToday, position -> {
            if (position != RecyclerView.NO_POSITION && position < mealsList.size()) {
                MealItem removed = mealsList.get(position);
                currentCaloriesSum -= removed.getCalories();
                currentProteinSum -= removed.getProtein();
                currentFatSum -= removed.getFat();
                currentCarbsSum -= removed.getCarbs();

                mealsList.remove(position);
                mealsAdapter.notifyItemRemoved(position);
                updateCalorieViews();
                saveCalorieData();
            }
            return kotlin.Unit.INSTANCE;
        });
        rvMeals.setAdapter(mealsAdapter);

        mealsList.clear();
        String savedString = prefs.getString("saved_meals_" + selectedDate, "");
        if (!savedString.isEmpty()) {
            String[] items = savedString.split(";");
            for (String itemStr : items) {
                String[] parts = itemStr.split("\\|");
                if (parts.length >= 2) {
                    String name = parts[0];
                    int cals = Integer.parseInt(parts[1]);
                    int p = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
                    int f = parts.length > 3 ? Integer.parseInt(parts[3]) : 0;
                    int c = parts.length > 4 ? Integer.parseInt(parts[4]) : 0;
                    mealsList.add(new MealItem(name, cals, p, f, c));
                }
            }
        }

        float coins = prefs.getFloat("coins", 25.0f);
        if (tvCoinsScore != null) {
            tvCoinsScore.setText(String.format(Locale.US, "%.2f", coins));
        }

        mealsAdapter.notifyDataSetChanged();
        updateCalorieViews();
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}