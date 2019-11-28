package mx.dev.franco.automusictagfixer.ui.trackdetail;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProviders;

import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.util.ArrayList;

import javax.inject.Inject;

import mx.dev.franco.automusictagfixer.R;
import mx.dev.franco.automusictagfixer.common.Action;
import mx.dev.franco.automusictagfixer.databinding.FragmentTrackDetailBinding;
import mx.dev.franco.automusictagfixer.identifier.IdentificationParams;
import mx.dev.franco.automusictagfixer.ui.BaseViewModelFragment;
import mx.dev.franco.automusictagfixer.ui.InformativeFragmentDialog;
import mx.dev.franco.automusictagfixer.ui.MainActivity;
import mx.dev.franco.automusictagfixer.ui.sdcardinstructions.SdCardInstructionsActivity;
import mx.dev.franco.automusictagfixer.utilities.ActionableMessage;
import mx.dev.franco.automusictagfixer.utilities.AndroidUtils;
import mx.dev.franco.automusictagfixer.utilities.Constants;
import mx.dev.franco.automusictagfixer.utilities.Constants.CorrectionActions;
import mx.dev.franco.automusictagfixer.utilities.Message;
import mx.dev.franco.automusictagfixer.utilities.RequiredPermissions;
import mx.dev.franco.automusictagfixer.utilities.SimpleMediaPlayer;
import mx.dev.franco.automusictagfixer.utilities.SimpleMediaPlayer.OnMediaPlayerEventListener;
import mx.dev.franco.automusictagfixer.utilities.SuccessIdentification;

import static android.view.View.VISIBLE;
import static mx.dev.franco.automusictagfixer.utilities.Constants.GOOGLE_SEARCH;

