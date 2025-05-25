import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;

class GanttChartPanel extends JPanel {
    private List<TaskData> tasks;
    private int maxTime;
    private static final int BAR_HEIGHT = 30;
    private static final int TIME_SCALE = 50; // pixels per time unit
    private static final int MARGIN = 50;

    public GanttChartPanel() {
        tasks = new ArrayList<>();
        setPreferredSize(new Dimension(800, 400));
        setBackground(Color.WHITE);
    }

    public void setTasks(List<TaskData> taskList) {
        this.tasks = taskList;
        maxTime = 0;
        for (TaskData task : tasks) {
            maxTime = Math.max(maxTime, task.endTime);
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw time scale
        g2d.setColor(Color.BLACK);
        for (int i = 0; i <= maxTime; i++) {
            int x = MARGIN + i * TIME_SCALE;
            g2d.drawLine(x, MARGIN - 5, x, MARGIN);
            g2d.drawString(String.valueOf(i), x - 5, MARGIN - 10);
        }

        // Draw task bars
        for (int i = 0; i < tasks.size(); i++) {
            TaskData task = tasks.get(i);
            int y = MARGIN + i * (BAR_HEIGHT + 10);
            
            // Draw task name
            g2d.drawString(task.name, 5, y + BAR_HEIGHT/2 + 5);
            
            // Draw task bar
            int startX = MARGIN + task.startTime * TIME_SCALE;
            int width = (task.endTime - task.startTime) * TIME_SCALE;
            g2d.setColor(new Color(100 + i * 20, 150 + i * 20, 200));
            g2d.fillRect(startX, y, width, BAR_HEIGHT);
            g2d.setColor(Color.BLACK);
            g2d.drawRect(startX, y, width, BAR_HEIGHT);
            
            // Draw time labels
            g2d.drawString(String.valueOf(task.startTime), startX, y - 5);
            g2d.drawString(String.valueOf(task.endTime), startX + width - 15, y - 5);
        }
    }
}

class TaskData {
    String name;
    int startTime;
    int endTime;
    
    public TaskData(String name, int startTime, int endTime) {
        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
    }
}

public class TaskScheduler extends JFrame {
    private DefaultTableModel tableModel;
    private JTable table;

    public TaskScheduler() {
        setTitle("Task Scheduler App");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 500);
        setLocationRelativeTo(null);

        String[] columnNames = {"Task Name", "Priority", "Duration", "Deadline"};
        tableModel = new DefaultTableModel(columnNames, 0);
        table = new JTable(tableModel);

        JScrollPane scrollPane = new JScrollPane(table);
        JButton addButton = new JButton("Add Task");
        JButton deleteButton = new JButton("Delete Task");
        JButton generateButton = new JButton("Run Scheduler");

        addButton.addActionListener(e -> tableModel.addRow(new Object[]{"", "", "", ""}));

        deleteButton.addActionListener(e -> {
            int selectedRow = table.getSelectedRow();
            if (selectedRow != -1) {
                tableModel.removeRow(selectedRow);
            }
        });

