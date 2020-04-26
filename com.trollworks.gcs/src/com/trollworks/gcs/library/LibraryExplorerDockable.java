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

package com.trollworks.gcs.library;

import com.trollworks.gcs.advantage.AdvantageList;
import com.trollworks.gcs.advantage.AdvantagesDockable;
import com.trollworks.gcs.character.GURPSCharacter;
import com.trollworks.gcs.character.SheetDockable;
import com.trollworks.gcs.equipment.EquipmentDockable;
import com.trollworks.gcs.equipment.EquipmentList;
import com.trollworks.gcs.io.Log;
import com.trollworks.gcs.menu.edit.Openable;
import com.trollworks.gcs.menu.file.RecentFilesMenu;
import com.trollworks.gcs.modifier.AdvantageModifierList;
import com.trollworks.gcs.modifier.AdvantageModifiersDockable;
import com.trollworks.gcs.modifier.EquipmentModifierList;
import com.trollworks.gcs.modifier.EquipmentModifiersDockable;
import com.trollworks.gcs.notes.NoteList;
import com.trollworks.gcs.notes.NotesDockable;
import com.trollworks.gcs.pdfview.PdfDockable;
import com.trollworks.gcs.pdfview.PdfRef;
import com.trollworks.gcs.skill.SkillList;
import com.trollworks.gcs.skill.SkillsDockable;
import com.trollworks.gcs.spell.SpellList;
import com.trollworks.gcs.spell.SpellsDockable;
import com.trollworks.gcs.template.Template;
import com.trollworks.gcs.template.TemplateDockable;
import com.trollworks.gcs.ui.RetinaIcon;
import com.trollworks.gcs.ui.image.Images;
import com.trollworks.gcs.ui.widget.IconButton;
import com.trollworks.gcs.ui.widget.StdFileDialog;
import com.trollworks.gcs.ui.widget.Toolbar;
import com.trollworks.gcs.ui.widget.Workspace;
import com.trollworks.gcs.ui.widget.dock.Dock;
import com.trollworks.gcs.ui.widget.dock.DockContainer;
import com.trollworks.gcs.ui.widget.dock.DockLayout;
import com.trollworks.gcs.ui.widget.dock.DockLocation;
import com.trollworks.gcs.ui.widget.dock.Dockable;
import com.trollworks.gcs.ui.widget.search.Search;
import com.trollworks.gcs.ui.widget.search.SearchTarget;
import com.trollworks.gcs.ui.widget.tree.FieldAccessor;
import com.trollworks.gcs.ui.widget.tree.IconAccessor;
import com.trollworks.gcs.ui.widget.tree.TextTreeColumn;
import com.trollworks.gcs.ui.widget.tree.TreeContainerRow;
import com.trollworks.gcs.ui.widget.tree.TreePanel;
import com.trollworks.gcs.ui.widget.tree.TreeRoot;
import com.trollworks.gcs.ui.widget.tree.TreeRow;
import com.trollworks.gcs.ui.widget.tree.TreeRowViewIterator;
import com.trollworks.gcs.utility.FileProxy;
import com.trollworks.gcs.utility.FileType;
import com.trollworks.gcs.utility.I18n;
import com.trollworks.gcs.utility.PathUtils;
import com.trollworks.gcs.utility.notification.Notifier;

import java.awt.BorderLayout;
import java.awt.KeyboardFocusManager;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.swing.Icon;
import javax.swing.ListCellRenderer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** A list of available library files. */
public class LibraryExplorerDockable extends Dockable implements DocumentListener, SearchTarget, FieldAccessor, IconAccessor, Openable {
    private Search    mSearch;
    private TreePanel mTreePanel;
    private Notifier  mNotifier;

    public static LibraryExplorerDockable get() {
        for (Dockable dockable : Workspace.get().getDock().getDockables()) {
            if (dockable instanceof LibraryExplorerDockable) {
                return (LibraryExplorerDockable) dockable;
            }
        }
        // Shouldn't be possible
        return null;
    }

