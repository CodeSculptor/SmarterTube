package com.liskovsoft.smartyoutubetv2.mobile.ui.browse;

import android.app.Activity;

import java.util.ArrayDeque;

/**
 * Runs Home's one-time launch dialogs (upload-notifications prompt, anonymous-stats prompt,
 * "What's new") one after another, so they never stack on top of each other.
 * <p>
 * Each step shows at most one dialog and calls {@code done} once it has been closed (or right away
 * when it has nothing to show). Steps may be asynchronous. Steps left when the activity goes away
 * are dropped; each step keeps its own "already asked" state, so a dropped step just asks on a
 * later launch.
 */
final class LaunchDialogQueue {
    interface Step {
        void run(Runnable done);
    }

    private final Activity mActivity;
    private final ArrayDeque<Step> mSteps = new ArrayDeque<>();
    private boolean mRunning;

    LaunchDialogQueue(Activity activity) {
        mActivity = activity;
    }

    LaunchDialogQueue add(Step step) {
        mSteps.add(step);
        return this;
    }

    void start() {
        if (!mRunning) {
            next();
        }
    }

    private void next() {
        Step step = mSteps.poll();
        if (step == null || mActivity.isFinishing() || mActivity.isDestroyed()) {
            mRunning = false;
            mSteps.clear();
            return;
        }
        mRunning = true;
        boolean[] called = {false};
        // Post, so a step that finishes synchronously doesn't recurse, and the next dialog opens
        // after the previous one's window is gone.
        step.run(() -> {
            if (!called[0]) {
                called[0] = true;
                mActivity.getWindow().getDecorView().post(this::next);
            }
        });
    }
}
