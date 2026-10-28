package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.HoursGramStarGiftCatalog;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public class HoursGramStudioActivity extends BaseFragment {

    private static final int APP_BACKGROUND = Color.rgb(60, 62, 75);
    private static final int CARD_BACKGROUND = Color.rgb(46, 48, 60);
    private static final int ACCENT_COLOR = Color.rgb(137, 147, 216);
    private static final int SUBTITLE_COLOR = Color.rgb(177, 178, 183);
    private static final int MAIN_TEXT_COLOR = Color.WHITE;
    private static final int MAX_STARS = 999999999;
    private static final int MARKET_PAGE_SIZE = 12;
    private static final String PREFERENCES_NAME = "hoursgram_profile_cosmetics";
    private static final int[] GRANT_AMOUNTS = {100, 500, 1000};

    private SharedPreferences preferences;
    private LinearLayout contentLayout;
    private ScrollView scrollView;
    private View starsSectionView;
    private View giftMarketSectionView;
    private Typeface bodyTypeface;
    private Typeface headingTypeface;
    private long userId;
    private int localStars;
    private Set<String> ownedGiftIds = new HashSet<>();
    private final ArrayList<HoursGramStarGiftCatalog.Gift> starGifts = new ArrayList<>();
    private boolean isGiftCatalogLoading;
    private boolean hasGiftCatalogLoaded;
    private boolean hasGiftCatalogError;
    private int marketDisplayedCount = MARKET_PAGE_SIZE;

    public HoursGramStudioActivity() {
        super();
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        TLRPC.User self = getUserConfig().getCurrentUser();
        userId = self != null ? self.id : getUserConfig().getClientUserId();
        preferences = ApplicationLoader.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        loadCosmetics();
        loadStarGiftCatalog();
        return true;
    }

    private void loadCosmetics() {
        localStars = Math.max(0, preferences.getInt("stars_" + userId, 0));
        Set<String> storedGiftIds = preferences.getStringSet("gifts_" + userId, Collections.emptySet());
        ownedGiftIds = storedGiftIds == null ? new HashSet<>() : new HashSet<>(storedGiftIds);
    }

    private void saveCosmetics() {
        preferences.edit()
                .putInt("stars_" + userId, localStars)
                .putStringSet("gifts_" + userId, new HashSet<>(ownedGiftIds))
                .apply();
    }

    private void loadStarGiftCatalog() {
        if (isGiftCatalogLoading || hasGiftCatalogLoaded) return;
        isGiftCatalogLoading = true;
        ConnectionsManager.getInstance(currentAccount).sendRequest(new HoursGramStarGiftCatalog.GetStarGifts(), (response, error) -> {
            AndroidUtilities.runOnUIThread(() -> {
                isGiftCatalogLoading = false;
                hasGiftCatalogError = error != null || !(response instanceof HoursGramStarGiftCatalog.Response);
                if (!hasGiftCatalogError) {
                    HoursGramStarGiftCatalog.Response catalog = (HoursGramStarGiftCatalog.Response) response;
                    starGifts.clear();
                    starGifts.addAll(catalog.gifts);
                    Collections.sort(starGifts, new Comparator<HoursGramStarGiftCatalog.Gift>() {
                        @Override
                        public int compare(HoursGramStarGiftCatalog.Gift first, HoursGramStarGiftCatalog.Gift second) {
                            return Long.compare(first.stars, second.stars);
                        }
                    });
                    hasGiftCatalogLoaded = true;
                }
                if (contentLayout != null && getParentActivity() != null) {
                    renderContent();
                }
            });
        });
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString("HoursGramStudioTitle", R.string.HoursGramStudioTitle));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        bodyTypeface = AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_REGULAR);
        headingTypeface = AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM);

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(APP_BACKGROUND);
        scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(28));
        scrollView.addView(contentLayout, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        frameLayout.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = frameLayout;
        renderContent();
        return fragmentView;
    }

    private void renderContent() {
        if (contentLayout == null) return;
        int scrollY = scrollView.getScrollY();
        contentLayout.removeAllViews();
        addProfilePreview();
        addLocalNotice();
        addStarsWallet();
        addGiftMarket();
        scrollView.post(() -> scrollView.scrollTo(0, scrollY));
    }

    private void addProfilePreview() {
        addSectionHeading(contentLayout, R.string.HoursGramStudioPreview, R.string.HoursGramStudioSubtitle);
        TLRPC.User self = getUserConfig().getCurrentUser();
        String displayName = self == null ? "HorusGram" : TextUtils.join(" ", new String[] {
                self.first_name == null ? "" : self.first_name,
                self.last_name == null ? "" : self.last_name
        }).trim();
        if (displayName.isEmpty()) displayName = "HorusGram";
        String username = self == null ? "" : UserObject.getPublicUsername(self);
        String about = "";
        if (self != null) {
            TLRPC.UserFull userFull = getMessagesController().getUserFull(self.id);
            if (userFull != null) {
                about = userFull.about;
            }
        }

        LinearLayout profileCard = createCard(contentLayout);
        profileCard.setBackground(makeRoundedDrawable(CARD_BACKGROUND, AndroidUtilities.dp(24)));
        profileCard.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(20), AndroidUtilities.dp(16), AndroidUtilities.dp(16));

        LinearLayout identity = new LinearLayout(getParentActivity());
        identity.setOrientation(LinearLayout.VERTICAL);
        identity.setGravity(Gravity.CENTER_HORIZONTAL);
        profileCard.addView(identity, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        BackupImageView avatar = new BackupImageView(getParentActivity());
        avatar.setRoundRadius(AndroidUtilities.dp(48));
        avatar.setForUserOrChat(self, new AvatarDrawable(self));
        identity.addView(avatar, new LinearLayout.LayoutParams(AndroidUtilities.dp(96), AndroidUtilities.dp(96)));

        TextView name = createText(displayName, 21, MAIN_TEXT_COLOR, true);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = AndroidUtilities.dp(12);
        identity.addView(name, nameParams);

        String usernameText = TextUtils.isEmpty(username) ? "" : "@" + username;
        TextView accountLine = createText(TextUtils.isEmpty(usernameText)
                ? LocaleController.getString("HoursGramStudioLocalProfile", R.string.HoursGramStudioLocalProfile)
                : usernameText, 13, ACCENT_COLOR, false);
        accountLine.setGravity(Gravity.CENTER);
        accountLine.setPadding(0, AndroidUtilities.dp(4), 0, 0);
        identity.addView(accountLine, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView balanceChip = createText("★  " + localStars + "  ·  " + LocaleController.getString("HoursGramStudioLocalOnlyShort", R.string.HoursGramStudioLocalOnlyShort), 12, ACCENT_COLOR, true);
        balanceChip.setGravity(Gravity.CENTER);
        balanceChip.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(7), AndroidUtilities.dp(12), AndroidUtilities.dp(7));
        balanceChip.setBackground(makeRoundedDrawable(CARD_BACKGROUND, AndroidUtilities.dp(20)));
        LinearLayout.LayoutParams balanceParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        balanceParams.topMargin = AndroidUtilities.dp(12);
        identity.addView(balanceChip, balanceParams);

        LinearLayout actionRow = new LinearLayout(getParentActivity());
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionRowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(42));
        actionRowParams.topMargin = AndroidUtilities.dp(18);
        profileCard.addView(actionRow, actionRowParams);

        TextView editAction = createButton(LocaleController.getString("EditName", R.string.EditName), false);
        editAction.setOnClickListener(v -> presentFragment(new ChangeNameActivity(null)));
        actionRow.addView(editAction, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        TextView starsAction = createButton(LocaleController.getString("HoursGramStudioActionStars", R.string.HoursGramStudioActionStars), false);
        starsAction.setOnClickListener(v -> scrollToSection(starsSectionView));
        LinearLayout.LayoutParams starsActionParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        starsActionParams.leftMargin = AndroidUtilities.dp(8);
        actionRow.addView(starsAction, starsActionParams);
        TextView giftsAction = createButton(LocaleController.getString("HoursGramStudioActionGifts", R.string.HoursGramStudioActionGifts), false);
        giftsAction.setOnClickListener(v -> scrollToSection(giftMarketSectionView));
        LinearLayout.LayoutParams giftsActionParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        giftsActionParams.leftMargin = AndroidUtilities.dp(8);
        actionRow.addView(giftsAction, giftsActionParams);

        LinearLayout infoCard = createCard(contentLayout);
        infoCard.setBackground(makeRoundedDrawable(CARD_BACKGROUND, AndroidUtilities.dp(20)));
        LinearLayout.LayoutParams infoCardParams = (LinearLayout.LayoutParams) infoCard.getLayoutParams();
        infoCardParams.topMargin = AndroidUtilities.dp(10);
        infoCard.setLayoutParams(infoCardParams);
        addProfileInfoRow(infoCard, String.valueOf(self == null ? 0 : self.id), LocaleController.getString("HoursGramStudioId", R.string.HoursGramStudioId));
        addProfileInfoRow(infoCard, TextUtils.isEmpty(about)
                ? LocaleController.getString("HoursGramStudioNoBio", R.string.HoursGramStudioNoBio)
                : about, LocaleController.getString("HoursGramStudioBio", R.string.HoursGramStudioBio));
        addProfileInfoRow(infoCard, TextUtils.isEmpty(usernameText)
                ? LocaleController.getString("HoursGramStudioNoUsername", R.string.HoursGramStudioNoUsername)
                : usernameText, LocaleController.getString("HoursGramStudioUsername", R.string.HoursGramStudioUsername));

        LinearLayout collectionCard = createCard(contentLayout);
        collectionCard.setBackground(makeRoundedDrawable(CARD_BACKGROUND, AndroidUtilities.dp(20)));
        LinearLayout.LayoutParams collectionParams = (LinearLayout.LayoutParams) collectionCard.getLayoutParams();
        collectionParams.topMargin = AndroidUtilities.dp(10);
        collectionCard.setLayoutParams(collectionParams);
        LinearLayout collectionHeader = new LinearLayout(getParentActivity());
        collectionHeader.setGravity(Gravity.CENTER_VERTICAL);
        collectionCard.addView(collectionHeader, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView collectionName = createText(LocaleController.getString("HoursGramStudioCollection", R.string.HoursGramStudioCollection), 15, MAIN_TEXT_COLOR, true);
        collectionHeader.addView(collectionName, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView collectionCount = createText(String.valueOf(ownedGiftIds.size()), 13, ACCENT_COLOR, true);
        collectionHeader.addView(collectionCount, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout giftRow = new LinearLayout(getParentActivity());
        giftRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams giftRowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        giftRowParams.topMargin = AndroidUtilities.dp(12);
        collectionCard.addView(giftRow, giftRowParams);
        boolean hasGifts = false;
        for (HoursGramStarGiftCatalog.Gift gift : starGifts) {
            if (!ownedGiftIds.contains(String.valueOf(gift.id))) continue;
            hasGifts = true;
            RLottieImageView image = createGiftAnimation(gift.sticker, 44);
            image.setBackground(makeRoundedDrawable(APP_BACKGROUND, AndroidUtilities.dp(14)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(AndroidUtilities.dp(52), AndroidUtilities.dp(52));
            params.rightMargin = AndroidUtilities.dp(8);
            giftRow.addView(image, params);
        }
        if (!hasGifts) {
            TextView empty = createText(LocaleController.getString("HoursGramStudioEmptyCollection", R.string.HoursGramStudioEmptyCollection), 13, SUBTITLE_COLOR, false);
            giftRow.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        TextView openMarket = createButton(LocaleController.getString("HoursGramStudioActionGifts", R.string.HoursGramStudioActionGifts), false);
        openMarket.setOnClickListener(v -> scrollToSection(giftMarketSectionView));
        LinearLayout.LayoutParams openMarketParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(40));
        openMarketParams.topMargin = AndroidUtilities.dp(12);
        collectionCard.addView(openMarket, openMarketParams);
        addBottomSpace(contentLayout, 16);
    }

    private void addProfileInfoRow(LinearLayout parent, String value, String label) {
        LinearLayout row = new LinearLayout(getParentActivity());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, AndroidUtilities.dp(2), 0, AndroidUtilities.dp(10));
        parent.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView valueView = createText(value, 14, MAIN_TEXT_COLOR, false);
        valueView.setMaxLines(3);
        valueView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(valueView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView labelView = createText(label, 12, SUBTITLE_COLOR, false);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = AndroidUtilities.dp(2);
        row.addView(labelView, labelParams);
    }

    private void scrollToSection(View target) {
        if (target != null && scrollView != null) {
            scrollView.smoothScrollTo(0, target.getTop());
        }
    }

    private void addLocalNotice() {
        LinearLayout notice = createCard(contentLayout);
        notice.setBackground(makeRoundedDrawable(CARD_BACKGROUND, AndroidUtilities.dp(14)));
        TextView text = createText(LocaleController.getString("HoursGramStudioLocalOnly", R.string.HoursGramStudioLocalOnly), 13, SUBTITLE_COLOR, false);
        text.setLineSpacing(AndroidUtilities.dp(2), 1f);
        notice.addView(text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addBottomSpace(contentLayout, 20);
    }

    private void addStarsWallet() {
        starsSectionView = addSectionHeading(contentLayout, R.string.HoursGramStudioLocalStars, 0);
        LinearLayout card = createCard(contentLayout);
        card.setBackground(makeRoundedDrawable(CARD_BACKGROUND, AndroidUtilities.dp(16)));
        TextView balance = createText(String.valueOf(localStars) + " ★", 28, ACCENT_COLOR, true);
        balance.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(balance, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        EditText input = new EditText(getParentActivity());
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(String.valueOf(localStars));
        input.setTextColor(MAIN_TEXT_COLOR);
        input.setHintTextColor(SUBTITLE_COLOR);
        input.setTextSize(15);
        input.setTypeface(bodyTypeface);
        input.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
        input.setBackground(makeStrokeDrawable(APP_BACKGROUND, ACCENT_COLOR, AndroidUtilities.dp(12)));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(48));
        inputParams.topMargin = AndroidUtilities.dp(12);
        card.addView(input, inputParams);

        TextView setBalance = createButton(LocaleController.getString("HoursGramStudioSetBalance", R.string.HoursGramStudioSetBalance), false);
        setBalance.setOnClickListener(v -> {
            String value = input.getText().toString().trim();
            if (value.isEmpty()) return;
            try {
                localStars = Math.min(MAX_STARS, Math.max(0, Integer.parseInt(value)));
                saveCosmetics();
                Toast.makeText(getParentActivity(), LocaleController.getString("HoursGramStudioBalanceUpdated", R.string.HoursGramStudioBalanceUpdated), Toast.LENGTH_SHORT).show();
                renderContent();
            } catch (NumberFormatException ignored) {
                input.setError(LocaleController.getString("HoursGramStudioSetBalance", R.string.HoursGramStudioSetBalance));
            }
        });
        LinearLayout.LayoutParams setParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(44));
        setParams.topMargin = AndroidUtilities.dp(8);
        card.addView(setBalance, setParams);

        LinearLayout grantRow = new LinearLayout(getParentActivity());
        grantRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams grantRowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(42));
        grantRowParams.topMargin = AndroidUtilities.dp(8);
        card.addView(grantRow, grantRowParams);
        for (int i = 0; i < GRANT_AMOUNTS.length; i++) {
            final int amount = GRANT_AMOUNTS[i];
            TextView grant = createButton(LocaleController.formatString("HoursGramStudioGrantStars", R.string.HoursGramStudioGrantStars, amount), false);
            grant.setTextSize(11);
            grant.setOnClickListener(v -> {
                localStars = Math.min(MAX_STARS, localStars + amount);
                saveCosmetics();
                renderContent();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            params.rightMargin = i == GRANT_AMOUNTS.length - 1 ? 0 : AndroidUtilities.dp(6);
            grantRow.addView(grant, params);
        }
        addBottomSpace(contentLayout, 20);
    }

    private void addGiftMarket() {
        giftMarketSectionView = addSectionHeading(contentLayout, R.string.HoursGramStudioMarket, R.string.HoursGramStudioMarketHint);
        if (isGiftCatalogLoading) {
            addCatalogMessage(R.string.HoursGramStudioMarketLoading, false);
            return;
        }
        if (hasGiftCatalogError) {
            addCatalogMessage(R.string.HoursGramStudioMarketError, true);
            return;
        }

        ArrayList<HoursGramStarGiftCatalog.Gift> availableGifts = new ArrayList<>();
        for (HoursGramStarGiftCatalog.Gift gift : starGifts) {
            if (!gift.isSoldOut && (gift.availabilityResale > 0 || gift.isLimited)) {
                availableGifts.add(gift);
            }
        }
        if (availableGifts.isEmpty()) {
            addCatalogMessage(R.string.HoursGramStudioMarketEmpty, false);
            return;
        }

        int visibleCount = Math.min(marketDisplayedCount, availableGifts.size());
        for (int start = 0; start < visibleCount; start += 2) {
            LinearLayout row = new LinearLayout(getParentActivity());
            row.setOrientation(LinearLayout.HORIZONTAL);
            contentLayout.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(createGiftCard(availableGifts.get(start)), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            int next = start + 1;
            if (next < visibleCount) {
                View space = new View(getParentActivity());
                row.addView(space, new LinearLayout.LayoutParams(AndroidUtilities.dp(8), 1));
                row.addView(createGiftCard(availableGifts.get(next)), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }
            addBottomSpace(contentLayout, 8);
        }
        if (visibleCount < availableGifts.size()) {
            TextView more = createButton(LocaleController.getString("HoursGramStudioShowMore", R.string.HoursGramStudioShowMore), false);
            more.setOnClickListener(v -> {
                marketDisplayedCount = Math.min(availableGifts.size(), marketDisplayedCount + MARKET_PAGE_SIZE);
                renderContent();
            });
            contentLayout.addView(more, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(44)));
        }
    }

    private void addCatalogMessage(int textRes, boolean canRetry) {
        TextView message = createText(LocaleController.getString(textRes), 13, SUBTITLE_COLOR, false);
        message.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16));
        message.setBackground(makeRoundedDrawable(CARD_BACKGROUND, AndroidUtilities.dp(16)));
        contentLayout.addView(message, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (canRetry) {
            TextView retry = createButton(LocaleController.getString("HoursGramStudioRetry", R.string.HoursGramStudioRetry), false);
            retry.setOnClickListener(v -> {
                hasGiftCatalogError = false;
                loadStarGiftCatalog();
                renderContent();
            });
            LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(42));
            retryParams.topMargin = AndroidUtilities.dp(8);
            contentLayout.addView(retry, retryParams);
        }
    }

    private View createGiftCard(HoursGramStarGiftCatalog.Gift gift) {
        LinearLayout card = new LinearLayout(getParentActivity());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(makeRoundedDrawable(CARD_BACKGROUND, AndroidUtilities.dp(16)));
        card.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(10));

        RLottieImageView giftImage = createGiftAnimation(gift.sticker, 76);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(84));
        card.addView(giftImage, imageParams);

        String giftName = TextUtils.isEmpty(gift.title)
                ? LocaleController.getString("HoursGramStudioGift", R.string.HoursGramStudioGift)
                : gift.title;
        TextView title = createText(giftName, 13, MAIN_TEXT_COLOR, true);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(30));
        titleParams.topMargin = AndroidUtilities.dp(4);
        card.addView(title, titleParams);

        TextView price = createText(gift.stars + " ★", 12, SUBTITLE_COLOR, false);
        price.setGravity(Gravity.CENTER);
        card.addView(price, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(26)));

        String giftId = String.valueOf(gift.id);
        boolean isOwned = ownedGiftIds.contains(giftId);
        boolean canAfford = localStars >= gift.stars;
        TextView action = createButton(isOwned
                ? LocaleController.getString("HoursGramStudioOwned", R.string.HoursGramStudioOwned)
                : canAfford
                    ? LocaleController.formatString("HoursGramStudioBuyGift", R.string.HoursGramStudioBuyGift, gift.stars)
                    : LocaleController.getString("HoursGramStudioNeedStars", R.string.HoursGramStudioNeedStars), isOwned || canAfford);
        action.setTextSize(10);
        action.setEnabled(!isOwned && canAfford);
        action.setAlpha(action.isEnabled() ? 1f : .65f);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(40));
        actionParams.topMargin = AndroidUtilities.dp(6);
        card.addView(action, actionParams);
        if (!isOwned && canAfford) {
            action.setOnClickListener(v -> {
                localStars -= (int) gift.stars;
                ownedGiftIds.add(giftId);
                saveCosmetics();
                renderContent();
            });
        }
        return card;
    }

    private RLottieImageView createGiftAnimation(TLRPC.Document document, int sizeDp) {
        RLottieImageView image = new RLottieImageView(getParentActivity());
        image.setAutoRepeat(true);
        image.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        if (document != null) {
            image.setAnimation(document, sizeDp, sizeDp);
        }
        return image;
    }

    private TextView addSectionHeading(LinearLayout parent, int titleRes, int subtitleRes) {
        TextView title = createText(LocaleController.getString(titleRes), 17, MAIN_TEXT_COLOR, true);
        parent.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (subtitleRes != 0) {
            TextView subtitle = createText(LocaleController.getString(subtitleRes), 12, SUBTITLE_COLOR, false);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = AndroidUtilities.dp(4);
            params.bottomMargin = AndroidUtilities.dp(10);
            parent.addView(subtitle, params);
        } else {
            addBottomSpace(parent, 10);
        }
        return title;
    }

    private LinearLayout createCard(LinearLayout parent) {
        LinearLayout card = new LinearLayout(getParentActivity());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
        parent.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private TextView createText(String value, float size, int color, boolean isHeading) {
        TextView text = new TextView(getParentActivity());
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setTypeface(isHeading ? headingTypeface : bodyTypeface);
        if (isHeading) {
            text.setFontFeatureSettings("'ss01'");
        }
        return text;
    }

    private TextView createButton(String label, boolean isSelected) {
        TextView button = createText(label, 13, isSelected ? Color.WHITE : ACCENT_COLOR, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
        button.setBackground(makeStrokeDrawable(isSelected ? ACCENT_COLOR : CARD_BACKGROUND, isSelected ? ACCENT_COLOR : 0xff596078, AndroidUtilities.dp(13)));
        return button;
    }

    private GradientDrawable makeRoundedDrawable(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable makeStrokeDrawable(int color, int strokeColor, int radius) {
        GradientDrawable drawable = makeRoundedDrawable(color, radius);
        drawable.setStroke(AndroidUtilities.dp(1), strokeColor);
        return drawable;
    }

    private void addBottomSpace(LinearLayout parent, int dp) {
        View space = new View(getParentActivity());
        parent.addView(space, new LinearLayout.LayoutParams(1, AndroidUtilities.dp(dp)));
    }
}
