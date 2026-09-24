package com.webapp.crazyshit;

import android.view.View;
import android.view.ViewParent;

/** Small touch helper shared by gesture code that needs to pause nested pager interception. */
final class ViewParentCompat {
    private ViewParentCompat() {
    }

    static void disallow(View view, boolean disallow) {
        if (view == null) return;
        ViewParent parent = view.getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(disallow);
    }
}
