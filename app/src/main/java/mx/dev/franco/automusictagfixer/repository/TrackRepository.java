package mx.dev.franco.automusictagfixer.repository;

import android.arch.core.util.Function;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MediatorLiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.Transformations;
import android.arch.persistence.db.SimpleSQLiteQuery;
import android.arch.persistence.db.SupportSQLiteQuery;
import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import mx.dev.franco.automusictagfixer.datasource.Sorter;
import mx.dev.franco.automusictagfixer.media_store_retriever.AsyncFileReader;
import mx.dev.franco.automusictagfixer.room.Track;
import mx.dev.franco.automusictagfixer.room.TrackDAO;
import mx.dev.franco.automusictagfixer.room.TrackRoomDatabase;
import mx.dev.franco.automusictagfixer.utilities.Constants;
import mx.dev.franco.automusictagfixer.utilities.shared_preferences.AbstractSharedPreferences;

import static mx.dev.franco.automusictagfixer.datasource.TrackAdapter.ASC;

public class TrackRepository {

    private TrackDAO mTrackDao;
    private LiveData<List<Track>> mAllTrack;
    private MediatorLiveData<List<Track>> mMediatorTrackData = new MediatorLiveData<>();
    private AbstractSharedPreferences mAbstractSharedPreferences;
    private String mCurrentOrder;
    public TrackRepository(TrackRoomDatabase db, AbstractSharedPreferences abstractSharedPreferences, Context context){
        mTrackDao = db.trackDao();
        mAbstractSharedPreferences = abstractSharedPreferences;
        mCurrentOrder = mAbstractSharedPreferences.getString(Constants.SORT_KEY);
        String query = "SELECT * FROM track_table ORDER BY" + mCurrentOrder;
        if(mCurrentOrder == null)
            mCurrentOrder = " title ASC ";
        SupportSQLiteQuery  sqLiteQuery = new SimpleSQLiteQuery(query);
        //mAllTrack = mTrackDao.getAllTracks(sqLiteQuery);
        mMediatorTrackData.addSource(mTrackDao.getAllTracks(sqLiteQuery), new Observer<List<Track>>() {
            @Override
            public void onChanged(@Nullable List<Track> tracks) {
                mMediatorTrackData.setValue(tracks);
            }
        });
    }

    public LiveData<List<Track>> getAllTracks(){
        return mMediatorTrackData;
    }

    public void getDataFromTracksFirst(final AsyncFileReader.IRetriever iRetriever){
        boolean databaseCreationCompleted = mAbstractSharedPreferences.getBoolean(Constants.COMPLETE_READ);
        if(!databaseCreationCompleted) {
            AsyncFileReader asyncFileReader = new AsyncFileReader();
            asyncFileReader.setTask(AsyncFileReader.INSERT_ALL);
            asyncFileReader.setListener(iRetriever);
            asyncFileReader.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
        else {
            iRetriever.onFinish(false);
        }
    }

    public void getNewTracks(final AsyncFileReader.IRetriever iRetriever){
            AsyncFileReader asyncFileReader = new AsyncFileReader();
            asyncFileReader.setTask(AsyncFileReader.UPDATE_LIST);
            asyncFileReader.setListener(iRetriever);
            asyncFileReader.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public void update(Track track){
        new updateTrack(mTrackDao).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,track);
    }

    public void checkAll(){
        new checkAll(mTrackDao).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public void uncheckAll(){
        new uncheckAll(mTrackDao).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public void delete(Track track){
        new removeTrack(mTrackDao).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, track);
    }

    public List<Track> search(String query){
        return mTrackDao.search(query);
    }

    public boolean sortTracks(String order, int orderType, List<Track> currentTracks) {
        String orderBy;
        if(orderType == ASC){
            orderBy = " " + order + " ASC ";
        }
        else {
            orderBy = " " + order + " DESC ";
        }

        //mCurrentOrder = mAbstractSharedPreferences.getString(Constants.SORT_KEY);
        //No need to re sort if is the same order
        if(orderBy.equals(mCurrentOrder))
            return true;


        String query = "SELECT * FROM track_table ORDER BY" + mCurrentOrder;
        SupportSQLiteQuery  sqLiteQuery = new SimpleSQLiteQuery(query);
        mMediatorTrackData.removeSource(mTrackDao.getAllTracks(sqLiteQuery));
        mMediatorTrackData.addSource(mTrackDao.getAllTracks(sqLiteQuery), new Observer<List<Track>>() {
            @Override
            public void onChanged(@Nullable List<Track> tracks) {
                mMediatorTrackData.setValue(tracks);
            }
        });

        List<Track> currentList = new ArrayList<>(currentTracks);
        Sorter sSorter = Sorter.getInstance();
        sSorter.setSortParams(orderBy, orderType);
        Collections.sort(currentList, sSorter);


        mAbstractSharedPreferences.putString(Constants.SORT_KEY,orderBy);
        mMediatorTrackData.setValue(currentList);


        return true;
    }

    private static class checkAll extends AsyncTask<Void, Void, Void> {

        private TrackDAO mAsyncTaskDao;

        checkAll(TrackDAO dao) {
            mAsyncTaskDao = dao;
        }

        @Override
        protected Void doInBackground(final Void... params) {
            mAsyncTaskDao.checkAll();
            return null;
        }
    }

    private static class uncheckAll extends AsyncTask<Void, Void, Void> {

        private TrackDAO mAsyncTaskDao;

        uncheckAll(TrackDAO dao) {
            mAsyncTaskDao = dao;
        }

        @Override
        protected Void doInBackground(final Void... params) {
            mAsyncTaskDao.uncheckAll();
            return null;
        }
    }

    private static class updateTrack extends AsyncTask<Track, Void, Void> {

        private TrackDAO mAsyncTaskDao;

        updateTrack(TrackDAO dao) {
            mAsyncTaskDao = dao;
        }

        @Override
        protected Void doInBackground(final Track... track) {
            mAsyncTaskDao.update(track[0]);
            return null;
        }
    }


    private static class removeTrack extends AsyncTask<Track, Void, Void> {

        private TrackDAO mAsyncTaskDao;

        removeTrack(TrackDAO dao) {
            mAsyncTaskDao = dao;
        }

        @Override
        protected Void doInBackground(final Track... track) {
            mAsyncTaskDao.delete(track[0]);
            return null;
        }
    }

}
