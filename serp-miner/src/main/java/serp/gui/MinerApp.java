package serp.gui;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import serp.app.Mode;
import serp.app.Pipeline;
import serp.app.PipelineResult;
import serp.model.PageSummary;
import serp.model.Ranked;

import java.util.List;

/**
 * JavaFX front-end for the pipeline: enter a query, pick a mode, watch live
 * progress while pages are fetched concurrently, then browse the ranked
 * chart, the combined summary, and a per-result card list with clickable
 * links and a short per-page summary underneath each title/link.
 */
public class MinerApp extends Application {

    private static final int NUM_RESULTS = 10;
    private static final int THREAD_POOL_SIZE = 8;
    private static final int CHART_MAX_ITEMS = 20;

    private final TextField queryField = new TextField();
    private final ComboBox<Mode> modeBox = new ComboBox<>(FXCollections.observableArrayList(Mode.values()));
    private final Button runButton = new Button("Run");
    private final Label queryTitleLabel = new Label();
    private final TextArea summaryArea = new TextArea();
    private final ListView<PageSummary> serpResultsList = new ListView<>();
    private final ProgressIndicator progressIndicator = new ProgressIndicator();
    private final Label statusLabel = new Label(" ");
    private SplitPane splitPane;

    @Override
    public void start(Stage stage) {
        queryField.setPromptText("Enter a query...");
        queryField.setPrefColumnCount(30);
        modeBox.getSelectionModel().selectFirst();
        summaryArea.setEditable(false);
        summaryArea.setWrapText(true);

        HBox controls = new HBox(8,
                new Label("Query:"), queryField,
                new Label("Mode:"), modeBox,
                runButton);
        controls.setPadding(new Insets(8, 8, 4, 8));
        controls.setAlignment(Pos.CENTER_LEFT);

        queryTitleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        queryTitleLabel.setPadding(new Insets(0, 8, 8, 8));

        VBox topBox = new VBox(controls, queryTitleLabel);

        ScrollPane summaryScroll = new ScrollPane(summaryArea);
        summaryScroll.setFitToWidth(true);
        summaryScroll.setFitToHeight(true);

        Tab summaryTab = new Tab("Summary", summaryScroll);
        summaryTab.setClosable(false);

        Tab serpTab = new Tab("SERP Results", buildSerpResultsList());
        serpTab.setClosable(false);

        TabPane tabPane = new TabPane(summaryTab, serpTab);

        splitPane = new SplitPane(tabPane, buildChart("Results", List.of()));
        splitPane.setDividerPositions(0.45);

        progressIndicator.setMaxSize(18, 18);
        progressIndicator.setVisible(false);
        HBox statusBox = new HBox(8, progressIndicator, statusLabel);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        statusBox.setPadding(new Insets(4, 8, 4, 8));

        BorderPane root = new BorderPane();
        root.setTop(topBox);
        root.setCenter(splitPane);
        root.setBottom(statusBox);

        runButton.setOnAction(e -> onRun());

        stage.setTitle("SERP Miner");
        stage.setScene(new Scene(root, 1200, 750));
        stage.show();
    }

    private ListView<PageSummary> buildSerpResultsList() {
        serpResultsList.setCellFactory(lv -> new PageSummaryCell(serpResultsList));
        serpResultsList.setPlaceholder(new Label("Run a query to see SERP results here."));
        return serpResultsList;
    }

    /** One "card" per SERP result: title, clickable link, short per-page summary - all word-wrapped and fully readable. */
    private class PageSummaryCell extends ListCell<PageSummary> {
        private final Label titleLabel = new Label();
        private final Hyperlink linkButton = new Hyperlink();
        private final Label summaryLabel = new Label();
        private final VBox card = new VBox(4, titleLabel, linkButton, summaryLabel);

