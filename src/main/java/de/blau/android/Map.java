package de.blau.android;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.WeakHashMap;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.FontMetrics;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import de.blau.android.exception.OsmException;
import de.blau.android.filter.Filter;
import de.blau.android.imageryoffset.ImageryOffsetUtils;
import de.blau.android.imageryoffset.Offset;
import de.blau.android.osm.BoundingBox;
import de.blau.android.osm.GeoPoint;
import de.blau.android.osm.GeoPoint.InterruptibleGeoPoint;
import de.blau.android.osm.Node;
import de.blau.android.osm.OsmElement;
import de.blau.android.osm.Relation;
import de.blau.android.osm.StorageDelegator;
import de.blau.android.osm.Tags;
import de.blau.android.osm.ViewBox;
import de.blau.android.osm.Way;
import de.blau.android.prefs.Preferences;
import de.blau.android.presets.Preset;
import de.blau.android.presets.Preset.PresetItem;
import de.blau.android.resources.DataStyle;
import de.blau.android.resources.DataStyle.FeatureStyle;
import de.blau.android.resources.TileLayerServer;
import de.blau.android.services.TrackerService;
import de.blau.android.util.Density;
import de.blau.android.util.GeoMath;
import de.blau.android.util.ThemeUtils;
import de.blau.android.util.Util;
import de.blau.android.util.collections.FloatPrimitiveList;
import de.blau.android.util.collections.LongHashSet;
import de.blau.android.validation.Validator;
import de.blau.android.views.IMapView;
import de.blau.android.views.layers.MapTilesLayer;
import de.blau.android.views.layers.MapTilesOverlayLayer;
import de.blau.android.views.layers.MapViewLayer;

/**
 * Paints all data provided previously by {@link Logic}.<br/>
 * As well as a number of overlays. There is a default overlay that fetches rendered tiles from an OpenStreetMap-server.
 * 
 * @author mb
 * @author Marcus Wolschon <Marcus@Wolschon.biz>
 */

public class Map extends View implements IMapView {

    @SuppressWarnings("unused")
    private static final String DEBUG_TAG = Map.class.getSimpleName();

    public static final int ICON_SIZE_DP = 20;

    /** Use reflection to access Canvas method only available in API11. */
    private static final Method mIsHardwareAccelerated;

    private static final int HOUSE_NUMBER_RADIUS = 10;

    /**
     * zoom level from which on we display data
     */
    private static final int SHOW_DATA_LIMIT = 12;

    /**
     * zoom level from which on we display icons and house numbers
     */
    private static final int SHOW_ICONS_LIMIT = 15;

    public static final int SHOW_LABEL_LIMIT = SHOW_ICONS_LIMIT + 5;

    /** half the width/height of a node icon in px */
    private final int iconRadius;

    private final int iconSelectedBorder;

    private final int houseNumberRadius;

    private final int verticalNumberOffset;

    private final ArrayList<BoundingBox> boundingBoxes = new ArrayList<>();

    private Preferences prefs;

    /** Direction we're pointing. 0-359 is valid, anything else is invalid. */
    private float orientation = -1f;

    /**
     * List of Overlays we are showing.<br/>
     * This list is initialized to contain only one {@link MapTilesLayer} at construction-time but can be changed to
     * contain additional overlays later.
     * 
     * @see #getOverlays()
     */
    final List<MapViewLayer>                 mOverlays       = Collections.synchronizedList(new ArrayList<MapViewLayer>());
    MapTilesLayer                            backgroundLayer = null;
    MapTilesOverlayLayer                     overlayLayer    = null;
    de.blau.android.photos.MapOverlay        photoLayer      = null;
    de.blau.android.tasks.MapOverlay         taskLayer       = null;
    de.blau.android.gpx.MapOverlay           gpxLayer        = null;
    de.blau.android.layer.geojson.MapOverlay geojsonLayer    = null;

    /**
     * The visible area in decimal-degree (WGS84) -space.
     */
    private ViewBox myViewBox;

    private StorageDelegator delegator;

    /**
     * show icons for POIs (in a wide sense of the word)
     */
    private boolean showIcons = false;

    /**
     * show icons for POIs tagged on (closed) ways
     */
    private boolean showWayIcons = false;

    /**
     * Always darken non-downloaded areas
     */
    private boolean alwaysDrawBoundingBoxes = false;

    /**
     * Stores icons that apply to a certain "thing". This can be e.g. a node or a SortedMap of tags.
     */
    private final WeakHashMap<Object, Bitmap> iconCache = new WeakHashMap<>();

    /**
     * Stores icons that apply to a certain "thing". This can be e.g. a node or a SortedMap of tags. This stores icons
     * for areas
     */
    private final WeakHashMap<Object, Bitmap> areaIconCache = new WeakHashMap<>();

    /**
     * Stores strings that apply to a certain "thing". This can be e.g. a node or a SortedMap of tags.
     */
    private final WeakHashMap<Object, String> labelCache = new WeakHashMap<>();

    /** Caches if the map is zoomed into edit range during one onDraw pass */
    private boolean tmpDrawingInEditRange;

    /** Caches the edit mode during one onDraw pass */
    private Mode tmpDrawingEditMode;

    /** Caches the currently selected nodes during one onDraw pass */
    private List<Node> tmpDrawingSelectedNodes;

    /** Caches the currently selected ways during one onDraw pass */
    private List<Way> tmpDrawingSelectedWays;

    /** Caches the current "clickable elements" set during one onDraw pass */
    private Set<OsmElement> tmpClickableElements;

    /** used for highlighting relation members */
    private List<Way>  tmpDrawingSelectedRelationWays;
    private List<Node> tmpDrawingSelectedRelationNodes;

    /**
     * Locked or not
     */
    private boolean tmpLocked;

    /**
     * 
     */
    private ArrayList<Way> tmpStyledWays = new ArrayList<>();
    private ArrayList<Way> tmpHiddenWays = new ArrayList<>();

    /** Caches the preset during one onDraw pass */
    private Preset[] tmpPresets;

    /** Caches the Paint used for node tolerance */
    private Paint nodeTolerancePaint;
    private Paint nodeTolerancePaint2;

    /** Caches the Paint used for way tolerance */
    private Paint wayTolerancePaint;
    private Paint wayTolerancePaint2;

    /** cached zoom level, calculated once per onDraw pass **/
    private int zoomLevel = 0;

    /** Cache the current filter **/
    private Filter tmpFilter = null;

    /** */
    private boolean inNodeIconZoomRange = false;

    /**
     * We just need one path object
     */
    private Path path = new Path();

    private LongHashSet handles;

    private Location displayLocation = null;
    private boolean  isFollowingGPS  = false;

    private Paint textPaint;

    /**
     * support for display a crosshairs at a position
     */
    private boolean showCrosshairs = false;
    private int     crosshairsLat  = 0;
    private int     crosshairsLon  = 0;

    static {
        Method m;
        try {
            m = Canvas.class.getMethod("isHardwareAccelerated", (Class[]) null);
        } catch (NoSuchMethodException e) {
            m = null;
        }
        mIsHardwareAccelerated = m;
    }

    private Context context;

    private Rect canvasBounds;

    private Validator validator;

    private Paint labelBackground;

    private float[][] coord = null;

    private FloatPrimitiveList points = new FloatPrimitiveList(); // allocate this just once

    private TrackerService tracker = null;

    @SuppressLint("NewApi")
    public Map(final Context context) {
        super(context);
        this.context = context;

        canvasBounds = new Rect();

        setFocusable(true);
        setFocusableInTouchMode(true);

        // Style me
        setBackgroundColor(ContextCompat.getColor(context, R.color.ccc_white));
        setDrawingCacheEnabled(false);
        //
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        iconRadius = Density.dpToPx(ICON_SIZE_DP / 2);
        houseNumberRadius = Density.dpToPx(HOUSE_NUMBER_RADIUS);
        verticalNumberOffset = Density.dpToPx(HOUSE_NUMBER_RADIUS / 2);
        iconSelectedBorder = Density.dpToPx(2);

        validator = App.getDefaultValidator(context);
    }

