package com.example.mylifeplan

import android.content.Context
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Locale
import java.util.Scanner

class AiChatActivity : AppCompatActivity() {

    private lateinit var rvChat: RecyclerView
    private lateinit var chatAdapter: ChatAdapter
    private val messageList = mutableListOf<ChatMessage>()

    private lateinit var etInput: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var switchAiMode: SwitchCompat

    private var useLocalAI = false
    private var llmInference: LlmInference? = null
    private var isLocalModelLoaded = false

    companion object {
        private const val MODEL_FILE_NAME = "gemma.bin"

        // ВСТАВЬТЕ СЮДА ВАШ БЕСПЛАТНЫЙ КЛЮЧ ИЗ GOOGLE AI STUDIO [1.1.5]
        private const val GEMINI_API_KEY = "API-Key"
        private const val GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$GEMINI_API_KEY"

        private const val SYSTEM_INSTRUCTION =
            "You are a fluffy, soft light-blue companion kitten in the cozy application MyLifePlan. " +
                    "Speak very affectionately, sweetly, and caringly. Always support the user, " +
                    "use 'Meow', and emojis like 🐾 and ⭐. Answer briefly, warmly, and to the point in English. " +
                    "Give only ONE short response per turn. It is strictly forbidden to write responses on behalf of the user!"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_chat)

        rvChat = findViewById(R.id.chat_recycler)
        etInput = findViewById(R.id.et_message_input)
        btnSend = findViewById(R.id.btn_send_message)
        switchAiMode = findViewById(R.id.switch_ai_mode)

        rvChat.layoutManager = LinearLayoutManager(this)
        chatAdapter = ChatAdapter(messageList)
        rvChat.adapter = chatAdapter

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        loadChatHistory()
        switchAiMode.setOnCheckedChangeListener { _, isChecked ->
            useLocalAI = isChecked
            val msg = if (useLocalAI) "Switched to Offline Mode (Local Gemma)" else "Switched to Online Mode (Cloud Gemini 1.5)"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // Инициализируем локальный ИИ в корутине на фоновом IO потоке! [1]
        lifecycleScope.launch(Dispatchers.IO) {
            initLocalLLM()
        }

        btnSend.setOnClickListener { sendMessage() }
    }

