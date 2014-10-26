package kameri.de.andruinoremotecontrol.service.commstack;

import java.nio.BufferOverflowException;

/**
 * Created by burimkameri on 05.10.14.
 * <p/>
 * RcCommunicationStack interprets incoming bytes to radio control communication via defined
 * structures.
 */
public class RcCommStack {

    // --
    // common command index values
    private final static int ARDUINO_RC_COMMAND_LENGTH = 7;
    private final static int ARDUINO_RC_COMMAND_TYPE_INDEX = 1;
    private final static byte ARDUINO_RC_COMMAND_BEGIN = '[';
    private final static byte ARDUINO_RC_COMMAND_END = ']';

    // ---
    // control command byte index values
    private final static byte ARDUINO_RC_CONTROL_TYPE_VALUE = '!';
    private final static int ARDUINO_RC_CONTROL_THROTTLE = 2;
    private final static int ARDUINO_RC_CONTROL_YAW = 3;
    private final static int ARDUINO_RC_CONTROL_PITCH = 4;
    private final static int ARDUINO_RC_CONTROL_ROLL = 5;

    /**
     * Communication stack callback listener. Has to be implemented by those who want to get notified about
     * new received commands. Each command has it's own callback method with according arguments.
     */
    public interface RcCommunicationStackListener {

        /**
         * New control command with according arguments received.
         * @param throttleValue The throttle control value has changed.
         * @param yawValue The yaw control value has changed.
         * @param pitchValue The pitch control value has changed.
         * @param rollValue The roll control value has changed.
         */
        public void onControlCommandReceived(int throttleValue, int yawValue, int pitchValue, int rollValue);
    }

    private RcCommunicationStackListener mListener;

    private static final int BUFFER_SIZE = 1024;// bytes
    private byte[] mBuf = new byte[BUFFER_SIZE];
    private int mWritePos = 0;

    public void setStackListener(RcCommunicationStackListener listener) {
        mListener = listener;
    }

    /**
     * Append with this method when new data has been received by any transport layer. This stack
     * will immediately interpret those data and trigger the callback eventually.
     * @param newData The received data this stack should interpret.
     * @throws BufferOverflowException Will be thrown if the stack buffer exceeds.
     */
    public synchronized void appendNewData(byte[] newData) throws BufferOverflowException {

//        String receivedBytes = "";
//        for (int i = 0; i < newData.length; i++) {
//            receivedBytes += "(" + ((int) newData[i]) + ")";
//        }
//        Log.i("RCDataInterpreter", "RECEIVED_BYTES [" + receivedBytes + "]");

		/*
         * Add received bytes to our buffer
		 */
        write(newData, 0, newData.length);

//        Log.i("RCDataInterpreter", "    availableBytes: " + mWritePos);

        int numProbeBytes = mWritePos - (ARDUINO_RC_COMMAND_LENGTH - 1);

//            Log.i("RCDataInterpreter", "    additionalBytes: " + additionalBytes);

        int i = 0;
        while (i < numProbeBytes) {
//                Log.i("RCDataInterpreter", "    checkOnPos: mBuf[" + i + "] = " + mBuf[i] + ", mBuf[" + i + 7 + "] = " + mBuf[i + 7] + ";");
            if (mBuf[i] == ARDUINO_RC_COMMAND_BEGIN && mBuf[i + (ARDUINO_RC_COMMAND_LENGTH - 1)] == ARDUINO_RC_COMMAND_END) {
                byte commandType = mBuf[i + ARDUINO_RC_COMMAND_TYPE_INDEX];
                switch (commandType) {
                    case ARDUINO_RC_CONTROL_TYPE_VALUE:// ascii dec val 33
                        byte throttle = mBuf[i + ARDUINO_RC_CONTROL_THROTTLE];
                        byte yaw = mBuf[i + ARDUINO_RC_CONTROL_YAW];
                        byte pitch = mBuf[i + ARDUINO_RC_CONTROL_PITCH];
                        byte roll = mBuf[i + ARDUINO_RC_CONTROL_ROLL];

                        // these conversion are required in java as java bytes
                        // are always signed and we need values higher then 127, which
                        // int supports :)
                        int throttleInValue = (int) throttle & 0xFF;
                        int yawInValue = (int) yaw & 0xFF;
                        int pitchInValue = (int) pitch & 0xFF;
                        int rollInValue = (int) roll & 0xFF;

                        mListener.onControlCommandReceived(throttleInValue, yawInValue, pitchInValue, rollInValue);
                        break;
                }
                i += ARDUINO_RC_COMMAND_LENGTH;
            } else {
                i++;
            }
        }

//            Log.i("RCDataInterpreter", "    numCheckBytes: " + i);
        // if at least of amount of bytes has been probed, remove the written bytes
        if (i > 0) {
            mWritePos -= i;
            if (mWritePos != 0) {
                System.arraycopy(mBuf, i, mBuf, 0, mWritePos);
            }
        }
    }

    /**
     * This variant of writing bytes into a buffer is very insecure since there are no checks.
     *
     * @param b      Bytes to write
     * @param offset Offset the write should begin
     * @param length Length of bytes to write
     */
    private void write(byte b[], int offset, int length) throws BufferOverflowException {
        if (length > 0) {
            int newSize = mWritePos + length;
            if (newSize > mBuf.length) {
                throw new BufferOverflowException();
            }
            System.arraycopy(b, offset, mBuf, mWritePos, length);
            mWritePos = newSize;
        }
    }
}
