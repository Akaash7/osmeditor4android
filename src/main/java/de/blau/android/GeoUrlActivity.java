package de.blau.android;

import java.io.Serializable;
import java.util.Locale;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import de.blau.android.util.GeoMath;

/**
 * Start vespucci with geo: URLs. see http://www.ietf.org/rfc/rfc5870.txt
 */
public class GeoUrlActivity extends Activity {

    private static final String DEBUG_TAG = "GeoUrlActivity";
    public static final String  GEODATA   = "de.blau.android.GeoUrlActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Uri data = getIntent().getData();
        if (data == null) {
            Log.d(DEBUG_TAG, "Called with null data, aborting");
            finish();
            return;
        }
        Log.d("GeoURLActivity", data.toString());
        Intent intent = new Intent(this, Main.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        String[] query = data.getSchemeSpecificPart().split("[\\?\\&]"); // used by osmand likely not standard conform
        if (query != null && query.length >= 1) {
            GeoUrlData geoData = null;
            String[] params = query[0].split(";");
            if (params != null && params.length >= 1) {
                String[] coords = params[0].split(",");
                boolean wgs84 = true; // for now the only supported datum
                if (params.length > 1) {
                    for (String p : params) {
                        if (p.toLowerCase(Locale.US).matches("crs=.*")) {
                            wgs84 = p.toLowerCase(Locale.US).matches("crs=wgs84");
                            Log.d(DEBUG_TAG, "crs found " + p + ", is wgs84 is " + wgs84);
                        }
                    }
                }
                if (coords != null && coords.length >= 2 && wgs84) {
                    try {
                        double lat = Double.parseDouble(coords[0]);
                        double lon = Double.parseDouble(coords[1]);
                        if (lon >= -180 && lon <= 180 && lat >= -GeoMath.MAX_LAT && lat <= GeoMath.MAX_LAT) {
                            geoData = new GeoUrlData();
                            geoData.setLat(lat);
                            geoData.setLon(lon);
                            intent.putExtra(GEODATA, geoData);
                        }
                    } catch (NumberFormatException e) {
                        Log.d(DEBUG_TAG, "Coordinates " + coords[0] + "/" + coords[1] + " not parseable");
                    }
                }
            }
            if (geoData != null && query.length > 1) {
                for (int i = 1; i < query.length; i++) {
                    params = query[i].split("=",2);
                    if (params.length == 2 && "z".equals(params[0])) {
                        geoData.setZoom(Integer.parseInt(params[1]));
                    }
                }
            }
        }
        startActivity(intent);
        finish();
    }

    public static class GeoUrlData implements Serializable {
        private static final long serialVersionUID = 3L;
        private double            lat;
        private double            lon;
        private int               zoom = -1;

        /**
         * @return the lat
         */
        public double getLat() {
            return lat;
        }
        
        /**
         * @return latitude in WGS*1E7 coords
         */
        public int getLatE7() {
            return (int) (lat*1E7D);
        }

        /**
         * @param lat the lat to set
         */
        public void setLat(double lat) {
            this.lat = lat;
        }

        /**
         * @return the lon
         */
        public double getLon() {
            return lon;
        }
        
        /**
         * @return longitude in WGS*1E7 coords
         */
        public int getLonE7() {
            return (int) (lon*1E7D);
        }

        /**
         * @param lon the lon to set
         */
        public void setLon(double lon) {
            this.lon = lon;
        }

        /**
         * @return the zoom
         */
        public int getZoom() {
            return zoom;
        }

        /**
         * @param zoom the zoom to set
         */
        public void setZoom(int zoom) {
            this.zoom = zoom;
        }
        
        /**
         * Check if we have a valid zoom value
         * 
         * @return true if zoom is present
         */
        public boolean hasZoom() {
            return zoom >= 0; 
        }
        
        @Override
        public String toString() {
            return "geo:" + lat + "," + lon + (hasZoom() ? "?z=" + zoom : "");
        }
    }
}
