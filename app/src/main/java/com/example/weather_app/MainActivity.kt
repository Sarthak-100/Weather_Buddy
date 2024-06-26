package com.example.weather_app

import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role.Companion.Image
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.room.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.weather_app.ui.theme.Weather_AppTheme
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


data class WeatherInfo(val maxTemp: Double, val minTemp: Double)

@Database(entities = [WeatherData::class], version = 1)
abstract class WeatherDatabase : RoomDatabase() {
    abstract fun weatherDao(): WeatherDao
}

@Entity(tableName = "weather")
data class WeatherData(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val cityName: String,
    val date: String,
    val maxTemp: Double,
    val minTemp: Double
)

@Dao
interface WeatherDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeather(weatherData: WeatherData)

    @Query("SELECT * FROM weather WHERE cityName = :cityName AND date = :date")
    suspend fun getWeatherData(cityName: String, date: String): WeatherData?

    @Query("SELECT * FROM weather")
    suspend fun getAllWeatherData(): List<WeatherData>

    @Query("DELETE FROM weather")
    suspend fun clearAllWeatherData()

    @Query("SELECT ROUND(AVG(maxTemp), 2) FROM weather WHERE cityName = :cityName AND strftime('%Y', date) BETWEEN strftime('%Y', date(:date, '-10 years')) AND strftime('%Y', date(:date, '-1 day')) AND (SELECT COUNT(*) FROM weather WHERE cityName = :cityName AND strftime('%Y', date) BETWEEN strftime('%Y', date(:date, '-10 years')) AND strftime('%Y', date(:date, '-1 day'))) = 10")
    suspend fun getAverageMaxTemp(cityName: String, date: String): Double?

    @Query("SELECT ROUND(AVG(minTemp), 2) FROM weather WHERE cityName = :cityName AND strftime('%Y', date) BETWEEN strftime('%Y', date(:date, '-10 years')) AND strftime('%Y', date(:date, '-1 day')) AND (SELECT COUNT(*) FROM weather WHERE cityName = :cityName AND strftime('%Y', date) BETWEEN strftime('%Y', date(:date, '-10 years')) AND strftime('%Y', date(:date, '-1 day'))) = 10")
    suspend fun getAverageMinTemp(cityName: String, date: String): Double?

}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = Room.databaseBuilder(
            applicationContext,
            WeatherDatabase::class.java, "weather-database"
        ).build()
        setContent {
            Weather_AppTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    clearDatabase(database)
                    WeatherApp(database)
                }
            }
        }
    }
    private fun clearDatabase(database: WeatherDatabase) {
        CoroutineScope(Dispatchers.IO).launch {
            database.weatherDao().clearAllWeatherData()
        }
    }
}

private fun fetchAndLogAllWeatherData(database: WeatherDatabase) {
    CoroutineScope((Dispatchers.IO)).launch {
        val allWeatherData = database.weatherDao().getAllWeatherData()
        Log.d("WeatherApp", "DATABASE CONTENTS:")
        allWeatherData.forEach { weatherData ->
            Log.d("WeatherApp", "Weather Data: $weatherData")
        }
        Log.d("WeatherApp", "***********************")
    }
}

