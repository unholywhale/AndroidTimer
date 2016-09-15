package com.whale.androidtimer;

import android.app.Activity;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.os.CountDownTimer;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.ListIterator;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private SQLiteDatabase mDb;
    private SetsDB mDbHelper;

    private boolean mReading = false;
    private long mLastReadTime = 0;
    private CountDownTimer mCountDownTimer;
    private int mCurrentSetId = 0;
    private ArrayList<Integer> mCurrentSet = new ArrayList<>();

    private ListView mListView;
    private SetsAdapter mAdapter;
    private RelativeLayout mTimerView;
    private TextView mCurrentMsView;
    private Menu mMenu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDbHelper = new SetsDB(this);

        mCurrentSetId = getCurrentSetId();

        mTimerView = (RelativeLayout) findViewById(R.id.timer_layout);
        mCurrentMsView = (TextView) findViewById(R.id.current_ms);
        setListView();
    }

    /**
     * Initialize the ListView and the set adapter
     */
    private void setListView() {
        mListView = (ListView) findViewById(R.id.sets_list);
        ArrayList<ArrayList<ContentValues>> sets = getSets(null);
        mAdapter = new SetsAdapter(this, R.layout.list_item, sets);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                mMenu.findItem(R.id.menu_delete).setVisible(true);
                mMenu.findItem(R.id.menu_average).setVisible(true);
                mAdapter.setChecked(position);
            }
        });
    }

    /**
     * Get the values and separate them by set
     * @param cursor
     * @return Array of sets
     */
    @NonNull
    private ArrayList<ArrayList<ContentValues>> getSets(@Nullable Cursor cursor) {
        if (cursor == null) {
            mDb = mDbHelper.getReadableDatabase();
            String[] projection = {SetsDB.KEY_ID, SetsDB.KEY_SET_ID, SetsDB.KEY_VALUE};
            cursor = mDb.query(false, SetsDB.TABLE_NAME, projection, null, null, null, null, null, null);
        }
        ArrayList<ContentValues> cvList = new ArrayList<>();
        ArrayList<ArrayList<ContentValues>> sets = new ArrayList<>();
        int setId = 0;
        while(cursor.moveToNext()) {
            ContentValues cv = new ContentValues();
            DatabaseUtils.cursorRowToContentValues(cursor, cv);
            if (setId == 0) {
                setId = cv.getAsInteger(SetsDB.KEY_SET_ID);
            }
            if (cv.getAsInteger(SetsDB.KEY_SET_ID) != setId) {
                sets.add(new ArrayList<>(cvList));
                cvList.clear();
                setId = cv.getAsInteger(SetsDB.KEY_SET_ID);
            }
            cvList.add(cv);
        }
        if (!cvList.isEmpty()) {
            sets.add(new ArrayList<>(cvList));
        }
        cursor.close();
        return sets;
    }

    /**
     * Get the id for the next set
     * @return new set id
     */
    private int getCurrentSetId() {
        mDb = mDbHelper.getReadableDatabase();
        String[] projection = { SetsDB.KEY_SET_ID };
        Cursor cursor = mDb.query(true, SetsDB.TABLE_NAME, projection, null, null, null, null, null, null);
        cursor.moveToLast();
        int setId;
        if (cursor.getCount() == 0) {
            setId = 1;
        } else {
            setId = cursor.getInt(cursor.getColumnIndexOrThrow(SetsDB.KEY_SET_ID)) + 1;
        }
        cursor.close();
        return setId;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        mMenu = menu;
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_add:
                startReading();
                break;
            case R.id.menu_delete:
                deleteItems();
                break;
            case R.id.menu_average:
                calculateAverages();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }


    private void calculateAverages() {
        mDb = mDbHelper.getReadableDatabase();
        String[] projection = { SetsDB.KEY_ID, SetsDB.KEY_SET_ID, SetsDB.KEY_VALUE };
        String selection = "";
        String[] selectionArgs = new String[mAdapter.getCheckedSet().size()];
        int c = 0;
        // Get the cursor for the checked sets
        for (int i : mAdapter.getCheckedSet()) {
            if (selection.isEmpty()) {
                selection = SetsDB.KEY_SET_ID + " = ? ";
            } else {
                selection += " OR " + SetsDB.KEY_SET_ID + " = ? ";
            }
            selectionArgs[c] = String.valueOf(mAdapter.getItem(i).get(0).getAsInteger(SetsDB.KEY_SET_ID));
            c++;
        }
        Cursor cursor = mDb.query(SetsDB.TABLE_NAME, projection, selection, selectionArgs, null, null, null);
        ArrayList<ArrayList<ContentValues>> sets = getSets(cursor);
        int min = -1;
        // Determine the smallest set size
        for (ArrayList<ContentValues> set : sets) {
            if (min == -1 || set.size() < min) {
                min = set.size();
            }
        }
        // Crop the sets to the smallest size
        ListIterator<ArrayList<ContentValues>> listIterator = sets.listIterator();
        while(listIterator.hasNext()) {
            listIterator.set(new ArrayList<>(listIterator.next().subList(0, min)));
        }
        ArrayList<Integer> averages = new ArrayList<>();
        // Get the averages
        for (ArrayList<ContentValues> set : sets) {
            for (int i = 0; i < set.size(); i++) {
                int setValue = set.get(i).getAsInteger(SetsDB.KEY_VALUE);
                if (averages.size() < i + 1) {
                    averages.add(setValue);
                } else {
                    averages.set(i, averages.get(i) + setValue);
                }
            }
        }
        for (int i = 0; i < averages.size(); i++) {
            averages.set(i, averages.get(i) / sets.size());
        }
        displayAverages(averages);
    }

    private void displayAverages(ArrayList<Integer> averages) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_layout);
        ListView avgList = (ListView) dialog.findViewById(R.id.averages_list);
        TextView avgCount = (TextView) dialog.findViewById(R.id.averages_count);
        avgCount.setText(String.format(Locale.getDefault(), "Average for %d values:", averages.size()));
        ArrayAdapter<Integer> avgAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, averages);
        avgList.setAdapter(avgAdapter);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    /**
     * Bulk delete items from the DB
     */
    private void deleteItems() {
        try {
            mDb = mDbHelper.getWritableDatabase();
            mDb.beginTransaction();
            for (int i : mAdapter.getCheckedSet()) {
                ArrayList<ContentValues> cv = mAdapter.getItem(i);
                if (cv == null) {
                    continue;
                }
                String selection = SetsDB.KEY_SET_ID + " = ?";
                String[] selectionArgs = { String.valueOf(cv.get(0).getAsInteger(SetsDB.KEY_SET_ID)) };
                mDb.delete(SetsDB.TABLE_NAME, selection, selectionArgs);
            }
            mDb.setTransactionSuccessful();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            mDb.endTransaction();
            mAdapter.clear();
            mAdapter.addAll(getSets(null));
            mAdapter.setChoiceMode(false);
            mAdapter.notifyDataSetChanged();
            mMenu.findItem(R.id.menu_delete).setVisible(false);
            mMenu.findItem(R.id.menu_average).setVisible(false);
        }
    }

    private void startReading() {
        mTimerView.setVisibility(View.VISIBLE);
        mReading = true;
        mLastReadTime = System.currentTimeMillis();
        startCountDown();
    }

    /**
     * Start 5 sec countdown, stop reading if finished
     */
    private void startCountDown() {

        if (mCountDownTimer != null) {
            mCountDownTimer.cancel();
        }

        mCountDownTimer = new CountDownTimer(5000, 1000) {

            @Override
            public void onTick(long millisUntilFinished) {

            }

            @Override
            public void onFinish() {
                stopReading();
            }
        }.start();
    }

    /**
     * Add new set to the DB and refresh the ListView
     */
    private void stopReading() {
        mReading = false;
        mTimerView.setVisibility(View.GONE);
        insertSet();
        mCurrentMsView.setText("0 ms");
        mCurrentSet.clear();
        mAdapter.clear();
        mAdapter.addAll(getSets(null));
        mAdapter.notifyDataSetChanged();
        mCurrentSetId++;
    }

    /**
     * Bulk insert to the DB
     */
    private void insertSet() {
        mDb = mDbHelper.getWritableDatabase();
        ArrayList<ContentValues> cvList = new ArrayList<>();
        for (int value : mCurrentSet) {
            ContentValues values = new ContentValues();
            values.put(SetsDB.KEY_SET_ID, mCurrentSetId);
            values.put(SetsDB.KEY_VALUE, value);
            cvList.add(values);
        }
        try {
            mDb.beginTransaction();
            for (ContentValues cv : cvList) {
                mDb.insert(SetsDB.TABLE_NAME, null, cv);
            }
            mDb.setTransactionSuccessful();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            mDb.endTransaction();
        }
    }

    /**
     * Read time between presses
     */
    private void registerPress() {
        long currentTime = System.currentTimeMillis();
        long timeMs = currentTime - mLastReadTime;
        mCurrentMsView.setText(String.format(Locale.getDefault(), "%d ms", timeMs));
        startCountDown(); // reset the countdown
        mLastReadTime = currentTime;
        mCurrentSet.add((int) timeMs);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        switch(keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (mReading) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        registerPress();
                    }
                    return true;
                }
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    private class SetsAdapter extends ArrayAdapter<ArrayList<ContentValues>> {

        private ArrayList<ArrayList<ContentValues>> objects;
        private Context context;
        private int resource;
        private HashSet<Integer> checked = new HashSet<>();
        private boolean choiceMode = false;

        SetsAdapter(Context context, int resource, ArrayList<ArrayList<ContentValues>> objects) {
            super(context, resource, objects);
            this.objects = objects;
            this.context = context;
            this.resource = resource;
        }

        void setChoiceMode(boolean on) {
            if (!on) {
                checked.clear();
            }
            choiceMode = on;
        }

        void setChecked(int position) {
            choiceMode = true;
            if (checked.contains(position)) {
                checked.remove(position);
            } else {
                checked.add(position);
            }
            notifyDataSetChanged();
        }

        HashSet<Integer> getCheckedSet() {
            return checked;
        }

        @NonNull
        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            View row = convertView;
            SetHolder holder;
            if (row == null) {
                row = ((Activity) context).getLayoutInflater().inflate(resource, parent, false);
                holder = new SetHolder();
                holder.setId = (TextView) row.findViewById(R.id.set_id);
                holder.setValues = (TextView) row.findViewById(R.id.set_values);
                holder.checkBox = (CheckBox) row.findViewById(R.id.set_checkbox);
                holder.checkBox.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        setChecked(position);
                    }
                });
                holder.delete = (ImageView) row.findViewById(R.id.set_delete);
                row.setTag(holder);
            } else {
                holder = (SetHolder) row.getTag();
            }
            int setId = 0;
            String valuesString = "";
            for(ContentValues cv : objects.get(position)) {
                setId = cv.getAsInteger(SetsDB.KEY_SET_ID);
                if (valuesString.isEmpty()) {
                    valuesString = String.valueOf(cv.getAsInteger(SetsDB.KEY_VALUE));
                } else {
                    valuesString += String.format(Locale.getDefault(), ", %d", cv.getAsInteger(SetsDB.KEY_VALUE));
                }
            }
            holder.setId.setText(String.format(Locale.getDefault(), "Set %d:", setId));
            holder.setValues.setText(valuesString);
            if (choiceMode) {
                holder.checkBox.setVisibility(View.VISIBLE);
            } else {
                holder.checkBox.setVisibility(View.GONE);
            }
            holder.checkBox.setChecked(checked.contains(position));
            return row;
        }

        class SetHolder {
            TextView setId;
            TextView setValues;
            CheckBox checkBox;
            ImageView delete;
        }
    }

}
