package com.chat.forum;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/** Full-screen board page. The board drawer lives inside ForumHomeFragment board mode. */
public class ForumBoardActivity extends AppCompatActivity {
    private static final int CONTAINER_ID = 0x00F0A001;
    private static final String EXTRA_CATEGORY_ID = "forum_board_category_id";
    private static final String EXTRA_CATEGORY_NAME = "forum_board_category_name";
    private static final String EXTRA_CATEGORY_DESCRIPTION = "forum_board_category_description";
    private static final String EXTRA_CATEGORY_LOGO = "forum_board_category_logo";

    public static Intent createIntent(Context context, long categoryId,
                                      @Nullable String name, @Nullable String description,
                                      @Nullable String logo) {
        return new Intent(context, ForumBoardActivity.class)
                .putExtra(EXTRA_CATEGORY_ID, categoryId)
                .putExtra(EXTRA_CATEGORY_NAME, name == null ? "" : name)
                .putExtra(EXTRA_CATEGORY_DESCRIPTION, description == null ? "" : description)
                .putExtra(EXTRA_CATEGORY_LOGO, logo == null ? "" : logo);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        long categoryId = getIntent().getLongExtra(EXTRA_CATEGORY_ID, 0L);
        if (categoryId <= 0L) {
            finish();
            return;
        }
        String name = getIntent().getStringExtra(EXTRA_CATEGORY_NAME);
        String description = getIntent().getStringExtra(EXTRA_CATEGORY_DESCRIPTION);
        String logo = getIntent().getStringExtra(EXTRA_CATEGORY_LOGO);

        FrameLayout container = new FrameLayout(this);
        container.setId(CONTAINER_ID);
        setContentView(container, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(container.getId(), ForumHomeFragment.newBoardInstance(
                            categoryId, name, description, logo))
                    .commit();
        }
    }

    @Override
    public void onBackPressed() {
        androidx.fragment.app.Fragment fragment = getSupportFragmentManager()
                .findFragmentById(CONTAINER_ID);
        if (fragment instanceof ForumHomeFragment
                && ((ForumHomeFragment) fragment).closeDrawerIfOpen()) {
            return;
        }
        super.onBackPressed();
    }
}