    public LibraryExplorerDockable() {
        super(new BorderLayout());
        mNotifier = new Notifier();
        TreeRoot root = new TreeRoot(mNotifier);
        fillTree(collectLibraryFiles(), root);
        mTreePanel = new TreePanel(root);
        mTreePanel.setShowHeader(false);
        mTreePanel.addColumn(new TextTreeColumn(I18n.Text("Library Explorer"), this, this));
        mTreePanel.setAllowColumnDrag(false);
        mTreePanel.setAllowColumnResize(false);
        mTreePanel.setAllowColumnContextMenu(false);
        mTreePanel.setAllowRowDropFromExternal(false);
        mTreePanel.setAllowedRowDragTypes(0); // Turns off row dragging
        mTreePanel.setShowRowDivider(false);
        mTreePanel.setShowColumnDivider(false);
        mTreePanel.setUseBanding(false);
        mTreePanel.setUserSortable(false);
        mTreePanel.setOpenableProxy(this);
        Toolbar toolbar = new Toolbar();
        mSearch = new Search(this);
        toolbar.add(mSearch, Toolbar.LAYOUT_FILL);
        toolbar.add(new IconButton(Images.TOGGLE_OPEN, I18n.Text("Opens/closes all hierarchical rows"), () -> mTreePanel.toggleDisclosure()));
        toolbar.add(new IconButton(Images.REFRESH, I18n.Text("Refresh"), () -> refresh()));
        add(toolbar, BorderLayout.NORTH);
        add(mTreePanel, BorderLayout.CENTER);
    }

    @Override
    public String getDescriptor() {
        return "library_explorer";
    }

    @Override
    public Icon getTitleIcon() {
        return Images.FOLDER;
    }

    @Override
    public String getTitle() {
        return I18n.Text("Library Explorer");
    }

    @Override
    public String getTitleTooltip() {
        return getTitle();
    }

    @Override
    public String getField(TreeRow row) {
        return ((LibraryExplorerRow) row).getName();
    }

    @Override
    public RetinaIcon getIcon(TreeRow row) {
        return ((LibraryExplorerRow) row).getIcon();
    }

    @Override
    public void changedUpdate(DocumentEvent event) {
        documentChanged();
    }

    @Override
    public void insertUpdate(DocumentEvent event) {
        documentChanged();
    }

    @Override
    public void removeUpdate(DocumentEvent event) {
        documentChanged();
    }

    private void documentChanged() {
        // mOutline.reapplyRowFilter();
    }

    private void fillTree(List<?> lists, TreeContainerRow parent) {
        int count = lists.size();
        for (int i = 1; i < count; i++) {
            Object entry = lists.get(i);
            if (entry instanceof List<?>) {
                List<?>             subList = (List<?>) entry;
                LibraryDirectoryRow dir     = new LibraryDirectoryRow((String) subList.get(0));
                fillTree(subList, dir);
                parent.addRow(dir);
            } else {
                parent.addRow(new LibraryFileRow((Path) entry));
            }
        }
    }

    public void refresh() {
        TreeRoot    root     = mTreePanel.getRoot();
        Set<String> selected = new HashSet<>();
        for (TreeRow row : mTreePanel.getExplicitlySelectedRows()) {
            selected.add(((LibraryExplorerRow) row).getSelectionKey());
        }
        Set<String> open = new HashSet<>();
        for (TreeRow row : new TreeRowViewIterator(mTreePanel, root)) {
            if (row instanceof TreeContainerRow && mTreePanel.isOpen((TreeContainerRow) row) && row instanceof LibraryExplorerRow) {
                open.add(((LibraryExplorerRow) row).getSelectionKey());
            }
        }
        mNotifier.startBatch();
        root.removeRow(new ArrayList<>(root.getChildren()));
        fillTree(collectLibraryFiles(), root);
        mNotifier.endBatch();
        mTreePanel.setOpen(true, collectRowsToOpen(root, open, null));
        mTreePanel.select(collectRows(root, selected, null));
    }

    public List<Object> collectLibraryFiles() {
        List<Object> list = new ArrayList<>();
        list.add("GCS");
        list.add(collectLibraryFileLists(I18n.Text("Library"), Library.getSystemRootPath()));
        list.add(collectLibraryFileLists(I18n.Text("User Library"), Library.getUserRootPath()));
        return list;
    }

