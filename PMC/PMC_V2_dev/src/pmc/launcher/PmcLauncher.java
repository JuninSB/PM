package pmc.launcher;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

public final class PmcLauncher extends Application {
    private final Path root = findRoot();
    private final java.io.File javaExe = root.resolve("main/bin/java.exe").toFile();
    private final java.io.File sklauncher = root.resolve("main/SKlauncher.jar").toFile();
    private final Path game = root.resolve("game");
    private final Path logFile = game.resolve("sklauncher/sklauncher_logs.txt");
    private final Path profileDir = game.resolve(".pmc");
    private final Path profileFile = profileDir.resolve("user.properties");
    private final ScheduledExecutorService watcher = Executors.newScheduledThreadPool(3, r -> {
        Thread t = new Thread(r, "PMC-Worker");
        t.setDaemon(true);
        return t;
    });
    private final List<StatusRow> rows = new ArrayList<>();
    private TextArea logArea;
    private Button startButton;
    private Label processState;
    private Process process;
    private long logPosition;
    private long launchStartedAt;
    private boolean autoClosing;
    private Stage mainStage;

    @Override
    public void start(Stage stage) {
        mainStage = stage;
        stage.setTitle("PMC V3 - Portable Minecraft");
        stage.setMinWidth(700);
        stage.setMinHeight(560);

        Label title = new Label("PMC V3");
        title.getStyleClass().add("title");
        Label subtitle = new Label("Portable Minecraft");
        subtitle.getStyleClass().add("subtitle");

        GridPane statusGrid = new GridPane();
        statusGrid.setHgap(18);
        statusGrid.setVgap(9);
        statusGrid.setPadding(new Insets(16, 0, 12, 0));
        addStatus(statusGrid, "Java");
        addStatus(statusGrid, "SKlauncher");
        addStatus(statusGrid, "Minecraft");
        addStatus(statusGrid, "Fabric");
        addStatus(statusGrid, "Mods");
        addStatus(statusGrid, "WorkDir");

        startButton = new Button("INICIAR MINECRAFT");
        startButton.getStyleClass().add("primary");
        startButton.setOnAction(e -> startSkLauncher());
        processState = new Label("Pronto");
        processState.getStyleClass().add("state");
        HBox actions = new HBox(10, startButton, processState);
        actions.setAlignment(Pos.CENTER_LEFT);

        Button openGame = new Button("ABRIR PASTA DO JOGO");
        openGame.setOnAction(e -> openPath(game));
        Button openLog = new Button("ABRIR LOG");
        openLog.setOnAction(e -> openPath(logFile));
        Button restart = new Button("REINICIAR LAUNCHER");
        restart.setOnAction(e -> restartLauncher(stage));
        HBox secondary = new HBox(8, openGame, openLog, restart);
        secondary.setAlignment(Pos.CENTER_LEFT);

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(false);
        logArea.setPromptText("Os logs do PMC e do SKlauncher aparecerão aqui...");
        VBox.setVgrow(logArea, Priority.ALWAYS);
        Label logTitle = new Label("LOGS");
        logTitle.getStyleClass().add("section");

        VBox top = new VBox(3, title, subtitle, statusGrid, actions, secondary);
        top.setPadding(new Insets(22, 26, 14, 26));
        VBox center = new VBox(8, logTitle, logArea);
        center.setPadding(new Insets(0, 26, 22, 26));
        VBox.setVgrow(center, Priority.ALWAYS);
        BorderPane layout = new BorderPane();
        layout.setTop(top);
        layout.setCenter(center);

        Scene scene = new Scene(layout, 760, 640);
        String stylesheet = URLEncoder.encode(css(), StandardCharsets.UTF_8).replace("+", "%20");
        scene.getStylesheets().add(URI.create("data:text/css," + stylesheet).toString());
        stage.setScene(scene);
        stage.show();
        stage.setOnCloseRequest(e -> {
            if (!autoClosing) shutdown();
        });

        append("[PMC] Verificando arquivos...");
        inspectFiles();
        askForNameIfNeeded();
        watchLog();
    }

    private void addStatus(GridPane grid, String name) {
        Label key = new Label(name);
        key.getStyleClass().add("key");
        Label value = new Label("Verificando...");
        value.getStyleClass().add("value");
        grid.add(key, 0, rows.size());
        grid.add(value, 1, rows.size());
        rows.add(new StatusRow(name, value));
    }

