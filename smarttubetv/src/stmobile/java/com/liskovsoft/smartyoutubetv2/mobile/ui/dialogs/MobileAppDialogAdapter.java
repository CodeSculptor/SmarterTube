package com.liskovsoft.smartyoutubetv2.mobile.ui.dialogs;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Flat list of section headers + option rows, built from a list of
 * {@link OptionCategory}. The category type controls which left-side indicator (radio /
 * checkbox) and whether to show a right-side switch — but a single tap on a row always
 * runs the same code path: flip {@link OptionItem#isSelected()} and call onSelect, then
 * re-render.
 *
 * Radio behavior is enforced locally — picking an item in a radio category clears the
 * selected state of its siblings via {@code onSelect(false)} before activating the new one.
 */
class MobileAppDialogAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_OPTION = 1;
    private static final int TYPE_LONG_TEXT = 2;

    private final List<Row> mRows = new ArrayList<>();

    void setCategories(List<OptionCategory> categories, boolean isExpandable) {
        mRows.clear();
        if (categories == null) {
            notifyDataSetChanged();
            return;
        }
        // Suppress the category-header row when it would be redundant with the dialog
        // title (expandable single category — the fragment already shows category.title up
        // top, see MobileAppDialogFragment#show).
        boolean suppressHeaders = isExpandable && categories.size() == 1;
        for (OptionCategory category : categories) {
            if (category.options == null || category.options.isEmpty()) {
                continue;
            }
            if (!suppressHeaders && !TextUtils.isEmpty(category.title)
                    && category.type != OptionCategory.TYPE_SINGLE_SWITCH
                    && category.type != OptionCategory.TYPE_SINGLE_BUTTON) {
                mRows.add(Row.header(category.title));
            }
            for (OptionItem item : category.options) {
                if (category.type == OptionCategory.TYPE_LONG_TEXT) {
                    mRows.add(Row.longText(item));
                } else {
                    mRows.add(Row.option(category, item));
                }
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mRows.size();
    }

    @Override
    public int getItemViewType(int position) {
        switch (mRows.get(position).kind) {
            case HEADER: return TYPE_HEADER;
            case LONG_TEXT: return TYPE_LONG_TEXT;
            case OPTION:
            default: return TYPE_OPTION;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case TYPE_HEADER:
                return new HeaderHolder(
                        inflater.inflate(R.layout.mobile_app_dialog_header, parent, false));
            case TYPE_LONG_TEXT:
                return new LongTextHolder(
                        inflater.inflate(R.layout.mobile_app_dialog_longtext, parent, false));
            case TYPE_OPTION:
            default:
                return new OptionHolder(
                        inflater.inflate(R.layout.mobile_app_dialog_option, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = mRows.get(position);
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).title.setText(row.title);
        } else if (holder instanceof LongTextHolder) {
            CharSequence body = row.item.getDescription();
            if (TextUtils.isEmpty(body)) {
                body = row.item.getTitle();
            }
            ((LongTextHolder) holder).body.setText(body);
        } else if (holder instanceof OptionHolder) {
            bindOption((OptionHolder) holder, row);
        }
    }

    private void bindOption(OptionHolder h, Row row) {
        OptionItem item = row.item;
        h.title.setText(item.getTitle());
        CharSequence desc = item.getDescription();
        if (TextUtils.isEmpty(desc)) {
            h.description.setVisibility(View.GONE);
        } else {
            h.description.setVisibility(View.VISIBLE);
            h.description.setText(desc);
        }

        h.radio.setVisibility(View.GONE);
        h.check.setVisibility(View.GONE);
        h.toggle.setVisibility(View.GONE);

        switch (row.categoryType) {
            case OptionCategory.TYPE_RADIO_LIST:
                h.radio.setVisibility(View.VISIBLE);
                h.radio.setChecked(item.isSelected());
                break;
            case OptionCategory.TYPE_CHECKBOX_LIST:
                h.check.setVisibility(View.VISIBLE);
                h.check.setChecked(item.isSelected());
                break;
            case OptionCategory.TYPE_SINGLE_SWITCH:
                h.toggle.setVisibility(View.VISIBLE);
                h.toggle.setChecked(item.isSelected());
                break;
            case OptionCategory.TYPE_STRING_LIST:
            case OptionCategory.TYPE_SINGLE_BUTTON:
            case OptionCategory.TYPE_CHAT:
            case OptionCategory.TYPE_COMMENTS:
            default:
                // No indicator — just a tappable row.
                break;
        }

        h.itemView.setOnClickListener(v -> onOptionClicked(row));
    }

    private void onOptionClicked(Row row) {
        OptionItem item = row.item;
        switch (row.categoryType) {
            case OptionCategory.TYPE_RADIO_LIST: {
                // Mutual exclusion: clear siblings (their callbacks will tear down any
                // side effects), then activate the new pick.
                if (row.siblings != null) {
                    for (OptionItem sibling : row.siblings) {
                        if (sibling != item && sibling.isSelected()) {
                            sibling.onSelect(false);
                        }
                    }
                }
                item.onSelect(true);
                break;
            }
            case OptionCategory.TYPE_CHECKBOX_LIST:
            case OptionCategory.TYPE_SINGLE_SWITCH:
                item.onSelect(!item.isSelected());
                break;
            case OptionCategory.TYPE_STRING_LIST:
            case OptionCategory.TYPE_SINGLE_BUTTON:
            case OptionCategory.TYPE_CHAT:
            case OptionCategory.TYPE_COMMENTS:
            default:
                item.onSelect(true);
                break;
        }
        // Re-render the whole list — a radio change touches multiple rows, and a callback
        // may have mutated the category contents (the presenter sometimes reopens or
        // replaces the dialog, which is harmless to re-bind against).
        notifyDataSetChanged();
    }

    // ----- view holders -----

    static class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView title;
        HeaderHolder(View v) {
            super(v);
            title = v.findViewById(R.id.category_title);
        }
    }

    static class OptionHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView description;
        final RadioButton radio;
        final CheckBox check;
        final SwitchCompat toggle;
        OptionHolder(View v) {
            super(v);
            title = v.findViewById(R.id.option_title);
            description = v.findViewById(R.id.option_description);
            radio = v.findViewById(R.id.option_radio);
            check = v.findViewById(R.id.option_check);
            toggle = v.findViewById(R.id.option_switch);
        }
    }

    static class LongTextHolder extends RecyclerView.ViewHolder {
        final TextView body;
        LongTextHolder(View v) {
            super(v);
            body = v.findViewById(R.id.longtext_body);
        }
    }

    // ----- row model -----

    private enum Kind { HEADER, OPTION, LONG_TEXT }

    private static class Row {
        final Kind kind;
        final CharSequence title;
        final OptionItem item;
        final int categoryType;
        final List<OptionItem> siblings;

        private Row(Kind kind, CharSequence title, OptionItem item,
                    int categoryType, List<OptionItem> siblings) {
            this.kind = kind;
            this.title = title;
            this.item = item;
            this.categoryType = categoryType;
            this.siblings = siblings;
        }

        static Row header(CharSequence title) {
            return new Row(Kind.HEADER, title, null, -1, null);
        }

        static Row option(OptionCategory category, OptionItem item) {
            return new Row(Kind.OPTION, null, item, category.type, category.options);
        }

        static Row longText(OptionItem item) {
            return new Row(Kind.LONG_TEXT, null, item, OptionCategory.TYPE_LONG_TEXT, null);
        }
    }
}
