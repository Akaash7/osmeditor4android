package de.blau.android.gpx;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.orhanobut.mockwebserverplus.MockWebServerPlus;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiSelector;
import de.blau.android.App;
import de.blau.android.Main;
import de.blau.android.Map;
import de.blau.android.SignalHandler;
import de.blau.android.TestUtils;
import de.blau.android.osm.Track;
import de.blau.android.osm.Track.TrackPoint;
import de.blau.android.osm.Track.WayPoint;
import de.blau.android.osm.ViewBox;
import de.blau.android.prefs.AdvancedPrefDatabase;
import de.blau.android.prefs.Preferences;
import de.blau.android.resources.TileLayerServer;
import de.blau.android.util.DateFormatter;
import de.blau.android.util.GeoMath;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class GpxTest {

    public static final int TIMEOUT    = 115;
    MockWebServerPlus       mockServer = null;
    Context                 context    = null;
    AdvancedPrefDatabase    prefDB     = null;
    Main                    main       = null;
    UiDevice                device     = null;

    @Rule
    public ActivityTestRule<Main> mActivityRule = new ActivityTestRule<>(Main.class);

    @Before
    public void setup() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        main = mActivityRule.getActivity();
        Preferences prefs = new Preferences(context);
        prefs.setBackGroundLayer(TileLayerServer.LAYER_NONE); // try to avoid downloading tiles
        main.getMap().setPrefs(main, prefs);
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        TestUtils.grantPermissons();
        TestUtils.dismissStartUpDialogs(main);
    }

    @After
    public void teardown() {
    }

    @Test
    public void recordSaveAndImportGpx() {
        TestUtils.zoomToLevel(main, 19);
        TestUtils.clickButton("de.blau.android:id/follow", false);
        TestUtils.clickResource(device, true, "de.blau.android:id/menu_gps", true);
        TestUtils.clickText(device, false, "Start GPS track", true);
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        InputStream is = loader.getResourceAsStream("20110513_121244-tp.gpx");
        Track track = new Track(main);
        track.importFromGPX(is);
        main.getTracker().getTrack().reset(); // clear out anything saved
        final CountDownLatch signal = new CountDownLatch(1);
        TestUtils.injectLocation(main, track.getTrack(), 1000, new SignalHandler(signal));
        try {
            signal.await(TIMEOUT, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Assert.fail(e.getMessage());
        }
        List<TrackPoint> recordedTrack = main.getTracker().getTrack().getTrack();
        Assert.assertEquals(track.getTrack().size(), recordedTrack.size());
        TestUtils.clickResource(device, true, "de.blau.android:id/menu_gps", true);
        TestUtils.clickText(device, false, "Pause GPS track", true);
        compareTrack(track, recordedTrack);
        TestUtils.clickResource(device, true, "de.blau.android:id/menu_gps", true);
        TestUtils.clickText(device, false, "Track management...", true);
        String filename = DateFormatter.getFormattedString("yyyy-MM-dd'T'HHmm"); // note this is a bit flaky
        TestUtils.clickText(device, false, "Export GPS track", true);
        device.waitForWindowUpdate(null, 5000);
        TestUtils.clickResource(device, true, "de.blau.android:id/menu_gps", true);
        TestUtils.clickText(device, false, "Track management...", true);
        TestUtils.clickText(device, false, "Import GPS track", true);
        TestUtils.clickText(device, false, filename, true);
        recordedTrack = main.getTracker().getTrack().getTrack(); // has been reloaded
        compareTrack(track, recordedTrack);
    }
    
    @Test
    public void importWayPoints() {
        main.getTracker().getTrack().reset(); // clear out anything saved
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        InputStream is = loader.getResourceAsStream("20110513_121244-tp.gpx");
        main.getTracker().getTrack().importFromGPX(is);
        Assert.assertEquals(112, main.getTracker().getTrack().getTrack().size());
        Assert.assertEquals(79, main.getTracker().getTrack().getWayPoints().length);
        WayPoint foundWp = null;
        for (WayPoint wp:main.getTracker().getTrack().getWayPoints()) {
            if (doubleEquals(47.3976189, wp.getLatitude()) && doubleEquals(8.3770144, wp.getLongitude())) {
                foundWp = wp;
                break;
            }
        }
        Assert.assertNotNull(foundWp);
        Map map = main.getMap();
        ViewBox viewBox = map.getViewBox();
        App.getLogic().setZoom(map, 19);
        viewBox.moveTo(map, foundWp.getLon(), foundWp.getLat());
        map.invalidate();
      
        if (App.getLogic().isLocked()) {
            UiObject lock = device.findObject(new UiSelector().resourceId("de.blau.android:id/floatingLock"));
            try {
                lock.click();
            } catch (UiObjectNotFoundException e) {
                Assert.fail(e.getMessage());
            }
        }
        
        float x = GeoMath.lonE7ToX(map.getWidth(), viewBox, foundWp.getLon());
        float y = GeoMath.latE7ToY(map.getHeight(), map.getWidth(), viewBox, foundWp.getLat());
        TestUtils.clickAt(x, y);
        
        TestUtils.clickText(device, true, "Create osm object from", true);
        Assert.assertTrue(TestUtils.findText(device, false, "Church"));        
    }
    
    boolean doubleEquals(double d1, double d2) {
        double epsilon = 0.0000001D;
        return d1 < d2 + epsilon && d1 > d2 - epsilon;
    }

    private void compareTrack(Track track, List<TrackPoint> recordedTrack) {
        Assert.assertEquals(track.getTrack().size(), recordedTrack.size());
        int i = 0;
        for (TrackPoint tp : track.getTrackPoints()) {
            TrackPoint recordedTrackPoint = recordedTrack.get(i);
            Assert.assertEquals(tp.getLatitude(), recordedTrackPoint.getLatitude(), 0.00000001);
            Assert.assertEquals(tp.getLongitude(), recordedTrackPoint.getLongitude(), 0.00000001);
            Assert.assertEquals(tp.getAltitude(), recordedTrackPoint.getAltitude(), 0.00000001);
            i++;
        }
    }
}
