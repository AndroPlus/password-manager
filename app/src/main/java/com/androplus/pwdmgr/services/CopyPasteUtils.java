package com.androplus.pwdmgr.services;

import static android.content.Context.CLIPBOARD_SERVICE;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.View;
import android.widget.EditText;

public class CopyPasteUtils {
    private static CopyPasteUtils   copyPasteUtils = null;
    static ClipboardManager  mClipboardManager = null;
    public static CopyPasteUtils getInstance(Activity activity) {
        if(copyPasteUtils == null) {
            copyPasteUtils = new CopyPasteUtils();
            mClipboardManager = (ClipboardManager) activity.getSystemService(CLIPBOARD_SERVICE);
        }
        return copyPasteUtils;
    }

    public void copyText(View view) {
        String currentText = ((EditText)view).getText().toString();

        if (currentText != null) {
            mClipboardManager.setPrimaryClip(ClipData.newPlainText("text", currentText));
            //Toast.makeText(this, "Text Copied!", Toast.LENGTH_SHORT).show();
        }
    }

    public void pasteText(View view, Context context) {
       EditText editText =  ((EditText)view);
        String clipboardText;

        clipboardText = mClipboardManager.getPrimaryClip().getItemAt(0)
                .coerceToText(context).toString();

        editText.setText(clipboardText);
    }
}
