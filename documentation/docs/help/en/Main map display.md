# Main Vespucci Screen

The main Vespucci map screen is your normal starting point for interacting with the application. It can be in two modes

####  ![Locked](../images/locked.png) locked
In this mode modifying the OpenStreetMap data is disabled, you can however use all the menus and other functions, for example down- or up-load data. The main reason to use this mode is to pan and zoom in high density areas, or to avoid accidental changes when pocketing your device. 

Tapping the lock icon will toggle the lock.

####  ![Unlocked](../images/unlocked.png) unlocked
In this mode you can add and change the geometry and tags of OpenStreetMap data. *You will still need to zoom until editing is enabled.*

If a small "T" is displayed on the lock icon you are in "Tag editing only" mode and will not be able to create new objects of change geometries, a small "I" indicates indoor mapping mode. A long press on the icon will display a menu allowing you to switch between editing modes.

The lock icon will always be located in the upper left corner of your devices screen. The placement of the following controls will depend on the size, orientation and age of your device, and may be in the top or bottom bar, or in a menu. 

### Map and Data display

Once you have downloaded some OSM data you will see it displayed over a configurable background layer. The default background is the standard "Mapnik" style map provided by openstreetmap.org, however you can choose a different background or none from the Preferences. The style of the OSM data displayed on top of the background layer can be fully customized, however the standard "Color round nodes" or for "Pen round nodes" should work for most situations, the latter has smaller "touch areas" mainly for working with a pen. Depending on the device it may however work better for you than the default.

Note: the map tiles are cached on device and only deleted if explicitly flushed. In principle you can pre-download the tiles for the area you want to work on and so spare yourself the need to access them online while outside. 

### Layer control

Vespucci supports a tiled background imagery layer, a tiled overlay background layer, a grid/scale layer, a task layer, a photo layer, a GeoJSON layer, a GPX/GPS layer and, naturally, an OSM data layer. Tapping the layer control (upper right corner) will display the layer dialog).

Currently it is not possible to change the ordering or add more than one layer of a specific type. The layer dialog supports the following: 

* Hide/Show button turns drawing of the layer off/on. Hiding a layer doesn't free any resources associated with the layer.
* Zoom to extent. Zooms and pans the screen so that the whole extent of the layer is displayed, if the extent cannot be determined this will zoom out to the full web-mercator coverage. Note: on the data layer this may not be particularly useful if you have downloaded areas that are far apart.
* Menu button.
    * Tile based layers: 
        * Select imagery. Same contents as on the preference screen, if multiple layers have been used, a most-recently-used list will be displayed above this menu entry, allowing quick layer switching. Selecting the "None" entry from the list will disable the layer, and requires re-enabling it via the "+" button on the layer dialog.
        * Flush tile cache. Moved here from main menu.
        * Background properties. Set contrast of layer, moved here from main menu.
    * GeoJSON layer. 
        * Change style. Show the layer styling dialog.
        * Discard. Delete the layer including any saved state.
    * GPX layer. The GPX layer is currently mainly controlled via the entries in the GPS menu.
        * Change style. Show the layer styling dialog.
    * Photo, Grid and Task layers.
        * Disable. Turn this layer off, needs to be re-enbled via preferences. For the tasks and photo layers this will free resources if the app is exited and re-started.
* "+" button: 
    * Load GeoJSON layer. Loads a GeoJSON layer from a file, any existing one will be replaced.
    * Map background. Shown if the background layer has been disabled, allows the same selection as from the preferences screen.
    * Map overlay. Shown if the overlay layer has been disabled, allows the same selection as from the preferences screen.

Besides the background layer you can add a "overlay" layer between the background and the OSM data, for example a layer showing buildings without an address.

### Highlighting of data issues

With the standard map style certain data issues will be highlighted in magenta, these are

* objects with 'fixme' and 'todo' tags (case insensitive)
* ways with 'highway=road'
* ways with 'highway' set to one of: motorway, motorway_link, trunk, trunk_link, primary, primary_link, secondary, secondary_link, tertiary, residential, unclassified, living_street and no 'name' or 'ref' tag
* relations with no 'type' tag
* certain points of interest that haven't been edited of verified in the last year 

### Indoor mode
   
In indoor mode the displayed level can be changed with the up/down buttons, tapping the level display button will show suitable objects that doesn’t have a level tag, tapping again will revert to the level selector.

Elements that are not on the current level are drawn in a light grey, this works best on an uncluttered background map, for example the Thunderforest Landscape map. Newly created elements will automatically have the current level assigned in the property editor.

## Available Actions

### ![Undo](../images/undolist_undo.png) Undo

Tapping the icon once will undo the last operation. A long press will display a list of the operations since the last save, if you have undone anything a corresponding "redo" checkpoint will be displayed. *Some operations consist of multiple simpler actions that will be listed as individual items.*

