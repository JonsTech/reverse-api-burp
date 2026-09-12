package io.github.reverseapi.export;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class OpenApiExporter {
    public void save(Component parent, String content, String extension, String description) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export " + description);
        chooser.setSelectedFile(new File((description.startsWith("Postman") ? "reverseapi.postman_collection." : "openapi.") + extension));
        chooser.setFileFilter(new FileNameExtensionFilter(description, extension));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return;
        File target = chooser.getSelectedFile();
        if (!target.getName().toLowerCase().endsWith("." + extension)) target = new File(target.getParentFile(), target.getName() + "." + extension);
        if (target.exists() && JOptionPane.showConfirmDialog(parent, "Overwrite " + target.getName() + "?", "Confirm overwrite",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        try { Files.writeString(target.toPath(), content, StandardCharsets.UTF_8); }
        catch (IOException e) { JOptionPane.showMessageDialog(parent, e.getMessage(), "Export failed", JOptionPane.ERROR_MESSAGE); }
    }
}

