package com.wintertodt.scouter;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import net.runelite.client.ui.PluginPanel;

final class ScouterPanel extends PluginPanel
{
    private final Model model = new Model();
    private final JTable table = new JTable(model);
    private final JLabel status = new JLabel("Connecting…");
    private final JButton hop = new JButton("Hop to selected world");
    private final IntConsumer onHop;
    private volatile boolean viewing;

    @Override public void onActivate() { viewing = true; }
    @Override public void onDeactivate() { viewing = false; }
    boolean isViewing() { return viewing; }

    ScouterPanel(IntConsumer onHop)
    {
        this.onHop = onHop;
        setLayout(new BorderLayout(0, 8));
        JLabel title = new JLabel("Wintertodt Scouter");
        JPanel heading = new JPanel(new BorderLayout(0, 6));
        heading.add(title, BorderLayout.NORTH);
        heading.add(status, BorderLayout.SOUTH);
        add(heading, BorderLayout.NORTH);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(28);
        table.getColumnModel().getColumn(0).setPreferredWidth(48);
        table.getColumnModel().getColumn(1).setPreferredWidth(112);
        table.getColumnModel().getColumn(2).setPreferredWidth(42);
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        table.setToolTipText("Select a world and press Hop. Reports expire after 30 seconds. * marks your current world.");
        JScrollPane scroll = new JScrollPane(table);
        scroll.setColumnHeaderView(table.getTableHeader());
        scroll.setPreferredSize(new Dimension(220, 260));
        add(scroll, BorderLayout.CENTER);
        add(hop, BorderLayout.SOUTH);
        hop.addActionListener(event -> hopSelected());
        table.addMouseListener(new MouseAdapter()
        {
            @Override public void mouseClicked(MouseEvent event)
            {
                if (event.getButton() == MouseEvent.BUTTON1 && event.getClickCount() == 2
                    && table.rowAtPoint(event.getPoint()) >= 0) { hopSelected(); }
            }
        });
    }

    private void hopSelected()
    {
        int selected = table.getSelectedRow();
        if (hop.isEnabled() && selected >= 0)
        { onHop.accept(model.rows.get(table.convertRowIndexToModel(selected)).world); }
    }

    void update(List<Row> rows, String message, boolean hopping)
    {
        int selected = table.getSelectedRow();
        int selectedWorld = selected < 0 ? -1 : model.rows.get(table.convertRowIndexToModel(selected)).world;
        model.rows = rows;
        model.fireTableDataChanged();
        for (int i = 0; i < rows.size(); i++)
        {
            if (rows.get(i).world == selectedWorld)
            {
                int view = table.convertRowIndexToView(i);
                table.setRowSelectionInterval(view, view);
                break;
            }
        }
        status.setText(message);
        status.setToolTipText(message);
        hop.setEnabled(hopping);
    }

    static final class Row
    {
        final int world;
        final String label;
        final String value;
        final String age;
        Row(int world, boolean current, String value, String age)
        { this.world = world; label = world + (current ? " *" : ""); this.value = value; this.age = age; }
    }

    private static final class Model extends AbstractTableModel
    {
        private List<Row> rows = new ArrayList<>();
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return 3; }
        @Override public String getColumnName(int column) { return new String[]{"World", "Energy / starts", "Age"}[column]; }
        @Override public Object getValueAt(int row, int column)
        {
            Row value = rows.get(row);
            return column == 0 ? value.label : column == 1 ? value.value : value.age;
        }
    }
}
