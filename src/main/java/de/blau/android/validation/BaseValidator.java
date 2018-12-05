package de.blau.android.validation;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.regex.Pattern;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;
import de.blau.android.App;
import de.blau.android.R;
import de.blau.android.osm.Node;
import de.blau.android.osm.OsmElement;
import de.blau.android.osm.Relation;
import de.blau.android.osm.Tags;
import de.blau.android.osm.Way;
import de.blau.android.presets.Preset;
import de.blau.android.presets.Preset.PresetItem;
import de.blau.android.util.GeoContext;
import de.blau.android.util.collections.MultiHashMap;

public class BaseValidator implements Validator {
    private static final String DEBUG_TAG = BaseValidator.class.getSimpleName();

    Preset[] presets;

    /**
     * Tags for objects that should be re-surveyed regularly.
     */
    MultiHashMap<String, PatternAndAge> resurveyTags;

    /**
     * Tags that should be present on objects (need to be in the preset for the object too
     */
    Map<String, Boolean> checkTags;

    /**
     * Regex for general tagged issues with the object
     */
    static final Pattern FIXME_PATTERN = Pattern.compile("(?i).*\\b(?:fixme|todo)\\b.*");

    /**
     * Construct a new instance
     * 
     * @param ctx Android Context
     */
    public BaseValidator(@NonNull Context ctx) {
        init(ctx);
    }

    @Override
    public void reset(Context context) {
        init(context);
    }

    /**
     * (Re-)initialize everything
     * 
     * @param ctx Android Context
     */
    private void init(@NonNull Context ctx) {
        // !!!! don't store ctx as that will cause a potential memory leak
        presets = App.getCurrentPresets(ctx);
        SQLiteDatabase db = (new ValidatorRulesDatabaseHelper(ctx)).getReadableDatabase();
        resurveyTags = ValidatorRulesDatabase.getDefaultResurvey(db);
        checkTags = ValidatorRulesDatabase.getDefaultCheck(db);
        db.close();
    }

    /**
     * Test if the element has any problems by searching all the tags for the words "fixme" or "todo", or if it has a
     * key in the list of things to regularly re-survey
     * 
     * @param status status before calling this method
     * @param e the OsmElement
     * @param tags the associated tags
     * @return the output status
     */
    int validateElement(int status, OsmElement e, SortedMap<String, String> tags) {
        // test for fixme etc
        for (Entry<String, String> entry : new ArrayList<>(tags.entrySet())) {
            // test key and value against pattern
            if (FIXME_PATTERN.matcher(entry.getKey()).matches() || FIXME_PATTERN.matcher(entry.getValue()).matches()) {
                status = status | Validator.FIXME;
            }
        }

        // age check
        if (resurveyTags != null) {
            for (String key : resurveyTags.getKeys()) {
                Set<PatternAndAge> values = resurveyTags.get(key);
                for (PatternAndAge value : values) {
                    if (tags.containsKey(key) && (value.getValue() == null || "".equals(value.getValue()) || value.matches(tags.get(key)))) {
                        long now = System.currentTimeMillis() / 1000;
                        long timestamp = e.getTimestamp();
                        if (timestamp >= 0 && (now - timestamp > value.getAge())) {
                            status = status | Validator.AGE;
                            break;
                        }
                        if (tags.containsKey(Tags.KEY_CHECK_DATE)) {
                            status = status | checkAge(tags, now, Tags.KEY_CHECK_DATE, value.getAge());
                            break;
                        }
                        if (tags.containsKey(Tags.KEY_CHECK_DATE + ":" + key)) {
                            status = status | checkAge(tags, now, Tags.KEY_CHECK_DATE + ":" + key, value.getAge());
                            break;
                        }
                    }
                }
            }
        }

        // find missing keys
        PresetItem pi = Preset.findBestMatch(presets, tags);
        if (pi != null && checkTags != null) {
            for (Entry<String, Boolean> entry : checkTags.entrySet()) {
                String key = entry.getKey();
                if (pi.hasKey(key, entry.getValue())) {
                    if (!e.hasTagKey(key)) {
                        if (!(Tags.KEY_NAME.equals(key)
                                && (e.hasTagWithValue(Tags.KEY_NONAME, Tags.VALUE_YES) || e.hasTagWithValue(Tags.KEY_VALIDATE_NO_NAME, Tags.VALUE_YES)))) {
                            status = status | Validator.MISSING_TAG;
                        }
                    }
                }
            }
        }
        return status;
    }

