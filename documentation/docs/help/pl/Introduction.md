# Wprowadzenie do Vespucci

Vespucci is a full featured OpenStreetMap editor that supports most operations that desktop editors provide. It has been tested successfully on Google's Android 2.3 to 7.0 and various AOSP based variants. A word of caution: while mobile device capabilities have caught up with their desktop rivals, particularly older devices have very limited memory available and tend to be rather slow. You should take this in to account when using Vespucci and keep, for example, the size of the areas you are editing to a reasonable size. 

## Pierwsze kroki

 Po włączeniu Vespucci pokazuje panel "Pobierz inny obszar"/"Wczytaj obszar". Jeśli widzisz współrzędne na ekranie i chcesz pobrać dane od razu, możesz wybrać odpowiednią opcję i ustawić promień wokół miejsca z którego chcesz pobierać dane. Nie wybieraj zbyt dużych obszarów używając słabszych urządzeń. 

Alternatywą dla powyższego jest wyłączenie panelu przez naciśnięcie "Pokaż mapę", a następnie przesunięcie i ustawienie przybliżenia do miejsca którego dane chcesz pbrać by je edytować. (zobacz: "Edycja z Vesspuci")

## Edycja z Vespucci

W zależności od wielkości ekranu oraz wieku twojego urządzenia opcje edycji mogą być dostępne bezpośrednio jako ikony na górnym pasku, przez rozwijalne menu po prawej stronie górnego paska, przez ikony dolnego paska (jeśli jest wyświetlany) lub przez klawisz menu.

### Pobieranie danych OSM

Select either the transfer icon ![Transfer](../images/menu_transfer.png) or the "Transfer" menu item. This will display seven options:

* **Download current view** - download the area visible on the screen and replace any existing data *(requires network connectivity)*
* **Add current view to download** - download the area visible on the screen and merge it with existing data *(requires network connectivity)*
* **Download at other location** - shows a form that allows you to enter coordinates, search for a location or use the current position, and then download an area around that location *(requires network connectivity)*
* **Upload data to OSM server** - upload edits to OpenStreetMap *(requires authentication)* *(requires network connectivity)*
* **Auto download** - download an area around the current geographic location automatically *(requires network connectivity)* *(requires GPS)*
* **File...** - saving and loading OSM data to/from on device files.
* **Note/Bugs...** - download (automatically and manually) OSM Notes and "Bugs" from QA tools (currently OSMOSE) *(requires network connectivity)*

The easiest way to download data to the device is to zoom and pan to the location you want to edit and then to select "Download current view". You can zoom by using gestures, the zoom buttons or the volume control buttons on the device.  Vespucci should then download data for the current view. No authentication is required for downloading data to your device.

### Edytowanie

#### Lock, unlock, "tag editing only"

To avoid accidental edits Vespucci starts in "locked" mode, a mode that only allows zooming and moving the map. Tap the ![Locked](../images/locked.png) icon to unlock the screen. 

A long press on the lock icon will enable "Tag editing only" mode which will not allow you to edit the geometry of objects or move them, this mode is indicated with a slightly different white lock icon. You can however create new nodes and ways with a long press as normal.

#### Singe tap, double tap, and long press

By default, selectable nodes and ways have an orange area around them indicating roughly where you have to touch to select an object. You have three options:

* Single tap: Selects object. 
    * An isolated node/way is highlighted immediately. 
    * However, if you try to select an object and Vespucci determines that the selection could mean multiple objects it will present a selection menu, enabling you to choose the object you wish to select. 
    * Selected objects are highlighted in yellow. 
    * For further information see [Node selected](../en/Node%20selected.md) and [Way selected](../en/Way%20selected.md).
* Double tap: Start [Multiselect mode](../en/Multiselect.md)
* Long press: Creates a "crosshair", enabling you to add nodes, see below and [Creating new objects](../en/Creating new objects.md)

Dobrą praktyką jest przybliżanie widoku gdy edytujesz obszar o dużej ilości elementów.

System cofania i ponawiania zmian w Vespucci jest dobrze dopracowany, więc nie bój się eksperymentować, jednakże nie wysyłaj testowych danych na serwer.

#### Selecting / De-selecting (single tap and "selection menu")

Touch an object to select and highlight it. Touching the screen in an empty region will de-select. If you have selected an object and you need to select something else, simply touch the object in question, there is no need to de-select first. A double tap on an object will start [Multiselect mode](../en/Multiselect.md).

Note that if you try to select an object and Vespucci determines that the selection could mean multiple objects (such as a node on a way or other overlapping objects) it will present a selection menu: Tap the object you wish to select and the object is selected. 

Selected objects are indicated through a thin yellow border. The yellow border may be hard to spot, depending on map background and zoom factor. Once a selection has been made, you will see a notification confirming the selection.

