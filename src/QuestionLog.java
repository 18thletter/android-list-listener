/*
 * QuestionLog.java
 *
 * Lists questions in the question log, and its options.
 */

package com.example.android.debuggermenu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.app.ExpandableListActivity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.TextView;
import android.widget.Toast;
import clientcore.GameState;
import clientcore.Question;

/**
 * Build a list of questions the player has encountered before.
 *
 * Child class of ExpandableListActivity. Gets a list of
 * {@link clientcore.Question Question}s from the {@link clientcore.GameState
 * GameState} and builds a list of questions where each question, if clicked,
 * expands to show the options of that question. The list of questions are
 * questions that the player has encountered before (question log). If the log
 * is empty, then a message is printed indicating such.
 *
 * Previously answered options of each question are by default shown in the
 * question log. Correctly answered options are highlighted green while
 * incorrectly answered options are highlighted red. The default behavior of the
 * way the options are shown can be changed by changing the code in the
 * {@link #setOptionCorrect setOptionCorrect()} and {@link #setOptionIncorrect
 * setOptionIncorrect()} or overloading those functions.
 *
 * There is also a menu for this question log, containing two options: a button
 * to toggle the display of correct/incorrect options, and a search button to
 * search the log for specific questions.
 *
 * @author Raymund Lew
 * @see android.app.ExpandableListActivity
 * @see SearchableQuestionLog
 * @see QuestionLogSearchSuggestProvider
 */
public class QuestionLog extends ExpandableListActivity {
    public final static String TAG = QuestionLog.class.getSimpleName();

    protected QuestionLogAdapter questionLogAdapter;

    /*
     * Kept package level for performance reasons, but of course can be changed
     * to private if object-oriented abstraction is required.
     */
    List<Question> questionList = new ArrayList<Question>();

    /*
     * Question IDs are mapped to a set of options that the user has previously
     * selected.
     *
     * Note that these options are Integers which correspond to the index of the
     * option within the Vector from questionList.getMyOptions().get(index)
     * (indexed from 0 through questionList.getMyOptions.size() - 1).
     *
     * Kept package level for performance reasons.
     */
    Map<Short, Set<Integer>> selectedOptionMap = new HashMap<Short, Set<Integer>>();

