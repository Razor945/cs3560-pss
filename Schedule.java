import Interfaces.RecurringInterface;
import Interfaces.ScheduleInterface; // Import the interface.
import java.io.*;
import Interfaces.TaskInterface;
import java.util.ArrayList;
import java.util.List;

public class Schedule implements ScheduleInterface {

    // Attribute: List to store tasks.
    private List<TaskInterface> tasks;

    // Constructor: Initialize the list of tasks.
    public Schedule() {
        tasks = new ArrayList<>();
    }

    @Override
    public boolean addTask(String name, String type, double startTime, double duration, int startDate) {
        TaskInterface newTask;

        if (type.equalsIgnoreCase("cancellation")) {
            // Create Anti-task directly using provided parameters
            newTask = new Anti(name, type, startTime, duration, startDate, null);

            // Remove the blocked task
            tasks.removeIf(task -> task.getName().equalsIgnoreCase(name));
        } else {
            // Handle other task types (e.g., Transient)
            newTask = new Transient(name, type, startTime, duration, startDate);
        }

        tasks.add(newTask);
        return true;
    }

    @Override
    public boolean addTask(String name, String type, double startTime, double duration, int startDate, int endDate, int frequency) {
        // Handle Recurring tasks
        if (!type.equalsIgnoreCase("recurring")) {
            System.out.println("Error: Invalid task type for recurring tasks.");
            return false;
        }

        TaskInterface newTask = new Recurring(name, type, startTime, duration, startDate, endDate, frequency);

        Recurring recurringTask = (Recurring) newTask;
        for (TaskInterface occurrence : recurringTask.getOccurrances()) {
            if (checkOverlap(occurrence)) {
                System.out.println("Error: Recurring task occurrence overlaps with existing tasks.");
                return false; // Abort if any occurrence overlaps
            }
        }

        // Add occurrences to the task list
        for (TaskInterface occurrence : recurringTask.getOccurrances()) {
            tasks.add(occurrence);
        }

        return true;
    }

    @Override
    public boolean removeTask(String name) {
        for (TaskInterface task : tasks) {
            if (task.getName().equals(name)) {
                tasks.remove(task);
                System.out.println("Task successfully removed.");
                return true;
            }
        }

        System.out.println("Error: Task not found.");
        return false; // Task not found
    }

    @Override
    public boolean checkOverlap(TaskInterface newTask) {
        for (TaskInterface task : tasks) {
            // Skip Anti-tasks during overlap checks
            if (task instanceof Anti) continue;

            if (Math.max(newTask.getStartDate(), task.getStartDate()) <= Math.min(newTask.getEndDate(), task.getEndDate())) {
                if (Math.max(newTask.getStartTime(), task.getStartTime()) < Math.min(newTask.getEndTime(), task.getEndTime())) {
                    return true; // Overlapping times
                }
            }
        }
        return false; // No overlap
    }

    @Override
    public void viewTask(String name) {
        for (TaskInterface task : tasks) {
            if (task.getName().equals(name)) {
                task.printTask();
                break;
            }
        }
    }

    @Override
    public boolean editTask(String name, double newStartTime, double newDuration, int newStartDate) {
        for (TaskInterface task : tasks) {
            if (task.getName().equals(name)) {
                task.setStartTime(newStartTime);
                task.setDuration(newDuration);
                task.setStartDate(newStartDate);
                System.out.println("Task edited successfully: " + name);
                return true;
            }
        }

        System.out.println("Error: Task not found.");
        return false; // Task not found
    }