    private void inspectFiles() {
        setStatus("Java", javaExe.isFile() ? "OK  Java " + javaVersion() : "ERRO  JRE portátil não encontrado", javaExe.isFile());
        setStatus("SKlauncher", sklauncher.isFile() ? "OK  Encontrado" : "ERRO  SKlauncher.jar ausente", sklauncher.isFile());
        boolean gameOk = Files.isDirectory(game);
        setStatus("WorkDir", gameOk ? "OK  " + game : "ERRO  pasta game ausente", gameOk);
        Path version = game.resolve("versions/26.2");
        setStatus("Minecraft", Files.isDirectory(version) ? "OK  26.2" : "AVISO  26.2 não encontrado", Files.isDirectory(version));
        boolean fabric = Files.isDirectory(game.resolve("versions/fabric-loader-0.19.4-26.2")) || hasFabricFile();
        setStatus("Fabric", fabric ? "OK  Encontrado" : "AVISO  Não encontrado", fabric);
        boolean mods = Files.isDirectory(game.resolve("mods"));
        setStatus("Mods", mods ? "OK  Encontrados" : "AVISO  Pasta não encontrada", mods);
        if (javaExe.isFile()) append("[PMC] Java encontrado: " + javaVersion()); else append("[PMC] ERRO: Java portátil não encontrado em jre/bin/java.exe");
        if (sklauncher.isFile()) append("[PMC] SKlauncher encontrado"); else append("[PMC] ERRO: SKlauncher.jar não encontrado");
    }

    private boolean hasFabricFile() {
        try (var stream = Files.walk(game.resolve(".fabric"))) {
            return stream.anyMatch(p -> p.getFileName().toString().toLowerCase().contains("fabric"));
        } catch (IOException e) { return false; }
    }