### ![Camera](../images/camera.png) Camera

Start a camera app, add the resulting photograph to the photo layer is enabled. The photograph itself should be stored in the Vespucci/Pictures 
directory, however this depends on the specific camera app.

### ![GPS](../images/menu_gps.png) GPS

The "on-map" GPS button duplicates the function of the "Follow GPS position" menu entry. When this is activated the GPS arrow will be displayed as an outline.

 * **Show location** - show arrow symbol at current position
 * **Follow GPS position** - pan and center screen to follow the GPS position
 * **Goto GPS position** - go to and zoom in to the current position
 * **Start GPS track** - start recording a GPX track
 * **Pause GPS track** - pause recording the current GPX track
 * **Clear GPS track** - clear the current GPX track
 * **Track management...** - manage existing tracks
    * **Upload GPS track** - upload the current GPX track *(requires network connectivity)*
    * **Export GPS track** - export track to file
    * **Import GPS track** - import track from file
 * **Goto start of track** - center the map on the first track point
 

### ![Transfer](../images/menu_transfer.png) Transfer

Select either the transfer icon ![Transfer](../images/menu_transfer.png) or the "Transfer" menu item. This will display seven or eight options:

 * **Download current view** - download the area visible on the screen and replace any existing data *(requires network connectivity)*
 * **Add current view to download** - download the area visible on the screen and merge it with existing data *(requires network connectivity)*
 * **Download at other location** - shows a form that lets you enter coordinates, search for a location or enter coordinates directly, and then download an area around that location *(requires network connectivity)*
 * **Upload data to OSM server** - upload edits to OpenStreetMap, the entry is disabled if you haven't changed anything yet, or there is no network available *(requires authentication)* *(requires network connectivity)*
 * **Close current changeset** - manually close the current changeset *(only available if a changeset is open)*
 * **Auto download** - download an area around the current geographic location automatically *(requires network connectivity)* *(requires GPS)*
 * **File...** - saving and loading OSM data to/from on device files.
    * **Export changes to OSC file** - write a ".osc" format file containing the current edits
    * **Read from JOSM file...** - read a JOSM compatible XML format file
    * **Save to JOSM file...** - save as a JOSM compatible XML format file
 * **Note/Bugs...** - down- and uploading Notes/Bugs
    * **Download bugs for current view** - download Notes/Bugs for the area visible on the screen
    * **Upload all** - upload all new or modified Notes/Bugs
    * **Clear** - remove all bugs from storage
    * **Auto download** - download Notes/Bugs around the current geographic location automatically *(requires network connectivity)* *(requires GPS)*
    * **File...** - save Notes/Bugs data to on device storage
        * **Save all Notes...** - write a ".osn" format file containing all downloaded notes
        * **Save new and changed Notes...** - write a ".osn" format file containing all new and changed notes
        * **Load custom tasks...** - read custom tasks in a simplified Osmose JSON format
        * **Save open custom tasks...** - save open custom tasks in a simplified Osmose JSON format

### ![Preferences](../images/menu_config.png) Preferences

Show the user preference screens. The settings are split into two sets: the first screen contains the more commonly used preferences, the "Advanced preferences" contains the less used ones. 

### ![Tools](../images/menu_tools.png) Tools

 * **Align background** - align the current background layer, this can be done manually or from the image alignment database *(requires network connectivity)*
 * **Apply stored offset to imagery** - apply stored offset, if it exists, for the current background layer 
 * **Add imagery from OAM** - query OAM for layers in the current map view and display a list allowing to add them to the local configuration
 * **More imagery tools**
    * **Update imagery layer configuration** - download a current version of the imagery layer configuration. *(requires network connectivity)*
    * **Flush all tile caches** - empty all on device tile caches. 
 * **Reset OAuth** - reset the OAuth tokens, this will force reauthorisation the next time you upload.
 * **Authorise OAuth** - start authorisation process immediately. *(requires network connectivity)*

### ![Find](../images/ic_menu_search_holo_light.png) Find

Search for a location and pan to it with the OpenStreetMap Nominatim or Photon service *(requires network connectivity)*

### Tag-Filter *(checkbox)*

Enable the tag based filter, the filter can be configured by tapping the filter button on the map display.

### Preset-Filter *(checkbox)*

Enable the preset based filter, the filter can be configured by tapping the filter button on the map display.

### Share position

Share the current position (center of the displayed map) with other apps on the device.

### ![Help](../images/menu_help.png) Help

Start the on device help browser.

### Authors and licenses

Some information on the licence of Vespucci itself, specific components and a list of all known contributors to the app.

### Debug

Internal information on the app and a button to force submission of a crash dump.
