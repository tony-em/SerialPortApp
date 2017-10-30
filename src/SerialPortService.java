import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.fazecast.jSerialComm.SerialPortPacketListener;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Arrays;

public class SerialPortService implements ReceiveAllPacksCompleteListener {

    public static SerialPortService serialPortService;
    private SerialPort serialPort;
    private OutputStream outputStream;

    private static final int PACKET_SIZE = 4096;

    public static final byte RECEIVE_TYPE_STRING_MESSAGE = 5;
    public static final byte RECEIVE_TYPE_FILE = 10;

    private SerialPortMessageReceivedListener messageReceivedListener;
    private SerialPortFileReceivedListener fileReceivedListener;

    public static SerialPortService getInstance() {
        if (serialPortService == null) {
            serialPortService = new SerialPortService();
        }

        return serialPortService;
    }

    private SerialPortService() {
    }

    interface SerialPortFileReceivedListener {
        void receiveFile(File file);
    }

    interface SerialPortMessageReceivedListener {
        void receiveMessage(String message);
    }

    public void setSerialPortFileReceivedListener(SerialPortFileReceivedListener listener) {
        fileReceivedListener = listener;
    }

    public void removeSerialPortFileReceivedListener() {
        fileReceivedListener = null;
    }

    public void setSerialPortMessageReceivedListener(SerialPortMessageReceivedListener listener) {
        messageReceivedListener = listener;
    }

    public void removeSerialPortMessageReceivedListener() {
        messageReceivedListener = null;
    }

    public static byte[] concatArray(byte[] a, byte[] b) {
        if (a == null || b == null)
            throw new NullPointerException();

        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);

