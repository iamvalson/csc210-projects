package serp.viz;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import serp.model.Ranked;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Renders a ranked-results bar chart to a PNG file for the CLI path. The
 * interactive GUI path uses its own JavaFX BarChart (serp.gui.MinerApp)
 * instead of this class - JFreeChart is kept here only because it remains the
 * simplest way to produce a saved PNG without spinning up a JavaFX toolkit for
 * a headless CLI run.
 */
public class ChartRenderer {

    private static final int DEFAULT_WIDTH = 900;
    private static final int DEFAULT_HEIGHT = 600;

    public JFreeChart createBarChart(String title, List<? extends Ranked> items, int maxItems) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        items.stream()
                .limit(maxItems)
                .forEach(item -> dataset.addValue(item.count(), "Count", item.label()));

        return ChartFactory.createBarChart(
                title,
                "",
                "Number of sources",
                dataset,
                PlotOrientation.HORIZONTAL,
                false, true, false);
    }

    public void saveAsPng(JFreeChart chart, Path outputFile) throws IOException {
        ChartUtils.saveChartAsPNG(outputFile.toFile(), chart, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }
}
