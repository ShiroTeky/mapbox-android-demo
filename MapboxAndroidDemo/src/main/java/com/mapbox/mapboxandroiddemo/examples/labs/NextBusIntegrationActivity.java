package com.mapbox.mapboxandroiddemo.examples.labs;

import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxandroiddemo.R;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.style.layers.CircleLayer;
import com.mapbox.mapboxsdk.style.layers.LineLayer;
import com.mapbox.mapboxsdk.style.layers.Property;
import com.mapbox.mapboxsdk.style.layers.PropertyFactory;
import com.mapbox.mapboxsdk.style.layers.SymbolLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;

import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.circleColor;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.circleRadius;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconAllowOverlap;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconAnchor;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconIgnorePlacement;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineColor;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineWidth;

/**
 * Display {@link SymbolLayer} icons on the map.
 */
public class NextBusIntegrationActivity extends AppCompatActivity implements
    OnMapReadyCallback {

  private static final String TAG = "NextBusIntegrationActivity";
  private static final String VEHICLE_SOURCE_ID = "VEHICLE_SOURCE_ID";
  private static final String LINE_SOURCE_ID = "LINE_SOURCE_ID";
  private static final String ROUTE_STOP_SOURCE_ID = "ROUTE_STOP_SOURCE_ID";
  private static final String VEHICLE_ICON_ID = "VEHICLE_ICON_ID";
  private static final String VEHICLE_LAYER_ID = "VEHICLE_LAYER_ID";
  private static final String LINE_LAYER_ID = "LINE_LAYER_ID";
  private static final String ROUTE_STOP_LAYER_ID = "ROUTE_STOP_LAYER_ID";
  private static final int CAMERA_ANIMATION_SPEED = 1400;
  private MapView mapView;
  private MapboxMap mapboxMap;
  private Handler handler;
  private Runnable runnable;
  private Call<List<RestBusAgencyRoute>> restBusSingleAgencyRouteListCall;
  private Call<List<RestBusAgencyRouteVehicle>> restBusSingleRouteVehicleLocationCall;
  private Call<RestBusRouteStopResponse> restBusSingleRouteStopInfoCall;
  private Retrofit restBusClient;
  private RestBusService restBusService;
  private List<RestBusAgencyRoute> restBusAgencyRouteList;
  private boolean agencyHasBeenSelected = false;

  // MillisecondsFrequencyToCallNextBusApi is the time interval when we call the API in milliseconds, by default this is set
  // to 2000 and you should only increase the value, reducing the interval will only cause server
  // traffic, the latitude and longitude values aren't updated that frequently.
  private int MillisecondsFrequencyToCallNextBusApi = 2000;


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Mapbox access token is configured here. This needs to be called either in your application
    // object or in the same activity which contains the mapview.
    Mapbox.getInstance(this, getString(R.string.access_token));

    // This contains the MapView in XML and needs to be called after the access token is configured.
    setContentView(R.layout.activity_lab_nextbus_integration);

    mapView = findViewById(R.id.mapView);
    mapView.onCreate(savedInstanceState);
    mapView.getMapAsync(this);
  }

  @Override
  public void onMapReady(@NonNull final MapboxMap mapboxMap) {
    mapboxMap.setStyle(new Style.Builder().fromUri(Style.MAPBOX_STREETS)

            // Add the SymbolLayer icon image to the map style
            .withImage(VEHICLE_ICON_ID, BitmapFactory.decodeResource(
                NextBusIntegrationActivity.this.getResources(), R.drawable.red_marker))

            // Adding a GeoJson source for the vehicle icons.
            .withSource(new GeoJsonSource(VEHICLE_SOURCE_ID))

            // Adding a GeoJson source for drawing the single route line.
            .withSource(new GeoJsonSource(LINE_SOURCE_ID))

            .withSource(new GeoJsonSource(ROUTE_STOP_SOURCE_ID))

            // Adding the actual SymbolLayer to the map style. An offset is added that the bottom of the red
            // marker icon gets fixed to the coordinate, rather than the middle of the icon being fixed to
            // the coordinate point. This is offset is not always needed and is dependent on the image
            // that you use for the SymbolLayer icon.
            .withLayer(new SymbolLayer(VEHICLE_LAYER_ID, VEHICLE_SOURCE_ID)
                .withProperties(PropertyFactory.iconImage(VEHICLE_ICON_ID),
                    iconAllowOverlap(true),
                    iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                    iconIgnorePlacement(true))
            )

            // Adding the actual SymbolLayer to the map style. An offset is added that the bottom of the red
            // marker icon gets fixed to the coordinate, rather than the middle of the icon being fixed to
            // the coordinate point. This is offset is not always needed and is dependent on the image
            // that you use for the SymbolLayer icon.
            .withLayerBelow(new LineLayer(LINE_LAYER_ID, LINE_SOURCE_ID)
                    .withProperties(
                        lineColor(Color.RED),
                        lineWidth(6f)
                    )
                , VEHICLE_LAYER_ID)

            // Adding the actual SymbolLayer to the map style. An offset is added that the bottom of the red
            // marker icon gets fixed to the coordinate, rather than the middle of the icon being fixed to
            // the coordinate point. This is offset is not always needed and is dependent on the image
            // that you use for the SymbolLayer icon.
            .withLayerAbove(new CircleLayer(ROUTE_STOP_LAYER_ID, ROUTE_STOP_SOURCE_ID)
                .withProperties(
                    circleRadius(4f),
                    circleColor(Color.BLACK)
                ), LINE_LAYER_ID)
        , new Style.OnStyleLoaded() {
          @Override
          public void onStyleLoaded(@NonNull Style style) {
            NextBusIntegrationActivity.this.mapboxMap = mapboxMap;

            restBusClient = new Retrofit.Builder()
                .baseUrl("http://restbus.info/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

            getListOfAllAgencies();
          }
        });
  }

  private void getListOfAllAgencies() {
    restBusService = restBusClient.create(RestBusService.class);
    Call<List<RestBusAgency>> restBusRouteListCall = restBusService.loadListOfAllAgencies();
    restBusRouteListCall.enqueue(new Callback<List<RestBusAgency>>() {
      @Override
      public void onResponse(Call<List<RestBusAgency>> call, Response<List<RestBusAgency>> response) {
        if (response.body() != null) {
          if (response.body().size() > 0) {
            loadSpinnerWithListOfAllAgencies(response.body());
          } else {
            Toast.makeText(NextBusIntegrationActivity.this,
                R.string.nextbus_integration_toast_could_not_load_agency, Toast.LENGTH_SHORT).show();
          }
        }
      }

      @Override
      public void onFailure(Call<List<RestBusAgency>> call, Throwable t) {
        Toast.makeText(NextBusIntegrationActivity.this,
            R.string.nextbus_integration_toast_could_not_load_agency, Toast.LENGTH_SHORT).show();
      }
    });
  }

  private void loadSpinnerWithListOfAllAgencies(List<RestBusAgency> agencyList) {
    Spinner allAgenciesListSpinner = findViewById(R.id.nextbus_all_agencies_list_spinner);
    ArrayList<String> spinnerArray = new ArrayList<>();
    for (RestBusAgency singleAgency : agencyList) {
      spinnerArray.add(singleAgency.getTitle());
    }
    ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<>(this,
        android.R.layout.simple_spinner_dropdown_item, spinnerArray);
    spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    allAgenciesListSpinner.setAdapter(spinnerArrayAdapter);
    allAgenciesListSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (agencyHasBeenSelected) {
          getRouteListForSingleAgency(agencyList.get(position).getTag());
        } else {
          agencyHasBeenSelected = true;
          Toast.makeText(NextBusIntegrationActivity.this,
              R.string.nextbus_integration_toast_select_agency, Toast.LENGTH_SHORT).show();
        }
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {

      }
    });
  }

  private void getRouteListForSingleAgency(String agencyId) {
    restBusSingleAgencyRouteListCall = restBusService.loadRestBusSingleAgencyRouteList(agencyId);
    restBusSingleAgencyRouteListCall.enqueue(new Callback<List<RestBusAgencyRoute>>() {
      @Override
      public void onResponse(Call<List<RestBusAgencyRoute>> call,
                             Response<List<RestBusAgencyRoute>> response) {
        if (response.body() != null) {
          if (response.body().size() > 0) {
            restBusAgencyRouteList = response.body();
            loadSpinnerWithListOfSingleAgencyRoutes(agencyId, response.body());
          } else {
            Toast.makeText(NextBusIntegrationActivity.this,
                R.string.nextbus_integration_toast_no_routes, Toast.LENGTH_SHORT).show();
          }
        }
      }

      @Override
      public void onFailure(Call<List<RestBusAgencyRoute>> call, Throwable t) {

      }
    });
  }

  private void loadSpinnerWithListOfSingleAgencyRoutes(String agencyId,
                                                       List<RestBusAgencyRoute> routeList) {
    Spinner singleAgencyRouteListSpinner = findViewById(R.id.nextbus_single_agency_route_list_spinner);
    ArrayList<String> spinnerArray = new ArrayList<>();
    for (RestBusAgencyRoute singleRoute : routeList) {
      spinnerArray.add(singleRoute.getTitle());
    }
    ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, spinnerArray);
    spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    singleAgencyRouteListSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

        restBusSingleRouteVehicleLocationCall = restBusService.loadRestBusVehicleLocationsForSingleRoute(agencyId, routeList.get(position).getId());
        restBusSingleRouteVehicleLocationCall.enqueue(new Callback<List<RestBusAgencyRouteVehicle>>() {
          @Override
          public void onResponse(Call<List<RestBusAgencyRouteVehicle>> call,
                                 Response<List<RestBusAgencyRouteVehicle>> response) {
            drawRouteLineOnMap(agencyId, routeList.get(position).getId());

            if (response.body() != null) {

              if (response.body().size() > 0) {

                mapboxMap.getStyle(new Style.OnStyleLoaded() {
                  @Override
                  public void onStyleLoaded(@NonNull Style style) {

                    List<Feature> vehicleLocationFeatureList = new ArrayList<>();
                    for (RestBusAgencyRouteVehicle singleVehicle : response.body()) {

                      vehicleLocationFeatureList.add(Feature.fromGeometry(Point.
                          fromLngLat(singleVehicle.getLon(),
                              singleVehicle.getLat())));

                    }

                    GeoJsonSource geoJsonSource = style.getSourceAs(VEHICLE_SOURCE_ID);
                    if (geoJsonSource != null) {
                      geoJsonSource.setGeoJson(FeatureCollection.fromFeatures(vehicleLocationFeatureList));
                    }
                  }
                });

              } else {
                Toast.makeText(NextBusIntegrationActivity.this,
                    R.string.nextbus_integration_toast_no_vehicles, Toast.LENGTH_SHORT).show();
              }
            }
          }

          @Override
          public void onFailure(Call<List<RestBusAgencyRouteVehicle>> call, Throwable t) {

          }
        });



