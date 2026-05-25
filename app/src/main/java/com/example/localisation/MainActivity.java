package com.example.localisation;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private TextView tvInfo;
    private Button btnSendNow;
    private RequestQueue requestQueue;
    private LocationManager locationManager;
    private LocationListener locationListener;

    private double lastLatitude = 0.0;
    private double lastLongitude = 0.0;
    private double lastAltitude = 0.0;
    private float lastAccuracy = 0.0f;

    // URL du serveur : remplacez IP par celle de votre PC (ex: 192.168.1.10)
    private String insertUrl = "http://192.168.1.100/localisation/createPosition.php";

    // Identifiant unique du téléphone
    private String deviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvInfo = findViewById(R.id.tvInfo);
        btnSendNow = findViewById(R.id.btnSendNow);
        requestQueue = Volley.newRequestQueue(this);

        // Récupération de l'identifiant (IMEI ou Android ID)
        deviceId = getDeviceIdentifier();

        // Initialisation du LocationManager
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // Vérification et demande des permissions
        checkAndRequestPermissions();

        // Bouton manuel pour envoyer la dernière position
        btnSendNow.setOnClickListener(v -> sendPositionToServer());
    }

    private String getDeviceIdentifier() {
        // Méthode alternative à l'IMEI pour Android >= 10
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Utilise l'Android ID (unique par app, persistant jusqu'à réinstallation)
            return Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        } else {
            // Pour les anciennes versions, on peut utiliser l'IMEI
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
                if (tm != null) {
                    return tm.getDeviceId();
                }
            }
            return "unknown_device";
        }
    }

    private void checkAndRequestPermissions() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.READ_PHONE_STATE
            };
        }

        boolean allGranted = true;
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            ActivityCompat.requestPermissions(this, permissions, 100);
        } else {
            startLocationUpdates();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            boolean granted = true;
            for (int res : grantResults) {
                if (res != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }
            if (granted) {
                startLocationUpdates();
            } else {
                Toast.makeText(this, "Permissions refusées, l'application ne peut pas fonctionner", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startLocationUpdates() {
        // Vérification des permissions avant d'appeler requestLocationUpdates
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                lastLatitude = location.getLatitude();
                lastLongitude = location.getLongitude();
                lastAltitude = location.getAltitude();
                lastAccuracy = location.getAccuracy();

                String msg = String.format(Locale.getDefault(),
                        "Lat: %.6f\nLon: %.6f\nAlt: %.2f m\nPrécision: %.1f m",
                        lastLatitude, lastLongitude, lastAltitude, lastAccuracy);
                tvInfo.setText(msg);
                Toast.makeText(MainActivity.this, "Position mise à jour", Toast.LENGTH_SHORT).show();

                // Envoi automatique à chaque nouvelle position (ou commentez si vous préférez le bouton)
                sendPositionToServer();
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
                String statusText;
                switch (status) {
                    case LocationProvider.AVAILABLE:
                        statusText = "AVAILABLE";
                        break;
                    case LocationProvider.TEMPORARILY_UNAVAILABLE:
                        statusText = "TEMPORARILY_UNAVAILABLE";
                        break;
                    case LocationProvider.OUT_OF_SERVICE:
                        statusText = "OUT_OF_SERVICE";
                        break;
                    default:
                        statusText = "UNKNOWN";
                }
                Toast.makeText(MainActivity.this, "Status " + provider + " : " + statusText, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onProviderEnabled(@NonNull String provider) {
                Toast.makeText(MainActivity.this, "Provider " + provider + " activé", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onProviderDisabled(@NonNull String provider) {
                Toast.makeText(MainActivity.this, "Provider " + provider + " désactivé", Toast.LENGTH_SHORT).show();
            }
        };

        // Demande de mises à jour : 60 secondes min, 150 mètres min
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60000, 150, locationListener);
        // Optionnel : utiliser NETWORK_PROVIDER pour fallback
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60000, 150, locationListener);
    }

    private void sendPositionToServer() {
        if (lastLatitude == 0.0 && lastLongitude == 0.0) {
            Toast.makeText(this, "Aucune position disponible", Toast.LENGTH_SHORT).show();
            return;
        }

        // Format de la date pour MySQL : YYYY-MM-DD HH:MM:SS
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String currentDate = sdf.format(new Date());

        StringRequest request = new StringRequest(com.android.volley.Request.Method.POST, insertUrl,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Toast.makeText(MainActivity.this, "Envoyé: " + response, Toast.LENGTH_LONG).show();
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Toast.makeText(MainActivity.this, "Erreur: " + error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("latitude", String.valueOf(lastLatitude));
                params.put("longitude", String.valueOf(lastLongitude));
                params.put("date_position", currentDate);
                params.put("imei", deviceId);
                return params;
            }
        };

        requestQueue.add(request);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationManager != null && locationListener != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.removeUpdates(locationListener);
            }
        }
    }
}