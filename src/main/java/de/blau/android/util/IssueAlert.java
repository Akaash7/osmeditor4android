package de.blau.android.util;

import java.util.ArrayList;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import de.blau.android.App;
import de.blau.android.Logic;
import de.blau.android.Main;
import de.blau.android.R;
import de.blau.android.exception.OsmException;
import de.blau.android.osm.BoundingBox;
import de.blau.android.osm.Node;
import de.blau.android.osm.OsmElement;
import de.blau.android.osm.Relation;
import de.blau.android.osm.ViewBox;
import de.blau.android.osm.Way;
import de.blau.android.prefs.Preferences;
import de.blau.android.tasks.Note;
import de.blau.android.tasks.Task;
import de.blau.android.validation.Validator;

/**
 * Generate an Android notification for OSM elements that have an issue and for Notes and other QA "bugs"
 * 
 * @author simon
 *
 */
public class IssueAlert {
    static final String DEBUG_TAG = "IssueAlert";

    private static final String GROUP_DATA   = "Data";
    private static final String GROUP_NOTES  = "Notes";
    private static final String GROUP_OSMOSE = "Osmose";

    private static final int[] bearings = { R.string.bearing_ne, R.string.bearing_e, R.string.bearing_se, R.string.bearing_s, R.string.bearing_sw,
            R.string.bearing_w, R.string.bearing_nw, R.string.bearing_n };

    /**
     * Private constructor to avoid instantiation of the class
     */
    private IssueAlert() {
    }

    /**
     * Generate an alert/notification if something is problematic about the OSM object
     * 
     * @param context Android Context
     * @param e OsmElement we are generating an alert for
     */
    public static void alert(@NonNull Context context, @NonNull OsmElement e) {

        Preferences prefs = new Preferences(context);

        if (!prefs.generateAlerts()) { // don't generate alerts
            return;
        }

        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        Location location = null;
        try {
            location = locationManager.getLastKnownLocation("gps");
        } catch (SecurityException sex) {
            // can be safely ignored
        }
        double eLon;
        double eLat;
        switch (e.getName()) {
        case Node.NAME:
            eLon = ((Node) e).getLon() / 1E7D;
            eLat = ((Node) e).getLat() / 1E7D;
            break;
        case Way.NAME:
            double[] result = Logic.centroidLonLat((Way) e);
            if (result == null) {
                Log.d(DEBUG_TAG, "couldn't determine center for " + e);
                return;
            }
            eLon = result[0];
            eLat = result[1];
            break;
        case Relation.NAME:
            ViewBox box = new ViewBox(e.getBounds());
            result = box.getCenter();
            eLon = result[0];
            eLat = result[1];
            break;
        default:
            Log.e(DEBUG_TAG, "unknown element type " + e);
            return;
        }
        String title = context.getString(R.string.alert_data_issue);
        String ticker = title;
        StringBuilder message = new StringBuilder("");
        if (location != null) {
            // if we know where we are we can provide better information
            long distance = 0;
            if ("node".equals(e.getName())) {
                distance = Math.round(GeoMath.haversineDistance(location.getLongitude(), location.getLatitude(), eLon, eLat));
            } else if ("way".equals(e.getName())) {
                ClosestPoint cp = getClosestDistance(location.getLongitude(), location.getLatitude(), (Way) e);
                distance = Math.round(cp.distance);
                eLon = cp.lon;
                eLat = cp.lat;
            }
            // filter
            if (distance > prefs.getMaxAlertDistance()) {
                return;
            }
            long bearing = GeoMath.bearing(location.getLongitude(), location.getLatitude(), eLon, eLat);

            int index = (int) (bearing - 22.5);
            if (index < 0)
                index += 360;
            index = index / 45;

            message.append(context.getString(R.string.alert_distance_direction, distance, context.getString(bearings[index])) + "\n");
            ticker = ticker + " " + message;
        }
        Validator validator = App.getDefaultValidator(context);
        for (String p : validator.describeProblem(context, e)) {
            message.append(p);
        }
        NotificationCompat.Builder mBuilder;
        try {
            mBuilder = new NotificationCompat.Builder(context).setSmallIcon(R.drawable.logo_simplified).setContentTitle(title)
                    .setContentText(message.toString()).setPriority(NotificationCompat.PRIORITY_HIGH).setTicker(ticker).setAutoCancel(true)
                    .setGroup(GROUP_DATA);
            mBuilder.setColor(ContextCompat.getColor(context, R.color.osm_green));
        } catch (RuntimeException re) {
            // NotificationCompat.Builder seems to be flaky instead of crashing we produce a
            // crash dump and return
            ACRAHelper.nocrashReport(re, re.getMessage());
            return;
        }
        Intent resultIntent = new Intent(Intent.ACTION_VIEW);
        try {
            BoundingBox box = GeoMath.createBoundingBoxForCoordinates(eLat, eLon, prefs.getDownloadRadius(), true);

            Uri rc = Uri.parse("http://127.0.0.1:8111/load_and_zoom?left=" + box.getLeft() / 1E7D + "&right=" + box.getRight() / 1E7D + "&top="
                    + box.getTop() / 1E7D + "&bottom=" + box.getBottom() / 1E7D + "&select=" + e.getName() + e.getOsmId()); // NOSONAR
                                                                                                                            // JOSM
                                                                                                                            // RC
                                                                                                                            // assumes
                                                                                                                            // localhost

            Log.d(DEBUG_TAG, rc.toString());
            resultIntent.setData(rc);
            TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
            // Adds the back stack for the Intent (but not the Intent itself)
            stackBuilder.addParentStack(Main.class);
            // Adds the Intent that starts the Activity to the top of the stack
            stackBuilder.addNextIntent(resultIntent);
            PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
            mBuilder.setContentIntent(resultPendingIntent);

            NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            // mId allows you to update the notification later on.
            mNotificationManager.notify(id(e), mBuilder.build());
            App.getOsmDataNotifications(context).save(mNotificationManager, id(e));

        } catch (OsmException e1) {
            Log.d(DEBUG_TAG, "Illegal BB created from lat " + eLat + " lon " + eLon + " r " + prefs.getDownloadRadius());
        }
    }

