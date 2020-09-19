package ru.geekbrains.gb_android_2;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.facebook.drawee.view.SimpleDraweeView;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.squareup.otto.Subscribe;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

import ru.geekbrains.gb_android_2.customViews.ThermometerView;
import ru.geekbrains.gb_android_2.database.CitiesList;
import ru.geekbrains.gb_android_2.database.CitiesListDao;
import ru.geekbrains.gb_android_2.database.CitiesListSource;
import ru.geekbrains.gb_android_2.events.OpenChooseCityFragmentEvent;
import ru.geekbrains.gb_android_2.events.ShowCurrLocationItemEvent;
import ru.geekbrains.gb_android_2.events.ShowCurrentLocationWeatherEvent;
import ru.geekbrains.gb_android_2.forecastRequest.ForecastRequest;
import ru.geekbrains.gb_android_2.forecastRequest.OpenWeatherMap;
import ru.geekbrains.gb_android_2.model.HourlyWeatherData;
import ru.geekbrains.gb_android_2.model.WeatherData;
import ru.geekbrains.gb_android_2.rvDataAdapters.CurrentWeatherRecyclerDataAdapter;
import ru.geekbrains.gb_android_2.rvDataAdapters.HourlyWeatherRecyclerDataAdapter;
import ru.geekbrains.gb_android_2.rvDataAdapters.RVOnItemClick;
import ru.geekbrains.gb_android_2.rvDataAdapters.WeekWeatherRecyclerDataAdapter;

import static android.content.Context.LOCATION_SERVICE;
import static android.content.Context.MODE_PRIVATE;

public class WeatherMainFragment extends Fragment implements RVOnItemClick{
    public static String currentCity = "";
    private TextView cityTextView;
    private TextView degrees;
    private TextView updateTimeTextView;
    final String myLog = "myLog";
    private RecyclerView currentWeatherRecyclerView, weatherRecyclerView;
    private RecyclerView hourlyRecyclerView;
    private List<Integer> weatherIcon = new ArrayList<>();
    private List<Integer> cardViewColor = new ArrayList<>();
    private List<String> days = new ArrayList<>();
    private List<String> daysTemp = new ArrayList<>();
    private List<String> tempMax = new ArrayList<>();
    private List<String> tempMin = new ArrayList<>();
    private List<String> weatherStateInfo = new ArrayList<>();
    private List<String> hourlyTime = new ArrayList<>();
    private List<Integer> hourlyWeatherIcon = new ArrayList<>();
    private List<String> hourlyTemperature = new ArrayList<>();
    private List<String> currentWeather = new ArrayList<>();
    private TextView currTime;
    private TextView weatherStatusTextView;
    private ArrayList<String> citiesListFromRes;
    private ArrayList<WeatherData> weekWeatherData;
    private ArrayList<HourlyWeatherData> hourlyWeatherData;
    private SimpleDraweeView weatherStatusImage;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ThermometerView thermometerView;
    private MapView mMapView;
    private GoogleMap googleMap;
    private Double cityLatitude;
    private Double cityLongitude;
    CountDownLatch countDownLatch = new CountDownLatch(1);
    private String cityFromLocation;
    private MenuItem itemCurrLocation;
    private ConstraintLayout constraintLayout;


    static WeatherMainFragment create(CurrentDataContainer container) {
        WeatherMainFragment fragment = new WeatherMainFragment();    // создание
        // Передача параметра
        Bundle args = new Bundle();
        args.putSerializable("currCity", container);
        fragment.setArguments(args);
        Log.d("myLog", "WeatherMainFragment CREATE");
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Log.d("myLog", "onCreate - fragment WeatherMainFragment");
        Log.d("lifeCycle", "onCreate");

        super.onCreate(savedInstanceState);
    }

    // При создании фрагмента укажем его макет
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.d("lifeCycle", "onCreateView");
        Log.d("myLog", "onCreateView - fragment WeatherMainFragment");
        View rootView = inflater.inflate(R.layout.fragment_weather_main, container, false);