    private List<Object> collectLibraryFileLists(String name, Path root) {
        LibraryCollector collector = new LibraryCollector();
        try {
            Files.walkFileTree(root, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, collector);
        } catch (Exception exception) {
            Log.error(exception);
        }
        return collector.getResult(name);
    }

    private List<TreeContainerRow> collectRowsToOpen(TreeContainerRow parent, Set<String> selectors, List<TreeContainerRow> list) {
        if (list == null) {
            list = new ArrayList<>();
        }
        for (TreeRow row : parent.getChildren()) {
            if (row instanceof TreeContainerRow && row instanceof LibraryExplorerRow) {
                TreeContainerRow container = (TreeContainerRow) row;
                if (selectors.contains(((LibraryExplorerRow) row).getSelectionKey())) {
                    list.add(container);
                }
                collectRowsToOpen(container, selectors, list);
            }
        }
        return list;
    }

    private List<TreeRow> collectRows(TreeContainerRow parent, Set<String> selectors, List<TreeRow> list) {
        if (list == null) {
            list = new ArrayList<>();
        }
        for (TreeRow row : parent.getChildren()) {
            if (selectors.contains(((LibraryExplorerRow) row).getSelectionKey())) {
                list.add(row);
            }
            if (row instanceof TreeContainerRow) {
                collectRows((TreeContainerRow) row, selectors, list);
            }
        }
        return list;
    }

    @Override
    public boolean canOpenSelection() {
        return true;
    }

    @Override
    public void openSelection() {
        List<TreeContainerRow> containers = new ArrayList<>();
        boolean                hadFile    = false;
        for (TreeRow row : mTreePanel.getExplicitlySelectedRows()) {
            if (row instanceof TreeContainerRow) {
                containers.add((TreeContainerRow) row);
            } else {
                open(((LibraryFileRow) row).getPath());
                hadFile = true;
            }
        }
        if (!hadFile) {
            for (TreeContainerRow container : containers) {
                mTreePanel.setOpen(!mTreePanel.isOpen(container), container);
            }
        }
    }

    public Dockable getDockableFor(Path path) {
        for (Dockable dockable : getDockContainer().getDock().getDockables()) {
            if (dockable instanceof FileProxy) {
                File file = ((FileProxy) dockable).getBackingFile();
                if (file != null) {
                    try {
                        if (Files.isSameFile(path, file.toPath())) {
                            return dockable;
                        }
                    } catch (IOException ioe) {
                        Log.error(ioe);
                    }
                }
            }
        }
        return null;
    }

    public FileProxy open(Path path) {
        // See if it is already open
        FileProxy proxy = (FileProxy) getDockableFor(path);
        if (proxy == null) {
            // If it wasn't, load it and put it into the dock
            try {
                String ext = PathUtils.getExtension(path);
                if (FileType.ADVANTAGE.matchExtension(ext)) {
                    proxy = openAdvantageList(path);
                } else if (FileType.ADVANTAGE_MODIFIER.matchExtension(ext)) {
                    proxy = openAdvantageModifierList(path);
                } else if (FileType.EQUIPMENT.matchExtension(ext)) {
                    proxy = openEquipmentList(path);
                } else if (FileType.EQUIPMENT_MODIFIER.matchExtension(ext)) {
                    proxy = openEquipmentModifierList(path);
                } else if (FileType.SKILL.matchExtension(ext)) {
                    proxy = openSkillList(path);
                } else if (FileType.SPELL.matchExtension(ext)) {
                    proxy = openSpellList(path);
                } else if (FileType.NOTE.matchExtension(ext)) {
                    proxy = openNoteList(path);
                } else if (FileType.SHEET.matchExtension(ext)) {
                    proxy = dockSheet(new SheetDockable(new GURPSCharacter(path.toFile())));
                } else if (FileType.TEMPLATE.matchExtension(ext)) {
                    proxy = dockTemplate(new TemplateDockable(new Template(path.toFile())));
                } else if (FileType.PDF.matchExtension(ext)) {
                    proxy = dockPdf(new PdfDockable(new PdfRef(null, path.toFile(), 0), 1, null));
                }
            } catch (Throwable throwable) {
                StdFileDialog.showCannotOpenMsg(this, PathUtils.getLeafName(path, true), throwable);
                proxy = null;
            }
        } else {
            Dockable dockable = (Dockable) proxy;
            dockable.getDockContainer().setCurrentDockable(dockable);
        }
        if (proxy != null) {
            File file = proxy.getBackingFile();
            if (file != null) {
                RecentFilesMenu.addRecent(file);
            }
        }
        return proxy;
    }

