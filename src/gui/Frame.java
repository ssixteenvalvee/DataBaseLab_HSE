package gui;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import logic.*;

public class Frame extends JFrame {
    private FileDataBase db;
    private JTable table;
    private DefaultTableModel tableModel;
    private final String BACKUPS_DIR = "backups";

    public Frame() {
        setTitle("Автосалон - Управление БД");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);

        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                super.windowClosing(e);
                try {
                    if (db != null && db.IS_OPENED) {
                        db.closeDataBase();
                    }
                    System.exit(0);
                } catch (IOException ex) {
                    System.err.println(ex.getMessage());
                    JOptionPane.showMessageDialog(Frame.this,
                            "Something went wrong while closing: " + ex);
                }
            }
        });
        // create db
        try {
            db = new FileDataBase("data.idx", "data.dat");
            db.openDataBase();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Ошибка открытия БД: " + e.getMessage());
        }

        // buttons
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        JButton addBtn = new JButton("Добавить");
        JButton editBtn = new JButton("Изменить");
        JButton deleteBtn = new JButton("Удалить по ID");
        JButton vacuumBtn = new JButton("Сжать БД (Vacuum)");
        JButton backupBtn = new JButton("Бэкап");
        JButton backupLoadBtn =  new JButton("Загрузить Бэкап");
        toolBar.add(addBtn);
        toolBar.add(editBtn);
        toolBar.add(deleteBtn);
        toolBar.addSeparator();
        toolBar.add(vacuumBtn);
        toolBar.add(backupBtn);
        toolBar.add(backupLoadBtn);

        // taBle itself
        String[] columns = {"ID", "Название", "Л.С.", "Цена", "В наличии"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        JScrollPane scrollPane = new JScrollPane(table);

        // search panel
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JComboBox<FileDataBase.Field> fieldCombo = new JComboBox<>(FileDataBase.Field.values());
        JTextField searchField = new JTextField(15);
        JButton searchBtn = new JButton("Найти");
        JButton resetBtn = new JButton("Сбросить");

        searchPanel.add(new JLabel("Искать по:"));
        searchPanel.add(fieldCombo);
        searchPanel.add(searchField);
        searchPanel.add(searchBtn);
        searchPanel.add(resetBtn);

        // window layout
        setLayout(new BorderLayout());
        add(toolBar, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(searchPanel, BorderLayout.SOUTH);

        // event handlers

        // add
        addBtn.addActionListener(e -> {
            Dialogue dialog = new Dialogue(this, null);
            dialog.setVisible(true);
            Entity newEnt = dialog.getResult();
            if (newEnt != null) {
                try {
                    db.add(newEnt.getCarName(), newEnt.getHP(), newEnt.getPrice(), newEnt.getAvail());
                    refreshTable();
                } catch (IOException ex) {
                    showError("Ошибка добавления", ex);
                }
            }
        });

        // edit
        editBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row == -1) { showError("Выберите строку", null); return; }

            int id = (int) tableModel.getValueAt(row, 0);
            try {
                Entity old = db.seekByID(id);
                Dialogue dialog = new Dialogue(this, old);
                dialog.setVisible(true);
                Entity edited = dialog.getResult();
                if (edited != null) {
                    db.edit(id, FileDataBase.Field.NAME, edited.getCarName());
                    db.edit(id, FileDataBase.Field.HP, edited.getHP());
                    db.edit(id, FileDataBase.Field.PRICE, edited.getPrice());
                    db.edit(id, FileDataBase.Field.AVAILABLE, edited.getAvail());
                    refreshTable();
                }
            } catch (Exception ex) {
                showError("Ошибка редактирования", ex);
            }
        });

        // delete by id. basically delete by value is search + delete by id
        deleteBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row == -1) { showError("Выберите строку", null); return; }
            int id = (int) tableModel.getValueAt(row, 0);

            int confirm = JOptionPane.showConfirmDialog(this, "Удалить запись ID=" + id + "?", "Подтверждение", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                try {
                    db.deleteByID(id);
                    refreshTable();
                } catch (IOException ex) {
                    showError("Ошибка удаления", ex);
                }
            }
        });

        // vacuum
        vacuumBtn.addActionListener(e -> {
            try {
                db.vacuum();
                refreshTable();
                JOptionPane.showMessageDialog(this, "БД успешно сжата!");
            } catch (IOException ex) {
                showError("Ошибка Vacuum", ex);
            }
        });

        // backup
        backupBtn.addActionListener(e -> {
            try {
                String path = db.createBackupFile(BACKUPS_DIR);
                JOptionPane.showMessageDialog(this, "Бэкап создан:\n" + path);
            } catch (IOException ex) {
                showError("Ошибка бэкапа", ex);
            }
        });

        // load from backup
        backupLoadBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Выберите папку для бэкапа");

            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            File backupDir = new File(BACKUPS_DIR);
            if (backupDir.exists()) {
                chooser.setCurrentDirectory(backupDir);
            }

            int result = chooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedDir = chooser.getSelectedFile();
                String path = selectedDir.getPath();
                try {
                    db.recoverFromBackup(path);
                    refreshTable();
                    JOptionPane.showMessageDialog(this, "БД успешно восстановлена из " + path);
                } catch (IOException ex) {
                    System.err.println(ex.getMessage());
                    JOptionPane.showMessageDialog(this, "Ошибка восстановления: " + ex);
                }
            }
        });

        // find
        searchBtn.addActionListener(e -> {
            FileDataBase.Field field = (FileDataBase.Field) fieldCombo.getSelectedItem();
            String valStr = searchField.getText().trim();
            if (valStr.isEmpty()) { refreshTable(); return; }

            try {
                Object value;
                switch (field) {
                    case ID: value = Integer.parseInt(valStr); break;
                    case HP: value = Integer.parseInt(valStr); break;
                    case PRICE: value = Double.parseDouble(valStr); break;
                    case AVAILABLE: value = Boolean.parseBoolean(valStr); break;
                    default: value = valStr;
                }
                ArrayList<Entity> results = db.seekByValue(field, value);
                updateTable(results);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Неверный формат ввода для этого поля!");
            } catch (IOException ex) {
                showError("Ошибка поиска", ex);
            }
        });

        resetBtn.addActionListener(e -> {
            searchField.setText("");
            refreshTable();
        });

        refreshTable();
    }

    private void refreshTable() {
        try {
            updateTable(db.getAll());
        } catch (IOException ex) {
            showError("Ошибка чтения БД", ex);
        }
    }

    private void updateTable(ArrayList<Entity> list) {
        tableModel.setRowCount(0);
        for (Entity e : list) {
            tableModel.addRow(new Object[]{
                    e.getId(),
                    e.getCarName(),
                    e.getHP(),
                    e.getPrice(),
                    e.getAvail() ? "Да" : "Нет"
            });
        }
    }

    private void showError(String msg, Exception e) {
        String fullMsg = e == null ? msg : msg + ": " + e.getMessage();
        JOptionPane.showMessageDialog(this, fullMsg, "Ошибка", JOptionPane.ERROR_MESSAGE);
    }
}