    /**
     * Generate an unique id for an OsmElement
     * 
     * @param e the OsmElement
     * @return an unique id
     */
    private static int id(@NonNull OsmElement e) {
        return (e.getName() + e.getOsmId()).hashCode();
    }

    /**
     * Cancel a notification for a specific OsmElement
     * 
     * @param context Android Context
     * @param e the OsmElement
     */
    public static void cancel(@NonNull Context context, @NonNull OsmElement e) {
        NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        App.getOsmDataNotifications(context).remove(mNotificationManager, id(e));
    }

    /**
     * Generate an alert/notification if we found a task object nearby.
     * 
     * Will not generate an alert if the corresponding preference is not set
     * 
     * @param context Android Context
     * @param b the Task
     */
    public static void alert(@NonNull Context context, @NonNull Task b) {
        Log.d(DEBUG_TAG, "generating alert for " + b.getDescription());
        Preferences prefs = new Preferences(context);

        if (!prefs.generateAlerts()) { // don't generate alerts
            return;
        }
        alert(context, prefs, b);
    }

    /**
     * Generate an alert/notification if we found a task object nearby.
     * 
     * Always generates an alert regardless of the preference setting
     * 
     * @param context Android Context
     * @param prefs a Preference instance
     * @param b the Task
     */
    public static void alert(@NonNull Context context, @NonNull Preferences prefs, @NonNull Task b) {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        Location location = null;
        try {
            location = locationManager.getLastKnownLocation("gps");
        } catch (SecurityException sex) {
            // can be safely ignored
        }
        double eLon = b.getLon() / 1E7D;
        double eLat = b.getLat() / 1E7D;

        String title = b instanceof Note ? context.getString(R.string.alert_note) : context.getString(R.string.alert_bug);
        String ticker = title;
        String message = "";
        if (location != null) {
            // if we know where we are we can provide better information
            long distance = Math.round(GeoMath.haversineDistance(location.getLongitude(), location.getLatitude(), eLon, eLat));

            // filter
            if (distance > Math.sqrt(8D * prefs.getBugDownloadRadius() * prefs.getBugDownloadRadius())) {
                // diagonal of auto download box
                return;
            }

            long bearing = GeoMath.bearing(location.getLongitude(), location.getLatitude(), eLon, eLat);

            int index = (int) (bearing - 22.5);
            if (index < 0)
                index += 360;
            index = index / 45;

            message = context.getString(R.string.alert_distance_direction, distance, context.getString(bearings[index])) + "\n";
            ticker = ticker + " " + message;
        }
        message = message + b.getDescription();
        NotificationCompat.Builder mBuilder;
        try {
            mBuilder = new NotificationCompat.Builder(context).setSmallIcon(R.drawable.logo_simplified).setContentTitle(title).setContentText(message)
                    .setPriority(NotificationCompat.PRIORITY_HIGH).setTicker(ticker).setAutoCancel(true)
                    .setGroup(b instanceof Note ? GROUP_NOTES : GROUP_OSMOSE);
            mBuilder.setColor(ContextCompat.getColor(context, R.color.osm_green));
        } catch (RuntimeException re) {
            // NotificationCompat.Builder seems to be flaky instead of crashing we produce a
            // crash dump and return
            ACRAHelper.nocrashReport(re, re.getMessage());
            return;
        }
        Intent resultIntent = new Intent(Intent.ACTION_VIEW);
        Uri geo = Uri.fromParts("geo", eLat + "," + eLon, null);
        resultIntent.setData(geo);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        // Adds the back stack for the Intent (but not the Intent itself)
        stackBuilder.addParentStack(Main.class);
        // Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder.setContentIntent(resultPendingIntent);

        NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        // mId allows you to update the notification later on.
        int id = id(b);
        mNotificationManager.notify(id, mBuilder.build());
        App.getTaskNotifications(context).save(mNotificationManager, id);
    }

