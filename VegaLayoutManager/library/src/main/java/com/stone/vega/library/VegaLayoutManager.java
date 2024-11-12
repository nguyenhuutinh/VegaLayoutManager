package com.stone.vega.library;

import android.graphics.Rect;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Custom LayoutManager: VegaLayoutManager
 * Created by xmuSistone on 2017/9/20.
 */
public class VegaLayoutManager extends RecyclerView.LayoutManager {

    private int scroll = 0;
    private SparseArray<Rect> locationRects = new SparseArray<>();
    private SparseBooleanArray attachedItems = new SparseBooleanArray();
    private ArrayMap<Integer, Integer> viewTypeHeightMap = new ArrayMap<>();

    private boolean needSnap = false;
    private int lastDy = 0;
    private int maxScroll = -1;
    private RecyclerView.Adapter<?> adapter;
    private RecyclerView.Recycler recycler;

    public VegaLayoutManager() {
        setAutoMeasureEnabled(true);
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public void onAdapterChanged(@Nullable RecyclerView.Adapter oldAdapter, @Nullable RecyclerView.Adapter newAdapter) {
        super.onAdapterChanged(oldAdapter, newAdapter);
        this.adapter = newAdapter;

    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        this.recycler = recycler;
        if (state.isPreLayout()) {
            return;
        }

        buildLocationRects();

        detachAndScrapAttachedViews(recycler);
        layoutItemsOnCreate(recycler);
    }

    private void buildLocationRects() {
        locationRects.clear();
        attachedItems.clear();

        int tempPosition = getPaddingTop();
        int itemCount = getItemCount();
        for (int i = 0; i < itemCount; i++) {
            int viewType = adapter.getItemViewType(i);
            int itemHeight;
            if (viewTypeHeightMap.containsKey(viewType)) {
                itemHeight = viewTypeHeightMap.get(viewType);
            } else {
                View itemView = recycler.getViewForPosition(i);
                addView(itemView);
                measureChildWithMargins(itemView, View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                itemHeight = getDecoratedMeasuredHeight(itemView);
                viewTypeHeightMap.put(viewType, itemHeight);
            }

            Rect rect = new Rect();
            rect.left = getPaddingLeft();
            rect.top = tempPosition;
            rect.right = getWidth() - getPaddingRight();
            rect.bottom = rect.top + itemHeight;
            locationRects.put(i, rect);
            attachedItems.put(i, false);
            tempPosition += itemHeight;
        }

        computeMaxScroll();
    }

    public int findFirstVisibleItemPosition() {
        Rect displayRect = new Rect(0, scroll, getWidth(), getHeight() + scroll);
        for (int i = 0; i < locationRects.size(); i++) {
            if (Rect.intersects(displayRect, locationRects.get(i)) && attachedItems.get(i)) {
                return i;
            }
        }
        return 0;
    }

    private void computeMaxScroll() {
        if (locationRects.size() == 0) {
            maxScroll = 0;
            return;
        }

        maxScroll = locationRects.get(locationRects.size() - 1).bottom - getHeight();
        maxScroll = Math.max(maxScroll, 0);
    }

    private void layoutItemsOnCreate(RecyclerView.Recycler recycler) {
        Rect displayRect = new Rect(0, scroll, getWidth(), getHeight() + scroll);
        for (int i = 0; i < getItemCount(); i++) {
            Rect thisRect = locationRects.get(i);
            if (Rect.intersects(displayRect, thisRect)) {
                View childView = recycler.getViewForPosition(i);
                addView(childView);
                measureChildWithMargins(childView, View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                layoutItem(childView, thisRect);
                attachedItems.put(i, true);
            }
        }
    }

    private void layoutItemsOnScroll() {
        Rect displayRect = new Rect(0, scroll, getWidth(), getHeight() + scroll);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            int position = getPosition(child);
            if (!Rect.intersects(displayRect, locationRects.get(position))) {
                removeAndRecycleView(child, recycler);
                attachedItems.put(position, false);
            } else {
                layoutItem(child, locationRects.get(position));
            }
        }

        for (int i = 0; i < getItemCount(); i++) {
            if (!attachedItems.get(i) && Rect.intersects(displayRect, locationRects.get(i))) {
                View childView = recycler.getViewForPosition(i);
                addView(childView);
                measureChildWithMargins(childView, 0, 0);
                layoutItem(childView, locationRects.get(i));
                attachedItems.put(i, true);
            }
        }
    }

    private void layoutItem(View child, Rect rect) {
        int layoutTop = rect.top - scroll;
        int layoutBottom = rect.bottom - scroll;
        layoutDecoratedWithMargins(child, rect.left, layoutTop, rect.right, layoutBottom);
    }

    @Override
    public boolean canScrollVertically() {
        return true;
    }

    @Override
    public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (getChildCount() == 0) {
            return 0;
        }

        int travel = dy;
        if (scroll + dy < 0) {
            travel = -scroll;
        } else if (scroll + dy > maxScroll) {
            travel = maxScroll - scroll;
        }

        scroll += travel;
        layoutItemsOnScroll();

        return travel;
    }

    @Override
    public void onAttachedToWindow(RecyclerView view) {
        super.onAttachedToWindow(view);
    }

    public int getSnapHeight() {
        if (!needSnap) {
            return 0;
        }
        needSnap = false;

        Rect displayRect = new Rect(0, scroll, getWidth(), getHeight() + scroll);
        int itemCount = getItemCount();
        for (int i = 0; i < itemCount; i++) {
            Rect itemRect = locationRects.get(i);
            if (displayRect.intersect(itemRect)) {

                if (lastDy > 0) {
                    // scroll变大，属于列表往下走，往下找下一个为snapView
                    if (i < itemCount - 1) {
                        Rect nextRect = locationRects.get(i + 1);
                        return nextRect.top - displayRect.top;
                    }
                }
                return itemRect.top - displayRect.top;
            }
        }
        return 0;
    }

    public View findSnapView() {
        if (getChildCount() > 0) {
            return getChildAt(0);
        }
        return null;
    }
}
