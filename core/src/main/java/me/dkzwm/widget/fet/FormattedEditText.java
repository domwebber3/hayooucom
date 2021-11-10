/*
 * MIT License
 *
 * Copyright (c) 2017 dkzwm
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package me.dkzwm.widget.fet;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;
import androidx.appcompat.widget.AppCompatEditText;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Created by dkzwm on 2017/2/22.
 *
 * @author dkzwm
 */
public class FormattedEditText extends AppCompatEditText {
    public static final int MODE_NONE = -1;
    public static final int MODE_SIMPLE = 0;
    public static final int MODE_COMPLEX = 1;
    public static final int MODE_MASK = 2;
    public static final int MODE_HINT = 3;
    public static final int GRAVITY_TOP = 0;
    public static final int GRAVITY_CENTER = 1;
    public static final int GRAVITY_BOTTOM = 2;
    private static final Object SELECTION_SPAN = new Object();
    private static final InputFilter[] EMPTY_FILTERS = new InputFilter[0];
    private static final IPlaceholderSpan[] EMPTY_SPANS = new IPlaceholderSpan[0];
    private static final Spanned EMPTY_SPANNED = new SpannedString("");
    private static final char DEFAULT_PLACE_HOLDER = ' ';
    private static final char DEFAULT_MARK = '*';
    private static final char DIGIT_MASK = '0';
    private static final char LETTER_MASK = 'A';
    private static final char DIGIT_OR_LETTER_MASK = '*';
    private static final char CHARACTER_MASK = '?';
    private static final char ESCAPE_CHAR = '\\';
    private int mTouchSlop;
    private Placeholder[] mHolders;
    @Mode private int mMode = MODE_NONE;
    private char mPlaceholder = 0;
    private char mEmptyPlaceholder = 0;
    private char mMark = 0;
    private String mPlaceholders;
    private String mHintText;
    private String mFormatStyle;
    private boolean mShowHintWhileEmpty = false;
    private int mHintColor = Color.TRANSPARENT;
    private int mLastIndex;
    private boolean mIsFormatted = false;
    private List<TextWatcher> mWatchers;
    private Drawable mClearDrawable;
    private int mGravity = GRAVITY_CENTER;
    private int mRealPaddingRight;
    private int mDrawablePadding = 0;
    private float[] mDownPoint = new float[2];
    private OnClearClickListener mClearClickListener;
    private FormattedTextWatcher mTextWatcher;
    private LengthFilterDelegate mLengthFilterDelegate;
    private boolean mRestoring = false;
    private boolean mFilterRestoreTextChangeEvent = false;
    private PlaceholderComparator mComparator = new PlaceholderComparator();
    private int[] mEscapeIndexes;

    public FormattedEditText(Context context) {
        super(context);
        init(context, null, 0);
    }

