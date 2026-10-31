package gui;

import javax.swing.*;
import java.awt.*;
import logic.*;

public class Dialogue extends JDialog {
    private JTextField nameField, hpField, priceField;
    private JCheckBox availCheck;
    private Entity result = null;

    public Dialogue(JFrame parent, Entity toEdit) {
        super(parent, toEdit == null ? "Добавить автомобиль" : "Редактировать", true);
        setSize(300, 250);
        setLocationRelativeTo(parent);
        setLayout(new GridLayout(5, 2, 10, 10));

        add(new JLabel("Название:"));
        nameField = new JTextField();
        add(nameField);

        add(new JLabel("Л.С.:"));
        hpField = new JTextField();
        add(hpField);

        add(new JLabel("Цена:"));
        priceField = new JTextField();
        add(priceField);

        add(new JLabel("В наличии:"));
        availCheck = new JCheckBox();
        add(availCheck);

        if (toEdit != null) {
            // nameField.setText(toEdit.getCarName());
            hpField.setText(String.valueOf(toEdit.getHP()));
            priceField.setText(String.valueOf(toEdit.getPrice()));
            availCheck.setSelected(toEdit.getAvail());
        }

        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> {
            try {
                String name = nameField.getText();
                int hp = Integer.parseInt(hpField.getText());
                double price = Double.parseDouble(priceField.getText());
                boolean avail = availCheck.isSelected();

                result = new Entity(0, name, hp, price, avail);
                dispose();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Проверьте числа в полях Л.С. и Цена!", "Ошибка ввода", JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton cancelButton = new JButton("Отмена");
        cancelButton.addActionListener(e -> dispose());

        add(okButton);
        add(cancelButton);
    }

    public Entity getResult() {
        return result;
    }
}
