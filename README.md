# Weather Buddy

This Android app allows users to fetch weather information for a specific date. It uses the Visual Crossing Weather API to retrieve weather data for a given city and date. The app also stores fetched weather data in a local Room database for offline access.

## Features

- Fetch weather information for a specific date and city.
- Store fetched weather data in a local Room database.
- Display the maximum and minimum temperature for the fetched weather data.

## Implementation Details

### Architecture

The app follows the MVVM (Model-View-ViewModel) architecture pattern:

- **Model**: Consists of the data classes (`WeatherData`, `WeatherInfo`, `WeatherApiResponse`, `WeatherDataItem`) and the Room database (`WeatherDatabase` and `WeatherDao`).
- **View**: The UI components implemented using Jetpack Compose.
- **ViewModel**: Manages the app's UI-related data in a lifecycle-conscious way.

### Libraries Used

- **Jetpack Compose**: For building the UI.
- **Room**: For local database storage.
- **Ktor**: For networking to fetch weather data from the API.
- **Kotlinx Serialization**: For serializing JSON responses from the API.
- **Coroutines**: For asynchronous programming and managing background tasks.

### Data Flow

1. User enters a date in the format `YYYY-MM-DD` and clicks the "Get Weather" button.
2. The app validates the date format and checks for internet connectivity.
3. If the date is valid and internet is available, the app fetches weather data from the API using Ktor client.
4. If the date is in the future, the app calculates the average temperature for the past 10 years and stores it in the database.
5. If the date is in the past, the app fetches weather data from the database.
6. The fetched weather data is displayed on the UI.

### Usage

To use the app, simply enter a valid date in the input field and click the "Get Weather" button. The app will display the maximum and minimum temperature for the specified date.
