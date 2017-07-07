package de.blau.android.util;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import de.blau.android.App;
import de.blau.android.names.Names.NameAndTags;
import de.blau.android.osm.OsmElement.ElementType;
import de.blau.android.presets.Preset.PresetItem;
import de.blau.android.util.collections.MultiHashMap;

public class SearchIndexUtils {
	
	
	private static Pattern deAccentPattern = null; // cached regex
	
	/**
	 * normalize a string for the search index, currently only works for latin scripts
	 * @param n
	 * @return
	 */
	static public String normalize(String n) {
		String r = n.toLowerCase(Locale.US).trim();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
			r = deAccent(r);
		}
		StringBuilder b = new StringBuilder();
		for (char c:r.toCharArray()) {
			c = Character.toLowerCase(c);
			if (Character.isLetterOrDigit(c)) {
				b.append(c);
			} else if (Character.isWhitespace(c)) {
				if (b.length() > 0 && !Character.isWhitespace(b.charAt(b.length()-1))) {
					b.append(' ');
				}
			} else {
				switch (c) {
				case '&':
				case '/':
				case '_': 
				case '.': if (b.length() > 0 && !Character.isWhitespace(b.charAt(b.length()-1))) {
							b.append(' ');
						  }
						break;
				case '\'': break;
				}
			}
		}
		return b.toString();
	}
	
	@SuppressLint("NewApi")
	static private String deAccent(String str) {
	    String nfdNormalizedString = Normalizer.normalize(str, Normalizer.Form.NFD); 
	    if (deAccentPattern  == null) {
	    	deAccentPattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
	    }
	    return deAccentPattern.matcher(nfdNormalizedString).replaceAll("");
	}
	
	/**
	 * Slightly fuzzy search in the preset index for presets and return them, translated items first
	 * @param ctx
	 * @param term search term
	 * @param type OSM object "type"
	 * @param maxDistance maximum edit distance to return
	 * @param limit max number of results
	 * @return
	 */
	public static List<PresetItem> searchInPresets(Context ctx, String term, ElementType type, int maxDistance, int limit){
		ArrayList<MultiHashMap<String, PresetItem>> presetSeachIndices = new ArrayList<MultiHashMap<String, PresetItem>>();
		presetSeachIndices.add(App.getTranslatedPresetSearchIndex(ctx));	
		presetSeachIndices.add(App.getPresetSearchIndex(ctx));	
		ArrayList<IndexSearchResult> rawResult = new ArrayList<IndexSearchResult>();
		term = SearchIndexUtils.normalize(term);
		for (MultiHashMap<String, PresetItem> index:presetSeachIndices) {
			for (String s:index.getKeys()) {
				int distance = s.indexOf(term);
				if (distance == -1) {
					distance = OptimalStringAlignment.editDistance(s, term, maxDistance);
				} else {
					distance = 0; // literal substring match, we don't want to weight this worse than a fuzzy match
				}
				if ((distance >= 0 && distance <= maxDistance) ) {
					Set<PresetItem> presetItems = index.get(s);
					for (PresetItem pi:presetItems) {
						if (type == null || pi.appliesTo(type)) {
							IndexSearchResult isr = new IndexSearchResult();
							isr.count = distance * presetItems.size();
							isr.item = pi;
							rawResult.add(isr);
						}
					}
				}
			}
		}
		Collections.sort(rawResult);
		ArrayList<PresetItem>result = new ArrayList<PresetItem>();
		for (IndexSearchResult i:rawResult) {
			Log.d("SearchIndex","found " + i.item.getName());
			if (!result.contains(i.item)) {
				result.add(i.item);
			}
		}
		if (result.size() > 0) {
			return result.subList(0, Math.min(result.size(),limit));
		}
		return result; // empty
	}
	
	/**
	 * Return match is any of term in the name index
	 * 
	 * @param ctx
	 * @param term
	 * @param type
	 * @param maxDistance
	 * @return
	 */
	public static NameAndTags searchInNames(Context ctx, String term, int maxDistance) {
		Map<String,NameAndTags> namesSearchIndex = App.getNameSearchIndex(ctx);
		NameAndTags result = null;
		int lastDistance = Integer.MAX_VALUE;
		term = SearchIndexUtils.normalize(term);
		for (Entry<String,NameAndTags> entry:namesSearchIndex.entrySet()) {
			int distance = OptimalStringAlignment.editDistance(entry.getKey(), term, maxDistance);
			if (distance >= 0 && distance <= maxDistance) {
				if (distance < lastDistance) {
					result = entry.getValue();
					lastDistance = distance;
					if (distance == 0) { // no point in searching for better results
						return result;
					}
				}
			}
		}
		return result;
	}
}
