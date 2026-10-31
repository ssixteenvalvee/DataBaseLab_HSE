package gui;

import javax.swing.*;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Frame frame = new Frame();
            frame.setVisible(true);
        });
    }
}
