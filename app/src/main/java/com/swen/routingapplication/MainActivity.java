package com.swen.routingapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.location.Location;
import android.os.Bundle;

import android.graphics.BitmapFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;

import android.widget.Toast;

// classes needed to initialize map
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;

// classes needed to add the location component
import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.modes.CameraMode;

// classes needed to add a marker
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.style.layers.SymbolLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;

import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconAllowOverlap;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconIgnorePlacement;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconImage;

// classes to calculate a route
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncher;
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncherOptions;
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute;
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute;
import com.mapbox.api.directions.v5.models.DirectionsResponse;
import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.services.android.navigation.v5.routeprogress.ProgressChangeListener;
import com.mapbox.services.android.navigation.v5.routeprogress.RouteProgress;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import android.util.Log;

// classes needed to launch navigation UI
import android.view.View;
import android.widget.Button;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, MapboxMap.OnMapClickListener, PermissionsListener {

    // variables for adding location layer
    private MapView mapView;
    private MapboxMap mapboxMap;

    // variables for adding location layer
    private PermissionsManager permissionsManager;
    private LocationComponent locationComponent;
    // variables for calculating and drawing a route
    private DirectionsRoute currentRoute;
    private static final String TAG = "DirectionsActivity";
    private NavigationMapRoute navigationMapRoute;
    // variables needed to initialize navigation
    private Button button;

    private RequestQueue request_queue;

    private String currentOrderID;
    private Boolean activeDelivery;

    String token;
    String factoryID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Mapbox.getInstance(this, getString(R.string.access_token));
        setContentView(R.layout.activity_main);
        token = getIntent().getExtras().getString("token");
        factoryID = getIntent().getExtras().getString("factory_id");
        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
        request_queue = Volley.newRequestQueue(this);

        activeDelivery = false;

        button = findViewById(R.id.startButton);
        buttonGetDeliveryState();
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (activeDelivery){
                    //start directions
                    boolean simulateRoute = true;
                        NavigationLauncherOptions options = NavigationLauncherOptions.builder()
                                .directionsRoute(currentRoute)
                                .shouldSimulateRoute(simulateRoute)
                                .build();
                    // Call this method with Context from within an Activity
                    NavigationLauncher.startNavigation(MainActivity.this, options);
                    buttonGetDeliveryState();
                    //clear route
                    navigationMapRoute.removeRoute();
                    completeDelivery();
                }else{
                    buttonLoadingState();
                    getDelivery();
                }
            }
        });
    }

    @Override
    public void onMapReady(@NonNull final MapboxMap mapboxMap) {
        this.mapboxMap = mapboxMap;
        mapboxMap.setStyle(getString(R.string.navigation_guidance_day), new Style.OnStyleLoaded() {
            @Override
            public void onStyleLoaded(@NonNull Style style) {
                enableLocationComponent(style);
                addDestinationIconSymbolLayer(style);
                mapboxMap.addOnMapClickListener(MainActivity.this);
            }
        });
    }

    private void addDestinationIconSymbolLayer(@NonNull Style loadedMapStyle) {
        loadedMapStyle.addImage("destination-icon-id",
                BitmapFactory.decodeResource(this.getResources(), R.drawable.mapbox_marker_icon_default));
        GeoJsonSource geoJsonSource = new GeoJsonSource("destination-source-id");
        loadedMapStyle.addSource(geoJsonSource);
        SymbolLayer destinationSymbolLayer = new SymbolLayer("destination-symbol-layer-id", "destination-source-id");
        destinationSymbolLayer.withProperties(
                iconImage("destination-icon-id"),
                iconAllowOverlap(true),
                iconIgnorePlacement(true)
        );
        loadedMapStyle.addLayer(destinationSymbolLayer);
    }

    @SuppressWarnings({"MissingPermission"})
    @Override
    public boolean onMapClick(@NonNull LatLng point) {
        Log.d(TAG, "onMapClick: lat: " + point.getLatitude());
        Log.d(TAG, "onMapClick: lon: " + point.getLongitude());
//        Point destinationPoint = Point.fromLngLat(point.getLongitude(), point.getLatitude());
//        Point originPoint = Point.fromLngLat(locationComponent.getLastKnownLocation().getLongitude(),
//                locationComponent.getLastKnownLocation().getLatitude());
//
//        GeoJsonSource source = mapboxMap.getStyle().getSourceAs("touch-source-id");
//        if (source != null) {
//            source.setGeoJson(Feature.fromGeometry(destinationPoint));
//        }
        //getRoute(originPoint, destinationPoint);
        return true;
    }

    private void getRoute(Point origin, Point factory, Point destination) {
        NavigationRoute.builder(this)
                .accessToken(Mapbox.getAccessToken())
                .origin(origin)
                .addWaypoint(factory)
                .destination(destination)
                .build()
                .getRoute(new Callback<DirectionsResponse>() {
                    @Override
                    public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {
                        // You can get the generic HTTP info about the response
                        Log.d(TAG, "Response code: " + response.code());
                        if (response.body() == null) {
                            Log.e(TAG, "No routes found, make sure you set the right user and access token.");
                            buttonGetDeliveryState();
                            return;
                        } else if (response.body().routes().size() < 1) {
                            Log.e(TAG, "No routes found");
                            return;
                        }
                        currentRoute = response.body().routes().get(0);
                        // Draw the route on the map
                        if (navigationMapRoute != null) {
                            navigationMapRoute.removeRoute();
                        } else {
                            navigationMapRoute = new NavigationMapRoute(null, mapView, mapboxMap, R.style.NavigationMapRoute);
                        }
                        navigationMapRoute.addRoute(currentRoute);
                        buttonStartNavigationState();
                    }

                    @Override
                    public void onFailure(Call<DirectionsResponse> call, Throwable throwable) {
                        Log.e(TAG, "Error: " + throwable.getMessage());
                    }
                });
    }

    @SuppressWarnings({"MissingPermission"})
    private void enableLocationComponent(@NonNull Style loadedMapStyle) {
// Check if permissions are enabled and if not request
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
// Activate the MapboxMap LocationComponent to show user location
// Adding in LocationComponentOptions is also an optional parameter
            locationComponent = mapboxMap.getLocationComponent();
            locationComponent.activateLocationComponent(this, loadedMapStyle);
            locationComponent.setLocationComponentEnabled(true);
// Set the component's camera mode
            locationComponent.setCameraMode(CameraMode.TRACKING);
        } else {
            permissionsManager = new PermissionsManager(this);
            permissionsManager.requestLocationPermissions(this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onExplanationNeeded(List<String> permissionsToExplain) {
        Toast.makeText(this, R.string.user_location_permission_explanation, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onPermissionResult(boolean granted) {
        if (granted) {
            enableLocationComponent(mapboxMap.getStyle());
        } else {
            Toast.makeText(this, R.string.user_location_permission_not_granted, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    private void buttonGetDeliveryState(){
        button.setEnabled(true);
        button.setBackgroundResource(R.color.mapboxBlue);
        button.setText("Request Delivery");
        button.setTextColor(getResources().getColor(R.color.white));
        activeDelivery = false;
    }

    private void buttonLoadingState(){
        button.setEnabled(false);
        button.setBackgroundResource(R.color.md_grey_400);
        button.setText("Loading...");
        button.setTextColor(getResources().getColor(R.color.md_grey_700));
        activeDelivery = false;
    }

    private void buttonStartNavigationState(){
        button.setEnabled(true);
        button.setBackgroundResource(R.color.mapboxBlue);
        button.setText("Start Navigation");
        button.setTextColor(getResources().getColor(R.color.white));
        activeDelivery = true;
    }

    private void getDelivery(){
        String url = "https://pdq-routing-subsystem.herokuapp.com/delivery/request";
        Map<String, String> params = new HashMap<String, String>();
        params.put("factory_id",factoryID); params.put("token", token);
        JSONObject request_params = new JSONObject(params);
        JsonObjectRequest request = new JsonObjectRequest
                (Request.Method.POST, url, request_params,
                        new com.android.volley.Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {

                            try {
                                currentOrderID = response.getString("order_id");
                                JSONObject factory_location = response.getJSONObject("factory_location");
                                JSONObject delivery_location = response.getJSONObject("delivery_location");

                                double factory_lat = factory_location.getDouble("lat");
                                double factory_lon = factory_location.getDouble("lon");
                                double delivery_lat = delivery_location.getDouble("lat");
                                double delivery_lon = delivery_location.getDouble("lon");

                                Point factoryPoint = Point.fromLngLat(factory_lon,factory_lat);
                                Point deliveryPoint = Point.fromLngLat(delivery_lon,delivery_lat);
                                Point originPoint = Point.fromLngLat(locationComponent.getLastKnownLocation().getLongitude(),
                                        locationComponent.getLastKnownLocation().getLatitude());

                                GeoJsonSource source = mapboxMap.getStyle().getSourceAs("destination-source-id");
                                if (source != null) {source.setGeoJson(Feature.fromGeometry(deliveryPoint));}

                                GeoJsonSource fsource = mapboxMap.getStyle().getSourceAs("factory-source-id");
                                if (fsource != null) {fsource.setGeoJson(Feature.fromGeometry(factoryPoint));}

                                getRoute(originPoint, factoryPoint, deliveryPoint);


                            } catch (JSONException e) {
                                e.printStackTrace();
                                buttonGetDeliveryState();
                            }
                        }
                        },
                        new com.android.volley.Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            Log.d(TAG, "onErrorResponse:"+error);
                            Toast.makeText(getApplicationContext(), "Error occurred.",Toast.LENGTH_SHORT).show();
                            buttonGetDeliveryState();
                        }
                });
        request_queue.add(request);
    }

    private void completeDelivery(){
        String url = "https://pdq-routing-subsystem.herokuapp.com/delivery/deliver";
        Map<String, String> params = new HashMap<String, String>();
        params.put("order_id",currentOrderID);
        params.put("token", token);

        JSONObject request_params = new JSONObject(params);
        JsonObjectRequest request = new JsonObjectRequest
                (Request.Method.PUT, url, request_params,
                        new com.android.volley.Response.Listener<JSONObject>() {
                            @Override
                            public void onResponse(JSONObject response) {
                            }
                        },
                        new com.android.volley.Response.ErrorListener() {
                            @Override
                            public void onErrorResponse(VolleyError error) {
                                Log.d(TAG, "onErrorResponse:"+error);
                            }
                        });
        request_queue.add(request);
    }
    
}
