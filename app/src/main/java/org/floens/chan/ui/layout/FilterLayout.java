/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.floens.chan.ui.layout;

import static org.floens.chan.Chan.inject;
import static org.floens.chan.ui.theme.ThemeHelper.theme;
import static org.floens.chan.utils.AndroidUtils.dp;
import static org.floens.chan.utils.AndroidUtils.getAttrColor;
import static org.floens.chan.utils.AndroidUtils.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import org.floens.chan.R;
import org.floens.chan.core.manager.BoardManager;
import org.floens.chan.core.manager.FilterEngine;
import org.floens.chan.core.manager.FilterType;
import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.model.orm.Filter;
import org.floens.chan.core.repository.BoardRepository;
import org.floens.chan.ui.controller.FiltersController;
import org.floens.chan.ui.dialog.ColorPickerView;
import org.floens.chan.ui.drawable.DropdownArrowDrawable;
import org.floens.chan.ui.helper.BoardHelper;
import org.floens.chan.ui.view.FloatingMenu;
import org.floens.chan.ui.view.FloatingMenuItem;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

public class FilterLayout extends LinearLayout implements View.OnClickListener {
    private TextView typeText;
    private TextView boardsSelector;
    private TextView pattern;
    private TextView patternPreview;
    private TextView patternPreviewStatus;
    private CheckBox enabled;
    private ImageView help;
    private TextView actionText;
    private LinearLayout colorContainer;
    private View colorPreview;

    @Inject
    BoardManager boardManager;

    @Inject
    FilterEngine filterEngine;

    private FilterLayoutCallback callback;
    private Filter filter;

    public FilterLayout(Context context) {
        super(context);
    }

    public FilterLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FilterLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        inject(this);

        typeText = findViewById(R.id.type);
        boardsSelector = findViewById(R.id.boards);
        actionText = findViewById(R.id.action);
        pattern = findViewById(R.id.pattern);
        pattern.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter.pattern = s.toString();
                updateFilterValidity();
                updatePatternPreview();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        patternPreview = findViewById(R.id.pattern_preview);
        patternPreview.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePatternPreview();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        patternPreviewStatus = findViewById(R.id.pattern_preview_status);
        enabled = findViewById(R.id.enabled);
        help = findViewById(R.id.help);
        theme().helpDrawable.apply(help);
        help.setOnClickListener(this);
        colorContainer = findViewById(R.id.color_container);
        colorContainer.setOnClickListener(this);
        colorPreview = findViewById(R.id.color_preview);

        typeText.setOnClickListener(this);
        typeText.setCompoundDrawablesWithIntrinsicBounds(null, null, new DropdownArrowDrawable(dp(12), dp(12), true,
                getAttrColor(getContext(), R.attr.dropdown_dark_color), getAttrColor(getContext(), R.attr.dropdown_dark_pressed_color)), null);

        boardsSelector.setOnClickListener(this);
        boardsSelector.setCompoundDrawablesWithIntrinsicBounds(null, null, new DropdownArrowDrawable(dp(12), dp(12), true,
                getAttrColor(getContext(), R.attr.dropdown_dark_color), getAttrColor(getContext(), R.attr.dropdown_dark_pressed_color)), null);

