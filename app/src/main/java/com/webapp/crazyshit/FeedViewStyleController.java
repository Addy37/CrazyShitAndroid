package com.webapp.crazyshit;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;

/** Central 2.6+ view-style controller shared by Home and native collection feeds. */
final class FeedViewStyleController {
    private static final String HOME_PREF = "native_view_home";
    private static final String COLLECTION_PREF = "native_view_collection";
    private static final String[] LABELS = {"Cards", "List", "Grid", "Posters"};

    private static final Map<NativeMainActivity, ViewPager2.OnPageChangeCallback> MAIN_CALLBACKS =
            new WeakHashMap<>();
    private static final Map<NativeFeedBrowserActivity, Boolean> BROWSER_ATTACHED =
            new WeakHashMap<>();

    private FeedViewStyleController() {
    }

    static void prepareVisualRefresh(android.content.Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("app_prefs", 0);
        if (prefs.getBoolean("visual_refresh_2_11_1", false)) return;
        // Older lifecycle code saved List even when the user had never selected it.
        // Apply the approved card layout once; subsequent user choices remain untouched.
        prefs.edit().putInt(HOME_PREF, NativeFeedAdapter.VIEW_CARDS)
                .putInt(COLLECTION_PREF, NativeFeedAdapter.VIEW_CARDS)
                .putBoolean("visual_refresh_2_11_1", true).apply();
    }

