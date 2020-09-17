package ru.geekbrains.gb_android_2;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Objects;
import java.util.regex.Pattern;

import ru.geekbrains.gb_android_2.database.CitiesList;
import ru.geekbrains.gb_android_2.database.CitiesListDao;
import ru.geekbrains.gb_android_2.database.CitiesListSource;
import ru.geekbrains.gb_android_2.events.OpenWeatherMainFragmentEvent;
import ru.geekbrains.gb_android_2.forecastRequest.ForecastRequest;
import ru.geekbrains.gb_android_2.forecastRequest.OpenWeatherMap;
import ru.geekbrains.gb_android_2.model.HourlyWeatherData;
import ru.geekbrains.gb_android_2.model.WeatherData;
import ru.geekbrains.gb_android_2.rvDataAdapters.CitiesRecyclerDataAdapter;
import ru.geekbrains.gb_android_2.rvDataAdapters.RVOnItemClick;

import static android.content.Context.MODE_PRIVATE;


public class ChooseCityFragment extends Fragment implements RVOnItemClick {

    private TextInputEditText enterCity;
    static String currentCity = "";
    private RecyclerView recyclerView;
    private CitiesRecyclerDataAdapter adapter;
    private ArrayList<WeatherData> weekWeatherData = new ArrayList<>();
    private ArrayList<HourlyWeatherData> hourlyWeatherList = new ArrayList<>();
    final String myLog = "myLog";
    OpenWeatherMap openWeatherMap = OpenWeatherMap.getInstance();
    private boolean isErrorShown;
    // Паттерн для проверки, является ли введеное слово названием города.
    Pattern checkEnterCity = Pattern.compile("^[а-яА-ЯЁa-zA-Z]+(?:[\\s-][а-яА-ЯЁa-zA-Z]+)*$");
    private CitiesListSource citiesListSource;

