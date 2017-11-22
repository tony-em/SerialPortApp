import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

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
    private JProgressBar progressReceiveFile;
    private JProgressBar progressTransmitFile;
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
                        String s = textMsg.getText().trim();
                        addLogData("Transmit<Message> size: " + s.getBytes().length + " bytes >>> " + s);
                        textMsg.setText("");

                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                serialPortService.writeStringMessage(s);
                            }
                        }).start();
                    }

                    if (chbFile.isSelected() && !textFile.getText().isEmpty() && file != null) {
                        int checksum = 0;
                        try {
                            checksum = ByteBuffer.wrap(CRC16.getChecksumBytesArr(SerialPortService.getFileBytes(file))).getShort();
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                        String ch = Integer.toHexString(checksum).toUpperCase();
                        addLogData("Transmit<File> size: " + file.length() + " bytes >>> " + file.getName()
                                + " CRC16: 0x" + ch.substring(ch.length() - 4));

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
        progressReceiveFile = new JProgressBar(JProgressBar.HORIZONTAL);
        progressTransmitFile = new JProgressBar(JProgressBar.HORIZONTAL);
        progressReceiveFile.setMinimum(0);
        progressReceiveFile.setMaximum(100);
        progressReceiveFile.setStringPainted(true);
        progressReceiveFile.setString("Received file");
        progressTransmitFile.setMinimum(0);
        progressTransmitFile.setMaximum(100);
        progressTransmitFile.setString("Transmitted file");
        progressTransmitFile.setStringPainted(true);
        //p2.add(BorderLayout.WEST, progressReceiveFile);
        p1.add(progressReceiveFile);
        p1.add(progressTransmitFile);
        p2.add(BorderLayout.CENTER, textFile);
        p2.add(BorderLayout.EAST, openFileDialogBtn);
        p2.setEnabled(false);
        p1.add(chbStr);
        p1.add(chbFile);
        p1.add(comLabel);
        p1.add(boxCOMPorts);
        p1.add(btnConnect);
        p1.add(btnDisconnect);

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
        public void receiveFile(File file, int receivedChecksum, int calcChecksum) {
            String ch1 = Integer.toHexString(receivedChecksum).toUpperCase(),
                    ch2 = Integer.toHexString(calcChecksum).toUpperCase();
            addLogData("Receive<File>: size: " + file.length() + " bytes >>> " + file.getAbsolutePath() +
                    " CRC16: Received: 0x" + ch1.substring(ch1.length() - 4) +
                    " Calc: 0x" + ch1.substring(ch2.length() - 4));
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
            progressTransmitFile.setValue(i);

            if (progressTransmitFile.getValue() == 100)
                sendBtn.setEnabled(true);
        }
    };

    private SerialPortService.SerialPortReceiveFileProgressListener receiveFileProgressListener = new SerialPortService.SerialPortReceiveFileProgressListener() {
        @Override
        public void progressReceiveFile(int i) {
            progressReceiveFile.setValue(i);
        }
    };
}