    /**
     * Validate a Way with a highway tag
     * 
     * @param w the Way
     * @param highway the value of the highway tag
     * @return the status
     */
    int validateHighway(Way w, String highway) {
        int result = Validator.NOT_VALIDATED;

        if (Tags.VALUE_ROAD.equalsIgnoreCase(highway)) {
            // unsurveyed road
            result = result | Validator.HIGHWAY_ROAD;
        }
        GeoContext geoContext = App.getGeoContext();
        if (geoContext != null) {
            boolean imperial = geoContext.imperial(w);
            if (imperial) {
                SortedMap<String, String> tags = w.getTags();
                Set<String> keys = w.getTags().keySet();
                for (String key : keys) {
                    if (Tags.isSpeedKey(key) && !tags.get(key).endsWith(Tags.MPH)) {
                        return result | Validator.IMPERIAL_UNITS;
                    }
                }
            }
        }
        return result;
    }

    /**
     * Check that the date value of tag is not more that validFor seconds old
     * 
     * @param tags tags for the element
     * @param now current time in milliseconds
     * @param tag tag to retrieve the date value for
     * @param validFor time the element is valid for in seconds
     * @return Validator.AGE is too old, otherwise Validator.NOT_VALIDATED
     */
    private int checkAge(@NonNull SortedMap<String, String> tags, long now, @NonNull String tag, long validFor) {
        try {
            return now - new SimpleDateFormat(Tags.CHECK_DATE_FORMAT, Locale.US).parse(tags.get(tag)).getTime() / 1000 > validFor ? Validator.AGE
                    : Validator.NOT_VALIDATED;
        } catch (ParseException e) {
            return Validator.AGE;
        }
    }