    /**
     * Generate an unique id for a Task
     * 
     * @param b the Task
     * @return an unique id
     */
    private static int id(@NonNull Task b) {
        return (b.getClass().getSimpleName() + b.getId()).hashCode();
    }

    /**
     * Cancel a notification for a specific task
     * 
     * @param context Android Context, if null we do nothing
     * @param b Task we want to remove the notification for
     */
    public static void cancel(@Nullable Context context, @NonNull Task b) {
        if (context != null) {
            NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            App.getTaskNotifications(context).remove(mNotificationManager, id(b)); // cancels and removes from cache
        }
    }

    static class ClosestPoint {
        double distance = Double.MAX_VALUE;
        double lat;
        double lon;
    }

    /**
     * Get the closest distance from the coordinates to a way
     * 
     * @param lon WGS84 longitude
     * @param lat WGS84 latitude
     * @param w the Way
     * @return a ClosestPoint instance
     */
    @NonNull
    private static ClosestPoint getClosestDistance(double lon, double lat, @NonNull Way w) {
        ClosestPoint closest = new IssueAlert.ClosestPoint();

        double ny = GeoMath.latToMercator(lat);
        double nx = lon;

        ArrayList<Node> nodes = new ArrayList<>(w.getNodes());
        for (int i = 0; i <= nodes.size() - 2; i++) {
            double bx = nodes.get(i).getLon() / 1E7D;
            double by = GeoMath.latE7ToMercator(nodes.get(i).getLat());
            double ax = nodes.get(i + 1).getLon() / 1E7D;
            double ay = GeoMath.latE7ToMercator(nodes.get(i + 1).getLat());
            float[] newClosest = GeoMath.closestPoint((float) nx, (float) ny, (float) bx, (float) by, (float) ax, (float) ay);
            double newDistance = GeoMath.haversineDistance(nx, ny, newClosest[0], newClosest[1]);
            if (newDistance < closest.distance) {
                closest.distance = newDistance;
                closest.lon = newClosest[0];
                closest.lat = GeoMath.mercatorToLat(newClosest[1]);
            }
        }
        return closest;
    }
}
