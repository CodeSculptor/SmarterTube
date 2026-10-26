package com.liskovsoft.smartyoutubetv2.mobile.ui.playback;

import android.content.Context;
import android.net.Uri;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerUI;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.Locale;

/**
 * The phone player's Share dialog (#83). Upstream's player share always appends the current
 * position ({@code ?t=}) without saying so; this one works like YouTube's share sheet: a plain
 * "Share link", plus "Share link (start at m:ss)" when the video is past the start. QR and embed
 * entries are upstream's, unchanged. stmobile-only, so shared {@code common} code stays untouched.
 */
final class MobileShareDialog {
    private static final String TAG = MobileShareDialog.class.getSimpleName();

    private MobileShareDialog() {
    }

    static void show(Context context) {
        PlaybackPresenter presenter = PlaybackPresenter.instance(context);
        Video video = presenter.getVideo();

        if (video == null || video.videoId == null) {
            // Nothing to time-stamp (e.g. no video yet): fall back to upstream's dialog.
            presenter.onButtonClicked(R.id.action_share, PlayerUI.BUTTON_OFF);
            return;
        }

        PlaybackView view = presenter.getView();
        int positionSec = Utils.toSec(view != null ? view.getPositionMs() : video.getPositionMs());

        AppDialogPresenter dialog = AppDialogPresenter.instance(context);

        dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.share_link),
                optionItem -> share(context, Uri.parse("https://youtu.be/" + video.videoId))));

        if (positionSec > 0) {
            dialog.appendSingleButton(UiOptionItem.from(
                    context.getString(R.string.mobile_share_link_start_at, format(positionSec)),
                    optionItem -> share(context, Utils.convertToFullVideoUrl(video.videoId, positionSec))));
        }

        AppDialogUtil.appendShareQRLinkDialogItem(context, dialog, video, positionSec);
        AppDialogUtil.appendShareEmbedLinkDialogItem(context, dialog, video, positionSec);

        dialog.showDialog(video.getTitle());
    }

    private static void share(Context context, Uri url) {
        Log.d(TAG, "Sharing %s", url);
        Utils.showMultiChooser(context, url);
    }

    private static String format(int totalSec) {
        int hours = totalSec / 3600;
        int minutes = (totalSec % 3600) / 60;
        int seconds = totalSec % 60;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }
}
