package com.liskovsoft.smartyoutubetv2.mobile.ui.miniplayer;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.liskovsoft.smartyoutubetv2.mobile.ui.playback.MobilePlaybackActivity;

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
