package com.wintertodt.scouter;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScouterPanelTest
{
    @Test public void selectionSurvivesRefreshAndHopUsesSelectedWorld() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            AtomicInteger hopped = new AtomicInteger();
            ScouterPanel panel = new ScouterPanel(hopped::set);
            panel.update(Arrays.asList(row(307, "82%", "Local"), row(309, "60%", "2s")), "Connected", true);
            JTable table = find(panel, JTable.class);
            table.setRowSelectionInterval(1, 1);
            panel.update(Arrays.asList(row(307, "81%", "Local"), row(309, "59%", "3s"), row(402, "Unknown", "—")), "Connected", true);
            hopButton(panel).doClick();
            assertEquals(309, hopped.get());
            panel.update(Arrays.asList(row(307, "81%", "Local")), "Local scouting only", false);
            hopButton(panel).doClick();
            assertEquals(309, hopped.get());
        });
    }

    @Test public void rendersPreviewAtSidebarWidth() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            ScouterPanel panel = new ScouterPanel(world -> {});
            panel.update(Arrays.asList(row(307, "82%", "Local"), row(309, "1:25", "3s"),
                row(311, "Awaiting start", "4s"), row(389, "Stale", "32s"), row(402, "Unknown", "—")), "Connected", true);
            panel.setSize(225, 360);
            layout(panel);
            BufferedImage image = new BufferedImage(225, 360, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            panel.paint(graphics);
            graphics.dispose();
            try
            {
                File file = new File("build/reports/panel-preview.png");
                file.getParentFile().mkdirs();
                ImageIO.write(image, "png", file);
            }
            catch (Exception error) { throw new AssertionError(error); }
        });
    }

    private static ScouterPanel.Row row(int id, String value, String age)
    { return new ScouterPanel.Row(id, id == 307, value, age); }

    private static JButton hopButton(ScouterPanel panel)
    {
        for (Component child : panel.getComponents())
        { if (child instanceof JButton) { return (JButton) child; } }
        throw new AssertionError("Hop button missing");
    }

    private static void layout(Container parent)
    {
        parent.doLayout();
        for (Component child : parent.getComponents()) { if (child instanceof Container) { layout((Container) child); } }
    }

    private static <T> T find(Container parent, Class<T> type)
    {
        for (Component child : parent.getComponents())
        {
            if (type.isInstance(child)) { return type.cast(child); }
            if (child instanceof Container)
            {
                T found = find((Container) child, type);
                if (found != null) { return found; }
            }
        }
        return null;
    }
}
