/*
 * SearchableQuestionLog.java
 *
 * Question log that is searchable and displays search results.
 */
package com.example.android.debuggermenu;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import clientcore.GameState;
import clientcore.Question;

/**
 * A searchable question log that displays the search results from the
 * {@link QuestionLog QuestionLog} and search suggestion clicks.
 *
 * Displays the questions in the same two-level expandable list format
 * as the Question Log.
 *
 * @author Raymund Lew
 * @see QuestionLog
 * @see QuestionLogSearchSuggestProvider
 */
public class SearchableQuestionLog extends QuestionLog {
    public static final String TAG = SearchableQuestionLog.class
            .getSimpleName();

    private String searchQuery;
    private Short searchSuggestionId;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "SearchableQuestionLog created");

        handleIntent(getIntent());

        super.onCreate(savedInstanceState);
    }

    private void handleIntent(Intent intent) {
        searchSuggestionId = null;
        Log.d(TAG, "Intent is " + intent.getAction());

        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            searchQuery = intent.getStringExtra(SearchManager.QUERY);
        }
        /*
         * Android API seems to have a bug (at the time this code was written).
         * Intent.ACTION_VIEW (String) is "android.intent.action.VIEW", while
         * the intent sent to an activity as an action view intent is
         * "android.Intent.action.VIEW". Notice that the "i" in intent is
         * capitalized in one and not in the other.
         */
        else if (Intent.ACTION_VIEW.toLowerCase().equals(
                intent.getAction().toLowerCase())) {
            searchSuggestionId = Short.parseShort(intent.getDataString());
        }
    }

    /**
     * New intent can occur when a search is done from within this activity.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        /*
         * The intent has changed possibly. Set it in case getIntent() is ever
         * called
         */
        setIntent(intent);
        handleIntent(intent);

        /*
         * Now that the intent has been handled, reset the data structures so
         * that we can show a new list of items
         */
        questionList.clear();
        selectedOptionMap.clear();

        /*
         * Rebuild the question list and option map because the list has
         * possibly changed from a new search or search suggestion
         */
        buildQuestionListAndSelectedOptionMap();
        questionLogAdapter.notifyDataSetChanged();
    }

    /**
     * Only builds a list with questions and options specified in the search
     * suggestion or search results.
     */
    @Override
    protected void buildQuestionListAndSelectedOptionMap() {
        Log.d(TAG, "Building question list and selected option map");

        List<Question> gameStateQuestionList = GameState.getQuestionLog();

        /*
         * Sets only add unique elements. This set will only contain unique
         * question IDs.
         */
        Set<Short> questionIdSet = new HashSet<Short>();

        /* Listing a clicked search suggestion is handled here */
        if (searchSuggestionId != null) {
            for (Question question : gameStateQuestionList) {
                if (searchSuggestionId == question.getID()) {
                    addQuestionAndOptions(question, questionIdSet);
                }
            }
            return;
        }

        /* A search suggestion was not clicked on; handle the search query */

        /* Parse the search query for string tokens */
        List<String> queryTokens = new ArrayList<String>();
        StringTokenizer tokenizer = new StringTokenizer(
                searchQuery.toLowerCase());
        while (tokenizer.hasMoreTokens()) {
            queryTokens.add(tokenizer.nextToken());
        }

        boolean addToList;
        for (Question question : gameStateQuestionList) {
            addToList = false;

            /* Search the question and its options */
            for (String token : queryTokens) {
                if (addToList) {
                    break;
                }
                if (question.getText().toLowerCase().contains(token)) {
                    addToList = true;
                    break;
                }
                for (String optionText : question.getMyOptions()) {
                    if (optionText.toLowerCase().contains(token)) {
                        addToList = true;
                        break;
                    }
                }
            }

            if (addToList) {
                addQuestionAndOptions(question, questionIdSet);
            }
        }
    }
}
