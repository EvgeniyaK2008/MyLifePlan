package com.example.mylifeplan;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CalorieActivity extends AppCompatActivity {

    private ProgressBar pbCalories;
    private TextView tvCurrentCalories;
    private TextView tvTargetCalories;
    private EditText etHeight, etWeight, etAge;
    private Button btnCalculate;
    private LinearLayout rangesLayout;
    private TextView tvNormResult, tvSelectedTarget, tvDeficitRange, tvSurplusRange;
    private SeekBar sbCalorieTarget; // Ползунок

    private RecyclerView rvMeals;
    private MealsAdapter mealsAdapter;
    private List<MealItem> mealsList = new ArrayList<>();

    private int currentCaloriesSum = 0;
    private int targetCaloriesLimit = 1500; // Выбранная на ползунке цель
    private int baseCaloriesNorm = 1500;   // Оригинальная рассчитанная норма

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calorie);

        // Инициализация элементов
        pbCalories = findViewById(R.id.calorie_progress_bar);
        tvCurrentCalories = findViewById(R.id.tv_current_calories);
        tvTargetCalories = findViewById(R.id.tv_target_calories);
        etHeight = findViewById(R.id.et_height);
        etWeight = findViewById(R.id.et_weight);
        etAge = findViewById(R.id.et_age);
        btnCalculate = findViewById(R.id.btn_calculate);
        rangesLayout = findViewById(R.id.ranges_layout);
        tvNormResult = findViewById(R.id.tv_norm_result);
        tvSelectedTarget = findViewById(R.id.tv_selected_target);
        tvDeficitRange = findViewById(R.id.tv_deficit_range);
        tvSurplusRange = findViewById(R.id.tv_surplus_range);
        sbCalorieTarget = findViewById(R.id.sb_calorie_target);

        // Настройка списка продуктов
        rvMeals = findViewById(R.id.meals_recycler);
        rvMeals.setLayoutManager(new LinearLayoutManager(this));

        mealsAdapter = new MealsAdapter(mealsList, position -> {
            if (position != RecyclerView.NO_POSITION) {
                MealItem removed = mealsList.get(position);
                currentCaloriesSum -= removed.getCalories();
                if (currentCaloriesSum < 0) currentCaloriesSum = 0;

                mealsList.remove(position);
                mealsAdapter.notifyItemRemoved(position);
                mealsAdapter.notifyItemRangeChanged(position, mealsList.size());
                updateCalorieViews();
                saveCalorieData(); // Автосохранение [1.1.5]
            }
            return kotlin.Unit.INSTANCE;
        });
        rvMeals.setAdapter(mealsAdapter);

        // Назад
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        // Расчет калорий
        btnCalculate.setOnClickListener(v -> calculateCalories());

        // Настройка слушателя ползунка
        sbCalorieTarget.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    // Вычисляем минимальный безопасный дефицит
                    int minLimit = baseCaloriesNorm - 400;
                    if (minLimit < 1200) minLimit = 1200; // Физиологический порог не ниже 1200!

                    // Обновляем целевое значение на основе положения ползунка [1]
                    targetCaloriesLimit = minLimit + progress;
                    updateCalorieViews();
                    saveCalorieData(); // Автосохранение при каждом движении ползунка [1.1.5]
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Добавление еды
        TextView btnAddMeal = findViewById(R.id.btn_add_meal);
        btnAddMeal.setOnClickListener(v -> showAddMealDialog());

        // Загрузка сохраненных данных
        loadCalorieData();
    }

    private void calculateCalories() {
        String hStr = etHeight.getText().toString().trim();
        String wStr = etWeight.getText().toString().trim();
        String aStr = etAge.getText().toString().trim();

        if (hStr.isEmpty() || wStr.isEmpty() || aStr.isEmpty()) {
            Toast.makeText(this, "Пожалуйста, заполните все параметры!", Toast.LENGTH_SHORT).show();
            return;
        }

        double height = Double.parseDouble(hStr);
        double weight = Double.parseDouble(wStr);
        int age = Integer.parseInt(aStr);

        // Защитные валидаторы входных данных
        if (height < 120 || height > 220) {
            Toast.makeText(this, "Пожалуйста, укажите реальный рост (от 120 до 220 см)!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (weight < 35 || weight > 250) {
            Toast.makeText(this, "Пожалуйста, укажите реальный вес (от 35 до 250 кг)!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (age < 14 || age > 110) {
            Toast.makeText(this, "Возраст должен быть от 14 до 110 лет!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Формула Миффлина-Сан Жеора (с коэффициентом активности 1.2)
        baseCaloriesNorm = (int) (((10 * weight) + (6.25 * height) - (5 * age) + 5) * 1.2);

        // По умолчанию цель устанавливается на вычисленную норму
        targetCaloriesLimit = baseCaloriesNorm;

        setupCalorieSeekBar();
        updateCalorieViews();
        saveCalorieData(); // Сохраняем расчеты [1.1.5]
        Toast.makeText(this, "Параметры рассчитаны!", Toast.LENGTH_SHORT).show();
    }

    /**
     * Конфигурирует ползунок под рассчитанные лимиты
     */
    private void setupCalorieSeekBar() {
        // Минимальная планка: норма минус 400 ккал, но строго не ниже 1200 ккал
        int minLimit = baseCaloriesNorm - 400;
        if (minLimit < 1200) minLimit = 1200;

        // Максимальная планка: норма плюс 450 ккал
        int maxLimit = baseCaloriesNorm + 450;

        // Диапазон работы ползунка [1]
        int range = maxLimit - minLimit;

        sbCalorieTarget.setMax(range);

        // Позиционируем ползунок на текущую выбранную цель
        sbCalorieTarget.setProgress(targetCaloriesLimit - minLimit);
    }

    private void showAddMealDialog() {
        if (mealsList.size() >= 15) {
            Toast.makeText(this, "Максимум 15 приемов пищи за день", Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 40);

        final EditText etMealName = new EditText(this);
        etMealName.setHint("Название продукта");
        etMealName.setInputType(InputType.TYPE_CLASS_TEXT);
        layout.addView(etMealName);

        final EditText etMealCals = new EditText(this);
        etMealCals.setHint("Калорийность (ккал)");
        etMealCals.setInputType(InputType.TYPE_CLASS_NUMBER);
        layout.addView(etMealCals);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Добавить прием пищи")
                .setView(layout)
                .setPositiveButton("Добавить", (dialogInterface, i) -> {
                    String name = etMealName.getText().toString().trim();
                    String calsStr = etMealCals.getText().toString().trim();

                    if (!name.isEmpty() && !calsStr.isEmpty()) {
                        int cals = Integer.parseInt(calsStr);

                        mealsList.add(new MealItem(name, cals));
                        mealsAdapter.notifyItemInserted(mealsList.size() - 1);

                        currentCaloriesSum += cals;
                        updateCalorieViews();
                        saveCalorieData(); // Сохраняем [1.1.5]
                    } else {
                        Toast.makeText(CalorieActivity.this, "Заполните оба поля!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Отмена", null)
                .create();

        dialog.show();
    }

    private void updateCalorieViews() {
        tvCurrentCalories.setText(String.valueOf(currentCaloriesSum));
        tvTargetCalories.setText(String.valueOf(targetCaloriesLimit));

        pbCalories.setMax(targetCaloriesLimit);
        pbCalories.setProgress(Math.min(currentCaloriesSum, targetCaloriesLimit));

        // Вычисляем безопасные границы дефицита/профицита на основе ОРИГИНАЛЬНОЙ нормы
        int deficitMin = baseCaloriesNorm - 400;
        if (deficitMin < 1200) deficitMin = 1200;
        int deficitMax = baseCaloriesNorm - 200;
        if (deficitMax < 1200) deficitMax = 1200;

        int surplusMin = baseCaloriesNorm + 200;
        int surplusMax = baseCaloriesNorm + 450;

        tvNormResult.setText("Ваша норма: " + baseCaloriesNorm + " ккал");
        tvSelectedTarget.setText("Выбранная цель: " + targetCaloriesLimit + " ккал");
        tvDeficitRange.setText("Дефицит (похудение): " + deficitMin + " - " + deficitMax + " ккал");
        tvSurplusRange.setText("Профицит (набор): " + surplusMin + " - " + surplusMax + " ккал");
        rangesLayout.setVisibility(View.VISIBLE);
    }

    private void saveCalorieData() {
        SharedPreferences prefs = getSharedPreferences("MyLifePlanPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putInt("base_calorie_norm", baseCaloriesNorm);
        editor.putInt("calorie_target", targetCaloriesLimit);
        editor.putString("height", etHeight.getText().toString());
        editor.putString("weight", etWeight.getText().toString());
        editor.putString("age", etAge.getText().toString());

        StringBuilder sb = new StringBuilder();
        for (MealItem item : mealsList) {
            sb.append(item.getName()).append("|").append(item.getCalories()).append(";");
        }
        editor.putString("saved_meals", sb.toString());
        editor.commit(); // Записываем мгновенно [1.1.5]
    }

    private void loadCalorieData() {
        SharedPreferences prefs = getSharedPreferences("MyLifePlanPrefs", Context.MODE_PRIVATE);

        baseCaloriesNorm = prefs.getInt("base_calorie_norm", 1500);
        targetCaloriesLimit = prefs.getInt("calorie_target", 1500);
        etHeight.setText(prefs.getString("height", ""));
        etWeight.setText(prefs.getString("weight", ""));
        etAge.setText(prefs.getString("age", ""));

        String savedString = prefs.getString("saved_meals", "");
        mealsList.clear();
        currentCaloriesSum = 0;
        if (!savedString.isEmpty()) {
            String[] items = savedString.split(";");
            for (String itemStr : items) {
                String[] parts = itemStr.split("\\|");
                if (parts.length == 2) {
                    String name = parts[0];
                    int cals = Integer.parseInt(parts[1]);
                    mealsList.add(new MealItem(name, cals));
                    currentCaloriesSum += cals;
                }
            }
        }

        // Настраиваем ползунок
        if (!etHeight.getText().toString().isEmpty()) {
            setupCalorieSeekBar();
        }

        mealsAdapter.notifyDataSetChanged();
        updateCalorieViews();
    }
}