package io.github.juby210.recentsgrid;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;

import java.lang.reflect.Field;

import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class Main implements IXposedHookLoadPackage {
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        var cl = lpparam.classLoader;

        var dp = XposedHelpers.findClass("com.android.launcher3.DeviceProfile", cl);
        var iconSize = dp.getDeclaredField("overviewTaskIconDrawableSizePx");
        iconSize.setAccessible(true);
        var iconSizeGrid = dp.getDeclaredField("overviewTaskIconDrawableSizeGridPx");
        iconSizeGrid.setAccessible(true);
        XposedBridge.hookMethod(dp.getDeclaredConstructors()[0], new XC_MethodHook() {
            public void afterHookedMethod(MethodHookParam param) throws Throwable {
                var _this = param.thisObject;
                iconSizeGrid.setInt(_this, iconSize.getInt(_this));
            }
        });

        XposedBridge.hookMethod(
            XposedHelpers.findClass("com.android.launcher3.uioverrides.states.OverviewState", cl)
                .getDeclaredMethod("displayOverviewTasksAsGrid", dp),
            XC_MethodReplacement.returnConstant(Boolean.TRUE)
        );

        var taskView = XposedHelpers.findClass("com.android.quickstep.views.TaskView", cl);
        var isFocused = taskView.getDeclaredMethod("isFocusedTask");
        isFocused.setAccessible(true);
        XposedBridge.hookMethod(taskView.getDeclaredMethod("isGridTask"), new XC_MethodHook() {
            public void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.setResult(!((boolean) isFocused.invoke(param.thisObject)));
            }
        });

        // Android 15 changes: BaseActivityInterface -> BaseContainerInterface, mActivity -> container, updateTaskSize() -> updateTaskSize(Rect, Rect, Rect)
        var ctx = Context.class;
        var rect = Rect.class;
        Class<?> baseActivityInterface = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) try {
            baseActivityInterface = XposedHelpers.findClass("com.android.quickstep.BaseContainerInterface", cl);
        } catch (Throwable ignored) {}
        if (baseActivityInterface == null) baseActivityInterface = XposedHelpers.findClass("com.android.quickstep.BaseActivityInterface", cl);
        var calculateFocusTaskSize = baseActivityInterface.getDeclaredMethod("calculateFocusTaskSize", ctx, dp, rect);
        calculateFocusTaskSize.setAccessible(true);
        for (var m : baseActivityInterface.getDeclaredMethods()) {
            if (m.getName().equals("calculateTaskSize")) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    /** @noinspection JavaReflectionInvocation*/
                    public void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        param.setResult(calculateFocusTaskSize.invoke(param.thisObject, param.args[0], param.args[1], param.args[2]));
                    }
                });
                break;
            }
        }


        // cheat, temporarily set isTablet, because this method is too insane to reimplement
        // https://cs.android.com/android/platform/superproject/+/android14-qpr3-release:packages/apps/Launcher3/quickstep/src/com/android/quickstep/views/TaskView.java;l=1714?q=updateTaskSize&sq=&ss=android%2Fplatform%2Fsuperproject
        var isTablet = dp.getDeclaredField("isTablet");
        isTablet.setAccessible(true);
        Field container;
        try {
            container = taskView.getDeclaredField("mActivity");
        } catch (Throwable ignored) {
            container = taskView.getDeclaredField("container");
        }
        container.setAccessible(true);
        var getDP = XposedHelpers.findClass("com.android.launcher3.views.ActivityContext", cl).getDeclaredMethod("getDeviceProfile");
        getDP.setAccessible(true);
        var _container = container;
        for (var m : taskView.getDeclaredMethods()) {
            if (m.getName().equals("updateTaskSize")) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    public void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        isTablet.setBoolean(getDP.invoke(_container.get(param.thisObject)), true);
                    }

                    public void afterHookedMethod(MethodHookParam param) throws Throwable {
                        isTablet.setBoolean(getDP.invoke(_container.get(param.thisObject)), false);
                    }
                });
                break;
            }
        }

        var orientedState = XposedHelpers.findClass("com.android.quickstep.util.RecentsOrientedState", cl);
        var setFlag = orientedState.getDeclaredMethod("setFlag", int.class, boolean.class);
        setFlag.setAccessible(true);
        XposedBridge.hookMethod(orientedState.getDeclaredMethod("setDeviceProfile", dp), new XC_MethodHook() {
            public void afterHookedMethod(MethodHookParam param) throws Throwable {
                setFlag.invoke(param.thisObject, 2, Boolean.FALSE);
            }
        });
    }
}
