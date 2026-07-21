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
    private lateinit var tvMainCoins: TextView // Поле для отображения монет

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Инициализация виджета монет
        tvMainCoins = findViewById(R.id.tv_main_coins)

        // 1. Инициализация RecyclerView списка
        setupRecyclerView()

        // 2. Загрузка сохраненного списка планов при старте приложения
        loadSavedData()

        // 3. Кнопка профиля (аватарка)
        val btnProfile = findViewById<ImageButton>(R.id.btn_profile)
        btnProfile?.setOnClickListener {
            Toast.makeText(this, "Открытие профиля", Toast.LENGTH_SHORT).show()
        }

        // 4. Иконка меню
        val btnMenu = findViewById<ImageView>(R.id.menu_icon)
        btnMenu?.setOnClickListener {
            Toast.makeText(this, "Открытие меню", Toast.LENGTH_SHORT).show()
        }

        // 5. Кнопка добавления нового плана (интерактивный ввод)
        val btnAddPlan = findViewById<TextView>(R.id.add_btn)
        btnAddPlan?.setOnClickListener {
            showAddPlanDialog() // Запуск встроенного выбора времени и ввода задачи
        }

        // 6. Инициализация 8 кнопок разделов
        setupGridButtons()
    }

    /**
     * Срабатывает автоматически каждый раз, когда мы возвращаемся на этот экран
     * (например, после завершения таймера Помодоро на экране Учебы)
     */
    override fun onResume() {
        super.onResume()
        // Считываем обновленный баланс монет и мгновенно выводим на экран
        val prefs = getSharedPreferences("MyLifePlanPrefs", Context.MODE_PRIVATE)
        val coins = prefs.getFloat("coins", 0.0f)
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
     * Показывает встроенные часы (TimePickerDialog) для выбора времени
     */
    private fun showAddPlanDialog() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        // 1. Встроенный диалог выбора времени
        val timePickerDialog = TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute)

            // 2. После успешного выбора времени запрашиваем текст задачи
            showTaskInputDialog(formattedTime)
        }, hour, minute, true)

        timePickerDialog.show()
    }

    /**
     * Показывает диалог ввода названия задачи для выбранного времени
     */
    private fun showTaskInputDialog(selectedTime: String) {
        val inputEditText = EditText(this)
        inputEditText.hint = "Например, lunch..."

        // Настраиваем внутренний отступ для поля ввода в диалоге
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
     * Метод сохранения списка планов в память устройства в виде строки
     */
    private fun savePlansData() {
        val prefs = getSharedPreferences("MyLifePlanPrefs", Context.MODE_PRIVATE)
        val serialized = plansList.joinToString(";") { "${it.time}|${it.task}" }
        prefs.edit().putString("saved_plans", serialized).commit() // commit() мгновенно записывает изменения в память
    }

    /**
     * Метод загрузки монет и списка планов при запуске приложения
     */
    private fun loadSavedData() {
        val prefs = getSharedPreferences("MyLifePlanPrefs", Context.MODE_PRIVATE)

        // Загрузка монет
        val coins = prefs.getFloat("coins", 0.0f)
        tvMainCoins.text = String.format(Locale.US, "%.2f", coins)

        // Загрузка планов
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
            // Если проект запускается впервые и сохранения нет, показываем стартовые задачи
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

    /**
     * Индивидуальный переход для каждой из 8 кнопок
     */
    private fun onSectionClicked(sectionNumber: Int) {
        when (sectionNumber) {
            1 -> {
                // Раздел 1: Учеба (StudyActivity)
                val intent = Intent(this, StudyActivity::class.java)
                startActivity(intent)
            }
            2 -> {
                // Плитка 2: Калории (CalorieActivity)
                val intent = Intent(this, CalorieActivity::class.java)
                startActivity(intent)
            }
            3 -> {
                Toast.makeText(this, "Открытие Раздела 3", Toast.LENGTH_SHORT).show()
            }
            4 -> {
                Toast.makeText(this, "Открытие Раздела 4", Toast.LENGTH_SHORT).show()
            }
            5 -> {
                Toast.makeText(this, "Открытие Раздела 5", Toast.LENGTH_SHORT).show()
            }
            6 -> {
                Toast.makeText(this, "Открытие Раздела 6", Toast.LENGTH_SHORT).show()
            }
            7 -> {
                Toast.makeText(this, "Открытие Раздела 7", Toast.LENGTH_SHORT).show()
            }
            8 -> {
                Toast.makeText(this, "Открытие Раздела 8", Toast.LENGTH_SHORT).show()
            }
        }
    }
}