    /**
     * Create the stack of layers
     * 
     * Current the order is static
     * 
     * @param ctx Android Context
     */
    public void createOverlays(Context ctx) {
        // create an overlay that displays pre-rendered tiles from the internet.
        synchronized (mOverlays) {
            if (mOverlays.isEmpty()) // only set once
            {
                if (prefs == null) { // just to be safe
                    backgroundLayer = new MapTilesLayer(this, TileLayerServer.getDefault(ctx, true), null);
                    mOverlays.add(backgroundLayer);
                    backgroundLayer.setIndex(mOverlays.size()-1);
                } else {
                    mOverlays.clear();
                    TileLayerServer ts = TileLayerServer.get(ctx, prefs.backgroundLayer(), true);
                    backgroundLayer = new MapTilesLayer(this, ts != null ? ts : TileLayerServer.getDefault(ctx, true), null);
                    if (ts != null) {
                        ImageryOffsetUtils.applyImageryOffsets(ctx, ts, getViewBox());
                    }
                    mOverlays.add(backgroundLayer);
                    backgroundLayer.setIndex(mOverlays.size()-1);
                    if (activeOverlay(prefs.overlayLayer())) {
                        overlayLayer = new MapTilesOverlayLayer(this);
                        mOverlays.add(overlayLayer);
                        overlayLayer.setIndex(mOverlays.size()-1);
                    }
                    photoLayer = new de.blau.android.photos.MapOverlay(this);
                    mOverlays.add(photoLayer);
                    photoLayer.setIndex(mOverlays.size()-1);
                    mOverlays.add(new de.blau.android.grid.MapOverlay(this));
                    mOverlays.add(null); // placeholder for data layer
                    gpxLayer = new de.blau.android.gpx.MapOverlay(this);
                    mOverlays.add(gpxLayer);
                    gpxLayer.setIndex(mOverlays.size()-1);
                    taskLayer = new de.blau.android.tasks.MapOverlay(this);
                    mOverlays.add(taskLayer);
                    taskLayer.setIndex(mOverlays.size()-1);
                    geojsonLayer = new de.blau.android.layer.geojson.MapOverlay(this);
                    mOverlays.add(geojsonLayer);
                    geojsonLayer.setIndex(mOverlays.size()-1);
                }
            }
        }
    }

    /**
     * Get the list of configured layers
     * 
     * @return a List of MapViewLayers
     */
    public List<MapViewLayer> getLayers() {
        return mOverlays;
    }

    /**
     * Return the current background layer
     * 
     * @return the current background layer or null if none is configured
     */
    @Nullable
    public MapTilesLayer getBackgroundLayer() {
        return backgroundLayer;
    }

    /**
     * Return the current overlay layer
     * 
     * @return the current overlay layer or null if none is configured
     */
    @Nullable
    public MapTilesOverlayLayer getOverlayLayer() {
        return overlayLayer;
    }

    @Nullable
    public de.blau.android.photos.MapOverlay getPhotoLayer() {
        return photoLayer;
    }

    @Nullable
    public de.blau.android.gpx.MapOverlay getGpxLayer() {
        return gpxLayer;
    }

    @Nullable
    public de.blau.android.tasks.MapOverlay getTaskLayer() {
        return taskLayer;
    }

    @Nullable
    public de.blau.android.layer.geojson.MapOverlay getGeojsonLayer() {
        return geojsonLayer;
    }

    public void onDestroy() {
        synchronized (mOverlays) {
            for (MapViewLayer osmvo : mOverlays) {
                if (osmvo != null) {
                    osmvo.onDestroy();
                }
            }
        }
        synchronized (iconCache) {
            iconCache.clear();
        }
        synchronized (areaIconCache) {
            areaIconCache.clear();
        }
        synchronized (labelCache) {
            labelCache.clear();
        }
        tmpPresets = null;
    }

