package com.example.mylifeplan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.net.HttpURLConnection
import java.net.URL
import java.util.Scanner

enum class SpotType {
    CAFE,
    PARK,
    LIBRARY,
    FOUNTAIN,
    CAT_SPOT
}

data class CozySpot(
    val title: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val type: SpotType
)

class CozySpotsMapActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private var locationOverlay: MyLocationNewOverlay? = null
    private val activeMarkers = mutableListOf<Marker>()

    // Адрес Cloudflare Worker для связи с Google Gemini [1]:
    private val GEMINI_URL = "https://lively-field-4bff.lee25seoyeon2008.workers.dev"

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            enableMyLocation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(this, getSharedPreferences("osm_prefs", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = "MyLifePlanCuteApp_Innovation2026"

        setContentView(R.layout.activity_cozy_spots_map)

        findViewById<ImageButton>(R.id.btn_map_back).setOnClickListener { finish() }

        map = findViewById(R.id.osm_map)

        val osmFrSource = org.osmdroid.tileprovider.tilesource.XYTileSource(
            "OSM_France", 0, 19, 256, ".png",
            arrayOf(
                "https://a.tile.openstreetmap.fr/osmfr/",
                "https://b.tile.openstreetmap.fr/osmfr/",
                "https://c.tile.openstreetmap.fr/osmfr/"
            )
        )
        map.setTileSource(osmFrSource)
        map.setMultiTouchControls(true)

        val mapController = map.controller
        mapController.setZoom(15.2)

        val startCenter = GeoPoint(56.8389, 60.6057)
        mapController.setCenter(startCenter)

        checkPermissionsAndShowLocation()

        // Сразу запускаем показ меток
        loadSpotsWithGemini(startCenter.latitude, startCenter.longitude)
    }

    /**
     * Показывает места: сразу ставит страховочные и в фоне запрашивает у Gemini
     */
    private fun loadSpotsWithGemini(lat: Double, lon: Double) {
        // 1. МГНОВЕННО ставим точки (0 секунд задержки, они сразу видны на экране!):
        displaySpotsOnMap(getRealEkbPlaces(lat, lon))

        Toast.makeText(this, "Gemini подбирает уютные места... 🐾", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val aiSpots = withContext(Dispatchers.IO) {
                fetchSpotsFromGemini(lat, lon)
            }

            // 2. Если сеть ответила — плавно заменяем точки на сгенерированные ИИ:
            if (aiSpots.isNotEmpty()) {
                displaySpotsOnMap(aiSpots)
                Toast.makeText(this@CozySpotsMapActivity, "Gemini нашел ${aiSpots.size} мест для отдыха! ⭐", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Запрос к Gemini через Cloudflare Worker с защищенным парсингом JSON [1]
     */
    private fun fetchSpotsFromGemini(lat: Double, lon: Double): List<CozySpot> {
        val resultList = mutableListOf<CozySpot>()
        try {
            val prompt = "Пользователь находится в точке: широта $lat, долгота $lon. " +
                    "Определи этот город и район, найди 5 РЕАЛЬНЫХ красивых и уютных мест для отдыха и прогулок поблизости " +
                    "(парки, скверы, тихие кофейни, набережные, библиотеки). " +
                    "Укажи их реальные координаты рядом с пользователем. " +
                    "Верни ответ ТОЛЬКО в формате JSON: " +
                    "{\"spots\":[{\"title\":\"Название\",\"description\":\"Описание\",\"lat\":$lat,\"lon\":$lon,\"type\":\"CAFE\"}]} " +
                    "Значения type только: CAFE, PARK, LIBRARY, FOUNTAIN, CAT_SPOT."

            val jsonBody = JSONObject()
            val contentsArray = JSONArray()
            val contentObj = JSONObject()
            contentObj.put("role", "user")
            val partsArray = JSONArray()
            val partObj = JSONObject()
            partObj.put("text", prompt)
            partsArray.put(partObj)
            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
            jsonBody.put("contents", contentsArray)

            val url = URL(GEMINI_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            val os = conn.outputStream
            os.write(jsonBody.toString().toByteArray(Charsets.UTF_8))
            os.close()

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val scanner = Scanner(conn.inputStream, "UTF-8").useDelimiter("\\A")
                val responseBody = if (scanner.hasNext()) scanner.next() else ""
                scanner.close()

                val root = JSONObject(responseBody)
                val rawText = root.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")

                // Вырезаем чистый JSON от первой { до последней }
                val startIdx = rawText.indexOf('{')
                val endIdx = rawText.lastIndexOf('}')

                if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
                    val cleanJson = rawText.substring(startIdx, endIdx + 1).trim()
                    val parsed = JSONObject(cleanJson)
                    val array = parsed.getJSONArray("spots")

                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val title = item.getString("title")
                        val desc = item.getString("description")
                        val spotLat = item.getDouble("lat")
                        val spotLon = item.getDouble("lon")
                        val typeStr = item.optString("type", "PARK")

                        val type = try {
                            SpotType.valueOf(typeStr)
                        } catch (e: Exception) {
                            SpotType.PARK
                        }

                        resultList.add(CozySpot(title, desc, spotLat, spotLon, type))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("CozySpots", "Ошибка Gemini: ${e.message}")
        }
        return resultList
    }

    /**
     * Страховочные места: рассчитываются прямо вокруг переданных координат (lat, lon)
     */
    private fun getRealEkbPlaces(lat: Double, lon: Double): List<CozySpot> {
        return listOf(
            CozySpot(
                title = "Кофейня «Теплый плед» ☕",
                description = "Любимый уютный спот с ароматным кофе и десертами.",
                latitude = lat + 0.0020,
                longitude = lon + 0.0018,
                type = SpotType.CAFE
            ),
            CozySpot(
                title = "Тихий зеленый сквер 🌿",
                description = "Живописный сквер с лавочками для релаксации и снятия стресса.",
                latitude = lat - 0.0018,
                longitude = lon + 0.0025,
                type = SpotType.PARK
            ),
            CozySpot(
                title = "Прохладный фонтан 🌊",
                description = "Журчание воды, свежий воздух и спокойная атмосфера.",
                latitude = lat + 0.0028,
                longitude = lon - 0.0020,
                type = SpotType.FOUNTAIN
            ),
            CozySpot(
                title = "Книжный уголок тишины 📖",
                description = "Оазис тишины, книг и глубокого спокойствия.",
                latitude = lat - 0.0022,
                longitude = lon - 0.0025,
                type = SpotType.LIBRARY
            ),
            CozySpot(
                title = "Кошачий тайный сквер 🐾",
                description = "Мяу! Секретное место силы нашего пушистого котенка ⭐",
                latitude = lat + 0.0010,
                longitude = lon - 0.0030,
                type = SpotType.CAT_SPOT
            )
        )
    }

    /**
     * Отрисовывает метки и центрирует камеру прямо на них
     */
    private fun displaySpotsOnMap(spots: List<CozySpot>) {
        for (m in activeMarkers) {
            map.overlays.remove(m)
        }
        activeMarkers.clear()

        for (spot in spots) {
            val marker = Marker(map)
            marker.position = GeoPoint(spot.latitude, spot.longitude)
            marker.title = spot.title
            marker.snippet = spot.description

            val iconResId = when (spot.type) {
                SpotType.CAFE -> R.drawable.ic_spot_cafe
                SpotType.PARK -> R.drawable.ic_spot_park
                SpotType.LIBRARY -> R.drawable.ic_spot_library
                SpotType.FOUNTAIN -> R.drawable.ic_spot_fountain
                SpotType.CAT_SPOT -> R.drawable.ic_spot_cat
            }

            marker.icon = getScaledMarkerIcon(iconResId, sizeDp = 46)
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

            map.overlays.add(marker)
            activeMarkers.add(marker)
        }

        // Автоматически наводим экран прямо на метки!
        if (spots.isNotEmpty()) {
            map.controller.setCenter(GeoPoint(spots[0].latitude, spots[0].longitude))
        }

        map.invalidate()
    }

    private fun getScaledMarkerIcon(drawableResId: Int, sizeDp: Int = 46): Drawable {
        val drawable = ContextCompat.getDrawable(this, drawableResId)
            ?: return ContextCompat.getDrawable(this, android.R.drawable.ic_menu_myplaces)!!

        val sizePx = (sizeDp * resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        return BitmapDrawable(resources, bitmap)
    }

    private fun checkPermissionsAndShowLocation() {
        val hasFine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            enableMyLocation()
        } else {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun enableMyLocation() {
        locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), map)
        locationOverlay?.enableMyLocation()

        locationOverlay?.runOnFirstFix {
            val myLoc = locationOverlay?.myLocation
            if (myLoc != null) {
                runOnUiThread {
                    map.controller.animateTo(myLoc)
                    loadSpotsWithGemini(myLoc.latitude, myLoc.longitude)
                }
            }
        }

        map.overlays.add(locationOverlay)
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }
}