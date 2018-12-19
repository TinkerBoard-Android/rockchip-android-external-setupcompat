/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.setupcompat.template;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff.Mode;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.PersistableBundle;
import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.annotation.VisibleForTesting;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewStub;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import com.google.android.setupcompat.R;
import com.google.android.setupcompat.TemplateLayout;
import com.google.android.setupcompat.item.FooterButton;
import com.google.android.setupcompat.item.FooterButton.ButtonType;
import com.google.android.setupcompat.item.FooterButton.OnButtonEventListener;
import com.google.android.setupcompat.item.FooterButtonInflater;
import com.google.android.setupcompat.logging.internal.ButtonFooterMixinMetrics;
import com.google.android.setupcompat.util.PartnerConfig;
import com.google.android.setupcompat.util.PartnerConfigHelper;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link Mixin} for managing buttons. By default, the button bar expects that buttons on the
 * start (left for LTR) are "secondary" borderless buttons, while buttons on the end (right for LTR)
 * are "primary" accent-colored buttons.
 */
public class ButtonFooterMixin implements Mixin {

  private final Context context;

  @Nullable private final ViewStub footerStub;

  @VisibleForTesting final boolean applyPartnerResources;

  private LinearLayout buttonContainer;
  private FooterButton primaryButton;
  private FooterButton secondaryButton;
  @IdRes private int primaryButtonId;
  @IdRes private int secondaryButtonId;

  private int footerBarPaddingTop;
  private int footerBarPaddingBottom;

  private static final AtomicInteger nextGeneratedId = new AtomicInteger(1);

  @VisibleForTesting public final ButtonFooterMixinMetrics metrics = new ButtonFooterMixinMetrics();

  private final OnButtonEventListener onButtonEventListener =
      new OnButtonEventListener() {
        @Override
        public void onClickListenerChanged(@Nullable OnClickListener listener, @IdRes int id) {
          if (buttonContainer != null && id != 0) {
            Button button = buttonContainer.findViewById(id);
            if (button != null) {
              button.setOnClickListener(listener);
            }
          }
        }

        @Override
        public void onTouchListenerChanged(@Nullable OnTouchListener listener, @IdRes int id) {
          if (buttonContainer != null && id != 0) {
            Button button = buttonContainer.findViewById(id);
            if (button != null) {
              button.setOnTouchListener(listener);
            }
          }
        }

        @Override
        public void onEnabledChanged(boolean enabled, @IdRes int id) {
          if (buttonContainer != null && id != 0) {
            Button button = buttonContainer.findViewById(id);
            if (button != null) {
              button.setEnabled(enabled);
            }
          }
        }

        @Override
        public void onVisibilityChanged(int visibility, @IdRes int id) {
          if (buttonContainer != null && id != 0) {
            Button button = buttonContainer.findViewById(id);
            if (button != null) {
              button.setVisibility(visibility);
              autoSetButtonBarVisibility();
            }
          }
        }

        @Override
        public void onTextChanged(CharSequence text, @IdRes int id) {
          if (buttonContainer != null && id != 0) {
            Button button = buttonContainer.findViewById(id);
            if (button != null) {
              button.setText(text);
            }
          }
        }
      };

  /**
   * Creates a mixin for managing buttons on the footer.
   *
   * @param layout The {@link TemplateLayout} containing this mixin.
   * @param attrs XML attributes given to the layout.
   * @param defStyleAttr The default style attribute as given to the constructor of the layout.
   * @param applyPartnerResources determine applies partner resources or not.
   */
  public ButtonFooterMixin(
      TemplateLayout layout,
      @Nullable AttributeSet attrs,
      @AttrRes int defStyleAttr,
      boolean applyPartnerResources) {
    context = layout.getContext();
    footerStub = (ViewStub) layout.findManagedViewById(R.id.suc_layout_footer);
    this.applyPartnerResources = applyPartnerResources;

    int defaultPadding =
        context
            .getResources()
            .getDimensionPixelSize(R.dimen.suc_customization_footer_padding_vertical);
    TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SucFooterBar, defStyleAttr, 0);
    footerBarPaddingTop =
        a.getDimensionPixelSize(R.styleable.SucFooterBar_sucFooterBarPaddingTop, defaultPadding);
    footerBarPaddingBottom =
        a.getDimensionPixelSize(R.styleable.SucFooterBar_sucFooterBarPaddingBottom, defaultPadding);

