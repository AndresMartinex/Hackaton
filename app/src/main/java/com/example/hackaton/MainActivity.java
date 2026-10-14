package com.example.hackaton;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private MazeView maze;
    private Vibrator vibrator;
    private long lastTimestamp = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        maze = findViewById(R.id.mazeView);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnCamara).setOnClickListener(v -> {
            try {
                startActivity(new Intent(MediaStore.ACTION_IMAGE_CAPTURE));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "No hay app de cámara disponible", Toast.LENGTH_SHORT).show();
            }
        });

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer == null) {
            Toast.makeText(this, "Este dispositivo no tiene acelerómetro", Toast.LENGTH_LONG).show();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
            vibrator = vm.getDefaultVibrator();
        } else {
            vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        }

        maze.setListener(new MazeView.Listener() {
            @Override
            public void onNearWall() {
                vibrar(30);
            }

            @Override
            public void onWin() {
                vibrar(300);
                Toast.makeText(MainActivity.this, "¡Ganaste!", Toast.LENGTH_SHORT).show();
                maze.postDelayed(maze::newMaze, 1500);
            }
        });
    }

    private void vibrar(long ms) {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(ms);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        lastTimestamp = 0;
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (lastTimestamp == 0) {
            lastTimestamp = event.timestamp;
            return;
        }
        float dt = Math.min((event.timestamp - lastTimestamp) / 1_000_000_000f, 0.05f);
        lastTimestamp = event.timestamp;

        // En vertical, el eje X del sensor está invertido respecto a la pantalla
        maze.onTilt(-event.values[0], event.values[1], dt);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }
}