    public void onLowMemory() {
        synchronized (mOverlays) {
            for (MapViewLayer osmvo : mOverlays) {
                if (osmvo != null) {
                    osmvo.onLowMemory();
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDraw(final Canvas canvas) {
        super.onDraw(canvas);
        long time = System.currentTimeMillis();

        zoomLevel = calcZoomLevel(canvas);

        // set in paintOsmData now tmpDrawingInEditRange = Main.logic.isInEditZoomRange();
        final Logic logic = App.getLogic();
        tmpDrawingEditMode = logic.getMode();
        tmpFilter = logic.getFilter();
        tmpDrawingSelectedNodes = logic.getSelectedNodes();
        tmpDrawingSelectedWays = logic.getSelectedWays();
        tmpClickableElements = logic.getClickableElements();
        tmpDrawingSelectedRelationWays = logic.getSelectedRelationWays();
        tmpDrawingSelectedRelationNodes = logic.getSelectedRelationNodes();
        tmpPresets = App.getCurrentPresets(context);
        tmpLocked = logic.isLocked();

        inNodeIconZoomRange = zoomLevel > SHOW_ICONS_LIMIT;

        // Draw our Overlays.
        canvas.getClipBounds(canvasBounds);

        int attributionOffset = 2;
        synchronized (mOverlays) {
            for (MapViewLayer osmvo : mOverlays) {
                if (osmvo == null) {
                    if (zoomLevel > SHOW_DATA_LIMIT) {
                        paintOsmData(canvas);
                    }
                } else {
                    osmvo.setAttributionOffset(attributionOffset);
                    osmvo.onManagedDraw(canvas, this);
                    attributionOffset = osmvo.getAttributionOffset();
                }
            }
        }

        if (zoomLevel > 10) {
            if (tmpDrawingEditMode != Mode.MODE_ALIGN_BACKGROUND) {
                // shallow copy to avoid modification issues
                boundingBoxes.clear();
                boundingBoxes.addAll(delegator.getBoundingBoxes());
                paintStorageBox(canvas, boundingBoxes);
            }
        }
        paintGpsPos(canvas);
        if (tmpDrawingInEditRange)
            paintCrosshairs(canvas);

        if (tmpDrawingEditMode == Mode.MODE_ALIGN_BACKGROUND) {
            paintZoomAndOffset(canvas);
        }

        time = System.currentTimeMillis() - time;

        if (prefs.isStatsVisible()) {
            paintStats(canvas, (int) (1 / (time / 1000f)));
        }
    }

    @Override
    protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        try {
            myViewBox.setRatio(this, (float) w / h, true);
        } catch (OsmException e) {
            Log.d(DEBUG_TAG, "onSizeChanged got " + e.getMessage());
        }
    }

    /* Overlay Event Forwarders */

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        synchronized (mOverlays) {
            for (MapViewLayer osmvo : mOverlays) {
                if (osmvo != null && osmvo.onTouchEvent(event, this)) {
                    return true;
                }
            }
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        synchronized (mOverlays) {
            for (MapViewLayer osmvo : mOverlays) {
                if (osmvo != null && osmvo.onKeyDown(keyCode, event, this)) {
                    return true;
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        synchronized (mOverlays) {
            for (MapViewLayer osmvo : mOverlays) {
                if (osmvo != null && osmvo.onKeyUp(keyCode, event, this)) {
                    return true;
                }
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onTrackballEvent(MotionEvent event) {
        synchronized (mOverlays) {
            for (MapViewLayer osmvo : mOverlays) {
                if (osmvo != null && osmvo.onTrackballEvent(event, this)) {
                    return true;
                }
            }
        }
        return super.onTrackballEvent(event);
    }

    /**
     * As of Android 4.0.4, clipping with Op.DIFFERENCE is not supported if hardware acceleration is used. (see
     * http://android-developers.blogspot.de/2011/03/android-30-hardware-acceleration.html) Op.DIFFERENCE and clipPath
     * supported as of 18
     * 
     * !!! FIXME Disable using HW clipping completely for now, see bug
     * https://github.com/MarcusWolschon/osmeditor4android/issues/307
     * 
     * @param c Canvas to check
     * @return true if the canvas supports proper clipping with Op.DIFFERENCE
     */
    private boolean hasFullClippingSupport(Canvas c) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB /* && Build.VERSION.SDK_INT < 18 */ && mIsHardwareAccelerated != null) {
            try {
                return !(Boolean) mIsHardwareAccelerated.invoke(c, (Object[]) null);
            } catch (IllegalArgumentException e) {
            } catch (IllegalAccessException e) {
            } catch (InvocationTargetException e) {
            }
        }
        // Older versions do not use hardware acceleration
        return true;
    }

    private boolean myIsHardwareAccelerated(Canvas c) {
        if (mIsHardwareAccelerated != null) {
            try {
                return (Boolean) mIsHardwareAccelerated.invoke(c, (Object[]) null);
            } catch (IllegalArgumentException e) {
            } catch (IllegalAccessException e) {
            } catch (InvocationTargetException e) {
            }
        }
        // Older versions do not use hardware acceleration
        return false;
    }

    private void paintCrosshairs(Canvas canvas) {
        //
        if (showCrosshairs) {
            float x = GeoMath.lonE7ToX(getWidth(), getViewBox(), crosshairsLon);
            float y = GeoMath.latE7ToY(getHeight(), getWidth(), getViewBox(), crosshairsLat);
            Paint paint = DataStyle.getCurrent(DataStyle.CROSSHAIRS_HALO).getPaint();
            drawCrosshairs(canvas, x, y, paint);
            paint = DataStyle.getCurrent(DataStyle.CROSSHAIRS).getPaint();
            drawCrosshairs(canvas, x, y, paint);
        }
    }

    private void drawCrosshairs(Canvas canvas, float x, float y, Paint paint) {
        canvas.save();
        canvas.translate(x, y);
        canvas.drawPath(DataStyle.getCurrent().getCrosshairsPath(), paint);
        canvas.restore();
    }

    /**
     * Show a marker for the current GPS position
     * 
     * @param canvas canvas to draw on
     */
    private void paintGpsPos(final Canvas canvas) {
        if (displayLocation == null)
            return;
        ViewBox viewBox = getViewBox();
        float x = GeoMath.lonE7ToX(getWidth(), viewBox, (int) (displayLocation.getLongitude() * 1E7));
        float y = GeoMath.latE7ToY(getHeight(), getWidth(), viewBox, (int) (displayLocation.getLatitude() * 1E7));

        float o = -1f;
        if (displayLocation.hasBearing() && displayLocation.hasSpeed() && displayLocation.getSpeed() > 1.4f) {
            // 1.4m/s ~= 5km/h ~= walking pace
            // faster than walking pace - use the GPS bearing
            o = displayLocation.getBearing();
        } else {
            // slower than walking pace - use the compass orientation (if available)
            if (orientation >= 0) {
                o = orientation;
            }
        }
        Paint paint = null;
        if (isFollowingGPS) {
            paint = DataStyle.getCurrent(DataStyle.GPS_POS_FOLLOW).getPaint();
        } else {
            paint = DataStyle.getCurrent(DataStyle.GPS_POS).getPaint();
        }

        if (o < 0) {
            // no orientation data available
            canvas.drawCircle(x, y, paint.getStrokeWidth(), paint);
        } else {
            // show the orientation using a pointy indicator
            canvas.save();
            canvas.translate(x, y);
            canvas.rotate(o);
            canvas.drawPath(DataStyle.getCurrent().getOrientationPath(), paint);
            canvas.restore();
        }
        if (displayLocation.hasAccuracy()) {
            // FIXME this assumes square pixels
            float accuracyInPixels = (float) (GeoMath.convertMetersToGeoDistance(displayLocation.getAccuracy())
                    * ((double) getWidth() / (viewBox.getWidth() / 1E7D)));
            RectF accuracyRect = new RectF(x - accuracyInPixels, y + accuracyInPixels, x + accuracyInPixels, y - accuracyInPixels);
            canvas.drawOval(accuracyRect, DataStyle.getCurrent(DataStyle.GPS_ACCURACY).getPaint());
        }
    }

    /**
     * Show some statistics for depugging purposes
     * 
     * @param canvas canvas to draw on
     * @param fps frames per second
     */
    private void paintStats(final Canvas canvas, final int fps) {
        int pos = 1;
        String text = "";
        Paint infotextPaint = DataStyle.getCurrent(DataStyle.INFOTEXT).getPaint();
        float textSize = infotextPaint.getTextSize();

        BoundingBox viewBox = getViewBox();

        text = "viewBox: " + viewBox.toString();
        canvas.drawText(text, 5, getHeight() - textSize * pos++, infotextPaint);
        text = "Relations (current/API) :" + delegator.getCurrentStorage().getRelations().size() + "/" + delegator.getApiRelationCount();
        canvas.drawText(text, 5, getHeight() - textSize * pos++, infotextPaint);
        text = "Ways (current/API) :" + delegator.getCurrentStorage().getWays().size() + "/" + delegator.getApiWayCount();
        canvas.drawText(text, 5, getHeight() - textSize * pos++, infotextPaint);
        text = "Nodes (current/Waynodes/API) :" + delegator.getCurrentStorage().getNodes().size() + "/" + delegator.getCurrentStorage().getWaynodes().size()
                + "/" + delegator.getApiNodeCount();
        canvas.drawText(text, 5, getHeight() - textSize * pos++, infotextPaint);
        text = "fps: " + fps;
        canvas.drawText(text, 5, getHeight() - textSize * pos++, infotextPaint);
        text = "hardware acceleration: " + (myIsHardwareAccelerated(canvas) ? "on" : "off");
        canvas.drawText(text, 5, getHeight() - textSize * pos++, infotextPaint);
        text = "zoom level: " + zoomLevel;
        canvas.drawText(text, 5, getHeight() - textSize * pos, infotextPaint);
    }

    /**
     * Paint the current tile zoom level and offset ... very ugly used when adjusting the offset
     * 
     * @param canvas canvas to draw on
     */
    private void paintZoomAndOffset(final Canvas canvas) {
        int pos = ThemeUtils.getActionBarHeight(context) + 5 + (int) de.blau.android.grid.MapOverlay.LONGTICKS_DP * 3;
        Offset o = getBackgroundLayer().getTileLayerConfiguration().getOffset(zoomLevel);
        String text = context.getString(R.string.zoom_and_offset, zoomLevel, o != null ? String.format(Locale.US, "%.5f", o.getDeltaLon()) : "0.00000",
                o != null ? String.format(Locale.US, "%.5f", o.getDeltaLat()) : "0.00000");
        float textSize = textPaint.getTextSize();
        float textWidth = textPaint.measureText(text);
        FontMetrics fm = textPaint.getFontMetrics();
        float yOffset = pos + textSize;
        canvas.drawRect(5, yOffset + fm.bottom, 5 + textWidth, yOffset - textSize, labelBackground);
        canvas.drawText(text, 5, pos + textSize, textPaint);
    }

    /**
     * Paints all OSM data on the given canvas.
     * 
     * @param canvas Canvas, where the data shall be painted on.
     */
    private void paintOsmData(final Canvas canvas) {
        // first find all nodes that we need to display

        List<Node> paintNodes = delegator.getCurrentStorage().getNodes(getViewBox());

        // the following should guarantee that if the selected node is off screen but the handle not, the handle gets
        // drawn
        // note this isn't perfect because touch areas of other nodes just outside the screen still won't get drawn
        // TODO check if we can't avoid searching paintNodes multiple times
        if (tmpDrawingSelectedNodes != null) {
            for (Node n : tmpDrawingSelectedNodes) {
                if (!paintNodes.contains(n)) {
                    paintNodes.add(n);
                }
            }
        }

        //
        tmpDrawingInEditRange = App.getLogic().isInEditZoomRange(); // do this after density calc

        boolean drawTolerance = tmpDrawingInEditRange // if we are not in editing range none of the further checks are
                                                      // necessary
                && !tmpLocked && tmpDrawingEditMode.elementsSelectable();

        // Paint all ways
        List<Way> ways = delegator.getCurrentStorage().getWays(getViewBox());

        boolean filterMode = tmpFilter != null; // we have an active filter

        List<Way> waysToDraw = ways;
        if (filterMode) {
            /*
             * Split the ways in to those that we are going to show and those that we hide, rendering is far simpler for
             * the later
             */
            tmpHiddenWays.clear();
            tmpStyledWays.clear();
            for (Way w : ways) {
                if (tmpFilter.include(w, tmpDrawingInEditRange && tmpDrawingSelectedWays != null && tmpDrawingSelectedWays.contains(w))) {
                    tmpStyledWays.add(w);
                } else {
                    tmpHiddenWays.add(w);
                }
            }
            // draw hidden ways first
            for (Way w : tmpHiddenWays) {
                paintHiddenWay(canvas, w);
            }
            waysToDraw = tmpStyledWays;
        }

        boolean displayHandles = tmpDrawingSelectedNodes == null && tmpDrawingSelectedRelationWays == null && tmpDrawingSelectedRelationNodes == null
                && tmpDrawingEditMode.elementsGeomEditiable();
        Collections.sort(waysToDraw, layerComparator);
        for (Way w : waysToDraw) {
            paintWay(canvas, w, displayHandles, drawTolerance);
        }

        // Paint nodes
        Boolean hwAccelarationWorkaround = myIsHardwareAccelerated(canvas) && Build.VERSION.SDK_INT < 19;

        ViewBox viewBox = getViewBox();
        int coordSize = 0;
        float r = wayTolerancePaint.getStrokeWidth() / 2;
        float r2 = r * r;
        if (drawTolerance) {
            if (coord == null || coord.length < paintNodes.size()) {
                coord = new float[paintNodes.size()][2];
            }
        }
        for (Node n : paintNodes) {
            boolean noTolerance = false;
            int lat = n.getLat();
            float y = GeoMath.latE7ToY(getHeight(), getWidth(), viewBox, lat);
            int lon = n.getLon();
            float x = GeoMath.lonE7ToX(getWidth(), viewBox, lon);
            if (drawTolerance) {
                // this reduces the number of tolerance fields drawn
                // while it rather expensive traversing the array is
                // still reasonably cheap
                if (coordSize != 0) {
                    for (int i = 0; i < coordSize; i++) {
                        float x1 = coord[i][0];
                        float y1 = coord[i][1];
                        float d2 = (x1 - x) * (x1 - x) + (y1 - y) * (y1 - y);
                        if (d2 < r2) {
                            noTolerance = true;
                            break;
                        }
                    }
                }
                if (!noTolerance) {
                    coord[coordSize][0] = x;
                    coord[coordSize][1] = y;
                    coordSize++;
                }
            }
            paintNode(canvas, n, x, y, hwAccelarationWorkaround,
                    drawTolerance && !noTolerance && (n.getState() != OsmElement.STATE_UNCHANGED || delegator.isInDownload(lat, lon)));
        }
        paintHandles(canvas);
    }

    /**
     * For ordering according to layer value and draw lines on top of areas in the same layer
     */
    private Comparator<Way> layerComparator = new Comparator<Way>() {
        @Override
        public int compare(Way w1, Way w2) {
            int layer1 = 0;
            int layer2 = 0;
            String layer1Str = w1.getTagWithKey(Tags.KEY_LAYER);
            if (layer1Str != null) {
                try {
                    layer1 = Integer.parseInt(layer1Str);
                } catch (NumberFormatException e) {
                    // FIXME should validate here
                }
            }
            String layer2Str = w2.getTagWithKey(Tags.KEY_LAYER);
            if (layer2Str != null) {
                try {
                    layer2 = Integer.parseInt(layer2Str);
                } catch (NumberFormatException e) {
                    // FIXME should validate here
                }
            }
            int result = layer2 == layer1 ? 0 : layer2 > layer1 ? +1 : -1;
            if (result == 0) {
                FeatureStyle fs1 = matchStyle(w1);
                Style style1 = fs1.getPaint().getStyle();
                FeatureStyle fs2 = matchStyle(w2);
                Style style2 = fs2.getPaint().getStyle();
                result = style2 == style1 ? 0 : style2 == Style.STROKE ? -1 : +1;
            }
            return result;
        }
    };

    /**
     * Dim everything that hasn't been downloaded
     * 
     * @param canvas the canvas we are drawing on
     * @param list list of bounding boxes that we've downloaded
     */
    private void paintStorageBox(final Canvas canvas, List<BoundingBox> list) {
        if (!tmpLocked || alwaysDrawBoundingBoxes) {
            Canvas c = canvas;
            Bitmap b = null;
            // Clipping with Op.DIFFERENCE is not supported when a device uses hardware acceleration
            // drawing to a bitmap however will currently not be accelerated
            if (!hasFullClippingSupport(canvas)) {
                b = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
                c = new Canvas(b);
            } else {
                c.save();
            }
            int screenWidth = getWidth();
            int screenHeight = getHeight();
            ViewBox viewBox = getViewBox();
            path.reset();
            RectF screen = new RectF(0, 0, getWidth(), getHeight());
            for (BoundingBox bb : list) {
                if (viewBox.intersects(bb)) { // only need to do this if we are on screen
                    float left = GeoMath.lonE7ToX(screenWidth, viewBox, bb.getLeft());
                    float right = GeoMath.lonE7ToX(screenWidth, viewBox, bb.getRight());
                    float bottom = GeoMath.latE7ToY(screenHeight, screenWidth, viewBox, bb.getBottom());
                    float top = GeoMath.latE7ToY(screenHeight, screenWidth, viewBox, bb.getTop());
                    RectF rect = new RectF(left, top, right, bottom);
                    rect.intersect(screen);
                    path.addRect(rect, Path.Direction.CW);
                }
            }

            Paint boxpaint = DataStyle.getCurrent(DataStyle.VIEWBOX).getPaint();
            c.clipPath(path, Region.Op.DIFFERENCE);
            c.drawRect(screen, boxpaint);

            if (!hasFullClippingSupport(canvas)) {
                canvas.drawBitmap(b, 0, 0, null);
            } else {
                c.restore();
            }
        }
    }

    /**
     * Paints the given node on the canvas.
     * 
     * @param canvas Canvas, where the node shall be painted on.
     * @param node Node which shall be painted.
     * @param hwAccelarationWorkaround use a workaround for operatons that are not supported when HW accelation is used
     * @param drawTolerance draw the touch halo
     */
    private void paintNode(final Canvas canvas, final Node node, final float x, final float y, final boolean hwAccelarationWorkaround,
            final boolean drawTolerance) {

        boolean isSelected = tmpDrawingSelectedNodes != null && tmpDrawingSelectedNodes.contains(node);

        boolean isTagged = node.isTagged();
        boolean hasProblem = false;

        boolean filteredObject = false;
        boolean filterMode = tmpFilter != null; // we have an active filter
        if (filterMode) {
            filteredObject = tmpFilter.include(node, isSelected);
        }

        // draw tolerance
        if (drawTolerance && (!filterMode || (filterMode && filteredObject))) {
            if (prefs.isToleranceVisible() && tmpClickableElements == null) {
                drawNodeTolerance(canvas, isTagged, x, y, nodeTolerancePaint);
            } else if (tmpClickableElements != null && tmpClickableElements.contains(node)) {
                drawNodeTolerance(canvas, isTagged, x, y, nodeTolerancePaint2);
            }
        }

        String featureStyle;
        String featureStyleThin;
        String featureStyleTagged;
        String featureStyleFont;
        String featureStyleFontSmall;
        if (isSelected && tmpDrawingInEditRange) {
            // general node style
            featureStyle = DataStyle.SELECTED_NODE;
            // style for house numbers
            featureStyleThin = DataStyle.SELECTED_NODE_THIN;
            // style for tagged nodes or otherwise important
            featureStyleTagged = DataStyle.SELECTED_NODE_TAGGED;
            // style for label text
            featureStyleFont = DataStyle.LABELTEXT_NORMAL_SELECTED;
            // style for small label text
            featureStyleFontSmall = DataStyle.LABELTEXT_SMALL_SELECTED;
            if (tmpDrawingSelectedNodes.size() == 1 && tmpDrawingSelectedWays == null && prefs.largeDragArea() && tmpDrawingEditMode.elementsGeomEditiable()) {
                // don't draw large areas in multi-select mode
                canvas.drawCircle(x, y, DataStyle.getCurrent().getLargDragToleranceRadius(), DataStyle.getCurrent(DataStyle.NODE_DRAG_RADIUS).getPaint());
            }
        } else if ((tmpDrawingSelectedRelationNodes != null && tmpDrawingSelectedRelationNodes.contains(node)) && tmpDrawingInEditRange) {
            // general node style
            featureStyle = DataStyle.SELECTED_RELATION_NODE;
            // style for house numbers
            featureStyleThin = DataStyle.SELECTED_RELATION_NODE_THIN;
            // style for tagged nodes or otherwise important
            featureStyleTagged = DataStyle.SELECTED_RELATION_NODE_TAGGED;
            // style for label text
            featureStyleFont = DataStyle.LABELTEXT_NORMAL;
            // style for small label text
            featureStyleFontSmall = DataStyle.LABELTEXT_SMALL;
            isSelected = true;
        } else if (node.hasProblem(context, validator) != Validator.OK) {
            // general node style
            featureStyle = DataStyle.PROBLEM_NODE;
            // style for house numbers
            featureStyleThin = DataStyle.PROBLEM_NODE_THIN;
            // style for tagged nodes or otherwise important
            featureStyleTagged = DataStyle.PROBLEM_NODE_TAGGED;
            // style for label text
            featureStyleFont = DataStyle.LABELTEXT_NORMAL_PROBLEM;
            // style for small label text
            featureStyleFontSmall = DataStyle.LABELTEXT_SMALL_PROBLEM;
            hasProblem = true;
        } else {
            // general node style
            featureStyle = DataStyle.NODE;
            // style for house numbers
            featureStyleThin = DataStyle.NODE_THIN;
            // style for tagged nodes or otherwise important
            featureStyleTagged = DataStyle.NODE_TAGGED;
            // style for label text
            featureStyleFont = DataStyle.LABELTEXT_NORMAL;
            // style for small label text
            featureStyleFontSmall = DataStyle.LABELTEXT_SMALL;
        }

        boolean noIcon = true;
        boolean isTaggedAndInZoomLimit = isTagged && inNodeIconZoomRange;

        if (filterMode && !filteredObject) {
            featureStyle = DataStyle.HIDDEN_NODE;
            featureStyleThin = featureStyle;
            featureStyleTagged = featureStyle;
            isTaggedAndInZoomLimit = false;
        }

        if (isTaggedAndInZoomLimit && showIcons) {
            noIcon = tmpPresets == null || !paintNodeIcon(node, canvas, x, y, isSelected || hasProblem ? featureStyleTagged : null);
            if (noIcon) {
                String houseNumber = node.getTagWithKey(Tags.KEY_ADDR_HOUSENUMBER);
                if (houseNumber != null && !"".equals(houseNumber)) { // draw house-numbers
                    paintHouseNumber(x, y, canvas, featureStyleThin, featureStyleFontSmall, houseNumber);
                    return;
                }
            } else if (zoomLevel > SHOW_LABEL_LIMIT && node.hasTagKey(Tags.KEY_NAME)) {
                Paint p = DataStyle.getCurrent(DataStyle.NODE_TAGGED).getPaint();
                paintLabel(x, y, canvas, featureStyleFont, node, p.getStrokeWidth(), true);
            }
        }

        if (noIcon) {
            // draw regular nodes or without icons
            Paint p = DataStyle.getCurrent(isTagged ? featureStyleTagged : featureStyle).getPaint();
            float strokeWidth = p.getStrokeWidth();
            if (hwAccelarationWorkaround) { // FIXME we don't actually know if this is slower than drawPoint
                canvas.drawCircle(x, y, strokeWidth / 2, p);
            } else {
                canvas.drawPoint(x, y, p);
            }
            if (isTaggedAndInZoomLimit) {
                paintLabel(x, y, canvas, featureStyleFont, node, strokeWidth, false);
            }
        }
    }

    /**
     * Draw a circle with center at x,y with the house number in it
     * 
     * @param x screen x
     * @param y screen y
     * @param canvas canvas we are drawing on
     * @param featureKeyThin style to use for the housenumber circle
     * @param featureKeyFont style to use for the housenumber number
     * @param houseNumber the number as a string
     */
    private void paintHouseNumber(final float x, final float y, final Canvas canvas, final String featureKeyThin, final String featureKeyFont,
            final String houseNumber) {
        FeatureStyle fontStyle = DataStyle.getCurrent(featureKeyFont);
        Paint fontPaint = fontStyle.getPaint();
        Paint paint = DataStyle.getCurrent(featureKeyThin).getPaint();
        canvas.drawCircle(x, y, houseNumberRadius, paint);
        canvas.drawCircle(x, y, houseNumberRadius, labelBackground);
        canvas.drawText(houseNumber, x - fontPaint.measureText(houseNumber) / 2, y + verticalNumberOffset, fontStyle.getPaint());
    }

    /**
     * Paint a label under the node, does not try to do collision avoidance
     * 
     * @param x screen x
     * @param y screen y
     * @param canvas canvas we are drawing on
     * @param featureKeyThin style to use for the label
     * @param e the OsmElement
     * @param strokeWidth current stroke scaling factor
     * @param withIcon offset the label so that we don't overlap an icon
     */
    private void paintLabel(final float x, final float y, final Canvas canvas, final String featureKeyThin, final OsmElement e, final float strokeWidth,
            final boolean withIcon) {
        FeatureStyle fs = DataStyle.getCurrent(featureKeyThin);
        Paint paint = fs.getPaint();
        SortedMap<String, String> tags = e.getTags();
        String label = labelCache.get(tags); // may be null!
        if (label == null) {
            if (!labelCache.containsKey(tags)) {
                label = e.getTagWithKey(Tags.KEY_NAME);
                if (label == null && tmpPresets != null) {
                    PresetItem match = Preset.findBestMatch(tmpPresets, e.getTags());
                    if (match != null) {
                        label = match.getTranslatedName();
                    } else {
                        label = e.getPrimaryTag(context);
                        // if label is still null, leave it as is
                    }
                }
                synchronized (labelCache) {
                    labelCache.put(tags, label);
                    if (label == null) {
                        return;
                    }
                }
            } else {
                return;
            }
        }
        float halfTextWidth = paint.measureText(label) / 2;
        FontMetrics fm = fs.getFontMetrics();
        float yOffset = y + strokeWidth + (withIcon ? 2 * iconRadius : iconRadius);
        canvas.drawRect(x - halfTextWidth, yOffset + fm.bottom, x + halfTextWidth, yOffset - paint.getTextSize() + fm.bottom, labelBackground);
        canvas.drawText(label, x - halfTextWidth, yOffset, paint);
    }

    static final Bitmap NOICON = Bitmap.createBitmap(2, 2, Config.ARGB_8888);

    /**
     * Retrieve icon for the element, caching it if it isn't in the cache
     * 
     * @param element element we want to find an icon for
     * @return icon or null if none is found
     */
    private Bitmap getIcon(OsmElement element) {
        SortedMap<String, String> tags = element.getTags();
        boolean isWay = element instanceof Way;
        WeakHashMap<Object, Bitmap> tempCache = isWay ? areaIconCache : iconCache;

        Bitmap icon = tempCache.get(tags); // may be null!
        if (icon == null && tmpPresets != null) {
            if (tempCache.containsKey(tags)) {
                // no point in trying to match
                return null;
            }
            // icon not cached, ask the preset, render to a bitmap and cache result
            PresetItem match = null;
            if (isWay) {
                // don't show building icons, only icons for those with POI tags
                if (Logic.areaHasIcon((Way) element)) {
                    SortedMap<String, String> tempTags = new TreeMap<>(tags);
                    tempTags.remove(Tags.KEY_BUILDING);
                    icon = iconCache.get(tags); // maybe we already cached this for a node
                    if (icon == null) {
                        match = Preset.findBestMatch(tmpPresets, tempTags);
                    }
                }
            } else {
                match = Preset.findBestMatch(tmpPresets, tags);
            }
            if (match != null) {
                Drawable iconDrawable = match.getMapIcon();
                if (iconDrawable != null) {
                    icon = Bitmap.createBitmap(iconRadius * 2, iconRadius * 2, Config.ARGB_8888);
                    // icon.eraseColor(Color.WHITE); // replace nothing with white?
                    iconDrawable.draw(new Canvas(icon));
                }
            } else {
                icon = NOICON;
            }
            synchronized (tempCache) {
                tempCache.put(tags, icon);
            }
        }
        return icon != NOICON ? icon : null;
    }

    /**
     * Paints an icon for an element. tmpPreset needs to be available (i.e. not null).
     * 
     * @param element the element whose icon should be painted
     * @param canvas the canvas on which to draw
     * @param x the x position where the center of the icon goes
     * @param y the y position where the center of the icon goes
     */
    private boolean paintNodeIcon(OsmElement element, Canvas canvas, float x, float y, String featureKey) {
        Bitmap icon = getIcon(element);
        if (icon != null) {
            float w2 = icon.getWidth() / 2f;
            float h2 = icon.getHeight() / 2f;
            if (featureKey != null) { // selected
                RectF r = new RectF(x - w2 - iconSelectedBorder, y - h2 - iconSelectedBorder, x + w2 + iconSelectedBorder, y + h2 + iconSelectedBorder);
                canvas.drawRoundRect(r, iconSelectedBorder, iconSelectedBorder, DataStyle.getCurrent(featureKey).getPaint());
            }
            // we have an icon! draw it.
            canvas.drawBitmap(icon, x - w2, y - h2, null);
            return true;
        }
        return false;
    }

    /**
     * Paint the tolerance halo for a node
     * 
     * @param canvas the canvas we are drawing on
     * @param isTagged true if the node has any tags
     * @param x screen x
     * @param y screen y
     * @param paint the parameters to use for the colour
     */
    private void drawNodeTolerance(final Canvas canvas, final boolean isTagged, final float x, final float y, final Paint paint) {
        canvas.drawCircle(x, y, isTagged ? paint.getStrokeWidth() : wayTolerancePaint.getStrokeWidth() / 2, paint);
    }

    /**
     * Paints the given way on the canvas.
     * 
     * @param canvas Canvas, where the node shall be painted on.
     * @param way way which shall be painted.
     * @param displayHandles draw geometry improvement handles
     * @param drawTolerance if true draw the halo
     */
    private void paintWay(final Canvas canvas, final Way way, final boolean displayHandles, boolean drawTolerance) {
        pointListToLinePointsArray(points, way.getNodes());
        float[] linePoints = points.getArray();
        int pointsSize = points.size();
        Paint paint;
        String labelFontStyle = DataStyle.LABELTEXT_NORMAL;
        String labelFontStyleSmall = DataStyle.LABELTEXT_SMALL;

        boolean isSelected = tmpDrawingInEditRange // if we are not in editing range don't show selected way ... may be
                                                   // a better idea to do so
                && tmpDrawingSelectedWays != null && tmpDrawingSelectedWays.contains(way);
        boolean isMemberOfSelectedRelation = tmpDrawingInEditRange && tmpDrawingSelectedRelationWays != null && tmpDrawingSelectedRelationWays.contains(way);

        // draw way tolerance
        if (drawTolerance) {
            if (prefs.isToleranceVisible() && tmpClickableElements == null) {
                canvas.drawLines(linePoints, 0, pointsSize, wayTolerancePaint);
            } else if (tmpClickableElements != null && tmpClickableElements.contains(way)) {
                canvas.drawLines(linePoints, 0, pointsSize, wayTolerancePaint2);
            }
        }

        FeatureStyle fp; // no need to get the default here

        if (way.hasProblem(context, validator) != Validator.OK) {
            fp = DataStyle.getCurrent(DataStyle.PROBLEM_WAY);
        } else {
            fp = matchStyle(way);
        }

        // draw selectedWay highlighting
        if (isSelected) {
            FeatureStyle selectedStyle = DataStyle.getCurrent(DataStyle.SELECTED_WAY);
            paint = selectedStyle.getPaint();
            paint.setStrokeWidth(fp.getPaint().getStrokeWidth() * selectedStyle.getWidthFactor());
            canvas.drawLines(linePoints, 0, pointsSize, paint);
            paint = DataStyle.getCurrent(DataStyle.WAY_DIRECTION).getPaint();
            drawWayArrows(canvas, linePoints, pointsSize, false, paint, displayHandles && tmpDrawingSelectedWays.size() == 1);
            labelFontStyle = DataStyle.LABELTEXT_NORMAL_SELECTED;
            labelFontStyleSmall = DataStyle.LABELTEXT_SMALL_SELECTED;
        } else if (isMemberOfSelectedRelation) {
            FeatureStyle relationSelectedStyle = DataStyle.getCurrent(DataStyle.SELECTED_RELATION_WAY);
            paint = relationSelectedStyle.getPaint();
            paint.setStrokeWidth(fp.getPaint().getStrokeWidth() * relationSelectedStyle.getWidthFactor());
            canvas.drawLines(linePoints, 0, pointsSize, paint);
        }

        int onewayCode = way.getOneway();
        if (onewayCode != 0) {
            FeatureStyle directionArrows = DataStyle.getCurrent(DataStyle.ONEWAY_DIRECTION);
            drawWayArrows(canvas, linePoints, pointsSize, (onewayCode == -1), directionArrows.getPaint(), false);
        } else if (way.getTagWithKey(Tags.KEY_WATERWAY) != null) { // waterways flow in the way direction
            FeatureStyle directionArrows = DataStyle.getCurrent(DataStyle.ONEWAY_DIRECTION);
            drawWayArrows(canvas, linePoints, pointsSize, false, directionArrows.getPaint(), false);
        }

        //

        // draw the way itself
        // canvas.drawLines(linePoints, fp.getPaint()); doesn't work properly with HW acceleration
        if (pointsSize > 2) {
            path.reset();
            path.moveTo(linePoints[0], linePoints[1]);
            for (int i = 0; i < pointsSize; i = i + 4) {
                path.lineTo(linePoints[i + 2], linePoints[i + 3]);
            }
            canvas.drawPath(path, fp.getPaint());
        }

        // display icons on closed ways
        if (showIcons && showWayIcons && zoomLevel > SHOW_ICONS_LIMIT && way.isClosed()) {
            int vs = pointsSize;
            if (vs < way.nodeCount() * 2) {
                return;
            }
            double A = 0;
            double Y = 0;
            double X = 0;
            for (int i = 0; i < vs; i = i + 2) { // calc centroid
                double x1 = linePoints[i];
                double y1 = linePoints[i + 1];
                double x2 = linePoints[(i + 2) % vs];
                double y2 = linePoints[(i + 3) % vs];
                double d = x1 * y2 - x2 * y1;
                A = A + d;
                X = X + (x1 + x2) * d;
                Y = Y + (y1 + y2) * d;
            }
            if (Util.notZero(A)) {
                Y = Y / (3 * A); // NOSONAR nonZero tests for zero
                X = X / (3 * A); // NOSONAR nonZero tests for zero
                boolean iconDrawn = false;
                if (tmpPresets != null) {
                    iconDrawn = paintNodeIcon(way, canvas, (float) X, (float) Y, isSelected ? DataStyle.SELECTED_NODE_TAGGED : null);
                    boolean doLabel = false;
                    if (!iconDrawn) {
                        String houseNumber = way.getTagWithKey(Tags.KEY_ADDR_HOUSENUMBER);
                        if (houseNumber != null && !"".equals(houseNumber)) { // draw house-numbers
                            paintHouseNumber((float) X, (float) Y, canvas, isSelected ? DataStyle.SELECTED_NODE_THIN : DataStyle.NODE_THIN, labelFontStyleSmall,
                                    houseNumber);
                        } else {
                            doLabel = way.hasTagKey(Tags.KEY_NAME);
                        }
                    } else {
                        doLabel = zoomLevel > SHOW_LABEL_LIMIT && way.hasTagKey(Tags.KEY_NAME);
                    }
                    if (doLabel) {
                        Paint p = DataStyle.getCurrent(DataStyle.SELECTED_NODE_TAGGED).getPaint();
                        paintLabel((float) X, (float) Y, canvas, labelFontStyle, way, iconDrawn ? p.getStrokeWidth() : 0, iconDrawn);
                    }
                }
            }
        }
    }

    /**
     * Paints the given way on the canvas with the "hidden" style.
     * 
     * @param canvas Canvas, where the node shall be painted on.
     * @param way way which shall be painted.
     */
    private void paintHiddenWay(final Canvas canvas, final Way way) {
        pointListToLinePointsArray(points, way.getNodes());
        float[] linePoints = points.getArray();
        int pointsSize = points.size();

        //
        FeatureStyle fp = DataStyle.getCurrent(DataStyle.HIDDEN_WAY);

        // draw the way itself
        // canvas.drawLines(linePoints, fp.getPaint()); doesn't work properly with HW acceleration
        if (pointsSize > 2) {
            path.reset();
            path.moveTo(linePoints[0], linePoints[1]);
            for (int i = 0; i < pointsSize; i = i + 4) {
                path.lineTo(linePoints[i + 2], linePoints[i + 3]);
            }
            canvas.drawPath(path, fp.getPaint());
        }
    }

    private static final String WAY_         = "way-";
    private static final String WAY_HIGHWAY  = "way-highway";
    private static final String WAY_HIGHWAY_ = "way-highway-";
    private static final String HYPHEN       = "-";

    /**
     * Determine the style to use for way and cache it in the way object
     * 
     * If the way is untagged or a style can't be determined, we return a style for any relations the way is a member of
     * 
     * @param way way we need the style for
     * @return the style
     */
    private FeatureStyle matchStyle(final Way way) {
        FeatureStyle fp;
        FeatureStyle wayFp = way.getFeatureProfile();
        if (wayFp == null) {
            fp = DataStyle.getCurrent(DataStyle.WAY); // default for ways
            // three levels of hierarchy for roads and special casing of tracks, two levels for everything else
            String highwayType = way.getTagWithKey(Tags.KEY_HIGHWAY);
            if (highwayType != null) {
                FeatureStyle tempFp = DataStyle.getCurrent(WAY_HIGHWAY);
                if (tempFp != null) {
                    fp = tempFp;
                }
                tempFp = DataStyle.getCurrent(WAY_HIGHWAY_ + highwayType);
                if (tempFp != null) {
                    fp = tempFp;
                }
                String highwaySubType;
                if (highwayType.equals(Tags.VALUE_TRACK)) { // special case
                    highwaySubType = way.getTagWithKey(Tags.KEY_TRACKTYPE);
                } else {
                    highwaySubType = way.getTagWithKey(highwayType);
                }
                if (highwaySubType != null) {
                    tempFp = DataStyle.getCurrent(WAY_HIGHWAY_ + highwayType + HYPHEN + highwaySubType);
                    if (tempFp != null) {
                        fp = tempFp;
                    }
                }
            } else {
                // order in the array defines precedence
                FeatureStyle tempFp = null;
                for (String tag : Tags.WAY_TAGS) {
                    tempFp = getProfile(tag, way);
                    if (tempFp != null) {
                        fp = tempFp;
                        break;
                    }
                }
                if (tempFp == null) {
                    List<Relation> relations = way.getParentRelations();
                    // check for any relation memberships with low prio, take first one
                    if (relations != null) {
                        for (Relation r : relations) {
                            for (String tag : Tags.RELATION_TAGS) {
                                tempFp = getProfile(tag, r);
                                if (tempFp != null) {
                                    fp = tempFp;
                                    break;
                                }
                            }
                            if (tempFp != null) { // break out of loop over relations
                                break;
                            }
                        }
                    }
                }
            }
            way.setFeatureProfile(fp);
        } else {
            fp = wayFp;
        }
        return fp;
    }

    private FeatureStyle getProfile(String tag, OsmElement e) {
        String mainType = e.getTagWithKey(tag);
        FeatureStyle fp = null;
        if (mainType != null) {
            FeatureStyle tempFp = DataStyle.getCurrent(WAY_ + tag);
            if (tempFp != null) {
                fp = tempFp;
            }
            tempFp = DataStyle.getCurrent(WAY_ + tag + HYPHEN + mainType);
            if (tempFp != null) {
                fp = tempFp;
            }
        }
        return fp;
    }

    private void paintHandles(Canvas canvas) {
        if (handles != null && handles.size() > 0) {
            canvas.save();
            float lastX = 0;
            float lastY = 0;
            for (long l : handles.values()) {
                // draw handle
                float X = Float.intBitsToFloat((int) (l >>> 32));
                float Y = Float.intBitsToFloat((int) (l));
                canvas.translate(X - lastX, Y - lastY);
                lastX = X;
                lastY = Y;
                canvas.drawPath(DataStyle.getCurrent().getXPath(), DataStyle.getCurrent(DataStyle.HANDLE).getPaint());
            }
            canvas.restore();
            handles.clear(); // this is hopefully faster than allocating a new set
        }
    }

    /**
     * Draws directional arrows for a way
     * 
     * @param canvas the canvas on which to draw
     * @param linePoints line segment array in the format returned by {@link #pointListToLinePointsArray(Iterable)}.
     * @param reverse if true, the arrows will be painted in the reverse direction
     * @param paint the paint to use for drawing the arrows
     * @param addHandles if true draw arrows at 1/4 and 3/4 of the length and save the middle pos. for drawing a handle
     */
    private void drawWayArrows(Canvas canvas, float[] linePoints, int linePointsSize, boolean reverse, Paint paint, boolean addHandles) {
        double minLen = DataStyle.getCurrent().getMinLenForHandle();
        int ptr = 0;
        while (ptr < linePointsSize) {

            float x1 = linePoints[ptr++];
            float y1 = linePoints[ptr++];
            float x2 = linePoints[ptr++];
            float y2 = linePoints[ptr++];

            float xDelta = x2 - x1;
            float yDelta = y2 - y1;

            boolean secondArrow = false;
            if (addHandles) {
                double len = Math.hypot(xDelta, yDelta);
                if (len > minLen) {
                    if (handles == null)
                        handles = new LongHashSet();
                    handles.put(((long) (Float.floatToRawIntBits(x1 + xDelta / 2)) << 32) + (long) Float.floatToRawIntBits(y1 + yDelta / 2));
                    xDelta = xDelta / 4;
                    yDelta = yDelta / 4;
                    secondArrow = true;
                } else {
                    xDelta = xDelta / 2;
                    yDelta = yDelta / 2;
                }
            } else {
                xDelta = xDelta / 2;
                yDelta = yDelta / 2;
            }

            float x = x1 + xDelta;
            float y = y1 + yDelta;
            float angle = (float) (Math.atan2(y2 - y1, x2 - x1) * 180 / Math.PI);

            canvas.save();
            canvas.translate(x, y);
            canvas.rotate(reverse ? angle - 180 : angle);
            canvas.drawPath(DataStyle.WAY_DIRECTION_PATH, paint);
            canvas.restore();

            if (secondArrow) {
                canvas.save();
                canvas.translate(x + 2 * xDelta, y + 2 * yDelta);
                canvas.rotate(reverse ? angle - 180 : angle);
                canvas.drawPath(DataStyle.WAY_DIRECTION_PATH, paint);
                canvas.restore();
            }
        }
    }

    /**
     * Converts a geographical way/path/track to a list of screen-coordinate points for drawing.
     * 
     * Only segments that are inside the ViewBox are included.
     * 
     * @param points list to (re-)use for projected points in the format expected by
     *            {@link Canvas#drawLines(float[], Paint)
     * @param nodes An iterable (e.g. List or array) with GeoPoints of the line that should be drawn (e.g. a Way or a
     *            GPS track)
     */
    public void pointListToLinePointsArray(@NonNull final FloatPrimitiveList points, @NonNull final List<? extends GeoPoint> nodes) {
        points.clear(); // reset
        ViewBox box = getViewBox();
        boolean testInterrupted = false;
        // loop over all nodes
        GeoPoint prevNode = null;
        GeoPoint lastDrawnNode = null;
        int lastDrawnNodeLon = 0;
        int lastDrawnNodeLat = 0;
        float prevX = 0f;
        float prevY = 0f;
        int w = getWidth();
        int h = getHeight();
        boolean thisIntersects = false;
        boolean nextIntersects = false;
        int nodesSize = nodes.size();
        if (nodesSize > 0) {
            GeoPoint nextNode = nodes.get(0);
            int nextNodeLat = nextNode.getLat();
            int nextNodeLon = nextNode.getLon();
            float X = -Float.MAX_VALUE;
            float Y = -Float.MAX_VALUE;
            for (int i = 0; i < nodesSize; i++) {
                GeoPoint node = nextNode;
                int nodeLon = nextNodeLon;
                int nodeLat = nextNodeLat;
                boolean interrupted = false;
                if (i == 0) { // just do this once
                    testInterrupted = node instanceof InterruptibleGeoPoint;
                }
                if (testInterrupted && node != null) {
                    interrupted = ((InterruptibleGeoPoint) node).isInterrupted();
                }
                nextIntersects = true;
                if (i < nodesSize - 1) {
                    nextNode = nodes.get(i + 1);
                    nextNodeLat = nextNode.getLat();
                    nextNodeLon = nextNode.getLon();
                    nextIntersects = box.isIntersectionPossible(nextNodeLon, nextNodeLat, nodeLon, nodeLat);
                } else {
                    nextNode = null;
                }
                X = -Float.MAX_VALUE; // misuse this as a flag
                if (!interrupted && prevNode != null) {
                    if (thisIntersects || nextIntersects || (!(nextNode != null && lastDrawnNode != null)
                            || box.isIntersectionPossible(nextNodeLon, nextNodeLat, lastDrawnNodeLon, lastDrawnNodeLat))) {
                        X = GeoMath.lonE7ToX(w, box, nodeLon);
                        Y = GeoMath.latE7ToY(h, w, box, nodeLat);
                        if (prevX == -Float.MAX_VALUE) { // last segment didn't intersect
                            prevX = GeoMath.lonE7ToX(w, box, prevNode.getLon());
                            prevY = GeoMath.latE7ToY(h, w, box, prevNode.getLat());
                        }
                        // Line segment needs to be drawn
                        points.add(prevX);
                        points.add(prevY);
                        points.add(X);
                        points.add(Y);
                        lastDrawnNode = node;
                        lastDrawnNodeLat = nodeLat;
                        lastDrawnNodeLon = nodeLon;
                    }
                }
                prevNode = node;
                prevX = X;
                prevY = Y;
                thisIntersects = nextIntersects;
            }
        }
    }

    /**
     * ${@inheritDoc}.
     */
    @Override
    public ViewBox getViewBox() {
        return myViewBox;
    }

    /**
     * @param aSelectedNodes the currently selected nodes to edit.
     */
    void setSelectedNodes(final List<Node> aSelectedNodes) {
        tmpDrawingSelectedNodes = aSelectedNodes;
    }

    /**
     * 
     * @param aSelectedWays the currently selected ways to edit.
     */
    void setSelectedWays(final List<Way> aSelectedWays) {
        tmpDrawingSelectedWays = aSelectedWays;
    }

    /**
     * Get our current Preferences object
     * 
     * @return a Preferences instance
     */
    public Preferences getPrefs() {
        return prefs;
    }

    /**
     * Set the current Preferences object for this Map and any thing that needs changing
     * 
     * @param ctx Android Context
     * @param aPreference the new Preferences
     */
    public void setPrefs(Context ctx, final Preferences aPreference) {
        prefs = aPreference;
        TileLayerServer.setBlacklist(prefs.getServer().getCachedCapabilities().getImageryBlacklist());
        synchronized (mOverlays) {
            if (mOverlays.size() > 1) { // createOverlays hasn't run yet if 1 or less
                final TileLayerServer backgroundTS = TileLayerServer.get(ctx, prefs.backgroundLayer(), true);
                if (backgroundLayer != null) {
                    backgroundLayer.setRendererInfo(backgroundTS);
                    ImageryOffsetUtils.applyImageryOffsets(ctx, backgroundTS, getViewBox());
                }
                final TileLayerServer overlayTS = TileLayerServer.get(ctx, prefs.overlayLayer(), true);
                if (overlayTS != null) {
                    if (overlayLayer != null) {
                        overlayLayer.setRendererInfo(overlayTS);
                        ImageryOffsetUtils.applyImageryOffsets(ctx, overlayTS, getViewBox());
                    } else if (activeOverlay(overlayTS.getId())) {
                        overlayLayer = new MapTilesOverlayLayer(this);
                        overlayLayer.setRendererInfo(overlayTS);
                        mOverlays.add(1, overlayLayer);
                        ImageryOffsetUtils.applyImageryOffsets(ctx, overlayTS, getViewBox());
                    }
                }
            }
        }
        showIcons = prefs.getShowIcons();
        showWayIcons = prefs.getShowWayIcons();
        iconCache.clear();
        areaIconCache.clear();
        alwaysDrawBoundingBoxes = prefs.getAlwaysDrawBoundingBoxes();
    }

    /**
     * Check for a overlay that we actually have to display
     * 
     * @param layerId the layer id
     * @return true if we should allocate a layer
     */
    private boolean activeOverlay(String layerId) {
        return !(TileLayerServer.LAYER_NONE.equals(layerId) || TileLayerServer.LAYER_NOOVERLAY.equals(layerId));
    }

    public void updateProfile() {
        // changes when profile changes
        nodeTolerancePaint = DataStyle.getCurrent(DataStyle.NODE_TOLERANCE).getPaint();
        nodeTolerancePaint2 = DataStyle.getCurrent(DataStyle.NODE_TOLERANCE_2).getPaint();
        wayTolerancePaint = DataStyle.getCurrent(DataStyle.WAY_TOLERANCE).getPaint();
        wayTolerancePaint2 = DataStyle.getCurrent(DataStyle.WAY_TOLERANCE_2).getPaint();
        labelBackground = DataStyle.getCurrent(DataStyle.LABELTEXT_BACKGROUND).getPaint();
        FeatureStyle fs = DataStyle.getCurrent(DataStyle.LABELTEXT_NORMAL);
        textPaint = fs.getPaint();
    }

    void setOrientation(final float orientation) {
        this.orientation = orientation;
    }

    void setLocation(Location location) {
        displayLocation = location;
    }

    void setDelegator(final StorageDelegator delegator) {
        this.delegator = delegator;
    }

    public void setViewBox(final ViewBox viewBox) {
        myViewBox = viewBox;
        try {
            myViewBox.setRatio(this, (float) getWidth() / getHeight(), false);
        } catch (OsmException e) {
            Log.d(DEBUG_TAG, "setViewBox got " + e.getMessage());
        }
    }

    public void showCrosshairs(float x, float y) {
        showCrosshairs = true;
        // store as lat lon for redraws on translation and zooming
        crosshairsLat = GeoMath.yToLatE7(getHeight(), getWidth(), getViewBox(), y);
        crosshairsLon = GeoMath.xToLonE7(getWidth(), getViewBox(), x);
    }

    public void hideCrosshairs() {
        showCrosshairs = false;
    }

    /**
     * You can add/remove/reorder your Overlays using the List of {@link MapViewLayer}. The first (index 0) Overlay gets
     * drawn first, the one with the highest as the last one.
     */
    public List<MapViewLayer> getOverlays() {
        return mOverlays;
    }

    /**
     * ${@inheritDoc}.
     */
    @Override
    public int getZoomLevel() {
        return zoomLevel;
    }

    /**
     * This calculates the best tile zoom level to use (not the actual zoom level of the map!)
     * 
     * @param canvas Canvas we are drawing on
     * @return the tile zoom level
     */
    private int calcZoomLevel(Canvas canvas) {
        final TileLayerServer s = getBackgroundLayer().getTileLayerConfiguration();
        if (s == null || !s.isMetadataLoaded()) {// protection on startup
            return 0;
        }

        // Calculate lat/lon of view extents
        final double latBottom = getViewBox().getBottom() / 1E7; // GeoMath.yToLatE7(viewPort.height(), getViewBox(),
                                                                 // viewPort.bottom) / 1E7d;
        final double lonRight = getViewBox().getRight() / 1E7; // GeoMath.xToLonE7(viewPort.width() , getViewBox(),
                                                               // viewPort.right ) / 1E7d;
        final double latTop = getViewBox().getTop() / 1E7; // GeoMath.yToLatE7(viewPort.height(), getViewBox(),
                                                           // viewPort.top ) / 1E7d;
        final double lonLeft = getViewBox().getLeft() / 1E7; // GeoMath.xToLonE7(viewPort.width() , getViewBox(),
                                                             // viewPort.left ) / 1E7d;

        // Calculate tile x/y scaled 0.0 to 1.0
        final double xTileRight = (lonRight + 180d) / 360d;
        final double xTileLeft = (lonLeft + 180d) / 360d;
        final double yTileBottom = (1d - Math.log(Math.tan(Math.toRadians(latBottom)) + 1d / Math.cos(Math.toRadians(latBottom))) / Math.PI) / 2d;
        final double yTileTop = (1d - Math.log(Math.tan(Math.toRadians(latTop)) + 1d / Math.cos(Math.toRadians(latTop))) / Math.PI) / 2d;

        // Calculate the ideal zoom to fit into the view
        final double xTiles = (canvas.getWidth() / (xTileRight - xTileLeft)) / s.getTileWidth();
        final double yTiles = (canvas.getHeight() / (yTileBottom - yTileTop)) / s.getTileHeight();
        final double xZoom = Math.log(xTiles) / Math.log(2d);
        final double yZoom = Math.log(yTiles) / Math.log(2d);

        // Zoom out to the next integer step
        int zoom = (int) Math.floor(Math.max(0, Math.min(xZoom, yZoom)));
        // zoom = Math.min(zoom, s.getMaxZoomLevel());

        return zoom;
    }

    public Location getLocation() {
        return displayLocation;
    }

    /**
     * Set the flag that determines if the arror is just an outline or not
     * 
     * @param follow
     */
    public void setFollowGPS(boolean follow) {
        isFollowingGPS = follow;
    }

    /**
     * Return a list of the names of the currently used layers
     * 
     * @return a List containg the currently in use imagery names
     */
    public List<String> getImageryNames() {
        List<String> result = new ArrayList<>();
        synchronized (mOverlays) {
            for (MapViewLayer osmvo : mOverlays) {
                if (osmvo != null && osmvo instanceof MapTilesLayer) {
                    result.add(((MapTilesLayer) osmvo).getTileLayerConfiguration().getName());
                }
            }
        }
        return result;
    }

    /**
     * @return the iconRadius
     */
    public int getIconRadius() {
        return iconRadius;
    }

    void setTracker(TrackerService tracker) {
        this.tracker = tracker;
    }

    public TrackerService getTracker() {
        return this.tracker;
    }
}