        actionText.setOnClickListener(this);
        actionText.setCompoundDrawablesWithIntrinsicBounds(null, null, new DropdownArrowDrawable(dp(12), dp(12), true,
                getAttrColor(getContext(), R.attr.dropdown_dark_color), getAttrColor(getContext(), R.attr.dropdown_dark_pressed_color)), null);
    }

    public void setFilter(Filter filter) {
        this.filter = filter;

        pattern.setText(filter.pattern);

        updateFilterValidity();
        updateCheckboxes();
        updateFilterType();
        updateFilterAction();
        updateBoardsSummary();
        updatePatternPreview();
    }

    public void setCallback(FilterLayoutCallback callback) {
        this.callback = callback;
    }

    public Filter getFilter() {
        filter.enabled = enabled.isChecked();

        return filter;
    }

    @Override
    public void onClick(View v) {
        if (v == typeText) {
            @SuppressWarnings("unchecked") final SelectLayout<FilterType> selectLayout =
                    (SelectLayout<FilterType>) LayoutInflater.from(getContext())
                            .inflate(R.layout.layout_select, null);

            List<SelectLayout.SelectItem<FilterType>> items = new ArrayList<>();
            for (FilterType filterType : FilterType.values()) {
                String name = FiltersController.filterTypeName(filterType);
                String description = getString(filterType.isRegex ? R.string.filter_type_regex_matching : R.string.filter_type_string_matching);
                boolean checked = filter.hasFilter(filterType);

                items.add(new SelectLayout.SelectItem<>(
                        filterType, filterType.flag, name, description, name, checked
                ));
            }

            selectLayout.setItems(items);

            new AlertDialog.Builder(getContext())
                    .setView(selectLayout)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            List<SelectLayout.SelectItem<FilterType>> items = selectLayout.getItems();
                            int flags = 0;
                            for (SelectLayout.SelectItem<FilterType> item : items) {
                                if (item.checked) {
                                    flags |= item.item.flag;
                                }
                            }

                            filter.type = flags;
                            updateFilterType();
                            updatePatternPreview();
                        }
                    })
                    .show();
        } else if (v == boardsSelector) {
            @SuppressLint("InflateParams") @SuppressWarnings("unchecked") final SelectLayout<Board> selectLayout =
                    (SelectLayout<Board>) LayoutInflater.from(getContext())
                            .inflate(R.layout.layout_select, null);

            List<SelectLayout.SelectItem<Board>> items = new ArrayList<>();

            List<Board> allSavedBoards = new ArrayList<>();
            for (BoardRepository.SiteBoards item : boardManager.getSavedBoardsObservable().get()) {
                allSavedBoards.addAll(item.boards);
            }

            for (Board board : allSavedBoards) {
                String name = BoardHelper.getName(board);
                boolean checked = filterEngine.matchesBoard(filter, board);

                items.add(new SelectLayout.SelectItem<>(
                        board, board.id, name, "", name, checked
                ));
            }

            selectLayout.setItems(items);

            new AlertDialog.Builder(getContext())
                    .setView(selectLayout)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            List<SelectLayout.SelectItem<Board>> items = selectLayout.getItems();
                            boolean all = selectLayout.areAllChecked();
                            List<Board> boardList = new ArrayList<>(items.size());
                            if (!all) {
                                for (SelectLayout.SelectItem<Board> item : items) {
                                    if (item.checked) {
                                        boardList.add(item.item);
                                    }
                                }
                                if (boardList.isEmpty()) {
                                    all = true;
                                }
                            }

                            filterEngine.saveBoardsToFilter(boardList, all, filter);

                            updateBoardsSummary();
                        }
                    })
                    .show();
        } else if (v == actionText) {
            List<FloatingMenuItem> menuItems = new ArrayList<>(6);

            for (FilterEngine.FilterAction action : FilterEngine.FilterAction.values()) {
                menuItems.add(new FloatingMenuItem(action, FiltersController.actionName(action)));
            }

            FloatingMenu menu = new FloatingMenu(v.getContext());
            menu.setAnchor(v, Gravity.LEFT, -dp(5), -dp(5));
            menu.setPopupWidth(dp(150));
            menu.setCallback(new FloatingMenu.FloatingMenuCallback() {
                @Override
                public void onFloatingMenuItemClicked(FloatingMenu menu, FloatingMenuItem item) {
                    FilterEngine.FilterAction action = (FilterEngine.FilterAction) item.getId();
                    filter.action = action.id;
                    updateFilterAction();
                }

                @Override
                public void onFloatingMenuDismissed(FloatingMenu menu) {
                }
            });
            menu.setItems(menuItems);
            menu.show();
        } else if (v == help) {
            SpannableStringBuilder message = (SpannableStringBuilder) Html.fromHtml(getString(R.string.filter_help));
            TypefaceSpan[] typefaceSpans = message.getSpans(0, message.length(), TypefaceSpan.class);
            for (TypefaceSpan span : typefaceSpans) {
                if (span.getFamily().equals("monospace")) {
                    int start = message.getSpanStart(span);
                    int end = message.getSpanEnd(span);
                    message.setSpan(new BackgroundColorSpan(0x22000000), start, end, 0);
                }
            }

            StyleSpan[] styleSpans = message.getSpans(0, message.length(), StyleSpan.class);
            for (StyleSpan span : styleSpans) {
                if (span.getStyle() == Typeface.ITALIC) {
                    int start = message.getSpanStart(span);
                    int end = message.getSpanEnd(span);
                    message.setSpan(new BackgroundColorSpan(0x22000000), start, end, 0);
                }
            }

            new AlertDialog.Builder(getContext())
                    .setTitle(R.string.filter_help_title)
                    .setMessage(message)
                    .setPositiveButton(R.string.ok, null)
                    .show();
        } else if (v == colorContainer) {
            final ColorPickerView colorPickerView = new ColorPickerView(getContext());
            colorPickerView.setColor(filter.color);

            AlertDialog dialog = new AlertDialog.Builder(getContext())
                    .setTitle(R.string.filter_color_pick)
                    .setView(colorPickerView)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            filter.color = colorPickerView.getColor();
                            updateFilterAction();
                        }
                    })
                    .show();
            dialog.getWindow().setLayout(dp(300), dp(300));
        }
    }

    private void updateFilterValidity() {
        boolean valid = !TextUtils.isEmpty(filter.pattern) && filterEngine.compile(filter.pattern) != null;
        pattern.setError(valid ? null : getString(R.string.filter_invalid_pattern));

        if (callback != null) {
            callback.setSaveButtonEnabled(valid);
        }
    }

    private void updateBoardsSummary() {
        String text = getString(R.string.filter_boards) + " (";
        if (filter.allBoards) {
            text += getString(R.string.filter_all);
        } else {
            text += filterEngine.getFilterBoardCount(filter);
        }
        text += ")";
        boardsSelector.setText(text);
    }

    private void updateCheckboxes() {
        enabled.setChecked(filter.enabled);
    }

    private void updateFilterAction() {
        FilterEngine.FilterAction action = FilterEngine.FilterAction.forId(filter.action);
        actionText.setText(FiltersController.actionName(action));
        colorContainer.setVisibility(action == FilterEngine.FilterAction.COLOR ? VISIBLE : GONE);
        if (filter.color == 0) {
            filter.color = 0xffff0000;
        }
        colorPreview.setBackgroundColor(filter.color);
    }

    private void updateFilterType() {
        int types = FilterType.forFlags(filter.type).size();
        String text = getString(R.string.filter_types) + " (" + types + ")";
        typeText.setText(text);
    }

    private void updatePatternPreview() {
        String text = patternPreview.getText().toString();
        boolean matches = filterEngine.matches(filter, true, text, true);
        patternPreviewStatus.setText(matches ? R.string.filter_matches : R.string.filter_no_matches);
    }

    public interface FilterLayoutCallback {
        void setSaveButtonEnabled(boolean enabled);
    }
}