    static ChooseCityFragment create(CurrentDataContainer container) {
        ChooseCityFragment fragment = new ChooseCityFragment();    // создание

        // Передача параметра
        Bundle args = new Bundle();
        args.putSerializable("currCity", container);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Log.d("myLog", "onCreate - fragment SettingsFragment");
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        // First clear current all the menu items
        menu.clear();

        // Add the new menu items
        inflater.inflate(R.menu.choose_city_menu, menu);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.sorting){
            if(!CurrentDataContainer.isCitiesListSortedByName) {
                adapter.sortByName();
                CurrentDataContainer.isCitiesListSortedByName = true;
                Toast.makeText(getContext(), R.string.alfabetical_sorting, Toast.LENGTH_SHORT).show();
            } else {
                adapter.sortByCreatedTime();
                CurrentDataContainer.isCitiesListSortedByName = false;
                Toast.makeText(getContext(), R.string.sorting_by_date, Toast.LENGTH_SHORT).show();
            }
        }
        return false;
    }

    // При создании фрагмента укажем его макет
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.d("myLog", "onCreateView - fragment SettingsFragment");
        return getView() != null ? getView() :
                inflater.inflate(R.layout.fragment_choose_city, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        checkEnterCityField();
        setupRecyclerView();// тут создается адаптер на основании citiesList из этого класса ChooseCityFragment (адаптер берет список городов из этого класса)
        if(CurrentDataContainer.isCitiesListSortedByName) adapter.sortByName();
        setOnEnterCityEnterKeyListener();
    }

    private void initViews(View view) {
        enterCity = view.findViewById(R.id.enterCity);
        recyclerView = view.findViewById(R.id.cities);
    }

    private void setOnEnterCityEnterKeyListener() {
        enterCity.setOnKeyListener((view, keyCode, keyEvent) -> {
            if ((keyEvent.getAction() == KeyEvent.ACTION_DOWN) &&
                    (keyCode == KeyEvent.KEYCODE_ENTER)) {
                // Perform action on key press
                // "Выклчаем" editText, чтобы убрать с него фокус и дать возмоность показать ошибку:
                enterCity.setEnabled(false);
                // Если ошибка показалась, "включаем" его обратно, чтобы дать поьзователю исправить ошибку:
                if (isErrorShown) {
                    enterCity.setEnabled(true);
                    Toast.makeText(requireActivity(), R.string.setOnBtnOkEnterCityToast, Toast.LENGTH_SHORT).show();
                }
                if (!isErrorShown) {
                    enterCity.setEnabled(true);
                    if (!Objects.requireNonNull(enterCity.getText()).toString().equals("")) {
                        String previousCity = requireActivity()
                                .getSharedPreferences(MainActivity.SETTINGS, MODE_PRIVATE)
                                .getString("current city", "Saint Petersburg");
                        currentCity = enterCity.getText().toString();
                        // Делаем первую букву заглавной:
                        currentCity = currentCity.substring(0, 1).toUpperCase() + currentCity.substring(1);
                        //Создаем прогноз погоды на неделю для нового выбранного города:
                        takeWeatherInfoForFiveDays();
                        Handler handler = new Handler();
                        new Thread(() -> {
                                try {
                                    // Ждем, пока не получим актуальный response code:
                                    ForecastRequest.getForecastResponseReceived().await();

                                if (ForecastRequest.responseCode == 404) {
                                    Log.d(myLog, "RESPONSE COD = " + ForecastRequest.responseCode + " CURR CITY = " + currentCity);
                                    handler.post(()->{
                                        showAlertDialog(R.string.city_not_found);
                                        currentCity = previousCity;
                                    });
                                }
                                if (ForecastRequest.responseCode == 200) {
                                    CurrentDataContainer.isFirstEnter = false;
                                    CurrentDataContainer.isFirstCityInSession = false;


                                    //Добавляем новый город в RV
                                    citiesListSource.addCity(new CitiesList(currentCity));
                                    if(CurrentDataContainer.isCitiesListSortedByName) adapter.sortByName();
                                    else adapter.sortByCreatedTime();
                                    //Запоминаем выбранный город в SharedPreferences
                                    saveCurrentCityToPreference(requireActivity().getSharedPreferences(MainActivity.SETTINGS, MODE_PRIVATE), currentCity);

                                    saveIsFirstEnterToPreference(requireActivity().getSharedPreferences(MainActivity.SETTINGS, MODE_PRIVATE), CurrentDataContainer.isFirstEnter);

                                    Log.d(myLog, "RESPONSE COD = " + ForecastRequest.responseCode + " CURR CITY = " + currentCity);
                                    weekWeatherData = openWeatherMap.getWeekWeatherData(getResources());
                                    hourlyWeatherList = openWeatherMap.getHourlyWeatherData();
                                    requireActivity().runOnUiThread(() -> {
                                    CurrentDataContainer.getInstance().weekWeatherData = weekWeatherData;
                                    CurrentDataContainer.getInstance().hourlyWeatherList = hourlyWeatherList;
                                    Toast.makeText(requireActivity(), currentCity, Toast.LENGTH_SHORT).show();
                                    updateWeatherData();
                                    enterCity.setText("");
                                    });
                                }
                                if (ForecastRequest.responseCode != 200 && ForecastRequest.responseCode != 404) {
                                    Log.d(myLog, "RESPONSE COD = " + ForecastRequest.responseCode + " CURR CITY = " + currentCity);
                                    handler.post(()-> {
                                        showAlertDialog(R.string.connection_failed);
                                        currentCity = previousCity;
                                    });
                                }
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                }).start();
                            }
                    Log.d(myLog, "ChooseCityFragment - setOnBtnOkEnterCityClickListener -> BEFORE flag");
                    }
                return true;
            }
            return false;
        });
    }

    private void saveCurrentCityToPreference(SharedPreferences preferences, String currentCity) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("current city", currentCity);
        editor.apply();
    }

    private void saveIsFirstEnterToPreference(SharedPreferences preferences, boolean isFirstEnter) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("isFirstEnter", isFirstEnter);
        editor.apply();
    }

    private void showAlertDialog(int messageId){
        // Создаем билдер и передаем контекст приложения
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        // в билдере указываем заголовок окна (можно указывать как ресурс, так и строку)
        builder.setTitle(R.string.sorry_alert_dialog)
                // указываем сообщение в окне (также есть вариант со строковым параметром)
                .setMessage(messageId)
                // можно указать и пиктограмму
                .setIcon(R.drawable.ic_baseline_sentiment_dissatisfied_24)
                // устанавливаем кнопку (название кнопки также можно задавать строкой)
                .setPositiveButton(R.string.ok,
                        // Ставим слушатель, нажатие будем обрабатывать
                        (dialog, id) -> enterCity.setText(""));
        AlertDialog alert = builder.create();
        alert.show();
    }

    private void updateWeatherData(){
        EventBus.getBus().post(new OpenWeatherMainFragmentEvent());
    }


    // Обработчик нажатий на город из списка RV
    @Override
    public void onItemClicked(View view, String itemText, int position) {
        currentCity = itemText;
        // Ставим выбранный город на первое место в коллекции:
        adapter.putChosenCityToTopInCitiesList(currentCity);
        if(CurrentDataContainer.isCitiesListSortedByName) {adapter.sortByName();}
        else {adapter.sortByCreatedTime();}
        // Запоминаем текущий город
        SharedPreferences sharedPreferences = requireActivity().getSharedPreferences(MainActivity.SETTINGS, MODE_PRIVATE);
        String previousCity = sharedPreferences.getString("current city", null);
        // Записываем выбранный город в SharedPreferences
        saveCurrentCityToPreference(sharedPreferences, currentCity);

        //Создаем прогноз погоды на неделю для нового выбранного города:
        takeWeatherInfoForFiveDays();
        Handler handler = new Handler();
        new Thread(() -> {
            try {
                // Ждем, пока не получим актуальный response code:
                ForecastRequest.getForecastResponseReceived().await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if(ForecastRequest.responseCode == 200) {
                CurrentDataContainer.isFirstEnter = false;
                CurrentDataContainer.isFirstCityInSession = false;
                saveIsFirstEnterToPreference(requireActivity().getSharedPreferences(MainActivity.SETTINGS, MODE_PRIVATE), CurrentDataContainer.isFirstEnter);

                Log.d(myLog, "RESPONSE COD = " + ForecastRequest.responseCode + " CURR CITY = " + currentCity);

                weekWeatherData = openWeatherMap.getWeekWeatherData(getResources());
                hourlyWeatherList = openWeatherMap.getHourlyWeatherData();
                requireActivity().runOnUiThread(() -> {
                    CurrentDataContainer.getInstance().weekWeatherData = weekWeatherData;
                    CurrentDataContainer.getInstance().hourlyWeatherList = hourlyWeatherList;
                    //Обновляем данные погоды, если положение горизонтальное или открываем новое активити, если вертикальное
                    updateWeatherData();
                });
            } else {
                // Возвращаем предыдущий город в SharedPreferences
                saveCurrentCityToPreference(sharedPreferences, previousCity);

                Log.d(myLog, "RESPONSE COD = " + ForecastRequest.responseCode + " CURR CITY = " + currentCity);
                handler.post(()->showAlertDialog(R.string.connection_failed));
            }
        }).start();
        Log.d(myLog, "ChooseCityFragment - setOnBtnOkEnterCityClickListener -> BEFORE flag");
    }

    @Override
    public void onItemLongPressed(View view, int position) {
        TextView textView = (TextView) view;
        deleteItem(textView, position);
    }

    public void deleteItem(final TextView view, int position) {
        Snackbar.make(view, R.string.delete_city, Snackbar.LENGTH_LONG)
                .setAction(R.string.delete, v -> {
                    // В новом потоке удаяем город из бд:
                    Thread thread = new Thread(()-> {
                        adapter.remove(position);
                        // Удаляем запись из базы
                        CitiesList cityForRemove = citiesListSource
                                .getCitiesList()
                                .get(position);
                        citiesListSource.removeCity(cityForRemove.id);
                    });
                    thread.start();
                    try {
                        thread.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if(CurrentDataContainer.isCitiesListSortedByName) adapter.sortByName();
                }).show();
    }

    private void takeWeatherInfoForFiveDays(){
        ForecastRequest.getForecastFromServer(currentCity);
        Log.d("retrofit", "ChooseCityFragment - countDownLatch = " + ForecastRequest.getForecastResponseReceived().getCount());
    }

    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireActivity().getBaseContext());

        CitiesListDao citiesListDao = App
                .getInstance()
                .getCitiesListDao();
        citiesListSource = new CitiesListSource(citiesListDao);

        if (recyclerView.getItemDecorationCount() <= 0){
            DividerItemDecoration itemDecoration = new DividerItemDecoration(requireActivity().getBaseContext(), LinearLayoutManager.VERTICAL);
            recyclerView.addItemDecoration(itemDecoration);
        }

        adapter = new CitiesRecyclerDataAdapter(citiesListSource, this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
    }

    private void checkEnterCityField() {
        final TextView[] tv = new TextView[1];
        enterCity.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                tv[0] = (TextView) v;
                // Валидация, почти точно такая же, как и в поле логина
                validate(tv[0], checkEnterCity, getString(R.string.HintTextInputEditText));
                hideSoftKeyboard(requireActivity(), enterCity);
            }
        });
    }

    public static void hideSoftKeyboard (Activity activity, View view) {
        InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getApplicationWindowToken(), 0);
    }

    private void validate(TextView tv, Pattern check, String message){
        String value = tv.getText().toString();
        if (check.matcher(value).matches()) {    // Проверим на основе регулярных выражений
            hideError(tv);
            isErrorShown = false;
        } else {
            showError(tv, message);
            isErrorShown = true;
        }
    }
    // Показать ошибку
    private void showError(TextView view, String message) {
        view.setError(message);
    }
    // спрятать ошибку
    private void hideError(TextView view) {
        view.setError(null);
    }
}