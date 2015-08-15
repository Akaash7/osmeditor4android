package de.blau.android.osb;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Bitmap.Config;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import de.blau.android.Application;
import de.blau.android.Map;
import de.blau.android.R;
import de.blau.android.osm.BoundingBox;
import de.blau.android.osm.Server;
import de.blau.android.resources.Profile;
import de.blau.android.util.Density;
import de.blau.android.util.GeoMath;
import de.blau.android.util.IssueAlert;
import de.blau.android.views.IMapView;
import de.blau.android.views.overlay.OpenStreetMapViewOverlay;

public class MapOverlay extends OpenStreetMapViewOverlay {
	
	/** viewbox needs to be less wide than this for displaying bugs, just to avoid querying the whole world for bugs */ 
	private static final int TOLERANCE_MIN_VIEWBOX_WIDTH = 40000 * 32;
	
	private Bitmap cachedIconClosed;
	private float w2closed = 0f;
	private float h2closed = 0f;
	private Bitmap cachedIconOpen;
	private float w2open = 0f;
	private float h2open = 0f;
	
	/** Map this is an overlay of. */
	private final Map map;
	
	/** Bugs visible on the overlay. */
	private BugStorage bugs = Application.getBugStorage();
	
	public MapOverlay(final Map map, Server s) {
		this.map = map;
	}
	
	@Override
	public boolean isReadyToDraw() {
		if (map.getPrefs().isOpenStreetBugsEnabled()) {
			return map.getOpenStreetMapTilesOverlay().isReadyToDraw();
		}
		return true;
	}
	
	@Override
	protected void onDraw(Canvas c, IMapView osmv) {
		if (map.getPrefs().isOpenStreetBugsEnabled()) {
			
			// the idea is to have the circles a bit bigger when zoomed in, not so
			// big when zoomed out
			// currently we don't adjust the icon size for density final float radius = Density.dpToPx(1.0f + osmv.getZoomLevel() / 2.0f);
			BoundingBox bb = osmv.getViewBox();

			// 
			int w = map.getWidth();
			int h = map.getHeight();
			ArrayList<Bug> bugList = bugs.getBugs(bb);
			if (bugList != null) {
				Set<String>bugFilter = map.getPrefs().bugFilter();
				for (Bug b : bugList) {
					// filter
					if (b instanceof Note && !bugFilter.contains(b.bugFilterKey())) {
						continue;
					} else if (b instanceof OsmoseBug && !bugFilter.contains(b.bugFilterKey())) {
						continue;
					}
					float x = GeoMath.lonE7ToX(w , bb, b.getLon());
					float y = GeoMath.latE7ToY(h, w, bb, b.getLat()); 

					if (b.isClosed()) {
						if (cachedIconClosed == null) {
							cachedIconClosed = BitmapFactory.decodeResource(map.getContext().getResources(), R.drawable.bug_closed);
							w2closed = cachedIconClosed.getWidth()/2f;
							h2closed = cachedIconClosed.getHeight()/2f;
						}
						c.drawBitmap(cachedIconClosed, x-w2closed, y-h2closed, null); 
					} else {
						if (cachedIconOpen == null) {
							cachedIconOpen = BitmapFactory.decodeResource(map.getContext().getResources(), R.drawable.bug_open);
							w2open = cachedIconOpen.getWidth()/2f;
							h2open = cachedIconOpen.getHeight()/2f;
						}
						c.drawBitmap(cachedIconOpen, x-w2open, y-h2open, null); 
					}
				}
			}
		}
	}

	@Override
	protected void onDrawFinished(Canvas c, IMapView osmv) {
		// do nothing
	}
	
	/**
	 * Given screen coordinates, find all nearby bugs.
	 * @param x Screen X-coordinate.
	 * @param y Screen Y-coordinate.
	 * @param viewBox Map view box.
	 * @return List of bugs close to given location.
	 */
	public List<Bug> getClickedBugs(final float x, final float y, final BoundingBox viewBox) {
		List<Bug> result = new ArrayList<Bug>();
		if (map.getPrefs().isOpenStreetBugsEnabled()) {
			final float tolerance = Profile.getCurrent().nodeToleranceValue;
			ArrayList<Bug> bugList = bugs.getBugs(viewBox);
			if (bugList != null) {
				Set<String>bugFilter = map.getPrefs().bugFilter();
				for (Bug b : bugList) {
					// filter
					if (b instanceof Note && !bugFilter.contains(b.bugFilterKey())) {
						continue;
					} else if (b instanceof OsmoseBug && !bugFilter.contains(b.bugFilterKey())) {
						continue;
					}
					int lat = b.getLat();
					int lon = b.getLon();
					float differenceX = Math.abs(GeoMath.lonE7ToX(map.getWidth(), viewBox, lon) - x);
					float differenceY = Math.abs(GeoMath.latE7ToY(map.getHeight(), map.getWidth(), viewBox, lat) - y);
					if ((differenceX <= tolerance) && (differenceY <= tolerance)) {
						if (Math.hypot(differenceX, differenceY) <= tolerance) {
							result.add(b);
						}
					}
				}
			}
			// For debugging the OSB editor when the OSB site is down:
			//result.add(new Bug(GeoMath.yToLatE7(map.getHeight(), viewBox, y), GeoMath.xToLonE7(map.getWidth(), viewBox, x), true));
		}
		return result;
	}
}
