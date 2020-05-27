package com.neosensory.whatsup;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.VectorDrawable;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.neosensory.n2yo.N2YO;
import com.neosensory.neosensoryblessed.NeoBuzzPsychophysics;
import com.neosensory.neosensoryblessed.NeosensoryBlessed;
import com.neosensory.tlepredictionengine.TlePredictionEngine;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;
import java.util.concurrent.Executor;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {
  private static final int ACCESS_LOCATION_REQUEST = 2;
  private static final long UPDATE_INTERVAL_IN_MILLISECONDS =
      1000; // how often we should update the user's location
  private static final long UPDATE_FASTEST_INTERVAL_IN_MILLISECONDS =
      UPDATE_INTERVAL_IN_MILLISECONDS / 2;
  private static final int SEARCHDEGREES = 30; // 0 = straight up, 90 = horizon
  private static final double MAXSURFACEDISTANCETOUSER = 300; // km -- used for visibility on map
  private static final double MAXALTITUDE = 6000; // km
  private static final int MAXSATELLITES =
      20; // the maximum number of satellites to track at any given point in time. Need to be
  // careful of n2yo.com API limits when setting this
  private static final int REFRESHLOCATIONPERIOD =
      50; // how many ms until we try to update all the tracked satellite locations
  private static final long REFRESHSATELLITESPERIOD =
      120000; // how many milliseconds we wait until polling n2yo.com for the list of nearby
  // satellites. Need to be careful of the n2yo.com API limits when setting this
  private static final int NUMMOTORS = 4; // assume this is for Neosensory Buzz

  private NeosensoryBlessed
      blessedNeo; // instance of the Neosensory Android SDK to help connect to Buzz
  private GoogleMap mMap; // GoogleMap instance
  private N2YO n2yo; // n2yo.com instance used for obtaining latest satellite data
  private FusedLocationProviderClient
      fusedLocationClient; // instance used for getting user location
  private LocationRequest locationRequest;
  private LocationCallback locationCallback;

  // State tracking
  private Boolean locationEstablished = false;
  private Boolean initalCameraSet = false;
  private Boolean mapReady = false;
  private Boolean needWhatsUp = true;
  private Boolean authorizedCLI = false;
  private Boolean disconnectRequested = false;
  private Boolean exitThreadLoop = false;
  private Date timeOfLastWhatsUp;

  private static double userLatitude = 0;
  private static double userLongitude = 0;
  private static double userBearing = 0;
  private static double userAltitude = 0;
  private Marker userMarker;

  // use a hashtable because we need to synchronize the entire set
  Hashtable<Integer, Satellite> nearbySatellites = new Hashtable<Integer, Satellite>();

  // Thread Processing
  ThreadExecutor satThreadProcessor;

  private static int[] motorActivationFrame;
  private Boolean processedAllSatelliteUpdates = false;

  ////////////////////////////////////////////
  // Startup actions                        //
  ///////////////////////////////////////////

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // set the view for this app
    setContentView(R.layout.activity_maps);
    // set up access to n2yo.com for pulling current satellite data
    n2yo = new N2YO(getResources().getString(R.string.n2yo_api_key), this);
    // request Bluetooth be turned on from the Neosensory Android SDK
    NeosensoryBlessed.requestBluetoothOn(this);
    // create a thread executor for launching threads
    satThreadProcessor = new ThreadExecutor();
    // initialize our motor activations for a Buzz wristband
    motorActivationFrame = new int[NUMMOTORS];

    // If we already have all the needed permissions, launch all of our initializations. Otherwise,
    // we'll call this from our callback once permissions have been obtained.
    if (hasPermissions()) {
      initializations();
    }

    // boot up Google Maps
    SupportMapFragment mapFragment =
        (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
    mapFragment.getMapAsync(this);

    // set the time for
    timeOfLastWhatsUp = Calendar.getInstance().getTime();

    // lauch the main processing Thread for the app
    mainThreadLogic();
  }

  // When the Google Map is ready, create the initial marker for the user's location, but don't
  // display it until we acquire it
  @Override
  public void onMapReady(GoogleMap googleMap) {
    mapReady = true;
    mMap = googleMap;
    mMap.setMinZoomPreference(6);
    mMap.setMaxZoomPreference(10);
    userMarker =
        mMap.addMarker(
            new MarkerOptions().position(new LatLng(userLatitude, userLongitude)).title("Me"));
    userMarker.setFlat(true);
    userMarker.setVisible(false);
    Toast.makeText(
            this,
            "Getting location and gathering satellite data. This may take 20-30 seconds.",
            Toast.LENGTH_LONG)
        .show();
  }

  ////////////////////
  // Main App Logic //
  ///////////////////

  private void mainThreadLogic() {
    final Handler handler = new Handler();
    Runnable runnable =
        new Runnable() {
          @Override
          public void run() {
            while (!exitThreadLoop) {
              // Non-UI calls must go here
              Date now = Calendar.getInstance().getTime();
              // If we haven't yet made the n2yo.com call to obtain nearby satellites, call it
              if (needWhatsUp && locationEstablished) {
                n2yo.getWhatsUp(
                    (float) userLatitude,
                    (float) userLongitude,
                    (float) userAltitude,
                    SEARCHDEGREES,
                    0);
                needWhatsUp = false;
                timeOfLastWhatsUp = now;
              } else {
                // otherwise, if we have made the call to obtain nearby satellites, wait for
                // REFRESHSATELLITESPERIOD to call it again
                if ((now.getTime() - timeOfLastWhatsUp.getTime()) > REFRESHSATELLITESPERIOD) {
                  needWhatsUp = true;
                }
              }
              // If our hashtable contains satellites, process them and decide how we should vibrate
              if (!nearbySatellites.isEmpty()) {
                motorActivationFrame = new int[4];
                double nearestSatelliteDistance = MAXSURFACEDISTANCETOUSER * 4;
                Object[] tempSatArray = nearbySatellites.values().toArray();
                Satellite nearestSatellite = (Satellite) tempSatArray[0];
                for (int i = 0; i < tempSatArray.length; i++) {
                  Satellite satellite = (Satellite) tempSatArray[i];
                  double distanceToUser = updateSatelliteState(satellite);
                  if ((distanceToUser < nearestSatelliteDistance)&&(satellite.getTleUsedforLocation())) {
                    nearestSatelliteDistance = distanceToUser;
                    nearestSatellite = satellite;
                  }
                }
                motorActivationFrame = getSatelliteVibration(nearestSatellite);
                Log.i("Activations on process", Arrays.toString(motorActivationFrame));
                if (authorizedCLI) {
                  blessedNeo.vibrateMotors(motorActivationFrame);
                }
              }

              // Sleep on the thread for REFRESHLOCATIONPERIOD
              try {
                Thread.sleep(REFRESHLOCATIONPERIOD);
              } catch (InterruptedException e) {
                e.printStackTrace();
              }

              handler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      // UI calls MUST go here
                      // if our map is ready and we know the user location, update the user position
                      // on the map
                      if (mapReady && locationEstablished) {
                        userMarker.setPosition(new LatLng(userLatitude, userLongitude));
                        userMarker.setRotation((float) userBearing);
                        userMarker.setVisible(true);
                        if (!initalCameraSet) {
                          // only center the camera over the user once so the user can pan around
                          // without being overridden
                          mMap.moveCamera(
                              CameraUpdateFactory.newLatLng(
                                  new LatLng(userLatitude, userLongitude)));
                          initalCameraSet = true;
                        }
                        // Process satellite UI if they exist in our hashtable
                        if (!nearbySatellites.isEmpty()) {
                          Object[] tempSatArray = nearbySatellites.values().toArray();
                          for (int i = 0; i < tempSatArray.length; i++) {
                            updateSatelliteUI((Satellite) tempSatArray[i]);
                          }
                        }
                      }
                    }
                  });
            }

            if (disconnectRequested&&authorizedCLI) {
              blessedNeo.stopMotors();
              blessedNeo.resumeDeviceAlgorithm();
              // When disconnecting: it is possible for the device to process the disconnection request
              // prior to processing the request to resume the onboard algorithm, which causes the last
              // sent motor command to "stick"
              try {
                Thread.sleep(200);
              } catch (InterruptedException e) {
                e.printStackTrace();
              }
              blessedNeo.disconnectNeoDevice();
              disconnectRequested = false;
            }

          }
        };
    new Thread(runnable).start();
  }

  //////////////////////////////////////////////////////
  // Method for updating an individual satellite's UI //
  //////////////////////////////////////////////////////

  private void updateSatelliteUI(Satellite satellite) {
    // If the satellite doesn't yet have an assigned Google Maps marker and
    // we're not trying to delete the satellite, give it a marker
    if (((!satellite.getHasMarker()) && (!satellite.getReadyToRemoveSatellite()))
        && (satellite.getTleUsedforLocation())) {

      // See method getSatelliteIcon below for available icon types
      int iconCode = 0;
      if(satellite.getNoradID()==25544){
        iconCode = 2; // special icon for ISS
      }

      // also scale the icon size to altitude (smaller == higher)
      Marker satMarker =
          mMap.addMarker(
              new MarkerOptions()
                  .position(new LatLng(satellite.getLla()[0], satellite.getLla()[1]))
                  .title(satellite.getName())
                  .snippet(satellite.getIntlDesignator())
                  .icon(
                      getSatelliteIcon(
                          (int)
                              (200
                                  * (1
                                      - Utilities.getLinearMap(
                                          (float) satellite.getLla()[2],
                                          0,
                                          (float) MAXALTITUDE,
                                          0,
                                          0.75f,
                                          true))),iconCode)));
      satMarker.setFlat(true);
      satMarker.setVisible(true);
      satellite.setSatelliteMarker(satMarker);
    }
    // otherwise if the satellite has a marker, update its
    // position/transparency if we're not trying to remove the satellite
    if (satellite.getHasMarker()) {
      if ((satellite.getOutsideUserRange()) && (!satellite.getHeadedToUser())) {
        satellite.removeMarker();
      } else {
        satellite.updateMarkerPosition();
        satellite.getSatelliteMarker().setAlpha(satellite.getAlphaDistanceToUser());
      }
    }
  }

  ///////////////////////////////////////////////////////
  // Method for obtaining a satellite's motor encoding //
  ///////////////////////////////////////////////////////

  private int[] getSatelliteVibration(Satellite satellite) {
    int[] activation = new int[NUMMOTORS];
    // If we have a bearing and a TLE was used to update the satellite position, build up the
    // vibration for the satellite
    if (satellite.getUserBearingSet() && satellite.getTleUsedforLocation()) {
      // Build up the vibration for this satellite. Build up a quasi-illusion that
      // takes the takes the max actuator values found while looping through all of
      // the satellites.

      // here we map inverse distance from [0 1] where 1 is closes to the user and 0 is furthest,
      // as we want stronger vibrations for nearer to user
      float satelliteDistance = (float) satellite.getDistanceToUser();
      float linearDistance = 0;
      if (satelliteDistance <= MAXSURFACEDISTANCETOUSER) {
        linearDistance = 1 - (satelliteDistance / (float) MAXSURFACEDISTANCETOUSER);
        if (linearDistance > 1) {
          linearDistance = 1;
        }
      }

      // obtain an illusion-based encoding from the Neosensory SDK (see SDK JavaDocs)
      activation =
          NeoBuzzPsychophysics.GetIllusionActivations(
              linearDistance, (float) satellite.getUserBearing() / 360);
    }
    return activation;
  }

  /////////////////////////////////////////////////////////
  // Method for updating an individual satellite's state //
  /////////////////////////////////////////////////////////

  private double updateSatelliteState(Satellite satellite) {
    // If the satellite has a TLE, update it's position, distance to user, and bearing from user
    if (satellite.getHasTle()) {
      String[] tle = satellite.getTles();
      double[] latLonAlt = TlePredictionEngine.getSatellitePosition(tle[0], tle[1], true);
      satellite.setLla(latLonAlt[0], latLonAlt[1], latLonAlt[2]);
      double distanceToUser =
          Utilities.getDistance(userLatitude, latLonAlt[0], userLongitude, latLonAlt[1]);
      double bearing =
          Utilities.getBearing(userLatitude, latLonAlt[0], userLongitude, latLonAlt[1]);
      satellite.setUserBearing(bearing);

      // If the distance is beyond our threshold see whether or not we should remove it
      if (distanceToUser > MAXSURFACEDISTANCETOUSER) {
        // If a we're ready to remove the satellite (i.e. it's marker has been cleared off a UI
        // thread), we can try to remove the satellite from our HashTable
        if (satellite.getReadyToRemoveSatellite()) {
          // pop off satellites that are headed away from user and further than our
          // distance. We need to make sure the marker is removed first
          try {
            // if this doesn't work, we'll try to get rid of this on another
            // iteration
            nearbySatellites.remove(satellite.getNoradID());
          } catch (Exception e) {
            e.printStackTrace();
          }
          Log.i(
              "Hashtable", "Entry removed. Current satellites tracked: " + nearbySatellites.size());
        } else {
          // if we're not ready to remove the satellite, but it's outside our threshold range,
          // check to see if the satellite is headed towards the user
          if (!satellite.getHeadedToUser()) {
            // if the satellite is headed away from user signal that we need to remove the marker
            // from the UI thread
            satellite.setOutsideUserRange(true);
          }
          // else, continue to track the satellite since it's moving towards the user
        }
      }

      // update the satellite's calculated distance to the user
      satellite.setDistanceToUser(distanceToUser, MAXSURFACEDISTANCETOUSER);
    }
    return satellite.getDistanceToUser();
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // n2yo.com threaded response processing (may not actually be needed to run these on threads) //
  // Process "What's Up" response from n2yo.com                                                 //
  ////////////////////////////////////////////////////////////////////////////////////////////////

  private void processN2yoWhatsUp(JSONObject n2yoResponse) throws JSONException {
    int numSatellites = n2yoResponse.getJSONObject("info").getInt("satcount");
    JSONArray satelliteArray = n2yoResponse.getJSONArray("above");
    // launch a thread to process each of the satellites in the response
    for (int i = 0; i < numSatellites; i++) {
      JSONObject satInfo = satelliteArray.getJSONObject(i);
      AddSatelliteRunnable addSatellite = new AddSatelliteRunnable(satInfo);
      satThreadProcessor.execute(addSatellite);
    }
  }

  // a runnable for launching a thread to process an individual satellite
  public class AddSatelliteRunnable implements Runnable {
    private JSONObject satelliteInfo;

    public AddSatelliteRunnable(JSONObject satelliteInfo_) {
      this.satelliteInfo = satelliteInfo_;
    }

    @Override
    public void run() {
      int noradID = 0;
      String satName = null;
      String satIntlDesignator = null;
      double satLatitude = 0;
      double satLongitude = 0;
      double satAltitude = 0;
      double distanceToUser = 5000000;
      try {
        noradID = satelliteInfo.getInt("satid");
        satName = satelliteInfo.getString("satname");
        satIntlDesignator = satelliteInfo.getString("intDesignator");
        satLatitude = satelliteInfo.getDouble("satlat");
        satLongitude = satelliteInfo.getDouble("satlng");
        satAltitude = satelliteInfo.getDouble("satalt");
        distanceToUser =
            Utilities.getDistance(userLatitude, satLatitude, userLongitude, satLongitude);
        // only add satellites that are far away, but incoming so they don't just "pop" onto the
        // display when we refresh
        if ((satAltitude < MAXALTITUDE)
            && (nearbySatellites.size() <= MAXSATELLITES)
            && (!nearbySatellites.containsKey(noradID))
            && (distanceToUser >= MAXSURFACEDISTANCETOUSER)) {
          Log.i(
              "Hashtable", "Entry added . Current satellites tracked: " + nearbySatellites.size());
          nearbySatellites.put(
              noradID,
              new Satellite(
                  noradID, satIntlDesignator, satName, satLatitude, satLongitude, satAltitude));
          n2yo.getTle(noradID);
        }

      } catch (JSONException e) {
        e.printStackTrace();
      }
    }
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // n2yo.com threaded response processing (may not actually be needed to run these on threads) //
  // Process get TLE responses from n2yo.com                                                    //
  ////////////////////////////////////////////////////////////////////////////////////////////////

  private void processN2yoTle(JSONObject n2yoResponse) throws JSONException {
    addTleRunnable addTle = new addTleRunnable(n2yoResponse);
    satThreadProcessor.execute(addTle);
  }

  public class addTleRunnable implements Runnable {
    private JSONObject tleInfo;

    public addTleRunnable(JSONObject tleInfo_) {
      this.tleInfo = tleInfo_;
    }

    @Override
    public void run() {
      int noradID = -1;
      String tle = null;
      try {
        noradID = tleInfo.getJSONObject("info").getInt("satid");
        tle = tleInfo.getString("tle");
        String[] tleLines = tle.split("\r\n", 0);
        if (nearbySatellites.containsKey(noradID)) {
          nearbySatellites.get(noradID).setTles(tleLines[0], tleLines[1]);
        }
      } catch (JSONException e) {
        e.printStackTrace();
      }
    }
  }

  //////////////////////////////////////////////////////////////
  // Callbacks for processing Bluetooth and Internet Requests //
  //////////////////////////////////////////////////////////////

  // Handle processing JSON responses from our n2yo.com requests
  private final BroadcastReceiver n2yoReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          JSONObject receivedJSON = null;
          Bundle bundle = intent.getExtras();
          N2YO.CallId requestType = (N2YO.CallId) bundle.getSerializable("requestType");
          try {
            receivedJSON = new JSONObject((String) bundle.getString("responseObject"));
            if (requestType == N2YO.CallId.WHATSUP) {
              processN2yoWhatsUp(receivedJSON);
            } else if (requestType == N2YO.CallId.TLE) {
              processN2yoTle(receivedJSON);
            } else {
            }
          } catch (JSONException e) {
            e.printStackTrace();
          }
          String receivedJSONString = receivedJSON.toString();
          Log.i(
              "N2YO", "API Call Type: " + requestType.name() + " Response: " + receivedJSONString);
        }
      };

  // here we just check to see if a CLI is ready/available from a connected Neosensory Buzz
  // See the SDK and example app for other possible messages
  // https://github.com/neosensory/neosensory-sdk-for-android-java
  private final BroadcastReceiver CliReadyReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          if (intent.hasExtra("com.neosensory.neosensoryblessed.CliReadiness")) {
            // Check the message from NeosensoryBlessed to see if a Neosensory Command Line
            // Interface
            // has become ready to accept commands
            // Prior to calling other API commands we need to accept the Neosensory API ToS
            if (intent.getBooleanExtra("com.neosensory.neosensoryblessed.CliReadiness", false)) {
              blessedNeo.sendDeveloperAPIAuth();
              // sendDeveloperAPIAuth() will then transmit a message back requiring an explicit
              // acceptance of Neosensory's Terms of Service located at
              // https://neosensory.com/legal/dev-terms-service/
              blessedNeo.acceptApiTerms();
              blessedNeo.pauseDeviceAlgorithm();
              authorizedCLI = true;
            }
          }
        }
      };

  /////////////////////
  // Initializations //
  /////////////////////

  // This is the bulk of our initializations to be called *after* we've obtained all the necessary
  // permissions to do so
  private void initializations() {
    // obtain an instance of the Neosensory Android SDK to help facilitate API usage
    blessedNeo =
        NeosensoryBlessed.getInstance(getApplicationContext(), new String[] {"Buzz"}, true);

    // register the BroadcastReceiver for processing updates from the Neosensory SDK
    registerReceiver(CliReadyReceiver, new IntentFilter("BlessedBroadcast"));
    // register the BroadcastReceiver for processing updates from our n2yo.com library
    registerReceiver(n2yoReceiver, new IntentFilter("gotResponse"));

    // setup obtaining user location
    fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
    locationRequest = LocationRequest.create();
    locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
    locationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
    locationRequest.setFastestInterval(UPDATE_FASTEST_INTERVAL_IN_MILLISECONDS);

    // upon location request callback, update the user's latitude/longitude/altitude
    locationCallback =
        new LocationCallback() {
          @Override
          public void onLocationResult(LocationResult locationResult) {
            if (locationResult == null) {
              return;
            }
            for (Location location : locationResult.getLocations()) {
              if (location != null) {
                userLatitude = location.getLatitude();
                userLongitude = location.getLongitude();
                userAltitude = location.getAltitude();
                if (!locationEstablished) {
                  locationEstablished = true;
                }
              }
            }
          }
        };
    fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
  }

  // Check to see if we have the needed permissions. If we don't, request them
  private boolean hasPermissions() {
    if (getApplicationContext().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        || getApplicationContext().checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(
          new String[] {
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
          },
          ACCESS_LOCATION_REQUEST);
      return false;
    }
    return true;
  }

  // If we didn't initially have permissions as checked in onCreate, we can call for our bulk
  // initializations here after the permissions have been obtained
  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    if ((requestCode == ACCESS_LOCATION_REQUEST)
        && (grantResults.length > 0)
        && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
      initializations();
    } else {
      Toast.makeText(
              this,
              "Unable to obtain location permissions, which are required to use Bluetooth.",
              Toast.LENGTH_LONG)
          .show();
      super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
  }

  // Setup the ThreadExecutor for scheduling/executing threads
  public class ThreadExecutor implements Executor {
    public void execute(Runnable r) {
      new Thread(r).start();
    }
  }

  ///////////////
  // Utilities //
  ///////////////

  // return a BitmapDescriptor which is needed for setting a custom vector-based icon for a Google
  // Map icon
  // from https://gist.github.com/Ozius/1ef2151908c701854736
  private BitmapDescriptor getSatelliteIcon(int size, int type) {
// icon types:
    // 0: satellite
    // 1: space junk/debris
    // 2: ISS
    // 3: tracked/targeted satellite
    VectorDrawable vectorDrawable;
    switch(type){
      case 0: vectorDrawable = (VectorDrawable) getDrawable(R.drawable.ic_fp_satellite_icon);
      break;
      case 1: vectorDrawable = (VectorDrawable) getDrawable(R.drawable.garbage1);
        break;
      case 2: vectorDrawable = (VectorDrawable) getDrawable(R.drawable.iss);
        break;
      case 3: vectorDrawable = (VectorDrawable) getDrawable(R.drawable.ic_fp_satellite_icon);
      vectorDrawable.setTint(Color.argb(255, 255, 0, 0));
        break;
      default: vectorDrawable = (VectorDrawable) getDrawable(R.drawable.ic_fp_satellite_icon);
    }


    assert vectorDrawable != null;
    int h = size;
    int w = size;

    vectorDrawable.setBounds(0, 0, w, h);

    Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bm);
    vectorDrawable.draw(canvas);

    return BitmapDescriptorFactory.fromBitmap(bm);
  }

  /////////////
  // Cleanup //
  /////////////

  @Override
  protected void onDestroy() {
    super.onDestroy();
    unregisterReceiver(n2yoReceiver);
    unregisterReceiver(CliReadyReceiver);
    fusedLocationClient.removeLocationUpdates(locationCallback);
    disconnectRequested = true;
    exitThreadLoop = true;
    if (authorizedCLI) {
      blessedNeo.stopMotors();
      blessedNeo.resumeDeviceAlgorithm();
    }
  }
}
