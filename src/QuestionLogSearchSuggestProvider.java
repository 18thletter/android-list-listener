/*
 * QuestionLogSearchSuggestProvider.java
 *
 * Provider for the question log search.
 */

/*
 * A note on implementation specifications and future design concerns. Since I
 * am new to Android, I was not sure the best way to organize these three
 * classes--QuestionLog, QuestionLogSearchSuggestProvider, and
 * SearchableQuestionLog. I implemented these classes in a way that I believed
 * would do the job; however, these may not be the most efficient. It is
 * ultimately up to the programmers who write the full application to tweak or
 * change this implementation. As it is now, QuestionLog uses a List and a Map
 * to store its questions locally in the Activity. As the question log will
 * most likely be the top activity on the stack (or second to the top if
 * SearchableQuestionLog is created), these data structures, the List and the
 * Map, are destroyed and recreated (by polling the game server database using
 * GameState) every time the QuestionLog activity is called. This is memory
 * cheap in that these data structures are not kept in memory in the background
 * while the activity is not the focus, but is also computationally expensive
 * because the creation of a whole new list is done every time. An alternate
 * implementation would be to use QuestionLogSearchSuggestProvider to store the
 * data structures (because a content provider is created when the application
 * starts, and stays for the entire time the application is running)--this
 * would be computationally cheap but perhaps memory expensive. I suppose that
 * all of this depends on how large the question log database is, but since
 * this is a multiplayer game, it is conceivable that the question log database
 * could be indeed quite large.
 *
 * Another implementation specific is that I extended QuestionLog for
 * SearchableQuestionLog. I did this as a shortcut to preserve the state of the
 * expanded items and the state of the boolean that controls hiding/showing
 * selected options. To clarify, if the user turns off selected options in
 * QuestionLog, searches for a question, leaves on selected options, then
 * presses back, viewing selected options will be off again (and the questions
 * that were expanded before will be expanded again). The potential problem
 * with this arrangement is that two Lists and Maps are kept in memory: one for
 * QuestionLog and one for SearchableQuestionLog. Perhaps it may be better
 * controlling the search from within QuestionLog, and making QuestionLog the
 * searchable activity instead of creating a new activity.
 */
package com.example.android.debuggermenu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Log;
import clientcore.GameState;
import clientcore.Question;

/**
 * Provides search suggestions while a search is being typed into the search
 * dialog.
 *
 * Provides search suggestions while a search is being typed into the search
 * dialog. Implemented for this use specifically and only. Behavior from other
 * areas of the application are unknown and untested. Most of the overridden
 * functions are left unwritten and unsupported.
 *
 * Data in this content provider are stored in various data structures.
 * Normally, a SQLite database is used to store information of a content
 * provider in an Android application. However, a database in this case may not
 * be the best choice, since this content provider is for the question log of a
 * player in a multiplayer online game. There is no guarantee that the
 * information in the question log would stay the same. If the user logs on as
 * another player, or a user's friend uses her phone to log onto his own
 * account, or if the user decides to create another character player, then in
 * all these situations, a different question log must be loaded from the game
 * server. In this way, a database may not be efficient because the information
 * within it has too high of a probability to fluctuate.
 *
 * @author Raymund Lew
 * @see android.content.ContentProvider
 */
public class QuestionLogSearchSuggestProvider extends ContentProvider {
    public static final String TAG = QuestionLogSearchSuggestProvider.class
            .getSimpleName();

    public static final Uri CONTENT_URI = Uri.parse("content://"
            + QuestionLogSearchSuggestProvider.class.getName().toLowerCase());

    /*
     * Explanation of columns (protocol is from the search suggestion search
     * dialog):
     *
     * 0. (int) a unique ID number
     *
     * 1. (String) the text to show in the suggestion
     *
     * 2. (String) the smaller text to show under each suggestion
     *
     * 3. (Uri) data to pass to the searchable activity (SearchableQuestionLog).
     */
    private static final String COLUMN0 = BaseColumns._ID;
    private static final String COLUMN1 = SearchManager.SUGGEST_COLUMN_TEXT_1;
    private static final String COLUMN2 = SearchManager.SUGGEST_COLUMN_TEXT_2;
    private static final String COLUMN3 = SearchManager.SUGGEST_COLUMN_INTENT_DATA;

    /**
     * Not supported.
     */
    @Override
    public int delete(Uri arg0, String arg1, String[] arg2) {
        throw new UnsupportedOperationException();
    }

    /**
     * Not supported.
     */
    @Override
    public String getType(Uri uri) {
        throw new UnsupportedOperationException();
    }

    /**
     * Not supported.
     */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    /**
     * Not supported.
     */
    @Override
    public boolean onCreate() {
        Log.d(TAG, "QuestionLogSearchSuggestProvider created");
        return true;
    }

    /**
     * Returns a Cursor containing the search suggestion result.
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        /*
         * Get the question log; it has already been updated earlier in
         * QuestionLog
         */
        List<Question> questionList = GameState.getQuestionLog();

        /* Cursor to return */
        MatrixCursor suggestionCursor = new MatrixCursor(new String[] {
                COLUMN0, COLUMN1, COLUMN2, COLUMN3 });

        String emptyPlaceholder = "";

        /*
         * Sets only add unique elements. This set will only contain unique
         * question IDs.
         */
        Set<Short> questionIdSet = new HashSet<Short>();
        /*
         * And this map will only contain unique selected options.
         */
        Map<Short, Set<String>> optionMap = new HashMap<Short, Set<String>>();

        /* Counter to ensure unique IDs of the Cursor rows */
        int idCounter = 0;

        /* Parse the search query for string tokens */
        List<String> queryTokens = new ArrayList<String>();
        StringTokenizer tokenizer = new StringTokenizer(uri
                .getLastPathSegment().toLowerCase());
        while (tokenizer.hasMoreTokens()) {
            queryTokens.add(tokenizer.nextToken());
        }

        for (Question q : questionList) {
            Short id = q.getID();
            if (!optionMap.keySet().contains(id)) {
                optionMap.put(id, new HashSet<String>());
            }
            String questionText = q.getText();

            /*
             * The question ID of the clicked suggestion is what is passed to
             * the searchable activity
             */
            Uri intentData = Uri.parse(((Integer) (int) q.getID()).toString());

            /* Search and populate the cursor */
            for (String token : queryTokens) {
                if (questionText.toLowerCase().contains(token)) {
                    /*
                     * Only unique questions will be added to the suggestion
                     * list
                     */
                    if (questionIdSet.add(id)) {
                        suggestionCursor.addRow(new Object[] { idCounter++,
                                questionText, emptyPlaceholder, intentData });
                    }
                }
                for (String optionText : q.getMyOptions()) {
                    if (optionText.toLowerCase().contains(token)) {
                        /*
                         * And only unique options will be added to the
                         * suggestion list
                         */
                        if (optionMap.get(id).add(optionText)) {
                            suggestionCursor.addRow(new Object[] { idCounter++,
                                    optionText, questionText, intentData });
                        }
                    }
                }
            }
        }
        return suggestionCursor;
    }

    /**
     * Not supported.
     */
    @Override
    public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

}
