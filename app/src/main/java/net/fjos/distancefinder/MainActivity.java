package net.fjos.distancefinder;


import android.content.Context;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.os.StrictMode;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;


import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.annotations.Polyline;
import com.mapbox.mapboxsdk.annotations.PolylineOptions;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.constants.Style;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.services.Constants;
import com.mapbox.services.commons.ServicesException;
import com.mapbox.services.commons.geojson.LineString;
import com.mapbox.services.commons.models.Position;
import com.mapbox.services.directions.v4.DirectionsCriteria;
import com.mapbox.services.directions.v4.MapboxDirections;
import com.mapbox.services.directions.v4.models.Waypoint;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import retrofit2.Response;


public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
    private String navnFra;
    private String navnTil;
    private Marker tilmark;
    private Marker framark;

    private EditText toEditText;
    private EditText fromEditText;
    private Button calcButton;
    private static int distance;
    private int distanceTime;
    private Polyline lagredeMarkeringer;

    //Access token for mapbox, denne må være unik per utvikler
    private final static String MAPBOX_ACCESS_TOKEN = "pk.eyJ1Ijoia2ltczg5IiwiYSI6ImNpbmsxMHQyZjAwNjV3ZGtsZ3QwdjhiYXoifQ.gEIdNEA5hWpZ7-umrC-gMw";

    private MapView mapView = null;

    private Response<com.mapbox.services.directions.v4.models.DirectionsResponse> response;
    private com.mapbox.services.directions.v4.models.DirectionsRoute route;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Tillatter internett-aksess
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        //setter kartet (Mapbox)
        mapView = (MapView) findViewById(R.id.mapview);
        mapView.setAccessToken(MAPBOX_ACCESS_TOKEN);
        mapView.setStyleUrl(Style.MAPBOX_STREETS);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        //setter editexter og knapp
        toEditText = (EditText)findViewById(R.id.to);
        fromEditText = (EditText)findViewById(R.id.from);
        calcButton = (Button)findViewById(R.id.calcButton);
    }



    //når kartet er klar så skjer følgende...
    public void onMapReady(final MapboxMap mapboxMap) {

        calcButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                //først hentes innskreven tekst fra edittext
                String tilText = toEditText.getText().toString().trim();
                String fraText = fromEditText.getText().toString().trim();

                //hvis tiltext-edittexten ikke inneholder noe, blir en toastmelding vist
                if(tilText.length() == 0){
                    Toast.makeText(getApplicationContext(), "Til-stedet mangler", Toast.LENGTH_LONG).show();
                }
                //hvis fratext-edittexten ikke inneholder noe, blir en toastmelding vist
                else if(fraText.length() == 0){
                    Toast.makeText(getApplicationContext(), "Fra-stedet mangler", Toast.LENGTH_LONG).show();
                }
                //ellers fortsetter det
                else{
                    //denne sørger for at tastaturety går ned
                    InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);

                    //settes navn gjennom geocoding-metoden (metoden henter ut plassnavn fra teksten brukeren la inn)
                    navnFra= Geocoding(MainActivity.this,fromEditText.getText().toString());
                    navnTil= Geocoding(MainActivity.this,toEditText.getText().toString());

                    //deretter hentet latitude og longitude fra navnet gjennom metoden reverseGeonaming.
                    LatLng Fra=reverseGeocoding(MainActivity.this,fromEditText.getText().toString());
                    LatLng Til=reverseGeocoding(MainActivity.this,toEditText.getText().toString());

                    Waypoint fra = null;
                    Waypoint til = null;

                    //Denne sjekker integriteten av det Geocoding ha gjort.
                    // Hvis en av fra/til resultatene returnerer null, er det feil og det kommer ut en toastmelding som sier at den ikke finne det
                    if (Fra == null) {
                        Toast.makeText(getApplicationContext(), "Finner ikke fra-stedet, prøv et annet", Toast.LENGTH_LONG).show();

                    }
                    else if (Til == null) {
                        Toast.makeText(getApplicationContext(), "Finner ikke til-stedet, prøv et annet", Toast.LENGTH_LONG).show();

                    }
                    else{

                        //til og fra lokasjon settes inn i en Waypoint-objekt
                        til = new Waypoint(Til.getLongitude(), Til.getLatitude());
                        fra = new Waypoint(Fra.getLongitude(), Fra.getLatitude());

                        //distansen finnes og kartet zoomer til aktuell plass via findDistance-metoden
                        findDistance(mapboxMap,fra,til);
                        //Når utregningene er ferdig i findDistanse-metoden er ferdig, går den videre å skriver ut meldingen
                        String msg = "Distansen fra "+navnFra+" til "+navnTil+" er "+findDistanceFormat(distance)+" \n "
                                +"Tid å kjøre tar cirka "+findTimeFormat(distanceTime);
                        Snackbar.make(v,msg,Snackbar.LENGTH_INDEFINITE).show();
                    }
                }

            }
        });
    }

    public static LatLng reverseGeocoding(Context context, String locationName){
        //I reverseGeocoding finner vi stedskoordinater ut i fra sted som brukeren har flylt inn.
        Geocoder geoCoder = new Geocoder(context, Locale.getDefault());

        try {
            //Her hentes lokasjonen ut fra geocoder, deretter returner metoden stedskoordinater.
            List<Address> addresses = geoCoder.getFromLocationName(locationName, 1);
            return new LatLng(addresses.get(0).getLatitude(), addresses.get(0).getLongitude());


        } catch (Exception e) {
            //om ikke så vil returner den en toast-melding
            Toast.makeText(context, "Fant ikke stedet", Toast.LENGTH_LONG).show();
        }
        return null;
    }
    public static String findTimeFormat(int tidsdistanse){
        //Denne omgjør setter sekund/minutt/time, helt ut i fra hvor langt unna det er og hvor lang tid det tar å kjøre

        //setter minutt med en gang. dette ved å dele på 60 (sek)
        int totalTid = tidsdistanse/60;
        String maalVerdi;
        //hvis det er mer enn 60 min deles totaltiden på 60 og legges på "time" i maalVerdi, og hvis det er flere
        //timer blir maalVerdi heller satt til timeR
        if(totalTid>=60){
            totalTid=totalTid/60;

            if (totalTid ==1){
                maalVerdi = "time";
            }
            else{
                maalVerdi = "timer";
            }
        }

        //Hvis det er mindre enn ett minutt, settes Målverdi til sekunder, har valgt å ikke ta høyde for at det kan bli sekund da det ikke virker nødvendig.
        else if(totalTid<=1){
            totalTid = tidsdistanse;
            maalVerdi = "sekunder";
        }
        //Hvis ingen av de aktuelle blir valgt, kommer vi ned til minutter, der er prinsippet det samme som under time. totaltiden settes til minutt og målverdi settes til minutt
        //eller minutter uavhengig om hvor mange det er
        else{
            if (totalTid ==1){
                maalVerdi = "minutt";
            }
            else{
                maalVerdi = "minutter";
            }
        }
        //metoden returner dette.
        return totalTid+" "+maalVerdi;
    }

    public static String findDistanceFormat(int distansen){
        //har laget en tilsvarende metode som findTimeFormat, bare for avstand.

        //her settes distansen til km
        Double totalDistanse= Double.valueOf(distansen/1000);
        String maalVerdi;

        //hvis total km er mer enn 10, settes mil
        if(totalDistanse>=10){
            totalDistanse=totalDistanse/10;
            maalVerdi = "mil";
        }
        //her sjekkes det om den er mindre enn 1 km. Da vil den settes til meter
        else if(totalDistanse<=1){
            totalDistanse= Double.valueOf(distance);
            maalVerdi = "meter";
        }
        //
        else {
            maalVerdi="km";
        }
        return totalDistanse+" "+maalVerdi;

    }

    public static String Geocoding(Context context, String locationName){
        //tilsvarende den andre Geocoding-metoden. Bortsett fra at denne
        //finner stedet ut i fra stedet som brukeren oppgittet.
        //denne ble laget for å forsikre seg om integriteten av det brukeren har skrevet inn
        Geocoder geoCoder = new Geocoder(context, Locale.getDefault());

        try {
            List<Address> addresses = geoCoder.getFromLocationName(locationName, 1);
            return new String(addresses.get(0).getFeatureName());


        } catch (Exception e) {
            Toast.makeText(context, "Fant ikke stedet", Toast.LENGTH_LONG).show();
        }
        return null;
    }

    public void findDistance(MapboxMap mapboxMap,Waypoint fra,Waypoint til){

        //Denne metoden finner distansen og tidsforbruk gjennom API til Mapbox
        //Den skriver også linjen mellom plassene.
        MapboxDirections client = null;
        try {
            //først blir blir det satt opp en mapboxdirections, her legges "fra" og "til" lokasjonene til
            client = new MapboxDirections.Builder()
                    .setAccessToken(MAPBOX_ACCESS_TOKEN)
                    .setOrigin(fra)
                    .setDestination(til)
                    .setProfile(DirectionsCriteria.PROFILE_DRIVING)
                    .build();
        } catch (ServicesException e) {
            e.printStackTrace();
        }

        //lokasjonene blir delt i to på denne måten for å finne midten mellom de to destinasjonene.
        //Dette gjøres for å sette "kameraet" mellom de to lokasjonene.
        Double avrgLat = (fra.getLatitude()+til.getLatitude())/2;

        Double avrgLong = (fra.getLongitude()+til.getLongitude())/2;


        try {
            //Så blir directions kjørt, deretter blir distansen skrevet ut.
            response = client.executeCall();
            route = response.body().getRoutes().get(0);
            distance = route.getDistance();
            distanceTime = route.getDuration();

        }

        catch (IOException e) {
            e.printStackTrace();
        }

        //her tegnes linjene mellom lokasjonene opp
        LineString lineString = route.asLineString(Constants.OSRM_PRECISION_V4);
        List<Position> coordinates = lineString.getCoordinates();
        LatLng[] points = new LatLng[coordinates.size()];
        for (int i = 0; i < coordinates.size(); i++) {
            points[i] = new LatLng(
                    coordinates.get(i).getLatitude(),
                    coordinates.get(i).getLongitude());
        }

        PolylineOptions Markeringer = new PolylineOptions().add(points)
                .color(Color.parseColor("#cc3b60a7"))
                .width(5);

        //her settes "marker" på fra og til lokasjonene. (de røde "pilene")
        MarkerOptions fraMarkering = new MarkerOptions()
                .position(new LatLng(fra.getLatitude(), fra.getLongitude()))
                .title("Fra")
                .snippet(navnFra);

        MarkerOptions tilMarkering = new MarkerOptions()
                .position(new LatLng(til.getLatitude(), til.getLongitude()))
                .title("Til")
                .snippet(navnTil);


        //Hvis det finnes markeringer blir blir disse fjernet.
        if (lagredeMarkeringer != null) {
            mapboxMap.removeAnnotations();
            mapboxMap.removeMarker(framark);
            mapboxMap.removeMarker(tilmark);
        }
        //Deretter settes marker
        framark = mapboxMap.addMarker(fraMarkering);
        tilmark = mapboxMap.addMarker(tilMarkering);
        lagredeMarkeringer = mapboxMap.addPolyline(Markeringer);

        //Så settes kameraposisjonen mellom lokasjonene (utregningen fra lengere opp)
        CameraPosition posisjon = new CameraPosition.Builder()
                        .target(new LatLng(avrgLat,avrgLong))
                        .zoom(3)  //Denne setter zoom til 3, hadde tenkt at denen også skulle endres etter hvor langt det ble mellom plassene. Men det gikk ikke
                        .tilt(2)
                        .build();

        //denne animerer kameraet, slik at det mykt beveger seg over kartet
        mapboxMap.animateCamera(CameraUpdateFactory
                .newCameraPosition(posisjon), 4000);
    }



    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
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
}
