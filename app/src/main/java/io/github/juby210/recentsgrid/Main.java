package io.github.juby210.recentsgrid;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.BiConsumer;

import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class Main implements IXposedHookLoadPackage {
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        var cl = lpparam.classLoader;

        var dp = XposedHelpers.findClass("com.android.launcher3.DeviceProfile", cl);
        var overviewProfile = XposedHelpers.findClassIfExists("com.android.launcher3.deviceprofile.OverviewProfile", cl);
        if (overviewProfile == null) {
            var iconSize = dp.getDeclaredField("overviewTaskIconDrawableSizePx");
            iconSize.setAccessible(true);
            var iconSizeGrid = dp.getDeclaredField("overviewTaskIconDrawableSizeGridPx");
            iconSizeGrid.setAccessible(true);

            for (var c : dp.getDeclaredConstructors()) {
                XposedBridge.hookMethod(c, new XC_MethodHook() {
                    public void afterHookedMethod(MethodHookParam param) throws Throwable {
                        var _this = param.thisObject;
                        iconSizeGrid.setInt(_this, iconSize.getInt(_this));
                    }
                });
            }
        } else {
            var iconSize = overviewProfile.getDeclaredField("taskIconDrawableSizePx");
            iconSize.setAccessible(true);
            var iconSizeGrid = overviewProfile.getDeclaredField("taskIconDrawableSizeGridPx");
            iconSizeGrid.setAccessible(true);

            var rowSpacing = overviewProfile.getDeclaredField("rowSpacing");
            rowSpacing.setAccessible(true);
            var gridSideMargin = overviewProfile.getDeclaredField("gridSideMargin");
            gridSideMargin.setAccessible(true);

            for (var c : overviewProfile.getDeclaredConstructors()) {
                XposedBridge.hookMethod(c, new XC_MethodHook() {
                    public void afterHookedMethod(MethodHookParam param) throws Throwable {
                        var _this = param.thisObject;
                        iconSizeGrid.setInt(_this, iconSize.getInt(_this));
                        int rSpacing = rowSpacing.getInt(_this);
                        if (rSpacing == 0) {
                            int taskMargin = XposedHelpers.getIntField(_this, "taskMarginPx");
                            int desiredSpacing = Math.max(taskMargin, 32);
                            rowSpacing.setInt(_this, desiredSpacing);
                            gridSideMargin.setInt(_this, desiredSpacing);
                        }
                    }
                });
            }
        }

        var returnTrue = XC_MethodReplacement.returnConstant(Boolean.TRUE);
        XposedBridge.hookMethod(
                XposedHelpers.findClass("com.android.launcher3.uioverrides.states.OverviewState", cl)
                        .getDeclaredMethod("displayOverviewTasksAsGrid", dp),
                returnTrue
        );

        var taskView = XposedHelpers.findClass("com.android.quickstep.views.TaskView", cl);
        XC_MethodHook hook;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) hook = returnTrue;
        else {
            var isFocused = taskView.getDeclaredMethod("isFocusedTask");
            hook = new XC_MethodHook() {
                public void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(!((boolean) isFocused.invoke(param.thisObject)));
                }
            };
        }
        XposedBridge.hookMethod(taskView.getDeclaredMethod("isGridTask"), hook);

        // Android 15 changes: BaseActivityInterface -> BaseContainerInterface, mActivity -> mContainer
        var ctx = Context.class;
        var rect = Rect.class;
        Class<?> baseActivityInterface = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) try {
            baseActivityInterface = XposedHelpers.findClass("com.android.quickstep.BaseContainerInterface", cl);
        } catch (Throwable ignored) {}
        if (baseActivityInterface == null)
            baseActivityInterface = XposedHelpers.findClass("com.android.quickstep.BaseActivityInterface", cl);
        Method calculateFocusTaskSize = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) try {
            calculateFocusTaskSize = baseActivityInterface.getDeclaredMethod("calculateLargeTileSize", ctx, dp, rect);
        } catch (Throwable ignored) {}
        if (calculateFocusTaskSize == null)
            calculateFocusTaskSize = baseActivityInterface.getDeclaredMethod("calculateFocusTaskSize", ctx, dp, rect);
        calculateFocusTaskSize.setAccessible(true);
        for (var m : baseActivityInterface.getDeclaredMethods()) {
            if (m.getName().equals("calculateTaskSize")) {
                var finalCalculateFocusTaskSize = calculateFocusTaskSize;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    public void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        param.setResult(finalCalculateFocusTaskSize.invoke(param.thisObject, param.args[0], param.args[1], param.args[2]));
                    }
                });
                break;
            }
        }


        // cheat, temporarily set isTablet, because those methods are too insane to reimplement
        // https://cs.android.com/android/platform/superproject/+/android14-qpr3-release:packages/apps/Launcher3/quickstep/src/com/android/quickstep/views/RecentsView.java;l=2154?q=updateTaskSize&sq=&ss=android%2Fplatform%2Fsuperproject
        // https://cs.android.com/android/platform/superproject/+/android14-qpr3-release:packages/apps/Launcher3/quickstep/src/com/android/quickstep/views/TaskView.java;l=1714?q=updateTaskSize&sq=&ss=android%2Fplatform%2Fsuperproject
        var recentsView = XposedHelpers.findClass("com.android.quickstep.views.RecentsView", cl);
        Field mActivity = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) try {
            mActivity = recentsView.getDeclaredField("mContainer");
        } catch (Throwable ignored) {}
        if (mActivity == null) mActivity = recentsView.getDeclaredField("mActivity");
        mActivity.setAccessible(true);
        var getDP = XposedHelpers.findClass("com.android.launcher3.views.ActivityContext", cl).getDeclaredMethod("getDeviceProfile");
        getDP.setAccessible(true);
        var activity = mActivity;

        // Android 17 changes: isTablet -> (com.android.launcher3.deviceprofile.DeviceProperties) mDeviceProperties.isLargeScreen
        var deviceProperties = XposedHelpers.findClassIfExists("com.android.launcher3.deviceprofile.DeviceProperties", cl);
        Field mDevicePropertiesField = null;
        Field isLargeScreenField = null;
        Field isTabletField = null;

        if (deviceProperties != null) {
            try {
                mDevicePropertiesField = dp.getDeclaredField("mDeviceProperties");
            } catch (Throwable ignored) {
                mDevicePropertiesField = dp.getDeclaredField("deviceProperties");
            }
            mDevicePropertiesField.setAccessible(true);
            isLargeScreenField = deviceProperties.getDeclaredField("isLargeScreen");
            isLargeScreenField.setAccessible(true);
        } else {
            isTabletField = dp.getDeclaredField("isTablet");
            isTabletField.setAccessible(true);
        }

        var mDeviceProperties = mDevicePropertiesField;
        var isLargeScreen = isLargeScreenField;
        var isTablet = isTabletField;
        var setLargeScreen = new BiConsumer<Object, Boolean>() {
            public void accept(Object deviceProfileObj, Boolean value) {
                if (deviceProfileObj == null) return;
                try {
                    if (isLargeScreen != null && mDeviceProperties != null) {
                        isLargeScreen.setBoolean(mDeviceProperties.get(deviceProfileObj), value);
                    } else isTablet.setBoolean(deviceProfileObj, value);
                } catch (Throwable ignored) {}
            }
        };

        var setLargeScreenHook = new XC_MethodHook() {
            private boolean set = false;

            public void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var deviceProfile = getDP.invoke(activity.get(param.thisObject));
                var isAlreadyLarge = false;
                if (isLargeScreen != null && mDeviceProperties != null) {
                    var devProps = mDeviceProperties.get(deviceProfile);
                    isAlreadyLarge = devProps != null && isLargeScreen.getBoolean(devProps);
                } else isAlreadyLarge = isTablet.getBoolean(deviceProfile);

                if (!isAlreadyLarge) {
                    set = true;
                    setLargeScreen.accept(deviceProfile, Boolean.TRUE);
                }
            }

            public void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (set) {
                    setLargeScreen.accept(getDP.invoke(activity.get(param.thisObject)), Boolean.FALSE);
                    set = false;
                }
            }
        };

        Method updateTaskSize;
        try {
            updateTaskSize = recentsView.getDeclaredMethod("updateTaskSize");
        } catch (Throwable ignored) {
            updateTaskSize = recentsView.getDeclaredMethod("updateTaskSize", boolean.class);
        }
        XposedBridge.hookMethod(updateTaskSize, setLargeScreenHook);
        XposedBridge.hookMethod(recentsView.getDeclaredMethod("updateSizeAndPadding"), setLargeScreenHook);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            XposedHelpers.findAndHookMethod("com.android.quickstep.util.TaskViewSimulator", cl, "setDp", dp, new XC_MethodHook() {
                public void beforeHookedMethod(MethodHookParam param) {
                    setLargeScreen.accept(param.args[0], Boolean.TRUE);
                }
            });

            XposedBridge.hookMethod(taskView.getDeclaredMethod("updateTaskSize", Rect.class, Rect.class),  new XC_MethodHook() {
                public void afterHookedMethod(MethodHookParam param) {
                    var _this = param.thisObject;
                    var view = (View) _this;
                    int taskWidth = view.getWidth();
                    if (taskWidth <= 0 && view.getLayoutParams() != null) {
                        taskWidth = view.getLayoutParams().width;
                    }
                    if (taskWidth <= 0) taskWidth = view.getMeasuredWidth();
                    if (taskWidth <= 0) return;

                    var containers = (Iterable<?>) XposedHelpers.callMethod(_this, "getTaskContainers");
                    if (containers == null) return;

                    for (var container : containers) {
                        var iconView = XposedHelpers.callMethod(container, "getIconView");
                        if (iconView == null) continue;

                        int bgMargin = XposedHelpers.getIntField(iconView, "backgroundMarginTopStart");
                        int menuMargin = XposedHelpers.getIntField(iconView, "iconMenuMarginTopStart");
                        int targetWidth = taskWidth - 2 * (bgMargin + menuMargin);
                        if (targetWidth > 0) {
                            int currentMaxWidth = XposedHelpers.getIntField(iconView, "maxWidth");
                            if (currentMaxWidth != targetWidth) {
                                XposedHelpers.callMethod(iconView, "setMaxWidth", targetWidth);
                            }
                        }
                    }
                }
            });
        }

        var orientedState = XposedHelpers.findClass("com.android.quickstep.util.RecentsOrientedState", cl);
        var setFlag = orientedState.getDeclaredMethod("setFlag", int.class, boolean.class);
        setFlag.setAccessible(true);
        XposedBridge.hookMethod(orientedState.getDeclaredMethod("setDeviceProfile", dp), new XC_MethodHook() {
            public void afterHookedMethod(MethodHookParam param) throws Throwable {
                setFlag.invoke(param.thisObject, 2, Boolean.FALSE);
            }
        });

        // this runs only if you use another launcher and this is only a quickstep provider
        XposedBridge.hookMethod(
                XposedHelpers.findClass("com.android.quickstep.RecentsActivity", cl)
                        .getDeclaredMethod("createDeviceProfile"),
                new XC_MethodHook() {
                    public void afterHookedMethod(MethodHookParam param) {
                        setLargeScreen.accept(param.getResult(), Boolean.TRUE);
                    }
                }
        );
    }
}