    static void attachMain(NativeMainActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        ViewPager2 pager = fieldValue(activity, "primaryPager", ViewPager2.class);
        if (pager == null) return;

        if (!MAIN_CALLBACKS.containsKey(activity)) {
            ViewPager2.OnPageChangeCallback callback = new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    if (position == MainPagerAdapter.PAGE_HOME) {
                        applySavedMainLayout(activity);
                        updateMainLabel(activity);
                    }
                }
            };
            MAIN_CALLBACKS.put(activity, callback);
            pager.registerOnPageChangeCallback(callback);
        }

        pager.post(() -> {
            if (activity.isFinishing()) return;
            if (pager.getCurrentItem() == MainPagerAdapter.PAGE_HOME) {
                applySavedMainLayout(activity);
                updateMainLabel(activity);
            }
        });
    }

    static void detachMain(NativeMainActivity activity) {
        if (activity == null) return;
        ViewPager2.OnPageChangeCallback callback = MAIN_CALLBACKS.remove(activity);
        ViewPager2 pager = fieldValue(activity, "primaryPager", ViewPager2.class);
        if (pager != null && callback != null) {
            try {
                pager.unregisterOnPageChangeCallback(callback);
            } catch (Exception ignored) {
            }
        }
    }

    static void showMain(NativeMainActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        ViewPager2 pager = fieldValue(activity, "primaryPager", ViewPager2.class);
        if (pager == null || pager.getCurrentItem() != MainPagerAdapter.PAGE_HOME) {
            Toast.makeText(activity, "Open Home to change its view style.", Toast.LENGTH_SHORT).show();
            return;
        }

        int selected = safeMode(activity.getSharedPreferences("app_prefs", 0)
                .getInt(HOME_PREF, NativeFeedAdapter.VIEW_CARDS));
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("View style")
                .setSingleChoiceItems(LABELS, selected, null)
                .setNegativeButton("Close", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getListView().setOnItemClickListener((parent, view, position, id) -> {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            applyMainMode(activity, safeMode(position));
            dialog.dismiss();
        }));
        dialog.show();
    }

    static void attachBrowser(NativeFeedBrowserActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        applySavedBrowserLayout(activity);

        View root = activity.findViewById(android.R.id.content);
        View options = findByDescription(root, "Feed options");
        if (options == null) return;
        BROWSER_ATTACHED.put(activity, Boolean.TRUE);
        options.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            PopupMenu menu = new PopupMenu(activity, v);
            menu.getMenu().add(0, 1, 0, "View style");
            menu.getMenu().add(0, 2, 1, "Open website");
            menu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) {
                    showBrowser(activity);
                    return true;
                }
                if (item.getItemId() == 2) {
                    String baseUrl = stringField(activity, "baseUrl");
                    Intent intent = new Intent(activity, WebFallbackActivity.class);
                    intent.putExtra(
                            WebFallbackActivity.EXTRA_URL,
                            baseUrl.isEmpty() ? CrazyShitRepository.HOME : baseUrl
                    );
                    activity.startActivity(intent);
                    return true;
                }
                return false;
            });
            menu.show();
        });
    }

    static void detachBrowser(NativeFeedBrowserActivity activity) {
        BROWSER_ATTACHED.remove(activity);
    }

    private static void showBrowser(NativeFeedBrowserActivity activity) {
        int selected = safeMode(activity.getSharedPreferences("app_prefs", 0)
                .getInt(COLLECTION_PREF, NativeFeedAdapter.VIEW_CARDS));
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("View style")
                .setSingleChoiceItems(LABELS, selected, null)
                .setNegativeButton("Close", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getListView().setOnItemClickListener((parent, view, position, id) -> {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            applyBrowserMode(activity, safeMode(position));
            dialog.dismiss();
        }));
        dialog.show();
    }

    private static void applySavedMainLayout(NativeMainActivity activity) {
        int mode = safeMode(activity.getSharedPreferences("app_prefs", 0)
                .getInt(HOME_PREF, NativeFeedAdapter.VIEW_CARDS));
        applyMainMode(activity, mode);
    }

    private static void applyMainMode(NativeMainActivity activity, int mode) {
        Object pagerAdapter = rawField(activity, "primaryPagerAdapter");
        if (pagerAdapter == null) return;
        Object rawPages = rawField(pagerAdapter, "pages");
        if (!(rawPages instanceof Object[])) return;
        Object[] pages = (Object[]) rawPages;
        if (pages.length <= MainPagerAdapter.PAGE_HOME || pages[MainPagerAdapter.PAGE_HOME] == null) return;

        Object page = pages[MainPagerAdapter.PAGE_HOME];
        NativeFeedAdapter adapter = fieldValue(page, "feedAdapter", NativeFeedAdapter.class);
        RecyclerView recycler = fieldValue(page, "recycler", RecyclerView.class);
        if (adapter == null || recycler == null) return;

        activity.getSharedPreferences("app_prefs", 0)
                .edit()
                .putInt(HOME_PREF, mode)
                .apply();
        setIntField(page, "viewMode", mode);
        applyLayout(recycler, adapter, mode);
        updateMainLabel(activity);
    }

    private static void applySavedBrowserLayout(NativeFeedBrowserActivity activity) {
        int mode = safeMode(activity.getSharedPreferences("app_prefs", 0)
                .getInt(COLLECTION_PREF, NativeFeedAdapter.VIEW_CARDS));
        applyBrowserMode(activity, mode);
    }

    private static void applyBrowserMode(NativeFeedBrowserActivity activity, int mode) {
        NativeFeedAdapter adapter = fieldValue(activity, "adapter", NativeFeedAdapter.class);
        RecyclerView recycler = fieldValue(activity, "recycler", RecyclerView.class);
        if (adapter == null || recycler == null) return;

        activity.getSharedPreferences("app_prefs", 0)
                .edit()
                .putInt(COLLECTION_PREF, mode)
                .apply();
        applyLayout(recycler, adapter, mode);
    }

    private static void applyLayout(RecyclerView recycler, NativeFeedAdapter adapter, int mode) {
        RecyclerView.LayoutManager old = recycler.getLayoutManager();
        int position = 0;
        int offset = 0;
        if (old instanceof LinearLayoutManager) {
            LinearLayoutManager linear = (LinearLayoutManager) old;
            position = Math.max(0, linear.findFirstVisibleItemPosition());
            View anchor = linear.findViewByPosition(position);
            if (anchor != null) offset = anchor.getTop() - recycler.getPaddingTop();
        }

        adapter.setViewMode(mode);
        LinearLayoutManager next;
        if (mode == NativeFeedAdapter.VIEW_GRID || mode == NativeFeedAdapter.VIEW_POSTERS) {
            Configuration config = recycler.getResources().getConfiguration();
            boolean landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE;
            int columns = landscape && config.screenWidthDp >= 900 ? 3 : 2;
            GridLayoutManager grid = new GridLayoutManager(recycler.getContext(), columns);
            grid.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int adapterPosition) {
                    return adapter.isSectionAt(adapterPosition) ? columns : 1;
                }
            });
            next = grid;
        } else {
            next = new LinearLayoutManager(recycler.getContext());
        }
        recycler.setLayoutManager(next);
        if (adapter.getItemCount() > 0) {
            int safePosition = Math.min(position, adapter.getItemCount() - 1);
            next.scrollToPositionWithOffset(safePosition, offset);
            recycler.post(() -> {
                RecyclerView.LayoutManager manager = recycler.getLayoutManager();
                if (!(manager instanceof LinearLayoutManager)) return;
                LinearLayoutManager lm = (LinearLayoutManager) manager;
                adapter.preloadVisible(
                        lm.findFirstVisibleItemPosition(),
                        lm.findLastVisibleItemPosition()
                );
            });
        }
    }

    private static void updateMainLabel(NativeMainActivity activity) {
        ViewPager2 pager = fieldValue(activity, "primaryPager", ViewPager2.class);
        if (pager == null || pager.getCurrentItem() != MainPagerAdapter.PAGE_HOME) return;
        TextView title = fieldValue(activity, "headerTitle", TextView.class);
        TextView subtitle = fieldValue(activity, "headerSubtitle", TextView.class);
        if (title != null && !"Home".contentEquals(title.getText())) return;
        if (subtitle == null) return;
        subtitle.setText(R.string.zerochill_tagline);
    }

    static String label(int mode) {
        int safe = safeMode(mode);
        return LABELS[safe];
    }

    private static int safeMode(int mode) {
        if (mode < NativeFeedAdapter.VIEW_CARDS || mode > NativeFeedAdapter.VIEW_POSTERS) {
            return NativeFeedAdapter.VIEW_LIST;
        }
        return mode;
    }

    private static View findByDescription(View view, String description) {
        if (view == null) return null;
        CharSequence value = view.getContentDescription();
        if (value != null && description.contentEquals(value)) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findByDescription(group.getChildAt(i), description);
            if (found != null) return found;
        }
        return null;
    }

    private static String stringField(Object target, String name) {
        Object value = rawField(target, name);
        return value instanceof String ? (String) value : "";
    }

    private static Object rawField(Object target, String name) {
        if (target == null) return null;
        Field field = findField(target.getClass(), name);
        if (field == null) return null;
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static <T> T fieldValue(Object target, String name, Class<T> type) {
        Object value = rawField(target, name);
        return type.isInstance(value) ? type.cast(value) : null;
    }

    private static void setIntField(Object target, String name, int value) {
        Field field = findField(target.getClass(), name);
        if (field == null) return;
        try {
            field.setAccessible(true);
            field.setInt(target, value);
        } catch (Exception ignored) {
        }
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
