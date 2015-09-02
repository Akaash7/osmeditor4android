package de.blau.android.voice;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;

import de.blau.android.Application;
import de.blau.android.Logic;
import de.blau.android.R;
import de.blau.android.names.Names;
import de.blau.android.names.Names.NameAndTags;
import de.blau.android.osm.Node;
import de.blau.android.osm.Tags;
import de.blau.android.osm.OsmElement.ElementType;
import de.blau.android.prefs.Preferences;
import de.blau.android.presets.Preset;
import de.blau.android.presets.Preset.PresetItem;
import de.blau.android.util.GeoMath;
import de.blau.android.util.MultiHashMap;
import de.blau.android.util.SearchIndexUtils;

/**
 * Support for simple voice commands, format
 * <location> <number>
 * for an address
 * <location> <object> [<name>]
 * for a POI of some kind-
 * <location> can be one of left, here and right
 * @author simon
 *
 */
public class Commands {
	private static final String DEBUG_TAG = Commands.class.getSimpleName();
	private Preset[] presets;
	private Context ctx;
	
	MultiHashMap<String, PresetItem> presetSeachIndex; 
	Map<String,NameAndTags> namesSearchIndex;
	
	public Commands(Context ctx) {
		this.ctx = ctx;
		presets = Application.getCurrentPresets(ctx);
		presetSeachIndex = Application.getPresetSearchIndex(ctx);
		namesSearchIndex = Application.getNameSearchIndex(ctx);
	}
	
	public void processIntentResult(Intent data) {

		// Fill the list view with the strings the recognizer thought it
		// could have heard
		ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
		Logic logic = Application.mainActivity.getLogic();
		// try to find a command it simply stops at the first string that is valid
		for (String v:matches) {
			String[] words = v.split("\\s+", 3);
			if (words.length > 1) {
				String loc = words[0].toLowerCase(); 
				if (ctx.getString(R.string.voice_left).equals(loc) || ctx.getString(R.string.voice_here).equals(loc) || ctx.getString(R.string.voice_right).equals(loc) ) {
					if (!ctx.getString(R.string.voice_here).equals(loc)) {
						Toast.makeText(ctx,"Sorry currently only the command \"" + ctx.getString(R.string.voice_here) + "\" is supported", Toast.LENGTH_LONG).show();
					} 
					// 
					String first = words[1].toLowerCase();
					try {
						int number = Integer.parseInt(first);
						// worked if there is a further word(s) simply add it/them
						Toast.makeText(ctx,loc + " "+ number  + (words.length == 3?words[2]:""), Toast.LENGTH_LONG).show();
						Node node = createNode(loc);
						if (node != null) {
							TreeMap<String, String> tags = new TreeMap<String, String>(node.getTags());
							tags.put(Tags.KEY_ADDR_HOUSENUMBER, "" + number  + (words.length == 3?words[2]:""));
							tags.put("source:original_text", v);
							logic.setTags(Node.NAME, node.getOsmId(), tags);
						}
						return;
					} catch (Exception ex) {
						// ok wasn't a number
					}

					List<PresetItem> presetItems = SearchIndexUtils.searchInPresets(ctx, first.toString(),ElementType.NODE,2,1);
					if (presetItems != null && presetItems.size()==1) {
						addNode(createNode(loc), words.length == 3? words[2]:null, presetItems.get(0), logic, v);
						return;
					}

					if (namesSearchIndex == null) {
						return;
					}
					
					// search in names
					String input = "";
					for (int i=1;i<words.length;i++) {
						input = input + words[i] + (i<words.length?" ":"");
					}
					NameAndTags nt = SearchIndexUtils.searchInNames(ctx, input, 2);
					if (nt != null) {
						HashMap<String, String> map = new HashMap<String, String>();
						map.putAll(nt.getTags());
						PresetItem pi = Preset.findBestMatch(Application.getCurrentPresets(ctx), map);
						if (pi != null) {
							addNode(createNode(loc), nt.getName(), pi, logic, v);
							return;
						}
					}
				}
			} else if (words.length == 1) {

			}
		}

	}

	boolean addNode(Node node, String name, PresetItem pi, Logic logic, String original) {
		if (node != null) {
			Toast.makeText(ctx, pi.getName()  + (name != null? " name: " + name:""), Toast.LENGTH_LONG).show();
			if (node != null) {
				TreeMap<String, String> tags = new TreeMap<String, String>(node.getTags());
				for (Entry<String, String> tag : pi.getTags().entrySet()) {
					tags.put(tag.getKey(), tag.getValue());
				}
				if (name != null) {
					tags.put(Tags.KEY_NAME, name);
				}
				tags.put("source:original_text", original);
				logic.setTags(Node.NAME, node.getOsmId(), tags);
				return true;
			}
		}
		return false;
	}

	/**
	 * Create a new node at the current GPS pos 
	 * @return
	 */
	Node createNode(String loc) {
		LocationManager locationManager = (LocationManager)Application.mainActivity.getSystemService(android.content.Context.LOCATION_SERVICE);
		if (locationManager != null) {
			Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
			if (location != null) {
				if (ctx.getString(R.string.voice_here).equals(loc)) {
					double lon = location.getLongitude();
					double lat = location.getLatitude();
					if (lon >= -180 && lon <= 180 && lat >= -GeoMath.MAX_LAT && lat <= GeoMath.MAX_LAT) {
						Logic logic = Application.mainActivity.getLogic();
						logic.setSelectedNode(null);
						Node node = logic.performAddNode(lon, lat);
						logic.setSelectedNode(null);
						return node;
					}
				}
			}
		}
		return null;
	}
}
