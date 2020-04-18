package org.unsurv;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import androidx.appcompat.app.AppCompatActivity;

import android.view.MenuItem;
import android.view.View;

import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.api.IMapController;
import org.osmdroid.events.DelayedMapListener;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.OverlayManager;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * Allows the user to review and edit SurveillanceCamera captures, launched if user clicks on a
 * non training SurveillanceCamera in HistoryActivity
 */

public class OrganizeActivity extends AppCompatActivity {

  BottomNavigationView bottomNavigationView;

  SharedPreferences sharedPreferences;

  MapView map;
  IMapController mapController;
  OverlayManager overlayManager;

  GeoPoint centerMap;
  double standardZoom;

  List<Polyline> lines = new ArrayList<>();

  EditText centerLat;
  EditText centerLon;
  EditText gridLength;
  EditText gridHeight;
  EditText gridRows;
  EditText gridColumns;

  Button resetButton;
  Button drawButton;

  Context context;
  Resources resources;


  @Override
  protected void onResume() {

    BottomNavigationBadgeHelper.setBadgesFromSharedPreferences(bottomNavigationView, context);

    String oldLat = sharedPreferences.getString("gridCenterLat", "");
    String oldLon = sharedPreferences.getString("gridCenterLon", "");
    String oldZoom = sharedPreferences.getString("gridZoom", "");

    if (!oldLat.isEmpty() && !oldLon.isEmpty() && !oldZoom.isEmpty()) {
      centerMap = new GeoPoint(Double.parseDouble(oldLat), Double.parseDouble(oldLon));
      mapController.setZoom(Double.parseDouble(oldZoom));
      mapController.setCenter(centerMap);
    }


    super.onResume();
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {

    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_organize);

    context = this;
    resources = context.getResources();

    sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

    centerLat = findViewById(R.id.organize_center_lat_edit);
    centerLon = findViewById(R.id.organize_center_lon_edit);

    // default grid is 1000m x 1000m, 5 rows 5 columns
    gridLength = findViewById(R.id.organize_length_edit);
    gridLength.setText("3000");

    gridHeight = findViewById(R.id.organize_height_edit);
    gridHeight.setText("3000");

    gridRows = findViewById(R.id.organize_rows_edit);
    gridRows.setText("5");

    gridColumns = findViewById(R.id.organize_columns_edit);
    gridColumns.setText("5");

    resetButton = findViewById(R.id.organize_reset);
    drawButton = findViewById(R.id.organize_draw);


    map = findViewById(R.id.organize_camera_map);

    mapController = map.getController();
    overlayManager = map.getOverlayManager();

    CopyrightOverlay copyrightOverlay = new CopyrightOverlay(context);
    overlayManager.add(copyrightOverlay);

    map.setTilesScaledToDpi(true);
    map.setClickable(false);
    map.setMultiTouchControls(true);

    // MAPNIK fix
    // Configuration.getInstance().setUserAgentValue("github-unsurv-unsurv-android");
    // TODO add choice + backup strategy here
    map.setTileSource(TileSourceFactory.OpenTopo);

    // remove big + and - buttons at the bottom of the map
    final CustomZoomButtonsController zoomController = map.getZoomController();
    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER);


    standardZoom = 4.0;
    sharedPreferences.edit().putString("gridZoom", String.valueOf(standardZoom)).apply();

    // Setting starting position and zoom level.
    centerMap = new GeoPoint(50.972, 10.107);
    mapController.setZoom(standardZoom);
    mapController.setCenter(centerMap);

    // refresh values after 200ms delay
    map.addMapListener(new DelayedMapListener(new MapListener() {
      @Override
      public boolean onScroll(ScrollEvent event) {
        IGeoPoint centerAfterScroll = map.getMapCenter();

        centerMap = new GeoPoint(centerAfterScroll);
        sharedPreferences.edit().putString("gridCenterLat", String.valueOf(centerMap.getLatitude())).apply();
        sharedPreferences.edit().putString("gridCenterLon", String.valueOf(centerMap.getLongitude())).apply();


        centerLat.setText(String.valueOf(centerAfterScroll.getLatitude()));
        centerLon.setText(String.valueOf(centerAfterScroll.getLongitude()));

        return false;

      }

      @Override
      public boolean onZoom(ZoomEvent event) {

        sharedPreferences.edit().putString("gridZoom", String.valueOf(map.getZoomLevelDouble())).apply();


        return false;

      }
    }, 200));



    resetButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {


        new AlertDialog.Builder(context)
                .setTitle("Clear Data?")
                .setMessage("Do you want to clear this data?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(DialogInterface dialogInterface, int i) {

                    sharedPreferences.edit().remove("gridCenterLat").apply();
                    sharedPreferences.edit().remove("gridCenterLon").apply();
                    sharedPreferences.edit().remove("gridZoom").apply();

                    deleteGrid();

                    centerMap = new GeoPoint(50.972, 10.107);
                    mapController.setZoom(standardZoom);
                    mapController.setCenter(centerMap);
                    redrawMap();

                    Toast.makeText(context, "Successfully cleared data.", Toast.LENGTH_LONG).show();
                  }
                })
                .setNegativeButton("No", null)
                .show();

      }
    });

    drawButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {

        int length = Integer.parseInt(gridLength.getText().toString());
        int height = Integer.parseInt(gridHeight.getText().toString());

        int rows = Integer.parseInt(gridRows.getText().toString());
        int columns = Integer.parseInt(gridColumns.getText().toString());

        deleteGrid();
        drawGrid(centerMap.getLatitude(), centerMap.getLongitude(), length, height, rows, columns);


      }
    });


    // TODO listview with different images, save abort buttons, editable mapview, change upload date with + /- buttons

    bottomNavigationView = findViewById(R.id.navigation);
    bottomNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {

      @Override
      public boolean onNavigationItemSelected(@NonNull MenuItem item) {

        switch (item.getItemId()) {

          case R.id.bottom_navigation_history:
            Intent historyIntent = new Intent(OrganizeActivity.this, HistoryActivity.class);
            startActivity(historyIntent);
            return true;

          case R.id.bottom_navigation_camera:
            if (sharedPreferences.getBoolean("alwaysEnableManualCapture", false)) {
              Intent manualCaptureIntent = new Intent(OrganizeActivity.this, ManualCaptureActivity.class);
              startActivity(manualCaptureIntent);
              return true;
            } else {
              Intent cameraIntent = new Intent(OrganizeActivity.this, DetectorActivity.class);
              startActivity(cameraIntent);
              return true;

            }

          case R.id.bottom_navigation_map:
            Intent mapIntent = new Intent(OrganizeActivity.this, MapActivity.class);
            startActivity(mapIntent);
            return true;

          case R.id.bottom_navigation_stats:
            Intent statsIntent = new Intent(OrganizeActivity.this, StatisticsActivity.class);
            startActivity(statsIntent);
            return true;

        }

        return false;

      }
    });

    bottomNavigationView.getMenu().findItem(R.id.bottom_navigation_history).setChecked(false);
    bottomNavigationView.getMenu().findItem(R.id.bottom_navigation_camera).setChecked(false);
    bottomNavigationView.getMenu().findItem(R.id.bottom_navigation_map).setChecked(false);
    bottomNavigationView.getMenu().findItem(R.id.bottom_navigation_stats).setChecked(false);

  }


  void drawGrid(double centerLat, double centerLon, int length, int height, int rows, int columns){

    // outer rectangle of grid

    // left edge is center lon - length/2
    GeoPoint topLeft = new GeoPoint(LocationUtils.getNewLocation(
            centerLat, centerLon, height/2d, -length/2d));

    GeoPoint topRight = new GeoPoint(LocationUtils.getNewLocation(
            centerLat, centerLon, height/2d, length/2d));

    GeoPoint bottomRight = new GeoPoint(LocationUtils.getNewLocation(
            centerLat, centerLon, -height/2d, length/2d));

    GeoPoint bottomLeft = new GeoPoint(LocationUtils.getNewLocation(
            centerLat, centerLon, -height/2d, -length/2d));

    List<GeoPoint> outerRect = new ArrayList<>(Arrays.asList(topLeft, topRight, bottomRight, bottomLeft, topLeft));

    drawLine(outerRect);

    // stepsize of dividing lines
    int partHeight = height / rows;
    int partLength = length / columns;

    GeoPoint tmpRowStartpoint;
    GeoPoint tmpRowEndpoint;

    // one "step" south from topleft, that's the top point of the first divider line for rows
    tmpRowStartpoint = new GeoPoint(LocationUtils.getNewLocation(
            topLeft.getLatitude(), topLeft.getLongitude(), -partHeight, 0));

    // rows , ex: we need 4 lines to divide into 5 parts
    for (int i = 0; i < rows - 1; i++) {

      tmpRowEndpoint = new GeoPoint(LocationUtils.getNewLocation(
              tmpRowStartpoint.getLatitude(), tmpRowStartpoint.getLongitude(), 0, length));

      List<GeoPoint> gridLinePoints = new ArrayList<>(Arrays.asList(tmpRowStartpoint, tmpRowEndpoint));

      drawLine(gridLinePoints);

      // new startpoint is one "step" south of previous startpoint
      tmpRowStartpoint = new GeoPoint(LocationUtils.getNewLocation(
              tmpRowStartpoint.getLatitude(), tmpRowStartpoint.getLongitude(), -partHeight, 0));

    }

    GeoPoint tmpColStartpoint;
    GeoPoint tmpColEndpoint;

    tmpColStartpoint = new GeoPoint(LocationUtils.getNewLocation(
            topLeft.getLatitude(), topLeft.getLongitude(), 0, partLength));

    // columns
    for (int j = 0; j < columns - 1; j++) {

      tmpColEndpoint = new GeoPoint(LocationUtils.getNewLocation(
              tmpColStartpoint.getLatitude(), tmpColStartpoint.getLongitude(), -height, 0));

      List<GeoPoint> gridLinePoints = new ArrayList<>(Arrays.asList(tmpColStartpoint, tmpColEndpoint));

      drawLine(gridLinePoints);

      // new startpoint is one "step" east of previous startpoint
      tmpColStartpoint = new GeoPoint(LocationUtils.getNewLocation(
              tmpColStartpoint.getLatitude(), tmpColStartpoint.getLongitude(), 0, partLength));

    }


  }

  void drawLine(List<GeoPoint> geoPoints){

    Polyline polyline = new Polyline();

    polyline.setPoints(geoPoints);

    int hotPink = Color.argb(127, 255, 0, 255);

    polyline.setColor(hotPink);

    lines.add(polyline);

    overlayManager.add(polyline);

    redrawMap();

  }

  void redrawMap() {
    map.invalidate();
  }

  void deleteGrid() {
    overlayManager.removeAll(lines);
    redrawMap();
  }

}