    private suspend fun initLocalLLM() {
        try {
            val modelFile = File(filesDir, MODEL_FILE_NAME)

            // 1. Если файл модели отсутствует во внутренней памяти [2.14]
            if (!modelFile.exists()) {
                withContext(Dispatchers.Main) {
                    messageList.add(ChatMessage("Мяу... Я не нашел файл локальной модели gemma.bin во внутренней памяти приложения! Пожалуйста, загрузите его по пути data/data/com.example.mylifeplan/files/ для оффлайн-режима! 😿", false))
                    chatAdapter.notifyItemInserted(messageList.size - 1)
                    rvChat.scrollToPosition(messageList.size - 1)
                }
                return
            }

            // 2. Оповещаем о начале загрузки модели в ОЗУ [1]
            withContext(Dispatchers.Main) {
                Toast.makeText(this@AiChatActivity, "Загрузка локальной модели Gemma в ОЗУ... Подождите...", Toast.LENGTH_LONG).show()
            }

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(256)
                .build()

            // Инициализация тяжелого ИИ-движка в фоне [2.14]
            val inferenceEngine = LlmInference.createFromOptions(this@AiChatActivity, options)
            llmInference = inferenceEngine
            isLocalModelLoaded = true

            // 3. Оповещаем об успешном завершении загрузки! [1]
            withContext(Dispatchers.Main) {
                messageList.add(ChatMessage("Мяу! Локальная нейросеть Gemma успешно загружена в память телефона и готова к оффлайн-работе! ⭐🐾", false))
                chatAdapter.notifyItemInserted(messageList.size - 1)
                rvChat.scrollToPosition(messageList.size - 1)
                Toast.makeText(this@AiChatActivity, "Локальная модель готова!", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            // 4. Оповещаем в случае ошибки компиляции или нехватки памяти устройства [1]
            withContext(Dispatchers.Main) {
                Toast.makeText(this@AiChatActivity, "Ошибка загрузки ИИ-модели: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun sendMessage() {
        val query = etInput.text.toString().trim()
        if (query.isEmpty()) return

        // Если пользователь выбрал локальный режим, но модель Gemma еще не загрузилась в ОЗУ
        if (useLocalAI && !isLocalModelLoaded) {
            Toast.makeText(this, "Local model is still loading or missing gemma.bin...", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Собираем промпты с историей ДО добавления нового вопроса на экран [1.1.5]
        val jsonPayload = buildGeminiChatJson(query)
        val localPrompt = buildLocalPromptWithHistory(query)

        // 2. Отображаем сообщение пользователя на экране
        messageList.add(ChatMessage(query, true))
        chatAdapter.notifyItemInserted(messageList.size - 1)
        rvChat.scrollToPosition(messageList.size - 1)

        // 3. Автосохранение: записываем реплику пользователя в локальную память [1.1.5]
        saveChatHistory()

        etInput.setText("") // Очищаем поле ввода для нового сообщения

        // 4. Запускаем генерацию ответа в корутине [1]
        lifecycleScope.launch {
            // Переключаем выполнение расчетов ИИ на фоновый поток IO, чтобы UI не зависал! [1, 2.14]
            val response = withContext(Dispatchers.IO) {
                if (useLocalAI) {
                    generateLocalResponse(localPrompt) // Локальный запуск на телефоне
                } else {
                    getResponseFromGemini(jsonPayload) // Запрос в облако Google
                }
            }

            // 5. Выводим полученный ответ котенка на экран
            if (!response.isNullOrEmpty()) {
                messageList.add(ChatMessage(response, false))
            } else {
                messageList.add(ChatMessage("Мяу... Задумался и потерял мысль. Попробуй спросить еще раз! 🐾", false))
            }
            chatAdapter.notifyItemInserted(messageList.size - 1)
            rvChat.scrollToPosition(messageList.size - 1)

            // 6. Автосохранение: записываем полученный ответ котенка в память [1.1.5]
            saveChatHistory()
        }
    }

    private fun generateLocalResponse(prompt: String): String? {
        return try {
            val response = llmInference?.generateResponse(prompt)
            cleanHallucinations(response)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getResponseFromGemini(jsonPayload: String): String? {
        return try {
            val url = URL(GEMINI_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestMethod("POST")
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

                val jsonResponse = JSONObject(responseBody)
                jsonResponse.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text").trim()
            } else {
                // Считываем подробный текст ошибки, который прислал Google! [1.3.8]
                val errorStream = conn.errorStream
                var errorDetail = ""
                if (errorStream != null) {
                    val scanner = Scanner(errorStream, "UTF-8")
                    val errorBody = if (scanner.useDelimiter("\\A").hasNext()) scanner.next() else ""
                    scanner.close()
                    try {
                        // Парсим официальное сообщение об ошибке Google [1.3.8]
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
            systemPartObj.put("text", SYSTEM_INSTRUCTION)
            systemParts.put(systemPartObj)
            systemInstruction.put("parts", systemParts)
            jsonBody.put("systemInstruction", systemInstruction)

            val contentsArray = JSONArray()
            val historyLimit = 6
            val startIdx = Math.max(0, messageList.size - historyLimit)

            for (i in startIdx until messageList.size) {
                val msg = messageList[i]

                // ЗАЩИТА: Пропускаем стартовое приветствие котенка, если оно идет первым в отправляемой истории,
                // так как Google Gemini требует, чтобы диалог ВСЕГДА начинался с реплики "user"!
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
                contentsArray.put(contentObj);
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

    private fun buildLocalPromptWithHistory(currentQuery: String): String {
        val promptBuilder = StringBuilder()
        promptBuilder.append("[Instruction: ").append(SYSTEM_INSTRUCTION).append("]\n\n")

        val historyLimit = 6
        val startIdx = Math.max(0, messageList.size - historyLimit)

        for (i in startIdx until messageList.size) {
            val msg = messageList[i]
            if (msg.isUser) {
                promptBuilder.append("User: ").append(msg.text).append("\n")
            } else {
                promptBuilder.append("Kitten: ").append(msg.text).append("\n")
            }
        }
        promptBuilder.append("User: ").append(currentQuery).append("\nKitten:")
        return promptBuilder.toString()
    }

    private fun cleanHallucinations(response: String?): String? {
        if (response == null) return null
        var cleaned = response.trim()
        val stopMarkers = arrayOf("User:", "user:", "USER:", "Kitten:", "kitten:", "\nUser", "\nKitten", "\n\n")
        for (marker in stopMarkers) {
            val index = cleaned.indexOf(marker)
            if (index != -1) {
                cleaned = cleaned.substring(0, index)
            }
        }
        return cleaned.trim()
    }

    /**
     * Ограничивает историю последними 15 сообщениями и сохраняет её в память устройства [1.1.5]
     */
    private fun saveChatHistory() {
        val prefs = getSharedPreferences("MyLifePlanPrefs", Context.MODE_PRIVATE)

        // Ограничиваем историю последними 15 сообщениями, чтобы не перегружать память устройства [1.1.5]
        val limit = 15
        val startIdx = Math.max(0, messageList.size - limit)
        val subList = messageList.subList(startIdx, messageList.size)

        // Превращаем список сообщений в одну безопасную строку, заменяя разделители [1.1.5]
        val serialized = subList.joinToString(";") {
            val cleanText = it.text.replace("|", "/").replace(";", ",")
            "$cleanText|${it.isUser}"
        }

        prefs.edit().putString("saved_chat_history", serialized).commit() // commit() записывает мгновенно [1.1.5]
    }

    /**
     * Загружает сохраненную историю чата из памяти [1.1.5]
     */
    private fun loadChatHistory() {
        val prefs = getSharedPreferences("MyLifePlanPrefs", Context.MODE_PRIVATE)
        val savedString = prefs.getString("saved_chat_history", null)

        messageList.clear()

        if (!savedString.isNullOrEmpty()) {
            // Восстанавливаем сообщения из сохраненной строки [1.1.5]
            savedString.split(";").forEach { itemStr ->
                val parts = itemStr.split("|")
                if (parts.size == 2) {
                    val text = parts[0]
                    val isUser = parts[1].toBoolean()
                    messageList.add(ChatMessage(text, isUser))
                }
            }
        } else {
            // Если истории в памяти нет (первый запуск), добавляем дефолтное приветствие
            messageList.add(ChatMessage("Мяу! Привет! Рад видеть тебя в нашей уютной комнате. Как твои дела сегодня? 🐾✨", false))
        }
        chatAdapter.notifyDataSetChanged()
        rvChat.scrollToPosition(messageList.size - 1)
    }
}