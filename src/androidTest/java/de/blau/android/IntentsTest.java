package de.blau.android;

import java.io.IOException;
import java.util.ArrayList;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.orhanobut.mockwebserverplus.MockWebServerPlus;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.Instrumentation.ActivityMonitor;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.LargeTest;
import de.blau.android.osm.BoundingBox;
import de.blau.android.osm.Node;
import de.blau.android.prefs.AdvancedPrefDatabase;
import de.blau.android.prefs.Preferences;
import de.blau.android.resources.TileLayerServer;
import de.blau.android.tasks.OsmoseBug;
import de.blau.android.tasks.Task;
import okhttp3.HttpUrl;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class IntentsTest {
	
	MockWebServerPlus mockServer = null;
	MockWebServerPlus mockServerOsmose = null;
	Context context = null;
	ActivityMonitor monitor = null;
	ActivityMonitor mainMonitor = null;
	AdvancedPrefDatabase prefDB = null;
	Instrumentation instrumentation = null;
	Main main = null;
	
    @Rule
    public ActivityTestRule<Main> mActivityRule = new ActivityTestRule<>(Main.class);

    @Before
    public void setup() {
    	instrumentation = InstrumentationRegistry.getInstrumentation();
		context = instrumentation.getTargetContext();
 		main = mActivityRule.getActivity();
 		App.getDelegator().reset(false);
 		App.getTaskStorage().reset();
		Preferences prefs = new Preferences(context);
		prefs.setBackGroundLayer(TileLayerServer.LAYER_NONE); // try to avoid downloading tiles
		main.getMap().setPrefs(main, prefs);
    	mockServer = new MockWebServerPlus();
 		HttpUrl mockBaseUrl = mockServer.server().url("/api/0.6/");
		System.out.println("mock api url " + mockBaseUrl.toString());
 		prefDB = new AdvancedPrefDatabase(context);
 		prefDB.deleteAPI("Test");
		prefDB.addAPI("Test", "Test", mockBaseUrl.toString(), null, null, "user", "pass", null, false);
 		prefDB.selectAPI("Test");
 		mockServerOsmose = new MockWebServerPlus();
 		mockBaseUrl = mockServerOsmose.server().url("/en/api/0.2/");
 		prefs.putString(R.string.config_osmoseServer_key, mockBaseUrl.scheme()+"://"+mockBaseUrl.host()+":"+mockBaseUrl.port()+"/");
 		prefs.setBugsEnabled(true);
 	   	monitor = instrumentation.addMonitor(GeoUrlActivity.class.getName(), null, false);
 	   	// mainMonitor = instrumentation.addMonitor(Main.class.getName(), null, false);
		TestUtils.grantPermissons();
		TestUtils.dismissStartUpDialogs(main);
    }
    
    @After
    public void teardown() {
    	instrumentation.removeMonitor(monitor);
    	// instrumentation.removeMonitor(mainMonitor);
		try {
			mockServer.server().shutdown();
			mockServerOsmose.server().shutdown();
		} catch (IOException ioex) {
			System.out.println("Stopping mock webserver exception " + ioex);
		}
    }
    
    @Test
	public void geo() {
    	mockServer.enqueue("capabilities1");
    	mockServer.enqueue("download1");
    	mockServer.enqueue("notesDownload1");
    	mockServerOsmose.enqueue("osmoseDownload");
    	// <bounds minlat="47.3892400" minlon="8.3844600" maxlat="47.3911300" maxlon="8.3879800"/
		Uri	uri = Uri.parse("geo:47.3905,8.385?z=15");
		main.startActivity(new Intent(Intent.ACTION_VIEW, uri));
		GeoUrlActivity geo = (GeoUrlActivity) instrumentation.waitForMonitorWithTimeout(monitor, 30000);
		Assert.assertNotNull(geo);

		
    	// instrumentation.waitForMonitorWithTimeout(mainMonitor, 30000);
		// there currently doesn't seem to be a reasonable way to wait until we have downloaded 
    	try {
			Thread.sleep(5000);
		} catch (InterruptedException e1) {
		}
		Assert.assertNotNull(App.getDelegator().getOsmElement(Node.NAME, 101792984));
	   	ArrayList<Task> tasks = App.getTaskStorage().getTasks();
    	// 
    	Assert.assertEquals(151, tasks.size()); // combined count of OSMOSE bugs and notes
    }
}