/**
 * Use the {@link TrackDetailFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class TrackDetailFragment extends BaseViewModelFragment<TrackDetailViewModel> implements
    ManualCorrectionDialogFragment.OnManualCorrectionListener,
        CoverIdentificationResultsFragmentBase.OnCoverCorrectionListener,
        SemiAutoCorrectionDialogFragment.OnSemiAutoCorrectionListener {

    public static final String TAG = TrackDetailFragment.class.getName();

    //Menu items
    private MenuItem mPlayPreviewMenuItem;
    private MenuItem mManualEditMenuItem;
    private MenuItem mSearchInWebMenuItem;
    private ActionBar mActionBar;
    private FragmentTrackDetailBinding mFragmentTrackDetailBinding;
    private boolean mEditMode = false;

    @Inject
    SimpleMediaPlayer mPlayer;

    public TrackDetailFragment() {}

    public static TrackDetailFragment newInstance(int idTrack, int correctionMode) {
        TrackDetailFragment fragment = new TrackDetailFragment();
        Bundle bundle = new Bundle();
        bundle.putInt(Constants.MEDIA_STORE_ID, idTrack);
        bundle.putInt(CorrectionActions.MODE, correctionMode);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        ((MainActivity)getActivity()).mDrawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        mPlayer.addListener(new OnMediaPlayerEventListener() {
            @Override
            public void onStartPlaying() {
                mPlayPreviewMenuItem.setIcon(ContextCompat.getDrawable(getActivity(), R.drawable.ic_stop_white_24dp));
                addStopAction();
            }
            @Override
            public void onStopPlaying() {
                mPlayPreviewMenuItem.setIcon(ContextCompat.getDrawable(getActivity(),R.drawable.ic_play_arrow_white_24px));
                addPlayAction();
            }
            @Override
            public void onCompletedPlaying() {
                mPlayPreviewMenuItem.setIcon(ContextCompat.getDrawable(getActivity(),R.drawable.ic_play_arrow_white_24px));
                addPlayAction();
            }
            @Override
            public void onErrorPlaying(int what, int extra) {
                mPlayPreviewMenuItem.setEnabled(false);
            }
        });
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        Bundle bundle = getArguments();
        if(bundle != null)
            mViewModel.setInitialAction(
                bundle.getInt(CorrectionActions.MODE, CorrectionActions.VIEW_INFO));
        else
            mViewModel.setInitialAction(CorrectionActions.VIEW_INFO);

        mViewModel.observeReadingResult().observe(this, message -> {
            if(message == null) {
                onSuccessLoad(null);
            }
        });

        mViewModel.observeActionableMessage().observe(this, this::onActionableMessage);
        mViewModel.observeMessage().observe(this, this::onMessage);
        mViewModel.observeSuccessIdentification().observe(this, this::onIdentificationResults);
        mViewModel.observeCachedIdentification().observe(this, this::onIdentificationResults);
        mViewModel.observeFailIdentification().observe(this, this::onMessage);
        mViewModel.observeLoadingState().observe(this, this::loading);
        mViewModel.observeConfirmationRemoveCover().observe(this, this::onConfirmRemovingCover);
        mViewModel.observeInvalidInputsValidation().observe(this, this::onInputDataInvalid);
        mViewModel.observeWritingResult().observe(this, this::onWritingResult);
        mViewModel.observeRenamingResult().observe(this, this::onMessage);
        mViewModel.observeCoverSavingResult().observe(this, this::onActionableMessage);
        mViewModel.observeTrack().observe(this, track -> mPlayer.setPath(track.getPath()));
        mViewModel.observeLoadingMessage().observe(this, this::onLoadingMessage);

        requireActivity().getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if(mEditMode) {
                    enableEditModeElements();
                    showFabs();
                    disableFields();
                    enableAppBarLayout();
                    removeErrorTags();
                    mViewModel.restorePreviousValues();
                }
                else {
                    getParentFragment().getChildFragmentManager().popBackStack();
                }
            }
        });
    }

    @Override
    public TrackDetailViewModel getViewModel() {
        return ViewModelProviders.
            of(this, androidViewModelFactory).
            get(TrackDetailViewModel.class);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mFragmentTrackDetailBinding = DataBindingUtil.inflate(inflater,
                R.layout.fragment_track_detail,
                container,
                false);
        mFragmentTrackDetailBinding.setLifecycleOwner(this);
        mFragmentTrackDetailBinding.setViewModel(mViewModel);
        mFragmentTrackDetailBinding.layoutContentDetailsTrack.setViewmodel(mViewModel);
        return mFragmentTrackDetailBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ((MainActivity)getActivity()).setSupportActionBar(mFragmentTrackDetailBinding.toolbar);
        mFragmentTrackDetailBinding.collapsingToolbarLayout.setTitleEnabled(false);
        mActionBar = ((MainActivity)getActivity()).getSupportActionBar();
        mActionBar.setDisplayShowTitleEnabled(false);
        hideFabs();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.menu_details_track_dialog, menu);
        mPlayPreviewMenuItem = menu.findItem(R.id.action_play);
        mManualEditMenuItem = menu.findItem(R.id.action_edit_manual);
        mSearchInWebMenuItem = menu.findItem(R.id.action_web_search);
    }

    /**
     * Callback for processing the result from startActivityForResult call,
     * here we process the image picked by user and apply to audio file
     * @param requestCode is the code from what component
     *                    makes the request this can be snack bar,
     *                    toolbar or text "Añadir caratula de galería"
     * @param resultCode result code is the action requested, in this case
     *                   Intent.ACTION_PICK
     * @param data Data received, can be null
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode){
            case INTENT_GET_AND_UPDATE_FROM_GALLERY:
            case INTENT_OPEN_GALLERY:
                if (data != null){
                        Uri imageData = data.getData();
                        AndroidUtils.AsyncBitmapDecoder asyncBitmapDecoder = new AndroidUtils.AsyncBitmapDecoder();
                        mFragmentTrackDetailBinding.appBarLayout.setExpanded(true, true);
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            ImageDecoder.Source source = ImageDecoder.
                                    createSource(getActivity().getApplicationContext().getContentResolver(), imageData);
                            asyncBitmapDecoder.decodeBitmap(source, getCallback(requestCode));
                        }
                        else {
                            asyncBitmapDecoder.decodeBitmap(getActivity().getApplicationContext().getContentResolver(),
                                    imageData, getCallback(requestCode) );
                        }
                }
                break;

            case RequiredPermissions.REQUEST_PERMISSION_SAF:
                String msg;
                if (resultCode == Activity.RESULT_OK) {
                    // The document selected by the user won't be returned in the intent.
                    // Instead, a URI to that document will be contained in the return intent
                    // provided to this method as a parameter.  Pull that uri using "resultData.getData()"
                    boolean res = AndroidUtils.grantPermissionSD(getActivity().getApplicationContext(), data);
                    if (res) {
                        msg = getString(R.string.toast_apply_tags_again);
                    }
                    else {
                        msg = getString(R.string.could_not_get_permission);
                    }
                }
                else {
                    msg = getString(R.string.saf_denied);
                }

                AndroidUtils.createSnackbar(mFragmentTrackDetailBinding.rootContainerDetails, msg).show();
                break;
        }

    }

    @Override
    public void onStop() {
        super.onStop();
        mPlayer.stopPreview();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        ((MainActivity)getActivity()).setSupportActionBar(((MainActivity)getActivity()).mMainToolbar);
        ((MainActivity)getActivity()).mMainAppbar.setExpanded(true, true);
        ((MainActivity)getActivity()).mDrawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        mPlayer.removeListeners();
        mViewModel.cancelIdentification();
        mPlayer = null;
    }

    private void onWritingResult(Message actionableMessage) {
        enableEditModeElements();
        showFabs();
        disableFields();
        enableAppBarLayout();
        onMessage(actionableMessage);
    }

    private AndroidUtils.AsyncBitmapDecoder.AsyncBitmapDecoderCallback getCallback(int requestCode){
        return new AndroidUtils.AsyncBitmapDecoder.AsyncBitmapDecoderCallback() {
            @Override
            public void onBitmapDecoded(Bitmap bitmap) {
                ImageWrapper imageWrapper = new ImageWrapper();
                imageWrapper.width = bitmap.getWidth();
                imageWrapper.height = bitmap.getHeight();
                imageWrapper.bitmap = bitmap;
                imageWrapper.requestCode = requestCode;

                mViewModel.fastCoverChange(imageWrapper);
            }

            @Override
            public void onDecodingError(Throwable throwable) {
                Snackbar snackbar = AndroidUtils.getSnackbar(
                        mFragmentTrackDetailBinding.rootContainerDetails,
                        mFragmentTrackDetailBinding.rootContainerDetails.getContext());
                String msg = getString(R.string.error_load_image) + ": " + throwable.getMessage();
                snackbar.setText(msg);
                snackbar.setDuration(Snackbar.LENGTH_SHORT);
                snackbar.show();
            }
        };
    }

    /**
     * Callback to confirm the deletion of current cover;
     */
    private void onConfirmRemovingCover(Void voids) {
        InformativeFragmentDialog informativeFragmentDialog = InformativeFragmentDialog.
                newInstance(R.string.attention,
                        R.string.message_remove_cover_art_dialog,
                        R.string.accept, R.string.cancel_button, getActivity());
        informativeFragmentDialog.showNow(getChildFragmentManager(),
                informativeFragmentDialog.getClass().getCanonicalName());

        informativeFragmentDialog.setOnClickBasicFragmentDialogListener(
                new InformativeFragmentDialog.OnClickBasicFragmentDialogListener() {
                    @Override
                    public void onPositiveButton() {
                        informativeFragmentDialog.dismiss();
                        mViewModel.confirmRemoveCover();
                    }

                    @Override
                    public void onNegativeButton() {
                        informativeFragmentDialog.dismiss();
                    }
                }
        );
    }

    protected void loading(boolean showProgress) {
        if(showProgress) {
            mFragmentTrackDetailBinding.progressView.setVisibility(VISIBLE);
            disableEditModeElements();
        }
        else {
            mFragmentTrackDetailBinding.progressView.setVisibility(View.GONE);
            enableEditModeElements();
        }
    }

    private void onLoadingMessage(Integer message) {
        ((TextView)mFragmentTrackDetailBinding.progressView.findViewById(R.id.status_message)).setText(message);
    }

    /**
     * Callback when data from track is completely
     * loaded.
     * @param message The message to show.
     */
    private void onSuccessLoad(Message message) {
        mFragmentTrackDetailBinding.toolbar.setNavigationOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                requireActivity().onBackPressed();
            }
        });
        mFragmentTrackDetailBinding.
                layoutContentDetailsTrack.
                changeImageButton.setOnClickListener(v -> editCover(INTENT_OPEN_GALLERY));

        addFloatingActionButtonListeners();
        addAppBarOffsetListener();
        addToolbarButtonsListeners();
        mFragmentTrackDetailBinding.fabAutofix.shrink();
        mFragmentTrackDetailBinding.fabSaveInfo.shrink();
        showFabs();
        mFragmentTrackDetailBinding.progressView.findViewById(R.id.cancel_button).
                setOnClickListener(v -> mViewModel.cancelTasks());
    }

    /**
     * Callback from {@link SemiAutoCorrectionDialogFragment} when
     * user pressed apply only missing tags button
     */
    @Override
    public void onMissingTagsButton(SemiAutoCorrectionParams semiAutoCorrectionParams) {
        mPlayer.stopPreview();
        mViewModel.performCorrection(semiAutoCorrectionParams);
    }

    /**
     * Callback from {@link SemiAutoCorrectionDialogFragment} when
     * user pressed apply all tags button
     */
    @Override
    public void onOverwriteTagsButton(SemiAutoCorrectionParams semiAutoCorrectionParams) {
        mPlayer.stopPreview();
        mViewModel.performCorrection(semiAutoCorrectionParams);
    }

    @Override
    public void onManualCorrection(ManualCorrectionParams inputParams) {
        mPlayer.stopPreview();
        mViewModel.performCorrection(inputParams);
    }

    @Override
    public void onCancelManualCorrection() {
        enableEditModeElements();
        disableFields();
        enableAppBarLayout();
        mViewModel.restorePreviousValues();
    }

    @Override
    public void saveAsImageButton(CoverCorrectionParams coverCorrectionParams) {
        mViewModel.saveAsImageFileFrom(coverCorrectionParams);
    }

    @Override
    public void saveAsCover(CoverCorrectionParams coverCorrectionParams) {
        mPlayer.stopPreview();
        mViewModel.performCorrection(coverCorrectionParams);
    }

    /**
     * Callback when a correction process error ocurred.
     * @param message The message of error.
     * @param action The action to perform on snackbar.
     */
    public void onCorrectionError(String message, String action) {
        Snackbar snackbar = AndroidUtils.getSnackbar(
                mFragmentTrackDetailBinding.rootContainerDetails, getActivity().getApplicationContext());
        snackbar.setDuration(Snackbar.LENGTH_LONG);
        if (action != null && action.equals(getString(R.string.get_permission))){
            snackbar.setAction(action, v -> getActivity().startActivity(new Intent(getActivity(), SdCardInstructionsActivity.class)));
        }
        else if(action != null && action.equals(getString(R.string.add_manual))){
            snackbar.setAction(action, new OnClickListener() {
                @Override
                public void onClick(View v) {

                }
            });
        }

        snackbar.setText(message);
        snackbar.show();
    }


    private void onInputDataInvalid(ValidationWrapper validationWrapper) {
        EditText editText = mFragmentTrackDetailBinding.getRoot().findViewById(validationWrapper.getField());
        Animation animation = AnimationUtils.loadAnimation(getActivity().getApplicationContext(), R.anim.blink);
        editText.requestFocus();
        editText.setError(validationWrapper.getMessage().getMessage());
        editText.setAnimation(animation);
        editText.startAnimation(animation);
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        assert imm != null;
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
    }

    /**
     * This method creates the references to visual elements
     * in layout
     */
    private void hideFabs(){
        mFragmentTrackDetailBinding.fabAutofix.hide();
        mFragmentTrackDetailBinding.fabSaveInfo.hide();
    }

    /**
     * Add listeners for corresponding objects to
     * respond to user interactions
     */

    private void addFloatingActionButtonListeners(){
        //runs track id
        mFragmentTrackDetailBinding.fabAutofix.setOnClickListener(v ->{
                mViewModel.startIdentification(new IdentificationParams(IdentificationParams.ALL_TAGS));
        });

        mFragmentTrackDetailBinding.fabSaveInfo.setOnClickListener(v -> {
            ManualCorrectionDialogFragment manualCorrectionDialogFragment =
                    ManualCorrectionDialogFragment.newInstance(mViewModel.title.getValue());
            manualCorrectionDialogFragment.show(getChildFragmentManager(),
                    manualCorrectionDialogFragment.getClass().getCanonicalName());
        });
    }

    /**
     * Adds a effect to fading down the cover when user scroll up and fading up to the cover when user
     * scrolls down.
     */
    private void addAppBarOffsetListener(){
        mFragmentTrackDetailBinding.appBarLayout.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            if(verticalOffset < 0) {
                if(!mFragmentTrackDetailBinding.fabAutofix.isExtended()) {
                    mFragmentTrackDetailBinding.fabAutofix.extend();
                    mFragmentTrackDetailBinding.fabSaveInfo.extend();
                }
            }
            else {
                if(mFragmentTrackDetailBinding.fabAutofix.isExtended()) {
                    mFragmentTrackDetailBinding.fabAutofix.shrink();
                    mFragmentTrackDetailBinding.fabSaveInfo.shrink();
                }
            }

            //set alpha of cover depending on offset of expanded toolbar cover height,
            mFragmentTrackDetailBinding.cardContainerCover.setAlpha(1.0f - Math.abs(verticalOffset/(float)appBarLayout.getTotalScrollRange()));
            //when toolbar is fully collapsed show name of audio file in toolbar and back button
            if(Math.abs(verticalOffset) - appBarLayout.getTotalScrollRange() == 0) {
                mFragmentTrackDetailBinding.collapsingToolbarLayout.setTitleEnabled(true);
                mFragmentTrackDetailBinding.collapsingToolbarLayout.setTitle(mViewModel.filename.getValue());
                mActionBar.setDisplayShowTitleEnabled(true);
                mActionBar.setDisplayHomeAsUpEnabled(true);
                mActionBar.setDisplayShowHomeEnabled(true);
            }
            //hides title of toolbar and back button if toolbar is fully expanded
            else {
                mFragmentTrackDetailBinding.collapsingToolbarLayout.setTitleEnabled(false);
                mActionBar.setDisplayShowTitleEnabled(false);
                mActionBar.setDisplayHomeAsUpEnabled(false);
                mActionBar.setDisplayShowHomeEnabled(false);
            }
        });
    }

    /**
     * Disable the Save Fab button.
     */
    private void showFabs(){
        mFragmentTrackDetailBinding.fabAutofix.show();
        mFragmentTrackDetailBinding.fabSaveInfo.hide();
    }

    /**
     * Enable the Save Fab button.
     */
    private void editMode(){
        disableAppBarLayout();
        enableFieldsToEdit();
        disableEditModeElements();
        mEditMode = true;
        mFragmentTrackDetailBinding.fabAutofix.hide();
        mFragmentTrackDetailBinding.fabSaveInfo.show();
    }

    private void disableEditModeElements() {
        mManualEditMenuItem.setEnabled(false);
        mFragmentTrackDetailBinding.coverArtMenu.setEnabled(false);
        mFragmentTrackDetailBinding.fabAutofix.hide();
    }

    private void enableEditModeElements() {
        mManualEditMenuItem.setEnabled(true);
        mFragmentTrackDetailBinding.coverArtMenu.setEnabled(true);
        mFragmentTrackDetailBinding.fabAutofix.show();
    }

    /**
     * Enters edit mode, for modify manually
     * the information about the song
     */
    private void disableAppBarLayout() {
        //shrink toolbar to make it easy to user
        //focus in editing tags
        mFragmentTrackDetailBinding.appBarLayout.setExpanded(false);
    }

    /**
     * Exits edit mode, for modify manually
     * the information about the song
     */
    private void enableAppBarLayout() {
        //shrink toolbar to make it easy to user
        //focus in editing tags
        mFragmentTrackDetailBinding.appBarLayout.setExpanded(true);
    }

    /**
     * Starts a external app to search info about the current track.
     */
    private void searchInfoForTrack(){
        String title = mFragmentTrackDetailBinding.layoutContentDetailsTrack.trackNameDetails.
                getText().toString();
        String artist = mFragmentTrackDetailBinding.layoutContentDetailsTrack.artistNameDetails.
                getText().toString();

        String queryString = title + (!artist.isEmpty() ? (" " + artist) : "");
        String query = GOOGLE_SEARCH + queryString;
        Uri uri = Uri.parse(query);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        startActivity(intent);
    }

    /**
     * Enters edit mode, for modify manually
     * the information about the song
     */
    private void enableFieldsToEdit(){
        //Shrink toolbar to make it easy to user
        //focus in editing tags

        //Enable edit text for edit them
        mFragmentTrackDetailBinding.layoutContentDetailsTrack.trackNameDetails.setEnabled(true);
        mFragmentTrackDetailBinding.layoutContentDetailsTrack.artistNameDetails.setEnabled(true);
        mFragmentTrackDetailBinding.layoutContentDetailsTrack.albumNameDetails.setEnabled(true);
        mFragmentTrackDetailBinding.layoutContentDetailsTrack.trackNumber.setEnabled(true);
        mFragmentTrackDetailBinding.layoutContentDetailsTrack.trackYear.setEnabled(true);
        mFragmentTrackDetailBinding.layoutContentDetailsTrack.trackGenre.setEnabled(true);

        mFragmentTrackDetailBinding.layoutContentDetailsTrack.changeImageButton.setVisibility(VISIBLE);

        mFragmentTrackDetailBinding.layoutContentDetailsTrack.trackNameDetails.requestFocus();
        InputMethodManager imm =(InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(mFragmentTrackDetailBinding.layoutContentDetailsTrack.trackNameDetails,
                InputMethodManager.SHOW_IMPLICIT);
    }

    /**
     * Remove error tags from editable fields.
     */
    private void removeErrorTags(){
        //get descendants instances of edit text
        ArrayList<View> fields = mFragmentTrackDetailBinding.getRoot().getFocusables(View.FOCUS_DOWN);
        int numElements = fields.size();

        for (int i = 0 ; i < numElements ; i++) {
            if (fields.get(i) instanceof EditText) {
                ((EditText) fields.get(i)).setError(null);
            }
        }
    }

    /**
     * Disables the fields and
     * leaves out from edit mode
     */
    private void disableFields(){
        removeErrorTags();
        mFragmentTrackDetailBinding.layoutContentDetailsTrack.trackNameDetails.clearFocus();
        mFragmentTrackDetailBinding.layoutContentDetailsTrack.trackNameDetails.setEnabled(false);
        mFragmentTrackDetailBinding.layoutContentDetailsTrack.artistNameDetails.clearFocus();
        mFragmentTrackDetailBinding.layoutContentDetailsTrack.artistNameDetails.setEnabled(false);
        mFragmentTrackDetailBinding.layoutContentDetailsTrack.albumNameDetails.clearFocus();
        mFragmentTrackDetailBinding.layoutContentDetailsTrack.albumNameDetails.setEnabled(false);
        mFragmentTrackDetailBinding.layoutContentDetailsTrack.trackNumber.clearFocus();
        mFragmentTrackDetailBinding.layoutContentDetailsTrack.trackNumber.setEnabled(false);
        mFragmentTrackDetailBinding.layoutContentDetailsTrack.trackYear.clearFocus();
        mFragmentTrackDetailBinding.layoutContentDetailsTrack.trackYear.setEnabled(false);
        mFragmentTrackDetailBinding.layoutContentDetailsTrack.trackGenre.clearFocus();
        mFragmentTrackDetailBinding.layoutContentDetailsTrack.trackGenre.setEnabled(false);

        mFragmentTrackDetailBinding.layoutContentDetailsTrack.changeImageButton.setVisibility(View.GONE);
        mFragmentTrackDetailBinding.toolbarCoverArt.setEnabled(true);
        //to hide it, call the method again
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        try {
            assert imm != null;
            imm.hideSoftInputFromWindow(getActivity().getCurrentFocus().getWindowToken(), 0);
        }
        catch (Exception ignored){}

        mEditMode = false;
    }

    /**
     * Opens a dialog to select a image
     * to apply as new embed cover art.
     * @param codeIntent The code to distinguish if we pressed the cover toolbar,
     *                   the action button "Galería" from snackbar or "Añadir carátula de galería"
     *                   from main container.
     */
    private void editCover(int codeIntent){
        Intent selectorImageIntent = new Intent(Intent.ACTION_PICK);
        selectorImageIntent.setType("image/*");
        startActivityForResult(selectorImageIntent,codeIntent);
    }

    /**
     * Set the listeners to FAB buttons.
     */
    private void addToolbarButtonsListeners(){
        addPlayAction();

        //performs a web trackSearch in navigator
        //using the title and artist name
        mSearchInWebMenuItem.setOnMenuItemClickListener(item -> {
            searchInfoForTrack();
            return false;
        });

        mManualEditMenuItem.setOnMenuItemClickListener(item -> {
            editMode();
            return false;
        });

        mFragmentTrackDetailBinding.coverArtMenu.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(TrackDetailFragment.this.getActivity(), v);
            MenuInflater menuInflater = popupMenu.getMenuInflater();
            menuInflater.inflate(R.menu.menu_cover_art_options, popupMenu.getMenu());
            popupMenu.show();
            popupMenu.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case R.id.action_identify_cover:
                            mViewModel.startIdentification(
                                new IdentificationParams(IdentificationParams.ONLY_COVER));
                        break;
                    case R.id.action_update_cover:
                            editCover(TrackDetailFragment.INTENT_GET_AND_UPDATE_FROM_GALLERY);
                        break;
                    case R.id.action_extract_cover:
                            mViewModel.extractCover();
                        break;
                    case R.id.action_remove_cover:
                            mViewModel.removeCover();
                        break;
                }
                return false;
            });
        });
    }
    /**
     * Alternates the stop to the play action.
     */
    private void addPlayAction(){
        mPlayPreviewMenuItem.setOnMenuItemClickListener(null);
        mPlayPreviewMenuItem.setOnMenuItemClickListener(item -> {
                if(PreferenceManager.getDefaultSharedPreferences(getActivity().
                        getApplicationContext()).getBoolean("key_use_embed_player",true)) {
                    try {
                        mPlayer.playPreview();
                    } catch (IOException e) {
                        Snackbar snackbar = AndroidUtils.createSnackbar(mFragmentTrackDetailBinding.rootContainerDetails,
                                R.string.cannot_play_track);
                        snackbar.show();
                    }
                }
                else {
                    AndroidUtils.openInExternalApp(mViewModel.absolutePath.getValue(), getActivity());
                }
            return false;
        });
    }

    /**
     * Alternates the play to the stop action.
     */
    private void addStopAction(){
        mPlayPreviewMenuItem.setOnMenuItemClickListener(null);
        mPlayPreviewMenuItem.setOnMenuItemClickListener(item -> {
            mPlayer.stopPreview();
            return false;
        });
    }

    public void loadTrackData(Bundle inputBundle){
        Bundle bundle = inputBundle == null ? getArguments() : inputBundle;
        if(bundle != null)
            mViewModel.loadInfoTrack(bundle.getInt(Constants.MEDIA_STORE_ID,-1));
    }

    /**
     * Shows a message into a Snackbar
     * @param message The message object to show.
     */
    @Override
    protected void onMessage(Message message){
        if(message == null)
            return;

        Snackbar snackbar = AndroidUtils.createSnackbar(
            mFragmentTrackDetailBinding.rootContainerDetails,
            message
            );
        snackbar.show();
    }

    /**
     * Shows a message and an action into a Snackbar.
     * @param actionableMessage The message object with action to take.
     */
    @Override
    protected void onActionableMessage(ActionableMessage actionableMessage) {
        if(actionableMessage == null)
            return;

        Snackbar snackbar = AndroidUtils.createActionableSnackbar(
            mFragmentTrackDetailBinding.rootContainerDetails,
            actionableMessage,
            createOnClickListener(actionableMessage.getAction()));
        snackbar.show();
    }

    /**
     * Animates the transition from previous fragment to this fragment.
     * @param transit
     * @param enter
     * @param nextAnim
     * @return
     */
    @Override
    public Animation onCreateAnimation(int transit, boolean enter, int nextAnim) {
        Animation animation = super.onCreateAnimation(transit, enter, nextAnim);

        if (animation == null && nextAnim != 0) {
            animation = AnimationUtils.loadAnimation(getActivity(), nextAnim);
        }

        if (animation != null && getView() != null)
            getView().setLayerType(View.LAYER_TYPE_HARDWARE, null);

        if(animation != null)
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                ((MainActivity)getActivity()).mMainAppbar.setExpanded(false, true);
                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        loadTrackData(null);
                    }
                },250);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });

        return animation;
    }

    /**
     * Creates a OnClickListener object to respond according to an Action object.
     * @param action The action to execute.
     * @return A OnclickListener object.
     */
    private OnClickListener createOnClickListener (Action action) {

        return null;
    }


    private void onIdentificationResults(SuccessIdentification successIdentification) {
        if(successIdentification.getIdentificationType() == SuccessIdentification.ALL_TAGS){
            SemiAutoCorrectionDialogFragment semiAutoCorrectionDialogFragment =
                    SemiAutoCorrectionDialogFragment.newInstance(successIdentification.getMediaStoreId());
            semiAutoCorrectionDialogFragment.show(getChildFragmentManager(),
                    semiAutoCorrectionDialogFragment.getClass().getCanonicalName());

        }
        else {
            CoverIdentificationResultsFragmentBase coverIdentificationResultsFragmentBase =
                    CoverIdentificationResultsFragmentBase.newInstance(successIdentification.getMediaStoreId());
            coverIdentificationResultsFragmentBase.show(getChildFragmentManager(),
                    coverIdentificationResultsFragmentBase.getClass().getCanonicalName());
        }
    }
}
