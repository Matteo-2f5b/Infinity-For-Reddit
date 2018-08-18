package ml.docilealligator.infinityforreddit;


import android.support.v4.app.Fragment;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.util.ArrayList;
import java.util.Map;


/**
 * A simple {@link Fragment} subclass.
 */
public class BestPostFragment extends Fragment {

    private CoordinatorLayout mCoordinatorLayout;
    private RecyclerView mBestPostRecyclerView;
    private LinearLayoutManager mLinearLayoutManager;
    private ProgressBar mProgressBar;
    private ArrayList<BestPostData> mBestPostData;
    private String mLastItem;
    private PaginationSynchronizer mPaginationSynchronizer;
    private BestPostRecyclerViewAdapter mAdapter;

    private String bestPostDataParcelableState = "BPDPS";
    private String lastItemState = "LIS";
    private String paginationSynchronizerState = "PSS";

    private RequestQueue mRequestQueue;
    private RequestQueue mPaginationRequestQueue;
    private RequestQueue mAcquireAccessTokenRequestQueue;
    private RequestQueue mVoteThingRequestQueue;

    public BestPostFragment() {
        // Required empty public constructor
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if(savedInstanceState != null) {
            if(savedInstanceState.containsKey(bestPostDataParcelableState)) {
                mBestPostData = savedInstanceState.getParcelableArrayList(bestPostDataParcelableState);
                mLastItem = savedInstanceState.getString(lastItemState);
                mAdapter = new BestPostRecyclerViewAdapter(getActivity(), mBestPostData, mPaginationSynchronizer, mVoteThingRequestQueue, mAcquireAccessTokenRequestQueue);
                mBestPostRecyclerView.setAdapter(mAdapter);
                mBestPostRecyclerView.addOnScrollListener(new BestPostPaginationScrollListener(getActivity(), mLinearLayoutManager, mAdapter, mLastItem, mBestPostData, mPaginationSynchronizer,
                        mAcquireAccessTokenRequestQueue, mPaginationSynchronizer.isLoading(), mPaginationSynchronizer.isLoadSuccess()));
                mProgressBar.setVisibility(View.GONE);
            } else {
                queryBestPost(1);
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if(mRequestQueue != null) {
            mRequestQueue.cancelAll(this);
        }

        if(mAcquireAccessTokenRequestQueue != null) {
            mAcquireAccessTokenRequestQueue.cancelAll(AcquireAccessToken.class);
        }

        if(mVoteThingRequestQueue != null) {
            mVoteThingRequestQueue.cancelAll(VoteThing.class);
        }

        if(mPaginationRequestQueue != null) {
            mPaginationRequestQueue.cancelAll(BestPostPaginationScrollListener.class);
        }

        if(mBestPostData != null) {
            outState.putParcelableArrayList(bestPostDataParcelableState, mBestPostData);
            outState.putString(lastItemState, mLastItem);
            outState.putParcelable(paginationSynchronizerState, mPaginationSynchronizer);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if(mAdapter != null) {
            mAdapter.setCanStartActivity(true);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_best_post, container, false);
        mCoordinatorLayout = rootView.findViewById(R.id.coordinator_layout_best_post_fragment);
        mBestPostRecyclerView = rootView.findViewById(R.id.recycler_view_best_post_fragment);
        mLinearLayoutManager = new LinearLayoutManager(getActivity());
        mBestPostRecyclerView.setLayoutManager(mLinearLayoutManager);
        mProgressBar = rootView.findViewById(R.id.progress_bar_best_post_fragment);
        FloatingActionButton fab = rootView.findViewById(R.id.fab_best_post_fragment);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        mRequestQueue = Volley.newRequestQueue(getActivity());
        mAcquireAccessTokenRequestQueue = Volley.newRequestQueue(getActivity());
        mVoteThingRequestQueue = Volley.newRequestQueue(getActivity());

        if(savedInstanceState != null && savedInstanceState.getParcelable(paginationSynchronizerState) != null) {
            mPaginationSynchronizer = savedInstanceState.getParcelable(paginationSynchronizerState);
        } else {
            mPaginationSynchronizer = new PaginationSynchronizer();
            queryBestPost(1);
        }

        LastItemSynchronizer lastItemSynchronizer = new LastItemSynchronizer() {
            @Override
            public void lastItemChanged(String lastItem) {
                mLastItem = lastItem;
            }
        };
        mPaginationSynchronizer.setLastItemSynchronizer(lastItemSynchronizer);

        PaginationRequestQueueSynchronizer paginationRequestQueueSynchronizer = new PaginationRequestQueueSynchronizer() {
            @Override
            public void passQueue(RequestQueue q) {
                mPaginationRequestQueue = q;
            }
        };
        mPaginationSynchronizer.setPaginationRequestQueueSynchronizer(paginationRequestQueueSynchronizer);

        return rootView;
    }

    private void queryBestPost(final int refreshTime) {
        if(refreshTime < 0) {
            showErrorSnackbar();
            return;
        }

        mProgressBar.setVisibility(View.VISIBLE);

        Uri uri = Uri.parse(RedditUtils.OAUTH_API_BASE_URI + RedditUtils.BEST_POST_SUFFIX)
                .buildUpon().appendQueryParameter(RedditUtils.RAW_JSON_KEY, RedditUtils.RAW_JSON_VALUE)
                .build();

        StringRequest bestPostRequest = new StringRequest(Request.Method.GET, uri.toString(), new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                if(getActivity() != null) {
                    ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("response", response);
                    clipboard.setPrimaryClip(clip);
                    //new ParseBestPostDataAsyncTask(response, accessToken).execute();
                    new ParseBestPost(getActivity(), new ParseBestPost.ParseBestPostListener() {
                        @Override
                        public void onParseBestPostSuccess(ArrayList<BestPostData> bestPostData, String lastItem) {
                            mBestPostData = bestPostData;
                            mLastItem = lastItem;
                            mAdapter = new BestPostRecyclerViewAdapter(getActivity(), bestPostData, mPaginationSynchronizer, mVoteThingRequestQueue, mAcquireAccessTokenRequestQueue);

                            mBestPostRecyclerView.setAdapter(mAdapter);
                            mBestPostRecyclerView.addOnScrollListener(new BestPostPaginationScrollListener(getActivity(), mLinearLayoutManager, mAdapter, lastItem, bestPostData, mPaginationSynchronizer,
                                    mAcquireAccessTokenRequestQueue, mPaginationSynchronizer.isLoading(), mPaginationSynchronizer.isLoadSuccess()));
                            mProgressBar.setVisibility(View.GONE);
                        }

                        @Override
                        public void onParseBestPostFail() {
                            Toast.makeText(getActivity(), "Error parsing data", Toast.LENGTH_SHORT).show();
                            Log.i("Best post fetch error", "Error parsing data");
                            mProgressBar.setVisibility(View.GONE);
                        }
                    }).parseBestPost(response, new ArrayList<BestPostData>());
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                if (error instanceof AuthFailureError) {
                    // Error indicating that there was an Authentication Failure while performing the request
                    // Access token expired
                    new AcquireAccessToken(getActivity()).refreshAccessToken(mAcquireAccessTokenRequestQueue,
                            new AcquireAccessToken.AcquireAccessTokenListener() {
                                @Override
                                public void onAcquireAccessTokenSuccess() {
                                    queryBestPost(refreshTime - 1);
                                }

                                @Override
                                public void onAcquireAccessTokenFail() {}
                            });
                } else {
                    Log.i("best post fetch error", error.toString());
                    showErrorSnackbar();
                }
            }
        }) {
            @Override
            public Map<String, String> getHeaders() {
                String accessToken = getActivity().getSharedPreferences(SharedPreferencesUtils.AUTH_CODE_FILE_KEY, Context.MODE_PRIVATE).getString(SharedPreferencesUtils.ACCESS_TOKEN_KEY, "");
                return RedditUtils.getOAuthHeader(accessToken);
            }
        };
        bestPostRequest.setTag(BestPostFragment.class);
        mRequestQueue.add(bestPostRequest);
    }

    private void showErrorSnackbar() {
        mProgressBar.setVisibility(View.GONE);
        Snackbar snackbar = Snackbar.make(mCoordinatorLayout, "Error getting best post", Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction(R.string.retry, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                queryBestPost(1);
            }
        });
        snackbar.show();
    }
}