        PageSummaryCell(ListView<PageSummary> owner) {
            titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
            titleLabel.setWrapText(true);
            linkButton.setWrapText(true);
            linkButton.setStyle("-fx-font-size: 11px;");
            linkButton.setOnAction(e -> getHostServices().showDocument(linkButton.getText()));
            summaryLabel.setWrapText(true);
            summaryLabel.setStyle("-fx-opacity: 0.85;");
            card.setPadding(new Insets(10, 6, 10, 6));
            card.setStyle("-fx-border-color: transparent transparent #cccccc transparent; -fx-border-width: 0 0 1 0;");

            double horizontalPadding = 32;
            titleLabel.maxWidthProperty().bind(owner.widthProperty().subtract(horizontalPadding));
            linkButton.maxWidthProperty().bind(owner.widthProperty().subtract(horizontalPadding));
            summaryLabel.maxWidthProperty().bind(owner.widthProperty().subtract(horizontalPadding));
        }

        @Override
        protected void updateItem(PageSummary item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
            } else {
                titleLabel.setText(item.title());
                linkButton.setText(item.link());
                summaryLabel.setText(item.summary());
                setGraphic(card);
            }
        }
    }

    private void onRun() {
        String query = queryField.getText().trim();
        if (query.isEmpty()) {
            showAlert(AlertType.WARNING, "Enter a query first.");
            return;
        }

        String apiKey = System.getenv("SERPER_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            showAlert(AlertType.ERROR, "Set the SERPER_API_KEY environment variable before running.");
            return;
        }

        Mode mode = modeBox.getValue();

        runButton.setDisable(true);
        progressIndicator.setVisible(true);
        queryTitleLabel.setText("Results for: \"" + query + "\"");
        statusLabel.setText("Starting...");
        summaryArea.clear();
        serpResultsList.getItems().clear();

        Task<PipelineResult> task = new Task<>() {
            @Override
            protected PipelineResult call() throws Exception {
                Pipeline pipeline = new Pipeline(apiKey, THREAD_POOL_SIZE);
                return pipeline.run(query, mode, NUM_RESULTS, this::updateMessage);
            }
        };
        task.messageProperty().addListener((obs, oldMessage, newMessage) -> statusLabel.setText(newMessage));

        task.setOnSucceeded(event -> {
            runButton.setDisable(false);
            progressIndicator.setVisible(false);
            displayResult(task.getValue());
        });
        task.setOnFailed(event -> {
            runButton.setDisable(false);
            progressIndicator.setVisible(false);
            Throwable error = task.getException();
            String message = error != null ? error.getMessage() : "Unknown error";
            statusLabel.setText("Error: " + message);
            showAlert(AlertType.ERROR, message);
        });

        Thread worker = new Thread(task, "pipeline-worker");
        worker.setDaemon(true);
        worker.start();
    }

    private void displayResult(PipelineResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(result.resultLabel()).append(":\n\n");
        for (Ranked item : result.ranked()) {
            sb.append(String.format("%-45s %d%n", item.label(), item.count()));
        }
        sb.append("\nSummary:\n").append(result.summary());
        summaryArea.setText(sb.toString());
        summaryArea.positionCaret(0);

        serpResultsList.setItems(FXCollections.observableArrayList(result.pageSummaries()));

        BarChart<Number, String> chart = buildChart(result.resultLabel() + " — \"" + result.query() + "\"", result.ranked());
        splitPane.getItems().set(1, chart);
    }

    private BarChart<Number, String> buildChart(String title, List<? extends Ranked> items) {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Number of sources");
        CategoryAxis yAxis = new CategoryAxis();

        BarChart<Number, String> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle(title);
        chart.setLegendVisible(false);

        XYChart.Series<Number, String> series = new XYChart.Series<>();
        items.stream()
                .limit(CHART_MAX_ITEMS)
                .forEach(item -> series.getData().add(new XYChart.Data<>(item.count(), item.label())));
        chart.getData().add(series);

        return chart;
    }

    private void showAlert(AlertType type, String message) {
        Alert alert = new Alert(type, message);
        alert.showAndWait();
    }
}
