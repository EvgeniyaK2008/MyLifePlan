package com.example.mylifeplan

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var rvPlans: RecyclerView
    private lateinit var plansAdapter: PlansAdapter
    private val plansList = mutableListOf<PlanItem>()

    // Элементы верхней панели
    private lateinit var tvMainCoins: TextView

    // Элементы интерактива с котенком
    private lateinit var ivPet: ImageView
    private lateinit var tvPetStatus: TextView
    private lateinit var btnFeedPet: TextView
    private var isPetFed: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Связываем элементы верхней панели
        tvMainCoins = findViewById(R.id.tv_main_coins)

        // Связываем элементы домика котенка
        ivPet = findViewById(R.id.iv_pet)
        tvPetStatus = findViewById(R.id.tv_pet_status)
        btnFeedPet = findViewById(R.id.btn_feed_pet)

        // 1. Инициализация RecyclerView списка
        setupRecyclerView()

        // 2. Загрузка сохраненных планов и состояния котенка
        loadSavedData()

        // 3. Обработчик для кормления питомца
        btnFeedPet.setOnClickListener {
            feedKitten()
        }

        // 4. Кнопка профиля (аватарка)
        val btnProfile = findViewById<ImageButton>(R.id.btn_profile)
        btnProfile?.setOnClickListener {
            Toast.makeText(this, "Открытие профиля", Toast.LENGTH_SHORT).show()
        }

        // 5. Иконка меню
        val btnMenu = findViewById<ImageView>(R.id.menu_icon)
        btnMenu?.setOnClickListener {
            Toast.makeText(this, "Открытие меню", Toast.LENGTH_SHORT).show()
        }

        // 6. Кнопка добавления нового плана
        val btnAddPlan = findViewById<TextView>(R.id.add_btn)
        btnAddPlan?.setOnClickListener {
            showAddPlanDialog()
        }

        // 7. Инициализация 8 кнопок разделов
        setupGridButtons()
    }

    /**
     * Вызывается каждый раз, когда мы возвращаемся на главный экран (например, после учебы)
     */
    override fun onResume() {
        super.onResume()
        // Мгновенно обновляем баланс монет
        val prefs = getSharedPreferences("MyLifePlanPrefs", Context.MODE_PRIVATE)
        val coins = prefs.getFloat("coins", 100.0f)
        tvMainCoins.text = String.format(Locale.US, "%.2f", coins)
    }

    private fun setupRecyclerView() {
        rvPlans = findViewById(R.id.plans_recycler)
        rvPlans.layoutManager = LinearLayoutManager(this)

        // Передаем метод удаления в адаптер
        plansAdapter = PlansAdapter(plansList) { position ->
            if (position != RecyclerView.NO_POSITION) {
                plansList.removeAt(position)
                plansAdapter.notifyItemRemoved(position)
                plansAdapter.notifyItemRangeChanged(position, plansList.size)
                savePlansData() // Автосохранение списка после удаления пункта
            }
        }
        rvPlans.adapter = plansAdapter
    }

    /**
     * Встроенные часы (TimePickerDialog) для выбора времени
     */
    private fun showAddPlanDialog() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        val timePickerDialog = TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute)
            showTaskInputDialog(formattedTime)
        }, hour, minute, true)

        timePickerDialog.show()
    }

    /**
     * Диалог ввода названия задачи
     */
    private fun showTaskInputDialog(selectedTime: String) {
        val inputEditText = EditText(this)
        inputEditText.hint = "Например, lunch..."
        inputEditText.setPadding(50, 40, 50, 40)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Новая задача на $selectedTime")
            .setView(inputEditText)
            .setPositiveButton("Добавить") { _, _ ->
                if (plansList.size >= 12) {
                    Toast.makeText(this, "Достигнут лимит задач на день (максимум 12)", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val taskText = inputEditText.text.toString().trim()
                if (taskText.isNotEmpty()) {
                    plansList.add(PlanItem(selectedTime, taskText))
                    plansAdapter.notifyItemInserted(plansList.size - 1)
                    rvPlans.scrollToPosition(plansList.size - 1)
                    savePlansData() // Автосохранение списка после добавления пункта
                } else {
                    Toast.makeText(this, "Текст задачи не может быть пустым", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .create()

        dialog.show()
    }

    /**
     * Метод интерактивного взаимодействия с питомцем за звезды
     */
    private fun feedKitten() {
        val prefs = getSharedPreferences("MyLifePlanPrefs", Context.MODE_PRIVATE)
        var coins = prefs.getFloat("coins", 100.0f)

        if (isPetFed) {
            // Если питомец уже сыт, укладываем его спать обратно
            isPetFed = false
            prefs.edit().putBoolean("is_pet_fed", false).commit()
            updatePetVisualState()
            Toast.makeText(this, "Котёнок сладко потянулся и уснул... 💤", Toast.LENGTH_SHORT).show()
        } else {
            // Если голоден, проверяем баланс монет
            if (coins >= 10.0f) {
                coins -= 10.0f
                isPetFed = true

                // Записываем новые значения в память устройства
                prefs.edit().apply {
                    putFloat("coins", coins)
                    putBoolean("is_pet_fed", true)
                }.commit()

                // Обновляем визуальный интерфейс
                tvMainCoins.text = String.format(Locale.US, "%.2f", coins)
                updatePetVisualState()
                Toast.makeText(this, "Вы покормили котенка! Мяу! 🐾⭐", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Недостаточно звёзд! Требуется как минимум 10.00 ⭐", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Обновляет изображение и надписи котенка в зависимости от его сытости
     */
    private fun updatePetVisualState() {
        if (isPetFed) {
            ivPet.setImageResource(R.drawable.cat_happy) // Картинка довольного котенка
            tvPetStatus.text = "Котёнок сыт и мурчит! 🐾"
            btnFeedPet.text = "Спит (Разбудить за 10 ⭐)"
        } else {
            ivPet.setImageResource(R.drawable.cat_sleep) // Картинка спящего котенка
            tvPetStatus.text = "Котёнок спит... 💤"
            btnFeedPet.text = "Покормить (10 ⭐)"
        }
    }

    /**
     * Метод сохранения списка планов
     */
    private fun savePlansData() {
        val prefs = getSharedPreferences("MyLifePlanPrefs", Context.MODE_PRIVATE)
        val serialized = plansList.joinToString(";") { "${it.time}|${it.task}" }
        prefs.edit().putString("saved_plans", serialized).commit()
    }

    /**
     * Метод загрузки всех сохраненных данных
     */
    private fun loadSavedData() {
        val prefs = getSharedPreferences("MyLifePlanPrefs", Context.MODE_PRIVATE)

        // Считываем монеты
        val coins = prefs.getFloat("coins", 100.0f)
        tvMainCoins.text = String.format(Locale.US, "%.2f", coins)

        // Восстанавливаем состояние сытости котенка
        isPetFed = prefs.getBoolean("is_pet_fed", false)
        updatePetVisualState()

        // Считываем список планов
        val savedString = prefs.getString("saved_plans", null)
        plansList.clear()
        if (!savedString.isNullOrEmpty()) {
            savedString.split(";").forEach { itemStr ->
                val parts = itemStr.split("|")
                if (parts.size == 2) {
                    plansList.add(PlanItem(parts[0], parts[1]))
                }
            }
        } else {
            plansList.add(PlanItem("07:00", "wake up"))
            plansList.add(PlanItem("07:05", "work out"))
            plansList.add(PlanItem("07:15", "breakfast"))
        }
        plansAdapter.notifyDataSetChanged()
    }

    private fun setupGridButtons() {
        val buttonIds = arrayOf(
            R.id.btn_section_1, R.id.btn_section_2, R.id.btn_section_3, R.id.btn_section_4,
            R.id.btn_section_5, R.id.btn_section_6, R.id.btn_section_7, R.id.btn_section_8
        )

        for (i in buttonIds.indices) {
            val button = findViewById<ImageButton>(buttonIds[i])
            button?.setOnClickListener {
                val sectionNumber = i + 1
                onSectionClicked(sectionNumber)
            }
        }
    }

    private fun onSectionClicked(sectionNumber: Int) {
        when (sectionNumber) {
            1 -> {
                val intent = Intent(this, StudyActivity::class.java)
                startActivity(intent)
            }
            2 -> {
                val intent = Intent(this, CalorieActivity::class.java)
                startActivity(intent)
            }
            3 -> {
                // Раздел 3: Румбокс котенка (RoomBoxActivity)
                val intent = Intent(this, RoomBoxActivity::class.java)
                startActivity(intent)
            }
            else -> {
                Toast.makeText(this, "Открытие Раздела $sectionNumber", Toast.LENGTH_SHORT).show()
            }
        }
    }
}