    int primaryBtn = a.getResourceId(R.styleable.SucFooterBar_sucFooterBarPrimaryFooterButton, 0);
    int secondaryBtn =
        a.getResourceId(R.styleable.SucFooterBar_sucFooterBarSecondaryFooterButton, 0);
    a.recycle();

    FooterButtonInflater inflater = new FooterButtonInflater(context);

    if (secondaryBtn != 0) {
      setSecondaryButton(inflater.inflate(secondaryBtn));
      metrics.logPrimaryButtonInitialStateVisibility(/* isVisible= */ true, /* isUsingXml= */ true);
    }

    if (primaryBtn != 0) {
      setPrimaryButton(inflater.inflate(primaryBtn));
      metrics.logSecondaryButtonInitialStateVisibility(
          /* isVisible= */ true, /* isUsingXml= */ true);
    }
  }

  private View addSpace() {
    LinearLayout buttonContainer = ensureFooterInflated();
    View space = new View(buttonContainer.getContext());
    space.setLayoutParams(new LayoutParams(0, 0, 1.0f));
    space.setVisibility(View.INVISIBLE);
    buttonContainer.addView(space);
    return space;
  }

  @NonNull
  private LinearLayout ensureFooterInflated() {
    if (buttonContainer == null) {
      if (footerStub == null) {
        throw new IllegalStateException("Footer stub is not found in this template");
      }
      buttonContainer = (LinearLayout) inflateFooter(R.layout.suc_footer_button_bar);
      if (Build.VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
        buttonContainer.setId(View.generateViewId());
      } else {
        buttonContainer.setId(generateViewId());
      }
      updateBottomBarPadding();
    }
    return buttonContainer;
  }

  @SuppressLint("InflateParams")
  private Button createThemedButton(Context context, @StyleRes int theme) {
    // Inflate a single button from XML, which when using support lib, will take advantage of
    // the injected layout inflater and give us AppCompatButton instead.
    LayoutInflater inflater = LayoutInflater.from(new ContextThemeWrapper(context, theme));
    return (Button) inflater.inflate(R.layout.suc_button, null, false);
  }

  /** Sets primary button for footer. */
  public void setPrimaryButton(FooterButton footerButton) {
    LinearLayout buttonContainer = ensureFooterInflated();

    // Set the default theme if theme is not set, or when running in setup flow.
    if (footerButton.getTheme() == 0 || applyPartnerResources) {
      footerButton.setTheme(R.style.SucPartnerCustomizationButton_Primary);
    }
    // TODO(b/120055778): Make sure customize attributes in theme can be applied during setup flow.
    // If sets background color to full transparent, the button changes to colored borderless ink
    // button style.
    if (applyPartnerResources
        && PartnerConfigHelper.get(context)
                .getColor(context, PartnerConfig.CONFIG_FOOTER_PRIMARY_BUTTON_BG_COLOR)
            == Color.TRANSPARENT) {
      footerButton.setTheme(R.style.SucPartnerCustomizationButton_Secondary);
    }

    Button button = inflateButton(footerButton);
    primaryButtonId = button.getId();
    buttonContainer.addView(button);
    autoSetButtonBarVisibility();

    if (applyPartnerResources) {
      // This API should only be called after primaryButtonId is set.
      updateButtonAttrsWithPartnerConfig(button, true, footerButton.getButtonType());
    }

    footerButton.setId(primaryButtonId);
    footerButton.setOnButtonEventListener(onButtonEventListener);
    primaryButton = footerButton;

    // Make sure the position of buttons are correctly and prevent primary button create twice or
    // more.
    repopulateButtons();
  }

  /** Returns the {@link FooterButton} of primary button. */
  public FooterButton getPrimaryButton() {
    return primaryButton;
  }

  @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
  public Button getPrimaryButtonView() {
    return buttonContainer == null ? null : buttonContainer.findViewById(primaryButtonId);
  }

  @VisibleForTesting
  boolean isPrimaryButtonVisible() {
    return getPrimaryButtonView() != null && getPrimaryButtonView().getVisibility() == View.VISIBLE;
  }

  /** Sets secondary button for footer. */
  public void setSecondaryButton(FooterButton footerButton) {
    LinearLayout buttonContainer = ensureFooterInflated();

    // Set the default theme if theme is not set, or when running in setup flow.
    if (footerButton.getTheme() == 0 || applyPartnerResources) {
      footerButton.setTheme(R.style.SucPartnerCustomizationButton_Secondary);
    }
    int color =
        PartnerConfigHelper.get(context)
            .getColor(context, PartnerConfig.CONFIG_FOOTER_SECONDARY_BUTTON_BG_COLOR);
    // TODO(b/120055778): Make sure customize attributes in theme can be applied during setup flow.
    // If doesn't set background color to full transparent or white, the button changes to colored
    // bordered ink button style.
    if (applyPartnerResources && (color != Color.TRANSPARENT && color != Color.WHITE)) {
      footerButton.setTheme(R.style.SucPartnerCustomizationButton_Primary);
    }

    Button button = inflateButton(footerButton);
    secondaryButtonId = button.getId();
    buttonContainer.addView(button);

    if (applyPartnerResources) {
      // This API should only be called after secondaryButtonId is set.
      updateButtonAttrsWithPartnerConfig(button, false, footerButton.getButtonType());
    }

    footerButton.setId(secondaryButtonId);
    footerButton.setOnButtonEventListener(onButtonEventListener);
    secondaryButton = footerButton;

    // Make sure the position of buttons are correctly and prevent secondary button create twice or
    // more.
    repopulateButtons();
  }

  public void repopulateButtons() {
    LinearLayout buttonContainer = ensureFooterInflated();
    Button tempPrimaryButton = getPrimaryButtonView();
    Button tempSecondaryButton = getSecondaryButtonView();
    buttonContainer.removeAllViews();

    if (tempSecondaryButton != null) {
      buttonContainer.addView(tempSecondaryButton);
    }
    addSpace();
    if (tempPrimaryButton != null) {
      buttonContainer.addView(tempPrimaryButton);
    }
  }

  @VisibleForTesting
  LinearLayout getButtonContainer() {
    return buttonContainer;
  }

  /** Returns the {@link FooterButton} of secondary button. */
  public FooterButton getSecondaryButton() {
    return secondaryButton;
  }

  /**
   * Checks the visibility state of footer buttons to set the visibility state of this footer bar
   * automatically.
   */
  private void autoSetButtonBarVisibility() {
    Button primaryButton = getPrimaryButtonView();
    Button secondaryButton = getSecondaryButtonView();
    boolean primaryVisible = primaryButton != null && primaryButton.getVisibility() == View.VISIBLE;
    boolean secondaryVisible =
        secondaryButton != null && secondaryButton.getVisibility() == View.VISIBLE;

    buttonContainer.setVisibility(primaryVisible || secondaryVisible ? View.VISIBLE : View.GONE);
  }

  /** Returns the visibility status for this footer bar. */
  @VisibleForTesting
  public int getVisibility() {
    return buttonContainer.getVisibility();
  }

  @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
  public Button getSecondaryButtonView() {
    return buttonContainer == null ? null : buttonContainer.findViewById(secondaryButtonId);
  }

  @VisibleForTesting
  boolean isSecondaryButtonVisible() {
    return getSecondaryButtonView() != null
        && getSecondaryButtonView().getVisibility() == View.VISIBLE;
  }

  private static int generateViewId() {
    for (; ; ) {
      final int result = nextGeneratedId.get();
      // aapt-generated IDs have the high byte nonzero; clamp to the range under that.
      int newValue = result + 1;
      if (newValue > 0x00FFFFFF) {
        newValue = 1; // Roll over to 1, not 0.
      }
      if (nextGeneratedId.compareAndSet(result, newValue)) {
        return result;
      }
    }
  }

  private Button inflateButton(FooterButton footerButton) {
    Button button = createThemedButton(context, footerButton.getTheme());
    if (Build.VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
      button.setId(View.generateViewId());
    } else {
      button.setId(generateViewId());
    }
    button.setText(footerButton.getText());
    button.setOnClickListener(footerButton.getOnClickListener());

    return button;
  }

  // TODO(b/120055778): Make sure customize attributes in theme can be applied during setup flow.
  private void updateButtonAttrsWithPartnerConfig(
      Button button, boolean isPrimaryButton, ButtonType buttonType) {
    updateButtonTextColorWithPartnerConfig(button, isPrimaryButton);
    updateButtonTextSizeWithPartnerConfig(button, isPrimaryButton);
    updateButtonTypeFaceWithPartnerConfig(button);
    updateButtonBackgroundWithPartnerConfig(button, isPrimaryButton);
    updateButtonRadiusWithPartnerConfig(button);
    updateButtonIconWithPartnerConfig(button, buttonType);
  }

  private void updateButtonTextColorWithPartnerConfig(Button button, boolean isPrimaryButton) {
    @ColorInt int color = 0;
    if (isPrimaryButton) {
      color =
          PartnerConfigHelper.get(context)
              .getColor(context, PartnerConfig.CONFIG_FOOTER_PRIMARY_BUTTON_TEXT_COLOR);
    } else {
      color =
          PartnerConfigHelper.get(context)
              .getColor(context, PartnerConfig.CONFIG_FOOTER_SECONDARY_BUTTON_TEXT_COLOR);
    }
    button.setTextColor(color);
  }

  private void updateButtonTextSizeWithPartnerConfig(Button button, boolean isPrimaryButton) {
    float size = 0.0f;
    if (isPrimaryButton) {
      size =
          PartnerConfigHelper.get(context)
              .getDimension(context, PartnerConfig.CONFIG_FOOTER_PRIMARY_BUTTON_TEXT_SIZE);
    } else {
      size =
          PartnerConfigHelper.get(context)
              .getDimension(context, PartnerConfig.CONFIG_FOOTER_SECONDARY_BUTTON_TEXT_SIZE);
    }
    button.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
  }

  private void updateButtonTypeFaceWithPartnerConfig(Button button) {
    String fontFamilyName =
        PartnerConfigHelper.get(context)
            .getString(context, PartnerConfig.CONFIG_FOOTER_BUTTON_FONT_FAMILY);
    Typeface font = Typeface.create(fontFamilyName, Typeface.NORMAL);
    if (font != null) {
      button.setTypeface(font);
    }
  }

  private void updateButtonBackgroundWithPartnerConfig(Button button, boolean isPrimaryButton) {
    if (Build.VERSION.SDK_INT >= VERSION_CODES.M) {
      if (isPrimaryButton) {
        int color =
            PartnerConfigHelper.get(context)
                .getColor(context, PartnerConfig.CONFIG_FOOTER_PRIMARY_BUTTON_BG_COLOR);
        if (color != Color.TRANSPARENT) {
          button.getBackground().setColorFilter(color, Mode.MULTIPLY);
        }
      } else {
        int color =
            PartnerConfigHelper.get(context)
                .getColor(context, PartnerConfig.CONFIG_FOOTER_SECONDARY_BUTTON_BG_COLOR);
        if (color != Color.TRANSPARENT) {
          button.getBackground().setColorFilter(color, Mode.MULTIPLY);
        }
      }
    }
  }

  private void updateButtonRadiusWithPartnerConfig(Button button) {
    if (Build.VERSION.SDK_INT >= VERSION_CODES.N) {
      float radius =
          PartnerConfigHelper.get(context)
              .getDimension(context, PartnerConfig.CONFIG_FOOTER_BUTTON_RADIUS);
      GradientDrawable gradientDrawable = getGradientDrawable(button);
      if (gradientDrawable != null) {
        gradientDrawable.setCornerRadius(radius);
      }
    }
  }

  private void updateButtonIconWithPartnerConfig(Button button, ButtonType buttonType) {
    if (button == null) {
      return;
    }
    Drawable icon;
    switch (buttonType) {
      case NEXT:
        icon =
            PartnerConfigHelper.get(context)
                .getDrawable(context, PartnerConfig.CONFIG_FOOTER_BUTTON_ICON_NEXT);
        break;
      case SKIP:
        icon =
            PartnerConfigHelper.get(context)
                .getDrawable(context, PartnerConfig.CONFIG_FOOTER_BUTTON_ICON_SKIP);
        break;
      case CANCEL:
        icon =
            PartnerConfigHelper.get(context)
                .getDrawable(context, PartnerConfig.CONFIG_FOOTER_BUTTON_ICON_CANCEL);
        break;
      case STOP:
        icon =
            PartnerConfigHelper.get(context)
                .getDrawable(context, PartnerConfig.CONFIG_FOOTER_BUTTON_ICON_STOP);
        break;
      case OTHER:
      default:
        icon = null;
        break;
    }
    setButtonIcon(button, icon);
  }

  private void setButtonIcon(Button button, Drawable icon) {
    if (button == null) {
      return;
    }

    if (icon != null) {
      // TODO(b/120488979): restrict the icons to a reasonable size
      int h = icon.getIntrinsicHeight();
      int w = icon.getIntrinsicWidth();
      icon.setBounds(0, 0, w, h);
    }

    Drawable iconStart = null;
    Drawable iconEnd = null;
    if (button.getId() == primaryButtonId) {
      iconEnd = icon;
    } else if (button.getId() == secondaryButtonId) {
      iconStart = icon;
    }
    if (Build.VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
      button.setCompoundDrawablesRelative(iconStart, null, iconEnd, null);
    } else {
      button.setCompoundDrawables(iconStart, null, iconEnd, null);
    }
  }

  GradientDrawable getGradientDrawable(Button button) {
    Drawable drawable = button.getBackground();
    if (drawable instanceof InsetDrawable) {
      LayerDrawable layerDrawable = (LayerDrawable) ((InsetDrawable) drawable).getDrawable();
      return (GradientDrawable) layerDrawable.getDrawable(0);
    } else if (drawable instanceof RippleDrawable) {
      InsetDrawable insetDrawable = (InsetDrawable) ((RippleDrawable) drawable).getDrawable(0);
      return (GradientDrawable) insetDrawable.getDrawable();
    }
    return null;
  }

  protected View inflateFooter(@LayoutRes int footer) {
    if (Build.VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
      LayoutInflater inflater =
          LayoutInflater.from(
              new ContextThemeWrapper(context, R.style.SucPartnerCustomizationButtonBar_Stackable));
      footerStub.setLayoutInflater(inflater);
    }
    footerStub.setLayoutResource(footer);
    return footerStub.inflate();
  }

  private void updateBottomBarPadding() {
    if (buttonContainer == null) {
      // Ignore action since buttonContainer is null
      return;
    }

    if (applyPartnerResources) {
      footerBarPaddingTop =
          (int)
              PartnerConfigHelper.get(context)
                  .getDimension(context, PartnerConfig.CONFIG_FOOTER_BUTTON_PADDING_TOP);
      footerBarPaddingBottom =
          (int)
              PartnerConfigHelper.get(context)
                  .getDimension(context, PartnerConfig.CONFIG_FOOTER_BUTTON_PADDING_BOTTOM);
    }
    buttonContainer.setPadding(
        buttonContainer.getPaddingLeft(),
        footerBarPaddingTop,
        buttonContainer.getPaddingRight(),
        footerBarPaddingBottom);
  }

  /** Returns the paddingTop of footer bar. */
  @VisibleForTesting
  int getPaddingTop() {
    return (buttonContainer != null) ? buttonContainer.getPaddingTop() : footerStub.getPaddingTop();
  }

  /** Returns the paddingBottom of footer bar. */
  @VisibleForTesting
  int getPaddingBottom() {
    return (buttonContainer != null)
        ? buttonContainer.getPaddingBottom()
        : footerStub.getPaddingBottom();
  }

  /** Uses for notify mixin the view already attached to window. */
  public void onAttachedToWindow() {
    metrics.logPrimaryButtonInitialStateVisibility(
        /* isVisible= */ isPrimaryButtonVisible(), /* isUsingXml= */ false);
    metrics.logSecondaryButtonInitialStateVisibility(
        /* isVisible= */ isSecondaryButtonVisible(), /* isUsingXml= */ false);
  }

  /** Uses for notify mixin the view already detached from window. */
  public void onDetachedFromWindow() {
    metrics.updateButtonVisibility(isPrimaryButtonVisible(), isSecondaryButtonVisible());
  }

  /**
   * Assigns logging metrics to bundle for PartnerCustomizationLayout to log metrics to SetupWizard.
   */
  public PersistableBundle getLoggingMetrics() {
    return metrics.getMetrics();
  }
}