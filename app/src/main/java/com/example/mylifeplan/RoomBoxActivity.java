package com.example.mylifeplan;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class RoomBoxActivity extends AppCompatActivity {

    private TextView tvCoinsScore;

    // Слои мебели и котенок в комнате
    private ImageView ivWindow, ivPainting, ivRug, ivDesk, ivRoomPet;

    // Кнопки магазина
    private TextView btnBuyRug, btnBuyPainting, btnBuyDesk, btnBuyWindow;

    // Переменные для расчета смещения пальца при перетаскивании
    private float dX = 0, dY = 0;

    // Общие монеты и хранилище данных
    private float coinsBalance = 0.0f;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room_box);

        prefs = getSharedPreferences("MyLifePlanPrefs", Context.MODE_PRIVATE);

        // Инициализация виджетов
        tvCoinsScore = findViewById(R.id.tv_coins_score);

        ivWindow = findViewById(R.id.iv_item_window);
        ivPainting = findViewById(R.id.iv_item_painting);
        ivRug = findViewById(R.id.iv_item_rug);
        ivDesk = findViewById(R.id.iv_item_desk);
        ivRoomPet = findViewById(R.id.iv_room_pet); // Иконка котенка

        btnBuyRug = findViewById(R.id.btn_buy_rug);
        btnBuyPainting = findViewById(R.id.btn_buy_painting);
        btnBuyDesk = findViewById(R.id.btn_buy_desk);
        btnBuyWindow = findViewById(R.id.btn_buy_window);

        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        // Навешиваем слушатели покупок
        btnBuyRug.setOnClickListener(v -> buyItem("rug", 15, btnBuyRug, ivRug));
        btnBuyPainting.setOnClickListener(v -> buyItem("painting", 20, btnBuyPainting, ivPainting));
        btnBuyDesk.setOnClickListener(v -> buyItem("desk", 30, btnBuyDesk, ivDesk));
        btnBuyWindow.setOnClickListener(v -> buyItem("window", 40, btnBuyWindow, ivWindow));

        // Делаем котенка перетаскиваемым (он доступен всегда по умолчанию)
        makeViewDraggable(ivRoomPet, "pet");

        // Загрузка состояния комнаты при открытии экрана
        loadGameState();
    }

    /**
     * Универсальный метод для покупки декораций
     */
    private void buyItem(String itemKey, int price, TextView buyButton, ImageView itemLayer) {
        boolean alreadyBought = prefs.getBoolean("room_has_" + itemKey, false);
        if (alreadyBought) {
            Toast.makeText(this, "Этот декор уже украшает вашу комнату!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (coinsBalance >= price) {
            coinsBalance -= price;

            SharedPreferences.Editor editor = prefs.edit();
            editor.putFloat("coins", coinsBalance);
            editor.putBoolean("room_has_" + itemKey, true);
            editor.commit(); // Запись мгновенно [1.1.5]

            tvCoinsScore.setText(String.format(Locale.US, "%.2f", coinsBalance));
            itemLayer.setVisibility(View.VISIBLE);
            setButtonOwned(buyButton);

            // Как только предмет куплен, делаем его перетаскиваемым
            makeViewDraggable(itemLayer, itemKey);

            Toast.makeText(this, "Покупка успешна! Перетащите предмет на любое место! ✨🐾", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Недостаточно звёзд! Требуется как минимум " + price + " ⭐", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Метод, превращающий любой ImageView в перетаскиваемый пальцем элемент
     */
    private void makeViewDraggable(final View view, final String itemKey) {
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // Фиксируем начальную разницу координат пальца и объекта
                        dX = v.getX() - event.getRawX();
                        dY = v.getY() - event.getRawY();
                        break;

                    case MotionEvent.ACTION_MOVE:
                        // Плавно перемещаем элемент вслед за пальцем
                        v.animate()
                                .x(event.getRawX() + dX)
                                .y(event.getRawY() + dY)
                                .setDuration(0)
                                .start();
                        break;

                    case MotionEvent.ACTION_UP:
                        // Как только палец отпущен, мгновенно записываем новые координаты в память [1.1.5]
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putFloat("pos_x_" + itemKey, v.getX());
                        editor.putFloat("pos_y_" + itemKey, v.getY());
                        editor.commit(); // commit() сохраняет мгновенно [1.1.5]
                        break;

                    default:
                        return false;
                }
                return true;
            }
        });
    }

    /**
     * Загружает состояние монет, купленной мебели и их координат из памяти устройства
     */
    private void loadGameState() {
        coinsBalance = prefs.getFloat("coins", 0.0f);
        tvCoinsScore.setText(String.format(Locale.US, "%.2f", coinsBalance));

        // Восстанавливаем позицию котенка
        loadViewPosition(ivRoomPet, "pet");

        // Загружаем Плед/Коврик
        boolean isRugBought = prefs.getBoolean("room_has_rug", false);
        ivRug.setVisibility(isRugBought ? View.VISIBLE : View.GONE);
        if (isRugBought) {
            setButtonOwned(btnBuyRug);
            loadViewPosition(ivRug, "rug");
            makeViewDraggable(ivRug, "rug");
        }

        // Загружаем Картину
        boolean isPaintingBought = prefs.getBoolean("room_has_painting", false);
        ivPainting.setVisibility(isPaintingBought ? View.VISIBLE : View.GONE);
        if (isPaintingBought) {
            setButtonOwned(btnBuyPainting);
            loadViewPosition(ivPainting, "painting");
            makeViewDraggable(ivPainting, "painting");
        }

        // Загружаем Стол
        boolean isDeskBought = prefs.getBoolean("room_has_desk", false);
        ivDesk.setVisibility(isDeskBought ? View.VISIBLE : View.GONE);
        if (isDeskBought) {
            setButtonOwned(btnBuyDesk);
            loadViewPosition(ivDesk, "desk");
            makeViewDraggable(ivDesk, "desk");
        }

        // Загружаем Окно
        boolean isWindowBought = prefs.getBoolean("room_has_window", false);
        ivWindow.setVisibility(isWindowBought ? View.VISIBLE : View.GONE);
        if (isWindowBought) {
            setButtonOwned(btnBuyWindow);
            loadViewPosition(ivWindow, "window");
            makeViewDraggable(ivWindow, "window");
        }
    }

    /**
     * Восстанавливает координаты X и Y для элемента из памяти [1.1.5]
     */
    private void loadViewPosition(final View view, final String itemKey) {
        // post() гарантирует, что координаты восстановятся ПОСЛЕ того, как разметка прорисуется
        view.post(() -> {
            float savedX = prefs.getFloat("pos_x_" + itemKey, -1);
            float savedY = prefs.getFloat("pos_y_" + itemKey, -1);
            if (savedX != -1 && savedY != -1) {
                view.setX(savedX);
                view.setY(savedY);
            }
        });
    }

    /**
     * Переводит кнопку товара в состояние "Куплено"
     */
    private void setButtonOwned(TextView button) {
        button.setText("Куплено");
        button.setEnabled(false);
        button.setAlpha(0.6f);
    }
}