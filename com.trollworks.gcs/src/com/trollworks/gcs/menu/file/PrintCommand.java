/*
 * Copyright ©1998-2020 by Richard A. Wilkes. All rights reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, version 2.0. If a copy of the MPL was not distributed with
 * this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, version 2.0.
 */

package com.trollworks.gcs.menu.file;

import com.trollworks.gcs.menu.Command;
import com.trollworks.gcs.menu.StdMenuBar;
import com.trollworks.gcs.ui.UIUtilities;
import com.trollworks.gcs.ui.print.PrintManager;
import com.trollworks.gcs.ui.widget.BaseWindow;
import com.trollworks.gcs.ui.widget.WindowUtils;
import com.trollworks.gcs.utility.FileProxy;
import com.trollworks.gcs.utility.I18n;
import com.trollworks.gcs.utility.PrintProxy;

import java.awt.EventQueue;
import java.awt.desktop.PrintFilesEvent;
import java.awt.desktop.PrintFilesHandler;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.concurrent.TimeUnit;

/** Provides the "Print..." command. */
public class PrintCommand extends Command implements PrintFilesHandler {
    /** The action command this command will issue. */
    public static final String CMD_PRINT = "Print";

    /** The singleton {@link PrintCommand}. */
    public static final PrintCommand INSTANCE = new PrintCommand();

    private PrintCommand() {
        super(I18n.Text("Print…"), CMD_PRINT, KeyEvent.VK_P);
    }

    @Override
    public void adjust() {
        setEnabled(!StdMenuBar.SUPRESS_MENUS && getTarget(PrintProxy.class) != null);
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        PrintProxy proxy = getTarget(PrintProxy.class);
        if (proxy != null) {
            print(proxy);
        }
    }

    /** @param proxy The {@link PrintProxy} to print. */
    public static void print(PrintProxy proxy) {
        if (proxy != null) {
            PrintManager mgr = proxy.getPrintManager();
            if (mgr != null) {
                mgr.print(proxy);
            } else {
                WindowUtils.showError(UIUtilities.getComponentForDialog(proxy), I18n.Text("There is no system printer available."));
            }
        }
    }

    @Override
    public void printFiles(PrintFilesEvent event) {
        for (File file : event.getFiles()) {
            EventQueue.invokeLater(new DeferredPrint(file));
        }
    }

    static class DeferredPrint implements Runnable {
        private long mStart;
        private File mFile;

        DeferredPrint(File file) {
            mFile = file;
            OpenDataFileCommand.open(file);
            mStart = System.currentTimeMillis();
        }

        @Override
        public void run() {
            FileProxy proxy = BaseWindow.findFileProxy(mFile);
            if (proxy != null) {
                print(proxy.getPrintProxy());
            } else if (System.currentTimeMillis() - mStart < TimeUnit.MILLISECONDS.convert(2, TimeUnit.MINUTES)) {
                EventQueue.invokeLater(this);
            }
        }
    }
}
