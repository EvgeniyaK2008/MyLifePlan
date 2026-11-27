package com.example.mylifeplan

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    // Элементы верхней панели
    private lateinit var tvMainCoins: TextView
    private lateinit var tvCalendarMonth: TextView
    private lateinit var ivMotivation: ImageView

    // Текстовое поле расписания (вместо сложного RecyclerView)
    private lateinit var tvPlansContent: TextView

    // Элементы котенка
    private lateinit var ivPet: ImageView
    private lateinit var tvPetStatus: TextView
    private lateinit var btnFeedPet: TextView
    private var isPetHappy: Boolean = false

    // Переменные календаря и дат
    private var selectedDate: String = ""
    private var activeTodayDate: String = ""
    private lateinit var currentWeekMondayCalendar: Calendar
    private val dateHighlightViews = mutableMapOf<String, TextView>()

    // Плеер фоновой lofi-музыки
    private var mediaPlayer: MediaPlayer? = null
    private var isMusicEnabled: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Связываем элементы экрана с разметкой
        tvMainCoins = findViewById(R.id.tv_main_coins)
        tvCalendarMonth = findViewById(R.id.tv_calendar_month)
        ivMotivation = findViewById(R.id.iv_motivation)
        tvPlansContent = findViewById(R.id.tv_plans_content)

        ivPet = findViewById(R.id.iv_pet)
        tvPetStatus = findViewById(R.id.tv_pet_status)
        btnFeedPet = findViewById(R.id.btn_feed_pet)

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val realTodayStr = sdf.format(Date())

        activeTodayDate = realTodayStr
        selectedDate = realTodayStr

        // Инициализация календаря и дат запускается ВСЕГДА
        initSecureTodayDate()
        initCalendarToCurrentMonday()
        setupDynamicHomeCalendar()

        // Проверяем ежедневный статус и загружаем сохраненные планы
        checkDailyStatusUpdate()
        loadSavedDataForSelectedDate()
        updateMotivationImageRotation()

        // Запуск музыки, если она была включена ранее
        val prefs = getSharedPreferences("MyLifePlanPrefs", Context.MODE_PRIVATE)
        isMusicEnabled = prefs.getBoolean("is_music_on", false)
        if (isMusicEnabled) {
            startBackgroundMusic()
        }

        // Кнопка кормления котенка
        btnFeedPet.setOnClickListener {
            feedKitten()
        }

        // Клик по профилю (диалог с отчетом)
        val btnProfile = findViewById<ImageButton>(R.id.btn_profile)
        btnProfile?.setOnClickListener {
            val coins = prefs.getFloat("coins", 25.0f)
            val timerToday = prefs.getBoolean("is_timer_completed_today", false)
            val calorieToday = prefs.getBoolean("is_calorie_compliant_today", false)

            val statusText = StringBuilder()
            statusText.append("🌟 Your Stars: ").append(String.format(Locale.US, "%.2f", coins)).append("\n\n")
            statusText.append("📅 Active Date: ").append(selectedDate).append("\n\n")
            statusText.append("🎯 Today's Quests:\n")
            statusText.append(if (timerToday) "  • Focus Study: Completed! ✅\n" else "  • Focus Study: Pending... ⏳\n")
            statusText.append(if (calorieToday) "  • Calorie Target: Compliant! ✅\n" else "  • Calorie Target: Pending... ⏳\n\n")
            statusText.append("Keep going! Every effort counts toward a cozier, happier space for your kitten. 🐾")

            AlertDialog.Builder(this)
                .setTitle("🐾 Profile & Daily Progress")
                .setMessage(statusText.toString())
                .setPositiveButton("Comforting, thanks!", null)
                .create()
                .show()
        }

        // Секретный триггер для демонстрационного видео на хакатон
        btnProfile?.setOnLongClickListener {
            val mPrefs = getSharedPreferences("MyLifePlanPrefs", Context.MODE_PRIVATE)

            mPrefs.edit().apply {
                putBoolean("is_timer_completed_today", true)
                putBoolean("is_calorie_compliant_today", true)
                putString("last_checked_date", "2026-08-07")
            }.commit()

            checkDailyStatusUpdate()

            val coins = mPrefs.getFloat("coins", 0.0f)
            tvMainCoins.text = String.format(Locale.US, "%.2f", coins)

            Toast.makeText(this, "DEBUG: Simulated next day success!", Toast.LENGTH_SHORT).show()
            true
        }

        // Кнопка меню: переключение фоновой музыки по клику
        val btnMenu = findViewById<ImageView>(R.id.menu_icon)
        btnMenu?.setOnClickListener {
            playOrPauseMusic()
        }

        // Кнопки переключения недель в календаре
        val btnPrevWeek = findViewById<ImageButton>(R.id.btn_prev_week)
        val btnNextWeek = findViewById<ImageButton>(R.id.btn_next_week)

        btnPrevWeek?.setOnClickListener {
            shiftCalendarWeek(-7)
        }

        btnNextWeek?.setOnClickListener {
            shiftCalendarWeek(7)
        }

        setupGridButtons()
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("MyLifePlanPrefs", Context.MODE_PRIVATE)
        val coins = prefs.getFloat("coins", 25.0f)
        tvMainCoins.text = String.format(Locale.US, "%.2f", coins)

        isPetHappy = prefs.getBoolean("is_pet_happy", false)
        updatePetVisualState()

        // Всегда обновляем текст планов при возврате на экран
        loadSavedDataForSelectedDate()

        if (isMusicEnabled) {
            startBackgroundMusic()
        }
    }

    private fun initSecureTodayDate() {
        val prefs = getSharedPreferences("MyLifePlanPrefs", Context.MODE_PRIVATE)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val systemToday = sdf.format(Date())

        val maxEncounteredDate = prefs.getString("max_date_encountered", systemToday) ?: systemToday

        if (systemToday < maxEncounteredDate) {
            activeTodayDate = maxEncounteredDate
            Toast.makeText(this, "Time travel detected! Using secure date: $activeTodayDate", Toast.LENGTH_LONG).show()
        } else {
            activeTodayDate = systemToday
            prefs.edit().putString("max_date_encountered", systemToday).commit()
        }
    }

    private fun initCalendarToCurrentMonday() {
        currentWeekMondayCalendar = Calendar.getInstance()
        while (currentWeekMondayCalendar.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            currentWeekMondayCalendar.add(Calendar.DAY_OF_YEAR, -1)
        }
    }

    private fun setupDynamicHomeCalendar() {
        val calendar = currentWeekMondayCalendar.clone() as Calendar

        val sdfDay = SimpleDateFormat("d", Locale.US)
        val sdfFull = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sdfMonth = SimpleDateFormat("MMMM yyyy", Locale.US)

        tvCalendarMonth.text = sdfMonth.format(calendar.time)

        val dayViews = listOf(
            findViewById<TextView>(R.id.tv_day_17) to findViewById<LinearLayout>(R.id.day_mo),
            findViewById<TextView>(R.id.tv_day_18) to findViewById<LinearLayout>(R.id.day_tu),
            findViewById<TextView>(R.id.tv_day_19) to findViewById<LinearLayout>(R.id.day_we),
            findViewById<TextView>(R.id.tv_day_20) to findViewById<LinearLayout>(R.id.day_th),
            findViewById<TextView>(R.id.tv_day_21) to findViewById<LinearLayout>(R.id.day_fr),
            findViewById<TextView>(R.id.tv_day_22) to findViewById<LinearLayout>(R.id.day_sa),
            findViewById<TextView>(R.id.tv_day_23) to findViewById<LinearLayout>(R.id.day_su)
        )

        dateHighlightViews.clear()

        dayViews.forEach { (tv, container) ->
            val dateStr = sdfFull.format(calendar.time)

            tv?.text = sdfDay.format(calendar.time)
            if (tv != null) {
                dateHighlightViews[dateStr] = tv
                tv.setBackgroundResource(0)
            }

            if (dateStr == selectedDate) {
                tv?.setBackgroundResource(R.drawable.bg_selected_day)
            }

            container?.setOnClickListener { selectCalendarDate(dateStr) }
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    private fun shiftCalendarWeek(days: Int) {
        currentWeekMondayCalendar.add(Calendar.DAY_OF_YEAR, days)
        setupDynamicHomeCalendar()

        val sdfFull = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        selectedDate = sdfFull.format(currentWeekMondayCalendar.time)
        setupDynamicHomeCalendar()
        loadSavedDataForSelectedDate()
    }

    private fun selectCalendarDate(newDate: String) {
        val oldHighlight = dateHighlightViews[selectedDate]
        oldHighlight?.setBackgroundResource(0)

        selectedDate = newDate
        highlightSelectedDateInCalendar()

        loadSavedDataForSelectedDate()
        Toast.makeText(this, "Viewing schedule for $selectedDate", Toast.LENGTH_SHORT).show()
    }

    private fun highlightSelectedDateInCalendar() {
        val newHighlight = dateHighlightViews[selectedDate]
        newHighlight?.setBackgroundResource(R.drawable.bg_selected_day)
    }

    private fun checkDailyStatusUpdate() {
        val prefs = getSharedPreferences("MyLifePlanPrefs", Context.MODE_PRIVATE)
        val lastCheckedDate = prefs.getString("last_checked_date", "")

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayStr = sdf.format(Date())

        if (lastCheckedDate.isNullOrEmpty()) {
            prefs.edit().apply {
                putString("last_checked_date", todayStr)
                putString("install_date", todayStr)
                putFloat("coins", 25.0f)
                putBoolean("is_pet_happy", true)
                putInt("current_feed_price", 1)
                putInt("consecutive_fail_days", 0)
            }.commit()

            isPetHappy = true
            updatePetVisualState()
            return
        }

        if (lastCheckedDate != todayStr) {
            val timerCompleted = prefs.getBoolean("is_timer_completed_today", false)
            val calorieCompliant = prefs.getBoolean("is_calorie_compliant_today", false)

            var coins = prefs.getFloat("coins", 25.0f)
            var consecutiveFails = prefs.getInt("consecutive_fail_days", 0)
            var feedPrice = prefs.getInt("current_feed_price", 1)

            val editor = prefs.edit()

            if (timerCompleted && calorieCompliant) {
                isPetHappy = true
                coins += 3.0f
                consecutiveFails = 0
                feedPrice = 1

                editor.putBoolean("is_pet_happy", true)
                editor.putFloat("coins", coins)
                editor.putInt("consecutive_fail_days", 0)
                editor.putInt("current_feed_price", 1)

                Toast.makeText(this, "Daily targets completed! You earned +3.00 ⭐!", Toast.LENGTH_LONG).show()
            } else {
                isPetHappy = false
                consecutiveFails++
                feedPrice = Math.min(10, consecutiveFails)

                editor.putBoolean("is_pet_happy", false)
                editor.putInt("consecutive_fail_days", consecutiveFails)
                editor.putInt("current_feed_price", feedPrice)

                Toast.makeText(this, "Targets missed. Kitten is hungry! Feed cost: $feedPrice ⭐", Toast.LENGTH_LONG).show()
            }

            editor.putBoolean("is_timer_completed_today", false)
            editor.putBoolean("is_calorie_compliant_today", false)
            editor.putString("last_checked_date", todayStr)
            editor.commit()

            updatePetVisualState()
        }
    }

    private fun updateMotivationImageRotation() {
        val cal = Calendar.getInstance()
        val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)

        val motivationResId = when (dayOfMonth % 3) {
            0 -> R.drawable.motivation
            1 -> R.drawable.motivation_1
            else -> R.drawable.motivation_2
        }
        ivMotivation.setImageResource(motivationResId)
    }

    private fun feedKitten() {
        val prefs = getSharedPreferences("MyLifePlanPrefs", Context.MODE_PRIVATE)
        var coins = prefs.getFloat("coins", 25.0f)
        val feedPrice = prefs.getInt("current_feed_price", 1)

        if (isPetHappy) {
            Toast.makeText(this, "Kitten is already happy and purring! 🐾", Toast.LENGTH_SHORT).show()
        } else {
            if (coins >= feedPrice) {
                coins -= feedPrice
                isPetHappy = true

                prefs.edit().apply {
                    putFloat("coins", coins)
                    putBoolean("is_pet_happy", true)
                }.commit()

                tvMainCoins.text = String.format(Locale.US, "%.2f", coins)
                updatePetVisualState()

                Toast.makeText(this, "You fed the kitten! Purr! 🐾⭐", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Not enough stars! Need $feedPrice.00 ⭐", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updatePetVisualState() {
        val prefs = getSharedPreferences("MyLifePlanPrefs", Context.MODE_PRIVATE)
        val feedPrice = prefs.getInt("current_feed_price", 1)

        if (isPetHappy) {
            ivPet.setImageResource(R.drawable.cat_happy)
            tvPetStatus.text = "Kitten is happy and purring! 🐾"
            btnFeedPet.visibility = View.INVISIBLE
        } else {
            ivPet.setImageResource(R.drawable.cat_sleep)
            tvPetStatus.text = "Kitten is hungry... 💤"
            btnFeedPet.text = "Feed ($feedPrice ⭐)"
            btnFeedPet.visibility = View.VISIBLE
        }
    }

    private fun playOrPauseMusic() {
        val prefs = getSharedPreferences("MyLifePlanPrefs", Context.MODE_PRIVATE)

        if (mediaPlayer?.isPlaying == true) {
            pauseBackgroundMusic()
            isMusicEnabled = false
            prefs.edit().putBoolean("is_music_on", false).commit()
            Toast.makeText(this, "Music Off 🔇", Toast.LENGTH_SHORT).show()
        } else {
            isMusicEnabled = true
            prefs.edit().putBoolean("is_music_on", true).commit()
            startBackgroundMusic()
            Toast.makeText(this, "Music On 🔊", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startBackgroundMusic() {
        if (mediaPlayer == null) {
            try {
                mediaPlayer = MediaPlayer.create(this, R.raw.cat).apply {
                    isLooping = true
                    setVolume(0.35f, 0.35f)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        mediaPlayer?.start()
    }

    private fun pauseBackgroundMusic() {
        mediaPlayer?.pause()
    }

    private fun stopBackgroundMusic() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    /**
     * Загружает расписание чистым текстом из памяти устройства
     */
    private fun loadSavedDataForSelectedDate() {
        val prefs = getSharedPreferences("MyLifePlanPrefs", Context.MODE_PRIVATE)

        val coins = prefs.getFloat("coins", 25.0f)
        tvMainCoins.text = String.format(Locale.US, "%.2f", coins)

        isPetHappy = prefs.getBoolean("is_pet_happy", false)
        updatePetVisualState()

        val isToday = (selectedDate == activeTodayDate)

        // Загружаем сохраненную строку планов на выбранную дату
        val savedString = prefs.getString("saved_plans_$selectedDate", null)

        if (!savedString.isNullOrEmpty()) {
            val formattedLines = savedString.split(";").mapNotNull { itemStr ->
                val parts = itemStr.split("|")
                if (parts.size == 2 && parts[0].isNotEmpty()) {
                    "${parts[0]}   •   ${parts[1]}"
                } else null
            }
            tvPlansContent.text = formattedLines.joinToString("\n")
        } else {
            if (isToday) {
                tvPlansContent.text = "07:00   •   Wake up\n07:05   •   Workout\n07:15   •   Breakfast"
            } else {
                tvPlansContent.text = "No plans recorded for this day."
            }
        }
    }

    private fun setupGridButtons() {
        val buttonIds = arrayOf(
            R.id.btn_section_1, R.id.btn_section_2, R.id.btn_section_3, R.id.btn_section_4
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
            1 -> startActivity(Intent(this, StudyActivity::class.java))
            2 -> startActivity(Intent(this, CalorieActivity::class.java))
            3 -> startActivity(Intent(this, RoomBoxActivity::class.java))
            4 -> startActivity(Intent(this, AiChatActivity::class.java))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBackgroundMusic()
    }
}