    /**
     * Return a List of Strings describing the problems detected in calcProblem
     * 
     * @param ctx Android Context
     * @param e OsmElement
     * @param tags the associates tags
     * @return a List of Strings describing the problems
     */
    @NonNull
    public List<String> describeProblemElement(@NonNull Context ctx, @NonNull OsmElement e, SortedMap<String, String> tags) {
        List<String> result = new ArrayList<>();

        // fixme etc.
        for (Entry<String, String> entry : tags.entrySet()) {
            // test key and value against pattern
            if (FIXME_PATTERN.matcher(entry.getKey()).matches() || FIXME_PATTERN.matcher(entry.getValue()).matches()) {
                result.add(entry.getKey() + ": " + entry.getValue());
            }
        }

        // resurvey age
        if ((e.getCachedProblems() & Validator.AGE) != 0) {
            result.add(ctx.getString(R.string.toast_needs_resurvey));
        }

        // missing tags
        PresetItem pi = Preset.findBestMatch(presets, tags);
        if (pi != null && checkTags != null) {
            for (Entry<String, Boolean> entry : checkTags.entrySet()) {
                String key = entry.getKey();
                if (pi.hasKey(key, entry.getValue())) {
                    if (!e.hasTagKey(key)) {
                        if (!(Tags.KEY_NAME.equals(key)
                                && (e.hasTagWithValue(Tags.KEY_NONAME, Tags.VALUE_YES) || e.hasTagWithValue(Tags.KEY_VALIDATE_NO_NAME, Tags.VALUE_YES)))) {
                            String hint = pi.getHint(key);
                            result.add(ctx.getString(R.string.toast_missing_key, hint != null ? hint : key));
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * Return a List of Strings describing the problems for a Way with a highway tag
     * 
     * @param ctx Android Context
     * @param w the Way
     * @param highway the value of the highway tag
     * @return a List containing the problem descriptions
     */
    List<String> describeProblemHighway(Context ctx, Way w, String highway) {
        List<String> wayProblems = new ArrayList<>();
        if (Tags.VALUE_ROAD.equalsIgnoreCase(highway)) {
            wayProblems.add(App.resources().getString(R.string.toast_unsurveyed_road));
        }
        //
        if ((w.getCachedProblems() & Validator.IMPERIAL_UNITS) != 0) {
            wayProblems.add(ctx.getString(R.string.toast_imperial_units));
        }
        return wayProblems;
    }

    @Override
    public int validate(@NonNull Node node) {
        int status = Validator.NOT_VALIDATED;
        SortedMap<String, String> tags = node.getTags();
        // tag based checks
        if (tags != null) {
            status = validateElement(status, node, tags);
        }
        if (status == Validator.NOT_VALIDATED) {
            status = Validator.OK;
        }
        return status;
    }

    @Override
    public int validate(@NonNull Way way) {
        int status = Validator.NOT_VALIDATED;
        SortedMap<String, String> tags = way.getTags();
        // tag based checks
        if (tags != null) {
            status = validateElement(status, way, tags);
        }
        String highway = way.getTagWithKey(Tags.KEY_HIGHWAY);
        if (highway != null) {
            status = status | validateHighway(way, highway);
        }
        if (status == Validator.NOT_VALIDATED) {
            status = Validator.OK;
        }
        return status;
    }

    @Override
    public int validate(@NonNull Relation relation) {
        int status = Validator.NOT_VALIDATED;
        SortedMap<String, String> tags = relation.getTags();
        // tag based checks
        if (tags != null) {
            status = validateElement(status, relation, tags);
        }
        if (noType(relation)) {
            status = status | Validator.NO_TYPE;
        }
        if (status == Validator.NOT_VALIDATED) {
            status = Validator.OK;
        }
        return status;
    }

    /**
     * Check if a Relation has a type tag with value
     * 
     * @param r the Relation
     * @return true if no complete type tag
     */
    private boolean noType(Relation r) {
        String type = r.getTagWithKey(Tags.KEY_TYPE);
        return type == null || "".equals(type);
    }

    @NonNull
    @Override
    public String[] describeProblem(@NonNull Context ctx, @NonNull Node node) {
        SortedMap<String, String> tags = node.getTags();
        ArrayList<String> result = new ArrayList<>();
        if (tags != null) {
            result.addAll(describeProblemElement(ctx, node, tags));
        }
        String[] resultArray = result.toArray(new String[result.size()]);
        return resultArray;
    }

    @NonNull
    @Override
    public String[] describeProblem(@NonNull Context ctx, @NonNull Way way) {
        SortedMap<String, String> tags = way.getTags();
        ArrayList<String> result = new ArrayList<>();
        if (tags != null) {
            result.addAll(describeProblemElement(ctx, way, tags));
        }
        String highway = way.getTagWithKey(Tags.KEY_HIGHWAY);
        if (highway != null) {
            result.addAll(describeProblemHighway(ctx, way, highway));
        }
        String[] resultArray = result.toArray(new String[result.size()]);
        return resultArray;
    }

    @NonNull
    @Override
    public String[] describeProblem(@NonNull Context ctx, @NonNull Relation relation) {
        SortedMap<String, String> tags = relation.getTags();
        ArrayList<String> result = new ArrayList<>();
        if (tags != null) {
            result.addAll(describeProblemElement(ctx, relation, tags));
        }
        if (noType(relation)) {
            result.add(App.resources().getString(R.string.toast_notype));
        }
        String[] resultArray = result.toArray(new String[result.size()]);
        return resultArray;
    }

    @Override
    public String[] describeProblem(@NonNull Context ctx, @NonNull OsmElement e) {
        if (e instanceof Node) {
            return describeProblem(ctx, (Node) e);
        }
        if (e instanceof Way) {
            return describeProblem(ctx, (Way) e);
        }
        if (e instanceof Relation) {
            return describeProblem(ctx, (Relation) e);
        }
        return null;
    }
}