        generateButton.addActionListener(e -> runScheduler());

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(addButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(generateButton);

        add(scrollPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void runScheduler() {
        try {
            // Get the project root directory
            String projectRoot = new File(".").getAbsolutePath();
            System.out.println("Project Root: " + projectRoot); // Debug print

            // Update paths to match your project structure
            String inputPath = new File(projectRoot, "input.txt").getAbsolutePath();
            String outputPath = new File(projectRoot, "taskoutput.txt").getAbsolutePath();
            String summaryPath = new File(projectRoot, "task_summary.txt").getAbsolutePath();
            String schedulerPath = new File(projectRoot, "trial.exe").getAbsolutePath();

            System.out.println("Scheduler Path: " + schedulerPath); // Debug print
            System.out.println("Output Path: " + outputPath); // Debug print
            System.out.println("Summary Path: " + summaryPath); // Debug print

            // Step 1: Write input.txt from table data
            writeInputFile();

            // Step 2: Run the C executable
            ProcessBuilder pb = new ProcessBuilder(schedulerPath);
            pb.directory(new File(projectRoot));
            
            // Add error logging
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Read process output for debugging
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                System.out.println("Process output: " + line);
            }

            // Wait for the process to complete
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                JOptionPane.showMessageDialog(this, 
                    "❌ Scheduler execution failed with exit code: " + exitCode + 
                    "\nOutput: " + output.toString(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Wait for output files to be created
            File outputFile = new File(outputPath);
            File summaryFile = new File(summaryPath);
            while (!outputFile.exists() || !summaryFile.exists()) {
                Thread.sleep(200);
            }

            // Step 3: Read the output files
            try {
                // Read taskoutput.txt
                String outputContent = new String(Files.readAllBytes(Paths.get(outputPath)));
                System.out.println("Output content: " + outputContent); // Debug print
                
                // Parse the output content
                Map<String, Map<String, String>> taskDetails = new HashMap<>();
                String currentTask = null;
                Map<String, String> currentTaskData = new HashMap<>();
                
                for (String outputLine : outputContent.split("\n")) {
                    outputLine = outputLine.trim();
                    if (outputLine.contains("\"taskDetails\"")) {
                        if (currentTask != null) {
                            taskDetails.put(currentTask, currentTaskData);
                            currentTaskData = new HashMap<>();
                        }
                        currentTask = outputLine.split(":")[1].replaceAll("[,\"]", "").trim();
                    } else if (outputLine.contains("\"priority\"")) {
                        currentTaskData.put("priority", outputLine.split(":")[1].replaceAll("[,\"]", "").trim());
                    } else if (outputLine.contains("\"burst_time\"")) {
                        currentTaskData.put("burst_time", outputLine.split(":")[1].replaceAll("[,\"]", "").trim());
                    } else if (outputLine.contains("\"waitingTime\"")) {
                        currentTaskData.put("waitingTime", outputLine.split(":")[1].replaceAll("[,\"]", "").trim());
                    } else if (outputLine.contains("\"turnaroundTime\"")) {
                        currentTaskData.put("turnaroundTime", outputLine.split(":")[1].replaceAll("[,\"]", "").trim());
                    }
                }
                if (currentTask != null) {
                    taskDetails.put(currentTask, currentTaskData);
                }
                
                // Read task_summary.txt
                String summaryContent = new String(Files.readAllBytes(Paths.get(summaryPath)));
                System.out.println("Summary content: " + summaryContent); // Debug print
                
                // Display results
                displayResults(taskDetails, summaryContent);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, 
                    "❌ Failed to read output files: " + ex.getMessage() + 
                    "\nOutput Path: " + outputPath + 
                    "\nSummary Path: " + summaryPath,
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, 
                "❌ Error: " + ex.getMessage() + 
                "\nStack trace: " + Arrays.toString(ex.getStackTrace()),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void writeInputFile() {
        try (PrintWriter pw = new PrintWriter(new FileWriter("input.txt"))) {
            int rowCount = tableModel.getRowCount();
            pw.println(rowCount);
            
            for (int i = 0; i < rowCount; i++) {
                String name = tableModel.getValueAt(i, 0).toString();
                String priority = tableModel.getValueAt(i, 1).toString();
                String duration = tableModel.getValueAt(i, 2).toString();
                String deadline = tableModel.getValueAt(i, 3).toString();
                
                pw.printf("%s %s %s %s%n", name, priority, duration, deadline);
            }
            
            pw.println("0"); // No dependencies
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, 
                "Error writing to input.txt: " + ex.getMessage(), 
                "File Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void displayResults(Map<String, Map<String, String>> taskDetails, String summaryContent) {
        // Step 4: Show outputs in popup tabs
        JDialog dialog = new JDialog(this, "Scheduling Results", true);
        dialog.setSize(800, 600);
        dialog.setLocationRelativeTo(this);

        JTabbedPane tabbedPane = new JTabbedPane();

        // Results Table Panel
        JPanel resultsPanel = new JPanel(new BorderLayout());
        String[] columnNames = {"Task", "Priority", "Duration", "Waiting Time", "Turnaround Time"};
        DefaultTableModel resultModel = new DefaultTableModel(columnNames, 0);
        JTable resultTable = new JTable(resultModel);
        resultTable.setFillsViewportHeight(true);
        resultsPanel.add(new JScrollPane(resultTable), BorderLayout.CENTER);

        // Gantt Chart Panel
        GanttChartPanel ganttPanel = new GanttChartPanel();
        List<TaskData> ganttData = new ArrayList<>();

        // Parse and display task output
        for (Map.Entry<String, Map<String, String>> entry : taskDetails.entrySet()) {
            String taskName = entry.getKey();
            Map<String, String> data = entry.getValue();
            
            resultModel.addRow(new Object[]{
                taskName,
                data.get("priority"),
                data.get("burst_time"),
                data.get("waitingTime"),
                data.get("turnaroundTime")
            });
            
            // Add to Gantt chart data
            ganttData.add(new TaskData(
                taskName,
                Integer.parseInt(data.get("waitingTime")),
                Integer.parseInt(data.get("turnaroundTime"))
            ));
        }

        ganttPanel.setTasks(ganttData);
        tabbedPane.addTab("Results Table", resultsPanel);
        tabbedPane.addTab("Gantt Chart", ganttPanel);

        // Summary Panel
        JPanel summaryPanel = new JPanel(new BorderLayout());
        JTextArea summaryArea = new JTextArea(summaryContent);
        summaryArea.setEditable(false);
        summaryArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        summaryPanel.add(new JScrollPane(summaryArea), BorderLayout.CENTER);
        tabbedPane.addTab("Summary", summaryPanel);

        dialog.add(tabbedPane);
        dialog.setVisible(true);
    }

    private void showResultsPopup() {
        // Create a dialog
        JDialog dialog = new JDialog(this, "Task Scheduler Results", true);
        dialog.setSize(800, 600);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        // Create main panel with padding
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Create table model with columns
        DefaultTableModel popupTableModel = new DefaultTableModel();
        popupTableModel.setColumnIdentifiers(new String[]{
            "Task Name", "Priority", "Duration", "Waiting Time", "Turnaround Time"
        });

        // Read and parse task_summary.txt from root directory
        try (BufferedReader br = new BufferedReader(new FileReader("task_summary.txt"))) {
            String line;
            String currentTask = null;
            String priority = "";
            String duration = "";
            String waitingTime = "";
            String turnaroundTime = "";

            while ((line = br.readLine()) != null) {
                line = line.trim();
                
                if (line.startsWith("Task:")) {
                    // If we have a previous task, add it to the table
                    if (currentTask != null) {
                        popupTableModel.addRow(new Object[]{
                            currentTask, priority, duration, waitingTime, turnaroundTime
                        });
                    }
                    // Start new task
                    currentTask = line.substring(5).trim();
                } else if (line.startsWith("Priority:")) {
                    priority = line.substring(9).trim();
                } else if (line.startsWith("Duration:")) {
                    duration = line.substring(9).trim();
                } else if (line.startsWith("Waiting Time:")) {
                    waitingTime = line.substring(13).trim();
                } else if (line.startsWith("Turnaround Time:")) {
                    turnaroundTime = line.substring(16).trim();
                }
            }
            
            // Add the last task
            if (currentTask != null) {
                popupTableModel.addRow(new Object[]{
                    currentTask, priority, duration, waitingTime, turnaroundTime
                });
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, 
                "Could not read task_summary.txt: " + ex.getMessage(), 
                "File Error", 
                JOptionPane.ERROR_MESSAGE);
        }

        // Create table with custom renderer
        JTable popupTable = new JTable(popupTableModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component comp = super.prepareRenderer(renderer, row, column);
                if (!isRowSelected(row)) {
                    comp.setBackground(row % 2 == 0 ? Color.WHITE : new Color(240, 240, 240));
                }
                return comp;
            }
        };

        // Set column widths
        popupTable.getColumnModel().getColumn(0).setPreferredWidth(100);
        popupTable.getColumnModel().getColumn(1).setPreferredWidth(80);
        popupTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        popupTable.getColumnModel().getColumn(3).setPreferredWidth(100);
        popupTable.getColumnModel().getColumn(4).setPreferredWidth(120);

        // Center align all columns
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        for (int i = 0; i < popupTable.getColumnCount(); i++) {
            popupTable.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
        }

        // Add table header
        JTableHeader header = popupTable.getTableHeader();
        header.setBackground(new Color(70, 130, 180));
        header.setForeground(Color.WHITE);
        header.setFont(header.getFont().deriveFont(Font.BOLD));

        // Create scroll pane for table
        JScrollPane tableScroll = new JScrollPane(popupTable);
        tableScroll.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        // Create summary panel
        JPanel summaryPanel = new JPanel(new GridLayout(2, 2, 10, 5));
        summaryPanel.setBorder(BorderFactory.createTitledBorder("Summary"));

        // Read averages from task_summary.txt in root directory
        try (BufferedReader br = new BufferedReader(new FileReader("task_summary.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("Average Waiting Time:")) {
                    summaryPanel.add(new JLabel("Average Waiting Time:"));
                    summaryPanel.add(new JLabel(line.substring(20).trim()));
                } else if (line.startsWith("Average Turnaround Time:")) {
                    summaryPanel.add(new JLabel("Average Turnaround Time:"));
                    summaryPanel.add(new JLabel(line.substring(23).trim()));
                }
            }
        } catch (IOException ex) {
            summaryPanel.add(new JLabel("Error reading summary"));
        }

        // Add components to main panel
        mainPanel.add(tableScroll, BorderLayout.CENTER);
        mainPanel.add(summaryPanel, BorderLayout.SOUTH);

        // Add close button
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dialog.dispose());
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(closeButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Add main panel to dialog
        dialog.add(mainPanel);

        dialog.setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new TaskScheduler().setVisible(true);
        });
    }
}
