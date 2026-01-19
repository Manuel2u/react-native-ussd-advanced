package com.ussdadvanced;

import android.accessibilityservice.AccessibilityService;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

public class USSDService extends AccessibilityService {

    private static final String TAG = "USSDService";
    private static AccessibilityEvent event;
    private static USSDService instance;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        USSDService.event = event;
        USSDController ussd = USSDController.INSTANCE;

        Log.d(TAG, String.format(
                "onAccessibilityEvent: [type] %s [class] %s [package] %s [text] %s",
                event.getEventType(), event.getClassName(), event.getPackageName(),
                event.getText()));

        if (ussd.isRunning() == null || !ussd.isRunning()) {
            return;
        }

        String response = null;
        if (!event.getText().isEmpty()) {
            List<CharSequence> res = new ArrayList<>(event.getText());
            res.remove("SEND");
            res.remove("CANCEL");
            response = String.join("\n", res);
        }

        if (LoginView(event) && notInputText(event)) {
            clickOnButton(event, 0);
            if (ussd.getSendType() == Boolean.TRUE && ussd.getCallbackMessage() != null) {
                ussd.getCallbackMessage().invoke(event);
            } else {
                ussd.stopRunning();
                USSDController.callbackInvoke.over(response != null ? response : "");
            }
        } else if (problemView(event) || LoginView(event)) {
            clickOnButton(event, 1);
            if (ussd.getSendType() == Boolean.TRUE && ussd.getCallbackMessage() != null) {
                ussd.getCallbackMessage().invoke(event);
            } else {
                USSDController.callbackInvoke.over(response != null ? response : "");
            }
        } else if (isUSSDWidget(event)) {
            if (notInputText(event)) {
                clickOnButton(event, 0);
                if (ussd.getSendType() == Boolean.TRUE && ussd.getCallbackMessage() != null) {
                    ussd.getCallbackMessage().invoke(event);
                } else {
                    ussd.stopRunning();
                    USSDController.callbackInvoke.over(response != null ? response : "");
                }
            } else {
                if (ussd.getSendType() == Boolean.TRUE) {
                    if (ussd.getCallbackMessage() != null) {
                        ussd.getCallbackMessage().invoke(event);
                    }
                } else {
                    USSDController.callbackInvoke.responseInvoke(event);
                }
            }
        }
    }

    public static void send(String text) {
        Log.d(TAG, "send() called with text: " + text);

        if (sendWithCurrentWindow(text)) {
            Log.d(TAG, "sendWithCurrentWindow succeeded");
            return;
        }

        Log.d(TAG, "Falling back to event-based send");
        if (event == null) {
            Log.e(TAG, "Cannot send - no active event");
            return;
        }
        if (notInputText(event)) {
            Log.w(TAG, "Dialog has no input field, trying to click button anyway");
            clickOnButton(event, 1);
            return;
        }
        setTextIntoField(event, text);
        clickOnButton(event, 1);
    }

    public static void send2(String text, AccessibilityEvent ev) {
        setTextIntoField(ev, text);
        clickOnButton(ev, 1);
    }

    public static void cancel() {
        clickOnButton(event, 0);
    }

    public static void cancel2(AccessibilityEvent ev) {
        clickOnButton(ev, 0);
    }

    private static void setTextIntoField(AccessibilityEvent event, String data) {
        Bundle arguments = new Bundle();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, data);
        }
        for (AccessibilityNodeInfo leaf : getLeaves(event)) {
            if (leaf.getClassName().equals("android.widget.EditText")
                    && !leaf.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                ClipboardManager clipboardManager = ((ClipboardManager) USSDController.INSTANCE.getContext()
                        .getSystemService(Context.CLIPBOARD_SERVICE));
                if (clipboardManager != null) {
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("text", data));
                }
                leaf.performAction(AccessibilityNodeInfo.ACTION_PASTE);
            }
        }
    }

    protected static boolean notInputText(AccessibilityEvent event) {
        for (AccessibilityNodeInfo leaf : getLeaves(event))
            if (leaf.getClassName().equals("android.widget.EditText"))
                return false;
        return true;
    }

    private boolean isUSSDWidget(AccessibilityEvent event) {
        String className = event.getClassName().toString();
        return (className.equals("amigo.app.AmigoAlertDialog")
                || className.equals("android.app.AlertDialog")
                || className.equals("com.android.phone.oppo.settings.LocalAlertDialog")
                || className.equals("com.zte.mifavor.widget.AlertDialog")
                || className.equals("color.support.v7.app.AlertDialog")
                || className.contains("AlertDialog"));
    }

    private boolean LoginView(AccessibilityEvent event) {
        if (!isUSSDWidget(event) || event.getText().isEmpty())
            return false;
        List<String> loginKeys = USSDController.INSTANCE.getMap().get(USSDController.KEY_LOGIN);
        if (loginKeys == null)
            return false;
        return loginKeys.contains(event.getText().get(0).toString().toLowerCase());
    }

    protected boolean problemView(AccessibilityEvent event) {
        if (!isUSSDWidget(event) || event.getText().isEmpty())
            return false;
        List<String> errorKeys = USSDController.INSTANCE.getMap().get(USSDController.KEY_ERROR);
        if (errorKeys == null)
            return false;
        return errorKeys.contains(event.getText().get(0).toString().toLowerCase());
    }

    protected static void clickOnButton(AccessibilityEvent event, int index) {
        int count = -1;
        for (AccessibilityNodeInfo leaf : getLeaves(event)) {
            if (leaf.getClassName().toString().toLowerCase().contains("button")) {
                count++;
                if (count == index) {
                    leaf.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    return;
                }
            }
        }
    }

    private static List<AccessibilityNodeInfo> getLeaves(AccessibilityEvent event) {
        List<AccessibilityNodeInfo> leaves = new ArrayList<>();
        if (event != null && event.getSource() != null) {
            getLeaves(leaves, event.getSource());
        }
        return leaves;
    }

    private static void getLeaves(List<AccessibilityNodeInfo> leaves, AccessibilityNodeInfo node) {
        if (node.getChildCount() == 0) {
            leaves.add(node);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                getLeaves(leaves, child);
            }
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "onInterrupt");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d(TAG, "onServiceConnected - USSD Accessibility Service is now active");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    private static List<AccessibilityNodeInfo> getCurrentDialogLeaves() {
        List<AccessibilityNodeInfo> leaves = new ArrayList<>();
        if (instance == null) {
            Log.e(TAG, "Service instance is null");
            return leaves;
        }

        AccessibilityNodeInfo rootNode = instance.getRootInActiveWindow();
        if (rootNode == null) {
            Log.e(TAG, "Root window is null");
            return leaves;
        }

        collectLeaves(leaves, rootNode);
        return leaves;
    }

    private static void collectLeaves(List<AccessibilityNodeInfo> leaves, AccessibilityNodeInfo node) {
        if (node == null)
            return;

        if (node.getChildCount() == 0) {
            leaves.add(node);
        } else {
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    collectLeaves(leaves, child);
                }
            }
        }
    }

    public static boolean sendWithCurrentWindow(String text) {
        List<AccessibilityNodeInfo> leaves = getCurrentDialogLeaves();

        if (leaves.isEmpty()) {
            Log.e(TAG, "No dialog leaves found");
            return false;
        }

        boolean textEntered = false;
        Bundle arguments = new Bundle();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        }

        for (AccessibilityNodeInfo leaf : leaves) {
            if (leaf.getClassName() != null && leaf.getClassName().toString().equals("android.widget.EditText")) {
                Log.d(TAG, "Found EditText, entering text: " + text);
                if (leaf.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                    textEntered = true;
                    Log.d(TAG, "Text entered successfully");
                } else {
                    Log.d(TAG, "ACTION_SET_TEXT failed, trying clipboard");
                    ClipboardManager clipboardManager = ((ClipboardManager) USSDController.INSTANCE.getContext()
                            .getSystemService(Context.CLIPBOARD_SERVICE));
                    if (clipboardManager != null) {
                        clipboardManager.setPrimaryClip(ClipData.newPlainText("text", text));
                        if (leaf.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
                            textEntered = true;
                            Log.d(TAG, "Text pasted from clipboard");
                        }
                    }
                }
                break;
            }
        }

        if (!textEntered) {
            Log.e(TAG, "Could not enter text - no EditText found or action failed");
            return false;
        }

        int buttonCount = -1;
        for (AccessibilityNodeInfo leaf : leaves) {
            if (leaf.getClassName() != null && leaf.getClassName().toString().toLowerCase().contains("button")) {
                buttonCount++;
                if (buttonCount == 1) {
                    Log.d(TAG, "Clicking Send button");
                    leaf.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    return true;
                }
            }
        }

        Log.e(TAG, "Could not find Send button");
        return false;
    }
}
