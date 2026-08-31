/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.HeadlessException;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.accessibility.AccessibleContext;
import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.FileChooserUI;

import org.apache.log4j.Logger;

import com.romraider.ui.ThemeToken;
import com.romraider.ui.UiThemeService;

/** File chooser whose dialog uses the same client-side title bar as the app. */
public class IntegratedFileChooser extends JFileChooser {
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(
            IntegratedFileChooser.class);
    private static final Pattern DESCRIPTION_EXTENSION = Pattern.compile(
            "\\.([A-Za-z0-9][A-Za-z0-9_-]*)");

    public IntegratedFileChooser() {
        super();
    }

    public IntegratedFileChooser(File currentDirectory) {
        super(currentDirectory);
    }

    public IntegratedFileChooser(String currentDirectoryPath) {
        super(currentDirectoryPath);
    }

    @Override
    public int showOpenDialog(Component parent) throws HeadlessException {
        return showDialog(parent, false);
    }

    @Override
    public int showSaveDialog(Component parent) throws HeadlessException {
        return showDialog(parent, true);
    }

    private int showDialog(Component parent, boolean save) {
        if (!nativeDialogsEnabled()) {
            return save ? super.showSaveDialog(parent)
                    : super.showOpenDialog(parent);
        }
        if (isWindows() && getFileSelectionMode() != DIRECTORIES_ONLY) {
            return showWindowsDialog(parent, save);
        }
        String kdialog = findKDialog();
        if (kdialog == null || getFileSelectionMode() == FILES_AND_DIRECTORIES) {
            return save ? super.showSaveDialog(parent)
                    : super.showOpenDialog(parent);
        }
        try {
            Process process = new ProcessBuilder(
                    buildKDialogCommand(kdialog, save))
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8).trim();
            int exitCode = process.waitFor();
            if (exitCode != 0 || output.isEmpty()) return CANCEL_OPTION;
            applySelection(output);
            return APPROVE_OPTION;
        } catch (IOException exception) {
            LOGGER.warn("KDE file dialog unavailable; using Swing chooser.",
                    exception);
            return save ? super.showSaveDialog(parent)
                    : super.showOpenDialog(parent);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return CANCEL_OPTION;
        }
    }

    private int showWindowsDialog(Component parent, boolean save) {
        Window owner = parent == null ? null
                : SwingUtilities.getWindowAncestor(parent);
        String title = getDialogTitle();
        int mode = save ? FileDialog.SAVE : FileDialog.LOAD;
        final FileDialog dialog;
        if (owner instanceof Dialog) {
            dialog = new FileDialog((Dialog) owner, title, mode);
        } else {
            dialog = new FileDialog((Frame) (owner instanceof Frame
                    ? owner : null), title, mode);
        }
        File current = getCurrentDirectory();
        if (current != null) dialog.setDirectory(current.getAbsolutePath());
        File selected = getSelectedFile();
        if (selected != null) {
            if (selected.getParentFile() != null) {
                dialog.setDirectory(selected.getParentFile().getAbsolutePath());
            }
            dialog.setFile(selected.getName());
        }
        dialog.setMultipleMode(!save && isMultiSelectionEnabled());
        dialog.setVisible(true);

        File[] files = dialog.getFiles();
        if (files.length == 0 && dialog.getFile() != null) {
            files = new File[] {new File(dialog.getDirectory(),
                    dialog.getFile())};
        }
        dialog.dispose();
        if (files.length == 0) return CANCEL_OPTION;
        applySelection(files);
        return APPROVE_OPTION;
    }

    List<String> buildKDialogCommand(String executable, boolean save) {
        List<String> command = new ArrayList<String>();
        command.add(executable);
        if (getFileSelectionMode() == DIRECTORIES_ONLY) {
            command.add("--getexistingdirectory");
            command.add(startingPath(false));
        } else {
            command.add(save ? "--getsavefilename" : "--getopenfilename");
            command.add(startingPath(save));
            command.add(kdeFilter());
            if (!save && isMultiSelectionEnabled()) {
                command.add("--multiple");
                command.add("--separate-output");
            }
        }
        String title = getDialogTitle();
        if (title != null && !title.trim().isEmpty()) {
            command.add("--title");
            command.add(title.trim());
        }
        return command;
    }

    private String startingPath(boolean includeSelectedFile) {
        File selected = getSelectedFile();
        if (includeSelectedFile && selected != null) {
            return selected.getAbsolutePath();
        }
        File current = getCurrentDirectory();
        if (current != null) return current.getAbsolutePath();
        return System.getProperty("user.home", ".");
    }

    private String kdeFilter() {
        FileFilter filter = getFileFilter();
        List<String> extensions = new ArrayList<String>();
        if (filter instanceof FileNameExtensionFilter) {
            for (String extension
                    : ((FileNameExtensionFilter) filter).getExtensions()) {
                extensions.add(extension);
            }
        } else if (filter != null) {
            Matcher matcher = DESCRIPTION_EXTENSION.matcher(
                    filter.getDescription());
            while (matcher.find()) extensions.add(matcher.group(1));
        }
        StringBuilder patterns = new StringBuilder();
        for (String extension : extensions) {
            if (patterns.length() > 0) patterns.append(' ');
            patterns.append("*.").append(extension);
        }
        if (patterns.length() == 0) patterns.append('*');
        String description = filter == null ? "All files"
                : filter.getDescription().replace('|', ' ').replace('\n', ' ');
        return patterns.append('|').append(description).toString();
    }

    private void applySelection(String output) {
        String[] paths = output.split("\\R");
        List<File> selected = new ArrayList<File>();
        for (String path : paths) {
            if (!path.trim().isEmpty()) selected.add(new File(path.trim()));
        }
        applySelection(selected.toArray(new File[selected.size()]));
    }

    private void applySelection(File[] selected) {
        if (selected.length == 0) return;
        File first = selected[0];
        setSelectedFile(first);
        if (isMultiSelectionEnabled()) {
            setSelectedFiles(selected);
        }
        File directory = first.isDirectory() ? first : first.getParentFile();
        if (directory != null) setCurrentDirectory(directory);
    }

    private static String findKDialog() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT)
                .contains("linux")) return null;
        String path = System.getenv("PATH");
        if (path == null) return null;
        for (String directory : path.split(Pattern.quote(File.pathSeparator))) {
            File executable = new File(directory, "kdialog");
            if (executable.isFile() && executable.canExecute()) {
                return executable.getAbsolutePath();
            }
        }
        return null;
    }

    static boolean nativeDialogsEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty(
                "romraider2.nativeFileDialogs", "true"));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT)
                .contains("windows");
    }

    @Override
    protected JDialog createDialog(Component parent) throws HeadlessException {
        FileChooserUI chooserUi = getUI();
        String title = chooserUi.getDialogTitle(this);
        putClientProperty(AccessibleContext.ACCESSIBLE_DESCRIPTION_PROPERTY,
                title);

        Window owner = parent == null ? null
                : SwingUtilities.getWindowAncestor(parent);
        final JDialog dialog;
        if (owner instanceof Frame) {
            dialog = new JDialog((Frame) owner, title, true);
        } else {
            dialog = new JDialog((Dialog) owner, title, true);
        }
        dialog.setName("INTEGRATED FILE CHOOSER DIALOG");
        dialog.setComponentOrientation(getComponentOrientation());
        dialog.setUndecorated(true);
        dialog.getRootPane().setWindowDecorationStyle(JRootPane.NONE);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        Container content = dialog.getContentPane();
        content.setLayout(new BorderLayout());
        content.add(IntegratedOptionDialog.createHeader(dialog, title),
                BorderLayout.NORTH);
        content.add(this, BorderLayout.CENTER);
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(
                UiThemeService.getInstance().color(
                        ThemeToken.RAISED_SURFACE)));
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        return dialog;
    }
}
