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
 * <p/>
 * <p/>
 * Some parts of this software are based on "Sparse rss" under the MIT license (see
 * below). Please refers to the original project to identify which parts are under the
 * MIT license.
 * <p/>
 * Copyright (c) 2010-2012 Stefan Handschuh
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package ru.yanus171.feedexfork.service;

import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.text.Html;
import android.text.TextUtils;
import android.util.Pair;
import android.util.Xml;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import ru.yanus171.feedexfork.Constants;
import ru.yanus171.feedexfork.MainApplication;
import ru.yanus171.feedexfork.R;
import ru.yanus171.feedexfork.activity.EntryActivity;
import ru.yanus171.feedexfork.activity.HomeActivity;
import ru.yanus171.feedexfork.fragment.EntriesListFragment;
import ru.yanus171.feedexfork.parser.HTMLParser;
import ru.yanus171.feedexfork.parser.OPML;
import ru.yanus171.feedexfork.parser.OneWebPageParser;
import ru.yanus171.feedexfork.parser.RssAtomParser;
import ru.yanus171.feedexfork.provider.FeedData;
import ru.yanus171.feedexfork.provider.FeedData.EntryColumns;
import ru.yanus171.feedexfork.provider.FeedData.FeedColumns;
import ru.yanus171.feedexfork.provider.FeedData.TaskColumns;
import ru.yanus171.feedexfork.provider.FeedDataContentProvider;
import ru.yanus171.feedexfork.utils.ArticleTextExtractor;
import ru.yanus171.feedexfork.utils.Connection;
import ru.yanus171.feedexfork.utils.DebugApp;
import ru.yanus171.feedexfork.utils.Dog;
import ru.yanus171.feedexfork.utils.FileUtils;
import ru.yanus171.feedexfork.utils.HtmlUtils;
import ru.yanus171.feedexfork.utils.NetworkUtils;
import ru.yanus171.feedexfork.utils.PrefUtils;
import ru.yanus171.feedexfork.utils.Timer;
import ru.yanus171.feedexfork.utils.UiUtils;
import ru.yanus171.feedexfork.view.EntryView;
import ru.yanus171.feedexfork.view.StatusText;
import ru.yanus171.feedexfork.view.StorageItem;

import static android.provider.BaseColumns._ID;
import static ru.yanus171.feedexfork.Constants.DB_AND;
import static ru.yanus171.feedexfork.Constants.DB_IS_NOT_NULL;
import static ru.yanus171.feedexfork.Constants.DB_OR;
import static ru.yanus171.feedexfork.Constants.GROUP_ID;
import static ru.yanus171.feedexfork.Constants.URL_LIST;
import static ru.yanus171.feedexfork.MainApplication.NOTIFICATION_CHANNEL_ID;
import static ru.yanus171.feedexfork.parser.OPML.AUTO_BACKUP_OPML_FILENAME;
import static ru.yanus171.feedexfork.provider.FeedData.EntryColumns.FEED_ID;
import static ru.yanus171.feedexfork.provider.FeedData.EntryColumns.IMAGES_SIZE;
import static ru.yanus171.feedexfork.provider.FeedData.EntryColumns.LINK;
import static ru.yanus171.feedexfork.provider.FeedData.EntryColumns.WHERE_NOT_FAVORITE;
import static ru.yanus171.feedexfork.provider.FeedData.EntryColumns.WHERE_READ;
import static ru.yanus171.feedexfork.utils.FileUtils.APP_SUBDIR;

public class FetcherService extends IntentService {

    public static final String ACTION_REFRESH_FEEDS = FeedData.PACKAGE_NAME + ".REFRESH";
    public static final String ACTION_MOBILIZE_FEEDS = FeedData.PACKAGE_NAME + ".MOBILIZE_FEEDS";
    private static final String ACTION_LOAD_LINK = FeedData.PACKAGE_NAME + ".LOAD_LINK";
    public static final String EXTRA_STAR = "STAR";

    private static final int THREAD_NUMBER = 10;
    private static final int MAX_TASK_ATTEMPT = 3;

    private static final int FETCHMODE_DIRECT = 1;
    private static final int FETCHMODE_REENCODE = 2;
    public static final int FETCHMODE_EXERNAL_LINK = 3;

    private static final String CHARSET = "charset=";
    private static final String CONTENT_TYPE_TEXT_HTML = "text/html";
    private static final String HREF = "href=\"";

    private static final String HTML_BODY = "<body";
    private static final String ENCODING = "encoding=\"";
    public static final String CUSTOM_KEEP_TIME = "customKeepTime";
    public static final String IS_ONE_WEB_PAGE = "isOneWebPage";
    public static final String IS_RSS = "isRss";
    public static final String URL_NEXT_PAGE_CLASS_NAME = "UrlNextPageClassName";
    public static final String NEXT_PAGE_MAX_COUNT = "NextPageMaxCount";

    public static Boolean mCancelRefresh = false;
    private static final ArrayList<Long> mActiveEntryIDList = new ArrayList<>();
    private static Boolean mIsDownloadImageCursorNeedsRequery = false;

    //private static volatile Boolean mIsDeletingOld = false;

    public static final ArrayList<MarkItem> mMarkAsStarredFoundList = new ArrayList<>();

    /* Allow different positions of the "rel" attribute w.r.t. the "href" attribute */
    public static final Pattern FEED_LINK_PATTERN = Pattern.compile(
            "[.]*<link[^>]* ((rel=alternate|rel=\"alternate\")[^>]* href=\"[^\"]*\"|href=\"[^\"]*\"[^>]* (rel=alternate|rel=\"alternate\"))[^>]*>",
            Pattern.CASE_INSENSITIVE);
    public static int mMaxImageDownloadCount = PrefUtils.getImageDownloadCount();

    public static StatusText.FetcherObservable Status() {
        if ( mStatusText == null ) {
            mStatusText = new StatusText.FetcherObservable();
        }
        return mStatusText;
    }

    private static StatusText.FetcherObservable mStatusText = null;

    public FetcherService() {
        super(FetcherService.class.getSimpleName());
        HttpURLConnection.setFollowRedirects(true);
    }

    public static boolean hasMobilizationTask(long entryId) {
        Cursor cursor = MainApplication.getContext().getContentResolver().query(TaskColumns.CONTENT_URI, TaskColumns.PROJECTION_ID,
                TaskColumns.ENTRY_ID + '=' + entryId + DB_AND + TaskColumns.IMG_URL_TO_DL + Constants.DB_IS_NULL, null, null);

        boolean result = cursor.getCount() > 0;
        cursor.close();

        return result;
    }

    public static void addImagesToDownload(String entryId, ArrayList<String> images) {
        if (images != null && !images.isEmpty()) {
            ContentValues[] values = new ContentValues[images.size()];
            for (int i = 0; i < images.size(); i++) {
                values[i] = new ContentValues();
                values[i].put(TaskColumns.ENTRY_ID, entryId);
                values[i].put(TaskColumns.IMG_URL_TO_DL, images.get(i));
            }

            MainApplication.getContext().getContentResolver().bulkInsert(TaskColumns.CONTENT_URI, values);
        }
    }

    public static void addEntriesToMobilize(Long[] entriesId) {
        ContentValues[] values = new ContentValues[entriesId.length];
        for (int i = 0; i < entriesId.length; i++) {
            values[i] = new ContentValues();
            values[i].put(TaskColumns.ENTRY_ID, entriesId[i]);
        }

        MainApplication.getContext().getContentResolver().bulkInsert(TaskColumns.CONTENT_URI, values);
    }

    static boolean isBatteryLow() {

        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent battery = MainApplication.getContext().registerReceiver(null, ifilter);
        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        float batteryPct = level / (float)scale * 100;

        long lowLevelPct = 20;
        try {
            lowLevelPct = Math.max(50, Long.parseLong(PrefUtils.getString("refresh.min_update_battery_level", 20)) );
        } catch (Exception ignored) {
        }
        return batteryPct < lowLevelPct;
    }