/*
        handler = new Handler();
        runnable = new Runnable() {
          @Override
          public void run() {


            // Schedule the next execution time for this runnable.
//            handler.postDelayed(this, MillisecondsFrequencyToCallNextBusApi);
          }
        };

        // The first time this runs we don't need a delay so we immediately post.
        handler.post(runnable);*/
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {

      }
    });
    singleAgencyRouteListSpinner.setAdapter(spinnerArrayAdapter);
  }

  private void drawRouteLineOnMap(String agencyId, String routeId) {
    restBusSingleRouteStopInfoCall = restBusService.loadRestBusStopLocationsForSingleRoute(agencyId, routeId);
    restBusSingleRouteStopInfoCall.enqueue(new Callback<RestBusRouteStopResponse>() {
      @Override
      public void onResponse(Call<RestBusRouteStopResponse> call,
                             Response<RestBusRouteStopResponse> response) {
        if (response.body() != null) {
          if (response.body().getStops().size() > 0) {
            mapboxMap.getStyle(new Style.OnStyleLoaded() {
              @Override
              public void onStyleLoaded(@NonNull Style style) {
                List<LatLng> latLngListForCameraBounds = new ArrayList<>();

                GeoJsonSource routeLineGeoJsonSource = style.getSourceAs(LINE_SOURCE_ID);

                if (routeLineGeoJsonSource != null) {
                  List<Feature> featureList = new ArrayList<>();

                  List<Point> pointList = new ArrayList<>();

                  for (Stop singleStop : response.body().getStops()) {
                    Point singlePoint = Point.fromLngLat(singleStop.lon, singleStop.lat);
                    pointList.add(singlePoint);
                    featureList.add(Feature.fromGeometry(singlePoint));
                    latLngListForCameraBounds.add(new LatLng(
                        singlePoint.latitude(),
                        singlePoint.longitude()
                    ));
                  }

                  /*// The list of stops from the API sometimes has the same stop at the end
                  // of the list and at the beginning. It should be removed from the end of
                  // the list if this is the case.*/

                  if (pointList.get(pointList.size() - 1).longitude() ==
                      pointList.get(0).longitude()) {
                    pointList.remove(pointList.size() - 1);
                    latLngListForCameraBounds.remove(latLngListForCameraBounds.size() - 1);
                    featureList.remove(featureList.size() - 1);
                  }


                  // Draw the selected route on the map by setting new GeoJSON on the
                  // LineLayer's GeoJsonSource
                  LineString routeLineString = LineString.fromLngLats(pointList);
                  routeLineGeoJsonSource.setGeoJson(routeLineString);

                  GeoJsonSource routeStopGeoJsonSource = style.getSourceAs(ROUTE_STOP_SOURCE_ID);
                  if (routeStopGeoJsonSource != null) {
                    routeStopGeoJsonSource.setGeoJson(FeatureCollection.fromFeatures(featureList));
                  }

                  if (latLngListForCameraBounds.size() > 1) {
                    LatLngBounds latLngBounds = new LatLngBounds.Builder()
                        .includes(latLngListForCameraBounds)
                        .build();

                    // Move the camera to the new route and add padding so that all
                    // stops are visible on the map
                    mapboxMap.animateCamera(CameraUpdateFactory.newLatLngBounds(latLngBounds,
                        65), CAMERA_ANIMATION_SPEED);

                  }
                }
              }
            });

          } else {
            Toast.makeText(NextBusIntegrationActivity.this,
                R.string.nextbus_integration_toast_could_not_draw_route, Toast.LENGTH_SHORT).show();
          }
        }
      }

      @Override
      public void onFailure(Call<RestBusRouteStopResponse> call, Throwable t) {

      }
    });

  }

  /**
   * Retrofit interface for interacting with Restbus, a
   * RESTful JSON API for the NextBus XML feed.
   * <p>
   * More info can be found at http://restbus.info/ and
   * http://www.nextbus.com/xmlFeedDocs/NextBusXMLFeed.pdf
   */
  public interface RestBusService {

    @GET("api/agencies")
    Call<List<RestBusAgency>> loadListOfAllAgencies();

    @GET("api/agencies/{agencyId}/routes")
    Call<List<RestBusAgencyRoute>> loadRestBusSingleAgencyRouteList(@retrofit2.http.Path("agencyId") String agencyId);

    @GET("api/agencies/{agencyTag}/routes/{routeTag}")
    Call<RestBusRouteStopResponse> loadRestBusStopLocationsForSingleRoute(
        @retrofit2.http.Path("agencyTag") String agencyId,
        @retrofit2.http.Path("routeTag") String routeId
    );

    @GET("api/agencies/{agencyTag}/routes/{routeTag}/vehicles")
    Call<List<RestBusAgencyRouteVehicle>> loadRestBusVehicleLocationsForSingleRoute(
        @retrofit2.http.Path("agencyTag") String agencyId,
        @retrofit2.http.Path("routeTag") String routeId
    );
  }

  @Override
  public void onResume() {
    super.onResume();
    mapView.onResume();
    // When the user returns to the activity we want to resume the API calling.
    if (handler != null && runnable != null) {
      handler.post(runnable);
    }
  }

  @Override
  protected void onStart() {
    super.onStart();
    mapView.onStart();
  }

  @Override
  protected void onStop() {
    super.onStop();
    mapView.onStop();
  }


  @Override
  public void onPause() {
    super.onPause();
    mapView.onPause();
    // When the user leaves the activity, there is no need in calling the API since the map
    // isn't in view.
    if (handler != null && runnable != null) {
      handler.removeCallbacks(runnable);
    }
  }

  @Override
  public void onLowMemory() {
    super.onLowMemory();
    mapView.onLowMemory();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    mapView.onDestroy();
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    mapView.onSaveInstanceState(outState);
  }

  //region Classes for Restbus API


  private class From {

    @SerializedName("href")
    @Expose
    private String href;
    @SerializedName("type")
    @Expose
    private String type;
    @SerializedName("rel")
    @Expose
    private String rel;
    @SerializedName("rt")
    @Expose
    private String rt;
    @SerializedName("title")
    @Expose
    private String title;

  }

  private class Links {

    @SerializedName("self")
    @Expose
    private Self self;
    @SerializedName("to")
    @Expose
    private List<To> to = null;
    @SerializedName("from")
    @Expose
    private List<From> from = null;

  }

  private class RestBusAgency {

    @SerializedName("id")
    @Expose
    private String tag;
    @SerializedName("title")
    @Expose
    private String title;
    @SerializedName("region")
    @Expose
    private String region;
    @SerializedName("_links")
    @Expose
    private Links links;


    public String getTag() {
      return tag;
    }

    public String getTitle() {
      return title;
    }
  }

  private class Self {

    @SerializedName("href")
    @Expose
    private String href;
    @SerializedName("type")
    @Expose
    private String type;
    @SerializedName("rel")
    @Expose
    private String rel;
    @SerializedName("rt")
    @Expose
    private String rt;
    @SerializedName("title")
    @Expose
    private String title;

  }

  private class To {

    @SerializedName("href")
    @Expose
    private String href;
    @SerializedName("type")
    @Expose
    private String type;
    @SerializedName("rel")
    @Expose
    private String rel;
    @SerializedName("rt")
    @Expose
    private String rt;
    @SerializedName("title")
    @Expose
    private String title;

  }

  private class RestBusAgencyRoute {

    @SerializedName("id")
    @Expose
    private String id;
    @SerializedName("title")
    @Expose
    private String title;
    @SerializedName("_links")
    @Expose
    private Links links;

    public String getId() {
      return id;
    }

    public String getTitle() {
      return title;
    }
  }

  private class RestBusAgencyRouteVehicle {

    @SerializedName("id")
    @Expose
    private String id;
    @SerializedName("routeId")
    @Expose
    private String routeId;
    @SerializedName("directionId")
    @Expose
    private String directionId;
    @SerializedName("predictable")
    @Expose
    private Boolean predictable;
    @SerializedName("secsSinceReport")
    @Expose
    private Integer secsSinceReport;
    @SerializedName("kph")
    @Expose
    private Integer kph;
    @SerializedName("heading")
    @Expose
    private Integer heading;
    @SerializedName("lat")
    @Expose
    private Double lat;
    @SerializedName("lon")
    @Expose
    private Double lon;
    @SerializedName("leadingVehicleId")
    @Expose
    private Object leadingVehicleId;
    @SerializedName("_links")
    @Expose
    private Links links;

    public Double getLat() {
      return lat;
    }

    public Double getLon() {
      return lon;
    }
  }

  private class Bounds {

    @SerializedName("sw")
    @Expose
    private Sw sw;
    @SerializedName("ne")
    @Expose
    private Ne ne;

  }

  private class Direction {

    @SerializedName("id")
    @Expose
    private String id;
    @SerializedName("title")
    @Expose
    private String title;
    @SerializedName("shortTitle")
    @Expose
    private String shortTitle;
    @SerializedName("useForUi")
    @Expose
    private Boolean useForUi;
    @SerializedName("stops")
    @Expose
    private List<String> stops = null;

  }

  private class Stop {

    @SerializedName("id")
    @Expose
    private String id;
    @SerializedName("code")
    @Expose
    private String code;
    @SerializedName("title")
    @Expose
    private String title;
    @SerializedName("lat")
    @Expose
    private Double lat;
    @SerializedName("lon")
    @Expose
    private Double lon;

  }

  private class Ne {

    @SerializedName("lat")
    @Expose
    private Double lat;
    @SerializedName("lon")
    @Expose
    private Double lon;

  }

  private class RestBusPoint {

    @SerializedName("lat")
    @Expose
    private Double lat;
    @SerializedName("lon")
    @Expose
    private Double lon;

  }

  private class RestBusRouteStopResponse {

    @SerializedName("id")
    @Expose
    private String id;
    @SerializedName("title")
    @Expose
    private String title;
    @SerializedName("shortTitle")
    @Expose
    private Object shortTitle;
    @SerializedName("color")
    @Expose
    private String color;
    @SerializedName("textColor")
    @Expose
    private String textColor;
    @SerializedName("bounds")
    @Expose
    private Bounds bounds;
    @SerializedName("stops")
    @Expose
    private List<Stop> stops = null;
    @SerializedName("directions")
    @Expose
    private List<Direction> directions = null;
    @SerializedName("paths")
    @Expose
    private List<Path> paths = null;
    @SerializedName("_links")
    @Expose
    private Links links;

    public List<Stop> getStops() {
      return stops;
    }
  }

  private class Path {

    @SerializedName("points")
    @Expose
    private List<RestBusPoint> points = null;

  }

  private class Sw {

    @SerializedName("lat")
    @Expose
    private Double lat;
    @SerializedName("lon")
    @Expose
    private Double lon;

  }
  //endregion
}