package com.example.mylifeplan

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Scanner

class AiChatActivity : AppCompatActivity() {

    private lateinit var rvChat: RecyclerView
    private lateinit var chatAdapter: ChatAdapter
    private val messageList = mutableListOf<ChatMessage>()

    private lateinit var etInput: EditText
    private lateinit var btnSend: ImageButton

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("MyLifePlanPrefs", Context.MODE_PRIVATE)
    }

    companion object {
        private const val GEMINI_API_KEY = "API-Key"
        private const val GEMINI_URL = "https://lively-field-4bff.lee25seoyeon2008.workers.dev"

        private const val SYSTEM_INSTRUCTION =
            "You are a fluffy, pastel-blue kitten companion in the cozy app MyLifePlan. " +
                    "Communicate gently, warmly, and lovingly. Always support the user, " +
                    "Respond concisely, warmly, and to the point in English" +
                    "Provide only ONE short response per turn. Never generate responses on behalf of the user! " +
                    "CRITICAL SCHEDULE RULE: Whenever the user asks to plan their day, organize tasks, or make a schedule, " +
                    "you MUST append a hidden JSON schedule at the very end wrapped in <schedule> and </schedule> tags! " +
                    "Example: " +
                    "Here is your cozy daily plan! 🐾⭐ <schedule>{\"schedule\":[{\"time\":\"09:00\",\"task\":\"Morning stretch & tea\"},{\"time\":\"11:00\",\"task\":\"Focus session\"},{\"time\":\"14:00\",\"task\":\"Cozy walk outside\"}]}</schedule> " +
                    "Always use strict JSON with 'time' and 'task' keys inside the <schedule> tags."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_chat)

        rvChat = findViewById(R.id.chat_recycler)
        etInput = findViewById(R.id.et_message_input)
        btnSend = findViewById(R.id.btn_send_message)

        rvChat.layoutManager = LinearLayoutManager(this)
        chatAdapter = ChatAdapter(messageList)
        rvChat.adapter = chatAdapter

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        loadChatHistory()

        findViewById<ImageButton>(R.id.btn_open_map)?.setOnClickListener {
            val intent = Intent(this, CozySpotsMapActivity::class.java)
            startActivity(intent)
        }

        btnSend.setOnClickListener { sendMessage() }
    }

    private fun buildDailySnapshot(): String {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val currentTime = SimpleDateFormat("HH:mm", Locale.US).format(Date())

        // 1. Калории и коридор
        val cals = prefs.getInt("current_cals_sum_$today", 0)
        val minSafe = prefs.getInt("safe_min_cals", 1400)
        val maxSafe = prefs.getInt("safe_max_cals", 1750)
        val meals = prefs.getString("saved_meals_$today", "None")

        // 2. Детальный разбор дедлайнов и задач за сегодня
        val tasksJson = prefs.getString("saved_study_tasks", null)
        val deadlineDetails = StringBuilder()

        if (!tasksJson.isNullOrEmpty()) {
            try {
                val array = JSONArray(tasksJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.getString("id")
                    val title = obj.getString("title")
                    val due = obj.optString("deadline", "No deadline")
                    val target = obj.optInt("target", 60)

                    // Достаем индивидуальный прогресс этой задачи на сегодня:
                    val spentToday = prefs.getInt("task_progress_${id}_$today", 0)
                    val status = if (spentToday >= target) "[DONE ✅]" else "[IN PROGRESS]"

                    deadlineDetails.append("\n  - $title: Due $due | Focus: $spentToday/${target}m $status")
                }
            } catch (e: Exception) {
                deadlineDetails.append(" None")
            }
        } else {
            deadlineDetails.append(" No active goals right now.")
        }

        val todayPlans = prefs.getString("saved_plans_$today", "None")

        return """
        [REAL-TIME USER CONTEXT:
         Current Time: $currentTime
         Active Goals & Deadlines:$deadlineDetails
         Nutrition: $cals kcal (Target safe corridor: $minSafe - $maxSafe kcal). Meals logged: $meals
         Today's active schedule: $todayPlans
         Tone & Health Rule: You are aware of user goals and their logged progress. Gently encourage focus and healthy eating. Never praise starvation!]
        """.trimIndent()
    }

    private fun sendMessage() {
        val query = etInput.text.toString().trim()
        if (query.isEmpty()) return

        val jsonPayload = buildGeminiChatJson(query)

        messageList.add(ChatMessage(query, true))
        chatAdapter.notifyItemInserted(messageList.size - 1)
        rvChat.scrollToPosition(messageList.size - 1)

        saveChatHistory()
        etInput.setText("")

        lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) {
                getResponseFromGemini(jsonPayload)
            }

            if (!response.isNullOrEmpty()) {
                val displayMessage: String

                if (response.contains("<schedule>")) {
                    displayMessage = response.substringBefore("<schedule>").trim()
                    val jsonString = response.substringAfter("<schedule>").substringBefore("</schedule>").trim()
                    parseAndSaveAiSchedule(jsonString)
                } else {
                    displayMessage = response
                }

                messageList.add(ChatMessage(displayMessage, false))
            } else {
                messageList.add(ChatMessage("Meow... I got a bit distracted. Could you please ask me again? 🐾", false))
            }

            chatAdapter.notifyItemInserted(messageList.size - 1)
            rvChat.scrollToPosition(messageList.size - 1)
            saveChatHistory()
        }
    }

    private fun getResponseFromGemini(jsonPayload: String): String? {
        return try {
            val url = URL(GEMINI_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 30000
            conn.readTimeout = 30000

            val os = conn.outputStream
            os.write(jsonPayload.toByteArray(charset("UTF-8")))
            os.close()

            val responseCode = conn.getResponseCode()
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val scanner = Scanner(conn.inputStream, "UTF-8")
                val responseBody = if (scanner.useDelimiter("\\A").hasNext()) scanner.next() else ""
                scanner.close()

                try {
                    val jsonResponse = JSONObject(responseBody)

                    if (jsonResponse.has("candidates")) {
                        jsonResponse.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text").trim()
                    } else if (jsonResponse.has("error")) {
                        val errorText = jsonResponse.getJSONObject("error").getString("message")
                        runOnUiThread { Toast.makeText(this@AiChatActivity, "Google Error: $errorText", Toast.LENGTH_LONG).show() }
                        null
                    } else {
                        runOnUiThread { Toast.makeText(this@AiChatActivity, "Raw Response: $responseBody", Toast.LENGTH_LONG).show() }
                        null
                    }
                } catch (jsonEx: Exception) {
                    runOnUiThread { Toast.makeText(this@AiChatActivity, "Parse Error. Raw response: $responseBody", Toast.LENGTH_LONG).show() }
                    null
                }
            } else {
                val errorStream = conn.errorStream
                var errorDetail = ""
                if (errorStream != null) {
                    val scanner = Scanner(errorStream, "UTF-8")
                    val errorBody = if (scanner.useDelimiter("\\A").hasNext()) scanner.next() else ""
                    scanner.close()
                    try {
                        val jsonError = JSONObject(errorBody)
                        errorDetail = jsonError.getJSONObject("error").getString("message")
                    } catch (e: Exception) {
                        errorDetail = errorBody
                    }
                }

                val errorMsg = "HTTP $responseCode: $errorDetail"
                runOnUiThread { Toast.makeText(this@AiChatActivity, errorMsg, Toast.LENGTH_LONG).show() }
                null
            }
        } catch (e: java.net.SocketTimeoutException) {
            runOnUiThread { Toast.makeText(this@AiChatActivity, "Connection Timeout!", Toast.LENGTH_LONG).show() }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            val exMsg = "Exception: ${e.message}"
            runOnUiThread { Toast.makeText(this@AiChatActivity, exMsg, Toast.LENGTH_LONG).show() }
            null
        }
    }

    private fun buildGeminiChatJson(currentQuery: String): String {
        try {
            val jsonBody = JSONObject()

            val systemInstruction = JSONObject()
            val systemParts = JSONArray()
            val systemPartObj = JSONObject()
            // СТАЛО (подмешиваем наш свежий срез дня прямо в инструкцию котика):
            val combinedInstruction = SYSTEM_INSTRUCTION + "\n\n" + buildDailySnapshot()
            systemPartObj.put("text", combinedInstruction)
            systemParts.put(systemPartObj)
            systemInstruction.put("parts", systemParts)
            jsonBody.put("systemInstruction", systemInstruction)

            val contentsArray = JSONArray()
            val historyLimit = 6
            val startIdx = Math.max(0, messageList.size - historyLimit)

            for (i in startIdx until messageList.size) {
                val msg = messageList[i]

                if (contentsArray.length() == 0 && !msg.isUser) {
                    continue
                }

                val contentObj = JSONObject()
                contentObj.put("role", if (msg.isUser) "user" else "model")

                val partsArray = JSONArray()
                val partObj = JSONObject()
                partObj.put("text", msg.text)
                partsArray.put(partObj)

                contentObj.put("parts", partsArray)
                contentsArray.put(contentObj)
            }

            val currentMsg = JSONObject()
            currentMsg.put("role", "user")
            val currentParts = JSONArray()
            val currentPart = JSONObject()
            currentPart.put("text", currentQuery)
            currentParts.put(currentPart)
            currentMsg.put("parts", currentParts)
            contentsArray.put(currentMsg)

            jsonBody.put("contents", contentsArray)
            return jsonBody.toString()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }

    private fun saveChatHistory() {
        val limit = 15
        val startIdx = Math.max(0, messageList.size - limit)
        val subList = messageList.subList(startIdx, messageList.size)

        val serialized = subList.joinToString(";") {
            val cleanText = it.text.replace("|", "/").replace(";", ",")
            "$cleanText|${it.isUser}"
        }

        prefs.edit().putString("saved_chat_history", serialized).commit()
    }

    private fun parseAndSaveAiSchedule(jsonString: String) {
        try {
            val jsonObject = JSONObject(jsonString)
            val scheduleArray = jsonObject.getJSONArray("schedule")

            val temporaryPlansList = mutableListOf<String>()

            for (i in 0 until scheduleArray.length()) {
                val item = scheduleArray.getJSONObject(i)
                val time = item.getString("time")
                val task = item.getString("task")
                temporaryPlansList.add("$time|$task")
            }

            val serializedPlans = temporaryPlansList.joinToString(";")

            val prefs = getSharedPreferences("MyLifePlanPrefs", Context.MODE_PRIVATE)
            val activeTodayDate = prefs.getString("max_date_encountered", "") ?: ""
            prefs.edit().putString("saved_plans_$activeTodayDate", serializedPlans).commit()

            runOnUiThread {
                Toast.makeText(this, "Meow! Today's schedule has been updated! 🐾⭐", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                Toast.makeText(this, "Failed to parse AI schedule!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadChatHistory() {
        val savedString = prefs.getString("saved_chat_history", null)

        messageList.clear()

        if (!savedString.isNullOrEmpty()) {
            savedString.split(";").forEach { itemStr ->
                val parts = itemStr.split("|")
                if (parts.size == 2) {
                    val text = parts[0]
                    val isUser = parts[1].toBoolean()
                    messageList.add(ChatMessage(text, isUser))
                }
            }
        } else {
            messageList.add(ChatMessage("Meow! Hello! I'm so happy to see you in our cozy room. How is your day going? 🐾✨", false))
        }
        chatAdapter.notifyDataSetChanged()
        rvChat.scrollToPosition(messageList.size - 1)
    }
}