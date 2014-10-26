package kameri.de.andruinoremotecontrol.service;

import java.util.ArrayList;

import kameri.de.andruinoremotecontrol.service.commstack.RcCommStack;

/**
 * Created by burimkameri on 07.10.14.
 */
public class RCDataDispatcher implements RcCommStack.RcCommunicationStackListener {

    private ArrayList<RcCommStack.RcCommunicationStackListener> mRCDataListeners = new ArrayList<RcCommStack.RcCommunicationStackListener>();

    public RCDataDispatcher() {
    }

    @Override
    public synchronized void onControlCommandReceived(int throttleValue, int yawValue, int pitchValue, int rollValue) {
        for (RcCommStack.RcCommunicationStackListener listener : mRCDataListeners) {
            if (listener != null) {
                listener.onControlCommandReceived(throttleValue, yawValue, pitchValue, rollValue);
            }
        }
    }

    public synchronized void addListener(RcCommStack.RcCommunicationStackListener listener) {
        if (!mRCDataListeners.contains(listener)) {
            mRCDataListeners.add(listener);
        }
    }

    public synchronized void removeListener(RcCommStack.RcCommunicationStackListener listener) {
        if (!mRCDataListeners.contains(listener)) {
            mRCDataListeners.remove(listener);
        }
    }

    public synchronized void removeAllListeners() {
        mRCDataListeners.clear();
    }
}
