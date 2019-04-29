package org.tensorflow.demo;

import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Network;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.NoCache;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class StatisticsActivity extends AppCompatActivity {

  private BottomNavigationView bottomNavigationView;
  private CameraRoomDatabase cameraDb;

  private TextView totalInArea;
  private TextView local7Days;
  private TextView local28Days;
  private TextView addedByUser;
  private TextView globalToday;
  private TextView global28Days;
  private TextView globalTotal;

  private List<HashMap<Date, Integer>> localCamerasPerDays;
  private List<HashMap<Date, Integer>> globalCamerasPerDays;

  private SharedPreferences sharedPreferences;

  private double latMin;
  private double latMax;
  private double lonMin;
  private double lonMax;

  private SynchronizedCameraRepository synchronizedCameraRepository;

  private int globalTodayAmount;
  private int global28DaysAmount;
  private int globalTotalAmount;

  private int local7DaysAmount;
  private int totalLocal28Days;
  private int totalLocal;




  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_statistics);

    sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    synchronizedCameraRepository = new SynchronizedCameraRepository(getApplication());

    totalInArea = findViewById(R.id.total_cameras_statistics);

    local7Days = findViewById(R.id.added_past_7_days_statistics);
    local28Days = findViewById(R.id.added_past_28_days_statistics);
    addedByUser = findViewById(R.id.added_by_user_statistics);

    globalToday = findViewById(R.id.global_today_statistics);
    global28Days = findViewById(R.id.global_28days_statistics);
    globalTotal = findViewById(R.id.global_total_statistics);

    SimpleDateFormat timestampIso8601 = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    timestampIso8601.setTimeZone(TimeZone.getTimeZone("UTC"));


    String homeZone = sharedPreferences.getString("area", null);

    String[] splitBorders = homeZone.split(",");

    latMin = Double.valueOf(splitBorders[0]);
    latMax = Double.valueOf(splitBorders[1]);
    lonMin = Double.valueOf(splitBorders[2]);
    lonMax = Double.valueOf(splitBorders[3]);

    long currentTime = System.currentTimeMillis();
    Date currentDate = new Date(currentTime);

    Calendar cal = Calendar.getInstance();
    cal.setTime(currentDate);

    // cal.add(Calendar.MONTH, -12);

    cal.add(Calendar.DATE, -7);
    Date sevenDaysBeforeToday = cal.getTime();

    // data from -7 days until today
    local7DaysAmount = StatisticsUtils.getTotalCamerasInTimeframeFromDb(sevenDaysBeforeToday, currentDate, synchronizedCameraRepository);

    // only subtract 21 here because we've subtracted 7 earlier
    cal.add(Calendar.DATE, -21);
    Date twentyEightDaysBeforeToday = cal.getTime();

    totalLocal28Days = StatisticsUtils.getTotalCamerasInTimeframeFromDb(twentyEightDaysBeforeToday, currentDate, synchronizedCameraRepository);

    totalLocal = StatisticsUtils.totalCamerasInDb(synchronizedCameraRepository);

    totalInArea.setText(String.valueOf(totalLocal));
    local7Days.setText(String.valueOf(local7DaysAmount));
    local28Days.setText(String.valueOf(totalLocal28Days));


    String baseURL = sharedPreferences.getString("synchronizationURL", null);

    queryServerForStatistics(baseURL, "global", "2018-01-01", "2019-01-01");

    android.support.v7.widget.Toolbar myToolbar = findViewById(R.id.my_toolbar);
    setSupportActionBar(myToolbar);

    bottomNavigationView = findViewById(R.id.navigation);
    bottomNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {

      @Override
      public boolean onNavigationItemSelected(@NonNull MenuItem item) {

        switch (item.getItemId()){

          case R.id.bottom_navigation_history:
            Intent historyIntent = new Intent(StatisticsActivity.this, HistoryActivity.class);
            startActivity(historyIntent);
            return true;

          case R.id.bottom_navigation_camera:
            Intent cameraIntent = new Intent(StatisticsActivity.this, DetectorActivity.class);
            startActivity(cameraIntent);
            return true;

          case R.id.bottom_navigation_map:
            Intent mapIntent = new Intent(StatisticsActivity.this, MapActivity.class);
            startActivity(mapIntent);
            return true;

          case R.id.bottom_navigation_stats:
            Intent statsIntent = new Intent(StatisticsActivity.this, StatisticsActivity.class);
            startActivity(statsIntent);
            return true;

        }
        return false;
      }
    });

    bottomNavigationView.getMenu().findItem(R.id.bottom_navigation_stats).setChecked(true);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    //return super.onCreateOptionsMenu(menu);
    getMenuInflater().inflate(R.menu.actionbar, menu);
    return true;
  }



  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {

      case R.id.action_settings:
        Intent settingsIntent = new Intent(StatisticsActivity.this, SettingsActivity.class);
        startActivity(settingsIntent);

        return true;

      default:
        // Fall back on standard behaviour when user choice not recognized.
        return super.onOptionsItemSelected(item);
    }
  }



  // Helper methods for querying database.
  private List<SurveillanceCamera> getCamerasInTimeframe(String sqlliteTimeStartpoint, String sqlliteTimeEndpoint) {
    return cameraDb.surveillanceCameraDao().getCamerasAddedInTimeframe(sqlliteTimeStartpoint, sqlliteTimeEndpoint);
  }

  private int getTotalCamerasUpTo(String sqliteTime) {
    return cameraDb.surveillanceCameraDao().getTotalCamerasUpTo(sqliteTime);
  }


  void queryServerForStatistics(String baseURL, String area, String startDate, String endDate){

    final String TAG = "StatisticsUtils";
    //TODO check api for negative values in left right top bottom see if still correct

    RequestQueue mRequestQueue;

    // Set up the network to use HttpURLConnection as the HTTP client.
    Network network = new BasicNetwork(new HurlStack());

    // Instantiate the RequestQueue with the cache and network.
    mRequestQueue = new RequestQueue(new NoCache(), network);

    // Start the queue
    mRequestQueue.start();

    String url = baseURL + "statistics/?area=" + area;

    if (startDate != null){
     url += "&start=" + startDate;
    }

    if (endDate != null){
      url += "&end=" + endDate;
    }

    JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONObject>() {
              @Override
              public void onResponse(JSONObject response) {

                JSONObject JSONFromServer;

                try {

                  JSONFromServer = response.getJSONObject("global");

                  globalTodayAmount = JSONFromServer.getInt("global_today");
                  global28DaysAmount = JSONFromServer.getInt("global_28days");
                  globalTotalAmount = JSONFromServer.getInt("global_total");



                } catch (Exception e) {
                  Log.i(TAG, "onResponse: " + e.toString());

                }

              }
            }, new Response.ErrorListener() {
      @Override
      public void onErrorResponse(VolleyError error) {
        // TODO: Handle Errors
      }
    }
    );

    jsonObjectRequest.setRetryPolicy(new DefaultRetryPolicy(
            30000,
            0,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
    ));

    mRequestQueue.add(jsonObjectRequest);

    mRequestQueue.addRequestFinishedListener(new RequestQueue.RequestFinishedListener<Object>() {

      @Override
      public void onRequestFinished(Request<Object> request) {

        redrawGlobalTextViews();

      }
    });

  }

  /**
   * updates all Textviews which rely on outside data
   */

  void redrawGlobalTextViews(){

    globalToday.setText(String.valueOf(globalTodayAmount));
    global28Days.setText(String.valueOf(global28DaysAmount));
    globalTotal.setText(String.valueOf(globalTotalAmount));

  }
}