@Composable
fun WeatherApp(database: WeatherDatabase) {
    val context = LocalContext.current
    val client = HttpClient(Android) {
        install(JsonFeature) {
            serializer = KotlinxSerializer(
                kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                }
            )
        }
    }
    val weatherImage = painterResource(R.drawable.weather_logo)
    var date by remember { mutableStateOf(TextFieldValue()) }
    var weatherInfo by remember { mutableStateOf<WeatherInfo?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var isDataFetchAttempted by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showErrorSnackbar by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    val city by remember { mutableStateOf("New York") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Weather App",
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Image(
            painter = weatherImage,
            contentDescription = "Weather Image",
            modifier = Modifier.fillMaxWidth().height(200.dp)
        )

        Text(
            text = "City: $city"
        )

        OutlinedTextField(
            value = date,
            onValueChange = {
                date = it
                error = null },
            label = { Text("Enter Date (YYYY-MM-DD)") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                Log.d("WeatherApp", "Get Weather button clicked")
                if (isValidDate(date.text)) {
                    coroutineScope.launch {
                        isLoading = true
                        weatherInfo = fetchWeatherInfo(
                            client,
                            city,
                            date.text,
                            "ZY5N3Q53GSTKKKLJEXUEUQYEX",
                            database,
                            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager,
                        )
                        isLoading = false
                    }
                }
                else{
                    error = "Invalid date format. Please enter date in YYYY-MM-DD format."
                    showErrorSnackbar = true
                    errorMessage = "Invalid date format. Please enter date in YYYY-MM-DD format."
                }
            },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Get Weather")
        }
        if (showErrorSnackbar) {
            Snackbar(
                modifier = Modifier.padding(16.dp),

            ) {
                Text(errorMessage, color = Color.White)
            }
        }
        if (isLoading) {
            showErrorSnackbar = false
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            if (error == null) {
//                if(weatherInfo == null){
//                    showErrorSnackbar = true
//                    errorMessage = "Error fetching data."
//                }
//                else{
//                    showErrorSnackbar = false
//                }
                weatherInfo?.let {
                    isDataFetchAttempted = true
                    showErrorSnackbar = false
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        showErrorSnackbar = false
                        Text(
                            text = "Max Temp: ${it.maxTemp}°C",
                            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Min Temp: ${it.minTemp}°C",
                            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }?: run {
                    if (isDataFetchAttempted) {
                        showErrorSnackbar = true
                        errorMessage = "Error fetching data."
                    }
                }
            }
        }
    }

}

fun isValidDate(dateString: String): Boolean {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.isLenient = false
        sdf.parse(dateString)
        true
    } catch (e: Exception) {
        Log.d("WeatherApp","Date Format : incorrect date format")
        false
    }
}

private fun isFutureDate(date: String): Boolean {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val currentDate = sdf.format(Date())
    return date >= currentDate
}

private fun isInternetAvailable(connectivityManager: ConnectivityManager): Boolean {
    val networkInfo = connectivityManager.activeNetworkInfo
    return networkInfo != null && networkInfo.isConnected
}

suspend fun fetchWeatherInfo(
    client: HttpClient,
    cityName: String,
    date: String,
    apiKey: String,
    database: WeatherDatabase,
    connectivityManager: ConnectivityManager
): WeatherInfo? {
    fetchAndLogAllWeatherData(database)
    var resp: WeatherInfo? = null;
    if (!isInternetAvailable(connectivityManager)) {
        Log.d("WeatherApp", "No internet connection")
        resp = retrieveWeatherInfoFromDatabase(cityName, date, database)
    } else if (isFutureDate(date)) {
        Log.d("WeatherApp", "Internet Connection Available fetching data from API")
        resp = fetchAverageWeatherInfoForFutureDate(client, cityName, date, apiKey, database)
    } else {
        Log.d("WeatherApp", "Internet Connection Available fetching data from API")
        resp = fetchAndStoreWeatherInfo(client, cityName, date, apiKey, database)
    }

    if (resp != null) {
        return resp;
    }
    else{
        return null
    }
}

private suspend fun fetchAndStoreWeatherInfo(
    client: HttpClient,
    cityName: String,
    date: String,
    apiKey: String,
    database: WeatherDatabase
): WeatherInfo? {
    Log.d("WeatherApp", "Retrieving weather info for past date")

    val url = "https://weather.visualcrossing.com/VisualCrossingWebServices/rest/services/timeline/$cityName/$date?unitGroup=metric&key=$apiKey&include=days"
    Log.d("WeatherApp", "Fetching weather info for city: $cityName, date: $date")
    try {
        val response: WeatherApiResponse = client.get(url)
        Log.d("WeatherApp", "API Response: $response")
        val day = response.days[0]
        val weatherInfo = WeatherInfo(day.tempmax, day.tempmin)

        weatherInfo?.let {
            withContext(Dispatchers.IO) {
                database.weatherDao().insertWeather(
                    WeatherData(
                        cityName = cityName,
                        date = date,
                        maxTemp = it.maxTemp,
                        minTemp = it.minTemp
                    )
                )
                Log.d("WeatherApp", "Inserted fetched data into database")
            }
        }
        return weatherInfo
    } catch (e: Exception) {
        Log.e("WeatherApp", "Error fetching weather data", e)
        return null
    }
}