You can also use menu items: For further information see [Node selected](../en/Node%20selected.md) and [Way selected](../en/Way%20selected.md).

#### Selected objects: Editing tags

A second touch on the selected object opens the tag editor and you can edit the tags associated with the object.

Note that for overlapping objects (such as a node on a way) the selection menu comes back up for a second time. Selecting the same object brings up the tag editor; selecting another object simply selects the other object.

#### Selected objects: Moving a Node or Way

Once you have selected an object, it can be moved. Note that objects can be dragged/moved only when they are selected. Simply drag near (i.e. within the tolerance zone of) the selected object to move it. If you select the large drag area in the preferences, you get a large area around the selected node that makes it easier to position the object. 

#### Adding a new Node/Point or Way (long press)

Long press where you want the node to be or the way to start. You will see a black "crosshair" symbol. 
* If you want to create a new node (not connected to an object), click away from existing objects.
* If you want to extend a way, click within the "tolerance zone" of the way (or a node on the way). The tolerance zone is indicated by the areas around a node or way.

Once you can see the crosshair symbol, you have these options:

* Touch in the same place.
    * If the crosshair is not near a node, touching the same location again creates a new node. If you are near a way (but not near a node), the new node will be on the way (and connected to the way).
    * If the crosshair is near a node (i.e. within the tolerance zone of the node), touching the same location just selects the node (and the tag editor opens. No new node is created. The action is the same as the selection above.
* Touch another place. Touching another location (outside of the tolerance zone of the crosshair) adds a way segment from the original position to the current position. If the crosshair was near a way or node, the new segment will be connected to that node or way.

Simply touch the screen where you want to add further nodes of the way. To finish, touch the final node twice. If the final node is  located on a way or node, the segment will be connected to the way or node automatically. 

You can also use a menu item: See [Creating new objects](../en/Creating new objects.md) for more information.

#### Ulepszanie Geometrii Linii

Jeśli odpowiednio oddalisz mapę, zauważysz mały "x" na środku odcinków linii które są odpowiednio długie. Przeciągnięcie "x" utworzy nowy węzeł linii w tym miejscu. Uwaga: aby uniknąć przypadkowego dodawania węzłów, tolerancja nacisku dla tej czynności jest dość mała.

#### Wytnij, Kopiuj & Wklej

Możesz skopiować lub wyciąć zaznaczone węzły i linie, by później wkleić je raz lub wiele razy do nowych lokalizacji. Wycinanie zachowuje osm id oraz wersję obiektu. By wkleić długo naciśnij docelowe miejsce (zobaczysz celownik wskazujący dokładnie gdzie obiekt się pojawi), a następnie wybierz "Wklej" z menu.

#### Efektywne Dodawanie Adresów

Vespucci has an "add address tags" function that tries to make surveying addresses more efficient. It can be selected:

* after a long press: Vespucci will add a node at the location and make a best guess at the house number and add address tags that you have been lately been using. If the node is on a building outline it will automatically add a "entrance=yes" tag to the node. The tag editor will open for the object in question and let you make any necessary further changes.
* in the node/way selected modes: Vespucci will add address tags as above and start the tag editor.
* in the tag editor.

Przewidywanie numerów adresowych zazwyczaj wymaga przynajmniej dwóch numerów po obu stronach drogi by zostać skutecznie użyta, im więcej numerów już zmapowanych tym lepsza dokładność.

Zastanów się nad użyciem trybu "Auto-pobierania" podczas użytkowania tej funkcji.  

#### Dodawanie ograniczeń skrętu

Vespucci has a fast way to add turn restrictions, if necessary it will split ways automatically and, if necessary, ask you to re-select elements. 

* select a way with a highway tag (turn restrictions can only be added to highways, if you need to do this for other ways, please use the generic "create relation" mode)
* select "Add restriction" from the menu
* select the "via" node or way (only possible "via" elements will have the touch area shown)
* select the "to" way (it is possible to double back and set the "to" element to the "from" element, Vespucci will assume that you are adding an no_u_turn restriction)
* set the restriction type in the property editor

### Vespucci w trybie "zablokowanym"

Gdy czerwona kłódka jest widoczna wszystkie nie-edytujące funkcje są dostępne. Dodatkowo długie naciśnięcie na lub obok obiektu pokaże dokładne informacje o nim, o ile jest to obiekt z OSM.

### Zapisywanie Zmian

*(wymagane jest połączenie z Internetem)*

Kliknij ten sam przycisk lub pozycję w menu, który wybrałeś by pobrać dane i wybierz "Wyślij dane na serwer OSM"

Vespucci obsługuje autoryzację OAuth oraz klasyczną metodę podawania loginu i hasła. OAuth jest preferowane gdyż unika wysyłanie niezaszyfrowanego hasła.

Niedawno zainstalowane wersje Vespucci mają domyślanie włączona autoryzację OAuth. Przy pierwszej próbie wysłania zmodyfikowanych danych, ukaże się strona internetowa OSM. Po zalogowaniu (poprzez szyfrowane połączenie) zostaniesz zapytany/zapytana o autoryzację dla Vespucci by móc za jego pomocą edytować dane. Jeśli chcesz lub musisz uwierzytelnić OAuth przed edycją istnieje taka opcja w menu "Narzędzia".

Jeśli chcesz zapisać swoją pracą, ale nie masz połączenia z internetem możesz zapisać ją do pliku .osm kompatybilnego z JOSM. Następnie po uzyskaniu połączenia możesz wysłać dane za pomocą Vespucci lub JOSM. 

#### Rozwiązywanie konfliktujących zmian

Vespucci posiada prostą funkcję rozwiązywania konfliktów edycji. Jednakże jeśli podejrzewasz że istnieją poważne problemy z twoim zestawem zmian, wyeksportuj go do pliku .osc ("Eksport" w menu "Transfer") i spróbuj naprawić je w JOSM. Zobacz dalsze wskazania na [conflict resolution](../en/Conflict resolution.md).  

# Użycie GPS

Możesz użyć Vespucci by utworzyć ślad GPX i odczytać go na swoim urządzeniu. Co więcej możesz wyświetlić swoją aktualną pozycję GPS (włączając opcję "Pokaż lokalizację" w menu GPS) i/lub włączyć centrowanie na niej oraz podążanie za pozycją GPS (włączając opcję "Podążaj za pozycją GPS" w menu GPS).  

If you have the latter set, moving the screen manually or editing will cause the "follow GPS" mode to be disabled and the blue GPS arrow will change from an outline to a filled arrow. To quickly return to the "follow" mode, simply touch the arrow or re-check the option from the menu.

## Notatki i Błędy

Vespucci umożliwia pobieranie, komentowanie i zamykanie Notatek OSM (poprzednio Błędów OSM) oraz "Błędów" wykrywanych przez [OSMOSE quality assurance tool](http://osmose.openstreetmap.fr/en/map/). Obydwa mogą zostać pobrane manualnie lub przez funkcję auto-pobierania. Zmienione i zamknięte wpisy można wysyłać pojedynczo od razu lub wszystkie naraz po pewnym czasie.

On the map the Notes and bugs are represented by a small bug icon ![Bug](../images/bug_open.png), green ones are closed/resolved, blue ones have been created or edited by you, and yellow indicates that it is still active and hasn't been changed. 

Błędy OSMOSE po zaznaczeniu dają możliwość wybrania adresu do obiektu, dotknięcie adresu wybierze obiekt, wyśrodkuje ekran na nim i pobierze obszar potrzebny do jego edycji jeśli zachodzi taka potrzeba. 

### Filtering

Besides globally enabling the notes and bugs display you can set a coarse grain display filter to reduce clutter. In the "Advanced preferences" you can individually select:

* Notes
* Osmose error
* Osmose warning
* Osmose minor issue


## Dostosowywanie Vespucci

### Opcje które mógłbyś/mogłabyś chcieć zmienić

* Background layer
* Overlay layer. Adding an overlay may cause issues with older devices and such with limited memory. Default: none.
* Notes/Bugs display. Open Notes and bugs will be displayed as a yellow bug icon, closed ones the same in green. Default: on.
* Photo layer. Displays georeferenced photographs as red camera icons, if direction information is available the icon will be rotated. Default: off.
* Node icons. Default: on.
* Keep screen on. Default: off.
* Large node drag area. Moving nodes on a device with touch input is problematic since your fingers will obscure the current position on the display. Turning this on will provide a large area which can be used for off-center dragging (selection and other operations still use the normal touch tolerance area). Default: off.

#### Ustawienia zaawansowane

* Always show context menu. When turned on every selection process will show the context menu, turned off the menu is displayed only when no unambiguous selection can be determined. Default: off (used to be on).
* Enable light theme. On modern devices this is turned on by default. While you can enable it for older Android versions the style is likely to be inconsistent.
* Show statistics. Will show some statistics for debugging, not really useful. Default: off (used to be on).  

## Zgłaszanie Problemów

If Vespucci crashes, or it detects an inconsistent state, you will be asked to send in the crash dump. Please do so if that happens, but please only once per specific situation. If you want to give further input or open an issue for a feature request or similar, please do so here: [Vespucci issue tracker](https://github.com/MarcusWolschon/osmeditor4android/issues). If you want to discuss something related to Vespucci, you can either start a discussion on the [Vespucci Google group](https://groups.google.com/forum/#!forum/osmeditor4android) or on the [OpenStreetMap Android forum](http://forum.openstreetmap.org/viewforum.php?id=56)


