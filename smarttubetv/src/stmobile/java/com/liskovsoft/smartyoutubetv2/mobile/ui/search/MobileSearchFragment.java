package com.liskovsoft.smartyoutubetv2.mobile.ui.search;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.MediaServiceSearchTagProvider;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView;
import com.liskovsoft.smartyoutubetv2.mobile.ui.browse.VideoCardAdapter;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Native portrait Search screen. Implements {@link SearchView} and is driven by the
 * existing {@link SearchPresenter} unchanged — query handling, result paging and the
 * filter dialog are all reused from the TV code; only the view layer is new.
 *
 * The toolbar holds the query field; below it the screen shows either query suggestions
 * (while typing) or a 2-column grid of results (after a search). Results from every
 * search group are flattened into one grid, keyed by group id so a continuation replaces
 * just its own slice.
 */
public class MobileSearchFragment extends Fragment implements SearchView {
    private static final int REQUEST_VOICE_SEARCH = 1001;
    private SearchPresenter mPresenter;
    private EditText mSearchInput;
    private RecyclerView mResultsList;
    private SwipeRefreshLayout mSwipeRefresh;
    private RecyclerView mSuggestionsList;
    private ProgressBar mProgressBar;
    private TextView mEmptyMessage;
    private VideoCardAdapter mResultsAdapter;
    private TagAdapter mTagsAdapter;
    private MediaServiceSearchTagProvider mTagsProvider;
    private final Map<Integer, List<Video>> mGroups = new LinkedHashMap<>();
    private final Handler mUiHandler = new Handler(Looper.getMainLooper());
    private boolean mProgressShowing;
    private boolean mSwipeRefreshing;
    private boolean mWantSuggestions;
    private boolean mSearchSubmitted;
    private boolean mSuppressWatcher;
    private boolean mFragmentCreated = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.mobile_search_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mSearchInput = view.findViewById(R.id.search_input);
        mResultsList = view.findViewById(R.id.results_list);
        mSwipeRefresh = view.findViewById(R.id.swipe_refresh);
        mSuggestionsList = view.findViewById(R.id.suggestions_list);
        mProgressBar = view.findViewById(R.id.progress_bar);
        mEmptyMessage = view.findViewById(R.id.empty_message);

        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().finish();
            }
        });
        view.findViewById(R.id.btn_clear).setOnClickListener(v -> mSearchInput.setText(""));
        view.findViewById(R.id.btn_voice).setOnClickListener(v -> startVoiceRecognition());

        int span = getResources().getInteger(R.integer.mobile_grid_span);
        int cardWidth = getResources().getDisplayMetrics().widthPixels / span;
        mResultsAdapter = new VideoCardAdapter(cardWidth, mVideoClick, mVideoLongClick);
        mResultsList.setLayoutManager(new GridLayoutManager(getContext(), span));
        mResultsList.setAdapter(mResultsAdapter);
        mResultsList.addOnScrollListener(mScrollListener);

        mSwipeRefresh.setColorSchemeResources(R.color.brand_accent);
        mSwipeRefresh.setProgressBackgroundColorSchemeResource(R.color.mobile_surface);
        mSwipeRefresh.setOnRefreshListener(() -> {
            // Re-run the last submitted query. Nothing to refresh while the user is still
            // typing (suggestions view) or before any search ran.
            String query = getSearchText();
            if (mSearchSubmitted && !TextUtils.isEmpty(query)) {
                mSwipeRefreshing = true;
                submitSearch(query);
            } else {
                stopSwipeRefresh();
            }
        });

        mTagsAdapter = new TagAdapter(this::onTagPicked);
        mSuggestionsList.setLayoutManager(new LinearLayoutManager(getContext()));
        mSuggestionsList.setAdapter(mTagsAdapter);

        mSearchInput.addTextChangedListener(mQueryWatcher);
        mSearchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submitSearch(mSearchInput.getText().toString());
                return true;
            }
            return false;
        });

        mPresenter = SearchPresenter.instance(getContext());
        mPresenter.setView(this);
        mPresenter.onViewInitialized();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!mFragmentCreated && mPresenter != null) {
            mPresenter.onViewResumed();
        }
        mFragmentCreated = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mPresenter != null) {
            mPresenter.onViewDestroyed();
        }
    }

    // ----- SearchView -----

    @Override
    public void updateSearch(VideoGroup group) {
        if (group == null) {
            return;
        }
        switch (group.getAction()) {
            case VideoGroup.ACTION_REPLACE:
                clearSearch();
                break;
            case VideoGroup.ACTION_SYNC:
                // Percent-watched refresh: synced in place, so mGroups (same Video objects) stays current.
                if (mResultsAdapter != null) mResultsAdapter.sync(group.getVideos());
                return;
            case VideoGroup.ACTION_REMOVE:
            case VideoGroup.ACTION_REMOVE_AUTHOR:
                for (List<Video> videos : mGroups.values()) {
                    videos.removeAll(group.getVideos());
                }
                rebuildResults();
                return;
            default:
                break;
        }
        if (group.isEmpty()) {
            return;
        }
        // A continuation carries the full cumulative list for its group, so replacing the
        // slice keyed by group id handles both the first page and later pages.
        mGroups.put(group.getId(), new ArrayList<>(group.getVideos()));
        rebuildResults();
    }

    @Override
    public void clearSearch() {
        mGroups.clear();
        if (mResultsAdapter != null) {
            mResultsAdapter.clear();
        }
        hideEmptyMessage();
    }

    @Override
    public void clearSearchTags() {
        if (mTagsAdapter != null) {
            mTagsAdapter.clear();
        }
    }

    @Override
    public void removeSearchTag(Tag tag) {
        if (mTagsAdapter != null) {
            mTagsAdapter.remove(tag);
        }
    }

    @Override
    public void setTagsProvider(MediaServiceSearchTagProvider provider) {
        mTagsProvider = provider;
    }

    @Override
    public void showProgressBar(boolean show) {
        mProgressShowing = show;
        // While pull-to-refresh is active the SwipeRefreshLayout spinner stands in for
        // the centre bar; don't show both.
        if (mProgressBar != null) {
            mProgressBar.setVisibility(show && !mSwipeRefreshing ? View.VISIBLE : View.GONE);
        }
        if (!show) {
            stopSwipeRefresh();
        }
        if (!show && mSearchSubmitted && mGroups.isEmpty()) {
            showEmptyMessage(getString(R.string.mobile_search_no_results));
        }
    }

    private void stopSwipeRefresh() {
        mSwipeRefreshing = false;
        if (mSwipeRefresh != null) {
            mSwipeRefresh.setRefreshing(false);
        }
    }

    @Override
    public void startSearch(String searchText) {
        if (!TextUtils.isEmpty(searchText)) {
            mSuppressWatcher = true;
            mSearchInput.setText(searchText);
            mSearchInput.setSelection(searchText.length());
            mSuppressWatcher = false;
            submitSearch(searchText);
        } else {
            focusSearchField();
        }
    }

    @Override
    public String getSearchText() {
        return mSearchInput != null ? mSearchInput.getText().toString() : "";
    }

    @Override
    public void startVoiceRecognition() {
        // Intent-based recognition: Google's speech UI runs in its own process and handles
        // the mic permission itself, so the app needs no RECORD_AUDIO permission. If no
        // recognizer is installed, fall back to the keyboard.
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.mobile_search_voice_prompt));
        try {
            startActivityForResult(intent, REQUEST_VOICE_SEARCH);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(getContext(), R.string.mobile_search_voice_unavailable,
                    Toast.LENGTH_SHORT).show();
            focusSearchField();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_VOICE_SEARCH || resultCode != Activity.RESULT_OK || data == null) {
            return;
        }
        List<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (results == null || results.isEmpty()) {
            return;
        }
        String spoken = results.get(0);
        if (TextUtils.isEmpty(spoken)) {
            return;
        }
        // Mirror onTagPicked: set the field without re-triggering the suggestions watcher,
        // then run the search.
        mSuppressWatcher = true;
        mSearchInput.setText(spoken);
        mSearchInput.setSelection(spoken.length());
        mSuppressWatcher = false;
        submitSearch(spoken);
    }

    @Override
    public void finishReally() {
        if (isAdded() && getActivity() != null) {
            getActivity().finish();
        }
    }

    // ----- helpers -----

    private void submitSearch(String query) {
        if (query == null) {
            return;
        }
        query = query.trim();
        if (query.isEmpty()) {
            return;
        }
        mWantSuggestions = false;
        mSuggestionsList.setVisibility(View.GONE);
        hideKeyboard();
        mSearchInput.clearFocus();
        mSearchSubmitted = true;
        mResultsList.scrollToPosition(0);
        mPresenter.onSearch(query);
    }

    private void onTagPicked(Tag tag) {
        if (tag == null || tag.tag == null) {
            return;
        }
        mSuppressWatcher = true;
        mSearchInput.setText(tag.tag);
        mSearchInput.setSelection(tag.tag.length());
        mSuppressWatcher = false;
        submitSearch(tag.tag);
    }

    private void rebuildResults() {
        List<Video> flat = new ArrayList<>();
        for (List<Video> videos : mGroups.values()) {
            flat.addAll(videos);
        }
        mResultsAdapter.setVideos(flat);
        if (!flat.isEmpty()) {
            hideEmptyMessage();
        }
    }

    private void loadSuggestions(String query) {
        if (mTagsProvider == null) {
            return;
        }
        mTagsProvider.search(query, results -> mUiHandler.post(() -> {
            if (!isAdded() || !mWantSuggestions) {
                return;
            }
            mTagsAdapter.setTags(results);
            mSuggestionsList.setVisibility(
                    mTagsAdapter.getItemCount() > 0 ? View.VISIBLE : View.GONE);
        }));
    }

    private final TextWatcher mQueryWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) { }

        @Override
        public void afterTextChanged(Editable s) {
            if (mSuppressWatcher) {
                return;
            }
            if (s.length() == 0) {
                mWantSuggestions = false;
                mSuggestionsList.setVisibility(View.GONE);
                if (mTagsAdapter != null) {
                    mTagsAdapter.clear();
                }
                return;
            }
            mWantSuggestions = true;
            loadSuggestions(s.toString());
        }
    };

    // Hosting activity declares configChanges="orientation|..." so it is NOT recreated on
    // rotation; re-read the grid span (values-sw600dp-land widens it) and resize cards.
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        int span = getResources().getInteger(R.integer.mobile_grid_span);
        if (mResultsList != null && mResultsList.getLayoutManager() instanceof GridLayoutManager) {
            ((GridLayoutManager) mResultsList.getLayoutManager()).setSpanCount(span);
        }
        if (mResultsAdapter != null) {
            mResultsAdapter.setCardWidth(getResources().getDisplayMetrics().widthPixels / span);
        }
    }

    private final VideoCardAdapter.OnVideoAction mVideoClick = video -> {
        if (mPresenter != null) {
            mPresenter.onVideoItemSelected(video);
            mPresenter.onVideoItemClicked(video);
        }
    };

    private final VideoCardAdapter.OnVideoAction mVideoLongClick = video -> {
        if (mPresenter != null) {
            mPresenter.onVideoItemLongClicked(video);
        }
    };

    private final RecyclerView.OnScrollListener mScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            if (dy <= 0 || mProgressShowing || mResultsAdapter == null) {
                return;
            }
            RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
            if (!(lm instanceof GridLayoutManager)) {
                return;
            }
            int lastVisible = ((GridLayoutManager) lm).findLastVisibleItemPosition();
            if (lastVisible >= mResultsAdapter.getItemCount() - 4) {
                Video last = mResultsAdapter.getLast();
                if (last != null && mPresenter != null) {
                    mPresenter.onScrollEnd(last);
                }
            }
        }
    };

    private void focusSearchField() {
        if (mSearchInput == null) {
            return;
        }
        mSearchInput.requestFocus();
        mSearchInput.post(() -> {
            InputMethodManager imm = (InputMethodManager)
                    mSearchInput.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(mSearchInput, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager)
                mSearchInput.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(mSearchInput.getWindowToken(), 0);
        }
    }

    private void showEmptyMessage(String message) {
        if (mEmptyMessage != null) {
            mEmptyMessage.setText(message);
            mEmptyMessage.setVisibility(View.VISIBLE);
        }
    }

    private void hideEmptyMessage() {
        if (mEmptyMessage != null) {
            mEmptyMessage.setVisibility(View.GONE);
        }
    }
}