    @Override
    public void printSchedule(String timeFrame) {
        tasks.sort((task1, task2) -> {
            int dateComparison = Integer.compare(task1.getStartDate(), task2.getStartDate());
            if (dateComparison == 0) { // If dates are the same, compare times
                return Double.compare(task1.getStartTime(), task2.getStartTime());
            }
            return dateComparison;
        });

        System.out.println("========= SCHEDULE =========");

        // Get the current date in YYYYMMDD format
        int today = getCurrentDate();

        for (TaskInterface task : tasks) {
            boolean shouldPrint = false;

            switch (timeFrame.toLowerCase()) {
                case "day":
                    shouldPrint = (task.getStartDate() == today);
                    break;
                case "week":
                    shouldPrint = (task.getStartDate() >= today && task.getStartDate() <= today + 6);
                    break;
                case "month":
                    int currentMonth = today / 100; // Extract YYYYMM
                    int taskMonth = task.getStartDate() / 100;
                    shouldPrint = (currentMonth == taskMonth);
                    break;
                case "all":
                    shouldPrint = true;
                    break;
                default:
                    System.out.println("Invalid timeframe. Showing all tasks.");
                    shouldPrint = true;
            }

            if (shouldPrint) {
                task.printTask();
            }
        }

        System.out.println("============================");
    }

    @Override
    public void viewTasksForTimeframe(int timeframe, int startDate) {
        System.out.println("\n========= SCHEDULE =========");
    
        for (TaskInterface task : tasks) {
            int taskStartDate = task.getStartDate();
    
            // Filter tasks based on the timeframe
            if ((timeframe == 1 && taskStartDate == startDate) || // View a day
                (timeframe == 2 && isWithinWeek(startDate, taskStartDate)) || // View a week
                (timeframe == 3 && isWithinMonth(startDate, taskStartDate)) || // View a month
                (timeframe == 4)) { // View all
                task.printTask();
            }
        }
    
        System.out.println("============================");
    }

    // Export the schedule to a file
    @Override
    public boolean exportSchedule(String fileName) {
        try (FileOutputStream fos = new FileOutputStream(fileName);
             ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(tasks);
            System.out.println("Schedule successfully exported to " + fileName);
            return true;
        } catch (IOException e) {
            System.out.println("An error occurred while exporting the schedule: " + e.getMessage());
            return false;
        }
    }

    // Import the schedule from a file
    @Override
    public boolean importSchedule(String fileName) {
        try (FileInputStream fis = new FileInputStream(fileName);
             ObjectInputStream ois = new ObjectInputStream(fis)) {
            tasks = (List<TaskInterface>) ois.readObject();
            System.out.println("Schedule successfully imported from " + fileName);
            return true;
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("An error occurred while importing the schedule: " + e.getMessage());
            return false;
        }
    }

    private int getCurrentDate() {
        // Use java.time to get the current date in YYYYMMDD format
        java.time.LocalDate currentDate = java.time.LocalDate.now();
        return currentDate.getYear() * 10000 + currentDate.getMonthValue() * 100 + currentDate.getDayOfMonth();
    }

    private boolean isWithinWeek(int startDate, int taskStartDate) {
        // Parse startDate and taskStartDate to LocalDate
        java.time.LocalDate start = parseDateToLocalDate(startDate);
        java.time.LocalDate taskStart = parseDateToLocalDate(taskStartDate);
    
        // Define the end of the week (7 days including the start day)
        java.time.LocalDate weekEnd = start.plusDays(6);
    
        // Check if taskStart falls within the week
        return (!taskStart.isBefore(start)) && (!taskStart.isAfter(weekEnd));
    }

    private boolean isWithinMonth(int startDate, int taskStartDate) {
        java.time.LocalDate start = parseDateToLocalDate(startDate);
        java.time.LocalDate taskStart = parseDateToLocalDate(taskStartDate);
    
        return start.getYear() == taskStart.getYear() && start.getMonth() == taskStart.getMonth();
    }

    private java.time.LocalDate parseDateToLocalDate(int date) {
        int year = date / 10000;
        int month = (date % 10000) / 100;
        int day = date % 100;
    
        return java.time.LocalDate.of(year, month, day);
    }
    
    public TaskInterface findTaskByName(String taskName) {
        for (TaskInterface task : tasks) {
            if (task.getName().equalsIgnoreCase(taskName)) {
                return task; // Return the found task
            }
        }
        return null; // Return null if no task is found
    }
}