    @Override
    public void onHandleIntent(final Intent intent) {
        if (intent == null) { // No intent, we quit
            return;
        }
        Status().ClearError();

        FileUtils.INSTANCE.reloadPrefs();

        if (intent.hasExtra(Constants.FROM_AUTO_BACKUP)) {
            LongOper(R.string.exportingToFile, new Runnable() {
                @Override
                public void run() {
                try {
                    final String sourceFileName = OPML.GetAutoBackupOPMLFileName();
                    OPML.exportToFile( sourceFileName );
                    //final ArrayList<String> resultList = new ArrayList<>();
                    //resultList.add( sourceFileName );
                    for (StorageItem destDir: FileUtils.INSTANCE.createStorageList() ) {
                        File destFile = new File(destDir.getMPath().getAbsolutePath() + "/" + APP_SUBDIR, AUTO_BACKUP_OPML_FILENAME);
                        if ( !destFile.getAbsolutePath().equals(sourceFileName) ) {
                            try {
                                FileUtils.INSTANCE.copy(new File(sourceFileName), destFile);
                                //resultList.add( destFile.getAbsolutePath() );
                            } catch ( Exception e ) {
                                e.printStackTrace();
                            }
                        }
                    }
                    PrefUtils.putLong( AutoJobService.LAST_JOB_OCCURED + PrefUtils.AUTO_BACKUP_INTERVAL, System.currentTimeMillis() );
                    UiUtils.RunOnGuiThread(  new Runnable() {
                        @Override
                        public void run() {
                            for ( int i =0; i < 2; i++ )
                                Toast.makeText( MainApplication.getContext(), getString(R.string.auto_backup_opml_file_created) + "\n" + sourceFileName, Toast.LENGTH_LONG ).show();
                        }
                    });
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                    DebugApp.SendException( e, FetcherService.this );
                }
                }
            });
            return;
        } else if (intent.hasExtra(Constants.FROM_IMPORT)) {
            LongOper(R.string.importingFromFile, new Runnable() {
                @Override
                public void run() {
                    try {
                        OPML.importFromFile( intent.getStringExtra( Constants.EXTRA_FILENAME ) );
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (Exception e) {
                        e.printStackTrace();
                        DebugApp.SendException(e, FetcherService.this);
                    }
                }
            });
            return;
        } else if (intent.hasExtra( Constants.SET_VISIBLE_ITEMS_AS_OLD )) {
            startForeground(Constants.NOTIFICATION_ID_REFRESH_SERVICE, StatusText.GetNotification("", ""));
            EntriesListFragment.SetVisibleItemsAsOld(intent.getStringArrayListExtra(URL_LIST ) );
            stopForeground(true);
            return;
        } else if (intent.hasExtra( Constants.FROM_DELETE_OLD )) {
            startForeground(Constants.NOTIFICATION_ID_REFRESH_SERVICE, StatusText.GetNotification("", ""));
            long keepTime = (long) (GetDefaultKeepTime() * 86400000L);
            long keepDateBorderTime = keepTime > 0 ? System.currentTimeMillis() - keepTime : 0;
            PrefUtils.putBoolean(PrefUtils.IS_REFRESHING, true);
            deleteOldEntries(keepDateBorderTime);
            deleteGhost();
            if ( PrefUtils.CALCULATE_IMAGES_SIZE() )
                CalculateImageSizes();
            if ( Build.VERSION.SDK_INT >= 21 )
                PrefUtils.putLong( AutoJobService.LAST_JOB_OCCURED + PrefUtils.DELETE_OLD_INTERVAL, System.currentTimeMillis() );
            PrefUtils.putBoolean(PrefUtils.IS_REFRESHING, false);
            stopForeground(true);
            return;
        }

        mIsWiFi = GetIsWifi();

        final boolean isFromAutoRefresh = intent.getBooleanExtra(Constants.FROM_AUTO_REFRESH, false);

        if (ACTION_MOBILIZE_FEEDS.equals(intent.getAction())) {
            ExecutorService executor = CreateExecutorService(); try {
                mobilizeAllEntries(executor);
                downloadAllImages(executor);
            } finally {
                executor.shutdown();
            }
        } else if (ACTION_LOAD_LINK.equals(intent.getAction())) {
            LongOper(R.string.loadingLink, new Runnable() {
                @Override
                public void run() {
                    ExecutorService executor = CreateExecutorService(); try {
                        LoadLink(GetExtrenalLinkFeedID(),
                                 intent.getStringExtra(Constants.URL_TO_LOAD),
                                 intent.getStringExtra(Constants.TITLE_TO_LOAD),
                                 FetcherService.ForceReload.No,
                                 true,
                                 true,
                                 intent.getBooleanExtra(EXTRA_STAR, false), AutoDownloadEntryImages.Yes);

                        downloadAllImages(executor);
                    } finally { executor.shutdown(); }
                }
            } );

        //} else if (ACTION_DOWNLOAD_IMAGES.equals(intent.getAction())) {
        //    downloadAllImages();
        } else { // == Constants.ACTION_REFRESH_FEEDS
            LongOper(R.string.RefreshFeeds, new Runnable() {
                @Override
                public void run() {
                    long keepTime = (long) (GetDefaultKeepTime() * 86400000L);
                    long keepDateBorderTime = keepTime > 0 ? System.currentTimeMillis() - keepTime : 0;

                    String feedId = intent.getStringExtra(Constants.FEED_ID);
                    String groupId = intent.getStringExtra(Constants.GROUP_ID);

                    mMarkAsStarredFoundList.clear();
                    int newCount;
                    ExecutorService executor = CreateExecutorService(); try {
                        try {
                            newCount = (feedId == null ?
                                    refreshFeeds( executor, keepDateBorderTime, groupId, isFromAutoRefresh) :
                                    refreshFeed( executor, feedId, keepDateBorderTime));

                        } finally {
                            if (mMarkAsStarredFoundList.size() > 5) {
                                ArrayList<String> list = new ArrayList<>();
                                for (MarkItem item : mMarkAsStarredFoundList)
                                    list.add(item.mCaption);
                                ShowNotification(TextUtils.join(", ", list),
                                        R.string.markedAsStarred,
                                        new Intent(FetcherService.this, HomeActivity.class),
                                        Constants.NOTIFICATION_ID_MANY_ITEMS_MARKED_STARRED);
                            } else if (mMarkAsStarredFoundList.size() > 0)
                                for (MarkItem item : mMarkAsStarredFoundList) {
                                    Uri entryUri = getEntryUri(item.mLink, item.mFeedID);

                                    int ID = -1;
                                    try {
                                        if (entryUri != null)
                                            ID = Integer.parseInt(entryUri.getLastPathSegment());
                                    } catch (Throwable ignored) {

                                    }

                                    ShowNotification(item.mCaption,
                                            R.string.markedAsStarred,
                                            GetActionIntent( Intent.ACTION_VIEW, entryUri),
                                            ID);
                                }
                        }

                        if (newCount > 0) {
                            if (PrefUtils.getBoolean(PrefUtils.NOTIFICATIONS_ENABLED, true)) {
                                Cursor cursor = getContentResolver().query(EntryColumns.CONTENT_URI, new String[]{Constants.DB_COUNT}, EntryColumns.WHERE_UNREAD, null, null);

                                cursor.moveToFirst();
                                newCount = cursor.getInt(0); // The number has possibly changed
                                cursor.close();

                                if (newCount > 0) {
                                    ShowNotification(getResources().getQuantityString(R.plurals.number_of_new_entries, newCount, newCount),
                                            R.string.flym_feeds,
                                            new Intent(FetcherService.this, HomeActivity.class),
                                            Constants.NOTIFICATION_ID_NEW_ITEMS_COUNT);
                                }
                            } else if (Constants.NOTIF_MGR != null) {
                                Constants.NOTIF_MGR.cancel(Constants.NOTIFICATION_ID_NEW_ITEMS_COUNT);
                            }
                        }

                        mobilizeAllEntries( executor );
                        downloadAllImages( executor );
                    } finally {
                        executor.shutdown();
                    }
                    if ( isFromAutoRefresh && Build.VERSION.SDK_INT >= 21 )
                        PrefUtils.putLong( AutoJobService.LAST_JOB_OCCURED + PrefUtils.REFRESH_INTERVAL, System.currentTimeMillis() );
                }
            } );
        }
    }

    private void deleteGhost() {
        final int status = Status().Start( R.string.deleting_ghost_entries, false );
        final Cursor cursor = getContentResolver().query(EntryColumns.CONTENT_URI, new String[] {LINK}, null, null, null );
        final HashSet<String> mapEntryLinkHash = new HashSet<>();
        while  ( cursor.moveToNext() )
            mapEntryLinkHash.add( FileUtils.INSTANCE.getLinkHash( cursor.getString( 0 ) ) );
        cursor.close();
        deleteGhostHtmlFiles( mapEntryLinkHash );
        deleteGhostImages( mapEntryLinkHash );
        Status().End( status );
    }


    private void deleteGhostHtmlFiles( final HashSet<String> mapEntryLink ) {
        if ( isCancelRefresh() )
            return;
        int deletedCount = 0;
        final File folder = FileUtils.INSTANCE.GetHTMLFolder();
        String[] fileNames = folder.list();
        if (fileNames != null  )
            for (String fileName : fileNames) {
                if ( !mapEntryLink.contains( fileName ) ) {
                    if ( new File( folder, fileName ).delete() )
                        deletedCount++;
                    Status().ChangeProgress(getString(R.string.deleteFullTexts) + String.format( " %d", deletedCount ) );
                    if (FetcherService.isCancelRefresh())
                        break;

                }
            }
        Status().ChangeProgress( "" );
        //Status().End( status );
    }

    private void deleteGhostImages(  final HashSet<String> setEntryLinkHash ) {
        if ( isCancelRefresh() )
            return;
        HashSet<String> setEntryLinkHashFavorities = new HashSet<>();
        Cursor cursor = getContentResolver().query( EntryColumns.FAVORITES_CONTENT_URI, new String[] {LINK}, null, null, null );
        while ( cursor.moveToNext() )
            setEntryLinkHashFavorities.add( cursor.getString( 0 ) );
        cursor.close();
        int deletedCount = 0;
        final File folder = FileUtils.INSTANCE.GetImagesFolder();
        File[] files = FileUtils.INSTANCE.GetImagesFolder().listFiles();
        final int status = Status().Start( getString(R.string.image_count) + String.format(": %d", files.length), false ); try {
            final int FIRST_COUNT_TO_DELETE = files.length - 8000;
            if (FIRST_COUNT_TO_DELETE > 500)
                Arrays.sort(files, new Comparator<File>() {

                    @Override
                    public int compare(File f1, File f2) {
                        return Long.valueOf(f1.lastModified()).compareTo(f2.lastModified());
                    }
                });
            if (isCancelRefresh())
                return;
            for (File file : files) {
                final String fileName = file.getName();
                final String[] list = TextUtils.split(fileName, "_");
                if (fileName.equals(".nomedia"))
                    continue;
                String linkHash = list[0];
                if ( deletedCount < FIRST_COUNT_TO_DELETE && !setEntryLinkHashFavorities.contains(linkHash) ||
                     list.length != 3 ||
                     list.length >= 2 && !setEntryLinkHash.contains(linkHash) ){
                    if (new File(folder, fileName).delete())
                        deletedCount++;
                    Status().ChangeProgress(getString(R.string.deleteImages) + String.format(" %d", deletedCount));
                    if (FetcherService.isCancelRefresh())
                        break;
                }
            }
        } finally {
            Status().ChangeProgress( "" );
            Status().End( status );
        }
    }

    private void LongOper( int textID, Runnable oper ) {
        LongOper( getString( textID ), oper );
    }

    private void LongOper( String title, Runnable oper ) {
        startForeground(Constants.NOTIFICATION_ID_REFRESH_SERVICE, StatusText.GetNotification("", title));
        Status().SetNotificationTitle( title );
        PrefUtils.putBoolean(PrefUtils.IS_REFRESHING, true);
        synchronized (mCancelRefresh) {
            mCancelRefresh = false;
        }
        try {
            oper.run();
        } catch (Exception e) {
            e.printStackTrace();
            //Toast.makeText( this, getString( R.string.error ) + ": " + e.getMessage(), Toast.LENGTH_LONG ).show();
            DebugApp.SendException( e, this );
        } finally {
            Status().SetNotificationTitle( "" );
            PrefUtils.putBoolean(PrefUtils.IS_REFRESHING, false);
            stopForeground(true);
            synchronized (mCancelRefresh) {
                mCancelRefresh = false;
            }
        }
    }

    public static float GetDefaultKeepTime() {
        return Float.parseFloat(PrefUtils.getString(PrefUtils.KEEP_TIME, "4"));
    }

    private static boolean mIsWiFi = false;
    private boolean GetIsWifi() {
        ConnectivityManager cm = (ConnectivityManager) MainApplication.getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return (ni != null && ni.getType() == ConnectivityManager.TYPE_WIFI );
    }
    public static boolean isNotCancelRefresh() {
        return !isCancelRefresh();
    }
    public static boolean isCancelRefresh() {
        synchronized (mCancelRefresh) {
            if ( !mIsWiFi && Status().mBytesRecievedLast > PrefUtils.getMaxSingleRefreshTraffic() * 1024 * 1024 )
                return true;
            //if (mCancelRefresh) {
            //    MainApplication.getContext().getContentResolver().delete( TaskColumns.CONTENT_URI, null, null );
            //}
            return mCancelRefresh;
        }
    }

    public static boolean isEntryIDActive(long id) {
        synchronized (mActiveEntryIDList) {
            return mActiveEntryIDList.contains( id );
        }
    }
    public static void setEntryIDActiveList(ArrayList<Long> list) {
        synchronized (mActiveEntryIDList) {
            mActiveEntryIDList.clear();
            mActiveEntryIDList.addAll( list );
        }
    }
    public static void addActiveEntryID( long value ) {
        synchronized (mActiveEntryIDList) {
            if ( !mActiveEntryIDList.contains( value ) )
                mActiveEntryIDList.add( value );
        }
    }
    public static void removeActiveEntryID( long value ) {
        synchronized (mActiveEntryIDList) {
            if ( mActiveEntryIDList.contains( value ) )
                mActiveEntryIDList.remove( value );
        }
    }
    public static void clearActiveEntryID() {
        synchronized (mActiveEntryIDList) {
            mActiveEntryIDList.clear();
        }
    }
    private static boolean isDownloadImageCursorNeedsRequery() {
        synchronized (mIsDownloadImageCursorNeedsRequery) {
            return mIsDownloadImageCursorNeedsRequery;
        }
    }
    public static void setDownloadImageCursorNeedsRequery( boolean value ) {
        synchronized (mIsDownloadImageCursorNeedsRequery) {
            mIsDownloadImageCursorNeedsRequery = value;
        }
    }

    public static void CancelStarNotification( long entryID ) {
        if ( Constants.NOTIF_MGR != null ) {
            Constants.NOTIF_MGR.cancel((int) entryID);
            Constants.NOTIF_MGR.cancel(Constants.NOTIFICATION_ID_MANY_ITEMS_MARKED_STARRED);
            Constants.NOTIF_MGR.cancel(Constants.NOTIFICATION_ID_NEW_ITEMS_COUNT);
        }
    }

    private void mobilizeAllEntries( ExecutorService executor) {
        final String statusText = getString(R.string.mobilizeAll);
        int status = Status().Start(statusText, false); try {
            final ContentResolver cr = getContentResolver();
            Cursor cursor = cr.query(TaskColumns.CONTENT_URI, new String[]{_ID, TaskColumns.ENTRY_ID, TaskColumns.NUMBER_ATTEMPT},
                    TaskColumns.IMG_URL_TO_DL + Constants.DB_IS_NULL, null, null);
            Status().ChangeProgress("");
            final ArrayList<ContentProviderOperation> operations = new ArrayList<>();

            ArrayList<Future<FetcherService.DownloadResult>> futures = new ArrayList<>();

            while (cursor.moveToNext() && !isCancelRefresh()) {
                final long taskId = cursor.getLong(0);
                final long entryId = cursor.getLong(1);
                int attemptCount = 0;
                if (!cursor.isNull(2)) {
                    attemptCount = cursor.getInt(2);
                }

                final int finalAttemptCount = attemptCount;
                futures.add( executor.submit( new Callable<DownloadResult>() {
                    @Override
                    public DownloadResult call() {
                        DownloadResult result = new DownloadResult();
                        result.mAttemptNumber = finalAttemptCount;
                        result.mTaskID = taskId;
                        result.mOK = false;

                        Cursor curEntry = cr.query(EntryColumns.CONTENT_URI(entryId), new String[]{EntryColumns.FEED_ID}, null, null, null);
                        curEntry.moveToFirst();
                        final String feedID = curEntry.getString( 0 );
                        curEntry.close();
                        if ( mobilizeEntry( entryId, ArticleTextExtractor.MobilizeType.Yes, IsAutoDownloadImages( feedID ), true, false, false)) {
                            ContentResolver cr = MainApplication.getContext().getContentResolver();
                            cr.delete(TaskColumns.CONTENT_URI(taskId), null, null);//operations.add(ContentProviderOperation.newDelete(TaskColumns.CONTENT_URI(taskId)).build());
                            result.mOK = true;
                        }
                        return result;
                    }
                }) );
            }

            FinishExecutionService( statusText, status,  operations, futures );

            cursor.close();

            if (!operations.isEmpty()) {
                Status().ChangeProgress(R.string.applyOperations);
                try {
                    cr.applyBatch(FeedData.AUTHORITY, operations);
                } catch (Throwable ignored) {
                }
            }
        } finally { Status().End( status ); }


    }

    public static AutoDownloadEntryImages IsAutoDownloadImages(String feedId) {
        final ContentResolver cr = MainApplication.getContext().getContentResolver();
        AutoDownloadEntryImages result = AutoDownloadEntryImages.Yes;
        Cursor curFeed = cr.query( FeedColumns.CONTENT_URI( feedId ), new String[] { FeedColumns.IS_IMAGE_AUTO_LOAD }, null, null, null );
        if ( curFeed.moveToFirst() )
            result = curFeed.isNull( 0 ) || curFeed.getInt( 0 ) == 1 ? AutoDownloadEntryImages.Yes : AutoDownloadEntryImages.No;
        curFeed.close();
        return result;
    }

    public enum AutoDownloadEntryImages {Yes, No}

    public static boolean mobilizeEntry(final long entryId,
                                        final ArticleTextExtractor.MobilizeType mobilize,
                                        final AutoDownloadEntryImages autoDownloadEntryImages,
                                        final boolean isCorrectTitle,
                                        final boolean isShowError,
                                        final boolean isForceReload ) {
        boolean success = false;
        ContentResolver cr = MainApplication.getContext().getContentResolver();
        Uri entryUri = EntryColumns.CONTENT_URI(entryId);
        Cursor entryCursor = cr.query(entryUri, null, null, null, null);

        if (entryCursor.moveToFirst()) {
            int linkPos = entryCursor.getColumnIndex(LINK);
            final String link = entryCursor.getString(linkPos);
            if ( isForceReload || !FileUtils.INSTANCE.isMobilized(link, entryCursor ) ) { // If we didn't already mobilized it
                int abstractHtmlPos = entryCursor.getColumnIndex(EntryColumns.ABSTRACT);
                final long feedId = entryCursor.getLong(entryCursor.getColumnIndex(EntryColumns.FEED_ID));
                Connection connection = null;

                try {

                    // Try to find a text indicator for better content extraction
                    String contentIndicator = null;
                    String text = entryCursor.getString(abstractHtmlPos);
                    if (!TextUtils.isEmpty(text)) {
                        text = Html.fromHtml(text).toString();
                        if (text.length() > 60) {
                            contentIndicator = text.substring(20, 40);
                        }
                    }

                    connection = new Connection( link);

                    String mobilizedHtml;
                    Status().ChangeProgress(R.string.extractContent);

                    if (FetcherService.isCancelRefresh())
                        return false;
                    Document doc = Jsoup.parse(connection.getInputStream(), null, "");

                    String title = entryCursor.getString(entryCursor.getColumnIndex(EntryColumns.TITLE));
                    //if ( entryCursor.isNull( titlePos ) || title == null || title.isEmpty() || title.startsWith("http")  ) {
                    if ( isCorrectTitle ) {
                        Elements titleEls = doc.getElementsByTag("title");
                        if (!titleEls.isEmpty())
                            title = titleEls.first().text();
                    }

                    mobilizedHtml = ArticleTextExtractor.extractContent(doc,
                                                                        link,
                                                                        contentIndicator,
                                                                        mobilize,
                                                                        !String.valueOf( feedId ).equals( GetExtrenalLinkFeedID() ),
                                                                        entryCursor.getInt(entryCursor.getColumnIndex(EntryColumns.IS_WITH_TABLES) ) == 1);

                    Status().ChangeProgress("");

                    if (mobilizedHtml != null) {
                        ContentValues values = new ContentValues();
                        FileUtils.INSTANCE.saveMobilizedHTML(link, mobilizedHtml, values);
                        if ( title != null )
                            values.put(EntryColumns.TITLE, title);

                        ArrayList<String> imgUrlsToDownload = new ArrayList<>();
                        if (autoDownloadEntryImages == AutoDownloadEntryImages.Yes && NetworkUtils.needDownloadPictures()) {
                            //imgUrlsToDownload = HtmlUtils.getImageURLs(mobilizedHtml);
                            HtmlUtils.replaceImageURLs( mobilizedHtml, "", -1, link, true, imgUrlsToDownload, null, mMaxImageDownloadCount );
                        }

                        String mainImgUrl;
                        if (!imgUrlsToDownload.isEmpty() ) {
                            mainImgUrl = HtmlUtils.getMainImageURL(imgUrlsToDownload);
                        } else {
                            mainImgUrl = HtmlUtils.getMainImageURL(mobilizedHtml);
                        }

                        if (mainImgUrl != null) {
                            values.put(EntryColumns.IMAGE_URL, mainImgUrl);
                        }

                        cr.update( entryUri, values, null, null );//operations.add(ContentProviderOperation.newUpdate(entryUri).withValues(values).build());

                        success = true;
                        if ( imgUrlsToDownload != null && !imgUrlsToDownload.isEmpty() ) {
                            addImagesToDownload(String.valueOf(entryId), imgUrlsToDownload);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    if ( isShowError ) {
                        String title = "";
                        Cursor cursor = cr.query( FeedColumns.CONTENT_URI( feedId ), new String[]{ FeedColumns.NAME }, null, null, null);
                        if ( cursor.moveToFirst() && cursor.isNull( 0 ) )
                            title = cursor.getString( 0 );
                        cursor.close();
                        Status().SetError(title + ": ", String.valueOf( feedId ), String.valueOf( entryId ), e);
                    } else {
                        ContentValues values = new ContentValues();
                        FileUtils.INSTANCE.saveMobilizedHTML( link, e.toString(), values );
                        cr.update( entryUri, values, null, null );
                    }
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            } else { // We already mobilized it
                success = true;
                //operations.add(ContentProviderOperation.newDelete(TaskColumns.CONTENT_URI(taskId)).build());
            }
        }
        entryCursor.close();
        return success;
    }


    public static Intent GetActionIntent( String action, Uri uri, Class<?> class1 ) {
        return new Intent(action, uri).setPackage( MainApplication.getContext().getPackageName() ).setClass( MainApplication.getContext(), class1 );
    }
    public static Intent GetActionIntent( String action, Uri uri ) {
        return GetActionIntent( action, uri, EntryActivity.class );
    }
    public static Intent GetIntent( String extra ) {
        return new Intent(MainApplication.getContext(), FetcherService.class).putExtra( extra, true );
    }
    public static void StartServiceLoadExternalLink(String url, String title, boolean star) {
        FetcherService.StartService( new Intent(MainApplication.getContext(), FetcherService.class)
                .setAction( ACTION_LOAD_LINK )
                .putExtra(Constants.URL_TO_LOAD, url)
                .putExtra(Constants.TITLE_TO_LOAD, title)
                .putExtra( EXTRA_STAR, star ));
    }

    public enum ForceReload {Yes, No}
//    public static void OpenLink( Uri entryUri ) {
//        PrefUtils.putString(PrefUtils.LAST_ENTRY_URI, entryUri.toString());
//        Intent intent = new Intent(MainApplication.getContext(), HomeActivity.class);
//        intent.setFlags( Intent.FLAG_ACTIVITY_NEW_TASK );
//        MainApplication.getContext().startActivity( intent );
//    }

    public static Uri GetEnryUri( final String url ) {
        Timer timer = new Timer( "GetEnryUri" );
        Uri entryUri = null;
        String url1 = url.replace("https:", "http:");
        String url2 = url.replace("http:", "https:");
        ContentResolver cr = MainApplication.getContext().getContentResolver();
        Cursor cursor = cr.query(EntryColumns.CONTENT_URI,
                new String[]{_ID, EntryColumns.FEED_ID},
                LINK + "='" + url1 + "'" + DB_OR + LINK + "='" + url2 + "'",
                null,
                null);
        try {
            if (cursor.moveToFirst())
                entryUri = Uri.withAppendedPath( EntryColumns.ENTRIES_FOR_FEED_CONTENT_URI( cursor.getString(1) ), cursor.getString(0) );
        } finally {
            cursor.close();
        }
        timer.End();
        return entryUri;
    }
    public static Pair<Uri,Boolean> LoadLink(final String feedID,
                                             final String url,
                                             final String title,
                                             final ForceReload forceReload,
                                             final boolean isCorrectTitle,
                                             final boolean isShowError,
                                             final boolean isStarred,
                                             AutoDownloadEntryImages autoDownloadEntryImages) {
        boolean load;
        final ContentResolver cr = MainApplication.getContext().getContentResolver();
        final int status = FetcherService.Status().Start(MainApplication.getContext().getString(R.string.loadingLink), false); try {
            Uri entryUri = GetEnryUri( url );
            if ( entryUri != null ) {
                load = (forceReload == ForceReload.Yes);
                if (load) {
                    ContentValues values = new ContentValues();
                    values.put(EntryColumns.DATE, (new Date()).getTime());
                    cr.update(entryUri, values, null, null);//operations.add(ContentProviderOperation.newUpdate(entryUri).withValues(values).build());
                }
            } else {

                ContentValues values = new ContentValues();
                values.put(EntryColumns.TITLE, title);
                values.put(EntryColumns.SCROLL_POS, 0);
                //values.put(EntryColumns.ABSTRACT, NULL);
                //values.put(EntryColumns.IMAGE_URL, NULL);
                //values.put(EntryColumns.AUTHOR, NULL);
                //values.put(EntryColumns.ENCLOSURE, NULL);
                values.put(EntryColumns.DATE, (new Date()).getTime());
                values.put(LINK, url);
                values.put(EntryColumns.IS_WITH_TABLES, 0);
                values.put(EntryColumns.IMAGES_SIZE, 0);
                if ( isStarred )
                    values.put(EntryColumns.IS_FAVORITE, 1);

                //values.put(EntryColumns.MOBILIZED_HTML, enclosureString);
                //values.put(EntryColumns.ENCLOSURE, enclosureString);
                entryUri = cr.insert(EntryColumns.ENTRIES_FOR_FEED_CONTENT_URI(feedID), values);
                load = true;
            }

            if ( forceReload == ForceReload.Yes )
                FileUtils.INSTANCE.deleteMobilized( entryUri );

            if ( load && !FetcherService.isCancelRefresh() )
                mobilizeEntry(Long.parseLong(entryUri.getLastPathSegment()),
                              ArticleTextExtractor.MobilizeType.Yes, autoDownloadEntryImages,  isCorrectTitle, isShowError, false);
            return new Pair<>(entryUri, load);
        } finally {
            FetcherService.Status().End(status);
        }
        //stopForeground( true );
    }

    private static String mExtrenalLinkFeedID = "";
    public static String GetExtrenalLinkFeedID() {
        //Timer timer = new Timer( "GetExtrenalLinkFeedID()" );
        synchronized ( mExtrenalLinkFeedID ) {
            if (mExtrenalLinkFeedID.isEmpty()) {

                ContentResolver cr = MainApplication.getContext().getContentResolver();
                Cursor cursor = cr.query(FeedColumns.CONTENT_URI,
                        FeedColumns.PROJECTION_ID,
                        FeedColumns.FETCH_MODE + "=" + FetcherService.FETCHMODE_EXERNAL_LINK, null, null);
                if (cursor.moveToFirst())
                    mExtrenalLinkFeedID = cursor.getString(0);
                cursor.close();

                if (mExtrenalLinkFeedID.isEmpty()) {
                    ContentValues values = new ContentValues();
                    values.put(FeedColumns.FETCH_MODE, FetcherService.FETCHMODE_EXERNAL_LINK);
                    values.put(FeedColumns.NAME, MainApplication.getContext().getString(R.string.externalLinks));
                    mExtrenalLinkFeedID = cr.insert(FeedColumns.CONTENT_URI, values).getLastPathSegment();
                }
            }
        }
        //timer.End();
        return mExtrenalLinkFeedID;
    }

    public static class DownloadResult{
        public Long mTaskID;
        public Integer mAttemptNumber;
        public Boolean mOK;

    }
    private static void downloadAllImages( ExecutorService executor ) {
        StatusText.FetcherObservable obs = Status();
        final String statusText = MainApplication.getContext().getString(R.string.AllImages);
        int status = obs.Start(statusText, false); try {

            ContentResolver cr = MainApplication.getContext().getContentResolver();
            Cursor cursor = cr.query(TaskColumns.CONTENT_URI, new String[]{_ID, TaskColumns.ENTRY_ID, TaskColumns.IMG_URL_TO_DL,
                    TaskColumns.NUMBER_ATTEMPT, LINK}, TaskColumns.IMG_URL_TO_DL + Constants.DB_IS_NOT_NULL, null, null);
            ArrayList<ContentProviderOperation> operations = new ArrayList<>();

            ArrayList<Future<DownloadResult>> futures = new ArrayList<>();
            while (cursor != null && cursor.moveToNext() && !isCancelRefresh() && !isDownloadImageCursorNeedsRequery()) {
                final long taskId = cursor.getLong(0);
                final long entryId = cursor.getLong(1);
                final String entryLink = cursor.getString(4);
                final String imgPath = cursor.getString(2);
                int attemptNum = 0;
                if (!cursor.isNull(3)) {
                    attemptNum = cursor.getInt(3);
                }
                final int finalNbAttempt = attemptNum;
                futures.add( executor.submit(new Callable<DownloadResult>() {
                    @Override
                    public DownloadResult call() {
                        DownloadResult result = new DownloadResult();
                        result.mAttemptNumber = finalNbAttempt;
                        result.mTaskID = taskId;
                        result.mOK = false;
                        try {
                            NetworkUtils.downloadImage(entryId, entryLink, imgPath, true, false);
                            result.mOK = true;
                        } catch ( Exception e ) {
                            Status().SetError( "", "", "", e );
                        }
                        return result;
                    }
                }) );
            }
            FinishExecutionService( statusText, status, operations, futures);

            cursor.close();

            if (!operations.isEmpty()) {
                //obs.Change( status, statusText );
                obs.ChangeProgress(R.string.applyOperations);
                try {
                    cr.applyBatch(FeedData.AUTHORITY, operations);
                } catch (Throwable ignored) {

                }
            }
        } finally { obs.End( status ); }

        if ( isDownloadImageCursorNeedsRequery() ) {
            setDownloadImageCursorNeedsRequery( false );
            downloadAllImages( executor );
        }
    }
    public static int FinishExecutionService(String statusText,
                                              int status,
                                              ArrayList<ContentProviderOperation> operations,
                                              ArrayList<Future<DownloadResult>> futures) {
        int countOK = 0;
        for ( Future<DownloadResult> item: futures ) {
            try {
                final DownloadResult result = item.get();
                if (result.mOK) {
                    countOK++;// If we are here, everything WAS OK
                    if ( operations != null )
                        operations.add(ContentProviderOperation.newDelete(TaskColumns.CONTENT_URI(result.mTaskID)).build());
                } else if ( operations != null ) {
                    if (result.mAttemptNumber + 1 > MAX_TASK_ATTEMPT) {
                        operations.add(ContentProviderOperation.newDelete(TaskColumns.CONTENT_URI(result.mTaskID)).build());
                    } else {
                        ContentValues values = new ContentValues();
                        values.put(TaskColumns.NUMBER_ATTEMPT, result.mAttemptNumber + 1);
                        operations.add(ContentProviderOperation.newUpdate(TaskColumns.CONTENT_URI(result.mTaskID)).withValues(values).build());
                    }
                }
                Status().Change(status, statusText + String.format(" %d/%d", futures.indexOf( item ) + 1, futures.size()));
            } catch (Exception e) {
                e.printStackTrace();//DebugApp.AddErrorToLog( null ,e );
            }
        }
        return countOK;
    }
    public static void downloadEntryImages(final String feedId, final long entryId, final String entryLink, final ArrayList<String> imageList ) {
        final StatusText.FetcherObservable obs = Status();
        final String statusText = MainApplication.getContext().getString(R.string.article_images_downloading);
        int status = obs.Start( statusText, true); try {
            int downloadedCount = 0;
            ExecutorService executor = CreateExecutorService(); try {
                ArrayList<Future<DownloadResult>> futures = new ArrayList<>();
                for( final String imgPath: imageList ) {
                    futures.add( executor.submit( new Callable<DownloadResult>() {
                        @Override
                        public DownloadResult call() {
                            DownloadResult result = new DownloadResult();
                            result.mOK = false;
                            if ( !isCancelRefresh() && isEntryIDActive( entryId ) ) {
                                try {
                                    NetworkUtils.downloadImage(entryId, entryLink, imgPath, true, false);
                                    result.mOK = true;
                                } catch (Exception e) {
                                    obs.SetError(entryLink, feedId, String.valueOf(entryId), e);
                                }
                            }
                            return result;
                        } } ) );
                }
                downloadedCount = FinishExecutionService(statusText, status, null, futures );
                //Dog.v( "downloadedCount = " + downloadedCount );
            } finally {
                executor.shutdown();
            }
            if ( downloadedCount > 0 )
                EntryView.NotifyToUpdate( entryId, entryLink );
        } catch ( Exception e ) {
            obs.SetError(null, "", String.valueOf(entryId), e);
        } finally {
            obs.ResetBytes();
            obs.End(status);
        }
    }

    private static ExecutorService CreateExecutorService() {
        return Executors.newFixedThreadPool(THREAD_NUMBER);
    }


    private void deleteOldEntries(final long defaultKeepDateBorderTime) {
        if ( isCancelRefresh() )
            return;
        int status = Status().Start(MainApplication.getContext().getString(R.string.deleteOldEntries), false);
        ContentResolver cr = MainApplication.getContext().getContentResolver();
        final Cursor cursor = cr.query(FeedColumns.CONTENT_URI,
                new String[]{_ID, FeedColumns.OPTIONS},
                FeedColumns.LAST_UPDATE + Constants.DB_IS_NOT_NULL, null, null);
        try {
            //mIsDeletingOld = true;
            while (cursor.moveToNext()) {
                long keepDateBorderTime = defaultKeepDateBorderTime;
                final String jsonText = cursor.isNull( 1 ) ? "" : cursor.getString(1);
                if ( !jsonText.isEmpty() )
                    try {
                        JSONObject jsonOptions = new JSONObject(jsonText);
                        if (jsonOptions.has(CUSTOM_KEEP_TIME))
                            keepDateBorderTime = jsonOptions.getDouble(CUSTOM_KEEP_TIME) == 0 ? 0 : System.currentTimeMillis() - (long) (jsonOptions.getDouble(CUSTOM_KEEP_TIME) * 86400000l);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                final long feedID = cursor.getLong(0);
                DeleteOldEntries(feedID, keepDateBorderTime);
            }
        } finally {
            Status().End(status);
            cursor.close();
        }
    }

    private void DeleteOldEntries(final long feedID, final long keepDateBorderTime) {
        if (keepDateBorderTime > 0 && !isCancelRefresh() ) {
            ContentResolver cr = MainApplication.getContext().getContentResolver();
            final String deleteRead = PrefUtils.getBoolean( "delete_read_articles", false ) ? DB_OR + WHERE_READ : "";
            String where = "(" + EntryColumns.DATE + '<' + keepDateBorderTime + deleteRead + ")" + DB_AND + WHERE_NOT_FAVORITE;
            // Delete the entries, the cache files will be deleted by the content provider
            cr.delete(EntryColumns.ENTRIES_FOR_FEED_CONTENT_URI(feedID), where, null);
        }
    }

    private int refreshFeeds(final ExecutorService executor, final long keepDateBorderTime, String groupID, final boolean isFromAutoRefresh) {
        String statusText = "";
        int status = Status().Start( statusText, false ); try {
            ContentResolver cr = getContentResolver();
            final Cursor cursor;
            String where = PrefUtils.getBoolean(PrefUtils.REFRESH_ONLY_SELECTED, false) && isFromAutoRefresh ? FeedColumns.IS_AUTO_REFRESH + Constants.DB_IS_TRUE : null;
            if (groupID != null)
                cursor = cr.query(FeedColumns.FEEDS_FOR_GROUPS_CONTENT_URI(groupID), FeedColumns.PROJECTION_ID, null, null, null);
            else
                cursor = cr.query(FeedColumns.CONTENT_URI, FeedColumns.PROJECTION_ID, where, null, null);

            ArrayList<Future<DownloadResult>> futures = new ArrayList<>();
            while (cursor.moveToNext()) {
                //Status().Start(String.format("%d from %d", cursor.getPosition(), cursor.getCount()));
                final String feedId = cursor.getString(0);
                futures.add(executor.submit(new Callable<DownloadResult>() {
                    @Override
                    public DownloadResult call() {
                        DownloadResult result = new DownloadResult();
                        result.mOK = false;
                        try {
                            if (!isCancelRefresh()) {
                                refreshFeed(executor, feedId, keepDateBorderTime);
                                result.mOK = true;
                            }
                        } catch (Exception ignored) {

                        }
                        return result;
                    }
                }));
                //Status().End();
            }
            cursor.close();
            return FinishExecutionService( statusText, status, null, futures );
        } finally { Status().End( status ); }
    }

    private int refreshFeed( ExecutorService executor, String feedId, long keepDateBorderTime) {


        int newCount = 0;

        if ( GetExtrenalLinkFeedID().equals( feedId ) )
            return 0;

        ContentResolver cr = getContentResolver();
        Cursor cursor = cr.query(FeedColumns.CONTENT_URI(feedId), null, null, null, null);

        if (cursor.moveToFirst()) {
            int urlPosition = cursor.getColumnIndex(FeedColumns.URL);
            int idPosition = cursor.getColumnIndex(_ID);
            int titlePosition = cursor.getColumnIndex(FeedColumns.NAME);
            if ( cursor.isNull( cursor.getColumnIndex(FeedColumns.REAL_LAST_UPDATE) ) ) {
                keepDateBorderTime = 0;
            }
            boolean isRss = true;
            boolean isOneWebPage = false;
            try {
                JSONObject jsonOptions  = new JSONObject( cursor.getString( cursor.getColumnIndex(FeedColumns.OPTIONS) ) );
                isRss = jsonOptions.getBoolean(IS_RSS);
                isOneWebPage = jsonOptions.has(IS_ONE_WEB_PAGE) && jsonOptions.getBoolean(IS_ONE_WEB_PAGE);

                final String feedID = cursor.getString(idPosition);
                final String feedUrl = cursor.getString(urlPosition);
                final boolean isLoadImages  = NetworkUtils.needDownloadPictures() && ( IsAutoDownloadImages( feedID ) == AutoDownloadEntryImages.Yes );
                final int status = Status().Start(cursor.getString(titlePosition), false); try {
                    if ( isRss )
                        newCount = ParseRSSAndAddEntries(feedUrl, cursor, keepDateBorderTime, feedID);
                    else if ( isOneWebPage )
                        newCount = OneWebPageParser.INSTANCE.parse(keepDateBorderTime, feedID, feedUrl, jsonOptions, isLoadImages, 0 );
                    else
                        newCount = HTMLParser.Parse(executor, feedID, feedUrl, jsonOptions, 1);
                } finally {
                    Status().End(status);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        cursor.close();
        return newCount;
    }


    private void ShowNotification(String text, int captionID, Intent intent, int ID){
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder notifBuilder = new NotificationCompat.Builder(MainApplication.getContext()) //
                .setContentIntent(contentIntent) //
                .setSmallIcon(R.mipmap.ic_launcher) //
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher)) //
                //.setTicker(text) //
                .setWhen(System.currentTimeMillis()) //
                .setAutoCancel(true) //
                .setContentTitle(getString(captionID)) //
                .setLights(0xffffffff, 0, 0);
        if (Build.VERSION.SDK_INT >= 26 )
            notifBuilder.setChannelId( NOTIFICATION_CHANNEL_ID );

        if (PrefUtils.getBoolean(PrefUtils.NOTIFICATIONS_VIBRATE, false)) {
            notifBuilder.setVibrate(new long[]{0, 1000});
        }

        String ringtone = PrefUtils.getString(PrefUtils.NOTIFICATIONS_RINGTONE, null);
        if (ringtone != null && ringtone.length() > 0) {
            notifBuilder.setSound(Uri.parse(ringtone));
        }

        if (PrefUtils.getBoolean(PrefUtils.NOTIFICATIONS_LIGHT, false)) {
            notifBuilder.setLights(0xffffffff, 300, 1000);
        }

        Notification nf;
        if (Build.VERSION.SDK_INT < 16)
            nf = notifBuilder.setContentText(text).build();
        else
            nf = new NotificationCompat.BigTextStyle(notifBuilder.setContentText(text)).bigText(text).build();

        if (Constants.NOTIF_MGR != null) {
            Constants.NOTIF_MGR.notify(ID, nf);
        }

    }

    private Uri getEntryUri(String entryLink, String feedID) {
        Uri entryUri = null;
        Cursor cursor = MainApplication.getContext().getContentResolver().query(
                EntryColumns.ENTRIES_FOR_FEED_CONTENT_URI(feedID),
                new String[]{_ID},
                LINK + "='" + entryLink + "'",
                null,
                null);
        if (cursor.moveToFirst())
            entryUri = EntryColumns.CONTENT_URI(cursor.getLong(0));
        cursor.close();
        return entryUri;
    }


    private static String ToString (InputStream inputStream, Xml.Encoding encoding ) throws
    IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        //InputStream inputStream = connection.getInputStream();

        byte[] byteBuffer = new byte[4096];

        int n;
        while ((n = inputStream.read(byteBuffer)) > 0) {
            Status().AddBytes(n);
            outputStream.write(byteBuffer, 0, n);
        }
        String content = outputStream.toString(encoding.name()).replace(" & ", " &amp; ");
        content = content.replaceAll( "<[a-z]+?:", "<" );
        content = content.replaceAll( "</[a-z]+?:", "</" );
        content = content.replace( "&mdash;", "-" );

        return content;
    }


    private int ParseRSSAndAddEntries(String feedUrl, Cursor cursor, long keepDateBorderTime, String feedId) {
        RssAtomParser handler = null;

        int fetchModePosition = cursor.getColumnIndex(FeedColumns.FETCH_MODE);
        int realLastUpdatePosition = cursor.getColumnIndex(FeedColumns.REAL_LAST_UPDATE);
        int retrieveFullscreenPosition = cursor.getColumnIndex(FeedColumns.RETRIEVE_FULLTEXT);
        int autoImageDownloadPosition = cursor.getColumnIndex(FeedColumns.IS_IMAGE_AUTO_LOAD);
        int titlePosition = cursor.getColumnIndex(FeedColumns.NAME);
        int urlPosition = cursor.getColumnIndex(FeedColumns.URL);
        int iconPosition = cursor.getColumnIndex(FeedColumns.ICON);

        Connection connection = null;
        ContentResolver cr = MainApplication.getContext().getContentResolver();
        try {

            connection = new Connection( feedUrl);
            String contentType = connection.getContentType();
            int fetchMode = cursor.getInt(fetchModePosition);

            boolean autoDownloadImages = cursor.isNull(autoImageDownloadPosition) || cursor.getInt(autoImageDownloadPosition) == 1;

            if (fetchMode == 0) {
                if (contentType != null) {
                    int index = contentType.indexOf(CHARSET);

                    if (index > -1) {
                        int index2 = contentType.indexOf(';', index);

                        try {
                            Xml.findEncodingByName(index2 > -1 ? contentType.substring(index + 8, index2) : contentType.substring(index + 8));
                            fetchMode = FETCHMODE_DIRECT;
                        } catch (UnsupportedEncodingException ignored) {
                            fetchMode = FETCHMODE_REENCODE;
                        }
                    } else {
                        fetchMode = FETCHMODE_REENCODE;
                    }

                } else {
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));

                    char[] chars = new char[20];

                    int length = bufferedReader.read(chars);

                    FetcherService.Status().AddBytes(length);

                    String xmlDescription = new String(chars, 0, length);

                    connection.disconnect();
                    connection = new Connection(feedUrl);

                    int start = xmlDescription.indexOf(ENCODING);

                    if (start > -1) {
                        try {
                            Xml.findEncodingByName(xmlDescription.substring(start + 10, xmlDescription.indexOf('"', start + 11)));
                            fetchMode = FETCHMODE_DIRECT;
                        } catch (UnsupportedEncodingException ignored) {
                            fetchMode = FETCHMODE_REENCODE;
                        }
                    } else {
                        // absolutely no encoding information found
                        fetchMode = FETCHMODE_DIRECT;
                    }
                }

                ContentValues values = new ContentValues();
                values.put(FeedColumns.FETCH_MODE, fetchMode);
                cr.update(FeedColumns.CONTENT_URI(feedId), values, null, null);
            }

            handler = new RssAtomParser(new Date(cursor.getLong(realLastUpdatePosition)),
                    keepDateBorderTime,
                    feedId,
                    cursor.getString(titlePosition),
                    feedUrl,
                    cursor.getInt(retrieveFullscreenPosition) == 1);
            handler.setFetchImages(NetworkUtils.needDownloadPictures() && autoDownloadImages);

            InputStream inputStream = connection.getInputStream();

            switch (fetchMode) {
                default:
                case FETCHMODE_DIRECT: {
                    if (contentType != null) {
                        int index = contentType.indexOf(CHARSET);

                        int index2 = contentType.indexOf(';', index);

                        parseXml(//cursor.getString(urlPosition),
                                inputStream,
                                Xml.findEncodingByName(index2 > -1 ? contentType.substring(index + 8, index2) : contentType.substring(index + 8)),
                                handler);

                    } else {
                        InputStreamReader reader = new InputStreamReader(connection.getInputStream());
                        parseXml(reader, handler);

                    }
                    break;
                }
                case FETCHMODE_REENCODE: {
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

                    byte[] byteBuffer = new byte[4096];

                    int n;
                    while ((n = inputStream.read(byteBuffer)) > 0) {
                        FetcherService.Status().AddBytes(n);
                        outputStream.write(byteBuffer, 0, n);
                    }

                    String xmlText = outputStream.toString();

                    int start = xmlText != null ? xmlText.indexOf(ENCODING) : -1;

                    if (start > -1) {
                        parseXml( new StringReader(new String(outputStream.toByteArray(),
                                                   xmlText.substring(start + 10,
                                                                     xmlText.indexOf('"', start + 11)))),
                                  handler );
                    } else {
                        // use content type
                        if (contentType != null) {
                            int index = contentType.indexOf(CHARSET);

                            if (index > -1) {
                                int index2 = contentType.indexOf(';', index);

                                try {
                                    StringReader reader = new StringReader(new String(outputStream.toByteArray(), index2 > -1 ? contentType.substring(
                                            index + 8, index2) : contentType.substring(index + 8)));
                                    parseXml(reader, handler);
                                } catch (Exception ignored) {
                                }
                            } else {
                                StringReader reader = new StringReader(new String(outputStream.toByteArray()));
                                parseXml(reader, handler);
                            }
                        }
                    }
                    break;
                }
            }


            connection.disconnect();
        } catch(FileNotFoundException e){
            if (handler == null || (!handler.isDone() && !handler.isCancelled())) {
                ContentValues values = new ContentValues();

                // resets the fetch mode to determine it again later
                values.put(FeedColumns.FETCH_MODE, 0);

                values.put(FeedColumns.ERROR, getString(R.string.error_feed_error));
                cr.update(FeedColumns.CONTENT_URI(feedId), values, null, null);
                FetcherService.Status().SetError( cursor.getString(titlePosition) + ": " + getString(R.string.error_feed_error), feedId, "", e);
            }
        } catch(Exception e){
            if (handler == null || (!handler.isDone() && !handler.isCancelled())) {
                ContentValues values = new ContentValues();

                // resets the fetch mode to determine it again later
                values.put(FeedColumns.FETCH_MODE, 0);

                values.put(FeedColumns.ERROR, e.getMessage() != null ? e.getMessage() : getString(R.string.error_feed_process));
                cr.update(FeedColumns.CONTENT_URI(feedId), values, null, null);

                FetcherService.Status().SetError(cursor.getString(titlePosition) + ": " + e.toString(),
                        feedId, "", e);
            }
        } finally{
            /* check and optionally find favicon */
            try {
                if (handler != null && cursor.getBlob(iconPosition) == null) {
                    if (handler.getFeedLink() != null)
                        NetworkUtils.retrieveFavicon(this, new URL(handler.getFeedLink()), feedId);
                    else
                        NetworkUtils.retrieveFavicon(this, new URL( feedUrl ), feedId);
                }
            } catch (Throwable ignored) {
            }

            if (connection != null) {
                connection.disconnect();
            }
        }
        return handler != null ? handler.getNewCount() : 0;
    }

    private static void parseXml ( InputStream in, Xml.Encoding
        encoding,
                ContentHandler contentHandler) throws IOException, SAXException {
            Status().ChangeProgress(R.string.parseXml);
            Xml.parse(ToString(in, encoding), contentHandler);
            Status().ChangeProgress("");
            Status().AddBytes(contentHandler.toString().length());
        }

        private static void parseXml (Reader reader,
                ContentHandler contentHandler) throws IOException, SAXException {
            Status().ChangeProgress(R.string.parseXml);
            Xml.parse(reader, contentHandler);
            Status().ChangeProgress("");
            Status().AddBytes(contentHandler.toString().length());
        }

        public static void cancelRefresh () {
            synchronized (mCancelRefresh) {
                MainApplication.getContext().getContentResolver().delete( TaskColumns.CONTENT_URI, null, null );
                mCancelRefresh = true;
            }
        }

        public static void deleteAllFeedEntries ( Uri uri ){
            int status = Status().Start("deleteAllFeedEntries", true);
            try {
                ContentResolver cr = MainApplication.getContext().getContentResolver();
                cr.delete(uri, WHERE_NOT_FAVORITE, null);
                if ( FeedDataContentProvider.URI_MATCHER.match(uri) == FeedDataContentProvider.URI_ENTRIES_FOR_FEED ) {
                    String feedID = uri.getPathSegments().get( 1 );
                    ContentValues values = new ContentValues();
                    values.putNull(FeedColumns.LAST_UPDATE);
                    values.putNull(FeedColumns.REAL_LAST_UPDATE);
                    cr.update(FeedColumns.CONTENT_URI(feedID), values, null, null);
                }
            } finally {
                Status().End(status);
            }

        }

//        public static void createTestData () {
//            int status = Status().Start("createTestData", true);
//            try {
//                {
//                    final String testFeedID = "10000";
//                    final String testAbstract1 = "safdkhfgsadjkhgfsakdhgfasdhkgf sadfdasfdsafasdfasd safdkhfgsadjkhgfsakdhgfasdhkgf sadfdasfdsafasdfasd safdkhfgsadjkhgfsakdhgfasdhkgf sadfdasfdsafasdfasd safdkhfgsadjkhgfsakdhgfasdhkgf sadfdasfdsafasdfasd safdkhfgsadjkhgfsakdhgfasdhkgf sadfdasfdsafasdfasd safdkhfgsadjkhgfsakdhgfasdhkgf sadfdasfdsafasdfasd safdkhfgsadjkhgfsakdhgfasdhkgf sadfdasfdsafasdfasd safdkhfgsadjkhgfsakdhgfasdhkgf sadfdasfdsafasdfasd safdkhfgsadjkhgfsakdhgfasdhkgf sadfdasfdsafasdfasd safdkhfgsadjkhgfsakdhgfasdhkgf sadfdasfdsafasdfasd safdkhfgsadjkhgfsakdhgfasdhkgf sadfdasfdsafasdfasd safdkhfgsadjkhgfsakdhgfasdhkgf sadfdasfdsafasdfasd safdkhfgsadjkhgfsakdhgfasdhkgf sadfdasfdsafasdfasd ";
//                    String testAbstract = "";
//                    for (int i = 0; i < 10; i++)
//                        testAbstract += testAbstract1;
//                    //final String testAbstract2 = "sfdsdafsdafs sfdsdafsdafs sfdsdafsdafs sfdsdafsdafs sfdsdafsdafs sfdsdafsdafs sfdsdafsdafs sfdsdafsdafs sfdsdafsdafs fffffffffffffff fffffffffffffff fffffffffffffff fffffffffffffff fffffffffffffff fffffffffffffff fffffffffffffff fffffffffffffff";
//
//                    deleteAllFeedEntries(EntryColumns.ENTRIES_FOR_FEED_CONTENT_URI( testFeedID) );
//
//                    ContentResolver cr = MainApplication.getContext().getContentResolver();
//                    ContentValues values = new ContentValues();
//                    values.put(_ID, testFeedID);
//                    values.put(FeedColumns.NAME, "testFeed");
//                    values.putNull(FeedColumns.IS_GROUP);
//                    //values.putNull(FeedColumns.GROUP_ID);
//                    values.putNull(FeedColumns.LAST_UPDATE);
//                    values.put(FeedColumns.FETCH_MODE, 0);
//                    cr.insert(FeedColumns.CONTENT_URI, values);
//
//                    for (int i = 0; i < 30; i++) {
//                        values.clear();
//                        values.put(_ID, i);
//                        values.put(EntryColumns.ABSTRACT, testAbstract);
//                        values.put(EntryColumns.TITLE, "testTitle" + i);
//                        cr.insert(EntryColumns.ENTRIES_FOR_FEED_CONTENT_URI(testFeedID), values);
//                    }
//                }
//
//                {
//                    // small
//                    final String testFeedID = "10001";
//                    final String testAbstract1 = "safdkhfgsadjkhgfsakdhgfasdhkgf sadfdasfdsafasdfasd safdkhfgsadjkhgfsakdhgfasdhkgf sadfdasfdsafasdfasd ";
//                    String testAbstract = "";
//                    for (int i = 0; i < 1; i++)
//                        testAbstract += testAbstract1;
//                    //final String testAbstract2 = "sfdsdafsdafs sfdsdafsdafs sfdsdafsdafs sfdsdafsdafs sfdsdafsdafs sfdsdafsdafs sfdsdafsdafs sfdsdafsdafs sfdsdafsdafs fffffffffffffff fffffffffffffff fffffffffffffff fffffffffffffff fffffffffffffff fffffffffffffff fffffffffffffff fffffffffffffff";
//
//                    deleteAllFeedEntries(EntryColumns.ENTRIES_FOR_FEED_CONTENT_URI( testFeedID) );
//
//                    ContentResolver cr = MainApplication.getContext().getContentResolver();
//                    ContentValues values = new ContentValues();
//                    values.put(_ID, testFeedID);
//                    values.put(FeedColumns.NAME, "testFeedSmall");
//                    values.putNull(FeedColumns.IS_GROUP);
//                    //values.putNull(FeedColumns.GROUP_ID);
//                    values.putNull(FeedColumns.LAST_UPDATE);
//                    values.put(FeedColumns.FETCH_MODE, 0);
//                    cr.insert(FeedColumns.CONTENT_URI, values);
//
//                    for (int i = 0; i < 30; i++) {
//                        values.clear();
//                        values.put(_ID, 100 + i);
//                        values.put(EntryColumns.ABSTRACT, testAbstract);
//                        values.put(EntryColumns.TITLE, "testTitleSmall" + i);
//                        cr.insert(EntryColumns.ENTRIES_FOR_FEED_CONTENT_URI(testFeedID), values);
//                    }
//                }
//            } finally {
//                Status().End(status);
//            }
//
//        }

    public static void StartService(Intent intent) {
        final Context context = MainApplication.getContext();

        final boolean isFromAutoRefresh = intent.getBooleanExtra(Constants.FROM_AUTO_REFRESH, false);
        //boolean isOpenActivity = intent.getBooleanExtra(Constants.OPEN_ACTIVITY, false);

        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        // Connectivity issue, we quit
        if (networkInfo == null || networkInfo.getState() != NetworkInfo.State.CONNECTED) {
            if (ACTION_REFRESH_FEEDS.equals(intent.getAction()) && !isFromAutoRefresh) {
                // Display a toast in that case
                UiUtils.RunOnGuiThread( new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(context, R.string.network_error, Toast.LENGTH_SHORT).show();
                    }
                });
            }
            return;
        }

        boolean skipFetch = isFromAutoRefresh && PrefUtils.getBoolean(PrefUtils.REFRESH_WIFI_ONLY, false)
                && networkInfo.getType() != ConnectivityManager.TYPE_WIFI;
        // We need to skip the fetching process, so we quit
        if (skipFetch)
            return;

        if (isFromAutoRefresh && Build.VERSION.SDK_INT < 26 && isBatteryLow())
            return;

        final boolean foreground = !ACTION_MOBILIZE_FEEDS.equals(intent.getAction());
        if (Build.VERSION.SDK_INT >= 26 && foreground)
            context.startForegroundService(intent);
        else
            context.startService( intent );
    }

    static Intent GetStartIntent() {
        return new Intent(MainApplication.getContext(), FetcherService.class)
                .setAction( FetcherService.ACTION_REFRESH_FEEDS );
    }

    void CalculateImageSizes() {
        final int status = Status().Start(R.string.setting_calculate_image_sizes, false ); try {
            {
                ContentValues values = new ContentValues();
                values.put( IMAGES_SIZE, 0 );
                getContentResolver().update( FeedColumns.CONTENT_URI, values, null, null );
            }

            final HashMap<Long, Long> mapEntryIDToSize = new HashMap<>();
            final HashMap<Long, Long> mapFeedIDToSize = new HashMap<>();

            final HashMap<String, Long> mapEntryLinkHashToID = new HashMap<>();
            final HashMap<String, Long> mapEntryLinkHashToFeedID = new HashMap<>();
            Cursor cursor = getContentResolver().query(EntryColumns.CONTENT_URI, new String[]{_ID, LINK, FEED_ID}, null, null, null);
            while (cursor.moveToNext()) {
                final String linkHash = FileUtils.INSTANCE.getLinkHash(cursor.getString(1));
                mapEntryLinkHashToID.put(linkHash, cursor.getLong(0));
                mapEntryLinkHashToFeedID.put(linkHash, cursor.getLong(2));
            }
            cursor.close();

            final HashMap<Long, Long> mapFeedIDToGroupID = new HashMap<>();
            cursor = getContentResolver().query(FeedColumns.CONTENT_URI, new String[]{_ID, GROUP_ID}, GROUP_ID + DB_IS_NOT_NULL, null, null);
            while (cursor.moveToNext())
                if (!cursor.isNull(1))
                    mapFeedIDToGroupID.put(cursor.getLong(0), cursor.getLong(1));
            cursor.close();

            File[] files = FileUtils.INSTANCE.GetImagesFolder().listFiles();
            if (isCancelRefresh())
                return;
            int index = 0;
            for (File file : files) {
                index++;
                if ( index % 71 == 0 ) {
                    Status().ChangeProgress(String.format("%d/%d", index, files.length));
                    if (FetcherService.isCancelRefresh())
                        break;
                }
                final String fileName = file.getName();
                final String[] list = TextUtils.split(fileName, "_");
                if (fileName.equals(".nomedia"))
                    continue;
                if (list.length >= 2) {
                    final String entryLinkHash = list[0];
                    if (!mapEntryLinkHashToID.containsKey(entryLinkHash))
                        continue;
                    final long entryID = mapEntryLinkHashToID.get(entryLinkHash);
                    final long feedID = mapEntryLinkHashToFeedID.get(entryLinkHash);
                    final long groupID = mapFeedIDToGroupID.containsKey(feedID) ? mapFeedIDToGroupID.get(feedID) : -1L;
                    final long size = file.length();

                    if (!mapEntryIDToSize.containsKey(entryID))
                        mapEntryIDToSize.put(entryID, size);
                    else
                        mapEntryIDToSize.put(entryID, mapEntryIDToSize.get(entryID) + size);

                    if (!mapFeedIDToSize.containsKey(feedID))
                        mapFeedIDToSize.put(feedID, size);
                    else
                        mapFeedIDToSize.put(feedID, mapFeedIDToSize.get(feedID) + size);

                    if (groupID != -1) {
                        if (!mapFeedIDToSize.containsKey(groupID))
                            mapFeedIDToSize.put(groupID, size);
                        else
                            mapFeedIDToSize.put(groupID, mapFeedIDToSize.get(groupID) + size);
                    }
                }
            }

            Status().ChangeProgress(R.string.applyOperations);
            if (FetcherService.isCancelRefresh())
                return;
            ArrayList<ContentProviderOperation> operations = new ArrayList<>();
            for (Map.Entry<Long, Long> item : mapEntryIDToSize.entrySet())
                operations.add(ContentProviderOperation.newUpdate(EntryColumns.CONTENT_URI(item.getKey()))
                                   .withValue(EntryColumns.IMAGES_SIZE, item.getValue()).build());
            if (FetcherService.isCancelRefresh())
                return;
            for (Map.Entry<Long, Long> item : mapFeedIDToSize.entrySet())
                operations.add(ContentProviderOperation.newUpdate(FeedColumns.CONTENT_URI(item.getKey()))
                                   .withValue(FeedColumns.IMAGES_SIZE, item.getValue()).build());
            if (FetcherService.isCancelRefresh())
                return;

            if (!operations.isEmpty())
                try {
                    FeedDataContentProvider.mNotifyEnabled = false;
                    getContentResolver().applyBatch(FeedData.AUTHORITY, operations);
                    FeedDataContentProvider.mNotifyEnabled = true;
                    getContentResolver().notifyChange(FeedColumns.GROUPED_FEEDS_CONTENT_URI, null);
                } catch (Exception e) {
                    DebugApp.AddErrorToLog(null, e);
                }
        } finally {
            Status().ChangeProgress( "" );
            Status().End( status );
        }

    }
}
