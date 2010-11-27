/*
    Copyright (c) 2010 Josiah Barber (aka Darshan)

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.
*/

package com.darshancomputing.BatteryIndicatorPro;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.graphics.drawable.TransitionDrawable;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.view.View.OnKeyListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public class AlarmsActivity extends Activity {
    private AlarmDatabase alarms;
    private Resources res;
    private Context context;
    private Str str;
    private Cursor mCursor;
    private LayoutInflater mInflater;
    private AlarmAdapter mAdapter;
    private LinearLayout mAlarmsList;

    private int curId; /* The alarm id for the View that was just long-pressed */
    private TransitionDrawable curTrans = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        context = getApplicationContext();
        res = getResources();
        str = new Str(res);

        setContentView(R.layout.alarms);
        setWindowSubtitle(res.getString(R.string.alarm_settings));

        alarms = new AlarmDatabase(context);
        mCursor = alarms.getAllAlarms(true);
        startManagingCursor(mCursor);

        //alarms.clearAllAlarms();

        mInflater = LayoutInflater.from(context);
        mAdapter = new AlarmAdapter();
        mAlarmsList = (LinearLayout) findViewById(R.id.alarms_list);
        populateList();
        mCursor.registerDataSetObserver(new AlarmsObserver());

        View addAlarm = findViewById(R.id.add_alarm);
        addAlarm.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                addAlarm();
                mCursor.requery();
            }
        });
    }

    private void populateList() {
        mAlarmsList.removeAllViews();

        if (mCursor.moveToFirst()) {
            while (! mCursor.isAfterLast()) {
                View v = mInflater.inflate(R.layout.alarm_item , mAlarmsList, false);
                mAdapter.bindView(v);
                mAlarmsList.addView(v, mAlarmsList.getChildCount());
                mCursor.moveToNext();
            }
        }
    }

    // TODO: delete this
    private void addAlarm() {
        int i = (new java.util.Random()).nextInt(5);

        switch(i) {
        case 0:
            alarms.addAlarm(0,  0, true);
            break;
        case 1:
            alarms.addAlarm(1, 15, true);
            break;
        case 2:
            alarms.addAlarm(2, 95, false);
            break;
        case 3:
            alarms.addAlarm(3, 58, false);
            break;
        case 4:
            alarms.addAlarm(4,  0, false);
            break;
        default:
            break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCursor.close();
        alarms.close();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private void setWindowSubtitle(String subtitle) {
        setTitle(res.getString(R.string.app_full_name) + " - " + subtitle);
    }

    //@Override
    //public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
    //    getMenuInflater().inflate(R.menu.alarm_item_context, menu);
    //}

    @Override
    public boolean onContextItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.delete_alarm:
                alarms.deleteAlarm(curId);
                mCursor.requery();

                return true;
            default:
                break;
        }

        return super.onContextItemSelected(item);
    }

    private class AlarmsObserver extends DataSetObserver {
        public AlarmsObserver(){
            super();
        }

        @Override
        public void onChanged() {
            super.onChanged();
            populateList();
        }
    }

    private class AlarmAdapter {
        private int idIndex, typeIndex, thresholdIndex, enabledIndex;

        public AlarmAdapter() {
                   idIndex = mCursor.getColumnIndexOrThrow(AlarmDatabase.KEY_ID);
                 typeIndex = mCursor.getColumnIndexOrThrow(AlarmDatabase.KEY_TYPE);
            thresholdIndex = mCursor.getColumnIndexOrThrow(AlarmDatabase.KEY_THRESHOLD);
              enabledIndex = mCursor.getColumnIndexOrThrow(AlarmDatabase.KEY_ENABLED);
        }

        public void bindView(View view) {
            final  TextView summary_tv  = (TextView)       view.findViewById(R.id.alarm_summary);
            final      View summary_box =                  view.findViewById(R.id.alarm_summary_box);
            final      View indicator   =                  view.findViewById(R.id.indicator);
            final ImageView barOnOff    = (ImageView) indicator.findViewById(R.id.bar_onoff);
            //final  CheckBox clockOnOff = (CheckBox)  indicator.findViewById(R.id.clock_onoff);

            final int    id = mCursor.getInt(idIndex);
            int        type = mCursor.getInt(typeIndex);
            int   threshold = mCursor.getInt(thresholdIndex);
            Boolean enabled = (mCursor.getInt(enabledIndex) == 1);

            String s = str.alarm_types_display[type];
            if (str.alarm_type_values[type].equals("temp_rises")) {
                s += " " + threshold + str.degree_symbol + "C";
                // TODO: Convert to F if pref is to do so
            }
            if (str.alarm_type_values[type].equals("charge_rises") ||
                str.alarm_type_values[type].equals("charge_drops")) {
                s += " " + threshold + "%";
            }
            final String summary = s;

            barOnOff.setImageResource(enabled ? R.drawable.ic_indicator_on : R.drawable.ic_indicator_off);
            //clockOnOff.setChecked(enabled);
            summary_tv.setText(summary);

            indicator.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    //clockOnOff.toggle();
                    //Boolean enabled = clockOnOff.isChecked();
                    Boolean enabled = alarms.getEnabledness(id);
                    alarms.setEnabledness(id, ! enabled);

                    barOnOff.setImageResource(! enabled ? R.drawable.ic_indicator_on : R.drawable.ic_indicator_off);
                }
            });

            summary_box.setOnCreateContextMenuListener(new OnCreateContextMenuListener() {
                public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
                    getMenuInflater().inflate(R.menu.alarm_item_context, menu);
                    menu.setHeaderTitle(summary);
                    curId = id;

                    if (curTrans != null) {
                        curTrans.resetTransition();
                        curTrans = null;
                    }
                }
            });

            summary_box.setOnTouchListener(new OnTouchListener() {
                public boolean onTouch(View v, android.view.MotionEvent m) {
                    if (v.isPressed() && curTrans == null ) {
                        curTrans = (TransitionDrawable) v.getBackground().getCurrent();
                        curTrans.startTransition(350);
                    } else if (! v.isPressed() && curTrans != null) {
                        curTrans.resetTransition();
                        curTrans = null;
                    }

                    return false;
                }
            });

            summary_box.setOnKeyListener(new OnKeyListener() {
                public boolean onKey(View v, int keyCode, android.view.KeyEvent event) {
                    if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER &&
                        event.getAction() == android.view.KeyEvent.ACTION_DOWN) v.setPressed(true);

                    if (v.isPressed() && curTrans == null) {
                        curTrans = (TransitionDrawable) v.getBackground().getCurrent();
                        curTrans.startTransition(350);
                    } else if (curTrans != null) {
                        curTrans.resetTransition();
                        curTrans = null;
                    }

                    return false;
                }
            });

            //summary_box.setOnClickListener(new OnClickListener() {
            //    public void onClick(View v) {
            //    }
            //});
        }
    }
}