    public FormattedEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);
    }

    public FormattedEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr) {
        mTextWatcher = new FormattedTextWatcher();
        super.addTextChangedListener(mTextWatcher);
        ViewConfiguration viewConfiguration = ViewConfiguration.get(getContext());
        mTouchSlop = viewConfiguration.getScaledTouchSlop();
        if (attrs != null) {
            TypedArray ta =
                    context.obtainStyledAttributes(
                            attrs, R.styleable.FormattedEditText, defStyleAttr, 0);
            try {
                @Mode int mode = ta.getInt(R.styleable.FormattedEditText_fet_mode, MODE_NONE);
                String mark = ta.getString(R.styleable.FormattedEditText_fet_mark);
                int hintColor = ta.getColor(R.styleable.FormattedEditText_fet_hintTextColor, 0);
                String placeHolder = ta.getString(R.styleable.FormattedEditText_fet_placeholder);
                String emptyPlaceHolder =
                        ta.getString(R.styleable.FormattedEditText_fet_emptyPlaceholder);
                String formatStyle = ta.getString(R.styleable.FormattedEditText_fet_formatStyle);
                String hintText = ta.getString(R.styleable.FormattedEditText_fet_hintText);
                boolean showHintWhileEmpty =
                        ta.getBoolean(R.styleable.FormattedEditText_fet_showHintWhileEmpty, false);
                mClearDrawable = ta.getDrawable(R.styleable.FormattedEditText_fet_clearDrawable);
                mGravity =
                        ta.getInt(
                                R.styleable.FormattedEditText_fet_drawableGravity, GRAVITY_CENTER);
                mDrawablePadding =
                        ta.getDimensionPixelSize(
                                R.styleable.FormattedEditText_fet_drawablePadding, 0);
                setConfig(
                        Config.create()
                                .mode(mode)
                                .placeholder(
                                        (placeHolder == null || placeHolder.length() == 0)
                                                ? DEFAULT_PLACE_HOLDER
                                                : placeHolder.charAt(0))
                                .hintColor(hintColor)
                                .hintText(hintText)
                                .mark(
                                        (mark == null || mark.length() == 0)
                                                ? DEFAULT_MARK
                                                : mark.charAt(0))
                                .emptyPlaceholder(
                                        (emptyPlaceHolder == null || emptyPlaceHolder.length() == 0)
                                                ? 0
                                                : emptyPlaceHolder.charAt(0))
                                .formatStyle(formatStyle)
                                .showHintWhileEmpty(showHintWhileEmpty),
                        true);
            } finally {
                ta.recycle();
            }
        }
        setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight(), getPaddingBottom());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) {
                throw new UnsupportedOperationException(
                        "We can not support this feature when the layout is right-to-left");
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        resetClearDrawableBound();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mClearDrawable != null) {
            int width = mClearDrawable.getIntrinsicWidth() + mDrawablePadding * 2;
            int height = mClearDrawable.getIntrinsicHeight() + mDrawablePadding * 2;
            int measuredWidth = getMeasuredWidth();
            int measuredHeight = getMeasuredHeight();
            int remeasuredWidth = measuredWidth, remeasuredHeight = measuredHeight;
            if (measuredWidth < width) {
                int specMode = MeasureSpec.getMode(widthMeasureSpec);
                int specSize = MeasureSpec.getSize(widthMeasureSpec);
                if (specMode != MeasureSpec.EXACTLY) {
                    remeasuredWidth = Math.max(width, measuredWidth);
                    if (specMode == MeasureSpec.AT_MOST) {
                        remeasuredWidth = Math.min(remeasuredWidth, specSize);
                    }
                }
            }
            if (measuredHeight < height) {
                int specMode = MeasureSpec.getMode(heightMeasureSpec);
                int specSize = MeasureSpec.getSize(heightMeasureSpec);
                if (specMode != MeasureSpec.EXACTLY) {
                    remeasuredHeight = Math.max(height, measuredHeight);
                    if (specMode == MeasureSpec.AT_MOST) {
                        remeasuredHeight = Math.min(remeasuredHeight, specSize);
                    }
                }
            }
            if (remeasuredWidth != measuredWidth || remeasuredHeight != measuredHeight) {
                setMeasuredDimension(remeasuredWidth, remeasuredHeight);
            }
        }
    }

    @Override
    public void addTextChangedListener(TextWatcher watcher) {
        if (mWatchers == null) {
            mWatchers = new ArrayList<>();
        }
        mWatchers.add(watcher);
    }

    @Override
    public void removeTextChangedListener(TextWatcher watcher) {
        if (mWatchers != null) {
            mWatchers.remove(watcher);
        }
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        mRealPaddingRight = right;
        if (mClearDrawable != null) {
            right += mClearDrawable.getIntrinsicWidth() + mDrawablePadding * 2;
        }
        super.setPadding(left, top, right, bottom);
        resetClearDrawableBound();
    }

    @Override
    public void setPaddingRelative(int start, int top, int end, int bottom) {
        mRealPaddingRight = end;
        if (mClearDrawable != null) {
            end += mClearDrawable.getIntrinsicWidth() + mDrawablePadding * 2;
        }
        super.setPaddingRelative(start, top, end, bottom);
        resetClearDrawableBound();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mClearDrawable != null && isFocused() && length() > 0) {
            canvas.save();
            canvas.translate(getScrollX(), getScrollY());
            mClearDrawable.draw(canvas);
            canvas.restore();
        }
    }

    @Override
    protected void drawableStateChanged() {
        if (mClearDrawable != null) {
            final int[] state = getDrawableState();
            if (mClearDrawable.isStateful() && mClearDrawable.setState(state)) {
                final Rect dirty = mClearDrawable.getBounds();
                final int scrollX = getScrollX();
                final int scrollY = getScrollY();
                invalidate(
                        dirty.left + scrollX,
                        dirty.top + scrollY,
                        dirty.right + scrollX,
                        dirty.bottom + scrollY);
            }
        }
        super.drawableStateChanged();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mClearDrawable != null) {
            final float x = event.getX();
            final float y = event.getY();
            final int action = event.getActionMasked();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    mDownPoint[0] = x;
                    mDownPoint[1] = y;
                    break;
                case MotionEvent.ACTION_UP:
                    final Rect rect = mClearDrawable.getBounds();
                    if (rect.top - mDrawablePadding <= y
                            && rect.bottom + mDrawablePadding >= y
                            && rect.left - mDrawablePadding <= x
                            && rect.right + mDrawablePadding >= x) {
                        if (Math.abs(mDownPoint[0] - x) <= mTouchSlop
                                && Math.abs(mDownPoint[1] - y) <= mTouchSlop) {
                            if (mClearClickListener != null) {
                                if (!mClearClickListener.onClearClick(this, mClearDrawable)) {
                                    clearTextInTouch(event);
                                }
                            } else {
                                clearTextInTouch(event);
                            }
                            super.onTouchEvent(event);
                            return true;
                        }
                    }
                    break;
            }
        }
        return super.onTouchEvent(event);
    }

    public void setClearDrawable(Drawable drawable) {
        if (mClearDrawable != drawable) {
            mClearDrawable = drawable;
            requestLayout();
        }
    }

    public void setClearDrawablePadding(int pad) {
        if (mDrawablePadding != pad) {
            mDrawablePadding = pad;
            if (mClearDrawable != null) {
                requestLayout();
            }
        }
    }

    public void setOnClearClickListener(OnClearClickListener clickListener) {
        mClearClickListener = clickListener;
    }

    @Override
    @CallSuper
    public void setFilters(InputFilter[] filters) {
        if (filters == null) {
            throw new IllegalArgumentException("filters can not be null");
        }
        boolean havingFilter = false;
        for (int i = 0; i < filters.length; i++) {
            if (filters[i] instanceof InputFilter.LengthFilter) {
                mLengthFilterDelegate = new LengthFilterDelegate(filters[i]);
                filters[i] = mLengthFilterDelegate;
            } else if (filters[i] instanceof PlaceholderFilter) {
                havingFilter = true;
            }
        }
        if (!havingFilter) {
            InputFilter[] replaceFilters = new InputFilter[filters.length + 1];
            replaceFilters[0] = new PlaceholderFilter();
            System.arraycopy(filters, 0, replaceFilters, 1, filters.length);
            super.setFilters(replaceFilters);
            return;
        }
        super.setFilters(filters);
    }

    @Mode
    public int getMode() {
        return mMode;
    }

    public String getFormatStyle() {
        return mFormatStyle;
    }

    public char getPlaceholder() {
        return mPlaceholder;
    }

    public char getEmptyPlaceholder() {
        return mEmptyPlaceholder;
    }

    public char getMark() {
        return mMark;
    }

    public String getHintText() {
        return mHintText;
    }

    public boolean isShowHintWhileEmpty() {
        return mShowHintWhileEmpty;
    }

    public int getHintColor() {
        return mHintColor;
    }

    private void clearTextInTouch(MotionEvent event) {
        event.setAction(MotionEvent.ACTION_CANCEL);
        Editable editable = getText();
        if (editable != null) {
            editable.clear();
        }
    }

    private void setConfig(Config config, boolean create) {
        if (config.mMode != null) {
            mMode = config.mMode;
        }
        if (mMode == MODE_NONE) {
            clearArray(mHolders);
            mHolders = null;
            return;
        }
        if (config.mFormatStyle != null) {
            mFormatStyle = config.mFormatStyle;
            if (mMode == MODE_SIMPLE) {
                if (config.mPlaceholder != null) {
                    mPlaceholder = config.mPlaceholder;
                }
                parseSimplePlaceholders();
            } else if (mMode == MODE_COMPLEX) {
                if (config.mMark != null) {
                    mMark = config.mMark;
                }
                parseComplexPlaceholders();
            } else {
                final int length = mFormatStyle.length();
                final int[] temp = new int[length];
                int index = 0;
                for (int i = 0; i < mFormatStyle.length(); i++) {
                    char c = mFormatStyle.charAt(i);
                    if (c == ESCAPE_CHAR) {
                        temp[index] = i;
                        index++;
                    }
                }
                final int[] indexes = new int[index];
                System.arraycopy(temp, 0, indexes, 0, index);
                mEscapeIndexes = indexes;
                if (mMode == MODE_HINT) {
                    checkHintStyleIsRight(config.mHintText);
                }
            }
        } else if (mFormatStyle != null) {
            if (mMode == MODE_SIMPLE) {
                if (config.mPlaceholder != null && mPlaceholder != config.mPlaceholder) {
                    mPlaceholder = config.mPlaceholder;
                    if (mHolders != null) {
                        final Placeholder[] placeholders = mHolders;
                        for (Placeholder holder : placeholders) {
                            holder.holder = config.mPlaceholder;
                        }
                        mPlaceholders = String.valueOf(mPlaceholder);
                    } else {
                        parseSimplePlaceholders();
                    }
                }
            } else if (mMode == MODE_COMPLEX) {
                if (config.mMark != null && mMark != config.mMark) {
                    mMark = config.mMark;
                    parseComplexPlaceholders();
                }
            } else if (mMode == MODE_HINT) {
                checkHintStyleIsRight(config.mHintText);
            }
        } else {
            throw new IllegalArgumentException("Format style can not be empty");
        }
        if (config.mShowHintWhileEmpty != null) {
            mShowHintWhileEmpty = config.mShowHintWhileEmpty;
        }
        if (config.mEmptyPlaceholder != null) {
            mEmptyPlaceholder = config.mEmptyPlaceholder;
        }
        if (config.mHintColor != null) {
            mHintColor = config.mHintColor;
        }
        Editable text = getText();
        if (text == null || text.length() == 0) {
            return;
        }
        if (!create) {
            setText(getRealText());
        } else {
            setText(text);
        }
        text = getText();
        Selection.setSelection(text, text.length());
    }

    private void parseSimplePlaceholders() {
        if (TextUtils.isDigitsOnly(mFormatStyle)) {
            mHolders = new Placeholder[mFormatStyle.length()];
            Placeholder holder = new Placeholder();
            int index = Character.getNumericValue(mFormatStyle.charAt(0));
            holder.index = index;
            holder.holder = mPlaceholder;
            mHolders[0] = holder;
            for (int i = 1; i < mFormatStyle.length(); i++) {
                int number = Character.getNumericValue(mFormatStyle.charAt(i));
                holder = new Placeholder();
                index = mHolders[i - 1].index + 1 + number;
                holder.index = index;
                holder.holder = mPlaceholder;
                mHolders[i] = holder;
            }
            mPlaceholders = String.valueOf(mPlaceholder);
        } else {
            throw new IllegalArgumentException("Format style must be numeric");
        }
    }

    private void parseComplexPlaceholders() {
        if (mFormatStyle.indexOf(mMark) == -1) {
            throw new IllegalArgumentException("Format style must be have Mark strings");
        }
        final int length = mFormatStyle.length();
        final Placeholder[] temp = new Placeholder[length];
        int realCount = 0;
        Placeholder holder;
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            final char sub = mFormatStyle.charAt(i);
            if (mMark != sub) {
                if (!Character.isDigit(sub)) {
                    builder.append(sub);
                }
                holder = new Placeholder();
                holder.index = i;
                holder.holder = sub;
                temp[realCount] = holder;
                realCount += 1;
            }
        }
        if (length > 0) {
            holder = new Placeholder();
            holder.index = length;
            holder.holder = 0;
            temp[realCount] = holder;
            realCount += 1;
        }
        mPlaceholders = builder.toString();
        mHolders = new Placeholder[realCount];
        System.arraycopy(temp, 0, mHolders, 0, realCount);
        clearArray(temp);
    }

    public String getRealText() {
        return getRealText(false);
    }

    private String getRealText(boolean saved) {
        if (saved && mMode == MODE_NONE) {
            return null;
        }
        Editable editable = getText();
        if (editable == null || editable.length() == 0) {
            return "";
        }
        SpannableStringBuilder value = new SpannableStringBuilder(editable);
        IPlaceholderSpan[] spans;
        if (mMode == MODE_NONE) {
            spans = EMPTY_SPANS;
        } else if (mMode < MODE_MASK) {
            spans =
                    value.getSpans(
                            0,
                            Math.min(value.length(), mHolders[mHolders.length - 1].index),
                            IPlaceholderSpan.class);
        } else {
            spans =
                    value.getSpans(
                            0,
                            Math.min(value.length(), mFormatStyle.length()),
                            IPlaceholderSpan.class);
            if (spans.length == mFormatStyle.length()) {
                return "";
            }
        }
        if (spans.length == 0) {
            if (saved) {
                value.clear();
                return null;
            }
        } else {
            clearNonEmptySpans(value, spans, false);
        }
        final String realText = value.toString();
        value.clear();
        return realText;
    }

    private void checkHintStyleIsRight(String hintText) {
        if (hintText != null) {
            mHintText = hintText;
            int indexInStyle = 0;
            int indexInText = 0;
            boolean nextCharIsText = false;
            while (indexInStyle < mFormatStyle.length()) {
                if (indexInText >= mHintText.length()) {
                    throw new IllegalArgumentException(
                            "Hint text style must be conform to formatting style");
                }
                char charInStyle = mFormatStyle.charAt(indexInStyle);
                if (!nextCharIsText && isMaskChar(charInStyle)) {
                    if (isMismatchMask(charInStyle, mHintText.charAt(indexInText))) {
                        throw new IllegalArgumentException(
                                "Hint text style must be conform to formatting style");
                    } else {
                        indexInText += 1;
                        indexInStyle += 1;
                    }
                } else if (!nextCharIsText && charInStyle == ESCAPE_CHAR) {
                    nextCharIsText = true;
                    indexInStyle += 1;
                } else {
                    char charInText = mHintText.charAt(indexInText);
                    if (charInStyle != charInText) {
                        throw new IllegalArgumentException(
                                "Hint text style must be conform to formatting style");
                    }
                    nextCharIsText = false;
                    indexInText += 1;
                    indexInStyle += 1;
                }
            }
            if (mHintText.length() != indexInText) {
                throw new IllegalArgumentException(
                        "Hint text style must be conform to formatting style");
            }
        }
    }

    private void resetClearDrawableBound() {
        if (mClearDrawable != null) {
            final int top = getPaddingTop() + mDrawablePadding;
            final int bottom = getPaddingBottom() + mDrawablePadding;
            int width = mClearDrawable.getIntrinsicWidth();
            int height = mClearDrawable.getIntrinsicHeight();
            final int newRight = getWidth() - mRealPaddingRight - mDrawablePadding;
            final int h = getHeight();
            switch (mGravity) {
                case GRAVITY_TOP:
                    mClearDrawable.setBounds(newRight - width, top, newRight, top + height);
                    break;
                case GRAVITY_CENTER:
                    int newTop = top + (h - top - bottom - height) / 2;
                    mClearDrawable.setBounds(newRight - width, newTop, newRight, newTop + height);
                    break;
                case GRAVITY_BOTTOM:
                default:
                    int newBottom = h - bottom;
                    mClearDrawable.setBounds(
                            newRight - width, newBottom - height, newRight, newBottom);
                    break;
            }
        }
    }

    private void sendBeforeTextChanged(CharSequence s, int start, int count, int after) {
        final List<TextWatcher> list = mWatchers;
        if (list != null) {
            final int size = list.size();
            for (int i = 0; i < size; i++) {
                list.get(i).beforeTextChanged(s, start, count, after);
            }
        }
    }

    private void sendOnTextChanged(CharSequence s, int start, int before, int count) {
        final List<TextWatcher> list = mWatchers;
        if (list != null) {
            final int size = list.size();
            for (int i = 0; i < size; i++) {
                list.get(i).onTextChanged(s, start, before, count);
            }
        }
    }

    private void sendAfterTextChanged(Editable s) {
        final List<TextWatcher> list = mWatchers;
        if (list != null) {
            final int size = list.size();
            for (int i = 0; i < size; i++) {
                list.get(i).afterTextChanged(s);
            }
        }
    }

    private <T> void clearArray(final T[] array) {
        if (array != null) {
            Arrays.fill(array, null);
        }
    }

    private void formatTextWhenDelete(final Editable editable, int start, int before) {
        mIsFormatted = true;
        final boolean filter = mFilterRestoreTextChangeEvent;
        super.removeTextChangedListener(mTextWatcher);
        InputFilter[] filters = editable.getFilters();
        editable.setFilters(EMPTY_FILTERS);
        int selectionStart, selectionEnd;
        if (!filter) {
            selectionStart = Selection.getSelectionStart(editable);
            selectionEnd = Selection.getSelectionEnd(editable);
            editable.setSpan(SELECTION_SPAN, selectionStart, selectionEnd, Spanned.SPAN_MARK_MARK);
        }
        if (mMode < MODE_MASK) {
            final boolean deletedLast = start >= editable.length();
            if (!deletedLast) {
                formatDefined(editable, start, true);
            } else {
                for (int i = start; i > 0; i--) {
                    final char sub = editable.charAt(i - 1);
                    final char place = findPlaceholder(i - 1);
                    if (sub == place) {
                        editable.delete(i - 1, i);
                    } else {
                        break;
                    }
                }
            }
        } else {
            formatMask(editable, start, true);
        }
        if (!filter) {
            selectionStart = editable.getSpanStart(SELECTION_SPAN);
            selectionEnd = editable.getSpanEnd(SELECTION_SPAN);
            editable.removeSpan(SELECTION_SPAN);
            editable.setFilters(filters);
            Editable text = getText();
            Selection.setSelection(text, selectionStart, selectionEnd);
        } else {
            setFilters(filters);
        }
        mIsFormatted = false;
        super.addTextChangedListener(mTextWatcher);
    }

    private void formatTextWhenAppend(final Editable editable, int start, int before, int count) {
        mIsFormatted = true;
        final boolean filter = mFilterRestoreTextChangeEvent;
        super.removeTextChangedListener(mTextWatcher);
        InputFilter[] filters = editable.getFilters();
        editable.setFilters(EMPTY_FILTERS);
        int selectionStart, selectionEnd;
        if (!filter) {
            selectionStart = Selection.getSelectionStart(editable);
            selectionEnd = Selection.getSelectionEnd(editable);
            editable.setSpan(SELECTION_SPAN, selectionStart, selectionEnd, Spanned.SPAN_MARK_MARK);
        }
        if (mMode < MODE_MASK) {
            boolean appendedLast = start > mHolders[mHolders.length - 1].index;
            if (!appendedLast) {
                formatDefined(editable, start, false);
            }
        } else {
            formatMask(editable, start, false);
        }
        if (!filter) {
            selectionStart = editable.getSpanStart(SELECTION_SPAN);
            selectionEnd = editable.getSpanEnd(SELECTION_SPAN);
            editable.removeSpan(SELECTION_SPAN);
            editable.setFilters(filters);
            if (mLengthFilterDelegate != null) {
                CharSequence out =
                        mLengthFilterDelegate.mFilter.filter(
                                editable, 0, editable.length(), EMPTY_SPANNED, 0, 0);
                if (out != null) {
                    editable.delete(out.length(), editable.length());
                }
            }
            Selection.setSelection(
                    editable,
                    Math.min(selectionStart, editable.length()),
                    Math.min(selectionEnd, editable.length()));
        } else {
            editable.setFilters(filters);
        }
        mIsFormatted = false;
        super.addTextChangedListener(mTextWatcher);
    }

    private char findPlaceholder(int index) {
        final int len = mHolders.length;
        final int last = mLastIndex;
        final int centerIndex = mHolders[last].index;
        if (centerIndex == index) {
            return mHolders[last].holder;
        } else if (centerIndex < index) {
            for (int i = last; i < len; i++) {
                mLastIndex = i;
                if (mHolders[i].index == index) {
                    return mHolders[i].holder;
                } else if (mHolders[i].index > index) {
                    return 0;
                }
            }
        } else {
            for (int i = last; i >= 0; i--) {
                mLastIndex = i;
                if (mHolders[i].index == index) {
                    return mHolders[i].holder;
                } else if (mHolders[i].index < index) {
                    return 0;
                }
            }
        }
        return 0;
    }

    private void formatDefined(Editable editable, int start, boolean deletion) {
        start = clearPlaceholders(editable, start);
        int selectionIndex = -1;
        int indexInText = start;
        final int maxPos = mHolders[mHolders.length - 1].index;
        while (indexInText < maxPos) {
            if (indexInText >= editable.length()) {
                break;
            }
            char placeholder = findPlaceholder(indexInText);
            if (placeholder != 0) {
                editable.insert(indexInText, String.valueOf(placeholder));
                editable.setSpan(
                        new PlaceholderSpan(),
                        indexInText,
                        indexInText + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                indexInText += 1;
                if (selectionIndex == -1) {
                    if (indexInText == start + 1) {
                        selectionIndex = indexInText;
                    }
                } else if (indexInText == selectionIndex + 1) {
                    selectionIndex = indexInText;
                }
            } else {
                indexInText += 1;
            }
        }
        if (deletion && start == 0 && selectionIndex != -1) {
            editable.setSpan(
                    SELECTION_SPAN, selectionIndex, selectionIndex, Spanned.SPAN_MARK_MARK);
        }
    }

    private void formatMask(final Editable editable, int start, boolean deletion) {
        start = clearPlaceholders(editable, start);
        if (start == -1) {
            return;
        }
        if (deletion
                && start == editable.length()
                && ((mMode == MODE_MASK && mEmptyPlaceholder == 0)
                        || (mMode == MODE_HINT && mHintText == null))) {
            return;
        }
        int indexInStyle = start + rangeCountEscapeChar(start);
        int indexInText = start;
        int selectionIndex = -1;
        boolean nextCharIsText = false;
        final int styleLength = mFormatStyle.length();
        while (indexInStyle < styleLength) {
            char charInStyle = mFormatStyle.charAt(indexInStyle);
            if (!nextCharIsText && isMaskChar(charInStyle)) {
                if (indexInText >= editable.length()) {
                    if (mMode == MODE_MASK) {
                        if (mEmptyPlaceholder != 0) {
                            editable.insert(indexInText, String.valueOf(mEmptyPlaceholder));
                            editable.setSpan(
                                    new EmptyPlaceholderSpan(),
                                    indexInText,
                                    indexInText + 1,
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            indexInText += 1;
                            indexInStyle += 1;
                        } else {
                            break;
                        }
                    } else {
                        if (mHintText == null) {
                            break;
                        }
                        editable.insert(
                                indexInText, mHintText.subSequence(indexInText, indexInText + 1));
                        editable.setSpan(
                                new HintPlaceholderSpan(
                                        mHintColor == Color.TRANSPARENT
                                                ? getCurrentHintTextColor()
                                                : mHintColor),
                                indexInText,
                                indexInText + 1,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        indexInText += 1;
                        indexInStyle += 1;
                    }
                } else if (isMismatchMask(charInStyle, editable.charAt(indexInText))) {
                    editable.delete(indexInText, indexInText + 1);
                } else {
                    indexInText += 1;
                    indexInStyle += 1;
                }
            } else if (!nextCharIsText && charInStyle == ESCAPE_CHAR) {
                nextCharIsText = true;
                indexInStyle += 1;
            } else {
                editable.insert(indexInText, String.valueOf(charInStyle));
                editable.setSpan(
                        new PlaceholderSpan(),
                        indexInText,
                        indexInText + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                nextCharIsText = false;
                indexInText += 1;
                indexInStyle += 1;
                if (selectionIndex == -1 || indexInText == selectionIndex + 1) {
                    selectionIndex = indexInText;
                }
            }
        }
        if (deletion && start == 0 && selectionIndex != -1) {
            editable.setSpan(
                    SELECTION_SPAN, selectionIndex, selectionIndex, Spanned.SPAN_MARK_MARK);
        }
    }

    private int clearPlaceholders(Editable editable, int start) {
        IPlaceholderSpan[] spans;
        boolean sorted;
        if (start > 0) {
            sorted = true;
            mComparator.mEditable = editable;
            IPlaceholderSpan[] left;
            if (mMode < MODE_MASK) {
                int i;
                for (i = start; i > 0; i--) {
                    char holder = findPlaceholder(i);
                    if (holder == 0) {
                        break;
                    }
                }
                start = i;
                left = EMPTY_SPANS;
            } else {
                left = editable.getSpans(0, start, IPlaceholderSpan.class);
                Arrays.sort(left, mComparator);
            }
            IPlaceholderSpan[] right;
            if (start >= editable.length()) {
                right = EMPTY_SPANS;
            } else {
                right = editable.getSpans(start, editable.length(), IPlaceholderSpan.class);
                Arrays.sort(right, mComparator);
            }
            mComparator.mEditable = null;
            if (left.length == 0) {
                spans = right;
            } else if (left.length == start) {
                start = 0;
                spans = new IPlaceholderSpan[left.length + right.length];
                System.arraycopy(left, 0, spans, 0, left.length);
                System.arraycopy(right, 0, spans, left.length, right.length);
            } else {
                int last = start - 1;
                int index;
                for (index = left.length - 1; index >= 0; index--) {
                    int spanStart = editable.getSpanStart(left[index]);
                    if (last != spanStart) {
                        last += 1;
                        index += 1;
                        break;
                    }
                    last = spanStart - 1;
                }
                start = last;
                int leftLength = left.length - index;
                if (leftLength == 0) {
                    spans = right;
                } else {
                    spans = new IPlaceholderSpan[leftLength + right.length];
                    System.arraycopy(left, index, spans, 0, leftLength);
                    System.arraycopy(right, 0, spans, leftLength, right.length);
                }
            }
        } else {
            sorted = false;
            spans = editable.getSpans(0, editable.length(), IPlaceholderSpan.class);
        }
        if (spans.length == editable.length() - start) {
            editable.delete(start, editable.length());
            if (start == 0 && isNeedClearText()) {
                return -1;
            }
        } else if (spans.length > 0) {
            clearNonEmptySpans(editable, spans, sorted);
        }
        return start;
    }

    private boolean isNeedClearText() {
        return (mMode == MODE_MASK && (mShowHintWhileEmpty || mEmptyPlaceholder == 0))
                || (mMode == MODE_HINT && (mShowHintWhileEmpty || mHintText == null));
    }

    private int rangeCountEscapeChar(int end) {
        if (mEscapeIndexes == null) {
            return 0;
        }
        int count = 0;
        for (int escapeIndex : mEscapeIndexes) {
            if (escapeIndex < end) {
                count += 1;
            } else {
                break;
            }
        }
        return count;
    }

    private void clearNonEmptySpans(Editable editable, IPlaceholderSpan[] spans, boolean sorted) {
        if (!sorted) {
            mComparator.mEditable = editable;
            Arrays.sort(spans, mComparator);
            mComparator.mEditable = null;
        }
        IPlaceholderSpan last = spans[0], current = spans[0];
        int lastStart = editable.getSpanStart(last);
        for (int i = 1; i < spans.length; i++) {
            int spanStart = editable.getSpanStart(spans[i]);
            if (lastStart + 1 == spanStart) {
                current = spans[i];
            } else {
                editable.delete(editable.getSpanStart(last), editable.getSpanEnd(current));
                last = current = spans[i];
                spanStart = editable.getSpanStart(last);
            }
            lastStart = spanStart;
        }
        editable.delete(editable.getSpanStart(last), editable.getSpanEnd(current));
    }

    private boolean isMismatchMask(char mask, char value) {
        return mask != CHARACTER_MASK
                && (mask != LETTER_MASK || !Character.isLetter(value))
                && (mask != DIGIT_MASK || !Character.isDigit(value))
                && (mask != DIGIT_OR_LETTER_MASK
                        || (!Character.isDigit(value) && !Character.isLetter(value)));
    }

    private boolean isMaskChar(char mask) {
        return mask == DIGIT_MASK
                || mask == LETTER_MASK
                || mask == DIGIT_OR_LETTER_MASK
                || mask == CHARACTER_MASK;
    }

    @Override
    public Parcelable onSaveInstanceState() {
        int start = getSelectionStart();
        int end = getSelectionEnd();
        SavedState savedState = new SavedState(super.onSaveInstanceState());
        savedState.mMode = mMode;
        savedState.mPlaceholder = mPlaceholder;
        savedState.mEmptyPlaceholder = mEmptyPlaceholder;
        savedState.mMark = mMark;
        savedState.mPlaceholders = mPlaceholders;
        savedState.mHintText = mHintText;
        savedState.mFormatStyle = mFormatStyle;
        savedState.mShowHintWhileEmpty = mShowHintWhileEmpty;
        savedState.mHintColor = mHintColor;
        savedState.mSelectionStart = start;
        savedState.mSelectionEnd = end;
        savedState.mRealText = getRealText(true);
        return savedState;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState savedState = (SavedState) state;
        mMode = savedState.mMode;
        mPlaceholder = savedState.mPlaceholder;
        mEmptyPlaceholder = savedState.mEmptyPlaceholder;
        mMark = savedState.mMark;
        mPlaceholders = savedState.mPlaceholders;
        mHintText = savedState.mHintText;
        mFormatStyle = savedState.mFormatStyle;
        mShowHintWhileEmpty = savedState.mShowHintWhileEmpty;
        mHintColor = savedState.mHintColor;
        if (mMode == MODE_SIMPLE) {
            parseSimplePlaceholders();
        } else if (mMode == MODE_COMPLEX) {
            parseComplexPlaceholders();
        }
        if (savedState.mRealText != null) {
            mRestoring = true;
            super.onRestoreInstanceState(savedState.getSuperState());
            mRestoring = false;
            mFilterRestoreTextChangeEvent = true;
            setText(savedState.mRealText);
            mFilterRestoreTextChangeEvent = false;
            Editable text = getText();
            Selection.setSelection(
                    text,
                    Math.min(savedState.mSelectionStart, text.length()),
                    Math.min(savedState.mSelectionEnd, text.length()));
        } else {
            super.onRestoreInstanceState(savedState.getSuperState());
        }
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({MODE_NONE, MODE_SIMPLE, MODE_COMPLEX, MODE_MASK, MODE_HINT})
    @interface Mode {}

    public interface OnClearClickListener {
        boolean onClearClick(FormattedEditText editText, Drawable drawable);
    }

    private interface IPlaceholderSpan {}

    private interface IEmptyPlaceholderSpan extends IPlaceholderSpan {}

    private static class Placeholder {
        int index;
        char holder;
    }

    private static class PlaceholderSpan implements IPlaceholderSpan {}

    private static class EmptyPlaceholderSpan implements IEmptyPlaceholderSpan {}

    private static class HintPlaceholderSpan extends ForegroundColorSpan
            implements IEmptyPlaceholderSpan {
        HintPlaceholderSpan(int color) {
            super(color);
        }
    }

    public static class Config {
        private Integer mMode;
        private Integer mHintColor;
        private Character mMark;
        private Character mPlaceholder;
        private Character mEmptyPlaceholder;
        private Boolean mShowHintWhileEmpty;
        private String mHintText;
        private String mFormatStyle;

        private Config() {}

        public static Config create() {
            return new Config();
        }

        public Config mode(int mode) {
            mMode = mode;
            return this;
        }

        public Config hintText(String hintText) {
            mHintText = hintText;
            return this;
        }

        public Config mark(Character mark) {
            mMark = mark;
            return this;
        }

        public Config placeholder(Character placeholder) {
            mPlaceholder = placeholder;
            return this;
        }

        public Config showHintWhileEmpty(boolean showHintWhileEmpty) {
            mShowHintWhileEmpty = showHintWhileEmpty;
            return this;
        }

        public Config formatStyle(String formatStyle) {
            mFormatStyle = formatStyle;
            return this;
        }

        public Config hintColor(int hintColor) {
            mHintColor = hintColor;
            return this;
        }

        public Config emptyPlaceholder(Character emptyPlaceholder) {
            mEmptyPlaceholder = emptyPlaceholder;
            return this;
        }

        public void config(FormattedEditText editText) {
            editText.setConfig(this, false);
        }
    }

    private static class SavedState extends BaseSavedState {
        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
        private int mMode = MODE_NONE;
        private char mPlaceholder = 0;
        private char mEmptyPlaceholder = 0;
        private char mMark = 0;
        private String mPlaceholders;
        private String mHintText;
        private String mFormatStyle;
        private String mRealText;
        private boolean mShowHintWhileEmpty = false;
        private int mHintColor = Color.TRANSPARENT;
        private int mSelectionStart;
        private int mSelectionEnd;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            mMode = in.readInt();
            mPlaceholder = (char) in.readInt();
            mEmptyPlaceholder = (char) in.readInt();
            mMark = (char) in.readInt();
            mRealText = in.readString();
            mPlaceholders = in.readString();
            mHintText = in.readString();
            mFormatStyle = in.readString();
            mShowHintWhileEmpty = in.readInt() != 0;
            mHintColor = in.readInt();
            mSelectionStart = in.readInt();
            mSelectionEnd = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(mMode);
            out.writeInt(mPlaceholder);
            out.writeInt(mEmptyPlaceholder);
            out.writeInt(mMark);
            out.writeString(mRealText);
            out.writeString(mPlaceholders);
            out.writeString(mHintText);
            out.writeString(mFormatStyle);
            out.writeInt(mShowHintWhileEmpty ? 1 : 0);
            out.writeInt(mHintColor);
            out.writeInt(mSelectionStart);
            out.writeInt(mSelectionEnd);
        }
    }

    private static class PlaceholderComparator implements Comparator<IPlaceholderSpan> {
        private Editable mEditable;

        @Override
        public int compare(IPlaceholderSpan o1, IPlaceholderSpan o2) {
            int x = mEditable.getSpanStart(o1);
            int y = mEditable.getSpanStart(o2);
            return (x < y) ? -1 : ((x == y) ? 0 : 1);
        }
    }

    private class PlaceholderFilter implements InputFilter {
        private StringBuilder mFilterBuilder = new StringBuilder();

        @Override
        public CharSequence filter(
                CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            if (mRestoring) {
                return null;
            }
            if (mMode == MODE_SIMPLE || mMode == MODE_COMPLEX) {
                if (mPlaceholders == null || mIsFormatted || source.length() == 0) {
                    return null;
                }
                mFilterBuilder.setLength(0);
                final int len = source.length();
                for (int i = 0; i < len; i++) {
                    char sub = source.charAt(i);
                    if (mPlaceholders.indexOf(sub) == -1) {
                        mFilterBuilder.append(sub);
                    }
                }
                return mFilterBuilder;
            }
            return null;
        }
    }

    private class LengthFilterDelegate implements InputFilter {
        private InputFilter mFilter;

        private LengthFilterDelegate(InputFilter filter) {
            mFilter = filter;
        }

        @Override
        public CharSequence filter(
                CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            if (mRestoring) {
                return null;
            }
            if (!mIsFormatted && (mMode >= MODE_MASK)) {
                IEmptyPlaceholderSpan[] spans =
                        dest.getSpans(0, dest.length(), IEmptyPlaceholderSpan.class);
                if (spans.length == 0) {
                    return mFilter.filter(source, start, end, dest, dstart, dend);
                }
                return null;
            }
            return mFilter.filter(source, start, end, dest, dstart, dend);
        }
    }

    private class FormattedTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            if (mRestoring) {
                return;
            }
            sendBeforeTextChanged(s, start, count, after);
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (mRestoring) {
                return;
            }
            sendOnTextChanged(s, start, before, count);
            if (mMode == MODE_NONE || (mMode < MODE_MASK) && (mHolders == null)) {
                return;
            }
            if (!mIsFormatted && s instanceof Editable) {
                if (count == 0) {
                    formatTextWhenDelete((Editable) s, start, before);
                } else {
                    formatTextWhenAppend((Editable) s, start, before, count);
                }
            }
        }

        @Override
        public void afterTextChanged(Editable s) {
            if (mRestoring) {
                return;
            }
            sendAfterTextChanged(s);
        }
    }
}
