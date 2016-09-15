package com.whale.androidtimer;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Database model
 */

public class SetsDB extends SQLiteOpenHelper {

    public static final String TABLE_NAME = "timer_sets";
    public static final int DB_VERSION = 2;
    public static final String KEY_ID = "_id";
    public static final String KEY_SET_ID = "set_id";
    public static final String KEY_VALUE = "value";

    public SetsDB(Context context) {
        super(context, TABLE_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_SETS_TABLE = "CREATE TABLE " + TABLE_NAME + " ( " +
                KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                KEY_SET_ID + " INTEGER," +
                KEY_VALUE + " INTEGER )";
        db.execSQL(CREATE_SETS_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        this.onCreate(db);
    }
}