    private String javaVersion() {
        try {
            Process p = new ProcessBuilder(javaExe.toString(), "-version").redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                p.waitFor(3, TimeUnit.SECONDS);
                if (line != null) return line.replace("java version ", "").replace("\"", "");
            }
        } catch (Exception ignored) { }
        return "desconhecida";
    }

    private void startSkLauncher() {
        if (process != null && process.isAlive()) return;
        if (!javaExe.isFile() || !sklauncher.isFile() || !Files.isDirectory(game)) {
            showError("Não foi possível iniciar", "Verifique Java portátil, SKlauncher.jar e a pasta game.");
            inspectFiles();
            return;
        }
        try {
            List<String> command = List.of(javaExe.toString(), "--add-exports=java.base/jdk.internal.loader=ALL-UNNAMED", "--add-opens=java.base/jdk.internal.loader=ALL-UNNAMED", "-jar", sklauncher.toString(), "--workDir", game.toString());
            process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(false).start();
            launchStartedAt = System.nanoTime();
            startButton.setDisable(true);
            processState.setText("EXECUTANDO");
            processState.getStyleClass().setAll("state", "running");
            append("[PMC] Iniciando SKlauncher...");
            append("[PMC] SKlauncher iniciado com sucesso. Esta janela fechará em 5 segundos.");
            processState.setText("OK — fechando em 5 segundos...");
            watcher.schedule(() -> Platform.runLater(() -> {
                autoClosing = true;
                mainStage.close();
            }), 5, TimeUnit.SECONDS);
            readProcessStream(process.getInputStream(), "[SKL]");
            readProcessStream(process.getErrorStream(), "[SKL][ERRO]");
            watcher.submit(() -> {
                try {
                    int code = process.waitFor();
                    Platform.runLater(() -> {
                        append("[PMC] SKlauncher terminou. Código de saída: " + code);
                        process = null;
                        startButton.setDisable(false);
                        processState.setText("Encerrado (" + code + ")");
                        processState.getStyleClass().setAll("state");
                        long elapsed = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - launchStartedAt);
                        long delay = Math.max(0, 5 - elapsed);
                        watcher.schedule(() -> Platform.runLater(() -> {
                            showMainStage();
                            mainStage.toFront();
                            mainStage.requestFocus();
                        }), delay, TimeUnit.SECONDS);
                    });
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
        } catch (IOException e) {
            append("[PMC] ERRO ao iniciar SKlauncher: " + e.getMessage());
            showError("Falha ao iniciar", e.getMessage());
        }
    }

    private void readProcessStream(java.io.InputStream input, String prefix) {
        watcher.submit(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) append(prefix + " " + line);
            } catch (IOException e) { append("[PMC] Falha lendo saída do SKlauncher: " + e.getMessage()); }
        });
    }

    private void watchLog() {
        watcher.scheduleWithFixedDelay(() -> {
            try {
                if (!Files.isRegularFile(logFile)) return;
                long size = Files.size(logFile);
                if (size < logPosition) logPosition = 0;
                try (var channel = java.nio.channels.FileChannel.open(logFile, StandardOpenOption.READ)) {
                    channel.position(logPosition);
                    try (var reader = new BufferedReader(new InputStreamReader(java.nio.channels.Channels.newInputStream(channel), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) append("[SKL-LOG] " + line);
                        logPosition = channel.position();
                    }
                }
            } catch (IOException ignored) { }
        }, 0, 500, TimeUnit.MILLISECONDS);
    }

    private void askForNameIfNeeded() {
        try {
            Files.createDirectories(profileDir);
            var properties = new java.util.Properties();
            if (Files.isRegularFile(profileFile)) {
                try (var reader = Files.newBufferedReader(profileFile, StandardCharsets.UTF_8)) { properties.load(reader); }
            }
            String uuid = properties.getProperty("uuid");
            String name = properties.getProperty("name");
            if (uuid == null || uuid.isBlank()) {
                uuid = UUID.randomUUID().toString();
                properties.setProperty("uuid", uuid);
            }
            if (name == null || name.isBlank()) {
                TextInputDialog dialog = new TextInputDialog();
                dialog.setTitle("PMC V3");
                dialog.setHeaderText("ESCOLHA UM NOME");
                dialog.setContentText("Nome do perfil local:");
                var result = dialog.showAndWait();
                if (result.isPresent() && !result.get().trim().isEmpty()) {
                    properties.setProperty("name", result.get().trim());
                    append("[PMC] Perfil local salvo para o UUID " + uuid + ".");
                } else {
                    append("[PMC] Nenhum nome informado; a conta configurada no SKlauncher será mantida.");
                }
                try (var writer = Files.newBufferedWriter(profileFile, StandardCharsets.UTF_8)) { properties.store(writer, "PMC local profile"); }
            }
        } catch (IOException e) {
            append("[PMC] Não foi possível salvar o perfil local: " + e.getMessage());
        }
    }

    private void append(String text) {
        Platform.runLater(() -> {
            logArea.appendText(text + System.lineSeparator());
            logArea.positionCaret(logArea.getLength());
        });
    }

    private void setStatus(String name, String text, boolean good) {
        for (StatusRow row : rows) if (row.name.equals(name)) {
            row.value.setText(text);
            row.value.getStyleClass().setAll("value", text.startsWith("ERRO") ? "error" : text.startsWith("AVISO") ? "warning" : "ok");
        }
    }

    private void openPath(Path path) {
        try {
            if (!Files.exists(path)) Files.createDirectories(path);
            Desktop.getDesktop().open(path.toFile());
        } catch (Exception e) { showError("Não foi possível abrir", path.toString()); }
    }

    private void restartLauncher(Stage stage) {
        shutdown();
        stage.close();
        Platform.runLater(() -> new PmcLauncher().start(new Stage()));
    }

    private void showError(String title, String message) {
        Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, message).showAndWait());
    }

    private void shutdown() {
        if (process != null && process.isAlive()) process.destroy();
        watcher.shutdownNow();
    }

    private void showMainStage() {
        mainStage.setOpacity(1);
        mainStage.show();
    }

    private static Path findRoot() {
        try {
            Path jarDirectory = Path.of(PmcLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().getParent();
            return Files.isDirectory(jarDirectory.resolve("game")) ? jarDirectory : jarDirectory.getParent();
        }
        catch (Exception e) { return Path.of(".").toAbsolutePath().normalize(); }
    }

    private static boolean isFile(Path path) {
        return Files.isRegularFile(path);
    }

    private static String css() {
        return "body { -fx-font-family: 'Segoe UI'; }" +
                ".root { -fx-background-color: #111827; }" +
                ".title { -fx-font-size: 30px; -fx-font-weight: bold; -fx-text-fill: #f9fafb; }" +
                ".subtitle { -fx-font-size: 14px; -fx-text-fill: #9ca3af; }" +
                ".key { -fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #e5e7eb; -fx-min-width: 110px; }" +
                ".value { -fx-font-size: 14px; -fx-text-fill: #9ca3af; }" +
                ".ok { -fx-text-fill: #18864b; } .warning { -fx-text-fill: #a15c00; } .error { -fx-text-fill: #c5221f; }" +
                ".button { -fx-background-radius: 6; -fx-padding: 9 14; -fx-cursor: hand; }" +
                ".primary { -fx-background-color: linear-gradient(to right, #7c3aed, #a855f7); -fx-text-fill: white; -fx-font-weight: bold; }" +
                ".primary:hover { -fx-background-color: linear-gradient(to right, #6d28d9, #9333ea); }" +
                ".button:hover { -fx-border-color: #a855f7; -fx-border-radius: 6; }" +
                ".state { -fx-text-fill: #9ca3af; -fx-padding: 0 0 0 6; } .running { -fx-text-fill: #34d399; -fx-font-weight: bold; }" +
                ".section { -fx-font-weight: bold; -fx-text-fill: #e5e7eb; }" +
                ".text-area { -fx-font-family: 'Consolas'; -fx-font-size: 12px; -fx-control-inner-background: #111827; -fx-text-fill: #d1d5db; -fx-highlight-fill: #374151; }";
    }

    private record StatusRow(String name, Label value) { }

    public static void main(String[] args) { launch(args); }
}
