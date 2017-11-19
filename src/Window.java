import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.io.IOException;

public class Window extends JFrame {

    private File file;
    private DefaultComboBoxModel<String> COMPortsList;
    private JComboBox<String> boxCOMPorts;
    private JTextField textMsg;
    private JTextField textFile;
    private SerialPortService serialPortService;
    private JTextArea textArea;
    private JRadioButton chbStr;
    private JRadioButton chbFile;
    private JProgressBar progressFile;
    private JButton sendBtn;

    public Window(String name) {
        super(name);
        initCOM();
        initUI();
    }

    private void initUI() {
        Panel p1 = new Panel();
        Panel p2 = new Panel(new BorderLayout());
        Panel p3 = new Panel(new BorderLayout());
        Panel pp = new Panel(new BorderLayout());

        ButtonGroup group = new ButtonGroup();
        chbStr = new JRadioButton();
        chbStr.setSelected(true);
        chbStr.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                p2.setEnabled(false);
                textMsg.setEnabled(true);
            }
        });
        chbFile = new JRadioButton();
        chbFile.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                p2.setEnabled(true);
                textMsg.setEnabled(false);
            }
        });
        chbStr.setText("Message");
        chbFile.setText("File");
        group.add(chbStr);
        group.add(chbFile);

        textMsg = new JTextField();
        sendBtn = new JButton("Send");
        sendBtn.setEnabled(false);
        sendBtn.setFocusPainted(true);
        textMsg.setEnabled(false);
        textMsg.requestFocus();
        sendBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (serialPortService.portIsOpen()) {

                    // transmit data
                    if (chbStr.isSelected() && !textMsg.getText().trim().isEmpty()) {
                        serialPortService.writeStringMessage(textMsg.getText().trim());
                        addLogData("Transmit<Message> size: " + textMsg.getText().trim().getBytes().length + " bytes >>> " + textMsg.getText().trim());
                        textMsg.setText("");
                    }

                    if (chbFile.isSelected() && !textFile.getText().isEmpty() && file != null) {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    serialPortService.writeFile(file);
                                } catch (IOException e1) {
                                    addLogData("File error");
                                    e1.printStackTrace();
                                }
                            }
                        }).start();

                        sendBtn.setEnabled(false);
                        addLogData("Transmit<File> size: " + file.length() + " bytes >>> " + file.getName());
                    }
                } else {
                    showMsgBox("Port is not open");
                }
            }
        });
        p3.add(BorderLayout.CENTER, textMsg);
        p3.add(BorderLayout.EAST, sendBtn);
        this.getRootPane().setDefaultButton(sendBtn);

        JLabel comLabel = new JLabel("Select COM port: ");
        COMPortsList = new DefaultComboBoxModel<>();
        String[] ports = SerialPortService.getComPorts();
        for (int i = 0; i < ports.length; i++) {
            COMPortsList.addElement(ports[i]);
        }
        boxCOMPorts = new JComboBox<>(COMPortsList);
        JButton btnConnect = new JButton("Connect");
        JButton btnDisconnect = new JButton("Disconnect");
        btnDisconnect.setEnabled(false);
        btnConnect.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                serialPortService.initNewSerialPort(COMPortsList.getElementAt(boxCOMPorts.getSelectedIndex()), 115200);
                serialPortService.openPort();

                if (serialPortService.portIsOpen()) {
                    btnConnect.setEnabled(false);
                    btnDisconnect.setEnabled(true);
                    boxCOMPorts.setEnabled(false);
                    sendBtn.setEnabled(true);
                    textMsg.setEnabled(true);

                    serialPortService.setSerialPortMessageReceivedListener(messageReceivedListener);
                    serialPortService.setSerialPortFileReceivedListener(fileReceivedListener);
                    serialPortService.setSerialPortTransmitFileProgressListener(transmitFileProgressListener);
                    serialPortService.setSerialPortReceiveFileProgressListener(receiveFileProgressListener);

                } else {
                    showMsgBox("Error open port. The selected port is already busy");
                }
            }
        });
        btnDisconnect.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                serialPortService.removeSerialPortFileReceivedListener();
                serialPortService.removeSerialPortMessageReceivedListener();
                serialPortService.removeSerialPortTransmitFileProgressListener();
                serialPortService.removeSerialPortReceiveFileProgressListener();

                try {
                    serialPortService.closePort();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }

                btnConnect.setEnabled(true);
                btnDisconnect.setEnabled(false);
                boxCOMPorts.setEnabled(true);
                sendBtn.setEnabled(false);
                textMsg.setEnabled(false);
            }
        });
        p1.add(chbStr);
        p1.add(chbFile);
        p1.add(comLabel);
        p1.add(boxCOMPorts);
        p1.add(btnConnect);
        p1.add(btnDisconnect);

        textFile = new JTextField();
        textFile.setEditable(false);
        JButton openFileDialogBtn = new JButton("Edit");
        openFileDialogBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser fileChooser = new JFileChooser();
                int code = fileChooser.showDialog(null, "Select file for transmit");
                if (code == JFileChooser.APPROVE_OPTION) {
                    file = fileChooser.getSelectedFile();
                    textFile.setText(file.getAbsolutePath());
                }
            }
        });
        progressFile = new JProgressBar(JProgressBar.HORIZONTAL);
        progressFile.setMinimum(0);
        progressFile.setMaximum(100);
        progressFile.setStringPainted(true);
        p2.add(BorderLayout.WEST, progressFile);
        p2.add(BorderLayout.CENTER, textFile);
        p2.add(BorderLayout.EAST, openFileDialogBtn);
        p2.setEnabled(false);

        pp.add(BorderLayout.NORTH, p2);
        pp.add(BorderLayout.SOUTH, p1);

        textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setBorder(null);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setAutoscrolls(true);
        JScrollPane scrollPane = new JScrollPane(textArea, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(null);
        DefaultCaret caret = (DefaultCaret) textArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        scrollPane.setAutoscrolls(true);

        Container container = getContentPane();
        container.setLayout(new BorderLayout());
        container.add(BorderLayout.NORTH, pp);
        container.add(BorderLayout.SOUTH, p3);
        container.add(BorderLayout.CENTER, scrollPane);

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(900, 400);
        setVisible(true);
        setResizable(false);
    }

    private void initCOM() {
        serialPortService = SerialPortService.getInstance();
    }

    private void showMsgBox(String msg) {
        JOptionPane.showMessageDialog(null, msg, "Message", JOptionPane.INFORMATION_MESSAGE);
    }

    private void addLogData(String str) {
        textArea.append(str + "\r\n");
    }

    private SerialPortService.SerialPortFileReceivedListener fileReceivedListener = new SerialPortService.SerialPortFileReceivedListener() {
        @Override
        public void receiveFile(File file) {
            addLogData("Receive<File>: size: " + file.length() + " bytes >>> " + file.getAbsolutePath());
        }
    };

    private SerialPortService.SerialPortMessageReceivedListener messageReceivedListener = new SerialPortService.SerialPortMessageReceivedListener() {
        @Override
        public void receiveMessage(String message) {
            addLogData("Receive<Message> size: " + message.getBytes().length + " bytes >>> " + message);
        }
    };

    private SerialPortService.SerialPortTransmitFileProgressListener transmitFileProgressListener = new SerialPortService.SerialPortTransmitFileProgressListener() {
        @Override
        public void progressTransmitFile(int i) {
            progressFile.setValue(i);

            if (progressFile.getValue() == 100)
                sendBtn.setEnabled(true);
        }
    };

    private SerialPortService.SerialPortReceiveFileProgressListener receiveFileProgressListener = new SerialPortService.SerialPortReceiveFileProgressListener() {
        @Override
        public void progressReceiveFile(int i) {
            progressFile.setValue(i);

            if (sendBtn.isEnabled())
                sendBtn.setEnabled(false);

            if (progressFile.getValue() == 100)
                sendBtn.setEnabled(true);
        }
    };
}