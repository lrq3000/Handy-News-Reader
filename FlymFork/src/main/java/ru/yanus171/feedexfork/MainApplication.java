/**
 * Flym
 * <p/>
 * Copyright (c) 2012-2015 Frederic Julian
 * <p/>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ru.yanus171.feedexfork;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.Configuration;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.StrictMode;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ru.yanus171.feedexfork.activity.BaseActivity;
import ru.yanus171.feedexfork.activity.EditFeedActivity;
import ru.yanus171.feedexfork.activity.HomeActivity;
import ru.yanus171.feedexfork.activity.HomeActivityNewTask;
import ru.yanus171.feedexfork.service.FetcherService;
import ru.yanus171.feedexfork.utils.DebugApp;
import ru.yanus171.feedexfork.utils.Dog;
import ru.yanus171.feedexfork.utils.EntryUrlVoc;
import ru.yanus171.feedexfork.utils.FileUtils;
import ru.yanus171.feedexfork.utils.FileVoc;
import ru.yanus171.feedexfork.utils.LabelVoc;
import ru.yanus171.feedexfork.utils.PrefUtils;


import static android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_DOCUMENT;
import static ru.yanus171.feedexfork.provider.FeedData.EntryColumns.ENTRIES_FOR_FEED_CONTENT_URI;
import static ru.yanus171.feedexfork.provider.FeedData.EntryColumns.FAVORITES_CONTENT_URI;
import static ru.yanus171.feedexfork.provider.FeedData.EntryColumns.UNREAD_ENTRIES_CONTENT_URI;
import static ru.yanus171.feedexfork.service.FetcherService.Status;

public class MainApplication extends Application {

    private static Context mContext;

    public static Context getContext() {
        return mContext;
    }

    public static FileVoc mImageFileVoc = null;
    public static FileVoc mHTMLFileVoc = null;
    public static LabelVoc mLabelVoc = null;

    public static final String OPERATION_NOTIFICATION_CHANNEL_ID = "operation_channel";
    public static final String READING_NOTIFICATION_CHANNEL_ID = "reading_channel";
    public static final String UNREAD_NOTIFICATION_CHANNEL_ID = "unread_channel";

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = getApplicationContext();
        Status();
        mImageFileVoc = new FileVoc(FileUtils.INSTANCE.GetImagesFolder() );
        mHTMLFileVoc = new FileVoc(FileUtils.INSTANCE.GetHTMLFolder() );

        GoogleCheck.INSTANCE.check();

        LabelVoc.INSTANCE.initInThread();
        BaseActivity.InitLocale( mContext );

        Thread.setDefaultUncaughtExceptionHandler(new DebugApp().new UncaughtExceptionHandler(this));

        if (Build.VERSION.SDK_INT >= 24) {
            try {
                Method m = StrictMode.class.getMethod("disableDeathOnFileUriExposure");
                m.invoke(null);
            } catch (Exception e) {
                Dog.e("disableDeathOnFileUriExposure", e);
            }
        }
        PrefUtils.putBoolean(PrefUtils.IS_REFRESHING, false);


        if (Build.VERSION.SDK_INT >= 26) {
            Context context = MainApplication.getContext();
            {
                NotificationChannel channel = new NotificationChannel(OPERATION_NOTIFICATION_CHANNEL_ID, context.getString(R.string.long_operation), NotificationManager.IMPORTANCE_LOW);
                channel.setDescription(context.getString(R.string.long_operation));
                NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
                notificationManager.createNotificationChannel(channel);
            }
            {
                NotificationChannel channel = new NotificationChannel(READING_NOTIFICATION_CHANNEL_ID, context.getString(R.string.reading_article), NotificationManager.IMPORTANCE_LOW);
                channel.setDescription(context.getString(R.string.reading_article));
                NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
                notificationManager.createNotificationChannel(channel);
            }
            {
                NotificationChannel channel = new NotificationChannel(UNREAD_NOTIFICATION_CHANNEL_ID, context.getString(R.string.unread_article), NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription(context.getString(R.string.unread_article));
                NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
                notificationManager.createNotificationChannel(channel);
            }
        }

        mImageFileVoc.init1();
        mHTMLFileVoc.init1();
        EntryUrlVoc.INSTANCE.initInThread();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {

            ArrayList<ShortcutInfo> list = new ArrayList<ShortcutInfo>();
            ShortcutManager shortcutManager = getSystemService(ShortcutManager.class);
            list.add( new ShortcutInfo.Builder(getContext(), "idSearch")
                .setShortLabel( getContext().getString( R.string.menu_add_feed ) )
                .setIcon(Icon.createWithResource(getContext(), R.drawable.cup_new_add))
                .setIntent(new Intent( Intent.ACTION_WEB_SEARCH )
                               .setPackage( getContext().getPackageName() )
                               .setClass(getContext(), EditFeedActivity.class))
                .build() );
            list.add( new ShortcutInfo.Builder(getContext(), "idExternal")
                .setShortLabel( getContext().getString( R.string.externalLinks ) )
                .setIcon(Icon.createWithResource(getContext(), R.drawable.cup_new_load_later))

                .setIntent(new Intent(getContext(), HomeActivityNewTask.class)
                           .setAction( Intent.ACTION_MAIN )
                           .setData(ENTRIES_FOR_FEED_CONTENT_URI(FetcherService.GetExtrenalLinkFeedID() ) ) )
                          .build() );
            list.add( new ShortcutInfo.Builder(getContext(), "idFavorities")
                          .setShortLabel( getContext().getString( R.string.favorites ) )
                          .setIcon(Icon.createWithResource(getContext(), R.drawable.cup_with_star))
                          .setIntent(new Intent(getContext(), HomeActivityNewTask.class)
                                         .setAction( Intent.ACTION_MAIN )
                                         .setData( FAVORITES_CONTENT_URI ))
                          .build() );
            list.add( new ShortcutInfo.Builder(getContext(), "idUnread")
                          .setShortLabel( getContext().getString( R.string.unread_entries ) )
                          .setIcon(Icon.createWithResource(getContext(), R.drawable.cup_new_unread))
                          .setIntent(new Intent(getContext(), HomeActivityNewTask.class)
                                         .setAction( Intent.ACTION_MAIN )
                                         .setData( UNREAD_ENTRIES_CONTENT_URI ))
                          .build() );

            shortcutManager.setDynamicShortcuts(list);
        }
    }

    @Override
    public void onConfigurationChanged (Configuration newConfig) {
        BaseActivity.InitLocale( mContext );
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext( BaseActivity.InitLocale( base ));
    }
}