private suspend fun fetchAverageWeatherInfoForFutureDate(
    client: HttpClient,
    cityName: String,
    date: String,
    apiKey: String,
    database: WeatherDatabase
): WeatherInfo? {
    Log.d("WeatherApp", "Retrieving average weather info for future date")
    val year = date.substringBefore("-").toInt()
    val pastYears = (year - 10 until year).map { it.toString() }
    val temps = pastYears.mapNotNull { year ->
        fetchAndStoreWeatherInfo(client, cityName, "$year-${date.substringAfter("-")}", apiKey, database)?.let {
            Pair(it.maxTemp, it.minTemp)
        }
    }
    Log.d("WeatherApp", "Temps: $temps")
    if (temps.isNotEmpty()) {
        val avgMaxTemp = temps.sumOf { it.first } / temps.size
        val avgMinTemp = temps.sumOf { it.second } / temps.size
        val roundedAvgMaxTemp = avgMaxTemp.toBigDecimal().setScale(2, RoundingMode.HALF_UP).toDouble()
        val roundedAvgMinTemp = avgMinTemp.toBigDecimal().setScale(2, RoundingMode.HALF_UP).toDouble()
        val futureWeatherInfo = WeatherInfo(roundedAvgMaxTemp, roundedAvgMinTemp)

        withContext(Dispatchers.IO) {
            database.weatherDao().insertWeather(
                WeatherData(
                    cityName = cityName,
                    date = date,
                    maxTemp = roundedAvgMaxTemp,
                    minTemp = roundedAvgMinTemp
                )
            )
            Log.d("WeatherApp", "Inserted average of fetched data into database")
        }
        return futureWeatherInfo
    }
    return null
}

suspend fun retrieveWeatherInfoFromDatabase(cityName: String, date: String, database: WeatherDatabase): WeatherInfo? {
    Log.d("WeatherApp", "Retrieving weather info from database")

    if(isFutureDate(date)){
        Log.d("WeatherApp", "Future date entered")
        try{
            val weatherData = database.weatherDao().getWeatherData(cityName, date)
            return if (weatherData != null) {
                Log.d("WeatherApp", "Future date present in database")
                Log.d("WeatherApp", "Retrieved Data: $weatherData")
                WeatherInfo(weatherData.maxTemp, weatherData.minTemp)
            } else {
                Log.d("WeatherApp", "Future date not present in database...Trying to find average of past 10 years data")
                val avgMaxTemp = database.weatherDao().getAverageMaxTemp(cityName, date)
                val avgMinTemp = database.weatherDao().getAverageMinTemp(cityName, date)
                return if (avgMaxTemp != null && avgMinTemp != null) {
                    Log.d("WeatherApp", "Retrieved Data: avgMaxTemp:$avgMaxTemp avgMinTemp:$avgMinTemp")
                    WeatherInfo(avgMaxTemp, avgMinTemp)
                } else {
                    Log.d("WeatherApp", "Complete data of past 10 years not found in database")
                    null
                }
            }
        }
        catch(e: Exception){
            Log.d("WeatherApp", "Exception: $e")
            return null
        }
    }
    else{
        Log.d("WeatherApp", "Past date entered")
        val weatherData = database.weatherDao().getWeatherData(cityName, date)
        Log.d("WeatherApp", "Retrieved Data: $weatherData")
        return if (weatherData != null) {
            WeatherInfo(weatherData.maxTemp, weatherData.minTemp)
        } else {
            Log.d("WeatherApp", "Data not found in database")
            return null
        }
    }
}

// Placeholder for the API response classes
@Serializable
data class WeatherApiResponse(
    val days: List<WeatherDataItem>,
    val resolvedAddress: String,
    val timezone: String
)

@Serializable
data class WeatherDataItem(
    val datetime: String,
    val tempmax: Double,
    val tempmin: Double
)

