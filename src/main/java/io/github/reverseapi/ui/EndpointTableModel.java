package io.github.reverseapi.ui;

import io.github.reverseapi.model.ApiOperation;
import io.github.reverseapi.model.CaptureStore;

import javax.swing.table.AbstractTableModel;
import java.util.List;

public final class EndpointTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {"Include", "Host", "Method", "Normalized path", "Original example", "Status", "Request type", "Response type", "Count", "Confidence / reason"};
    private final CaptureStore store;
    private List<ApiOperation> rows = List.of();

    public EndpointTableModel(CaptureStore store) { this.store = store; refresh(); }
    public void refresh() { rows = store.operations(); fireTableDataChanged(); }
    public ApiOperation row(int modelRow) { return rows.get(modelRow); }
    @Override public int getRowCount() { return rows.size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int column) { return COLUMNS[column]; }
    @Override public Class<?> getColumnClass(int column) { return column == 0 ? Boolean.class : column == 8 ? Integer.class : String.class; }
    @Override public boolean isCellEditable(int row, int column) { return column == 0 || column == 3; }

    @Override public Object getValueAt(int rowIndex, int columnIndex) {
        ApiOperation op = rows.get(rowIndex); var sample = op.latest();
        return switch (columnIndex) {
            case 0 -> op.included(); case 1 -> op.host(); case 2 -> op.method(); case 3 -> op.normalizedPath();
            case 4 -> op.originalExamplePath(); case 5 -> String.valueOf(sample.statusCode());
            case 6 -> sample.requestContentType(); case 7 -> sample.responseContentType(); case 8 -> op.observations();
            case 9 -> op.confidence() + "% — " + op.reason(); default -> "";
        };
    }

    @Override public void setValueAt(Object value, int rowIndex, int columnIndex) {
        ApiOperation op = rows.get(rowIndex);
        if (columnIndex == 0) op.included(Boolean.TRUE.equals(value));
        if (columnIndex == 3 && value != null) {
            String path = value.toString().trim();
            if (!path.isBlank()) op.normalizedPath(path.startsWith("/") ? path : "/" + path);
        }
        store.changed(); fireTableRowsUpdated(rowIndex, rowIndex);
    }
}
