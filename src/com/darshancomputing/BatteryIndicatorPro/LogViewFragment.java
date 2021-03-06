/*
    Copyright (c) 2010-2013 Darshan-Josiah Barber

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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import android.support.v4.app.DialogFragment;
import android.support.v4.app.ListFragment;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;

public class LogViewFragment extends ListFragment {
    private static BatteryInfoActivity activity;
    private LogDatabase logs;
    private Col col;
    private Cursor completeCursor;
    private Cursor filteredCursor;
    private Cursor timeDeltaCursor;
    private LayoutInflater mInflater;
    private LogAdapter mAdapter;
    private TextView header_text;
    private Boolean reversed = false;
    private Boolean convertF;

    private static final String LOG_TAG = "BatteryBot";

    private static final String[] CSV_ORDER = {LogDatabase.KEY_TIME, LogDatabase.KEY_STATUS_CODE, LogDatabase.KEY_CHARGE, 
                                               LogDatabase.KEY_TEMPERATURE, LogDatabase.KEY_VOLTAGE};

    private final Messenger messenger = new Messenger(new MessageHandler());
    private boolean serviceConnected;
    private Messenger serviceMessenger;
    private BatteryInfoService.RemoteConnection serviceConnection;

    public void bindService() {
        if (! serviceConnected) {
            Intent biServiceIntent = new Intent(activity.context, BatteryInfoService.class);
            serviceConnection = new BatteryInfoService.RemoteConnection(messenger);

            activity.context.bindService(biServiceIntent, serviceConnection, 0);
            serviceConnected = true;
        }
    }

    public class MessageHandler extends Handler {
        @Override
        public void handleMessage(Message incoming) {
            if (! serviceConnected) {
                Log.i(LOG_TAG, "serviceConected is false; ignoring message: " + incoming);
                return;
            }

            switch (incoming.what) {
            case BatteryInfoService.RemoteConnection.CLIENT_SERVICE_CONNECTED:
                serviceMessenger = incoming.replyTo;
                sendServiceMessage(BatteryInfoService.RemoteConnection.SERVICE_REGISTER_CLIENT);
                break;
            case BatteryInfoService.RemoteConnection.CLIENT_BATTERY_INFO_UPDATED:
                reloadList(false);
                break;
            default:
                super.handleMessage(incoming);
            }
        }
    }

    private void sendServiceMessage(int what) {
        Message outgoing = Message.obtain();
        outgoing.what = what;
        outgoing.replyTo = messenger;
        try { serviceMessenger.send(outgoing); } catch (android.os.RemoteException e) {}
    }

    /*
    private static final IntentFilter batteryChangedFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    private final Handler mHandler = new Handler();
    private final Runnable mUpdateStatus = new Runnable() {
        public void run() {
            reloadList(false);
        }
    };

    private final BroadcastReceiver mBatteryInfoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (! Intent.ACTION_BATTERY_CHANGED.equals(action)) return;

            // TODO: Do this better.  Perphaps have the Activity be the client of the Service, or perhaps
            //  leave it with CIF as client, but it calls Activity.infoUpdated() after receiving message
            //  from Service.  If so, make sure (and document) that Service shouldn't send that message
            //  until after logs have been updated (and not just info).  Perhaps even renaming message.

            // Give the service a couple seconds to process the update
            mHandler.postDelayed(mUpdateStatus, 2 * 1000);
        }
    };
*/

    @Override
    public View onCreateView (LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        mInflater = inflater;

        View logs_header = View.inflate(activity.context, R.layout.logs_header, null);
        header_text = (TextView) logs_header.findViewById(R.id.header_text);
        ListView lv = (ListView) view.findViewById(android.R.id.list);
        lv.addHeaderView(logs_header, null, false);
        setHeaderText();
        setListAdapter(mAdapter);

        return view;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = (BatteryInfoActivity) getActivity();

        setHasOptionsMenu(true);
        setRetainInstance(true);

        convertF = activity.settings.getBoolean(SettingsActivity.KEY_CONVERT_F, false);
        col = new Col();

        logs = new LogDatabase(activity.context);
        completeCursor = logs.getAllLogs(false);
        timeDeltaCursor = new TimeDeltaCursor(completeCursor);
        filteredCursor = new FilteredCursor(timeDeltaCursor);

        mAdapter = new LogAdapter(activity.context, filteredCursor);

        serviceConnection = new BatteryInfoService.RemoteConnection(messenger);
        bindService();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (serviceConnected) {
            activity.context.unbindService(serviceConnection);
            serviceConnected = false;
        }
        completeCursor.close();
        logs.close();
    }

    @Override
    public void onResume() {
        super.onResume();
        //activity.context.registerReceiver(mBatteryInfoReceiver, batteryChangedFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        //activity.context.unregisterReceiver(mBatteryInfoReceiver);
    }

    public class ConfirmClearLogsDialogFragment extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(activity)
                .setTitle(activity.res.getString(R.string.confirm_clear_logs))
                .setPositiveButton(activity.res.getString(R.string.yes),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface di, int id) {
                            logs.clearAllLogs();
                            reloadList(false);
                            di.cancel();
                        }
                    })
                .setNegativeButton(activity.res.getString(R.string.cancel),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface di, int id) {
                            di.cancel();
                        }
                    })
                .create();
        }
    }

    public class ConfigureLogFilterDialogFragment extends DialogFragment {
        final boolean[] checked_items = new boolean[activity.str.log_filter_pref_keys.length];

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            for (int i = 0; i < checked_items.length; i++) {
                checked_items[i] = activity.settings.getBoolean(activity.str.log_filter_pref_keys[i], true);
            }

            return new AlertDialog.Builder(activity)
                .setTitle(activity.res.getString(R.string.configure_log_filter))
                .setMultiChoiceItems(R.array.log_filters, checked_items,
                                     new DialogInterface.OnMultiChoiceClickListener() {
                                         @Override
                                         public void onClick(DialogInterface di, int id, boolean isChecked) {
                                             checked_items[id] = isChecked;
                                         }
                                     })
                .setPositiveButton(activity.res.getString(R.string.okay),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface di, int id) {
                            di.cancel(); // setFilters() is called in onCancel()
                        }
                    })
                .create();
        }

        @Override
        public void onCancel(DialogInterface di) {
            setFilters(checked_items);
        }
    }

    private void setFilters(boolean[] checked_items) {
        SharedPreferences.Editor editor = activity.settings.edit();

        for (int i = 0; i < checked_items.length; i++) {
            editor.putBoolean(activity.str.log_filter_pref_keys[i], checked_items[i]);
        }

        editor.commit();

        reloadList(false);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        inflater.inflate(R.menu.logs, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        switch (filteredCursor.getCount()) {
        case 0:
            menu.findItem(R.id.menu_clear).setEnabled(false);
            menu.findItem(R.id.menu_export).setEnabled(false);
            menu.findItem(R.id.menu_reverse).setEnabled(false);
            break;
        case 1:
            menu.findItem(R.id.menu_clear).setEnabled(true);
            menu.findItem(R.id.menu_export).setEnabled(true);
            menu.findItem(R.id.menu_reverse).setEnabled(false);
            break;
        default:
            menu.findItem(R.id.menu_clear).setEnabled(true);
            menu.findItem(R.id.menu_export).setEnabled(true);
            menu.findItem(R.id.menu_reverse).setEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        DialogFragment df;

        switch (item.getItemId()) {
        case R.id.menu_clear:
            df = new ConfirmClearLogsDialogFragment();
            df.show(getFragmentManager(), "TODO: What is this string for?");

            return true;
        case R.id.menu_log_filter:
            df = new ConfigureLogFilterDialogFragment();
            df.show(getFragmentManager(), "TODO: What is this string for?2");

            return true;
        case R.id.menu_export:
            exportCSV();

            return true;
        case R.id.menu_reverse:
            reversed = (reversed) ? false : true;
            reloadList(true);

            return true;
        case android.R.id.home:
            startActivity(new Intent(activity, BatteryInfoActivity.class));
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    private void reloadList(Boolean newQuery){
        if (newQuery) {
            completeCursor.close();
            completeCursor = logs.getAllLogs(reversed);
            timeDeltaCursor = new TimeDeltaCursor(completeCursor);
            filteredCursor = new FilteredCursor(timeDeltaCursor);

            mAdapter.changeCursor(filteredCursor);
        } else {
            filteredCursor.requery();
        }

        setHeaderText();
    }

    private void setHeaderText() {
        int count = filteredCursor.getCount();

        if (count == 0)
            header_text.setText(activity.str.logs_empty);
        else
            header_text.setText(activity.str.n_log_items(count));
    }

    private void exportCSV() {
        String state = Environment.getExternalStorageState();

        if (state != null && state.equals(Environment.MEDIA_MOUNTED_READ_ONLY)) {
            Toast.makeText(activity.context, activity.str.read_only_storage, Toast.LENGTH_SHORT).show();
            return;
        } else if (state == null || !state.equals(Environment.MEDIA_MOUNTED)) {
            Toast.makeText(activity.context, activity.str.inaccessible_w_reason + state, Toast.LENGTH_SHORT).show();
            return;
        }

        Date d = new Date();
        String csvFileName = "BatteryIndicatorPro-Logs-" + d.getTime() + ".csv";

        File root    = Environment.getExternalStorageDirectory();
        File csvFile = new File(root, csvFileName);

        String[] csvFields = {activity.str.date, activity.str.time, activity.str.status, activity.str.charge,
                              activity.str.temperature, activity.str.temperature_f, activity.str.voltage};

        try {
            if (!csvFile.createNewFile() || !csvFile.canWrite()) {
                Toast.makeText(activity.context, activity.str.inaccessible_storage, Toast.LENGTH_SHORT).show();
                return;
            }

            BufferedWriter buf = new BufferedWriter(new FileWriter(csvFile));

            int cols = csvFields.length;
            int i;
            for (i = 0; i < cols; i++) {
                buf.write(csvFields[i]);
                if (i != cols - 1) buf.write(",");
            }
            buf.write("\r\n");

            int statusCode;
            int[] statusCodes;
            int status, plugged, status_age;
            String s;

            for (completeCursor.moveToFirst(); !completeCursor.isAfterLast(); completeCursor.moveToNext()) {
                cols = CSV_ORDER.length;
                for (i = 0; i < cols; i++) {
                    if (CSV_ORDER[i].equals(LogDatabase.KEY_TIME)) {
                        d.setTime(completeCursor.getLong(mAdapter.timeIndex));
                        buf.write(mAdapter.dateFormat.format(d) + "," + mAdapter.timeFormat.format(d) + ",");
                    } else if (CSV_ORDER[i].equals(LogDatabase.KEY_STATUS_CODE)) {
                        statusCode  = completeCursor.getInt(mAdapter.statusCodeIndex);
                        statusCodes = LogDatabase.decodeStatus(statusCode);
                        status      = statusCodes[0];
                        plugged     = statusCodes[1];
                        status_age  = statusCodes[2];

                        if (status_age == LogDatabase.STATUS_OLD)
                            s = activity.str.log_statuses_old[status];
                        else
                            s = activity.str.log_statuses[status];
                        if (plugged > 0)
                            s += " " + activity.str.pluggeds[plugged];

                        buf.write(s + ",");
                    } else if (CSV_ORDER[i].equals(LogDatabase.KEY_CHARGE)) {
                        buf.write(String.valueOf(completeCursor.getInt(mAdapter.chargeIndex)) + ",");
                    } else if (CSV_ORDER[i].equals(LogDatabase.KEY_TEMPERATURE)) {
                        int temperature = completeCursor.getInt(mAdapter.temperatureIndex);
                        buf.write(String.valueOf(temperature / 10.0) + ",");
                        buf.write(String.valueOf(java.lang.Math.round(temperature * 9 / 5.0) / 10.0 + 32.0) + ",");
                    } else if (CSV_ORDER[i].equals(LogDatabase.KEY_VOLTAGE)) {
                        buf.write(String.valueOf(completeCursor.getInt(mAdapter.voltageIndex) / 1000.0));
                    }
                }
                buf.write("\r\n");
            }
            buf.close();
        } catch (Exception e) {
            Toast.makeText(activity.context, activity.str.inaccessible_storage, Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(activity.context, activity.str.file_written, Toast.LENGTH_SHORT).show();
    }

    // Based on http://stackoverflow.com/a/7343721/1427098
    private class FilteredCursor extends CursorWrapper {
        private Cursor wrappedCursor;

        private ArrayList<Integer> shownIDs;
        private int len;
        private int pos;

        public FilteredCursor(Cursor cursor) {
            super(cursor);

            shownIDs = new ArrayList<Integer>();
            wrappedCursor = cursor;

            refilter();
        }

        public void refilter() {
            if (wrappedCursor.isClosed()) return;

            shownIDs.clear();

            int wrappedCursorPos = wrappedCursor.getPosition();
            int statusCodeIndex = wrappedCursor.getColumnIndexOrThrow(LogDatabase.KEY_STATUS_CODE);

            boolean show_plugged_in    = activity.settings.getBoolean("plugged_in",    true);
            boolean show_unplugged     = activity.settings.getBoolean("unplugged",     true);
            boolean show_charging      = activity.settings.getBoolean("charging",      true);
            boolean show_discharging   = activity.settings.getBoolean("discharging",   true);
            boolean show_fully_charged = activity.settings.getBoolean("fully_charged", true);
            boolean show_unknown       = activity.settings.getBoolean("unknown",       true);

            for (wrappedCursor.moveToFirst(); !wrappedCursor.isAfterLast(); wrappedCursor.moveToNext()) {
                int statusCode    = wrappedCursor.getInt(statusCodeIndex);
                int[] statusCodes = LogDatabase.decodeStatus(statusCode);
                int status        = statusCodes[0];
                int plugged       = statusCodes[1];
                int status_age    = statusCodes[2];

                if (status == BatteryInfo.STATUS_FULLY_CHARGED && show_fully_charged) {
                    shownIDs.add(wrappedCursor.getPosition());
                } else if ((status == BatteryInfo.STATUS_UNKNOWN ||
                            status == BatteryInfo.STATUS_DISCHARGING ||
                            status == BatteryInfo.STATUS_NOT_CHARGING ||
                            status > BatteryInfo.STATUS_MAX) &&
                           show_unknown) {
                    shownIDs.add(wrappedCursor.getPosition());
                } else if (status_age == LogDatabase.STATUS_OLD) {
                    if ((status == BatteryInfo.STATUS_UNPLUGGED && show_discharging) ||
                        (status == BatteryInfo.STATUS_CHARGING  && show_charging))
                        shownIDs.add(wrappedCursor.getPosition());
                } else if (status_age == LogDatabase.STATUS_NEW) {
                    if ((status == BatteryInfo.STATUS_UNPLUGGED && show_unplugged) ||
                        (status == BatteryInfo.STATUS_CHARGING  && show_plugged_in))
                        shownIDs.add(wrappedCursor.getPosition());
                }
            }

            wrappedCursor.moveToPosition(wrappedCursorPos);

            len = shownIDs.size();
            pos = -1;
        }

        @Override
        public boolean requery() {
            boolean ret = super.requery();
            refilter();
            return ret;
        }

        @Override
        public int getCount() {
            return len;
        }

        @Override
        public boolean moveToPosition(int newPos) {
            boolean moved = super.moveToPosition(shownIDs.get(newPos));

            if (moved) pos = newPos;

            return moved;
        }

        @Override
        public final boolean move(int offset) {
            return moveToPosition(pos + offset);
        }

        @Override
        public final boolean moveToFirst() {
            return moveToPosition(0);
        }

        @Override
        public final boolean moveToLast() {
            return moveToPosition(len - 1);
        }

        @Override
        public final boolean moveToNext() {
            return moveToPosition(pos + 1);
        }

        @Override
        public final boolean moveToPrevious() {
            return moveToPosition(pos - 1);
        }

        @Override
        public final boolean isFirst() {
            return len != 0 && pos == 0;
        }

        @Override
        public final boolean isLast() {
            return len != 0 && pos == len - 1;
        }

        @Override
        public final boolean isBeforeFirst() {
            return len == 0 || pos == -1;
        }

        @Override
        public final boolean isAfterLast() {
            return len == 0 || pos == len;
        }

        @Override
        public int getPosition() {
            return pos;
        }
    }

    private class TimeDeltaCursor extends CursorWrapper {
        public static final String KEY_TIME_DELTA = "time_delta";

        private Cursor wrappedCursor;

        private int deltaColumnIndex;
        private String deltaColumnName = KEY_TIME_DELTA;

        private int statusCodeIndex, timeIndex;

        private long last_plugged, last_unplugged;

        private ArrayList<Long> deltas;

        public TimeDeltaCursor(Cursor cursor) {
            super(cursor);

            deltas = new ArrayList<Long>();
            wrappedCursor = cursor;

            deltaColumnIndex = super.getColumnCount();

            gen_deltas();
        }

        private void gen_delta() {
            long time         = wrappedCursor.getLong(timeIndex);
            int statusCode    = wrappedCursor.getInt(statusCodeIndex);
            int[] statusCodes = LogDatabase.decodeStatus(statusCode);
            int status        = statusCodes[0];
            int plugged       = statusCodes[1];
            int status_age    = statusCodes[2];

            if (status == BatteryInfo.STATUS_FULLY_CHARGED) {
                if (last_plugged > 0)
                   deltas.add(time - last_plugged);
                else
                   deltas.add(-1l);
            } else if (status_age == LogDatabase.STATUS_NEW && status == BatteryInfo.STATUS_UNPLUGGED) {
                if (last_plugged > 0)
                   deltas.add(time - last_plugged);
                else
                   deltas.add(-1l);

                last_unplugged = time;
            } else if (status_age == LogDatabase.STATUS_NEW && status == BatteryInfo.STATUS_CHARGING) {
                if (last_unplugged > 0)
                    deltas.add(time - last_unplugged);
                else
                    deltas.add(-1l);

                last_plugged = time;
            } else {
                deltas.add(-1l);
            }
        }

        private boolean wrappedIsChronological() {
            if (wrappedCursor.getCount() < 2) return true;
            boolean chrono = true;

            int pos = wrappedCursor.getPosition();

            wrappedCursor.moveToFirst();
            long time1 = wrappedCursor.getLong(timeIndex);
            wrappedCursor.moveToNext();
            long time2 = wrappedCursor.getLong(timeIndex);

            while (time2 == time1 && !wrappedCursor.isAfterLast()) {
                wrappedCursor.moveToNext();
                time2 = wrappedCursor.getLong(timeIndex);
            }

            if (time2 < time1)
                chrono = false;

            wrappedCursor.moveToPosition(pos);

            return chrono;
        }

        public void gen_deltas() {
            if (wrappedCursor.isClosed()) return;

            deltas.clear();

            last_plugged = -1;
            last_unplugged = -1;

            int wrappedCursorPos = wrappedCursor.getPosition();
            statusCodeIndex = wrappedCursor.getColumnIndexOrThrow(LogDatabase.KEY_STATUS_CODE);
                  timeIndex = wrappedCursor.getColumnIndexOrThrow(LogDatabase.KEY_TIME);

            if (wrappedIsChronological())
                for (wrappedCursor.moveToFirst(); !wrappedCursor.isAfterLast(); wrappedCursor.moveToNext())
                    gen_delta();
            else
                for (wrappedCursor.moveToLast(); !wrappedCursor.isBeforeFirst(); wrappedCursor.moveToPrevious())
                    gen_delta();

            wrappedCursor.moveToPosition(wrappedCursorPos);
        }

        @Override
        public boolean requery() {
            boolean ret = super.requery();
            gen_deltas();
            return ret;
        }

        @Override
        public int getColumnCount() {
            return deltaColumnIndex + 1;
        }

        @Override
        public int getColumnIndex(String columnName) {
            if (deltaColumnName.equals(columnName))
                return deltaColumnIndex;
            else
                return super.getColumnIndex(columnName);
        }

        @Override
        public int getColumnIndexOrThrow(String columnName) throws java.lang.IllegalArgumentException {
            if (deltaColumnName.equals(columnName))
                return deltaColumnIndex;
            else
                return super.getColumnIndexOrThrow(columnName);
        }

        @Override
        public String getColumnName(int columnIndex) {
            if (columnIndex == deltaColumnIndex)
                return deltaColumnName;
            else
                return super.getColumnName(columnIndex);
        }

        @Override
        public String[] getColumnNames() {
            String[] a = super.getColumnNames();
            String[] b = new String[a.length + 1];

            for (int i = 0; i < a.length; i++)
                b[i] = a[i];

            b[a.length] = deltaColumnName;

            return b;
        }

        @Override
        public long getLong(int columnIndex) {
            if (columnIndex == deltaColumnIndex){
                int pos = getPosition();

                if (! wrappedIsChronological())
                    pos = getCount() - 1 - pos;

                return deltas.get(pos);
            } else
                return super.getLong(columnIndex);
        }

        // TODO: This was introduced in API 11.  It seems to be safely ignored by APIs less than 11, other than
        //         taking a while for the verifier to no-op it.  Is it worth overriding to add only to
        //         API 11+ devices?  I think it only slows things down on the first run, and not noticeably on most devices.
        /*
        @Override
        public int getType(int columnIndex) {
            if (columnIndex == deltaColumnIndex)
                return Cursor.FIELD_TYPE_INTEGER;
            else
                return super.getType(columnIndex);
        }
        */

        @Override
        public boolean isNull(int columnIndex) {
            if (columnIndex == deltaColumnIndex)
                return false;
            else
                return super.isNull(columnIndex);
        }
    }

    private class LogAdapter extends CursorAdapter {
        public int statusCodeIndex, chargeIndex, timeIndex, temperatureIndex, voltageIndex, timeDeltaIndex;
        public DateFormat dateFormat, timeFormat;

        private Date d = new Date();

        public LogAdapter(Context context, Cursor cursor) {
            super(context, cursor);

            dateFormat = android.text.format.DateFormat.getDateFormat(context);
            timeFormat = android.text.format.DateFormat.getTimeFormat(context);

             statusCodeIndex = cursor.getColumnIndexOrThrow(LogDatabase.KEY_STATUS_CODE);
                 chargeIndex = cursor.getColumnIndexOrThrow(LogDatabase.KEY_CHARGE);
                   timeIndex = cursor.getColumnIndexOrThrow(LogDatabase.KEY_TIME);
            temperatureIndex = cursor.getColumnIndexOrThrow(LogDatabase.KEY_TEMPERATURE);
                voltageIndex = cursor.getColumnIndexOrThrow(LogDatabase.KEY_VOLTAGE);
              timeDeltaIndex = cursor.getColumnIndexOrThrow(TimeDeltaCursor.KEY_TIME_DELTA);
        }

        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            return mInflater.inflate(R.layout.log_item , parent, false);
        }

        public void bindView(View view, Context context, Cursor cursor) {
            TextView    status_tv = (TextView) view.findViewById(R.id.status);
            TextView   percent_tv = (TextView) view.findViewById(R.id.percent);
            TextView      time_tv = (TextView) view.findViewById(R.id.time);
            TextView temp_volt_tv = (TextView) view.findViewById(R.id.temp_volt);
            TextView time_diff_tv = (TextView) view.findViewById(R.id.time_diff);

            int statusCode = cursor.getInt(statusCodeIndex);
            int[] statusCodes = LogDatabase.decodeStatus(statusCode);
            int status     = statusCodes[0];
            int plugged    = statusCodes[1];
            int status_age = statusCodes[2];

            String s;

            if (status_age == LogDatabase.STATUS_OLD) {
                 status_tv.setTextColor(col.old_status);
                percent_tv.setTextColor(col.old_status);
                s = activity.str.log_statuses_old[status];

                time_diff_tv.setVisibility(View.GONE);
            } else {
                switch (status) {
                case 0:
                     status_tv.setTextColor(col.unplugged);
                    percent_tv.setTextColor(col.unplugged);
                    break;
                case 2:
                     status_tv.setTextColor(col.plugged);
                    percent_tv.setTextColor(col.plugged);
                    break;
                case 5:
                     status_tv.setTextColor(col.charged);
                    percent_tv.setTextColor(col.charged);
                    break;
                default:
                     status_tv.setTextColor(col.unknown);
                    percent_tv.setTextColor(col.unknown);
                }

                s = activity.str.log_statuses[status];
                long delta;

                switch (status) {
                case 0:
                case 5:
                    delta = cursor.getLong(timeDeltaIndex);

                    if (delta < 0) {
                        time_diff_tv.setVisibility(View.GONE);
                        break;
                    }

                    time_diff_tv.setText(String.format(activity.res.getString(R.string.after_n_hours_plugged_in),
                                                       delta / 1000.0 / 60.0 / 60.0));

                    time_diff_tv.setVisibility(View.VISIBLE);
                    break;
                case 2:
                    delta = cursor.getLong(timeDeltaIndex);

                    if (delta < 0) {
                        time_diff_tv.setVisibility(View.GONE);
                        break;
                    }

                    time_diff_tv.setText(String.format(activity.res.getString(R.string.after_n_hours_unplugged),
                                                       delta / 1000.0 / 60.0 / 60.0));

                    time_diff_tv.setVisibility(View.VISIBLE);
                    break;
                default:
                    time_diff_tv.setVisibility(View.GONE);
                }
            }

            if (plugged > 0)
                s += " " + activity.str.pluggeds[plugged];

            status_tv.setText(s);

            percent_tv.setText("" + cursor.getInt(chargeIndex) + "%");

            d.setTime(cursor.getLong(timeIndex));
            time_tv.setText(dateFormat.format(d) + "  " + timeFormat.format(d));

            int temperature = cursor.getInt(temperatureIndex);
            if (temperature != 0) temp_volt_tv.setText("" + activity.str.formatTemp(temperature, convertF));
            else temp_volt_tv.setText(""); /* TextViews are reused */

            int voltage = cursor.getInt(voltageIndex);
            if (voltage != 0) temp_volt_tv.setText(((String) temp_volt_tv.getText().toString()) + " / " + activity.str.formatVoltage(voltage));
        }
    }

    private class Col {
        public int old_status;
        public int charged;
        public int plugged;
        public int unplugged;
        public int unknown;

        public Col() {
            old_status = activity.res.getColor(R.color.log_old_status);
            charged    = activity.res.getColor(R.color.log_charged);
            plugged    = activity.res.getColor(R.color.log_plugged);
            unplugged  = activity.res.getColor(R.color.log_unplugged);
            unknown    = activity.res.getColor(R.color.log_unknown);
        }
    }
}
