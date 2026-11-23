package com.liskovsoft.smartyoutubetv2.mobile.ui.miniplayer;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.liskovsoft.smartyoutubetv2.mobile.ui.playback.MobilePlaybackActivity;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.List;

/**
 * The player lives in its own task (taskAffinity in the manifest) so it can stay parked while the
 * browse screens are used (#96). These helpers find the other (browse) task and bring it forward.
 */
public final class MiniPlayerTasks {
    private MiniPlayerTasks() {}

    /** The app's browse task (any task whose root is not the player), or null when there is none. */
    private static ActivityManager.AppTask findBrowseTask(Context context) {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.AppTask> tasks = am != null ? am.getAppTasks() : null;
            if (tasks == null) {
                return null;
            }
            String player = MobilePlaybackActivity.class.getName();
            for (ActivityManager.AppTask task : tasks) {
                ActivityManager.RecentTaskInfo info = task.getTaskInfo();
                Intent base = info != null ? info.baseIntent : null;
                ComponentName root = base != null ? base.getComponent() : null;
                if (root != null && !player.equals(root.getClassName())) {
                    return task;
                }
            }
        } catch (RuntimeException e) {
            // Task lookup is best effort; callers fall back to starting Home.
        }
        return null;
    }

    public static boolean hasBrowseTask(Context context) {
        return findBrowseTask(context) != null;
    }

    /**
     * Minimise: bring the browse task forward with the player sliding down over it (#96). A plain
     * task move ignores custom animations, so on Android 10+ (where the task's top activity is
     * known) the top activity is re-delivered with a custom-animation start instead; that is the
     * same "relaunch the screen below" the player's Back always did. Older versions, or any
     * failure, use the plain move.
     */
    public static boolean bringBrowseForwardSliding(Context context) {
        ActivityManager.AppTask task = findBrowseTask(context);
        if (task == null) {
            return false;
        }
        try {
            ActivityManager.RecentTaskInfo info = task.getTaskInfo();
            ComponentName top = Build.VERSION.SDK_INT >= 29 && info != null ? info.topActivity : null;
            if (top != null) {
                Intent intent = new Intent().setComponent(top)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                android.os.Bundle options = ActivityOptions.makeCustomAnimation(
                        context, R.anim.mini_hold, R.anim.mini_slide_down).toBundle();
                context.startActivity(intent, options);
                return true;
            }
        } catch (RuntimeException e) {
            // fall through to the plain move
        }
        return bringBrowseForward(context);
    }

    /** Expand: bring the player forward sliding up over the browse screen. */
    public static void expandPlayerSliding(Context context) {
        Intent intent = new Intent(context, MobilePlaybackActivity.class);
        android.os.Bundle options = ActivityOptions.makeCustomAnimation(
                context, R.anim.mini_slide_up, R.anim.mini_hold).toBundle();
        context.startActivity(intent, options);
    }

    /** Bring the browse task to the front as it was left. False when there is none. */
    public static boolean bringBrowseForward(Context context) {
        ActivityManager.AppTask task = findBrowseTask(context);
        if (task == null) {
            return false;
        }
        try {
            task.moveToFront();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
