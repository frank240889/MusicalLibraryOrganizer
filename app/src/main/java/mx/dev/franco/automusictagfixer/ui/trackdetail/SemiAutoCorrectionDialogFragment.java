package mx.dev.franco.automusictagfixer.ui.trackdetail;

import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.TextInputLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.LinearSnapHelper;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SnapHelper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

import mx.dev.franco.automusictagfixer.R;
import mx.dev.franco.automusictagfixer.fixer.AudioTagger;
import mx.dev.franco.automusictagfixer.ui.ResultsFragmentBase;
import mx.dev.franco.automusictagfixer.utilities.Constants;

public class SemiAutoCorrectionDialogFragment extends ResultsFragmentBase<ResultsViewModel> {

  public interface OnSemiAutoCorrectionListener {
    void onMissingTagsButton(SemiAutoCorrectionParams semiAutoCorrectionParams);
    void onOverwriteTagsButton(SemiAutoCorrectionParams semiAutoCorrectionParams);
  }

  private OnSemiAutoCorrectionListener mOnSemiAutoCorrectionListener;
  private SemiAutoCorrectionParams mSemiAutoCorrectionParams;
  private int mCenteredItem = -1;
  private IdentificationResultsAdapter adapter;

  public SemiAutoCorrectionDialogFragment(){}

  public static SemiAutoCorrectionDialogFragment newInstance(String id) {
    SemiAutoCorrectionDialogFragment semiAutoCorrectionDialogFragment = new SemiAutoCorrectionDialogFragment();
    Bundle bundle = new Bundle();
    bundle.putInt(LAYOUT_ID, R.layout.layout_results_track_id);
    bundle.putString(TRACK_ID, id);
    semiAutoCorrectionDialogFragment.setArguments(bundle);
    return semiAutoCorrectionDialogFragment;
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    if(getParentFragment() instanceof OnSemiAutoCorrectionListener)
      mOnSemiAutoCorrectionListener = (OnSemiAutoCorrectionListener) getParentFragment();
    else if(context instanceof OnSemiAutoCorrectionListener)
      mOnSemiAutoCorrectionListener = (OnSemiAutoCorrectionListener) context;
    else
      throw new RuntimeException(context.toString() + " must implement " +
          OnSemiAutoCorrectionListener.class.getCanonicalName());

    mSemiAutoCorrectionParams = new SemiAutoCorrectionParams();
    mSemiAutoCorrectionParams.setCorrectionMode(Constants.CACHED);
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    adapter = new IdentificationResultsAdapter();
    mViewModel.observeProgress().observe(this, this::onLoading);
    mViewModel.observeResults().observe(this, adapter);
    mViewModel.fetchResults(mTrackId);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    Button missingTagsButton = view.findViewById(R.id.missing_tags_button);
    Button allTagsButton = view.findViewById(R.id.all_tags_button);
    CheckBox checkBoxRename = view.findViewById(R.id.checkbox_rename);
    TextInputLayout label = view.findViewById(R.id.label_rename_to);
    EditText newNameEditText = view.findViewById(R.id.new_name_edit_text);
    RecyclerView listResults = view.findViewById(R.id.results_list);

    LinearLayoutManager layoutManager = new LinearLayoutManager(listResults.getContext());
    layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
    listResults.setLayoutManager(layoutManager);
    listResults.setItemViewCacheSize(5);
    listResults.setAdapter(adapter);
    SnapHelper snapHelper = new LinearSnapHelper();
    snapHelper.attachToRecyclerView(listResults);

    listResults.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
        if(newState == RecyclerView.SCROLL_STATE_IDLE) {
          View snapView = snapHelper.findSnapView(layoutManager);
          if(snapView != null) {
            mCenteredItem = layoutManager.getPosition(snapView);
            listResults.smoothScrollToPosition(mCenteredItem);
          }
        }
      }
    });

    newNameEditText.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        mSemiAutoCorrectionParams.setNewName(s.toString());
      }
      @Override
      public void afterTextChanged(Editable s) {}
    });

    checkBoxRename.setOnCheckedChangeListener((compoundButton, checked) -> {
      if(!checked) {
        newNameEditText.setText("");
        label.setVisibility(View.GONE);
      }
      else {
        label.setVisibility(View.VISIBLE);
      }
    });

    missingTagsButton.setOnClickListener(view1 -> {
      mSemiAutoCorrectionParams.setCodeRequest(AudioTagger.MODE_WRITE_ONLY_MISSING);
      mSemiAutoCorrectionParams.setPosition(mCenteredItem+"");
      mOnSemiAutoCorrectionListener.onMissingTagsButton(mSemiAutoCorrectionParams);
    });

    allTagsButton.setOnClickListener(view12 -> {
      mSemiAutoCorrectionParams.setCodeRequest(AudioTagger.MODE_OVERWRITE_ALL_TAGS);
      mSemiAutoCorrectionParams.setPosition(mCenteredItem+"");
      mOnSemiAutoCorrectionListener.onOverwriteTagsButton(mSemiAutoCorrectionParams);
    });
  }

  @Override
  protected void onLoading(boolean loading) {
    getView().findViewById(R.id.loading_progress_bar).setVisibility(loading ? View.VISIBLE : View.GONE);
  }

  @Override
  protected ResultsViewModel getViewModel() {
    return ViewModelProviders.of(this, androidViewModelFactory).get(ResultsViewModel.class);
  }
}