    private FileProxy openAdvantageList(Path path) throws IOException {
        AdvantageList list = new AdvantageList();
        list.load(path.toFile());
        list.getModel().setLocked(true);
        return dockLibrary(new AdvantagesDockable(list));
    }

    private FileProxy openAdvantageModifierList(Path path) throws IOException {
        AdvantageModifierList list = new AdvantageModifierList();
        list.load(path.toFile());
        list.getModel().setLocked(true);
        return dockLibrary(new AdvantageModifiersDockable(list));
    }

    private FileProxy openEquipmentList(Path path) throws IOException {
        EquipmentList list = new EquipmentList();
        list.load(path.toFile());
        list.getModel().setLocked(true);
        return dockLibrary(new EquipmentDockable(list));
    }

    private FileProxy openEquipmentModifierList(Path path) throws IOException {
        EquipmentModifierList list = new EquipmentModifierList();
        list.load(path.toFile());
        list.getModel().setLocked(true);
        return dockLibrary(new EquipmentModifiersDockable(list));
    }

    private FileProxy openSkillList(Path path) throws IOException {
        SkillList list = new SkillList();
        list.load(path.toFile());
        list.getModel().setLocked(true);
        return dockLibrary(new SkillsDockable(list));
    }

    private FileProxy openSpellList(Path path) throws IOException {
        SpellList list = new SpellList();
        list.load(path.toFile());
        list.getModel().setLocked(true);
        return dockLibrary(new SpellsDockable(list));
    }

    private FileProxy openNoteList(Path path) throws IOException {
        NoteList list = new NoteList();
        list.load(path.toFile());
        list.getModel().setLocked(true);
        return dockLibrary(new NotesDockable(list));
    }

    /**
     * @param library The {@link LibraryDockable} to dock.
     * @return The {@link LibraryDockable} that was passed in.
     */
    public LibraryDockable dockLibrary(LibraryDockable library) {
        // Order of docking:
        // 1. Stack with another library
        // 2. Dock to the top of a template
        // 2. Dock to the right of a sheet
        // 3. Dock to the right of the library explorer
        Dockable template = null;
        Dockable sheet    = null;
        Dock     dock     = getDockContainer().getDock();
        for (Dockable dockable : dock.getDockables()) {
            if (dockable instanceof LibraryDockable) {
                dockable.getDockContainer().stack(library);
                return library;
            }
            if (template == null && dockable instanceof TemplateDockable) {
                template = dockable;
            }
            if (sheet == null && dockable instanceof SheetDockable) {
                sheet = dockable;
            }
        }
        if (template != null) {
            dock.dock(library, template, DockLocation.NORTH);
        } else {
            dock.dock(library, Objects.requireNonNullElse(sheet, this), DockLocation.EAST);
        }
        return library;
    }

    /**
     * @param sheet The {@link SheetDockable} to dock.
     * @return The {@link SheetDockable} that was passed in.
     */
    public SheetDockable dockSheet(SheetDockable sheet) {
        // Order of docking:
        // 1. Stack with another sheet
        // 2. Dock to the left of a library or template
        // 3. Dock to the right of the library explorer
        Dockable other = null;
        Dock     dock  = getDockContainer().getDock();
        for (Dockable dockable : dock.getDockables()) {
            if (dockable instanceof SheetDockable) {
                dockable.getDockContainer().stack(sheet);
                return sheet;
            }
            if (other == null && (dockable instanceof TemplateDockable || dockable instanceof LibraryDockable)) {
                other = dockable;
            }
        }
        if (other != null) {
            DockContainer dc     = other.getDockContainer();
            DockLayout    layout = dc.getDock().getLayout().findLayout(dc);
            if (layout.isVertical()) {
                dock.dock(sheet, layout, DockLocation.WEST);
            } else {
                dock.dock(sheet, other, DockLocation.WEST);
            }
        } else {
            dock.dock(sheet, this, DockLocation.EAST);
        }
        return sheet;
    }

