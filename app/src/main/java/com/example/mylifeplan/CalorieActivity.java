package com.example.mylifeplan;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
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
import java.util.ArrayList;
import java.util.List;

public class CalorieActivity extends AppCompatActivity {

    private ProgressBar pbCalories;
    private TextView tvCurrentCalories;
    private TextView tvTargetCalories;
    private EditText etHeight, etWeight, etAge;
    private Button btnCalculate;
    private LinearLayout rangesLayout;
    private TextView tvNormResult, tvDeficitRange, tvSurplusRange;

    private RecyclerView rvMeals;
    private MealsAdapter mealsAdapter;
    private List<MealItem> mealsList = new ArrayList<>();

    private int currentCaloriesSum = 0;
    private int targetCaloriesLimit = 1500; // Целевая норма по умолчанию

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
        tvDeficitRange = findViewById(R.id.tv_deficit_range);
        tvSurplusRange = findViewById(R.id.tv_surplus_range);

        // Настройка списка продуктов
        rvMeals = findViewById(R.id.meals_recycler);
        rvMeals.setLayoutManager(new LinearLayoutManager(this));

        // Передаем логику удаления с возвратом Unit для совместимости с Kotlin
        mealsAdapter = new MealsAdapter(mealsList, position -> {
            if (position != RecyclerView.NO_POSITION) {
                // Вычитаем калории удаленного продукта из общей суммы
                MealItem removed = mealsList.get(position);
                currentCaloriesSum -= removed.getCalories();
                if (currentCaloriesSum < 0) currentCaloriesSum = 0;

                mealsList.remove(position);
                mealsAdapter.notifyItemRemoved(position);
                mealsAdapter.notifyItemRangeChanged(position, mealsList.size());
                updateCalorieViews();
            }
            return kotlin.Unit.INSTANCE;
        });
        rvMeals.setAdapter(mealsAdapter);

        // Назад
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        // Расчет калорий
        btnCalculate.setOnClickListener(v -> calculateCalories());

        // Добавление еды
        TextView btnAddMeal = findViewById(R.id.btn_add_meal);
        btnAddMeal.setOnClickListener(v -> showAddMealDialog());

        updateCalorieViews();
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

        // Упрощенная формула Миффлина-Сан Жеора (с коэффициентом активности 1.2)
        int norm = (int) (((10 * weight) + (6.25 * height) - (5 * age) + 5) * 1.2);
        targetCaloriesLimit = norm;

        // Расчет безопасных порогов дефицита/профицита
        int deficitMin = norm - 400;
        int deficitMax = norm - 200;
        int surplusMin = norm + 200;
        int surplusMax = norm + 450;

        // Отображение результатов на экране
        tvNormResult.setText("Ваша норма: " + norm + " ккал");
        tvDeficitRange.setText("Дефицит (похудение): " + deficitMin + " - " + deficitMax + " ккал");
        tvSurplusRange.setText("Профицит (набор): " + surplusMin + " - " + surplusMax + " ккал");

        rangesLayout.setVisibility(View.VISIBLE);
        updateCalorieViews();
        Toast.makeText(this, "Лимит калорий обновлен!", Toast.LENGTH_SHORT).show();
    }

    private void showAddMealDialog() {
        // Ограничение: максимум 15 приемов пищи за день
        if (mealsList.size() >= 15) {
            Toast.makeText(this, "Достигнут лимит записей за день (максимум 15)", Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 40);

        // Поле 1: Текстовое имя (строго текст)
        final EditText etMealName = new EditText(this);
        etMealName.setHint("Название продукта / блюда");
        etMealName.setInputType(InputType.TYPE_CLASS_TEXT);
        layout.addView(etMealName);

        // Поле 2: Калорийность (строго число)
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

                        // Добавление в список
                        mealsList.add(new MealItem(name, cals));
                        mealsAdapter.notifyItemInserted(mealsList.size() - 1);

                        // Обновление общего счетчика калорий
                        currentCaloriesSum += cals;
                        updateCalorieViews();
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
    }
}