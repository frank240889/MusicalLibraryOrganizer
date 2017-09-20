package mx.dev.franco.musicallibraryorganizer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.transition.Fade;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.appindexing.Thing;
import com.google.android.gms.common.api.GoogleApiClient;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import mx.dev.franco.musicallibraryorganizer.services.DetectorInternetConnection;
import mx.dev.franco.musicallibraryorganizer.services.FixerTrackService;
import mx.dev.franco.musicallibraryorganizer.services.Job;

import static mx.dev.franco.musicallibraryorganizer.SplashActivity.sharedPreferences;
import static mx.dev.franco.musicallibraryorganizer.services.GnService.apiInitialized;


public class SelectFolderActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener,
        MediaPlayer.OnCompletionListener,
        Toolbar.OnMenuItemClickListener,
        ActionMode.Callback,
    TrackAdapter.AudioItemHolder.ClickListener{
    public static String TAG = SelectFolderActivity.class.getName();

    //static field to indicate that task must not continue in case
    //user cancel the operation
    public static volatile boolean shouldContinue = true;
    //flag to deny make concurrent tasks between automatic mode
    //and manual mode, and avoid inconsistent behaviour or data
    public static boolean isProcessingTask = false;

    //flag to indicate when the app is retrieving data from Gracenote service
    public static boolean isGettingData = false;

    //actions to indicate in snackbar what is happen
    public static final int ACTION_PLAY = 30;
    public static final int ACTION_EDIT = 31;
    public static final int ACTION_SHOW_INFO = 32;

    //Reasons why cannot execute task
    public static final int NO_INTERNET_CONNECTION = 40;
    public static final int NO_INITIALIZED_API = 41;
    public static final int PROCESSING_TASK = 42;

    //media player instance, only one is allowed
    public static CustomMediaPlayer mediaPlayer;
    //snackbar to indicate to user what is happening
    public static Snackbar snackbar;
    //Adapter with AudioItem objects for display in recyclerview
    public static TrackAdapter audioItemArrayAdapter;
    //datasource passed to adapter
    public static List<AudioItem> audioItemList;
    //general toast to indicate some actions happening to user.
    public static Toast statusProcessToast;

    //actions to indicate to app from where to retrieve data.
    private static final int RE_SCAN = 20;
    private static final int CREATE_DATABASE = 21;
    private static final int READ_FROM_DATABASE = 22;

    //message to user when permission to read files is not granted, or
    //in case there have no music mfiles
    private TextView searchAgainMessage;
    //search object, for search more quickly
    //any track in recyclerview list
    private SearchView searchView;
    //fab button, this executes main task: correct a bunch of selected tracks;
    //this executes the automatic mode, without intervention of user,
    //this button either can cancel the task, in case the user decide it.
    private FloatingActionButton fab;
    //swipe refresh layout for give to user the
    //facility to re scan his/her library, this is hold
    //to material design patterns
    private SwipeRefreshLayout swipeRefreshLayout;
    //recycler view used for better performance in case the
    //user has a huge musical library
    private RecyclerView recyclerView;
    //this action mode enables the contextual toolbar
    //on long click on item in list.
    private ActionMode actionMode;
    //this menu has some less useful (but important) options,
    private Menu menu;
    //instance to connection do datadabse
    private DataTrackDbHelper dbHelper;
    //current item that was first long clicked
    private AudioItem currentAudioItem;
    //local broadcast to manage response from FixerTrackService.
    private LocalBroadcastManager localBroadcastManager;
    //these filters help to separate the action to take,
    //depending on response from FixerTrackService
    private IntentFilter intentFilter;
    private IntentFilter intentFilter1;
    private IntentFilter intentFilter2;
    //the receiver of responses.
    private ResponseReceiver receiver;

    private GoogleApiClient client;

    //contextual toolbar
    private Toolbar toolbar;
    //global intent sent to FixerTrackService
    private Intent intentFixerTrackService;



    //ServiceConnection serviceConnection;
    //NewFilesScannerService newFilesScannerService;
    //DetectorChangesFiles detectorChangesFiles;


    @RequiresApi(api = Build.VERSION_CODES.M)
    @SuppressLint("UseSparseArrays")
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        intentFilter = new IntentFilter(FixerTrackService.ACTION_DONE);
        intentFilter1 = new IntentFilter(FixerTrackService.ACTION_CANCEL);
        intentFilter2 = new IntentFilter(FixerTrackService.ACTION_COMPLETE_TASK);
        receiver = new ResponseReceiver();
        localBroadcastManager = LocalBroadcastManager.getInstance(getApplicationContext());

        /*serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d(TAG_SELECT_FOLDER,"CONNECTED");
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.d(TAG_SELECT_FOLDER,"DISCONNECTED");
            }
        };*/

        //detectorChangesFiles = new DetectorChangesFiles("/storage/emulated/0/Music/TestMusic/", this, audioItemArrayAdapter);
        //detectorChangesFiles.startWatching();

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        getWindow().setAllowEnterTransitionOverlap(true);
        getWindow().setAllowReturnTransitionOverlap(true);
        getWindow().requestFeature(Window.FEATURE_ACTION_MODE_OVERLAY);
        getWindow().requestFeature(Window.FEATURE_CONTENT_TRANSITIONS);
        getWindow().setSharedElementExitTransition(new Fade());




        setContentView(R.layout.activity_select_folder);
        searchAgainMessage = (TextView) findViewById(R.id.searchAgainMessage);

        dbHelper = DataTrackDbHelper.getInstance(getApplicationContext());

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setOnMenuItemClickListener(this);
        setSupportActionBar(toolbar);

        this.fab = (FloatingActionButton) findViewById(R.id.fab);
        this.fab.hide();

        //Initialize data source and adapter
        audioItemList = new ArrayList<>();
        audioItemArrayAdapter = new TrackAdapter(getApplicationContext(), audioItemList, this);

        //Initialize recycler view, and swipe refresh layout
        swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.list_of_files);

        swipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.primaryColor,null));
        //Lets implement functionality of refresh layout listener
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                searchAgainMessage.setVisibility(View.GONE);
                //No permission to access files ? show reason
                if(!RequiredPermissions.ACCESS_GRANTED_FILES) {
                    showReason();
                }
                //lets read the files from content provider, type scan is re_scan because is the second (or more) use of app,
                //or the user make a refresh to get some files that has recently copied to SD card.
                else{
                    AsyncReadFile asyncReadFile = new AsyncReadFile(RE_SCAN);
                    asyncReadFile.execute();
                }
            }

        });

        statusProcessToast = Toast.makeText(getApplicationContext(),"",Toast.LENGTH_SHORT);
        statusProcessToast.setGravity(Gravity.CENTER,0,0);

        snackbar = Snackbar.make(swipeRefreshLayout, "", Snackbar.LENGTH_INDEFINITE);

        //If we already had the permission granted, lets go to read data from database and pass them to audioItemArrayAdapter, to show in the ListView,
        //otherwise we show the reason to access files
        if(!RequiredPermissions.ACCESS_GRANTED_FILES) {
            showReason();
        }

        else{
            //if we have the permission, check Bundle object to verify if the activity comes from onPause or from onCreate
            if(savedInstanceState == null){
                setupAdapter();
                int taskType = DataTrackDbHelper.existDatabase(this) && dbHelper.getCount(null) > 0 ? READ_FROM_DATABASE : CREATE_DATABASE;
                AsyncReadFile asyncReadFile = new AsyncReadFile(taskType);
                asyncReadFile.execute();
            }
        }




        this.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
                    ActivityCompat.requestPermissions(SelectFolderActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
                }
                else {

                    int canContinue = allowExecute(SelectFolderActivity.this);
                    if(canContinue != 0) {
                        showToast(canContinue);
                        return;
                    }

                    if(getCountSelectedItems() == 0) {
                        Toast toast = Toast.makeText(getApplicationContext(),getString(R.string.no_songs_to_correct),Toast.LENGTH_SHORT);
                        toast.setGravity(Gravity.CENTER,0,0);
                        toast.show();
                        return;
                    }

                    for(int i = 0; i < audioItemArrayAdapter.getItemCount() ; i++){
                        AudioItem audioItem = audioItemList.get(i);
                        if(audioItem.isSelected()){
                            SelectFolderActivity.this.recyclerView.smoothScrollToPosition(audioItem.getPosition());
                            break;
                        }
                    }


                    final Intent intent = new Intent(SelectFolderActivity.this, FixerTrackService.class);
                    intent.putExtra("singleTrack",false);
                    startService(intent);
                    startTask();

                }

            }
        });

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
        dbHelper = DataTrackDbHelper.getInstance(getApplicationContext());
        mediaPlayer = CustomMediaPlayer.getInstance(this);
        mediaPlayer.setOnCompletionListener(this);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState){
        super.onRestoreInstanceState(savedInstanceState);

    }

    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */

    @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.connect();
        AppIndex.AppIndexApi.start(client, getIndexApiAction());
        //Intent intent = new Intent(this, NewFilesScannerService.class);
        //bindService(intent,serviceConnection, Context.BIND_NOT_FOREGROUND);

    }

    @Override
    protected void onResume(){
        super.onResume();
        localBroadcastManager.registerReceiver(receiver,intentFilter);
        localBroadcastManager.registerReceiver(receiver,intentFilter1);
        localBroadcastManager.registerReceiver(receiver,intentFilter2);

    }

    @Override
    protected void onPause(){
        super.onPause();
        localBroadcastManager.unregisterReceiver(receiver);
    }

    @Override
    public void onStop() {

        //Log.d("onStop","onStop");
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        AppIndex.AppIndexApi.end(client, getIndexApiAction());
        client.disconnect();


        super.onStop();
    }

    @Override
    public void onDestroy(){
        if(mediaPlayer != null && mediaPlayer.isPlaying()){
            try {
                mediaPlayer.playPreview(mediaPlayer.getCurrentId());
            }catch (Exception e){
                e.printStackTrace();
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState){
        super.onSaveInstanceState(savedInstanceState);
        if(recyclerView != null) {
            savedInstanceState.putBoolean("createItemList", false);
            savedInstanceState.putParcelable("stateList", recyclerView.getLayoutManager().onSaveInstanceState());
        }
        //Log.d("onSaveInstanceState","onSaved");
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.

        getMenuInflater().inflate(R.menu.select_folder, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        searchView = (SearchView) MenuItemCompat.getActionView(searchItem);

        // Define the listener
        MenuItemCompat.OnActionExpandListener expandListener = new MenuItemCompat.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                if(audioItemArrayAdapter != null && audioItemArrayAdapter.getItemCount() > 0) {
                    audioItemArrayAdapter.getFilter().filter("");
                }

                searchView.setOnQueryTextListener(null);
                return true;  // Return true to collapse action swipeRefreshLayout
            }

            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener(){

                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        if(audioItemArrayAdapter !=null && audioItemArrayAdapter.getItemCount() > 0) {
                            audioItemArrayAdapter.getFilter().filter(newText);
                        }
                        else {
                            Toast toast = Toast.makeText(getApplicationContext(),getString(R.string.empty_list),Toast.LENGTH_SHORT);
                            toast.setGravity(Gravity.CENTER,0,0);
                            toast.show();
                        }
                        return true;
                    }
                });
                return true;  // Return true to expand action swipeRefreshLayout
            }
        };

        // Assign the listener to that action item
        MenuItemCompat.setOnActionExpandListener(searchItem, expandListener);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        //noinspection SimplifiableIfStatement

        switch (id){
            case R.id.select_all:
                    selectAllItems();
                break;
            case R.id.go_to_current_playback:
                if(mediaPlayer != null && mediaPlayer.isPlaying() && audioItemArrayAdapter.getItemCount() > 0){
                    int position = getItemByIdOrPath(mediaPlayer.getCurrentId(),"").getPosition();
                    recyclerView.smoothScrollToPosition(position);
                }
                else {
                    Toast toast = Toast.makeText(getApplicationContext(),getString(R.string.empty_list),Toast.LENGTH_SHORT);
                    toast.setGravity(Gravity.CENTER,0,0);
                    toast.show();
                }
                break;
            case R.id.eraseCache:
                removeCache();
                break;

            case R.id.faq:
                    Intent intent = new Intent(this,QuestionsActivity.class);
                    startActivity(intent);
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    private void removeCache() {
        Toast t = Toast.makeText(getApplicationContext(),getString(R.string.no_cache),Toast.LENGTH_SHORT);
        t.setGravity(Gravity.CENTER,0,0);
        t.show();
    }


    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation swipeRefreshLayout item clicks here.
        //item.setChecked(true);
        int id = item.getItemId();

        if (id == R.id.rate) {
            Toast.makeText(this, "En desarrollo", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.share) {
            Toast.makeText(this, "En desarrollo", Toast.LENGTH_SHORT).show();
        }
        else if(id == R.id.settings){

            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        }


        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }


    public static int allowExecute(Context mContext){
        Context context = mContext.getApplicationContext();
        //No internet connection
        if(!DetectorInternetConnection.isConnected(context)){
            return NO_INTERNET_CONNECTION;
        }

        //API not initialized
        if(!apiInitialized){
            Job.scheduleJob(context);
            return NO_INITIALIZED_API;
        }

        //Task is already executing
        if(isProcessingTask){
            return PROCESSING_TASK;
        }

        return 0;
    }

    public void setSnackBar(int action, String message, boolean playing, final long id, final Context context){

        switch (action){
            case ACTION_PLAY:
                snackbar.setDuration(Snackbar.LENGTH_INDEFINITE);
                snackbar.setAction("Detener", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if(mediaPlayer.isPlaying()){
                            try {
                                mediaPlayer.playPreview(mediaPlayer.getCurrentId());
                                snackbar.dismiss();
                            } catch (IOException | InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
                if(playing)
                    snackbar.show();
                else
                    snackbar.dismiss();

                snackbar.setText("Reproduciendo " + message);
                break;
            case ACTION_EDIT:
                snackbar.setText(message);
                snackbar.setDuration(5000);
                snackbar.setAction("Editar", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent2 = new Intent(SelectFolderActivity.this, DetailsTrackDialogActivity.class);
                        intent2.putExtra("itemId",id);
                        context.startActivity(intent2);
                        snackbar.dismiss();
                    }
                });
                snackbar.show();
                break;
            case ACTION_SHOW_INFO:
                snackbar.setText(message);
                snackbar.setDuration(Snackbar.LENGTH_SHORT);
                snackbar.setAction("", null);
                break;
        }
    }

    private void selectAllItems(){
        if(audioItemList.size() == 0 ){
            Toast t = Toast.makeText(getApplicationContext(),getString(R.string.no_items),Toast.LENGTH_SHORT);
            t.setGravity(Gravity.CENTER,0,0);
            t.show();
            return;
        }

        if(isProcessingTask){
            Toast t = Toast.makeText(getApplicationContext(),getString(R.string.processing_task),Toast.LENGTH_SHORT);
            t.setGravity(Gravity.CENTER,0,0);
            t.show();
            return;
        }


        SharedPreferences shaPreferences = getSharedPreferences("ShaPreferences", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = shaPreferences.edit();

        final boolean areAllSelected = audioItemArrayAdapter.areAllSelected();

        if(areAllSelected){
            audioItemArrayAdapter.setAllSelected(false);
            editor.putBoolean("allSelected",false);
        }
        else {
            audioItemArrayAdapter.setAllSelected(true);
            editor.putBoolean("allSelected",true);
        }
        editor.apply();


        new Thread(new Runnable() {
            @Override
            public void run() {
                ContentValues values = new ContentValues();
                values.put(TrackContract.TrackData.COLUMN_NAME_IS_SELECTED,!areAllSelected);
                dbHelper.updateData(values);
            }
        }).start();

        for(int f = 0; f< audioItemArrayAdapter.getItemCount() ; f++){
            audioItemList.get(f).setChecked(!areAllSelected);
            audioItemArrayAdapter.notifyItemChanged(f);
        }

    }

    public static AudioItem getItemByIdOrPath(long id, String path){
        AudioItem audioItem = null;

        if(id != -1){
            for(int t = 0; t < audioItemArrayAdapter.getItemCount() ; t++){
                if(audioItemList.get(t).getId() == id ){
                        audioItem =  audioItemList.get(t);
                    break;
                }
            }
            return audioItem;
        }

        if(path != null && !path.equals("")){
            for(int t = 0; t < audioItemArrayAdapter.getItemCount() ; t++){
                if(audioItemList.get(t).getNewAbsolutePath().equals(path)){
                        audioItem = audioItemList.get(t);
                    break;
                }
            }
        }

        return audioItem;
    }

    /**
     *
     * @return the number of elements marked
     *          as selected
     */
    int getCountSelectedItems(){
        int numberOfSelectedItems = 0;
        if(audioItemArrayAdapter != null && audioItemArrayAdapter.getItemCount() >0) {
            for (int t = 0; t < audioItemArrayAdapter.getItemCount(); t++) {
                if (audioItemList.get(t).isChecked()) {
                    numberOfSelectedItems++;
                }
            }
        }

        return numberOfSelectedItems;
    }
    protected void correctSong(final View view, final int position) throws IOException, InterruptedException {
        final long id = (long) view.findViewById(R.id.path).getTag();
        final AudioItem audioItem = getItemByIdOrPath(id,"");

        Log.d("ACCESS_GRANTED_FILES",position+"");


        if(!RequiredPermissions.ACCESS_GRANTED_FILES){
            Toast toast = Toast.makeText(getApplicationContext(), getString(R.string.permission_denied),Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.CENTER,0,0);
            toast.show();
            return;
        }

        if(audioItem.isProcessing()){
            Toast toast = Toast.makeText(getApplicationContext(), getString(R.string.current_file_processing),Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.CENTER,0,0);
            toast.show();
            return;
        }

        if(!checkFileIntegrity(audioItem)){
            showConfirmationDialog(position,audioItem,null);
            return;
        }


        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.confirm_correction_title)).setMessage(getString(R.string.confirm_correction) + " " + audioItem.getTitle() + "?")
                .setNegativeButton(getString(R.string.manual_mode), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        onClickCoverArt(view, position);
                    }
                })
                .setPositiveButton("Si", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        shouldContinue = true;
                        audioItem.setProcessing(true);
                        audioItemArrayAdapter.notifyItemChanged(audioItem.getPosition());
                        intentFixerTrackService = new Intent(SelectFolderActivity.this, FixerTrackService.class);
                        intentFixerTrackService.putExtra("singleTrack",true);
                        intentFixerTrackService.putExtra("id",id);
                        startService(intentFixerTrackService);
                        startTask();

                    }
                });
        final AlertDialog dialog =  builder.create();
        dialog.show();


    }

    public void cancelProcessing(){
        if(SelectFolderActivity.audioItemList != null && SelectFolderActivity.audioItemList.size() > 0){
            for(int k = 0 ; k < SelectFolderActivity.audioItemList.size() ; k++) {
                AudioItem audioItem = SelectFolderActivity.audioItemList.get(k);
                if (audioItem.isProcessing()){
                    audioItem.setProcessing(false);
                    audioItemArrayAdapter.notifyItemChanged(audioItem.getPosition());
                    Log.d("procssing_audio_file", audioItem.isProcessing() + "");
                }
            }
        }
    }

    public static boolean checkFileIntegrity(long id){
        final AudioItem audioItem = getItemByIdOrPath(id,"");
        String path = audioItem.getNewAbsolutePath();
        File file = new File(path);

        return file.exists() && file.length() > 0 && file.canRead();
    }

    public static boolean checkFileIntegrity(AudioItem audioItem){
        String path = audioItem.getNewAbsolutePath();
        File file = new File(path);

        return file.exists() && file.length() > 0 && file.canRead();
    }

    private void showConfirmationDialog(final int position, final AudioItem audioItem, final String[] ids){
        String msg = "";
        String title = "";

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        if(ids != null){
            msg = "¿Confirme que desea eliminar de la lista los items seleccionados?";
            title = "Eliminar seleccionados.";
            builder.setPositiveButton("Si", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    try {
                        if(mediaPlayer.isPlaying())
                            mediaPlayer.playPreview(mediaPlayer.getCurrentId());
                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace();
                    }
                    dbHelper.deleteItems(audioItemArrayAdapter.getIdsSelectedItems());
                    audioItemArrayAdapter.removeItems(audioItemArrayAdapter.getSelectedItems());
                    audioItemArrayAdapter.sortByPath();
                    audioItemArrayAdapter.renewItemsPositions();
                    actionMode.finish();
                }
            });
        }
        else{
            msg = getString(R.string.file_error);
            title = getString(R.string.title_dialog_file_error);
            builder.setPositiveButton("Si", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    try {
                        if(mediaPlayer.isPlaying())
                            mediaPlayer.playPreview(mediaPlayer.getCurrentId());
                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace();
                    }
                    audioItemArrayAdapter.removeItem(position);
                    dbHelper.removeItem(audioItem.getId(), TrackContract.TrackData.TABLE_NAME);
                    audioItemArrayAdapter.sortByPath();
                    audioItemArrayAdapter.renewItemsPositions();
                }
            });
        }

        builder.setTitle(title).setMessage(msg);

        final AlertDialog dialog =  builder.create();
        dialog.setCancelable(false);
        dialog.show();
    }


    public void onClickCoverArt(View view, int position){


        final long p = (long) ((ViewGroup)view.getParent()).findViewById(R.id.path).getTag();
        final AudioItem audioItem = getItemByIdOrPath(p,"");

        if(audioItem.isProcessing()){
            Toast toast = Toast.makeText(getApplicationContext(), getString(R.string.current_file_processing),Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.CENTER,0,0);
            toast.show();
            return;
        }

        if(!checkFileIntegrity(audioItem.getId())){
            showConfirmationDialog(position,audioItem,null);
            return;
        }

        Intent intent = new Intent(this, DetailsTrackDialogActivity.class);
        //TransitionManager.beginDelayedTransition();
        intent.putExtra("itemId",(long)((ViewGroup)view.getParent()).findViewById(R.id.path).getTag());

        startActivity(intent);
    }

    protected void selectItem(long id, View view){

        Cursor data = dbHelper.getDataRow(id);
        CheckBox checkBox = null;

        if(data != null){
            if(view != null) {
                checkBox = (CheckBox) view;
                Log.d("THE_PATH",view.getTag()+"");

            }

            data.moveToFirst();
            boolean isChecked = data.getInt(data.getColumnIndexOrThrow(TrackContract.TrackData.COLUMN_NAME_IS_SELECTED)) != 0;
            Log.d("IS_SELECTED_FROM_DB",isChecked+"");
            AudioItem audioItem = getItemByIdOrPath(id, "");
            Log.d("ID_POSITION",id + "_" + audioItem.getPosition());
            ContentValues newValues = new ContentValues();
            if(isChecked) {
                newValues.put(TrackContract.TrackData.COLUMN_NAME_IS_SELECTED, false);
                audioItem.setChecked(false);
                if(checkBox != null){
                    checkBox.setChecked(false);
                }
                else {
                    audioItemArrayAdapter.notifyItemChanged(audioItem.getPosition());
                }


            }
            else {
                newValues.put(TrackContract.TrackData.COLUMN_NAME_IS_SELECTED, true);
                audioItem.setChecked(true);
                if(checkBox != null){
                    checkBox.setChecked(true);
                }
                else {
                    audioItemArrayAdapter.notifyItemChanged(audioItem.getPosition());
                }


            }
            dbHelper.updateData(id, newValues);
            isChecked = data.getInt(data.getColumnIndexOrThrow(TrackContract.TrackData.COLUMN_NAME_IS_SELECTED)) != 0;
            Log.d("IS_SELECTED_NOW",isChecked+"");
            newValues.clear();
            data.close();
            data = null;
            System.gc();
        }
    }

    protected void executeScan(){
        Toast toast = Toast.makeText(this, "Buscando música", Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.CENTER,0,0);
        toast.show();
        setupAdapter();
        AsyncReadFile asyncReadFile = new AsyncReadFile(CREATE_DATABASE);
        asyncReadFile.execute();

    }

    @Override
    public void onItemClicked(int position, View view) {
        if (actionMode == null){
            switch (view.getId()) {
                case R.id.coverArt:
                    onClickCoverArt(view, position);
                    break;
                case R.id.checkBoxTrack:
                    selectItem((long) view.getTag(), view);
                    break;
                default:

                    int canContinue = allowExecute(SelectFolderActivity.this);
                    if(canContinue != 0) {
                        showToast(canContinue);
                        return;
                    }

                    try {
                        correctSong(view, position);
                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace();
                    }
                    break;
            }
        }
        else {
            long id;

            switch (view.getId()) {
                case R.id.coverArt:
                    id = (long) ((ViewGroup)view.getParent()).findViewById(R.id.path).getTag();
                    break;
                case R.id.checkBoxTrack:
                    id = (long)view.getTag();
                    break;
                default:
                    id = (long)view.findViewById(R.id.path).getTag();
                    break;
            }
            toggleSelection(position,id);

        }
    }

    @Override
    public boolean onItemLongClicked(int position, View view) {
        Log.d("longclic",position+"");
        if(actionMode == null) {
            currentAudioItem = getItemByIdOrPath((long)view.findViewById(R.id.path).getTag(),null);
            SelectFolderActivity.this.actionMode = startSupportActionMode(SelectFolderActivity.this);
            toggleSelection(position, currentAudioItem.getId());
        }
        else{
            currentAudioItem = null;
            actionMode.finish();
            actionMode = null;
        }
        return false;
    }

    private void toggleSelection(int position, long id) {
        audioItemArrayAdapter.toggleSelection(position, id);
        int count = audioItemArrayAdapter.getSelectedItemCount();

        if (count == 0) {
            actionMode.finish();
        } else {
            actionMode.invalidate();
        }
    }

    protected void setupAdapter(){
        Log.d("ADAPTER","SETUP_ADAPTER");

        recyclerView = (RecyclerView) findViewById(R.id.list_view_songs);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(linearLayoutManager);
        recyclerView.setHasFixedSize(true);
        recyclerView.setAdapter(audioItemArrayAdapter);
    }

    private void detachAdapter(){
        Log.d("ADAPTER","DETACH_ADAPTER");
        recyclerView.setAdapter(null);
    }

    protected void askForPermission(String permission) {

        switch (permission){
            case Manifest.permission.READ_EXTERNAL_STORAGE:
                //Sino tenemos el permiso lo pedimos
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{permission}, RequiredPermissions.READ_INTERNAL_STORAGE_PERMISSION);
                }
                else {
                    executeScan();
                }
            break;
        }


    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setFilePermissionGranted();
            executeScan();
        }
        else{
            searchAgainMessage.setVisibility(View.VISIBLE);
            /*Intent i = new Intent();
            i.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.addCategory(Intent.CATEGORY_DEFAULT);
            i.setData(Uri.parse("package:" + getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            i.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            startActivity(i);
            SelectFolderActivity.this.swipeRefreshLayout.setRefreshing(false);*/
        }

    }

    private void startTask(){
        this.fab.setOnClickListener(null);
        this.fab.setImageDrawable(getDrawable(R.drawable.ic_stop_white_24dp));
        this.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(SelectFolderActivity.this);
                builder.setTitle(R.string.cancelling).setMessage(R.string.cancel_task)
                        .setNegativeButton("No", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        })
                        .setPositiveButton("Si", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                                if(receiver != null && receiver.isOrderedBroadcast()) {
                                    receiver.abortBroadcast();
                                    receiver.clearAbortBroadcast();
                                }

                                FixerTrackService.cancelGnMusicIdFileProcessing();
                                cancelProcessing();
                                shouldContinue = false;
                                Log.d("shouldContinue",shouldContinue+"");
                                finishTaskByUser();
                            }
                        });
                final AlertDialog dialog =  builder.create();
                dialog.setCancelable(false);
                dialog.show();
            }
        });
    }

    private void finishTaskByUser(){
        this.fab.setOnClickListener(null);
        this.fab.setImageDrawable(getDrawable(R.drawable.ic_android_white_24px));
        this.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
                    ActivityCompat.requestPermissions(SelectFolderActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
                }
                else {


                    int canContinue = allowExecute(SelectFolderActivity.this);
                    if(canContinue != 0) {
                        showToast(canContinue);
                        return;
                    }

                    if(getCountSelectedItems() == 0) {
                        Toast toast = Toast.makeText(getApplicationContext(),getString(R.string.no_songs_to_correct),Toast.LENGTH_SHORT);
                        toast.setGravity(Gravity.CENTER,0,0);
                        toast.show();
                        return;
                    }

                    for(int i = 0; i < audioItemArrayAdapter.getItemCount() ; i++){
                        AudioItem audioItem = audioItemList.get(i);
                        if(audioItem.isSelected()){
                            SelectFolderActivity.this.recyclerView.smoothScrollToPosition(audioItem.getPosition());
                            break;
                        }
                    }

                    intentFixerTrackService = new Intent(SelectFolderActivity.this, FixerTrackService.class);
                    intentFixerTrackService.putExtra("singleTrack",false);
                    startService(intentFixerTrackService);
                    startTask();

                }
            }
        });
    }

    private void showToast(int reason){
        Toast t = Toast.makeText(getApplicationContext(),"",Toast.LENGTH_SHORT);
        t.setGravity(Gravity.CENTER,0,0);
        switch (reason){
            case SelectFolderActivity.NO_INTERNET_CONNECTION:
                t.setText(R.string.no_internet_connection);
                break;
            case SelectFolderActivity.PROCESSING_TASK:
                t.setText(R.string.processing_task);
                break;
            case SelectFolderActivity.NO_INITIALIZED_API:
                t.setText(R.string.initializing_recognition_api);
                break;
        }
        t.show();
    }

    private void setFilePermissionGranted(){
        SplashActivity.sharedPreferences = getSharedPreferences("ShaPreferences", Context.MODE_PRIVATE);
        SplashActivity.editor = sharedPreferences.edit();
        SplashActivity.editor.putBoolean("accessFilesPermission", true);
        SplashActivity.editor.apply();
        RequiredPermissions.ACCESS_GRANTED_FILES = true;
        Log.d("READ_INTERNAL",PackageManager.PERMISSION_GRANTED+"");
    }

    private void showReason(){
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.title_dialog_permision).setMessage(R.string.explanation_permission_access_files);
        builder.setPositiveButton(R.string.ok_button, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                askForPermission(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        });
        builder.setNegativeButton(R.string.cancel_button, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                searchAgainMessage.setVisibility(View.VISIBLE);
                SelectFolderActivity.this.swipeRefreshLayout.setRefreshing(false);
            }
        });
        final AlertDialog dialog =  builder.create();
        dialog.setCancelable(false);
        dialog.show();
    }



    @Override
    public void onCompletion(MediaPlayer mp) {
        CustomMediaPlayer.onCompletePlayback(mediaPlayer.getCurrentId());
                    SelectFolderActivity.snackbar.dismiss();
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        MenuInflater inflater = mode.getMenuInflater();
        inflater.inflate(R.menu.cab_menu, menu);
        fab.hide();
        swipeRefreshLayout.setEnabled(false);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {


        if(item.getItemId() == R.id.action_play){
            if(isProcessingTask){
                Toast toast = Toast.makeText(getApplicationContext(),
                        "Espera a que termine la corrección en curso para poder editar esta canción.",
                        Toast.LENGTH_LONG);
                toast.setGravity(Gravity.CENTER,0,0);
                toast.show();
            }


            if(mediaPlayer == null){
                mediaPlayer = CustomMediaPlayer.getInstance(SelectFolderActivity.this);
            }
            try {
                mediaPlayer.playPreview(currentAudioItem.getId());
                if(mediaPlayer.isPlaying()){
                    //setSnackBar(mediaPlayer.getCurrentPath(), true);
                }
                else{
                    snackbar.dismiss();
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
            mode.finish();
        }
        else{
            showConfirmationDialog(0,null, audioItemArrayAdapter.getIdsSelectedItems());
        }

        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        this.actionMode = null;
        fab.show();
        audioItemArrayAdapter.clearSelection();
        swipeRefreshLayout.setEnabled(true);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        return false;
    }

    private class AsyncReadFile extends AsyncTask<Void, AudioItem, Void> {
        private DataTrackDbHelper trackDbHelper = DataTrackDbHelper.getInstance(getApplicationContext());
        private int taskType;
        private int counterPosition = 0;
        private Cursor data;
        private ContentValues contentValues = new ContentValues();

        AsyncReadFile(int codeTaskType){
            this.taskType = codeTaskType;
            if(codeTaskType == CREATE_DATABASE){
                trackDbHelper.clearDb();
            }
        }

        /**
         * This method gets data from media store,
         * only mp3 files data is retrieved
         * @return
         */
        Cursor getDataFromDevice() {

            String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension("mp3");

            //Only select mp3 music files
            String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0" + " and "
                    + MediaStore.Audio.Media.MIME_TYPE + " = " + " \'" +mimeType + "\'";

            String[] projection = { //Columns to retrieve
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.AlbumColumns.ALBUM ,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.DATA, // absolute path to audio file
                    MediaStore.Audio.AudioColumns.DISPLAY_NAME,
                    MediaStore.Audio.Media.SIZE
            };
            //get data from content provider
            //the last parameter sorts the data alphanumerically by the "DATA" field
            return getApplicationContext().getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    null,
                    MediaStore.Audio.Media.DATA);
        }

        /**
         * This method rescan mediastore DB, and
         * if there are new elements, add them to list
         */
        void rescanAndUpdateList(){
            data = getDataFromDevice();
            while(data.moveToNext()){
                boolean existInTable = trackDbHelper.existInDatabase(data.getInt(0));
                Log.d("existInTable",existInTable+"");
                if(!existInTable){
                    createAndAddAudioItem();
                    audioItemArrayAdapter.setSorted(false);
                }
            }
        }

        /**
         * This method creates new table, in case is
         * the first use of app, then passes this data to adapter
         */
        void createNewTable(){
            data = getDataFromDevice();
            if(data.moveToFirst()) {
                do {
                    createAndAddAudioItem();
                }
                while (data.moveToNext());
            }
            contentValues.clear();
            contentValues = null;
        }

        /**
         * This method read data from our
         * DB created after the first use of app,
         * then passes this data to adapter
         */
        void readFromDatabase(){

            data = trackDbHelper.getDataFromDB();
            int dataLength = data != null ? data.getCount() : 0;
            if (dataLength > 0) {
                while (data.moveToNext()) {
                        boolean isChecked = data.getInt(data.getColumnIndexOrThrow(TrackContract.TrackData.COLUMN_NAME_IS_SELECTED)) != 0;
                        String title = data.getString(data.getColumnIndexOrThrow(TrackContract.TrackData.COLUMN_NAME_TITLE)).equals("") ?
                                "No disponible" : data.getString(data.getColumnIndexOrThrow(TrackContract.TrackData.COLUMN_NAME_TITLE));
                        String artist = data.getString(data.getColumnIndexOrThrow(TrackContract.TrackData.COLUMN_NAME_ARTIST)).equals("") ?
                                "No disponible" : data.getString(data.getColumnIndexOrThrow(TrackContract.TrackData.COLUMN_NAME_ARTIST));
                        String album = data.getString(data.getColumnIndexOrThrow(TrackContract.TrackData.COLUMN_NAME_ALBUM)).equals("") ?
                                "No disponible" : data.getString(data.getColumnIndexOrThrow(TrackContract.TrackData.COLUMN_NAME_ALBUM));
                        String filename = data.getString(data.getColumnIndexOrThrow(TrackContract.TrackData.COLUMN_NAME_CURRENT_FILENAME)).equals("") ?
                                "No disponible" : data.getString(data.getColumnIndexOrThrow(TrackContract.TrackData.COLUMN_NAME_CURRENT_FILENAME));
                        String id = data.getString(data.getColumnIndexOrThrow(TrackContract.TrackData._ID));
                        String fullPath = data.getString(data.getColumnIndexOrThrow(TrackContract.TrackData.COLUMN_NAME_CURRENT_FULL_PATH));
                        String path = data.getString(data.getColumnIndexOrThrow(TrackContract.TrackData.COLUMN_NAME_CURRENT_PATH));
                        int totalSeconds = Integer.parseInt(data.getString(data.getColumnIndexOrThrow(TrackContract.TrackData.COLUMN_NAME_DURATION)));
                        String sFilesizeInMb = data.getString(data.getColumnIndexOrThrow(TrackContract.TrackData.COLUMN_NAME_FILE_SIZE));
                        float fFileSizeInMb = Float.parseFloat(sFilesizeInMb);
                        String status = data.getString(data.getColumnIndexOrThrow(TrackContract.TrackData.COLUMN_NAME_STATUS));
                        //byte[] cover = data.getBlob(data.getColumnIndex(TrackContract.TrackData.COLUMN_NAME_COVER_ART));

                        AudioItem audioItem = new AudioItem();
                        audioItem.setTitle(title).setArtist(artist).setAlbum(album).setDuration(totalSeconds).setHumanReadableDuration(AudioItem.getHumanReadableDuration(totalSeconds)).setId(Long.parseLong(id)).setNewAbsolutePath(fullPath).setPosition(counterPosition).setStatus(Integer.parseInt(status)).setFileName(filename).setSize(fFileSizeInMb).setPath(path).setChecked(isChecked);
                        totalSeconds = 0;
                        publishProgress(audioItem);
                        counterPosition++;

                }//end while
            }//end if
        }


        /**
         * Here we add the audio item to adapter
         * created at onCreated callback from
         * parent activity.
         */
        void createAndAddAudioItem(){
            int mediaStoreId = data.getInt(0);//mediastore id
            String title = !data.getString(1).contains("unknown") ? data.getString(1) : "No disponible";
            String artist = !data.getString(2).contains("unknown") ? data.getString(2) : "No disponible";
            String album = !data.getString(3).contains("unknown") ? data.getString(3) : "No disponible";
            int duration = data.getInt(4);

            String humanReadableDuration = AudioItem.getHumanReadableDuration(duration);
            String fullPath = Uri.parse(data.getString(5)).toString(); //MediaStore.Audio.Media.DATA column is the file path
            String filename = data.getString(6); //MediaStore.Audio.AudioColumns.DISPLAY_NAME, column is file name
            String fileSize = data.getString(7); //
            String path = new File(fullPath).getParent();
            float fileSizeInMb = Float.parseFloat(fileSize) / 1048576;


            //ContentValues values = new ContentValues();
            contentValues.put(TrackContract.TrackData.COLUMN_NAME_MEDIASTORE_ID,mediaStoreId);
            contentValues.put(TrackContract.TrackData.COLUMN_NAME_TITLE, title);
            contentValues.put(TrackContract.TrackData.COLUMN_NAME_ARTIST, artist);
            contentValues.put(TrackContract.TrackData.COLUMN_NAME_ALBUM, album);
            contentValues.put(TrackContract.TrackData.COLUMN_NAME_DURATION, duration);
            contentValues.put(TrackContract.TrackData.COLUMN_NAME_FILE_SIZE, fileSizeInMb);
            contentValues.put(TrackContract.TrackData.COLUMN_NAME_CURRENT_FILENAME, filename);
            contentValues.put(TrackContract.TrackData.COLUMN_NAME_CURRENT_PATH, path);
            contentValues.put(TrackContract.TrackData.COLUMN_NAME_CURRENT_FULL_PATH, fullPath);
            contentValues.put(TrackContract.TrackData.COLUMN_NAME_STATUS, AudioItem.FILE_STATUS_NO_PROCESSED);
            contentValues.put(TrackContract.TrackData.COLUMN_NAME_ADDED_RECENTLY,true);


            AudioItem audioItem = new AudioItem();

            //we need to set id in audio item because all operations
            //we do, relay in this id,
            //so when we save row to DB
            //it returns its id as a result
            long _id = trackDbHelper.insertRow(contentValues, TrackContract.TrackData.TABLE_NAME);
            audioItem.setTitle(title).setArtist(artist).setAlbum(album).setDuration(duration).setHumanReadableDuration(humanReadableDuration).setNewAbsolutePath(fullPath).setPath(path).setFileName(filename).setPosition(counterPosition).setSize(fileSizeInMb);
            audioItem.setId(_id);

            publishProgress(audioItem);
            counterPosition++;
            contentValues.clear();
        }

        @Override
        protected Void doInBackground(Void... voids) {

            switch (taskType){
                //If we are updating for new elements added
                case RE_SCAN:
                    rescanAndUpdateList();
                    break;
                //if database does not exist
                case CREATE_DATABASE:
                    createNewTable();
                    break;
                //if we are reading data from database
                case READ_FROM_DATABASE:
                    readFromDatabase();
                    break;
            }
            return null;
        }


        @Override
        protected void onPreExecute() {
            swipeRefreshLayout.setRefreshing(true);
            isGettingData = true;
            Toast toast = Toast.makeText(getApplicationContext(),"",Toast.LENGTH_SHORT);
            SelectFolderActivity.this.fab.hide();

            if(taskType == CREATE_DATABASE) {
                toast.setText("Obteniendo información de canciones...");
            }

            else if(taskType == READ_FROM_DATABASE){
                toast.setText("Cargando lista.");
            }
            else{
                toast.setText("Actualizando lista.");
            }
            toast.setGravity(Gravity.CENTER,0,0);
            toast.show();
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            swipeRefreshLayout.setRefreshing(false);
            isGettingData = false;
            counterPosition = 0;
            SelectFolderActivity.this.fab.show();


            //close cursor
            if(this.data != null) {
                data.close();
                data = null;
            }

            //there are not songs?
            if(audioItemList.size() == 0){
                Toast toast =  Toast.makeText(getApplicationContext(),"No se encontraron canciones en MP3.",Toast.LENGTH_SHORT);
                toast.setGravity(Gravity.CENTER,0,0);
                toast.show();
                return;
            }

            if(!audioItemArrayAdapter.isSorted()) {
                audioItemArrayAdapter.sortByPath();
                audioItemArrayAdapter.notifyDataSetChanged();
                //Log.d("sort adapter", audioItemArrayAdapter.getItemCount()+"  "+ audioItemList.size());
            }

            getSupportActionBar().setTitle(audioItemList.size() + " canciones");
            Log.d("sort adapter", audioItemArrayAdapter.getItemCount()+"  "+ audioItemList.size());
                AsyncLoadCoverArt asyncLoadCoverArt = new AsyncLoadCoverArt(taskType);
                asyncLoadCoverArt.execute();

            System.gc();
        }

        @Override
        protected void onProgressUpdate(AudioItem... audioItems) {
            super.onProgressUpdate(audioItems);
            audioItemList.add(audioItems[0]);
            Log.d("position",audioItems[0].getPosition()+" , size adapter: " + audioItemArrayAdapter.getItemCount());
            audioItemArrayAdapter.notifyItemInserted(audioItems[0].getPosition());
        }

        @Override
        public void onCancelled(){
            super.onCancelled();
            if(this.data != null){
                data.close();
                data = null;
            }
            isGettingData = false;
            swipeRefreshLayout.setRefreshing(false);
        }

    }

    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    public Action getIndexApiAction() {
        Thing object = new Thing.Builder()
                .setName("SelectFolder Page") // TODO: Define a title for the content shown.
                // TODO: Make sure this auto-generated URL is correct.
                .setUrl(Uri.parse("http://[ENTER-YOUR-URL-HERE]"))
                .build();
        return new Action.Builder(Action.TYPE_VIEW)
                .setObject(object)
                .setActionStatus(Action.STATUS_TYPE_COMPLETED)
                .build();
    }


    public class ResponseReceiver extends BroadcastReceiver{

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = "";
            action = intent.getAction();
            long id = intent.getLongExtra("id",-1);
            boolean singleTrack = intent.getBooleanExtra("singleTrack",false);
            AudioItem audioItem = null;

            if(id != -1 ){
                audioItem = getItemByIdOrPath(id,null);
                audioItem.setProcessing(false);
                audioItemArrayAdapter.notifyItemChanged(audioItem.getPosition());
            }

            switch (action){
                case FixerTrackService.ACTION_DONE:
                    int status = -1;
                    audioItem.setChecked(false);
                    if(singleTrack) {
                        status = intent.getIntExtra("status", audioItem.getStatus());
                        intent.removeExtra("status");
                    }
                    if((singleTrack && status == AudioItem.FILE_STATUS_BAD) || (singleTrack && status == AudioItem.FILE_STATUS_INCOMPLETE)){
                        setSnackBar(ACTION_EDIT,getString(R.string.incomplete_info_found) + audioItem.getFileName(),false,id, context);
                    }

                    intent.removeExtra("id");
                    intent.removeExtra("singleTrack");

                    break;
                case FixerTrackService.ACTION_CANCEL:
                case FixerTrackService.ACTION_COMPLETE_TASK:
                    finishTaskByUser();
                    break;
                case FixerTrackService.ACTION_FAIL:
                    break;
            }




        }
    }

    private class AsyncLoadCoverArt extends AsyncTask<Void, AudioItem, Void>{
        private ContentValues contentValues;
        private MediaMetadataRetriever mediaMetadataRetriever;
        private int taskType;

        AsyncLoadCoverArt(int taskType){
            this.taskType = taskType;
            contentValues = new ContentValues();
            mediaMetadataRetriever = new MediaMetadataRetriever();
        }

        private void extractAndLoadCoverArt(){
            //Columns to retrieve
            String[] projection = {TrackContract.TrackData._ID, TrackContract.TrackData.COLUMN_NAME_CURRENT_FULL_PATH};
            String selection = TrackContract.TrackData.COLUMN_NAME_ADDED_RECENTLY + " = ?";
            //condition to accomplish
            String[] selectionArgs = {1+""};

            Cursor cursor = SelectFolderActivity.this.dbHelper.getDataFromDB(projection,selection,selectionArgs);
            //if cursor could be move to first element, means that cursor is not empty
            boolean hasData = cursor.moveToFirst();

            //Log.d("cursor",hasData+"_" +hasData + "_" + cursor.getCount());
            //No data? then finish process
            if(!hasData) {
                cursor.close();
                cursor = null;
                return;
            }

            AudioItem audioItem = null;
            byte[] coverArt = null;

            do{
                long id = cursor.getLong(cursor.getColumnIndex(TrackContract.TrackData._ID));
                String path = cursor.getString(cursor.getColumnIndex(TrackContract.TrackData.COLUMN_NAME_CURRENT_FULL_PATH));

                audioItem = getItemByIdOrPath(id,"");
                assert audioItem != null;

                try {
                    mediaMetadataRetriever.setDataSource(path);

                    coverArt = mediaMetadataRetriever.getEmbeddedPicture();
                    if(coverArt != null) {
                        audioItem.setCoverArt(coverArt);
                        contentValues.put(TrackContract.TrackData.COLUMN_NAME_COVER_ART,coverArt);
                        Log.d("COVER ART","setCoverArt " + audioItem.getId());
                    }
                }
                catch (Exception e){
                    e.printStackTrace();
                }
                finally {
                    //once has been loaded its cover art, mark as FALSE its column and field "added recently"
                    contentValues.put(TrackContract.TrackData.COLUMN_NAME_ADDED_RECENTLY,false);
                    SelectFolderActivity.this.dbHelper.updateData(id,contentValues);
                    contentValues.clear();
                    coverArt = null;
                    publishProgress(audioItem);
                    audioItem = null;
                }


            }while (cursor.moveToNext());

            cursor.close();
            cursor = null;
        }

        private void loadCoverArt(){
            //Columns to retrieve
            String[] projection = {TrackContract.TrackData.COLUMN_NAME_COVER_ART};
            String selection = TrackContract.TrackData._ID + " = ?";
            //condition to accomplish
            String[] selectionArgs = new String[1];
            Cursor cursor = null;
            for(AudioItem audioItem: audioItemList){
                selectionArgs[0] = audioItem.getId()+"";
                cursor = SelectFolderActivity.this.dbHelper.getDataFromDB(projection,selection,selectionArgs);
                //if cursor could be move to first element, means that cursor is not empty
                boolean hasData = cursor.moveToFirst();
                //No data? then finish process
                if(!hasData) {
                    cursor.close();
                    cursor = null;
                    continue;
                }
                byte[] cover = cursor.getBlob(cursor.getColumnIndex(TrackContract.TrackData.COLUMN_NAME_COVER_ART));
                audioItem.setCoverArt(cover);
                publishProgress(audioItem);
                cursor.close();
                cursor = null;
                Log.d("cover_art_loaded","true");
            }
        }

        @Override
        protected void onPreExecute(){
            //don't let refresh layout till all cover art be loaded.
            swipeRefreshLayout.setEnabled(false);
        }

        @Override
        protected Void doInBackground(Void... params) {
            if(taskType == READ_FROM_DATABASE){
                loadCoverArt();
            }
            else {
                extractAndLoadCoverArt();
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(AudioItem... audioItem){
            super.onProgressUpdate(audioItem);
            int position = audioItem[0].getPosition();
            audioItemArrayAdapter.notifyItemChanged(position);
        }

        @Override
        protected void onPostExecute(Void voids){
            contentValues = null;
            mediaMetadataRetriever.release();
            mediaMetadataRetriever = null;
            swipeRefreshLayout.setEnabled(true);
        }
    }

}