        // Проверяем, первый раз пользователь открывает приложение или нет:
        SharedPreferences sharedPreferences = requireActivity().getSharedPreferences("settings", MODE_PRIVATE);
        CurrentDataContainer.isFirstEnter = sharedPreferences.getBoolean("isFirstEnter", true);

        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        initViews(view);
        if (CurrentDataContainer.isFirstEnter) {
            constraintLayout.setAlpha(0f);
            showAlertDialog(getString(R.string.please_choose_city_first));
        }
        Log.d("lifeCycle", "onViewCreated");
        moveViewsIfLandscapeOrientation(view);
        takeCitiesListFromResources(getResources());
        generateDaysList();
        addDataToWeatherIconsIdFromRes(weatherIcon);
        addDefaultDataToCardViewColorList();
        addDefaultDataToDaysTempFromRes(getResources());
        addDefaultDataToHourlyWeatherRV(getResources());
        addDefaultDataToCurrentWeatherRV(getResources());
        addDefaultDataToWeatherStateInfo();
        updateWeatherInfo(getResources()); //здесь забрали citiesList
        setupRecyclerView();
        setupHourlyWeatherRecyclerView();
        setupCurrentWeatherRecyclerView();
        setOnCityTextViewClickListener();
        setOnSwipeRefreshListener();
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
Log.d("lifeCycle", "onActivityCreated");
        super.onActivityCreated(savedInstanceState);
        Log.d(myLog, "WeatherMainFragment - savedInstanceState exists = " + (savedInstanceState != null));
        updateChosenCity();
        Log.d("cityName", "updateChosenCity.3 = " + currentCity);
        takeWeatherInfoForFirstCityInSession(currentCity);
        Log.d(myLog, "WeatherMainFragment: onActivityCreated !AFTER updateChosenCity, currentCity: " + currentCity);
    }

    private void takeCityFromSharedPreference(SharedPreferences preferences) {
//        currentCity = preferences.getString("current city", "Saint Petersburg");
        if(cityFromLocation != null) currentCity = preferences.getString("current city", cityFromLocation);
        else currentCity = preferences.getString("current city", "Your City");
    }

    private void setOnSwipeRefreshListener() {
        swipeRefreshLayout.setOnRefreshListener(()-> {
            CurrentDataContainer.isFirstCityInSession = false;
            OpenWeatherMap openWeatherMap = OpenWeatherMap.getInstance();

            // Обновляем значение currentCity:
            takeCityFromSharedPreference(requireActivity().getSharedPreferences("settings", MODE_PRIVATE));

            ForecastRequest.getForecastFromServer(currentCity);
            Log.d("retrofit", "WeatherMain - countDownLatch = " + ForecastRequest.getForecastResponseReceived().getCount());

            new Thread(() -> {
                try {
                    // Ждем, пока не получим актуальные данные:
                    ForecastRequest.getForecastResponseReceived().await();

                    weekWeatherData = openWeatherMap.getWeekWeatherData(getResources());
                    hourlyWeatherData = openWeatherMap.getHourlyWeatherData();
                    CurrentDataContainer.getInstance().weekWeatherData = weekWeatherData;
                    CurrentDataContainer.getInstance().hourlyWeatherList = hourlyWeatherData;

                    requireActivity().runOnUiThread(() -> {
                        updateWeatherInfo(getResources());
                        Log.d("swipe", "setOnSwipeRefreshListener -> weather updated");
                        if(ForecastRequest.responseCode != 200) showAlertDialog(getResources().getString(R.string.connection_failed));
                        setupRecyclerView();
                        setupHourlyWeatherRecyclerView();
                        setupCurrentWeatherRecyclerView();
                        swipeRefreshLayout.setRefreshing(false);
                    });
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        });
    }

    private void takeWeatherInfoForFirstCityInSession(String currentCity){
        if(CurrentDataContainer.isFirstCityInSession){
            Log.d("googleMap", "*FIRST ENTER*");
            OpenWeatherMap openWeatherMap = OpenWeatherMap.getInstance();

            ForecastRequest.getForecastFromServer(currentCity);
            Log.d("retrofit", "WeatherMain - countDownLatch = " + ForecastRequest.getForecastResponseReceived().getCount());

            new Thread(() -> {
                try {
                    // Ждем, пока не получим актуальный response code:
                    ForecastRequest.getForecastResponseReceived().await();
                    Log.d("googleMap", "await finished");

                    Log.d("retrofit", "response code for first enter = " + ForecastRequest.responseCode);
                    weekWeatherData = openWeatherMap.getWeekWeatherData(getResources());
                    hourlyWeatherData = openWeatherMap.getHourlyWeatherData();
                    CurrentDataContainer.getInstance().weekWeatherData = weekWeatherData;
                    CurrentDataContainer.getInstance().hourlyWeatherList = hourlyWeatherData;
                    requireActivity().runOnUiThread(() -> {
                        String alertMessage;
                        if(cityFromLocation != null) {
                            String alertMessageFromRes = getResources().getString(R.string.alert_message);
                            alertMessage = String.format(alertMessageFromRes, cityFromLocation);
                        } else alertMessage = getResources().getString(R.string.city_not_found);

                        if(ForecastRequest.responseCode == 404 && CurrentDataContainer.isFirstEnter){}
                        else if(ForecastRequest.responseCode == 404){
                            showAlertDialog(alertMessage);
                        }
                        else if(ForecastRequest.responseCode != 200) showAlertDialog(getResources().getString(R.string.connection_failed));
                        else {


                            // Возвращаем видимость лэйауту:
                            constraintLayout.setAlpha(1f);

                            // Если мы попали сюда, выбрав город на карте:
                            if (cityFromLocation != null) {
                                // Сохраним текущий город в шерид преференс:
                                SharedPreferences preferences = requireActivity().getSharedPreferences(MainActivity.SETTINGS, MODE_PRIVATE);
                                SharedPreferences.Editor editor = preferences.edit();
                                editor.putString("current city", cityFromLocation);
                                editor.apply();

                                // Здесь кладем в переменную currentCity значение из SharedPreferences:
                                takeCityFromSharedPreference(preferences);
                                Log.d("cityName", "updateChosenCity.5 = " + currentCity);
                            }

                            updateChosenCity();

                            Log.d("cityName", "updateChosenCity.4 = " + this.currentCity);
                            updateWeatherInfo(getResources());
                            Log.d("googleMap", "updateWeatherInfo finished");

                            setupRecyclerView();
                            setupHourlyWeatherRecyclerView();
                            setupCurrentWeatherRecyclerView();

                            // Добавляем текущий город и далее все города, которые были успешно найдены на карте в бд:
                            new Thread (()->{
                               CitiesListDao citiesListDao = App
                                       .getInstance()
                                       .getCitiesListDao();
                               CitiesListSource citiesListSource = new CitiesListSource(citiesListDao);
                               citiesListSource.addCity(new CitiesList(currentCity));
                           }).start();
                        }
                    });
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        } else {
            Log.d(myLog, "*NOT FIRST ENTER*");
        }
    }

    private void moveViewsIfLandscapeOrientation( View view){
        // Проверяем ориентацию экрана и в случае альбомной, меняем расположение элементов:
        // Можно ли расположить рядом фрагмент с выбором города
        boolean isLandscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        if (isLandscape) {
            ConstraintLayout constraintLayout = view.findViewById(R.id.full_screen_constraintlayout);
            ConstraintSet constraintSet = new ConstraintSet();
            constraintSet.clone(constraintLayout);
            constraintSet.setVerticalBias(R.id.center, 0.67f);
            constraintSet.connect(R.id.degrees,ConstraintSet.BOTTOM,R.id.center,ConstraintSet.TOP,0);
            constraintSet.setVisibility(R.id.weekWeatherRV, View.GONE);
            constraintSet.applyTo(constraintLayout);
        }
    }

    private void initViews(View view) {
        cityTextView = view.findViewById(R.id.city);
        degrees = view.findViewById(R.id.degrees);
        hourlyRecyclerView = view.findViewById(R.id.hourlyWeatherRV);
        weatherRecyclerView = view.findViewById(R.id.weekWeatherRV);
        currentWeatherRecyclerView = view.findViewById(R.id.currentWeatherRV);
        currTime = view.findViewById(R.id.currTime);
        weatherStatusTextView = view.findViewById(R.id.cloudyInfoTextView);
        updateTimeTextView = view.findViewById(R.id.update_time);
        weatherStatusImage = view.findViewById(R.id.weatherStatus);
        swipeRefreshLayout = view.findViewById(R.id.swiperefresh);
        thermometerView = view.findViewById(R.id.thermometerView);
        constraintLayout = view.findViewById(R.id.full_screen_constraintlayout);
    }

    private void setOnCityTextViewClickListener(){
        cityTextView.setOnClickListener(view -> {
            BottomSheetDialogChooseCityFragment dialogFragment =
                    BottomSheetDialogChooseCityFragment.newInstance();
//                dialogFragment.setOnDialogListener(dialogListener);
            dialogFragment.show(getChildFragmentManager(),
                    "dialog_fragment");
        });
    }

    private void updateChosenCity() {
        SharedPreferences sharedPreferences = requireActivity().getSharedPreferences("settings", MODE_PRIVATE);
        takeCityFromSharedPreference(sharedPreferences);
        cityTextView.setText(currentCity);
    }

    private  void updateWeatherInfo(Resources resources){
        ArrayList<String> citiesList = citiesListFromRes;
        if(CurrentDataContainer.isFirstCityInSession) {
            Log.d("googleMap", "updateWeatherInfo -> urrentDataContainer.isFirstCityInSession" );
            if(ForecastRequest.responseCode != 200) {
                Log.d(myLog, "updateWeatherInfo from resources");

                degrees.setText("+0°");
                Log.d("swipe", "new degrees = " + degrees.getText().toString());

                Date currentDate = new Date();
                DateFormat dateFormat = new SimpleDateFormat("E, dd MMM", Locale.getDefault());
                String dateText = dateFormat.format(currentDate);
                currTime.setText(dateText);

                DateFormat updateTimeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                String timeText = updateTimeFormat.format(currentDate);
                String upateTimeFromRes = resources.getString(R.string.update);
                updateTimeTextView.setText(String.format(upateTimeFromRes, timeText));

                Log.d(myLog, "WEatherMainFragment - updateWeatherInfo - FIRSTENTER; responseCode != 200; CITIES LIST = " + citiesList.toString());
            } else {
                setNewWeatherData(weekWeatherData, hourlyWeatherData);
                Log.d(myLog, "WEatherMainFragment - updateWeatherInfo - FIRSTENTER; responseCode == 200; CITIES LIST = " + citiesList.toString());
            }
        }
        if(!CurrentDataContainer.isFirstCityInSession) {
            Log.d("googleMap", "updateWeatherInfo -> !currentDataContainer.isFirstCityInSession" );

            currentCity = requireActivity()
                    .getSharedPreferences(MainActivity.SETTINGS, MODE_PRIVATE)
                    .getString("current city", "Saint Petersburg");
            weekWeatherData = CurrentDataContainer.getInstance().weekWeatherData;
            hourlyWeatherData = CurrentDataContainer.getInstance().hourlyWeatherList;
            setNewWeatherData(weekWeatherData, hourlyWeatherData);
        }
    }


    private void setNewWeatherData(ArrayList<WeatherData> weekWeatherData, ArrayList<HourlyWeatherData> hourlyWeatherData) {
        if (weekWeatherData != null && weekWeatherData.size() != 0 && hourlyWeatherData != null && hourlyWeatherData.size() != 0) {
            WeatherData wd = weekWeatherData.get(0);
            degrees.setText(wd.getDegrees());
            Log.d("swipe", "new degrees = " + degrees.getText().toString());

            setThermometerViewParameters(wd.getIntDegrees());

            Date currentDate = new Date();
            DateFormat timeFormat = new SimpleDateFormat("E, dd MMM", Locale.getDefault());
            String dateText = timeFormat.format(currentDate);
            currTime.setText(dateText);

            DateFormat updateTimeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            String timeText = updateTimeFormat.format(currentDate);
            String upateTimeFromRes = getResources().getString(R.string.update);
            updateTimeTextView.setText(String.format(upateTimeFromRes, timeText));

            weatherStatusTextView.setText(wd.getWeatherStateInfo());

            addNewDataToCurrentWeatherRV(wd);

            setWeatherStatusImage(wd.getWeatherIcon());

            tempMax = new ArrayList<>();
            tempMin = new ArrayList<>();
            weatherStateInfo = new ArrayList<>();

            for (int i = 0; i < OpenWeatherMap.FORECAST_DAYS; i++) {
                WeatherData weatherData = weekWeatherData.get(i);
                daysTemp.set(i, weatherData.getDegrees());
                tempMax.add(weatherData.getTempMax());
                tempMin.add(weatherData.getTempMin());
                weatherStateInfo.add(weatherData.getWeatherStateInfo());
                String imageName =weatherData.getWeatherIcon();
                Integer resID = getResources().getIdentifier(imageName , "drawable", requireActivity().getPackageName());
                weatherIcon.set(i, resID);
                cardViewColor.set(i, ContextCompat.getColor(requireContext(), weatherData.getCardViewColor()));
                Log.d("cardColor", "weatherData - "+ i+ weatherData.toString());

            }
            for (int i = 0; i < 8 ; i++) {
                HourlyWeatherData hourlyData = hourlyWeatherData.get(i);
                hourlyTime.set(i, hourlyData.getTime());
                String iconName = hourlyData.getStateImage();
                Integer iconId =  getResources().getIdentifier(iconName , "drawable", requireActivity().getPackageName());
                hourlyWeatherIcon.set(i,iconId);
                hourlyTemperature.set(i, hourlyData.getTemperature());
            }
        }
    }

    private void addNewDataToCurrentWeatherRV(WeatherData wd){
        currentWeather = new ArrayList<>();
        currentWeather.add(wd.getWindInfo());
        // take settings switch data from preferences:
        SharedPreferences sharedPreferences = requireActivity().getSharedPreferences(MainActivity.SETTINGS, Context.MODE_PRIVATE);
        if (sharedPreferences.getBoolean("feels like" , false)) currentWeather.add(wd.getFeelLike());
        if (sharedPreferences.getBoolean("pressure" , false))  currentWeather.add(wd.getPressure());
    }

    private void setWeatherStatusImage(String weatherIconId){
        switch (weatherIconId) {
            case "thunderstorm": {
                Uri uri = Uri.parse("http://192.168.1.35/users-images/thumbs/user_id/ffffffffffff1f1f.png");
                weatherStatusImage.setImageURI(uri);
                weatherStatusImage.setColorFilter(ContextCompat.getColor(requireContext(), R.color.weather_status_thunderstorm), PorterDuff.Mode.SRC_IN);
                break;
            }
            case "shower_rain":
                weatherStatusImage.setImageResource(R.drawable.rain_weather_status_3);
                weatherStatusImage.setColorFilter(ContextCompat.getColor(requireContext(), R.color.weather_status_shower_rain), PorterDuff.Mode.SRC_IN);
                break;
            case "rain_day":
                weatherStatusImage.setImageResource(R.drawable.rain_weather_status_3);
                weatherStatusImage.setColorFilter(ContextCompat.getColor(requireContext(), R.color.weather_status_rain_day), PorterDuff.Mode.SRC_IN);
                break;
            case "snow":
                weatherStatusImage.setImageResource(R.drawable.snow_weather_status_2);
//             Uri uri = Uri.parse("https://www.vhv.rs/file/max/33/332714_snow-falling-png.png"); // второй вариант
//             weatherStatusImage.setImageURI(uri);
                weatherStatusImage.setColorFilter(ContextCompat.getColor(requireContext(), R.color.weather_status_snow), PorterDuff.Mode.SRC_IN);
                break;
            case "mist":
                weatherStatusImage.setImageResource(R.drawable.mist_weather_status);
                weatherStatusImage.setColorFilter(ContextCompat.getColor(requireContext(), R.color.weather_status_mist), PorterDuff.Mode.SRC_IN);
                break;
            case "clear_sky_day": {
                // Второй вариант
//                Uri uri = Uri.parse("https://www.nicepng.com/png/full/389-3899694_beach-illustration-sunshine-rays-white-cinematic-bars-png.png");
//                weatherStatusImage.setImageURI(uri);
                weatherStatusImage.setImageResource(R.drawable.sunny_weather_status_4);
                weatherStatusImage.setColorFilter(ContextCompat.getColor(requireContext(), R.color.weather_status_clear_sky_day), PorterDuff.Mode.SRC_IN);
                // Вариант фона
//                weatherStatusImage.setBackgroundColor(getResources().getColor(R.color.weather_status_sun_back));
                break;
            }
            case "few_clouds_day":
                weatherStatusImage.setImageResource(R.drawable.little_cloudy_weather_status_2);
                weatherStatusImage.setColorFilter(ContextCompat.getColor(requireContext(), R.color.weather_status_few_clouds_day), PorterDuff.Mode.SRC_IN);
                break;
            case "scattered_clouds":
                weatherStatusImage.setImageResource(R.drawable.cloudy_weather_status);
                weatherStatusImage.setColorFilter(ContextCompat.getColor(requireContext(), R.color.weather_status_scattered_clouds), PorterDuff.Mode.SRC_IN);
                break;
            case "broken_clouds": {
                // Второй вариант:
//                Uri uri = Uri.parse("https://cdn.clipart.email/ebf7869a3ef385ffb67b8a2a0dcba02a_cartoon-clouds-png-transparent-without-background-image-free-png-_1000-824.png");
//                weatherStatusImage.setImageURI(uri);
                weatherStatusImage.setImageResource(R.drawable.cloudy_weather_status);
                weatherStatusImage.setColorFilter(ContextCompat.getColor(requireContext(), R.color.weather_status_broken_clouds), PorterDuff.Mode.SRC_IN);
                break;
            }
        }
    }

    private void setThermometerViewParameters(int degrees){
        if(degrees <= -30) {
            ThermometerView.level = 15;
            ThermometerView.levelColor = ContextCompat.getColor(requireContext(), R.color.thermometer_1);
        }
        else if(degrees <= -20) {
            ThermometerView.level = 20;
            ThermometerView.levelColor = ContextCompat.getColor(requireContext(), R.color.thermometer_2);
        }
        else if(degrees <= -10){
            ThermometerView.level = 25;
            ThermometerView.levelColor = ContextCompat.getColor(requireContext(), R.color.thermometer_3);
        }
        else if(degrees <= 0){
            ThermometerView.level = 30;
            ThermometerView.levelColor = ContextCompat.getColor(requireContext(), R.color.thermometer_4);
        }
        else if(degrees <= 10) {
            ThermometerView.level = 35;
            ThermometerView.levelColor = ContextCompat.getColor(requireContext(), R.color.thermometer_5);
        }
        else if(degrees <= 15){
            ThermometerView.level = 45;
            ThermometerView.levelColor = ContextCompat.getColor(requireContext(), R.color.thermometer_6);
        }
        else if(degrees <= 20){
            ThermometerView.level = 55;
            ThermometerView.levelColor = ContextCompat.getColor(requireContext(), R.color.thermometer_7);
        }
        else if(degrees <= 25) {
            ThermometerView.level = 65;
            ThermometerView.levelColor = ContextCompat.getColor(requireContext(), R.color.thermometer_8);
        }
        else if(degrees <= 30) {
            ThermometerView.level = 75;
            ThermometerView.levelColor = ContextCompat.getColor(requireContext(), R.color.thermometer_9);
        }
        else  if(degrees <= 40) {
            ThermometerView.level = 85;
            ThermometerView.levelColor = ContextCompat.getColor(requireContext(), R.color.thermometer_10);
        }
        else if(degrees <= 50) {
            ThermometerView.level = 95;
            ThermometerView.levelColor = ContextCompat.getColor(requireContext(), R.color.thermometer_11);
        }
        else {
            ThermometerView.level = 100;
            ThermometerView.levelColor = ContextCompat.getColor(requireContext(), R.color.thermometer_12);        }
        Log.d("BatteryView", "setThermometerViewParameters");
        thermometerView.invalidate();
    }

    private void showAlertDialog(String message){
        // Создаем билдер и передаем контекст приложения
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        // в билдере указываем заголовок окна (можно указывать как ресурс, так и строку)
        builder.setTitle(R.string.sorry_alert_dialog)
                // указываем сообщение в окне (также есть вариант со строковым параметром)
                .setMessage(message)
                // можно указать и пиктограмму
                .setIcon(R.drawable.ic_baseline_sentiment_dissatisfied_24)
                // устанавливаем кнопку (название кнопки также можно задавать строкой)
                .setPositiveButton(R.string.ok,
                        // Ставим слушатель, нажатие будем обрабатывать
                        (dialog, id) -> {
                    if (CurrentDataContainer.isFirstEnter) EventBus.getBus().post(new OpenChooseCityFragmentEvent());
                        });

        AlertDialog alert = builder.create();
        alert.show();
    }

    public void takeCitiesListFromResources(Resources resources){
        String[] cities = resources.getStringArray(R.array.cities);
        List<String> cit = Arrays.asList(cities);
        citiesListFromRes = new ArrayList<>(cit);
    }

    public void generateDaysList(){
        Date currentDate = new Date();
        DateFormat timeFormat = new SimpleDateFormat("E", Locale.getDefault());
        String curDay = timeFormat.format(currentDate);
        Log.d(myLog, "CURDAY = " + curDay);
        ArrayList<String> daysList = new ArrayList<>();
        daysList.add(curDay);
        for (int i = 1; i < OpenWeatherMap.FORECAST_DAYS ; i++) {
            Calendar instance = Calendar.getInstance(Locale.getDefault());
            instance.add(Calendar.DAY_OF_MONTH, i); // прибавляем 1 день к установленной дате
            Date nextDate = instance.getTime(); // получаем измененную дату
            String nextDay = timeFormat.format(nextDate);
            daysList.add(nextDay);
        }
        Log.d(myLog, "WEEK: "+ daysList.toString());
        days = daysList;
    }

    public void addDefaultDataToDaysTempFromRes(Resources resources){
        String[] daysTempStringArr = resources.getStringArray(R.array.daysTemp);
        daysTemp  = Arrays.asList(daysTempStringArr);
        tempMax = daysTemp;
        tempMin = daysTemp;
    }

    private void addDefaultDataToWeatherStateInfo(){
        for (int i = 0; i < OpenWeatherMap.FORECAST_DAYS ; i++) {
           weatherStateInfo.add("not found");
        }
    }

    public void addDataToWeatherIconsIdFromRes(List<Integer> weatherIcon){
        weatherIcon.add(R.drawable.clear_sky_day);
        weatherIcon.add(R.drawable.few_clouds_day);
        weatherIcon.add(R.drawable.scattered_clouds);
        weatherIcon.add(R.drawable.broken_clouds);
        weatherIcon.add(R.drawable.shower_rain);
        weatherIcon.add(R.drawable.rain_day);
        weatherIcon.add(R.drawable.thunderstorm);
        weatherIcon.add(R.drawable.snow);
        weatherIcon.add(R.drawable.mist);
    }

    private void addDefaultDataToCardViewColorList(){
        for (int i = 0; i < 5 ; i++) {
            cardViewColor.add(ContextCompat.getColor(requireContext(),R.color.white));
        }
    }

    public void addDefaultDataToHourlyWeatherRV(Resources resources){
        String[] hourlyTempStringArr = resources.getStringArray(R.array.daysTemp);
        hourlyTemperature  = Arrays.asList(hourlyTempStringArr);

        String[] hoursStringArr = resources.getStringArray(R.array.hours);
        hourlyTime  = Arrays.asList(hoursStringArr);

        addDataToWeatherIconsIdFromRes(hourlyWeatherIcon);
    }

    private void addDefaultDataToCurrentWeatherRV(Resources resources){
        currentWeather = new ArrayList<>();
        String windInfoFromRes = resources.getString(R.string.windInfo);
        String wind = String.format(windInfoFromRes, "0");
        String feelsFromRes = resources.getString(R.string.feels_like_temp);
        String feels = String.format(feelsFromRes, "+","0");
        String pressureFromRes = resources.getString(R.string.pressureInfo);
        String pressure = String.format(pressureFromRes, "0");
        currentWeather.add(wind);
        currentWeather.add(feels);
        currentWeather.add(pressure);
    }

    @Override
    public void onItemClicked(View view, String itemText, int position) {}

    @Override
    public void onItemLongPressed(View itemText, int position) {}

    private void setupRecyclerView() {
        // Заменяем среднюю температуру из daysTemp на наибольшую/наименьшую темперутуру из tempMax и tempMin:
        daysTemp = new ArrayList<>();
        for (int i = 0; i < OpenWeatherMap.FORECAST_DAYS ; i++) {
            daysTemp.add(tempMax.get(i) + "/" + tempMin.get(i));
        }

        Log.d("tempMax-min in RV", daysTemp.toString());
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireActivity().getBaseContext(), LinearLayoutManager.VERTICAL, false);
        WeekWeatherRecyclerDataAdapter weekWeatherAdapter = new WeekWeatherRecyclerDataAdapter(days, daysTemp, weatherIcon, weatherStateInfo, cardViewColor, this);

        weatherRecyclerView.setLayoutManager(layoutManager);
        weatherRecyclerView.setAdapter(weekWeatherAdapter);
    }

    private void setupHourlyWeatherRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireActivity().getBaseContext(), LinearLayoutManager.HORIZONTAL, false);
        HourlyWeatherRecyclerDataAdapter hourlyWeatherRecyclerDataAdapter = new HourlyWeatherRecyclerDataAdapter(hourlyTime, hourlyWeatherIcon, hourlyTemperature, this);

        hourlyRecyclerView.setLayoutManager(layoutManager);
        hourlyRecyclerView.setAdapter(hourlyWeatherRecyclerDataAdapter);
    }

    private void setupCurrentWeatherRecyclerView(){
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireActivity().getBaseContext(), LinearLayoutManager.HORIZONTAL, false);
        CurrentWeatherRecyclerDataAdapter currentWeatherRecyclerDataAdapter = new CurrentWeatherRecyclerDataAdapter(currentWeather, this);

        currentWeatherRecyclerView.setLayoutManager(layoutManager);
        currentWeatherRecyclerView.setAdapter(currentWeatherRecyclerDataAdapter);
    }
    @Override
    public void onStart() {
        super.onStart();
        EventBus.getBus().register(this);
    }

    @Override
    public void onStop() {
        EventBus.getBus().unregister(this);
        super.onStop();
    }
}