package de.blau.android.tasks;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

import android.content.Context;
import android.util.Log;
import de.blau.android.App;
import de.blau.android.osm.BoundingBox;
import de.blau.android.prefs.Preferences;
import de.blau.android.tasks.Task.State;

class OsmoseServer {
	
	private static final String DEBUG_TAG = OsmoseServer.class.getSimpleName();
	
	private final static String apiPath = "/api/0.2/";
	/** 
	 * the list of supported languages was simply generated from the list of .po in the osmose repo and tested against the API
	 */
	private final static List<String> supportedLanguages = Arrays.asList("ca", "cs", "en", "da", "de", "el", "es", "fr", "hu", "it", "ja", "lt", "nl", "pl", "pt", "ro", "ru", "sw", "uk");
	
	/**
	 * Timeout for connections in milliseconds.
	 */
	private static final int TIMEOUT = 45 * 1000;
	
	/**
	 * Perform an HTTP request to download up to limit bugs inside the specified area.
	 * Blocks until the request is complete.
	 * 
	 * @param context the Android context
	 * @param area Latitude/longitude *1E7 of area to download.
	 * @return All the bugs in the given area.
	 */
	public static Collection<OsmoseBug> getBugsForBox(Context context, BoundingBox area, long limit) {
		Collection<OsmoseBug> result = null;
		// http://osmose.openstreetmap.fr/de/api/0.2/errors?bbox=8.32,47.33,8.42,47.28&full=true
		try {
			Log.d(DEBUG_TAG, "getBugssForBox");
			URL url;

			url = new URL(getServerURL(context)  + "errors?" +
					"bbox=" +
					area.getLeft() / 1E7d +
					"," + area.getBottom() / 1E7d +
					"," + area.getRight() / 1E7d +
					"," + area.getTop() / 1E7d +
					"&full=true");
			Log.d(DEBUG_TAG, "query: " + url.toString());
			
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			boolean isServerGzipEnabled = false;

			//--Start: header not yet sent
			con.setReadTimeout(TIMEOUT);
			con.setConnectTimeout(TIMEOUT);
			con.setRequestProperty("Accept-Encoding", "gzip");
			con.setRequestProperty("User-Agent", App.userAgent);

			//--Start: got response header
			isServerGzipEnabled = "gzip".equals(con.getHeaderField("Content-encoding"));
			
			if (con.getResponseCode() != HttpURLConnection.HTTP_OK) {
				return new ArrayList<>();
			}

			InputStream is;
			if (isServerGzipEnabled) {
				is = new GZIPInputStream(con.getInputStream());
			} else {
				is = con.getInputStream();
			}
			result = OsmoseBug.parseBugs(is);
		} catch (MalformedURLException e) {
		    Log.e(DEBUG_TAG,e.getMessage());
		} catch (IOException e) {
		    Log.e(DEBUG_TAG,e.getMessage());
		}
		return result;
	}
	
	/**
	 * Change the state of the big on the server
	 * 
	 * @param context the Android context
	 * @param bug bug with the state the server side bug should be changed to
	 * @return true if successful
	 */
	public static boolean changeState(Context context, OsmoseBug bug) {
		// http://osmose.openstreetmap.fr/de/api/0.2/error/3313305479/done
		// http://osmose.openstreetmap.fr/de/api/0.2/error/3313313045/false
		if (bug.state ==  State.OPEN) {
			return false; // open is the default state and we shouldn't actually get here
		}
		try {		
			URL url;
			url = new URL(getServerURL(context)  + "error/" + bug.getId() + "/" + (bug.state == State.CLOSED ? "done" : "false"));	
			Log.d(DEBUG_TAG, "changeState " + url.toString());
			
			HttpURLConnection con = (HttpURLConnection) url.openConnection();

			//--Start: header not yet sent
			con.setReadTimeout(TIMEOUT);
			con.setConnectTimeout(TIMEOUT);
			con.setRequestProperty("User-Agent", App.userAgent);
			int responseCode = con.getResponseCode();
			if (responseCode != HttpURLConnection.HTTP_OK) {
				Log.d(DEBUG_TAG, "changeState respnse code " + responseCode);
				if (responseCode ==  HttpURLConnection.HTTP_GONE) {
					bug.changed = false; // don't retry
					App.getTaskStorage().setDirty();
				}
				return false; 
			}
			bug.changed = false;
			App.getTaskStorage().setDirty();			
		} catch (MalformedURLException e) {
		    Log.e(DEBUG_TAG,e.getMessage());
			return false; 
		} catch (IOException e) {
		    Log.e(DEBUG_TAG,e.getMessage());
			return false; 
		}
		Log.d(DEBUG_TAG, "changeState sucess");
		return true;	
	}
	
	/**
	 * Get the OSMOSE server from preferences
     *
	 * @param context the Android context
	 * @return the server URL
	 */
	private static String getServerURL(Context context) {
		Preferences prefs = new Preferences(context);
		String lang = Locale.getDefault().getLanguage();
		if (!supportedLanguages.contains(lang)) {
			lang = "en";
		}
		return prefs.getOsmoseServer() + lang + apiPath;
	}
}
