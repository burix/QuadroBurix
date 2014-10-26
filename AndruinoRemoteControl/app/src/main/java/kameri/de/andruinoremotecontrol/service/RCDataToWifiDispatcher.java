package kameri.de.andruinoremotecontrol.service;

import java.io.BufferedOutputStream;
import java.io.IOException;

import kameri.de.andruinoremotecontrol.service.commstack.RcCommStack;

/**
 * Created by burimkameri on 07.10.14.
 */
public class RCDataToWifiDispatcher implements RcCommStack.RcCommunicationStackListener {

    private int mCachedThrottle = -1;
    private int mCachedYaw = -1;
    private int mCachedPitch = -1;
    private int mCachedRoll = -1;

    private BufferedOutputStream mBufferedOutputStream;
    private byte[] mByteBuffer = new byte[7];

    public RCDataToWifiDispatcher(BufferedOutputStream buffOutStream) {
        mBufferedOutputStream = buffOutStream;
        mByteBuffer[0] = '[';
        mByteBuffer[1] = '!';
        mByteBuffer[6] = ']';
    }

    @Override
    public void onControlCommandReceived(int throttleValue, int yawValue, int pitchValue, int rollValue) {
        if (mBufferedOutputStream != null) {

            mCachedThrottle = throttleValue;
            mCachedYaw = yawValue;
            mCachedPitch = pitchValue;
            mCachedRoll = rollValue;

            // here we have the chance to filter the data

            mByteBuffer[2] = (byte) mCachedThrottle;
            mByteBuffer[3] = (byte) mCachedYaw;
            mByteBuffer[4] = (byte) mCachedPitch;
            mByteBuffer[5] = (byte) mCachedRoll;

            try {
                mBufferedOutputStream.write(mByteBuffer, 0, 7);
            } catch (IOException e) {
                e.printStackTrace();
            }

//            String networkEncodedString = "[" + commandType + "=" + controlValue + "]";
//
//            int commandType = -1;
//            int cachedValue = -1;
//            switch (control) {
//                case CONTROL_THROTTLE:
//                    commandType = ARDUINO_COMMAND_MOTOR_1_SPEED;
//                    cachedValue = mCachedThrottle;
//                    break;
//                case CONTROL_YAW:
//                    commandType = ARDUINO_COMMAND_MOTOR_2_SPEED;
//                    cachedValue = mCachedYaw;
//                    break;
//                case CONTROL_PITCH:
//                    commandType = ARDUINO_COMMAND_MOTOR_3_SPEED;
//                    cachedValue = mCachedPitch;
//                    break;
//                case CONTROL_ROLL:
//                    commandType = ARDUINO_COMMAND_MOTOR_4_SPEED;
//                    cachedValue = mCachedRoll;
//                    break;
//            }
//            if(cachedValue!=controlValue)
//            {
//                String networkEncodedString = "[" + commandType + "=" + controlValue + "]";
//                Log.d("RCDataToWifiDispatcher", "Sending networkEncodedString [" + networkEncodedString + "]");
//                mWriter.println(networkEncodedString);
//            }
        }
    }
}