        return r;
    }

    private static int getCountPackets(int lengthBuff) {
        double d = (double) lengthBuff / (double) PACKET_SIZE;
        return (d % 4096 == 0.0) ? (int) d : (int) (++d);
    }

    private static byte[] intToByteArr(int i) {
        return ByteBuffer.allocate(4).putInt(i).array();
    }

    private static int byteArrToInt(byte[] arr) {
        return ByteBuffer.wrap(arr).getInt();
    }

    public void writeStringMessage(String msg) {
        byte[] buff = new byte[0];
        try {
            buff = msg.getBytes("utf-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        sendPacks(RECEIVE_TYPE_STRING_MESSAGE, buff);
    }

    private String getStringMessageByByteArr(byte[] buff) {
        String s = "";
        try {
            s = new String(buff, "utf-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return s;
    }

    public void writeFile(File file) throws IOException {
        String[] fileParts = file.getName().split("\\.");
        String fileExtension = fileParts[fileParts.length - 1];
        StringBuilder fileName = new StringBuilder("");
        for (int i = 0; i < fileParts.length - 1; i++) {
            fileName.append(fileParts[i]);
        }

        byte[] fNameBytes = fileName.toString().getBytes("utf-8");
        byte[] fExtBytes = fileExtension.getBytes("utf-8");
        byte[] fNameSize = {(byte) fNameBytes.length};
        byte[] fExtSize = {(byte) fExtBytes.length};
        byte[] fileBytes = Files.readAllBytes(file.toPath());

        fileBytes = concatArray(concatArray(concatArray(fNameSize, fNameBytes), concatArray(fExtSize, fExtBytes)), fileBytes);
        sendPacks(RECEIVE_TYPE_FILE, fileBytes);
    }

    public File getFileByByteArr(byte[] buff) throws IOException {
        byte fNameSize = buff[0];
        byte[] fNameBytes = Arrays.copyOfRange(buff, 1, fNameSize + 1);
        byte fExtSize = buff[fNameSize + 1];
        byte[] fExtBytes = Arrays.copyOfRange(buff, fNameSize + 2, fNameSize + fExtSize + 2);

        String fName = new String(fNameBytes, "utf-8"), fExt = new String(fExtBytes, "utf-8");

        File file = File.createTempFile(fName, "." + fExt);
        FileOutputStream fileOutputStream = new FileOutputStream(file);
        fileOutputStream.write(Arrays.copyOfRange(buff, fNameSize + fExtSize + 2, buff.length));
        fileOutputStream.close();

        return file;
    }

    private void sendPacks(byte type, byte[] buffData) {
        buffData = concatArray(concatArray(new byte[]{type}, intToByteArr(buffData.length)), buffData);

        int countPacks = getCountPackets(buffData.length);
        for (int i = 0; i < countPacks; i++) {
            byte[] b = Arrays.copyOfRange(buffData, i * 4096, (i + 1) * 4096);
            write(b);

            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void write(byte[] buff) {
        try {
            outputStream.write(buff, 0, buff.length);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void throwException() {
        throw new NullPointerException("Serial port is not initialized...");
    }

    public boolean isAvailable() {
        if (serialPort != null) {
            return !(serialPort.bytesAvailable() == 0);
        } else throwException();

        return false;
    }

    public void initNewSerialPort(String comPortName, int baudRate) {
        if (serialPort != null && comPortName.equalsIgnoreCase(getComPortName()) && serialPort.getBaudRate() == baudRate)
            return;

        serialPort = SerialPort.getCommPort(comPortName.toUpperCase());
        serialPort.setBaudRate(baudRate);
    }

    public String getComPortName() {
        if (serialPort != null) {
            return serialPort.getSystemPortName();
        } else throwException();

        return null;
    }

    public static String[] getComPorts() {
        SerialPort[] ports = SerialPort.getCommPorts();

        String[] s = new String[ports.length];
        for (int i = 0; i < ports.length; i++) {
            s[i] = ports[i].getSystemPortName();
        }

        return s;
    }

    public void openPort() {
        if (serialPort != null) {
            serialPort.openPort();

            receiver.setReceiveAllPacksCompleteListener(this);
            serialPort.addDataListener(receiver);

            outputStream = serialPort.getOutputStream();
        } else throwException();
    }

    public void closePort() throws IOException {
        if (serialPort != null) {
            outputStream.close();
            outputStream = null;
            receiver.removeReceivedAllPacksCompleteListener();
            serialPort.removeDataListener();
            serialPort.closePort();
        } else throwException();
    }

    public boolean portIsOpen() {
        if (serialPort != null) {
            return serialPort.isOpen();
        } else throwException();

        return false;
    }

    private SerialPortPacketReceiver receiver = new SerialPortPacketReceiver();

    @Override
    public void receivedDataComplete(byte receiveType, byte[] receiveBuff) {
        switch (receiveType) {

            case RECEIVE_TYPE_STRING_MESSAGE:
                if (messageReceivedListener != null) {
                    messageReceivedListener.receiveMessage(getStringMessageByByteArr(receiveBuff));
                }

                break;

            case RECEIVE_TYPE_FILE:
                if (fileReceivedListener != null) {
                    try {
                        fileReceivedListener.receiveFile(getFileByByteArr(receiveBuff));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                break;

            default:
                return;
        }
    }

    private static class SerialPortPacketReceiver implements SerialPortPacketListener {

        private ReceiveAllPacksCompleteListener receiveAllPacksCompleteListener;

        public void setReceiveAllPacksCompleteListener(ReceiveAllPacksCompleteListener listener) {
            receiveAllPacksCompleteListener = listener;
        }

        public void removeReceivedAllPacksCompleteListener() {
            receiveAllPacksCompleteListener = null;
        }

        private int packetCounter = 0;
        private int countPacks = 0;
        private byte receiveType = -1;
        private int dataSize = 0;
        private byte[] receiveBuff = new byte[0];

        @Override
        public int getPacketSize() {
            return SerialPortService.PACKET_SIZE;
        }

        @Override
        public int getListeningEvents() {
            return SerialPort.LISTENING_EVENT_DATA_RECEIVED;
        }

        @Override
        public void serialEvent(SerialPortEvent serialPortEvent) {
            byte[] buff = serialPortEvent.getReceivedData();

            if (packetCounter == 0) {
                receiveType = buff[0];
                dataSize = byteArrToInt(Arrays.copyOfRange(buff, 1, 5));
                countPacks = getCountPackets(dataSize + 5);
            }

            writeToReceiveBuff(buff);
            packetCounter++;

            if (packetCounter == countPacks) {
                if (receiveAllPacksCompleteListener != null) {
                    receiveAllPacksCompleteListener.receivedDataComplete(receiveType, receiveBuff);
                }

                packetCounter = 0;
                countPacks = 0;
                receiveType = -1;
                dataSize = 0;
                receiveBuff = new byte[0];
            }
        }

        private void writeToReceiveBuff(byte[] wrBuff) {
            if (packetCounter == countPacks - 1 && packetCounter == 0) {
                receiveBuff = concatArray(receiveBuff, Arrays.copyOfRange(wrBuff, 5, dataSize + 5));

            } else if (packetCounter == countPacks - 1 && packetCounter != 0) {
                receiveBuff = concatArray(receiveBuff, Arrays.copyOfRange(wrBuff, 0,
                        (dataSize - packetCounter * PACKET_SIZE) + 5));

            } else {
                receiveBuff = concatArray(receiveBuff, Arrays.copyOfRange(wrBuff, (packetCounter == 0) ? 5 : 0, PACKET_SIZE));
            }
        }
    }
}

interface ReceiveAllPacksCompleteListener {
    void receivedDataComplete(byte receiveType, byte[] receiveBuff);
}