package org.telegram.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;

public class LauncherIconController {
    public static void tryFixLauncherIconIfNeeded() {
        if (!isEnabled(LauncherIcon.DEFAULT)) {
            setIcon(LauncherIcon.DEFAULT);
            return;
        }
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (icon != LauncherIcon.DEFAULT && isEnabled(icon)) {
                setIcon(LauncherIcon.DEFAULT);
                return;
            }
        }
    }

    public static boolean isEnabled(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        int i = ctx.getPackageManager().getComponentEnabledSetting(icon.getComponentName(ctx));
        return i == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || i == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon == LauncherIcon.DEFAULT;
    }

    public static void setIcon(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        PackageManager pm = ctx.getPackageManager();
        for (LauncherIcon i : LauncherIcon.values()) {
            pm.setComponentEnabledSetting(i.getComponentName(ctx), i == icon ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }
    }

    public enum LauncherIcon {
        DEFAULT("DefaultIcon", R.color.ic_background, R.mipmap.hoursgram_logo, R.string.AppIconDefault),
        MONET("MonetIcon", R.color.ic_background_monet, R.drawable.ic_foreground_monet, R.string.AppIconMonet, true),
        GRADIENT("GradientIcon", R.mipmap.ic_background_gradient, R.drawable.ic_foreground_white, R.string.AppIconGradient, true),
        AURORA("AuroraIcon", R.mipmap.ic_background_aurora, R.drawable.ic_foreground_white, R.string.AppIconAurora, true),
        NEO("NeoIcon", R.mipmap.ic_background_neo, R.mipmap.ic_foreground_neo, R.string.AppIconNeo, true),
        GOOGLE("GoogleIcon", R.color.white, R.mipmap.ic_foreground_google, R.string.AppIconGoogle, true),
        AMETHYST("AmethystIcon", R.mipmap.ic_background_amethyst, R.mipmap.ic_foreground_amethyst, R.string.AppIconAmethyst, true),
        DSGN480("Dsgn480Icon", R.mipmap.ic_background_480dsgn, R.mipmap.ic_foreground_480dsgn, R.string.AppIcon480DSGN, true),
        ORBIT("OrbitIcon", R.color.ic_background, R.mipmap.ic_foreground_orbit, R.string.AppIconOrbit, true),
        SPACE("SpaceIcon", R.mipmap.ic_background_space, R.mipmap.ic_foreground_space, R.string.AppIconSpace, true),
        WINTER("WinterIcon", R.mipmap.ic_background_winter, R.drawable.ic_foreground, R.string.AppIconWinter, true),
        SUS("SusIcon", R.color.ic_background_sus, R.mipmap.ic_foreground_sus, R.string.AppIconSus, true);

        public final String key;
        public final int background;
        public final int foreground;
        public final int title;
        public final boolean premium;
        public final boolean hidden;

        private ComponentName componentName;

        public ComponentName getComponentName(Context ctx) {
            if (componentName == null) {
                componentName = new ComponentName(ctx.getPackageName(), "com.exteragram.messenger." + key);
            }
            return componentName;
        }

        LauncherIcon(String key, int background, int foreground, int title) {
            this(key, background, foreground, title, false);
        }

        LauncherIcon(String key, int background, int foreground, int title, boolean hidden) {
            this.key = key;
            this.background = background;
            this.foreground = foreground;
            this.title = title;
            this.premium = false;
            this.hidden = hidden;
        }
    }
}