    /*
     * Shows selected options on the list. What specifically is done to show the
     * options can be changed in the setOptionCorrect() and setOptionIncorrect()
     * functions.
     *
     * Kept package level for performance reasons.
     */
    boolean showOptions = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "QuestionLog created");

        /* Load the questions */
        new LoadQuestionLog().execute();

        /*
         * If the device has a keyboard, and the user starts typing on it, the
         * search dialog will pop up.
         */
        setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);
    }

    /**
     * For polling the GameState and building the question log in another thread
     * to avoid "Application Not Responding" error from happening.
     */
    protected class LoadQuestionLog extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            /* Build the question list and selected option map */
            buildQuestionListAndSelectedOptionMap();
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            /* Set the list adapter */
            setListAdapter(questionLogAdapter = new QuestionLogAdapter());

            setContentView(R.layout.questionlog_layout);
        }

    }

    /**
     * Builds the question log {@link java.util.List List} and a
     * {@link java.util.Map Map} mapping selected options with a question ID.
     *
     * Only unique questions are added to the list and only unique options are
     * added to the map.
     */
    protected void buildQuestionListAndSelectedOptionMap() {
        Log.d(TAG, "Building question list and selected option map");

        /* Update the question log and get the list of questions */
        GameState.updateQuestionLog();
        List<Question> gameStateQuestionList = GameState.getQuestionLog();

        /*
         * Sets only add unique elements. This set will only contain unique
         * question IDs.
         */
        Set<Short> questionIdSet = new HashSet<Short>();

        for (Question question : gameStateQuestionList) {
            addQuestionAndOptions(question, questionIdSet);
        }
    }

    /**
     * Helper function for {@link #buildQuestionListAndSelectedOptionMap
     * buildQuestionListAndSelectedOptionMap()}.
     *
     * @param question
     * @param questionIdSet
     */
    protected void addQuestionAndOptions(Question question,
            Set<Short> questionIdSet) {
        Short id = question.getID();
        if (questionIdSet.add(id)) {
            /* Only add a question if it has a unique ID */
            questionList.add(question);
        }
        if (selectedOptionMap.containsKey(id)) {
            /* Note that only unique options will be added */
            selectedOptionMap.get(id).add((int) question.getChoice());
        } else {
            Set<Integer> set = new HashSet<Integer>();
            set.add((int) question.getChoice());
            selectedOptionMap.put(id, set);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        /* No use to search an empty list */
        if (questionList.isEmpty()) {
            return false;
        }

        getMenuInflater().inflate(R.menu.questionlog_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.questionlog_menu_options_toggle:
            /* Toggle showing/hiding options */
            if (showOptions) {
                showOptions = false;
                Toast.makeText(this, R.string.questionlog_toast_hide_options,
                        Toast.LENGTH_SHORT).show();
            } else {
                showOptions = true;
                Toast.makeText(this, R.string.questionlog_toast_show_options,
                        Toast.LENGTH_LONG).show();
            }
            questionLogAdapter.notifyDataSetChanged();
            return true;
        case R.id.questionlog_menu_search:
            onSearchRequested();
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Toggles the menu button to hide or show the selected options.
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(R.id.questionlog_menu_options_toggle);
        if (showOptions) {
            item.setTitle(getResources().getString(
                    R.string.questionlog_menu_hide_options));
            item.setIcon(getResources().getDrawable(
                    R.drawable.questionlog_menu_hide_options));
        } else {
            item.setTitle(getResources().getString(
                    R.string.questionlog_menu_show_options));
            item.setIcon(getResources().getDrawable(
                    R.drawable.questionlog_menu_show_options));
        }
        return true;
    }

    /**
     * What to do to the TextView of the option selected if the question was
     * answered incorrectly.
     *
     * Kept package level for performance reasons.
     *
     * @param childView
     */
    void setOptionIncorrect(TextView childView) {
        childView.setTextColor(getResources().getColor(
                R.color.questionlog_incorrect));
    }

    /**
     * What to do to the Textview of the option selected if the question was
     * answered correctly.
     *
     * Kept package level for performance reasons.
     *
     * @param childView
     */
    void setOptionCorrect(TextView childView) {
        childView.setTextColor(getResources().getColor(
                R.color.questionlog_correct));
    }

    /**
     * List adapter for the question log.
     *
     * @author Raymund Lew
     * @see android.widget.BaseExpandableListAdapter
     */
    protected class QuestionLogAdapter extends BaseExpandableListAdapter {

        @Override
        public Object getChild(int groupPosition, int childPosition) {
            return questionList.get(groupPosition).getMyOptions()
                    .get(childPosition);
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return childPosition;
        }

        /**
         * Returns a View that shows or hides selected options. If the question
         * that an incorrect option belongs to contains the correct answer in
         * its option map, it will not be shown.
         */
        @Override
        public View getChildView(int groupPosition, int childPosition,
                boolean isLastChild, View convertView, ViewGroup parent) {
            TextView childView = (TextView) getLayoutInflater().inflate(
                    R.layout.questionlog_child_row, null).findViewById(
                    R.id.questionlog_option);
            Question question = questionList.get(groupPosition);
            childView.setText(question.getMyOptions().get(childPosition));

            /*
             * If the option is one that has been chosen before and showing
             * options is enabled
             */
            if (showOptions
                    && selectedOptionMap.get(question.getID()).contains(
                            childPosition)) {
                /* Show correct option */

                /*
                 * Note: question.getAnswer() for some reason returns the index
                 * of the correct answer (corresponding to
                 * questionList.getMyOptions().get(index)) minus 1. The logic of
                 * this function depends on that and should be changed if
                 * question.getAnswer() changes in the future.
                 */
                if (childPosition == (int) question.getAnswer() + 1) {
                    setOptionCorrect(childView);
                } else {
                    /*
                     * Only show incorrect options if the correct option isn't
                     * there
                     */
                    if (!selectedOptionMap.get(question.getID()).contains(
                            question.getAnswer() + 1)) {
                        setOptionIncorrect(childView);
                    }
                }
            }
            return childView;
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            return questionList.get(groupPosition).getMyOptions().size();
        }

        @Override
        public Object getGroup(int groupPosition) {
            return questionList.get(groupPosition).getText();
        }

        @Override
        public int getGroupCount() {
            return questionList.size();
        }

        @Override
        public long getGroupId(int groupPosition) {
            return groupPosition;
        }

        @Override
        public View getGroupView(int groupPosition, boolean isExpanded,
                View convertView, ViewGroup parent) {
            TextView groupView = (TextView) getLayoutInflater().inflate(
                    R.layout.questionlog_group_row, null).findViewById(
                    R.id.questionlog_question);
            groupView.setText(questionList.get(groupPosition).getText());
            return groupView;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return false;
        }

    }
}
