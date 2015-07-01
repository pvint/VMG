package ca.dotslash.pvint.vmg;

import android.app.ActionBar;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.location.Criteria;
import android.location.Location;

import android.location.LocationManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Vibrator;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.MathContext;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;


public class MainActivity extends ActionBarActivity
        implements OnMapReadyCallback, GoogleMap.OnMapLongClickListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener, LocationSource,
        GoogleMap.OnMyLocationButtonClickListener {

    private final double EARTH_RADIUS = 6371000.0;
    private final String LABEL = "VMG";

    GoogleMap googleMap;
    private Marker destinationMarker;
    Handler mHandler;
    Location curLoc;
    GoogleApiClient mGoogleApiClient;
    private  LocationRequest mLocationRequest;
    private Location location;
    private LocationListener mLocationListener;
    private LocationRequest locationRequest;
    private boolean followme;
    private Vibrator vibrate;

    private int gpsInterval = 10;
    private int gpsFastestInterval = 10;


    private LocationRequest locationrequest;

    private TextView distanceText;
    private TextView bearingText;
    private TextView speedText;
    private TextView vmcText;
    private TextView etaText;
    private TextView headingText;
    private TextView headingErrorText;

    private String velocityUnits;
    private String distanceUnits;

    private Polyline originalVector;
    private Polyline waypointVector;
    private Polyline currentVector;
    private Polyline currentLongVector;
    private Polyline testVector;
    private Circle circleFive;
    private CircleOptions circleOptions;

    private int running;

    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;

    private Map<String, Float> unitFactors;


    // flag that should be set true if handler should stop
    boolean mStopHandler = false;

    @Override
    public void onMapLongClick(LatLng latLng) {

    }

    protected void createLocationRequest() {
        //Log.d(LABEL, "createLocationRequest");
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(gpsInterval * 1000 );
        mLocationRequest.setFastestInterval(gpsFastestInterval * 1000 );
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private float metersToNauticalMiles(float m)
    {
        return m / 1852.0f;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // onCreate gets called when orientation changes!  lazy workaround for the moment...
        //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);



//listener on changed sort order preference:
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        prefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {


                //Log.d("VMG","Settings key changed: " + key + prefs.getString(key, "zz"));
                if (key.equals("gpsIntervalPref"))
                {
                    int i = Integer.parseInt(prefs.getString(key, "10"));
                    if ( i < 1 || i > 9999)
                        return;
                    else
                    {
                        gpsInterval = i;
                        gpsFastestInterval = gpsInterval;
//Log.d("VMG","Interval: " + gpsInterval);
                        mLocationRequest.setInterval(gpsInterval * 1000 );
                        mLocationRequest.setFastestInterval(gpsFastestInterval * 1000 );
                    }
                }

            }
        };
        prefs.registerOnSharedPreferenceChangeListener(prefListener);


        // set to follow location by default
        followme = false;   // Will get set when MyLoc button clicked

        Map<String, String> map = new HashMap<String, String>();
        unitFactors = new HashMap<String, Float>();

        unitFactors.put("NM", 1852.0f);
        unitFactors.put("km", 1000.0f);
        unitFactors.put("m", 1.0f); // seems silly, but makes for easier code below!

        velocityUnits = "kn";   // TODO - need to decide what method to use in handling units
        distanceUnits = "NM";

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        createLocationRequest();

        distanceText =  (TextView) findViewById(R.id.distanceTo);
        distanceText.setText("∞");

        bearingText = (TextView) findViewById(R.id.bearingTo);
        bearingText.setText("--");

        speedText = (TextView) findViewById(R.id.velocityText);
        speedText.setText("--");

        vmcText = (TextView) findViewById(R.id.velocityMadeGoodText);
        vmcText.setText("--");

        etaText = (TextView) findViewById(R.id.etaText);
        etaText.setText("--");

        headingText = (TextView) findViewById(R.id.headingText);
        headingText.setText("--");

        headingErrorText = (TextView) findViewById(R.id.headingOffsetText);
        headingErrorText.setText("--");


        createMapView();

        mGoogleApiClient.connect();




        googleMap.setMyLocationEnabled(true);
        googleMap.setOnMyLocationButtonClickListener(this);

        location = googleMap.getMyLocation();

        if (location != null) {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), 12.0f));
        }

        googleMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
        //googleMap.getUiSettings().setCompassEnabled(true);
        googleMap.getUiSettings().setMyLocationButtonEnabled(true);
        googleMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(LatLng ll) {

                location = googleMap.getMyLocation();

                // if we don't have a location acquired just return
                if (location == null)
                    return;

                if (destinationMarker != null) {
                    destinationMarker.remove();
                }

                destinationMarker = googleMap.addMarker(new MarkerOptions()
                                .position(ll)
                                .title("Destination")
                                .draggable(false)
                                .alpha(0.8f)
                );


                if (originalVector != null)
                    originalVector.remove();

                originalVector = googleMap.addPolyline(new PolylineOptions()
                        .add(ll, new LatLng(location.getLatitude(), location.getLongitude()))
                        .width(1).color(Color.DKGRAY).geodesic(true));

                vibrate = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                // Vibrate for 500 milliseconds
                vibrate.vibrate(30);
                updateWaypointVector(ll);

                displayDistance();

            }
        });

        if (savedInstanceState != null)
        {
            LatLng ll = new LatLng(savedInstanceState.getDouble("destLat"), savedInstanceState.getDouble("destLon"));

            destinationMarker = addMarker(ll);

            originalVector = googleMap.addPolyline(new PolylineOptions()
                        .add(new LatLng(savedInstanceState.getDouble("vectorLat1"), savedInstanceState.getDouble("vectorLon1")),
                                new LatLng(savedInstanceState.getDouble("vectorLat2"), savedInstanceState.getDouble("vectorLon2")))
                        .width(1).color(Color.DKGRAY).geodesic(true));


        }
        //Log.d("VMG","Done onCreate");
    }

    @Override
    public boolean onMyLocationButtonClick()
    {
        followme = !followme;
        return false;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // Adding the pointList arraylist to Bundle
    //    outState.putParcelableArrayList("points", pointList);

        // Saving the bundle
        super.onSaveInstanceState(outState);

        if (destinationMarker != null) {
            outState.putDouble("destLat", destinationMarker.getPosition().latitude);
            outState.putDouble("destLon", destinationMarker.getPosition().longitude);

            // save originalVector
            outState.putDouble("vectorLat1", originalVector.getPoints().get(0).latitude);
            outState.putDouble("vectorLon1", originalVector.getPoints().get(0).longitude);
            outState.putDouble("vectorLat2", originalVector.getPoints().get(1).latitude);
            outState.putDouble("vectorLon2", originalVector.getPoints().get(1).longitude);

        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);


        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        switch (id) {
            case R.id.action_settings:
                Intent i = new Intent(this, PreferencesHelpExample.class);
                startActivity(i);

                return true;
            case R.id.settingsAbout:
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.dotslash.ca/vmg"));
                startActivity(browserIntent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
        //noinspection SimplifiableIfStatement
        /*if (id == R.id.action_settings) {
            return true;
        }*/

        //return super.onOptionsItemSelected(item);
    }




    @Override
    public void onMapReady(GoogleMap map) { // FIXME not working!!!
        /*map.addMarker(new MarkerOptions()
                .position(new LatLng(0, 0))
                .title("Marker"));*/

        Toast.makeText(getApplicationContext(), "MapReady", Toast.LENGTH_LONG).show();
//        this.addMarker();
    }



    /**
     * Initialises the mapview
     */
    private void createMapView(){
        /**
         * Catch the null pointer exception that
         * may be thrown when initialising the map
         */
        try {
            if(null == googleMap){
                googleMap = ((MapFragment) getFragmentManager().findFragmentById(
                        R.id.mapView)).getMap();

                /**
                 * If the map is still null after attempted initialisation,
                 * show an error to the user
                 */
                if(null == googleMap) {
                    Toast.makeText(getApplicationContext(),
                            "Error creating map", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (NullPointerException exception){
            //Log.e("mapApp", exception.toString());
        }
    }

    /**
     * Adds a marker to the map
     */
    private Marker addMarker(LatLng ll)
    {
        if(null == googleMap)
            return null;
        Marker dm;

        dm = googleMap.addMarker(new MarkerOptions()
                        .position(ll)
                        .title("Destination")
                        .draggable(true)
        );

        location = googleMap.getMyLocation();

        if (location == null)
            return dm;

        if (originalVector != null)
            originalVector.remove();

        originalVector = googleMap.addPolyline(new PolylineOptions()
                .add(ll, new LatLng(location.getLatitude(), location.getLongitude()))
                .width(1).color(Color.DKGRAY).geodesic(true));

        vibrate = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        // Vibrate for n milliseconds
        vibrate.vibrate(30);


        return dm;
    }


    private void updateWaypointVector(LatLng l)
    {
        if (waypointVector != null)
            waypointVector.remove();

        waypointVector = googleMap.addPolyline(new PolylineOptions()
                .add(l, new LatLng(location.getLatitude(), location.getLongitude()))
                .width(3).color(Color.BLUE).geodesic(true));
    }

    @Override
    public void onConnected(Bundle bundle)
    {
        location = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);

        // start listening to location updates
        // this is suitable for foreground listening,
        // with the onLocationChanged() invoked for location updates
        if (locationRequest == null) {
            locationRequest = LocationRequest.create()
                    .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
                    .setFastestInterval(5000L)
                    .setInterval(10000L)
                    .setSmallestDisplacement(1.0F);
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, locationRequest, this);
        }

        if (location == null) {
            // Blank for a moment...
        }
        else {
            handleNewLocation(location);
        };
    }

    private void handleNewLocation(Location location) {
        //Log.d(LABEL, "handleNewLocation" + location.toString());
        if (location != null) {
            LatLng myLocation = new LatLng(location.getLatitude(),
                    location.getLongitude());

            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(myLocation,
                    15));
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        //Log.i("VMG", "Location services suspended. Please reconnect.");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }


    @Override
    public void onLocationChanged(Location loc) {

        location = loc;

        if (followme)
        {
            googleMap.animateCamera(CameraUpdateFactory.newLatLng(new LatLng(location.getLatitude(), location.getLongitude())));
        }

        float d = 0;
        float b = 0;
        if (destinationMarker != null) {
            Location l = new Location("marker");
            l.setLatitude(destinationMarker.getPosition().latitude);
            l.setLongitude(destinationMarker.getPosition().longitude);

            d = location.distanceTo(l);
            b = location.bearingTo(l);
            if (b < 0)
                b += 360;

            /*distanceText.setText(Float.toString(d) + " m");
            bearingText.setText(Double.toString(Math.floor((double) b)) + "°");*/
            displayDistance();
        }

        if (location.hasBearing() && location.hasSpeed()) {
            float h; // = location.getBearing();
            float v; // = location.getSpeed();

            if (googleMap.getMyLocation() == null)
                return;

            h = googleMap.getMyLocation().getBearing();
            v = googleMap.getMyLocation().getSpeed();

            float vmc = 0.0f;
            float eta = 0.0f;

            if (destinationMarker != null) {
                vmc = v * (float) Math.cos((double) Math.toRadians(h - b));
                //speedText.setText(Float.toString(v));
                //vmcText.setText(Float.toString(vmc));

                // calc ETA
                if ( d > 0 )
                {
                    eta = d / vmc;
                }
                else
                {
                    eta = 0.0f;
                }
                displayETA( eta );

                if (waypointVector != null)
                    waypointVector.remove();

                waypointVector = googleMap.addPolyline(new PolylineOptions()
                        .add(new LatLng(location.getLatitude(), location.getLongitude()),
                                destinationMarker.getPosition())
                        .width(3).color(Color.BLUE).geodesic(true));

            }
            displaySpeed(v, vmc, h);


            // update vector - show for current heading and 5 minutes out
            // move it ahead 5 minutes
            // using approximation that 111,111m == 1 degree of lon/lat
 /*         // NOTE: This approximation proved to be too inaccurate for my needs
            // Keeping for reference though, as it is a very inexpensive calculation
            float x = v * (float) Math.sin(Math.toRadians((double) h)) * 5 * 60 / 111111;
            float y = v * (float) Math.cos(Math.toRadians((double) h)) * 5 * 60 / 111111;

            // also draw a longer light grey vector... say, 30 minutes out
            float xx = v * (float) Math.sin(Math.toRadians((double) h)) * 30 * 60 / 111111;
            float yy = v * (float) Math.cos(Math.toRadians((double) h)) * 30 * 60 / 111111;*/

            // I'm getting slightly skewed results around Montreal - vector appears about 5 degrees east of real vector
            // trying spherical law of cosines for comparison
            // This method is significantly better

            double lt = Math.toRadians(location.getLatitude());
            double lg = Math.toRadians(location.getLongitude());
            double theta = Math.toRadians((double) h);

            // get the ratio for distance for 5 minutes' and 30 minutes' positions
            double dRatio1 = v * 60 * 5 / EARTH_RADIUS;
            double dRatio2 = v * 60 * 30 / EARTH_RADIUS;

            // φ2 = asin( sin φ1 ⋅ cos δ + cos φ1 ⋅ sin δ ⋅ cos θ )
            // λ2 = λ1 + atan2( sin θ ⋅ sin δ ⋅ cos φ1, cos δ − sin φ1 ⋅ sin φ2 )
            double lat2 = Math.asin( Math.sin(lt) * Math.cos(dRatio1) +
                    Math.cos(lt) * Math.sin(dRatio1) * Math.cos(theta));

            double lon2 = lg + Math.atan2(Math.sin(theta) *
                            Math.sin(dRatio1) * Math.cos(lt),
                            Math.cos(dRatio1) - Math.sin(lt) * Math.sin(lat2));

            /*if (testVector != null)
                testVector.remove();

            testVector = googleMap.addPolyline(new PolylineOptions()
                    .add(new LatLng(location.getLatitude(), location.getLongitude()),
                            new LatLng(Math.toDegrees(lat2), Math.toDegrees(lon2)))
                    .width(3).color(0xffff0000));
*/

            if (currentVector != null)
            {
                currentVector.remove();
            }
            currentVector = googleMap.addPolyline(new PolylineOptions()
                        .add(new LatLng(location.getLatitude(), location.getLongitude())
                                , new LatLng(Math.toDegrees(lat2), Math.toDegrees(lon2)))
                        .width(3).color(Color.GREEN).zIndex(1));

            /*if (circleFive != null)
                circleFive.remove();

            float z = googleMap.getCameraPosition().zoom;

            circleOptions = new CircleOptions()
                    .center(new LatLng(Math.toDegrees(lat2), Math.toDegrees(lon2)))
                    .fillColor(0x000000)
                    .strokeColor(Color.GREEN)
                    .strokeWidth(2)
                    .radius(1.0f/z*1000.0f); // In meters

            // Get back the mutable Circle
            circleFive = googleMap.addCircle(circleOptions);*/

            // calc for 30 minutes out
            lat2 = Math.asin( Math.sin(lt) * Math.cos(dRatio2) +
                    Math.cos(lt) * Math.sin(dRatio2) * Math.cos(theta));

            lon2 = lg + Math.atan2(Math.sin(theta) *
                            Math.sin(dRatio2) * Math.cos(lt),
                    Math.cos(dRatio2) - Math.sin(lt) * Math.sin(lat2));


            if (currentLongVector != null)
            {
                currentLongVector.remove();
            }
            currentLongVector = googleMap.addPolyline(new PolylineOptions()
                    .add(new LatLng(location.getLatitude(), location.getLongitude())
                            , new LatLng(Math.toDegrees(lat2), Math.toDegrees(lon2)))
                    .width(1).color(0xaa444444));



        }


        //Log.d("VMG", Float.toString(d));
    }

    private String secondsToTime( float s )
    {
        // convert seconds to human time, ie: "00:13:46"
        int secs = (int) s;

        int hours = secs / 3600;
        int minutes = secs / 60 % 60;
        int seconds = secs % 60;

        String t = String.format("%d:%02d:%02d", hours, minutes, seconds);

        return t;
    }

    private void displaySpeed(float v, float vmc, float b) {

        switch (velocityUnits)
        {
            case "kn":
                v = metersToNauticalMiles(v) * 3600.0f;
                vmc = metersToNauticalMiles(vmc) * 3600.0f;
                break;

        }

        b = (b < 0) ? b + 360.0f : b;

        speedText.setText(String.format("%.1f %s", v, velocityUnits));
        vmcText.setText(String.format("%.1f %s", vmc, velocityUnits));
        headingText.setText(String.format("%s °", formatToSigFigs(b)));

        if (vmc < 0)
        {
            vmcText.setTextColor(Color.RED);
        }
        else
        {
            vmcText.setTextColor(Color.BLACK);
        }
    }


    private void displayDistance()
    {
        Location l = new Location("marker");
        l.setLatitude(destinationMarker.getPosition().latitude);
        l.setLongitude(destinationMarker.getPosition().longitude);

        float d = location.distanceTo(l);
        float b = location.bearingTo(l);
        b = (b < 0) ? b + 360 : b;

        float he = location.getBearing() - b;
        he = (he <= 180) ? he + 360 : he;
        he = (he > 180) ? he - 360 : he;

        switch (distanceUnits)
        {
            case "NM":
                d = metersToNauticalMiles(d);
                break;
        }

        distanceText.setText(String.format("%s %s", formatToSigFigs(d), distanceUnits));
        bearingText.setText(String.format("%s °", formatToSigFigs(b)));
        headingErrorText.setText(String.format("%s °", formatToSigFigs(he)));

    }

    private void displayETA( float eta )
    {

        if (eta <= 0)
        {
            etaText.setTextColor(Color.RED);
            etaText.setText( "∞" );
        }
        else
        {
            etaText.setTextColor(Color.BLACK);
            etaText.setText( secondsToTime(eta) );
        }
    }

    private String formatToSigFigs(float n)
    {
        return formatToSigFigs(n, 3);
    }

    private String formatToSigFigs(float n, int figs)
    {

        BigDecimal bd = new BigDecimal(n);
        bd = bd.round(new MathContext( figs ));
        String s = bd.toPlainString();

        return s;
    }

    @Override
    public void activate(OnLocationChangedListener onLocationChangedListener) {
        //Log.d("VMG","activate onChangeListener");
    }

    @Override
    public void deactivate() {

    }


}