    /**
     * @param template The {@link TemplateDockable} to dock.
     * @return The {@link TemplateDockable} that was passed in.
     */
    public TemplateDockable dockTemplate(TemplateDockable template) {
        // Order of docking:
        // 1. Stack with another template
        // 2. Dock to the bottom of a library
        // 3. Dock to the right of a sheet
        // 4. Dock to the right of the library explorer
        Dockable sheet   = null;
        Dockable library = null;
        Dock     dock    = getDockContainer().getDock();
        for (Dockable dockable : dock.getDockables()) {
            if (dockable instanceof TemplateDockable) {
                dockable.getDockContainer().stack(template);
                return template;
            }
            if (sheet == null && dockable instanceof SheetDockable) {
                sheet = dockable;
            }
            if (library == null && dockable instanceof LibraryDockable) {
                library = dockable;
            }
        }
        if (library != null) {
            dock.dock(template, library, DockLocation.SOUTH);
        } else {
            dock.dock(template, Objects.requireNonNullElse(sheet, this), DockLocation.EAST);
        }
        return template;
    }

    /**
     * @param pdf The {@link PdfDockable} to dock.
     * @return The {@link PdfDockable} that was passed in.
     */
    public PdfDockable dockPdf(PdfDockable pdf) {
        // Order of docking:
        // 1. Stack with another pdf
        // 2. Dock to the right of a sheet
        // 2. Dock to the left of a library or template
        // 3. Dock to the right of the library explorer
        Dockable sheet = null;
        Dockable other = null;
        Dock     dock  = getDockContainer().getDock();
        for (Dockable dockable : dock.getDockables()) {
            if (dockable instanceof PdfDockable) {
                dockable.getDockContainer().stack(pdf);
                return pdf;
            }
            if (sheet == null && dockable instanceof SheetDockable) {
                sheet = dockable;
            }
            if (other == null && (dockable instanceof TemplateDockable || dockable instanceof LibraryDockable)) {
                other = dockable;
            }
        }
        if (sheet != null) {
            dock.dock(pdf, sheet, DockLocation.EAST);
        } else if (other != null) {
            DockContainer dc     = other.getDockContainer();
            DockLayout    layout = dc.getDock().getLayout().findLayout(dc);
            if (layout.isVertical()) {
                dock.dock(pdf, layout, DockLocation.WEST);
            } else {
                dock.dock(pdf, other, DockLocation.WEST);
            }
        } else {
            dock.dock(pdf, this, DockLocation.EAST);
        }
        return pdf;
    }

    @Override
    public boolean isJumpToSearchAvailable() {
        return mSearch.isEnabled() && mSearch != KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner();
    }

    @Override
    public void jumpToSearchField() {
        mSearch.requestFocusInWindow();
    }

    @Override
    public ListCellRenderer<Object> getSearchRenderer() {
        return new LibraryExplorerRowRenderer();
    }

    @Override
    public List<Object> search(String filter) {
        ArrayList<Object> list = new ArrayList<>();
        filter = filter.toLowerCase();
        collect(mTreePanel.getRoot(), filter, list);
        return list;
    }

    private static void collect(TreeRow row, String text, ArrayList<Object> list) {
        if (row instanceof LibraryExplorerRow) {
            if (((LibraryExplorerRow) row).getName().toLowerCase().contains(text)) {
                list.add(row);
            }
        }
        if (row instanceof TreeContainerRow) {
            for (TreeRow child : ((TreeContainerRow) row).getChildren()) {
                collect(child, text, list);
            }
        }
    }

    @Override
    public void searchSelect(List<Object> selection) {
        List<TreeRow> list = new ArrayList<>();
        for (Object one : selection) {
            if (one instanceof TreeRow) {
                list.add((TreeRow) one);

            }
        }
        mTreePanel.setParentsOpen(list);
        mTreePanel.select(list);
        mTreePanel.requestFocus();
    }
}